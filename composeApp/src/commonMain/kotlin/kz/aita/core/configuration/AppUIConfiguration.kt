package kz.aita.core.configuration

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.store.ConfigurationStore
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.properties.Delegates

object AppUIConfiguration {

  interface StateValues {

    val screenWidth: Dp
    val screenHeight: Dp
    val isWideScreen: Boolean

    val titleTextSize: TextUnit
    val smallTextSizeState: TextUnit
    val defaultTextSizeState: TextUnit
    val mediumTextSizeState: TextUnit

    val strings: List<LocalizedStringGroupDataModel>

    val AccentColor: Color
    val DarkColor: Color
    val DarkColorDynamic: Color
    val GreenColor: Color
    val WhatsAppColor: Color
    val GreenColorSemiTransparent: Color
    val RedColor: Color
    val GrayColor: Color
    val GrayColorSemiTransparent: Color
    val AccentColorSemiTransparent: Color // 0xaa10ab9d// 0xaa10ab9d
    val SecondaryColor: Color
    val TextColor: Color
    val ActionTextColor: Color
    val TextColorSemiTransparent: Color
    val AccentTextColor: Color
    val BackgroundColor: Color

    val focusedBorderWidthState: Dp
    val thickBorderWidthState: Dp
    val notFocusedBorderWidthState: Dp

    val defaultTextFieldHeightState: Dp
    val largeTextFieldHeightState: Dp

    val xSmallIconSizeState: Dp
    val smallIconSizeState: Dp
    val mediumIconSizeState: Dp
    val defaultIconSizeState: Dp
    val largeIconSizeState: Dp
    val largishIconSizeState: Dp
    val xLargeIconSizeState: Dp
    val xxLargeIconSizeState: Dp

    val mediumHeightState: Dp
    val dropdownButtonHeightState: Dp
    val textFieldSideButtonHeightState: Dp
    val phoneNumberTextFieldHeightState: Dp
    val appBarHeightState: Dp
    val scrollableAreaHeightState: Dp
    val scrollableAreaHeightLargeState: Dp
    val genericTextFieldWithTitleHeightState: Dp
    val genericTextFieldWithTitleHeightTitlelessState: Dp
    val genericTextFieldWithTitleHeightLargeState: Dp
    val camBarcodeScannerPreviewHeightState: Dp
  }

  lateinit var stateValues: StateValues

  private val _screenWidthState = MutableStateFlow(0.dp)
  private val _screenHeightState = MutableStateFlow(0.dp)
  private val _isWideScreenState = MutableStateFlow(true)

  private val _сornerRadius = MutableStateFlow(12.dp)

  private val _titleTextSizeState = MutableStateFlow(20.sp)
  private val _largeTextSizeState = MutableStateFlow(20.sp)
  private val _smallTextSizeState = MutableStateFlow(12.sp)
  private val _defaultTextSizeState = MutableStateFlow(14.sp)
  private val _mediumTextSizeState = MutableStateFlow(16.sp)

  private val focusedBorderWidthState = MutableStateFlow(1.dp)
  private val thickBorderWidthState = MutableStateFlow(2.dp)
  private val notFocusedBorderWidthState = MutableStateFlow(0.5f.dp)

  private val defaultTextFieldHeightState = MutableStateFlow(56.dp)
  private val largeTextFieldHeightState = MutableStateFlow(64.dp)

  private val _xSmallIconSizeState = MutableStateFlow(10.dp)
  private val _smallIconSizeState = MutableStateFlow(12.dp)
  private val _mediumIconSizeState = MutableStateFlow(16.dp)
  private val _defaultIconSizeState = MutableStateFlow(20.dp)
  private val _largeIconSizeState = MutableStateFlow(24.dp)
  private val _largishIconSizeState = MutableStateFlow(26.dp)
  private val _xLargeIconSizeState = MutableStateFlow(28.dp)
  private val _xxLargeIconSizeState = MutableStateFlow(32.dp)

  private val _mediumHeightState = MutableStateFlow(40.dp)
  private val _dropdownButtonHeightState = MutableStateFlow(48.dp)
  private val _textFieldSideButtonHeightState = MutableStateFlow(45.dp)
  private val _phoneNumberTextFieldHeightState = MutableStateFlow(50.dp)
  private val _appBarHeightState = MutableStateFlow(56.dp)
  private val _scrollableAreaHeightState = MutableStateFlow(90.dp)
  private val _scrollableAreaHeightLargeState = MutableStateFlow(180.dp)
  private val _genericTextFieldWithTitleHeightState = MutableStateFlow(70.dp)
  private val _genericTextFieldWithTitleHeightTitlelessState = MutableStateFlow(40.dp)
  private val _genericTextFieldWithTitleHeightLargeState = MutableStateFlow(80.dp)
  private val _camBarcodeScannerPreviewHeightState = MutableStateFlow(0.dp)

