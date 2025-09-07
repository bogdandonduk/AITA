package kz.aita.model.dataModel.response

import kotlinx.serialization.Serializable
import kz.aita.model.dataModel.GlobalConfigurationDataModel

@Serializable
data class GetGlobalConfigurationResponse(
  val payload: GlobalConfigurationDataModel
)