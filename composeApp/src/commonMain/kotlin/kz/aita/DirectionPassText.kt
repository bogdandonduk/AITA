package kz.aita

internal fun AppConfiguration.directionText(key: String): String {
    val t = when (key) {
        "brand" -> listOf("Brand · optional", "Бренд · необязательно", "Бренд · міндетті емес", "Бренд · милдеттүү эмес", "Бренд · ихтиёрӣ", "Brend · ixtiyoriy")
        "photos" -> listOf("With product photos", "С фотографиями товара", "Тауар суреттерімен", "Товардын сүрөттөрү менен", "Бо аксҳои мол", "Mahsulot rasmlari bilan")
        "picking" -> listOf("Check the shipment", "Проверка комплектации", "Жөнелтімді тексеру", "Жүктү текшерүү", "Санҷиши бор", "Jo‘natmani tekshirish")
        "picking_help" -> listOf("Check the agreed item and quantity. Marks are saved with your app state; changing an item or quantity requires a new check. Confirm packing and dispatch on the Actions tab.", "Проверьте согласованный товар и количество. Отметки сохраняются в состоянии приложения; изменение товара или количества требует новой проверки. Подтвердите упаковку и отправку на вкладке «Действия».", "Келісілген тауар мен мөлшерді тексеріңіз. Белгілер қолданба күйінде сақталады; тауар не мөлшер өзгерсе, қайта тексеріледі. Қаптау мен жөнелтуді «Әрекеттер» қойындысында растаңыз.", "Макулдашылган товарды жана санды текшериңиз. Белгилер колдонмонун абалында сакталат; товар же сан өзгөрсө кайра текшерилет. Таңгактоо жана жөнөтүүнү «Аракеттер» өтмөгүндө ырастаңыз.", "Мол ва миқдори мувофиқашударо санҷед. Аломатҳо дар ҳолати барнома сабт мешаванд; тағйири мол ё миқдор санҷиши нав мехоҳад. Бастабандӣ ва фиристоданро дар ҷадвали амалҳо тасдиқ кунед.", "Kelishilgan mahsulot va miqdorni tekshiring. Belgilar ilova holatida saqlanadi; mahsulot yoki miqdor o‘zgarsa qayta tekshiriladi. Qadoqlash va jo‘natishni «Amallar» sahifasida tasdiqlang.")
        "find" -> listOf("Product, barcode or order", "Товар, штрихкод или заказ", "Тауар, штрихкод не тапсырыс", "Товар, штрихкод же буйрутма", "Мол, штрихкод ё фармоиш", "Mahsulot, shtrix-kod yoki buyurtma")
        "remaining" -> listOf("Not checked", "Не проверено", "Тексерілмеген", "Текшериле элек", "Санҷиданашуда", "Tekshirilmagan")
        "all" -> listOf("All items", "Все товары", "Барлық тауарлар", "Бардык товарлар", "Ҳамаи молҳо", "Barcha mahsulotlar")
        "checked" -> listOf("Checked", "Проверено", "Тексерілді", "Текшерилди", "Санҷида шуд", "Tekshirildi")
        "pending" -> listOf("Quantity is not agreed yet", "Количество ещё не согласовано", "Мөлшер әлі келісілмеген", "Саны макулдаша элек", "Миқдор ҳанӯз мувофиқа нашудааст", "Miqdor hali kelishilmagan")
        "full" -> listOf("This checklist is too large to save more marks. Finish or clear an earlier shipment first.", "В списке слишком много отметок для сохранения. Сначала завершите или очистите предыдущую комплектацию.", "Белгілерді сақтау шегі толды. Алдымен бұрынғы жөнелтімді аяқтаңыз не тазалаңыз.", "Белгилерди сактоо чеги толду. Адегенде мурунку жүктү бүтүрүңүз же тазалаңыз.", "Барои сабт аломатҳо зиёданд. Аввал бори пешинаро анҷом диҳед ё пок кунед.", "Belgilarni saqlash chegarasi to‘ldi. Avval oldingi jo‘natmani yakunlang yoki tozalang.")
        "empty" -> listOf("No matching items", "Подходящих товаров нет", "Сәйкес тауар жоқ", "Туура келген товар жок", "Моли мувофиқ нест", "Mos mahsulot topilmadi")
        "reset" -> listOf("Clear check marks", "Сбросить отметки", "Белгілерді тазалау", "Белгилерди тазалоо", "Пок кардани аломатҳо", "Belgilarni tozalash")
        else -> error("Unknown direction message: $key")
    }
    return t[listOf("en", "ru", "kk", "ky", "tg", "uz").indexOf(stateValues.appLanguage.lowercase().let { if (it == "tj") "tg" else it }).takeIf { it >= 0 } ?: 0]
}
