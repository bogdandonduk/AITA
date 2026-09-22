package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Device-only account picker. No password, token, factor, or authorization is stored here. */
@Serializable data class SavedLoginUser(val id: String, val name: String, val login: String)
internal fun savedLoginUser(account: UserAccountDataModel): SavedLoginUser? {
    val login = kz.aita.auth.normalizeAitaEmail(account.email)
        ?: kz.aita.auth.normalizeAitaPhoneAlias(account.phoneNumber) ?: return null
    return SavedLoginUser(account.id, listOf(account.firstName, account.lastName).filter { it.isNotBlank() }.joinToString(" ").take(200).ifBlank { login }, login)
}
object SavedLoginUsers {
    private const val key = "saved-login-users-v1"
    private val mutex = Mutex()
    private var lastSaved: SavedLoginUser? = null
    private suspend fun read(): List<SavedLoginUser> = getLocalKv(key)?.let { jsonBase.decodeFromString<List<SavedLoginUser>>(it) }.orEmpty()
    suspend fun list(): List<SavedLoginUser> = mutex.withLock {
        try { read().take(32) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { emptyList() }
    }
    suspend fun remember(account: UserAccountDataModel, generation: Long) = mutex.withLock {
        val entry = savedLoginUser(account) ?: return@withLock
        if (entry == lastSaved || !authenticatedSessionGenerationIsCurrent(generation)) return@withLock
        try {
            val entries = read()
            if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock
            if (entries.firstOrNull() != entry) putLocalKv(key, jsonBase.encodeToString((listOf(entry) + entries.filterNot { it.id == entry.id }).take(32)))
            lastSaved = entry
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* Identity convenience cannot prevent sign-in. */ }
    }
    suspend fun forget(id: String) = mutex.withLock {
        putLocalKv(key, jsonBase.encodeToString(read().filterNot { it.id == id }))
        if (lastSaved?.id == id) lastSaved = null
    }
}
