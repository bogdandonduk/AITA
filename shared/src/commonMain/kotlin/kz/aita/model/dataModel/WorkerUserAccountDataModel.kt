package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class WorkerUserAccountDataModel(
  val id: Long,
  val isActive: Boolean,
  val phoneNumber: String,
  val email: String,
  val firstName: String,
  val lastName: String,
  val countryLocale: String,
  val storeId: Long,
  val storeSubId: Long,
  val storeJob: StoreJobDataModel,
  val salary: String,
  val salaryCurrency: String,
  val addedAt: Long
)