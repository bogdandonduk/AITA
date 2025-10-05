package kz.aita.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp

object Users: Table("users") {
  val id = uuid("id").uniqueIndex()
  val phoneNumber = varchar("phone_number", 255).uniqueIndex()
  val email = varchar("email", 255).uniqueIndex()
  val firstName = varchar("first_name", 255)
  val lastName = varchar("last_name", 255)
  val countryLocale = varchar("country_locale", 255)
  val storeWorkerAccountId = uuid("store_worker_account_id").nullable()
  val storeSupplierAccountId = uuid("store_supplier_account_id").nullable()
  val passwordHash = varchar("password_hash", 100) // BCrypt ~60 chars, give some headroom
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active")

  override val primaryKey = PrimaryKey(id)
}