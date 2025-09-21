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
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedColorGroupDataModel
import kz.aita.model.dataModel.StylizedDimensionGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel
import kz.aita.util.toColor

object AppUIConfiguration {

  interface StateValues {

    val strings: List<LocalizedStringGroupDataModel>?
    val dimensions: List<StylizedDimensionGroupDataModel>?
    val colors: List<StylizedColorGroupDataModel>?
    val drawables: List<StylizedDrawablePathsGroupDataModel>?

    val appLocale: String
    val appTheme: Long

    val screenWidth: Dp
    val screenHeight: Dp
    val isNarrowScreen: Boolean

    val textSize: TextUnit
    val titleTextSize: TextUnit
    val smallTextSize: TextUnit
    val accentTextSize: TextUnit

    val AccentColor: Color
    val BackgroundColor: Color
    val TextColor: Color
    val AccentTextColor: Color
  }

  private val _appLocaleState = MutableStateFlow("")
  private val _appThemeState = MutableStateFlow(0L)

  private val _screenWidthState = MutableStateFlow(0.0.dp)
  private val _screenHeightState = MutableStateFlow(0.0.dp)

  private val _isNarrowScreenState = MutableStateFlow(false)

  private val _textSizeState = MutableStateFlow(0.sp)
  private val _titleTextSizeState = MutableStateFlow(0.sp)

  private val _smallTextSizeState = MutableStateFlow(0.sp)
  private val _accentTextSizeState = MutableStateFlow(0.sp)

  private val _AccentColor = MutableStateFlow(Color(0x000))
  private val _BackgroundColor = MutableStateFlow(Color(0x000))
  private val _TextColor = MutableStateFlow(Color(0x000))

  private val _AccentTextColor = MutableStateFlow(Color(0xfff))
  lateinit var stateValues: StateValues

  lateinit var coroutineScope: CoroutineScope

  fun setAppTheme() {

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

      override val appLocale: String by _appLocaleState.collectAsState()
      override val appTheme: Long by _appThemeState.collectAsState()

      override val screenWidth: Dp by _screenWidthState.collectAsState()
      override val screenHeight: Dp by _screenHeightState.collectAsState()
      override val isNarrowScreen: Boolean by _isNarrowScreenState.collectAsState()
      override val textSize: TextUnit by _textSizeState.collectAsState()
      override val titleTextSize: TextUnit by _titleTextSizeState.collectAsState()
      override val smallTextSize: TextUnit by _smallTextSizeState.collectAsState()
      override val accentTextSize: TextUnit by _accentTextSizeState.collectAsState()

      override val AccentColor: Color by _AccentColor.collectAsState()
      override val BackgroundColor: Color by _BackgroundColor.collectAsState()
      override val TextColor: Color by _TextColor.collectAsState()
      override val AccentTextColor: Color by _AccentTextColor.collectAsState()
    }

    coroutineScope = rememberCoroutineScope()

    coroutineScope.launch {
      configurationRepository
        .colorsState
        .payload
        .collect {
          it?.let {
            _AccentColor.emit(it.extractColor(0, 0).toColor())
            _BackgroundColor.emit(it.extractColor(1, 0).toColor())
            _TextColor.emit(it.extractColor(2, 0).toColor())
          }
        }
    }

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
          _isNarrowScreenState.emit(_screenWidthState.value.value >= 600f)


          _titleTextSizeState.emit((_screenWidthState.value.value / 50).sp)
        }
      }
    }
  }
}