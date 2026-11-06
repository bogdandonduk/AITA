package kz.aita

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/**
 * Small bounded reader for plain XLSX tables. It deliberately rejects formulas, encryption,
 * external relationships and unsupported cells rather than silently importing cached/rounded data.
 */
internal suspend fun readStockXlsx(bytes: ByteArray): StockImportWorkbook {
    val files = stockWorkbookEntries(bytes)
    suspend fun xml(path: String): StockXml {
        val entry = files[path] ?: throw StockFileProblem("file_format")
        currentCoroutineContext().ensureActive()
        val compressed = bytes.copyOfRange(entry.offset, entry.offset + entry.size)
        val raw =
            if (entry.method == 0) compressed else inflateStockFile(compressed, entry.expanded)
        if (raw.size != entry.expanded || stockFileCrc(raw) != entry.crc)
            throw StockFileProblem("file_format")
        return readStockXml(decodeStockText(raw, StockTextEncoding.UTF8))
    }
    val book = xml("xl/workbook.xml")
    val rels = xml("xl/_rels/workbook.xml.rels").children.filter { it.name == "Relationship" }
    if (rels.any { it.attrs["TargetMode"] == "External" }) throw StockFileProblem("file_external")
    val targets = rels.associate { rel ->
        val target = rel.attrs["Target"].orEmpty()
        if (target.contains(":") || target.contains("\\") || target.split('/').any { it == ".." })
            throw StockFileProblem("file_external")
        rel.attrs["Id"].orEmpty() to if (target.startsWith('/')) target.drop(1) else "xl/$target"
    }
    val strings =
        if ("xl/sharedStrings.xml" in files)
            xml("xl/sharedStrings.xml")
                .children
                .filter { it.name == "si" }
                .map { it.textContent("t") }
        else emptyList()
    // Numeric cells with a simple zero-padding style are common barcode exports.
    val formats =
        if ("xl/styles.xml" in files) {
            val styles = xml("xl/styles.xml")
            val custom =
                styles.child("numFmts")?.children.orEmpty().associate {
                    it.attrs["numFmtId"] to it.attrs["formatCode"].orEmpty()
                }
            styles.child("cellXfs")?.children.orEmpty().map {
                custom[it.attrs["numFmtId"]].orEmpty()
            }
        } else emptyList()
    val sheets = book.child("sheets")?.children?.filter { it.name == "sheet" }.orEmpty()
    if (sheets.isEmpty() || sheets.size > 20) throw StockFileProblem("file_limit")
    var totalRows = 0
    val tables =
        sheets
            .map { sheet ->
                val path = targets[sheet.attrs["id"]] ?: throw StockFileProblem("file_format")
                if (!path.startsWith("xl/worksheets/") || !path.endsWith(".xml"))
                    throw StockFileProblem("file_format")
                val worksheet = xml(path)
                val rows =
                    worksheet
                        .child("sheetData")
                        ?.children
                        .orEmpty()
                        .filter { it.name == "row" }
                        .map { row ->
                            val values = mutableMapOf<Int, String>()
                            for (cell in row.children.filter { it.name == "c" }) {
                                if (cell.child("f") != null) throw StockFileProblem("file_formula")
                                val ref = cell.attrs["r"].orEmpty()
                                if (!Regex("[A-Z]{1,3}[1-9][0-9]{0,6}").matches(ref))
                                    throw StockFileProblem("file_format")
                                val col =
                                    ref.takeWhile { it in 'A'..'Z' }
                                        .fold(0) { n, c -> n * 26 + (c - 'A' + 1) } - 1
                                if (col !in 0 until STOCK_FILE_MAX_COLUMNS || col in values)
                                    throw StockFileProblem("file_limit")
                                val raw = cell.child("v")?.text.orEmpty()
                                val value =
                                    when (cell.attrs["t"]) {
                                        "s" ->
                                            strings.getOrNull(raw.toIntOrNull() ?: -1)
                                                ?: throw StockFileProblem("file_format")
                                        "inlineStr" -> cell.child("is")?.textContent("t").orEmpty()
                                        "str" -> raw
                                        null,
                                        "n" -> {
                                            if (raw.isBlank()) ""
                                            else {
                                                // Excel numeric values beyond 15 significant digits
                                                // may already be damaged.
                                                if (
                                                    !Regex("-?[0-9]+(?:\\.[0-9]+)?").matches(raw) ||
                                                        raw.filter(Char::isDigit)
                                                            .trimStart('0')
                                                            .length > 15
                                                )
                                                    throw StockFileProblem("file_numeric")
                                                val format =
                                                    formats
                                                        .getOrNull(
                                                            cell.attrs["s"]?.toIntOrNull() ?: 0
                                                        )
                                                        .orEmpty()
                                                if (
                                                    format.isNotEmpty() &&
                                                        format.all { it == '0' } &&
                                                        format.length <= 64 &&
                                                        raw.all(Char::isDigit)
                                                )
                                                    raw.padStart(format.length, '0')
                                                else raw
                                            }
                                        }
                                        else -> throw StockFileProblem("file_cell")
                                    }
                                if (value.length > 4000) throw StockFileProblem("file_limit")
                                values[col] = value
                            }
                            if (values.isEmpty()) emptyList()
                            else List(values.keys.max() + 1) { values[it].orEmpty() }
                        }
                        .filter { it.any(String::isNotBlank) }
                totalRows += rows.size
                if (rows.size > STOCK_FILE_MAX_ROWS || totalRows > 6000)
                    throw StockFileProblem("file_limit")
                StockImportTable(sheet.attrs["name"].orEmpty().take(120), rows)
            }
            .filter { it.rows.isNotEmpty() }
    if (tables.isEmpty()) throw StockFileProblem("file_empty")
    return StockImportWorkbook(tables)
}

