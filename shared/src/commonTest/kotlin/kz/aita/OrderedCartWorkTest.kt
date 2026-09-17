package kz.aita

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OrderedCartWorkTest {
    @Test fun rapidPostsAndAwaitedClearExecuteInOriginalOrder() = runTest {
        val queue = OrderedCartWork(backgroundScope)
        val seen = mutableListOf<Int>()
        repeat(100) { index -> assertTrue(queue.post { delay(1); seen += index }) }
        queue.run { seen += 100 }
        assertEquals((0..100).toList(), seen)
    }
    @Test fun oneFailedCommandDoesNotDropFollowingChanges() = runTest {
        val queue = OrderedCartWork(backgroundScope)
        val seen = mutableListOf<Int>()
        queue.post { seen += 1; error("save failed") }
        queue.post { seen += 2 }
        queue.drain()
        assertEquals(listOf(1, 2), seen)
        assertFailsWith<IllegalStateException> { queue.run { error("reported to caller") } }
        assertEquals(7, queue.run { 7 })
    }
    @Test fun overflowIsExplicitAndDoesNotSilentlyReplaceEarlierEdits() = runTest {
        val queue = OrderedCartWork(backgroundScope, capacity = 1)
        val seen = mutableListOf<Int>()
        assertTrue(queue.post { seen += 1 })
        assertFalse(queue.post { seen += 2 })
        queue.drain(); assertEquals(listOf(1), seen)
    }
    @Test fun cancelledQueuedCommandDoesNotExecuteLater() = runTest {
        val queue = OrderedCartWork(backgroundScope)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        queue.post { entered.complete(Unit); release.await() }
        entered.await()
        var changed = false
        val pending = launch { queue.run { changed = true } }
        runCurrent(); pending.cancelAndJoin(); release.complete(Unit)
        queue.drain(); assertFalse(changed)
    }
}
