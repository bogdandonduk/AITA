package kz.aita

import kotlinx.coroutines.*

/** The server response is authoritative even when this device cannot save its offline copy.
 * A cache write must never abort the rest of login or terminate a background coroutine.
 */
internal suspend fun persistAuthenticatedAccountCache(account: UserAccountDataModel,
    generation: Long = currentAuthenticatedSessionGeneration()): Boolean = withContext(Dispatchers.ourIo) {
    if (!authenticatedSessionGenerationIsCurrent(generation) || userAccountState.payloadValue?.id != account.id)
        return@withContext false
    try {
        setStoredUserAccountDataModel?.invoke(account)
        true
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) {
        RuntimeDiagnostics.capture(failure, "account.cache.write")
        logCloudConnectionDiagnostic("Account is available online; its local cache could not be saved")
        false
    }
}
