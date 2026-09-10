package kz.aita

import kotlin.test.*

class ReceiptRasterJvmTest {
    private fun rasterData(bytes: ByteArray): Pair<Int,List<Byte>> {
        var offset=7;var strips=0;val pixels=ArrayList<Byte>()
        while (offset+8 <= bytes.size && bytes[offset]==0x1d.toByte() && bytes[offset+1]==0x76.toByte()) {
            assertEquals(0x30,bytes[offset+2].toInt());assertEquals(0,bytes[offset+3].toInt())
            val width=(bytes[offset+4].toInt() and 255)+((bytes[offset+5].toInt() and 255) shl 8)
            val height=(bytes[offset+6].toInt() and 255)+((bytes[offset+7].toInt() and 255) shl 8)
            assertEquals(48,width);assertEquals(32,height)
            pixels.addAll(bytes.drop(offset+8).take(width*height));offset+=8+width*height;strips++
        }
        assertEquals(7,bytes.size-offset)
        return strips to pixels
    }
    @Test fun actualRendererProducesVisibleUnicodeRaster() {
        val data=assertNotNull(renderReceiptRaster(listOf("AITA","Чек № 123","Ә Ғ Қ Ң Ө Ұ Ү Һ І", "ИТОГО: 500 ₸")))
        val (strips,pixels)=rasterData(data)
        assertEquals(4,strips);assertTrue(pixels.any { it.toInt()!=0 })
    }
    @Test fun differingCyrillicTextProducesDifferentGlyphs() {
        assertFalse(assertNotNull(renderReceiptRaster(listOf("ПРИВЕТ"))).contentEquals(assertNotNull(renderReceiptRaster(listOf("РАХМЕТ")))))
    }
    @Test fun longReceiptDoesNotNeedAReceiptHeightBitmap() {
        val bytes=assertNotNull(renderReceiptRaster(List(200) { "Товар $it: 100 ₸" }))
        assertEquals(200,rasterData(bytes).first)
    }
    @Test fun jobIsDeterministicAndIndependentOfUiTheme() {
        assertContentEquals(renderReceiptRaster(listOf("AITA","Қайтарым 50 ₸")),renderReceiptRaster(listOf("AITA","Қайтарым 50 ₸")))
    }
}
