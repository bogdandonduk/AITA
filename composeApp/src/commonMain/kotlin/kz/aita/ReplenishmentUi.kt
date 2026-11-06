package kz.aita

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*

@Composable
internal fun AppConfiguration.ReplenishmentEntry(modifier: Modifier = Modifier) {
    if (!canUseReplenishment(stateValues.activeStoreId)) return
    val openTool = stockWorkspaceToolLauncher()
    actionButton(
        modifier,
        text = replenishmentText("title", stateValues.appLanguage),
        iconPath = stateValues.drawablePathIconTransactionSupply,
        autoLoading = false,
        confirmationRequired = false,
        onClick = { openTool(StockWorkspaceTool.PURCHASE_PLAN) },
    )
}

@Composable
internal fun AppConfiguration.ReplenishmentSheet(onDismiss: () -> Unit) {
    val language = stateValues.appLanguage
    fun text(key: String) = replenishmentText(key, language)
    val owner = remember { captureReceiptActionOwner() }
    val account = remember { stateValues.userAccount?.id.orEmpty() }
    val store = remember { stateValues.activeStoreId.orEmpty() }
    val inventoryScope = remember { inventoryViewScopeKey() }
    fun current() =
        owner.isCurrent() &&
            inventoryViewScopeKey() == inventoryScope &&
            stateValues.activeStoreId == store &&
            canUseReplenishment(store)
    val inventoryStatus by stockLoadStatusState.collectAsState()
    val planningDay = currentStockLocalDate()
    val scope = rememberCoroutineScope()
    val writer = remember { Mutex() }
    val plannerScroll = rememberLazyListState()
    var draft by remember {
        mutableStateOf(ReplenishmentDraft(accountId = account, storeId = store))
    }
    var loaded by remember { mutableStateOf(false) }
    var restoreFailed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var load by remember { mutableStateOf(ReplenishmentLoad()) }
    var error by remember { mutableStateOf("") }
    var controls by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var explanation by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<AnalyticsReportSnapshotDataModel?>(null) }
    var revision by remember { mutableStateOf(0) }
    val zone = remember { TimeZone.currentSystemDefault().id }
    val snapshot = load.snapshot?.takeIf { it.lookbackDays == draft.settings.lookbackDays }
    val frozen = draft.pendingOrder != null
    var suggestions by remember { mutableStateOf<List<ReplenishmentSuggestion>>(emptyList()) }
    suspend fun persist(value: ReplenishmentDraft) = writer.withLock {
        saveReplenishmentDraft(value)
    }
    LaunchedEffect(Unit) {
        try {
            val saved = readReplenishmentDraft(account, store)
            if (current()) draft = saved
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            restoreFailed = true
            error = text("storage")
        } finally {
            loaded = true
        }
        if (current()) getSuppliers()
    }
    LaunchedEffect(stateValues.activeStoreId, stateValues.userAccount?.id, inventoryStatus) {
        if (!current()) onDismiss()
    }
    LaunchedEffect(loaded, draft.settings.lookbackDays, revision) {
        if (!loaded || !current()) return@LaunchedEffect
        loading = true
        try {
            val result =
                loadReplenishment(draft.settings.lookbackDays, zone) { saved ->
                    withContext(Dispatchers.Main) { if (current()) load = saved }
                }
            if (current()) load = result
        } finally {
            loading = false
        }
    }
    LaunchedEffect(snapshot, draft.settings, planningDay) {
        suggestions =
            if (snapshot == null) emptyList()
            else
                try {
                    withContext(Dispatchers.Default) {
                        snapshot.suggest(
                            draft.settings.copy(categoryId = ""),
                            getCurrentTimeMillis(),
                        )
                    }
                } catch (_: IllegalArgumentException) {
                    emptyList()
                }
    }
    LaunchedEffect(draft, loaded, sending) {
        if (!loaded || restoreFailed || sending || !current()) return@LaunchedEffect
        delay(400)
        try {
            persist(draft)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            if (current()) error = text("storage")
        }
    }
    fun dismiss() {
        if (sending || closing) return
        closing = true
        scope.launch {
            try {
                if (loaded && !restoreFailed) persist(draft)
                onDismiss()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = text("storage")
            } finally {
                closing = false
            }
        }
    }
    fun send() {
        if (sending) return
        if (!current()) {
            error = text("access")
            return
        }
        if (!loaded) {
            error = text("loading")
            return
        }
        if (restoreFailed) {
            error = text("storage")
            return
        }
        if (!currentUserCanManageSupplierOrders(store)) {
            error = currentUserPermissionDeniedMessage().extractLocalizedString(language).orEmpty()
            return
        }
        val pending = draft.pendingOrder
        if (pending == null && !draft.settings.valid(currentStockLocalDate())) {
            error = text("invalid_settings")
            return
        }
        if (pending == null && snapshot == null) {
            error = load.message?.extractLocalizedString(language) ?: text("offline_empty")
            return
        }
        val body =
            if (pending != null) pending
            else
                try {
                    val source = requireNotNull(snapshot)
                    require(
                        draft.supplierId.isNotBlank() &&
                            stateValues.suppliers.orEmpty().any {
                                it.id == draft.supplierId && it.isActive
                            }
                    )
                    val quantities =
                        draft.quantities.mapValues { (_, value) ->
                            requireNotNull(value.trim().replace(',', '.').toDoubleOrNull())
                        }
                    replenishmentOrder(
                        source,
                        draft.settings,
                        quantities,
                        draft.supplierId,
                        account,
                        newDiagnosticId(),
                        getCurrentTimeMillis(),
                    )
                } catch (_: IllegalArgumentException) {
                    error = text("selection_error")
                    return
                }
        sending = true
        error = ""
        scope.launch {
            try {
                val frozenDraft = draft.copy(pendingOrder = body, completedOrderId = null)
                persist(frozenDraft)
                if (!current()) return@launch
                draft = frozenDraft
                val answer = CompletableDeferred<DataState<SupplierOrderWithLinesDataModel>>()
                addSupplierOrder(body) { answer.complete(it) }
                val result = withTimeoutOrNull(30_000) { answer.await() }
                if (!current()) return@launch
                if (result is DataState.Success) {
                    val complete =
                        draft.copy(
                            pendingOrder = null,
                            quantities = emptyMap(),
                            completedOrderId = body.order.id,
                        )
                    persist(complete)
                    draft = complete
                    revision++
                } else {
                    error =
                        result?.message?.extractLocalizedString(language)?.takeIf {
                            it.isNotBlank()
                        } ?: text("pending")
                    // Only an explicit response that also cleared the existing request journal
                    // permits editing. Timeouts/lost responses keep the exact intent frozen.
                    if (
                        result != null &&
                            pendingSupplierOrderDraft(
                                store,
                                body.lines.map { it.goodsItemId }.sorted().first(),
                            ) == null &&
                            current()
                    ) {
                        val editable = draft.copy(pendingOrder = null)
                        persist(editable)
                        draft = editable
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                if (current()) error = text("storage")
            } finally {
                sending = false
            }
        }
    }
    val filtered =
        remember(suggestions, query, draft.settings.categoryId) {
            suggestions.filter { row ->
                (draft.settings.categoryId.isBlank() ||
                    draft.settings.categoryId in row.item.goodsItem.categoryIds) &&
                    (query.isBlank() ||
                        row.item.goodsItem.name.any { it.value.contains(query, true) } ||
                        row.item.goodsItem.allBarcodeValues().any { it.contains(query, true) })
            }
        }
    if (report == null)
        AitaBottomSheet(
            title = text(if (controls) "settings" else "title"),
            iconPath = stateValues.drawablePathIconTransactionSupply,
            onDismiss = {
                when {
                    sending -> Unit
                    controls -> controls = false
                    confirming -> confirming = false
                    else -> dismiss()
                }
            },
        ) {
            if (controls)
                ReplenishmentSettingsContent(draft.settings) {
                    draft = draft.copy(settings = it, completedOrderId = null)
                    controls = false
                }
            else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = plannerScroll,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text("intro"),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            actionButton(
                                Modifier.weight(1f),
                                text = text("settings"),
                                autoLoading = false,
                                confirmationRequired = false,
                                onClick = {
                                    if (!frozen) controls = true else error = text("pending")
                                },
                            )
                            actionButton(
                                Modifier.weight(1f),
                                text = text("refresh"),
                                iconPath = stateValues.drawablePathIconRefresh,
                                loading = loading,
                                enabled = !loading,
                                autoLoading = false,
                                confirmationRequired = false,
                                onClick = { revision++ },
                            )
                        }
                    }
                    item {
                        ReplenishmentReminderCard(draft.settings, zone, frozen) { restored ->
                            if (!frozen)
                                draft = draft.copy(settings = restored, completedOrderId = null)
                        }
                    }
                    snapshot?.let { data ->
                        item {
                            Text(
                                "${text("snapshot")}: ${receiptUiDateTime(data.generatedAtMillis)}",
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize,
                            )
                        }
                        if (load.cached)
                            item {
                                Text(
                                    text("cached"),
                                    color = stateValues.ErrorColor,
                                    fontSize = stateValues.smallTextSize,
                                )
                            }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                actionButton(
                                    Modifier.fillMaxWidth(),
                                    text = text("calculation"),
                                    iconPath =
                                        if (explanation) stateValues.drawablePathIconExpandLess
                                        else stateValues.drawablePathIconExpandMore,
                                    enabledColor = stateValues.BackgroundColor,
                                    textColor = stateValues.TextColor,
                                    autoLoading = false,
                                    confirmationRequired = false,
                                    onClick = { explanation = !explanation },
                                )
                                if (explanation) {
                                    Text(
                                        text("help"),
                                        color = stateValues.PlaceholderTextColor,
                                        fontSize = stateValues.smallTextSize,
                                    )
                                    Text(
                                        text("assumptions"),
                                        color = stateValues.PlaceholderTextColor,
                                        fontSize = stateValues.smallTextSize,
                                    )
                                }
                            }
                        }
                        if (data.unmatchedTransactionLines > 0)
                            item {
                                Text(
                                    "${text("unmatched")}: ${data.unmatchedTransactionLines}",
                                    color = stateValues.ErrorColor,
                                    fontSize = stateValues.smallTextSize,
                                )
                            }
                        item {
                            actionButton(
                                Modifier.fillMaxWidth(),
                                text = text("report"),
                                autoLoading = false,
                                confirmationRequired = false,
                                onClick = {
                                    if (!draft.settings.valid(planningDay)) {
                                        error = text("invalid_settings")
                                        return@actionButton
                                    }
                                    val reportRows =
                                        if (draft.quantities.isEmpty()) filtered
                                        else
                                            suggestions.filter {
                                                it.item.goodsItem.id in draft.quantities
                                            }
                                    if (reportRows.isEmpty() || reportRows.size > 200) {
                                        error = text("report_limit")
                                        return@actionButton
                                    }
                                    if (
                                        draft.quantities.isNotEmpty() &&
                                            reportRows.size != draft.quantities.size
                                    ) {
                                        error = text("selection_error")
                                        return@actionButton
                                    }
                                    if (
                                        draft.quantities.isNotEmpty() &&
                                            reportRows.any { row ->
                                                val value =
                                                    draft.quantities[row.item.goodsItem.id]
                                                        ?.trim()
                                                        ?.replace(',', '.')
                                                        ?.toDoubleOrNull()
                                                val unit = row.item.unit
                                                unit == null ||
                                                    row.item.unlimited ||
                                                    row.item.uncertainUnits ||
                                                    value == null ||
                                                    !value.isFinite() ||
                                                    value <= 0 ||
                                                    value > 999_999_999 ||
                                                    (unit.roundTotal && value % 1.0 != 0.0)
                                            }
                                    ) {
                                        error = text("quantity_error")
                                        return@actionButton
                                    }
                                    report =
                                        AnalyticsReportSnapshotDataModel(
                                            text("title"),
                                            stateValues.stores
                                                .findStoreOrBranchForUi(store)
                                                ?.name
                                                ?.extractLocalizedString(language)
                                                .orEmpty(),
                                            "${receiptUiDateTime(data.startMillis)} — ${receiptUiDateTime(data.endMillisExclusive - 1)}",
                                            text(draft.settings.event),
                                            getCurrentTimeMillis(),
                                            listOf(
                                                AnalyticsReportSectionDataModel(
                                                    text("title"),
                                                    reportRows.map { row ->
                                                        AnalyticsReportRowDataModel(
                                                            row.item.goodsItem.name
                                                                .extractLocalizedString(language)
                                                                .orEmpty(),
                                                            (draft.quantities[row.item.goodsItem.id]
                                                                ?: row.suggestedQuantity
                                                                    ?.planNumber()
                                                                    .orEmpty()) +
                                                                " " +
                                                                row.item.unit
                                                                    ?.immutableUnitName
                                                                    ?.extractLocalizedString(
                                                                        language
                                                                    )
                                                                    .orEmpty(),
                                                            "${text("stock")}: ${row.usableStock.planNumber()} · ${text("incoming")}: ${row.confirmedIncoming.planNumber()} · ${text("order_by")}: ${row.orderBy}",
                                                        )
                                                    },
                                                    listOf(
                                                        "${text("lead")}: ${draft.settings.leadDays} · ${text("cover")}: ${draft.settings.coverDays}",
                                                        "${text("buffer")}: ${draft.settings.bufferPercent}% · ${text("uplift")}: ${draft.settings.upliftPercent}%",
                                                        "${text("arrival")}: ${draft.settings.arrival(planningDay)}",
                                                        text("assumptions"),
                                                        "${text("snapshot")}: ${receiptUiDateTime(data.generatedAtMillis)}",
                                                    ) +
                                                        if (load.cached) listOf(text("cached"))
                                                        else emptyList(),
                                                )
                                            ),
                                        )
                                },
                            )
                        }
                        item {
                            if (frozen)
                                Text(
                                    "${text("supplier")}: ${stateValues.suppliers.orEmpty().firstOrNull{it.id==draft.supplierId}?.name?.extractLocalizedString(language).orEmpty()}",
                                    color = stateValues.TextColor,
                                    fontSize = stateValues.textSize,
                                )
                            else
                                SimpleDropdownField(
                                    title = text("supplier"),
                                    selectedId = draft.supplierId,
                                    placeholder = text("supplier"),
                                    restoreLastChoice = false,
                                    options =
                                        stateValues.suppliers
                                            .orEmpty()
                                            .filter { it.isActive }
                                            .map {
                                                DropdownOption(
                                                    it.id,
                                                    it.name
                                                        .extractLocalizedString(language)
                                                        .orEmpty(),
                                                )
                                            },
                                    onSelected = {
                                        if (!frozen)
                                            draft =
                                                draft.copy(supplierId = it, completedOrderId = null)
                                    },
                                )
                        }
                        item {
                            SimpleTextInput(
                                Modifier.fillMaxWidth(),
                                value = query,
                                placeholder = stateValues.stringSearchByAnyData,
                                autoFocus = false,
                                onValueChange = { query = it.take(150) },
                            )
                        }
                        item {
                            Text(
                                "${text("selected")}: ${draft.quantities.size}",
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize,
                            )
                        }
                        if (!frozen)
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    actionButton(
                                        Modifier.weight(1f),
                                        text = text("select"),
                                        autoLoading = false,
                                        confirmationRequired = false,
                                        onClick = {
                                            val selected = filtered.filter {
                                                it.suggestedQuantity?.let { n -> n > 0 } == true
                                            }
                                            val combined =
                                                selected.associate {
                                                    it.item.goodsItem.id to
                                                        it.suggestedQuantity!!.planNumber()
                                                } + draft.quantities
                                            if (combined.size > 500) {
                                                error = text("selection_error")
                                                return@actionButton
                                            }
                                            draft =
                                                draft.copy(
                                                    quantities = combined,
                                                    completedOrderId = null,
                                                )
                                        },
                                    )
                                    actionButton(
                                        Modifier.weight(1f),
                                        text = text("clear"),
                                        autoLoading = false,
                                        confirmationRequired = false,
                                        onClick = { draft = draft.copy(quantities = emptyMap()) },
                                    )
                                }
                            }
                        if (filtered.isEmpty())
                            item { Text(text("empty"), color = stateValues.PlaceholderTextColor) }
                        items(filtered, key = { it.item.goodsItem.id }) { row ->
                            val id = row.item.goodsItem.id
                            ReplenishmentRow(row, draft.quantities[id], frozen) { value ->
                                if (
                                    value != null &&
                                        id !in draft.quantities &&
                                        draft.quantities.size >= 500
                                )
                                    error = text("selection_error")
                                else
                                    draft =
                                        draft.copy(
                                            quantities =
                                                if (value == null) draft.quantities - id
                                                else draft.quantities + (id to value),
                                            completedOrderId = null,
                                        )
                            }
                        }
                    }
                }
                load.message
                    ?.extractLocalizedString(language)
                    ?.takeIf { it.isNotBlank() && !load.cached }
                    ?.let {
                        Text(
                            it,
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                        )
                    }
                if (frozen)
                    Text(
                        text("pending"),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                    )
                if (draft.completedOrderId != null)
                    Text(
                        text("saved"),
                        color = stateValues.AccentColor,
                        fontSize = stateValues.smallTextSize,
                    )
                if (!frozen && !draft.settings.valid(planningDay))
                    Text(
                        text("invalid_settings"),
                        color = stateValues.ErrorColor,
                        fontSize = stateValues.smallTextSize,
                    )
                if (error.isNotBlank())
                    Text(
                        error,
                        color = stateValues.ErrorColor,
                        fontSize = stateValues.smallTextSize,
                    )
                if (confirming) {
                    val supplierName =
                        stateValues.suppliers
                            .orEmpty()
                            .firstOrNull {
                                it.id == (draft.pendingOrder?.order?.supplierId ?: draft.supplierId)
                            }
                            ?.name
                            ?.extractLocalizedString(language)
                            .orEmpty()
                    StockPlanConfirmation(
                        text("confirm_order")
                            .replace(
                                "{count}",
                                (draft.pendingOrder?.lines?.size ?: draft.quantities.size)
                                    .toString(),
                            )
                            .replace("{supplier}", supplierName),
                        onCancel = { confirming = false },
                        onConfirm = {
                            confirming = false
                            send()
                        },
                    )
                } else
                    actionButton(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        text = text(if (frozen) "retry" else "create"),
                        loading = sending,
                        enabled = !sending,
                        autoLoading = false,
                        confirmationRequired = false,
                        onClick = { confirming = true },
                    )
            }
        }
    report?.let { AnalyticsReportBottomSheet(it) { report = null } }
}

