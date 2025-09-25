package kz.aita

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kz.aita.core.configurationRepository
import kz.aita.core.extractColor
import kz.aita.core.extractDimensionScreenWidthDivisor
import kz.aita.core.extractPath
import kz.aita.core.extractString
import kz.aita.core.extractValue
import kz.aita.core.genericLocalService
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedColorGroupDataModel
import kz.aita.model.dataModel.StylizedDimensionGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.util.screenWidthDivideAsDp
import kz.aita.util.screenWidthDivideAsSp
import kz.aita.util.toColor

object AppUIConfiguration {

  const val KEY_APP_THEME = "key_appTheme"
  const val KEY_APP_LOCALE = "key_appLocale"

  interface StateValues {

    val strings: List<LocalizedStringGroupDataModel>?
    val dimensions: List<StylizedDimensionGroupDataModel>?
    val colors: List<StylizedColorGroupDataModel>?
    val drawables: List<StylizedDrawablePathsGroupDataModel>?

    val appLocaleLanguage: String
    val appThemeId: Long
    val appSizeModeId: Long

    val stringAppName: String
    val stringLogIn: String
    val stringPhoneNumber: String
    val stringEmail: String
    val stringPassword: String

    val screenWidth: Dp
    val screenHeight: Dp
    val wideScreenMinWidth: Float

    val isNarrowScreen: Boolean

    val textSize: TextUnit
    val titleTextSize: TextUnit
    val smallTextSize: TextUnit
    val accentTextSize: TextUnit

    val AccentColor: Color
    val BackgroundColor: Color
    val TextColor: Color
    val PlaceholderTextColor: Color
    val AccentTextColor: Color
    val focusedBorderWidth: Dp
    val unfocusedBorderWidth: Dp
    val cornerRadius: Dp
    val iconSize: Dp

    val drawablePathAITALogo: String
  }

  private val _appLocaleLanguageState = MutableStateFlow("en")
  private val _appThemeIdState = MutableStateFlow(0L)
  private val _appSizeModeIdState = MutableStateFlow(0L)

  private val _stringAppNameState = MutableStateFlow("AITAA")
  private val _stringLogInState = MutableStateFlow("Log In")
  private val _stringPhoneNumberState = MutableStateFlow("Phone number")
  private val _stringEmailState = MutableStateFlow("Email")
  private val _stringPasswordState = MutableStateFlow("Password")

  private val _screenWidthState = MutableStateFlow(0f.dp)
  private val _screenHeightState = MutableStateFlow(0f.dp)
  private val _wideScreenMinWidthState = MutableStateFlow(600f)

  private val _isNarrowScreenState = MutableStateFlow(false)

  private val _textSizeState = MutableStateFlow(14.sp)
  private val _titleTextSizeState = MutableStateFlow(24.sp)
  private val _accentTextSizeState = MutableStateFlow(18.sp)
  private val _smallTextSizeState = MutableStateFlow(10.sp)
  private val _focusedBorderWidthState = MutableStateFlow(1.dp)
  private val _unfocusedBorderWidthState = MutableStateFlow(0.5.dp)
  private val _cornerRadius = MutableStateFlow(14.dp)
  private val _iconSize = MutableStateFlow(24.dp)

  private val _AccentColorState = MutableStateFlow(Color(0xffffba24))
  private val _BackgroundColorState = MutableStateFlow(Color(0xffffffff))
  private val _TextColorState = MutableStateFlow(Color(0xffffffff))
  private val _AccentTextColorState = MutableStateFlow(Color(0xffffffff))
  private val _PlaceholderTextColorState = MutableStateFlow(Color(0xaa000000))

  private val _drawablePathAITALogo = MutableStateFlow("svg/10.svg")

  lateinit var stateValues: StateValues

  lateinit var coroutineScope: CoroutineScope

  suspend fun setAppTheme(themeId: Long) {
    genericLocalService
      .put(KEY_APP_THEME, themeId.toString())
  }

  suspend fun setAppLocale(language: String) {
    genericLocalService
      .put(KEY_APP_LOCALE, language)
  }

