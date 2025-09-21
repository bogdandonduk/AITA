package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class StylizedColorDataModel(
  val themeId: Long,
  val valueHex: String
)

