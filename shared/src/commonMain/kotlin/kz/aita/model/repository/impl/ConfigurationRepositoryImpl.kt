package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kz.aita.core.remote.RemoteConfiguration
import kz.aita.model.dataModel.GlobalConfigurationDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.model.dataModel.response.GetDrawablesConfigResponseDataModel
import kz.aita.model.dataModel.response.GetGlobalConfigurationResponse
import kz.aita.model.dataModel.response.GetStringsResponseDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState

class ConfigurationRepositoryImpl(
  private val genericRemoteService: GenericRemoteService
) : ConfigurationRepository {

  override fun getGlobalConfiguration(): Flow<DataState<GlobalConfigurationDataModel>> {
    return flow {
      try {
        val response = genericRemoteService
          .request<GetGlobalConfigurationResponse, Unit>(
            method = HttpMethod.Get,
            RemoteConfiguration.SERVER_URL,
            RemoteConfiguration.GLOBAL_CONFIGURATION_PATH
          )

        emit(DataState.Success(response.payload))
      } catch (exception: Exception) {
        emit(DataState.Failure(exception))
      }
    }
  }

  override fun getStrings(): Flow<DataState<List<LocalizedStringGroupDataModel>>> {
    return flow {
      try {
        val response = genericRemoteService
          .request<GetStringsResponseDataModel, Unit>(
            method = HttpMethod.Get,
            RemoteConfiguration.SERVER_URL,
            RemoteConfiguration.STRING_RESOURCES_PATH
          )

        emit(DataState.Success(response.payload))
      } catch (exception: Exception) {
        emit(DataState.Failure(exception))
      }
    }
  }

  override fun getDrawableConfiguration(): Flow<DataState<List<StylizedDrawablePathsGroupDataModel>>> {
    return flow {
      try {
        val response = genericRemoteService
          .request<GetDrawablesConfigResponseDataModel, Unit>(
            method = HttpMethod.Get,
            RemoteConfiguration.SERVER_URL,
            RemoteConfiguration.DRAWABLE_CONFIGURATION_PATH
          )

        emit(DataState.Success(response.payload))
      } catch (exception: Exception) {
        emit(DataState.Failure(exception))
      }
    }
  }

  override fun getSvgDrawable(
    key: Long,
    themeId: Long
  ): Flow<DataState<String>> {
    return flow {
      try {
        val response = genericRemoteService
          .request<String, Unit>(
            method = HttpMethod.Get,
            RemoteConfiguration.SERVER_URL,
            RemoteConfiguration.DRAWABLE_SVG_RESOURCES_PATH + key + themeId + ".svg"
          )

        emit(DataState.Success(response))
      } catch (exception: Exception) {
        emit(DataState.Failure(exception))
      }
    }
  }
}