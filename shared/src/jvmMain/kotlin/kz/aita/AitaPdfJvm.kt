package kz.aita

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.font.FontRenderContext
import java.awt.geom.PathIterator
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.DeflaterOutputStream

/** Small, subsetted Type 3 vector fonts from the local JDK font engine. No font files are shipped.
 * Each character has its original UTF-16 ToUnicode mapping, including Cyrillic, Kazakh and tenge.
 * Outlines and advance widths come from the same glyphs used for layout, not an assumed code page.
 */
actual fun renderAitaPdfDocument(document: AitaPdfDocument): ByteArray {
    val context = FontRenderContext(null, true, true)
    data class Key(val bold: Boolean, val codePoint: Int)
    data class Glyph(val text: String, val advance: Float, val program: ByteArray, val bounds: java.awt.geom.Rectangle2D)
    val fonts = mapOf(false to Font(Font.SANS_SERIF, Font.PLAIN, 1000), true to Font(Font.SANS_SERIF, Font.BOLD, 1000))
    val fallbackFonts by lazy { GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts.toList() }
    val glyphs = linkedMapOf<Key, Glyph>()
    fun glyph(key: Key): Glyph = glyphs.getOrPut(key) {
        require(glyphs.size < 8192) { "Document uses too many distinct glyphs" }
        val style = if (key.bold) Font.BOLD else Font.PLAIN
        val base = fonts.getValue(key.bold)
        val font = if (base.canDisplay(key.codePoint)) base else fallbackFonts.firstOrNull { it.canDisplay(key.codePoint) }
            ?.deriveFont(style, 1000f) ?: error("No installed font can display U+${key.codePoint.toString(16)}")
        val text = String(Character.toChars(key.codePoint))
        val chars = text.toCharArray()
        val vector = font.layoutGlyphVector(context, chars, 0, chars.size, Font.LAYOUT_LEFT_TO_RIGHT)
        val advance = vector.getGlyphPosition(vector.numGlyphs).x.toFloat()
        val outline = vector.outline
        val bounds = outline.bounds2D
        val data = StringBuilder()
        data.append("${advance.pdfN()} 0 ${bounds.minX.pdfN()} ${(-bounds.maxY).pdfN()} ${bounds.maxX.pdfN()} ${(-bounds.minY).pdfN()} d1\n")
        val iterator = outline.getPathIterator(null)
        val p = DoubleArray(6)
        var x = 0.0; var y = 0.0; var startX = 0.0; var startY = 0.0
        while (!iterator.isDone) {
            when (iterator.currentSegment(p)) {
                PathIterator.SEG_MOVETO -> {
                    x = p[0]; y = p[1]; startX = x; startY = y
                    data.append("${x.pdfN()} ${(-y).pdfN()} m\n")
                }
                PathIterator.SEG_LINETO -> {
                    x = p[0]; y = p[1]; data.append("${x.pdfN()} ${(-y).pdfN()} l\n")
                }
                PathIterator.SEG_QUADTO -> {
                    val cx1 = x + (p[0] - x) * 2.0 / 3.0; val cy1 = y + (p[1] - y) * 2.0 / 3.0
                    val cx2 = p[2] + (p[0] - p[2]) * 2.0 / 3.0; val cy2 = p[3] + (p[1] - p[3]) * 2.0 / 3.0
                    x = p[2]; y = p[3]
                    data.append("${cx1.pdfN()} ${(-cy1).pdfN()} ${cx2.pdfN()} ${(-cy2).pdfN()} ${x.pdfN()} ${(-y).pdfN()} c\n")
                }
                PathIterator.SEG_CUBICTO -> {
                    data.append("${p[0].pdfN()} ${(-p[1]).pdfN()} ${p[2].pdfN()} ${(-p[3]).pdfN()} ${p[4].pdfN()} ${(-p[5]).pdfN()} c\n")
                    x = p[4]; y = p[5]
                }
                PathIterator.SEG_CLOSE -> { data.append("h\n"); x = startX; y = startY }
            }
            iterator.next()
        }
        data.append(if (iterator.windingRule == PathIterator.WIND_EVEN_ODD) "f*\n" else "f\n")
        Glyph(text, advance, data.toString().toByteArray(Charsets.US_ASCII), bounds)
    }
    fun measure(text: String, style: AitaPdfStyle): Float = text.codePoints().toArray().sumOf {
        glyph(Key(style.bold, it)).advance.toDouble()
    }.toFloat() * style.size / 1000f
    val pages = layoutAitaPdfDocument(document, ::measure) { style ->
        val m = fonts.getValue(style.bold).getLineMetrics("HgӘәҚқ₸", context)
        (-m.ascent * style.size / 1000f) to (m.descent * style.size / 1000f)
    }
    val pdf = AitaPdfObjects()
    val catalog = pdf.reserve(); val pageTree = pdf.reserve()
    data class Encoded(val fontName: String, val code: Int)
    val encoded = mutableMapOf<Key, Encoded>()
    val fontRefs = linkedMapOf<String, Int>()
    glyphs.entries.groupBy { it.key.bold }.values.flatMap { it.chunked(255) }.forEachIndexed { group, entries ->
        val fontName = "F${group + 1}"
        val charRefs = entries.map { pdf.stream(it.value.program) }
        val cmap = buildString {
            append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n/CIDSystemInfo << /Registry (AITA) /Ordering (Unicode) /Supplement 0 >> def\n/CMapName /AITAUnicode def\n/CMapType 2 def\n1 begincodespacerange\n<00> <FF>\nendcodespacerange\n")
            entries.withIndex().toList().chunked(100).forEach { chunk ->
                append("${chunk.size} beginbfchar\n")
                chunk.forEach { (i, entry) ->
                    val utf16 = entry.value.text.toByteArray(Charsets.UTF_16BE).joinToString("") { String.format(Locale.ROOT, "%02X", it.toInt() and 255) }
                    append("<${(i + 1).pdfHex()}> <$utf16>\n")
                }
                append("endbfchar\n")
            }
            append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
        }
        val unicodeRef = pdf.stream(cmap.toByteArray(Charsets.US_ASCII))
        val encoding = entries.indices.joinToString(" ") { "/g${it + 1}" }
        val procs = charRefs.mapIndexed { i, ref -> "/g${i + 1} $ref 0 R" }.joinToString(" ")
        val widths = entries.joinToString(" ") { it.value.advance.pdfN() }
        val bounds = listOf(entries.minOf { it.value.bounds.minX }, entries.minOf { -it.value.bounds.maxY },
            entries.maxOf { it.value.bounds.maxX }, entries.maxOf { -it.value.bounds.minY }).joinToString(" ") { it.pdfN() }
        val ref = pdf.add("<< /Type /Font /Subtype /Type3 /Name /$fontName /FontBBox [$bounds] /FontMatrix [0.001 0 0 0.001 0 0] /CharProcs << $procs >> /Encoding << /Type /Encoding /Differences [1 $encoding] >> /FirstChar 1 /LastChar ${entries.size} /Widths [$widths] /Resources << >> /ToUnicode $unicodeRef 0 R >>")
        fontRefs[fontName] = ref
        entries.forEachIndexed { i, entry -> encoded[entry.key] = Encoded(fontName, i + 1) }
    }
    val resources = fontRefs.entries.joinToString(" ") { "/${it.key} ${it.value} 0 R" }
    val pageRefs = pages.map { page ->
        val content = buildString {
            append("0 g 0 G\n")
            for (line in page.lines) {
                if (line.divider) {
                    append("0.6 w ${document.margin.pdfN()} ${(page.height - line.baseline).pdfN()} m ${(page.width - document.margin).pdfN()} ${(page.height - line.baseline).pdfN()} l S\n")
                } else if (line.text.isNotEmpty()) {
                    append("BT 1 0 0 1 ${line.x.pdfN()} ${(page.height - line.baseline).pdfN()} Tm\n")
                    var currentFont: String? = null
                    for (cp in line.text.codePoints().toArray()) {
                        val item = encoded.getValue(Key(line.style.bold, cp))
                        if (item.fontName != currentFont) {
                            append("/${item.fontName} ${line.style.size.pdfN()} Tf\n"); currentFont = item.fontName
                        }
                        append("<${item.code.pdfHex()}> Tj\n")
                    }
                    append("ET\n")
                }
            }
        }
        val contents = pdf.stream(content.toByteArray(Charsets.US_ASCII))
        pdf.add("<< /Type /Page /Parent $pageTree 0 R /MediaBox [0 0 ${page.width.pdfN()} ${page.height.pdfN()}] /Resources << /Font << $resources >> >> /Contents $contents 0 R >>")
    }
    pdf.set(catalog, "<< /Type /Catalog /Pages $pageTree 0 R >>")
    pdf.set(pageTree, "<< /Type /Pages /Kids [${pageRefs.joinToString(" ") { "$it 0 R" }}] /Count ${pageRefs.size} >>")
    return pdf.finish(catalog)
}

