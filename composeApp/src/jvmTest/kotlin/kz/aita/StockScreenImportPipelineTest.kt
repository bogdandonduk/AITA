package kz.aita

import kotlinx.serialization.encodeToString
import kotlin.test.*

/** The boundary from native recognition geometry to the existing durable inventory command
 * schema. Real addGoodsItem/addGoodsBatches offline replay is exercised by AitaSharedJvmFlowTest.
 * This test never accesses an account, a window, a server, or the user's cache.
 */
class StockScreenImportPipelineTest {
    private val config get() = globalAppConfigurationState.payloadValue
    private val account = "synthetic-capture-account"
    private val store = "synthetic-capture-store"

    private fun frame(source: StockScreenSource, quantity: String = "12,5", unit: String = "kg"):
        StockScreenFrame {
        fun cell(text: String, x: Int, y: Int, width: Int) =
            StockScreenWord(text, x.toFloat(), y.toFloat(), width.toFloat(), 22f)
        return StockScreenFrame("owned-window-1", 100L, "Synthetic stock fixture", listOf(
            cell("Barcode / Штрихкод", 10, 10, 170), cell("Name / Название", 200, 10, 250),
            cell("Quantity", 470, 10, 90), cell("Unit", 580, 10, 50),
            cell("Supply price", 650, 10, 150), cell("Sale price", 830, 10, 150),
            cell("0001234567890", 10, 55, 170), cell("Молоко 2,5%", 200, 55, 250),
            cell(quantity, 470, 55, 90), cell(unit, 580, 55, 50),
            cell("450,25", 650, 55, 150), cell("600,50 ₸", 830, 55, 150),
        ), source, width = 1100, height = 700)
    }

    @Test fun nativeStyleRowsBecomeStableInventoryCommandsAcrossPartialImportRestart() {
        for (source in StockScreenSource.entries) {
            val recognizer = StockScreenRecognizer()
            val first = recognizer.read(frame(source)).rows
            val captured = mergeScreenStockRows(emptyList(), first)
            val repeated = mergeScreenStockRows(captured, recognizer.read(frame(source)).rows).single()
            assertEquals(2, repeated.observations)
            assertEquals("12.5", repeated.quantity)
            assertEquals("0001234567890", repeated.barcode)
            assertFalse(repeated.reviewed)
            if (source == StockScreenSource.OCR) assertContains(repeated.warnings, "check_ocr")

            // Represents the user's explicit field review, including the internal barcode.
            val reviewed = repeated.copy(reviewed = true)
            assertTrue(reviewed.problems(config).isEmpty(), reviewed.problems(config).toString())
            val item = reviewed.toStockItem(account, store, "ru", config)
            val batch = requireNotNull(reviewed.toStockBatch(account, store, config))
            assertEquals(reviewed.itemId, item.id)
            assertEquals(reviewed.batchId, batch.id)
            assertEquals(item.id, batch.goodsItemId)
            assertEquals(account, item.userId)
            assertEquals(store, item.storeId)
            assertEquals(store, batch.storeId)
            assertEquals("0001234567890", item.barcodes.single())
            assertEquals("Молоко 2,5%", item.name.single { it.language == "ru" }.value)
            assertEquals("600.5", item.salePrices.single().price)
            assertEquals("450.25", item.supplyPrices.single().price)
            assertEquals("KZT", item.salePrices.single().currency)
            assertEquals(12.5, batch.quantity.total)
            assertEquals("450.25", batch.supplyPrice.price)
            assertEquals(StockBatchKindDataModel.UNIVERSAL, batch.kind)

            val commands = listOf(
                InventoryCreateCommand("synthetic-item-write", store, item = item),
                InventoryCreateCommand("synthetic-batch-write", store, batches = listOf(batch)),
            )
            val persisted = stockScreenImportJson.encodeToString(commands)
            val restoredCommands = stockScreenImportJson.decodeFromString<List<InventoryCreateCommand>>(persisted)
            val partial = reviewed.copy(importStarted = true, itemSaved = true)
            val draft = StockScreenImportDraft(accountId = account, storeId = store, rows = listOf(partial))
            val restoredDraft = stockScreenImportJson.decodeFromString<StockScreenImportDraft>(
                stockScreenImportJson.encodeToString(draft))
            validateStockScreenImportDraft(restoredDraft, account, store)
            val resumed = restoredDraft.rows.single()
            assertEquals(commands[0], resumed.pendingItemWrite(restoredCommands, store))
            assertEquals(commands[1], resumed.pendingBatchWrite(restoredCommands, store))
            assertEquals(item, resumed.toStockItem(account, store, "ru", config))
            assertEquals(batch, resumed.toStockBatch(account, store, config))
            assertFalse(resumed.hasExistingBarcode(listOf(item), store))
            assertTrue(resumed.hasExistingBarcode(listOf(item.copy(id = "another-stock-item")), store))
            // A new screenshot cannot rewrite the already submitted quantity or identities.
            val later = recognizer.read(frame(source, quantity = "99")).rows
            assertEquals(resumed, mergeScreenStockRows(listOf(resumed), later).single())
        }
    }

    @Test fun gramTemplateWithoutVisibleQuantityDoesNotInventABatch() {
        val captured = StockScreenRecognizer().read(frame(StockScreenSource.OCR, quantity = "", unit = "g")).rows
        val row = mergeScreenStockRows(emptyList(), captured).single().copy(reviewed = true)
        assertEquals("g", row.unit)
        assertEquals("", row.quantity)
        assertTrue(row.problems(config).isEmpty())
        val item = row.toStockItem(account, store, "ru", config)
        assertEquals(row.resolveUnit(config.goodsItemsQuantityUnits)?.id, item.measurementUnitId)
        assertNull(row.toStockBatch(account, store, config))
    }
}
