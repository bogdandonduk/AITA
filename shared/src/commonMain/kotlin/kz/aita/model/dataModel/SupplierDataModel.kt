package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class SupplierDataModel(
  val id: String,
  val typeId: String,
  val phoneNumbers: String,
  val emails: String,
  val addedAt: Long,
  val isActive: Boolean
)