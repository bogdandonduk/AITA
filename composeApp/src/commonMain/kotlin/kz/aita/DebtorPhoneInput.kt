package kz.aita

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

internal fun debtorPhoneValue(callingCode: String?, nationalNumber: String): String {
    val national = nationalNumber.filter(Char::isDigit)
    if (national.isEmpty()) return "" // Choosing a country alone is not a debtor contact.
    return callingCode.orEmpty().filter(Char::isDigit) + national
}

internal fun debtorPhoneNationalNumber(value: String, callingCode: String?): String {
    val digits = value.filter(Char::isDigit)
    val code = callingCode.orEmpty().filter(Char::isDigit)
    return if (code.isNotEmpty() && digits.startsWith(code)) digits.removePrefix(code) else digits
}

/** The payment draft owns the full number; the standard phone control owns only its visual editor. */
@Composable
internal fun AppConfiguration.DebtorPhoneInput(
    value: String,
    identityKey: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValueChange: (String) -> Unit
) {
    val countries = stateValues.globalAppConfiguration.countries.withTajikistanFallback()
    val detected = countries.sortedByDescending { it.phoneNumberCode.length }
        .firstOrNull { value.isNotBlank() && value.filter(Char::isDigit).startsWith(it.phoneNumberCode) }
    val initialCode = "+${(detected ?: countries.firstOrNull { it.locale.equals("kz", true) }
        ?: countries.firstOrNull())?.phoneNumberCode ?: "7"}"
    var selectedCode by rememberSaveable(identityKey) { mutableStateOf(initialCode) }
    // Restore an externally loaded transaction draft, but retain the chosen country for an empty editor.
    LaunchedEffect(value, identityKey) {
        if (value.isNotBlank() && detected != null) selectedCode = "+${detected.phoneNumberCode}"
    }
    val national = debtorPhoneNationalNumber(value, selectedCode)
    countrySelectionPhoneNumberTextField(
        modifier = modifier,
        countries = countries,
        valueInitial = national,
        valueIsNationalNumber = true,
        selectedCountryCodeInitial = selectedCode,
        identityKey = identityKey,
        autoFocus = false,
        retainTextAcrossRecreation = false,
        persistTextDraft = false,
        retainSelectionAcrossRecreation = false,
        persistSelectionDraft = false,
        onSelectedCountryCodeChange = { next ->
            if (next != null && next != selectedCode) {
                val nextValue = debtorPhoneValue(next, debtorPhoneNationalNumber(value, selectedCode))
                selectedCode = next
                if (nextValue != value) onValueChange(nextValue)
            }
        },
        onValueChange = { text, _, code, apply ->
            val nextValue = debtorPhoneValue(code ?: selectedCode, text)
            apply()
            if (nextValue != value) onValueChange(nextValue)
        }
    )
}
