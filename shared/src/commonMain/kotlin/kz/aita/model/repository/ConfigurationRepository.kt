package kz.aita.model.repository

import kotlinx.coroutines.flow.Flow
import kz.aita.model.dataModel.GlobalConfigurationDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.model.wrapper.DataState

interface ConfigurationRepository {

  fun getGlobalConfiguration(): Flow<DataState<GlobalConfigurationDataModel>>

  fun getStrings(): Flow<DataState<List<LocalizedStringGroupDataModel>>>

  fun getDrawableConfig(): Flow<DataState<List<StylizedDrawablePathsGroupDataModel>>>

  fun getSvgDrawable(key: Long, themeId: Long): Flow<DataState<String>>
}
