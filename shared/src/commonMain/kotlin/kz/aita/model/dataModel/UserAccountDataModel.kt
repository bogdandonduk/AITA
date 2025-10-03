package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class UserAccountDataModel(
  val id: Long,
  val phoneNumber: String,
  val email: String,
  val firstName: String,
  val lastName: String,
  val countryLocale: String,
  val storeWorkerAccount: StoreWorkerDataModel?,
  val storeSupplierAccount: StoreSupplierDataModel?
)