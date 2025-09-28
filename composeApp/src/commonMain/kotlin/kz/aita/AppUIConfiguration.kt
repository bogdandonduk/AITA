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
import kz.aita.compose.util.toColor
import kz.aita.core.configurationRepository
import kz.aita.core.extractColor
import kz.aita.core.extractPath
import kz.aita.core.extractString
import kz.aita.core.extractValue
import kz.aita.core.genericLocalService
import kz.aita.model.dataModel.GlobalAppConfigurationDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedColorGroupDataModel
import kz.aita.model.dataModel.StylizedDimensionGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel

object AppUIConfiguration {

  const val KEY_APP_THEME = "key_appTheme"
  const val KEY_APP_LOCALE = "key_appLocale"

  interface StateValues {

    val globalAppConfiguration: GlobalAppConfigurationDataModel
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
    val stringEnterPhoneNumber: String
    val stringEmail: String
    val stringEnterEmailAddress: String
    val stringPassword: String
    val stringEnterPassword: String
    val stringCancel: String
    val stringClear: String
    val stringLoginAndOrPasswordIncorrect: String
    val stringPhoneNumberMustBe: String
    val stringEmailMustBe: String
    val stringPasswordMustBe: String
    val stringRepeatPassword: String
    val stringPasswordsMustMatch: String
    val stringFirstName: String
    val stringLastName: String
    val stringEnterFirstName: String
    val stringEnterLastName: String
    val stringUserWithThisPhoneNumberIsAlreadyRegistered: String
    val stringUserWithThisEmailAddressIsAlreadyRegistered: String
    val stringSignUp: String

    val screenWidth: Dp
    val screenHeight: Dp
    val wideScreenMinWidth: Float
    val boundWidgetWidth: Dp

    val isNarrowScreen: Boolean

    val textSize: TextUnit
    val titleTextSize: TextUnit
    val smallTextSize: TextUnit
    val accentTextSize: TextUnit

    val focusedBorderWidth: Dp
    val unfocusedBorderWidth: Dp
    val cornerRadius: Dp
    val iconSize: Dp
    val textFieldHeightMultiplierRelativeToTextSize: Float
    val textFieldHeight: Dp
    val textFieldIconPadding: Dp

    val AccentColor: Color
    val BackgroundColor: Color
    val TextColor: Color
    val AccentTextColor: Color
    val PlaceholderTextColor: Color
    val DisabledColor: Color
    val ErrorColor: Color

    val drawablePathAITALogo: String
    val drawablePathIconPassword: String
    val drawablePathIconCancel: String
    val drawablePathIconEyeHide: String
    val drawablePathIconEyeShow: String

    val drawablePathIconEmail: String
    val drawablePathIconPhone: String
    val drawablePathIconExpandMore: String
    val drawablePathIconExpandLess: String
    val drawablePathIconPerson: String
  }

  private val _appLocaleLanguageState = MutableStateFlow("ru")
  private val _appThemeIdState = MutableStateFlow(0L)
  private val _appSizeModeIdState = MutableStateFlow(0L)

  private val _stringAppNameState = MutableStateFlow("AITAA")
  private val _stringLogInState = MutableStateFlow("Log Innnn")
  private val _stringPhoneNumberState = MutableStateFlow("Phone number")
  private val _stringEnterPhoneNumberState = MutableStateFlow("Enter phone number")
  private val _stringEmailState = MutableStateFlow("Email")
  private val _stringEnterEmailAddressState = MutableStateFlow("Enter email address")
  private val _stringPasswordState = MutableStateFlow("Password")
  private val _stringEnterPasswordState = MutableStateFlow("Enter password")
  private val _stringCancelState = MutableStateFlow("Cancel")
  private val _stringClearState = MutableStateFlow("Clear")
  private val _stringLoginAndOrPasswordIncorrectState = MutableStateFlow("Login and/or password incorrect")
  private val _stringPhoneNumberMustBeState = MutableStateFlow("Incorrect phone number length")
  private val _stringEmailMustBeState = MutableStateFlow("Incorrect email address format")
  private val _stringPasswordMustBeState = MutableStateFlow("Password must be 8 or more symbols long")
  private val _stringRepeatPasswordState = MutableStateFlow("Repeat password")
  private val _stringPasswordsMustMatchState = MutableStateFlow("Passwords must match")
  private val _stringFirstNameState = MutableStateFlow("First name")
  private val _stringLastNameState = MutableStateFlow("Last name")
  private val _stringEnterFirstNameState = MutableStateFlow("Enter first name")
  private val _stringEnterLastNameState = MutableStateFlow("Enter last name")
  private val _stringUserWithThisPhoneNumberIsAlreadyRegisteredState = MutableStateFlow("User with this phone number is already registered")
  private val _stringUserWithThisEmailAddressIsAlreadyRegisteredState = MutableStateFlow("User with this email address is already registered")
  private val _stringSignUpState = MutableStateFlow("Sign Up")

