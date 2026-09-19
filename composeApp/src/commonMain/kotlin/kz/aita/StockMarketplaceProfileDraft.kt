package kz.aita

/** Editing preserves whitespace and an intentionally cleared language until the save boundary. */
internal fun List<LocalizedStringDataModel>.marketplaceProfileDraftText(language: String): String =
    firstOrNull { it.language.equals(language, ignoreCase = true) }?.value
        ?: firstOrNull { it.language == "main" }?.value
        ?: firstOrNull()?.value.orEmpty()

internal fun List<LocalizedStringDataModel>.withMarketplaceProfileDraftText(
    language: String, value: String
): List<LocalizedStringDataModel> {
    val selectedLanguage = language.takeIf { it.isNotBlank() } ?: DEFAULT_APP_LANGUAGE
    val entry = LocalizedStringDataModel(selectedLanguage, value)
    return if (any { it.language.equals(selectedLanguage, ignoreCase = true) }) {
        map { if (it.language.equals(selectedLanguage, ignoreCase = true)) entry else it }
    } else this + entry
}
