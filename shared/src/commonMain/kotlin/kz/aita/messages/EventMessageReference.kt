package kz.aita

import kotlinx.serialization.Serializable

/** Language-independent presentation identity. Values are immutable event-time facts, not code. */
@Serializable
data class EventMessageReference(
    val key: String,
    val arguments: Map<String, String> = emptyMap(),
    val children: Map<String, List<EventMessageReference>> = emptyMap()
)

internal data class EventMessageTemplate(
    val key: String,
    val en: String,
    val ru: String,
    val kk: String,
    val childSeparator: String = " • ",
    val ky: String? = null,
    val tg: String? = null,
    val uz: String? = null
) {
    fun text(language: String): String = when (eventMessageLanguage(language)) {
        "ru" -> ru
        "kk" -> kk
        "ky" -> ky ?: en
        "tg" -> tg ?: en
        "uz" -> uz ?: en
        else -> en
    }

    fun exactText(language: String): String? = when (eventMessageLanguage(language)) {
        "en" -> en
        "ru" -> ru
        "kk" -> kk
        "ky" -> ky
        "tg" -> tg ?: structuralText()
        "uz" -> uz ?: structuralText()
        else -> null
    }

    // A join or immutable fact contains no language-specific words. Its children still need
    // exact translations, and fact values must never be interpreted as another template.
    private fun structuralText(): String? = en.takeIf {
        Regex("\\{[a-zA-Z][a-zA-Z0-9_]{0,63}\\}").replace(it, "").none(Char::isLetter)
    }
}

internal fun eventMessageLanguage(language: String): String =
    if (canonicalLanguageCode(language) == "system") effectiveAppLanguage(language)
    else canonicalLanguageCode(language).takeIf { it in SUPPORTED_APP_LANGUAGES } ?: "en"

/** Shared by server writes, API compatibility rendering, offline caches, popups and history. */
object EventMessages {
    internal val templates: Map<String, EventMessageTemplate> by lazy {
        (coreEventMessageTemplates() + applicationEventMessageTemplates() + authenticationEventMessageTemplates() + subscriptionEventMessageTemplates() + companySupportMessageTemplates() + marketplaceEventMessageTemplates() + marketplaceExperienceMessageTemplates() + contactVerificationMessageTemplates() + inventoryContinuityMessageTemplates() + marketplaceProductMessageTemplates() + clientUpdateMessageTemplates() + tutorialMessageTemplates() + accountPresentationMessageTemplates() + storePeopleMessageTemplates() + checkoutCartMessageTemplates() + printerConnectionMessageTemplates() + diagnosticMessageTemplates() + appStateMessageTemplates() + settingsDownloadsMessageTemplates() + deviceWorkflowMessageTemplates() + notificationEventMessageTemplates()).also { definitions ->
            check(definitions.map { it.key }.toSet().size == definitions.size) { "Duplicate event message key" }
            definitions.forEach { definition ->
                val keys = placeholders(definition.en)
                check(definition.ky != null && keys == placeholders(definition.ru) &&
                    keys == placeholders(definition.kk) && keys == placeholders(definition.ky) &&
                    (definition.tg == null || keys == placeholders(definition.tg)) &&
                    (definition.uz == null || keys == placeholders(definition.uz))) {
                    "Inconsistent event message parameters: ${definition.key}"
                }
            }
        }.associateBy { it.key }
    }
    private val placeholder = Regex("\\{([a-zA-Z][a-zA-Z0-9_]{0,63})\\}")
    private fun placeholders(text: String): Set<String> = placeholder.findAll(text).map { it.groupValues[1] }.toSet()

    /** A missing/newer key or malformed arguments are not permission to throw away old text. */
    fun render(
        reference: EventMessageReference?,
        language: String,
        resourceLookup: (Long) -> List<LocalizedStringDataModel>? = { null }
    ): String? = renderChecked(reference, language, resourceLookup, 0, RenderBudget(), exact = false)

    /** Null means untranslated, not permission to label English as the requested language. */
    fun renderExact(
        reference: EventMessageReference?,
        language: String,
        resourceLookup: (Long) -> List<LocalizedStringDataModel>? = { null }
    ): String? {
        val requested = canonicalLanguageCode(language)
        if (requested != "main" && requested != "system" && requested !in SUPPORTED_APP_LANGUAGES) return null
        return renderChecked(reference, language, resourceLookup, 0, RenderBudget(), exact = true)
    }

    private class RenderBudget(var nodes: Int = 256, var characters: Int = 65_536)

