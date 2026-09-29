package kz.aita

private fun countryWords(en: String, ru: String, kk: String, ky: String, tg: String, uz: String) =
    listOf("main" to en, "en" to en, "ru" to ru, "kk" to kk, "ky" to ky, "tg" to tg, "uz" to uz)
        .map { LocalizedStringDataModel(it.first, it.second) }
internal fun russiaCountry() = CountryDataModel(
    locale = "ru", language = "ru", name = countryWords("Russia", "Россия", "Ресей", "Орусия", "Русия", "Rossiya"),
    flagDrawablePath = "png/flag_ru.png", cities = emptyList(), phoneNumberCode = "7", phoneNumberSize = 10,
    currencies = listOf(CurrencyDataModel("RUB", "₽", countryWords("Russian ruble", "Российский рубль", "Ресей рублі", "Орус рубли", "Рубли русӣ", "Rossiya rubli"))),
    cashlessPaymentOptions = listOf(PaymentOptionDataModel("0", countryWords("Card", "Карта", "Карта", "Карта", "Корт", "Karta")),
        PaymentOptionDataModel("1", countryWords("QR / bank transfer", "QR / СБП", "QR / аударым", "QR / которуу", "QR / интиқол", "QR / o‘tkazma"))),
    preferredCashlessPaymentOptionId = "0")
internal fun russiaLegalIdFormats() = listOf(10, 12).map { length ->
    val title = countryWords("INN", "ИНН", "Салық нөмірі", "Салык номери", "РМА", "STIR")
    LegalIdFormatDataModel(id = "ru_inn_$length", countryLocales = listOf("ru"), name = title, label = title,
        placeholder = countryWords("$length digits", "$length цифр", "$length сан", "$length цифра", "$length рақам", "$length ta raqam"),
        required = true, length = length, digitsOnly = true, regex = "^[0-9]{$length}$")
}
internal fun russiaCompanyForms() = listOf(
    CompanyFormDataModel(id = "ru_ooo", name = countryWords("Limited liability company", "ООО", "ЖШҚ", "ЖЧК", "ҶДММ", "MChJ"),
        parameters = emptyList(), countryLocales = listOf("ru"), legalIdFormatId = "ru_inn_10"),
    CompanyFormDataModel(id = "ru_ip", name = countryWords("Individual entrepreneur", "ИП", "ЖК", "Жеке ишкер", "Соҳибкори инфиродӣ", "YTT"),
        parameters = emptyList(), countryLocales = listOf("ru"), legalIdFormatId = "ru_inn_12"),
    CompanyFormDataModel(id = "ru_ao", name = countryWords("Joint-stock company", "АО", "АҚ", "АК", "ҶС", "AJ"),
        parameters = emptyList(), countryLocales = listOf("ru"), legalIdFormatId = "ru_inn_10"))
