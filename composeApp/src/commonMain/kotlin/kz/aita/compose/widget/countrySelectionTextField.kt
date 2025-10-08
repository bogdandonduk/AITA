package kz.aita.compose.widget

import androidx.compose.runtime.Composable
import kz.aita.AppConfiguration
import kz.aita.compose.util.checkAsPhoneNumber
import kz.aita.compose.util.filterAsPhoneNumber
import kz.aita.model.dataModel.CountryDataModel

@Composable
fun AppConfiguration.countrySelectionTextField(
  countries: List<CountryDataModel> = stateValues.globalAppConfiguration.countries,
  titleText: String = stateValues.stringPhoneNumber,
  placeholderText: String = stateValues.stringEnterPhoneNumber,
): DomainSelectionTextFieldContent {

  return domainSelectionTextField(
    domains = countries.map {
      SelectableDomain(
        id = "+${it.phoneNumberCode}",
        name = it.name,
        iconPath = it.flagDrawablePath
      )
    },
    titleText = titleText,
    placeholderText = placeholderText,
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
