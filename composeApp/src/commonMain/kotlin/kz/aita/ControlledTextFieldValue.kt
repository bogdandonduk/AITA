package kz.aita

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Keep accepted typing selection/IME composition; replace only rejected or external text. */
internal fun reconcileControlledTextFieldValue(
    editorValue: TextFieldValue,
    parentText: String
): TextFieldValue = if (editorValue.text == parentText) {
    editorValue
} else {
    // A keypad/preset replacement or parent clamp is not an IME composition.
    TextFieldValue(text = parentText, selection = TextRange(parentText.length))
}
