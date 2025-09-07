package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kz.aita.core.remote.DRAWABLE_CONFIG_PATH
import kz.aita.core.remote.GLOBAL_CONFIG_PATH
import kz.aita.core.remote.DRAWABLE_RESOURCES_PATH
import kz.aita.core.remote.SERVER_URL
import kz.aita.core.remote.STRING_RESOURCES_PATH
import kz.aita.model.dataModel.GlobalConfigurationDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.model.dataModel.response.GetDrawablesConfigResponseDataModel
import kz.aita.model.dataModel.response.GetGlobalConfigurationResponse
import kz.aita.model.dataModel.response.GetStringsResponseDataModel
import kz.aita.model.dataModel.response.GetSvgDrawableResponse
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
            SERVER_URL,
            GLOBAL_CONFIG_PATH
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
            SERVER_URL,
            STRING_RESOURCES_PATH
          )

        emit(DataState.Success(response.payload))
      } catch (exception: Exception) {
        emit(DataState.Failure(exception))
      }
    }
  }

  override fun getDrawableConfig(): Flow<DataState<List<StylizedDrawablePathsGroupDataModel>>> {
    return flow {
      try {
        val response = genericRemoteService
          .request<GetDrawablesConfigResponseDataModel, Unit>(
            method = HttpMethod.Get,
            SERVER_URL,
            DRAWABLE_CONFIG_PATH
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
          .request<GetSvgDrawableResponse, Unit>(
            method = HttpMethod.Get,
            SERVER_URL,
            DRAWABLE_CONFIG_PATH
          )

        emit(DataState.Success(response.payload))
      } catch (exception: Exception) {
        emit(DataState.Failure(exception))
      }
    }
  }
}