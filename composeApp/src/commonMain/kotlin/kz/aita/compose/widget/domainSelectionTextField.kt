package kz.aita.compose.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration
import kz.aita.compose.wrapper.ImeWithAction

@Composable
fun AppConfiguration.domainSelectionTextField(
  modifier: Modifier = Modifier,
  titleText: String,
  placeholderText: String,
  domains: List<SelectableDomain>,
  selectedInitial: String = domains.first().id,
  selectionEnabled: Boolean = true,
  imeWithAction: ImeWithAction? = null,
  cornerRadius: Dp = stateValues.cornerRadius,
  onContentValidityCheck: ((String, String) -> Boolean)? = null,
  onFilterValue: ((String, String) -> Boolean)? = null,
  onValueChange: ((String, () -> Unit) -> Unit)? = null
): DomainSelectionTextFieldContent {

  var selectedId by rememberSaveable {
    mutableStateOf(selectedInitial)
  }

  var selected by remember {
    mutableStateOf(domains.find { it.id.equals(selectedId, true) } ?: domains.first())
  }

  LaunchedEffect(selectedId) {
    selected = domains.find { it.id.equals(selectedId, true) } ?: domains.first()
  }

  LaunchedEffect(selectedInitial) {
    selectedId = selectedInitial
  }

  val isDomainSelectionDropdownExpandedState = remember {
    MutableTransitionState(false)
      .apply {
        targetState = false
      }
  }

  var genericTextFieldContent: GenericTextFieldContent? = null

  Column {
    genericTextFieldContent = genericTextField(
      modifier = modifier,
      titleText = titleText,
      placeholderText = placeholderText,
      leadingIcon = {
        selectableDomainWidget(
          domain = selected,
          onClick = selectionEnabled.takeIf { it }?.run {
            {
              isDomainSelectionDropdownExpandedState.targetState =
                !isDomainSelectionDropdownExpandedState.targetState
            }
          }
        )
      },
      keyboardType = KeyboardType.Phone,
      imeWithAction = imeWithAction ?: ImeWithAction.Default,
      contentInvalidText = stateValues.stringPhoneNumberMustBe,
      onContentValidityCheck = onContentValidityCheck?.run {
        {
          invoke(it, selectedId)
        }
      },
      onFilterValue = onFilterValue?.run {
        {

          invoke(it, selectedId).apply {
            println("running right!!! $this")
          }
        }
      },
      onValueChange = onValueChange
    )

    if (selectionEnabled) {
      Spacer(modifier = Modifier.height(1.dp))

      AnimatedVisibility(
        modifier = modifier,
        visibleState = isDomainSelectionDropdownExpandedState,
        enter = expandVertically(),
        exit = shrinkVertically()
      ) {
        LazyColumn(
          modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .fillMaxWidth()
            .height((domains.size * stateValues.textFieldHeight.value).dp)
            .border(
              width = stateValues.focusedBorderWidth,
              color = stateValues.AccentColor,
              shape = RoundedCornerShape(cornerRadius)
            )
        ) {
          itemsIndexed(domains) { index, country ->
            selectableDomainWidget(
              modifier = Modifier
                .fillParentMaxWidth(),
              domain = country,
              showName = true
            ) {
              selectedId = domains[index].id

              isDomainSelectionDropdownExpandedState.targetState =
                !isDomainSelectionDropdownExpandedState.targetState
            }
          }
        }
      }
    }

  }

  return DomainSelectionTextFieldContent(
    value = genericTextFieldContent!!.value,
    isFocused = genericTextFieldContent.isFocused,
    selectedId = selectedId,
    isContentValid = genericTextFieldContent.isContentValid,
    onContentValidityCheck = genericTextFieldContent.onContentValidityCheck
  )
}

class DomainSelectionTextFieldContent(
  var value: TextFieldValue,
  var selectedId: String,
  var isFocused: Boolean,
  var isContentValid: Boolean,
  val onContentValidityCheck: ((String) -> Boolean)? = null
) {

  fun checkContentValidity() {
    isContentValid = onContentValidityCheck?.invoke(value.text) ?: true
  }
}
