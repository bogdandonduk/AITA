package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class SupplierDataModel(
  val typeId: String,
  val isActive: Boolean,
  val clientId: String,
  val addedAt: Long
)