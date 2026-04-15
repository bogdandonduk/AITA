package kz.aita.server.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

object RealtimeUpdates: Table("realtime_updates") {

  val userId = uuid("userId").uniqueIndex()
  val updateIds = jsonb("updateIds", Json, ListSerializer(String.serializer()))

  override val primaryKey = PrimaryKey(userId)
}