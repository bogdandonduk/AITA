package kz.aita

internal fun windowsInstallerMessage(key: String): String {
    val text = when (key) {
        "launcher" -> listOf("Cannot locate the installed AITA launcher. Close all AITA windows and run the downloaded installer manually.", "Не найден установленный AITA.exe. Закройте все окна AITA и запустите скачанный установщик вручную.", "Орнатылған AITA.exe табылмады. AITA терезелерін жауып, орнатқышты қолмен іске қосыңыз.", "Орнотулган AITA.exe табылган жок. AITA терезелерин жаап, орноткучту кол менен иштетиңиз.", "AITA.exe ёфт нашуд. Равзанаҳои AITA-ро пӯшед ва насбкунандаро дастӣ оғоз кунед.", "O‘rnatilgan AITA.exe topilmadi. AITA oynalarini yoping va o‘rnatuvchini qo‘lda ishga tushiring.")
        else -> listOf("Windows could not finish the update. Your work and installer are saved. Installation details:", "Windows не смогла завершить обновление. Данные и установщик сохранены. Подробности установки:", "Windows жаңартуды аяқтай алмады. Деректер мен орнатқыш сақталды. Орнату мәліметтері:", "Windows жаңыртууну аяктай алган жок. Маалыматтар жана орноткуч сакталды. Орнотуу маалыматы:", "Windows навсозиро анҷом дода натавонист. Маълумот ва насбкунанда нигоҳ дошта шуданд. Тафсилот:", "Windows yangilashni tugata olmadi. Ma’lumotlar va o‘rnatuvchi saqlandi. Tafsilotlar:")
    }
    return text[listOf("en", "ru", "kk", "ky", "tg", "uz").indexOf(appLanguageState.value).coerceAtLeast(0)]
}
