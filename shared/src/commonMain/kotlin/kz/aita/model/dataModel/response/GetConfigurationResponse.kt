package kz.aita.model.dataModel.response

import kotlinx.serialization.Serializable
import kz.aita.model.dataModel.GlobalAppConfigurationDataModel

@Serializable
data class GetConfigurationResponse(
  val payload: GlobalAppConfigurationDataModel
)