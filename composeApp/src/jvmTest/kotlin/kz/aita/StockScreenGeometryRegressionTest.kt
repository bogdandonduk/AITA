package kz.aita

import kotlin.test.*

/** Geometry mirrors the native DataGridView/custom-painted synthetic Windows fixture. */
class StockScreenGeometryRegressionTest {
    private fun w(text: String, x: Int, y: Int, width: Int = 90, height: Int = 20) =
        StockScreenWord(text, x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
    private fun frame(words: List<StockScreenWord>, source: StockScreenSource = StockScreenSource.UI_AUTOMATION) =
        StockScreenFrame("fixture", 1L, "Synthetic fixture", words, source, 1100, 700)
    private fun headers(y: Int = 10) = listOf(
        w("Штрихкод", 10, y, 150), w("Название", 210, y, 230), w("Количество", 510, y),
        w("Ед.", 620, y, 45), w("Цена", 710, y), w("Категория", 870, y, 130),
    )
    private fun product(barcode: String = "4601234567893", name: String = "Молоко 2,5%", y: Int = 55) = listOf(
        w(barcode, 10, y, 150), w(name, 210, y, 230), w("12,5", 510, y),
        w("kg", 620, y, 45), w("600,00", 710, y), w("Dairy", 870, y, 130),
    )

    @Test fun bilingualAccessibleHeaderCellsMapToSingleSemanticColumns() {
        val heads = listOf(w("Barcode / Штрихкод",10,10,150),w("Name / Название",210,10,230),
            w("Quantity / Количество",510,10,95),w("Unit / Ед.",620,10,70),
            w("Sale price",710,10,100),w("Category / Категория",870,10,170))
        val parsed=StockScreenRecognizer().read(frame(heads+product())).rows.single()
        assertEquals("Молоко 2,5%",parsed.name)
        assertEquals("4601234567893",parsed.barcode)
        assertEquals("12.5",parsed.quantity)
        assertEquals("kg",parsed.unit)
        assertEquals("600",parsed.sale)
        assertEquals("Dairy",parsed.category)
    }

    @Test fun unrecognizedColumnDoesNotLeakIntoKnownQuantityOrPrice() {
        val heads=listOf(w("Barcode",10,10,150),w("Name",210,10,200),w("Qty",460,10,60),
            w("Reserved",570,10,70),w("Price",760,10,100))
        val cells=listOf(w("0012345678905",10,50,150),w("Tea",210,50,200),w("12",460,50,60),
            w("999",570,50,70),w("200",760,50,100))
        val result=StockScreenRecognizer().read(frame(heads+cells))
        assertTrue(result.columns.any {it.field==ScreenStockField.IGNORE && it.title=="Reserved"})
        assertEquals("12",result.rows.single().quantity)
        assertEquals("200",result.rows.single().sale)
        assertFalse(result.rows.single().name.contains("999"))
    }

    @Test fun explicitIgnoreColumnKeepsUnrelatedNumericDataOutOfPrices() {
        val parser=StockScreenRecognizer()
        parser.useColumns(listOf(ScreenStockColumn(ScreenStockField.BARCODE,85f,"Code"),
            ScreenStockColumn(ScreenStockField.NAME,325f,"Item"),ScreenStockColumn(ScreenStockField.QUANTITY,555f,"Qty"),
            ScreenStockColumn(ScreenStockField.IGNORE,642f,"Hidden reservation"),ScreenStockColumn(ScreenStockField.SALE,755f,"Sale"),
            ScreenStockColumn(ScreenStockField.CATEGORY,935f,"Category")),1100)
        val data=product().map {if(it.text=="kg")it.copy(text="999") else it}
        val row=parser.read(frame(data)).rows.single()
        assertEquals("12.5",row.quantity);assertEquals("600",row.sale);assertEquals("",row.unit)
    }

    @Test fun wrappedOcrPriceHeadersKeepSaleAndSupplyDistinct() {
        val heads=listOf(w("Штрихкод",10,10,150),w("Название",210,10,230),
            w("Цена",710,10,90),w("Цена",870,10,90),w("продажи",710,32,90),w("поставки",870,32,90))
        val data=listOf(w("4601234567893",10,65,150),w("Молоко",210,65,230),w("600,00",710,65,90),w("450,00",870,65,90))
        val row=StockScreenRecognizer().read(frame(heads+data,StockScreenSource.OCR)).rows.single()
        assertEquals("600",row.sale)
        assertEquals("450",row.supply)
        assertFalse(row.name.contains("продажи"))
    }

    @Test fun wrappedProductNameContinuationIsPreservedWithoutCreatingAnotherProduct() {
        val first=product(name="Молоко",y=55)
        val continuation=w("ультрапастеризованное",210,78,230)
        val second=product(barcode="4601234567909",name="Кефир",y=115)
        val rows=StockScreenRecognizer().read(frame(headers()+first+continuation+second,StockScreenSource.OCR)).rows
        assertEquals(2,rows.size)
        assertEquals("Молоко ультрапастеризованное",rows[0].name)
        assertEquals("Кефир",rows[1].name)
        assertEquals("12.5",rows[0].quantity)
    }

    @Test fun separateNumericCellsNeverBecomeAThousandsGroupedPrice() {
        val parser=StockScreenRecognizer()
        parser.useColumns(listOf(ScreenStockColumn(ScreenStockField.BARCODE,85f,"Barcode"),
            ScreenStockColumn(ScreenStockField.NAME,325f,"Name"),ScreenStockColumn(ScreenStockField.SALE,765f,"Price")),1100)
        val data=listOf(w("4601234567893",10,55,150),w("Milk",210,55,230),w("1",710,55,10),w("250",770,55,30))
        val row=parser.read(frame(data,StockScreenSource.OCR)).rows.single()
        assertContains(row.warnings,"invalid_sale")
        assertNull(screenStockNumber(row.sale))
        assertNotEquals("1250",row.sale)
    }

    @Test fun nearbyOcrThousandsFragmentsPreserveTheFullPrice() {
        val parser=StockScreenRecognizer()
        parser.useColumns(listOf(ScreenStockColumn(ScreenStockField.BARCODE,85f,"Barcode"),
            ScreenStockColumn(ScreenStockField.NAME,325f,"Name"),ScreenStockColumn(ScreenStockField.SALE,765f,"Price")),1100)
        val data=listOf(w("4601234567893",10,55,150),w("Milk",210,55,230),w("1",710,55,10),w("250,50",724,55,55))
        val row=parser.read(frame(data,StockScreenSource.OCR)).rows.single()
        assertEquals("1250.5",row.sale)
        assertFalse("invalid_sale" in row.warnings)
    }

    @Test fun ambiguousThousandsOrDecimalPriceStillRequiresReviewWithCurrencySuffix() {
        val row=StockScreenRecognizer().read(frame(headers()+product().map {if(it.text=="600,00")it.copy(text="1,234 ₸") else it})).rows.single()
        assertContains(row.warnings,"ambiguous_number")
        assertEquals("KZT",row.currency)
        assertFalse(row.reviewed)
    }

    @Test fun overlappingScrollObservationsNeverSumQuantityOrDuplicateExistingRows() {
        val parser=StockScreenRecognizer()
        val first=parser.read(frame(headers()+product(y=55)+product("4601234567909","Кефир",95))).rows
        val next=parser.read(frame(product("4601234567909","Кефир",35)+product("4601234567916","Йогурт",75))).rows
        val merged=mergeScreenStockRows(first,next)
        assertEquals(3,merged.size)
        assertTrue(merged.all {it.quantity=="12.5"})
        assertEquals(2,merged.single {it.barcode=="4601234567909"}.observations)
    }

    @Test fun reviewedOrPartiallyImportedRowsStayFrozenAcrossLaterFrames() {
        val original=StockScreenRecognizer().read(frame(headers()+product())).rows.single()
        val incoming=original.copy(quantity="500",sale="1",name="Wrong later read",source="changed source")
        val frozen=listOf(original.copy(reviewed=true),original.copy(importStarted=true),original.copy(itemSaved=true),original.copy(complete=true))
        frozen.forEach {saved -> assertEquals(saved,mergeScreenStockRows(listOf(saved),listOf(incoming)).single())}
    }
}
