package kz.aita.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import kz.aita.StateHost

@Composable
fun AppConfiguration.dropdownListWidget(
  modifier: Modifier = Modifier,
  titleText: String,
  textColor: Color = stateValues.TextColor,
  titleTextSize: TextUnit = stateValues.accentTextSize,
  titleTextColor: Color = textColor,
  showId: Boolean = true,
  showName: Boolean = false,
  domains: List<SelectableDomain>,
  selectedInitial: String? = null,
  cornerRadius: Dp = stateValues.cornerRadius,
  search: Triple<String?, StateHost?, String?>? = null
): DropdownListWidgetContent {

  var selectedId by rememberSaveable {
    mutableStateOf(selectedInitial ?: domains.first().id)
  }

  var selected by remember {
    mutableStateOf(domains.find { it.id.equals(selectedId, true) } ?: domains.first())
  }

  LaunchedEffect(selectedId) {
    selected = domains.find { it.id.equals(selectedId, true) } ?: domains.first()
  }

  LaunchedEffect(selectedInitial) {
    selectedInitial?.let {
      selectedId = it
    }
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
        .height(stateValues.textFieldHeight)
        .border(
          width = if (isDomainSelectionDropdownExpandedState.targetState) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
          color = if (isDomainSelectionDropdownExpandedState.targetState) stateValues.AccentColor else textColor,
          shape = RoundedCornerShape(cornerRadius)
        ),
      textColor = textColor,
      domain = selected,
      showId = showId,
      showName = showName,
      showExpansion = true
    ) {
      isDomainSelectionDropdownExpandedState.targetState =
        !isDomainSelectionDropdownExpandedState.targetState
    }

    if (domains.isNotEmpty()) {
      Spacer(modifier = Modifier.height(1.dp))

      AnimatedVisibility(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 0.dp, max = stateValues.screenHeight / 3),
        visibleState = isDomainSelectionDropdownExpandedState,
        enter = expandVertically(),
        exit = shrinkVertically()
      ) {
        Column(
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
          val searchTextFieldContent = if (search != null) {
            Spacer(modifier = Modifier.height(1.dp))

            searchTextField(
              modifier = Modifier
                .fillMaxWidth(),
              stateHost = search.second,
              stateKey = search.third,
              focusedBorderWidth = 0.dp,
              unfocusedBorderWidth = 0.dp,
              focusedBorderColor = Color.Transparent,
              unfocusedBorderColor = Color.Transparent
            )
          } else null

          Spacer(modifier = Modifier.height(1.dp))

          LazyColumn(

          ) {
            itemsIndexed(
              domains.run {
                val query = searchTextFieldContent?.value?.text

                if (query?.isNotEmpty() == true)
                  filter { it.searchContains(query) }
                else this
              }
            ) { index, domain ->
              selectableDomainWidget(
                modifier = Modifier
                  .fillParentMaxWidth(),
                textColor = textColor,
                domain = domain,
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

  }

  return DropdownListWidgetContent(selectedId = selectedId)
}

class DropdownListWidgetContent(var selectedId: String)
