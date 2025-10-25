package kz.aita.server.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

object GenericGoodsItems: Table("generic_goods_items") {
  val id = uuid("id").uniqueIndex()

  val barcode = jsonb("barcode", Json, ListSerializer(String.serializer()))

  val name = text("name")
  val typeIds = text("type_ids").nullable().default(null)

  val categoryIds = text("category_ids").nullable().default(null)

  val supplierIds = text("supplier_ids").nullable().default(null)

  val manufacturerIds = text("manufacturer_ids").nullable().default(null)

  override val primaryKey = PrimaryKey(id)
}