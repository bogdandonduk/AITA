package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

private class PriceWatchMessage(val key: String) : Exception()

@Stable
internal class MarketPriceWatchController(private val scope: CoroutineScope, private val current: () -> Boolean,
    private val request: suspend (MarketPriceWatchUpdate?) -> MarketPriceWatchResult = ::requestMarketPriceWatch) {
    var state by mutableStateOf<MarketPriceWatchState?>(null); private set
    var message by mutableStateOf<List<LocalizedStringDataModel>?>(null); private set
    var busy by mutableStateOf(false); private set
    var denied by mutableStateOf(false); private set
    var pending by mutableStateOf<MarketPriceWatchUpdate?>(null); private set
    val count get() = state?.takeIf { it.enabled }?.settings?.items?.size ?: 0
    fun follows(row: MarketPriceInsightRow) = state?.takeIf { it.enabled }?.settings?.items?.any { it.itemId == row.goodsItemId && it.gtin == row.basis?.gtin } == true

    private fun accept(result: MarketPriceWatchResult) {
        if (!current()) return
        if (result.denied) state = null else result.state?.let { state = it }
        denied = result.denied
        message = result.message
    }
    fun refresh() {
        if (busy || !current()) return
        busy = true
        scope.launch {
            try {
                val result = request(null)
                if (current()) { accept(result); if (result.state != null || result.denied) pending = null }
            } finally { busy = false }
        }
    }
    private suspend fun send(body: MarketPriceWatchUpdate) {
        if (!current()) return
        pending = body
        val result = request(body)
        if (!current()) return
        accept(result)
        if (result.state != null || result.rejected || result.denied || result.conflict) pending = null
    }
    fun retry() {
        val body = pending ?: return
        if (busy || !current()) return
        busy = true
        scope.launch { try { send(body) } finally { busy = false } }
    }
    private fun change(transform: (MarketPriceWatchState) -> MarketPriceWatchSettings) {
        if (busy || !current()) return
        busy = true; message = null
        scope.launch {
            try {
                // Resolve an uncertain prior save and merge against the current server state.
                // An expected revision still prevents overwriting a simultaneous edit elsewhere.
                val fresh = request(null)
                if (!current()) return@launch
                accept(fresh)
                val confirmed = fresh.state ?: return@launch
                pending = null
                val settings = transform(confirmed).normalized() ?: throw PriceWatchMessage("watch_invalid")
                send(MarketPriceWatchUpdate(newDiagnosticId(), confirmed.revision, settings.items.isNotEmpty(), settings))
            } catch (cancel: CancellationException) { throw cancel }
            catch (problem: PriceWatchMessage) { if (current()) message = marketPriceMessage(problem.key) }
            finally { busy = false }
        }
    }
    fun toggle(row: MarketPriceInsightRow, request: MarketPriceRequest) {
        // Capture the action shown to the user. A lost successful reply must not turn a
        // second "Follow" press into "Stop" after the reconciliation read.
        val follow = !follows(row)
        change { confirmed ->
        val old = confirmed.settings
        if (!follow) old.copy(items = old.items.filterNot { it.itemId == row.goodsItemId })
        else {
            if (confirmed.enabled && (old.country != request.country || !old.city.equals(request.city, true))) throw PriceWatchMessage("watch_region")
            val items = old.items.takeIf { confirmed.enabled }.orEmpty().filterNot { it.itemId == row.goodsItemId }
            if (items.size >= MARKET_PRICE_WATCH_MAX_ITEMS) throw PriceWatchMessage("watch_limit")
            val code = row.basis?.gtin ?: throw PriceWatchMessage("watch_invalid")
            MarketPriceWatchSettings(request.country, request.city, items + MarketPriceWatchItem(row.goodsItemId, code), old.thresholdBasisPoints)
        }
        }
    }
    fun remove(item: String) = change { it.settings.copy(items = it.settings.items.filterNot { row -> row.itemId == item }) }
    fun threshold(value: Int) = change { it.settings.copy(thresholdBasisPoints = value) }
    fun stop() = change { it.settings.copy(items = emptyList()) }
}

