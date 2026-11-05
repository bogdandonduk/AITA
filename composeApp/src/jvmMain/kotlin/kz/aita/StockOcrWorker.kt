package kz.aita

import kotlinx.serialization.json.*
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

/** Private subprocess entry point; no files, network calls, telemetry, or UI are used for frames. */
internal object StockOcrWorker {
    @JvmStatic fun main(args: Array<String>) {
        val output = java.io.PrintWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(java.io.FileDescriptor.out), Charsets.UTF_8), true)
        System.setOut(java.io.PrintStream(java.io.OutputStream.nullOutputStream()))
        var phase = "arguments"
        try {
            require(args.size == 2)
            val decoded = args.map { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }
            phase = "engine_setup"
            val engine = Tesseract().apply {
                setDatapath(decoded[0]); setLanguage(decoded[1])
                setOcrEngineMode(1) // The pinned tessdata_fast files are LSTM-only.
                setPageSegMode(11) // Sparse table text with independent word rectangles.
                setVariable("user_defined_dpi", "144")
            }
            val input = System.`in`.buffered(65536)
            while (true) {
                phase = "read_frame"
                val line = StockCaptureProtocol.readLine(input, 9_400_000) ?: break
                val bytes = Base64.getDecoder().decode(line)
                require(bytes.size <= 7_000_000)
                phase = "decode_png"
                val image = ByteArrayInputStream(bytes).use { encoded ->
                    ImageIO.createImageInputStream(encoded).use { stream ->
                        val readers = ImageIO.getImageReaders(stream)
                        require(readers.hasNext())
                        val reader = readers.next()
                        try {
                            require(reader.formatName.equals("png", true))
                            reader.input = stream
                            val width = reader.getWidth(0); val height = reader.getHeight(0)
                            require(width > 0 && height > 0 && width.toLong() * height <= StockCaptureProtocol.MAX_PIXELS)
                            reader.read(0)
                        } finally { reader.dispose() }
                    }
                }
                phase = "recognize"
                val words = try {
                    engine.getWords(listOf(image), ITessAPI.TessPageIteratorLevel.RIL_WORD).take(StockCaptureProtocol.MAX_WORDS)
                        .filter { !it.text.isNullOrBlank() && it.text.length <= 512 && it.confidence.isFinite() }
                        .map { word -> buildJsonObject {
                            put("text", word.text.trim()); put("confidence", (word.confidence / 100f).coerceIn(0f, 1f))
                            put("x", word.boundingBox.x); put("y", word.boundingBox.y)
                            put("width", word.boundingBox.width); put("height", word.boundingBox.height)
                        } }
                } finally { image.flush() }
                phase = "write_result"
                output.println(buildJsonObject { put("type", "ocr"); put("words", JsonArray(words)) }.toString())
                output.flush()
            }
        } catch (error: Throwable) {
            // Do not echo a native provider error, filename, window title or recognized string.
            // Fixed phases and Java class names distinguish a broken packaged runtime from
            // image recognition without disclosing either source content or exception messages.
            val classes = generateSequence(error) { it.cause }.take(4)
                .map { it.javaClass.name.take(160).filter { char -> char.isLetterOrDigit() || char in ".$" } }
                .joinToString(",") { "\"$it\"" }
            output.println("{\"type\":\"error\",\"code\":\"ocr_unavailable\",\"phase\":\"$phase\",\"classes\":[$classes]}")
            output.flush()
        }
    }
}
