package kz.aita.compose.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.withStyle


fun getTransformedTextWithSelectionFocusTextColor(
  textFieldValue: TextFieldValue,
  selectionFocusTextColor: Color
): TransformedText {
  return AnnotatedString.Builder()
    .apply {
      for (i in textFieldValue.text.indices) {
        if (i in textFieldValue.selection.min until textFieldValue.selection.max)
          withStyle(SpanStyle(color = selectionFocusTextColor)) { append(textFieldValue.text[i]) }
        else
          append(textFieldValue.text[i])
      }
    }
    .toAnnotatedString()
    .run {
      TransformedText(
        this,
        offsetMapping = OffsetMapping.Identity
      )
    }
}

fun getPasswordTransformedTextWithSelectionFocusTextColor(
  textFieldValue: TextFieldValue,
  selectionFocusTextColor: Color
) : TransformedText {
  return AnnotatedString.Builder()
    .apply {
      val maskChar = '•'
      for (i in textFieldValue.text.indices) {
        if (i in textFieldValue.selection.min until textFieldValue.selection.max)
          withStyle(SpanStyle(color = selectionFocusTextColor)) { append (maskChar) }
        else
          append(maskChar)
      }
    }
    .toAnnotatedString()
    .run {
      TransformedText(
        this,
        offsetMapping = OffsetMapping.Identity
      )
    }
}