  @Composable
  operator fun invoke(
    narrowScreenContent: @Composable AppUIConfiguration.() -> Unit,
    wideScreenContent: @Composable AppUIConfiguration.() -> Unit,
    vararg keys: Any
  ) {
    stateValues = object : StateValues {

      override val strings: List<LocalizedStringGroupDataModel>? by configurationRepository.stringsState.payload.collectAsState()
      override val dimensions: List<StylizedDimensionGroupDataModel>? by configurationRepository.dimensionsState.payload.collectAsState()
      override val colors: List<StylizedColorGroupDataModel>? by configurationRepository.colorsState.payload.collectAsState()
      override val drawables: List<StylizedDrawablePathsGroupDataModel>? by configurationRepository.drawablesState.payload.collectAsState()

      override val appLocaleLanguage: String by _appLocaleLanguageState.collectAsState()
      override val appThemeId: Long by _appThemeIdState.collectAsState()
      override val appSizeModeId: Long by _appSizeModeIdState.collectAsState()

      override val stringAppName: String by _stringAppNameState.collectAsState()
      override val stringLogIn: String by _stringLogInState.collectAsState()
      override val stringPhoneNumber: String by _stringPhoneNumberState.collectAsState()
      override val stringEmail: String by _stringEmailState.collectAsState()
      override val stringPassword: String by _stringPasswordState.collectAsState()

      override val screenWidth: Dp by _screenWidthState.collectAsState()
      override val screenHeight: Dp by _screenHeightState.collectAsState()
      override val wideScreenMinWidth: Float by _wideScreenMinWidthState.collectAsState()
      override val isNarrowScreen: Boolean by _isNarrowScreenState.collectAsState()
      override val textSize: TextUnit by _textSizeState.collectAsState()
      override val titleTextSize: TextUnit by _titleTextSizeState.collectAsState()
      override val accentTextSize: TextUnit by _accentTextSizeState.collectAsState()
      override val smallTextSize: TextUnit by _smallTextSizeState.collectAsState()
      override val focusedBorderWidth: Dp by _focusedBorderWidthState.collectAsState()
      override val unfocusedBorderWidth: Dp by _unfocusedBorderWidthState.collectAsState()
      override val cornerRadius: Dp by _cornerRadius.collectAsState()
      override val iconSize: Dp by _iconSize.collectAsState()

      override val AccentColor: Color by _AccentColorState.collectAsState()
      override val BackgroundColor: Color by _BackgroundColorState.collectAsState()
      override val TextColor: Color by _TextColorState.collectAsState()
      override val AccentTextColor: Color by _AccentTextColorState.collectAsState()
      override val PlaceholderTextColor: Color by _PlaceholderTextColorState.collectAsState()

      override val drawablePathAITALogo: String by _drawablePathAITALogo.collectAsState()
    }

    coroutineScope = rememberCoroutineScope()

    key(keys) {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
      ) {
        if (!stateValues.isNarrowScreen) {
          narrowScreenContent()
        } else {
          wideScreenContent()
        }

        coroutineScope.launch {
          _screenWidthState.emit(maxWidth)
          _screenHeightState.emit(maxHeight)

          launch {
            genericLocalService
              .observe(KEY_APP_THEME)
              .collect {
                it?.let {
                  _appThemeIdState.emit(it.toLong())
                }
              }
          }

          launch {
            genericLocalService
              .observe(KEY_APP_LOCALE)
              .collect {
                it?.let {
                  _appLocaleLanguageState.emit(it)
                }
              }
          }

          launch {
            configurationRepository
              .stringsState
              .payload
              .collect {
                _stringAppNameState.emit(it.extractString(0, stateValues.appLocaleLanguage))
                _stringLogInState.emit(it.extractString(1, stateValues.appLocaleLanguage))
                _stringPhoneNumberState.emit(it.extractString(2, stateValues.appLocaleLanguage))
                _stringEmailState.emit(it.extractString(3, stateValues.appLocaleLanguage))
                _stringPasswordState.emit(it.extractString(4, stateValues.appLocaleLanguage))
              }
          }

          launch {
            configurationRepository
              .dimensionsState
              .payload
              .collect {
                it?.let {
                  _wideScreenMinWidthState.emit(it.extractValue(4, stateValues.appSizeModeId))

                  _textSizeState.emit(it.extractDimensionScreenWidthDivisor(0, stateValues.appSizeModeId).screenWidthDivideAsSp(stateValues.screenWidth))
                  _titleTextSizeState.emit(it.extractDimensionScreenWidthDivisor(1, stateValues.appSizeModeId).screenWidthDivideAsSp(stateValues.screenWidth))
                  _accentTextSizeState.emit(it.extractDimensionScreenWidthDivisor(2, stateValues.appSizeModeId).screenWidthDivideAsSp(stateValues.screenWidth))
                  _smallTextSizeState.emit(it.extractDimensionScreenWidthDivisor(3, stateValues.appSizeModeId).screenWidthDivideAsSp(stateValues.screenWidth))

                  _focusedBorderWidthState.emit(it.extractValue(5, stateValues.appSizeModeId).dp)
                  _unfocusedBorderWidthState.emit(it.extractValue(6, stateValues.appSizeModeId).dp)

                  _cornerRadius.emit(it.extractValue(7, stateValues.appSizeModeId).dp)
                  _iconSize.emit(it.extractDimensionScreenWidthDivisor(7, stateValues.appSizeModeId).screenWidthDivideAsDp(stateValues.screenWidth))
                }
              }
          }

          launch {
            configurationRepository
              .colorsState
              .payload
              .collect {
                it?.let {
                  _AccentColorState.emit(it.extractColor(0, stateValues.appThemeId).toColor())
                  _BackgroundColorState.emit(it.extractColor(1, stateValues.appThemeId).toColor())
                  _TextColorState.emit(it.extractColor(2, stateValues.appThemeId).toColor())
                  _AccentTextColorState.emit(it.extractColor(3, stateValues.appThemeId).toColor())
                  _PlaceholderTextColorState.emit(it.extractColor(4, stateValues.appThemeId).toColor())
                }
              }
          }

          launch {
            configurationRepository
              .drawablesState
              .payload
              .collect {
                it?.let {
                  _drawablePathAITALogo.emit(it.extractPath(0, stateValues.appThemeId))
                }
              }
          }

          _isNarrowScreenState.emit(stateValues.screenWidth.value < stateValues.wideScreenMinWidth)
        }
      }
    }
  }
}