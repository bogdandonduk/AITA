package kz.aita

import kotlinx.serialization.Serializable
import kz.aita.auth.AitaSecurityEmailProof

@Serializable data class AccountDeletionRequest(val currentPassword: String, val secondFactorCode: String = "",
    val emailProof: AitaSecurityEmailProof? = null, val confirmed: Boolean = false)

fun accountDeletionText(key: String, language: String): String {
    val values = when (key) {
        "title" -> listOf("Delete account", "Удалить аккаунт", "Аккаунтты жою", "Аккаунтту өчүрүү", "Нест кардани ҳисоб", "Hisobni o‘chirish")
        "explain" -> listOf("Permanently remove your profile, photos, sign-in methods and personal lists in every app mode. All sessions will close. Store receipts and required business records remain. Transfer or delete owned businesses and settle balances first.",
            "Профиль, фотографии, способы входа и личные списки во всех режимах будут удалены без восстановления. Все сеансы завершатся. Чеки магазинов и необходимые деловые записи сохранятся. Сначала передайте или удалите принадлежащие вам организации и закройте остатки средств.",
            "Барлық режимдегі профиль, фотосуреттер, кіру тәсілдері және жеке тізімдер қайтарымсыз жойылады. Барлық сеанс жабылады. Дүкен чектері сақталады. Алдымен ұйымдарды беріңіз немесе жойыңыз және қаражат қалдығын жабыңыз.",
            "Бардык режимдеги профиль, сүрөттөр, кирүү ыкмалары жана жеке тизмелер кайтарымсыз өчүрүлөт. Бардык сеанстар жабылат. Дүкөн чектери сакталат. Адегенде уюмдарды өткөрүп же өчүрүп, каражат калдыгын жабыңыз.",
            "Профил, аксҳо, роҳҳои воридшавӣ ва рӯйхатҳои шахсӣ дар ҳамаи режимҳо бебозгашт нест мешаванд. Ҳамаи ҷаласаҳо баста мешаванд. Расидҳои мағоза нигоҳ дошта мешаванд. Аввал ташкилотҳоро супоред ё нест кунед ва бақияҳоро пӯшед.",
            "Barcha rejimlardagi profil, rasmlar, kirish usullari va shaxsiy ro‘yxatlar qaytarilmasdan o‘chiriladi. Barcha seanslar yopiladi. Do‘kon cheklari saqlanadi. Avval tashkilotlarni topshiring yoki o‘chiring va qoldiq mablag‘larni yoping.")
        "owned_business" -> listOf("Transfer or delete the businesses you own first.", "Сначала передайте или удалите организации, которыми владеете.", "Алдымен өз ұйымдарыңызды беріңіз не жойыңыз.", "Адегенде өз уюмдарыңызды өткөрүңүз же өчүрүңүз.", "Аввал ташкилотҳои худро супоред ё нест кунед.", "Avval tashkilotlaringizni topshiring yoki o‘chiring.")
        "balance" -> listOf("Settle the wallet balance and pending payments first.", "Сначала закройте остаток кошелька и незавершённые платежи.", "Алдымен әмиян қалдығын және аяқталмаған төлемдерді жабыңыз.", "Адегенде капчык калдыгын жана бүтпөгөн төлөмдөрдү жабыңыз.", "Аввал бақияи ҳамён ва пардохтҳои нотамомро пӯшед.", "Avval hamyon qoldig‘i va tugallanmagan to‘lovlarni yoping.")
        "workshift" -> listOf("End your open workshift first.", "Сначала завершите открытую рабочую смену.", "Алдымен жұмыс ауысымын аяқтаңыз.", "Адегенде иш нөөмөтүн бүтүрүңүз.", "Аввал басти кориро анҷом диҳед.", "Avval ish smenasini tugating.")
        "confirmation" -> listOf("Check your password and security confirmation, then retry.", "Проверьте пароль и подтверждение безопасности и повторите попытку.", "Құпия сөз бен қауіпсіздік растауын тексеріңіз.", "Сырсөздү жана коопсуздук ырастоосун текшериңиз.", "Рамз ва тасдиқи амниятро санҷед.", "Parol va xavfsizlik tasdig‘ini tekshiring.")
        "deleted" -> listOf("Account deleted", "Аккаунт удалён", "Аккаунт жойылды", "Аккаунт өчүрүлдү", "Ҳисоб нест шуд", "Hisob o‘chirildi")
        else -> listOf("Could not delete the account. Check the connection and retry.", "Не удалось удалить аккаунт. Проверьте соединение и повторите попытку.", "Аккаунт жойылмады. Байланысты тексеріп, қайталаңыз.", "Аккаунт өчүрүлгөн жок. Байланышты текшерип, кайталаңыз.", "Ҳисоб нест нашуд. Пайвастшавиро санҷида, такрор кунед.", "Hisob o‘chirilmadi. Ulanishni tekshirib, takrorlang.")
    }
    return values[listOf("en","ru","kk","ky","tg","uz").indexOf(language).coerceAtLeast(0)]
}
fun accountDeletionMessage(key: String) = listOf("en","ru","kk","ky","tg","uz").map { LocalizedStringDataModel(it,accountDeletionText(key,it)) }
