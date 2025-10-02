package kz.aita.server.db

import kotlinx.serialization.KSerializer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.*        // Instant/LocalDateTime columns
import org.jetbrains.exposed.sql.json.jsonb
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object RefreshSessions : Table("refresh_sessions") {
  val id = uuid("id")
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
  val tokenHash = char("token_hash", 64).index()  // hex(sha256) = 64 chars
  val deviceId = varchar("device_id", 64).nullable()
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