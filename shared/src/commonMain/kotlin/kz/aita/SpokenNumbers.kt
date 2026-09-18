package kz.aita

/** Applied only to speech results, before the field's existing validation. No translation service. */
enum class VoiceNumberField { Text, Integer, Decimal, Digits }

private data class SpokenNumberWord(val value: Long = 0, val kind: Int = 0)
private data class SpokenNumberToken(val start: Int, val end: Int, val text: String)
private class SpokenNumberLanguage {
    val words = mutableMapOf<String, SpokenNumberWord>()
    var tenCombinesWithUnit = false
    fun values(value: Long, vararg names: String) = names.forEach { words[spokenWordKey(it)] = SpokenNumberWord(value) }
    fun marker(kind: Int, vararg names: String) = names.forEach { words[spokenWordKey(it)] = SpokenNumberWord(kind = kind) }
    fun series(numbers: List<Long>, names: String) = names.split(' ').forEachIndexed { i, name -> values(numbers[i], name) }
}

private const val HUNDRED = 1
private const val SCALE = 2
private const val POINT = 3
private const val MINUS = 4
private const val JOIN = 5
private const val NUMBER_LIMIT = 999_999_999_999_999L
private val spokenNumberLanguages by lazy {
    fun language(block: SpokenNumberLanguage.() -> Unit) = SpokenNumberLanguage().apply(block)
    val small = (0L..19L).toList()
    val tens = (2L..9L).map { it * 10 }
    mapOf(
        "en" to language {
            series(small, "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen")
            values(0, "nought"); series(tens, "twenty thirty forty fifty sixty seventy eighty ninety")
            marker(HUNDRED, "hundred"); marker(POINT, "point", "dot", "comma"); marker(MINUS, "minus", "negative"); marker(JOIN, "and")
            words["thousand"] = SpokenNumberWord(1_000, SCALE); words["million"] = SpokenNumberWord(1_000_000, SCALE); words["billion"] = SpokenNumberWord(1_000_000_000, SCALE)
        },
        "ru" to language {
            series(small, "ноль один два три четыре пять шесть семь восемь девять десять одиннадцать двенадцать тринадцать четырнадцать пятнадцать шестнадцать семнадцать восемнадцать девятнадцать")
            values(0, "нуль"); values(1, "одна", "одно"); values(2, "две")
            series(tens, "двадцать тридцать сорок пятьдесят шестьдесят семьдесят восемьдесят девяносто")
            series((1L..9L).map { it * 100 }, "сто двести триста четыреста пятьсот шестьсот семьсот восемьсот девятьсот")
            marker(POINT, "запятая", "точка"); marker(MINUS, "минус"); marker(JOIN, "и")
            listOf("тысяча", "тысячи", "тысяч").forEach { words[it] = SpokenNumberWord(1_000, SCALE) }
            listOf("миллион", "миллиона", "миллионов").forEach { words[it] = SpokenNumberWord(1_000_000, SCALE) }
            listOf("миллиард", "миллиарда", "миллиардов").forEach { words[it] = SpokenNumberWord(1_000_000_000, SCALE) }
        },
        "kk" to language {
            tenCombinesWithUnit = true
            series((0L..10L).toList(), "нөл бір екі үш төрт бес алты жеті сегіз тоғыз он")
            series(tens, "жиырма отыз қырық елу алпыс жетпіс сексен тоқсан")
            marker(HUNDRED, "жүз"); marker(POINT, "үтір", "нүкте"); marker(MINUS, "минус"); marker(JOIN, "және")
            words["мың"] = SpokenNumberWord(1_000, SCALE); words["миллион"] = SpokenNumberWord(1_000_000, SCALE); words["миллиард"] = SpokenNumberWord(1_000_000_000, SCALE)
        },
        "ky" to language {
            tenCombinesWithUnit = true
            series((0L..10L).toList(), "нөл бир эки үч төрт беш алты жети сегиз тогуз он")
            series(tens, "жыйырма отуз кырк элүү алтымыш жетимиш сексен токсон")
            marker(HUNDRED, "жүз"); marker(POINT, "үтүр", "чекит"); marker(MINUS, "минус"); marker(JOIN, "жана")
            words["миң"] = SpokenNumberWord(1_000, SCALE); words["миллион"] = SpokenNumberWord(1_000_000, SCALE); words["миллиард"] = SpokenNumberWord(1_000_000_000, SCALE)
        },
        "tg" to language {
            series(small, "сифр як ду се чор панҷ шаш ҳафт ҳашт нӯҳ даҳ ёздаҳ дувоздаҳ сездаҳ чордаҳ понздаҳ шонздаҳ ҳабдаҳ ҳаждаҳ нуздаҳ")
            values(0, "нол"); values(9, "нуҳ"); values(17, "ҳафдаҳ")
            series(tens, "бист сӣ чил панҷоҳ шаст ҳафтод ҳаштод навад")
            series((2L..9L).map { it * 100 }, "дусад сесад чорсад панҷсад шашсад ҳафтсад ҳаштсад нӯҳсад")
            marker(HUNDRED, "сад"); marker(POINT, "нуқта", "вергул"); marker(MINUS, "минус"); marker(JOIN, "у", "ва")
            words["ҳазор"] = SpokenNumberWord(1_000, SCALE); words["миллион"] = SpokenNumberWord(1_000_000, SCALE); words["миллиард"] = SpokenNumberWord(1_000_000_000, SCALE)
            words.toMap().filterValues { it.kind == 0 || it.kind == HUNDRED || it.kind == SCALE }.forEach { (name, value) -> words[name + "у"] = value }
        },
        "uz" to language {
            tenCombinesWithUnit = true
            series((0L..10L).toList(), "nol bir ikki uch to'rt besh olti yetti sakkiz to'qqiz o'n")
            series(tens, "yigirma o'ttiz qirq ellik oltmish yetmish sakson to'qson")
            series((0L..10L).toList(), "нол бир икки уч тўрт беш олти етти саккиз тўққиз ўн")
            series(tens, "йигирма ўттиз қирқ эллик олтмиш етмиш саксон тўқсон")
            marker(HUNDRED, "yuz", "юз"); marker(POINT, "vergul", "nuqta", "вергул", "нуқта"); marker(MINUS, "minus", "минус"); marker(JOIN, "va", "ва")
            listOf("ming", "минг").forEach { words[it] = SpokenNumberWord(1_000, SCALE) }
            listOf("million", "миллион").forEach { words[it] = SpokenNumberWord(1_000_000, SCALE) }
            listOf("milliard", "миллиард").forEach { words[it] = SpokenNumberWord(1_000_000_000, SCALE) }
        }
    )
}

