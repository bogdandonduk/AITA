package kz.aita.compose.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.core.StateHost

@Composable
fun AppConfiguration.domainSelectionTextFieldGroupWidget(
  modifier: Modifier = Modifier,
  titleText: String,
  placeholderText: String,
  stateHost: StateHost,
  stateKey: String,
  valueInitial: List<DomainSelectionTextFieldGroupItemContent>? = null,
  domains: List<SelectableDomain>,
  secondaryDomains: List<SelectableDomain>? = null,
  secondaryDomainsShowId: Boolean = true,
  secondaryDomainsShowName: Boolean = true,
  addDomainActionButtonText: String,
  addSecondaryDomainActionButtonText: String,
  keyboardType: KeyboardType = KeyboardType.Text,
  isFocusedInitial: Boolean = false,
  onFilterValue: ((String, String) -> Boolean)? = null,
  onContentValidityCheck: ((String, String) -> Boolean)? = null,
): DomainSelectionTextFieldGroupWidgetContent {
  var data: List<DomainSelectionTextFieldGroupItemContent> by rememberSaveable {
    mutableStateOf(valueInitial ?: listOf(DomainSelectionTextFieldGroupItemContent(TextFieldValue(""), domains.takeIf { it.isNotEmpty() }?.first()?.id ?: "", secondaryDomains?.takeIf { it.isNotEmpty() }?.first()?.id ?: "")))
  }

  LaunchedEffect(valueInitial) {
    valueInitial?.run {
      data = this
    }
  }

  var availableDomains by rememberSaveable { mutableStateOf(domains) }
  var availableSecondaryDomains by rememberSaveable { mutableStateOf(secondaryDomains) }

  LaunchedEffect(data) {
    println("secondary domains ${secondaryDomains?.map { it.id }}")
    println("data domains ${data.map { it.selectedSecondaryDomainId }}")
    availableDomains = domains.filter { domain -> data.find { it.selectedDomainId == domain.id } == null }
    availableSecondaryDomains = secondaryDomains?.filter { secondaryDomain -> data.find { it.selectedSecondaryDomainId == secondaryDomain.id } == null }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
  ) {
    data.forEachIndexed { index, item ->
      val instance = domainSelectionTextField(
        titleText = if (data.size == 1 && index == 0) titleText else "$titleText ${index + 1}",
        placeholderText = placeholderText,
        valueInitial = item.value.text,
        stateHost = stateHost,
        stateKey = "${stateKey}_$index",
        titleIconButtonPath = if (data.size == 1) null else stateValues.drawablePathIconDelete,
        onTitleIconButtonClick = if (data.size == 1) null else {
          {
            data = data.toMutableList().apply {
              removeAt(index)

              if (index != data.lastIndex) {
                coroutineScope.launch {
                  for (i in (index + 1)..data.lastIndex) {
                    stateHost.state.value["${stateKey}_$i"]?.run {
                      stateHost.setState("${stateKey}_${i - 1}" to this)
                    }
                  }
                }
              }
            }
          }
        },
        domains = domains,
        selectedInitial = item.selectedDomainId,
        secondaryDomains = availableSecondaryDomains,
        selectedSecondaryInitial = item.selectedSecondaryDomainId,
        displayFullDomain = true,
        secondaryDomainsShowId = secondaryDomainsShowId,
        secondaryDomainsShowName = secondaryDomainsShowName,
        keyboardType = keyboardType,
        isFocusedInitial = isFocusedInitial && index == data.lastIndex,
        onFilterValue = onFilterValue,
        onContentValidityCheck = onContentValidityCheck
      )

      Spacer(modifier = Modifier.height(stateValues.marginTextField))

      LaunchedEffect(instance.value.text) {
        data = data.toMutableList().apply {
          set(index, get(index).copy(value = instance.value))
        }
      }
      LaunchedEffect(instance.selectedId) {
        data = data.toMutableList().apply {
          set(index, get(index).copy(selectedDomainId = instance.selectedId))
        }
      }
      LaunchedEffect(instance.selectedSecondaryId) {
        data = data.toMutableList().apply {
          set(index, get(index).copy(selectedSecondaryDomainId = instance.selectedSecondaryId!!))
        }
      }
    }

    if (availableDomains.isNotEmpty())
      actionButton(
        modifier = Modifier
          .fillMaxWidth(),
        text = addDomainActionButtonText,
        iconPath = stateValues.drawablePathIconAdd
      ) {
        data = data.toMutableList().apply {
          add(DomainSelectionTextFieldGroupItemContent(TextFieldValue(), availableDomains.takeIf { it.isNotEmpty() }?.first()?.id ?: "", availableSecondaryDomains?.takeIf { it.isNotEmpty() }?.first()?.id ?: ""))
        }
      }

    if (!addSecondaryDomainActionButtonText.equals(addDomainActionButtonText, true) && availableSecondaryDomains?.isNotEmpty() == true)
      actionButton(
        modifier = Modifier
          .fillMaxWidth(),
        text = addSecondaryDomainActionButtonText,
        iconPath = stateValues.drawablePathIconAdd
      ) {
        data = data.toMutableList().apply {
          add(DomainSelectionTextFieldGroupItemContent(TextFieldValue(), availableDomains.takeIf { it.isNotEmpty() }?.first()?.id ?: "", availableSecondaryDomains?.takeIf { it.isNotEmpty() }?.first()?.id ?: ""))
        }
      }
  }

  return DomainSelectionTextFieldGroupWidgetContent(data)
}

data class DomainSelectionTextFieldGroupWidgetContent(
  val data: List<DomainSelectionTextFieldGroupItemContent>
)

data class DomainSelectionTextFieldGroupItemContent(
  val value: TextFieldValue,
  val selectedDomainId: String,
  val selectedSecondaryDomainId: String
)
