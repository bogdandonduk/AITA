package kz.aita

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.*
import kotlinx.coroutines.runBlocking

/** Uses an in-memory SQLite database, never the app's real local data directory. */
class StockImportDraftStorageTest {
    private fun database(work: suspend () -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppDatabase.Schema.create(driver).await()
            setAppDatabaseForTests(AppDatabase(driver))
            work()
        } finally {
            setAppDatabaseForTests(null)
            driver.close()
        }
    }

    @Test
    fun unicodeDraftSurvivesChunkingAndIsIsolatedByAccountAndStore() = database {
        val draft = "Молоко 😀\n".repeat(15_000)
        writeStockImportDraft("one", "shop", draft)
        writeStockImportDraft("one", "other", "other store")
        writeStockImportDraft("two", "shop", "other user")
        assertEquals(draft, readStockImportDraft("one", "shop"))
        assertEquals("other store", readStockImportDraft("one", "other"))
        assertEquals("other user", readStockImportDraft("two", "shop"))
        assertNull(readStockImportDraft("three", "shop"))
    }

    @Test
    fun corruptDraftIsAnErrorNotAnEmptyImport() = database {
        val key = "stock-screen-import-v1:3:one:4:shop"
        val marker = "aita-cache-chunks-v1:${key.length}:$key:manifest"
        putLocalKv(marker, "broken")
        assertFailsWith<IllegalStateException> { readStockImportDraft("one", "shop") }
        assertEquals("broken", getLocalKv(marker))
    }

    @Test
    fun rowCheckpointCanRecoverAnAcceptedItemWithoutRewritingTheDraft() = database {
        writeStockImportDraft("one", "shop", "immutable item ids and values")
        writeStockImportProgress("one", "shop", "row1", StockImportProgress(true, false, false))
        writeStockImportProgress("one", "shop", "row1", StockImportProgress(true, true, false))
        assertEquals(
            mapOf("row1" to StockImportProgress(true, true, false)),
            readStockImportProgress("one", "shop", listOf("row1", "row2")),
        )
        assertEquals("immutable item ids and values", readStockImportDraft("one", "shop"))
        writeStockImportProgress("one", "shop", "row1", StockImportProgress(true, true, true))
        assertEquals(
            StockImportProgress(true, true, true),
            readStockImportProgress("one", "shop", listOf("row1"))["row1"],
        )
    }

    @Test
    fun clearingOneDraftCannotDeleteAnotherOwnersCheckpoints() = database {
        for ((account, store) in listOf("one" to "shop", "one" to "shop-more", "two" to "shop")) {
            writeStockImportDraft(account, store, "draft")
            writeStockImportProgress(account, store, "row", StockImportProgress(true, true, false))
        }
        deleteStockImportDraft("one", "shop")
        assertNull(readStockImportDraft("one", "shop"))
        assertTrue(readStockImportProgress("one", "shop", listOf("row")).isEmpty())
        assertEquals("draft", readStockImportDraft("one", "shop-more"))
        assertEquals(1, readStockImportProgress("one", "shop-more", listOf("row")).size)
        assertEquals(1, readStockImportProgress("two", "shop", listOf("row")).size)
    }

    @Test
    fun corruptAndImpossibleProgressCannotTurnIntoSuccess() = database {
        assertFailsWith<IllegalArgumentException> {
            writeStockImportProgress("one", "shop", "row", StockImportProgress(true, false, true))
        }
        putLocalKv("stock-screen-import-v1:3:one:4:shop:progress:row", "1|0|1")
        assertFailsWith<IllegalStateException> {
            readStockImportProgress("one", "shop", listOf("row"))
        }
    }

    @Test
    fun maximumSizeDraftRestoresSparseCheckpointsAndIgnoresOtherOwners() = database {
        val rows = (1..3000).map { "row$it" }
        for (row in listOf("row1", "row129", "row3000")) {
            writeStockImportProgress("one", "shop", row, StockImportProgress(true, true, false))
            writeStockImportProgress("other", "shop", row, StockImportProgress(true, true, true))
        }
        val progress = readStockImportProgress("one", "shop", rows)
        assertEquals(setOf("row1", "row129", "row3000"), progress.keys)
        assertTrue(progress.values.all { it.itemSaved && !it.complete })
        clearStockImportProgress("one", "shop")
        assertTrue(readStockImportProgress("one", "shop", rows).isEmpty())
        assertEquals(3, readStockImportProgress("other", "shop", rows).size)
    }

    @Test
    fun oversizedAndNullProgressCannotBeMistakenForMissingOrValid() = database {
        val key = "stock-screen-import-v1:3:one:4:shop:progress:row"
        for (value in listOf("1|1|1" + "x".repeat(3_000_000), null)) {
            putLocalKv(key, value)
            assertFailsWith<IllegalStateException> {
                readStockImportProgress("one", "shop", listOf("row"))
            }
        }
    }
}
