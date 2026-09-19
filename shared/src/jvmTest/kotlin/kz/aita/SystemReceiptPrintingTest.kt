package kz.aita

import java.awt.image.BufferedImage
import java.awt.print.Printable
import kotlin.test.*

class SystemReceiptPrintingTest {
    @Test fun driverPagesUseExactRollWidthsAndShortReceiptHeight() {
        for (width in listOf(58, 80)) {
            val document = AitaPdfDocument(listOf(AitaPdfBlock("125 ₸ Қазақша"))).forReceiptPaper(width)
            val pages = SystemReceiptPages(document)
            val format = pages.getPageFormat(0)
            assertEquals(width * 72.0 / 25.4, format.width, .01)
            assertTrue(format.height < 100)
            val image = BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            try {
                assertEquals(Printable.PAGE_EXISTS, pages.getPrintable(0).print(graphics, format, 0))
                assertEquals(Printable.NO_SUCH_PAGE, pages.getPrintable(0).print(graphics, format, 1))
            } finally { graphics.dispose() }
        }
    }
    @Test fun longReceiptsPaginateAtRollWidthAndKeepBarcode() {
        val blocks = List(200) { AitaPdfBlock("Item $it · 125 ₸") } +
            AitaPdfBlock("", barcodePayload = transactionReceiptBarcodePayload("00000000-0000-0000-0000-000000000001"))
        val pages = SystemReceiptPages(AitaPdfDocument(blocks).forReceiptPaper(58))
        assertTrue(pages.getNumberOfPages() > 1)
        repeat(pages.getNumberOfPages()) { assertEquals(58 * 72.0 / 25.4, pages.getPageFormat(it).width, .01) }
    }
}
