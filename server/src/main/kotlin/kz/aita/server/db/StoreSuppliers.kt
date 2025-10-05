package kz.aita.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object StoreSuppliers: Table("store_workers") {
  val id = uuid("id").uniqueIndex()
  val isActive = bool("is_active")
  val storeId = varchar("store_id", 255)
  val storeSubId = varchar("store_sub_id", 255)
  val addedAt = timestamp("added_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}