package kz.aita.compose.util

import androidx.compose.ui.graphics.Color
import kz.aita.model.dataModel.CountryDataModel

fun String.toColor(): Color {
  return Color(toULong(radix = 16).toInt())
}

fun String.checkAsEmail(): Boolean {
  return isNotEmpty() && isNotBlank() && !contains(" ") &&
      contains("@") && contains(".") &&
      Regex("^[a-zA-Z0-9]").matches(first().toString()) &&
      filter { it == '@' }.length == 1 && lastIndexOf(".") > lastIndexOf("@") &&
      lastIndexOf(".") != lastIndex
}

fun String.checkAsPhoneNumber(country: CountryDataModel): Boolean {
  return length == country.phoneNumberSize
}

fun String.filterAsPhoneNumber(country: CountryDataModel): Boolean {
  return isNumericalString() && length <= country.phoneNumberSize
}

fun String.checkAsPassword(): Boolean {
  return length >= 8 && isNotBlank() && any { it.isDigit() }
}

fun String.isNumericalString(): Boolean {
  return all { it.isDigit() }
}

fun String.isNumericalDoubleString(): Boolean {
  var dots = 0

  forEach {
    if (it == '.')
      dots ++
  }

  return if (dots > 1)
    false
  else all { it.isDigit() || it == '.' }
}

fun String.checkAsPersonName(): Boolean {
  return isNotEmpty() && isNotBlank() && matches(Regex("""^[\p{L}\p{M} .-]+$"""))
}

fun String.filterAsPersonName(): Boolean {
  return isEmpty() || matches(Regex("""^[\p{L}\p{M} .-]+$"""))
}


