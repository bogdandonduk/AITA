package kz.aita.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kz.aita.AppUIConfiguration
import kz.aita.util.checkAsEmail
import kz.aita.model.ImeWithAction
import kz.aita.model.TextFieldContent

@Composable
fun AppUIConfiguration.emailTextField(
  modifier: Modifier = Modifier,
  imeWithAction: ImeWithAction? = null
): TextFieldContent {

  return genericTextField(
    modifier = modifier,
    titleText = stateValues.stringEmail,
    placeholderText = stateValues.stringEnterEmail,
    leadingIconPath = stateValues.drawablePathIconEmail,
    keyboardType = KeyboardType.Email,
    imeWithAction = imeWithAction ?: ImeWithAction.Default,
    contentInvalidText = "Fucking invalid email",
    onContentValidityCheck = {
      it.checkAsEmail()
    },
  )
}