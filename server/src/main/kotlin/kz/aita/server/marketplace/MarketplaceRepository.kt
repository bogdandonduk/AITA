package kz.aita.server.marketplace

import kz.aita.*
import kz.aita.server.subscriptions.SubscriptionRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

internal class MarketFailure(val key: String, val status: Int) : RuntimeException(key)
internal fun marketFail(key: String = "market.invalid", status: Int = 400): Nothing = throw MarketFailure(key, status)
internal fun marketUuid(value: String): UUID = runCatching { UUID.fromString(value) }
    .getOrNull()?.takeIf { it.toString() == value.lowercase() } ?: marketFail()

/** Same transaction as authorization/publication. No commits, providers, or private DTO responses. */
internal class MarketplaceRepository(private val db: Connection,
    private val ownsStore: (UUID, UUID) -> Boolean) {
    init { check(!db.autoCommit) }

    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        db.prepareStatement(sql).use { statement ->
            args.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
        }
    private fun execute(sql: String, vararg args: Any?): Int = db.prepareStatement(sql).use { statement ->
        args.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
        statement.executeUpdate()
    }
    private fun requirePublisher(user: UUID, store: UUID) {
        val subscriptions = SubscriptionRepository(db)
        subscriptions.lockLocation(store) ?: marketFail("market.unavailable", 404)
        if (!ownsStore(user, store)) marketFail("market.owner", 403)
        if (!subscriptions.hasAccess(store, System.currentTimeMillis())) marketFail("subscription.required", 402)
    }
    private fun clean(value: String, max: Int, required: Boolean = false): String {
        val result = value.trim()
        if (result.length > max || (required && result.isEmpty()) || result.any { it.code < 32 && it != '\n' && it != '\t' }) marketFail()
        return result
    }
    private fun storefrontRow(row: ResultSet) = MarketStorefront(
        row.getString("store_id"), row.getString("display_name"), row.getString("city"),
        row.getString("public_address"), row.getString("pickup_note"), row.getBoolean("is_published"), row.getLong("revision"))
    private fun listingRow(row: ResultSet) = MarketListing(
        row.getString("id"), row.getString("store_id"), row.getString("goods_item_id"), row.getString("title"),
        row.getString("description"), row.getString("gtin"), row.getBoolean("is_published"), row.getLong("revision"))
    private fun storefront(store: UUID): MarketStorefront = query("SELECT * FROM marketplace_storefronts WHERE store_id=?", store,
        map = ::storefrontRow).singleOrNull() ?: MarketStorefront(store.toString())
    private fun dashboardUnchecked(store: UUID) = MarketPublicationDashboard(storefront(store),
        query("SELECT * FROM marketplace_listings WHERE store_id=? ORDER BY title,id LIMIT 1000", store, map = ::listingRow))
    fun dashboard(user: UUID, store: UUID): MarketPublicationDashboard {
        requirePublisher(user, store)
        return dashboardUnchecked(store)
    }
    private fun audit(user: UUID, session: UUID?, store: UUID, listing: UUID?, type: String, before: String?, after: String, now: Long) {
        execute("""INSERT INTO marketplace_publication_events
            (id,store_id,listing_id,actor_user_id,session_id,event_type,before_snapshot,after_snapshot,created_at_millis)
            VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?)""", UUID.randomUUID(), store, listing, user, session, type, before, after, now)
    }
    fun updateStorefront(user: UUID, session: UUID?, request: MarketStorefrontUpdate): MarketPublicationDashboard {
        val raw = request.storefront; val store = marketUuid(raw.storeId)
        requirePublisher(user, store)
        if (raw.revision < 0L) marketFail()
        val input = raw.copy(displayName = clean(raw.displayName, 120, raw.published), city = clean(raw.city, 100, raw.published),
            publicAddress = clean(raw.publicAddress, 400, raw.published), pickupNote = clean(raw.pickupNote, 1000))
        val current = storefront(store)
        if (current.revision > 0L && input.copy(revision = current.revision) == current) return dashboardUnchecked(store)
        if (input.revision != current.revision) marketFail("market.changed", 409)
        val now = System.currentTimeMillis()
        execute("""INSERT INTO marketplace_storefronts
            (store_id,display_name,city,public_address,pickup_note,is_published,revision,updated_by,updated_at_millis)
            VALUES (?,?,?,?,?,?,1,?,?) ON CONFLICT(store_id) DO UPDATE SET
            display_name=EXCLUDED.display_name,city=EXCLUDED.city,public_address=EXCLUDED.public_address,
            pickup_note=EXCLUDED.pickup_note,is_published=EXCLUDED.is_published,
            revision=marketplace_storefronts.revision+1,updated_by=EXCLUDED.updated_by,updated_at_millis=EXCLUDED.updated_at_millis""",
            store, input.displayName, input.city, input.publicAddress, input.pickupNote, input.published, user, now)
        audit(user, session, store, null, "storefront", current.takeIf { it.revision > 0L }?.let { jsonBase.encodeToString(it) },
            jsonBase.encodeToString(storefront(store)), now)
        return dashboardUnchecked(store)
    }
    fun updateListing(user: UUID, session: UUID?, request: MarketListingUpdate): MarketPublicationDashboard {
        val raw = request.listing; val store = marketUuid(raw.storeId); val itemId = marketUuid(raw.goodsItemId)
        requirePublisher(user, store)
        if (raw.revision < 0 || storefront(store).revision == 0L) marketFail()
        val previous = query("SELECT * FROM marketplace_listings WHERE store_id=? AND goods_item_id=?", store, itemId, map = ::listingRow).singleOrNull()
        val item = items(listOf(itemId)).singleOrNull()
        val root = query("SELECT coalesce(parent_store_id,id) FROM stores WHERE id=?", store) { it.getString(1) }.single()
        // A withdrawn/inactive item can always be UNPUBLISHED by its location owner. It cannot
        // be republished, nor can its current private data be borrowed from another store.
        val withdrawingExisting = previous != null && !raw.published
        if (!withdrawingExisting) {
            if (item == null) marketFail("market.unavailable", 404)
            if (item.storeId !in setOf(store.toString(), root)) marketFail("market.owner", 403)
        }
        val gtin = raw.gtin?.takeIf { it.isNotBlank() }?.let { marketCanonicalGtin(it) ?: marketFail() }
        if (!withdrawingExisting && gtin != null && gtin !in item?.standardBarcodeValues().orEmpty().mapNotNull(::marketCanonicalGtin)) marketFail()
        if (raw.id.isNotEmpty() && (previous == null || raw.id != previous.id)) marketFail("market.changed", 409)
        val input = raw.copy(id = previous?.id ?: UUID.randomUUID().toString(), title = clean(raw.title, 180, true),
            description = clean(raw.description, 2000), gtin = gtin)
        if (previous != null && input.copy(revision = previous.revision) == previous) return dashboardUnchecked(store)
        if (input.revision != (previous?.revision ?: 0L)) marketFail("market.changed", 409)
        if (previous == null && query("SELECT count(*) FROM marketplace_listings WHERE store_id=?", store) { it.getInt(1) }.single() >= 1000)
            marketFail("market.listing_limit", 409)
        val id = marketUuid(input.id); val now = System.currentTimeMillis()
        execute("""INSERT INTO marketplace_listings
            (id,store_id,goods_item_id,title,description,gtin,is_published,revision,created_at_millis,updated_at_millis,updated_by)
            VALUES (?,?,?,?,?,?,?,1,?,?,?) ON CONFLICT(store_id,goods_item_id) DO UPDATE SET
            title=EXCLUDED.title,description=EXCLUDED.description,gtin=EXCLUDED.gtin,is_published=EXCLUDED.is_published,
            revision=marketplace_listings.revision+1,updated_at_millis=EXCLUDED.updated_at_millis,updated_by=EXCLUDED.updated_by""",
            id, store, itemId, input.title, input.description, gtin, input.published, now, now, user)
        val result = dashboardUnchecked(store)
        audit(user, session, store, id, "listing", previous?.let { jsonBase.encodeToString(it) },
            jsonBase.encodeToString(result.listings.first { it.id == input.id }), now)
        return result
    }

    // Mirrored shape of grantsStoreAccess, including start, exact physical location and lifetime shape.
    // SQL filters BEFORE pagination. An inactive/private/expired location must never enter the page.
    private val publicJoins = """
        FROM marketplace_listings l JOIN marketplace_storefronts f ON f.store_id=l.store_id
        JOIN stores s ON s.id=l.store_id LEFT JOIN stores p ON p.id=s.parent_store_id
        JOIN stock_items i ON i.id=l.goods_item_id
        JOIN store_subscription_states e ON e.store_id=l.store_id
    """.trimIndent()
    private val publicPredicate = """
        l.is_published AND f.is_published AND s.is_active AND (s.parent_store_id IS NULL OR p.is_active) AND i.is_active
        AND i.store_id IN (s.id,coalesce(s.parent_store_id,s.id))
        AND e.status='active' AND coalesce(e.current_period_start_millis,e.started_at_millis)<=?
        AND ((e.access_kind='lifetime' AND e.plan_id='internal_lifetime' AND e.current_period_end_millis IS NULL AND NOT e.auto_renew)
          OR (e.access_kind IN ('paid','timed') AND e.current_period_end_millis>?))
    """.trimIndent()

    private data class Candidate(val listing: MarketListing, val sourceUpdated: Long)
    private fun candidates(sql: String, args: List<Any?>): List<Candidate> = query(sql, *args.toTypedArray()) {
        Candidate(listingRow(it), it.getLong("updated_at_millis"))
    }
    fun browse(user: UUID, text: String, city: String, after: String?, gtin: String?, storeId: String? = null): MarketPage {
        val search = clean(text, 120); val place = clean(city, 100); val now = System.currentTimeMillis()
        val conditions = mutableListOf(publicPredicate); val args = mutableListOf<Any?>(now, now)
        if (search.isNotEmpty()) {
            // Literal search, never the private stock Searchable contract (which includes costs/notes).
            conditions += "(strpos(lower(l.title),lower(?))>0 OR strpos(lower(l.description),lower(?))>0 OR l.gtin=?)"
            args.add(search); args.add(search); args.add(marketCanonicalGtin(search) ?: "")
        }
        if (place.isNotEmpty()) { conditions += "lower(f.city)=lower(?)"; args.add(place) }
        if (storeId != null) { conditions += "l.store_id=?"; args.add(marketUuid(storeId)) }
        if (after != null) { conditions += "l.id>?"; args.add(marketUuid(after)) }
        if (gtin != null) { conditions += "l.gtin=?"; args.add(marketCanonicalGtin(gtin) ?: marketFail()) }
        val rows = candidates("SELECT l.* $publicJoins WHERE ${conditions.joinToString(" AND ")} ORDER BY l.id LIMIT 41", args)
        val page = rows.take(40)
        return MarketPage(project(user, page, now), if (rows.size > 40) page.last().listing.id else null, now)
    }
    /** Dedicated same-product search, independent of the discovery grid's loaded window.
     * Candidate keyset is bounded before price projection; empty compatible pages can still continue.
     * Real stock/price changes are checked under this route's repeatable-read snapshot.
     */
    fun compare(user: UUID, request: MarketComparisonRequest, reference: MarketShoppingLine, now: Long): MarketComparisonPage {
        val selection = request.selection
        if (!selection.isValidMarketComparison()) marketFail("market.comparison_invalid")
        val place = clean(request.city, 100)
        val conditions = mutableListOf(publicPredicate, "l.gtin=?", "l.id<>?", "l.store_id<>?")
        val args = mutableListOf<Any?>(now, now, selection.basis.gtin, marketUuid(selection.offerId), marketUuid(reference.storeId))
        if (place.isNotEmpty()) { conditions += "lower(f.city)=lower(?)"; args.add(place) }
        request.after?.let { conditions += "l.id>?"; args.add(marketUuid(it)) }
        val candidates = candidates("SELECT l.* $publicJoins WHERE ${conditions.joinToString(" AND ")} ORDER BY l.id LIMIT ${MARKET_COMPARISON_PAGE_CANDIDATES + 1}", args)
        val scanned = candidates.take(MARKET_COMPARISON_PAGE_CANDIDATES)
        val compatible = project(user, scanned, now, scanned.associate { it.listing.id to selection.units }).filter { it.matchesComparison(selection) }.map { offer ->
            MarketShoppingLine(offer.id, offer.storefront.storeId, offer.title, offer.storefront.displayName,
                selection.units, selection.basis, offer.unitName, offer.sourceUpdatedAtMillis)
        }
        val quotes = quoteShopping(user, listOf(reference) + compatible, now)
        return MarketComparisonPage(selection, quotes.first(), quotes.drop(1),
            if (candidates.size > MARKET_COMPARISON_PAGE_CANDIDATES) scanned.last().listing.id else null, now, place)
    }

    /** Same public visibility predicate for details, list estimates and the browse page. */
    fun offersByIds(user: UUID, ids: List<UUID>, now: Long = System.currentTimeMillis(), units: Int = 1): List<MarketOffer> {
        if (ids.isEmpty()) return emptyList()
        require(ids.size <= 100 && units in 1..MARKET_SHOPPING_MAX_UNITS)
        val rows = candidates("SELECT l.* $publicJoins WHERE $publicPredicate AND l.id IN (${ids.joinToString(",") { "?" }}) ORDER BY l.id",
            listOf(now, now) + ids)
        return project(user, rows, now, ids.associate { it.toString() to units })
    }
    fun offer(user: UUID, id: String): MarketOffer = offersByIds(user, listOf(marketUuid(id))).singleOrNull()
        ?: marketFail("market.unavailable", 404)

    /** A public shop page can remain empty. It never falls back to the private Stores model. */
    fun publicShop(storeId: String): MarketStorefront {
        val store = marketUuid(storeId)
        val now = System.currentTimeMillis()
        return query("""SELECT f.* FROM marketplace_storefronts f JOIN stores s ON s.id=f.store_id
            LEFT JOIN stores p ON p.id=s.parent_store_id JOIN store_subscription_states e ON e.store_id=f.store_id
            WHERE f.store_id=? AND f.is_published AND s.is_active AND (s.parent_store_id IS NULL OR p.is_active)
            AND e.status='active' AND coalesce(e.current_period_start_millis,e.started_at_millis)<=?
            AND ((e.access_kind='lifetime' AND e.plan_id='internal_lifetime' AND e.current_period_end_millis IS NULL AND NOT e.auto_renew)
                OR (e.access_kind IN ('paid','timed') AND e.current_period_end_millis>?))""", store, now, now,
            map = ::storefrontRow).singleOrNull() ?: marketFail("market.shop_unavailable", 404)
    }

    /** Estimates use the requested selling-unit multiples and the real retail promotion rules.
     * Do not extrapolate a selected batch's price across other batches with different prices/units.
     * Neither the selected batch id nor its stock count leaves this repository.
     */
    fun quoteShopping(user: UUID, lines: List<MarketShoppingLine>, now: Long): List<MarketShoppingQuotedLine> {
        if (lines.isEmpty()) return emptyList()
        require(lines.size <= MARKET_SHOPPING_MAX_LINES)
        val ids = lines.map { marketUuid(it.offerId) }
        val rows = candidates("SELECT l.* $publicJoins WHERE $publicPredicate AND l.id IN (${ids.joinToString(",") { "?" }})",
            listOf(now, now) + ids)
        val offers = project(user, rows, now, lines.associate { it.offerId to it.units }).associateBy { it.id }
        val itemIds = rows.map { marketUuid(it.listing.goodsItemId) }.distinct()
        val storeIds = rows.map { marketUuid(it.listing.storeId) }.distinct()
        val itemById = items(itemIds).associateBy { it.id }
        val batchByItem = batches(itemIds, storeIds, now).associateBy { it.storeId to it.goodsItemId }
        val listingById = rows.associateBy { it.listing.id }
        return lines.map { line ->
            val offer = offers[line.offerId]
                ?: return@map MarketShoppingQuotedLine(line, status = MARKET_QUOTE_UNAVAILABLE)
            val currentBasis = offer.shoppingBasis()
            if (currentBasis == null) return@map MarketShoppingQuotedLine(line, offer, status = MARKET_QUOTE_PRICE)
            if (line.basis != currentBasis) return@map MarketShoppingQuotedLine(line, offer, status = MARKET_QUOTE_CHANGED)
            val listing = listingById.getValue(line.offerId).listing
            val item = itemById[listing.goodsItemId]
                ?: return@map MarketShoppingQuotedLine(line, offer, status = MARKET_QUOTE_UNAVAILABLE)
            val batch = batchByItem[listing.storeId to listing.goodsItemId]
                ?: return@map MarketShoppingQuotedLine(line, offer, status = MARKET_QUOTE_QUANTITY)
            // Decimal selling units such as 3 x 0.1 kg must not become 0.30000000000000004
            // and incorrectly fail against a recorded 0.3 kg batch.
            val total = marketRequestedQuantity(line.basis.pricedAmount, line.units)
            if (!total.isFinite() || !batch.isMarketSellableAt(line.storeId, now) || batch.quantity.total < total)
                return@map MarketShoppingQuotedLine(line, offer, status = MARKET_QUOTE_QUANTITY)
            if (item.firstViolatedPromotionRestriction(0, total, batch, now) != null)
                return@map MarketShoppingQuotedLine(line, offer, status = MARKET_QUOTE_PRICE)
            val price = item.promotedPriceForTransaction(0, quantityTotal = total, batch = batch, nowMillis = now).finalPrice
            if (price.currency.trim().uppercase() != line.basis.currencyCode)
                return@map MarketShoppingQuotedLine(line, offer, status = MARKET_QUOTE_CHANGED)
            val minor = marketPriceMinor(price.price)
            val subtotal = marketShoppingSubtotal(minor, line.units)
            MarketShoppingQuotedLine(line, offer, minor, subtotal,
                if (subtotal == null) MARKET_QUOTE_PRICE else MARKET_QUOTE_ESTIMATED)
        }
    }
    fun saved(user: UUID): MarketPage {
        val now = System.currentTimeMillis()
        val total = query("SELECT count(*) FROM buyer_saved_offers WHERE user_id=?", user) { it.getInt(1) }.single()
        val rows = candidates("SELECT l.* $publicJoins JOIN buyer_saved_offers b ON b.listing_id=l.id WHERE $publicPredicate AND b.user_id=? ORDER BY b.created_at_millis DESC,l.id LIMIT 100",
            listOf(now, now, user))
        return MarketPage(project(user, rows, now), checkedAtMillis = now, unavailableSavedCount = (total-rows.size).coerceAtLeast(0))
    }
    fun updateSaved(user: UUID, request: MarketSavedUpdate): MarketPage {
        val offer = marketUuid(request.offerId)
        // Serialize an account's limit/check/insert, without taking any store/financial lock.
        if (query("SELECT id FROM users WHERE id=? FOR UPDATE", user) { it.getString(1) }.isEmpty()) marketFail("market.owner",403)
        if (!request.saved) execute("DELETE FROM buyer_saved_offers WHERE user_id=? AND listing_id=?", user, offer)
        else {
            val exists = query("SELECT listing_id FROM buyer_saved_offers WHERE user_id=? AND listing_id=?", user, offer) { it.getString(1) }.isNotEmpty()
            if (!exists) {
                val now = System.currentTimeMillis()
                if (query("SELECT l.id $publicJoins WHERE $publicPredicate AND l.id=?", now, now, offer) { it.getString(1) }.isEmpty())
                    marketFail("market.unavailable",404)
                if (query("SELECT count(*) FROM buyer_saved_offers WHERE user_id=?", user) { it.getInt(1) }.single() >= 100) marketFail("market.saved_limit",409)
                execute("INSERT INTO buyer_saved_offers(user_id,listing_id,created_at_millis) VALUES (?,?,?) ON CONFLICT DO NOTHING",user,offer,now)
            }
        }
        return saved(user)
    }
    fun clearUnavailableSaved(user: UUID): MarketPage {
        query("SELECT id FROM users WHERE id=? FOR UPDATE",user) { it.getString(1) }
        val now=System.currentTimeMillis()
        execute("DELETE FROM buyer_saved_offers WHERE user_id=? AND listing_id NOT IN (SELECT l.id $publicJoins WHERE $publicPredicate)",user,now,now)
        return saved(user)
    }

    private fun items(ids: List<UUID>): List<GoodsItemDataModel> {
        if(ids.isEmpty()) return emptyList()
        // Select only fields needed by retail price calculation; no costs, notes, owner, or supplier data.
        return query("""SELECT id,store_id,barcodes,barcode_models,sale_prices,promotions,active_shelf_batch_id,category_ids,updated_at_millis
            FROM stock_items WHERE is_active AND id IN (${ids.joinToString(",") { "?" }})""", *ids.toTypedArray()) { row ->
            GoodsItemDataModel(id=row.getString("id"),storeId=row.getString("store_id"),
                barcodes=jsonBase.decodeFromString(row.getString("barcodes")), barcodeModels=jsonBase.decodeFromString(row.getString("barcode_models")),
                salePrices=jsonBase.decodeFromString(row.getString("sale_prices")), promotions=jsonBase.decodeFromString(row.getString("promotions")),
                activeShelfBatchId=row.getString("active_shelf_batch_id"), categoryIds=jsonBase.decodeFromString(row.getString("category_ids")),
                updatedAtMillis=row.getLong("updated_at_millis"))
        }
    }
    private fun batches(itemIds: List<UUID>, storeIds: List<UUID>, now: Long): List<GoodsBatchDataModel> {
        if (itemIds.isEmpty() || storeIds.isEmpty()) return emptyList()
        return query("""SELECT DISTINCT ON (b.store_id,b.goods_item_id)
            b.id,b.goods_item_id,b.store_id,b.quantity,b.sale_price_override,b.expiration_date_millis,
            b.discounts,b.promotions,b.shelf_priority,b.status,b.updated_at_millis
            FROM stock_batches b JOIN stock_items i ON i.id=b.goods_item_id
            WHERE b.is_active AND b.status IN ('Delivered','OnShelf')
            AND (b.expiration_date_millis IS NULL OR b.expiration_date_millis>?)
            AND (CASE WHEN NOT jsonb_exists(b.quantity::jsonb,'total') THEN 1
                      WHEN jsonb_typeof(b.quantity::jsonb->'total')='number' THEN (b.quantity::jsonb->>'total')::numeric ELSE 0 END)>0
            AND (CASE WHEN NOT jsonb_exists(b.quantity::jsonb,'pricedAmount') THEN 1
                      WHEN jsonb_typeof(b.quantity::jsonb->'pricedAmount')='number' THEN (b.quantity::jsonb->>'pricedAmount')::numeric ELSE 0 END)>0
            AND b.goods_item_id IN (${itemIds.joinToString(",") { "?" }})
            AND b.store_id IN (${storeIds.joinToString(",") { "?" }})
            ORDER BY b.store_id,b.goods_item_id,(b.id=i.active_shelf_batch_id) DESC NULLS LAST,
                b.expiration_date_millis ASC NULLS LAST,b.shelf_priority DESC,b.id""", now, *(itemIds+storeIds).toTypedArray()) { row ->
            GoodsBatchDataModel(id=row.getString("id"),goodsItemId=row.getString("goods_item_id"),storeId=row.getString("store_id"),
                quantity=jsonBase.decodeFromString(row.getString("quantity")), supplyPrice=PriceDataModel("0","",""),
                salePriceOverride=row.getString("sale_price_override")?.let { jsonBase.decodeFromString<PriceDataModel>(it) },
                expirationDateMillis=row.getLong("expiration_date_millis").let { if(row.wasNull()) null else it },
                discounts=jsonBase.decodeFromString(row.getString("discounts")), promotions=jsonBase.decodeFromString(row.getString("promotions")),
                shelfPriority=row.getInt("shelf_priority"), status=StockBatchStatusDataModel.valueOf(row.getString("status")),
                updatedAtMillis=row.getLong("updated_at_millis"))
        }
    }
    private fun project(user: UUID, candidates: List<Candidate>, now: Long, requestedUnits: Map<String, Int> = emptyMap()): List<MarketOffer> {
        if(candidates.isEmpty()) return emptyList()
        val itemIds=candidates.map { marketUuid(it.listing.goodsItemId) }.distinct()
        val storeIds=candidates.map { marketUuid(it.listing.storeId) }.distinct()
        val items=items(itemIds).associateBy { it.id }
        val batches=batches(itemIds,storeIds,now).groupBy { it.storeId to it.goodsItemId }
        val stores=query("SELECT * FROM marketplace_storefronts WHERE store_id IN (${storeIds.joinToString(",") { "?" }})",*storeIds.toTypedArray(),map=::storefrontRow).associateBy { it.storeId }
        val saved=query("SELECT listing_id FROM buyer_saved_offers WHERE user_id=?",user) { it.getString(1) }.toSet()
        return candidates.mapNotNull { candidate ->
            val listing=candidate.listing; val item=items[listing.goodsItemId] ?: return@mapNotNull null
            val shop=stores[listing.storeId] ?: return@mapNotNull null
            val eligible=batches[listing.storeId to listing.goodsItemId].orEmpty()
                .filter { it.isMarketSellableAt(listing.storeId,now) }
                .sortedWith(compareByDescending<GoodsBatchDataModel> { it.id==item.activeShelfBatchId }
                    .thenBy { it.expirationDateMillis ?: Long.MAX_VALUE }.thenByDescending { it.shelfPriority }.thenBy { it.id })
            val batch=eligible.firstOrNull()
            val priced=batch?.quantity?.pricedAmount
            // In a list/comparison, priceMinor is the selling-unit price at that requested count.
            // Evaluate restrictions at the same count; a valid minimum-quantity offer must not be
            // excluded merely because one unit on a discovery card would violate its minimum.
            val total = priced?.let { marketRequestedQuantity(it, requestedUnits[listing.id] ?: 1) } ?: 1.0
            val base=if(batch==null) null else item.basePriceForTransaction(0,batch=batch,quantityTotal=total)
            val price=if(batch==null || base==null || base.price.toMoneyDouble()<=0.0 ||
                item.firstViolatedPromotionRestriction(0,total,batch,now)!=null) null
                else item.promotedPriceForTransaction(0,quantityTotal=total,batch=batch,nowMillis=now).finalPrice
            val currency=price?.currency?.trim()?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{3}")) }
            val minor=if(currency==null) null else price?.price?.let(::marketPriceMinor)
            // A later stock barcode change must not silently keep a stale comparison identity alive.
            val publicGtin = listing.gtin?.takeIf { it in item.standardBarcodeValues().mapNotNull(::marketCanonicalGtin) }
            MarketOffer(listing.id,shop,listing.title,listing.description,publicGtin,
                categoryIds=item.categoryIds.take(16),priceMinor=minor,currencyCode=currency.takeIf { minor!=null },
                pricedAmount=priced.takeIf { minor!=null },unitId=batch?.quantity?.id?.takeIf { minor!=null },
                unitName=if(minor==null) emptyList() else batch?.quantity?.immutableUnitName.orEmpty().take(12)
                    .map { LocalizedStringDataModel(it.language.take(12), it.value.take(120)) },
                availability=if(batch!=null && minor!=null && batch.quantity.total >= total) MARKET_AVAILABILITY_RECORDED else MARKET_AVAILABILITY_CONFIRM,
                checkedAtMillis=now,sourceUpdatedAtMillis=maxOf(candidate.sourceUpdated,item.updatedAtMillis,batch?.updatedAtMillis ?: 0L),
                saved=listing.id in saved)
        }
    }
}

/** Parse decimal text once; running the POS Double floor helper twice can lose another cent. */
internal fun marketPriceMinor(raw: String): Long? = runCatching {
    val value=BigDecimal(raw.trim().replace(',', '.'))
    if(value < BigDecimal.ZERO || value > BigDecimal("10000000000")) null
    else value.movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact()
}.getOrNull()

/** Exact decimal multiplication before adapting to the existing POS Double quantity API. */
internal fun marketRequestedQuantity(pricedAmount: Double, units: Int): Double {
    require(pricedAmount.isFinite() && pricedAmount > 0.0 && units in 1..MARKET_SHOPPING_MAX_UNITS)
    return BigDecimal.valueOf(pricedAmount).multiply(BigDecimal.valueOf(units.toLong())).toDouble()
}
