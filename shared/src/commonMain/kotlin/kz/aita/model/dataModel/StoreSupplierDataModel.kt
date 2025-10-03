package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StoreSupplierDataModel(
  val isActive: Boolean,
  val storeId: Long,
  val storeSubId: Long,
  val addedAt: Long
)