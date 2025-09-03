package kz.aita.model.dataModel

data class GlobalAppConfigurationDataModel(
  val countries: List<CountryDataModel>,
  val companyForms: List<CompanyFormDataModel>
)