internal fun Double.planNumber(): String = quantityAmountText(false, 3)

@Composable
private fun AppConfiguration.ReplenishmentRow(
    row: ReplenishmentSuggestion,
    value: String?,
    frozen: Boolean,
    change: (String?) -> Unit,
) {
    val language = stateValues.appLanguage
    fun text(key: String) = replenishmentText(key, language)
    var details by remember(row.item.goodsItem.id) { mutableStateOf(false) }
    val unit = row.item.unit
    val blocked = row.item.unlimited || row.item.uncertainUnits
    Column(
        Modifier.fillMaxWidth()
            .border(
                1.dp,
                stateValues.AccentColor.copy(alpha = .4f),
                RoundedCornerShape(stateValues.cornerRadius),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = value != null,
                enabled = !frozen && !blocked,
                onCheckedChange = {
                    if (it)
                        change(row.suggestedQuantity?.takeIf { n -> n > 0 }?.planNumber().orEmpty())
                    else change(null)
                },
            )
            Column(
                Modifier.weight(1f).clickable { details = !details },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    row.item.goodsItem.name.extractLocalizedString(language).orEmpty(),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${text("suggested")}: ${row.suggestedQuantity?.planNumber()?:"—"} ${unit?.immutableUnitName?.extractLocalizedString(language).orEmpty()}",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                )
                Text(
                    "${text("order_by")}: ${row.orderBy}",
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                )
            }
        }
        if (row.item.unlimited)
            Text(
                text("unlimited"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
            )
        else if (row.item.uncertainUnits)
            Text(
                text("units"),
                color = stateValues.ErrorColor,
                fontSize = stateValues.smallTextSize,
            )
        else if (row.item.observationDays == 0)
            Text(
                text("no_history"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
            )
        else if (row.lowHistory)
            Text(
                text("low_history"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
            )
        if (details) {
            listOf(
                    "sold" to row.item.sold,
                    "returns" to row.item.returned,
                    "daily" to row.dailyQuantity,
                    "stock" to row.usableStock,
                    "incoming" to row.confirmedIncoming,
                    "expiring" to row.expiringStock,
                    "undated" to row.undatedIncoming,
                )
                .forEach { (label, amount) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text(label),
                            Modifier.weight(1f),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                        )
                        Text(
                            amount.planNumber(),
                            color = stateValues.TextColor,
                            fontSize = stateValues.smallTextSize,
                        )
                    }
                }
        }
        if (value != null && unit != null) {
            if (frozen)
                Text(
                    "${text("quantity")}: $value",
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                )
            else {
                SimpleTextInput(
                    Modifier.fillMaxWidth(),
                    value = value,
                    placeholder = text("quantity"),
                    autoFocus = false,
                    keyboardType =
                        if (unit.roundTotal) KeyboardType.Number else KeyboardType.Decimal,
                    onValueChange = { change(it.take(40)) },
                )
                StockQuantityQuickFillButtons(
                    unit,
                    value,
                    shortcutAmounts =
                        (listOfNotNull(row.suggestedQuantity?.takeIf { it > 0 }) +
                                listOf(1.0, 5.0, 10.0, 50.0))
                            .distinct(),
                    onAmountSelected = { change(it) },
                )
                val number = value.replace(',', '.').toDoubleOrNull()
                if (
                    value.isNotBlank() &&
                        (number == null ||
                            !number.isFinite() ||
                            number <= 0 ||
                            number > 999_999_999 ||
                            (unit.roundTotal && number % 1.0 != 0.0))
                )
                    Text(
                        text("quantity_error"),
                        color = stateValues.ErrorColor,
                        fontSize = stateValues.smallTextSize,
                    )
            }
        }
    }
}

