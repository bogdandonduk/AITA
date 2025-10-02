package kz.aita.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp

object Users : Table("users") {
  val id = uuid("id")                             // We'll set UUIDs in code
  val email = varchar("email", 255).uniqueIndex()
  val phoneNumber = varchar("phone_number", 255).uniqueIndex()
  val firstName = varchar("first_name", 255).uniqueIndex()
  val lastName = varchar("last_name", 255).uniqueIndex()
  val countryLocale = varchar("country_locale", 255).uniqueIndex()
  val passwordHash = varchar("password_hash", 100) // BCrypt ~60 chars, give some headroom
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)
  override val primaryKey = PrimaryKey(id)
}