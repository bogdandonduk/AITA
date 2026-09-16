package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

const val DEFAULT_NEW_ACCOUNT_APP_MODE = APP_MODE_BUYER
fun isSelectableAppMode(mode: Int?): Boolean = mode!=null && mode in setOf(APP_MODE_STORE, APP_MODE_BUYER, APP_MODE_SUPPLIER)

@Serializable data class UserAppModePreferenceDataModel(val appModeId: Int)
@Serializable data class UserAppModePreferenceResult(val accountId: String, val appModeId: Int)
@Serializable internal data class SavedAppMode(val mode: Int, val pending: Boolean = false)
internal data class AppModeOwner(val accountId: String, val generation: Long)
internal data class AppModeChoice(
    val owner: AppModeOwner? = null,
    val mode: Int = DEFAULT_NEW_ACCOUNT_APP_MODE,
    val revision: Long = 0,
    val hydrated: Boolean = false,
    val liveAccountAdopted: Boolean = false,
    val locallySelected: Boolean = false,
    val pending: Boolean = false
)

/** Null on an old account means “not migrated”, NOT “replace the old user's choice with Buyer”. */
internal fun initialAccountAppMode(serverMode: Int?, saved: SavedAppMode?, legacyOwnedMode: Int?): SavedAppMode = when {
    saved?.pending == true && isSelectableAppMode(saved.mode) -> saved
    isSelectableAppMode(serverMode) -> SavedAppMode(requireNotNull(serverMode))
    saved != null && isSelectableAppMode(saved.mode) -> saved.copy(pending = true)
    isSelectableAppMode(legacyOwnedMode) -> SavedAppMode(requireNotNull(legacyOwnedMode), pending = true)
    else -> SavedAppMode(APP_MODE_STORE, pending = true) // Preserve pre-feature business-account fallback.
}

/** Latest local intent wins over late reads/acks; persistence and network effects are injectable. */
internal class AccountAppModeCoordinator(
    private val scope: CoroutineScope,
    private val isOwnerCurrent: (AppModeOwner) -> Boolean,
    private val load: suspend (AppModeOwner) -> SavedAppMode?,
    private val persist: suspend (AppModeOwner, SavedAppMode) -> Unit,
    private val publish: (Int) -> Unit,
    private val sync: suspend (AppModeOwner, Int) -> Boolean,
    private val acknowledged: (AppModeOwner, Int) -> Unit = { _, _ -> }
) {
    private val value = MutableStateFlow(AppModeChoice())
    val snapshot: AppModeChoice get() = value.value
    private val adoption = Mutex()
    private val storage = Mutex()
    private val network = Mutex()
    private fun current(s: AppModeChoice) = value.value == s && s.owner?.let(isOwnerCurrent) != false

    private fun publishLatest() {
        // A background adoption can overlap a UI click. Never finish by projecting
        // the older captured value after the newer choice was already published.
        do {
            val observed=value.value
            if (observed.owner?.let(isOwnerCurrent)==false) return
            publish(observed.mode)
        } while (value.value!=observed)
    }

    fun select(owner: AppModeOwner?, mode: Int) {
        if (!isSelectableAppMode(mode) || owner?.let(isOwnerCurrent) == false) return
        val selected = value.updateAndGet { old -> AppModeChoice(owner,mode,old.revision+1,
            hydrated=true,liveAccountAdopted=old.owner==owner && old.liveAccountAdopted,
            locallySelected=true,pending=owner!=null) }
        if (current(selected)) publishLatest()
        retryPending()
    }

    suspend fun adopt(owner: AppModeOwner, mode: Int?, authoritative: Boolean, legacy: Int? = null) = adoption.withLock {
        if (!isOwnerCurrent(owner)) return@withLock
        val before = value.value
        if (before.owner == owner && before.hydrated && (!authoritative || before.liveAccountAdopted)) return@withLock
        val saved = load(owner)
        if (!isOwnerCurrent(owner)) return@withLock
        val now = value.value
        val choice = if (now.owner==owner && now.locallySelected) SavedAppMode(now.mode,now.pending)
            else initialAccountAppMode(mode,saved,legacy).let { resolved ->
                if(!authoritative && saved?.pending!=true) resolved.copy(pending=false) else resolved
            }
        val next = value.updateAndGet { old ->
            // A non-suspending local click may happen between storage loading and this CAS.
            if (old.owner==owner && old.locallySelected) old.copy(hydrated=true,liveAccountAdopted=authoritative || old.liveAccountAdopted)
            else AppModeChoice(owner,choice.mode,old.revision+1,true,authoritative,false,choice.pending)
        }
        if (!current(next)) return@withLock
        publishLatest()
        saveLatest()
        retryPending()
    }

    private suspend fun saveLatest() = storage.withLock {
        val selected=value.value; val owner=selected.owner ?: return@withLock
        if (current(selected) && selected.hydrated) persist(owner,SavedAppMode(selected.mode,selected.pending))
    }
    fun retryPending() { scope.launch {
        try {
            saveLatest()
            network.withLock {
                while (true) {
                    val selected=value.value; val owner=selected.owner ?: break
                    if (!selected.pending || !current(selected)) break
                    if (!sync(owner,selected.mode)) break
                    storage.withLock ack@{
                        if (!current(selected)) return@ack
                        // Only this exact owner/choice may be acknowledged; never merge stale account data.
                        persist(owner,SavedAppMode(selected.mode,false))
                        if (value.compareAndSet(selected,selected.copy(pending=false)) && isOwnerCurrent(owner))
                            acknowledged(owner,selected.mode)
                    }
                }
            }
        } catch(cancel: CancellationException) { throw cancel }
        catch(_: Exception) { /* Keep the latest pending intent for the next reconnect. */ }
    } }
}

