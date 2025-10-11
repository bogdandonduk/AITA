package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class CompanyFormParameterDataModel(
  val name: List<LocalizedStringDataModel>,
  val length: Int,
  val number: Boolean,
  val nonLetterSymbolsEnabled: Boolean
)
