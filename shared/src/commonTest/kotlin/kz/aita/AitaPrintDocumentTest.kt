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
    @Test fun sessionHostnamesAreRemovedWithoutChangingAddresses() {
        assertEquals("", securitySessionDisplayIp("localhost"))
        assertEquals("127.0.0.1", securitySessionDisplayIp("localhost/127.0.0.1"))
        assertEquals("2001:db8::5", securitySessionDisplayIp("2001:db8::5"))
        assertEquals("192.0.2.4", securitySessionDisplayIp("192.0.2.4"))
    }
}