internal object AccountAppModes {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.ourIo)
    private fun owner()=userAccountState.payloadValue?.id?.takeIf { it.isNotBlank() && getStoredUserAuthTokens?.invoke()!=null }
        ?.let { AppModeOwner(it,currentAuthenticatedSessionGeneration()) }
    private fun key(owner: AppModeOwner)="account.app-mode.v1:${owner.accountId}"
    private val coordinator=AccountAppModeCoordinator(scope,
        isOwnerCurrent={ it==owner() },
        load={ expected -> getLocalKv(key(expected))?.let { runCatching { jsonBase.decodeFromString(SavedAppMode.serializer(),it) }.getOrNull() } },
        persist={ expected, saved -> putLocalKv(key(expected),jsonBase.encodeToString(SavedAppMode.serializer(),saved)) },
        publish={ appModeState.value=it },
        sync={ expected, mode ->
            val response=networkRequest<UserAppModePreferenceResult,UserAppModePreferenceDataModel>(HttpMethod.Put,
                endpointUrl="user/app-mode",body=UserAppModePreferenceDataModel(mode),expectedSessionGeneration=expected.generation)
            !response.negative && response.payload==UserAppModePreferenceResult(expected.accountId,mode)
        },
        acknowledged={ expected, mode ->
            userAccountState.payloadValue?.takeIf { it.id==expected.accountId }?.let { account ->
                val merged=account.copy(appModeId=mode)
                userAccountState.emit(DataState.Success(merged));setStoredUserAccountDataModel?.invoke(merged)
            }
        })
    fun select(mode: Int)=coordinator.select(owner(),mode)
    fun retryPending()=coordinator.retryPending()
    fun mergeAccount(account: UserAccountDataModel): UserAccountDataModel = coordinator.snapshot.let { s ->
        if(s.owner==owner() && s.owner?.accountId==account.id && s.hydrated) account.copy(appModeId=s.mode) else account
    }
    suspend fun acceptAccount(account: UserAccountDataModel, authoritative: Boolean) {
        val expected=owner()?.takeIf { it.accountId==account.id } ?: return
        try {
            // A device-global legacy value is importable only for its previously cached owner.
            // A new account's explicit server Buyer default always outranks it.
            val cachedId=getStoredUserAccountDataModel?.invoke()?.id
            val legacy=if(cachedId==account.id && account.appModeId==null) getLocalKv(KEY_APP_MODE)?.toIntOrNull() else null
            coordinator.adopt(expected,account.appModeId,authoritative,legacy)
        } catch(cancel: CancellationException) { throw cancel }
        catch(_: Exception) { logCloudConnectionDiagnostic("App-mode restore interrupted; current choice retained") }
    }
}
