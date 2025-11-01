package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class ParameterDataModel(
  val name: List<LocalizedStringDataModel>,
  val value: String,
  val length: Int,
  val number: Boolean,
  val nonLetterSymbolsEnabled: Boolean
)
