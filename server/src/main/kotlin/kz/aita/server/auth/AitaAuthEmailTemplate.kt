package kz.aita.server.auth

internal data class AuthEmailCopy(val subject: String, val html: String, val text: String, val inlineImages: List<AitaInlineEmailImage> = AitaAuthEmailBranding.images)

internal fun aitaAuthEmailCopy(purpose: String, locale: String, code: String, ttlMinutes: Long): AuthEmailCopy {
    require(code.length == 6 && code.all { it in '0'..'9' })
    val ru = locale.startsWith("ru", ignoreCase = true)
    val kk = locale.startsWith("kk", ignoreCase = true) || locale.startsWith("kz", ignoreCase = true)
    if (purpose == "TOTP_RESET_NOTICE") {
        val title = when { ru -> "Аутентификатор удалён"; kk -> "Аутентификатор жойылды"; else -> "Authenticator removed" }
        val detail = when {
            ru -> "Аутентификатор AITA удалён после подтверждения пароля и кода из письма. Все сеансы входа и старые резервные коды отозваны. Войдите заново и подключите новый аутентификатор. Если это были не вы, немедленно смените пароль AITA и защитите почту."
            kk -> "AITA аутентификаторы құпия сөз бен email коды расталғаннан кейін жойылды. Барлық кіру сеанстары мен ескі резервтік кодтар қайтарып алынды. Қайта кіріп, жаңа аутентификаторды қосыңыз. Мұны сіз жасамасаңыз, AITA құпия сөзін дереу өзгертіп, поштаңызды қорғаңыз."
            else -> "Your AITA authenticator was removed after password and email-code confirmation. All sign-in sessions and old recovery codes were revoked. Sign in again and connect a new authenticator. If this was not you, change your AITA password immediately and secure your email account."
        }
        return AuthEmailCopy("AITA · $title", aitaAuthEmailLayout(title, detail, null, "", "AITA", if (ru) "ru" else if (kk) "kk" else "en"), "AITA · $title\n\n$detail")
    }
    val title = when (purpose) {
        "LOGIN_EMAIL_FACTOR" -> when { ru -> "Подтвердите вход"; kk -> "Кіруді растаңыз"; else -> "Confirm your sign-in" }
        "SECURITY_EMAIL_PROOF" -> when { ru -> "Подтвердите изменение"; kk -> "Өзгерісті растаңыз"; else -> "Confirm a security change" }
        "TOTP_RECOVERY" -> when { ru -> "Сброс аутентификатора"; kk -> "Аутентификаторды қалпына келтіру"; else -> "Reset your authenticator" }
        "PASSWORD_RECOVERY" -> when { ru -> "Восстановление пароля"; kk -> "Құпия сөзді қалпына келтіру"; else -> "Reset your password" }
        "EMAIL_ALIAS" -> when { ru -> "Подтвердите email для входа"; kk -> "Кіру email мекенжайын растаңыз"; else -> "Confirm your sign-in email" }
        "PHONE_ALIAS" -> when { ru -> "Подтвердите номер для входа"; kk -> "Кіру нөмірін растаңыз"; else -> "Confirm your sign-in number" }
        else -> when { ru -> "Ваш код входа"; kk -> "Кіру кодыңыз"; else -> "Your sign-in code" }
    }
    val instruction = if (purpose == "TOTP_RECOVERY") when {
        ru -> "Этот код удалит аутентификатор и резервные коды, а также завершит все сеансы входа. Введите его только если вы запросили сброс в AITA."
        kk -> "Бұл код аутентификатор мен резервтік кодтарды жойып, барлық кіру сеанстарын аяқтайды. Оны AITA қолданбасында қалпына келтіруді өзіңіз сұратсаңыз ғана енгізіңіз."
        else -> "This code removes your authenticator and recovery codes and ends all sign-in sessions. Enter it only if you requested this reset in AITA."
    } else if (purpose == "SECURITY_EMAIL_PROOF") when {
        ru -> "Введите код в настройках AITA, чтобы подтвердить запрошенное изменение."
        kk -> "Сұратылған өзгерісті растау үшін кодты AITA баптауларына енгізіңіз."
        else -> "Enter this code in AITA settings to confirm the change you requested."
    } else when { ru -> "Введите код в AITA."; kk -> "Кодты AITA қолданбасына енгізіңіз."; else -> "Enter this code in AITA." }
    val expiry = when {
        ru -> "Действует $ttlMinutes мин. Никому не сообщайте код."
        kk -> "$ttlMinutes минут жарамды. Кодты ешкімге бермеңіз."
        else -> "Valid for $ttlMinutes minutes. Never share this code."
    }
    val unsolicited = when {
        ru -> "Не запрашивали код? Просто проигнорируйте это письмо."
        kk -> "Кодты сұратпадыңыз ба? Бұл хатты елемеңіз."
        else -> "Did not request this? You can ignore this email."
    }
    val text = "AITA\n$title\n\n$instruction\n\n$code\n\n$expiry\n$unsolicited"
    val html = aitaAuthEmailLayout(title, instruction, code, expiry, unsolicited, if (ru) "ru" else if (kk) "kk" else "en")
    return AuthEmailCopy("AITA · $title", html, text)
}
