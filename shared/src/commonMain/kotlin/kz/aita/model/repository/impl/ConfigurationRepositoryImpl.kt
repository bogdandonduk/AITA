package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kz.aita.core.io
import kz.aita.model.wrapper.MutableDataStateFlow
import kz.aita.model.dataModel.GlobalConfigurationDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedColorGroupDataModel
import kz.aita.model.dataModel.StylizedDimensionGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kotlin.coroutines.CoroutineContext

class ConfigurationRepositoryImpl(
  private val genericRemoteService: GenericRemoteService
) : Repository(), ConfigurationRepository {

  private val _globalAppConfigurationState = MutableDataStateFlow(
    coroutineScope = this,
    initial = GlobalConfigurationDataModel(
      serverUrl = "http://192.168.100.9:8080/",
      globalConfigurationPath = "config/global",
      stringResourcesPath = "res/string",
      dimensionResourcesPath = "res/dimension",
      colorResourcesPath = "res/color",
      drawableResourcesConfigurationPath = "res/drawableConfig",
      drawableResourcesPath = "res/drawable",
      companyForms = emptyList(),
      countries = emptyList()
    )
  )
  override val globalAppConfigurationState = _globalAppConfigurationState.asDataStateFlow()

  private val _stringsState = MutableDataStateFlow<List<LocalizedStringGroupDataModel>>(this)
  override val stringsState = _stringsState.asDataStateFlow()

  private val _dimensionsState = MutableDataStateFlow<List<StylizedDimensionGroupDataModel>>(this)
  override val dimensionsState = _dimensionsState.asDataStateFlow()

  private val _colorsState = MutableDataStateFlow<List<StylizedColorGroupDataModel>>(this)
  override val colorsState = _colorsState.asDataStateFlow()

  private val _drawablesState = MutableDataStateFlow<List<StylizedDrawablePathsGroupDataModel>>(this)
  override val drawablesState = _drawablesState.asDataStateFlow()

  init {
    getGlobalConfiguration()
  }

  override fun getGlobalConfiguration(loadAll: Boolean) {
    launch {
      try {
        val response = genericRemoteService
          .request<GlobalConfigurationDataModel, Unit>(
            method = HttpMethod.Get,
            globalAppConfigurationState.payloadValueNonNull.serverUrl,
            globalAppConfigurationState.payloadValueNonNull.globalConfigurationPath
          )

        _globalAppConfigurationState.emit(DataState.Success(response))

        if (loadAll) {
          getStrings()
          getDimensions()
          getColors()
          getDrawables()
        }
      } catch (exception: Exception) {
        _globalAppConfigurationState.emit(DataState.Failure(exception))
        exception.printStackTrace()
      }
    }
  }

  override fun getStrings() {
    launch {
      try {
        val response = genericRemoteService
          .request<List<LocalizedStringGroupDataModel>, Unit>(
            method = HttpMethod.Get,
            globalAppConfigurationState.payloadValueNonNull.serverUrl,
            globalAppConfigurationState.payloadValueNonNull.stringResourcesPath
          )

        _stringsState.emit(DataState.Success(response))
      } catch (exception: Exception) {
        _stringsState.emit(DataState.Failure(exception))
        exception.printStackTrace()
      }
    }
  }

  override fun getDimensions() {
    launch {
      try {
        val response = genericRemoteService
          .request<List<StylizedDimensionGroupDataModel>, Unit>(
            method = HttpMethod.Get,
            globalAppConfigurationState.payloadValueNonNull.serverUrl,
            globalAppConfigurationState.payloadValueNonNull.dimensionResourcesPath
          )

        _dimensionsState.emit(DataState.Success(response))
      } catch (exception: Exception) {
        _dimensionsState.emit(DataState.Failure(exception))
        exception.printStackTrace()
      }
    }
  }

  override fun getColors() {
    launch {
      try {
        val response = genericRemoteService
          .request<List<StylizedColorGroupDataModel>, Unit>(
            method = HttpMethod.Get,
            globalAppConfigurationState.payloadValueNonNull.serverUrl,
            globalAppConfigurationState.payloadValueNonNull.colorResourcesPath
          )

        _colorsState.emit(DataState.Success(response))
      } catch (exception: Exception) {
        _colorsState.emit(DataState.Failure(exception))
        exception.printStackTrace()
      }
    }
  }

  override fun getDrawables() {
    launch {
      try {
        val response = genericRemoteService
          .request<List<StylizedDrawablePathsGroupDataModel>, Unit>(
            method = HttpMethod.Get,
            globalAppConfigurationState.payloadValueNonNull.serverUrl,
            globalAppConfigurationState.payloadValueNonNull.drawableResourcesConfigurationPath
          )

        _drawablesState.emit(DataState.Success(response))
      } catch (exception: Exception) {
        _drawablesState.emit(DataState.Failure(exception))
        exception.printStackTrace()
      }
    }
  }

  override fun getDrawable(
    key: Long,
    themeId: Long,
    format: String
  ): Flow<DataState<String>> {
    return flow {
      try {
        val response = genericRemoteService
          .request<String, Unit>(
            method = HttpMethod.Get,
            globalAppConfigurationState.payloadValueNonNull.serverUrl,
            "${globalAppConfigurationState.payloadValueNonNull.drawableResourcesPath}/$format/$key/$themeId.$format"
          )

        emit(DataState.Success(response))
      } catch (exception: Exception) {
        emit(DataState.Failure(exception))
        exception.printStackTrace()
      }
    }
  }

  override fun getDrawable(
    name: String,
    format: String
  ): Flow<DataState<String>> {
    return flow {
      try {
        val response = genericRemoteService
          .request<String, Unit>(
            method = HttpMethod.Get,
            globalAppConfigurationState.payloadValueNonNull.serverUrl,
            "${globalAppConfigurationState.payloadValueNonNull.drawableResourcesPath}/$format/$name.$format"
          )

        emit(DataState.Success(response))
      } catch (exception: Exception) {
        emit(DataState.Failure(exception))
        exception.printStackTrace()
      }
    }
  }
}
