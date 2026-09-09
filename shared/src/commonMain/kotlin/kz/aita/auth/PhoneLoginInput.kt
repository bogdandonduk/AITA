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

/** Compatibility keys for primary phones stored by earlier sign-up screens without '+'.
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

/** A national-number editor is not a free-form international identifier. Do not silently submit
 * a partly entered number (or the country code alone) as an unknown account to the code endpoint.
 */
fun aitaPhoneLoginFromNationalInput(raw: String, callingCode: String, nationalLength: Int): String? {
    val national = normalizeAitaPhoneFieldInput(raw, callingCode, nationalLength)
        ?.takeIf { it.length == nationalLength } ?: return null
    return normalizeAitaPhoneAlias(callingCode + national)
}

/** SQL compatibility lookup is deliberately broad; validate each stored value before trusting it.
 * In particular, stripping '+' for lookup must not turn an explicit +8 country into a +7 trunk.
 */
fun <T> aitaMatchingPhoneLoginOwners(raw: String, candidates: Iterable<Pair<String?, T>>): Set<T> {
    val canonical = normalizeAitaPhoneAlias(raw) ?: return emptySet()
    return candidates.mapNotNull { (stored, owner) ->
        owner.takeIf { stored != null && normalizeAitaPhoneAlias(stored) == canonical }
    }.toSet()
}