private fun spokenWordKey(text: String) = text.lowercase().replace('ё', 'е')
    .map { if (it in "’‘ʻʼ`") '\'' else it }.joinToString("")

private fun SpokenNumberLanguage.word(text: String): SpokenNumberWord? = words[spokenWordKey(text)]
    ?: text.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toLongOrNull()
        ?.takeIf { it <= NUMBER_LIMIT }?.let { SpokenNumberWord(it) }

private fun parseSpokenWhole(tokens: List<String>, language: SpokenNumberLanguage): String? {
    if (tokens.isEmpty()) return null
    val words = tokens.map { language.word(it) ?: return null }
    // Digit-by-digit codes retain zeros; they are never added arithmetically.
    if (words.all { it.kind == 0 && it.value in 0..9 }) return tokens.zip(words).joinToString("") { (token, word) ->
        if (token.all { it in '0'..'9' }) token else word.value.toString()
    }
    if (tokens.size == 1 && tokens[0].all { it in '0'..'9' }) return tokens[0]
    var total = 0L; var group = 0L; var lastScale = Long.MAX_VALUE
    var previous = -1; var hasHundred = false
    words.forEachIndexed { index, word ->
        when (word.kind) {
            JOIN -> {
                if (index == 0 || index == words.lastIndex || previous !in listOf(2, 3, 4) || words[index + 1].kind == JOIN) return null
                return@forEachIndexed
            }
            SCALE -> {
                if (word.value >= lastScale || (previous == 4 && group == 0L)) return null
                val factor = if (previous == -1) 1 else group
                if (factor <= 0 || factor > (NUMBER_LIMIT - total) / word.value) return null
                total += factor * word.value; group = 0; lastScale = word.value; previous = 4; hasHundred = false
            }
            HUNDRED -> {
                if (hasHundred || (previous != -1 && previous != 4 && !(previous == 0 && group in 1..9))) return null
                group = (if (group == 0L) 1 else group) * 100; previous = 3; hasHundred = true
            }
            0 -> {
                val value = word.value
                val rank = when { value >= 100 -> 3; value >= 20 && value % 10 == 0L || value == 10L && language.tenCombinesWithUnit -> 2; value >= 10 -> 1; else -> 0 }
                if (previous !in listOf(-1, 4) && !(previous == 3 && value < 100) && !(previous == 2 && value < 10)) return null
                // Turkic eleven = ten + one; English/Russian have a separate word.
                if (rank == 3 && hasHundred) return null
                group += value; previous = rank; if (rank == 3) hasHundred = true
            }
            else -> return null
        }
    }
    return (total + group).takeIf { it <= NUMBER_LIMIT }?.toString()
}

