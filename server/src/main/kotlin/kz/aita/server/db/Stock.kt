package kz.aita.server.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.PriceDataModel
import kz.aita.model.dataModel.QuantityDataModel
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

  val quantity = jsonb("quantity", Json, QuantityDataModel.serializer())

  val categoryIds = jsonb("category_ids", Json, ListSerializer(String.serializer()))

  val salePrices = jsonb("sale_prices", Json, ListSerializer(PriceDataModel.serializer()))
  val returnPrices = jsonb("return_prices", Json, ListSerializer(PriceDataModel.serializer()))
  val supplyPrices = jsonb("supply_prices", Json, ListSerializer(PriceDataModel.serializer()))

  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}