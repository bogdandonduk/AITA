package kz.aita.auth

/** The field holds only the national number; the separate country picker owns the calling code. */
fun normalizeAitaPhoneFieldInput(raw: String, callingCode: String, nationalLength: Int): String? {
    if (nationalLength <= 0) return null
    val clean = raw.trim()
    if (clean.any { it.digitToIntOrNull() == null && !it.isWhitespace() && it !in "+()-./" }) return null
    if (clean.count { it == '+' } > 1 || ('+' in clean && !clean.startsWith('+'))) return null
    val digits = aitaAuthCodeDigits(clean)
    val country = callingCode.removePrefix("+").takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }
        ?: return null
    val national = when {
        clean.startsWith('+') -> {
            if (!digits.startsWith(country) || digits.length != country.length + nationalLength) return null
            digits.drop(country.length)
        }
        digits.length == country.length + nationalLength && digits.startsWith(country) -> digits.drop(country.length)
        country == "7" && nationalLength == 10 && digits.length == 11 && digits.startsWith('8') -> digits.drop(1)
        else -> digits
    }
    return national.takeIf { it.length <= nationalLength }
}

/** Indexed compatibility lookup for primary phones stored by earlier sign-up screens without '+'.
 * Never guess a national number's country. The caller must supply a complete phone identifier.
 */
fun aitaPhoneLoginStorageCandidates(raw: String): Set<String> {
    val canonical = normalizeAitaPhoneAlias(raw) ?: return emptySet()
    val digits = canonical.removePrefix("+")
    return buildSet {
        add(canonical)
        add(digits)
        if (digits.length == 11 && digits.startsWith('7')) add("8" + digits.drop(1))
    }
}

/** Multiple representations owned by the same account are fine; two distinct owners fail closed. */
fun <T> uniqueAitaPhoneLoginOwner(primaryOwners: Iterable<T>, verifiedAliasOwners: Iterable<T>): T? =
    (primaryOwners.toSet() + verifiedAliasOwners.toSet()).singleOrNull()
