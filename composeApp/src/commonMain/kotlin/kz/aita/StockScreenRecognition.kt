package kz.aita

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

/** Review data only. It is never an instruction to overwrite stock or to add quantities twice. */
@Serializable
internal data class ScreenStockRow(
    val id: String,
    val name: String = "",
    val barcode: String = "",
    val quantity: String = "",
    val unit: String = "",
    val sale: String = "",
    val supply: String = "",
    val category: String = "",
    val description: String = "",
    val currency: String = "",
    val source: String = "",
    val warnings: Set<String> = emptySet(),
    val observations: Int = 1,
    val selected: Boolean = true,
    val reviewed: Boolean = false,
    val resolvedNumbers: Set<String> = emptySet(),
    val alternatives: List<String> = emptyList(),
    val importStarted: Boolean = false,
    val itemId: String = "",
    val batchId: String = "",
    val itemSaved: Boolean = false,
    val complete: Boolean = false,
    val categoryId: String = "",
    val unitId: String = "",
)

internal enum class ScreenStockField { NAME, BARCODE, QUANTITY, UNIT, SALE, SUPPLY, CATEGORY, DESCRIPTION, IGNORE }
internal data class ScreenStockColumn(val field: ScreenStockField, val center: Float, val title: String)
internal data class ScreenStockLine(val words: List<StockScreenWord>) {
    val text get() = words.joinToString(" ") { it.text.trim() }
    val centerY get() = words.map { it.y + it.height / 2 }.average().toFloat()
}
internal data class ScreenStockRecognition(
    val rows: List<ScreenStockRow>,
    val columns: List<ScreenStockColumn>,
    val preview: List<ScreenStockLine>,
    val truncated: Boolean = false,
)

private fun screenLabel(value: String): String = value.lowercase().replace('ё', 'е')
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

private val screenFieldAliases = mapOf(
    ScreenStockField.NAME to listOf("name", "product", "product name", "item name", "title", "наименование", "название", "товар", "название товара", "номенклатура", "атауы", "аталышы", "номи мол", "nomi", "mahsulot"),
    ScreenStockField.BARCODE to listOf("barcode", "bar code", "ean", "ean13", "штрихкод", "штрих код", "штрих код товара", "ш к", "штрихкоды", "shtrix kod"),
    ScreenStockField.QUANTITY to listOf("quantity", "qty", "stock", "balance", "on hand", "количество", "кол во", "остаток", "остатки", "қалдық", "саны", "калдык", "миқдор", "qoldiq", "miqdori"),
    ScreenStockField.UNIT to listOf("unit", "uom", "units", "ед", "ед изм", "единица", "единица измерения", "бірлік", "өлшем", "бирдик", "воҳид", "birlik"),
    ScreenStockField.SALE to listOf("sale price", "selling price", "retail price", "price", "цена", "цена продажи", "розничная цена", "розница", "сату бағасы", "баасы", "нархи фурӯш", "sotuv narxi"),
    ScreenStockField.SUPPLY to listOf("cost", "cost price", "purchase price", "supply price", "закупочная цена", "цена закупки", "цена поставки", "себестоимость", "закуп", "сатып алу бағасы", "нархи харид", "xarid narxi"),
    ScreenStockField.CATEGORY to listOf("category", "group", "категория", "группа", "категория товара", "санат", "категориясы", "гурӯҳ", "toifa"),
    ScreenStockField.DESCRIPTION to listOf("description", "описание", "сипаттама", "тавсиф", "tavsif"),
)

