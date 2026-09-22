package kz.aita

internal fun AppConfiguration.savedLoginText(key: String): String {
    val values = when (key) {
        "tab" -> listOf("Saved users", "Сохранённые пользователи", "Сақталған пайдаланушылар", "Сакталган колдонуучулар", "Корбарони сабтшуда", "Saqlangan foydalanuvchilar")
        "select" -> listOf("Select user", "Выберите пользователя", "Пайдаланушыны таңдаңыз", "Колдонуучуну тандаңыз", "Корбарро интихоб кунед", "Foydalanuvchini tanlang")
        else -> listOf("Forget saved user", "Убрать сохранённого пользователя", "Сақталған пайдаланушыны өшіру", "Сакталган колдонуучуну өчүрүү", "Корбари сабтшударо фаромӯш кунед", "Saqlangan foydalanuvchini unutish")
    }
    return values[listOf("en", "ru", "kk", "ky", "tg", "uz").indexOf(effectiveAppLanguage(stateValues.appLanguage)).coerceAtLeast(0)]
}
