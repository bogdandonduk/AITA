package kz.aita

import kotlin.test.*

class AitaPdfDocumentTest {
    private fun layout(document: AitaPdfDocument) = layoutAitaPdfDocument(document,
        { text, style -> pdfTextClusterEnds(text).size * style.size / 2 },
        { style -> -style.size to style.size * 0.25f })

    @Test fun headersAreSemanticAndLanguageIndependent() {
        for (title in listOf("Goods receipt", "Товарный чек", "Тауар чегі")) {
            val document = AitaPdfDocument(listOf(AitaPdfBlock("ТОО Happy Kids", AitaPdfRole.Store),
                AitaPdfBlock("Астана, Мәңгілік Ел", AitaPdfRole.Address), AitaPdfBlock(title, AitaPdfRole.Title), AitaPdfBlock("Total: 6600 ₸")))
            val lines = layout(document).single().lines
            lines.take(3).forEach {
                assertTrue(it.style.bold); assertTrue(it.style.centered); assertTrue(it.style.size > document.bodySize)
                val width = pdfTextClusterEnds(it.text).size * it.style.size / 2
                assertEquals(document.width / 2, it.x + width / 2, 0.001f)
            }
            assertEquals(document.margin, lines.last().x); assertFalse(lines.last().style.bold)
        }
    }
    @Test fun measuredWrapPreservesUnicodeAndMoney() {
        val text = "Әә Ғғ Ққ Ңң Өө Ұұ Үү Һһ Іі 6600.00 ₸"
        val wrapped = wrapAitaPdfText(text, 45f) { it.length * 5f }
        assertEquals(text, wrapped.joinToString(" "))
        assertTrue(wrapped.all { it.length * 5 <= 45 })
    }
    @Test fun unbrokenIdentifiersWrapWithoutLostCharacters() {
        val id = "f688c9d9-5395-4413-94ae-b3fbcdfc7854".repeat(4)
        assertEquals(id, wrapAitaPdfText(id, 35f) { it.length * 5f }.joinToString(""))
    }
    @Test fun surrogatePairsStayTogether() {
        val text = "A\uD83D\uDE42B\uD83D\uDE42C"
        val wrapped = wrapAitaPdfText(text, 10f) { pdfTextClusterEnds(it).size * 10f }
        assertEquals(listOf("A", "\uD83D\uDE42", "B", "\uD83D\uDE42", "C"), wrapped)
    }
    @Test fun combiningMarksStayWithTheirBase() {
        assertEquals(listOf("и\u0306", "о\u0301"), wrapAitaPdfText("и\u0306о\u0301", 10f) { pdfTextClusterEnds(it).size * 10f })
    }
    @Test fun oversizedGlyphFailsRatherThanClips() {
        assertFailsWith<IllegalArgumentException> { wrapAitaPdfText("Ж", 5f) { 20f } }
    }
    @Test fun paginationKeepsAllRowsInsideMargins() {
        val document = AitaPdfDocument((1..240).map { AitaPdfBlock("Position $it — 10 ₸") }, maxHeight=240f, minHeight=180f)
        val pages = layout(document)
        assertTrue(pages.size > 1)
        assertEquals(document.blocks.map { it.text }, pages.flatMap { it.lines }.map { it.text })
        pages.forEach { page ->
            assertTrue(page.height <= document.maxHeight)
            page.lines.forEach { assertTrue(it.baseline + it.style.size * .25f <= page.height - document.margin) }
        }
    }
    @Test fun dividerIsBoundedVectorRuleNotWrappedHyphens() {
        val line = layout(AitaPdfDocument(listOf(AitaPdfBlock("-".repeat(100), AitaPdfRole.Divider)))).single().lines.single()
        assertTrue(line.divider); assertEquals("", line.text)
    }
    @Test fun emptyDocumentStillHasOneValidPage() {
        val page = layout(AitaPdfDocument(emptyList())).single()
        assertEquals(280f, page.height); assertTrue(page.lines.isEmpty())
    }
    @Test fun lineEndingsAndControlsAreNormalizedWithoutAsciiReplacement() {
        val texts = layout(AitaPdfDocument(listOf(AitaPdfBlock("Ә\r\nҚ\r₸\u0000\nІ")))).single().lines.map { it.text }
        assertEquals(listOf("Ә", "Қ", "₸", "І"), texts)
    }
    @Test fun blankLinesRetainVerticalSpace() {
        val lines = layout(AitaPdfDocument(listOf(AitaPdfBlock("A\n\nB")))).single().lines
        assertEquals(listOf("A", "", "B"), lines.map { it.text }); assertTrue(lines[2].baseline > lines[1].baseline)
    }
    @Test fun invalidGeometryIsRejected() {
        for (document in listOf(AitaPdfDocument(emptyList(), width=Float.NaN), AitaPdfDocument(emptyList(), margin=200f), AitaPdfDocument(emptyList(), bodySize=0f), AitaPdfDocument(emptyList(), maxHeight=100f))) {
            assertFailsWith<IllegalArgumentException> { layout(document) }
        }
    }
    @Test fun metricsTooTallForPageAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            layoutAitaPdfDocument(AitaPdfDocument(listOf(AitaPdfBlock("a"))), { _, _ -> 1f }, { -1000f to 1000f })
        }
    }
    @Test fun unreasonableDocumentSizeIsRejectedBeforeMeasurement() {
        assertFailsWith<IllegalArgumentException> { layout(AitaPdfDocument(listOf(AitaPdfBlock("a".repeat(2_000_001))))) }
    }
    @Test fun safeFileNamesDoNotEscapeTheirFolder() {
        assertEquals("Тауар чегі ₸.pdf", safeReceiptPdfFileName("Тауар чегі ₸.Pdf"))
        assertEquals("receipt.pdf", safeReceiptPdfFileName("..."))
        assertFalse(safeReceiptPdfFileName("../../чек\n.pdf").contains('/'))
        assertFalse(safeReceiptPdfFileName("C:\\file.pdf").contains('\\'))
        assertTrue(safeReceiptPdfFileName("a".repeat(200)).length <= 100)
    }
}
