package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal data class InstallerHelper(val platform: String, val fileName: String, val command: String) {
    val url: String get() = "https://aita.kz/install/$fileName"
}

internal fun installerHelper(platform: String): InstallerHelper? = when (platform) {
    "windows" -> InstallerHelper(platform, "aita-install.ps1", "powershell.exe -NoProfile -ExecutionPolicy Bypass -File \"\$HOME\\Downloads\\aita-install.ps1\"")
    "linux" -> InstallerHelper(platform, "aita-install.py", "python3 ./aita-install.py")
    else -> null
}

internal fun installerGuideUrl(language: String) = "https://aita.kz/install/#" +
    if (canonicalLanguageCode(language) == "ru") "ru" else "en"

/** This is an explicit alternative, not an automatic execution of downloaded scripts. The browser
 * owns the save location; the helper itself verifies the release before invoking the OS installer. */
@Composable
internal fun AppConfiguration.InstallerHelperAction(platform: String) {
    val helper = installerHelper(platform) ?: return
    var expanded by remember(platform) { mutableStateOf(false) }
    actionButton(text = installerHelpText("method"), iconPath = downloadsIconPath(), iconRes = downloadsIconResource(),
        autoLoading = false, confirmationRequired = false, onClick = { expanded = true })
    if (expanded) AitaBottomSheet(title = installerHelpText("method"), iconPath = downloadsIconPath(), onDismiss = { expanded = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(installerHelpText("intro"), color = stateValues.TextColor, fontSize = stateValues.textSize)
            Text(installerHelpText(if (platform == "windows") "windows" else "linux"),
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            InstallerHelperLink(installerHelpText("download"), helper.url)
            Text(installerHelpText("command"), color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.SemiBold)
            SelectionContainer { Text(helper.command, color = stateValues.TextColor, fontSize = stateValues.smallTextSize) }
            Text(installerHelpText("instructions"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            InstallerGuideAction()
        }
    }
}

@Composable
internal fun AppConfiguration.InstallerGuideAction() =
    InstallerHelperLink(installerHelpText("guide"), installerGuideUrl(stateValues.appLanguage))

@Composable
private fun AppConfiguration.InstallerHelperLink(label: String, url: String) {
    val scope = rememberCoroutineScope()
    var opening by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var opened by remember(url) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (failed) Text(updateText("error.unavailable"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        actionButton(text = label, iconPath = downloadsIconPath(), iconRes = downloadsIconResource(),
            autoLoading = false, confirmationRequired = false, loading = opening, enabled = !opening, onClick = {
                if (!opening) {
                    opening = true; failed = false; opened = false
                    // Preserve the user gesture on browsers with strict popup/download policies.
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            opened = openExternalUrlPlatformAction?.invoke(url)?.success == true
                            failed = !opened
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { failed = true }
                        finally { opening = false }
                    }
                }
            })
        if (opened && (url.endsWith(".ps1") || url.endsWith(".py")))
            Text(installerHelpText("browser"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
    }
}

private fun AppConfiguration.installerHelpText(key: String): String {
    val locale = canonicalLanguageCode(stateValues.appLanguage)
    fun text(en: String, ru: String, kk: String, ky: String, tg: String, uz: String) = when (locale) {
        "ru" -> ru; "kk" -> kk; "ky" -> ky; "tg" -> tg; "uz" -> uz; else -> en
    }
    return when (key) {
        "method" -> text("Install or update with a script", "Установить или обновить скриптом", "Скриптпен орнату немесе жаңарту", "Скрипт менен орнотуу же жаңыртуу", "Насб ё навсозӣ бо скрипт", "Skript orqali o‘rnatish yoki yangilash")
        "intro" -> text("An alternative way to install the latest compatible AITA release. Save your work first. Your app data and offline cache are kept.", "Другой способ установить последний совместимый выпуск AITA. Сначала сохраните работу. Данные приложения и офлайн-кэш сохраняются.", "AITA-ның соңғы үйлесімді нұсқасын орнатудың басқа жолы. Алдымен жұмысты сақтаңыз. Қолданба деректері мен офлайн кэш сақталады.", "AITAнын акыркы шайкеш чыгарылышын орнотуунун башка жолу. Алгач ишиңизди сактаңыз. Колдонмонун маалыматтары жана офлайн кэш сакталат.", "Роҳи дигари насби нашри охирини мувофиқи AITA. Аввал коратонро захира кунед. Маълумоти барнома ва кэши офлайн нигоҳ дошта мешаванд.", "AITAning so‘nggi mos versiyasini o‘rnatishning boshqa usuli. Avval ishingizni saqlang. Ilova ma’lumotlari va oflayn kesh saqlanadi.")
        "windows" -> text("Windows 10/11, 64-bit. Save the script in Downloads and open Windows PowerShell. EXE is the default; the guide also explains MSI.", "Windows 10/11, 64 бита. Сохраните скрипт в «Загрузки» и откройте Windows PowerShell. По умолчанию — EXE; вариант MSI описан в инструкции.", "Windows 10/11, 64 бит. Скриптті «Жүктеулерге» сақтап, Windows PowerShell ашыңыз. Әдепкісі — EXE; MSI нұсқаулықта көрсетілген.", "Windows 10/11, 64 бит. Скриптти «Жүктөөлөргө» сактап, Windows PowerShell ачыңыз. Демейки — EXE; MSI нускамада көрсөтүлгөн.", "Windows 10/11, 64 бит. Скриптро дар Downloads захира карда, Windows PowerShell-ро кушоед. Пешфарз EXE аст; MSI дар дастур шарҳ дода шудааст.", "Windows 10/11, 64 bit. Skriptni Downloads jildiga saqlang va Windows PowerShellni oching. Odatda EXE ishlatiladi; MSI qo‘llanmada tushuntirilgan.")
        "linux" -> text("Save the script, then open its folder in a terminal. Requires Python 3 and OpenSSL. Start without sudo; the package manager asks for permission when needed.", "Сохраните скрипт и откройте его папку в терминале. Нужны Python 3 и OpenSSL. Запускайте без sudo: менеджер пакетов сам запросит разрешение при установке.", "Скриптті сақтап, оның қалтасын терминалда ашыңыз. Python 3 және OpenSSL керек. Sudo-сыз іске қосыңыз: пакет менеджері қажет кезде рұқсат сұрайды.", "Скриптти сактап, анын папкасын терминалда ачыңыз. Python 3 жана OpenSSL керек. Sudo колдонбой баштаңыз: пакет менеджери уруксатты өзү сурайт.", "Скриптро захира карда, ҷузвдонашро дар терминал кушоед. Python 3 ва OpenSSL лозиманд. Бе sudo оғоз кунед: мудири бастаҳо ҳангоми зарурат иҷозат мепурсад.", "Skriptni saqlang va uning jildini terminalda oching. Python 3 va OpenSSL kerak. Sudo ishlatmay boshlang: paket menejeri kerak bo‘lganda ruxsat so‘raydi.")
        "download" -> text("Download the script", "Скачать скрипт", "Скриптті жүктеу", "Скриптти жүктөө", "Боргирии скрипт", "Skriptni yuklab olish")
        "command" -> text("Run this command", "Выполните команду", "Осы пәрменді орындаңыз", "Бул буйрукту аткарыңыз", "Ин фармонро иҷро кунед", "Ushbu buyruqni bajaring")
        "instructions" -> text("The helper checks the release signature and installer checksum before installation. Review the version, enter y, then follow the system prompts. It never asks for your AITA password.", "Помощник проверит подпись выпуска и контрольную сумму установщика. Проверьте версию, введите y и следуйте системным подсказкам. Пароль AITA не нужен.", "Көмекші нұсқа қолтаңбасын және орнатушының бақылау сомасын тексереді. Нұсқаны қарап, y енгізіп, жүйе нұсқауларын орындаңыз. AITA құпиясөзі қажет емес.", "Жардамчы чыгарылыштын кол тамгасын жана орнотуучунун текшерүү суммасын текшерет. Нусканы карап, y киргизип, системанын көрсөтмөлөрүн аткарыңыз. AITA сырсөзү кереги жок.", "Ёвар имзои нашр ва маблағи назоратии насбкунандаро месанҷад. Нусхаро санҷед, y ворид кунед ва дастурҳои системаро иҷро кунед. Рамзи AITA лозим нест.", "Yordamchi versiya imzosi va o‘rnatuvchining nazorat summasini tekshiradi. Versiyani tekshirib, y kiriting va tizim ko‘rsatmalariga amal qiling. AITA paroli kerak emas.")
        "browser" -> text("The download was opened in your browser. Check its downloads list for the file and save location.", "Загрузка открыта в браузере. Файл и папку сохранения смотрите в списке загрузок браузера.", "Жүктеу браузерде ашылды. Файл мен сақтау қалтасын браузердің жүктеулер тізімінен қараңыз.", "Жүктөө браузерде ачылды. Файлды жана сакталган папканы браузердин жүктөөлөр тизмесинен караңыз.", "Боргирӣ дар браузер кушода шуд. Файл ва ҷузвдони захираро дар рӯйхати боргириҳои браузер бинед.", "Yuklash brauzerda ochildi. Fayl va saqlash jildini brauzer yuklamalar ro‘yxatidan tekshiring.")
        else -> text("Installation and update guide", "Установка и обновление: инструкции", "Орнату және жаңарту нұсқаулығы", "Орнотуу жана жаңыртуу нускамасы", "Дастури насб ва навсозӣ", "O‘rnatish va yangilash yo‘riqnomasi")
    }
}
