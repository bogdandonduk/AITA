package kz.aita.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object StoreWorkers : Table("store_workers") {
  val id = uuid("id").uniqueIndex()
  val privilegeModeId = integer("privilege_mode_id")
  val isActive = bool("is_active")
  val storeId = varchar("store_id", 255)
  val storeSubId = varchar("store_sub_id", 255)
  val salary = varchar("salary", 255)
  val salaryCurrency = double("salary_currency")
  val addedAt = timestamp("added_at").defaultExpression(CurrentTimestamp)

  override val primaryKey = PrimaryKey(id)
}