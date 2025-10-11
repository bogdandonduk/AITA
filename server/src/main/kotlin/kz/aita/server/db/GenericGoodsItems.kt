package kz.aita.server.db

import org.jetbrains.exposed.sql.Table

object GenericGoodsItems: Table("generic_goods_items") {
  val id = uuid("id").uniqueIndex()

  val barcode = text("barcode").nullable().default(null)

  val name = text("name")
  val typeIds = text("type_ids").nullable().default(null)

  val categoryIds = text("category_ids").nullable().default(null)

  val supplierIds = text("supplier_ids").nullable().default(null)

  val manufacturerIds = text("manufacturer_ids").nullable().default(null)

  override val primaryKey = PrimaryKey(id)
}