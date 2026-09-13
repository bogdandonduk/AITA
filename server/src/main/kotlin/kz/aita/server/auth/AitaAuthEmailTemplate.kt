package kz.aita.server.auth

internal data class AuthEmailCopy(val subject: String, val html: String, val text: String, val inlineImages: List<AitaInlineEmailImage> = AitaAuthEmailBranding.images)

internal fun aitaAuthEmailCopy(purpose: String, locale: String, code: String, ttlMinutes: Long): AuthEmailCopy {
    require(code.length == 6 && code.all { it in '0'..'9' })
    val emailLanguage = kz.aita.normalizeAuthEmailLocale(locale)
    val ru = emailLanguage == "ru"
    val kk = emailLanguage == "kk"
    val tg = emailLanguage == "tg"
    val ky = emailLanguage == "ky"
    val uz = emailLanguage == "uz"
    if (purpose == "TOTP_RESET_NOTICE") {
        val title = when { ru -> "Аутентификатор удалён"; kk -> "Аутентификатор жойылды"; tg -> "Аутентификатор нест карда шуд"; ky -> "Аутентификатор өчүрүлдү"; uz -> "Autentifikator oʻchirildi"; else -> "Authenticator removed" }
        val detail = when {
            ru -> "Аутентификатор AITA удалён после подтверждения пароля и кода из письма. Все сеансы входа и старые резервные коды отозваны. Войдите заново и подключите новый аутентификатор. Если это были не вы, немедленно смените пароль AITA и защитите почту."
            kk -> "AITA аутентификаторы құпия сөз бен email коды расталғаннан кейін жойылды. Барлық кіру сеанстары мен ескі резервтік кодтар қайтарып алынды. Қайта кіріп, жаңа аутентификаторды қосыңыз. Мұны сіз жасамасаңыз, AITA құпия сөзін дереу өзгертіп, поштаңызды қорғаңыз."
            tg -> "Аутентификатори AITA-и шумо пас аз тасдиқи гузарвожа ва коди почтаи электронӣ нест карда шуд. Ҳамаи сеансҳои воридшавӣ ва кодҳои кӯҳнаи барқарорсозӣ бекор карда шуданд. Дубора ворид шуда, аутентификатори навро пайваст кунед. Агар ин корро шумо накарда бошед, фавран гузарвожаи AITA-ро иваз кунед ва ҳисоби почтаи электронии худро ҳифз намоед."
            ky -> "Сырсөз жана электрондук каттагы код ырасталгандан кийин AITA аутентификаторуңуз өчүрүлдү. Бардык кирүү сеанстары жана эски калыбына келтирүү коддору жокко чыгарылды. Кайра кирип, жаңы аутентификаторду туташтырыңыз. Муну сиз жасабасаңыз, AITA сырсөзүңүздү дароо өзгөртүп, электрондук почтаңызды коргоңуз."
            uz -> "AITA autentifikatoringiz parol va elektron pochta kodi tasdiqlangandan keyin oʻchirildi. Barcha kirish seanslari va eski tiklash kodlari bekor qilindi. Qayta kiring va yangi autentifikatorni ulang. Agar buni siz qilmagan boʻlsangiz, AITA parolingizni darhol oʻzgartiring va elektron pochta hisobingizni himoyalang."
            else -> "Your AITA authenticator was removed after password and email-code confirmation. All sign-in sessions and old recovery codes were revoked. Sign in again and connect a new authenticator. If this was not you, change your AITA password immediately and secure your email account."
        }
        return AuthEmailCopy("AITA · $title", aitaAuthEmailLayout(title, detail, null, "", "AITA", emailLanguage), "AITA · $title\n\n$detail")
    }
    val title = when (purpose) {
        "LOGIN_EMAIL_FACTOR" -> when { ru -> "Подтвердите вход"; kk -> "Кіруді растаңыз"; tg -> "Воридшавии худро тасдиқ кунед"; ky -> "Кирүүңүздү ырастаңыз"; uz -> "Kirishni tasdiqlang"; else -> "Confirm your sign-in" }
        "SECURITY_EMAIL_PROOF" -> when { ru -> "Подтвердите изменение"; kk -> "Өзгерісті растаңыз"; tg -> "Тағйири амниятиро тасдиқ кунед"; ky -> "Коопсуздук өзгөртүүсүн ырастаңыз"; uz -> "Xavfsizlik oʻzgarishini tasdiqlang"; else -> "Confirm a security change" }
        "TOTP_RECOVERY" -> when { ru -> "Сброс аутентификатора"; kk -> "Аутентификаторды қалпына келтіру"; tg -> "Аз нав танзим кардани аутентификатор"; ky -> "Аутентификаторду баштапкы абалга келтирүү"; uz -> "Autentifikatorni qayta sozlash"; else -> "Reset your authenticator" }
        "PASSWORD_RECOVERY" -> when { ru -> "Восстановление пароля"; kk -> "Құпия сөзді қалпына келтіру"; tg -> "Барқарор кардани гузарвожа"; ky -> "Сырсөздү калыбына келтирүү"; uz -> "Parolni tiklash"; else -> "Reset your password" }
        "EMAIL_ALIAS" -> when { ru -> "Подтвердите email для входа"; kk -> "Кіру email мекенжайын растаңыз"; tg -> "Почтаи электронии воридшавиро тасдиқ кунед"; ky -> "Кирүү үчүн электрондук почтаңызды ырастаңыз"; uz -> "Kirish uchun elektron pochtangizni tasdiqlang"; else -> "Confirm your sign-in email" }
        "PHONE_ALIAS" -> when { ru -> "Подтвердите номер для входа"; kk -> "Кіру нөмірін растаңыз"; tg -> "Рақами воридшавиро тасдиқ кунед"; ky -> "Кирүү үчүн номериңизди ырастаңыз"; uz -> "Kirish uchun raqamingizni tasdiqlang"; else -> "Confirm your sign-in number" }
        else -> when { ru -> "Ваш код входа"; kk -> "Кіру кодыңыз"; tg -> "Коди воридшавии шумо"; ky -> "Кирүү кодуңуз"; uz -> "Kirish kodingiz"; else -> "Your sign-in code" }
    }
    val instruction = if (purpose == "TOTP_RECOVERY") when {
        ru -> "Этот код удалит аутентификатор и резервные коды, а также завершит все сеансы входа. Введите его только если вы запросили сброс в AITA."
        kk -> "Бұл код аутентификатор мен резервтік кодтарды жойып, барлық кіру сеанстарын аяқтайды. Оны AITA қолданбасында қалпына келтіруді өзіңіз сұратсаңыз ғана енгізіңіз."
        tg -> "Ин код аутентификатор ва кодҳои барқарорсозиро нест карда, ҳамаи сеансҳои воридшавиро анҷом медиҳад. Онро танҳо дар сурате ворид кунед, ки азнавтанзимкуниро дар AITA худатон дархост карда бошед."
        ky -> "Бул код аутентификаторуңузду жана калыбына келтирүү коддорун өчүрүп, бардык кирүү сеанстарын аяктатат. Аны AITA колдонмосунда баштапкы абалга келтирүүнү өзүңүз сурансаңыз гана киргизиңиз."
        uz -> "Bu kod autentifikatoringiz va tiklash kodlarini oʻchiradi hamda barcha kirish seanslarini tugatadi. Uni faqat AITA da ushbu qayta sozlashni oʻzingiz soʻragan boʻlsangiz kiriting."
        else -> "This code removes your authenticator and recovery codes and ends all sign-in sessions. Enter it only if you requested this reset in AITA."
    } else if (purpose == "SECURITY_EMAIL_PROOF") when {
        ru -> "Введите код в настройках AITA, чтобы подтвердить запрошенное изменение."
        kk -> "Сұратылған өзгерісті растау үшін кодты AITA баптауларына енгізіңіз."
        tg -> "Барои тасдиқи тағйире, ки худатон дархост кардед, ин кодро дар танзимоти AITA ворид кунед."
        ky -> "Сиз суранган өзгөртүүнү ырастоо үчүн бул кодду AITA жөндөөлөрүнө киргизиңиз."
        uz -> "Oʻzingiz soʻragan oʻzgarishni tasdiqlash uchun bu kodni AITA sozlamalariga kiriting."
        else -> "Enter this code in AITA settings to confirm the change you requested."
    } else when { ru -> "Введите код в AITA."; kk -> "Кодты AITA қолданбасына енгізіңіз."; tg -> "Ин кодро дар AITA ворид кунед."; ky -> "Бул кодду AITA колдонмосуна киргизиңиз."; uz -> "Bu kodni AITA ga kiriting."; else -> "Enter this code in AITA." }
    val expiry = when {
        ru -> "Действует $ttlMinutes мин. Никому не сообщайте код."
        kk -> "$ttlMinutes минут жарамды. Кодты ешкімге бермеңіз."
        tg -> "Дар давоми $ttlMinutes дақиқа эътибор дорад. Ин кодро ҳеҷ гоҳ ба касе надиҳед."
        ky -> "$ttlMinutes мүнөт жарактуу. Бул кодду эч кимге бербеңиз."
        uz -> "$ttlMinutes daqiqa amal qiladi. Bu kodni hech kimga bermang."
        else -> "Valid for $ttlMinutes minutes. Never share this code."
    }
    val unsolicited = when {
        ru -> "Не запрашивали код? Просто проигнорируйте это письмо."
        kk -> "Кодты сұратпадыңыз ба? Бұл хатты елемеңіз."
        tg -> "Инро дархост накардед? Метавонед ин паёмро нодида гиред."
        ky -> "Муну сураган жоксузбу? Бул катты этибарга албасаңыз болот."
        uz -> "Buni soʻramaganmidingiz? Ushbu xatga eʼtibor bermasligingiz mumkin."
        else -> "Did not request this? You can ignore this email."
    }
    val text = "AITA\n$title\n\n$instruction\n\n$code\n\n$expiry\n$unsolicited"
    val html = aitaAuthEmailLayout(title, instruction, code, expiry, unsolicited, emailLanguage)
    return AuthEmailCopy("AITA · $title", html, text)
}
