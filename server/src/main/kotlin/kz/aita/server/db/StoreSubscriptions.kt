package kz.aita.server.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kz.aita.SubscriptionDataModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

object StoreSubscriptions: Table("store_subscriptions") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").uniqueIndex()
  val history = jsonb("history", Json, ListSerializer(SubscriptionDataModel.serializer()))

  override val primaryKey = PrimaryKey(id)
}