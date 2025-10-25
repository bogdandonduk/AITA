package kz.aita.server.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

object Stores: Table("stores") {
  val id = uuid("id").uniqueIndex()
  val userIds = jsonb("user_ids", Json, ListSerializer(String.serializer()))
  val typeIds = text("type_ids").nullable().default(null)

  val name = text("name")
  val alias = text("alias").nullable().default(null)
  val description = text("description").nullable().default(null)
  val companyForms = text("company_forms").nullable().default(null)

  val location = text("location").nullable().default(null)
  val phoneNumbers = text("phone_numbers").nullable().default(null)
  val emails = text("emails").nullable().default(null)

  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}