package kz.aita.server.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kz.aita.model.dataModel.WorkerPrivilegeModeDataModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

object Workers: Table("workers") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id")
  val workerTypeId = uuid("type_id")
  val placeId = uuid("place_id")
  val privilegeModes = jsonb("privilege_modes", Json, ListSerializer(WorkerPrivilegeModeDataModel.serializer()))
  val phoneNumber = varchar("phone_number", 32).uniqueIndex()
  val email = varchar("phone_number", 255).uniqueIndex()
  val firstName = varchar("first_name", 255)
  val lastName = varchar("last_name", 255)
  val salary = text("salary").default("0")
  val salaryCurrencyCode = text("salary_currency_code")
  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}