/** Preserve decimal meaning; never turn a price/weight into a barcode or repair OCR digits silently. */
internal fun screenStockNumber(raw: String): String? {
    var text = screenStockNumericText(raw)
    if (text.isBlank() || text.length > 40 || text.any { !it.isDigit() && it !in " .,+'-" }) return null
    if (text.startsWith('+')) text = text.drop(1)
    // Spaces/apostrophes must be genuine groups of three, not separate cell values.
    if (text.contains(' ') || text.contains('\'')) {
        if (!Regex("-?\\d{1,3}(?:[ ']+\\d{3})+(?:[.,]\\d{1,6})?").matches(text)) return null
        text = text.replace(" ", "").replace("'", "")
    }
    if (text.contains('.') && text.contains(',')) {
        val decimal = if (text.lastIndexOf('.') > text.lastIndexOf(',')) '.' else ','
        val grouping = if (decimal == '.') ',' else '.'
        val parts = text.split(decimal)
        if (parts.size != 2 || !Regex("-?\\d{1,3}(?:${Regex.escape(grouping.toString())}\\d{3})+").matches(parts[0]) ||
            !Regex("\\d{1,6}").matches(parts[1])) return null
        text = parts[0].replace(grouping.toString(), "") + "." + parts[1]
    } else {
        text = text.replace(',', '.')
    }
    if (!Regex("-?\\d+(?:\\.\\d{1,6})?").matches(text)) return null
    val number = text.toDoubleOrNull() ?: return null
    if (!number.isFinite() || number < 0 || number > 999_999_999.0) return null
    val pieces = text.split('.')
    val whole = pieces[0].trimStart('0').ifEmpty { "0" }
    val decimal = pieces.getOrNull(1)?.trimEnd('0').orEmpty()
    return whole + if (decimal.isEmpty()) "" else ".$decimal"
}

/** A lone separator followed by three digits can mean either a decimal or a thousands group. */
internal fun screenStockNumberAmbiguous(raw: String): Boolean =
    Regex("[1-9]\\d{0,2}[.,]\\d{3}").matches(screenStockNumericText(raw))

internal fun screenStockGroupedNumber(raw: String): String? = if (screenStockNumberAmbiguous(raw))
    screenStockNumber(screenStockNumericText(raw).replace(",", "").replace(".", "")) else null

private fun screenStockNumericText(raw: String): String = raw.trim().replace('\u00a0', ' ').replace('\u202f', ' ')
    .replace(Regex("(?i)(KZT|RUB|RUR|USD|EUR|KGS|UZS|TJS|₸|₽|\\$|€|сомонӣ|сом|сум|сўм|тг\\.?)"), "").trim()

internal fun screenStockCurrency(text: String): String = when {
    Regex("(?i)(KZT|₸|\\bтг\\b)").containsMatchIn(text) -> "KZT"
    Regex("(?i)(RUB|RUR|₽|руб)").containsMatchIn(text) -> "RUB"
    Regex("(?i)(USD|\\$)").containsMatchIn(text) -> "USD"
    Regex("(?i)(EUR|€)").containsMatchIn(text) -> "EUR"
    Regex("(?i)(UZS|сум|сўм)").containsMatchIn(text) -> "UZS"
    Regex("(?i)(TJS|сомонӣ)").containsMatchIn(text) -> "TJS"
    Regex("(?i)(KGS|сом)").containsMatchIn(text) -> "KGS"
    else -> ""
}

internal fun screenStockBarcode(raw: String): String? {
    val code = raw.trim()
    // Barcode columns may contain arbitrary internal codes. Preserve zeros and characters exactly.
    if (code.length !in 3..64 || !Regex("[A-Za-z0-9][A-Za-z0-9._/\\-]*").matches(code)) return null
    return code
}

internal fun screenStockUnit(raw: String): String = when (screenLabel(raw)) {
    "kg", "кг", "килограмм", "килограммы", "килограммдар" -> "kg"
    "g", "г", "гр", "грамм", "граммы", "grams" -> "g"
    "l", "л", "литр", "литры", "litre", "liter" -> "l"
    "ml", "мл", "миллилитр" -> "ml"
    "pc", "pcs", "piece", "pieces", "шт", "штук", "штука", "дана", "дона", "dona" -> "pcs"
    "m", "м", "метр", "meter", "metre" -> "m"
    else -> raw.trim().lowercase()
}

/** Bounded layout-aware recognition. Capture never calls this with hidden/other-window content. */
internal class StockScreenRecognizer {
    private var columns = emptyList<ScreenStockColumn>()
    private var width = 0
    private var explicitMapping = false
    private var serial = 0

    fun reset() { columns = emptyList(); width = 0; explicitMapping = false }
    fun useColumns(next: List<ScreenStockColumn>, frameWidth: Int) {
        columns = next.sortedBy { it.center }
        width = frameWidth
        explicitMapping = true
    }

