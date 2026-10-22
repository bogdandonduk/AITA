package kz.aita

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.*

class SupplierPickingLedgerTest {
    private val unit = QuantityDataModel("0", emptyList(), 4.0, 1.0, true)
    private fun line(id: String = "line", order: String = "order", qty: QuantityDataModel = unit) = SupplierOrderLineDataModel(
        id, order, "goods", qty, supplierAcceptedQuantity = qty, goodsItemBarcodeSnapshots = listOf("4006381333931"))
    private fun bundle(lines: List<SupplierOrderLineDataModel> = listOf(line()), id: String = "order") = SupplierOrderWithLinesDataModel(
        SupplierOrderDataModel(id = id, storeId = "store", supplierId = "supplier", status = SupplierOrderStatusDataModel.Confirmed), lines)
    private fun owner(account: String = "account", supplier: String = "supplier") = SupplierPickingScope(account, supplier) { true }
    private class Memory {
        val data = mutableMapOf<String, String>()
        var dropWrite = false
        suspend fun read(key: String) = data[key]
        suspend fun write(key: String, value: String) { if (!dropWrite) data[key] = value }
    }
    @Test fun journalSurvivesRestartWithoutUsingRemoteNavigationState() = runTest {
        val memory = Memory(); val source = supplierPickingLine(line())
        val first = SupplierPickingLedger(memory::read, memory::write) { 1000 }
        first.update(owner(), listOf(source), mapOf(source.key to 2.0))
        val restarted = SupplierPickingLedger(memory::read, memory::write) { 2000 }
        assertEquals(2.0, supplierPickedQuantity(source, restarted.load(owner())[source.key]))
        assertTrue(restarted.load(owner("another")).isEmpty())
        assertTrue(restarted.load(owner(supplier = "another")).isEmpty())
    }
    @Test fun partialQuantitiesDoNotPermitPackingAndExactCompletionDoes() = runTest {
        val memory = Memory(); val ledger = SupplierPickingLedger(memory::read, memory::write) { 1000 }
        val source = supplierPickingLine(line())
        val partial = ledger.update(owner(), listOf(source), mapOf(source.key to 2.0))
        assertEquals(emptyList(), supplierPickingReadyOrderIds(listOf(bundle()), listOf("order"), partial))
        val complete = ledger.update(owner(), listOf(source), mapOf(source.key to 4.0))
        assertEquals(listOf("order"), supplierPickingReadyOrderIds(listOf(bundle()), listOf("order", "order"), complete))
    }
    @Test fun packingChecksOnlyFullyPreparedOrdersNeverNoLineOrIncompleteSnapshots() {
        val first = supplierPickingLine(line())
        val entries = mapOf(first.key to SupplierPickingEntry(first.signature, 4.0, 1000))
        assertEquals(listOf("order"), supplierPickingReadyOrderIds(listOf(bundle(), bundle(listOf(line("other", "second")), "second")), listOf("order", "second"), entries))
        assertTrue(supplierPickingReadyOrderIds(listOf(bundle(emptyList())), listOf("order"), entries).isEmpty())
        assertTrue(supplierPickingReadyOrderIds(listOf(bundle(listOf(line(), line()))), listOf("order"), entries).isEmpty())
        assertTrue(supplierPickingReadyOrderIds(listOf(bundle(listOf(line().copy(supplierAcceptedQuantity = null)))), listOf("order"), entries).isEmpty())
        assertTrue(supplierPickingReadyOrderIds(listOf(bundle(listOf(line().copy(isActive = false)))), listOf("order"), entries).isEmpty())
    }
    @Test fun declinedZeroQuantityLinesDoNotRequirePhysicalPicking() {
        val source = supplierPickingLine(line())
        val entries = mapOf(source.key to SupplierPickingEntry(source.signature, 4.0, 1000))
        assertEquals(listOf("order"), supplierPickingReadyOrderIds(listOf(bundle(listOf(line(), line("declined").copy(supplierAcceptedQuantity = unit.copy(total = 0.0))))), listOf("order"), entries))
        assertTrue(supplierPickingReadyOrderIds(listOf(bundle(listOf(line().copy(supplierAcceptedQuantity = unit.copy(total = 0.0))))), listOf("order"), entries).isEmpty())
    }
    @Test fun changedProductSubstituteOrQuantityInvalidatesPhysicalProgressButPriceDoesNot() {
        val source = supplierPickingLine(line()); val entry = SupplierPickingEntry(source.signature, 4.0, 1000)
        assertEquals(0.0, supplierPickedQuantity(supplierPickingLine(line().copy(substituteGoodsItemId = "other")), entry))
        assertEquals(0.0, supplierPickedQuantity(supplierPickingLine(line(qty = unit.copy(total = 5.0))), entry))
        assertEquals(0.0, supplierPickedQuantity(supplierPickingLine(line().copy(supplierAcceptedQuantity = unit.copy(id = "g"))), entry))
        assertEquals(4.0, supplierPickedQuantity(supplierPickingLine(line().copy(supplierComment = "New time")), entry))
    }
    @Test fun invalidQuantitiesNeverPersist() = runTest {
        val memory = Memory(); val ledger = SupplierPickingLedger(memory::read, memory::write)
        val source = supplierPickingLine(line())
        for (quantity in listOf(-1.0, 5.0, 1.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFails { ledger.update(owner(), listOf(source), mapOf(source.key to quantity)) }
        }
        assertTrue(memory.data.isEmpty())
        assertNull(supplierPickingLine(line(qty = unit.copy(total = 4.5))).quantity)
    }
    @Test fun failedReadBackDoesNotClaimSavedProgress() = runTest {
        val memory = Memory(); val ledger = SupplierPickingLedger(memory::read, memory::write) { 1000 }
        val source = supplierPickingLine(line())
        ledger.update(owner(), listOf(source), mapOf(source.key to 1.0)); memory.dropWrite = true
        assertFails { ledger.update(owner(), listOf(source), mapOf(source.key to 4.0)) }
        assertEquals(1.0, ledger.load(owner())[source.key]?.quantity)
    }
    @Test fun corruptOrForeignJournalIsNotOverwritten() = runTest {
        val memory = Memory(); val ledger = SupplierPickingLedger(memory::read, memory::write)
        val key = "supplier-picking-v2:account:supplier"; val source = supplierPickingLine(line())
        for (raw in listOf("broken", jsonBase.encodeToString(SupplierPickingJournal("foreign", "supplier")))) {
            memory.data[key] = raw
            assertFails { ledger.update(owner(), listOf(source), mapOf(source.key to 1.0)) }
            assertEquals(raw, memory.data[key])
        }
    }
    @Test fun queuedSaveNeverCrossesAccountGeneration() = runTest {
        val gate = CompletableDeferred<Unit>(); var current = true; var writes = 0
        val ledger = SupplierPickingLedger({ gate.await(); null }, { _, _ -> writes++ })
        val scope = SupplierPickingScope("account", "supplier") { current }
        val source = supplierPickingLine(line())
        val update = async { runCatching { ledger.update(scope, listOf(source), mapOf(source.key to 1.0)) } }
        current = false; gate.complete(Unit)
        assertTrue(update.await().isFailure); assertEquals(0, writes)
    }
    @Test fun concurrentRunChangesPreserveOtherRunProgress() = runTest {
        val memory = Memory(); val ledger = SupplierPickingLedger(memory::read, memory::write) { 1000 }
        val first = supplierPickingLine(line()); val second = supplierPickingLine(line("line2", "order2"))
        val one = async { ledger.update(owner(), listOf(first), mapOf(first.key to 1.0)) }
        val two = async { ledger.update(owner(), listOf(second), mapOf(second.key to 3.0)) }
        one.await(); two.await()
        assertEquals(2, ledger.load(owner()).size)
        ledger.update(owner(), listOf(first), mapOf(first.key to 0.0))
        assertEquals(setOf(second.key), ledger.load(owner()).keys)
    }
    @Test fun barcodeMatchingNeverUsesShorterPrefixAndSubstitutesReplaceOriginalCodes() {
        val source = supplierPickingLine(line())
        assertEquals(SupplierPickingScan.Missing, supplierPickingScan("4006381333931123", listOf(source)))
        assertEquals(SupplierPickingScan.Missing, supplierPickingScan("400638", listOf(source)))
        assertEquals(1.0, (supplierPickingScan("4006381333931", listOf(source)) as SupplierPickingScan.Matches).quantity)
        val substitute = supplierPickingLine(line().copy(substituteGoodsItemId = "new", substituteGoodsItemBarcodeSnapshots = listOf("NEW-123")))
        assertEquals(SupplierPickingScan.Missing, supplierPickingScan("4006381333931", listOf(substitute)))
        assertEquals(substitute, (supplierPickingScan("NEW-123", listOf(substitute)) as SupplierPickingScan.Matches).lines.single())
    }
    @Test fun duplicateBarcodeRequiresChoosingOrderLineInsteadOfGuessing() {
        val first = supplierPickingLine(line()); val second = supplierPickingLine(line("line2", "order2"))
        assertEquals(2, (supplierPickingScan("4006381333931", listOf(first, second)) as SupplierPickingScan.Matches).lines.size)
    }
    private fun scale(body: String): String {
        val sum = body.reversed().mapIndexed { index, c -> c.digitToInt() * if (index % 2 == 0) 3 else 1 }.sum()
        return body + ((10 - sum % 10) % 10)
    }
    @Test fun weightedBarcodeAddsActualWeightAndProductCodeAloneNeedsManualQuantity() {
        val source = supplierPickingLine(line(qty = unit.copy(id = "1", total = 3.0, roundTotal = false)).copy(goodsItemBarcodeSnapshots = listOf("2212345")))
        val result = supplierPickingScan(scale("221234501250"), listOf(source)) as SupplierPickingScan.Matches
        assertEquals(1.25, result.quantity)
        assertNull((supplierPickingScan("2212345", listOf(source)) as SupplierPickingScan.Matches).quantity)
        val grams = source.copy(quantity = source.quantity!!.copy(id = "g"))
        assertEquals(1250.0, (supplierPickingScan(scale("221234501250"), listOf(grams)) as SupplierPickingScan.Matches).quantity)
    }
    @Test fun sameWeightProductAcrossKilogramAndGramOrdersKeepsEachLineUnit() {
        val kilograms = supplierPickingLine(line(qty = unit.copy(id = "1", total = 3.0, roundTotal = false)).copy(goodsItemBarcodeSnapshots = listOf("2212345")))
        val grams = supplierPickingLine(line("grams", "order2", unit.copy(id = "g", total = 3000.0, roundTotal = false)).copy(goodsItemBarcodeSnapshots = listOf("2212345")))
        val result = supplierPickingScan(scale("221234500100"), listOf(kilograms, grams)) as SupplierPickingScan.Matches
        assertEquals(2, result.lines.size)
        assertEquals(0.1, result.quantityForLine(kilograms))
        assertEquals(100.0, result.quantityForLine(grams))
        assertNull(result.quantity)
    }
    @Test fun mixedOrdinaryAndWeightedLinesNeverAssumeTheSameIncrement() {
        val kilogram = supplierPickingLine(line(qty = unit.copy(id = "1", total = 3.0, roundTotal = false)).copy(goodsItemBarcodeSnapshots = listOf("2212345")))
        val piece = supplierPickingLine(line("piece", "order2").copy(goodsItemBarcodeSnapshots = listOf("2212345")))
        val result = supplierPickingScan("2212345", listOf(kilogram, piece)) as SupplierPickingScan.Matches
        assertNull(result.quantityForLine(kilogram))
        assertEquals(1.0, result.quantityForLine(piece))
    }
    @Test fun floatingPointAdditionAtAgreedBoundaryIsSavedCanonically() = runTest {
        val memory = Memory(); val ledger = SupplierPickingLedger(memory::read, memory::write) { 1000 }
        val source = supplierPickingLine(line(qty = unit.copy(id = "1", total = 0.3, roundTotal = false)))
        assertTrue(supplierPickingQuantityValid(source, 0.1 + 0.2))
        val result = ledger.update(owner(), listOf(source), mapOf(source.key to 0.1 + 0.2))
        assertEquals(0.3, result[source.key]?.quantity)
        assertTrue(supplierPickingComplete(source, result[source.key]))
        assertFalse(supplierPickingQuantityValid(source, 0.3001))
    }
    @Test fun wrappedOrMalformedScaleCodesDoNotBecomePhysicalScans() {
        val source = supplierPickingLine(line(qty = unit.copy(id = "1", total = 3.0, roundTotal = false)).copy(goodsItemBarcodeSnapshots = listOf("2212345")))
        val barcode = scale("221234501250")
        assertEquals(SupplierPickingScan.Missing, supplierPickingScan("ABC$barcode", listOf(source)))
        assertEquals(SupplierPickingScan.Missing, supplierPickingScan("$barcode-XYZ", listOf(source)))
        assertEquals(1.25, (supplierPickingScan("]E0$barcode", listOf(source)) as SupplierPickingScan.Matches).quantity)
    }
    @Test fun fullLegacyUpcAndItsLeadingZeroEanAliasAgreeWithoutGuessingShortPrefixes() {
        val barcode = scale("21234501250")
        val quantity = unit.copy(id = "1", total = 3.0, roundTotal = false)
        val upc = supplierPickingLine(line(qty = quantity).copy(goodsItemBarcodeSnapshots = listOf(barcode)))
        assertEquals(1.25, (supplierPickingScan("0$barcode", listOf(upc)) as SupplierPickingScan.Matches).quantity)
        val ean = supplierPickingLine(line(qty = quantity).copy(goodsItemBarcodeSnapshots = listOf("0$barcode")))
        assertEquals(1.25, (supplierPickingScan(barcode, listOf(ean)) as SupplierPickingScan.Matches).quantity)
        val short = supplierPickingLine(line(qty = quantity).copy(goodsItemBarcodeSnapshots = listOf("212345")))
        assertEquals(1.25, (supplierPickingScan(barcode, listOf(short)) as SupplierPickingScan.Matches).quantity)
        assertEquals(SupplierPickingScan.Missing, supplierPickingScan("0$barcode", listOf(short)))
    }
    @Test fun leadingZeroPluIsPreservedExactly() {
        val quantity = unit.copy(id = "1", total = 3.0, roundTotal = false)
        val row = supplierPickingLine(line(qty = quantity).copy(goodsItemBarcodeSnapshots = listOf("00012")))
        assertEquals(0.1, (supplierPickingScan(scale("020001200100"), listOf(row)) as SupplierPickingScan.Matches).quantity)
        assertEquals(SupplierPickingScan.Missing, supplierPickingScan(scale("020001200100"), listOf(row.copy(barcodes = listOf("12")))))
    }
    @Test fun ambiguousScaleLayoutNeverChoosesAnArbitraryWeight() {
        val body = scale("221234511250")
        val source = supplierPickingLine(line(qty = unit.copy(id = "1", roundTotal = false)).copy(goodsItemBarcodeSnapshots = listOf(body)))
        assertEquals(SupplierPickingScan.AmbiguousWeight, supplierPickingScan(body, listOf(source)))
        val bad = body.dropLast(1) + ((body.last().digitToInt() + 1) % 10)
        assertEquals(SupplierPickingScan.Missing, supplierPickingScan(bad, listOf(source)))
    }
}
