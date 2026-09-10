package kz.aita.server

import java.util.UUID
import org.jetbrains.exposed.sql.transactions.TransactionManager

/** PostgreSQL transaction locks, in a disjoint namespace from auth/order locks. Only parsed UUIDs
 * enter SQL. The root-level lock also covers transfers between two branches and destination clones.
 * All checkout, transfer/decision, and batch-edit/delete paths take it BEFORE inventory reads.
 */
internal fun stockInventoryLockSql(rootId: UUID): String =
    "SELECT pg_advisory_xact_lock(hashtextextended('aita_inventory_root:$rootId', 0))"

internal fun lockStockInventoryRootsInsideTransaction(rootIds: Iterable<UUID>) {
    rootIds.distinct().sortedBy(UUID::toString).forEach { rootId ->
        TransactionManager.current().exec(stockInventoryLockSql(rootId))
    }
}
