package kz.aita.compose.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kz.aita.AppUIConfiguration
import kz.aita.compose.util.checkAsPhoneNumber
import kz.aita.compose.util.isNumericalString
import kz.aita.compose.wrapper.ImeWithAction
import kz.aita.model.dataModel.CountryDataModel

@Composable
fun AppUIConfiguration.phoneNumberWithCountrySelectionTextField(
  modifier: Modifier = Modifier,
  countries: List<CountryDataModel>,
  countrySelectionEnabled: Boolean = true,
  imeWithAction: ImeWithAction? = null,
  cornerRadius: Dp = stateValues.cornerRadius
): PhoneNumberWithCountrySelectionTextFieldContent {
  var selectedCountryLocale by rememberSaveable {
    mutableStateOf(countries.first().locale)
  }

  var selectedCountry by remember {
    mutableStateOf(countries.find { it.locale.equals(selectedCountryLocale, true) } ?: countries.first())
  }

  LaunchedEffect(selectedCountryLocale) {
    selectedCountry = countries.find { it.locale.equals(selectedCountryLocale, true) } ?: countries.first()
  }

  val isCountrySelectionDropdownExpandedState = remember {
    MutableTransitionState(false)
      .apply {
        targetState = false
      }
  }

  var genericTextFieldContent: GenericTextFieldContent? = null

  Column {
    genericTextFieldContent = genericTextField(
      modifier = modifier,
      titleText = stateValues.stringPhoneNumber,
      placeholderText = stateValues.stringEnterPhoneNumber,
      leadingIcon = {
        countryPhoneCodeWithFlagWidget(
          country = selectedCountry,
          onClick = countrySelectionEnabled.takeIf { it }?.run {
            {
              isCountrySelectionDropdownExpandedState.targetState =
                !isCountrySelectionDropdownExpandedState.targetState
            }
          }
        )
      },
      keyboardType = KeyboardType.Email,
      imeWithAction = imeWithAction ?: ImeWithAction.Default,
      contentInvalidText = stateValues.stringPhoneNumberMustBe,
      onContentValidityCheck = { it: String ->
        it.checkAsPhoneNumber(selectedCountry)
      },
      onFilterValue = {
        it.isNumericalString() && it.length <= selectedCountry.phoneNumberSize
      }
    )

    if (countrySelectionEnabled) {
      Spacer(modifier = Modifier.height(1.dp))
      AnimatedVisibility(
        modifier = modifier,
        visibleState = isCountrySelectionDropdownExpandedState,
        enter = expandVertically(),
        exit = shrinkVertically()
      ) {
        LazyColumn(
          modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .fillMaxWidth()
            .height((countries.size * stateValues.textFieldHeight.value).dp)
            .border(
              width = stateValues.focusedBorderWidth,
              color = stateValues.AccentColor,
              shape = RoundedCornerShape(cornerRadius)
            )
        ) {
          itemsIndexed(countries) { index, country ->
            countryPhoneCodeWithFlagWidget(
              modifier = Modifier
                .fillParentMaxWidth(),
              country = country
            ) {
              selectedCountryLocale = countries[index].locale

              isCountrySelectionDropdownExpandedState.targetState =
                !isCountrySelectionDropdownExpandedState.targetState
            }
          }
        }
      }
    }

  }

  return PhoneNumberWithCountrySelectionTextFieldContent(
    value = genericTextFieldContent!!.value,
    isFocused = genericTextFieldContent.isFocused,
    selectedCountryLocale = selectedCountryLocale,
    isContentValid = genericTextFieldContent.isContentValid,
    onContentValidityCheck = genericTextFieldContent.onContentValidityCheck
  )
}

class PhoneNumberWithCountrySelectionTextFieldContent(
  var value: TextFieldValue,
  var selectedCountryLocale: String,
  var isFocused: Boolean,
  var isContentValid: Boolean,
  val onContentValidityCheck: ((String) -> Boolean)? = null
) {

  fun checkContentValidity() {
    isContentValid = onContentValidityCheck?.invoke(value.text) ?: true
  }
}
