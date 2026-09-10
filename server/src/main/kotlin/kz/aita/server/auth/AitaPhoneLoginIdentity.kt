package kz.aita.server.auth

import kz.aita.auth.normalizeAitaStoredMainPhone
import kz.aita.auth.aitaMainPhoneNationalCandidate
import kz.aita.auth.aitaPhoneLoginStorageCandidates
import kz.aita.auth.aitaMatchingPhoneLoginOwners
import kz.aita.auth.normalizeAitaPhoneAlias
import kz.aita.auth.uniqueAitaPhoneLoginOwner
import kz.aita.server.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.UUID

/** Match full identifiers, including legacy display-formatted phones, never a national suffix.
 * Always inspect BOTH primary and extra-phone namespaces, even when an exact primary matches.
 * Password, email-code, recovery and identity-claim paths must agree about these owners.
 */
private fun storedPhoneLookup(column: Expression<*>): ExpressionWithColumnType<String> =
    CustomFunction<String>("translate", TextColumnType(), column,
        stringParam(AitaStoredPhoneLookup.from), stringParam(AitaStoredPhoneLookup.to))

private fun phoneLoginOwnersInside(raw: String, verifiedAliasesOnly: Boolean): Pair<Set<UUID>, Set<UUID>> {
    val digits = aitaPhoneLoginStorageCandidates(raw).map { it.removePrefix("+") }.distinct()
    if (digits.isEmpty()) return emptySet<UUID>() to emptySet()
    // No early exact-match return and no LIMIT before validation: either could hide a conflicting
    // formatted owner. Query only matching full numbers; do not copy the users table into memory.
    val national = aitaMainPhoneNationalCandidate(raw)
    val primaries = Users.select(Users.id, Users.phoneNumber, Users.countryLocale).where {
        (storedPhoneLookup(Users.phoneNumber) inList digits) or
            (national?.let { (number, locales) ->
                (storedPhoneLookup(Users.phoneNumber) eq number) and
                    (CustomFunction<String>("lower", TextColumnType(), CustomFunction<String>("btrim", TextColumnType(), Users.countryLocale)) inList locales.toList())
            } ?: Op.FALSE)
    }.map { normalizeAitaStoredMainPhone(it[Users.phoneNumber], it[Users.countryLocale]) to it[Users.id] }
    val aliases = AuthSecurityProfiles.select(AuthSecurityProfiles.userId, AuthSecurityProfiles.phoneLoginAlias).where {
        (storedPhoneLookup(AuthSecurityProfiles.phoneLoginAlias) inList digits) and
            (if (verifiedAliasesOnly) AuthSecurityProfiles.phoneAliasVerifiedAtMillis.isNotNull() else Op.TRUE)
    }.map { it[AuthSecurityProfiles.phoneLoginAlias] to it[AuthSecurityProfiles.userId] }
    return aitaMatchingPhoneLoginOwners(raw, primaries) to aitaMatchingPhoneLoginOwners(raw, aliases)
}

internal fun resolvePhoneLoginUserInside(raw: String): UUID? {
    val (primaries, aliases) = phoneLoginOwnersInside(raw, verifiedAliasesOnly = true)
    return uniqueAitaPhoneLoginOwner(primaries, aliases)
}

/** Caller must hold this transaction-scoped identity lock until a primary/verified alias is saved.
 * The interpolated value is normalized again and restricted to '+' and ASCII digits.
 */
internal fun lockPhoneLoginIdentityInside(raw: String) {
    val canonical = requireNotNull(normalizeAitaPhoneAlias(raw))
    check(canonical.startsWith('+') && canonical.drop(1).all { it in '0'..'9' })
    TransactionManager.current().exec("SELECT pg_advisory_xact_lock(hashtextextended('aita.phone:$canonical', 0))")
}

internal fun phoneLoginIdentityHasOtherOwnerInside(raw: String, owner: UUID? = null): Boolean {
    if (normalizeAitaPhoneAlias(raw) == null) return true
    // A stored extra phone is reserved even if old data lacks its verification timestamp.
    val (primaries, aliases) = phoneLoginOwnersInside(raw, verifiedAliasesOnly = false)
    return (primaries + aliases).any { it != owner }
}
