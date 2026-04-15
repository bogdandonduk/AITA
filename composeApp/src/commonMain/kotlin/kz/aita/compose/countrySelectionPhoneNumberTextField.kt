package kz.aita.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import kz.aita.CountryDataModel
import kz.aita.StateHost
import kz.aita.checkAsPhoneNumber
import kz.aita.filterAsPhoneNumber
import kz.aita.toLocalizedSingleMain

@Composable
fun AppConfiguration.countrySelectionPhoneNumberTextField(
  countries: List<CountryDataModel> = stateValues.globalAppConfiguration.countries,
  valueInitial: String? = null,
  stateHost: StateHost,
  stateKey: String,
  lockedId: String? = null,
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
        iconPath = it.flagDrawablePath,
        iconRes = it.mapIconRes()
      )
    },
    lockedSecondaryDomainId = lockedId,
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
    onContentValidityCheck = { text, _, selectedSecondaryId ->
      countries.find { "+${it.phoneNumberCode}" == selectedSecondaryId }?.run {
        text.checkAsPhoneNumber(this)
      } == true
    },
    onFilterValue = { text, _, selectedSecondaryId ->
      countries.find { "+${it.phoneNumberCode}" == selectedSecondaryId }?.run {
        text.filterAsPhoneNumber(this)
      } == true
    }
  )
}
