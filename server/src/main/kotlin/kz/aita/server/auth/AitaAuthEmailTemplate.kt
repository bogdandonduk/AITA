package kz.aita.server.auth

internal data class AuthEmailCopy(val subject: String, val html: String, val text: String)

internal fun aitaAuthEmailCopy(purpose: String, locale: String, code: String, ttlMinutes: Long): AuthEmailCopy {
    require(code.length == 6 && code.all { it in '0'..'9' })
    val ru = locale.startsWith("ru", ignoreCase = true)
    val kk = locale.startsWith("kk", ignoreCase = true) || locale.startsWith("kz", ignoreCase = true)
    val title = when (purpose) {
        "PASSWORD_RECOVERY" -> when { ru -> "Восстановление пароля"; kk -> "Құпия сөзді қалпына келтіру"; else -> "Reset your password" }
        "EMAIL_ALIAS" -> when { ru -> "Подтвердите email для входа"; kk -> "Кіру email мекенжайын растаңыз"; else -> "Confirm your sign-in email" }
        "PHONE_ALIAS" -> when { ru -> "Подтвердите номер для входа"; kk -> "Кіру нөмірін растаңыз"; else -> "Confirm your sign-in number" }
        else -> when { ru -> "Ваш код входа"; kk -> "Кіру кодыңыз"; else -> "Your sign-in code" }
    }
    val instruction = when { ru -> "Введите код в AITA."; kk -> "Кодты AITA қолданбасына енгізіңіз."; else -> "Enter this code in AITA." }
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
    val html = """<!doctype html><html><body style="margin:0;background:#f5f5f2;color:#252830;font-family:Arial,sans-serif"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:32px 16px"><table role="presentation" width="440" style="width:100%;max-width:440px;background:#fff;border-radius:20px" cellpadding="0" cellspacing="0"><tr><td style="padding:28px"><div style="font-weight:800;font-size:28px;letter-spacing:-1px">aita<span style="color:#ffba24">.</span></div><h2 style="margin:24px 0 12px;font-size:20px">$title</h2><p style="margin:0;color:#555">$instruction</p><div style="margin:20px 0;padding:18px;background:#fff7e3;border-radius:12px;font-size:34px;font-weight:700;letter-spacing:7px;text-align:center">$code</div><p style="font-size:13px;color:#666">$expiry</p><p style="font-size:12px;color:#888">$unsolicited</p></td></tr></table></td></tr></table></body></html>"""
    return AuthEmailCopy("AITA · $title", html, text)
}
