package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class UserSettingsDataModel(
  val registrationTime: Long,
  val appLanguage: String,
  val appThemeId: Long,
  val appSizeModeId: Long
)