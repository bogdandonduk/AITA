package kz.aita.model.dataModel

import kotlinx.serialization.Serializable
import kz.aita.core.Searchable

@Serializable
data class StoreDataModel(
  val id: String,
  val userIds: List<String>,
  val storeTypeIds: List<String>,
  val name: List<LocalizedStringDataModel>,
  val alias: List<LocalizedStringDataModel>,
  val description: List<LocalizedStringDataModel>,
  val companyForms: List<CompanyFormDataModel>,
  val location: LocationDataModel,
  val phoneNumbers: List<String>,
  val emails: List<String>,
  val countryLocales: List<String>,
  val createdAt: Long,
  val isActive: Boolean,
): Searchable {

  override val exactSearchOperands: List<String>
    get() {
      return mutableListOf<String>()
        .apply {
          name.forEach {
            add(it.value)
          }

          alias?.forEach {
            add(it.value)
          }

          description?.forEach {
            add(it.value)
          }

          location?.name?.let { add(it) }
          location?.postalIndex?.let { add(it) }
          location?.latitude?.let { add(it.toString()) }
          location?.longitude?.let { add(it.toString()) }

          phoneNumbers?.forEach { add(it) }
          emails?.forEach { add(it) }
        }
    }
  override val containsSearchOperands: List<String>
    get() {
      return mutableListOf<String>()
        .apply {
          name.forEach {
            add(it.value)
          }

          alias?.forEach {
            add(it.value)
          }

          description?.forEach {
            add(it.value)
          }

          location?.name?.let { add(it) }
          location?.postalIndex?.let { add(it) }
          location?.latitude?.let { add(it.toString()) }
          location?.longitude?.let { add(it.toString()) }

          phoneNumbers?.forEach { add(it) }
          emails?.forEach { add(it) }
        }
    }
  override val uniqueSearchOperands: List<String>
    get() {
      return emptyList()
    }
}
