package kz.aita

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import kotlinx.coroutines.*

class StockFileImportTest {
    private fun csv(text: String) = runBlocking {
        readStockImportFile(StockImportFile("stock.csv", text.toByteArray()))
    }.tables.single()

    private fun xmlFile(
        sheet: String,
        strings: String = "",
        styles: String = "",
        extra: Map<String, String> = emptyMap(),
    ): ByteArray {
        val entries =
            linkedMapOf(
                "xl/workbook.xml" to
                    """<?xml version="1.0"?><workbook xmlns:r="urn:test"><sheets><sheet name="Остатки" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to
                    """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""",
                "xl/worksheets/sheet1.xml" to
                    "<worksheet><sheetData>$sheet</sheetData></worksheet>",
            )
        if (strings.isNotEmpty()) entries["xl/sharedStrings.xml"] = "<sst>$strings</sst>"
        if (styles.isNotEmpty()) entries["xl/styles.xml"] = styles
        entries.putAll(extra)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (path, data) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(data.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun xlsx(bytes: ByteArray) = runBlocking {
        readStockImportFile(StockImportFile("stock.xlsx", bytes))
    }

    private fun failure(code: String, block: () -> Unit) =
        assertEquals(code, assertFailsWith<StockFileProblem> { block() }.code)

    @Test
    fun quotedMultilineCyrillicAndLeadingZerosSurvive() {
        val rows =
            csv(
                    "\uFEFFШтрихкод;Название;Количество;Цена\r\n00012345;\"Чай; зелёный\n\"\"Премиум\"\"\";1,5;230,50\r\n"
                )
                .rows
        assertEquals(2, rows.size)
        assertEquals("00012345", rows[1][0])
        assertEquals("Чай; зелёный\n\"Премиум\"", rows[1][1])
        assertEquals("230,50", rows[1][3])
    }

    @Test
    fun tabsAndBareFinalCellAndEmptyTrailingColumns() {
        assertEquals(
            listOf(listOf("a", "b", ""), listOf("00001", "Milk", "")),
            runBlocking { parseStockDelimited("a\tb\t\n00001\tMilk\t", '\t') },
        )
        assertEquals(';', detectStockDelimiter("Name;Price\nTea;1,5"))
    }

    @Test
    fun malformedQuotesNeverImportPartialRows() {
        failure("file_quotes") { csv("Name,Price\nTea,1\n\"broken,2") }
        failure("file_quotes") { csv("Name,Price\n\"Tea\"oops,2") }
    }

    @Test
    fun sizeAndRowAndColumnLimitsRejectInsteadOfTruncate() {
        failure("file_limit") { csv("Name,Price\n" + (1..3001).joinToString("\n") { "Tea$it,1" }) }
        failure("file_limit") { csv((1..65).joinToString(",") { "field$it" }) }
        failure("file_limit") { csv("Name,Price\n" + "a".repeat(4001) + ",1") }
    }

    @Test
    fun legacyAndUnicodeEncodingsRequireCorrectInterpretation() {
        val text = "Штрихкод;Название\n00123;Молоко"
        assertEquals(
            text,
            decodeStockText(
                text.toByteArray(charset("windows-1251")),
                StockTextEncoding.WINDOWS1251,
            ),
        )
        failure("file_encoding") {
            decodeStockText(text.toByteArray(charset("windows-1251")), StockTextEncoding.UTF8)
        }
        assertEquals(
            text,
            decodeStockText(text.toByteArray(charset("UTF-16")), StockTextEncoding.UTF8),
        )
        assertEquals(
            text,
            decodeStockText(text.toByteArray(charset("UTF-16LE")), StockTextEncoding.UTF16LE),
        )
        assertEquals(
            "Чай 😀",
            decodeStockText("Чай 😀".toByteArray(charset("UTF-16LE")), StockTextEncoding.UTF16LE),
        )
        failure("file_encoding") {
            decodeStockText(byteArrayOf(0, 0xd8.toByte()), StockTextEncoding.UTF16LE)
        }
        failure("file_encoding") {
            decodeStockText(byteArrayOf(0, 0xdc.toByte()), StockTextEncoding.UTF16LE)
        }
    }

    @Test
    fun mappingRequiresExplicitUnitsAndUnambiguousNumbers() {
        val table =
            csv(
                "Name,Barcode,Quantity,Unit,Sale price,Supply price\nTea,00012345,1.250,kg,1200,900"
            )
        val row =
            stockFileRows(
                    table,
                    0,
                    table.rows.first().map(::stockImportHeaderField),
                    "KZT",
                    null,
                    false,
                )
                .single()
        assertContains(row.problems(globalAppConfigurationState.payloadValue), "QUANTITY")
        assertEquals("00012345", row.barcode)
        assertFalse(row.reviewed)
        val mapped =
            mergeScreenStockRows(
                    emptyList(),
                    listOf(
                        row.copy(
                            quantity = "1.25",
                            reviewed = true,
                            resolvedNumbers = setOf("QUANTITY"),
                        )
                    ),
                )
                .single()
        assertEquals(
            1.25,
            mapped
                .toStockBatch("user", "store", globalAppConfigurationState.payloadValue)
                ?.quantity
                ?.total,
        )
        assertEquals(
            "900",
            mapped
                .toStockItem("user", "store", "ru", globalAppConfigurationState.payloadValue)
                .supplyPrices
                .single()
                .price,
        )
    }

    @Test
    fun quantitiesAreNotAddedTwiceAcrossRepeatedFileReads() {
        val table =
            csv("Name,Barcode,Quantity,Unit,Sale price,Supply price\nTea,00012345,5,pcs,10,8")
        val incoming =
            stockFileRows(
                table,
                0,
                table.rows.first().map(::stockImportHeaderField),
                "KZT",
                null,
                false,
            )
        val first = mergeScreenStockRows(emptyList(), incoming)
        val repeated = mergeScreenStockRows(first, incoming)
        assertEquals(first.single().itemId, repeated.single().itemId)
        assertEquals("5", repeated.single().quantity)
        assertEquals(1, repeated.size)
    }

    @Test
    fun duplicateColumnMappingsRejectedAndMissingCostsNotInvented() {
        val table = csv("Name,Price\nTea,10")
        failure("file_mapping") {
            stockFileRows(
                table,
                0,
                listOf(ScreenStockField.NAME, ScreenStockField.NAME),
                "KZT",
                null,
                false,
            )
        }
        assertEquals(
            "",
            stockFileRows(
                    table,
                    0,
                    listOf(ScreenStockField.NAME, ScreenStockField.SALE),
                    "KZT",
                    null,
                    false,
                )
                .single()
                .supply,
        )
        assertEquals(
            "10",
            stockFileRows(
                    table,
                    0,
                    listOf(ScreenStockField.NAME, ScreenStockField.SALE),
                    "KZT",
                    null,
                    true,
                )
                .single()
                .supply,
        )
    }

    @Test
    fun perRowCurrencyIsPreservedAndConflictingPriceCurrenciesNeedReview() {
        val table =
            csv(
                "Name;Barcode;Sale price;Supply price;Currency;Unit\nTea;00012345;10;8;USD;pcs\nRice;00023456;10 USD;8 KZT;USD;kg\nMilk;00034567;10;8;unknown;pcs"
            )
        val rows =
            stockFileRows(
                table,
                0,
                table.rows.first().map(::stockImportHeaderField),
                "KZT",
                null,
                false,
            )
        assertEquals("USD", rows[0].currency)
        assertEquals("", rows[1].currency)
        assertContains(rows[1].warnings, "currency_conflict")
        assertContains(rows[1].problems(globalAppConfigurationState.payloadValue), "CURRENCY")
        assertContains(
            rows[1].copy(currency = "USD").problems(globalAppConfigurationState.payloadValue),
            "SUPPLY",
        )
        assertContains(rows[2].problems(globalAppConfigurationState.payloadValue), "CURRENCY")
    }

    @Test
    fun currencyNamesAreWholeTokensAndMixedCurrenciesAreNeverStrippedIntoOnePrice() {
        assertEquals("", stockExplicitCurrency("USDT"))
        assertEquals("", stockExplicitCurrency("notKZT"))
        assertEquals("TJS", stockExplicitCurrency("сомонӣ"))
        assertEquals(setOf("TJS"), screenStockCurrencies("15 сомонӣ"))
        assertEquals(setOf("USD", "KZT"), screenStockCurrencies("10 USD KZT"))
        assertNull(screenStockNumber("10 USD KZT"))
        assertEquals("15", screenStockNumber("15 руб."))
    }

    @Test
    fun xlsxReadsSharedInlineRichStringsAndBarcodeZeroPadding() {
        val data =
            xmlFile(
                """<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="inlineStr"><is><t>Название</t></is></c></row>
            <row r="2"><c r="A2" s="0"><v>12345</v></c><c r="B2" t="s"><v>1</v></c></row>""",
                "<si><t>Barcode</t></si><si><r><t>Чай </t></r><r><t>&amp; кофе</t></r></si>",
                """<styleSheet><numFmts><numFmt numFmtId="164" formatCode="0000000000000"/></numFmts><cellXfs><xf numFmtId="164"/></cellXfs></styleSheet>""",
            )
        val rows = xlsx(data).tables.single().rows
        assertEquals("0000000012345", rows[1][0])
        assertEquals("Чай & кофе", rows[1][1])
        assertEquals("Остатки", xlsx(data).tables.single().name)
    }

    @Test
    fun xlsxPreservesSparseCellsAndRejectsFormulasPrecisionAndExternalContent() {
        assertEquals(
            listOf("", "", "Tea"),
            xlsx(xmlFile("""<row><c r="C1" t="inlineStr"><is><t>Tea</t></is></c></row>"""))
                .tables
                .single()
                .rows
                .single(),
        )
        failure("file_formula") {
            xlsx(xmlFile("""<row><c r="A1"><f>SUM(A1:A2)</f><v>10</v></c></row>"""))
        }
        failure("file_numeric") {
            xlsx(xmlFile("""<row><c r="A1"><v>1234567890123456</v></c></row>"""))
        }
        failure("file_numeric") { xlsx(xmlFile("""<row><c r="A1"><v>1.2E+12</v></c></row>""")) }
        failure("file_external") {
            xlsx(
                xmlFile(
                    "",
                    extra =
                        mapOf(
                            "xl/_rels/workbook.xml.rels" to
                                """<Relationships><Relationship Id="rId1" TargetMode="External" Target="https://example.invalid/private"/></Relationships>"""
                        ),
                )
            )
        }
        failure("file_format") {
            xlsx(xmlFile("<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///etc/passwd'>]>"))
        }
    }

    @Test
    fun zipBombHeaderAndTruncatedFilesAreRejectedBeforeInflation() {
        val data = xmlFile("<row><c r=\"A1\"><v>1</v></c></row>")
        failure("file_format") { xlsx(data.copyOf(data.size - 3)) }
        val directory =
            (0 until data.size - 4).first {
                data[it] == 0x50.toByte() &&
                    data[it + 1] == 0x4b.toByte() &&
                    data[it + 2] == 1.toByte() &&
                    data[it + 3] == 2.toByte()
            }
        data[directory + 24] = 0
        data[directory + 25] = 0
        data[directory + 26] = 0
        data[directory + 27] = 0x40
        failure("file_limit") { xlsx(data) }
    }

    @Test
    fun workbookEscapedTextIsDecodedOnceAndMalformedXmlDoesNotBecomeAnItem() {
        val text =
            "<row><c r=\"A1\" t=\"inlineStr\"><is><t>&amp;lt; &#x1F600; &quot;Tea&quot;</t></is></c></row>"
        assertEquals("&lt; 😀 \"Tea\"", xlsx(xmlFile(text)).tables.single().rows.single().single())
        for (value in listOf("&unknown;", "&bad", "&#0;", "&#xD800;", "&#1;")) {
            failure("file_format") {
                xlsx(xmlFile("<row><c r=\"A1\" t=\"inlineStr\"><is><t>$value</t></is></c></row>"))
            }
        }
        val extra =
            mapOf(
                "xl/workbook.xml" to
                    """<workbook xmlns:r="urn:test"><sheets><sheet name="A > B" r:id="rId1"/></sheets></workbook>"""
            )
        assertEquals("A > B", xlsx(xmlFile(text, extra = extra)).tables.single().name)
    }

    @Test
    fun malformedWorkbookFuzzNeverEscapesAsAnotherFailureOrLargeAllocation() {
        val random = kotlin.random.Random(36)
        repeat(150) {
            val data = xmlFile("<row><c r=\"A1\"><v>1</v></c></row>")
            repeat(3) { data[random.nextInt(data.size)] = random.nextInt(256).toByte() }
            try {
                xlsx(data)
            } catch (e: Exception) {
                assertTrue(e is StockFileProblem, "unexpected ${e::class}: ${e.message}")
            }
        }
    }

    @Test
    fun largeLocalImportYieldsAndCanBeCancelledBeforeProducingRows() = runBlocking {
        val source = "Name,Price\n" + (1..2500).joinToString("\n") { "${"Tea".repeat(80)}$it,100" }
        var completed = false
        val parsing =
            launch(start = CoroutineStart.UNDISPATCHED) {
                parseStockDelimited(source, ',')
                completed = true
            }
        parsing.cancelAndJoin()
        assertFalse(completed, "A large import must yield to cancellation and the UI event loop")
    }
}
