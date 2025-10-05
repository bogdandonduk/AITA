package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class AppThemeDataModel(
  val id: Long,
  val name: List<LocalizedStringDataModel>
)
