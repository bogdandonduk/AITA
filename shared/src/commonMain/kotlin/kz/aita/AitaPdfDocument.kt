package kz.aita

/** Semantic text, not localized word matching. Dimensions are PDF points, independent of UI scale. */
enum class AitaPdfRole { Body, Store, Address, Title, Heading, Total, Divider }
data class AitaPdfBlock(val text: String, val role: AitaPdfRole = AitaPdfRole.Body)
data class AitaPdfStyle(val size: Float, val bold: Boolean = false, val centered: Boolean = false)
data class AitaPdfDocument(
    val blocks: List<AitaPdfBlock>,
    val width: Float = 226f,
    val maxHeight: Float = 842f,
    val minHeight: Float = 280f,
    val margin: Float = 12f,
    val bodySize: Float = 9f
) {
    fun style(role: AitaPdfRole): AitaPdfStyle = when (role) {
        AitaPdfRole.Store -> AitaPdfStyle(bodySize * 1.4f, bold = true, centered = true)
        AitaPdfRole.Address, AitaPdfRole.Title -> AitaPdfStyle(bodySize * 1.25f, bold = true, centered = true)
        AitaPdfRole.Heading -> AitaPdfStyle(bodySize, bold = true, centered = true)
        AitaPdfRole.Total -> AitaPdfStyle(bodySize * 1.1f, bold = true)
        else -> AitaPdfStyle(bodySize)
    }
}
data class AitaPdfLine(val text: String, val style: AitaPdfStyle, val x: Float, val baseline: Float, val divider: Boolean = false)
data class AitaPdfPage(val width: Float, val height: Float, val lines: List<AitaPdfLine>)

/** Native glyph rendering is required. There is deliberately no ASCII/Helvetica fallback. */
expect fun renderAitaPdfDocument(document: AitaPdfDocument): ByteArray

internal fun String.pdfCleanText(): String = buildString {
    for (c in this@pdfCleanText) when {
        c == '\t' -> append("    ")
        c >= ' ' && c != '\u007f' -> append(c)
    }
}

/** Never split a UTF-16 surrogate pair or a base character from its combining marks. */
internal fun pdfTextClusterEnds(text: String): List<Int> {
    val ends = ArrayList<Int>()
    var i = 0
    while (i < text.length) {
        i += if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
        while (i < text.length && (text[i].category.let { it == CharCategory.NON_SPACING_MARK || it == CharCategory.COMBINING_SPACING_MARK || it == CharCategory.ENCLOSING_MARK })) i++
        ends += i
    }
    return ends
}

internal fun wrapAitaPdfText(text: String, available: Float, measure: (String) -> Float): List<String> {
    if (text.isEmpty()) return listOf("")
    val output = ArrayList<String>()
    var rest = text
    while (rest.isNotEmpty()) {
        if (measure(rest) <= available) { output += rest; break }
        val ends = pdfTextClusterEnds(rest)
        var low = 0
        var high = ends.size - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (measure(rest.substring(0, ends[middle])) <= available) low = middle else high = middle - 1
        }
        val fitted = ends[low]
        require(measure(rest.substring(0, fitted)) <= available + 0.05f) { "A PDF glyph is wider than the page" }
        val lastSpace = rest.lastIndexOf(' ', startIndex = fitted - 1)
        val breakAt = if (lastSpace > 0 && rest.substring(0, lastSpace).isNotBlank()) lastSpace else fitted
        output += rest.substring(0, breakAt).trimEnd()
        rest = rest.substring(breakAt).trimStart()
    }
    return output
}

/** Layout once using exactly the same fonts/metrics that the platform will draw. */
fun layoutAitaPdfDocument(
    document: AitaPdfDocument,
    measure: (String, AitaPdfStyle) -> Float,
    metrics: (AitaPdfStyle) -> Pair<Float, Float> // ascent (negative), descent (positive)
): List<AitaPdfPage> {
    require(document.width.isFinite() && document.maxHeight.isFinite() && document.margin.isFinite())
    require(document.width >= 100f && document.maxHeight > document.margin * 2 + 48f)
    require(document.margin >= 0f && document.bodySize in 6f..30f)
    require(document.minHeight in 0f..document.maxHeight)
    require(document.blocks.size <= 50_000 && document.blocks.sumOf { it.text.length.toLong() } <= 2_000_000L) { "Document is too large" }
    val contentWidth = document.width - document.margin * 2
    require(contentWidth >= 60f)
    val pages = ArrayList<AitaPdfPage>()
    var lines = ArrayList<AitaPdfLine>()
    var y = document.margin
    fun finishPage() {
        require(pages.size < 1_000) { "Document has too many pages" }
        pages += AitaPdfPage(document.width, (y + document.margin).coerceIn(document.minHeight, document.maxHeight), lines)
        lines = ArrayList()
        y = document.margin
    }
    for (block in document.blocks) {
        val style = document.style(block.role)
        val (ascent, descent) = metrics(style)
        require(ascent.isFinite() && descent.isFinite() && ascent <= 0 && descent >= 0)
        val step = maxOf(style.size * 1.4f, descent - ascent + 2f)
        require(step.isFinite() && step <= document.maxHeight - document.margin * 2) { "PDF font metrics do not fit the page" }
        if (block.role == AitaPdfRole.Divider) {
            if (y + 12f > document.maxHeight - document.margin && lines.isNotEmpty()) finishPage()
            lines += AitaPdfLine("", style, document.margin, y + 6f, divider = true)
            y += 12f
            continue
        }
        for (paragraph in block.text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val wrapped = wrapAitaPdfText(paragraph.pdfCleanText(), contentWidth) { measure(it, style) }
            for (text in wrapped) {
                if (y + step > document.maxHeight - document.margin && lines.isNotEmpty()) finishPage()
                val width = measure(text, style)
                require(width.isFinite() && width >= 0f)
                val x = if (style.centered) document.margin + (contentWidth - width) / 2f else document.margin
                lines += AitaPdfLine(text, style, x, y - ascent)
                y += step
            }
        }
    }
    if (lines.isNotEmpty() || pages.isEmpty()) finishPage()
    return pages
}
