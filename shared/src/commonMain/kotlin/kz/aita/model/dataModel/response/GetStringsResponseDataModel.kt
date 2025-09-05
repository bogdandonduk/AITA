package kz.aita.model.dataModel.response

import kotlinx.serialization.Serializable
import kz.aita.model.dataModel.LocalizedStringGroupDataModel

@Serializable
data class GetStringsResponseDataModel(
  val payload: List<LocalizedStringGroupDataModel>
)
