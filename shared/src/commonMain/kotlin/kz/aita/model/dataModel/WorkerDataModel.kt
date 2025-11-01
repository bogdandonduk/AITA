package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class WorkerDataModel(
  val id: String,
  val userId: String,
  val workerTypeId: String,
  val placeId: String,
  val privilegeModes: List<WorkerPrivilegeModeDataModel>,
  val phoneNumber: String,
  val emails: String,
  val firstName: String,
  val lastName: String,
  val salary: String,
  val salaryCurrencyCode: String,
  val addedAt: Long,
  val isActive: Boolean
)