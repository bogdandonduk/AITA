package kz.aita.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object Stores: Table("stores") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id")

  val name = text("name")
  val alias = text("alias").nullable()
  val description = text("description").nullable()
  val companyForm = text("company_form")

  val location = text("location")
  val phoneNumbers = text("phone_numbers")
  val emails = text("emails")

  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active")

  override val primaryKey = PrimaryKey(id)
}