package kz.aita.server

import kz.aita.*
import org.jetbrains.exposed.sql.*
import java.util.UUID
import java.util.zip.GZIPInputStream

internal object OpenGoodsCatalogue : Table("open_goods_catalogue") {
    val id = uuid("id")
    val code = text("code").uniqueIndex()
    val sortName = text("sort_name")
    val searchText = text("search_text")
    val payload = text("payload")
    override val primaryKey = PrimaryKey(id)
}
internal object PublicCatalogueImports : Table("public_catalogue_imports") {
    val sourceKey = text("source_key")
    val rowCount = integer("row_count")
    override val primaryKey = PrimaryKey(sourceKey)
}
internal const val OPEN_GOODS_RESOURCE = "/catalogue/open-goods.jsonl.gz"
private const val OPEN_GOODS_SNAPSHOT = "open-food-facts:2026-09-20:1"
internal fun openGoodsCatalogueBytes(): ByteArray =
    checkNotNull(OpenGoodsCatalogue.javaClass.getResourceAsStream(OPEN_GOODS_RESOURCE)).use { it.readBytes() }

/** Packaged snapshot only: a restart never fetches internet data or changes store-owned records. */
internal fun seedOpenGoodsInsideTransaction() {
    if (PublicCatalogueImports.selectAll().where { PublicCatalogueImports.sourceKey eq OPEN_GOODS_SNAPSHOT }.any()) return
    var count = 0
    GZIPInputStream(openGoodsCatalogueBytes().inputStream()).bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() }.chunked(250).forEach { batch ->
            val products = batch.map { jsonBase.decodeFromString<GenericGoodsItemDataModel>(it) }
            products.forEach(::validateOpenGoodsEntry)
            OpenGoodsCatalogue.batchInsert(products, ignore = true, shouldReturnGeneratedValues = false) { item ->
                this[OpenGoodsCatalogue.id] = UUID.fromString(item.id)
                this[OpenGoodsCatalogue.code] = item.barcode!!.single()
                this[OpenGoodsCatalogue.sortName] = item.name.first().value.lowercase()
                this[OpenGoodsCatalogue.searchText] = (item.id + " " + item.barcode.orEmpty().joinToString(" ") + " " + item.name.joinToString(" ") { it.value }).lowercase()
                this[OpenGoodsCatalogue.payload] = jsonBase.encodeToString(GenericGoodsItemDataModel.serializer(), item)
            }
            count += products.size
        }
    }
    require(count > 0)
    PublicCatalogueImports.insert { it[sourceKey] = OPEN_GOODS_SNAPSHOT; it[rowCount] = count }
}
internal fun validateOpenGoodsEntry(item: GenericGoodsItemDataModel) {
    UUID.fromString(item.id)
    require(item.barcode?.size == 1 && item.barcode.orEmpty().single().isLikelyStandardGoodsItemBarcode())
    require(item.name.isNotEmpty() && item.name.all { it.value.isNotBlank() && it.value.length <= 500 })
    require(item.supplierIds.isNullOrEmpty() && item.manufacturerIds.isNullOrEmpty())
    val source = requireNotNull(item.catalogueSource)
    require(source.license == "ODbL-1.0" && source.name in setOf("Open Food Facts", "Open Beauty Facts"))
    require(source.url.matches(Regex("https://world\\.open(food|beauty)facts\\.org/product/[0-9]+")))
}

/** Filter and limit in PostgreSQL; importing more templates does not hydrate them on clients. */
internal fun searchOpenGoodsInsideTransaction(barcodes: List<String>, tokens: List<String>, limit: Int): List<GenericGoodsItemDataModel> {
    val query = OpenGoodsCatalogue.selectAll()
    if (barcodes.isNotEmpty()) query.andWhere { OpenGoodsCatalogue.code inList barcodes }
    tokens.forEach { token -> query.andWhere { OpenGoodsCatalogue.searchText like "%$token%" } }
    return query.orderBy(OpenGoodsCatalogue.sortName to SortOrder.ASC, OpenGoodsCatalogue.id to SortOrder.ASC)
        .limit(limit.coerceIn(1, 10200)).map { jsonBase.decodeFromString<GenericGoodsItemDataModel>(it[OpenGoodsCatalogue.payload]) }
}
