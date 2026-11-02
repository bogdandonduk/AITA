package kz.aita

import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Bounds are calendar dates in the viewer's timezone, not fixed 24-hour UTC windows. */
@Serializable
data class RecordListOptions(
    val dateField: String = "created",
    val fromDate: String = "",
    val throughDate: String = "",
    val sort: String = "default",
    val ascending: Boolean = false
)

fun parseRecordDate(text: String): LocalDate? = runCatching {
    val value = text.trim()
    when {
        Regex("\\d{4}-\\d{2}-\\d{2}").matches(value) -> LocalDate.parse(value)
        Regex("\\d{2}\\.\\d{2}\\.\\d{4}").matches(value) -> value.split('.').let { LocalDate(it[2].toInt(), it[1].toInt(), it[0].toInt()) }
        else -> null
    }
}.getOrNull()

fun RecordListOptions.valid(): Boolean {
    val from = parseRecordDate(fromDate); val through = parseRecordDate(throughDate)
    return (fromDate.isBlank() || from != null) && (throughDate.isBlank() || through != null) &&
        (from == null || through == null || from <= through)
}

/** Parse the filter once per list; large offline inventories must not reparse it for every row. */
class RecordDateMatcher(private val options: RecordListOptions, private val zone: TimeZone = TimeZone.currentSystemDefault()) {
    private val from = parseRecordDate(options.fromDate)
    private val through = parseRecordDate(options.throughDate)
    private val valid = (options.fromDate.isBlank() || from != null) && (options.throughDate.isBlank() || through != null) &&
        (from == null || through == null || from <= through)
    fun matches(created: Long, modified: Long): Boolean {
        if (!valid) return false
        if (from == null && through == null) return true
        val millis = if (options.dateField == "modified") modified else created
        if (millis <= 0L) return false
        val day = runCatching { Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date }.getOrNull() ?: return false
        return (from == null || day >= from) && (through == null || day <= through)
    }
}

fun recordMatchesDate(created: Long, modified: Long, options: RecordListOptions, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean =
    RecordDateMatcher(options, zone).matches(created, modified)

fun <T> recordDateList(items: List<T>, options: RecordListOptions, created: (T) -> Long, modified: (T) -> Long,
                       identity: (T) -> String, zone: TimeZone = TimeZone.currentSystemDefault()): List<T> {
    val matcher = RecordDateMatcher(options, zone)
    val filtered = if (options.fromDate.isBlank() && options.throughDate.isBlank()) items
        else items.filter { matcher.matches(created(it), modified(it)) }
    if (options.sort !in setOf("created", "modified")) return filtered
    fun time(item: T) = if (options.sort == "modified") modified(item) else created(item)
    return filtered.sortedWith(Comparator { a, b ->
        val at = time(a); val bt = time(b)
        when {
            at <= 0 && bt > 0 -> 1
            bt <= 0 && at > 0 -> -1
            at != bt -> if (options.ascending) at.compareTo(bt) else bt.compareTo(at)
            else -> identity(a).compareTo(identity(b))
        }
    })
}

fun recordDateText(key: String, language: String): String {
    val values = when (key) {
        "created" -> listOf("Added", "Добавлено", "Қосылған", "Кошулган", "Илова шуд", "Qo‘shilgan")
        "registered" -> listOf("Registered", "Зарегистрирован", "Тіркелген", "Катталган", "Сабти ном", "Ro‘yxatdan o‘tgan")
        "modified" -> listOf("Modified", "Изменено", "Өзгертілген", "Өзгөртүлгөн", "Тағйир ёфт", "O‘zgartirilgan")
        "unknown" -> listOf("Date unavailable", "Дата неизвестна", "Күні белгісіз", "Күнү белгисиз", "Сана номаълум", "Sana noma’lum")
        "tools" -> listOf("Dates and sorting", "Даты и сортировка", "Күндер және сұрыптау", "Күндөр жана иреттөө", "Санаҳо ва мураттабсозӣ", "Sanalar va saralash")
        "field" -> listOf("Search by date", "Поиск по дате", "Күні бойынша іздеу", "Күнү боюнча издөө", "Ҷустуҷӯ аз рӯи сана", "Sana bo‘yicha qidirish")
        "from" -> listOf("From", "С", "Бастап", "Баштап", "Аз", "Dan")
        "through" -> listOf("Through", "По включительно", "Қоса алғанда дейін", "Чейин кошо", "То, бо шумули рӯз", "Gacha, shu kun ham")
        "sort" -> listOf("Sort by", "Сортировать по", "Сұрыптау", "Иреттөө", "Мураттаб кардан", "Saralash")
        "default" -> listOf("Standard order", "Обычный порядок", "Қалыпты рет", "Кадимки тартип", "Тартиби муқаррарӣ", "Odatdagi tartib")
        "ascending" -> listOf("Oldest first", "Сначала старые", "Ескілері алдымен", "Адегенде эскилери", "Аввал кӯҳнаҳо", "Avval eskilari")
        "descending" -> listOf("Newest first", "Сначала новые", "Жаңалары алдымен", "Адегенде жаңылары", "Аввал навҳо", "Avval yangilari")
        "invalid" -> listOf("Choose a valid date range", "Выберите корректный период", "Дұрыс кезеңді таңдаңыз", "Туура мезгилди тандаңыз", "Давраи дурустро интихоб кунед", "To‘g‘ri davrni tanlang")
        else -> error("Unknown record date label: $key")
    }
    return values[listOf("en","ru","kk","ky","tg","uz").indexOf(language.lowercase().substringBefore('-')).coerceAtLeast(0)]
}
