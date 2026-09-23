package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString


private val subscriptionReadMutex = Mutex()
private val subscriptionPublicationMutex = Mutex()
private val subscriptionWriteMutex = Mutex()
// A failed disk write must not restore an old positive proof in this process.
private val deniedSubscriptionKeys = MutableStateFlow<Set<String>>(emptySet())
private val verifiedSubscriptions = MutableStateFlow<Map<String, VerifiedStoreSubscription>>(emptyMap())
private val subscriptionAccessRevision = MutableStateFlow(0L)
val subscriptionAccessRevisionState = subscriptionAccessRevision.asStateFlow()
val currentSubscriptionDashboardState = MutableDataStateFlow<SubscriptionDashboardDataModel>(GlobalScope)
val subscriptionLoadingStoreIdState = MutableStateFlow<String?>(null)
val pendingSubscriptionCommandIdState = MutableStateFlow<String?>(null)
private fun pendingSubscriptionKey(account: String, store: String) = "subscription-pending-v1:$account:$store"
val subscriptionLoadFailureState = MutableStateFlow<List<LocalizedStringDataModel>?>(null)
private fun subscriptionCacheKey(account: String, store: String) = "subscription-proof-v1:$account:$store"
private fun currentSubscriptionAccountId(): String? = userAccountState.payloadValue?.id
    ?.takeIf { it.isNotBlank() && getStoredUserAuthTokens?.invoke() != null }

fun currentStoreSubscriptionGate(storeId: String?, now: Long = getCurrentTimeMillis()): StoreSubscriptionGate {
    if (currentStoreModel(storeId)?.isManagementStore() == true) return StoreSubscriptionGate.Required
    val account = currentSubscriptionAccountId()
    val key = if (account != null && !storeId.isNullOrBlank()) subscriptionCacheKey(account, storeId) else null
    return resolveStoreSubscriptionGate(account, storeId, key?.let { verifiedSubscriptions.value[it] },
        key != null && key in deniedSubscriptionKeys.value, now)
}
fun currentStoreHasSubscriptionAccess(storeId: String?, now: Long = getCurrentTimeMillis()): Boolean =
    currentStoreSubscriptionGate(storeId, now) == StoreSubscriptionGate.Active

internal fun clearStoreSubscriptionRuntime() {
    verifiedSubscriptions.value = emptyMap()
    deniedSubscriptionKeys.value = emptySet()
    currentSubscriptionDashboardState.emit(DataState.Empty())
    subscriptionLoadingStoreIdState.value = null
    pendingSubscriptionCommandIdState.value = null
    subscriptionLoadFailureState.value = null
    subscriptionAccessRevision.update { it + 1L }
}

internal fun selectStoreSubscriptionScope(storeId: String?) {
    // Expose no previous branch's wallet/charges while the new branch's dashboard is loading.
    currentSubscriptionDashboardState.emit(DataState.Empty())
    activeStoreSubscriptionState.emit(DataState.Empty())
    activeStoreSubscriptionChargesState.emit(DataState.Empty())
    subscriptionPlansState.emit(DataState.Empty())
    subscriptionLoadFailureState.value = null
    pendingSubscriptionCommandIdState.value = null
    subscriptionLoadingStoreIdState.value = storeId?.takeUnless { currentStoreModel(it)?.isManagementStore() == true }
    subscriptionAccessRevision.update { it + 1L }
}

internal suspend fun restoreStoreSubscriptionCache(storeId: String) {
    if (currentStoreModel(storeId)?.isManagementStore() == true) return
    val account = currentSubscriptionAccountId() ?: return
    val generation = currentAuthenticatedSessionGeneration()
    val key = subscriptionCacheKey(account, storeId)
    subscriptionPublicationMutex.withLock {
        if (key in verifiedSubscriptions.value || key in deniedSubscriptionKeys.value) return@withLock
        try {
            val cached = getLocalKv(key)?.let { jsonBase.decodeFromString<VerifiedStoreSubscription>(it) } ?: return@withLock
            if (currentSubscriptionAccountId() != account || !authenticatedSessionGenerationIsCurrent(generation) ||
                cached.accountId != account || cached.subscription.storeId != storeId) return@withLock
            verifiedSubscriptions.update { if (key in it) it else it + (key to cached) }
            subscriptionAccessRevision.update { it + 1L }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A corrupt/absent cache never grants access. Verify online. */ }
    }
}

