package kz.aita.help

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kz.aita.*

const val HELP_CATALOGUE_MAX_BYTES = 2_097_152
val helpJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
@Serializable enum class HelpMode(val id: Int, val slug: String) {
    STORE(APP_MODE_STORE,"store"), BUYER(APP_MODE_BUYER,"buyer"),
    SUPPLIER(APP_MODE_SUPPLIER,"supplier"), MANUFACTURER(APP_MODE_MANUFACTURER,"manufacturer");
    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id==id } ?: STORE
        fun fromSlug(slug: String?) = entries.firstOrNull { it.slug==slug }
    }
}
@Serializable enum class HelpCategory { START, ACCOUNT, STOCK, SALES, MONEY, TEAM, MARKETPLACE, PARTNERS, ORDERS, DEVICES, SETTINGS }
typealias HelpText = Map<String,String>
fun HelpText.languageFor(language: String): String = canonicalLanguageCode(effectiveAppLanguage(language)).let { code ->
    code.takeIf { !this[it].isNullOrBlank() } ?: "en".takeIf { !this[it].isNullOrBlank() } ?: "ru"
}
fun HelpText.localized(language: String): String = this[languageFor(language)].orEmpty()

/** Assets are curated screenshots, not arbitrary remote URLs, HTML, executable content or user attachments. */
@Serializable data class HelpScreenshot(
    val asset: String,
    val alt: HelpText,
    val caption: HelpText = emptyMap(),
    val width: Int,
    val height: Int,
    val language: String? = null
)
@Serializable data class HelpStep(val id: String, val text: HelpText, val screenshots: List<HelpScreenshot> = emptyList())
@Serializable data class HelpTutorial(
    val id: String,
    val modes: Set<HelpMode>,
    val category: HelpCategory,
    val title: HelpText,
    val introduction: HelpText,
    val steps: List<HelpStep>,
    val caution: HelpText = emptyMap()
)
@Serializable data class HelpFaq(val id: String, val modes: Set<HelpMode>, val category: HelpCategory,
    val question: HelpText, val answer: HelpText, val tutorialId: String? = null)
@Serializable data class HelpCatalogue(val schema: Int = 1, val revision: Long = 1,
    val tutorials: List<HelpTutorial> = emptyList(), val faqs: List<HelpFaq> = emptyList()) {
    fun forMode(mode: HelpMode) = copy(tutorials=tutorials.filter { mode in it.modes }, faqs=faqs.filter { mode in it.modes })
}
/** Render a whole article in one complete language, not a partly translated sequence of steps. */
private fun completeHelpLanguage(parts: List<HelpText>, requested: String): String {
    val candidates = listOf(canonicalLanguageCode(effectiveAppLanguage(requested)), "en", "ru").distinct()
    return candidates.firstOrNull { lang -> parts.all { !it[lang].isNullOrBlank() } } ?: "en"
}
fun HelpTutorial.contentLanguageFor(requested: String): String = completeHelpLanguage(
    listOf(title,introduction) + steps.map { it.text } + listOfNotNull(caution.takeIf { it.isNotEmpty() }), requested)
fun HelpFaq.contentLanguageFor(requested: String): String = completeHelpLanguage(listOf(question,answer),requested)
fun validHelpAssetName(name: String) = Regex("[0-9a-f]{64}\\.(png|jpg|webp)").matches(name)
private fun validHelpId(id: String) = Regex("[a-z][a-z0-9.-]{1,95}").matches(id)
private fun validHelpText(text: HelpText, required: Boolean = true): Boolean =
    text.size<=7 && (!required || !text["en"].isNullOrBlank()) && text.all { (lang,value) ->
        lang in setOf("en","ru","kk","ky","tg","uz") && value.length<=6000 &&
            value.none { it.code<32 && it!='\n' && it!='\t' }
    }
fun validHelpCatalogue(catalogue: HelpCatalogue, mode: HelpMode? = null): Boolean {
    if(catalogue.schema!=1 || catalogue.revision !in 1..9_007_199_254_740_991 ||
        catalogue.tutorials.size !in 1..400 || catalogue.faqs.size>300) return false
    val ids=catalogue.tutorials.map { it.id }.toSet()
    if(ids.size!=catalogue.tutorials.size || catalogue.faqs.map { it.id }.distinct().size!=catalogue.faqs.size) return false
    for(t in catalogue.tutorials) {
        if(!validHelpId(t.id) || t.modes.isEmpty() || (mode!=null && mode !in t.modes) ||
            !validHelpText(t.title) || !validHelpText(t.introduction) || !validHelpText(t.caution,false) ||
            t.steps.size !in 2..12 || t.steps.map { it.id }.distinct().size!=t.steps.size) return false
        for(s in t.steps) {
            if(!validHelpId(s.id) || !validHelpText(s.text) || s.screenshots.size>4) return false
            for(image in s.screenshots) if(!validHelpAssetName(image.asset) || !validHelpText(image.alt) ||
                !validHelpText(image.caption,false) || image.width !in 1..6000 || image.height !in 1..6000 ||
                image.width.toLong()*image.height>24_000_000 ||
                (image.language!=null && image.language !in setOf("en","ru","kk","ky","tg","uz"))) return false
        }
    }
    return catalogue.faqs.all { f -> validHelpId(f.id) && f.modes.isNotEmpty() &&
        (mode==null || mode in f.modes) && validHelpText(f.question) && validHelpText(f.answer) &&
        (f.tutorialId==null || catalogue.tutorials.any { it.id==f.tutorialId && it.modes.containsAll(f.modes) }) }
}
fun filterHelpTutorials(catalogue: HelpCatalogue, mode: HelpMode, language: String,
    query: String, category: HelpCategory? = null): List<HelpTutorial> {
    val terms=query.trim().take(200).lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    return catalogue.tutorials.filter { t ->
        val displayLanguage=t.contentLanguageFor(language)
        mode in t.modes && (category==null || t.category==category) &&
        terms.all { term -> sequenceOf(t.title.localized(displayLanguage),t.introduction.localized(displayLanguage),t.caution.localized(displayLanguage))
            .plus(t.steps.asSequence().map { it.text.localized(displayLanguage) }).any { it.contains(term,true) } } }
}
fun filterHelpFaqs(catalogue: HelpCatalogue, mode: HelpMode, language: String, query: String): List<HelpFaq> {
    val terms=query.trim().take(200).lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    return catalogue.faqs.filter { entry ->
        val displayLanguage=entry.contentLanguageFor(language)
        mode in entry.modes && terms.all { term ->
            entry.question.localized(displayLanguage).contains(term,true) || entry.answer.localized(displayLanguage).contains(term,true) } }
}
