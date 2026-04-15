package kz.aita.compose

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction

class ImeWithAction(
  val ime: ImeAction,
  private val action: (() -> Unit)? = null
) {

  companion object {

    val Default: ImeWithAction = ImeWithAction(ImeAction.Companion.Next)
  }

  fun getKeyboardActions(): KeyboardActions {
    return action?.run {
      when (ime.toString()) {
        "Go" -> KeyboardActions(
          onGo = {
            action()
          }
        )
        "Search" -> KeyboardActions(
          onSearch = {
            action()
          }
        )
        "Send" -> KeyboardActions(
          onSend = {
            action()
          }
        )
        "Previous" -> KeyboardActions(
          onPrevious = {
            action()
          }
        )
        "Next" -> KeyboardActions(
          onNext = {
            action()
          }
        )
        "Done" -> KeyboardActions(
          onDone = {
            action()
          }
        )
        else -> KeyboardActions.Companion.Default
      }
    } ?: KeyboardActions.Companion.Default
  }
}