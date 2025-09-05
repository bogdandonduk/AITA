package kz.aita.model.repository.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kz.aita.model.dataModel.GlobalAppConfigurationDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.model.dataModel.response.GetStringsResponseDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState

class ConfigurationRepositoryImpl(
  private val genericRemoteService: GenericRemoteService
) : ConfigurationRepository {

  override fun getConfiguration(): Flow<DataState<GlobalAppConfigurationDataModel>> {
    return flow {
      genericRemoteService
    }
  }

  override fun getStrings(): Flow<DataState<List<LocalizedStringGroupDataModel>>> {
    return flow {

    }
  }

  override fun getDrawables(): Flow<DataState<List<StylizedDrawablePathsGroupDataModel>>> {
    return flow {

    }
  }
}