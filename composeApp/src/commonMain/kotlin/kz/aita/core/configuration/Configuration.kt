package kz.aita.core.configuration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.properties.Delegates

object AppConfiguration {
  var isLandscape by Delegates.notNull<Boolean>()

  var screenWidth by Delegates.notNull<Int>()
  var screenHeight by Delegates.notNull<Int>()
  var isWideScreen by Delegates.notNull<Boolean>()
  var screenWidthQuarter by Delegates.notNull<Int>()

  const val minWidgetWidth = 280
  const val maxWidgetWidth = 350
  const val displayCenterFraction = 0.5f
  var widgetWidth by Delegates.notNull<Dp>()
  var narrowWidgetWidth by Delegates.notNull<Int>()
  var landscapeGroupWidth by Delegates.notNull<Int>()

  val spaceBetweenWidgets = 24.dp

  val xSmallCornerRadius = 2.dp
  val smallCornerRadius = 4.dp
  val mediumCornerRadius = 6.dp
  val largeCornerRadius = 8.dp
  val xLargeCornerRadius = 10.dp
  val xxLargeCornerRadius = 12.dp
  val xxxLargeCornerRadius = 14.dp

  val smallTextSize = 12.dp
  val defaultTextSize = 14.dp
  val mediumTextSize = 16.dp
  val xMediumTextSize = 18.dp
  val largeTextSize = 20.sp
  val xLargeTextSize = 24.dp
  val xxLargeTextSize = 28.dp
  val xxxLargeTextSize = 32.dp

  val xSmallTextPadding = 2.dp
  val smallTextPadding = 4.dp
  val mediumTextPadding = 6.dp
  val largeTextPadding = 8.dp
  val xLargeTextPadding = 10.dp
  val xxLargeTextPadding = 12.dp
  val xxxLargeTextPadding = 14.dp
  val hugeTextPadding = 16.dp

  val spaceBetweenTitleAndTextField = 6.dp

  val spaceBetweenTextFields = 12.dp
  val focusedBorderWidth = 1.dp
  val thickBorderWidth = 2.dp
  val notFocusedBorderWidth = 0.5f.dp

  val defaultTextFieldHeight = 56.dp
  val largeTextFieldHeight = 64.dp

  val xSmallIconSize = 10.dp
  val smallIconSize = 12.dp
  val mediumIconSize = 16.dp
  val defaultIconSize = 20.dp
  val largeIconSize = 24.dp
  val largishIconSize = 26.dp
  val xLargeIconSize = 28.dp
  val xxLargeIconSize = 32.dp

  val xxxLargeOffset = 28.dp
  val xxLargeOffset = 24.dp
  val xLargeOffset = 20.dp
  val largeOffset = 16.dp
  val mediumOffset = 12.dp
  val defaultOffset = 10.dp
  val smallOffset = 8.dp
  val xSmallOffset = 4.dp
  val xxSmallOffset = 2.dp

  val mediumHeight = 40.dp
  val dropdownButtonHeight = 48.dp
  val textFieldSideButtonHeight = 45.dp
  val phoneNumberTextFieldHeight = 50.dp
  val appBarHeight = 56.dp
  val scrollableAreaHeight = 90.dp
  val scrollableAreaHeightLarge = 180.dp
  val genericTextFieldWithTitleHeight = 70.dp
  val genericTextFieldWithTitleHeightTitleless = 40.dp
  val genericTextFieldWithTitleHeightLarge = 80.dp
  var camBarcodeScannerPreviewHeight = 0.dp

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
  private val _appTheme = MutableStateFlow(0)
  val appTheme = _appTheme.asStateFlow()

  suspend fun setAppLocale(locale: String) {
    _appLocale.emit(locale)
  }

  @Composable
  fun getStringResource(res: StringResource): String {
    return stringResource(res)
  }

  var AccentColor = Color(0xffffba24)
  var DarkColor = Color(0xff32363E)
  var DarkColorDynamic = Color(0xff32363E)
  var GreenColor = Color(0xff6BB522)
  var WhatsAppColor = Color(0xff36bb22)
  var GreenColorSemiTransparent = Color(0xbb6BB522)
  var RedColor = Color(0xffEC493C)
  var GrayColor = Color(0xffA7A7A7)
  var GrayColorSemiTransparent = Color(0x33A7A7A7)
  var AccentColorSemiTransparent = Color(0xaaffba24) // 0xaa10ab9d// 0xaa10ab9d
  var SecondaryColor = Color(0xffF0F0F0)
  var TextColor = Color(0xff404040)
  var ActionTextColor = Color(0xaa0000ff)
  var TextColorSemiTransparent = Color(0xaa404040)
  var AccentTextColor = Color.White
  var BackgroundColor = Color.White

  suspend fun initColors(isDarkTheme: Boolean) {
    BackgroundColor = if (isDarkTheme) Color(0xff32363E) else Color.White
    SecondaryColor = if (isDarkTheme) Color.DarkGray else Color(0xffF0F0F0)
    TextColor = if (isDarkTheme) Color.White else Color(0xff404040)
    TextColorSemiTransparent = if (isDarkTheme) Color(0xaaffffff) else Color(0xaa404040)
    GreenColor = if (isDarkTheme) Color.Green else Color(0xff6BB522)
    DarkColorDynamic = if (isDarkTheme) Color(0xffF0F0F0) else Color(0xff32363E)
    ActionTextColor = if (isDarkTheme) Color(0xaa00FFFF) else Color(0xaa0000ff)

    _appTheme.emit(1)
  }

  @Composable
  operator fun invoke(
    content: @Composable AppConfiguration.() -> Unit
  ) {
    val appLocale by appLocale.collectAsState()
    val appTheme by appTheme.collectAsState()

    key(appLocale, appTheme) {
      content()
    }
  }
}
