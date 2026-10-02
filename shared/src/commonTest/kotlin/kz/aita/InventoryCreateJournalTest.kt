package kz.aita

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class InventoryCreateJournalTest {
    @Test fun itemAndBatchRemainOrderedAcrossRestartAndLostReply() = runTest {
        val disk = mutableMapOf<String, String>()
        fun journal() = InventoryCreateJournal({ disk[it] }, { key, text -> disk[key] = text })
        val first = InventoryCreateCommand("operation-1", "store", item = GoodsItemDataModel(id = "item-uuid", storeId = "store"))
        val second = InventoryCreateCommand("operation-2", "store")
        journal().change("account") { it + first }
        journal().change("account") { it + second }
        assertEquals(listOf(first, second), journal().list("account"))
        // A lost reply leaves the same item UUID and order, never invents another operation.
        assertEquals("item-uuid", journal().list("account").first().item?.id)
        journal().change("account") { it.filterNot { row -> row.id == first.id } }
        assertEquals(listOf(second), journal().list("account"))
        assertTrue(journal().list("another-account").isEmpty())
    }
    @Test fun failedDurableWriteNeverPublishesAnUncommittedQueue() = runTest {
        var broken = false
        var disk: String? = null
        val journal = InventoryCreateJournal({ disk }, { _, text -> if (broken) error("Disk full") else disk = text })
        val first = InventoryCreateCommand("one", "store")
        journal.change("account") { it + first }
        broken = true
        assertFailsWith<IllegalStateException> { journal.change("account") { it + InventoryCreateCommand("two", "store") } }
        assertEquals(listOf(first), journal.list("account"))
        assertEquals(listOf(first), InventoryCreateJournal({ disk }, { _, _ -> }).list("account"))
    }
    @Test fun corruptQueueCannotBeSilentlyReplacedAndAccountsStaySeparate() = runTest {
        var writes = 0
        val journal = InventoryCreateJournal({ if (it == "broken") "{incomplete" else null }, { _, _ -> writes++ })
        assertFails { journal.change("broken") { emptyList() } }
        assertEquals(0, writes)
        assertTrue(journal.list("new-account").isEmpty())
    }
    @Test fun cloudSnapshotCannotHideOrDuplicateLocallyCreatedRows() {
        val pending = GoodsItemDataModel(id = "a", storeId = "store", barcodes = listOf("2618000001248"))
        val other = GoodsItemDataModel(id = "b", storeId = "store")
        assertEquals(listOf(other, pending), mergeById(listOf(pending.copy(barcodes = emptyList()), other), listOf(pending)) { it.id })
    }
}
