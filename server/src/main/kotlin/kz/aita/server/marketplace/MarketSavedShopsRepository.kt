package kz.aita.server.marketplace

import kz.aita.*
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

internal class MarketSavedShopsRepository(private val db: Connection) {
    init { check(!db.autoCommit) }
    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> = db.prepareStatement(sql).use { s ->
        s.queryTimeout = 5; args.forEachIndexed { i, v -> s.setObject(i + 1, v) }
        s.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
    }
    private fun execute(sql: String, vararg args: Any?) = db.prepareStatement(sql).use { s ->
        s.queryTimeout = 5; args.forEachIndexed { i, v -> s.setObject(i + 1, v) }; s.executeUpdate()
    }
    fun snapshot(user: UUID) = MarketSavedShops(user.toString(),
        query("SELECT revision FROM buyer_saved_shop_states WHERE user_id=?", user) { it.getLong(1) }.singleOrNull() ?: 0,
        query("SELECT store_id FROM buyer_saved_shops WHERE user_id=? ORDER BY created_at_millis,store_id", user) { it.getString(1) },
        System.currentTimeMillis())

    fun change(user: UUID, input: MarketSavedShopChange): MarketSavedShops {
        if (!input.isValidSavedShopChange()) marketFail("market.saved_shops_failed")
        if (query("SELECT id FROM users WHERE id=? AND is_active FOR UPDATE", user) { it.getString(1) }.isEmpty()) marketFail("market.shopping_denied",403)
        execute("INSERT INTO buyer_saved_shop_states(user_id) VALUES (?) ON CONFLICT DO NOTHING",user)
        query("SELECT revision FROM buyer_saved_shop_states WHERE user_id=? FOR UPDATE",user) { it.getLong(1) }
        val before = snapshot(user)
        val exists = input.storeId in before.storeIds
        // Absolute desired state makes a lost-response retry harmless. A stale contrary intent
        // cannot undo another device's newer edit, including a save -> unsave -> save cycle.
        if (exists == input.saved) return before
        if (before.revision != input.expectedRevision || before.revision == Long.MAX_VALUE) marketFail("market.saved_shops_changed",409)
        val store = marketUuid(input.storeId)
        if (input.saved) {
            if (before.storeIds.size >= MARKET_SAVED_SHOPS_MAX) marketFail("market.saved_shops_full",409)
            val now = System.currentTimeMillis()
            val visible = query("SELECT f.store_id ${MarketplacePublicVisibility.shopJoins} WHERE ${MarketplacePublicVisibility.shopPredicate} AND f.store_id=?",now,now,store) { it.getString(1) }
            if (visible.isEmpty()) marketFail("market.shop_unavailable",404)
            execute("INSERT INTO buyer_saved_shops(user_id,store_id,created_at_millis) VALUES (?,?,?)",user,store,now)
        } else execute("DELETE FROM buyer_saved_shops WHERE user_id=? AND store_id=?",user,store)
        execute("UPDATE buyer_saved_shop_states SET revision=revision+1 WHERE user_id=?",user)
        return snapshot(user)
    }
}
