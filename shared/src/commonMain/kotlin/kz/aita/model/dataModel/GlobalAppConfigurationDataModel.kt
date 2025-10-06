package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GlobalAppConfigurationDataModel(
  val appName: String,
  val serverUrl: String,
  val globalConfigurationPath: String,
  val exceptionConfigurationPath: String,
  val logInPath: String,
  val signUpPath: String,
  val stringResourcesPath: String,
  val dimensionResourcesPath: String,
  val colorResourcesPath: String,
  val drawableResourcesConfigurationPath: String,
  val drawableResourcesPath: String,
  val companyForms: List<CompanyFormDataModel>,
  val countries: List<CountryDataModel>,
  val languages: List<AppLanguageDataModel>,
  val themes: List<AppThemeDataModel>,
  val goodsItemsQuantityUnits: List<QuantityDataModel>
)
