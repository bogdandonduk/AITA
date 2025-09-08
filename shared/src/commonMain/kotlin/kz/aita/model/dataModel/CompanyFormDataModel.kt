package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class CompanyFormDataModel(
  val id: Long,
  val name: String,
  val parameters: List<Pair<String, String>>
)