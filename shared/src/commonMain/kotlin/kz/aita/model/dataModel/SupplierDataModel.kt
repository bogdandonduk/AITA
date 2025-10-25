package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class SupplierDataModel(
  val id: String,
  val typeIds: List<String>?,
  val name: List<LocalizedStringDataModel>,
  val phoneNumbers: List<String>?,
  val emails: List<String>?,
  val addedAt: Long,
  val isActive: Boolean
)