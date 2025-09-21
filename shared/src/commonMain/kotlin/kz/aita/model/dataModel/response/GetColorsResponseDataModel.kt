package kz.aita.model.dataModel.response

import kotlinx.serialization.Serializable
import kz.aita.model.dataModel.StylizedColorGroupDataModel

@Serializable
class GetColorsResponseDataModel(
  val payload: List<StylizedColorGroupDataModel>
)