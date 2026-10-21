package kz.aita.server.marketplace

import kz.aita.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import java.security.MessageDigest
import java.security.SecureRandom

internal class MarketDeliveryRepository(private val db: Connection, private val market: MarketplaceRepository,
    private val ownsStore: (UUID, UUID) -> Boolean, private val now: () -> Long = System::currentTimeMillis) {
    init { check(!db.autoCommit) }
    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> = db.prepareStatement(sql).use { s ->
        s.queryTimeout = 5
        args.forEachIndexed { i, a -> s.setObject(i + 1, a) }
        s.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
    }
    private fun execute(sql: String, vararg args: Any?) = db.prepareStatement(sql).use { s ->
        s.queryTimeout = 5; args.forEachIndexed { i, a -> s.setObject(i + 1, a) }; s.executeUpdate()
    }
    private fun lockUser(user: UUID) {
        if (query("SELECT id FROM users WHERE id=? AND is_active FOR UPDATE", user) { it.getObject(1) }.isEmpty()) fail("forbidden", 403)
    }
    private fun fail(key: String, status: Int = 400): Nothing = marketFail("delivery.$key", status)
    private fun manager(user: UUID, store: String) = ownsStore(user, marketUuid(store))
    private fun courierEligible(user: UUID, store: String): Boolean = manager(user, store) || query(
        """SELECT 1 FROM store_worker_memberships m JOIN stores s ON s.id=? AND s.is_active
           JOIN users u ON u.id=m.user_id AND u.is_active
           WHERE m.user_id=? AND m.is_active AND (m.store_id=s.id OR m.store_id=s.parent_store_id) LIMIT 1""",
        marketUuid(store), user) { true }.isNotEmpty()

    fun policy(user: UUID, store: String, seller: Boolean): MarketDeliveryPolicy {
        val branch = if (seller) {
            if (!manager(user, store)) fail("forbidden", 403)
            store
        } else market.publicShop(store).operatingBranchId
        return query("SELECT payload::text FROM market_delivery_policies WHERE branch_store_id=?", marketUuid(branch)) {
            jsonBase.decodeFromString<MarketDeliveryPolicy>(it.getString(1))
        }.singleOrNull() ?: MarketDeliveryPolicy(branch)
    }
    fun savePolicy(user: UUID, input: MarketDeliveryPolicy): MarketDeliveryPolicy {
        if (!input.isValid()) fail("policy_invalid")
        lockUser(user)
        if (!manager(user, input.branchStoreId)) fail("forbidden", 403)
        if (query("SELECT 1 FROM stores WHERE id=? AND is_active AND parent_store_id IS NOT NULL AND branch_type='INTERNET' FOR UPDATE",
                marketUuid(input.branchStoreId)) { true }.isEmpty()) fail("forbidden", 403)
        val prior = policy(user, input.branchStoreId, true)
        if (prior.revision != input.revision) fail("changed", 409)
        val next = input.copy(revision = prior.revision + 1, serviceArea = input.serviceArea.trim(), instructions = input.instructions.trim())
        execute("""INSERT INTO market_delivery_policies(branch_store_id,revision,payload) VALUES (?,?,?::jsonb)
            ON CONFLICT(branch_store_id) DO UPDATE SET revision=EXCLUDED.revision,payload=EXCLUDED.payload""",
            marketUuid(next.branchStoreId), next.revision, jsonBase.encodeToString(next))
        return next
    }
    private fun raw(id: String, lock: Boolean = false): Pair<MarketDeliveryOrder, String> = query(
        "SELECT payload::text,handover_code,position::text FROM market_delivery_orders WHERE id=?" + if (lock) " FOR UPDATE" else "",
        marketUuid(id)) { row ->
        val order = jsonBase.decodeFromString<MarketDeliveryOrder>(row.getString(1))
        order.copy(position = row.getString(3)?.let { jsonBase.decodeFromString<MarketCourierPosition>(it) }) to row.getString(2)
    }.singleOrNull() ?: fail("missing", 404)

    private fun visible(user: UUID, value: Pair<MarketDeliveryOrder, String>): MarketDeliveryOrder {
        val (order, code) = value
        val buyer = user.toString() == order.buyerId
        val seller = manager(user, order.branchStoreId)
        val courier = user.toString() == order.courierId && courierEligible(user, order.branchStoreId)
        if (!buyer && !seller && !courier) fail("forbidden", 403)
        return order.copy(handoverCode = code.takeIf { buyer && !order.terminal },
            position = order.position.takeIf { !order.terminal && order.request.mode == MarketDeliveryMode.EXPRESS })
    }
    fun get(user: UUID, id: String) = visible(user, raw(id))

    fun page(user: UUID, view: String, store: String?, before: String?): MarketDeliveryPage {
        val filter: String; val key: UUID
        when (view) {
            "buyer" -> { filter = "buyer_id"; key = user }
            "courier" -> { filter = "courier_id"; key = user }
            "seller" -> { if (store == null || !manager(user, store)) fail("forbidden", 403); filter = "branch_store_id"; key = marketUuid(store) }
            else -> fail("invalid")
        }
        val args = mutableListOf<Any?>(key)
        before?.let { args += marketUuid(it); args += key }
        val ids = query("SELECT id::text FROM market_delivery_orders WHERE $filter=?" + (if (before != null) " AND (created_at_millis,id) < (SELECT created_at_millis,id FROM market_delivery_orders WHERE id=? AND $filter=?)" else "") + " ORDER BY created_at_millis DESC,id DESC LIMIT 41",
            *args.toTypedArray()) { it.getString(1) }
        val rows = ids.take(40).mapNotNull { id ->
            val order = raw(id)
            if (view == "courier" && !courierEligible(user, order.first.branchStoreId)) null
            else visible(user, order).copy(position = null, handoverCode = null)
        }
        return MarketDeliveryPage(user.toString(), view, store.takeIf { view == "seller" }, rows,
            ids.takeIf { it.size > 40 }?.get(39), now())
    }

    fun command(user: UUID, command: MarketDeliveryCommand): MarketDeliveryOutcome {
        val commandId = marketUuid(command.commandId)
        if (command.note.length > 500 || command.note.any { it.code < 32 && it != '\n' } || command.expectedRevision !in 0 until Long.MAX_VALUE)
            fail("invalid")
        lockUser(user)
        val hash = MessageDigest.getInstance("SHA-256").digest(jsonBase.encodeToString(command).toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        query("SELECT request_hash,order_id::text FROM market_delivery_commands WHERE user_id=? AND command_id=?", user, commandId) {
            it.getString(1) to it.getString(2)
        }.singleOrNull()?.let { (savedHash, id) ->
            if (savedHash != hash) fail("changed", 409)
            return MarketDeliveryOutcome(command.commandId, get(user, id), replayed = true)
        }
        val order = if (command.action == MarketDeliveryAction.REQUEST) create(user, command) else change(user, command)
        execute("INSERT INTO market_delivery_commands(user_id,command_id,request_hash,order_id,created_at_millis) VALUES(?,?,?,?,?)",
            user, commandId, hash, marketUuid(order.id), now())
        return MarketDeliveryOutcome(command.commandId, get(user, order.id))
    }

    private fun create(user: UUID, command: MarketDeliveryCommand): MarketDeliveryOrder {
        val request = command.request ?: fail("invalid")
        if (command.orderId != null || !request.address.isValid(request.mode) || marketDeliveryItemsTotal(request.lines) == null) fail("invalid")
        val policy = policy(user, request.storefrontId, false)
        if (policy.revision != request.policyRevision || request.mode !in policy.modes || policy.currency != request.currency || policy.fee(request.mode) != request.feeMinor)
            fail("changed", 409)
        val snapshot = MarketShoppingRepository(db, market).snapshot(user)
        if (snapshot.revision != request.shoppingRevision) fail("changed", 409)
        val selected = snapshot.lines.filter { it.line.storeId == request.storefrontId && it.line.basis.currencyCode == request.currency && !it.line.collected }
        if (selected.size != request.lines.size || selected.isEmpty()) fail("changed", 409)
        val reviewed = request.lines.associateBy { it.line.offerId }
        if (selected.any { row ->
            val expected = reviewed[row.line.offerId]
            row.status != MARKET_QUOTE_ESTIMATED || row.unitPriceMinor == null || expected == null || expected.line.basis != row.line.basis ||
                expected.line.units != row.line.units || expected.unitPriceMinor != row.unitPriceMinor || row.offer?.storefront?.operatingBranchId != policy.branchStoreId
        }) fail("changed", 409)
        val open = query("SELECT count(*) FROM market_delivery_orders WHERE buyer_id=? AND status NOT IN ('DELIVERED','CANCELLED')", user) { it.getInt(1) }.single()
        if (open >= 50) fail("too_many", 409)
        val time = now()
        val scale = if (request.mode == MarketDeliveryMode.STANDARD) 86_400_000L else 60_000L
        val from = if (request.mode == MarketDeliveryMode.STANDARD) policy.standardMinDays else if (request.mode == MarketDeliveryMode.EXPRESS) policy.expressMinMinutes else 30
        val to = if (request.mode == MarketDeliveryMode.STANDARD) policy.standardMaxDays else if (request.mode == MarketDeliveryMode.EXPRESS) policy.expressMaxMinutes else 120
        val canonical = request.copy(lines = selected.map { MarketDeliveryLine(it.line, requireNotNull(it.unitPriceMinor)) },
            address = request.address.copy(recipient=request.address.recipient.trim(), phone=request.address.phone.trim(), address=request.address.address.trim(), note=request.address.note.trim()))
        val order = MarketDeliveryOrder(command.commandId, user.toString(), policy.branchStoreId, selected.first().line.shopName,
            canonical, createdAtMillis=time, updatedAtMillis=time, estimatedFromMillis=time + from * scale, estimatedToMillis=time + to * scale,
            events=listOf(MarketDeliveryEvent(MarketDeliveryAction.REQUEST, MarketDeliveryStatus.REQUESTED, time)))
        if (order.totalMinor == null) fail("invalid")
        val code = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')
        execute("""INSERT INTO market_delivery_orders(id,buyer_id,branch_store_id,revision,status,created_at_millis,updated_at_millis,handover_code,payload)
            VALUES(?,?,?,0,'REQUESTED',?,?,?,?::jsonb)""",marketUuid(order.id),user,marketUuid(order.branchStoreId),time,time,code,jsonBase.encodeToString(order))
        return order
    }

    private fun change(user: UUID, command: MarketDeliveryCommand): MarketDeliveryOrder {
        val (order, code) = raw(command.orderId ?: fail("invalid"), lock = true)
        visible(user, order to code)
        if (order.revision != command.expectedRevision) fail("changed", 409)
        val buyer = order.buyerId == user.toString()
        val seller = manager(user, order.branchStoreId)
        val courier = order.courierId == user.toString() && courierEligible(user, order.branchStoreId)
        if (command.action !in order.availableActions(buyer, seller, courier)) fail("transition", 409)
        val time = now()
        var next = order.copy(revision=order.revision + 1, status=command.action.nextStatus(order.status), updatedAtMillis=time, position=null, handoverCode=null)
        when (command.action) {
            MarketDeliveryAction.CONFIRM -> {
                val quoted = market.quoteShopping(marketUuid(order.buyerId), order.request.lines.map { it.line }, time)
                if (quoted.size != order.request.lines.size || quoted.zip(order.request.lines).any { (a,b) -> a.status != MARKET_QUOTE_ESTIMATED || a.unitPriceMinor != b.unitPriceMinor } || order.estimatedToMillis <= time)
                    fail("availability", 409)
            }
            MarketDeliveryAction.ASSIGN -> {
                val publicId = command.courierPublicId?.trim()?.takeIf { it.length in 1..16 } ?: fail("courier_invalid")
                val worker = query("SELECT id,concat_ws(' ',first_name,last_name) FROM users WHERE public_id=? AND is_active FOR UPDATE", publicId) {
                    it.getObject(1,UUID::class.java) to it.getString(2)
                }.singleOrNull() ?: fail("courier_invalid")
                if (!courierEligible(worker.first, order.branchStoreId)) fail("courier_invalid")
                next = next.copy(courierId=worker.first.toString(),courierName=worker.second.take(240),courierAccepted=false)
            }
            MarketDeliveryAction.ACCEPT_ASSIGNMENT -> next = next.copy(courierAccepted=true)
            MarketDeliveryAction.DELIVER -> if (!buyer && !MessageDigest.isEqual(code.toByteArray(), command.handoverCode.orEmpty().toByteArray())) fail("code_invalid")
            MarketDeliveryAction.DELAY -> {
                val from = command.estimatedFromMillis ?: fail("invalid")
                val to = command.estimatedToMillis ?: fail("invalid")
                if (from !in time..(time + 90L*86_400_000) || to !in from..(time + 90L*86_400_000) || command.note.isBlank()) fail("delay_invalid")
                next = next.copy(estimatedFromMillis=from,estimatedToMillis=to)
            }
            MarketDeliveryAction.CANCEL -> if (command.note.isBlank()) fail("reason_required")
            else -> Unit
        }
        next = next.copy(events = (order.events + MarketDeliveryEvent(command.action,next.status,time,command.note.trim(),
            next.estimatedFromMillis.takeIf { command.action == MarketDeliveryAction.DELAY }, next.estimatedToMillis.takeIf { command.action == MarketDeliveryAction.DELAY })).takeLast(100))
        execute("""UPDATE market_delivery_orders SET courier_id=?,revision=?,status=?,updated_at_millis=?,payload=?::jsonb,
            position=CASE WHEN ? THEN NULL ELSE position END WHERE id=?""", next.courierId?.let(::marketUuid),next.revision,next.status.name,time,
            jsonBase.encodeToString(next),next.terminal || command.action == MarketDeliveryAction.ASSIGN,marketUuid(next.id))
        return next
    }

    fun location(user: UUID, id: String, update: MarketCourierLocationUpdate): MarketDeliveryOrder {
        val (order, code) = raw(id, lock = true)
        val time = now()
        if (order.courierId != user.toString() || !courierEligible(user,order.branchStoreId)) fail("forbidden",403)
        if (!order.courierAccepted || order.revision != update.assignmentRevision || order.request.mode != MarketDeliveryMode.EXPRESS ||
            order.status !in setOf(MarketDeliveryStatus.ON_THE_WAY,MarketDeliveryStatus.ARRIVED)) fail("transition",409)
        if (!update.position.isValid(time)) fail("position_invalid")
        if (order.position?.capturedAtMillis?.let { it >= update.position.capturedAtMillis } == true) return visible(user,order to code)
        execute("UPDATE market_delivery_orders SET position=?::jsonb WHERE id=?",jsonBase.encodeToString(update.position.copy(receivedAtMillis=time)),marketUuid(id))
        return get(user,id)
    }
}