    fun read(frame: StockScreenFrame): ScreenStockRecognition {
        val words = frame.words.asSequence().filter { it.text.isNotBlank() && it.text.length <= 512 &&
            it.x.isFinite() && it.y.isFinite() && it.width.isFinite() && it.height.isFinite() &&
            it.x >= 0 && it.y >= 0 && it.width > 0 && it.height > 0 &&
            (frame.width <= 0 || it.x + it.width <= frame.width + 2) &&
            (frame.height <= 0 || it.y + it.height <= frame.height + 2) }
            .take(12_000).toList()
        val lines = groupLines(words)
        if (width > 0 && frame.width > 0 && abs(width - frame.width) > 8) reset()
        var headerY: Float? = null
        if (!explicitMapping) {
            for ((index, line) in lines.withIndex()) {
                val header = joinWrappedHeader(line, lines.getOrNull(index + 1))
                val found = detectColumns(header)
                if (found.any { it.field == ScreenStockField.NAME } && found.count { it.field != ScreenStockField.IGNORE } >= 2) {
                    columns = found; width = frame.width; headerY = header.words.maxOf { it.y + it.height }; break
                }
            }
        }
        val result = mutableListOf<ScreenStockRow>()
        if (columns.any { it.field == ScreenStockField.NAME } && columns.count { it.field != ScreenStockField.IGNORE } >= 2) {
            var previousProductY = -1000f
            for (line in lines) {
                if (headerY != null && line.centerY <= headerY + 2) continue
                if (detectColumns(line).count { it.field != ScreenStockField.IGNORE } >= 2 || isFooter(line.text)) continue
                val cells = assign(line.words, columns)
                val name = cells[ScreenStockField.NAME].orEmpty().trim()
                val barcode = cells[ScreenStockField.BARCODE].orEmpty().trim()
                if (barcode.isBlank() && result.isNotEmpty() && line.centerY - previousProductY <= line.words.maxOf { it.height } * 1.65f &&
                    cells.keys.all { it in setOf(ScreenStockField.NAME, ScreenStockField.DESCRIPTION, ScreenStockField.CATEGORY) }) {
                    val old = result.last()
                    result[result.lastIndex] = old.copy(
                        name = listOf(old.name, name).filter { it.isNotBlank() }.joinToString(" ").take(300),
                        description = listOf(old.description, cells[ScreenStockField.DESCRIPTION].orEmpty()).filter { it.isNotBlank() }.joinToString(" ").take(2000),
                        category = listOf(old.category, cells[ScreenStockField.CATEGORY].orEmpty()).filter { it.isNotBlank() }.joinToString(" ").take(300),
                        source = (old.source + "\n" + line.text).take(1200), warnings = old.warnings + "wrapped_text")
                    previousProductY = line.centerY
                    continue
                }
                if (name.isBlank() || name.length > 300 || name.none { it.isLetter() }) continue
                // Reject navigation/sidebar lines which share the name column but no product values.
                if (barcode.isBlank() && listOf(ScreenStockField.SALE, ScreenStockField.QUANTITY, ScreenStockField.SUPPLY)
                        .none { screenStockNumber(cells[it].orEmpty()) != null }) continue
                result += row(cells, line, frame)
                previousProductY = line.centerY
            }
        } else {
            // Detail forms with explicit labels. No price/quantity guessing from arbitrary numbers.
            val detail = linkedMapOf<ScreenStockField, String>()
            val detailWords = mutableListOf<StockScreenWord>()
            for (line in lines) {
                val split = line.text.indexOf(':')
                if (split <= 0) continue
                val key = fieldFor(line.text.substring(0, split)) ?: continue
                val value = line.text.substring(split + 1).trim()
                if (value.isNotEmpty()) { detail[key] = value; detailWords += line.words }
            }
            if (!detail[ScreenStockField.NAME].isNullOrBlank()) result += row(detail, ScreenStockLine(detailWords), frame)
            else for (line in lines) {
                if (isFooter(line.text)) continue
                val codes = line.words.filter { Regex("\\d{8,14}").matches(it.text.trim()) }
                if (codes.size != 1) continue
                val code = codes.single()
                val nameWords = line.words.filter { it !== code && it.text.any(Char::isLetter) }
                if (nameWords.isEmpty()) continue
                result += row(mapOf(ScreenStockField.NAME to nameWords.joinToString(" ") { it.text },
                    ScreenStockField.BARCODE to code.text), line, frame).let { it.copy(warnings = it.warnings + "map_columns") }
            }
        }
        return ScreenStockRecognition(result.take(250), columns, lines.take(120), result.size > 250 || words.size == 12_000)
    }

