package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StoreWorkerDataModel(
  val privilegeModeId: Int,
  val isActive: Boolean,
  val storeId: String,
  val salary: String,
  val salaryCurrency: String,
  val addedAt: Long
)