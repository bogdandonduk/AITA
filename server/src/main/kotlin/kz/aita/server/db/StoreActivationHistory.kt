package kz.aita.server.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kz.aita.ActivationHistoryEntryDataModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

object StoreActivationHistory: Table("store_activation_history") {
  val id = uuid("id").uniqueIndex()
  val storeId = uuid("store_id").uniqueIndex()
  val history = jsonb("history", Json, ListSerializer(ActivationHistoryEntryDataModel.serializer()))

  override val primaryKey = PrimaryKey(id)
}