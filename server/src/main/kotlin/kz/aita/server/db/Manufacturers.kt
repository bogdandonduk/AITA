package kz.aita.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object Manufacturers: Table("manufacturers") {

  val id = uuid("id").uniqueIndex()
  val userIds = text("user_ids").nullable().default(null)
  val typeIds = text("type_ids").nullable().default(null)
  val categoryIds = text("category_ids").nullable().default(null)

  val name = text("name")

  val phoneNumbers = text("phone_numbers").nullable().default(null)
  val emails = text("emails").nullable().default(null)

  val addedAt = timestamp("added_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}
