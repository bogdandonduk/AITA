package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kz.aita.core.io
import kz.aita.model.dataModel.CityDataModel
import kz.aita.model.dataModel.CountryDataModel
import kz.aita.model.dataModel.ExceptionDataModel
import kz.aita.model.wrapper.MutableDataStateFlow
import kz.aita.model.dataModel.GlobalAppConfigurationDataModel
import kz.aita.model.dataModel.AppLanguageDataModel
import kz.aita.model.dataModel.AppThemeDataModel
import kz.aita.model.dataModel.CurrencyDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.QuantityDataModel
import kz.aita.model.dataModel.StylizedColorGroupDataModel
import kz.aita.model.dataModel.StylizedDimensionGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlowNonNull

class ConfigurationRepositoryImpl(
  private val genericRemoteService: GenericRemoteService
): Repository(), ConfigurationRepository {

  private val _globalAppConfigurationState = MutableDataStateFlowNonNull(
    coroutineScope = this,
    initial = GlobalAppConfigurationDataModel(
      appName = "AITA",
      serverUrl = "http://192.168.100.9:8080",
      globalConfigurationPath = "config/global",
      exceptionConfigurationPath = "config/exception",
      logInPath = "auth/logIn",
      signUpPath = "auth/signUp",
      refreshPath = "auth/refresh",
      logOutPath = "auth/logOut",
      userAccountPath = "userAccount",
      storesPath = "stores",
      stockPath = "stock",
      suppliersPath = "suppliers",
      stringResourcesPath = "res/string",
      dimensionResourcesPath = "res/dimension",
      colorResourcesPath = "res/color",
      drawableResourcesConfigurationPath = "res/drawableConfig",
      drawableResourcesPath = "res/drawable",
      companyForms = emptyList(),
      countries = listOf(
        CountryDataModel(
          locale = "kz",
          language = "kk",
          name = listOf(
            LocalizedStringDataModel(
              "en",
              "Kazakhstan"
            ),
            LocalizedStringDataModel(
              "ru",
              "Казахстан"
            ),
            LocalizedStringDataModel(
              "kk",
              "Казакстан"
            )
          ),
          flagDrawablePath = "png/flag_kz.png",
          phoneNumberCode = "7",
          phoneNumberSize = 10,
          currency = "₸",
          cities = listOf(
            CityDataModel(
              name = listOf(
                LocalizedStringDataModel(
                  "en",
                  "Astana"
                ),
                LocalizedStringDataModel(
                  "ru",
                  "Астана"
                ),
                LocalizedStringDataModel(
                  "kk",
                  "Астана"
                )
              ),
              51.1667, 71.4333,
              51.0230, 71.2660,
              51.250071, 71.5500
            ),
          )
        )
      ),
      languages = listOf(
        AppLanguageDataModel(
          "en",
          listOf(
            LocalizedStringDataModel(
              "en",
              "English"
            ),
            LocalizedStringDataModel(
              "ru",
              "Английский"
            ),
            LocalizedStringDataModel(
              "kk",
              "Ағылшынша"
            )
          ),
          "png/flag_en.png"
        ),
        AppLanguageDataModel(
          "ru",
          listOf(
            LocalizedStringDataModel(
              "en",
              "Russian"
            ),
            LocalizedStringDataModel(
              "ru",
              "Русский"
            ),
            LocalizedStringDataModel(
              "kk",
              "Орысша"
            )
          ),
          "png/flag_ru.png"
        ),
        AppLanguageDataModel(
          "kk",
          listOf(
            LocalizedStringDataModel(
              "en",
              "Kazakh"
            ),
            LocalizedStringDataModel(
              "ru",
              "Казахский"
            ),
            LocalizedStringDataModel(
              "kk",
              "Қазақша"
            )
          ),
          "png/flag_kz.png"
        )
      ),
      currencies = listOf(
        CurrencyDataModel(
          currency = "KZT",
          countries = listOf("kz"),
          symbol = "₸",
          name = listOf(
            LocalizedStringDataModel(
              language = "en",
              value = "tenge"
            ),
            LocalizedStringDataModel(
              language = "ru",
              value = "тенге"
            ),
            LocalizedStringDataModel(
              language = "kk",
              value = "теңге"
            )
          )
        ),
        CurrencyDataModel(
          currency = "TJS",
          countries = listOf("tj"),
          symbol = "SM",
          name = listOf(
            LocalizedStringDataModel(
              language = "en",
              value = "somoni"
            ),
            LocalizedStringDataModel(
              language = "ru",
              value = "сом"
            ),
            LocalizedStringDataModel(
              language = "kk",
              value = "сом"
            )
          )
        ),
        CurrencyDataModel(
          currency = "RUB",
          countries = listOf("ru"),
          symbol = "₽",
          name = listOf(
            LocalizedStringDataModel(
              language = "en",
              value = "rub."
            ),
            LocalizedStringDataModel(
              language = "ru",
              value = "руб."
            ),
            LocalizedStringDataModel(
              language = "kk",
              value = "руб."
            )
          )
        ),
        CurrencyDataModel(
          currency = "USD",
          symbol = "$",
          countries = listOf("us"),
          name = listOf(
            LocalizedStringDataModel(
              language = "en",
              value = "US$"
            ),
            LocalizedStringDataModel(
              language = "ru",
              value = "$ США"
            ),
            LocalizedStringDataModel(
              language = "kk",
              value = "US$"
            )
          )
        )
      ),
      themes = listOf(
        AppThemeDataModel(
          0,
          listOf(
            LocalizedStringDataModel(
              "en",
              "Light"
            ),
            LocalizedStringDataModel(
              "ru",
              "Светлая"
            ),
            LocalizedStringDataModel(
              "kk",
              "Жарық"
            )
          )
        ),
        AppThemeDataModel(
          1,
          listOf(
            LocalizedStringDataModel(
              "en",
              "Dark"
            ),
            LocalizedStringDataModel(
              "ru",
              "Темная"
            ),
            LocalizedStringDataModel(
              "kk",
              "Қараңғы"
            )
          )
        )
      ),
      goodsItemsQuantityUnits = listOf(
        QuantityDataModel(
          id = 0,
          listOf(
            LocalizedStringDataModel(
              "en",
              "pc."
            ),
            LocalizedStringDataModel(
              "ru",
              "шт."
            ),
            LocalizedStringDataModel(
              "kk",
              "шт."
            )
          ),
          roundTotal = true
        ),
        QuantityDataModel(
          id = 1,
          listOf(
            LocalizedStringDataModel(
              "en",
              "kg."
            ),
            LocalizedStringDataModel(
              "ru",
              "кг."
            ),
            LocalizedStringDataModel(
              "kk",
              "кг."
            )
          ),
          roundTotal = false
        )
      )
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

  private val _exceptionsState = MutableDataStateFlow<List<ExceptionDataModel>>(this)
  override val exceptionsState = _exceptionsState.asDataStateFlow()

  init {
    getGlobalConfiguration()
  }

  override fun getGlobalConfiguration(loadAll: Boolean) {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<GlobalAppConfigurationDataModel, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.globalConfigurationPath,
          onFailure = {
            _globalAppConfigurationState.emit(DataState.Failure(it))
          }
        )?.run {
          _globalAppConfigurationState.emit(DataState.Success(this))

          if (loadAll) {
            getStrings()
            getDimensions()
            getColors()
            getDrawables()
            getExceptions()
          }
        }
    }
  }

  override fun getStrings() {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<List<LocalizedStringGroupDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.stringResourcesPath,
          onFailure = {
            _stringsState.emit(DataState.Failure(it))
          }
        )?.run {
          _stringsState.emit(DataState.Success(this))
        }
    }
  }

  override fun getDimensions() {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<List<StylizedDimensionGroupDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.dimensionResourcesPath,
          onFailure = {
            _dimensionsState.emit(DataState.Failure(it))
          }
        )?.run {
          _dimensionsState.emit(DataState.Success(this))
        }
    }
  }

  override fun getColors() {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<List<StylizedColorGroupDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.colorResourcesPath,
          onFailure = {
            _colorsState.emit(DataState.Failure(it))
          }
        )?.run {
          _colorsState.emit(DataState.Success(this))
        }
    }
  }

  override fun getDrawables() {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<List<StylizedDrawablePathsGroupDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.drawableResourcesConfigurationPath,
          onFailure = {
            _drawablesState.emit(DataState.Failure(it))
          }
        )?.run {
          _drawablesState.emit(DataState.Success(this))
        }
    }
  }

  override fun getDrawable(
    key: Long,
    themeId: Long,
    format: String
  ): Flow<DataState<String>> {
    return flow {
      genericRemoteService
        .request<String, Unit>(
          method = HttpMethod.Get,
          endpointUrl = "${globalAppConfigurationState.payloadValue.drawableResourcesPath}/$format/$key/$themeId.$format",
          onFailure = {
            emit(DataState.Failure(it))
          }
        )?.run {
          emit(DataState.Success(this))
        }
    }
  }

  override fun getDrawable(
    name: String,
    format: String
  ): Flow<DataState<String>> {
    return flow {
      genericRemoteService
        .request<String, Unit>(
          method = HttpMethod.Get,
          endpointUrl = "${globalAppConfigurationState.payloadValue.drawableResourcesPath}/$format/$name.$format",
          onFailure = {
            emit(DataState.Failure(it))
          }
        )?.run {
          emit(DataState.Success(this))
        }
    }
  }

  override fun getExceptions() {
    launch(Dispatchers.io) {
      genericRemoteService
        .request<List<ExceptionDataModel>, Unit>(
          method = HttpMethod.Get,
          endpointUrl = globalAppConfigurationState.payloadValue.exceptionConfigurationPath,
          onFailure = {
            _exceptionsState.emit(DataState.Failure(it))
          }
        )?.run {
          _exceptionsState.emit(DataState.Success(this))
        }
    }
  }
}
