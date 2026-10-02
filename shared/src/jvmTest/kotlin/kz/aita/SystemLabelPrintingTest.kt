package kz.aita

import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.print.Printable
import javax.imageio.ImageIO
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.BinaryBitmap
import com.google.zxing.common.HybridBinarizer

class SystemLabelPrintingTest {
    @Test fun labelsUseTheirOwnPaperSizeAndBoundedCopyCount() {
        val label = StockItemLabelDataModel("Мёд / Бал / Honey", "4006381333931", "3500 ₸", storeName = "AITA", copies = 5)
        val pages = SystemLabelPages(label)
        assertEquals(5, pages.numberOfPages)
        val format = pages.getPageFormat(0)
        assertEquals(58 * 72.0 / 25.4, format.width, .01)
        assertEquals(40 * 72.0 / 25.4, format.height, .01)
        val image = BufferedImage(464, 320, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE; graphics.fillRect(0, 0, 464, 320)
            graphics.scale(203.0 / 72, 203.0 / 72)
            assertEquals(Printable.PAGE_EXISTS, pages.getPrintable(0).print(graphics, format, 0))
            assertEquals(Printable.NO_SUCH_PAGE, pages.getPrintable(0).print(graphics, format, 5))
        } finally { graphics.dispose() }
        assertTrue((0 until image.width).count { image.getRGB(it, 210) and 0xffffff == 0 } > 40)
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels))))
        assertEquals(label.barcode, decoded.text)
        File("build/label-print-fixtures").mkdirs()
        ImageIO.write(image, "png", File("build/label-print-fixtures/label-58x40.png"))
        assertFailsWith<IllegalArgumentException> { SystemLabelPages(label.copy(copies = 0)) }
        assertFailsWith<IllegalArgumentException> { SystemLabelPages(label.copy(copies = 100)) }
        assertFailsWith<IllegalArgumentException> { SystemLabelPages(label.copy(barcode = "")) }
    }
    @Test fun nativeLabelResultNeverFallsThroughToBrowserOrRawAfterSubmission() = runBlocking {
        val oldNative = printStockItemLabelPlatformAction
        val oldRaw = printLabelPrinterBytes
        var rawCalls = 0
        try {
            printLabelPrinterBytes = { rawCalls++; ReceiptPlatformActionResult(true) }
            printStockItemLabelPlatformAction = { ReceiptPlatformActionResult(false, "driver rejected") }
            assertFalse(printStockItemLabel(StockItemLabelDataModel(barcode = "4006381333931")).success)
            assertEquals(0, rawCalls)
            printStockItemLabelPlatformAction = { ReceiptPlatformActionResult(true) }
            assertTrue(printStockItemLabel(StockItemLabelDataModel(barcode = "4006381333931")).success)
            assertEquals(0, rawCalls)
        } finally { printStockItemLabelPlatformAction = oldNative; printLabelPrinterBytes = oldRaw }
    }
}
