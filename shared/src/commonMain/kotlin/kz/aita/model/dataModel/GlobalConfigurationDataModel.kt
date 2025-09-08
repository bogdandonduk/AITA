package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GlobalConfigurationDataModel(
  val stringResourcesPath: String,
  val drawableConfigurationPath: String,
  val drawableResourcesPath: String,
  val drawableSvgResourcesPath: String,
  val drawablePngResourcesPath: String,
  val companyForms: List<CompanyFormDataModel>,
  val countries: List<CountryDataModel>
)
