package kz.aita.model

import androidx.compose.ui.text.input.TextFieldValue

data class TextFieldContent(
  var value: TextFieldValue,
  var isFocused: Boolean,
  var isContentValid: Boolean,
  private val onContentValidityCheck: ((String) -> Boolean)? = null,
) {

  fun checkContentValidity() {
    isContentValid = onContentValidityCheck?.invoke(value.text) ?: true
  }
}