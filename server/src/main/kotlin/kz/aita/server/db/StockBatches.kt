package kz.aita.server.db

import kotlinx.serialization.json.Json
import kz.aita.GoodsBatchShelfQueueDataModel
import kz.aita.PriceDataModel
import kz.aita.QuantityDataModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

object StockBatches: Table("stock_batches") {
  val id = uuid("id").uniqueIndex()
  val goodsItemId = uuid("goods_item_id")

  val userId = uuid("user_id")
  val storeId = uuid("store_id")
  val supplierId = uuid("supplierId")

  val salePrice = jsonb("sale_price", Json, PriceDataModel.serializer())
  val returnPrice = jsonb("return_price", Json, PriceDataModel.serializer())
  val supplyPrice = jsonb("supply_price", Json, PriceDataModel.serializer())

  val quantity = jsonb("quantity", Json, QuantityDataModel.serializer())

  val supplyTime = timestamp("supply_time")
  val expirationTime = timestamp("expiration_time")
  val shelfQueue = jsonb("shelf_queue", Json, GoodsBatchShelfQueueDataModel.serializer())

  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

  val createdByUserId = uuid("created_by_user_id")

  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}