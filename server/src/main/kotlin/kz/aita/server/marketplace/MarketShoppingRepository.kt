package kz.aita.server.marketplace

import kz.aita.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

/** No stock or money writes. All mutations + immutable outcomes commit together. */
internal class MarketShoppingRepository(private val db: Connection, private val market: MarketplaceRepository) {
    init { check(!db.autoCommit) }

    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        db.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, arg -> statement.setObject(index + 1, arg) }
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
        }
    private fun execute(sql: String, vararg args: Any?) = db.prepareStatement(sql).use { statement ->
        args.forEachIndexed { index, arg -> statement.setObject(index + 1, arg) }
        statement.executeUpdate()
    }
    private fun revision(user: UUID): Long = query("SELECT revision FROM buyer_shopping_lists WHERE user_id=?", user) { it.getLong(1) }.singleOrNull() ?: 0L
    private fun lines(user: UUID): List<MarketShoppingLine> = query(
        "SELECT * FROM buyer_shopping_lines WHERE user_id=? ORDER BY created_at_millis,offer_id", user) { row ->
        MarketShoppingLine(row.getString("offer_id"), row.getString("store_id"), row.getString("public_title"),
            row.getString("public_shop_name"), row.getInt("units"), jsonBase.decodeFromString(row.getString("basis")),
            jsonBase.decodeFromString(row.getString("unit_name")), row.getLong("updated_at_millis"))
    }
    fun snapshot(user: UUID): MarketShoppingSnapshot {
        val now = System.currentTimeMillis()
        return MarketShoppingSnapshot(user.toString(), revision(user), market.quoteShopping(user, lines(user), now), now)
    }
    private data class Recorded(val hash: String, val accepted: Boolean, val errorKey: String?, val revision: Long?)
    fun comparison(user: UUID, request: MarketComparisonRequest): MarketComparisonPage {
        val selection = request.selection
        if (!selection.isValidMarketComparison()) marketFail("market.comparison_invalid")
        val sourceId = marketUuid(selection.offerId)
        val now = System.currentTimeMillis()
        val reference = if (selection.shoppingRevision != null) {
            if (revision(user) != selection.shoppingRevision) marketFail("market.shopping_changed", 409)
            val line = lines(user).firstOrNull { it.offerId == sourceId.toString() } ?: marketFail("market.shopping_changed", 409)
            if (line.units != selection.units || line.basis != selection.basis) marketFail("market.shopping_changed", 409)
            line
        } else {
            val offer = market.offersByIds(user, listOf(sourceId), now, selection.units).singleOrNull() ?: marketFail("market.unavailable", 404)
            if (!offer.matchesComparison(selection)) marketFail("market.comparison_changed", 409)
            MarketShoppingLine(offer.id, offer.storefront.storeId, offer.title, offer.storefront.displayName,
                selection.units, selection.basis, offer.unitName, offer.sourceUpdatedAtMillis)
        }
        return market.compare(user, request, reference, now)
    }

    /** A separate endpoint keeps older servers from ignoring replacement fields and merely ADDING. */
    fun replace(user: UUID, session: UUID?, request: MarketShoppingCommand): MarketShoppingOutcome {
        if (request.replaceOfferId == null) marketFail("market.shopping_invalid")
        return applyCommand(user, session, request, replacement = true)
    }
    fun apply(user: UUID, session: UUID?, request: MarketShoppingCommand): MarketShoppingOutcome =
        applyCommand(user, session, request, replacement = false)

    private fun applyCommand(user: UUID, session: UUID?, request: MarketShoppingCommand, replacement: Boolean): MarketShoppingOutcome {
        val id = marketUuid(request.commandId)
        val offerId = marketUuid(request.offerId)
        if (!request.isValidMarketShoppingCommand() || replacement != (request.replaceOfferId != null)) marketFail("market.shopping_invalid")
        val replacingId = request.replaceOfferId?.let(::marketUuid)
        val hash = MessageDigest.getInstance("SHA-256").digest(jsonBase.encodeToString(request).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        // Shared by all device sessions of this account. No store/subscription is needed to plan.
        // REPEATABLE READ conflicts retry the whole outer transaction, never just the mutation.
        if (query("SELECT id FROM users WHERE id=? AND is_active FOR UPDATE", user) { it.getString(1) }.isEmpty())
            marketFail("market.shopping_denied", 403)
        val now = System.currentTimeMillis()
        execute("""INSERT INTO buyer_shopping_lists(user_id,created_at_millis,updated_at_millis)
            VALUES (?,?,?) ON CONFLICT(user_id) DO NOTHING""", user, now, now)
        // A user-row lock alone does not advance a REPEATABLE READ snapshot when that row did
        // not change. Lock the revision row too: a concurrent revision forces an outer retry,
        // including for no-op removals/replays which otherwise would never update that row.
        query("SELECT revision FROM buyer_shopping_lists WHERE user_id=? FOR UPDATE", user) { it.getLong(1) }.single()
        val recorded = query("SELECT * FROM buyer_shopping_commands WHERE user_id=? AND command_id=?", user, id) {
            Recorded(it.getString("request_hash"), it.getBoolean("accepted"), it.getString("error_key"),
                it.getLong("applied_revision").let { value -> if (it.wasNull()) null else value })
        }.singleOrNull()
        if (recorded != null) {
            if (recorded.hash != hash) marketFail("market.shopping_command_mismatch", 409)
            return MarketShoppingOutcome(request.commandId, recorded.accepted, recorded.revision, true,
                recorded.errorKey, snapshot(user))
        }
        val currentRevision = revision(user)
        val currentLines = lines(user)
        val current = currentLines.firstOrNull { it.offerId == offerId.toString() }
        val replacing = currentLines.firstOrNull { it.offerId == replacingId?.toString() }
        var error: String? = null
        var source: MarketOffer? = null
        when {
            currentRevision != request.expectedRevision -> error = "market.shopping_changed"
            currentRevision == Long.MAX_VALUE -> error = "market.shopping_changed"
            replacingId != null -> {
                when {
                    replacing == null || replacing.units != request.units || replacing.basis != request.basis -> error = "market.shopping_changed"
                    current != null -> error = "market.comparison_already_listed"
                    else -> {
                        source = market.offersByIds(user, listOf(offerId), now, request.units).singleOrNull()
                        val target = source
                        if (target == null) error = "market.unavailable"
                        else if (target.shoppingBasis() != request.basis || target.storefront.storeId == replacing.storeId) error = "market.comparison_changed"
                        else {
                            val quote = market.quoteShopping(user, listOf(MarketShoppingLine(target.id, target.storefront.storeId,
                                target.title, target.storefront.displayName, request.units, requireNotNull(request.basis), target.unitName)), now).single()
                            if (quote.status != MARKET_QUOTE_ESTIMATED || quote.subtotalMinor == null) error = "market.comparison_unavailable"
                            else if (quote.subtotalMinor != request.reviewedSubtotalMinor) error = "market.comparison_price_changed"
                        }
                    }
                }
            }
            request.units > 0 && current != null && current.basis != request.basis -> error = "market.shopping_basis_changed"
            request.units > 0 && current == null -> {
                if (currentLines.size >= MARKET_SHOPPING_MAX_LINES) error = "market.shopping_limit"
                else {
                    source = market.offersByIds(user, listOf(offerId), now, request.units).singleOrNull()
                    if (source == null) error = "market.unavailable"
                    else if (source.shoppingBasis() == null || source.shoppingBasis() != request.basis)
                        error = "market.shopping_basis_changed"
                }
            }
        }
        var applied: Long? = null
        if (error == null) {
            val changed = if (request.units == 0) current != null else current?.units != request.units
            if (replacingId != null) {
                // The existing list position survives; both deletion and insertion are in this same transaction.
                val created = query("SELECT created_at_millis FROM buyer_shopping_lines WHERE user_id=? AND offer_id=?", user, replacingId) { it.getLong(1) }.single()
                val offer = requireNotNull(source)
                execute("DELETE FROM buyer_shopping_lines WHERE user_id=? AND offer_id=?", user, replacingId)
                execute("""INSERT INTO buyer_shopping_lines
                    (user_id,offer_id,store_id,public_title,public_shop_name,units,basis,unit_name,created_at_millis,updated_at_millis)
                    VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)""", user, offerId, marketUuid(offer.storefront.storeId),
                    offer.title, offer.storefront.displayName, request.units, jsonBase.encodeToString(requireNotNull(request.basis)),
                    jsonBase.encodeToString(offer.unitName), created, now)
            }
            else if (request.units == 0) execute("DELETE FROM buyer_shopping_lines WHERE user_id=? AND offer_id=?", user, offerId)
            else if (current != null) {
                if (changed) execute("UPDATE buyer_shopping_lines SET units=?,updated_at_millis=? WHERE user_id=? AND offer_id=?",
                    request.units, now, user, offerId)
            } else {
                val offer = requireNotNull(source)
                execute("""INSERT INTO buyer_shopping_lines
                    (user_id,offer_id,store_id,public_title,public_shop_name,units,basis,unit_name,created_at_millis,updated_at_millis)
                    VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)""", user, offerId, marketUuid(offer.storefront.storeId),
                    offer.title, offer.storefront.displayName, request.units, jsonBase.encodeToString(requireNotNull(request.basis)),
                    jsonBase.encodeToString(offer.unitName), now, now)
            }
            applied = if (changed) currentRevision + 1L else currentRevision
            if (changed) execute("UPDATE buyer_shopping_lists SET revision=?,updated_at_millis=? WHERE user_id=?", applied, now, user)
        }
        // Rejections are recorded too: an uncertain old command must not become a different success
        // merely because the list or a publication changed before its retry.
        execute("""INSERT INTO buyer_shopping_commands
            (user_id,command_id,request_hash,session_id,offer_id,requested_units,expected_revision,accepted,error_key,applied_revision,created_at_millis,replaced_offer_id,reviewed_subtotal_minor)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""", user, id, hash, session, offerId, request.units, request.expectedRevision,
            error == null, error, applied, now, replacingId, request.reviewedSubtotalMinor)
        return MarketShoppingOutcome(request.commandId, error == null, applied, errorKey = error, snapshot = snapshot(user))
    }
}
