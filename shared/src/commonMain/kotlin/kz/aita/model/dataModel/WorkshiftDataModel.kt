package kz.aita.model.dataModel

data class WorkshiftDataModel(
  val id: Long,
  val startTime: Long,
  val endTime: Long,
  val employeeId: Long
)