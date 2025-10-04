package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StoreSupplierDataModel(
  val isActive: Boolean,
  val storeId: String,
  val storeSubId: String,
  val addedAt: Long
)