package kz.aita.model.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kz.aita.model.wrapper.DataStateFlow
import kz.aita.model.dataModel.GlobalConfigurationDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedColorGroupDataModel
import kz.aita.model.dataModel.StylizedDimensionGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.model.wrapper.DataState
import kotlin.coroutines.CoroutineContext

interface ConfigurationRepository {

  val globalAppConfigurationState: DataStateFlow<GlobalConfigurationDataModel>
  val stringsState: DataStateFlow<List<LocalizedStringGroupDataModel>>
  val dimensionsState: DataStateFlow<List<StylizedDimensionGroupDataModel>>
  val colorsState: DataStateFlow<List<StylizedColorGroupDataModel>>
  val drawablesState: DataStateFlow<List<StylizedDrawablePathsGroupDataModel>>

  fun getGlobalConfiguration(loadAll: Boolean = true)

  fun getStrings()

  fun getDimensions()

  fun getColors()

  fun getDrawables()

  fun getDrawable(key: Long, themeId: Long, format: String): Flow<DataState<String>>

  fun getDrawable(name: String, format: String): Flow<DataState<String>>
}
