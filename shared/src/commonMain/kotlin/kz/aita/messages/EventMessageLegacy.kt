package kz.aita

/** Exact, bounded recovery of application-owned v1 wording. Never guess from part of a message. */
private object LegacyEventMessageIndex {
    private val placeholder = Regex("\\{([a-zA-Z][a-zA-Z0-9_]{0,63})\\}")
    private data class Pattern(val key: String, val regex: Regex, val names: List<String>)
    private val definitions = legacyEventMessagePatterns()
    private val staticReferences: Map<String, List<EventMessageReference>> = buildMap {
        definitions.forEach { definition ->
            listOf(definition.en, definition.ru, definition.kk).distinct().forEach pattern@ { text ->
                if (!placeholder.containsMatchIn(text)) {
                    put(text, (get(text).orEmpty() + EventMessageReference(definition.key)).distinct())
                }
            }
        }
    }
    private val patterns: List<Pattern> = buildList {
        definitions.forEach { definition ->
            listOf(definition.en, definition.ru, definition.kk).distinct().forEach pattern@ { text ->
                val matches = placeholder.findAll(text).toList()
                // No bare capture, adjacent captures, or broad pattern which could classify a note.
                if (matches.isEmpty() || matches.size > 6 ||
                    text.length - matches.sumOf { it.value.length } < 5 ||
                    matches.zipWithNext().any { (a, b) -> a.range.last + 1 == b.range.first }) return@pattern
                var previous = 0
                val expression = buildString {
                    matches.forEach { match ->
                        append(Regex.escape(text.substring(previous, match.range.first)))
                        append("(.*?)")
                        previous = match.range.last + 1
                    }
                    append(Regex.escape(text.substring(previous)))
                }
                add(Pattern(definition.key, Regex(expression, RegexOption.DOT_MATCHES_ALL), matches.map { it.groupValues[1] }))
            }
        }
    }

    fun candidates(text: String): Set<EventMessageReference> {
        if (text.isBlank() || text.length > 8_192) return emptySet()
        staticReferences[text]?.let { return it.toSet() }
        return buildSet {
            for (pattern in patterns) {
                val match = pattern.regex.matchEntire(text) ?: continue
                val values = linkedMapOf<String, String>()
                var consistent = true
                pattern.names.forEachIndexed { index, name ->
                    val value = match.groupValues[index + 1]
                    if (values.containsKey(name) && values[name] != value) consistent = false
                    values[name] = value
                }
                if (consistent) add(EventMessageReference(pattern.key, values))
            }
        }
    }
}

internal fun legacyEventMessageIsAmbiguous(text: String): Boolean =
    LegacyEventMessageIndex.candidates(text).size > 1

/** Unknown/ambiguous old text stays readable rather than being mislabeled or rewritten. */
fun legacyEventMessageReference(text: String): EventMessageReference? =
    LegacyEventMessageIndex.candidates(text).singleOrNull()

fun List<LocalizedStringDataModel>.eventMessageReferenceOrNull(): EventMessageReference? {
    explicitEventMessageReference()?.let { return it }
    val source = filter { it.value.isNotBlank() && it.language.lowercase() in setOf("main", "en", "ru", "kk") }
    if (source.isEmpty()) return null
    val candidates = source.map { LegacyEventMessageIndex.candidates(it.value) }
    if (candidates.any { it.isEmpty() }) return null
    return candidates.reduce { a, b -> a.intersect(b) }.singleOrNull()
}

/** A literal child is data; braces or language names inside it are never evaluated. */
fun eventFact(value: String): EventMessageReference = eventMessageReference("event.literal", "value" to value)

fun eventNamedFact(value: String, fallbackKey: String): EventMessageReference =
    if (value.isBlank()) EventMessageReference(fallbackKey) else eventFact(value)
