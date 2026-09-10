package kz.aita

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** Native PDF writer/reader round-trip, on a device. Does not contact AITA or modify business data. */
@RunWith(AndroidJUnit4::class)
class AitaReceiptPdfAndroidTest {
    private fun <T> withRenderer(document: AitaPdfDocument, block: (PdfRenderer) -> T): T {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("aita-unicode-", ".pdf", context.cacheDir)
        try {
            file.writeBytes(renderAitaPdfDocument(document))
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try { PdfRenderer(descriptor) } catch (failure: Exception) { descriptor.close(); throw failure }
            try { return block(renderer) } finally { renderer.close() }
        } finally { file.delete() }
    }
    @Test fun unicodeReceiptCanBeOpenedAndRenderedNatively() {
        withRenderer(AitaPdfDocument(listOf(
            AitaPdfBlock("ТОО Happy Kids", AitaPdfRole.Store),
            AitaPdfBlock("Қазақстан, Астана", AitaPdfRole.Address),
            AitaPdfBlock("Тауар чегі / Товарный чек", AitaPdfRole.Title),
            AitaPdfBlock("Әә Ғғ Ққ Ңң Өө Ұұ Үү Һһ Іі"), AitaPdfBlock("6600.00 ₸", AitaPdfRole.Total)
        ))) { renderer ->
            assertEquals(1, renderer.pageCount)
            val page = renderer.openPage(0)
            try {
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    assertTrue(pixels.count { Color.red(it) < 128 && Color.alpha(it) > 0 } > 100)
                } finally { bitmap.recycle() }
            } finally { page.close() }
        }
    }
    @Test fun longReceiptExportsMultipleReadablePages() {
        withRenderer(AitaPdfDocument((1..250).map { AitaPdfBlock("Тауар $it: 2200.00 ₸") })) { renderer ->
            assertTrue(renderer.pageCount > 1)
            for (index in 0 until renderer.pageCount) {
                val page = renderer.openPage(index)
                try { assertEquals(226, page.width); assertTrue(page.height in 280..842) } finally { page.close() }
            }
        }
    }
}
