package kz.aita

import com.google.zxing.common.BitArray
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.oned.Code128Reader
import java.util.Random
import java.util.UUID
import kotlin.test.*

class TransactionReceiptBarcodeJvmTest {
    private fun decode(modules: BooleanArray): String {
        val row = BitArray(modules.size)
        modules.forEachIndexed { index, black -> if (black) row.set(index) }
        return Code128Reader().decodeRow(0, row, null).text
    }

    @Test fun independentStandardDecoderRecoversBoundaryAndRandomUuidPayloads() {
        val random = Random(731L)
        val ids = listOf("00000000-0000-0000-0000-000000000000", "ffffffff-ffff-ffff-ffff-ffffffffffff") +
            List(250) { UUID(random.nextLong(), random.nextLong()).toString() }
        ids.forEach { id ->
            val payload = assertNotNull(transactionReceiptBarcodePayload(id))
            val modules = transactionReceiptBarcodeModules(payload)
            assertEquals(297, modules.size)
            assertTrue(modules.take(10).none { it })
            assertTrue(modules.takeLast(10).none { it })
            assertEquals(payload, decode(modules))
            assertEquals(id, parseTransactionReceiptBarcode(decode(modules)))
            val operation = "txn-$id"
            val operationPayload = assertNotNull(transactionOperationReceiptBarcodePayload(operation))
            assertEquals(operation, parseTransactionReceiptBarcodeIdentity(decode(transactionReceiptBarcodeModules(operationPayload)))?.clientOperationId)
        }
    }

    @Test fun imageDecoderRecoversBothReceiptIdentityKindsFromCameraStyleFrames() {
        val id = "bf2fb02a-d7bd-411a-80fc-c7298bcbb449"
        val payloads = listOf(assertNotNull(transactionReceiptBarcodePayload(id)),
            assertNotNull(transactionOperationReceiptBarcodePayload("txn-$id")),
            assertNotNull(compactReceiptBarcodePayload("BF2FB02A")), assertNotNull(compactReceiptBarcodePayload("OBF2FB02A")))
        for (payload in payloads) {
            val geometry = transactionReceiptBarcodeGeometry(payload, 3f, 110f)
            val width = geometry.width.toInt() + 80
            val height = geometry.height.toInt() + 80
            val image = IntArray(width * height) { 0xffffffff.toInt() }
            geometry.bars.forEach { bar ->
                for (y in bar.y.toInt() until (bar.y + bar.height).toInt()) {
                    for (x in bar.x.toInt() until (bar.x + bar.width).toInt()) image[(y + 40) * width + x + 40] = 0xff000000.toInt()
                }
            }
            val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, image)))
            val decoded = MultiFormatReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true)).text
            assertEquals(payload, decoded)
            assertEquals(parseTransactionReceiptBarcodeIdentity(payload), parseTransactionReceiptBarcodeIdentity(decoded))
        }
    }

    @Test fun thermalRasterContainsOneDecodableTwoDotSymbolBeforeFeedAndCut() {
        val payload = assertNotNull(transactionReceiptBarcodePayload("9a765abc-1234-4567-8901-123456789abc"))
        val bytes = assertNotNull(renderReceiptRaster(listOf("AITA", "Thank you"), payload))
        var offset = 7
        var rowNumber = 0
        val barcodeScanLine = ArrayList<Boolean>()
        while (offset + 8 <= bytes.size && bytes[offset] == 0x1d.toByte() && bytes[offset + 1] == 0x76.toByte()) {
            val stride = (bytes[offset + 4].toInt() and 255) + ((bytes[offset + 5].toInt() and 255) shl 8)
            val height = (bytes[offset + 6].toInt() and 255) + ((bytes[offset + 7].toInt() and 255) shl 8)
            assertEquals(48, stride)
            repeat(height) { y ->
                if (rowNumber >= 64) { // Two original 32-row text strips precede the barcode.
                    val center = bytes[offset + 8 + y * stride + 24].toInt() and 255
                    barcodeScanLine += center != 0
                    // Bars stay in the middle of the 384-dot sheet with generous side margins.
                    assertEquals(0, bytes[offset + 8 + y * stride].toInt())
                    assertEquals(0, bytes[offset + 8 + y * stride + stride - 1].toInt())
                }
                rowNumber++
            }
            offset += 8 + stride * height
        }
        assertEquals(594, barcodeScanLine.size)
        assertTrue(barcodeScanLine.chunked(2).all { it[0] == it[1] })
        assertEquals(payload, decode(barcodeScanLine.toBooleanArray()))
        assertEquals(listOf(0x1b, 0x64, 3, 0x1d, 0x56, 0x42, 0), bytes.drop(offset).map { it.toInt() and 255 })
    }

    @Test fun rotatedAndHorizontalVectorBarsDecodeWithoutFontOrThemeDependencies() {
        val payload = assertNotNull(transactionReceiptBarcodePayload("ffffffff-ffff-ffff-ffff-ffffffffffff"))
        for (vertical in listOf(false, true)) {
            val geometry = transactionReceiptBarcodeGeometry(payload, 2f, 96f, vertical)
            val sampleCount = (if (vertical) geometry.height else geometry.width).toInt()
            val middle = (if (vertical) geometry.width else geometry.height) / 2f
            val sampled = BooleanArray(sampleCount) { index ->
                val x = if (vertical) middle else index + .5f
                val y = if (vertical) index + .5f else middle
                geometry.bars.any { x >= it.x && x < it.x + it.width && y >= it.y && y < it.y + it.height }
            }
            assertEquals(payload, decode(sampled))
        }
    }
}
