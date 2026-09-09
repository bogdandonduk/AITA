package kz.aita.server.auth

import kz.aita.auth.AitaAuthIdentifierKind
import kz.aita.auth.AitaNormalizedLoginIdentifier
import kz.aita.auth.normalizeAitaEmail

/** PostgreSQL translate() maps the same decimal characters accepted by our Kotlin input policy,
 * then deletes only its allowed separators. Invalid letters and other punctuation stay intact.
 * The complete stored value is still checked by normalizeAitaPhoneAlias after this SQL prefilter.
 */
internal object AitaStoredPhoneLookup {
    private val localizedDigits = (Char.MIN_VALUE..Char.MAX_VALUE)
        .filter { it !in '0'..'9' && it.digitToIntOrNull() != null }
    val from: String = localizedDigits.joinToString("") +
        (Char.MIN_VALUE..Char.MAX_VALUE).filter(Char::isWhitespace).joinToString("") + "+()-./"
    val to: String = localizedDigits.joinToString("") { it.digitToInt().toString() }
}

/** A phone is an account lookup key, never an email recipient. An email identifier may itself
 * be a verified extra email; a phone identifier always uses that owner's main email.
 */
internal fun aitaEmailCodeDestination(identifier: AitaNormalizedLoginIdentifier, mainEmail: String): String? =
    normalizeAitaEmail(if (identifier.kind == AitaAuthIdentifierKind.EMAIL) identifier.value else mainEmail)
