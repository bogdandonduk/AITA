package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class WorkerPrivilegeModeDataModel(
  val id: String,
  val parameters: List<ParameterDataModel>
)
