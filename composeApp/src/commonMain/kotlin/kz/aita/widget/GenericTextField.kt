package kz.aita.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kz.aita.AppUIConfiguration
import kz.aita.util.UtilUI.getTransformedTextWithSelectionFocusTextColor
import kz.aita.wrapper.TextFieldContent

@Composable
fun AppUIConfiguration.genericTextField(
  modifier: Modifier = Modifier,

  enabled: Boolean = true,

  valueInitial: String = "",

  titleText: String = "",

  titleTextSize: TextUnit = stateValues.textSize,
  titleTextColor: Color = stateValues.TextColor,

  textSize: TextUnit = stateValues.textSize,
  textColor: Color = stateValues.TextColor,

  isFocusedInitial: Boolean = false,
  focusRequester: FocusRequester? = null,
  updateIsFocusedAction: ((FocusState) -> Unit)? = null,

  placeholderTextSize: TextUnit = stateValues.textSize,
  placeholderTextColor: Color = stateValues.PlaceholderTextColor,
  onPlaceholderTextClick: (() -> Unit)? = null,

  selectionFocusTextColor: Color = stateValues.AccentTextColor,
  selectionBackgroundColor: Color = stateValues.AccentColor,

  focusedBorderWidth: Dp = stateValues.focusedBorderWidth,
  unfocusedBorderWidth: Dp = stateValues.unfocusedBorderWidth,

  focusedBorderColor: Color = stateValues.AccentColor,
  unfocusedBorderColor: Color = stateValues.TextColor,

  backgroundColor: Color = stateValues.BackgroundColor,

  cornerRadius: Dp = stateValues.cornerRadius,

  keyboardType: KeyboardType = KeyboardType.Text,
  imeAction: ImeAction = ImeAction.Next,
  keyboardActions: KeyboardActions = KeyboardActions.Default,

  leadingIcon: @Composable (() -> Unit)? = null,
  leadingIconPainter: Painter? = null,

  leadingIconSize: Dp = stateValues.iconSize,
  leadingIconContentDescription: String = "",
  leadingIconFocusedTintColor: Color = stateValues.AccentColor,
  leadingIconUnfocusedTintColor: Color = stateValues.TextColor,

  trailingIconPainter: Painter? = null,
  trailingIconSize: Dp = leadingIconSize,
  trailingIconContentDescription: String = "",
  trailingIconTintColor: Color = textColor,
  onTrailingIconClick: (() -> Unit)? = null,
  visualTransformation: (TextFieldValue) -> TransformedText = {
    getTransformedTextWithSelectionFocusTextColor(it, selectionFocusTextColor)
  },
  onValueChange: ((updateAction: () -> Unit) -> Unit)? = null
): TextFieldContent {

  var value by rememberSaveable(TextFieldValue.Saver) {
    mutableStateOf(TextFieldValue(valueInitial))
  }

  var isFocused by rememberSaveable {
    mutableStateOf(isFocusedInitial)
  }

  var focusRequester by rememberSaveable {
    mutableStateOf(FocusRequester())
  }

  val focusManager = LocalFocusManager.current

  Column(modifier = modifier) {
    val titleTextPresent = titleText.isNotEmpty() && titleText.isNotBlank()

    if (titleTextPresent)
      Text(
        text = titleText,
        style = TextStyle(
          color = titleTextColor,
          fontSize = titleTextSize,
          fontWeight = FontWeight.Bold
        )
      )

    val textSelectionColors = TextSelectionColors(
      handleColor = selectionBackgroundColor,
      backgroundColor = selectionBackgroundColor
    )

    CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
      BasicTextField(
        value = value,
        onValueChange = {
          if (onValueChange != null) {
            onValueChange.run {
              value = it
            }
          } else
            value = it
        },
        enabled = enabled,
        modifier = Modifier
          .clip(RoundedCornerShape(cornerRadius))
          .background(backgroundColor)
          .border(
            width = if (isFocused) focusedBorderWidth else unfocusedBorderWidth,
            color = if (isFocused) focusedBorderColor else unfocusedBorderColor,
            shape = RoundedCornerShape(cornerRadius)
          )
          .run {
            updateIsFocusedAction?.let {
              onFocusChanged(it)
            } ?: this
          }
          .run {
            focusRequester?.let {
              focusRequester(it)
            } ?: this
          },
        keyboardOptions = KeyboardOptions.Default.copy(
          keyboardType = keyboardType,
          imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        textStyle = TextStyle(
          fontSize = textSize,
          color = textColor
        ),
        visualTransformation = {
          visualTransformation(value)
        },
        singleLine = true,
        cursorBrush = SolidColor(selectionBackgroundColor),
        decorationBox = { innerTextField ->
          Box(
            modifier = Modifier
              .fillMaxWidth()
          ) {
            Row {
              leadingIcon?.run {
                Box {
                  invoke()
                }
              } ?: leadingIconPainter?.run {
                Icon(
                  modifier = Modifier
                    .size(leadingIconSize),
                  painter = leadingIconPainter,
                  contentDescription = leadingIconContentDescription,
                  tint = if (isFocused) leadingIconFocusedTintColor else leadingIconUnfocusedTintColor
                )
              }

              Box {
                innerTextField()
              }

              LaunchedEffect(Unit) {
                if (isFocused)
                  focusRequester.requestFocus()
                else
                  focusManager.clearFocus()
              }

//              if (value.text.isEmpty()) {
//                val lModifier = onPlaceholderTextClick?.run {
//                  if (!isFocused)
//                    Modifier
//                  else
//                    Modifier
//                      .clickable(
//                        interactionSource = remember {
//                          MutableInteractionSource()
//                        },
//                        indication = ripple(color = Color.Transparent, radius = 0.dp)
//                      ) {
//                        onPlaceholderTextClick()
//                      }
//                }
//
//                Text(
//                  text = placeholderText,
//                  modifier = (lModifier ?: Modifier)
//                    .alpha(0.5f),
//                  fontSize = placeholderTextSize,
//                  color = placeholderTextColor,
//                  maxLines = 1,
//                  overflow = TextOverflow.Ellipsis,
//                )
//              }

//              trailingIconPainter?.run {
//                if (isFocused || value.text.isNotEmpty())
//                  Icon(
//                    modifier = Modifier
//                      .clip(RoundedCornerShape(cornerRadius)
//                        .background(backgroundColor)
//                        .size(trailingIconSize)
//                        .clickable(
//                          interactionSource = remember {
//                            MutableInteractionSource()
//                          },
//                          indication = ripple(color = trailingIconTintColor),
//                          onClick = onTrailingIconClick
//                        ),
//                        painter = this,
//                        contentDescription = trailingIconContentDescription,
//                        tint = trailingIconTintColor
//                      )
//              }
            }
          }
        }
      )
    }
  }

  return TextFieldContent(
    value = value,
    isFocused = isFocused
  )
}
