package kz.aita

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
      dots++
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

infix fun String.localized(locale: String): LocalizedStringDataModel {
  return LocalizedStringDataModel(locale, this)
}

fun getStoreWorkers() {
  TODO("Not yet implemented")
}

fun addStoreWorker(
  phoneNumber: String,
  email: String,
  firstName: String,
  lastName: String,
  password: String
) {
  TODO("Not yet implemented")
}



fun getGoodsCategories() {
  GlobalScope.launch(Dispatchers.io) {
    categoriesState
      .emit(
        DataState.Success(
          listOf(

          )
        )
      )
  }
}

fun List<LocalizedStringGroupDataModel>?.extractString(id: Long, language: String): String? {
  return this
    ?.run {
      find { it.id == id }
        ?.values
        ?.find {
          (language == "system" && it.language == getSystemLocaleLanguage()) || it.language == language
        }?.value
    }
}

fun List<StylizedDimensionGroupDataModel>.extractValue(id: Long, sizeModeId: Long): Float? {
  return find { it.id == id }?.values?.find { it.sizeModeId == -1L || it.sizeModeId == sizeModeId }?.value
}

fun List<StylizedColorGroupDataModel>.extractColor(id: Long, themeId: Long): String? {
  return find { it.id == id }?.values?.find { it.themeId == -1L || it.themeId == themeId }?.valueHex
}

fun List<StylizedDrawablePathsGroupDataModel>.extractPath(id: Long, themeId: Long): String? {
  return find { it.id == id }?.values?.find { it.themeId == -1L || it.themeId == themeId }?.path
}

fun getFullDrawableRemoteResourceUrl(path: String): String {
  return globalAppConfigurationState.payloadValue.run {
    "${serverUrl.first}/${drawableResourcesPath.first}"
  } + "/$path"
}

fun getFullDrawableLocalResourceUrl(path: String): String {
  return "files/$path"
}

fun List<RemoteResponseDataModel>.extractExceptionMessage(id: String): List<LocalizedStringDataModel>? {
  return find { it.id == id }?.message
}

fun List<LocalizedStringDataModel>.extractLocalizedString(language: String): String? {
  return find { language == "system" && it.language == getSystemLocaleLanguage() || it.language == language || it.language == "main" }?.value
}

fun String.toLocalizedSingleMain(): List<LocalizedStringDataModel> {
  return listOf(LocalizedStringDataModel("main", this))
}

fun List<CountryDataModel>.getCurrency(code: String): CurrencyDataModel? {
  val currencies = mutableListOf<CurrencyDataModel>().apply {
    this@getCurrency.forEach {
      addAll(it.currencies)
    }
  }

  return currencies.find { it.code.equals(code, true) }
}

fun List<CountryDataModel>.getCurrenciesByCountry(locale: String): List<CurrencyDataModel>? {
  return this.find { it.locale == locale }?.currencies
}

fun List<CountryDataModel>.getFirstCurrencyByCountry(locale: String): CurrencyDataModel? {
  return getCurrenciesByCountry(locale)?.takeIf { it.isNotEmpty() }?.first()
}