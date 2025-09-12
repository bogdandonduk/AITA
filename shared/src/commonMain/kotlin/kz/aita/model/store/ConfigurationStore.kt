package kz.aita.model.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kz.aita.core.async.coroutines.io
import kz.aita.core.di.configurationRepository
import kz.aita.core.remote.RemoteConfiguration
import kz.aita.model.dataModel.GlobalConfigurationDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.model.store.base.Store
import kz.aita.model.wrapper.DataState

object ConfigurationStore : Store() {

  private val _globalAppConfigurationState = MutableStateFlow<DataState<GlobalConfigurationDataModel>>(DataState.Empty())
  val globalAppConfigurationState = _globalAppConfigurationState.asStateFlow()

  private val _drawableConfigurationState = MutableStateFlow<DataState<List<StylizedDrawablePathsGroupDataModel>>>(DataState.Empty())
  val drawableConfigurationState = _drawableConfigurationState.asStateFlow()

  private val _stringsState = MutableStateFlow<DataState<List<LocalizedStringGroupDataModel>>>(DataState.Empty())
  val stringsState = _stringsState.asStateFlow()

  private val _strings = MutableStateFlow<List<LocalizedStringGroupDataModel>>(emptyList())
  val strings = _strings.asStateFlow()

  init {
    initialize()

    coroutineScope.launch(Dispatchers.io) {
      stringsState.collect {
        if (it is DataState.Success)
          _strings.emit(it.payload)
      }
    }
  }

  override fun initialize() {
    coroutineScope.launch(Dispatchers.io) {
      configurationRepository
        .getGlobalConfiguration()
        .collect { globalConfigurationDataState ->
          (globalConfigurationDataState as? DataState.Success)?.payload?.run {
            RemoteConfiguration.STRING_RESOURCES_PATH = stringResourcesPath
            RemoteConfiguration.DRAWABLE_CONFIGURATION_PATH = drawableConfigurationPath
            RemoteConfiguration.DRAWABLE_RESOURCES_PATH = drawableResourcesPath
            RemoteConfiguration.DRAWABLE_SVG_RESOURCES_PATH = drawableSvgResourcesPath
            RemoteConfiguration.DRAWABLE_PNG_RESOURCES_PATH = drawablePngResourcesPath
          }

          _globalAppConfigurationState.emit(globalConfigurationDataState)

          launch {
            configurationRepository
              .getStrings("en")
              .collect { strings ->
                println("strings $strings")
                _stringsState.emit(strings)
              }
          }

          launch {
            configurationRepository
              .getDrawableConfiguration()
              .collect { drawableConfiguration ->
                _drawableConfigurationState.emit(drawableConfiguration)
              }
          }
        }
    }
  }
}