@Composable
internal fun AppConfiguration.MarketPriceWatchPanel(watch: MarketPriceWatchController) {
    val language = stateValues.appLanguage
    fun text(key: String) = marketPriceText(key, language)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text("watch_help"), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
        watch.message?.let { Text(it.extractLocalizedString(language).orEmpty(), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
        actionButton(Modifier.fillMaxWidth(), text = text(if (watch.pending != null) "watch_retry" else "watch_refresh"),
            iconPath = stateValues.drawablePathIconRefresh, loading = watch.busy, autoLoading = false, confirmationRequired = false,
            onClick = { if (watch.pending != null) watch.retry() else watch.refresh() })
        val state = watch.state
        if (state?.enabled == true) {
            Text("${text(state.settings.country)} · ${state.settings.city.ifBlank { text("all_cities") }}", color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
            SimpleDropdownField(title = text("watch_threshold"), selectedId = state.settings.thresholdBasisPoints.toString(),
                options = marketPriceWatchThresholds.map { DropdownOption(it.toString(), "${it / 100}%") },
                restoreLastChoice = false, placeholder = "", onSelected = { it.toIntOrNull()?.let(watch::threshold) })
            state.checkedAtMillis?.let { Text("${text("checked")}: ${receiptUiDateTime(it)}", color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize) }
            state.nextCheckAtMillis?.let { Text("${text("watch_next")}: ${receiptUiDateTime(it)}", color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize) }
            for (item in state.settings.items) {
                val observation = state.observations.firstOrNull { it.itemId == item.itemId }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(observation?.name?.extractLocalizedString(language).orEmpty().ifBlank { item.gtin }, color = stateValues.TextColor, fontSize = stateValues.textSize)
                        Text(text("watch_${observation?.status ?: "waiting"}"), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
                    }
                    actionButton(Modifier.widthIn(max = 150.dp), text = text("watch_off"), textSize = stateValues.smallTextSize,
                        loading = watch.busy, autoLoading = false, confirmationRequired = false, onClick = { watch.remove(item.itemId) })
                }
            }
            actionButton(Modifier.fillMaxWidth(), text = text("watch_stop"), iconPath = stateValues.drawablePathIconCancel,
                loading = watch.busy, autoLoading = false, confirmationRequired = true, onClick = watch::stop)
        } else if (!watch.busy && state != null) Text(text("watch_none"), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
    }
}

@Composable
internal fun AppConfiguration.MarketPriceNotificationLink(notification: NotificationDataModel) {
    if (notification.category != "market_prices" || notification.storeId != stateValues.activeStoreId ||
        !canUseMarketPriceInsights(stateValues.activeStoreId)) return
    var open by remember(notification.id) { mutableStateOf(false) }
    val details = remember(notification) { notification.marketPriceAlertDetails() }
    var showChanges by remember(notification.id) { mutableStateOf(false) }
    if (details != null) {
        actionButton(Modifier.fillMaxWidth().padding(top = 8.dp),
            text = "${marketPriceText("watch_changes", stateValues.appLanguage)} · ${details.changes.size}",
            iconPath = if (showChanges) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore,
            enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
            autoLoading = false, confirmationRequired = false, onClick = { showChanges = !showChanges })
        if (showChanges) MarketPriceAlertChanges(details)
    }
    actionButton(Modifier.fillMaxWidth().padding(top = 8.dp), text = marketPriceText("title", stateValues.appLanguage),
        iconRes = tabIconResource(AitaTabIcon.Compare), autoLoading = false, confirmationRequired = false,
        onClick = { open = true })
    if (open) MarketPriceInsightsSheet(initialRequest = MarketPriceRequest(
        country = notification.metadata["country"] ?: "kz", city = notification.metadata["city"].orEmpty()),
        openAlerts = true) {
            open = false
            // Marking it before opening removes this composable from the Unread tab,
            // immediately disposing the report dialog it owns.
            if (notification.readAtMillis == null && notification.userId == stateValues.userAccount?.id)
                markNotificationRead(notification.id)
        }
}

@Composable
internal fun AppConfiguration.MarketPriceAlertChanges(details: MarketPriceAlertDetails) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${marketPriceText(details.country, stateValues.appLanguage)} · ${details.city.ifBlank { marketPriceText("all_cities", stateValues.appLanguage) }}",
            color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
        details.changes.forEach { change ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(change.name.extractLocalizedString(stateValues.appLanguage).orEmpty().ifBlank { change.basis.gtin.orEmpty() },
                    color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((label, amount) in listOf("watch_previous" to change.previousMinor, "watch_new" to change.currentMinor)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(marketPriceText(label, stateValues.appLanguage), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
                            Text(marketMoneyLabel(amount, change.basis.currencyCode), color = stateValues.TextColor, fontSize = stateValues.textSize)
                        }
                    }
                }
                Text("${marketPriceText("watch_difference", stateValues.appLanguage)}: ${marketPriceChangePercent(change)}",
                    color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
            }
        }
        Text("${marketPriceText("checked", stateValues.appLanguage)}: ${receiptUiDateTime(details.checkedAtMillis)}",
            color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
    }
}

internal fun marketPriceChangePercent(change: MarketPriceChange): String {
    val value = marketPriceDifferenceBasisPoints(change.currentMinor, change.previousMinor) ?: return ""
    val magnitude = kotlin.math.abs(value)
    val fraction = (magnitude % 100).toString().padStart(2, '0').trimEnd('0')
    return "${if (value >= 0) "+" else "-"}${magnitude / 100}${if (fraction.isEmpty()) "" else ".$fraction"}%"
}
