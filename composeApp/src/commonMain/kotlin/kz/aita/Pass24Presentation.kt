package kz.aita

internal fun AppConfiguration.pass24Text(key: String): String {
    val words = when (key) {
        "batch_quantity" -> listOf("Enter a quantity greater than zero", "Введите количество больше нуля", "Нөлден үлкен мөлшер енгізіңіз", "Нөлдөн чоң сан киргизиңиз", "Миқдори аз сифр зиёдро ворид кунед", "Noldan katta miqdor kiriting")
        "batch_price" -> listOf("Enter a valid purchase price (zero is allowed)", "Введите корректную цену поставки (можно 0)", "Сатып алу бағасын енгізіңіз (0 болады)", "Сатып алуу баасын киргизиңиз (0 болот)", "Нархи дурусти харидро ворид кунед (0 мумкин)", "Xarid narxini kiriting (0 mumkin)")
        "batch_date" -> listOf("Check the batch dates", "Проверьте даты партии", "Партия күндерін тексеріңіз", "Партия күндөрүн текшериңиз", "Санаҳои партияро санҷед", "Partiya sanalarini tekshiring")
        "catalogue_short" -> listOf("Catalogue", "Каталог", "Каталог", "Каталог", "Феҳрист", "Katalog")
        "shops_short" -> listOf("Shops", "Магазины", "Дүкендер", "Дүкөндөр", "Мағозаҳо", "Do‘konlar")
        "catalogue" -> listOf("Explore the catalogue", "Каталог товаров", "Тауарлар каталогы", "Товарлар каталогу", "Феҳристи молҳо", "Mahsulotlar katalogi")
        "spotlight" -> listOf("Discover in AITA", "Открывайте в AITA", "AITA-дан табыңыз", "AITAдан табыңыз", "Дар AITA пайдо кунед", "AITA orqali toping")
        "shops" -> listOf("Meet the shops", "Знакомьтесь с магазинами", "Дүкендермен танысыңыз", "Дүкөндөр менен таанышыңыз", "Бо мағозаҳо шинос шавед", "Do‘konlar bilan tanishing")
        "new" -> listOf("Fresh discoveries", "Свежие находки", "Жаңа табылғандар", "Жаңы табылгалар", "Бозёфтҳои нав", "Yangi topilmalar")
        "all" -> listOf("All offers", "Все предложения", "Барлық ұсыныстар", "Бардык сунуштар", "Ҳамаи пешниҳодҳо", "Barcha takliflar")
        "discover" -> listOf("Find your next favourite", "Найдите то, что полюбите", "Өзіңізге ұнайтынды табыңыз", "Жактырган нерсеңизди табыңыз", "Чизи дӯстдоштаатонро ёбед", "Yoqtirgan narsangizni toping")
        "discover_note" -> listOf("Local shops. Useful finds. One shopping list.", "Местные магазины. Полезные находки. Один список покупок.", "Жергілікті дүкендер. Пайдалы заттар. Бір сатып алу тізімі.", "Жергиликтүү дүкөндөр. Пайдалуу табылгалар. Бир соода тизмеси.", "Мағозаҳои маҳаллӣ. Бозёфтҳои муфид. Як рӯйхати харид.", "Mahalliy do‘konlar. Foydali topilmalar. Bitta xarid ro‘yxati.")
        "advertisement" -> listOf("Advertisement", "Реклама", "Жарнама", "Жарнама", "Реклама", "Reklama")
        else -> listOf(key, key, key, key, key, key)
    }
    return words[listOf("en", "ru", "kk", "ky", "tg", "uz").indexOf(stateValues.appLanguage).coerceAtLeast(0)]
}
