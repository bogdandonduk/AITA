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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration

@Composable
fun AppConfiguration.dropdownListWidget(
  modifier: Modifier = Modifier,
  titleText: String,
  textColor: Color = stateValues.TextColor,

  titleTextSize: TextUnit = stateValues.accentTextSize,
  titleTextColor: Color = textColor,
  showId: Boolean = false,
  showName: Boolean = true,
  domains: List<SelectableDomain>,
  selectedInitial: String = domains.first().id,
  cornerRadius: Dp = stateValues.cornerRadius
): DropdownListWidgetContent {

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

    selectableDomainWidget(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(cornerRadius))
        .height((domains.size * stateValues.textFieldHeight.value).dp)
        .border(
          width = if (isDomainSelectionDropdownExpandedState.targetState) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
          color = if (isDomainSelectionDropdownExpandedState.targetState) stateValues.AccentColor else textColor,
          shape = RoundedCornerShape(cornerRadius)
        ),
      textColor = textColor,
      domain = SelectableDomain(
        id = selected.id,
        name = selected.name,
        iconPath = selected.iconPath
      ),
      showId = showId,
      showName = showName
    ) {
      isDomainSelectionDropdownExpandedState.targetState =
        !isDomainSelectionDropdownExpandedState.targetState
    }

    if (domains.isNotEmpty()) {
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
              textColor = textColor,
              domain = country,
              showId = showId,
              showName = showName
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

  return DropdownListWidgetContent(selectedId = selectedId)
}

class DropdownListWidgetContent(var selectedId: String)
