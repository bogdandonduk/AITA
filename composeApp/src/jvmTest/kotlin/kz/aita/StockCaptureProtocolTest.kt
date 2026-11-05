package kz.aita

import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlin.test.*

class StockCaptureProtocolTest {
    @Test fun inputIsBoundedBeforeParsing() {
        assertFailsWith<IllegalStateException> { StockCaptureProtocol.readLine(ByteArrayInputStream(ByteArray(20) { 65 }), 10) }
        assertEquals("{}", StockCaptureProtocol.readLine(ByteArrayInputStream("{}\r\nextra".toByteArray()), 10))
        assertNull(StockCaptureProtocol.readLine(ByteArrayInputStream(byteArrayOf())))
    }
    @Test fun wordsMustRemainInsideSelectedWindow() {
        val data = StockCaptureProtocol.parse("""{"words":[
            {"text":"Milk","x":2,"y":4,"width":30,"height":12,"confidence":0.8},
            {"text":"Other window","x":-20,"y":4,"width":30,"height":12},
            {"text":"Overflow","x":98,"y":4,"width":30,"height":12},
            {"text":"Control","x":2,"y":4,"width":30,"height":12,"confidence":-2}
        ]}""")
        val words = StockCaptureProtocol.words(data["words"], 100, 100)
        assertEquals(listOf("Milk", "Control"), words.map { it.text })
        assertEquals(0.8f, words[0].confidence)
        assertEquals(0f, words[1].confidence)
        assertFailsWith<IllegalArgumentException> { StockCaptureProtocol.words(data["words"], 10_000, 10_000) }
    }
    @Test fun tooManyWordsAreRejectedRatherThanGrowingUnbounded() {
        val words = JsonArray(List(2001) { buildJsonObject { put("text", "fixture") } })
        assertFailsWith<IllegalArgumentException> { StockCaptureProtocol.words(words, 100, 100) }
    }
    @Test fun everyAppLanguageHasLocalOcrModelsAndLatinCyrillicCoverage() {
        val expected = mapOf("en" to "rus+eng", "ru" to "rus+eng", "kk-KZ" to "kaz+rus+eng",
            "ky" to "kir+rus+eng", "tg" to "tgk+rus+eng", "uz" to "uzb+uzb_cyrl+rus+eng")
        expected.forEach { (tag, models) -> assertEquals(models, StockCaptureProtocol.ocrLanguage(tag)) }
        assertEquals("rus+eng", StockCaptureProtocol.ocrLanguage("../../unexpected"))
    }
    @Test fun bundledModelsMatchPinnedOfficialHashes() {
        val manifest = StockCaptureProtocol.parse(javaClass.getResourceAsStream("/stock-capture/tessdata/manifest.json")!!.bufferedReader().use { it.readText() })
        assertEquals("65727574dfcd264acbb0c3e07860e4e9e9b22185", manifest["commit"]!!.jsonPrimitive.content)
        assertEquals("Apache-2.0", manifest["license"]!!.jsonPrimitive.content)
        assertEquals(7, manifest["models"]!!.jsonArray.size)
        manifest["models"]!!.jsonArray.forEach { entry ->
            val value = entry.jsonObject; val name = value["file"]!!.jsonPrimitive.content
            val bytes = javaClass.getResourceAsStream("/stock-capture/tessdata/$name")!!.use { it.readBytes() }
            assertEquals(value["bytes"]!!.jsonPrimitive.int, bytes.size, name)
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(value["sha256"]!!.jsonPrimitive.content, actual, name)
        }
    }
    @Test fun bundledRecognitionLicensesPreserveUpstreamBytes() {
        val manifest = StockCaptureProtocol.parse(javaClass.getResourceAsStream("/stock-capture/licenses-manifest.json")!!.bufferedReader().use { it.readText() })
        assertTrue(manifest["components"]!!.jsonArray.size >= 14)
        manifest["components"]!!.jsonArray.forEach { entry ->
            val value = entry.jsonObject; val file = value["file"]!!.jsonPrimitive.content
            val bytes = javaClass.getResourceAsStream("/stock-capture/$file")!!.use { it.readBytes() }
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(value["sha256"]!!.jsonPrimitive.content, actual, file)
        }
        val notice = javaClass.getResourceAsStream("/stock-capture/THIRD-PARTY-NOTICES.txt")!!.bufferedReader().use { it.readText() }
        assertContains(notice, "This software is based in part on the work of the Independent JPEG Group.")
    }
    @Test fun allSafeDiagnosticsAreTranslatedAndNeverReflectUntrustedMessages() {
        for (code in StockCaptureProtocol.messageCodes) {
            for (language in listOf("en", "ru", "kk", "ky", "tg", "uz")) {
                assertTrue(stockCaptureStatusMessage(code, language).length > 10)
                if (language != "en") assertNotEquals(stockCaptureStatusMessage(code, "en"), stockCaptureStatusMessage(code, language))
            }
        }
        assertFalse(stockCaptureStatusMessage("PRIVATE STOCK NAME", "en").contains("PRIVATE"))
    }
}
