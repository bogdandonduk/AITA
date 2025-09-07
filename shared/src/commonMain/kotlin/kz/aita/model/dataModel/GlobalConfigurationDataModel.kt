package kz.aita.model.dataModel

data class GlobalConfigurationDataModel(
  val countries: List<CountryDataModel>,
  val companyForms: List<CompanyFormDataModel>,
  val vectorDrawableResourcesPath: String,
  val stringResourcesPath: String
)
