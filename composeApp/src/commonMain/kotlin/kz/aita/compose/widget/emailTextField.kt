package kz.aita.compose.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kz.aita.AppConfiguration
import kz.aita.compose.util.checkAsEmail
import kz.aita.compose.wrapper.ImeWithAction

@Composable
fun AppConfiguration.emailTextField(
  modifier: Modifier = Modifier,
  imeWithAction: ImeWithAction? = null
): GenericTextFieldContent {

  return genericTextField(
    modifier = modifier,
    valueInitial = "norbuchin@gmail.com",
    titleText = stateValues.stringEmail,
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