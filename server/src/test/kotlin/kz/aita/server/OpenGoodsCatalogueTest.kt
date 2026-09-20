package kz.aita.server

import kz.aita.*
import java.util.zip.GZIPInputStream
import kotlin.test.*

class OpenGoodsCatalogueTest {
    @Test fun snapshotContainsValidDistinctProductsAndKeepsAttribution() {
        val rows = GZIPInputStream(openGoodsCatalogueBytes().inputStream()).bufferedReader().useLines { lines ->
            lines.map { jsonBase.decodeFromString<GenericGoodsItemDataModel>(it) }.toList()
        }
        assertTrue(rows.isNotEmpty())
        rows.forEach(::validateOpenGoodsEntry)
        assertEquals(rows.size, rows.map { it.id }.toSet().size)
        assertEquals(rows.size, rows.map { it.barcode!!.single() }.toSet().size)
    }
}
