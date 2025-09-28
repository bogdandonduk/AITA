package kz.aita.core

import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedColorGroupDataModel
import kz.aita.model.dataModel.StylizedDimensionGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel

fun List<LocalizedStringGroupDataModel>?.extractString(id: Long, language: String): String {
  return this?.run { find { it.id == id }?.values?.find { it.language == language }?.value } ?: ""
}

fun List<StylizedDimensionGroupDataModel>.extractValue(id: Long, sizeModeId: Long): Float {
  return find { it.id == id }!!.values.find { it.sizeModeId == -1L || it.sizeModeId == sizeModeId }!!.value
}

fun List<StylizedColorGroupDataModel>.extractColor(id: Long, themeId: Long): String {
  return find { it.id == id }!!.values.find { it.themeId == -1L || it.themeId == themeId }!!.valueHex
}

fun List<StylizedDrawablePathsGroupDataModel>.extractPath(id: Long, themeId: Long): String {
  return find { it.id == id }!!.values.find { it.themeId == -1L || it.themeId == themeId }!!.path
}

fun getFullDrawableResourceUrl(path: String): String {
  return configurationRepository.globalAppConfigurationState.payloadValue.run {
    serverUrl + drawableResourcesPath
  } + "/$path"
}






