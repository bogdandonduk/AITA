package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class CompanyFormDataModel(
  val id: String,
  val name: List<LocalizedStringDataModel>,
  val parameters: List<CompanyFormParameterDataModel>
)