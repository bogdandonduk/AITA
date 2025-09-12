package kz.aita.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.aita.core.configuration.AppUIConfiguration
import kz.aita.util.UtilUI.getTransformedTextWithSelectionFocusTextColor

@Composable
fun AppUIConfiguration.GenericTextField(
  modifier: Modifier = Modifier,
  value: TextFieldValue,
  isFocused: Boolean,
  focusRequester: FocusRequester? = null,
  enabled: Boolean = true,
  updateIsFocusedAction: (FocusState) -> Unit,
  onPlaceholderTextClick: (() -> Unit)? = null,
  titleText: String,
  titleTextColor: Color = stateValues.TextColor,
  titleTextSize: TextUnit = mediumTextSize,
  textSize: TextUnit = defaultTextSize,
  textColor: Color = stateValues.TextColor,
  textWeight: FontWeight = FontWeight.Normal,
  textAlign: TextAlign = TextAlign.Start,
  placeholderText: String,
  placeholderTextSize: TextUnit = textSize,
  placeholderTextColor: Color = stateValues.TextColorSemiTransparent,
  selectionFocusTextColor: Color = stateValues.AccentTextColor,
  selectionBackgroundColor: Color = stateValues.AccentColor,
  focusedBorderWidth: Dp = AppUIConfiguration.focusedBorderWidth,
  notFocusedBorderWidth: Dp = AppUIConfiguration.notFocusedBorderWidth,
  focusedBorderColor: Color,
  notFocusedBorderColor: Color,
  backgroundColor: Color,
  cornerRadius: Dp = xxxLargeCornerRadius,
  keyboardType: KeyboardType = KeyboardType.Text,
  imeAction: ImeAction = ImeAction.Next,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  leadingIcon: @Composable (() -> Unit)? = null,
  leadingIconPainter: Painter? = null,
  leadingIconSize: Dp = largeIconSize,
  leadingIconContentDescription: String = "",
  leadingIconTintColor: Color = ,
  trailingIconPainter: Painter? = null,
  trailingIconSize: Dp = leadingIconSize,
  trailingIconContentDescription: String = "",
  trailingIconTintColor: Color = textColor,
  onTrailingIconClick: () -> Unit = {},
  visualTransformation: (TextFieldValue) -> TransformedText = {
    getTransformedTextWithSelectionFocusTextColor(it, selectionFocusTextColor)
  },
  onValueChange: (TextFieldValue) -> Unit
) {
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
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = Modifier
          .clip(RoundedCornerShape(cornerRadius))
          .background(backgroundColor)
          .border(
            width = if (isFocused) focusedBorderWidth else notFocusedBorderWidth,
            color = if (isFocused) focusedBorderColor else notFocusedBorderColor,
            shape = RoundedCornerShape(cornerRadius)
          )
          .onFocusChanged(updateIsFocusedAction)
          .run {
            if (focusRequester != null)
              focusRequester(focusRequester)
            else
              this
          },
        keyboardOptions = KeyboardOptions.Default.copy(
          keyboardType = keyboardType,
          imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        textStyle = TextStyle(
          fontSize = textSize,
          color = textColor,
          fontWeight = textWeight,
          textAlign = textAlign
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
                Box(
                  modifier = Modifier
                ) {
                  invoke()
                }
              } ?: leadingIconPainter?.run {
                Icon(
                  modifier = Modifier
                    .size(leadingIconSize),
                  painter = leadingIconPainter,
                  contentDescription = leadingIconContentDescription,
                  tint = leadingIconTintColor
                )
              }

              Box {
                innerTextField()
              }

//              LaunchedEffect(Unit) {
//                if (focusRequester != null && isFocused)
//                  focusRequester.requestFocus()
//              }

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
}
