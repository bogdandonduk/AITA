package kz.aita

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Each filter/page owns its result cell. Late reads cannot relabel old rows as a new query. */
@Stable private class DeliveryWorkspaceRead {
    var active = true
    var page by mutableStateOf<MarketDeliveryPage?>(null)
    var busy by mutableStateOf(true)
    var fresh by mutableStateOf(false)
    var partial by mutableStateOf(false)
    var accessDenied by mutableStateOf(false)
    var error by mutableStateOf<List<LocalizedStringDataModel>?>(null)
}

@Composable internal fun AppConfiguration.MarketDeliveryPanel(modifier: Modifier = Modifier, view: String = "buyer", store: String? = null) {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val owner = remember(account, generation, store) { captureMarketRequestScope(store) }
    val signal by MarketplaceSignals.revision.collectAsState()
    val ui = rememberCoroutineScope()
    val host = NavigationScreenModel.Buyer.Main.Shopping
    val key = "delivery-workbench:$account:$view:${store.orEmpty()}"
    var filter by remember(key) { mutableStateOf(runCatching {
        host.state.value[key]?.takeIf { it.length < 2_000 }?.let { jsonBase.decodeFromString<MarketDeliveryFilter>(it) }?.normalized()?.takeIf { it.isValid() }
    }.getOrNull() ?: MarketDeliveryFilter(queue = MarketDeliveryQueue.ACTIVE)) }
    var search by remember(key) { mutableStateOf(filter.search) }
    var before by remember(key) { mutableStateOf<String?>(null) }
    var opened by rememberSaveable(account, view, store) { mutableStateOf<String?>(null) }
    var pending by remember(account, generation) { mutableStateOf<MarketDeliverySendResult?>(null) }
    var mutationEpoch by remember(account, generation) { mutableStateOf(0L) }
    var sending by remember(account, generation) { mutableStateOf(false) }
    var pendingError by remember(account, generation) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var clock by remember { mutableStateOf(getCurrentTimeMillis()) }
    val data = remember(account, generation, view, store, filter, before) { DeliveryWorkspaceRead() }
    val requests = remember(data) { Channel<Unit>(Channel.CONFLATED) }
    val detailOpen by rememberUpdatedState(opened != null)
    val scroll = rememberLazyListState()
    fun choose(next: MarketDeliveryFilter) {
        filter = next.normalized(); before = null
        host.setStateNow(key to jsonBase.encodeToString(filter))
    }
    LaunchedEffect(search, key) {
        delay(350)
        if (owner?.isCurrent() == true && search.trim() != filter.search) choose(filter.copy(search = search))
    }
    DisposableEffect(data) { onDispose { data.active = false; requests.close() } }
    LaunchedEffect(data) { scroll.scrollToItem(0) }
    LaunchedEffect(data, signal, opened) {
        data.fresh = false
        if (opened == null) requests.trySend(Unit)
    }
    LaunchedEffect(data) {
        val owned = owner ?: run { data.busy = false; return@LaunchedEffect }
        if (before == null) {
            val exact = cachedMarketDeliveryPage(owned, view, store, filter)
            val saved = exact ?: cachedMarketDeliverySubset(owned, view, store, filter)
            if (data.active && owned.isCurrent()) { data.page = saved; data.partial = exact == null && saved != null }
        }
        val savedEpoch = mutationEpoch
        val savedSignal = MarketplaceSignals.revision.value
        val savedCommand = cachedMarketDeliveryCommand(owned)
        if (data.active && owned.isCurrent() && !sending && savedEpoch == mutationEpoch &&
            savedSignal == MarketplaceSignals.revision.value) {
            pending = savedCommand
            pendingError = savedCommand.error
        }
        for (ignored in requests) {
            delay(200)
            awaitClientBackgroundWork()
            while (requests.tryReceive().isSuccess) { /* coalesce repeated realtime invalidations */ }
            if (!data.active || !owned.isCurrent()) break
            if (detailOpen) continue
            data.busy = true
            val readEpoch = mutationEpoch
            val readSignal = MarketplaceSignals.revision.value
            fun currentRead() = readEpoch == mutationEpoch && readSignal == MarketplaceSignals.revision.value
            try {
                val savedPending = cachedMarketDeliveryCommand(owned)
                if (data.active && owned.isCurrent() && currentRead() && !sending) {
                    pending = savedPending
                    pendingError = savedPending.error
                }
                val result = withTimeoutOrNull(15_000) { loadMarketDeliveryPage(owned, view, store, before, filter) }
                if (data.active && owned.isCurrent()) {
                    if (result?.httpStatusCode in setOf(401, 403) && result?.transportFailure == false) {
                        // Even an older authoritative denial revokes saved customer details.
                        data.fresh = false; data.page = null; data.accessDenied = true
                        data.error = result?.message ?: eventMessage("delivery.refresh_failed")
                    } else if (!currentRead()) {
                        // A pre-mutation empty page must not hide a durably acknowledged order.
                        // Keep one trailing read after local actions or background invalidation.
                        requests.trySend(Unit)
                    } else {
                        data.fresh = result?.negative == false && result.payload != null
                        if (data.fresh) { data.page = result?.payload; data.partial = false; data.accessDenied = false; data.error = result?.message }
                        else data.error = result?.message ?: eventMessage("delivery.refresh_failed")
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (data.active && owned.isCurrent()) {
                    if (currentRead()) { data.fresh = false; data.error = eventMessage("delivery.refresh_failed") }
                    else requests.trySend(Unit)
                }
            }
            finally { if (data.active) data.busy = false }
        }
    }
    LaunchedEffect(data) {
        while (isActive) {
            delay(30_000); awaitClientBackgroundWork(); clock = getCurrentTimeMillis()
            if (!detailOpen && before == null) requests.trySend(Unit)
        }
    }
    val confirmedFallback = pending?.confirmedBuyerFallback(account.orEmpty(), view, filter, data.page,
        data.fresh, data.accessDenied, before, clock)
    LazyColumn(modifier, state = scroll, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sectionTabsWidget("delivery-queue:$key", listOf(
                    TabContent("ACTIVE", deliveryText("queue.ACTIVE"), icon = AitaTabIcon.Truck),
                    TabContent("ATTENTION", deliveryText("queue.ATTENTION"), icon = AitaTabIcon.Warning),
                    TabContent("HISTORY", deliveryText("queue.HISTORY"), icon = AitaTabIcon.Recent),
                    TabContent("ALL", deliveryText("queue.ALL"), icon = AitaTabIcon.All)), selectedId = filter.queue.name,
                    onSelected = { choose(filter.copy(queue = MarketDeliveryQueue.valueOf(it))) })
                aitaFormTextField(Modifier.fillMaxWidth(), search, { search = it.take(120) }, deliveryText("search_orders"),
                    identityKey = "delivery-search:$key", autoFocus = false, parentOwnsValue = true)
                sectionTabsWidget("delivery-filter-mode:$key", listOf(TabContent("ALL", deliveryText("all_modes"), icon = AitaTabIcon.All)) +
                    MarketDeliveryMode.entries.map { TabContent(it.name, deliveryText(it.name), icon = when (it) {
                        MarketDeliveryMode.EXPRESS -> AitaTabIcon.Quick
                        MarketDeliveryMode.PICKUP -> AitaTabIcon.Store
                        else -> AitaTabIcon.Truck
                    }) }, selectedId = filter.mode?.name ?: "ALL", onSelected = { selected ->
                        choose(filter.copy(mode = MarketDeliveryMode.entries.firstOrNull { it.name == selected }))
                    })
                if (filter.queue == MarketDeliveryQueue.ATTENTION) Text(deliveryText("attention_help.$view"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                if (!data.fresh && data.page != null) Text(deliveryText(if (data.partial) "cached_subset" else "cached"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                data.error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
                actionButton(text = deliveryText("refresh"), iconPath = stateValues.drawablePathIconRefresh,
                    enabled = !data.busy, loading = data.busy, autoLoading = false, confirmationRequired = false,
                    onClick = { requests.trySend(Unit) })
                pendingError?.let { Text(it.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
                if (pending?.pending != null) DeliveryCard {
                    Text(deliveryText("pending"), color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)
                    actionButton(text = deliveryText("retry"), enabled = !sending, loading = sending, autoLoading = false, confirmationRequired = false, onClick = {
                        if (owner != null && !sending) {
                            mutationEpoch++; sending = true; data.fresh = false
                            ui.launch { try {
                                val result = sendMarketDeliveryCommand(owner)
                                if (owner.isCurrent()) { pending = result; pendingError = result.error; requests.trySend(Unit) }
                            } finally { sending = false } }
                        }
                    })
                }
                if (before != null) actionButton(text = deliveryText("latest"), autoLoading = false, confirmationRequired = false, onClick = { before = null })
            }
        }
        if (confirmedFallback != null) item("last-confirmed") {
            DeliveryCard {
                Text(deliveryText("last_confirmed"), color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)
                Text(confirmedFallback.shopName, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                Text(deliveryText(confirmedFallback.status.name) + " · " + receiptUiDateTime(confirmedFallback.updatedAtMillis),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                confirmedFallback.totalMinor?.let { Text(marketMoneyLabel(it, confirmedFallback.request.currency),
                    color = stateValues.TextColor, fontSize = stateValues.textSize) }
                actionButton(text = deliveryText("view_order"), autoLoading = false, confirmationRequired = false,
                    onClick = { opened = confirmedFallback.id })
            }
        }
        if (!data.busy && data.page?.orders.isNullOrEmpty() && confirmedFallback == null) item("empty") {
            Text(deliveryText(if (data.fresh) "empty_filter" else if (data.page != null) "empty_saved" else "unavailable_filter"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.textSize)
        }
        items(data.page?.orders.orEmpty(), key = { it.id }) { order ->
            DeliveryCard(Modifier.clickable { opened = order.id }) {
                Text(order.shopName, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                Text("${deliveryText(order.request.mode.name)} · ${deliveryText(order.status.name)}", color = stateValues.AccentColor, fontSize = stateValues.textSize)
                Text(receiptUiDateTime(order.createdAtMillis) + " · " + order.id.take(8).uppercase(), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                order.attentionReasons(view, clock).forEach { reason -> Text(deliveryText("attention.${reason.name}"),
                    color = if (reason == MarketDeliveryAttention.OVERDUE) stateValues.ErrorColor else stateValues.AccentColor, fontSize = stateValues.smallTextSize) }
                Text(order.request.lines.take(3).joinToString(" · ") { it.line.title }, color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                order.totalMinor?.let { Text(marketMoneyLabel(it, order.request.currency), color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold) }
                if (!order.terminal) Text(deliveryText("estimate") + ": " + receiptUiDateTime(order.estimatedFromMillis) + " — " + receiptUiDateTime(order.estimatedToMillis), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
        }
        data.page?.nextBefore?.let { cursor -> item("more") { actionButton(text = deliveryText("next"), autoLoading = false,
            confirmationRequired = false, enabled = !data.busy, onClick = { before = cursor }) } }
    }
    opened?.let { id -> key(account,generation,id,store) { MarketDeliveryOrderSheet(id, store, data.page?.orders?.firstOrNull { it.id == id } ?: confirmedFallback?.takeIf { it.id == id }, onDismiss = { opened = null }) } }
}
