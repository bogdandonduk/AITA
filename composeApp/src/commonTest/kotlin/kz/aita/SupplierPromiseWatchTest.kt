package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplierPromiseWatchTest {
    private data class Order(
        val id: String,
        val promisedAt: Long?,
        val status: String,
    )

    @Test
    fun overdueAndDueSoonPromisesLeadWithoutDuplicatingOrders() {
        val now = 1_000_000L
        val ordinary = Order("ordinary", now + SUPPLIER_PROMISE_WATCH_WINDOW_MILLIS + 1L, "OPEN")
        val dueSoon = Order("soon", now + 2_000L, "OPEN")
        val overdue = Order("late", now - 1L, "OPEN")
        val duplicate = dueSoon.copy(status = "UPDATED")

        val watch = buildSupplierPromiseWatch(
            values = listOf(ordinary, dueSoon, overdue, duplicate),
            nowEpochMillis = now,
            promisedAt = { it.promisedAt },
            isTerminal = { isTerminalSupplierOrderStatus(it.status) },
            stableKey = { it.id },
        )

        assertEquals(listOf("late", "soon", "ordinary"), watch.prioritized.map { it.id })
        assertEquals(listOf("late", "soon"), watch.attention.map { it.id })
        assertEquals(1, watch.overdueCount)
        assertEquals(1, watch.dueSoonCount)
        assertEquals(now + 2_000L, watch.nextPromiseAtEpochMillis)
        assertTrue(watch.hasAttention)
    }

    @Test
    fun terminalAndUnscheduledOrdersDoNotBecomeFalseAttentionOrNextPromise() {
        val now = 10_000L
        val deliveredLate = Order("done", now - 5_000L, "DELIVERED")
        val unscheduled = Order("open", null, "OPEN")

        val watch = buildSupplierPromiseWatch(
            values = listOf(deliveredLate, unscheduled),
            nowEpochMillis = now,
            promisedAt = { it.promisedAt },
            isTerminal = { isTerminalSupplierOrderStatus(it.status) },
            stableKey = { it.id },
        )

        assertTrue(watch.attention.isEmpty())
        assertFalse(watch.hasAttention)
        assertEquals(null, watch.nextPromiseAtEpochMillis)
        assertEquals(listOf("done", "open"), watch.prioritized.map { it.id })
    }

    @Test
    fun missingStableKeysNeverDropDistinctItemsBecauseOfHashCollisions() {
        class SameHash(val label: String) {
            override fun hashCode(): Int = 7
        }

        val first = SameHash("first")
        val second = SameHash("second")
        val watch = buildSupplierPromiseWatch(
            values = listOf(first, second),
            nowEpochMillis = 100L,
            promisedAt = { null },
            isTerminal = { false },
            stableKey = { null },
        )

        assertEquals(listOf("first", "second"), watch.prioritized.map { it.label })
    }
}
