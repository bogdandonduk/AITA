package kz.aita

/** Resource keys are language codes, not countries. Retain only the two historical aliases. */
fun canonicalLanguageCode(language: String?): String {
    val value = language?.trim()?.replace('_', '-')?.substringBefore('-')?.lowercase().orEmpty()
    return when (value) { "kz" -> "kk"; "tj" -> "tg"; else -> value }
}

/** Persist "system", but resolve it on the CLIENT before rendering or asking for an email. */
fun effectiveAppLanguage(preference: String?, systemLanguage: String = getSystemLocaleLanguage()): String {
    val selected = normalizeAppLanguagePreference(preference)
    return if (selected == "system") canonicalLanguageCode(systemLanguage)
        .takeIf { it in SUPPORTED_APP_LANGUAGES } ?: DEFAULT_APP_LANGUAGE else selected
}

/** No device locale is available on the server; unknown email tags keep the English fallback. */
fun normalizeAuthEmailLocale(locale: String?): String = canonicalLanguageCode(locale)
    .takeIf { it in SUPPORTED_APP_LANGUAGES } ?: "en"

/** Resolve a system preference here, never against the server machine's own locale. */
fun authRequestLocale(locale: String?): String = if (canonicalLanguageCode(locale) == "system")
    effectiveAppLanguage("system") else normalizeAuthEmailLocale(locale)

/** Exact, nonblank values only. Do not let an English fallback mask a bundled translation. */
fun List<LocalizedStringDataModel>.exactLocalizedValue(language: String): String? {
    val requested = if (canonicalLanguageCode(language) == "system") effectiveAppLanguage(language) else canonicalLanguageCode(language)
    val fullTag = language.trim().replace('_', '-').lowercase()
    return firstOrNull { it.language.trim().replace('_', '-').equals(fullTag, true) && it.value.isNotBlank() }?.value
        ?: firstOrNull { canonicalLanguageCode(it.language) == requested && it.value.isNotBlank() }?.value
}

/** Only resource/metadata lists use this; authored names and notes are never translated by text. */
fun List<LocalizedStringDataModel>.withMissingLocalizedValues(
    bundled: List<LocalizedStringDataModel>
): List<LocalizedStringDataModel> {
    val result = linkedMapOf<String, LocalizedStringDataModel>()
    (this + bundled).forEach { value ->
        val key = canonicalLanguageCode(value.language)
        if (key.isNotBlank() && (key !in result || result[key]?.value.isNullOrBlank())) result[key] = value
    }
    return result.values.toList()
}

/** All exact sources outrank ANY fallback language, independently of download/cache age. */
fun resolveLocalizedResource(
    id: Long,
    language: String,
    primary: List<LocalizedStringDataModel>?,
    bundled: List<LocalizedStringDataModel>?
): String? {
    val selected = if (canonicalLanguageCode(language) == "system") effectiveAppLanguage(language) else canonicalLanguageCode(language)
    val value = primary?.exactLocalizedValue(selected) ?: bundled?.exactLocalizedValue(selected)
        ?: bundledTranslatedStringResource(id, selected)
        ?: primary?.extractLocalizedString(selected) ?: bundled?.extractLocalizedString(selected)
    return value?.let { normalizeAppResourceCopy(id, it) }
}

/** Preserve first-row precedence while completing every language before legacy projection. */
fun mergeLocalizedStringGroups(
    primary: List<LocalizedStringGroupDataModel>,
    bundled: List<LocalizedStringGroupDataModel>
): List<LocalizedStringGroupDataModel> {
    val firstBundled = bundled.distinctBy { it.id }.associateBy { it.id }
    val result = linkedMapOf<Long, LocalizedStringGroupDataModel>()
    primary.forEach { group -> if (group.id !in result) result[group.id] = group.copy(
        values = group.values.withMissingLocalizedValues(firstBundled[group.id]?.values.orEmpty())) }
    firstBundled.forEach { (id, group) -> if (id !in result) result[id] = group }
    return result.values.toList()
}

/** App-owned resource copy only: never rewrite business names, messages or other user-authored text. */
internal fun normalizeAppResourceCopy(id: Long, value: String): String {
    // Correct stale app-owned scale labels from older bundled or downloaded catalogues.
    if (id == 912L) {
        if (value == "Крупный") return "Большой"
        if (value == "Калон") return "Бузург"
    }
    val text = value
        .replace("an AITA server", "a server")
        .replace("AITA serveriga", "serverga")
        .replace("AITA serveri", "server")
        .replace("AITA server", "server")
        .replace("Сервер AITA", "Сервер")
        .replace("сервера AITA", "сервера")
        .replace("сервер AITA", "сервер")
        .replace("сервери AITA-ро", "серверро")
        .replace("сервери AITA", "сервер")
        .replace("AITA серверінің", "сервердің")
        .replace("AITA серверинин", "сервердин")
        .replace("AITA серверіне", "серверге")
        .replace("AITA серверине", "серверге")
        .replace("AITA серверін", "серверді")
        .replace("AITA серверин", "серверди")
        .replace("AITA сервері", "сервер")
        .replace("AITA сервери", "сервер")
        .replace("AITA сервер", "сервер")
        .let { if (it.startsWith("сервер")) "С" + it.drop(1) else if (it.startsWith("serverga")) "S" + it.drop(1) else it }
    return if (id == 1138L) text.trimEnd().removeSuffix(".") else text
}
