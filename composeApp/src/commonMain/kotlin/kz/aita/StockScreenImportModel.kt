package kz.aita

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class StockScreenImportDraft(
    val schema: Int = 1,
    val accountId: String,
    val storeId: String,
    val rows: List<ScreenStockRow> = emptyList(),
)
internal val stockScreenImportJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Restored identifiers are write/retry identities, not merely presentation keys. Never repair them
 * silently: changing or sharing an item/batch id can repeat stock or acknowledge a different row.
 */
internal fun validateStockScreenImportDraft(draft: StockScreenImportDraft, accountId: String, storeId: String) {
    require(draft.schema == 1 && draft.accountId == accountId && draft.storeId == storeId && draft.rows.size <= 3000)
    fun validId(value: String) = value.isNotBlank() && value.length <= 200 && value == value.trim() &&
        value.none { it.code < 32 || it.code == 127 }
    require(validId(accountId) && validId(storeId))
    require(draft.rows.all { row -> validId(row.id) && validId(row.itemId) && validId(row.batchId) &&
        (!row.itemSaved || row.importStarted) && (!row.complete || (row.itemSaved && !row.selected)) &&
        (!row.importStarted || row.reviewed) })
    require(draft.rows.map { it.id }.distinct().size == draft.rows.size)
    require(draft.rows.map { it.itemId }.distinct().size == draft.rows.size)
    require(draft.rows.map { it.batchId }.distinct().size == draft.rows.size)
}

internal fun stockScreenDraftFits(accountId: String, storeId: String, rows: List<ScreenStockRow>): Boolean =
    rows.size <= 3000 && stockScreenImportJson.encodeToString(StockScreenImportDraft.serializer(),
        StockScreenImportDraft(accountId = accountId, storeId = storeId, rows = rows)).length <= 7_500_000

internal fun ScreenStockRow.resolveUnit(units: List<QuantityDataModel>): QuantityDataModel? =
    units.firstOrNull { it.id == unitId && unitId.isNotBlank() } ?: units.singleOrNull { candidate ->
        unit.isNotBlank() && candidate.immutableUnitName.any { screenStockUnit(it.value) == screenStockUnit(unit) }
    }

internal fun ScreenStockRow.problems(configuration: GlobalAppConfigurationDataModel): List<String> = buildList {
    if (name.isBlank() || name.length > 300) add("NAME")
    if (screenStockBarcode(barcode) == null) add("BARCODE")
    if (screenStockNumber(sale) == null) add("SALE")
    if (screenStockNumber(supply) == null) add("SUPPLY")
    for ((field,value) in listOf("SALE" to sale,"SUPPLY" to supply)) {
        val embeddedCurrency=screenStockCurrency(value)
        if (embeddedCurrency.isNotBlank() && embeddedCurrency!=currency && field !in this) add(field)
    }
    if (description.length > 2000) add("DESCRIPTION")
    for ((field, value) in listOf("SALE" to sale, "SUPPLY" to supply, "QUANTITY" to quantity)) {
        if (screenStockNumberAmbiguous(value) && field !in resolvedNumbers && field !in this) add(field)
    }
    if (configuration.countries.withSupportedCountries().flatMap { it.currencies }.none { it.code == currency }) add("CURRENCY")
    val unit = resolveUnit(configuration.goodsItemsQuantityUnits)
    if (unit == null) add("UNIT")
    if (quantity.isNotBlank()) {
        val count = screenStockNumber(quantity)?.toDoubleOrNull()
        if (count == null || (unit?.roundTotal == true && count % 1.0 != 0.0)) add("QUANTITY")
    }
}