    private fun row(cells: Map<ScreenStockField, String>, line: ScreenStockLine, frame: StockScreenFrame): ScreenStockRow {
        val warnings = linkedSetOf<String>()
        if (frame.source == StockScreenSource.OCR) warnings += "check_ocr"
        if (line.words.any { it.confidence < .85f }) warnings += "low_confidence"
        val rawBarcode = cells[ScreenStockField.BARCODE].orEmpty()
        if (rawBarcode.isBlank()) warnings += "no_barcode"
        else if (screenStockBarcode(rawBarcode) == null) warnings += "invalid_barcode"
        else if (rawBarcode.length in setOf(8, 12, 13, 14) && rawBarcode.all(Char::isDigit) && !rawBarcode.hasValidRetailBarcodeChecksum())
            warnings += "barcode_checksum"
        fun number(field: ScreenStockField): String {
            val raw = cells[field].orEmpty()
            if (raw.isBlank()) return ""
            val normalized = screenStockNumber(raw)
            if (normalized == null) warnings += "invalid_${field.name.lowercase()}"
            if (screenStockNumberAmbiguous(raw)) { warnings += "ambiguous_number"; return raw }
            return normalized ?: raw
        }
        val sale = number(ScreenStockField.SALE)
        if (sale.isBlank()) warnings += "no_price"
        val unit = screenStockUnit(cells[ScreenStockField.UNIT].orEmpty())
        if (unit.isBlank()) warnings += "no_unit"
        val currencies = listOf(ScreenStockField.SALE, ScreenStockField.SUPPLY).map { screenStockCurrency(cells[it].orEmpty()) }.filter { it.isNotEmpty() }.distinct()
        if (currencies.size > 1) warnings += "currency_conflict"
        return ScreenStockRow(id = "capture-${++serial}", name = cells[ScreenStockField.NAME].orEmpty().take(300),
            barcode = rawBarcode.take(128), quantity = number(ScreenStockField.QUANTITY), unit = unit,
            sale = sale, supply = number(ScreenStockField.SUPPLY), category = cells[ScreenStockField.CATEGORY].orEmpty().take(300),
            description = cells[ScreenStockField.DESCRIPTION].orEmpty().take(2000), currency = currencies.singleOrNull().orEmpty(),
            source = line.text.take(1200), warnings = warnings)
    }

    private fun assign(words: List<StockScreenWord>, cols: List<ScreenStockColumn>): Map<ScreenStockField, String> {
        val sorted = cols.sortedBy { it.center }
        return words.groupBy { word ->
            val center = word.x + word.width / 2
            sorted.minBy { abs(it.center - center) }.field
        }.mapValues { (field, group) ->
            val sortedWords = group.sortedBy { it.x }
            // A large gap between numeric cells is not a thousands separator.
            val separateNumbers = field in setOf(ScreenStockField.SALE, ScreenStockField.SUPPLY, ScreenStockField.QUANTITY) &&
                sortedWords.zipWithNext().any { (a, b) -> a.text.any(Char::isDigit) && b.text.any(Char::isDigit) &&
                    b.x - a.x - a.width > max(a.height, b.height) * .8f }
            sortedWords.joinToString(if (separateNumbers) " | " else " ") { it.text.trim() }
        }
    }

    private fun detectColumns(line: ScreenStockLine): List<ScreenStockColumn> {
        val words = line.words.sortedBy { it.x }
        val found = mutableListOf<ScreenStockColumn>()
        var index = 0
        while (index < words.size) {
            var consumed = 1
            var recognized = false
            for (length in minOf(4, words.size - index) downTo 1) {
                val group = words.subList(index, index + length)
                // Do not join widely separated table headers into a phrase.
                if (group.zipWithNext().any { (a, b) -> b.x - a.x - a.width > max(a.height, b.height) * 2 }) continue
                val text = group.joinToString(" ") { it.text }
                val field = fieldFor(text) ?: continue
                if (found.none { it.field == field }) {
                    val left = group.first().x; val right = group.last().let { it.x + it.width }
                    found += ScreenStockColumn(field, (left + right) / 2, text)
                }
                consumed = length; recognized = true; break
            }
            if (!recognized && consumed == 1 && found.none { abs(it.center - (words[index].x + words[index].width / 2)) < 1 }) {
                val word = words[index]
                found += ScreenStockColumn(ScreenStockField.IGNORE, word.x + word.width / 2, word.text)
            }
            index += consumed
        }
        return found
    }