private fun Number.pdfN(): String = String.format(Locale.ROOT, "%.4f", toDouble()).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }
private fun Int.pdfHex(): String = String.format(Locale.ROOT, "%02X", this)
private class AitaPdfObjects {
    private val objects = mutableListOf<ByteArray>()
    fun reserve(): Int { objects += byteArrayOf(); return objects.size }
    fun set(id: Int, text: String) { objects[id - 1] = text.toByteArray(Charsets.US_ASCII) }
    fun add(text: String): Int = reserve().also { set(it, text) }
    fun stream(bytes: ByteArray): Int {
        val compressed = ByteArrayOutputStream().also { output -> DeflaterOutputStream(output).use { it.write(bytes) } }.toByteArray()
        val content = ByteArrayOutputStream()
        content.write("<< /Length ${compressed.size} /Filter /FlateDecode >>\nstream\n".toByteArray(Charsets.US_ASCII))
        content.write(compressed); content.write("\nendstream".toByteArray(Charsets.US_ASCII))
        objects += content.toByteArray(); return objects.size
    }
    fun finish(root: Int): ByteArray {
        val output = ByteArrayOutputStream()
        fun text(value: String) { output.write(value.toByteArray(Charsets.US_ASCII)) }
        text("%PDF-1.4\n")
        val offsets = objects.mapIndexed { i, bytes ->
            check(bytes.isNotEmpty())
            val offset = output.size(); text("${i + 1} 0 obj\n"); output.write(bytes); text("\nendobj\n"); offset
        }
        val xref = output.size()
        text("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { text("${it.toString().padStart(10, '0')} 00000 n \n") }
        text("trailer\n<< /Size ${objects.size + 1} /Root $root 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return output.toByteArray()
    }
}
