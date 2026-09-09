package kz.aita.auth

/** Sign-in must accept existing passwords, not impose today's registration complexity rules. */
fun aitaPrimarySignInIsWellFormed(
    identifier: String,
    expectedKind: AitaAuthIdentifierKind,
    password: String,
    passwordRequired: Boolean
): Boolean = normalizeAitaLoginIdentifier(identifier)?.kind == expectedKind &&
    (!passwordRequired || password.isNotBlank())

fun aitaSecondFactorIsWellFormed(raw: String): Boolean {
    val code = raw.trim()
    val digits = aitaAuthCodeDigits(code)
    if (digits.length == 6 && code.all { it.digitToIntOrNull() != null || it.isWhitespace() }) return true
    val recovery = normalizeAitaRecoveryCode(code)
    return recovery.length == 12 && code.all { it.isLetterOrDigit() || it == '-' || it.isWhitespace() }
}