internal fun ScreenStockRow.toStockItem(accountId: String, storeId: String, language: String,
    configuration: GlobalAppConfigurationDataModel): GoodsItemDataModel {
    require(problems(configuration).isEmpty())
    require(itemId.isNotBlank())
    return GoodsItemDataModel(id = itemId, userId = accountId, storeId = storeId,
        barcodes = listOf(barcode.trim()), name = listOf(LocalizedStringDataModel(language, name.trim()), LocalizedStringDataModel("main", name.trim())),
        description = if (description.isBlank()) emptyList() else listOf(LocalizedStringDataModel(language, description)),
        measurementUnitId = requireNotNull(resolveUnit(configuration.goodsItemsQuantityUnits)).id,
        categoryIds = listOfNotNull(categoryId.takeIf { it.isNotBlank() }),
        salePrices = listOf(PriceDataModel(requireNotNull(screenStockNumber(sale)), currency, "")),
        returnPrices = listOf(PriceDataModel(requireNotNull(screenStockNumber(sale)), currency, "")),
        supplyPrices = listOf(PriceDataModel(requireNotNull(screenStockNumber(supply)), currency, "")))
}

internal fun ScreenStockRow.toStockBatch(accountId: String, storeId: String,
    configuration: GlobalAppConfigurationDataModel): GoodsBatchDataModel? {
    val count = if (quantity.isBlank()) 0.0 else requireNotNull(screenStockNumber(quantity)).toDouble()
    if (count <= 0) return null
    require(batchId.isNotBlank() && itemId.isNotBlank())
    return GoodsBatchDataModel(id = batchId, goodsItemId = itemId, userId = accountId, storeId = storeId,
        quantity = requireNotNull(resolveUnit(configuration.goodsItemsQuantityUnits)).copy(total = count),
        supplyPrice = PriceDataModel(requireNotNull(screenStockNumber(supply)), currency, ""),
        kind = StockBatchKindDataModel.UNIVERSAL, status = StockBatchStatusDataModel.Delivered)
}

internal fun ScreenStockRow.hasExistingBarcode(existing: List<GoodsItemDataModel>, storeId: String): Boolean {
    val token = barcode.normalizedBarcodeToken()
    return existing.any { it.isActive && it.storeId == storeId && it.id != itemId &&
        it.allBarcodeValues().any { code -> code.normalizedBarcodeToken() == token } }
}

/** A bulk review must not rescan and normalize the whole warehouse for every input row. */
internal fun stockImportBarcodeOwners(existing: List<GoodsItemDataModel>, storeId: String): Map<String, Set<String>> =
    buildMap<String, MutableSet<String>> {
        for (item in existing) if (item.isActive && item.storeId == storeId) {
            for (code in item.allBarcodeValues()) {
                val token = code.normalizedBarcodeToken()
                if (token.isNotEmpty()) getOrPut(token) { mutableSetOf() }.add(item.id)
            }
        }
    }

internal fun ScreenStockRow.hasExistingBarcode(owners: Map<String, Set<String>>): Boolean =
    owners[barcode.normalizedBarcodeToken()]?.any { it != itemId } == true

/** The durable outbox owns a write even when its visible stock projection failed or is still
 * hydrating. Resuming the same review row must not enqueue another command for that identity.
 * Prefer a rejection if an older client left multiple matching commands: never conceal it as saved.
 */
internal fun ScreenStockRow.pendingItemWrite(pending: List<InventoryCreateCommand>, storeId: String): InventoryCreateCommand? {
    val matching = pending.filter { command -> command.storeId == storeId &&
        command.item?.let { it.id == itemId && it.storeId == storeId } == true }
    return matching.firstOrNull { it.rejected } ?: matching.firstOrNull()
}

internal fun ScreenStockRow.pendingBatchWrite(pending: List<InventoryCreateCommand>, storeId: String): InventoryCreateCommand? {
    val matching = pending.filter { command -> command.storeId == storeId && command.batches.any {
        it.id == batchId && it.goodsItemId == itemId && it.storeId == storeId
    } }
    return matching.firstOrNull { it.rejected } ?: matching.firstOrNull()
}

internal fun ScreenStockRow.canCorrectRejectedCreate(confirmedFailure: Boolean, ownerCurrent: Boolean,
    existing: List<GoodsItemDataModel>, pending: List<InventoryCreateCommand>): Boolean =
    confirmedFailure && ownerCurrent && !itemSaved && !complete &&
        existing.none { it.id == itemId } && pending.none { it.item?.id == itemId || it.batches.any { batch -> batch.goodsItemId == itemId } }