    private fun fieldFor(value: String): ScreenStockField? {
        val exact = screenFieldAliases.entries.firstOrNull { screenLabel(value) in it.value }?.key
        if (exact != null) return exact
        // Some accessibility providers expose bilingual header labels in a single cell.
        val parts = value.split(Regex("[/|()\\n]")).filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val alternatives = parts.map { part ->
            screenFieldAliases.entries.firstOrNull { screenLabel(part) in it.value }?.key ?: return null
        }.distinct()
        return alternatives.singleOrNull()
    }

    private fun joinWrappedHeader(top: ScreenStockLine, bottom: ScreenStockLine?): ScreenStockLine {
        if (bottom == null || bottom.centerY - top.centerY > top.words.maxOf { it.height } * 1.7f) return top
        return ScreenStockLine(top.words.map { first ->
            val next = bottom.words.singleOrNull { second ->
                abs((first.x + first.width / 2) - (second.x + second.width / 2)) <= max(first.width, second.width) * .4f &&
                    fieldFor(first.text + " " + second.text) != null
            }
            if (next == null) first else first.copy(text = first.text + " " + next.text,
                height = next.y + next.height - first.y)
        })
    }

    private fun isFooter(value: String): Boolean = Regex("(?i)^(итого|всего|total|subtotal|страница|page|показано)(?:\\s|:|$)").containsMatchIn(value.trim().lowercase())

    private fun groupLines(words: List<StockScreenWord>): List<ScreenStockLine> {
        val lines = mutableListOf<MutableList<StockScreenWord>>()
        words.sortedWith(compareBy<StockScreenWord> { it.y + it.height / 2 }.thenBy { it.x }).forEach { word ->
            val center = word.y + word.height / 2
            val match = lines.lastOrNull()?.takeIf { row ->
                val median = row.map { it.y + it.height / 2 }.average().toFloat()
                abs(median - center) <= minOf(word.height, row.first().height) * .55f
            }
            if (match == null) lines += mutableListOf(word) else match += word
        }
        return lines.map { ScreenStockLine(it.sortedBy { word -> word.x }) }
    }
}

/** Repeated screenshots are observations, never stock movements. Conflicts require explicit review. */
internal fun mergeScreenStockRows(existing: List<ScreenStockRow>, incoming: List<ScreenStockRow>, limit: Int = 3000): List<ScreenStockRow> {
    val result = existing.toMutableList()
    for (next in incoming) {
        val index = result.indexOfFirst { row ->
            if (next.barcode.isNotBlank() && screenStockBarcode(next.barcode) != null) row.barcode == next.barcode
            else row.barcode.isBlank() && row.name == next.name && row.source == next.source
        }
        if (index < 0) {
            if (result.size < limit) result += next.copy(id = newDiagnosticId(), itemId = newDiagnosticId(), batchId = newDiagnosticId())
            continue
        }
        val old = result[index]
        if (old.complete || old.importStarted || old.itemSaved || old.reviewed) continue
        var conflict = false
        fun merge(a: String, b: String): String {
            if (a.isNotBlank() && b.isNotBlank() && a != b) conflict = true
            return a.ifBlank { b }
        }
        val merged = old.copy(name = merge(old.name, next.name), quantity = merge(old.quantity, next.quantity),
            unit = merge(old.unit, next.unit), sale = merge(old.sale, next.sale), supply = merge(old.supply, next.supply),
            category = merge(old.category, next.category), description = merge(old.description, next.description),
            currency = merge(old.currency, next.currency), observations = (old.observations + 1).coerceAtMost(1_000_000))
        val warn = (old.warnings + next.warnings).toMutableSet()
        if (conflict) warn += "conflicting_readings"
        if (merged.sale.isNotBlank()) warn -= "no_price"
        if (merged.unit.isNotBlank()) warn -= "no_unit"
        result[index] = merged.copy(warnings = warn,
            alternatives = if (conflict) (old.alternatives + next.source).distinct().take(5) else old.alternatives)
    }
    return result
}
