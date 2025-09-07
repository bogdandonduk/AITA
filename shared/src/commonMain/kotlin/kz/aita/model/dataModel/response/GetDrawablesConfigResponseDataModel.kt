package kz.aita.model.dataModel.response

import kotlinx.serialization.Serializable
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel

@Serializable
data class GetDrawablesConfigResponseDataModel(
  val payload: List<StylizedDrawablePathsGroupDataModel>
)