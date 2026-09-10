package kz.aita

import java.util.Locale
import java.util.zip.InflaterInputStream
import kotlin.test.*

class AitaPdfJvmTest {
    private fun document() = AitaPdfDocument(listOf(
        AitaPdfBlock("ТОО Happy Kids", AitaPdfRole.Store), AitaPdfBlock("Қазақстан, Астана", AitaPdfRole.Address),
        AitaPdfBlock("Товарный чек", AitaPdfRole.Title), AitaPdfBlock("Әә Ғғ Ққ Ңң Өө Ұұ Үү Һһ Іі — 6600.00 ₸")))
    private fun streams(bytes: ByteArray): List<String> {
        val raw = bytes.toString(Charsets.ISO_8859_1)
        return Regex("<< /Length (\\d+) /Filter /FlateDecode >>\\nstream\\n").findAll(raw).map { match ->
            val start = match.range.last + 1
            InflaterInputStream(bytes.copyOfRange(start, start + match.groupValues[1].toInt()).inputStream()).use { it.readBytes().toString(Charsets.US_ASCII) }
        }.toList()
    }
    @Test fun originalKazakhAndTengeHaveToUnicodeMappings() {
        val cmaps = streams(renderAitaPdfDocument(document())).filter { "begincmap" in it }.joinToString("\n")
        for (char in "ӘәҒғҚқҢңӨөҰұҮүҺһІі₸") assertTrue(String.format(Locale.ROOT, "<%04X>", char.code) in cmaps, "Missing mapping for $char")
        assertFalse("<003F>" in cmaps, "No source character should be replaced with a question mark")
    }
    @Test fun fileHasConsistentObjectOffsetsAndTrailer() {
        val raw = renderAitaPdfDocument(document()).toString(Charsets.ISO_8859_1)
        assertTrue(raw.startsWith("%PDF-1.4\n")); assertTrue(raw.endsWith("%%EOF\n"))
        val offset = Regex("startxref\\n(\\d+)").find(raw)!!.groupValues[1].toInt()
        assertTrue(raw.substring(offset).startsWith("xref\n"))
        val xref = raw.substring(offset).lines()
        val count = xref[1].substringAfter(' ').toInt()
        for (id in 1 until count) {
            val objectOffset = xref[id + 2].take(10).toInt()
            assertTrue(raw.substring(objectOffset).startsWith("$id 0 obj\n"))
        }
    }
    @Test fun pdfNumbersAreIndependentOfDeviceLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US); val english = renderAitaPdfDocument(document())
            Locale.setDefault(Locale.GERMANY); val german = renderAitaPdfDocument(document())
            assertContentEquals(english, german)
        } finally { Locale.setDefault(old) }
    }
    @Test fun longReceiptHasSeveralPagesAndEveryTextLine() {
        val pdf = renderAitaPdfDocument(AitaPdfDocument((1..300).map { AitaPdfBlock("Row $it — ₸") }))
        val raw = pdf.toString(Charsets.ISO_8859_1)
        val count = Regex("/Type /Pages /Kids \\[.*?] /Count (\\d+)").find(raw)!!.groupValues[1].toInt()
        assertTrue(count > 1)
        val commands = streams(pdf).filter { it.startsWith("0 g 0 G") }
        assertEquals(count, commands.size)
        assertEquals(300, commands.sumOf { Regex("BT 1 0 0 1").findAll(it).count() })
    }
    @Test fun severalFontSubsetsDoNotOverflowByteCharacterCodes() {
        val font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
        val characters = (32..1200).filter { font.canDisplay(it) && it.toChar().category != CharCategory.NON_SPACING_MARK }.take(400).map { it.toChar() }.joinToString("")
        assertTrue(characters.length > 255)
        val raw = renderAitaPdfDocument(AitaPdfDocument(listOf(AitaPdfBlock(characters)))).toString(Charsets.ISO_8859_1)
        assertTrue(Regex("/Subtype /Type3").findAll(raw).count() >= 2)
        Regex("/LastChar (\\d+)").findAll(raw).forEach { assertTrue(it.groupValues[1].toInt() <= 255) }
    }
    @Test fun emptyDocumentStillExportsValidPdf() {
        val raw = renderAitaPdfDocument(AitaPdfDocument(emptyList())).toString(Charsets.ISO_8859_1)
        assertTrue("/Count 1" in raw); assertTrue(raw.endsWith("%%EOF\n"))
    }
}
