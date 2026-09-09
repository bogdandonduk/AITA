package kz.aita.server.auth

import kz.aita.auth.aitaPhoneLoginStorageCandidates
import kz.aita.auth.normalizeAitaPhoneAlias
import kz.aita.auth.uniqueAitaPhoneLoginOwner
import kz.aita.server.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.UUID

/** Use indexed representations produced by current/legacy AITA writers, never choose the first
 * of two owners. This is shared by password, code, recovery and legacy password routes.
 */
internal fun resolvePhoneLoginUserInside(raw: String): UUID? {
    val candidates = aitaPhoneLoginStorageCandidates(raw)
    if (candidates.isEmpty()) return null
    val primaries = Users.select(Users.id).where { Users.phoneNumber inList candidates }.limit(2).map { it[Users.id] }
    val aliases = AuthSecurityProfiles.select(AuthSecurityProfiles.userId).where {
        (AuthSecurityProfiles.phoneLoginAlias inList candidates) and AuthSecurityProfiles.phoneAliasVerifiedAtMillis.isNotNull()
    }.limit(2).map { it[AuthSecurityProfiles.userId] }
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
    val candidates = aitaPhoneLoginStorageCandidates(raw)
    if (candidates.isEmpty()) return true
    val primaries = Users.select(Users.id).where { Users.phoneNumber inList candidates }.limit(2).map { it[Users.id] }
    // A stored profile alias is reserved even if old data lacks its verification timestamp.
    val aliases = AuthSecurityProfiles.select(AuthSecurityProfiles.userId).where {
        AuthSecurityProfiles.phoneLoginAlias inList candidates
    }.limit(2).map { it[AuthSecurityProfiles.userId] }
    return (primaries + aliases).any { it != owner }
}