private data class StockZipEntry(
    val offset: Int,
    val size: Int,
    val expanded: Int,
    val method: Int,
    val crc: Long,
)

private fun stockWorkbookEntries(bytes: ByteArray): Map<String, StockZipEntry> {
    fun num(at: Int, length: Int): Long {
        if (at < 0 || at.toLong() + length > bytes.size) throw StockFileProblem("file_format")
        return (0 until length).fold(0L) { n, i ->
            n or ((bytes[at + i].toLong() and 255) shl (8 * i))
        }
    }
    fun n(at: Int, len: Int): Int =
        num(at, len).takeIf { it <= Int.MAX_VALUE }?.toInt() ?: throw StockFileProblem("file_limit")
    val end =
        (bytes.size - 22 downTo maxOf(0, bytes.size - 65557)).firstOrNull {
            num(it, 4) == 0x06054b50L && it + 22 + n(it + 20, 2) == bytes.size
        } ?: throw StockFileProblem("file_format")
    if (n(end + 4, 2) != 0 || n(end + 6, 2) != 0 || n(end + 8, 2) != n(end + 10, 2))
        throw StockFileProblem("file_format")
    val count = n(end + 10, 2)
    if (count !in 1..512) throw StockFileProblem("file_limit")
    val directory = n(end + 16, 4)
    val directorySize = n(end + 12, 4)
    if (directory.toLong() + directorySize != end.toLong()) throw StockFileProblem("file_format")
    var pos = directory
    var expandedTotal = 0L
    val entries = mutableMapOf<String, StockZipEntry>()
    repeat(count) {
        if (num(pos, 4) != 0x02014b50L) throw StockFileProblem("file_format")
        val flags = n(pos + 8, 2)
        val method = n(pos + 10, 2)
        val size = n(pos + 20, 4)
        val expanded = n(pos + 24, 4)
        val nameLength = n(pos + 28, 2)
        val extra = n(pos + 30, 2)
        val comment = n(pos + 32, 2)
        val local = n(pos + 42, 4)
        if (flags and 1 != 0 || method !in setOf(0, 8)) throw StockFileProblem("file_format")
        if (nameLength !in 1..240 || pos.toLong() + 46 + nameLength + extra + comment > end)
            throw StockFileProblem("file_format")
        val name = bytes.copyOfRange(pos + 46, pos + 46 + nameLength).decodeToString()
        if (
            name in entries ||
                name.startsWith('/') ||
                name.contains('\\') ||
                name.split('/').any { it == ".." }
        )
            throw StockFileProblem("file_format")
        expandedTotal += expanded
        if (expandedTotal > 24 * 1024 * 1024 || expanded > 8 * 1024 * 1024)
            throw StockFileProblem("file_limit")
        if (num(local, 4) != 0x04034b50L || n(local + 8, 2) != method || n(local + 6, 2) != flags)
            throw StockFileProblem("file_format")
        val localNameLength = n(local + 26, 2)
        val data = local.toLong() + 30 + localNameLength + n(local + 28, 2)
        if (data + size > directory || localNameLength != nameLength || data > Int.MAX_VALUE)
            throw StockFileProblem("file_format")
        if (
            !bytes
                .copyOfRange(local + 30, local + 30 + localNameLength)
                .contentEquals(bytes.copyOfRange(pos + 46, pos + 46 + nameLength))
        )
            throw StockFileProblem("file_format")
        if (method == 0 && size != expanded) throw StockFileProblem("file_format")
        entries[name] = StockZipEntry(data.toInt(), size, expanded, method, num(pos + 16, 4))
        pos += 46 + nameLength + extra + comment
    }
    if (pos != end) throw StockFileProblem("file_format")
    return entries
}

