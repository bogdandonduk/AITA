package kz.aita.compose.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kz.aita.AppConfiguration
import kz.aita.compose.util.checkAsPassword
import kz.aita.compose.util.getPasswordTransformedTextWithSelectionFocusTextColor
import kz.aita.compose.util.getTransformedTextWithSelectionFocusTextColor
import kz.aita.compose.wrapper.ImeWithAction
import kz.aita.core.StateHost

@Composable
fun AppConfiguration.passwordTextField(
  modifier: Modifier = Modifier,
  titleText: String? = null,
  placeholderText: String? = null,
  stateHost: StateHost,
  stateKey: String,
  contentInvalidText: String? = null,
  imeWithAction: ImeWithAction? = null
): GenericTextFieldContent {

  var showPassword by rememberSaveable {
    mutableStateOf(false)
  }

  return genericTextField(
    modifier = modifier,
    stateHost = stateHost,
    stateKey = stateKey,
    titleText = titleText ?: stateValues.stringPassword,
    placeholderText = placeholderText ?: stateValues.stringEnterPassword,
    leadingIconPath = stateValues.drawablePathIconPassword,
    keyboardType = KeyboardType.Password,
    imeWithAction = imeWithAction ?: ImeWithAction.Default,
    trailingIconExtraPath = if (showPassword) stateValues.drawablePathIconEyeHide else stateValues.drawablePathIconEyeShow,
    trailingIconExtraOnClick = {
      showPassword = !showPassword
    },
    contentInvalidText = contentInvalidText ?: stateValues.stringPasswordMustBe,
    onContentValidityCheck = {
      it.checkAsPassword()
    },
    visualTransformation =
      if (showPassword) {
        {
          getTransformedTextWithSelectionFocusTextColor(
            it,
            stateValues.AccentTextColor
          )
        }
      } else {
        {
          getPasswordTransformedTextWithSelectionFocusTextColor(
            it,
            stateValues.AccentTextColor
          )
        }
      }
  )
}