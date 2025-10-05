package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class AppLanguageDataModel(
  val language: String,
  val name: List<LocalizedStringDataModel>,
  val flagDrawablePath: String
)