private fun parseSpokenNumber(tokens: List<String>, language: SpokenNumberLanguage): String? {
    var parts = tokens
    val negative = language.word(parts.first())?.kind == MINUS
    if (negative) parts = parts.drop(1)
    if (parts.isEmpty()) return null
    val points = parts.indices.filter { language.word(parts[it])?.kind == POINT }
    if (points.size > 1) return null
    val result = if (points.isEmpty()) parseSpokenWhole(parts, language) else {
        val split = points.single()
        val whole = if (split == 0) "0" else parseSpokenWhole(parts.take(split), language)
        val fractionParts = parts.drop(split + 1)
        // Magnitudes in a fraction are ambiguous; do not silently turn them into a price.
        val fraction = fractionParts.takeIf { it.isNotEmpty() && it.all { word -> language.word(word)?.let { n -> n.kind == 0 && n.value < 100 } == true } }
            ?.let { parseSpokenWhole(it, language) }
        if (whole != null && fraction != null) "$whole.$fraction" else null
    }
    return result?.let { if (negative) "-$it" else it }
}

/** Unknown/malformed number phrases remain verbatim. The recognizer's language prevents
 * false matches such as Russian «он» (he) becoming Kazakh «он» (ten). */
fun spokenNumbersToDigits(text: String, languageTag: String): String {
    if (text.length > 4096) return text
    val code = languageTag.replace('_', '-').substringBefore('-').lowercase().let { if (it == "kz") "kk" else it }
    val language = spokenNumberLanguages[code] ?: return text
    val tokens = mutableListOf<SpokenNumberToken>()
    var at = 0
    while (at < text.length) {
        if (!text[at].isLetterOrDigit()) { at++; continue }
        val start = at++
        while (at < text.length && (text[at].isLetterOrDigit() || text[at] in "'’‘ʻʼ`")) at++
        tokens += SpokenNumberToken(start, at, text.substring(start, at))
    }
    val output = StringBuilder(); var copied = 0; var index = 0
    while (index < tokens.size) {
        val first = tokens[index]
        if (language.word(first.text) == null) { index++; continue }
        var end = index + 1
        while (end < tokens.size && language.word(tokens[end].text) != null) {
            val gap = text.substring(tokens[end - 1].end, tokens[end].start)
            val wordHyphen = gap == "-" && tokens[end - 1].text.any(Char::isLetter) && tokens[end].text.any(Char::isLetter)
            if (!gap.all(Char::isWhitespace) && !wordHyphen) break
            end++
        }
        var numberEnd = end
        while (numberEnd > index && language.word(tokens[numberEnd - 1].text)?.kind == JOIN) numberEnd--
        val span = tokens.subList(index, numberEnd)
        val replacement = if (span.isNotEmpty() && span.any { it.text.any(Char::isLetter) }) parseSpokenNumber(span.map { it.text }, language) else null
        if (replacement != null) {
            output.append(text.substring(copied, first.start)).append(replacement)
            copied = span.last().end
        }
        index = end
    }
    return output.append(text.substring(copied)).toString()
}

/** Null means the spoken result cannot safely fill this numeric field; preserve its old value. */
fun voiceTextForField(text: String, languageTag: String, field: VoiceNumberField): String? {
    val converted = spokenNumbersToDigits(text, languageTag)
    if (field == VoiceNumberField.Text) return converted
    val compact = converted.trim().filterNot(Char::isWhitespace)
    val pattern = when (field) {
        VoiceNumberField.Decimal -> "[+-]?[0-9]+([.,][0-9]+)?"
        VoiceNumberField.Digits -> "\\+?[0-9]+"
        else -> "[+-]?[0-9]+"
    }
    return compact.takeIf { it.length <= 128 && Regex(pattern).matches(it) }
}
