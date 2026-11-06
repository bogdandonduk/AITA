package kz.aita

import androidx.compose.runtime.Composable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

internal const val STOCK_FILE_MAX_BYTES = 8 * 1024 * 1024
internal const val STOCK_FILE_MAX_ROWS = 3001 // includes the column header
internal const val STOCK_FILE_MAX_COLUMNS = 64

internal class StockFileProblem(val code: String) : IllegalArgumentException(code)

internal data class StockImportFile(
    val name: String = "",
    val bytes: ByteArray? = null,
    val error: String? = null,
)

internal data class StockImportTable(val name: String, val rows: List<List<String>>)

internal data class StockImportWorkbook(val tables: List<StockImportTable>)

internal enum class StockTextEncoding {
    UTF8,
    WINDOWS1251,
    UTF16LE,
    UTF16BE,
}

internal data class StockFilePicker(val busy: Boolean, val launch: () -> Unit)

@Composable
internal expect fun rememberStockFilePicker(
    title: String,
    onResult: (StockImportFile) -> Unit,
): StockFilePicker

internal expect suspend fun inflateStockFile(bytes: ByteArray, expectedSize: Int): ByteArray

/**
 * Local file parsing only: no upload, macros, formula execution, URLs or external workbook links.
 */
internal suspend fun readStockImportFile(
    file: StockImportFile,
    delimiter: Char? = null,
    encoding: StockTextEncoding = StockTextEncoding.UTF8,
): StockImportWorkbook {
    val bytes = file.bytes ?: throw StockFileProblem("file_error")
    if (bytes.isEmpty() || bytes.size > STOCK_FILE_MAX_BYTES) throw StockFileProblem("file_size")
    currentCoroutineContext().ensureActive()
    val ext = file.name.substringAfterLast('.', "").lowercase()
    return if (ext == "xlsx") readStockXlsx(bytes)
    else {
        if (ext !in setOf("csv", "tsv", "txt")) throw StockFileProblem("file_format")
        val text = decodeStockText(bytes, encoding)
        StockImportWorkbook(
            listOf(
                StockImportTable(
                    file.name,
                    parseStockDelimited(
                        text,
                        delimiter ?: if (ext == "tsv") '\t' else detectStockDelimiter(text),
                    ),
                )
            )
        )
    }
}

internal fun decodeStockText(bytes: ByteArray, encoding: StockTextEncoding): String {
    fun u(i: Int) = bytes[i].toInt() and 255
    val bom =
        when {
            bytes.size >= 3 && u(0) == 239 && u(1) == 187 && u(2) == 191 -> 3
            bytes.size >= 2 && u(0) == 255 && u(1) == 254 -> 2
            bytes.size >= 2 && u(0) == 254 && u(1) == 255 -> 2
            else -> 0
        }
    val mode =
        when {
            bom == 3 -> StockTextEncoding.UTF8
            bom == 2 -> if (u(0) == 255) StockTextEncoding.UTF16LE else StockTextEncoding.UTF16BE
            else -> encoding
        }
    val text =
        try {
            when (mode) {
                StockTextEncoding.UTF8 ->
                    bytes.decodeToString(startIndex = bom, throwOnInvalidSequence = true)
                StockTextEncoding.WINDOWS1251 ->
                    buildString {
                        val high =
                            "ЂЃ‚ѓ„…†‡€‰Љ‹ЊЌЋЏђ‘’“”•–—\uFFFD™љ›њќћџ\u00a0ЎўЈ¤Ґ¦§Ё©Є«¬\u00ad®Ї°±Ііґµ¶·ё№є»јЅѕї"
                        for (i in bom until bytes.size) {
                            val c = u(i)
                            append(
                                when {
                                    c < 128 -> c.toChar()
                                    c < 192 -> high[c - 128]
                                    else -> ('А'.code + c - 192).toChar()
                                }
                            )
                        }
                    }
                else -> {
                    if ((bytes.size - bom) % 2 != 0) throw StockFileProblem("file_encoding")
                    buildString {
                        for (i in bom until bytes.size step 2) append(
                            (if (mode == StockTextEncoding.UTF16LE) u(i) + (u(i + 1) shl 8)
                                else (u(i) shl 8) + u(i + 1))
                                .toChar()
                        )
                    }
                }
            }
        } catch (_: CharacterCodingException) {
            throw StockFileProblem("file_encoding")
        }
    if (text.any { it == '\uFFFD' || (it.code < 32 && it !in "\r\n\t") })
        throw StockFileProblem("file_encoding")
    var index = 0
    while (index < text.length) {
        val c = text[index++]
        if (c.isHighSurrogate()) {
            if (text.getOrNull(index)?.isLowSurrogate() != true)
                throw StockFileProblem("file_encoding")
            index++
        } else if (c.isLowSurrogate()) throw StockFileProblem("file_encoding")
    }
    return text.removePrefix("\uFEFF")
}

internal fun detectStockDelimiter(text: String): Char {
    // Only unquoted separators in the first logical record participate. Commas in prices/names
    // must not defeat semicolon/tab files. The chosen delimiter remains explicitly editable.
    val counts = mutableMapOf('\t' to 0, ';' to 0, ',' to 0)
    var quoted = false
    var i = 0
    while (i < text.length) {
        if (i > STOCK_FILE_MAX_COLUMNS * 4001) throw StockFileProblem("file_limit")
        val c = text[i++]
        if (c == '"') {
            if (quoted && text.getOrNull(i) == '"') i++ else quoted = !quoted
        } else if (!quoted) {
            if (c == '\r' || c == '\n') break
            if (c in counts) counts[c] = counts.getValue(c) + 1
        }
    }
    return counts.maxBy { it.value }.key
}

