package kz.aita.compose.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import kz.aita.AppConfiguration
import kz.aita.core.checkAsPhoneNumber
import kz.aita.core.filterAsPhoneNumber
import kz.aita.core.toLocalizedSingleMain
import kz.aita.compose.wrapper.ImeWithAction
import kz.aita.core.StateHost
import kz.aita.model.dataModel.CountryDataModel

@Composable
fun AppConfiguration.countrySelectionPhoneNumberTextField(
  countries: List<CountryDataModel> = stateValues.globalAppConfiguration.countries,
  valueInitial: String? = null,
  stateHost: StateHost,
  stateKey: String,
  titleText: String = stateValues.stringPhoneNumber,
  placeholderText: String = stateValues.stringEnterPhoneNumber,
  imeWithAction: ImeWithAction? = null
): DomainSelectionTextFieldContent {

  return domainSelectionTextField(
    domains = emptyList(),
    secondaryDomains = countries.map {
      SelectableDomain(
        id = "+${it.phoneNumberCode}",
        displayId = "+${it.phoneNumberCode}".toLocalizedSingleMain(),
        name = it.name,
        iconPath = it.flagDrawablePath
      )
    },
    valueInitial = valueInitial?.run {
      countries
        .find { startsWith(it.phoneNumberCode) }
        ?.takeIf { countryMatch ->
          length > countryMatch.phoneNumberSize
        }?.let { countryMatch ->
          substringAfter(countryMatch.phoneNumberCode)
        } ?: this
    },
    titleText = titleText,
    stateHost = stateHost,
    stateKey = stateKey,
    placeholderText = placeholderText,
    keyboardType = KeyboardType.Phone,
    imeWithAction = imeWithAction,
    contentInvalidText = stateValues.stringPhoneNumberMustBe,
    onContentValidityCheck = { text, selectedId ->
      countries.find { "+${it.phoneNumberCode}" == selectedId }?.run {
        text.checkAsPhoneNumber(this)
      } == true
    },
    onFilterValue = { text, selectedId ->
      countries.find { "+${it.phoneNumberCode}" == selectedId }?.run {
        text.filterAsPhoneNumber(this)
      } == true
    }
  )
}
