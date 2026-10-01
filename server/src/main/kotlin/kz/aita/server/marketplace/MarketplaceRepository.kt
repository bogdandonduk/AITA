package kz.aita.server.marketplace

import kz.aita.*
import kz.aita.server.subscriptions.SubscriptionRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

internal class MarketFailure(val key: String, val status: Int) : RuntimeException(key)
internal fun marketFail(key: String = "market.invalid", status: Int = 400): Nothing = throw MarketFailure(key, status)
internal fun marketUuid(value: String): UUID = runCatching { UUID.fromString(value) }
    .getOrNull()?.takeIf { it.toString() == value.lowercase() } ?: marketFail()

/** Same transaction as authorization/publication. No commits, providers, or private DTO responses. */
internal class MarketplaceRepository(private val db: Connection,
    private val ownsStore: (UUID, UUID) -> Boolean) {
    private var readsStock: (UUID, UUID) -> Boolean = ownsStore
    constructor(db: Connection, ownsStore: (UUID, UUID) -> Boolean, readsStock: (UUID, UUID) -> Boolean) : this(db, ownsStore) {
        this.readsStock = readsStock
    }
    init { check(!db.autoCommit) }
    private var basketReadStartedNanos: Long? = null

    /** Per-request wall budget, not just a per-statement timeout multiplied by every batch. */
    fun <T> withBasketReadBudget(work: () -> T): T {
        check(basketReadStartedNanos == null)
        basketReadStartedNanos = System.nanoTime()
        try { return work() }
        catch (failure: SQLException) {
            if (failure.sqlState == "57014") marketFail("market.basket_busy", 503)
            throw failure
        } finally { basketReadStartedNanos = null }
    }
    /** Reuse the bounded public-read machinery, but report the comparison's own recovery text. */
    fun <T> withComparisonReadBudget(work: () -> T): T = try { withBasketReadBudget(work) }
    catch (failure: MarketFailure) {
        if (failure.key == "market.basket_busy") marketFail("market.comparison_window_busy", 503)
        throw failure
    }

    fun <T> withDiscoveryReadBudget(work: () -> T): T = try { withBasketReadBudget(work) }
    catch (failure: MarketFailure) {
        if (failure.key == "market.basket_busy") marketFail("market.discovery_busy", 503)
        throw failure
    }

    fun <T> withOfferDetailReadBudget(work: () -> T): T = try { withBasketReadBudget(work) }
    catch (failure: MarketFailure) {
        if (failure.key == "market.basket_busy") marketFail("market.detail_busy", 503)
        throw failure
    }

    private fun basketQueryTimeoutSeconds(): Int? {
        val started = basketReadStartedNanos ?: return null
        val remaining = 8_000_000_000L - (System.nanoTime() - started)
        if (remaining <= 0L) marketFail("market.basket_busy", 503)
        return ((remaining + 999_999_999L) / 1_000_000_000L).toInt().coerceIn(1, 5)
    }

    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        db.prepareStatement(sql).use { statement ->
            basketQueryTimeoutSeconds()?.let { statement.queryTimeout = it }
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
        val internet = query("SELECT parent_store_id IS NOT NULL AND branch_type='INTERNET' FROM stores WHERE id=? AND is_active", store) { it.getBoolean(1) }.singleOrNull() == true
        if (!internet) marketFail("market.profile_internet_only", 403)
        if (!subscriptions.hasAccess(store, System.currentTimeMillis())) marketFail("subscription.required", 402)
    }
    private fun clean(value: String, max: Int, required: Boolean = false): String {
        val result = value.trim()
        if (result.length > max || (required && result.isEmpty()) || result.any { it.code < 32 && it != '\n' && it != '\t' }) marketFail()
        return result
    }
    private fun storefrontRow(row: ResultSet) = MarketStorefront(
        row.getString("store_id"), row.getString("display_name"), row.getString("city"),
        row.getString("public_address"), row.getString("pickup_note"), row.getBoolean("is_published"), row.getLong("revision"), row.getBoolean("share_branch_availability"), row.getString("branch_store_id"))
    private fun listingRow(row: ResultSet) = MarketListing(
        row.getString("id"), row.getString("store_id"), row.getString("goods_item_id"), row.getString("title"),
        row.getString("description"), row.getString("gtin"), row.getBoolean("is_published"), row.getLong("revision"),
        jsonBase.decodeFromString<MarketProductDetails>(row.getString("product")).normalizedMarketProduct())
    private fun storefront(store: UUID): MarketStorefront = query("SELECT * FROM marketplace_storefronts WHERE store_id=?", store,
        map = ::storefrontRow).singleOrNull() ?: MarketStorefront(store.toString(), branchStoreId=store.toString())
    private fun storefrontForBranch(branch: UUID): MarketStorefront = query("SELECT * FROM marketplace_storefronts WHERE branch_store_id=?", branch,
        map = ::storefrontRow).singleOrNull() ?: MarketStorefront(branch.toString(), branchStoreId=branch.toString())
    private fun selectedLocations(shop: UUID): List<String> = query("SELECT location_store_ids FROM marketplace_storefronts WHERE store_id=?",shop) {
        jsonBase.decodeFromString<List<String>>(it.getString(1))
    }.singleOrNull().orEmpty()
    private fun locationChoices(branch: UUID): List<MarketPublicationLocation> = query("""SELECT l.id,l.name,l.address,l.parent_store_id IS NULL AS warehouse
        FROM stores owner JOIN stores l ON l.id=owner.parent_store_id OR
            (l.parent_store_id=owner.parent_store_id AND l.branch_type='PHYSICAL')
        WHERE owner.id=? AND l.is_active AND length(trim(coalesce(l.address,'')))>0
        ORDER BY (l.parent_store_id IS NULL) DESC,l.id LIMIT 200""",branch) { row ->
        MarketPublicationLocation(row.getString("id"),publicLocationName(row.getString("name")),
            cleanLocationAddress(row.getString("address")),row.getBoolean("warehouse"))
    }.filter { it.name.isNotEmpty() && it.address.isNotBlank() }
    private fun publicLocationName(raw: String): List<LocalizedStringDataModel> =
        jsonBase.decodeFromString<List<LocalizedStringDataModel>>(raw)
            .map { LocalizedStringDataModel(it.language.trim().take(12),it.value.trim().filter { char -> char.code >= 32 || char == '\n' || char == '\t' }.take(180)) }
            .filter { it.language.isNotBlank() && it.value.isNotBlank() }.distinctBy { it.language }.take(12)
    private fun cleanLocationAddress(raw: String?): String = raw.orEmpty().trim().filter { it.code >= 32 }.take(400)
    private fun dashboardUnchecked(branch: UUID): MarketPublicationDashboard {
        val shop=storefrontForBranch(branch)
        return MarketPublicationDashboard(shop,
            query("SELECT * FROM marketplace_listings WHERE store_id=? ORDER BY title,id LIMIT 1000",marketUuid(shop.storeId),map=::listingRow),
            selectedLocations(marketUuid(shop.storeId)),locationChoices(branch))
    }
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
        val raw = request.storefront; val store = marketUuid(raw.storeId); val branch=marketUuid(raw.operatingBranchId)
        requirePublisher(user, branch)
        val current=storefrontForBranch(branch)
        if (raw.storeId!=current.storeId) marketFail("market.owner",403)
        val previousLocations=selectedLocations(store)
        val locations=request.locationStoreIds ?: previousLocations
        if (locations.size>MARKET_STOREFRONT_MAX_LOCATIONS || locations.distinct().size!=locations.size ||
            locations.any { marketDiscoveryId(it)!=it }) marketFail("market.locations_invalid")
        // Revalidate only new choices. A disconnected/deactivated saved location stays removable;
        // public reads independently hide it until it becomes eligible again.
        val allowed=locationChoices(branch).map { it.storeId }.toSet()
        if (locations.any { it !in allowed && it !in previousLocations }) marketFail("market.locations_invalid")
        if (raw.revision < 0L) marketFail()
        val input = raw.copy(displayName = clean(raw.displayName, 120, raw.published), city = clean(raw.city, 100, raw.published),
            publicAddress = clean(raw.publicAddress, 400, raw.published), pickupNote = clean(raw.pickupNote, 1000),
            branchStoreId=branch.toString(),shareBranchAvailability=raw.shareBranchAvailability && locations.isNotEmpty())
        if (current.revision > 0L && input.copy(revision = current.revision) == current && locations==previousLocations) return dashboardUnchecked(branch)
        if (input.revision != current.revision) marketFail("market.changed", 409)
        val now = System.currentTimeMillis()
        execute("""INSERT INTO marketplace_storefronts
            (store_id,display_name,city,public_address,pickup_note,is_published,revision,updated_by,updated_at_millis,share_branch_availability,branch_store_id,location_store_ids)
            VALUES (?,?,?,?,?,?,1,?,?,?,?,?::jsonb) ON CONFLICT(store_id) DO UPDATE SET
            display_name=EXCLUDED.display_name,city=EXCLUDED.city,public_address=EXCLUDED.public_address,
            pickup_note=EXCLUDED.pickup_note,is_published=EXCLUDED.is_published,
            share_branch_availability=EXCLUDED.share_branch_availability,location_store_ids=EXCLUDED.location_store_ids,
            revision=marketplace_storefronts.revision+1,updated_by=EXCLUDED.updated_by,updated_at_millis=EXCLUDED.updated_at_millis""",
            store, input.displayName, input.city, input.publicAddress, input.pickupNote, input.published, user, now, input.shareBranchAvailability,
            branch,jsonBase.encodeToString(locations))
        audit(user, session, store, null, "storefront", current.takeIf { it.revision > 0L }?.let { jsonBase.encodeToString(MarketStorefrontUpdate(it,previousLocations)) },
            jsonBase.encodeToString(MarketStorefrontUpdate(storefront(store),locations)), now)
        return dashboardUnchecked(branch)
    }
    fun updateListing(user: UUID, session: UUID?, request: MarketListingUpdate): MarketPublicationDashboard {
        val raw = request.listing; val store = marketUuid(raw.storeId); val itemId = marketUuid(raw.goodsItemId)
        val branch=marketUuid(request.branchStoreId ?: raw.storeId)
        requirePublisher(user, branch)
        val shop=storefrontForBranch(branch)
        if (shop.storeId!=raw.storeId) marketFail("market.owner",403)
        if (raw.revision < 0 || shop.revision == 0L) marketFail()
        val previous = query("SELECT * FROM marketplace_listings WHERE store_id=? AND goods_item_id=?", store, itemId, map = ::listingRow).singleOrNull()
        val item = items(listOf(itemId)).singleOrNull()
        val root = query("SELECT coalesce(parent_store_id,id) FROM stores WHERE id=?", branch) { it.getString(1) }.single()
        // A withdrawn/inactive item can always be UNPUBLISHED by its location owner. It cannot
        // be republished, nor can its current private data be borrowed from another store.
        val withdrawingExisting = previous != null && !raw.published
        if (!withdrawingExisting) {
            if (item == null) marketFail("market.unavailable", 404)
            if (item.storeId !in setOf(branch.toString(), root) && query("""SELECT 1
                FROM store_management_parent_migrations m JOIN stores legacy ON legacy.id=m.original_store_id
                WHERE m.original_store_id=? AND m.management_store_id=? AND legacy.parent_store_id=m.management_store_id""",
                marketUuid(item.storeId),marketUuid(root)) { it.getInt(1) }.isEmpty()) marketFail("market.owner", 403)
        }
        val gtin = raw.gtin?.takeIf { it.isNotBlank() }?.let { marketCanonicalGtin(it) ?: marketFail() }
        if (!withdrawingExisting && gtin != null && gtin !in item?.standardBarcodeValues().orEmpty().mapNotNull(::marketCanonicalGtin)) marketFail()
        if (raw.id.isNotEmpty() && (previous == null || raw.id != previous.id)) marketFail("market.changed", 409)
        val input = raw.copy(id = previous?.id ?: UUID.randomUUID().toString(), title = clean(raw.title, 180, true),
            description = clean(raw.description, 2000), gtin = gtin,
            product = (if (!request.replaceProduct && previous != null) previous.product else raw.product)
                .also { if (!it.isValidMarketProduct()) marketFail("market.profile_invalid") })
        if (previous != null && input.copy(revision = previous.revision) == previous) return dashboardUnchecked(branch)
        if (input.revision != (previous?.revision ?: 0L)) marketFail("market.changed", 409)
        if (previous == null && query("SELECT count(*) FROM marketplace_listings WHERE store_id=?", store) { it.getInt(1) }.single() >= 1000)
            marketFail("market.listing_limit", 409)
        val id = marketUuid(input.id); val now = System.currentTimeMillis()
        execute("""INSERT INTO marketplace_listings
            (id,store_id,goods_item_id,title,description,gtin,is_published,revision,created_at_millis,updated_at_millis,updated_by,product)
            VALUES (?,?,?,?,?,?,?,1,?,?,?,?::jsonb) ON CONFLICT(store_id,goods_item_id) DO UPDATE SET
            title=EXCLUDED.title,description=EXCLUDED.description,gtin=EXCLUDED.gtin,is_published=EXCLUDED.is_published,
            product=EXCLUDED.product,
            revision=marketplace_listings.revision+1,updated_at_millis=EXCLUDED.updated_at_millis,updated_by=EXCLUDED.updated_by""",
            id, store, itemId, input.title, input.description, gtin, input.published, now, now, user, jsonBase.encodeToString(input.product))
        val result = dashboardUnchecked(branch)
        audit(user, session, store, id, "listing", previous?.let { jsonBase.encodeToString(it) },
            jsonBase.encodeToString(result.listings.first { it.id == input.id }), now)
        return result
    }

    // Mirrored shape of grantsStoreAccess, including start, exact physical location and lifetime shape.
    // SQL filters BEFORE pagination. An inactive/private/expired location must never enter the page.
    private val publicJoins = """
        FROM marketplace_listings l JOIN marketplace_storefronts f ON f.store_id=l.store_id
        JOIN stores s ON s.id=f.branch_store_id LEFT JOIN stores p ON p.id=s.parent_store_id
        JOIN stock_items i ON i.id=l.goods_item_id
        JOIN store_subscription_states e ON e.store_id=s.id
    """.trimIndent()
    private val publicPredicate = """
        l.is_published AND i.is_active AND ${MarketplacePublicVisibility.cataloguePredicate}
        AND ${MarketplacePublicVisibility.shopPredicate}
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
    /** Complete filtered window and counts from the SAME repeatable-read transaction. The legacy
     * browse route remains compatible; new clients never fall back to unfiltered legacy results. */
    fun discover(user: UUID, request: MarketDiscoveryRequest): MarketDiscoveryResult {
        if (!request.isValidDiscoveryRequest()) marketFail("market.discovery_invalid")
        val input = request.query.normalizedDiscoveryQuery() ?: marketFail("market.discovery_invalid")
        val now = System.currentTimeMillis()
        val shop = input.storefrontId?.let { publicShop(it, now) }
        val catalogue = discoveryCategories()
        val tree = MarketCategoryTree(catalogue.categories)
        val descendants = input.categoryId?.let { category ->
            tree.subtreeIds(category).takeIf { it.isNotEmpty() } ?: marketFail("market.category_changed", 409)
        }
        val conditions = mutableListOf(publicPredicate)
        val args = mutableListOf<Any?>(now, now)
        // Terms are ANDed across public title/description. SQL wildcard characters are literal;
        // no private item name, note, purchase cost or supplier field participates in discovery.
        if (input.text.isNotEmpty()) {
            val barcode = marketCanonicalGtin(input.text)
            if (barcode != null) { conditions += "l.gtin=?"; args += barcode }
            else input.text.split(' ').distinct().forEach { term ->
                conditions += "(strpos(lower(l.title),lower(?))>0 OR strpos(lower(l.description),lower(?))>0)"
                args += term; args += term
            }
        }
        if (input.city.isNotEmpty()) { conditions += "lower(f.city)=lower(?)"; args += input.city }
        input.storefrontId?.let { conditions += "l.store_id=?"; args += marketUuid(it) }
        if (descendants != null) {
            // pgJDBC escapes a literal question mark as ??. The server receives the indexable
            // JSONB ?| operator, not a second bind parameter or a string-concatenated SQL fragment.
            conditions += "(i.category_ids::jsonb ??| ?::text[])"
            args += descendants.joinToString(",", "{", "}") // UUID-only IDs, passed as ONE bound value.
        }
        val joins = publicJoins + if (input.savedOnly) " JOIN buyer_saved_offers b ON b.listing_id=l.id" else ""
        if (input.savedOnly) { conditions += "b.user_id=?"; args += user }
        val where = conditions.joinToString(" AND ")
        val counts = query("SELECT count(*),count(DISTINCT l.store_id) $joins WHERE $where", *args.toTypedArray()) {
            it.getLong(1) to it.getLong(2)
        }.single()
        val ordering = when (input.sort) {
            MARKET_DISCOVERY_TITLE -> "lower(l.title),l.id"
            else -> if (input.savedOnly) "b.created_at_millis DESC,l.id" else "l.created_at_millis DESC,l.id"
        }
        val rows = candidates("SELECT l.* $joins WHERE $where ORDER BY $ordering LIMIT ?", args + request.limit)
        val publicCategoryIds = tree.byId.keys
        val offers = project(user, rows, now, publicCategoryIds = publicCategoryIds, selectedCategoryIds = descendants)
        // Unavailable is global to this user's saved set, NOT the number excluded by their filters.
        val unavailable = if (!input.savedOnly) 0 else query("""SELECT count(*) FROM buyer_saved_offers b
            WHERE b.user_id=? AND NOT EXISTS(SELECT 1 $publicJoins WHERE $publicPredicate AND l.id=b.listing_id)""",
            user, now, now) { it.getLong(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }.single()
        return MarketDiscoveryResult(input, request.limit,
            MarketPage(offers, rows.lastOrNull()?.listing?.id?.takeIf { counts.first > rows.size }, now, unavailable),
            counts.first, counts.second, catalogue.version, catalogue.categories.takeUnless { request.knownCategoryVersion == catalogue.version },
            accountId = user.toString(), storefront = shop)
    }

    private fun discoveryCategories(): MarketCategoryCatalogue {
        val rows = query("SELECT id,name,type_ids FROM generic_goods_categories ORDER BY id LIMIT ${MARKET_CATEGORY_MAX_COUNT + 1}") { row ->
            val names = jsonBase.decodeFromString<List<LocalizedStringDataModel>>(row.getString("name"))
                .filter { it.language.isNotBlank() && it.value.isNotBlank() }.take(12)
                .map { LocalizedStringDataModel(it.language.take(12), it.value.take(480)) }
            val ancestors = jsonBase.decodeFromString<List<String>>(row.getString("type_ids"))
                .mapNotNull(::marketDiscoveryId).distinct().takeLast(64)
            MarketCategory(row.getString("id"), names, ancestors)
        }
        if (rows.size > MARKET_CATEGORY_MAX_COUNT) marketFail("market.categories_unavailable", 503)
        val categories = rows.filter { it.name.isNotEmpty() }
        val version = java.security.MessageDigest.getInstance("SHA-256")
            .digest(jsonBase.encodeToString(categories).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return MarketCategoryCatalogue(version, categories)
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

    /** One UUID-bounded candidate scan, then bounded quote batches, all on the caller's SAME
     * read-only repeatable-read transaction and check instant. No independent cursor snapshots.
     * Incompatible candidates still count toward the scan cap, so an empty window may expand.
     */
    fun compareWindow(user: UUID, request: MarketComparisonWindowRequest, reference: MarketShoppingLine,
        now: Long): MarketComparisonWindowResult {
        val input = request.normalizedComparisonWindowRequest() ?: marketFail("market.comparison_window_invalid")
        val selection = input.selection
        val conditions = mutableListOf(publicPredicate, "l.gtin=?", "l.id<>?", "l.store_id<>?")
        val args = mutableListOf<Any?>(now, now, selection.basis.gtin, marketUuid(selection.offerId), marketUuid(reference.storeId))
        if (input.city.isNotEmpty()) { conditions += "lower(f.city)=lower(?)"; args.add(input.city) }
        args.add(input.candidateLimit + 1)
        val candidates = candidates("SELECT l.* $publicJoins WHERE ${conditions.joinToString(" AND ")} ORDER BY l.id LIMIT ?", args)
        val scanned = candidates.take(input.candidateLimit)
        val original = quoteShopping(user, listOf(reference), now).single()
        // quoteShopping projects price/stock once per bounded batch. Only compatible public
        // quotes become rows; candidate labels come from that projection, never private stock.
        val matches = scanned.map { candidate ->
            MarketShoppingLine(candidate.listing.id, candidate.listing.storeId, candidate.listing.title,
                "", selection.units, selection.basis)
        }.chunked(MARKET_SHOPPING_MAX_LINES).flatMap { quoteShopping(user, it, now) }.mapNotNull { quote ->
            val offer = quote.offer?.takeIf { it.matchesComparison(selection) } ?: return@mapNotNull null
            quote.copy(line = quote.line.copy(title = offer.title, shopName = offer.storefront.displayName,
                unitName = offer.unitName, updatedAtMillis = offer.sourceUpdatedAtMillis))
        }.rankedComparison()
        return MarketComparisonWindowResult(user.toString(), input, original, matches, scanned.size,
            candidates.size > input.candidateLimit, now)
    }

    internal data class BasketCandidates(val choices: List<MarketBasketChoice>, val limitedSourceIds: List<String>, val checked: Int)

    /** One bounded index-driven lateral scan per demand, then batch price reads. The original
     * account list supplies all identities. A candidate can never borrow another user's intent.
     * UUID order is intentionally not called price order; the cap is disclosed in the result.
     */
    fun basketCandidates(user: UUID, source: MarketShoppingSnapshot, city: String): BasketCandidates {
        val fixed = source.basketFixedLines().map { it.offerId }.toSet()
        val eligible = source.lines.map { it.line }.filter { it.offerId !in fixed }
        if (eligible.isEmpty()) return BasketCandidates(emptyList(), emptyList(), 0)
        val allIds = source.lines.map { marketUuid(it.line.offerId) }
        val args = mutableListOf<Any?>()
        eligible.forEach { args.add(marketUuid(it.offerId)); args.add(it.basis.gtin); args.add(marketUuid(it.storeId)) }
        args.add(source.checkedAtMillis); args.add(source.checkedAtMillis)
        args.addAll(allIds)
        if (city.isNotEmpty()) args.add(city)
        val sourceById = eligible.associateBy { it.offerId }
        val rows = query("""SELECT w.source_offer_id,c.* FROM
            (VALUES ${eligible.joinToString(",") { "(?::uuid,?::text,?::uuid)" }}) w(source_offer_id,gtin,source_store_id)
            CROSS JOIN LATERAL (SELECT l.*,f.display_name AS shop_name $publicJoins
                WHERE $publicPredicate AND l.gtin=w.gtin AND l.store_id<>w.source_store_id
                AND l.id NOT IN (${allIds.joinToString(",") { "?" }})
                ${if (city.isNotEmpty()) "AND lower(f.city)=lower(?)" else ""}
                ORDER BY l.id LIMIT ${MARKET_BASKET_CANDIDATES_PER_LINE + 1}) c
            ORDER BY w.source_offer_id,c.id""", *args.toTypedArray()) { row ->
            val sourceId = row.getString("source_offer_id")
            val line = sourceById.getValue(sourceId)
            sourceId to line.copy(offerId = row.getString("id"), storeId = row.getString("store_id"),
                title = row.getString("title"), shopName = row.getString("shop_name"), updatedAtMillis = row.getLong("updated_at_millis"))
        }
        val limited = rows.groupBy { it.first }.filterValues { it.size > MARKET_BASKET_CANDIDATES_PER_LINE }.keys.sorted()
        val scanned = rows.groupBy { it.first }.values.flatMap { it.take(MARKET_BASKET_CANDIDATES_PER_LINE) }
        // The same barcode can occur with different units/amounts. Group requested multiples so
        // quoteShopping's offerId->quantity map cannot give one source another source's quantity.
        val quotes = scanned.groupBy { it.second.units }.values.flatMap { sameUnits ->
            sameUnits.chunked(MARKET_SHOPPING_MAX_LINES).flatMap { batch ->
                val priced = quoteShopping(user, batch.map { it.second }, source.checkedAtMillis)
                batch.zip(priced).map { (entry, quote) -> MarketBasketChoice(entry.first, quote) }
            }
        }
        return BasketCandidates(quotes, limited, scanned.size)
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

    /** The same projection/visibility transaction as legacy details, now bound to its buyer.
     * The envelope carries only public fields and that buyer's own saved marker.
     */
    fun offerDetail(user: UUID, id: String): MarketOfferDetailResult {
        val current = offer(user, id)
        val availability = branchAvailability(current)
        return MarketOfferDetailResult(user.toString(), current, availability.first, availability.second)
    }

    /** A public shop page can remain empty. It never falls back to the private Stores model. */
    fun publicShop(storeId: String, now: Long = System.currentTimeMillis()): MarketStorefront {
        val store = marketUuid(storeId)
        return query("SELECT f.* ${MarketplacePublicVisibility.shopJoins} WHERE f.store_id=? AND ${MarketplacePublicVisibility.shopPredicate}",
            store, now, now, map = ::storefrontRow).singleOrNull() ?: marketFail("market.shop_unavailable", 404)
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
        val storeIds = offers.values.map { marketUuid(it.storefront.operatingBranchId) }.distinct()
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
            val batch = batchByItem[offer.storefront.operatingBranchId to listing.goodsItemId]
                ?: return@map MarketShoppingQuotedLine(line, offer, status = MARKET_QUOTE_QUANTITY)
            // Decimal selling units such as 3 x 0.1 kg must not become 0.30000000000000004
            // and incorrectly fail against a recorded 0.3 kg batch.
            val total = marketRequestedQuantity(line.basis.pricedAmount, line.units)
            if (!total.isFinite() || !batch.isMarketSellableAt(offer.storefront.operatingBranchId, now) || batch.quantity.total < total)
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
        val rows = candidates("SELECT l.* $publicJoins JOIN buyer_saved_offers b ON b.listing_id=l.id WHERE $publicPredicate AND b.user_id=? ORDER BY b.created_at_millis DESC,l.id LIMIT $MARKET_SAVED_MAX_OFFERS",
            listOf(now, now, user))
        return MarketPage(project(user, rows, now), checkedAtMillis = now, unavailableSavedCount = (total-rows.size).coerceAtLeast(0))
    }
    fun updateSaved(user: UUID, request: MarketSavedUpdate): MarketPage {
        val offer = marketUuid(request.offerId)
        // The route uses SERIALIZABLE and retries the complete transaction. The user lock
        // protects account deletion; on its own it cannot protect a count in an old snapshot.
        if (query("SELECT id FROM users WHERE id=? FOR UPDATE", user) { it.getString(1) }.isEmpty()) marketFail("market.owner",403)
        if (!request.saved) execute("DELETE FROM buyer_saved_offers WHERE user_id=? AND listing_id=?", user, offer)
        else {
            val exists = query("SELECT listing_id FROM buyer_saved_offers WHERE user_id=? AND listing_id=?", user, offer) { it.getString(1) }.isNotEmpty()
            if (!exists) {
                val now = System.currentTimeMillis()
                if (query("SELECT l.id $publicJoins WHERE $publicPredicate AND l.id=?", now, now, offer) { it.getString(1) }.isEmpty())
                    marketFail("market.unavailable",404)
                if (query("SELECT count(*) FROM buyer_saved_offers WHERE user_id=?", user) { it.getInt(1) }.single() >= MARKET_SAVED_MAX_OFFERS) marketFail("market.saved_limit",409)
                execute("INSERT INTO buyer_saved_offers(user_id,listing_id,created_at_millis) VALUES (?,?,?) ON CONFLICT DO NOTHING",user,offer,now)
            }
        }
        return saved(user).copy(savedMutation = MarketSavedUpdate(offer.toString(), request.saved))
    }
    fun clearUnavailableSaved(user: UUID): MarketPage {
        if (query("SELECT id FROM users WHERE id=? FOR UPDATE",user) { it.getString(1) }.isEmpty()) marketFail("market.shopping_denied", 403)
        val now=System.currentTimeMillis()
        execute("DELETE FROM buyer_saved_offers WHERE user_id=? AND listing_id NOT IN (SELECT l.id $publicJoins WHERE $publicPredicate)",user,now,now)
        return saved(user).copy(unavailableSavedCleared = true)
    }

    /** Inventory readers get a family publication index, never a seller's draft or private location settings. */
    fun stockPublicationStatus(user: UUID, store: UUID): MarketStockPublicationStatus {
        if (!readsStock(user, store)) marketFail("market.owner", 403)
        val root = query("SELECT coalesce(parent_store_id,id) FROM stores WHERE id=? AND is_active", store) { it.getString(1) }
            .singleOrNull() ?: marketFail("market.unavailable", 404)
        val now = System.currentTimeMillis()
        val visible = query("SELECT f.store_id ${MarketplacePublicVisibility.shopJoins} WHERE s.parent_store_id=? AND ${MarketplacePublicVisibility.shopPredicate}",
            marketUuid(root), now, now) { it.getString(1) }.isNotEmpty()
        val entries = if (!visible) emptyList() else query("""SELECT DISTINCT ON(l.goods_item_id)
            l.goods_item_id,l.gtin,l.is_published,i.barcode_models,i.barcodes,i.measurement_unit_id
            $publicJoins WHERE s.parent_store_id=? AND ${MarketplacePublicVisibility.shopPredicate}
            AND l.is_published AND i.is_active AND ${MarketplacePublicVisibility.cataloguePredicate}
            ORDER BY l.goods_item_id,l.is_published DESC LIMIT 1000""",marketUuid(root),now,now) { row ->
            val item = GoodsItemDataModel(id=row.getString("goods_item_id"),
                barcodes=jsonBase.decodeFromString(row.getString("barcodes")),barcodeModels=jsonBase.decodeFromString(row.getString("barcode_models")))
            val code=row.getString("gtin")?.takeIf { it in item.standardBarcodeValues().mapNotNull(::marketCanonicalGtin) }
            MarketStockPublicationEntry(item.id,code,row.getString("measurement_unit_id"),row.getBoolean("is_published"))
        }
        return MarketStockPublicationStatus(user.toString(),store.toString(),root,visible,entries,now)
    }

    /** Only explicitly selected, still eligible physical locations. These are availability hints, not branch quotes. */
    private fun branchAvailability(offer: MarketOffer): Pair<List<MarketBranchAvailability>, Boolean> {
        if (!offer.storefront.shareBranchAvailability) return emptyList<MarketBranchAvailability>() to false
        val shop=marketUuid(offer.storefront.storeId)
        val selected=selectedLocations(shop)
        if (selected.isEmpty()) return emptyList<MarketBranchAvailability>() to false
        val itemId=query("SELECT goods_item_id FROM marketplace_listings WHERE id=? AND store_id=?",marketUuid(offer.id),shop) { it.getString(1) }
            .singleOrNull() ?: return emptyList<MarketBranchAvailability>() to false
        val item=items(listOf(marketUuid(itemId))).singleOrNull() ?: return emptyList<MarketBranchAvailability>() to false
        val now=offer.checkedAtMillis
        data class Branch(val id:String,val name:List<LocalizedStringDataModel>,val address:String)
        val args=buildList<Any?> { addAll(selected.map(::marketUuid));add(marketUuid(offer.storefront.operatingBranchId));add(now);add(now) }
        val found=query("""SELECT s.id,s.name,s.address FROM stores owner JOIN stores s ON s.id IN (${selected.joinToString(",") { "?" }})
            LEFT JOIN store_subscription_states e ON e.store_id=s.id
            WHERE owner.id=? AND s.is_active AND
                ((s.id=owner.parent_store_id AND s.parent_store_id IS NULL)
                OR (s.parent_store_id=owner.parent_store_id AND s.branch_type='PHYSICAL'
                    AND e.status='active' AND coalesce(e.current_period_start_millis,e.started_at_millis)<=?
                    AND ((e.access_kind='lifetime' AND e.plan_id='internal_lifetime' AND e.current_period_end_millis IS NULL AND NOT e.auto_renew)
                        OR (e.access_kind IN ('paid','timed') AND e.current_period_end_millis>?))))
            ORDER BY s.id LIMIT ${MARKET_BRANCH_MAX_LOCATIONS + 1}""",
            *args.toTypedArray()) { row ->
            Branch(row.getString("id"),publicLocationName(row.getString("name")),cleanLocationAddress(row.getString("address")))
        }
        val branches = found.take(MARKET_BRANCH_MAX_LOCATIONS).filter { it.name.isNotEmpty() && it.address.isNotBlank() }
        if (branches.isEmpty()) return emptyList<MarketBranchAvailability>() to (found.size>MARKET_BRANCH_MAX_LOCATIONS)
        val branchIds = branches.map { marketUuid(it.id) }
        // Search by exact raw variants first; then recheck canonical STANDARD GTINs in Kotlin.
        // Never reuse the private stock mirror's fuzzy name-based fallback for a public match.
        // Only the still-valid reviewed public identity may link a separately owned branch item.
        // A private stock barcode edit cannot silently relink a published product to another SKU.
        val publicIdentity = item.copy(barcodes = offer.gtin?.let { listOf(it) }.orEmpty(), barcodeModels = emptyList())
        val codes = publicIdentity.standardBarcodeValues().mapNotNull(::marketCanonicalGtin).flatMap { canonical ->
            listOf(canonical,canonical.trimStart('0')) + listOf(8,12,13).filter { length -> canonical.take(14-length).all { it=='0' } }.map { canonical.takeLast(it) }
        }.distinct()
        val barcodeArray = codes.joinToString(",", "{", "}")
        // JDBC binds UUID store keys and text parameters in one ordered collection. Avoid
        // reifying an inferred UUID/String intersection type when creating the vararg array.
        val matchArguments = buildList<Any?> {
            addAll(branchIds)
            add(item.measurementUnitId)
            add(barcodeArray)
            add(barcodeArray)
        }
        val matching: List<GoodsItemDataModel> = if (codes.isEmpty()) emptyList() else query("""SELECT i.id,i.store_id,i.barcodes,i.barcode_models,i.measurement_unit_id
            FROM stock_items i WHERE i.is_active AND i.store_id IN (${branchIds.joinToString(",") { "?" }})
            AND i.measurement_unit_id=? AND
            ((i.barcodes::jsonb ??| ?::text[]) OR EXISTS(SELECT 1 FROM jsonb_array_elements(i.barcode_models::jsonb) bc WHERE bc->>'value'=ANY(?::text[])))
            ORDER BY i.id LIMIT 5001""", *matchArguments.toTypedArray()) { row ->
            GoodsItemDataModel(id=row.getString("id"),storeId=row.getString("store_id"),
                barcodes=jsonBase.decodeFromString(row.getString("barcodes")),barcodeModels=jsonBase.decodeFromString(row.getString("barcode_models")),
                measurementUnitId=row.getString("measurement_unit_id"))
        }
        if (matching.size>5000) return emptyList<MarketBranchAvailability>() to true
        val matches = matching.filter { marketSameBranchProduct(publicIdentity,it) }
        val byId = (matches+item).associateBy { it.id }
        val available = batches(byId.keys.map(::marketUuid),branchIds,now).filter { batch ->
            val candidate = byId[batch.goodsItemId]
            candidate != null && (candidate.id==item.id || candidate.storeId==batch.storeId) && batch.isMarketSellableAt(batch.storeId,now) &&
                batch.quantity.id==item.measurementUnitId
        }.map { it.storeId }.toSet()
        return branches.map { branch -> MarketBranchAvailability(branch.id,branch.name,branch.address,
            if(branch.id in available) MARKET_AVAILABILITY_RECORDED else MARKET_AVAILABILITY_CONFIRM,now) } to (found.size>MARKET_BRANCH_MAX_LOCATIONS)
    }

    private fun items(ids: List<UUID>): List<GoodsItemDataModel> {
        if(ids.isEmpty()) return emptyList()
        // Select only fields needed by retail price calculation; no costs, notes, owner, or supplier data.
        return query("""SELECT id,store_id,barcodes,barcode_models,measurement_unit_id,sale_prices,promotions,active_shelf_batch_id,category_ids,updated_at_millis
            FROM stock_items WHERE is_active AND id IN (${ids.joinToString(",") { "?" }})""", *ids.toTypedArray()) { row ->
            GoodsItemDataModel(id=row.getString("id"),storeId=row.getString("store_id"),
                measurementUnitId=row.getString("measurement_unit_id"),
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
    private fun project(user: UUID, candidates: List<Candidate>, now: Long, requestedUnits: Map<String, Int> = emptyMap(),
        publicCategoryIds: Set<String>? = null, selectedCategoryIds: Set<String>? = null): List<MarketOffer> {
        if(candidates.isEmpty()) return emptyList()
        val itemIds=candidates.map { marketUuid(it.listing.goodsItemId) }.distinct()
        val storeIds=candidates.map { marketUuid(it.listing.storeId) }.distinct()
        val items=items(itemIds).associateBy { it.id }
        val stores=query("SELECT * FROM marketplace_storefronts WHERE store_id IN (${storeIds.joinToString(",") { "?" }})",*storeIds.toTypedArray(),map=::storefrontRow).associateBy { it.storeId }
        val branchIds=stores.values.map { marketUuid(it.operatingBranchId) }.distinct()
        val batches=batches(itemIds,branchIds,now).groupBy { it.storeId to it.goodsItemId }
        val saved=query("SELECT listing_id FROM buyer_saved_offers WHERE user_id=?",user) { it.getString(1) }.toSet()
        return candidates.mapNotNull { candidate ->
            val listing=candidate.listing; val item=items[listing.goodsItemId] ?: return@mapNotNull null
            val shop=stores[listing.storeId] ?: return@mapNotNull null
            val eligible=batches[shop.operatingBranchId to listing.goodsItemId].orEmpty()
                .filter { it.isMarketSellableAt(shop.operatingBranchId,now) }
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
                categoryIds=item.categoryIds.filter { publicCategoryIds == null || it in publicCategoryIds }.distinct()
                    .sortedBy { if (selectedCategoryIds == null || it in selectedCategoryIds) 0 else 1 }.take(16),priceMinor=minor,currencyCode=currency.takeIf { minor!=null },
                pricedAmount=priced.takeIf { minor!=null },unitId=batch?.quantity?.id?.takeIf { minor!=null },
                unitName=if(minor==null) emptyList() else batch?.quantity?.immutableUnitName.orEmpty().take(12)
                    .map { LocalizedStringDataModel(it.language.take(12), it.value.take(120)) },
                availability=if(batch!=null && minor!=null && batch.quantity.total >= total) MARKET_AVAILABILITY_RECORDED else MARKET_AVAILABILITY_CONFIRM,
                checkedAtMillis=now,sourceUpdatedAtMillis=maxOf(candidate.sourceUpdated,item.updatedAtMillis,batch?.updatedAtMillis ?: 0L),
                saved=listing.id in saved,product=listing.product,
                originalPriceMinor=base?.takeIf { it.currency.trim().uppercase() == currency }?.price?.let(::marketPriceMinor)
                    ?.takeIf { original -> minor != null && original > minor })
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