/** RFC 4180-style quoting, including multiline names. Never truncate rows or guess lost zeros. */
internal suspend fun parseStockDelimited(text: String, delimiter: Char): List<List<String>> {
    if (delimiter !in "\t;,") throw StockFileProblem("file_format")
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var closed = false
    var i = 0
    var nextYield = 16_384
    fun addCell() {
        if (row.size >= STOCK_FILE_MAX_COLUMNS || cell.length > 4000)
            throw StockFileProblem("file_limit")
        row += cell.toString().trim()
        cell.clear()
        closed = false
    }
    fun addRow() {
        addCell()
        if (row.any { it.isNotBlank() }) {
            if (rows.size >= STOCK_FILE_MAX_ROWS) throw StockFileProblem("file_limit")
            rows += row
        }
        row = mutableListOf()
    }
    while (i < text.length) {
        if (i >= nextYield) {
            yield()
            nextYield = i + 16_384
        }
        val c = text[i++]
        when {
            quoted ->
                when {
                    c == '"' && text.getOrNull(i) == '"' -> {
                        cell.append('"')
                        i++
                    }
                    c == '"' -> {
                        quoted = false
                        closed = true
                    }
                    else -> cell.append(c)
                }
            c == delimiter -> addCell()
            c == '\n' || c == '\r' -> {
                if (c == '\r' && text.getOrNull(i) == '\n') i++
                addRow()
            }
            c == '"' && cell.isEmpty() && !closed -> quoted = true
            c == '"' || (closed && !c.isWhitespace()) -> throw StockFileProblem("file_quotes")
            !closed -> cell.append(c)
        }
        if (cell.length > 4000) throw StockFileProblem("file_limit")
    }
    if (quoted) throw StockFileProblem("file_quotes")
    if (row.isNotEmpty() || cell.isNotEmpty() || closed) addRow()
    if (rows.isEmpty()) throw StockFileProblem("file_empty")
    return rows
}

internal fun stockFileRows(
    table: StockImportTable,
    header: Int,
    mapping: List<ScreenStockField>,
    currency: String,
    defaultUnit: QuantityDataModel?,
    supplyEqualsSale: Boolean,
): List<ScreenStockRow> {
    if (header !in table.rows.indices || mapping.size != table.rows.maxOf { it.size })
        throw StockFileProblem("file_mapping")
    val chosen = mapping.filter { it != ScreenStockField.IGNORE }
    if (ScreenStockField.NAME !in chosen || chosen.size != chosen.distinct().size)
        throw StockFileProblem("file_mapping")
    return table.rows
        .drop(header + 1)
        .mapIndexedNotNull { index, cells ->
            if (cells.all { it.isBlank() } || cells == table.rows[header])
                return@mapIndexedNotNull null
            fun field(key: ScreenStockField) =
                mapping
                    .indexOf(key)
                    .takeIf { it >= 0 }
                    ?.let { cells.getOrNull(it) }
                    .orEmpty()
                    .trim()
            val name = field(ScreenStockField.NAME)
            val sale = field(ScreenStockField.SALE)
            val priceCurrencies =
                listOf(ScreenStockField.SALE, ScreenStockField.SUPPLY, ScreenStockField.CURRENCY)
                    .flatMap { screenStockCurrencies(field(it)) }
                    .distinct()
            val rowCurrency =
                when {
                    priceCurrencies.size > 1 -> ""
                    field(ScreenStockField.CURRENCY).isNotBlank() &&
                        stockExplicitCurrency(field(ScreenStockField.CURRENCY)).isBlank() -> ""
                    priceCurrencies.isNotEmpty() -> priceCurrencies.single()
                    else -> currency
                }
            val unit = field(ScreenStockField.UNIT)
            ScreenStockRow(
                id = newDiagnosticId(),
                name = name,
                barcode = field(ScreenStockField.BARCODE),
                sale = sale,
                supply =
                    field(ScreenStockField.SUPPLY).ifBlank { if (supplyEqualsSale) sale else "" },
                unit =
                    unit.ifBlank {
                        defaultUnit?.immutableUnitName?.extractLocalizedString("main").orEmpty()
                    },
                unitId = if (unit.isBlank()) defaultUnit?.id.orEmpty() else "",
                quantity = field(ScreenStockField.QUANTITY),
                category = field(ScreenStockField.CATEGORY),
                description = field(ScreenStockField.DESCRIPTION),
                currency = rowCurrency,
                source = "${table.name.take(120)} · ${header + index + 2}",
                warnings =
                    buildSet {
                        if (name.isBlank()) add("map_columns")
                        if (screenStockBarcode(field(ScreenStockField.BARCODE)) == null)
                            add("invalid_barcode")
                        if (screenStockNumber(sale) == null) add("invalid_sale")
                        if (
                            screenStockNumberAmbiguous(sale) ||
                                screenStockNumberAmbiguous(field(ScreenStockField.SUPPLY)) ||
                                screenStockNumberAmbiguous(field(ScreenStockField.QUANTITY))
                        )
                            add("ambiguous_number")
                        if (field(ScreenStockField.CATEGORY).isNotBlank()) add("file_category")
                        if (priceCurrencies.size > 1) add("currency_conflict")
                    },
            )
        }
        .also { if (it.isEmpty()) throw StockFileProblem("file_empty") }
}
