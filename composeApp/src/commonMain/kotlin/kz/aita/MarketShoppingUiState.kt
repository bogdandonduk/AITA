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
    var checking by mutableStateOf(false)
        private set
    var cancellingCommandId by mutableStateOf<String?>(null)
        private set
    var cancelling by mutableStateOf(false)
        private set
    var notice by mutableStateOf<List<LocalizedStringDataModel>?>(null)
        private set
    var fresh by mutableStateOf(false)
        private set
    var error by mutableStateOf<List<LocalizedStringDataModel>?>(null)
        private set
    var acknowledgedCommandId by mutableStateOf<String?>(null)
        private set
    var lastChangeAccepted by mutableStateOf(false)
        private set
    private var acknowledgedJournalRevision = -1L
    private var observedJournalRevision = -1L
    private var reviewNotice = false
    var active = true
    val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    val canChange: Boolean get() = active && owner?.isCurrent() == true && snapshot != null && pending == null && !changing && !checking && !cancelling

    private fun accept(result: MarketShoppingClientResult) {
        if (!active || owner?.isCurrent() != true) return
        // Completion is an event, not a list snapshot. A newer GET may arrive before this
        // callback; it must not swallow a valid acknowledgement or leave review stuck open.
        if (result.acknowledged && result.journalRevision >= acknowledgedJournalRevision) {
            acknowledgedJournalRevision = result.journalRevision
            // A safe server cancellation is a neutral resolution, not a failed list edit.
            reviewNotice = !result.accepted && result.notice == null
            acknowledgedCommandId = result.acknowledgedCommandId
            lastChangeAccepted = result.accepted
            if (pending == null || pending?.command?.commandId == result.acknowledgedCommandId) {
                error = result.error
                notice = result.notice
            }
        }
        if (result.journalRevision >= 0L && result.journalRevision < observedJournalRevision) return
        result.snapshot?.let { snapshot = snapshot.acceptShoppingSnapshot(it) }
        if (result.journalRevision >= 0L) {
            observedJournalRevision = result.journalRevision
            pending = result.pending
            cancellingCommandId = result.cancellingCommandId
        } else if (result.pending != null) pending = result.pending
        if (result.error != null || !reviewNotice) error = result.error
        if (result.notice != null) notice = result.notice
        else if ((pending == null && result.acknowledged) || result.error != null) notice = null
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
    /** Add buttons cannot turn into quantity replacement when their rendered state is old. */
    fun add(offerId: String, units: Int, basis: MarketShoppingBasis?): Boolean {
        if (!canChange) return false
        val current = snapshot ?: return false
        val command = current.newShoppingLineCommand(offerId, units, basis, newClientSideUuidString())
        if (command == null) {
            notice = eventMessage(when {
                contains(offerId) -> "market.shopping_already_listed"
                current.lines.size >= MARKET_SHOPPING_MAX_LINES -> "market.shopping_limit"
                else -> "market.shopping_invalid"
            })
            return false
        }
        return send(command)
    }
    fun changeLine(review: MarketShoppingLineReview, units: Int): Boolean {
        if (!canChange) return false
        val command = review.command(snapshot, units, newClientSideUuidString())
        if (command == null) { requireNewLineReview(); return false }
        return send(command)
    }
    fun compareLine(review: MarketShoppingLineReview): MarketComparisonSelection? {
        if (!canChange) return null
        return review.comparison(snapshot).also { if (it == null) requireNewLineReview() }
    }
    private fun requireNewLineReview() {
        notice = eventMessage("market.shopping_edit_changed")
        refresh()
    }
    fun replace(selection: MarketComparisonSelection, candidate: MarketShoppingQuotedLine): String? {
        val current = snapshot ?: return null
        val command = selection.reviewedReplacement(current, candidate, newClientSideUuidString()) ?: return null
        return if (send(command)) command.commandId else null
    }
    fun applyBasket(command: MarketShoppingCommand): Boolean {
        val current = snapshot ?: return false
        val basket = command.basketChange ?: return false
        if (basket.basketIntentError(current, command.expectedRevision) != null) return false
        return send(command)
    }
    private fun send(command: MarketShoppingCommand): Boolean {
        val owned = owner ?: return false
        if (!canChange || !command.isValidMarketShoppingCommand()) return false
        changing = true
        notice = null
        scope.launch {
            try { accept(MarketShoppingDelivery.change(owned, command)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (active && owned.isCurrent()) accept(MarketShoppingDelivery.cached(owned).copy(error = eventMessage("market.shopping_pending")))
            } finally { changing = false; if (active) refresh() }
        }
        return true
    }
    fun checkResult() {
        val owned = owner ?: return
        if (changing || checking || cancelling || pending == null || !active || !owned.isCurrent()) return
        checking = true
        notice = null
        scope.launch {
            try { accept(MarketShoppingDelivery.checkResult(owned)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (active && owned.isCurrent()) error = eventMessage("market.shopping_result_failed") }
            finally { checking = false }
        }
    }
    fun cancelPending(expected: MarketShoppingCommand) {
        val owned = owner ?: return
        if (changing || checking || cancelling || pending == null || !active || !owned.isCurrent()) return
        if (pending?.command != expected) return
        cancelling = true
        error = null
        notice = null
        scope.launch {
            try { accept(MarketShoppingDelivery.cancelPending(owned, expected)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (active && owned.isCurrent()) error = eventMessage("market.shopping_cancel_failed") }
            finally { cancelling = false }
        }
    }

    fun retry() {
        val owned = owner ?: return
        if (changing || checking || cancelling || !active) return
        changing = true
        notice = null
        scope.launch {
            try { accept(MarketShoppingDelivery.retry(owned)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (active && owned.isCurrent()) error = eventMessage("market.shopping_pending") }
            finally { changing = false; if (active) refresh() }
        }
    }
    fun refresh(userInitiated: Boolean = false) {
        if (userInitiated) { reviewNotice = false; error = null; notice = null }
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