  fun calculateStandardWidgetWidth(
    isLandscape: Boolean,
    screenWidth: Int,
    screenWidthQuarter: Int,
    minWidgetWidth: Int,
    maxWidgetWidth: Int,
    divider: Int = 5
  ) : Int {
    return if (isLandscape) {
      when {
        screenWidthQuarter in minWidgetWidth..maxWidgetWidth -> screenWidthQuarter
        screenWidthQuarter < minWidgetWidth -> minWidgetWidth
        else -> maxWidgetWidth
      }
    } else
      screenWidth - screenWidth / divider
  }
  private val _appLocale = MutableStateFlow("")
  val appLocale = _appLocale.asStateFlow()
  private val _appTheme = MutableStateFlow(0L)
  val appTheme = _appTheme.asStateFlow()

  suspend fun setAppLocale(locale: String) {
    _appLocale.emit(locale)
  }

  suspend fun setAppTheme(index: Long) {
    _appTheme.emit(index)
  }

  @Composable
  fun getStringResource(res: StringResource): String {
    return stringResource(res)
  }

  private val _AccentColor = MutableStateFlow(Color(0xffffba24))
  private val _DarkColor = MutableStateFlow(Color(0xff32363E))
  private val _DarkColorDynamic = MutableStateFlow(Color(0xff32363E))
  private val _GreenColor = MutableStateFlow(Color(0xff6BB522))
  private val _WhatsAppColor = MutableStateFlow(Color(0xff36bb22))
  private val _GreenColorSemiTransparent = MutableStateFlow(Color(0xbb6BB522))
  private val _RedColor = MutableStateFlow(Color(0xffEC493C))
  private val _GrayColor = MutableStateFlow(Color(0xffA7A7A7))
  private val _GrayColorSemiTransparent = MutableStateFlow(Color(0x33A7A7A7))
  private val _AccentColorSemiTransparent = MutableStateFlow(Color(0xaaffba24)) // 0xaa10ab9d// 0xaa10ab9d
  private val _SecondaryColor = MutableStateFlow(Color(0xffF0F0F0))
  private val _TextColor = MutableStateFlow(Color(0xff404040))
  private val _ActionTextColor = MutableStateFlow(Color(0xaa0000ff))
  private val _TextColorSemiTransparent = MutableStateFlow(Color(0xaa404040))
  private val _AccentTextColor = MutableStateFlow(Color.White)
  private val _BackgroundColor = MutableStateFlow(Color.White)

  suspend fun initColors(isDarkTheme: Boolean) {
    _BackgroundColor.emit(if (isDarkTheme) Color(0xff32363E) else Color.White)
    _SecondaryColor.emit(if (isDarkTheme) Color.DarkGray else Color(0xffF0F0F0))
    _TextColor.emit(if (isDarkTheme) Color.White else Color(0xff404040))
    _TextColorSemiTransparent.emit(if (isDarkTheme) Color(0xaaffffff) else Color(0xaa404040))
    _GreenColor.emit(if (isDarkTheme) Color.Green else Color(0xff6BB522))
    _DarkColorDynamic.emit(if (isDarkTheme) Color(0xffF0F0F0) else Color(0xff32363E))
    _ActionTextColor.emit(if (isDarkTheme) Color(0xaa00FFFF) else Color(0xaa0000ff))

    _appTheme.emit(1)
  }

