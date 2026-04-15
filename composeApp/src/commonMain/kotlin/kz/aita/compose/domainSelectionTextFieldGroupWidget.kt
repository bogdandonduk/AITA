package kz.aita.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.launch
import kz.aita.StateHost

@Composable
fun AppConfiguration.domainSelectionTextFieldGroupWidget(
  modifier: Modifier = Modifier,
  titleText: String,
  placeholderText: String,
  stateHost: StateHost? = null,
  stateKey: String? = null,
  valueInitial: List<DomainSelectionTextFieldGroupItemContent>? = null,
  domains: List<SelectableDomain>,
  secondaryDomains: List<SelectableDomain>? = null,
  secondaryDomainsShowId: Boolean = true,
  secondaryDomainsShowName: Boolean = true,
  addDomainActionButtonText: String,
  addSecondaryDomainActionButtonText: String? = null,
  keyboardType: KeyboardType = KeyboardType.Text,
  isFocusedInitial: Boolean = false,
  onFilterValue: ((String, String, String?) -> Boolean)? = null,
  onContentValidityCheck: ((String, String, String?) -> Boolean)? = null,
): DomainSelectionTextFieldGroupWidgetContent {
  var isContentValid by rememberSaveable {
    mutableStateOf(true)
  }

  var data: List<DomainSelectionTextFieldGroupItemContent> by rememberSaveable {
    mutableStateOf(
      valueInitial?.takeIf { it.isNotEmpty() } ?: listOf(
        TextFieldValue("").run {
          DomainSelectionTextFieldGroupItemContent(
            this,
            domains.takeIf {
              it.isNotEmpty()
            }?.first()?.id ?: "", secondaryDomains?.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
            isContentValid = isContentValid,
            onContentValidityCheck = onContentValidityCheck?.let {
              {
                val value = it(this@run.text, domains.takeIf { d ->
                  d.isNotEmpty()
                }?.first()?.id ?: "", secondaryDomains?.takeIf { sd ->
                  sd.isNotEmpty()
                }?.first()?.id ?: "")
                isContentValid = value
                value
              }
            }
          )
        }

      )
    )
  }

  LaunchedEffect(valueInitial) {
    valueInitial?.run {
      data = this
    }
  }

  var availableDomains by rememberSaveable { mutableStateOf(domains) }
  var availableSecondaryDomains by rememberSaveable { mutableStateOf(secondaryDomains) }

  LaunchedEffect(data) {
    availableDomains = domains.filter { domain -> data.find { it.selectedDomainId == domain.id } == null }
    availableSecondaryDomains =
      secondaryDomains?.filter { secondaryDomain -> data.find { it.selectedSecondaryDomainId == secondaryDomain.id } == null }
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
        titleIconButtonPath = if (data.size == 1) null else stateValues.drawablePathIconDelete,
        onTitleIconButtonClick = if (data.size == 1) null else {
          {
            data = data.toMutableList().apply {
              removeAt(index)

              if (index != data.lastIndex) {
                coroutineScope.launch {
                  for (i in (index + 1)..data.lastIndex) {
                    stateHost?.state?.value["${stateKey}_$i"]?.run {
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
        if (data[index].value.text != instance.value.text) {
          println("fucking in ${data[index].value.text} != ${instance.value.text}")

          data = data.toMutableList().apply {
            set(index, get(index).copy(value = instance.value))
          }
        }
      }
      LaunchedEffect(instance.selectedId) {
        try {
          data = data.toMutableList().apply {
            set(index, get(index).copy(selectedDomainId = instance.selectedId))
          }
        } catch (thr: Throwable) {

        }
      }
      LaunchedEffect(instance.selectedSecondaryId) {
        instance.selectedSecondaryId?.let {
          try {
            data = data.toMutableList().apply {
              set(index, get(index).copy(selectedSecondaryDomainId = it))
            }
          } catch (thr: Throwable) {

          }
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
          add(
            DomainSelectionTextFieldGroupItemContent(
              TextFieldValue(),
              availableDomains.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
              availableSecondaryDomains?.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
              isContentValid
            )
          )
        }
      }

    if (addSecondaryDomainActionButtonText != null && !addSecondaryDomainActionButtonText.equals(
        addDomainActionButtonText,
        true
      ) && availableSecondaryDomains?.isNotEmpty() == true
    )
      actionButton(
        modifier = Modifier
          .fillMaxWidth(),
        text = addSecondaryDomainActionButtonText,
        iconPath = stateValues.drawablePathIconAdd
      ) {
        data = data.toMutableList().apply {
          add(
            DomainSelectionTextFieldGroupItemContent(
              TextFieldValue(),
              availableDomains.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
              availableSecondaryDomains?.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
              isContentValid
            )
          )
        }
      }
  }

  return DomainSelectionTextFieldGroupWidgetContent(data)
}

data class DomainSelectionTextFieldGroupWidgetContent(
  val data: List<DomainSelectionTextFieldGroupItemContent>
)

data class DomainSelectionTextFieldGroupItemContent(
  var value: TextFieldValue,
  var selectedDomainId: String,
  var selectedSecondaryDomainId: String,
  var isContentValid: Boolean,
  val onContentValidityCheck: ((String) -> Boolean)? = null
) {

  fun checkContentValidity() {
    isContentValid = onContentValidityCheck?.invoke(value.text) ?: true
  }
}
