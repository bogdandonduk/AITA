package kz.aita.auth

import kz.aita.*

internal fun aitaAuthenticationUiOwnerIsCurrent(
    capturedGeneration: Long, capturedAccountId: String?, currentGeneration: Long, currentAccountId: String?
): Boolean = capturedGeneration == currentGeneration && capturedAccountId == currentAccountId

internal fun aitaRecoveryResultBelongsToOwner(
    capturedGeneration: Long, capturedAccountId: String?, currentGeneration: Long, currentAccountId: String?, recoveredUserId: String
): Boolean = capturedAccountId != null && capturedAccountId == recoveredUserId &&
    aitaAuthenticationUiOwnerIsCurrent(capturedGeneration, capturedAccountId, currentGeneration, currentAccountId)

/** Opaque attempt owner. A -> B -> A must not make the first A's security response current. */
class AitaAuthenticationUiOwner internal constructor(private val generation: Long, private val accountId: String?) {
    fun matchesAccount(userId: String): Boolean = aitaRecoveryResultBelongsToOwner(generation, accountId,
        currentAuthenticatedSessionGeneration(), userAccountState.payloadValue?.id, userId)
    fun isCurrent(): Boolean = aitaAuthenticationUiOwnerIsCurrent(generation, accountId,
        currentAuthenticatedSessionGeneration(), userAccountState.payloadValue?.id)
}

fun captureAitaAuthenticationUiOwner(): AitaAuthenticationUiOwner =
    AitaAuthenticationUiOwner(currentAuthenticatedSessionGeneration(), userAccountState.payloadValue?.id)

/** Email recovery revokes sessions on the server. Preserve local carts/drafts/workshifts rather
 * than calling explicit logout (which can close the active shift). A local storage failure must
 * not turn a committed reset into an apparent server failure; the next request still reauthenticates.
 */
fun acknowledgeAitaAuthenticatorRecovery(owner: AitaAuthenticationUiOwner, recoveredUserId: String) {
    if (!owner.matchesAccount(recoveredUserId)) return
    val refresh = runCatching { getStoredUserAuthTokens?.invoke()?.refreshToken }.getOrNull() ?: return
    if (!owner.matchesAccount(recoveredUserId)) return
    // This existing helper also checks the currently installed refresh token before rejecting it.
    runCatching { rememberRejectedAuthRefreshToken(refresh) }
}
