package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class WorkshiftDataModel(
  val id: Long,
  val startTime: Long,
  val endTime: Long,
  val employeeId: Long
)