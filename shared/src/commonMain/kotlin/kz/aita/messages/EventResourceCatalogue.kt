package kz.aita

/** Immutable index of the existing central UI-string resources; labels are not copied into events. */
class EventResourceCatalogue(groups: List<LocalizedStringGroupDataModel>) {
    private val byId = groups.associateBy { it.id }
    private val textIds: Map<String, Set<Long>> = buildMap {
        val candidates = linkedMapOf<String, MutableSet<Long>>()
        groups.forEach { group ->
            group.values.forEach { value ->
                if (value.value.isNotBlank()) candidates.getOrPut(value.value) { linkedSetOf() }.add(group.id)
            }
        }
        candidates.forEach { (text, ids) -> put(text, ids.toSet()) }
    }
    private val uniqueTextIds: Map<String, Long> = textIds.mapNotNull { (text, ids) ->
        ids.singleOrNull()?.let { text to it }
    }.toMap()

    fun values(id: Long): List<LocalizedStringDataModel>? = byId[id]?.values

    fun referenceFor(text: String): EventMessageReference? {
        // An old alias is not evidence when two current resources or historical event types
        // use exactly the same wording (e.g. goods-item versus stock-item messages in Russian).
        if (textIds[text]?.size?.let { it > 1 } == true) return null
        uniqueTextIds[text]?.let { return EventMessageReference("resource.$it") }
        if (legacyEventMessageIsAmbiguous(text)) return null
        return legacyResourceMessageReference(text)
    }

    fun referenceFor(values: List<LocalizedStringDataModel>): EventMessageReference? {
        values.explicitEventMessageReference()?.let { return it }
        val nonEmpty = values.filter { it.value.isNotBlank() }
        if (nonEmpty.isEmpty()) return null
        val ids = nonEmpty.map { uniqueTextIds[it.value] ?: return null }.toSet()
        return ids.singleOrNull()?.let { EventMessageReference("resource.$it") }
    }
}

/** The wire model retains readable fallbacks for older clients and unknown future message keys. */
data class EventTextStorage(
    val reference: EventMessageReference?,
    val fallback: String,
    val translations: List<LocalizedStringDataModel>
)

fun eventTextForStorage(
    text: String,
    translations: List<LocalizedStringDataModel> = emptyList(),
    reference: EventMessageReference? = null,
    resources: EventResourceCatalogue = EventResourceCatalogue(emptyList())
): EventTextStorage {
    val resolved = reference ?: translations.eventMessageReferenceOrNull()
        ?: resources.referenceFor(translations) ?: legacyEventMessageReference(text) ?: resources.referenceFor(text)
    val renderable = listOf("en", "ru", "kk", "ky").all { EventMessages.render(resolved, it, resources::values) != null }
    return EventTextStorage(
        reference = resolved,
        fallback = if (renderable) "" else text,
        translations = if (renderable) translations.filter { value ->
            value.language != "main" && EventMessages.renderExact(resolved, value.language, resources::values) == null
        } else translations
    )
}

fun eventTextCompatibilityValues(
    reference: EventMessageReference?,
    fallback: List<LocalizedStringDataModel>,
    resources: EventResourceCatalogue = EventResourceCatalogue(emptyList())
): List<LocalizedStringDataModel> {
    if (reference == null) return fallback
    val rendered = (listOf("main") + SUPPORTED_APP_LANGUAGES).mapNotNull { language ->
        EventMessages.renderExact(reference, language, resources::values)?.let { text ->
            LocalizedStringDataModel(language, text)
        }
    }
    if (rendered.isEmpty()) return fallback
    // Keep supplied translations for languages whose structured template has not shipped yet.
    // Do not silently discard them when rebuilding readable values for an older client.
    return rendered.withMissingLocalizedValues(fallback).mapIndexed { index, value ->
        value.copy(messageTemplate = reference.takeIf { index == 0 })
    }
}

/** Include unknown fallback text in dedupe identity: an unrecognized key alone proves nothing. */
fun eventTextIdentity(text: EventTextStorage): String = buildString {
    text.reference?.let { append(EventMessages.identity(it)) }
    append('|').append(text.fallback.length).append(':').append(text.fallback)
    text.translations.sortedBy { it.language }.forEach {
        append('|').append(it.language.length).append(':').append(it.language)
        append(':').append(it.value.length).append(':').append(it.value)
    }
}
