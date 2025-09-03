package kz.aita.model.dataModel

data class EmployeeDataModel(
  val id: Long,
  val email: String,
  val countryCode: String,
  val phoneNumber: String,
  val firstName: String,
  val lastName: String,
  val storeId: Long,
  val storeSubId: Long,
  val storeJob: StoreJobDataModel,
  val salary: String
)