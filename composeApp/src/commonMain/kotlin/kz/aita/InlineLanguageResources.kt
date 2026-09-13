package kz.aita

/** Exact authored UI literals only; never apply this lookup to product names or user notes. */
internal fun bundledInlineTranslation(en: String, ru: String, kk: String, language: String): String? {
    val id = inlineNumberedResources[Triple(en, ru, kk)] ?: return null
    return bundledTranslatedStringResource(id, language)
}

private val inlineNumberedResources = mapOf(
    Triple("Actions", "Действия", "Әрекеттер") to 1650L,
    Triple("All categories", "Все категории", "Барлық санаттар") to 1182L,
    Triple("Back", "Назад", "Артқа") to 29L,
    Triple("Before", "Было", "Бұрын") to 1061L,
    Triple("Cancelled", "Отменено", "Бас тартылды") to 975L,
    Triple("Categories", "Категории", "Санаттар") to 114L,
    Triple("Clear filters", "Сбросить фильтры", "Сүзгілерді тазалау") to 2344L,
    Triple("Close", "Закрыть", "Жабу") to 319L,
    Triple("Closed", "Закрыт", "Жабық") to 832L,
    Triple("Closed", "Закрытые", "Жабық") to 832L,
    Triple("Driver handoff", "Передача водителю", "Жүргізушіге тапсыру") to 1757L,
    Triple("General", "Общее", "Жалпы") to 819L,
    Triple("History", "История", "Тарих") to 1330L,
    Triple("Items", "Товары", "Тауарлар") to 392L,
    Triple("Manufacturers", "Производители", "Өндірушілер") to 1197L,
    Triple("New password", "Новый пароль", "Жаңа құпия сөз") to 84L,
    Triple("Open", "Открытые", "Ашық") to 1377L,
    Triple("Overview", "Обзор", "Шолу") to 672L,
    Triple("Paid", "Оплачено", "Төленді") to 150L,
    Triple("Password", "Пароль", "Құпия сөз") to 6L,
    Triple("Products", "Товары", "Тауарлар") to 2312L,
    Triple("Quantity", "Количество", "Саны") to 271L,
    Triple("Refresh", "Обновить", "Жаңарту") to 237L,
    Triple("Repeat password", "Повторите пароль", "Құпия сөзді қайталаңыз") to 14L,
    Triple("Sending…", "Отправка…", "Жіберілуде…") to 907L,
    Triple("Sent", "Отправлено", "Жіберілді") to 967L,
    Triple("Updated", "Обновлено", "Жаңартылды") to 808L,
    Triple("Work", "Работа", "Жұмыс") to 2658L,
    Triple("days", "дней", "күн") to 1067L,
)
