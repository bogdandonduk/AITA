package kz.aita.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object Users: Table("users") {
  val id = uuid("id").uniqueIndex()
  val phoneNumber = varchar("phone_number", 32).uniqueIndex()
  val email = varchar("email", 255).uniqueIndex()
  val firstName = varchar("first_name", 255)
  val lastName = varchar("last_name", 255)
  val countryLocale = varchar("country_locale", 64)
  val workerIds = text("worker_ids").nullable().default(null)
  val supplierIds = text("supplier_ids").nullable().default(null)
  val passwordHash = varchar("password_hash", 100) // BCrypt ~60 chars, give some headroom
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}