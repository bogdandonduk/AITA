package kz.aita

/** Keep all shipped languages available on first launch and against older/offline catalogues. */
fun List<AppLanguageDataModel>.withBundledAppLanguages(): List<AppLanguageDataModel> {
    val builtins = bundledAppLanguages.associateBy { it.language }
    val seen = linkedMapOf<String, AppLanguageDataModel>()
    for (item in this) {
        val code = canonicalLanguageCode(item.language)
        val fallback = builtins[code] ?: continue
        val existing = seen[code]
        val names = (existing?.name ?: item.name).withMissingLocalizedValues(
            (if (existing == null) emptyList() else item.name) + fallback.name)
        seen[code] = (existing ?: item).copy(language = code, name = names,
            flagDrawablePath = (existing?.flagDrawablePath ?: item.flagDrawablePath).ifBlank { fallback.flagDrawablePath })
    }
    for (item in bundledAppLanguages) if (item.language !in seen) seen[item.language] = item
    return seen.values.toList()
}

private val bundledAppLanguages: List<AppLanguageDataModel> = listOf(
    AppLanguageDataModel(
        language = "en",
        name = listOf(
            LocalizedStringDataModel("en", "English"),
            LocalizedStringDataModel("ru", "Английский"),
            LocalizedStringDataModel("kk", "Ағылшынша"),
            LocalizedStringDataModel("tg", "Англисӣ"),
            LocalizedStringDataModel("ky", "Англисче"),
            LocalizedStringDataModel("uz", "Inglizcha"),
        ),
        flagDrawablePath = "png/flag_en.png"
    ),
    AppLanguageDataModel(
        language = "ru",
        name = listOf(
            LocalizedStringDataModel("en", "Russian"),
            LocalizedStringDataModel("ru", "Русский"),
            LocalizedStringDataModel("kk", "Орысша"),
            LocalizedStringDataModel("tg", "Русӣ"),
            LocalizedStringDataModel("ky", "Орусча"),
            LocalizedStringDataModel("uz", "Ruscha"),
        ),
        flagDrawablePath = "png/flag_ru.png"
    ),
    AppLanguageDataModel(
        language = "kk",
        name = listOf(
            LocalizedStringDataModel("en", "Kazakh"),
            LocalizedStringDataModel("ru", "Казахский"),
            LocalizedStringDataModel("kk", "Қазақша"),
            LocalizedStringDataModel("tg", "Қазоқӣ"),
            LocalizedStringDataModel("ky", "Казакча"),
            LocalizedStringDataModel("uz", "Qozoqcha"),
        ),
        flagDrawablePath = "png/flag_kz.png"
    ),
    AppLanguageDataModel(
        language = "tg",
        name = listOf(
            LocalizedStringDataModel("en", "Tajik"),
            LocalizedStringDataModel("ru", "Таджикский"),
            LocalizedStringDataModel("kk", "Тәжікше"),
            LocalizedStringDataModel("tg", "Тоҷикӣ"),
            LocalizedStringDataModel("ky", "Тажикче"),
            LocalizedStringDataModel("uz", "Tojikcha"),
        ),
        flagDrawablePath = "png/flag_tj.png"
    ),
    AppLanguageDataModel(
        language = "ky",
        name = listOf(
            LocalizedStringDataModel("en", "Kyrgyz"),
            LocalizedStringDataModel("ru", "Кыргызский"),
            LocalizedStringDataModel("kk", "Қырғызша"),
            LocalizedStringDataModel("tg", "Қирғизӣ"),
            LocalizedStringDataModel("ky", "Кыргызча"),
            LocalizedStringDataModel("uz", "Qirgʻizcha"),
        ),
        flagDrawablePath = "png/flag_kg.png"
    ),
    AppLanguageDataModel(
        language = "uz",
        name = listOf(
            LocalizedStringDataModel("en", "Uzbek"),
            LocalizedStringDataModel("ru", "Узбекский"),
            LocalizedStringDataModel("kk", "Өзбекше"),
            LocalizedStringDataModel("tg", "Ӯзбекӣ"),
            LocalizedStringDataModel("ky", "Өзбекче"),
            LocalizedStringDataModel("uz", "Oʻzbekcha"),
        ),
        flagDrawablePath = "png/flag_uz.png"
    ),
)
