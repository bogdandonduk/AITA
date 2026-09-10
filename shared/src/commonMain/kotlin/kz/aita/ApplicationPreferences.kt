package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val appAppearancePreferencesMutable = MutableStateFlow(UserPreferencesDataModel())
/** Atomic UI choice; the older individual flows remain for non-Compose integrations. */
val appAppearancePreferencesState: StateFlow<UserPreferencesDataModel> = appAppearancePreferencesMutable.asStateFlow()

internal object AppPreferences {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.ourIo + CoroutineExceptionHandler { _, error ->
        if (error !is Exception) throw error
        // Never log preference request bodies, account identities or server responses here.
        // Pending choices stay in memory/the journal and can be retried on reconnect.
        logCloudConnectionDiagnostic("Preference persistence/sync interrupted; pending selection retained")
    })
    private val intent = MutableStateFlow(AppPreferenceIntent())
    private val storageMutex = Mutex()
    private val accountMutex = Mutex()
    private var adoptedSession: Pair<String, Long>? = null
    private val dirty = MutableStateFlow<Map<String, AppPreferenceIntent>>(emptyMap())
    val revision: Long get() = intent.value.revision
    private fun journalKey(owner: String) = "app.preferences.pending.v2:$owner"
    private fun owner(): String? = if (getStoredUserAuthTokens?.invoke() == null) null else userAccountState.payloadValue?.id
    private fun ownedBy(id: String, generation: Long) = owner() == id && authenticatedSessionGenerationIsCurrent(generation)

    private fun publish() {
        // Callers normally run on the UI thread. Retrying the projection also prevents a
        // concurrent hydration from finishing with an older value after a user's click.
        do {
            val current = intent.value
            appAppearancePreferencesMutable.value = current.value
            appLanguageState.value = current.value.appLanguage
            appThemeIdState.value = current.value.appThemeId
            appSizeModeIdState.value = current.value.appSizeModeId
        } while (intent.value != current)
    }

    fun select(language: String? = null, theme: Long? = null, scale: Long? = null, sync: Boolean = true) {
        val selected = intent.updateAndGet { current ->
            AppPreferenceIntent(current.value.copy(
                appLanguage = language?.let(::normalizeAppLanguagePreference) ?: current.value.appLanguage,
                appThemeId = theme?.let(::normalizeAppThemePreference) ?: current.value.appThemeId,
                appSizeModeId = scale?.let(::normalizeAppSizeModePreference) ?: current.value.appSizeModeId
            ), current.revision + 1, isLocalSelection = true)
        }
        publish() // No launch/IO/delay before the visible choice.
        val id = if (sync) owner() else null
        if (id != null) dirty.updateAndGet { it + (id to selected) }
        scope.launch {
            delay(120)
            persistLatest()
            if (id != null) syncLatest(postFailure = false)
        }
    }

    /** Local database emissions are not a command bus. Hydrate once; never echo writes into UI. */
    suspend fun hydrate() {
        try { hydrateOnce() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { logCloudConnectionDiagnostic("Preference hydration unavailable; current selection retained") }
    }

    private suspend fun hydrateOnce() {
        val expected = intent.value
        val local = UserPreferencesDataModel(
            normalizeAppLanguagePreference(getLocalKv(KEY_APP_LOCALE)),
            normalizeAppThemePreference(getLocalKv(KEY_APP_THEME)?.toLongOrNull()),
            normalizeAppSizeModePreference(getLocalKv(KEY_APP_SIZE_MODE)?.toLongOrNull())
        )
        withContext(Dispatchers.Main) {
            if (expected.revision == 0L && intent.compareAndSet(expected, AppPreferenceIntent(local, 1L))) publish()
        }
    }

    private suspend fun persistLatest() = storageMutex.withLock {
        val current = intent.value
        putLocalKv(KEY_APP_LOCALE, current.value.appLanguage)
        putLocalKv(KEY_APP_THEME, current.value.appThemeId.toString())
        putLocalKv(KEY_APP_SIZE_MODE, current.value.appSizeModeId.toString())
        // Account-scoped journal survives reconnect/relaunch, without leaking another user's choices.
        dirty.value.forEach { (id, pending) -> putLocalKv(journalKey(id), jsonBase.encodeToString(UserPreferencesDataModel.serializer(), pending.value)) }
    }

    suspend fun acceptAccount(account: UserAccountDataModel, requestRevision: Long) {
        // Capture before waiting for another adoption; an A -> B -> A login is a new owner.
        val generation = currentAuthenticatedSessionGeneration()
        try { adoptAccount(account, requestRevision, generation) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { logCloudConnectionDiagnostic("Account preference persistence unavailable; current selection retained") }
    }

    private suspend fun adoptAccount(account: UserAccountDataModel, requestRevision: Long, generation: Long) = accountMutex.withLock {
        val session = account.id to generation
        if (!ownedBy(account.id, generation) || adoptedSession == session) return@withLock
        val saved = getLocalKv(journalKey(account.id))?.let {
            runCatching { jsonBase.decodeFromString(UserPreferencesDataModel.serializer(), it) }.getOrNull()
        }
        withContext(Dispatchers.Main) {
            if (!ownedBy(account.id, generation) || adoptedSession == session) return@withContext
            val override = authScreenPreferenceOverrideState.value
            val decision = resolveAppPreferenceChoice(
                current = intent.value,
                account = UserPreferencesDataModel(account.appLanguage, account.appThemeId, account.appSizeModeId),
                requestRevision = requestRevision,
                pending = dirty.value[account.id]?.value ?: saved,
                override = override
            )
            val next = intent.updateAndGet { AppPreferenceIntent(decision.value, it.revision + 1L) }
            adoptedSession = session
            publish()
            if (decision.shouldSync) dirty.updateAndGet { it + (account.id to next) }
            authScreenPreferenceOverrideState.compareAndSet(override, AuthScreenPreferenceOverrideDataModel())
        }
        persistLatest()
        retryPending()
    }

    fun retryPending(postFailure: Boolean = false) {
        scope.launch { persistLatest(); syncLatest(postFailure) }
    }

    private suspend fun syncLatest(postFailure: Boolean) = updateUserPreferencesMutex.withLock {
        val id = owner() ?: return@withLock
        val generation = currentAuthenticatedSessionGeneration()
        val selected = dirty.value[id] ?: return@withLock
        if (!ownedBy(id, generation)) return@withLock
        val response = networkRequest<UserAccountDataModel, UserPreferencesDataModel>(
            method = HttpMethod.Put,
            endpointUrl = globalAppConfigurationState.payloadValue.updateUserPreferencesPath.first,
            body = selected.value,
            expectedSessionGeneration = generation
        )
        if (!ownedBy(id, generation) || dirty.value[id] != selected) return@withLock
        if (response.negative || response.payload == null) {
            if (postFailure) postInAppNotification(response.message, NotificationType.Neutral, transient = true)
            return@withLock // Keep the journal; reconnect retries the latest choice, never replays it in UI.
        }
        storageMutex.withLock persistAck@{
            if (!ownedBy(id, generation) || dirty.value[id] != selected) return@persistAck
            val account = userAccountState.payloadValue?.takeIf { it.id == id } ?: return@persistAck
            // Merge only preferences. A preference response must not roll back newer store/profile data.
            val merged = account.copy(appLanguage = selected.value.appLanguage,
                appThemeId = selected.value.appThemeId, appSizeModeId = selected.value.appSizeModeId)
            userAccountState.emit(DataState.Success(merged))
            setStoredUserAccountDataModel?.invoke(merged)
            putLocalKv(journalKey(id), null)
            dirty.updateAndGet { map -> if (map[id] == selected) map - id else map }
        }
    }
}
