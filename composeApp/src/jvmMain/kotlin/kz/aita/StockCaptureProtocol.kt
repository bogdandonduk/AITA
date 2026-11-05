package kz.aita

import kotlinx.serialization.json.*
import java.io.InputStream

/** Hard bounds also apply before JSON parsing, so a broken provider cannot exhaust the client. */
internal object StockCaptureProtocol {
    const val MAX_LINE_BYTES = 10_000_000
    const val MAX_WORDS = 2_000
    const val MAX_PIXELS = 6_000_000L
    val json = Json { ignoreUnknownKeys = true }
    val messageCodes = setOf("ready", "paused", "source_not_foreground", "source_closed", "source_minimized",
        "source_unavailable", "source_unresponsive", "source_size_unsupported", "helper_unavailable",
        "ocr_unavailable", "ocr_timeout", "capture_limit", "windows_only", "capture_stopped", "no_readable_text")

    fun readLine(input: InputStream, limit: Int = MAX_LINE_BYTES): String? {
        val out = java.io.ByteArrayOutputStream(minOf(limit, 65536))
        while (true) {
            val b = input.read()
            if (b == -1) return if (out.size() == 0) null else out.toString(Charsets.UTF_8.name())
            if (b == 10) return out.toString(Charsets.UTF_8.name()).trimEnd('\r')
            check(out.size() < limit) { "capture_protocol_limit" }
            out.write(b)
        }
    }

    fun parse(line: String): JsonObject = json.parseToJsonElement(line).jsonObject
    fun words(value: JsonElement?, width: Int, height: Int): List<StockScreenWord> {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS)
        val list = value as? JsonArray ?: return emptyList()
        require(list.size <= MAX_WORDS)
        return list.mapNotNull { entry ->
            val item = entry as? JsonObject ?: return@mapNotNull null
            val text = item["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            fun number(key: String) = item[key]?.jsonPrimitive?.floatOrNull ?: Float.NaN
            val x = number("x"); val y = number("y"); val w = number("width"); val h = number("height")
            val confidence = item["confidence"]?.jsonPrimitive?.floatOrNull ?: 1f
            if (text.isEmpty() || text.length > 512 || text.any { it == '\u0000' } ||
                !listOf(x, y, w, h, confidence).all(Float::isFinite) ||
                x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > width + 2 || y + h > height + 2) null
            else StockScreenWord(text, x, y, w, h, confidence.coerceIn(0f, 1f))
        }
    }

    fun ocrLanguage(languageTag: String): String = when (languageTag.substringBefore('-').lowercase()) {
        "kk", "kaz" -> "kaz+rus+eng"
        "ky", "kir" -> "kir+rus+eng"
        "tg", "tgk" -> "tgk+rus+eng"
        "uz", "uzb" -> "uzb+uzb_cyrl+rus+eng"
        else -> "rus+eng" // Latin and Cyrillic stock labels commonly coexist even in English UI.
    }
}
