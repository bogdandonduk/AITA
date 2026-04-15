package kz.aita.server.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kz.aita.LocalizedStringDataModel
import kz.aita.PriceDataModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

object Stock: Table("stock") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id")
  val storeId = uuid("store_id")

  val barcode = jsonb("barcode", Json, ListSerializer(String.serializer()))
  val name = jsonb("name", Json, ListSerializer(LocalizedStringDataModel.serializer()))

  val measurementUnitId = text("measurement_unit_id")

  val categoryIds = jsonb("category_ids", Json, ListSerializer(String.serializer()))

  val salePrices = jsonb("sale_prices", Json, ListSerializer(PriceDataModel.serializer()))
  val returnPrices = jsonb("return_prices", Json, ListSerializer(PriceDataModel.serializer()))
  val supplyPrices = jsonb("supply_prices", Json, ListSerializer(PriceDataModel.serializer()))

  val isQuickItem = bool("is_quick_item")

  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}