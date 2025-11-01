package kz.aita.server.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kz.aita.model.dataModel.CompanyFormDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.LocationDataModel
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb

object Stores: Table("stores") {
  val id = uuid("id").uniqueIndex()
  val ownerUserIds = jsonb("owner_user_ids", Json, ListSerializer(String.serializer()))
  val storeTypeIds = jsonb("store_type_ids", Json, ListSerializer(String.serializer()))

  val name = jsonb("name", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val alias = jsonb("alias", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val description = jsonb("description", Json, ListSerializer(LocalizedStringDataModel.serializer()))
  val companyForms = jsonb("company_forms", Json, ListSerializer(CompanyFormDataModel.serializer()))

  val location = jsonb("location", Json, LocationDataModel.serializer())
  val phoneNumbers = jsonb("phone_numbers", Json, ListSerializer(String.serializer()))
  val emails = jsonb("emails", Json, ListSerializer(String.serializer()))
  val countryLocales = jsonb("country_locales", Json, ListSerializer(String.serializer()))

  val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
  val isActive = bool("is_active").default(true)

  override val primaryKey = PrimaryKey(id)
}