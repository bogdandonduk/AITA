package kz.aita

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Stable
internal class MarketSavedShopsUi(private val owner: MarketAccountScope?, private val scope: CoroutineScope) {
    var active = true
    var snapshot by mutableStateOf<MarketSavedShops?>(null); private set
    var loading by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var error by mutableStateOf<List<LocalizedStringDataModel>?>(null); private set
    val reads = Channel<Unit>(Channel.CONFLATED)
    val canChange get() = active && owner?.isCurrent() == true && snapshot != null && !loading && !saving && error == null
    fun saved(id: String) = id in snapshot?.storeIds.orEmpty()
    private fun accept(value: MarketSavedShops?) {
        if (value != null && value.revision >= (snapshot?.revision ?: -1)) snapshot = value
    }
    fun refresh() { reads.trySend(Unit) }
    suspend fun read() {
        val owned = owner ?: return
        if (!active || !owned.isCurrent()) return
        loading = true
        try {
            val result = loadMarketSavedShops(owned)
            if (active && owned.isCurrent()) {
                if (!result.negative && result.payload != null) { accept(result.payload); error = null }
                else error = result.message ?: eventMessage("market.saved_shops_failed")
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (active && owned.isCurrent()) error = eventMessage("market.saved_shops_failed") }
        finally { loading = false }
    }
    fun toggle(id: String) {
        val owned = owner ?: return
        val before = snapshot ?: return
        if (!canChange) return
        saving = true
        scope.launch {
            try {
                val result = changeMarketSavedShop(owned, MarketSavedShopChange(id, id !in before.storeIds, before.revision))
                if (active && owned.isCurrent()) {
                    if (!result.negative && result.payload != null) { accept(result.payload); error = null }
                    else error = result.message ?: eventMessage("market.saved_shops_failed")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (active && owned.isCurrent()) error = eventMessage("market.saved_shops_failed") }
            finally { saving = false; refresh() }
        }
    }
}

@Composable
internal fun AppConfiguration.rememberMarketSavedShops(): MarketSavedShopsUi {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val scope = rememberCoroutineScope()
    val state = remember(account, generation) { MarketSavedShopsUi(captureMarketRequestScope(), scope) }
    val revision by MarketplaceSignals.revision.collectAsState()
    DisposableEffect(state) { onDispose { state.active = false; state.reads.close() } }
    LaunchedEffect(state, revision) { state.refresh() }
    LaunchedEffect(state) {
        for (ignored in state.reads) {
            delay(150)
            while (state.reads.tryReceive().isSuccess) { }
            state.read()
        }
    }
    LaunchedEffect(state) { while (isActive) { delay(30_000); state.refresh() } }
    return state
}
