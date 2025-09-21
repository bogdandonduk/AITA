package kz.aita.core

import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedColorGroupDataModel
import kz.aita.model.dataModel.StylizedDimensionGroupDataModel

fun List<LocalizedStringGroupDataModel>.extractString(id: Long, language: String): String {
  return find { it.id == id }!!.values.find { it.language == language }!!.value
}

fun List<StylizedDimensionGroupDataModel>.extractDimensionScreenWidthDivisor(id: Long, sizeModeId: Long): Float {
  return find { it.id == id }!!.values.find { it.sizeModeId == sizeModeId }!!.screenWidthDivisor
}

fun List<StylizedColorGroupDataModel>.extractColor(id: Long, themeId: Long): String {
  return find { it.id == id }!!.values.find { it.themeId == themeId }!!.valueHex
}





