package kz.aita.server.db

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

object RefreshSessions: Table("refresh_sessions") {
  val id = uuid("id")
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val tokenHash = char("token_hash", 64).index()  // hex(sha256) = 64 chars
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val expiresAt = timestamp("expires_at")
  val rotatedFrom = uuid("rotated_from").nullable()
  val revokedAt = timestamp("revoked_at").nullable()

  val meta = jsonb<Map<String, String>>(
    name = "meta",
    jsonConfig = Json,
    kSerializer = MapSerializer(String.serializer(), String.serializer())
  ).nullable()
  override val primaryKey = PrimaryKey(id)
}