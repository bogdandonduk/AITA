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
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration
import kz.aita.compose.util.checkAsPassword
import kz.aita.compose.util.getPasswordTransformedTextWithSelectionFocusTextColor
import kz.aita.compose.util.getTransformedTextWithSelectionFocusTextColor
import kz.aita.compose.wrapper.ImeWithAction

@Composable
fun AppConfiguration.repeatedPasswordTextFieldGroup(
  modifier: Modifier = Modifier,
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
    .height(8.dp)
  )

  val repeatedPasswordTextFieldContent = genericTextField(
    modifier = modifier,
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