    private fun renderChecked(
        reference: EventMessageReference?,
        language: String,
        resourceLookup: (Long) -> List<LocalizedStringDataModel>?,
        depth: Int,
        budget: RenderBudget,
        exact: Boolean
    ): String? {
        if (reference == null || --budget.nodes < 0 || depth > 6 || reference.key.length > 160 ||
            reference.arguments.size + reference.children.size > 32 ||
            reference.arguments.values.any { it.length > 16_384 } ||
            reference.children.values.any { it.size > 40 }) return null
        val resourceId = reference.key.removePrefix("resource.").toLongOrNull()
            ?.takeIf { reference.key.startsWith("resource.") }
        val template = templates[reference.key]
        val resourceText = resourceId?.let { id ->
            val values = resourceLookup(id)
            val selected = eventMessageLanguage(language)
            val text = values?.exactLocalizedValue(selected) ?: bundledTranslatedStringResource(id, selected)
                ?: if (exact) null else values?.rawEventLocalizedText(selected)
            text?.let { normalizeAppResourceCopy(id, it) }
        }
        val pattern = resourceText ?: (if (exact) template?.exactText(language) else template?.text(language)) ?: return null
        val required = placeholders(pattern)
        if (reference.arguments.keys.intersect(reference.children.keys).isNotEmpty() ||
            (reference.arguments.keys + reference.children.keys) != required) return null
        val values = reference.arguments.toMutableMap()
        for ((name, children) in reference.children) {
            val rendered = children.map { renderChecked(it, language, resourceLookup, depth + 1, budget, exact) ?: return null }
            values[name] = rendered.joinToString(template?.childSeparator ?: " • ")
        }
        // One substitution pass. A name/note containing "{amount}" remains literal data.
        // Bound output before interpolation as well as the graph walk.
        val outputLength = pattern.length + placeholder.findAll(pattern).sumOf {
            values.getValue(it.groupValues[1]).length - it.value.length
        }
        budget.characters -= outputLength
        if (outputLength > 65_536 || budget.characters < 0) return null
        return placeholder.replace(pattern) { values.getValue(it.groupValues[1]) }
    }

    fun isRecognized(reference: EventMessageReference?): Boolean = render(reference, "en") != null

    fun localized(reference: EventMessageReference): List<LocalizedStringDataModel> =
        (listOf("main") + SUPPORTED_APP_LANGUAGES).mapNotNull { language ->
            renderExact(reference, language)?.let { text ->
                LocalizedStringDataModel(language, text, messageTemplate = reference.takeIf { language == "main" })
            }
        }

    /** Stable dedupe identity: never depend on today's language, hash-map iteration or wording. */
    fun identity(reference: EventMessageReference): String = buildString {
        fun field(value: String) { append(value.length).append(':').append(value) }
        var nodes = 256
        fun write(value: EventMessageReference, depth: Int) {
            if (depth > 6 || --nodes < 0) { field("invalid-depth"); return }
            field(value.key.take(160))
            append('a').append(value.arguments.size).append(':')
            value.arguments.entries.sortedBy { it.key }.take(32).forEach { (name, argument) -> field(name.take(64)); field(argument.take(16_384)) }
            append('c').append(value.children.size).append(':')
            value.children.entries.sortedBy { it.key }.take(32).forEach { (name, children) ->
                field(name.take(64)); append(children.size).append(':'); children.take(40).forEach { write(it, depth + 1) }
            }
        }
        write(reference, 0)
    }
}

fun eventMessage(key: String, vararg arguments: Pair<String, String>): List<LocalizedStringDataModel> =
    EventMessages.localized(EventMessageReference(key, arguments.toMap()))

fun eventMessage(reference: EventMessageReference): List<LocalizedStringDataModel> = EventMessages.localized(reference)

fun eventMessageReference(key: String, vararg arguments: Pair<String, String>): EventMessageReference =
    EventMessageReference(key, arguments.toMap())

fun joinEventMessages(parts: List<EventMessageReference>): List<LocalizedStringDataModel> =
    eventMessage(EventMessageReference("event.join", children = mapOf("parts" to parts)))

internal fun List<LocalizedStringDataModel>.rawEventLocalizedText(language: String): String? {
    val requested = eventMessageLanguage(language)
    return firstOrNull { it.language.replace('_', '-').substringBefore('-').equals(requested, ignoreCase = true) }?.value
        ?: firstOrNull { it.language == "main" }?.value
        ?: firstOrNull { it.language == "en" }?.value
        ?: firstOrNull()?.value
}

fun List<LocalizedStringDataModel>.explicitEventMessageReference(): EventMessageReference? =
    firstNotNullOfOrNull { it.messageTemplate }