@Composable
private fun AppConfiguration.ReplenishmentSettingsContent(
    initial: ReplenishmentSettings,
    onApply: (ReplenishmentSettings) -> Unit,
) {
    fun text(key: String) = replenishmentText(key, stateValues.appLanguage)
    var event by remember { mutableStateOf(initial.event) }
    var date by remember { mutableStateOf(initial.arrivalDate) }
    var lead by remember { mutableStateOf(initial.leadDays.toString()) }
    var cover by remember { mutableStateOf(initial.coverDays.toString()) }
    var buffer by remember { mutableStateOf(initial.bufferPercent.toString()) }
    var uplift by remember { mutableStateOf(initial.upliftPercent.toString()) }
    var lookback by remember { mutableStateOf(initial.lookbackDays) }
    var category by remember { mutableStateOf(initial.categoryId) }
    var error by remember { mutableStateOf("") }
    val today = currentStockLocalDate()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SimpleDropdownField(
                    title = text("event"),
                    selectedId = event,
                    placeholder = text("event"),
                    restoreLastChoice = false,
                    options =
                        listOf("regular", "new_year", "nauryz", "school", "kurban", "custom").map {
                            DropdownOption(it, text(it))
                        },
                    onSelected = {
                        event = it
                        date = replenishmentEventDate(it, today)?.toString().orEmpty()
                    },
                )
            }
            if (event != "regular") {
                item {
                    StockDatePartsEditor(
                        title = text("arrival"),
                        dateText = date,
                        onDateChanged = { date = it },
                    )
                }
                item {
                    Text(
                        text("date_help"),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                    )
                }
            }
            item { PlannerNumberField(text("lead"), lead) { lead = it } }
            item { PlannerNumberField(text("cover"), cover) { cover = it } }
            item { PlannerNumberField(text("buffer"), buffer) { buffer = it } }
            item { PlannerNumberField(text("uplift"), uplift) { uplift = it } }
            item {
                SimpleDropdownField(
                    title = text("lookback"),
                    selectedId = lookback.toString(),
                    placeholder = text("lookback"),
                    restoreLastChoice = false,
                    options =
                        listOf(7, 28, 90).map { DropdownOption(it.toString(), it.toString()) },
                    onSelected = { lookback = it.toInt() },
                )
            }
            item {
                SimpleDropdownField(
                    title = stateValues.stringCategory,
                    selectedId = category,
                    placeholder = stateValues.stringSelectCategory,
                    restoreLastChoice = false,
                    options =
                        listOf(DropdownOption("", localizedStringResource(497, "All"))) +
                            stateValues.goodsCategories.orEmpty().map {
                                DropdownOption(
                                    it.id,
                                    it.name.visibleGoodsCategoryName(
                                        stateValues.appLanguage,
                                        it.id,
                                    ),
                                )
                            },
                    onSelected = { category = it },
                )
            }
        }
        if (error.isNotBlank())
            Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        actionButton(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            text = text("apply"),
            autoLoading = false,
            confirmationRequired = false,
            onClick = {
                val settings =
                    ReplenishmentSettings(
                        event,
                        date,
                        lead.toIntOrNull() ?: -1,
                        cover.toIntOrNull() ?: -1,
                        buffer.toIntOrNull() ?: -1,
                        uplift.toIntOrNull() ?: -1,
                        lookback,
                        category,
                    )
                if (settings.valid(today)) onApply(settings) else error = text("invalid_settings")
            },
        )
    }
}

@Composable
private fun AppConfiguration.PlannerNumberField(
    title: String,
    value: String,
    change: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold,
        )
        SimpleTextInput(
            Modifier.fillMaxWidth(),
            value = value,
            placeholder = title,
            autoFocus = false,
            keyboardType = KeyboardType.Number,
            onValueChange = { change(it.take(4)) },
        )
    }
}
