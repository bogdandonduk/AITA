package kz.aita

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

class ReceiptPrinterSetupTest {
    @Test fun forgettingBrowserPrinterPersistsAcrossReloadAndDoesNotClearOtherSettings() = runBlocking<Unit> {
        val oldDriver = getSqlDelightDriver
        val oldName = browserReceiptPrinterNameState.value
        val oldEnabled = browserReceiptPrinterEnabledState.value
        val oldId = configuredReceiptPrinterDeviceIdState.value
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver).await()
        setAppDatabaseForTests(AppDatabase(driver)); getSqlDelightDriver = { driver }
        try {
            putLocalKv("receipt-paper-width-mm", "58")
            saveBrowserReceiptPrinterName("  AP58\n  ")
            assertEquals("AP58", browserReceiptPrinterNameState.value)
            setBrowserReceiptPrinterEnabled(false)
            browserReceiptPrinterEnabledState.value = true
            configuredReceiptPrinterDeviceIdState.value = "browser-system-print"
            loadBrowserReceiptPrinterName()
            assertFalse(browserReceiptPrinterEnabledState.value)
            assertNull(configuredReceiptPrinterDeviceIdState.value)
            assertNull(browserReceiptPrinterNameState.value)
            assertEquals("58", getLocalKv("receipt-paper-width-mm"))
            setBrowserReceiptPrinterEnabled(true)
            assertEquals("browser-system-print", configuredReceiptPrinterDeviceIdState.value)
        } finally {
            browserReceiptPrinterNameState.value = oldName
            browserReceiptPrinterEnabledState.value = oldEnabled
            configuredReceiptPrinterDeviceIdState.value = oldId
            getSqlDelightDriver = oldDriver; setAppDatabaseForTests(null); driver.close()
        }
    }

    @Test fun brandedTestSlipFitsOneShortPageInEverySupportedLanguageAndRollWidth() {
        for (language in listOf("en", "ru", "kk", "ky", "tg", "uz")) for (width in listOf(58, 80)) {
            val document = receiptPrinterTestDocument("AITA · Printer test", "19.09.2026 15:00", language).forReceiptPaper(width)
            val pages = SystemReceiptPages(document)
            assertEquals(1, pages.numberOfPages, "$language $width mm")
            assertTrue(pages.getPageFormat(0).height in 72.0..396.0)
            assertTrue(document.blocks.any { it.text == "aita.kz" })
            assertTrue(document.blocks.none { it.barcodePayload != null })
            val html = document.toPrintHtml("AITA printer test")
            assertTrue(html.contains("100.00 ₸"))
            assertTrue(html.contains("-webkit-text-fill-color:black"))
            // Retain the actual generated document for browser/PDF regression evidence.
            File("build/receipt-print-fixtures").apply { mkdirs() }
                .resolve("test-$language-$width.html").writeText(html)
        }
    }

    @Test fun saleReceiptKeepsItemsTotalsAndBarcodeWithinBoundedRollPages() {
        for (width in listOf(58, 80)) for (count in listOf(3, 80)) {
            val receipt = TransactionReceiptSnapshotDataModel(
                TransactionDataModel("9a765abc-1234-4567-8901-123456789abc", 1L, "purchase", "store-a",
                    emptyList(), count * 250.0, 0.0, 0, timeMillis = 1_700_000_000_000L),
                null, List(count) { index -> TransactionReceiptLineDataModel(index, "item-$index",
                    listOf(LocalizedStringDataModel("en", "Honey / Мёд / Бал ${index + 1}")), "4006381333931",
                    QuantityDataModel("0", listOf(LocalizedStringDataModel("en", "pc.")), total = 1.0, roundTotal = true),
                    250.0, "KZT", "₸") },
                TransactionPaymentDraftDataModel(0, 0, "cash", count * 250.0, 0.0, 0), "KZT", "₸")
            val document = receipt.buildReceiptPdfDocument("en", ReceiptTextLabelsDataModel()).forReceiptPaper(width)
            val pages = SystemReceiptPages(document)
            assertTrue(pages.numberOfPages in 1..20)
            for (page in 0 until pages.numberOfPages) assertTrue(pages.getPageFormat(page).height <= 843)
            assertEquals(1, document.blocks.count { it.barcodePayload != null })
            assertTrue(document.blocks.any { it.text.contains("Total:") && it.text.contains("₸") })
            File("build/receipt-print-fixtures").apply { mkdirs() }
                .resolve("sale-$count-$width.html").writeText(document.toPrintHtml("AITA sale receipt"))
        }
    }
}
