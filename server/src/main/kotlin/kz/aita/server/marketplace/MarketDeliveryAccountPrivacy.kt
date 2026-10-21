package kz.aita.server.marketplace

import java.sql.Connection
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kz.aita.*

internal fun hasOpenMarketDeliveries(db: Connection, user: UUID): Boolean = db.prepareStatement(
    "SELECT 1 FROM market_delivery_orders WHERE (buyer_id=? OR courier_id=?) AND status NOT IN ('DELIVERED','CANCELLED') LIMIT 1"
).use { s -> s.setObject(1,user); s.setObject(2,user); s.executeQuery().use { it.next() } }

internal fun hasOpenStoreDeliveries(db: Connection, stores: List<UUID>): Boolean {
    if(stores.isEmpty()) return false
    return db.prepareStatement("SELECT 1 FROM market_delivery_orders WHERE branch_store_id IN (${stores.joinToString { "?" }}) AND status NOT IN ('DELIVERED','CANCELLED') LIMIT 1").use { s ->
        stores.forEachIndexed { i,id -> s.setObject(i+1,id) };s.executeQuery().use { it.next() }
    }
}

/** Runs in the same locked transaction as account deletion. No orders are silently cancelled. */
internal fun eraseMarketDeliveryContacts(db: Connection, user: UUID) {
    check(!db.autoCommit)
    check(!hasOpenMarketDeliveries(db,user))
    val orders=db.prepareStatement("SELECT payload::text FROM market_delivery_orders WHERE buyer_id=? OR courier_id=? FOR UPDATE").use { s ->
        s.setObject(1,user); s.setObject(2,user)
        s.executeQuery().use { rows -> buildList { while(rows.next()) add(jsonBase.decodeFromString<MarketDeliveryOrder>(rows.getString(1))) } }
    }
    db.prepareStatement("UPDATE market_delivery_orders SET payload=?::jsonb,position=NULL WHERE id=?").use { s ->
        orders.forEach { order ->
            s.setString(1,jsonBase.encodeToString(order.withoutDeletedAccountContacts(user.toString())))
            s.setObject(2,UUID.fromString(order.id)); s.addBatch()
        }
        s.executeBatch()
    }
}
