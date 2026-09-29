package kz.aita

internal fun AppConfiguration.pass23Text(key: String): String {
    val words = when (key) {
        "uncategorized" -> listOf("Uncategorized", "Без категории", "Санатсыз", "Категориясыз", "Бе гурӯҳ", "Toifasiz")
        "no_subcategory" -> listOf("No subcategory", "Без подкатегории", "Ішкі санатсыз", "Ички категориясыз", "Бе зергурӯҳ", "Quyi toifasiz")
        "category_notice" -> listOf("Choose a category later", "Категория не выбрана — укажите её позже", "Санатты кейін таңдаңыз", "Категорияны кийин тандаңыз", "Гурӯҳро баъд интихоб кунед", "Toifani keyin tanlang")
        "markup" -> listOf("Sale price: percentage of purchase cost (120% = +20%)", "Цена продажи от закупочной (120% = наценка 20%)", "Сату бағасы: сатып алу бағасынан (120% = +20%)", "Сатуу баасы: сатып алуу баасынан (120% = +20%)", "Нархи фурӯш аз харид (120% = +20%)", "Sotish narxi: xarid narxidan (120% = +20%)")
        "linux" -> listOf("Linux x86-64 · DEB / RPM · Java included", "Linux x86-64 · DEB / RPM · Java включена", "Linux x86-64 · DEB / RPM · Java қамтылған", "Linux x86-64 · DEB / RPM · Java камтылган", "Linux x86-64 · DEB / RPM · Java дохил аст", "Linux x86-64 · DEB / RPM · Java kiritilgan")
        "finish" -> listOf("Finish · Back to transactions", "Завершить · К операциям", "Аяқтау · Операцияларға", "Бүтүрүү · Операцияларга", "Анҷом · Ба амалиётҳо", "Tugatish · Amaliyotlarga")
        else -> listOf(key, key, key, key, key, key)
    }
    return words[listOf("en", "ru", "kk", "ky", "tg", "uz").indexOf(stateValues.appLanguage).coerceAtLeast(0)]
}
