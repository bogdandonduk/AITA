package kz.aita.server.marketplace

import kz.aita.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

/** No stock or money writes. All mutations + immutable outcomes commit together. */
internal class MarketShoppingRepository(private val db: Connection, private val market: MarketplaceRepository,
    // Internal test seam only. Routes always use the real catalogue projection.
    private val quoteLines: (UUID, List<MarketShoppingLine>, Long) -> List<MarketShoppingQuotedLine> = market::quoteShopping
) {
    init { check(!db.autoCommit) }
    private var mutationStartedNanos: Long? = null
    private fun mutationTimeoutSeconds(): Int? {
        val started = mutationStartedNanos ?: return null
        val remaining = 10_000_000_000L - (System.nanoTime() - started)
        if (remaining <= 0L) marketFail("market.basket_apply_busy", 503)
        return ((remaining + 999_999_999L) / 1_000_000_000L).toInt().coerceIn(1, 5)
    }

    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        db.prepareStatement(sql).use { statement ->
            mutationTimeoutSeconds()?.let { statement.queryTimeout = it }
            args.forEachIndexed { index, arg -> statement.setObject(index + 1, arg) }
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
        }
    private fun execute(sql: String, vararg args: Any?) = db.prepareStatement(sql).use { statement ->
        mutationTimeoutSeconds()?.let { statement.queryTimeout = it }
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
        return MarketShoppingSnapshot(user.toString(), revision(user), quoteLines(user, lines(user), now), now)
    }
    /** Same read-only repeatable-read snapshot for intent, availability and every candidate.
     * No row locks, command records, list writes, stock writes or payment operations.
     */
    fun basketPlan(user: UUID, request: MarketBasketRequest): MarketBasketResult = market.withBasketReadBudget {
        val input = request.normalizedBasketRequest() ?: marketFail("market.basket_invalid")
        if (revision(user) != input.expectedRevision) marketFail("market.basket_list_changed", 409)
        val current = snapshot(user)
        val pool = market.basketCandidates(user, current, input.city)
        buildMarketBasketResult(current, input, pool.choices, pool.limitedSourceIds, pool.checked)
    }

    private fun commandHash(request: MarketShoppingCommand) = MessageDigest.getInstance("SHA-256")
        .digest(jsonBase.encodeToString(request).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    /** Read-only recovery. In particular, NEVER insert a list, lock its row, validate expiry to
     * apply an unrecorded review, or infer non-delivery from a missing record in this snapshot.
     */
    fun lookup(user: UUID, request: MarketShoppingCommand): MarketShoppingCommandLookup {
        if (!request.isValidMarketShoppingCommand()) marketFail("market.shopping_invalid")
        val id = marketUuid(request.commandId)
        val record = query("SELECT * FROM buyer_shopping_commands WHERE user_id=? AND command_id=?", user, id) {
            Recorded(it.getString("request_hash"), it.getBoolean("accepted"), it.getString("error_key"),
                it.getLong("applied_revision").let { value -> if (it.wasNull()) null else value })
        }.singleOrNull()
        if (record != null && record.hash != commandHash(request)) marketFail("market.shopping_command_mismatch", 409)
        return MarketShoppingCommandLookup(request.commandId, System.currentTimeMillis(), record?.let {
            MarketShoppingOutcome(request.commandId, it.accepted, it.revision, replayed = true,
                errorKey = it.errorKey, snapshot = snapshot(user))
        })
    }

    /** Read immutable outcomes only. No joins to current inventory, staff/session data, or
     * another buyer's history. Expand and refresh one bounded window rather than mixing pages.
     */
    private fun activityProjection(includeDetails: Boolean) = """SELECT command_id,created_at_millis,
        expected_revision,accepted,applied_revision,error_key,requested_units,replaced_offer_id,reviewed_subtotal_minor,
        (basket_change IS NOT NULL) AS has_basket, basket_change->>'currencyCode' AS basket_currency,
        (basket_change->>'reviewedItemsSubtotalMinor')::bigint AS basket_subtotal,
        jsonb_array_length(basket_change->'lines') AS basket_lines,
        (SELECT count(*) FROM jsonb_array_elements(COALESCE(basket_change->'lines','[]'::jsonb)) AS r
            WHERE r->>'sourceOfferId' <> r->>'targetOfferId') AS basket_changes,
        (activity_details IS NOT NULL) AS details_recorded,
        COALESCE(activity_details#>>'{lines,0,after,title}',activity_details#>>'{lines,0,before,title}') AS preview_title,
        activity_details#>>'{lines,0,after,basis,currencyCode}' AS detail_currency,
        ${if (includeDetails) "activity_details" else "NULL::jsonb AS activity_details"}
        FROM buyer_shopping_commands"""

    private fun activityEntry(row: ResultSet): MarketShoppingActivityEntry {
        val basket = row.getBoolean("has_basket")
        val replaced = row.getString("replaced_offer_id")
        val units = row.getInt("requested_units")
        val expected = row.getLong("expected_revision")
        val accepted = row.getBoolean("accepted")
        val applied = row.getLong("applied_revision").let { if (row.wasNull()) null else it }
        val subtotal = row.getLong(if (basket) "basket_subtotal" else "reviewed_subtotal_minor").let { if (row.wasNull()) null else it }
        return MarketShoppingActivityEntry(row.getString("command_id"), row.getLong("created_at_millis"), expected,
            accepted, applied, row.getString("error_key"), when {
                basket -> MARKET_ACTIVITY_BASKET
                replaced != null -> MARKET_ACTIVITY_REPLACE
                units == 0 -> MARKET_ACTIVITY_REMOVE
                else -> MARKET_ACTIVITY_QUANTITY
            }, units, if (basket) row.getString("basket_currency") else if (subtotal != null) row.getString("detail_currency") else null,
            subtotal, if (basket) row.getInt("basket_lines") else 1,
            if (basket) row.getInt("basket_changes") else if (accepted && applied != expected) 1 else 0,
            row.getString("activity_details")?.let { jsonBase.decodeFromString<MarketShoppingActivityDetails>(it) },
            row.getBoolean("details_recorded"), row.getString("preview_title"))
    }

    fun activity(user: UUID, request: MarketShoppingActivityRequest): MarketShoppingActivityPage {
        if (!request.isValidShoppingActivityRequest()) marketFail("market.shopping_activity_invalid")
        val rows = query(activityProjection(includeDetails = false) + """ WHERE user_id=?
            ORDER BY created_at_millis DESC,command_id DESC LIMIT ?""", user, request.limit + 1, map = ::activityEntry)
        return MarketShoppingActivityPage(user.toString(), request, rows.take(request.limit),
            rows.size > request.limit, System.currentTimeMillis())
    }

    /** Keyset navigation over this account's immutable outcomes. Each request has its own
     * repeatable-read snapshot; a cursor does not pin a database transaction across HTTP calls.
     * Filter predicates are selected from a closed vocabulary, all values remain bound parameters.
     */
    fun activitySearch(user: UUID, request: MarketShoppingActivitySearchRequest): MarketShoppingActivitySearchPage {
        val input = request.normalizedActivitySearch() ?: marketFail("market.activity_search_invalid")
        val predicates = mutableListOf("user_id=?")
        val args = mutableListOf<Any?>(user)
        input.filter.commandId?.let { predicates += "command_id=?"; args += marketUuid(it) }
        when (input.filter.kind) {
            MARKET_ACTIVITY_BASKET -> predicates += "basket_change IS NOT NULL"
            MARKET_ACTIVITY_REPLACE -> predicates += "basket_change IS NULL AND replaced_offer_id IS NOT NULL"
            MARKET_ACTIVITY_REMOVE -> predicates += "basket_change IS NULL AND replaced_offer_id IS NULL AND requested_units=0"
            MARKET_ACTIVITY_QUANTITY -> predicates += "basket_change IS NULL AND replaced_offer_id IS NULL AND requested_units>0"
        }
        when (input.filter.result) {
            MARKET_ACTIVITY_RESULT_APPLIED -> predicates += "accepted=TRUE AND applied_revision>expected_revision"
            MARKET_ACTIVITY_RESULT_REJECTED -> predicates += "accepted=FALSE"
            MARKET_ACTIVITY_RESULT_UNCHANGED -> predicates += "accepted=TRUE AND applied_revision=expected_revision"
        }
        val filter = predicates.joinToString(" AND ")
        val pageArgs = args.toMutableList()
        val comparison = if (input.newer) ">" else "<"
        val cursorClause = input.boundary?.let {
            pageArgs.add(it.recordedAtMillis); pageArgs.add(marketUuid(it.commandId))
            " AND (created_at_millis,command_id) $comparison (?,?)"
        }.orEmpty()
        val ordering = if (input.newer) "ASC" else "DESC"
        pageArgs.add(MARKET_SHOPPING_ACTIVITY_PAGE_SIZE + 1)
        val rows = query(activityProjection(includeDetails = false) + " WHERE $filter$cursorClause" +
            " ORDER BY created_at_millis $ordering,command_id $ordering LIMIT ?", *pageArgs.toTypedArray(), map = ::activityEntry)
        val window = rows.take(MARKET_SHOPPING_ACTIVITY_PAGE_SIZE)
        val entries = if (input.newer) window.reversed() else window
        val forwardMore = rows.size > MARKET_SHOPPING_ACTIVITY_PAGE_SIZE
        // The opposite direction needs only one indexed existence read, not a total count and
        // not another copy of all history details. At the head there can be no newer row.
        val oppositeMore = if (input.boundary == null || entries.isEmpty()) false else {
            val edge = if (input.newer) entries.last() else entries.first()
            val opposite = if (input.newer) "<" else ">"
            query("SELECT 1 FROM buyer_shopping_commands WHERE $filter" +
                " AND (created_at_millis,command_id) $opposite (?,?) LIMIT 1",
                *(args + listOf(edge.recordedAtMillis, marketUuid(edge.commandId))).toTypedArray()) { true }.isNotEmpty()
        }
        return MarketShoppingActivitySearchPage(user.toString(), input, entries,
            hasOlder = if (input.newer) oppositeMore else forwardMore,
            hasNewer = if (input.newer) forwardMore else oppositeMore,
            checkedAtMillis = System.currentTimeMillis())
    }

    fun activityDetail(user: UUID, commandId: String): MarketShoppingActivityEntry =
        query(activityProjection(includeDetails = true) + " WHERE user_id=? AND command_id=?",
            user, marketUuid(commandId), map = ::activityEntry).singleOrNull()
            ?: marketFail("market.shopping_activity_missing", 404)

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

    /** Apply a frozen currency plan as ONE list edit, not sequential single-line requests.
     * All checks precede the first line write. Outer transaction retries include outcome lookup.
     */
    fun applyBasket(user: UUID, session: UUID?, request: MarketShoppingCommand): MarketShoppingOutcome {
        check(mutationStartedNanos == null)
        mutationStartedNanos = System.nanoTime()
        try { return market.withBasketReadBudget { applyBasketInsideTransaction(user, session, request) } }
        catch (failure: MarketFailure) {
            if (failure.key == "market.basket_busy") marketFail("market.basket_apply_busy", 503)
            throw failure
        } catch (failure: SQLException) {
            if (failure.sqlState in setOf("57014", "55P03")) marketFail("market.basket_apply_busy", 503)
            throw failure
        } finally { mutationStartedNanos = null }
    }

    private fun applyBasketInsideTransaction(user: UUID, session: UUID?, request: MarketShoppingCommand): MarketShoppingOutcome {
        if (!request.isValidMarketShoppingCommand()) marketFail("market.basket_apply_invalid")
        val basket = request.basketChange ?: marketFail("market.basket_apply_invalid")
        val id = marketUuid(request.commandId)
        val hash = commandHash(request)
        if (query("SELECT id FROM users WHERE id=? AND is_active FOR UPDATE", user) { it.getString(1) }.isEmpty())
            marketFail("market.shopping_denied", 403)
        val now = System.currentTimeMillis()
        execute("""INSERT INTO buyer_shopping_lists(user_id,created_at_millis,updated_at_millis)
            VALUES (?,?,?) ON CONFLICT(user_id) DO NOTHING""", user, now, now)
        query("SELECT revision FROM buyer_shopping_lists WHERE user_id=? FOR UPDATE", user) { it.getLong(1) }.single()
        val recorded = query("SELECT * FROM buyer_shopping_commands WHERE user_id=? AND command_id=?", user, id) {
            Recorded(it.getString("request_hash"), it.getBoolean("accepted"), it.getString("error_key"),
                it.getLong("applied_revision").let { value -> if (it.wasNull()) null else value })
        }.singleOrNull()
        // Replay FIRST, including after review expiry, publication changes or later list edits.
        if (recorded != null) {
            if (recorded.hash != hash) marketFail("market.shopping_command_mismatch", 409)
            return MarketShoppingOutcome(request.commandId, recorded.accepted, recorded.revision, true,
                recorded.errorKey, snapshot(user))
        }
        val currentRevision = revision(user)
        val current = MarketShoppingSnapshot(user.toString(), currentRevision,
            lines(user).map { MarketShoppingQuotedLine(it) }, now)
        var error = basket.basketIntentError(current, request.expectedRevision)
        if (error == null && !basket.isWithinBasketReviewTime(now)) error = "market.basket_apply_expired"
        var quotes = emptyList<MarketShoppingQuotedLine>()
        if (error == null) {
            val requested = basket.lines.map { reviewed -> MarketShoppingLine(reviewed.targetOfferId,
                reviewed.targetStoreId, reviewed.reviewedTitle, "", reviewed.units, reviewed.basis) }
            // Reuse exact-location publication, subscription and quantity-aware retail-price rules.
            // Unchanged lines are re-quoted as well; a reviewed GROUP total cannot silently change.
            quotes = quoteLines(user, requested, now)
            error = basket.basketQuotesError(quotes)
        }
        var applied: Long? = null
        if (error == null) {
            basket.lines.forEachIndexed { index, reviewed ->
                if (reviewed.sourceOfferId != reviewed.targetOfferId) {
                    val offer = requireNotNull(quotes[index].offer)
                    check(execute("""UPDATE buyer_shopping_lines SET offer_id=?,store_id=?,public_title=?,
                        public_shop_name=?,unit_name=?::jsonb,updated_at_millis=? WHERE user_id=? AND offer_id=?""",
                        marketUuid(offer.id), marketUuid(offer.storefront.storeId), offer.title,
                        offer.storefront.displayName, jsonBase.encodeToString(offer.unitName), now, user,
                        marketUuid(reviewed.sourceOfferId)) == 1)
                    // units, basis, created_at_millis and other currency groups are preserved.
                }
            }
            applied = currentRevision + 1L
            check(execute("UPDATE buyer_shopping_lists SET revision=?,updated_at_millis=? WHERE user_id=?",
                applied, now, user) == 1)
        }
        execute("""INSERT INTO buyer_shopping_commands
            (user_id,command_id,request_hash,session_id,offer_id,requested_units,expected_revision,
                accepted,error_key,applied_revision,created_at_millis,basket_change,activity_details)
            VALUES (?,?,?,?,NULL,0,?,?,?,?,?,?::jsonb,?::jsonb)""", user, id, hash, session, request.expectedRevision,
            error == null, error, applied, now, jsonBase.encodeToString(basket),
            if (error == null) jsonBase.encodeToString(request.shoppingActivityDetails(current.lines.map { it.line }, lines(user))) else null)
        // A rejection/no-op is still a new immutable command visible to subsequent lock waiters.
        execute("UPDATE buyer_shopping_lists SET last_command_id=? WHERE user_id=?", id, user)
        return MarketShoppingOutcome(request.commandId, error == null, applied, errorKey = error, snapshot = snapshot(user))
    }

    private fun applyCommand(user: UUID, session: UUID?, request: MarketShoppingCommand, replacement: Boolean): MarketShoppingOutcome {
        if (request.basketChange != null) marketFail("market.basket_apply_invalid")
        val id = marketUuid(request.commandId)
        val offerId = marketUuid(request.offerId)
        if (!request.isValidMarketShoppingCommand() || replacement != (request.replaceOfferId != null)) marketFail("market.shopping_invalid")
        val replacingId = request.replaceOfferId?.let(::marketUuid)
        val hash = commandHash(request)
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
                            val quote = quoteLines(user, listOf(MarketShoppingLine(target.id, target.storefront.storeId,
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
            (user_id,command_id,request_hash,session_id,offer_id,requested_units,expected_revision,accepted,error_key,applied_revision,created_at_millis,replaced_offer_id,reviewed_subtotal_minor,activity_details)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)""", user, id, hash, session, offerId, request.units, request.expectedRevision,
            error == null, error, applied, now, replacingId, request.reviewedSubtotalMinor,
            if (error == null) jsonBase.encodeToString(request.shoppingActivityDetails(currentLines, lines(user))) else null)
        execute("UPDATE buyer_shopping_lists SET last_command_id=? WHERE user_id=?", id, user)
        return MarketShoppingOutcome(request.commandId, error == null, applied, errorKey = error, snapshot = snapshot(user))
    }
}
