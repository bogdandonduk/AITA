package kz.aita.model.repository

import kotlinx.coroutines.flow.Flow
import kz.aita.model.dataModel.*
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.DataStateFlow
import kz.aita.model.wrapper.DataStateFlowNonNull

interface ConfigurationRepository {

  val globalAppConfigurationState: DataStateFlowNonNull<GlobalAppConfigurationDataModel>
  val stringsState: DataStateFlow<List<LocalizedStringGroupDataModel>>
  val dimensionsState: DataStateFlow<List<StylizedDimensionGroupDataModel>>
  val colorsState: DataStateFlow<List<StylizedColorGroupDataModel>>
  val drawablesState: DataStateFlow<List<StylizedDrawablePathsGroupDataModel>>
  val exceptionsState: DataStateFlow<List<ExceptionDataModel>>

  fun getGlobalConfiguration(loadAll: Boolean = true)

  fun getStrings()

  fun getDimensions()

  fun getColors()

  fun getDrawables()

  fun getDrawable(key: Long, themeId: Long, format: String): Flow<DataState<String>>

  fun getDrawable(name: String, format: String): Flow<DataState<String>>

  fun getExceptions()
}
