package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class ExceptionDataModel(
  val id: Long,
  val message: String
)