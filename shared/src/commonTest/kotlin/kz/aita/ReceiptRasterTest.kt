package kz.aita

import kotlin.test.*

class ReceiptRasterTest {
    private fun layout(vararg lines: String) = layoutReceiptRasterLines(lines.toList()) { text, _ -> text.length * 12f }

    @Test fun multilingualTextIsNotTransliterated() {
        val text = "Әә Ғғ Ққ Ңң Өө Ұұ Үү Һһ Іі ₸"
        assertEquals(text, layout("AITA", text)[1].text)
        assertTrue(layout("AITA")[0].heading)
    }
    @Test fun controlCharactersCannotBecomePrinterCommands() {
        val line = layout("A\u001b@B\u001dV\u0000C\tD\u0085E\u00a0F").single().text
        assertEquals("A @B V C D E F", line)
        assertTrue(line.none { it.code < 32 || it.code in 127..159 })
    }
    @Test fun longNamesWrapWithinPrinterDots() {
        val lines = layout("Молоко растительное ".repeat(20))
        assertTrue(lines.size > 1)
        assertTrue(lines.all { it.text.length * 12 <= RECEIPT_RASTER_TEXT_WIDTH })
        assertEquals("Молоко растительное ".repeat(20).split(' ').filter { it.isNotBlank() }, lines.flatMap { it.text.split(' ') }.filter { it.isNotBlank() })
    }
    @Test fun separatorsDoNotLeaveASingleDashOnAnExtraLine() {
        assertEquals(1, layout("-".repeat(32)).size)
        assertEquals(31, layout("-".repeat(32)).single().text.length)
    }
    @Test fun wrappedIndentationIsPreserved() {
        val lines = layout("  " + "товар ".repeat(30))
        assertTrue(lines.all { it.text.startsWith("  ") })
    }
    @Test fun surrogatePairIsNotSplit() {
        val lines = layout("a".repeat(30) + "\uD83D\uDE80" + "b".repeat(30))
        assertTrue(lines.none { it.text.lastOrNull()?.isHighSurrogate() == true || it.text.firstOrNull()?.isLowSurrogate() == true })
    }
    @Test fun oversizedSingleGlyphFailsInsteadOfLooping() {
        assertFailsWith<IllegalArgumentException> { layoutReceiptRasterLines(listOf("x")) { _, _ -> 500f } }
    }
    @Test fun jobAndLineLimitsAreBounded() {
        assertFailsWith<IllegalArgumentException> { layoutReceiptRasterLines(List(4001) { "" }) { _, _ -> 0f } }
        assertFailsWith<IllegalArgumentException> { layoutReceiptRasterLines(listOf("x".repeat(250001))) { _, _ -> 0f } }
    }
    @Test fun rasterUsesMostSignificantBitForLeftPixelAndPadsWhite() {
        val encoder = ReceiptRasterEncoder()
        encoder.strip(9, 1, IntArray(9) { if (it == 0 || it == 8) 0xff000000.toInt() else 0xffffffff.toInt() })
        val bytes = encoder.finish().map { it.toInt() and 255 }
        assertEquals(listOf(0x1b,0x40,0x1c,0x2e,0x1b,0x61,0), bytes.take(7))
        assertEquals(listOf(0x1d,0x76,0x30,0,2,0,1,0,0x80,0x80), bytes.drop(7).take(10))
    }
    @Test fun transparentBlackCompositesOnWhite() {
        val encoder = ReceiptRasterEncoder()
        encoder.strip(8,1,IntArray(8))
        assertEquals(0, encoder.finish()[15].toInt())
    }
    @Test fun stripDimensionsAreValidated() {
        assertFailsWith<IllegalArgumentException> { ReceiptRasterEncoder().strip(385,1,IntArray(385)) }
        assertFailsWith<IllegalArgumentException> { ReceiptRasterEncoder().strip(1,65,IntArray(65)) }
        assertFailsWith<IllegalArgumentException> { ReceiptRasterEncoder().strip(8,1,IntArray(7)) }
    }
    @Test fun feedAndCutOccurAfterImageData() {
        val encoder=ReceiptRasterEncoder(); encoder.strip(8,1,IntArray(8))
        assertEquals(listOf(0x1b,0x64,3,0x1d,0x56,0x42,0),encoder.finish().takeLast(7).map { it.toInt() and 255 })
    }
    @Test fun largerJobsHaveABoundedDeadlineWithoutRetries() {
        assertTrue(receiptPrinterWriteTimeoutMillis(100000) > receiptPrinterWriteTimeoutMillis(1000))
        assertEquals(180000,receiptPrinterWriteTimeoutMillis(RECEIPT_RASTER_MAX_BYTES))
        assertFailsWith<IllegalArgumentException> { receiptPrinterWriteTimeoutMillis(0) }
        assertFailsWith<IllegalArgumentException> { receiptPrinterWriteTimeoutMillis(Int.MAX_VALUE) }
    }
}
