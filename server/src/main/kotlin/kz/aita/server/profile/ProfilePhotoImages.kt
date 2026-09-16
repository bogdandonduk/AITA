package kz.aita.server.profile

import kz.aita.*
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlin.math.max

internal class PhotoProblem(val code: String) : RuntimeException(code)

/** No original bytes or metadata are persisted. Only JPEG/PNG magic + built-in decoders are accepted. */
internal fun normalizeProfilePhoto(bytes: ByteArray): ByteArray {
    if (bytes.size !in 1..PROFILE_PHOTO_MAX_INPUT_BYTES) throw PhotoProblem("size")
    val jpeg = bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
    val png = bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))
    if (!jpeg && !png) throw PhotoProblem("format")
    try {
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader = ImageIO.getImageReadersByFormatName(if (jpeg) "JPEG" else "PNG").asSequence().firstOrNull()
                ?: throw PhotoProblem("format")
            try {
                reader.setInput(input, true, true)
                val width = reader.getWidth(0); val height = reader.getHeight(0)
                if (width !in 1..16384 || height !in 1..16384 || width.toLong() * height > 32_000_000) throw PhotoProblem("size")
                val parameters = reader.defaultReadParam
                // Bound the decoded raster as well as the compressed file, before allocating it.
                val sample = (max(width, height) + 2047).div(2048).coerceAtLeast(1)
                parameters.setSourceSubsampling(sample, sample, 0, 0)
                val source = reader.read(0, parameters) ?: throw PhotoProblem("format")
                val orientation = if (jpeg) jpegExifOrientation(bytes) else 1
                val rotated = orientProfilePhoto(source, orientation)
                try {
                    val output = BufferedImage(PROFILE_PHOTO_EDGE, PROFILE_PHOTO_EDGE, BufferedImage.TYPE_INT_RGB)
                    try {
                        val g = output.createGraphics()
                        try {
                            g.color = Color.WHITE; g.fillRect(0,0,output.width,output.height)
                            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                            val edge = minOf(rotated.width, rotated.height)
                            val left = (rotated.width-edge)/2; val top=(rotated.height-edge)/2
                            g.drawImage(rotated,0,0,output.width,output.height,left,top,left+edge,top+edge,null)
                        } finally { g.dispose() }
                        val encoded=ByteArrayOutputStream().use { out ->
                            if(!ImageIO.write(output,"JPEG",out)) throw PhotoProblem("format")
                            out.toByteArray()
                        }
                        if(encoded.size !in 1..PROFILE_PHOTO_MAX_OUTPUT_BYTES) throw PhotoProblem("size")
                        return encoded
                    } finally { output.flush() }
                } finally { if(rotated !== source) rotated.flush(); source.flush() }
            } finally { reader.dispose() }
        }
    } catch (problem:PhotoProblem) { throw problem }
    catch (_:Exception) { throw PhotoProblem("format") }
}

/** Handles all eight EXIF mirror/rotation values; invalid offsets are never dereferenced. */
internal fun jpegExifOrientation(bytes:ByteArray):Int {
    fun u8(p:Int)=bytes.getOrNull(p)?.toInt()?.and(255) ?: 0
    fun be16(p:Int)=(u8(p) shl 8) or u8(p+1)
    var p=2
    while(p+4<=bytes.size && u8(p)==255) {
        val marker=u8(p+1)
        if(marker==0xda || marker==0xd9) break
        val length=be16(p+2)
        if(length<2 || p.toLong()+2+length>bytes.size) break
        val end=p+2+length;val start=p+4
        if(marker==0xe1 && start+14<=end && bytes.copyOfRange(start,start+6).contentEquals(byteArrayOf(69,120,105,102,0,0))) {
            val tiff=start+6
            val little=u8(tiff)==73 && u8(tiff+1)==73
            if(!little && !(u8(tiff)==77 && u8(tiff+1)==77)) return 1
            fun u16(i:Int):Int=if(i<tiff || i+2>end) 0 else if(little) u8(i) or (u8(i+1) shl 8) else be16(i)
            fun u32(i:Int):Long {
                if(i<tiff || i+4>end)return -1
                var n=0L
                for(k in 0..3)n=n or (u8(i+k).toLong() shl (if(little) k*8 else (3-k)*8))
                return n
            }
            if(u16(tiff+2)!=42)return 1
            val offset=u32(tiff+4)
            if(offset<8 || offset>end-tiff-2)return 1
            val directory=tiff+offset.toInt();val count=u16(directory)
            if(directory.toLong()+2+count.toLong()*12>end)return 1
            for(i in 0 until count) {
                val entry=directory+2+i*12
                if(u16(entry)==0x112 && u16(entry+2)==3 && u32(entry+4)==1L)return u16(entry+8).takeIf{it in 1..8} ?: 1
            }
        }
        p=end
    }
    return 1
}
internal fun orientProfilePhoto(source:BufferedImage, orientation:Int):BufferedImage {
    if(orientation !in 2..8)return source
    val w=source.width;val h=source.height
    val result=BufferedImage(if(orientation>=5)h else w,if(orientation>=5)w else h,BufferedImage.TYPE_INT_ARGB)
    for(y in 0 until h)for(x in 0 until w) {
        val (dx,dy)=when(orientation) {
            2->w-1-x to y;3->w-1-x to h-1-y;4->x to h-1-y;5->y to x
            6->h-1-y to x;7->h-1-y to w-1-x;else->y to w-1-x
        }
        result.setRGB(dx,dy,source.getRGB(x,y))
    }
    return result
}
