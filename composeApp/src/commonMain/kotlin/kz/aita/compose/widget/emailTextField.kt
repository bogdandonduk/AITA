package kz.aita.compose.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kz.aita.AppConfiguration
import kz.aita.compose.util.checkAsEmail
import kz.aita.compose.wrapper.ImeWithAction
import kz.aita.core.StateHost

@Composable
fun AppConfiguration.emailTextField(
  modifier: Modifier = Modifier,
  stateHost: StateHost,
  stateKey: String,
  valueInitial: String? = null,
  imeWithAction: ImeWithAction? = null,
): GenericTextFieldContent {

  return genericTextField(
    modifier = modifier,
    valueInitial = valueInitial,
    titleText = stateValues.stringEmail,
    stateHost = stateHost,
    stateKey = stateKey,
    placeholderText = stateValues.stringEnterEmailAddress,
    leadingIconPath = stateValues.drawablePathIconEmail,
    keyboardType = KeyboardType.Email,
    imeWithAction = imeWithAction ?: ImeWithAction.Default,
    contentInvalidText = stateValues.stringEmailMustBe,
    onContentValidityCheck = {
      it.checkAsEmail()
    }
  )
}