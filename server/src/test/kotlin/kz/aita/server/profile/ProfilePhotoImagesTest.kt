package kz.aita.server.profile

import kz.aita.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.*

class ProfilePhotoImagesTest {
    private fun image(w:Int=800,h:Int=600,png:Boolean=false):ByteArray {
        val b=BufferedImage(w,h,if(png)BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val g=b.createGraphics(); try {g.color=Color(255,40,10,if(png)0 else 255);g.fillRect(0,0,w,h)}finally{g.dispose()}
        return ByteArrayOutputStream().use {ImageIO.write(b,if(png)"PNG" else "JPEG",it);b.flush();it.toByteArray()}
    }
    private fun exif(orientation:Int):ByteArray {
        val tiff=byteArrayOf(73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,orientation.toByte(),0,0,0,0,0,0,0)
        val payload=byteArrayOf(69,120,105,102,0,0)+tiff
        return byteArrayOf(-1,-31,0,(payload.size+2).toByte())+payload
    }
    @Test fun jpegAndPngBecomeBoundedSquareJpegWithNoExif() {
        for(png in listOf(false,true)) {
            val output=normalizeProfilePhoto(image(png=png));val decoded=ImageIO.read(ByteArrayInputStream(output))
            assertEquals(512,decoded.width);assertEquals(512,decoded.height)
            assertTrue(output.size<=PROFILE_PHOTO_MAX_OUTPUT_BYTES)
            assertNotNull(profilePhotoJpegBytes(Base64.getEncoder().encodeToString(output)))
        }
    }
    @Test fun transparentPngGetsOpaqueLightBackground() {
        val result=ImageIO.read(ByteArrayInputStream(normalizeProfilePhoto(image(png=true))))
        val color=Color(result.getRGB(256,256));assertTrue(color.red>245 && color.green>245 && color.blue>245)
    }
    @Test fun originalMetadataIsNotStored() {
        val raw=image();val encoded=raw.copyOfRange(0,2)+exif(6)+raw.copyOfRange(2,raw.size)
        assertEquals(6,jpegExifOrientation(encoded))
        val result=normalizeProfilePhoto(encoded);assertEquals(1,jpegExifOrientation(result))
        assertFalse(result.toString(Charsets.ISO_8859_1).contains("Exif"))
    }
    @Test fun exifParserAcceptsEightOrientationsAndBoundsTruncatedOffsets() {
        for(i in 1..8)assertEquals(i,jpegExifOrientation(byteArrayOf(-1,-40)+exif(i)+byteArrayOf(-1,-39)))
        for(n in 0..35)assertEquals(1,jpegExifOrientation((byteArrayOf(-1,-40)+exif(6)).copyOf(n)))
        assertEquals(1,jpegExifOrientation(byteArrayOf(-1,-40)+exif(9)))
    }
    @Test fun orientationTransformsAreExactForAsymmetricRaster() {
        val source=BufferedImage(2,3,BufferedImage.TYPE_INT_RGB)
        var c=1;for(y in 0..2)for(x in 0..1)source.setRGB(x,y,c++)
        val expected=mapOf(2 to listOf(2,1,4,3,6,5),3 to listOf(6,5,4,3,2,1),4 to listOf(5,6,3,4,1,2),
            5 to listOf(1,3,5,2,4,6),6 to listOf(5,3,1,6,4,2),7 to listOf(6,4,2,5,3,1),8 to listOf(2,4,6,1,3,5))
        for((o,values)in expected) {
            val result=orientProfilePhoto(source,o)
            assertEquals(values,(0 until result.height).flatMap {y->(0 until result.width).map{x->result.getRGB(x,y) and 255}},"orientation $o")
        }
    }
    @Test fun wrongFormatsAndCorruptHeadersAreRejected() {
        for(b in listOf("<svg/>".toByteArray(),"GIF89a".toByteArray(),byteArrayOf(-1,-40,-1,0),byteArrayOf(-119,80,78,71,13,10,26,10)))
            assertEquals("format",assertFailsWith<PhotoProblem>{normalizeProfilePhoto(b)}.code)
    }
    @Test fun compressedByteLimitIsEnforcedBeforeDecoding() {
        assertEquals("size",assertFailsWith<PhotoProblem>{normalizeProfilePhoto(ByteArray(PROFILE_PHOTO_MAX_INPUT_BYTES+1))}.code)
    }
    @Test fun hugePixelDimensionsAreRejectedFromHeaderBeforeRasterAllocation() {
        val raw=image();var p=2
        while(p+8<raw.size) {
            val marker=raw[p+1].toInt() and 255
            if(marker==0xc0) {raw[p+5]=0x7f;raw[p+6]=0xff.toByte();break}
            p+=2+((raw[p+2].toInt() and 255)*256+(raw[p+3].toInt() and 255))
        }
        assertEquals("size",assertFailsWith<PhotoProblem>{normalizeProfilePhoto(raw)}.code)
    }
    @Test fun lostReplyRetryCannotAcknowledgeAnInterveningEdit() {
        assertTrue(photoRetryAcknowledges(4,5,true));assertFalse(photoRetryAcknowledges(4,6,true))
        assertFalse(photoRetryAcknowledges(4,5,false));assertFalse(photoRetryAcknowledges(-1,0,true))
        assertFalse(photoRetryAcknowledges(Long.MAX_VALUE,Long.MIN_VALUE,true))
    }
}