  private val _screenWidthState = MutableStateFlow(0f.dp)
  private val _screenHeightState = MutableStateFlow(0f.dp)
  private val _wideScreenMinWidthState = MutableStateFlow(600f)
  private val _boundWidgetWidthState = MutableStateFlow(280f.dp)

  private val _isNarrowScreenState = MutableStateFlow(false)

  private val _textSizeState = MutableStateFlow(14.sp)
  private val _titleTextSizeState = MutableStateFlow(20.sp)
  private val _accentTextSizeState = MutableStateFlow(16.sp)
  private val _smallTextSizeState = MutableStateFlow(12.sp)
  private val _focusedBorderWidthState = MutableStateFlow(1.dp)
  private val _unfocusedBorderWidthState = MutableStateFlow(0.5.dp)
  private val _cornerRadiusState = MutableStateFlow(14.dp)
  private val _iconSizeState = MutableStateFlow(24.dp)
  private val _textFieldHeightMultiplierRelativeToTextSizeState = MutableStateFlow(2.6f)
  private val _textFieldHeightState = MutableStateFlow(((_textSizeState.value.value * _textFieldHeightMultiplierRelativeToTextSizeState.value)).dp)
  private val _textFieldIconPaddingState = MutableStateFlow((9.dp))

  private val _AccentColorState = MutableStateFlow(Color(0xffffba24))
  private val _BackgroundColorState = MutableStateFlow(Color(0xffffffff))
  private val _TextColorState = MutableStateFlow(Color(0xffffffff))
  private val _AccentTextColorState = MutableStateFlow(Color(0xffffffff))
  private val _PlaceholderTextColorState = MutableStateFlow(Color(0xaa000000))
  private val _DisabledColorState = MutableStateFlow(Color(0xffa7a7a7))
  private val _ErrorColorState = MutableStateFlow(Color(0xffff0000))

  private val _drawablePathAITALogoState = MutableStateFlow("svg/00.svg")
  private val _drawablePathIconPasswordState = MutableStateFlow("svg/10.svg")
  private val _drawablePathIconCancelState = MutableStateFlow("svg/20.svg")
  private val _drawablePathIconEyeHideState = MutableStateFlow("svg/30.svg")
  private val _drawablePathIconEyeShowState = MutableStateFlow("svg/40.svg")
  private val _drawablePathIconEmailState = MutableStateFlow("svg/50.svg")
  private val _drawablePathIconPhoneState = MutableStateFlow("svg/60.svg")
  private val _drawablePathIconExpandMoreState = MutableStateFlow("svg/70.svg")
  private val _drawablePathIconExpandLessState = MutableStateFlow("svg/80.svg")
  private val _drawablePathIconPersonState = MutableStateFlow("svg/80.svg")

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

      override val globalAppConfiguration: GlobalAppConfigurationDataModel by configurationRepository.globalAppConfigurationState.payload.collectAsState()
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
      override val stringEnterPhoneNumber: String by _stringEnterPhoneNumberState.collectAsState()
      override val stringEmail: String by _stringEmailState.collectAsState()
      override val stringEnterEmailAddress: String by _stringEnterEmailAddressState.collectAsState()
      override val stringPassword: String by _stringPasswordState.collectAsState()
      override val stringEnterPassword: String by _stringEnterPasswordState.collectAsState()
      override val stringCancel: String by _stringCancelState.collectAsState()
      override val stringClear: String by _stringClearState.collectAsState()
      override val stringLoginAndOrPasswordIncorrect: String by _stringLoginAndOrPasswordIncorrectState.collectAsState()
      override val stringPhoneNumberMustBe: String by _stringPhoneNumberMustBeState.collectAsState()
      override val stringEmailMustBe: String by _stringEmailMustBeState.collectAsState()
      override val stringPasswordMustBe: String by _stringPasswordMustBeState.collectAsState()
      override val stringRepeatPassword: String by _stringRepeatPasswordState.collectAsState()
      override val stringPasswordsMustMatch: String by _stringPasswordsMustMatchState.collectAsState()
      override val stringFirstName: String by _stringFirstNameState.collectAsState()
      override val stringLastName: String by _stringLastNameState.collectAsState()
      override val stringEnterFirstName: String by _stringEnterFirstNameState.collectAsState()
      override val stringEnterLastName: String by _stringEnterLastNameState.collectAsState()
      override val stringUserWithThisPhoneNumberIsAlreadyRegistered: String by _stringUserWithThisPhoneNumberIsAlreadyRegisteredState.collectAsState()
      override val stringUserWithThisEmailAddressIsAlreadyRegistered: String by _stringUserWithThisEmailAddressIsAlreadyRegisteredState.collectAsState()
      override val stringSignUp: String by _stringSignUpState.collectAsState()

