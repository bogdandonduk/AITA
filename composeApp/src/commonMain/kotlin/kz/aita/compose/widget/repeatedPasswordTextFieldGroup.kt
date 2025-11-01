package kz.aita.compose.widget

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kz.aita.AppConfiguration
import kz.aita.core.checkAsPassword
import kz.aita.compose.util.getPasswordTransformedTextWithSelectionFocusTextColor
import kz.aita.compose.util.getTransformedTextWithSelectionFocusTextColor
import kz.aita.compose.wrapper.ImeWithAction
import kz.aita.core.StateHost

@Composable
fun AppConfiguration.repeatedPasswordTextFieldGroup(
  modifier: Modifier = Modifier,
  stateHost: StateHost,
  stateKey: String,
  repeatedStateKey: String,
  passwordTitleText: String? = null,
  passwordPlaceholderText: String? = null,
  repeatPasswordTitleText: String? = null,
  repeatPasswordPlaceholderText: String? = null,
  imeWithAction: ImeWithAction? = null
): Pair<GenericTextFieldContent, GenericTextFieldContent> {

  var showPassword by rememberSaveable {
    mutableStateOf(false)
  }

  val passwordTextFieldContent = genericTextField(
    modifier = modifier,
    stateHost = stateHost,
    stateKey = stateKey,
    titleText = passwordTitleText ?: stateValues.stringPassword,
    placeholderText = passwordPlaceholderText ?: stateValues.stringEnterPassword,
    leadingIconPath = stateValues.drawablePathIconPassword,
    keyboardType = KeyboardType.Password,
    imeWithAction = imeWithAction ?: ImeWithAction.Default,
    trailingIconExtraPath = if (showPassword) stateValues.drawablePathIconEyeHide else stateValues.drawablePathIconEyeShow,
    trailingIconExtraOnClick = {
      showPassword = !showPassword
    },
    contentInvalidText = stateValues.stringPasswordMustBe,
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

  Spacer(modifier = Modifier
    .height(stateValues.marginTextField)
  )

  val repeatedPasswordTextFieldContent = genericTextField(
    modifier = modifier,
    stateHost = stateHost,
    stateKey = repeatedStateKey,
    titleText = repeatPasswordTitleText ?: stateValues.stringRepeatPassword,
    placeholderText = repeatPasswordPlaceholderText ?: stateValues.stringRepeatPassword,
    leadingIconPath = stateValues.drawablePathIconPassword,
    keyboardType = KeyboardType.Password,
    imeWithAction = imeWithAction ?: ImeWithAction.Default,
    contentInvalidText = stateValues.stringPasswordsMustMatch,
    onContentValidityCheck = {
      it == passwordTextFieldContent.value.text
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

  return Pair(passwordTextFieldContent, repeatedPasswordTextFieldContent)
}