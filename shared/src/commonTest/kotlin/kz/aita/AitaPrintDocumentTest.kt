package kz.aita

import kotlin.test.*

class AitaPrintDocumentTest {
    @Test fun receiptHtmlKeepsUnicodeAndEscapesUserContent() {
        val document = AitaPdfDocument(listOf(AitaPdfBlock("<script>alert('x')</script> 125 ₸ Қазақша")))
        val html = document.toPrintHtml("<img src=x onerror=bad>")
        assertContains(html, "125 ₸ Қазақша")
        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("<img"))
        assertContains(html, "&lt;script&gt;")
        assertContains(html, "size:226.0pt 842.0pt")
    }
    @Test fun receiptBarcodeIsVectorAndNeverShrunkToFit() {
        val payload = transactionReceiptBarcodePayload("12345678-1234-1234-1234-123456789abc")!!
        val html = AitaPdfDocument(listOf(AitaPdfBlock("", barcodePayload = payload))).toPrintHtml("Receipt")
        val geometry = transactionReceiptBarcodeGeometry(payload, .72f, 36f,
            vertical = transactionReceiptBarcodeModules(payload).size * .72f > 202f)
        assertContains(html, "width=\"${geometry.width}pt\"")
        assertContains(html, "height=\"${geometry.height}pt\"")
        assertContains(html, "<svg")
        assertFalse(html.contains("<script"))
    }
    @Test fun reportKeepsA4DimensionsAndSemanticHeading() {
        val html = AitaPdfDocument(listOf(AitaPdfBlock("Analytics", AitaPdfRole.Title)),
            width = 595f, maxHeight = 842f, minHeight = 842f, margin = 42f, bodySize = 10f).toPrintHtml("Report")
        assertContains(html, "size:595.0pt 842.0pt;margin:42.0pt")
        assertContains(html, "font-weight:700;text-align:center")
    }
    @Test fun newMessagesHaveAllSixLanguages() {
        listOf("device.workflow.discovery_unavailable", "device.workflow.report_failed", "device.workflow.system_print_help",
            "message.stock_is_visible_but_could_not_be_saved_on_this_device").forEach { key ->
            listOf("en","ru","kk","ky","tg","uz").forEach { lang ->
                assertFalse(EventMessages.renderExact(EventMessageReference(key), lang).isNullOrBlank(), "$key $lang")
            }
        }
    }
    @Test fun rollPreferenceOnlyChangesPreparedPrintCopyAndKeepsBarcodeScale() {
        val original = AitaPdfDocument(listOf(AitaPdfBlock("Receipt")))
        for (width in listOf(58, 80)) {
            val printCopy = original.forReceiptPaper(width)
            assertEquals(width * 72f / 25.4f, printCopy.width)
            assertEquals(0f, printCopy.minHeight)
            assertEquals((if (width == 58) 48f else 72f) * 72f / 25.4f,
                printCopy.width - 2 * printCopy.margin, .001f)
            assertContains(printCopy.toPrintHtml("Receipt"), "aita-receipt-paper")
        }
        assertEquals(226f, original.width)
        assertEquals(80, normalizedReceiptPaperWidthMm(10))
        assertEquals(80, normalizedReceiptPaperWidthMm(null))
        assertEquals(58, normalizedReceiptPaperWidthMm(58))
    }
    @Test fun directPrinterSelectionNeverSilentlySwitchesToDriverPrinting() {
        val oldHtml = preferHtmlDocumentPrinting
        val oldReceipt = configuredReceiptPrinterDeviceIdState.value
        val oldLabel = configuredLabelPrinterDeviceIdState.value
        try {
            preferHtmlDocumentPrinting = false
            configuredReceiptPrinterDeviceIdState.value = "print-service:XP-58"
            configuredLabelPrinterDeviceIdState.value = "service:TSPL Printer"
            assertFalse(receiptUsesSystemDocumentPrinting())
            assertFalse(labelUsesSystemDocumentPrinting())
            configuredReceiptPrinterDeviceIdState.value = SYSTEM_DOCUMENT_PRINTER_ID
            configuredLabelPrinterDeviceIdState.value = SYSTEM_DOCUMENT_PRINTER_ID
            assertTrue(receiptUsesSystemDocumentPrinting())
            assertTrue(labelUsesSystemDocumentPrinting())
        } finally {
            preferHtmlDocumentPrinting = oldHtml
            configuredReceiptPrinterDeviceIdState.value = oldReceipt
            configuredLabelPrinterDeviceIdState.value = oldLabel
        }
    }
    @Test fun printerGuidanceHasAllSixLanguages() {
        listOf("raw_receipt_help", "desktop_system_print", "receipt_paper", "receipt_system_help", "receipt_driver_help",
            "label_system_help", "print_busy", "print_cancelled", "print_queued", "driver_print_failed", "paper_save_failed").forEach { key ->
            listOf("en", "ru", "kk", "ky", "tg", "uz").forEach { language ->
                assertFalse(deviceWorkflowText(key, language).isBlank(), "$key $language")
            }
        }
    }
    @Test fun sessionHostnamesAreRemovedWithoutChangingAddresses() {
        assertEquals("", securitySessionDisplayIp("localhost"))
        assertEquals("127.0.0.1", securitySessionDisplayIp("localhost/127.0.0.1"))
        assertEquals("2001:db8::5", securitySessionDisplayIp("2001:db8::5"))
        assertEquals("192.0.2.4", securitySessionDisplayIp("192.0.2.4"))
    }
}
