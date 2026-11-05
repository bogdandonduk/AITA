package kz.aita

import kotlin.test.*
import kotlinx.serialization.encodeToString

class StockScreenImportModelTest {
    private val config get() = globalAppConfigurationState.payloadValue
    private fun row() = ScreenStockRow(id="review-1",name="Молоко",barcode="0460123456789",sale="750,50",supply="0",
        currency="KZT",unitId=config.goodsItemsQuantityUnits.first().id,itemId="stable-item",batchId="stable-batch",reviewed=true)

    @Test fun createsNoInventedStockWhenQuantityWasNotVisible() {
        val row=row()
        assertTrue(row.problems(config).isEmpty())
        assertNull(row.toStockBatch("user","store",config))
        assertNull(row.copy(quantity="0").toStockBatch("user","store",config))
        val item=row.toStockItem("user","store","ru",config)
        assertEquals("stable-item",item.id)
        assertEquals("0460123456789",item.barcodes.single())
        assertEquals("750.5",item.salePrices.single().price)
        assertEquals("0",item.supplyPrices.single().price)
    }

    @Test fun quantityIsNotMultipliedOnRetryAndBatchIdIsStable() {
        val row=row().copy(quantity="5")
        val batch=requireNotNull(row.toStockBatch("u","s",config))
        assertEquals("stable-batch",batch.id)
        assertEquals("stable-item",batch.goodsItemId)
        assertEquals(5.0,batch.quantity.total)
        assertEquals(StockBatchKindDataModel.UNIVERSAL,batch.kind)
        assertEquals(batch,row.toStockBatch("u","s",config))
    }

    @Test fun unknownUnitsAndFractionalPiecesCannotBeImported() {
        assertContains(row().copy(unitId="missing").problems(config),"UNIT")
        val whole=config.goodsItemsQuantityUnits.first {it.roundTotal}
        assertContains(row().copy(unitId=whole.id,quantity="0.5").problems(config),"QUANTITY")
    }

    @Test fun ambiguousValuesRequireExplicitChoice() {
        val ambiguous=row().copy(sale="1,250")
        assertTrue(screenStockNumberAmbiguous(ambiguous.sale))
        assertContains(ambiguous.problems(config),"SALE")
        assertEquals("1250",screenStockGroupedNumber(ambiguous.sale))
        assertEquals("1.25",screenStockNumber(ambiguous.sale))
        assertFalse(ambiguous.copy(sale="1250",resolvedNumbers=setOf("SALE")).problems(config).contains("SALE"))
        assertFalse(ambiguous.copy(sale="1.234",resolvedNumbers=setOf("SALE")).problems(config).contains("SALE"))
    }

    @Test fun duplicateCheckIgnoresOwnStableIdButNeverAnotherItem() {
        val row=row()
        val item=row.toStockItem("u","s","en",config)
        assertFalse(row.hasExistingBarcode(listOf(item),"s"))
        assertTrue(row.hasExistingBarcode(listOf(item.copy(id="other")),"s"))
        assertFalse(row.hasExistingBarcode(listOf(item.copy(id="other",storeId="elsewhere")),"s"))
    }

    @Test fun importCheckpointRoundTripsWithoutChangingIdsOrSubmission() {
        val row=row().copy(importStarted=true,itemSaved=true,quantity="4",resolvedNumbers=setOf("QUANTITY"))
        val draft=StockScreenImportDraft(accountId="u",storeId="s",rows=listOf(row))
        assertEquals(draft,stockScreenImportJson.decodeFromString<StockScreenImportDraft>(stockScreenImportJson.encodeToString(draft)))
        assertEquals(row,mergeScreenStockRows(listOf(row),listOf(row.copy(quantity="8"))).single())
    }
    @Test fun onlyConfirmedUnqueuedFailuresCanReopenTheOriginalFields() {
        val row = row().copy(importStarted = true)
        assertTrue(row.canCorrectRejectedCreate(true, true, emptyList(), emptyList()))
        assertFalse(row.canCorrectRejectedCreate(false, true, emptyList(), emptyList()))
        assertFalse(row.canCorrectRejectedCreate(true, false, emptyList(), emptyList()))
        val item = row.toStockItem("u", "s", "en", config)
        assertFalse(row.canCorrectRejectedCreate(true, true, listOf(item), emptyList()))
        assertFalse(row.canCorrectRejectedCreate(true, true, emptyList(), listOf(InventoryCreateCommand("pending", "s", item = item))))
        assertFalse(row.copy(itemSaved = true).canCorrectRejectedCreate(true, true, emptyList(), emptyList()))
    }

    @Test fun restoringDraftRejectsSharedWriteIdentitiesAndWrongOwner() {
        val first = row()
        val second = first.copy(id = "review-2", itemId = "item-2", batchId = "batch-2")
        val draft = StockScreenImportDraft(accountId = "u", storeId = "s", rows = listOf(first, second))
        validateStockScreenImportDraft(draft, "u", "s")
        assertFailsWith<IllegalArgumentException> { validateStockScreenImportDraft(draft, "other", "s") }
        for (invalid in listOf(second.copy(id = first.id), second.copy(itemId = first.itemId), second.copy(batchId = first.batchId),
            second.copy(id = ""), second.copy(itemId = " "), second.copy(batchId = "x".repeat(201)), second.copy(itemId = "item\n2"))) {
            assertFailsWith<IllegalArgumentException> {
                validateStockScreenImportDraft(draft.copy(rows = listOf(first, invalid)), "u", "s")
            }
        }
    }

    @Test fun restoringDraftRejectsImpossibleSuccessButAcceptsDurablePartialProgress() {
        val first = row()
        for (valid in listOf(first, first.copy(importStarted = true), first.copy(importStarted = true, itemSaved = true),
            first.copy(importStarted = true, itemSaved = true, complete = true, selected = false))) {
            validateStockScreenImportDraft(StockScreenImportDraft(accountId = "u", storeId = "s", rows = listOf(valid)), "u", "s")
        }
        for (invalid in listOf(first.copy(itemSaved = true), first.copy(importStarted = true, reviewed = false),
            first.copy(importStarted = true, complete = true, selected = false),
            first.copy(importStarted = true, itemSaved = true, complete = true, selected = true))) {
            assertFailsWith<IllegalArgumentException> {
                validateStockScreenImportDraft(StockScreenImportDraft(accountId = "u", storeId = "s", rows = listOf(invalid)), "u", "s")
            }
        }
    }

}
