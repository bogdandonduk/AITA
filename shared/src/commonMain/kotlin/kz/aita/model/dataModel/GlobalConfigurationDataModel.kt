package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GlobalConfigurationDataModel(
  val serverUrl: String,
  val globalConfigurationPath: String,
  val stringResourcesPath: String,
  val dimensionResourcesPath: String,
  val colorResourcesPath: String,
  val drawableResourcesPath: String,
  val companyForms: List<CompanyFormDataModel>,
  val countries: List<CountryDataModel>
)
