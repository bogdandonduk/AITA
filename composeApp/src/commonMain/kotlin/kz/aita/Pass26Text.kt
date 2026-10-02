package kz.aita

internal fun AppConfiguration.pass26Text(key: String): String {
    val words = when (key) {
        "copies" -> listOf("Enter 1–99 copies", "Введите количество от 1 до 99", "1–99 дана енгізіңіз", "1–99 нуска киргизиңиз", "Аз 1 то 99 нусха ворид кунед", "1–99 nusxa kiriting")
        "finish_print" -> listOf("Complete and print", "Завершить и распечатать", "Аяқтау және басып шығару", "Аяктоо жана басып чыгаруу", "Анҷом ва чоп", "Yakunlash va chop etish")
        "remove_quantity" -> listOf("Remove a quantity", "Убрать количество", "Мөлшерді азайту", "Санды азайтуу", "Кам кардани миқдор", "Miqdorni kamaytirish")
        "remove_help" -> listOf("Subtract from this batch. Confirm to save the new balance.", "Укажите, сколько убрать из партии. Нажмите «Подтвердить», чтобы сохранить новый остаток.", "Партиядан алынатын мөлшерді енгізіңіз. Жаңа қалдықты растаңыз.", "Партиядан алынуучу санды киргизиңиз. Жаңы калдыкты ырастаңыз.", "Миқдори камшавандаро ворид кунед. Бақияи навро тасдиқ кунед.", "Partiyadan olinadigan miqdorni kiriting. Yangi qoldiqni tasdiqlang.")
        "remove_error" -> listOf("Enter a positive quantity no greater than the batch balance", "Введите количество больше нуля, не превышающее остаток партии", "Нөлден үлкен, партия қалдығынан аспайтын мөлшер енгізіңіз", "Нөлдөн чоң, партия калдыгынан ашпаган сан киргизиңиз", "Миқдори мусбатро ворид кунед, ки аз бақия зиёд нест", "Noldan katta, qoldiqdan oshmaydigan miqdorni kiriting")
        "zero_quantity" -> listOf("Enter zero or a positive quantity", "Введите ноль или положительное количество", "Нөл немесе оң мөлшер енгізіңіз", "Нөл же оң сан киргизиңиз", "Сифр ё миқдори мусбат ворид кунед", "Nol yoki musbat miqdor kiriting")
        "installer" -> listOf("Installer format", "Формат установщика", "Орнатқыш пішімі", "Орноткуч форматы", "Формати насбкунанда", "O‘rnatuvchi formati")
        else -> listOf(key, key, key, key, key, key)
    }
    return words[listOf("en", "ru", "kk", "ky", "tg", "uz").indexOf(stateValues.appLanguage).coerceAtLeast(0)]
}
