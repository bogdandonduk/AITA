package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class GlobalAppConfigurationDataModel(
  val appName: String,
  val serverUrl: String,
  val globalAppConfigurationPath: String,
  val logInPath: String,
  val signUpPath: String,
  val refreshPath: String,
  val logOutPath: String,
  val getUserPath: String,
  val updateUserPath: String,
  val getStoresPath: String,
  val addStoresPath: String,
  val updateStoresPath: String,
  val deleteStoresPath: String,
  val getStockPath: String,
  val addGoodsItemPath: String,
  val updateGoodsItemPath: String,
  val deleteGoodsItemPath: String,
  val getGenericGoodsItemsPath: String,
  val getGenericGoodsCategoriesPath: String,
  val getSuppliersPath: String,
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
