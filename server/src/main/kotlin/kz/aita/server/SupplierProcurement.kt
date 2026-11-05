package kz.aita.server

import kz.aita.*
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.security.MessageDigest
import java.util.UUID

internal object SupplierOrderCommands : Table("supplier_order_commands") {
    val id = uuid("id")
    val orderId = uuid("order_id").references(SupplierOrders.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id")
    val operation = text("operation")
    val fingerprint = text("fingerprint")
    val createdAtMillis = long("created_at_millis")
    override val primaryKey = PrimaryKey(id)
}
internal class SupplierProcurementFailure(val kind: String) : IllegalArgumentException(kind)
internal data class SupplierProcurementResult(val bundle: SupplierOrderWithLinesDataModel, val changed: Boolean)
private fun invalid(): Nothing = throw SupplierProcurementFailure("invalid")
private fun parseSupplierUuid(raw: String): UUID = runCatching { UUID.fromString(raw.trim()) }.getOrElse { invalid() }
private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
private fun lockCommand(id: UUID) {
    TransactionManager.current().exec("SELECT pg_advisory_xact_lock(hashtextextended('supplier-command:$id', 0))")
}
private fun replay(id: UUID, order: UUID, user: UUID, operation: String, digest: String): SupplierProcurementResult? {
    lockCommand(id)
    val row = SupplierOrderCommands.selectAll().where { SupplierOrderCommands.id eq id }.singleOrNull() ?: return null
    if (row[SupplierOrderCommands.orderId] != order || row[SupplierOrderCommands.userId] != user ||
        row[SupplierOrderCommands.operation] != operation || row[SupplierOrderCommands.fingerprint] != digest)
        throw SupplierProcurementFailure("conflict")
    return SupplierProcurementResult(supplierOrderWithLinesInsideTransaction(order) ?: invalid(), false)
}
private fun record(id: UUID, order: UUID, user: UUID, operation: String, digest: String, now: Long) {
    SupplierOrderCommands.insert { row ->
        row[SupplierOrderCommands.id] = id; row[orderId] = order; row[userId] = user
        row[SupplierOrderCommands.operation] = operation; row[fingerprint] = digest; row[createdAtMillis] = now
    }
}
private fun priceValid(price: PriceDataModel): Boolean {
    val decimal = price.price.trim().replace(',', '.').toBigDecimalOrNull() ?: return false
    val amount = decimal.toDouble()
    return amount.isFinite() && amount >= 0.0 && price.currency.trim().matches(Regex("[A-Za-z]{3}"))
}

internal fun supplierPriceOnConfiguredBasis(price: PriceDataModel, source: QuantityDataModel, configured: QuantityDataModel): PriceDataModel {
    if (!priceValid(price) || source.pricedAmount <= 0.0 || !source.pricedAmount.isFinite()) invalid()
    val normalized = java.math.BigDecimal(price.price.replace(',', '.').trim())
        .multiply(java.math.BigDecimal.valueOf(configured.pricedAmount))
        .divide(java.math.BigDecimal.valueOf(source.pricedAmount), java.math.MathContext.DECIMAL128)
    if (!normalized.toDouble().isFinite()) invalid()
    return price.copy(price = normalized.stripTrailingZeros().toPlainString())
}
private fun quantityMatchesConfiguredUnit(quantity: QuantityDataModel, measurement: String): Boolean {
    val configured = defaultServerQuantityForGoodsItem(measurement, quantity.total)
    return quantity.id == configured.id && quantity.roundTotal == configured.roundTotal && supplierOrderQuantityValid(quantity)
}

internal fun supplierRequestedAmountForStorage(lines: List<SupplierOrderLineDataModel>, supplier: UUID): PriceDataModel? {
    val currencies = lines.mapNotNull { it.expectedSupplyPrice?.currency?.trim()?.uppercase() }.distinct()
    if (currencies.size != 1 || lines.any { it.expectedSupplyPrice == null }) return null
    val total = lines.sumOf { it.requestedQuantity.total / it.requestedQuantity.pricedAmount * it.expectedSupplyPrice!!.price.replace(',', '.').toDouble() }
    if (!total.isFinite()) invalid()
    val rounded = total.roundMoney(); if (!rounded.isFinite()) invalid()
    return PriceDataModel(rounded.toStockMoneyText(), currencies.single(), supplier.toString())
}

internal fun createSupplierOrderInsideTransaction(user: UUID, store: UUID, supplier: UUID,
    body: SupplierOrderWithLinesDataModel, now: Long, beforeWrite: () -> Unit = {}): SupplierProcurementResult {
    val digest = fingerprint(jsonBase.encodeToString(body))
    // Legacy clients have no request ID but resend the same immutable body on transport retries.
    val orderId = if (body.order.id.isBlank()) UUID.nameUUIDFromBytes("supplier-create:$user:$digest".toByteArray()) else parseSupplierUuid(body.order.id)
    lockSupplierOrdersInsideTransaction(listOf(orderId))
    replay(orderId, orderId, user, "create", digest)?.let { return it }
    if (!SupplierOrders.select(SupplierOrders.id).where { SupplierOrders.id eq orderId }.empty()) throw SupplierProcurementFailure("conflict")
    if (body.order.status !in setOf(SupplierOrderStatusDataModel.Draft, SupplierOrderStatusDataModel.Sent) ||
        !body.order.isActive || body.lines.isEmpty() || body.lines.size > 500) invalid()
    val goodsIds = body.lines.map { parseSupplierUuid(it.goodsItemId) }
    if (goodsIds.distinct().size != goodsIds.size) invalid()
    val items = StockItems.selectAll().where { (StockItems.id inList goodsIds) and (StockItems.storeId eq store) and (StockItems.isActive eq true) }.associateBy { it[StockItems.id] }
    if (items.size != goodsIds.size || body.lines.any { line ->
        !line.isActive || !quantityMatchesConfiguredUnit(line.requestedQuantity, items[parseSupplierUuid(line.goodsItemId)]?.get(StockItems.measurementUnitId).orEmpty()) ||
            line.requestedQuantity.id != items[parseSupplierUuid(line.goodsItemId)]?.get(StockItems.measurementUnitId) ||
            line.expectedSupplyPrice?.let { !priceValid(it) } == true
    }) invalid()
    beforeWrite()
    val lines = body.lines.map { line -> line.cleanForStorage(orderId).copy(
        supplierAcceptedQuantity = null, supplierOfferedSupplyPrice = null, supplierComment = null,
        supplierCommentLocalized = emptyList(), substituteGoodsItemId = null, deliveredBatchIds = emptyList(), receivedQuantityTotal = 0.0)
    }
    val amount = supplierRequestedAmountForStorage(lines, supplier)
    val clean = body.order.cleanForStorage(user, store, supplier, now).copy(
        id = orderId.toString(), amount = amount, createdAtMillis = now, updatedAtMillis = now,
        deliveredAtMillis = null, confirmedDeliveryTimeMillis = null, supplierComment = null,
        supplierCommentLocalized = emptyList())
    val createdOrderId = orderId
    SupplierOrders.insert { it.setSupplierOrderColumns(createdOrderId, clean, user, store, supplier) }
    lines.forEach { line -> SupplierOrderLines.insert {
        it.setSupplierOrderLineColumns(UUID.randomUUID(), line, createdOrderId, parseSupplierUuid(line.goodsItemId))
    } }
    record(orderId, orderId, user, "create", digest, now)
    return SupplierProcurementResult(supplierOrderWithLinesInsideTransaction(orderId) ?: invalid(), true)
}

internal fun receiveSupplierOrderInsideTransaction(user: UUID, request: ReceiveSupplierOrderRequestDataModel,
    now: Long, beforeWrite: () -> Unit = {}): SupplierProcurementResult {
    val orderId = parseSupplierUuid(request.orderId)
    lockSupplierOrdersInsideTransaction(listOf(orderId))
    val digest = fingerprint(jsonBase.encodeToString(request.copy(receiptId = "")))
    val receiptId = if (request.receiptId.isBlank()) UUID.nameUUIDFromBytes("supplier-receive:$user:$orderId:$digest".toByteArray()) else parseSupplierUuid(request.receiptId)
    replay(receiptId, orderId, user, "receive", digest)?.let { return it }
    val bundle = supplierOrderWithLinesInsideTransaction(orderId) ?: invalid()
    if (!bundle.order.canReceiveSupplierOrder()) throw SupplierProcurementFailure("closed")
    val store = parseSupplierUuid(bundle.order.storeId); val supplier = parseSupplierUuid(bundle.order.supplierId)
    if (request.receivedLines.isEmpty() || request.receivedLines.size > 500) invalid()
    val active = bundle.lines.filter { it.isActive }.associateBy { it.id }
    val lineIds = request.receivedLines.map { parseSupplierUuid(it.orderLineId).toString() }
    if (lineIds.distinct().size != lineIds.size) invalid()
    // Validate the complete request before inserting even its first batch.
    request.receivedLines.forEach { received ->
        val line = active[parseSupplierUuid(received.orderLineId).toString()] ?: invalid()
        val item = parseSupplierUuid(received.goodsItemId)
        val expectedItem = parseSupplierUuid(line.substituteGoodsItemId?.takeIf(String::isNotBlank) ?: line.goodsItemId)
        val expiration = received.expirationDateMillis; val manufactured = received.manufacturedAtMillis
        if (item != expectedItem || !supplierReceiptQuantityValid(line, received.receivedQuantity) || !priceValid(received.actualSupplyPrice) ||
            (expiration != null && manufactured != null && expiration < manufactured)) invalid()
        val row = StockItems.selectAll().where { (StockItems.id eq item) and (StockItems.storeId eq store) and (StockItems.isActive eq true) }.singleOrNull() ?: invalid()
        if (!quantityMatchesConfiguredUnit(received.receivedQuantity, row[StockItems.measurementUnitId])) invalid()
    }
    beforeWrite()
    val nextTotals = active.mapValues { it.value.receivedQuantityTotalValue() }.toMutableMap()
    request.receivedLines.forEach { received ->
        val lineId = parseSupplierUuid(received.orderLineId); val line = active.getValue(lineId.toString()); val item = parseSupplierUuid(received.goodsItemId)
        val configuredQuantity = defaultServerQuantityForGoodsItem(received.receivedQuantity.id, received.receivedQuantity.total)
        val configuredPrice = supplierPriceOnConfiguredBasis(received.actualSupplyPrice, received.receivedQuantity, configuredQuantity)
        val batchId = UUID.randomUUID()
        val shelfPriority = StockBatchesV2.select(StockBatchesV2.id).where { (StockBatchesV2.goodsItemId eq item) and (StockBatchesV2.storeId eq store) and (StockBatchesV2.isActive eq true) }.count().toInt()
        StockBatchesV2.insert { row ->
            row[id] = batchId; row[goodsItemId] = item; row[userId] = user; row[storeId] = store
            row[supplierId] = supplier; row[supplierOrderId] = orderId; row[quantity] = configuredQuantity
            row[supplyPrice] = configuredPrice.copy(supplierId = supplier.toString())
            row[deliveredAtMillis] = now; row[manufacturedAtMillis] = received.manufacturedAtMillis
            row[expirationDateMillis] = received.expirationDateMillis; row[discounts] = received.discounts
            row[promotions] = received.promotions.sanitizedStockPromotions(); row[shelfPosition] = (shelfPriority + 1).toString()
            row[StockBatchesV2.shelfPriority] = shelfPriority; row[status] = StockBatchStatusDataModel.Delivered.name
            row[additionalNotes] = received.notes?.trim()?.takeIf(String::isNotBlank)
            row[additionalNotesLocalized] = received.notesLocalized.map { it.copy(language = it.language.trim(), value = it.value.trim()) }.filter { it.language.isNotBlank() && it.value.isNotBlank() }.distinctBy { it.language }
            row[createdAtMillis] = now; row[updatedAtMillis] = now; row[createdByUserId] = user; row[isActive] = true
        }
        val total = line.receivedQuantityTotalValue() + received.receivedQuantity.total
        nextTotals[line.id] = total
        SupplierOrderLines.update({ SupplierOrderLines.id eq lineId }) {
            it[receivedQuantityTotal] = total
            it[deliveredBatchIds] = (line.deliveredBatchIds + batchId.toString()).distinct()
        }
    }
    val complete = active.isNotEmpty() && active.all { (id, line) -> nextTotals.getValue(id) + 0.0000001 >= line.receivableQuantity().total }
    SupplierOrders.update({ SupplierOrders.id eq orderId }) {
        it[status] = if (complete) SupplierOrderStatusDataModel.Delivered.name else SupplierOrderStatusDataModel.PartiallyDelivered.name
        it[deliveredAtMillis] = if (complete) now else null; it[updatedAtMillis] = maxOf(now, bundle.order.updatedAtMillis + 1L)
    }
    record(receiptId, orderId, user, "receive", digest, now)
    return SupplierProcurementResult(supplierOrderWithLinesInsideTransaction(orderId) ?: invalid(), true)
}

internal fun validateSupplierOrderEditInsideTransaction(existing: SupplierOrderWithLinesDataModel,
    requested: SupplierOrderWithLinesDataModel, supplierSide: Boolean) {
    if (!existing.order.isActive || existing.order.status in setOf(SupplierOrderStatusDataModel.Cancelled, SupplierOrderStatusDataModel.Delivered) ||
        existing.lines.any { it.receivedQuantityTotalValue() > 0.0 }) throw SupplierProcurementFailure("closed")
    if (requested.order.updatedAtMillis > 0L && requested.order.updatedAtMillis != existing.order.updatedAtMillis)
        throw SupplierProcurementFailure("conflict")
    if (!supplierSide && existing.order.status !in setOf(SupplierOrderStatusDataModel.Draft, SupplierOrderStatusDataModel.Sent))
        throw SupplierProcurementFailure("conflict")
    val lines = requested.lines.filter { it.isActive }
    if (lines.isEmpty() || lines.size > 500 || lines.map { it.goodsItemId }.distinct().size != lines.size) invalid()
    val ids = lines.map { it.id }.filter(String::isNotBlank)
    if (ids.distinct().size != ids.size) invalid()
    val current = existing.lines.filter { it.isActive }.associateBy { it.id }
    val allCurrent = existing.lines.associateBy { it.id }
    // Receiving evidence is server-owned in every mutation path, including newly inserted lines.
    requested.lines.forEach { line ->
        val previous = allCurrent[line.id]
        if (line.deliveredBatchIds != previous?.deliveredBatchIds.orEmpty() ||
            (line.receivedQuantityTotal ?: 0.0) != (previous?.receivedQuantityTotal ?: 0.0)) invalid()
    }
    if (!supplierSide && existing.lines.any { it.supplierAcceptedQuantity != null || it.supplierOfferedSupplyPrice != null })
        throw SupplierProcurementFailure("conflict")
    if (!supplierSide && lines.any { it.supplierAcceptedQuantity != null || it.supplierOfferedSupplyPrice != null || it.substituteGoodsItemId != null }) invalid()
    val store = parseSupplierUuid(existing.order.storeId)
    if (supplierSide && (ids.toSet() != current.keys || lines.size != current.size)) invalid()
    lines.forEach { line ->
        if (!supplierOrderQuantityValid(line.requestedQuantity) || line.expectedSupplyPrice?.let { !priceValid(it) } == true) invalid()
        val item = parseSupplierUuid(line.goodsItemId)
        val stock = StockItems.selectAll().where { (StockItems.id eq item) and (StockItems.storeId eq store) and (StockItems.isActive eq true) }.singleOrNull() ?: invalid()
        if (!quantityMatchesConfiguredUnit(line.requestedQuantity, stock[StockItems.measurementUnitId])) invalid()
        if (supplierSide) {
            val previous = current[line.id] ?: invalid()
            if (line.goodsItemId != previous.goodsItemId || line.requestedQuantity != previous.requestedQuantity) invalid()
            line.supplierAcceptedQuantity?.let { accepted ->
                if (!supplierOrderQuantityValid(accepted, allowZero = true) || accepted.id != previous.requestedQuantity.id ||
                    accepted.pricedAmount != previous.requestedQuantity.pricedAmount || accepted.roundTotal != previous.requestedQuantity.roundTotal) invalid()
            }
            line.supplierOfferedSupplyPrice?.let { if (!priceValid(it)) invalid() }
            line.substituteGoodsItemId?.takeIf(String::isNotBlank)?.let { raw ->
                val substitute = parseSupplierUuid(raw)
                if (StockItems.select(StockItems.id).where { (StockItems.id eq substitute) and (StockItems.storeId eq store) and
                    (StockItems.measurementUnitId eq line.requestedQuantity.id) and (StockItems.isActive eq true) }.empty()) invalid()
            }
        }
    }
    if (!supplierSide && requested.order.status !in setOf(SupplierOrderStatusDataModel.Draft, SupplierOrderStatusDataModel.Sent)) invalid()
}