  @Composable
  operator fun invoke(
    narrowScreenContent: @Composable AppUIConfiguration.() -> Unit,
    wideScreenContent: @Composable AppUIConfiguration.() -> Unit
  ) {
    val appLocale by appLocale.collectAsState()
    val appTheme by appTheme.collectAsState()

    key(appLocale, appTheme) {
      stateValues = object : StateValues {
        override val screenWidth: Dp by _screenWidthState.collectAsState()
        override val screenHeight: Dp by _screenHeightState.collectAsState()
        override val isWideScreen: Boolean by _isWideScreenState.collectAsState()
        override val titleTextSize: TextUnit by _titleTextSizeState.collectAsState()
        override val smallTextSizeState: TextUnit by _titleTextSizeState.collectAsState()
        override val defaultTextSizeState: TextUnit by _titleTextSizeState.collectAsState()
        override val mediumTextSizeState: TextUnit by _titleTextSizeState.collectAsState()

        override val strings: List<LocalizedStringGroupDataModel> by ConfigurationStore.strings.collectAsState()
        override val AccentColor: Color by _AccentColor.collectAsState()
        override val DarkColor: Color by _DarkColor.collectAsState()
        override val DarkColorDynamic: Color by _DarkColorDynamic.collectAsState()
        override val GreenColor: Color by _GreenColor.collectAsState()
        override val WhatsAppColor: Color by _WhatsAppColor.collectAsState()
        override val GreenColorSemiTransparent: Color by _GreenColorSemiTransparent.collectAsState()
        override val RedColor: Color by _RedColor.collectAsState()
        override val GrayColor: Color by _GrayColor.collectAsState()
        override val GrayColorSemiTransparent: Color by _GrayColorSemiTransparent.collectAsState()
        override val AccentColorSemiTransparent: Color by _AccentColorSemiTransparent.collectAsState()
        override val SecondaryColor: Color by _SecondaryColor.collectAsState()
        override val TextColor: Color by _TextColor.collectAsState()
        override val ActionTextColor: Color by _ActionTextColor.collectAsState()
        override val TextColorSemiTransparent: Color by _TextColorSemiTransparent.collectAsState()
        override val AccentTextColor: Color by _AccentTextColor.collectAsState()
        override val BackgroundColor: Color by _BackgroundColor.collectAsState()
        override val focusedBorderWidthState: Dp by _focusedBorderWidthState.collectAsState()
        override val thickBorderWidthState: Dp
          get() = TODO("Not yet implemented")
        override val notFocusedBorderWidthState: Dp
          get() = TODO("Not yet implemented")
        override val defaultTextFieldHeightState: Dp
          get() = TODO("Not yet implemented")
        override val largeTextFieldHeightState: Dp
          get() = TODO("Not yet implemented")
        override val xSmallIconSizeState: Dp
          get() = TODO("Not yet implemented")
        override val smallIconSizeState: Dp
          get() = TODO("Not yet implemented")
        override val mediumIconSizeState: Dp
          get() = TODO("Not yet implemented")
        override val defaultIconSizeState: Dp
          get() = TODO("Not yet implemented")
        override val largeIconSizeState: Dp
          get() = TODO("Not yet implemented")
        override val largishIconSizeState: Dp
          get() = TODO("Not yet implemented")
        override val xLargeIconSizeState: Dp
          get() = TODO("Not yet implemented")
        override val xxLargeIconSizeState: Dp
          get() = TODO("Not yet implemented")
        override val mediumHeightState: Dp
          get() = TODO("Not yet implemented")
        override val dropdownButtonHeightState: Dp
          get() = TODO("Not yet implemented")
        override val textFieldSideButtonHeightState: Dp
          get() = TODO("Not yet implemented")
        override val phoneNumberTextFieldHeightState: Dp
          get() = TODO("Not yet implemented")
        override val appBarHeightState: Dp
          get() = TODO("Not yet implemented")
        override val scrollableAreaHeightState: Dp
          get() = TODO("Not yet implemented")
        override val scrollableAreaHeightLargeState: Dp
          get() = TODO("Not yet implemented")
        override val genericTextFieldWithTitleHeightState: Dp
          get() = TODO("Not yet implemented")
        override val genericTextFieldWithTitleHeightTitlelessState: Dp
          get() = TODO("Not yet implemented")
        override val genericTextFieldWithTitleHeightLargeState: Dp
          get() = TODO("Not yet implemented")
        override val camBarcodeScannerPreviewHeightState: Dp
          get() = TODO("Not yet implemented")
      }

      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
      ) {
        if (!stateValues.isWideScreen) {
          narrowScreenContent()
        } else {
          wideScreenContent()
        }

        val coroutineScope = rememberCoroutineScope()

        coroutineScope.launch {
          _screenWidthState.emit(maxWidth)
          _screenHeightState.emit(maxHeight)
          _isWideScreenState.emit(_screenWidthState.value.value >= 600f)
          _titleTextSizeState.emit((_screenWidthState.value.value / 50).sp)
          _largeTextSizeState.emit((_screenWidthState.value.value / 60).sp)
        }
      }
    }
  }
}
