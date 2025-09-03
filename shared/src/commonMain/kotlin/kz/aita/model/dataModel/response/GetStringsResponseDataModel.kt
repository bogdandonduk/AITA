package kz.aita.model.dataModel.response

import kz.aita.model.dataModel.LocalizedStringGroupDataModel

data class GetStringsResponseDataModel(
  val code: String,
  val values: List<LocalizedStringGroupDataModel>
)
