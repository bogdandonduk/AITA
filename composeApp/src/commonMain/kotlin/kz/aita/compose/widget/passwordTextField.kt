package kz.aita.compose.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kz.aita.AppUIConfiguration
import kz.aita.compose.util.checkAsPassword
import kz.aita.compose.util.getPasswordTransformedTextWithSelectionFocusTextColor
import kz.aita.compose.util.getTransformedTextWithSelectionFocusTextColor
import kz.aita.compose.wrapper.ImeWithAction

@Composable
fun AppUIConfiguration.passwordTextField(
  modifier: Modifier = Modifier,
  imeWithAction: ImeWithAction? = null
): GenericTextFieldContent {

  var showPassword by rememberSaveable {
    mutableStateOf(false)
  }

  return genericTextField(
    modifier = modifier,
    titleText = stateValues.stringPassword,
    placeholderText = stateValues.stringEnterPassword,
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
}