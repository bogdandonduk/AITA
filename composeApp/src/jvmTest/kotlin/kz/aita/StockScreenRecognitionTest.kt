package kz.aita

import kotlin.test.*

class StockScreenRecognitionTest {
    private fun word(text: String, x: Int, y: Int, w: Int = 80, confidence: Float = 1f) =
        StockScreenWord(text, x.toFloat(), y.toFloat(), w.toFloat(), 20f, confidence)
    private fun table(source: StockScreenSource = StockScreenSource.UI_AUTOMATION) = StockScreenFrame("frame", 1, "Stock", listOf(
        word("Штрихкод", 10, 10, 140), word("Наименование", 180, 10, 250), word("Остаток", 460, 10),
        word("Ед. изм.", 560, 10), word("Цена продажи", 680, 10, 110), word("Цена поставки", 830, 10, 110),
        word("0001234567890", 10, 45, 140), word("Молоко 2,5%", 180, 45, 250), word("2,5", 460, 45),
        word("кг", 560, 45), word("1 250,50 ₸", 680, 45, 110), word("850,25", 830, 45, 110),
        word("Итого", 180, 80, 250), word("2,5", 460, 80)
    ), source, 1000, 500)

    @Test fun readsColumnsWithoutConvertingBarcodesOrSummingQuantities() {
        val parser = StockScreenRecognizer()
        val rows = parser.read(table()).rows
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("0001234567890", row.barcode)
        assertEquals("Молоко 2,5%", row.name)
        assertEquals("2.5", row.quantity)
        assertEquals("kg", row.unit)
        assertEquals("1250.5", row.sale)
        assertEquals("850.25", row.supply)
        assertEquals("KZT", row.currency)
        val repeated = mergeScreenStockRows(rows, parser.read(table()).rows).single()
        assertEquals("2.5", repeated.quantity)
        assertEquals(2, repeated.observations)
    }

    @Test fun keepsHeaderMappingWhileScrollingButClearsItWhenWindowResizes() {
        val parser = StockScreenRecognizer()
        parser.read(table())
        val next = table().copy(words = table().words.filter { it.y > 20 })
        assertEquals("1250.5", parser.read(next).rows.single().sale)
        assertTrue(parser.read(next.copy(width = 1200)).columns.isEmpty())
    }

    @Test fun preservesGramsAndRequiresReviewForOcr() {
        val frame = table(StockScreenSource.OCR).copy(words = table().words.map { if(it.text == "кг") it.copy(text="г") else it })
        val row = StockScreenRecognizer().read(frame).rows.single()
        assertEquals("g", row.unit)
        assertContains(row.warnings, "check_ocr")
    }

    @Test fun rejectsAmbiguousNumbersAndDoesNotRepairOcrCharacters() {
        assertNull(screenStockNumber("1O0"))
        assertNull(screenStockNumber("12 50"))
        assertNull(screenStockNumber("-1"))
        assertNull(screenStockNumber("NaN"))
        assertNull(screenStockNumber("1e4"))
        assertEquals("1250.5", screenStockNumber("1.250,50"))
        assertEquals("1250.5", screenStockNumber("1,250.50"))
        assertEquals("0", screenStockNumber("000,00"))
        assertEquals("1.234", screenStockNumber("1,234"))
        assertEquals("00O12345", screenStockBarcode("00O12345")) // review, never rewrite O to 0
        assertNull(screenStockBarcode("00123 456"))
    }

    @Test fun conflictingObservationsAreFlaggedNotAdded() {
        val original = StockScreenRecognizer().read(table()).rows.single()
        val merged = mergeScreenStockRows(listOf(original), listOf(original.copy(quantity="50", sale="1"))).single()
        assertEquals("2.5", merged.quantity)
        assertEquals("1250.5", merged.sale)
        assertContains(merged.warnings, "conflicting_readings")
        val reviewed = merged.copy(reviewed=true)
        assertEquals(reviewed, mergeScreenStockRows(listOf(reviewed), listOf(original.copy(quantity="9"))).single())
    }

    @Test fun detailLabelsWorkWithoutTableHeaders() {
        val frame = table().copy(words=listOf(word("Название: Яблоки",10,10,400),word("Штрихкод: 0460123456789",10,40,400),
            word("Остаток: 12,4",10,70,400),word("Единица: кг",10,100,400),word("Цена: 500",10,130,400)))
        val row = StockScreenRecognizer().read(frame).rows.single()
        assertEquals("Яблоки",row.name)
        assertEquals("0460123456789",row.barcode)
        assertEquals("12.4",row.quantity)
    }

    @Test fun croppedAndNonFiniteGeometryNeverBecomesStock() {
        val frame=table().copy(words=table().words.map { it.copy(x=Float.NaN) })
        assertTrue(StockScreenRecognizer().read(frame).rows.isEmpty())
        assertTrue(StockScreenRecognizer().read(table().copy(height=30)).rows.isEmpty())
    }

    @Test fun manualColumnMappingSupportsUnknownHeaders() {
        val parser=StockScreenRecognizer()
        parser.useColumns(listOf(ScreenStockColumn(ScreenStockField.BARCODE,80f,"A"),ScreenStockColumn(ScreenStockField.NAME,305f,"B"),
            ScreenStockColumn(ScreenStockField.QUANTITY,500f,"C"),ScreenStockColumn(ScreenStockField.UNIT,600f,"D"),
            ScreenStockColumn(ScreenStockField.SALE,735f,"E"),ScreenStockColumn(ScreenStockField.SUPPLY,885f,"F")),1000)
        assertEquals("1250.5",parser.read(table().copy(words=table().words.filter { it.y>20 })).rows.single().sale)
    }

    @Test fun duplicatesWithoutBarcodeRequireSameSourceAndNeverCollapseByNameAlone() {
        val a=ScreenStockRow("a",name="Milk",source="Milk 100",sale="100")
        val b=a.copy(id="b",source="Milk 200",sale="200")
        assertEquals(2,mergeScreenStockRows(listOf(a),listOf(b)).size)
        assertEquals(1,mergeScreenStockRows(listOf(a),listOf(a)).size)
    }

    @Test fun boundedDraftDoesNotGrowBeyondLimit() {
        val rows=(1..10).map {ScreenStockRow("$it",name="Item $it",barcode="100$it")}
        assertEquals(3,mergeScreenStockRows(emptyList(),rows,limit=3).size)
    }
}