      override val screenWidth: Dp by _screenWidthState.collectAsState()
      override val screenHeight: Dp by _screenHeightState.collectAsState()
      override val wideScreenMinWidth: Float by _wideScreenMinWidthState.collectAsState()
      override val boundWidgetWidth: Dp by _boundWidgetWidthState.collectAsState()
      override val isNarrowScreen: Boolean by _isNarrowScreenState.collectAsState()
      override val textSize: TextUnit by _textSizeState.collectAsState()
      override val titleTextSize: TextUnit by _titleTextSizeState.collectAsState()
      override val accentTextSize: TextUnit by _accentTextSizeState.collectAsState()
      override val smallTextSize: TextUnit by _smallTextSizeState.collectAsState()
      override val focusedBorderWidth: Dp by _focusedBorderWidthState.collectAsState()
      override val unfocusedBorderWidth: Dp by _unfocusedBorderWidthState.collectAsState()
      override val cornerRadius: Dp by _cornerRadiusState.collectAsState()
      override val iconSize: Dp by _iconSizeState.collectAsState()
      override val textFieldHeightMultiplierRelativeToTextSize: Float by _textFieldHeightMultiplierRelativeToTextSizeState.collectAsState()
      override val textFieldHeight: Dp by _textFieldHeightState.collectAsState()
      override val textFieldIconPadding: Dp by _textFieldIconPaddingState.collectAsState()

      override val AccentColor: Color by _AccentColorState.collectAsState()
      override val BackgroundColor: Color by _BackgroundColorState.collectAsState()
      override val TextColor: Color by _TextColorState.collectAsState()
      override val AccentTextColor: Color by _AccentTextColorState.collectAsState()
      override val PlaceholderTextColor: Color by _PlaceholderTextColorState.collectAsState()
      override val DisabledColor: Color by _DisabledColorState.collectAsState()
      override val ErrorColor: Color by _ErrorColorState.collectAsState()

