package kz.aita

/** A typed address is useful without inventing a geocoded point. Legacy numeric coordinates
 * remain zero and are never considered resolved by map, distance or provider refresh code. */
fun manualStoreAddress(text: String, language: String, countryCode: String = ""): LocationDataModel? {
    val address = text.trim().replace(Regex("\\s+"), " ")
    if (address.isEmpty() || address.length > 500 || address.any { it.code < 32 }) return null
    return LocationDataModel(name = address, fallbackAddress = address, provider = "manual",
        primaryLanguage = language, countryCode = countryCode.trim().uppercase(),
        localizedAddresses = listOf(LocalizedStringDataModel(language, address)))
}

fun LocationDataModel.isManualStoreAddress(): Boolean = provider in setOf("", "manual") &&
    providerObjectId.isBlank() && manualStoreAddress(displayAddress(primaryLanguage), primaryLanguage) != null