private suspend fun acceptSubscriptionDashboard(account: String, generation: Long,
    owner: InventoryOwner, dashboard: SubscriptionDashboardDataModel) = subscriptionPublicationMutex.withLock {
    if (currentSubscriptionAccountId() != account || !authenticatedSessionGenerationIsCurrent(generation)) return@withLock
    val subscription = dashboard.subscription
    val store = subscription.storeId
    val key = subscriptionCacheKey(account, store)
    val proof = VerifiedStoreSubscription(account, subscription, getCurrentTimeMillis(), dashboard.serverTimeMillis)
    // Old backends do not provide the location-scoped schema or server verification clock.
    if (dashboard.serverTimeMillis <= 0L) return@withLock
    val old = verifiedSubscriptions.value[key]
    if (old != null && (old.subscription.revision > subscription.revision ||
        (old.subscription.revision == subscription.revision && old.verifiedAtServerMillis > dashboard.serverTimeMillis))) return@withLock
    verifiedSubscriptions.update { it + (key to proof) }
    deniedSubscriptionKeys.update { it - key }
    inventoryStateMutex.withLock {
        if (inventoryOwnerIsCurrent(owner) && activeStoreIdState.value == store) {
            currentSubscriptionDashboardState.emit(DataState.Success(dashboard))
            activeStoreSubscriptionState.emit(DataState.Success(subscription))
            activeStoreSubscriptionChargesState.emit(DataState.Success(dashboard.charges))
            subscriptionPlansState.emit(DataState.Success(dashboard.plans))
            subscriptionLoadFailureState.value = null
        }
    }
    subscriptionAccessRevision.update { it + 1L }
    try { putLocalKv(key, jsonBase.encodeToString(proof)) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { logCloudConnectionDiagnostic("Subscription offline cache could not be saved; server entitlement is unchanged") }
}

/** Used after an authoritative 402/403. Does not erase inventory, drafts, or queued operations. */
suspend fun invalidateStoreSubscriptionAccess(storeId: String, observedBeforeMillis: Long = Long.MAX_VALUE,
    expectedAccountId: String? = userAccountState.payloadValue?.id,
    expectedGeneration: Long = currentAuthenticatedSessionGeneration()) {
    val account = expectedAccountId ?: return
    val generation = expectedGeneration
    val key = subscriptionCacheKey(account, storeId)
    subscriptionPublicationMutex.withLock {
        if (currentSubscriptionAccountId() != account || !authenticatedSessionGenerationIsCurrent(generation)) return@withLock
        // A delayed pre-purchase 402 must not revoke a newer successful activation in memory.
        if ((verifiedSubscriptions.value[key]?.verifiedAtLocalMillis ?: 0L) > observedBeforeMillis) return@withLock
        deniedSubscriptionKeys.update { it + key }
        verifiedSubscriptions.update { it - key }
        subscriptionAccessRevision.update { it + 1L }
        if (activeStoreIdState.value == storeId) {
            currentSubscriptionDashboardState.emit(DataState.Empty(eventMessage("subscription.required")))
            activeStoreSubscriptionState.emit(DataState.Empty(eventMessage("subscription.required")))
        }
        try { putLocalKv(key, null) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { logCloudConnectionDiagnostic("Subscription revocation cache update failed") }
    }
}

suspend fun refreshStoreSubscriptionNow(storeId: String, onlyIfUnknown: Boolean = false): ResponseDataModel<SubscriptionDashboardDataModel> {
    val account = currentSubscriptionAccountId() ?: return cloudSessionExpiredResponse()
    if (currentStoreModel(storeId)?.isManagementStore() == true) return ResponseDataModel(null, null, false)
    val generation = currentAuthenticatedSessionGeneration()
    val owner = inventoryOwners.current
    return subscriptionReadMutex.withLock {
        if (!authenticatedSessionGenerationIsCurrent(generation) || currentSubscriptionAccountId() != account)
            return@withLock cloudSessionExpiredResponse()
        if (onlyIfUnknown && currentStoreHasSubscriptionAccess(storeId))
            return@withLock ResponseDataModel(null, null, false)
        if (inventoryOwnerIsCurrent(owner) && activeStoreIdState.value == storeId) subscriptionLoadingStoreIdState.value = storeId
        try {
            val response = networkRequest<SubscriptionDashboardDataModel, Unit>(
                HttpMethod.Get, endpointUrl = globalAppConfigurationState.payloadValue.getStoreSubscriptionPath.first,
                headers = mapOf("store_id" to storeId), expectedSessionGeneration = generation)
            if (!authenticatedSessionGenerationIsCurrent(generation) || currentSubscriptionAccountId() != account)
                return@withLock cloudSessionExpiredResponse()
            val dashboard = response.payload
            if (!response.negative && dashboard?.subscription?.storeId == storeId) {
                acceptSubscriptionDashboard(account, generation, owner, dashboard)
            } else {
                if (response.httpStatusCode == 403) invalidateStoreSubscriptionAccess(storeId)
                if (inventoryOwnerIsCurrent(owner) && activeStoreIdState.value == storeId)
                    subscriptionLoadFailureState.value = response.message ?: eventMessage("subscription.verify")
            }
            response
        } finally {
            if (inventoryOwnerIsCurrent(owner) && subscriptionLoadingStoreIdState.value == storeId)
                subscriptionLoadingStoreIdState.value = null
        }
    }
}

suspend fun quoteStoreSubscriptionNow(request: StoreSubscriptionQuoteRequestDataModel): ResponseDataModel<StoreSubscriptionQuoteDataModel> =
    networkRequest(HttpMethod.Post, endpointUrl = "subscriptions/store/quote", body = request,
        headers = mapOf("store_id" to request.storeId), expectedSessionGeneration = currentAuthenticatedSessionGeneration())

suspend fun submitStoreSubscriptionNow(request: StoreSubscriptionUpdateRequestDataModel): ResponseDataModel<SubscriptionDashboardDataModel> {
    val account = currentSubscriptionAccountId() ?: return cloudSessionExpiredResponse()
    val generation = currentAuthenticatedSessionGeneration()
    val owner = inventoryOwners.current
    return subscriptionWriteMutex.withLock {
        if (!authenticatedSessionGenerationIsCurrent(generation) || currentSubscriptionAccountId() != account)
            return@withLock cloudSessionExpiredResponse()
        val pendingKey = pendingSubscriptionKey(account, request.storeId)
        try {
            val previous = getLocalKv(pendingKey)
            if (previous != null && previous != request.commandId)
                return@withLock ResponseDataModel(eventMessage("subscription.result_unknown"), null, true, 409)
            putLocalKv(pendingKey, request.commandId)
            if (getLocalKv(pendingKey) != request.commandId)
                return@withLock ResponseDataModel(eventMessage("subscription.journal_failed"), null, true)
            if (inventoryOwnerIsCurrent(owner) && activeStoreIdState.value == request.storeId) pendingSubscriptionCommandIdState.value = request.commandId
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock ResponseDataModel(eventMessage("subscription.journal_failed"), null, true) }
        val response = networkRequest<SubscriptionDashboardDataModel, StoreSubscriptionUpdateRequestDataModel>(
            HttpMethod.Post, endpointUrl = globalAppConfigurationState.payloadValue.updateStoreSubscriptionPath.first,
            body = request, headers = mapOf("store_id" to request.storeId), expectedSessionGeneration = generation)
        val dashboard = response.payload
        if ((!response.negative || response.httpStatusCode in setOf(400, 404, 422)) && !response.transportFailure) {
            try {
                if (getLocalKv(pendingKey) == request.commandId) putLocalKv(pendingKey, null)
                if (inventoryOwnerIsCurrent(owner) && activeStoreIdState.value == request.storeId) pendingSubscriptionCommandIdState.value = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The result can be recovered by command ID on next open. */ }
        }
        if (!response.negative && dashboard?.subscription?.storeId == request.storeId) {
            acceptSubscriptionDashboard(account, generation, owner, dashboard)
            // The wallet belongs to the billing owner, not necessarily the worker who submitted.
            if (dashboard.billingWallet?.userId == account) getUserFinanceDashboard()
            if (inventoryOwnerIsCurrent(owner) && activeStoreIdState.value == request.storeId) {
                getStock(request.storeId)
                getStockBatches(request.storeId)
            }
        }
        response
    }
}

internal fun ResponseDataModel<SubscriptionDashboardDataModel>.toSubscriptionDataState(): DataState<SubscriptionDashboardDataModel> {
    val value = payload
    return if (!negative && value != null) DataState.Success(value, message) else DataState.Empty(message)
}

fun subscriptionRequestStoreId(endpoint: String, headers: Map<String, String>, query: Map<String, Any?>): String? {
    if (!storeSubscriptionRequiredForEndpoint(endpoint)) return null
    return headers.entries.firstOrNull { it.key.equals("store_id", true) || it.key.equals("store-id", true) }?.value
        ?: query["storeId"]?.toString() ?: activeStoreIdState.value
}

/** Local suppression reduces unauthorized business traffic; the backend remains the authority. */
suspend fun checkStoreSubscriptionForNetwork(endpoint: String, storeId: String?): List<LocalizedStringDataModel>? {
    if (!storeSubscriptionRequiredForEndpoint(endpoint)) return null
    val path = endpoint.trim('/').lowercase()
    // The joining user's current location is not the target of these relationship commands.
    if (path in setOf("workers/request", "workers/invitations/accept")) return null
    val store = storeId ?: return eventMessage("subscription.verify")
    if (currentStoreModel(store)?.isManagementStore() == true) {
        if (storeEndpointRequiresOperatingBranch(path)) return eventMessage("store.operating_branch_required")
        return if (currentStoreHasWorkspaceAccess(store)) null else eventMessage("subscription.verify")
    }
    if (currentStoreHasSubscriptionAccess(store)) return null
    currentSubscriptionAccountId() ?: return null // Network authentication returns the actual session failure.
    restoreStoreSubscriptionCache(store)
    if (currentStoreHasSubscriptionAccess(store)) return null
    // A previous denial, expired proof or clock adjustment must recover after reconnect/renewal.
    // The read mutex coalesces concurrent successful refreshes for the same store.
    refreshStoreSubscriptionNow(store, onlyIfUnknown = true)
    return when (currentStoreSubscriptionGate(store)) {
        StoreSubscriptionGate.Active -> null
        StoreSubscriptionGate.Required -> eventMessage("subscription.required")
        // Absence of a local proof is not an authoritative 402. Let this network request
        // reach the server, which verifies the store subscription itself. Offline writes
        // still require a valid cached proof; a failed network call never grants one.
        StoreSubscriptionGate.Checking -> null
    }
}

/** Recovers a lost success response without persisting/replaying a secret promo code. */
suspend fun recoverPendingSubscriptionCommand(storeId: String): ResponseDataModel<SubscriptionDashboardDataModel>? {
    val account = currentSubscriptionAccountId() ?: return null
    val generation = currentAuthenticatedSessionGeneration()
    val owner = inventoryOwners.current
    return subscriptionWriteMutex.withLock {
        val key = pendingSubscriptionKey(account, storeId)
        val command = getLocalKv(key) ?: return@withLock null
        if (!authenticatedSessionGenerationIsCurrent(generation)) return@withLock cloudSessionExpiredResponse()
        if (inventoryOwnerIsCurrent(owner) && activeStoreIdState.value == storeId) pendingSubscriptionCommandIdState.value = command
        val response = networkRequest<SubscriptionDashboardDataModel, Unit>(HttpMethod.Get,
            endpointUrl = "subscriptions/store/command", headers = mapOf("store_id" to storeId),
            query = mapOf("commandId" to command), expectedSessionGeneration = generation)
        val dashboard = response.payload
        if (!response.negative && dashboard?.subscription?.storeId == storeId) {
            acceptSubscriptionDashboard(account, generation, owner, dashboard)
            if (getLocalKv(key) == command) putLocalKv(key, null)
            if (inventoryOwnerIsCurrent(owner) && activeStoreIdState.value == storeId) pendingSubscriptionCommandIdState.value = null
        }
        response
    }
}

/** Explicitly reviewing again fetches the latest revision first. Competing old/new commands still
 * serialize at the server and cannot charge twice for the same active period.
 */
suspend fun reviewSubscriptionAfterUnknownResult(storeId: String): Boolean {
    val account = currentSubscriptionAccountId() ?: return false
    val generation = currentAuthenticatedSessionGeneration()
    val response = refreshStoreSubscriptionNow(storeId)
    if (response.negative || response.payload == null || !authenticatedSessionGenerationIsCurrent(generation)) return false
    return subscriptionWriteMutex.withLock {
        if (currentSubscriptionAccountId() != account || !authenticatedSessionGenerationIsCurrent(generation)) return@withLock false
        putLocalKv(pendingSubscriptionKey(account, storeId), null)
        if (activeStoreIdState.value == storeId) pendingSubscriptionCommandIdState.value = null
        true
    }
}