private val stockCrcTable =
    LongArray(256) { value ->
        var c = value.toLong()
        repeat(8) { c = if (c and 1 != 0L) (c ushr 1) xor 0xedb88320L else c ushr 1 }
        c
    }

private suspend fun stockFileCrc(bytes: ByteArray): Long {
    var crc = 0xffffffffL
    bytes.forEachIndexed { index, b ->
        if (index % 65_536 == 0) yield()
        crc = (crc ushr 8) xor stockCrcTable[((crc xor (b.toLong() and 255)) and 255).toInt()]
    }
    return crc xor 0xffffffffL
}

/** No DTDs/entities or permissive HTML parsing. The limit includes all nodes and text. */
private data class StockXml(
    val name: String,
    val attrs: Map<String, String>,
    var text: String = "",
    val children: MutableList<StockXml> = mutableListOf(),
) {
    fun child(name: String) = children.firstOrNull { it.name == name }

    fun textContent(name: String): String =
        (if (this.name == name) text else "") + children.joinToString("") { it.textContent(name) }
}

private suspend fun readStockXml(input: String): StockXml {
    if (input.length > 8 * 1024 * 1024 || input.contains("<!", true))
        throw StockFileProblem("file_format")
    val doc = StockXml("document", emptyMap())
    val stack = mutableListOf(doc)
    var i = 0
    var nodes = 0
    var nextYield = 32_768
    fun entity(raw: String): String = buildString {
        var cursor = 0
        while (cursor < raw.length) {
            if (raw[cursor] != '&') {
                append(raw[cursor++])
                continue
            }
            val end = raw.indexOf(';', cursor + 1)
            if (end < 0 || end - cursor > 13) throw StockFileProblem("file_format")
            val value = raw.substring(cursor, end + 1)
            append(
                when (value) {
                    "&amp;" -> "&"
                    "&lt;" -> "<"
                    "&gt;" -> ">"
                    "&quot;" -> "\""
                    "&apos;" -> "'"
                    else -> {
                        val code =
                            if (value.startsWith("&#x"))
                                value.substring(3, value.length - 1).toIntOrNull(16)
                            else if (value.startsWith("&#"))
                                value.substring(2, value.length - 1).toIntOrNull()
                            else null
                        if (
                            code == null ||
                                code !in 1..0x10ffff ||
                                code in 0xd800..0xdfff ||
                                code in setOf(0xfffe, 0xffff) ||
                                (code < 32 && code !in setOf(9, 10, 13))
                        )
                            throw StockFileProblem("file_format")
                        if (code <= 0xffff) code.toChar().toString()
                        else {
                            val v = code - 0x10000
                            "${(0xd800 + (v shr 10)).toChar()}${(0xdc00 + (v and 1023)).toChar()}"
                        }
                    }
                }
            )
            cursor = end + 1
        }
    }
    while (i < input.length) {
        if (i >= nextYield) {
            yield()
            nextYield = i + 32_768
        }
        if (input[i] != '<') {
            val end = input.indexOf('<', i).let { if (it < 0) input.length else it }
            if (stack.size > 1) {
                val raw = input.substring(i, end)
                // Text whitespace between nodes is unimportant; whitespace within <t> is data.
                if (raw.isNotBlank() || stack.last().name in setOf("t", "v"))
                    stack.last().text += entity(raw)
            } else if (input.substring(i, end).isNotBlank()) throw StockFileProblem("file_format")
            i = end
            continue
        }
        if (input.startsWith("<?xml ", i) && i < 3) {
            val end = input.indexOf("?>", i)
            if (end < 0) throw StockFileProblem("file_format")
            i = end + 2
            continue
        }
        var end = i + 1
        var quote: Char? = null
        while (end < input.length) {
            val c = input[end]
            if (quote != null) {
                if (c == quote) quote = null
            } else if (c == '\'' || c == '"') quote = c
            else if (c == '>') break else if (c == '<') throw StockFileProblem("file_format")
            end++
        }
        if (end == input.length) throw StockFileProblem("file_format")
        val tag = input.substring(i + 1, end).trim()
        if (tag.startsWith('/')) {
            if (
                stack.size < 2 ||
                    stack.removeAt(stack.lastIndex).name != tag.drop(1).substringAfter(':').trim()
            )
                throw StockFileProblem("file_format")
        } else {
            if (++nodes > 250_000 || stack.size > 32) throw StockFileProblem("file_limit")
            val body = tag.removeSuffix("/").trimEnd()
            val fullName = body.takeWhile { !it.isWhitespace() }
            if (!Regex("[A-Za-z_][A-Za-z0-9_.:-]*").matches(fullName))
                throw StockFileProblem("file_format")
            val attrs = mutableMapOf<String, String>()
            var p = fullName.length
            while (p < body.length) {
                while (p < body.length && body[p].isWhitespace()) p++
                if (p == body.length) break
                val start = p
                while (p < body.length && body[p] != '=' && !body[p].isWhitespace()) p++
                val key = body.substring(start, p).substringAfter(':')
                if (!Regex("[A-Za-z_][A-Za-z0-9_.-]*").matches(key))
                    throw StockFileProblem("file_format")
                while (p < body.length && body[p].isWhitespace()) p++
                if (body.getOrNull(p++) != '=') throw StockFileProblem("file_format")
                while (p < body.length && body[p].isWhitespace()) p++
                val quote = body.getOrNull(p++)
                if (quote !in listOf('\'', '"')) throw StockFileProblem("file_format")
                val close = body.indexOf(quote!!, p)
                if (close < p) throw StockFileProblem("file_format")
                if (key in attrs) throw StockFileProblem("file_format")
                if ('<' in body.substring(p, close)) throw StockFileProblem("file_format")
                attrs[key] = entity(body.substring(p, close))
                p = close + 1
                if (p < body.length && !body[p].isWhitespace())
                    throw StockFileProblem("file_format")
            }
            val node = StockXml(fullName.substringAfter(':'), attrs)
            stack.last().children += node
            if (!tag.endsWith('/')) stack += node
        }
        i = end + 1
    }
    if (stack.size != 1 || doc.children.size != 1) throw StockFileProblem("file_format")
    return doc.children.single()
}
