package kz.aita.wrapper

import androidx.compose.ui.text.input.TextFieldValue

data class TextFieldContent(
  var value: TextFieldValue,
  var isFocused: Boolean
)