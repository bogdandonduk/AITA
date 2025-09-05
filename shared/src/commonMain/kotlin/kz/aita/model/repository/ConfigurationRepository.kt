package kz.aita.model.repository

import kotlinx.coroutines.flow.Flow
import kz.aita.model.dataModel.GlobalAppConfigurationDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.model.wrapper.DataState

interface ConfigurationRepository {

  fun getConfiguration(): Flow<DataState<GlobalAppConfigurationDataModel>>

  fun getStrings(): Flow<DataState<List<LocalizedStringGroupDataModel>>>

  fun getDrawables(): Flow<DataState<List<StylizedDrawablePathsGroupDataModel>>>
}
