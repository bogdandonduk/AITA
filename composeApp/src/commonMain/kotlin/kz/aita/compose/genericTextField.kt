package kz.aita.compose

import aita.composeapp.generated.resources.Res
import aita.composeapp.generated.resources._9_0
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
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
import io.kamel.image.config.LocalKamelConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kz.aita.StateHost

@Composable
fun AppConfiguration.genericTextField(
  modifier: Modifier = Modifier,

  titleText: String = "",

  stateHost: StateHost?,
  stateKey: String?,

  enabled: Boolean = true,
  wide: Boolean = false,
  valueInitial: String? = null,

  textSize: TextUnit = stateValues.textSize,
  textColor: Color = stateValues.TextColor,

  titleTextSize: TextUnit = stateValues.accentTextSize,
  titleTextColor: Color = textColor,

  isFocusedInitial: Boolean = false,
  updateIsFocusedAction: ((FocusState) -> Unit)? = null,
  forceRefocus: Boolean = false,
  placeholderText: String = "",
  placeholderTextSize: TextUnit = stateValues.textSize,
  placeholderTextColor: Color = stateValues.PlaceholderTextColor,

  selectionFocusTextColor: Color = stateValues.AccentTextColor,
  selectionBackgroundColor: Color = stateValues.AccentColor,

  focusedBorderWidth: Dp = stateValues.focusedBorderWidth,
  unfocusedBorderWidth: Dp = stateValues.unfocusedBorderWidth,

  focusedBorderColor: Color = stateValues.AccentColor,
  unfocusedBorderColor: Color = textColor,

  backgroundColor: Color = stateValues.BackgroundColor,

  cornerRadius: Dp = stateValues.cornerRadius,
  cornerShape: Shape = RoundedCornerShape(cornerRadius),
  keyboardType: KeyboardType = KeyboardType.Text,
  imeWithAction: ImeWithAction? = null,

  leadingIconPath: String? = null,
  leadingIcon: @Composable (() -> Unit)? = null,
  leadingIconContentDescription: String = placeholderText,
  trailingIcon: @Composable (() -> Unit)? = null,
  trailingIconExtraPath: String? = null,
  trailingIconExtraContentDescription: String = placeholderText,
  trailingIconExtraOnClick: (() -> Unit)? = null,
  visualTransformation: (TextFieldValue) -> TransformedText = {
    getTransformedTextWithSelectionFocusTextColor(it, selectionFocusTextColor)
  },
  contentInvalidText: String? = null,
  onContentValidityCheck: ((String) -> Boolean)? = null,
  onFilterValue: ((String) -> Boolean)? = null,
  onValueChange: ((String, () -> Unit) -> Unit)? = null
): GenericTextFieldContent {

  val state: Map<String, String>? by stateHost?.state?.collectAsState() ?: remember {
    mutableStateOf(emptyMap())
  }

  val stateValue = state?.get(stateKey)

  var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
    val initial = stateValue ?: valueInitial ?: ""
    mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
  }

  var isFocused by rememberSaveable {
    mutableStateOf(isFocusedInitial)
  }

  var focusRequester by remember {
    mutableStateOf(FocusRequester())
  }

  var isContentValid by rememberSaveable {
    mutableStateOf(true)
  }

  LaunchedEffect(valueInitial) {
    val initial = valueInitial ?: ""
    textFieldValue = TextFieldValue(initial, selection = TextRange(initial.length))
  }
  Column(
    modifier = modifier
  ) {
    val titleTextPresent = titleText.isNotEmpty() && titleText.isNotBlank()

    if (titleTextPresent)
      Text(
        modifier = Modifier
          .padding(bottom = 4.dp),
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
        value = textFieldValue,
        onValueChange = {
          if (onValueChange != null) {
            onValueChange(it.text) {
              if (onFilterValue == null || onFilterValue(it.text)) {
                textFieldValue = it

                stateKey?.run {
                  coroutineScope.launch {
                    stateHost?.setState(stateKey to it.text)
                  }
                }

              }
            }
          } else {
            if (onFilterValue == null || onFilterValue(it.text)) {
              textFieldValue = it
              stateKey?.run {
                coroutineScope.launch {
                  stateHost?.setState(stateKey to it.text)
                }
              }
            }
          }
        },
        enabled = enabled,
        modifier = Modifier
          .height(if (wide) stateValues.wideTextFieldHeight else stateValues.textFieldHeight)
          .clip(cornerShape)
          .background(backgroundColor)
          .border(
            width = if (isFocused) focusedBorderWidth else unfocusedBorderWidth,
            color = if (isFocused) focusedBorderColor else unfocusedBorderColor,
            shape = cornerShape
          )
          .focusRequester(focusRequester)
          .onFocusChanged {
            isFocused = it.isFocused

            updateIsFocusedAction?.invoke(it)
          },
        keyboardOptions = KeyboardOptions.Default.copy(
          keyboardType = keyboardType,
          imeAction = (imeWithAction ?: ImeWithAction(ime = ImeAction.Default)).ime
        ),
        keyboardActions = (imeWithAction ?: ImeWithAction(ime = ImeAction.Default)).getKeyboardActions(),
        textStyle = TextStyle(
          fontSize = textSize,
          color = textColor
        ),
        visualTransformation = {
          visualTransformation(textFieldValue)
        },
        singleLine = true,
        cursorBrush = SolidColor(selectionBackgroundColor),
        decorationBox = { innerTextField ->
          Box(
            modifier = modifier
              .fillMaxWidth()
          ) {
            Row(
              Modifier
                .fillMaxSize(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              leadingIcon?.invoke() ?: leadingIconPath?.run {
                CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
                  CpImage(
                    modifier = Modifier
                      .padding(
                        start = 12.dp,
                        top = stateValues.textFieldIconPadding,
                        bottom = stateValues.textFieldIconPadding
                      )
                      .size(stateValues.iconSize),
                    url = leadingIconPath,
                    fallbackRes = Res.drawable._9_0,
                    contentDescription = leadingIconContentDescription
                  )
                }
              }

              Box(
                Modifier
                  .weight(1f)
                  .run {
//                    if (wide)
//                      fillMaxHeight().padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
//                    else
                    padding(start = 12.dp)
                  }
              ) {
                Text(
                  text = if (textFieldValue.text.isEmpty()) placeholderText else "",
                  fontSize = placeholderTextSize,
                  color = placeholderTextColor,
                  textAlign = TextAlign.Start,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )

                innerTextField()
              }

              CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
                Row(
                  modifier = Modifier
                    .fillMaxHeight(),
                ) {
                  trailingIcon?.invoke() ?: trailingIconExtraPath?.run {
                    Box(
                      modifier = Modifier
                        .fillMaxHeight()
                        .clickable(
                          interactionSource = remember {
                            MutableInteractionSource()
                          },
                          indication = ripple(color = textColor, radius = cornerRadius),
                          onClick = {
                            trailingIconExtraOnClick?.invoke()
                            isFocused = true
                          }
                        )
                    ) {
                      CpImage(
                        modifier = Modifier
                          .padding(horizontal = 10.dp, vertical = stateValues.textFieldIconPadding)
                          .size(stateValues.iconSize),
                        url = trailingIconExtraPath,
                        fallbackRes = Res.drawable._9_0,
                        contentDescription = trailingIconExtraContentDescription
                      )
                    }
                  }

                  if (textFieldValue.text.isNotEmpty()) {
                    Box(
                      modifier = Modifier
                        .fillMaxHeight()
                        .clickable(
                          interactionSource = remember {
                            MutableInteractionSource()
                          },
                          indication = ripple(color = textColor, radius = cornerRadius)
                        ) {
                          textFieldValue = TextFieldValue("")
                          isFocused = true
                        }
                    ) {
                      CpImage(
                        modifier = Modifier
                          .padding(
                            start = 4.dp,
                            top = stateValues.textFieldIconPadding,
                            bottom = stateValues.textFieldIconPadding,
                            end = 12.dp
                          )
                          .size(stateValues.iconSize),
                        url = stateValues.drawablePathIconCancel,
                        fallbackRes = Res.drawable._9_0,
                        contentDescription = stateValues.stringClear
                      )
                    }
                  }
                }
              }
            }
          }
        }
      )
    }

    if (!isContentValid && contentInvalidText != null && contentInvalidText.isNotEmpty() && contentInvalidText.isNotBlank()) {
      Text(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 2.dp),
        text = contentInvalidText,
        style = TextStyle(
          color = stateValues.ErrorColor,
          fontSize = stateValues.smallTextSize,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )
      )
    }

    LaunchedEffect(isFocusedInitial) {
      if (isFocusedInitial) {
        delay(300)

        isFocused = true
        focusRequester.requestFocus()
      }
    }

    LaunchedEffect(forceRefocus, isFocused) {
      if (forceRefocus && !isFocused) {
        isFocused = true
        focusRequester.requestFocus()
      }
    }
  }

  val content = GenericTextFieldContent(
    value = textFieldValue,
    isFocused = isFocused,
    focusRequester = focusRequester,
    isContentValid = isContentValid,
    onContentValidityCheck = onContentValidityCheck?.run {
      {
        val value = this(textFieldValue.text)
        isContentValid = value
        value
      }
    },
    onReset = {
      textFieldValue = TextFieldValue()
    }
  )

  onContentValidityCheck?.let {
    LaunchedEffect(textFieldValue, isContentValid) {
      if (!isContentValid) {
        isContentValid = onContentValidityCheck(textFieldValue.text)
      }
    }
  }

  return content
}

class GenericTextFieldContent(
  var value: TextFieldValue,
  var isFocused: Boolean,
  var focusRequester: FocusRequester,
  var isContentValid: Boolean,
  val onContentValidityCheck: ((String) -> Boolean)? = null,
  val onReset: (() -> Unit)? = null
) {

  fun checkContentValidity() {
    isContentValid = onContentValidityCheck?.invoke(value.text) ?: true
  }

  fun reset() {
    onReset?.invoke()
  }
}
