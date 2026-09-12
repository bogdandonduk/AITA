package kz.aita

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One view owner. The durable journal/sender lives in shared code and survives screen disposal. */
@Stable
internal class MarketShoppingUiState(private val owner: MarketRequestScope?, private val scope: CoroutineScope) {
    var snapshot by mutableStateOf<MarketShoppingSnapshot?>(null)
        private set
    var pending by mutableStateOf<PendingMarketShoppingCommand?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var changing by mutableStateOf(false)
        private set
    var fresh by mutableStateOf(false)
        private set
    var error by mutableStateOf<List<LocalizedStringDataModel>?>(null)
        private set
    private var observedJournalRevision = -1L
    private var reviewNotice = false
    var active = true
    val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    val canChange: Boolean get() = active && owner?.isCurrent() == true && snapshot != null && pending == null && !changing

    private fun accept(result: MarketShoppingClientResult) {
        if (!active || owner?.isCurrent() != true) return
        if (result.journalRevision >= 0L && result.journalRevision < observedJournalRevision) return
        result.snapshot?.let { snapshot = snapshot.acceptShoppingSnapshot(it) }
        if (result.journalRevision >= 0L) {
            observedJournalRevision = result.journalRevision
            pending = result.pending
        } else if (result.pending != null) pending = result.pending
        if (result.acknowledged) reviewNotice = !result.accepted
        if (result.error != null || !reviewNotice) error = result.error
        fresh = result.fresh
    }
    suspend fun run() {
        val owned = owner ?: return
        accept(MarketShoppingDelivery.cached(owned))
        refreshRequests.trySend(Unit)
        for (ignored in refreshRequests) {
            delay(150)
            while (refreshRequests.tryReceive().isSuccess) { /* coalesce the pre-read burst */ }
            if (!active || !owned.isCurrent()) break
            loading = true
            try { accept(MarketShoppingDelivery.refresh(owned)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { fresh = false; error = eventMessage("market.shopping_refresh") }
            finally { loading = false }
        }
    }
    fun change(offerId: String, units: Int, basis: MarketShoppingBasis?) {
        val owned = owner ?: return
        val current = snapshot ?: return
        if (!canChange) return
        changing = true
        val command = MarketShoppingCommand(newClientSideUuidString(), current.revision, offerId, units, if (units == 0) null else basis)
        scope.launch {
            try { accept(MarketShoppingDelivery.change(owned, command)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (active && owned.isCurrent()) accept(MarketShoppingDelivery.cached(owned).copy(error = eventMessage("market.shopping_pending")))
            } finally { changing = false; if (active) refresh() }
        }
    }
    fun retry() {
        val owned = owner ?: return
        if (changing || !active) return
        changing = true
        scope.launch {
            try { accept(MarketShoppingDelivery.retry(owned)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (active && owned.isCurrent()) error = eventMessage("market.shopping_pending") }
            finally { changing = false; if (active) refresh() }
        }
    }
    fun refresh(userInitiated: Boolean = false) {
        if (userInitiated) { reviewNotice = false; error = null }
        refreshRequests.trySend(Unit)
    }
    fun contains(offerId: String) = snapshot?.lines?.any { it.line.offerId == offerId } == true
}

@Composable
internal fun AppConfiguration.rememberMarketShoppingUiState(): MarketShoppingUiState {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val coroutineScope = rememberCoroutineScope()
    val state = remember(account, generation) { MarketShoppingUiState(captureMarketRequestScope(), coroutineScope) }
    val remote by MarketplaceSignals.revision.collectAsState()
    DisposableEffect(state) { onDispose { state.active = false; state.refreshRequests.close() } }
    LaunchedEffect(state) { state.run() }
    LaunchedEffect(state, remote) { state.refresh() }
    LaunchedEffect(state) { while (isActive) { delay(30_000); state.refresh() } }
    return state
}
