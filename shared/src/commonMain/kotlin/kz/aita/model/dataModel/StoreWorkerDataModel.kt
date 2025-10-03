package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StoreWorkerDataModel(
  val privilegeModeId: Long,
  val isActive: Boolean,
  val storeId: Long,
  val storeSubId: Long,
  val salary: Double,
  val salaryCurrency: String,
  val addedAt: Long
)