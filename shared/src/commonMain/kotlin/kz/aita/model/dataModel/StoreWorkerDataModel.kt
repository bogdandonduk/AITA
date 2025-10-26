package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class WorkerDataModel(
  val userId: String,
  val storeId: String,
  val typeId: String,
  val privilegeModeId: String,
  val isActive: Boolean,
  val salary: String,
  val salaryCurrency: String,
  val addedAt: Long
)