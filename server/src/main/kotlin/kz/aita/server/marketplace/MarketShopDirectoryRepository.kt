package kz.aita.server.marketplace

import kz.aita.*
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

/** Public fields only. Runs in the caller's read-only repeatable-read transaction, so the count
 * and the requested window agree. No catalogue/stock projection is run once per shop card.
 */
internal class MarketShopDirectoryRepository(private val db: Connection) {
    init { check(!db.autoCommit) }
    private fun <T> query(sql: String, args: List<Any?>, map: (ResultSet) -> T): List<T> =
        db.prepareStatement(sql).use { statement ->
            statement.queryTimeout = 5
            args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
        }

    fun search(user: UUID, request: MarketShopDirectoryRequest): MarketShopDirectoryResult {
        val input = request.normalizedShopDirectoryRequest() ?: marketFail("market.shops_invalid")
        val now = System.currentTimeMillis()
        val predicates = mutableListOf(MarketplacePublicVisibility.shopPredicate)
        val args = mutableListOf<Any?>(now, now)
        // strpos is literal: %, _, quotes and backslashes never become SQL/wildcard syntax.
        input.text.split(' ').filter { it.isNotEmpty() }.forEach { term ->
            predicates += "strpos(lower(f.display_name || ' ' || f.public_address),lower(?))>0"
            args += term
        }
        if (input.city.isNotEmpty()) { predicates += "strpos(lower(f.city),lower(?))>0"; args += input.city }
        if (input.savedOnly) { predicates += "EXISTS (SELECT 1 FROM buyer_saved_shops b WHERE b.store_id=f.store_id AND b.user_id=?)"; args += user }
        val source = "${MarketplacePublicVisibility.shopJoins} WHERE ${predicates.joinToString(" AND ")}"
        val total = query("SELECT count(*) $source", args) { it.getLong(1) }.single()
        val rows = query("""SELECT f.store_id,f.display_name,f.city,f.public_address,f.pickup_note,f.is_published,f.revision,f.share_branch_availability,
            EXISTS (SELECT 1 FROM buyer_saved_shops b WHERE b.store_id=f.store_id AND b.user_id=?) AS saved,
            (SELECT count(*) FROM marketplace_listings l JOIN stock_items i ON i.id=l.goods_item_id
                WHERE l.store_id=f.store_id AND l.is_published AND i.is_active
                    AND i.store_id IN (s.id,coalesce(s.parent_store_id,s.id))) AS public_offer_count
            $source ORDER BY lower(f.display_name) COLLATE "C",f.store_id LIMIT ?""", listOf(user) + args + input.limit) { row ->
            MarketShopDirectoryEntry(MarketStorefront(row.getString("store_id"), row.getString("display_name"),
                row.getString("city"), row.getString("public_address"), row.getString("pickup_note"),
                row.getBoolean("is_published"), row.getLong("revision"), row.getBoolean("share_branch_availability")), row.getLong("public_offer_count"), row.getBoolean("saved"))
        }
        return MarketShopDirectoryResult(user.toString(), input, rows, total, now)
    }
}