      override val drawablePathAITALogo: String by _drawablePathAITALogoState.collectAsState()
      override val drawablePathIconPassword: String by _drawablePathIconPasswordState.collectAsState()
      override val drawablePathIconCancel: String by _drawablePathIconCancelState.collectAsState()
      override val drawablePathIconEyeHide: String by _drawablePathIconEyeHideState.collectAsState()
      override val drawablePathIconEyeShow: String by _drawablePathIconEyeShowState.collectAsState()
      override val drawablePathIconEmail: String by _drawablePathIconEmailState.collectAsState()
      override val drawablePathIconPhone: String by _drawablePathIconPhoneState.collectAsState()
      override val drawablePathIconExpandMore: String by _drawablePathIconExpandMoreState.collectAsState()
      override val drawablePathIconExpandLess: String by _drawablePathIconExpandLessState.collectAsState()
      override val drawablePathIconPerson: String by _drawablePathIconPersonState.collectAsState()
    }

    coroutineScope = rememberCoroutineScope()

    key(keys) {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
      ) {
        if (!stateValues.isNarrowScreen)
          narrowScreenContent()
        else
          wideScreenContent()

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
                _stringEnterPhoneNumberState.emit(it.extractString(3, stateValues.appLocaleLanguage))
                _stringEmailState.emit(it.extractString(4, stateValues.appLocaleLanguage))
                _stringEnterEmailAddressState.emit(it.extractString(5, stateValues.appLocaleLanguage))
                _stringPasswordState.emit(it.extractString(6, stateValues.appLocaleLanguage))
                _stringEnterPasswordState.emit(it.extractString(7, stateValues.appLocaleLanguage))
                _stringCancelState.emit(it.extractString(8, stateValues.appLocaleLanguage))
                _stringClearState.emit(it.extractString(9, stateValues.appLocaleLanguage))
                _stringLoginAndOrPasswordIncorrectState.emit(it.extractString(10, stateValues.appLocaleLanguage))
                _stringPhoneNumberMustBeState.emit(it.extractString(11, stateValues.appLocaleLanguage))
                _stringEmailMustBeState.emit(it.extractString(12, stateValues.appLocaleLanguage))
                _stringPasswordMustBeState.emit(it.extractString(13, stateValues.appLocaleLanguage))
                _stringRepeatPasswordState.emit(it.extractString(14, stateValues.appLocaleLanguage))
                _stringPasswordsMustMatchState.emit(it.extractString(15, stateValues.appLocaleLanguage))
                _stringFirstNameState.emit(it.extractString(16, stateValues.appLocaleLanguage))
                _stringLastNameState.emit(it.extractString(17, stateValues.appLocaleLanguage))
                _stringEnterFirstNameState.emit(it.extractString(18, stateValues.appLocaleLanguage))
                _stringEnterLastNameState.emit(it.extractString(19, stateValues.appLocaleLanguage))
                _stringUserWithThisPhoneNumberIsAlreadyRegisteredState.emit(it.extractString(20, stateValues.appLocaleLanguage))
                _stringUserWithThisEmailAddressIsAlreadyRegisteredState.emit(it.extractString(21, stateValues.appLocaleLanguage))
                _stringSignUpState.emit(it.extractString(22, stateValues.appLocaleLanguage))
              }
          }

          launch {
            configurationRepository
              .dimensionsState
              .payload
              .collect {
                it?.let {
                  _wideScreenMinWidthState.emit(it.extractValue(4, stateValues.appSizeModeId))
                  _boundWidgetWidthState.emit(it.extractValue(9, stateValues.appSizeModeId).dp)

                  _textSizeState.emit(it.extractValue(0, stateValues.appSizeModeId).sp)
                  _titleTextSizeState.emit(it.extractValue(1, stateValues.appSizeModeId).sp)
                  _accentTextSizeState.emit(it.extractValue(2, stateValues.appSizeModeId).sp)
                  _smallTextSizeState.emit(it.extractValue(3, stateValues.appSizeModeId).sp)

                  _focusedBorderWidthState.emit(it.extractValue(5, stateValues.appSizeModeId).dp)
                  _unfocusedBorderWidthState.emit(it.extractValue(6, stateValues.appSizeModeId).dp)

                  _cornerRadiusState.emit(it.extractValue(7, stateValues.appSizeModeId).dp)
                  _iconSizeState.emit(it.extractValue(8, stateValues.appSizeModeId).dp)
                  _textFieldHeightMultiplierRelativeToTextSizeState.emit(it.extractValue(10, stateValues.appSizeModeId))
                  _textFieldHeightState.emit((_textSizeState.value.value * _textFieldHeightMultiplierRelativeToTextSizeState.value).dp)
                  _textFieldIconPaddingState.emit(it.extractValue(11, stateValues.appSizeModeId).dp)
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
                  _DisabledColorState.emit(it.extractColor(5, stateValues.appThemeId).toColor())
                  _ErrorColorState.emit(it.extractColor(6, stateValues.appThemeId).toColor())
                }
              }
          }

          launch {
            configurationRepository
              .drawablesState
              .payload
              .collect {
                it?.let {
                  _drawablePathAITALogoState.emit(it.extractPath(0, stateValues.appThemeId))
                  _drawablePathIconPasswordState.emit(it.extractPath(1, stateValues.appThemeId))
                  _drawablePathIconCancelState.emit(it.extractPath(2, stateValues.appThemeId))
                  _drawablePathIconEyeHideState.emit(it.extractPath(3, stateValues.appThemeId))
                  _drawablePathIconEyeShowState.emit(it.extractPath(4, stateValues.appThemeId))
                  _drawablePathIconEmailState.emit(it.extractPath(5, stateValues.appThemeId))
                  _drawablePathIconExpandMoreState.emit(it.extractPath(7, stateValues.appThemeId))
                  _drawablePathIconExpandLessState.emit(it.extractPath(8, stateValues.appThemeId))
                  _drawablePathIconPersonState.emit(it.extractPath(9, stateValues.appThemeId))
                }
              }
          }

          _isNarrowScreenState.emit(stateValues.screenWidth.value < stateValues.wideScreenMinWidth)
        }
      }
    }
  }
}