package kz.aita.server.db

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object StoreUsers: Table("store_users") {
  val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
  val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)

  override val primaryKey = PrimaryKey(storeId, userId)
}