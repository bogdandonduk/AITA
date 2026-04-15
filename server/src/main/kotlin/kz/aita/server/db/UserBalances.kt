package kz.aita.server.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kz.aita.BalanceHistoryEntryDataModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

object UserBalances: Table("user_balances") {
  val id = uuid("id").uniqueIndex()
  val userId = uuid("user_id").uniqueIndex()
  val value = text("value")
  val currencyCode = text("currency_code")

  val history = jsonb("history", Json, ListSerializer(BalanceHistoryEntryDataModel.serializer()))

  override val primaryKey = PrimaryKey(id)
}