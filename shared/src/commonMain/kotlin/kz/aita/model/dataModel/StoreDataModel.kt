package kz.aita.model.dataModel

import kotlinx.serialization.Serializable
import kz.aita.core.Searchable

@Serializable
data class StoreDataModel(
  val id: String,
  val userId: String,
  val name: List<LocalizedStringDataModel>,
  val alias: List<LocalizedStringDataModel>?,
  val description: List<LocalizedStringDataModel>?,
  val companyForm: CompanyFormDataModel,
  val location: LocationDataModel,
  val phoneNumbers: List<String>,
  val emails: List<String>,
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

          add(location.name)
          add(location.postalIndex)
          add(location.latitude.toString())
          add(location.longitude.toString())

          addAll(phoneNumbers)
          addAll(emails)
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

          add(location.name)
          add(location.postalIndex)
          add(location.latitude.toString())
          add(location.longitude.toString())

          addAll(phoneNumbers)
          addAll(emails)
        }
    }
  override val uniqueSearchOperands: List<String>
    get() {
      return emptyList()
    }
}
