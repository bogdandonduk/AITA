package kz.aita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlin.math.abs

@Composable
internal fun AppConfiguration.MarketPriceInsightsEntry(modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    if (!canUseMarketPriceInsights(stateValues.activeStoreId)) return
    val openTool = stockWorkspaceToolLauncher()
    actionButton(modifier, text = marketPriceText("title", stateValues.appLanguage),
        iconRes = tabIconResource(AitaTabIcon.Compare), autoLoading = false, confirmationRequired = false,
        onClick = { onOpen?.invoke() ?: openTool(StockWorkspaceTool.MARKET_PRICES) })
}

private fun priceDifferenceText(row: MarketPriceInsightRow, language: String): String {
    val value = marketPriceDifferenceBasisPoints(row.ownPriceMinor, row.statistics?.medianMinor) ?: return ""
    val magnitude = abs(value)
    val fraction = (magnitude % 100).toString().padStart(2, '0').trimEnd('0')
    val percent = "${magnitude / 100}${if (fraction.isEmpty()) "" else ".$fraction"}%"
    return if (value == 0L) marketPriceText("same", language)
        else "$percent · ${marketPriceText(if (value > 0) "above" else "below", language)}"
}

private fun priceRowReason(row: MarketPriceInsightRow): String = when {
    row.barcodes.isEmpty() -> "no_gtin"
    row.basis == null || row.ownPriceMinor == null -> "no_price"
    else -> "not_enough"
}

internal fun marketPriceQuantityLabel(row: MarketPriceInsightRow, language: String): String? {
    val basis = row.basis ?: return null
    val unit = row.unitName.extractLocalizedString(language).orEmpty().ifBlank { when (basis.unitId) {
        "1" -> marketPriceText("kilogram", language)
        GRAMS_UNIT_ID -> marketPriceText("gram", language)
        "0" -> marketPriceText("piece", language)
        else -> basis.unitId
    } }
    return "${basis.pricedAmount.toString().removeSuffix(".0")} $unit"
}

@Composable
internal fun AppConfiguration.MarketPriceInsightsSheet(initialRequest: MarketPriceRequest? = null,
    openAlerts: Boolean = false, onDismiss: () -> Unit) {
    val language = stateValues.appLanguage
    fun text(key: String) = marketPriceText(key, language)
    val owner = remember { captureReceiptActionOwner() }
    val store = remember { stateValues.activeStoreId.orEmpty() }
    val inventoryScope = remember { inventoryViewScopeKey() }
    val status by stockLoadStatusState.collectAsState()
    fun current() = owner.isCurrent() && inventoryViewScopeKey() == inventoryScope && canUseMarketPriceInsights(store)
    val defaultCountry = remember { stateValues.stores.orEmpty().findStoreOrBranch(store)?.countryLocales?.firstOrNull { it in marketPriceCountries } ?: "kz" }
    var selection by remember { mutableStateOf(MarketPriceRequest(country = defaultCountry)) }
    var inputCountry by remember { mutableStateOf(defaultCountry) }
    var inputCity by remember { mutableStateOf("") }
    var inputSearch by remember { mutableStateOf("") }
    var restored by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var load by remember { mutableStateOf(MarketPriceLoad()) }
    var savedReports by remember { mutableStateOf<List<SavedMarketPriceReport>>(emptyList()) }
    var revision by remember { mutableStateOf(0) }
    var previousPages by remember { mutableStateOf<List<MarketPriceRequest>>(emptyList()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var method by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var watchExpanded by remember { mutableStateOf(openAlerts) }
    var filter by remember { mutableStateOf("all") }
    var error by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<AnalyticsReportSnapshotDataModel?>(null) }
    val scroll = rememberLazyListState()
    val page = load.page?.takeIf { it.request == selection && it.storeId == store }
    val latestSelection by rememberUpdatedState(selection)
    val latestRevision by rememberUpdatedState(revision)
    val watchScope = rememberCoroutineScope()
    val watch = remember { MarketPriceWatchController(watchScope, ::current) }
    LaunchedEffect(watch) { watch.refresh() }
    LaunchedEffect(watch.denied) {
        if (watch.denied) { load = MarketPriceLoad(message = marketPriceMessage("access"), accessDenied = true); savedReports = emptyList(); report = null }
    }
    LaunchedEffect(stateValues.activeStoreId, stateValues.userAccount?.id, status) { if (!current()) onDismiss() }
    LaunchedEffect(Unit) {
        val saved = initialRequest?.normalizedMarketPriceRequest() ?: restoreMarketPriceSelection()
        if (current() && saved != null) {
            selection = saved; inputCountry = saved.country; inputCity = saved.city; inputSearch = saved.search
        }
        restored = true
    }
    LaunchedEffect(restored, selection, revision) {
        if (!restored || !current()) return@LaunchedEffect
        val requested = selection
        val requestRevision = revision
        fun accepts() = current() && latestSelection == requested && latestRevision == requestRevision
        loading = true
        error = ""
        if (load.page?.request != requested) { expanded = null; load = MarketPriceLoad() }
        else load = load.copy(message = null)
        try {
            val availableReports = savedMarketPriceReports()
            if (accepts()) savedReports = availableReports
            val result = loadMarketPriceInsights(requested) { cached ->
                withContext(Dispatchers.Main) { if (accepts() && (load.page?.checkedAtMillis ?: 0) <= (cached.page?.checkedAtMillis ?: 0)) load = cached }
            }
            if (accepts()) {
                val visible = load.takeIf { it.page?.request == requested }
                val visiblePage = visible?.page
                val resultPage = result.page
                load = if (!result.accessDenied && visiblePage != null &&
                    (resultPage == null || (result.cached && resultPage.checkedAtMillis < visiblePage.checkedAtMillis)))
                    visible.copy(cached = true, message = result.message) else result
                val confirmedReports = if (result.accessDenied) emptyList() else savedMarketPriceReports()
                if (accepts()) savedReports = confirmedReports
            }
        } finally { if (accepts()) loading = false }
    }
    LaunchedEffect(selection) {
        // Back navigation and saved reports restore the entire selected scope.
        // Draft-only edits do not change selection and therefore remain untouched.
        inputCountry = selection.country
        inputCity = selection.city
        inputSearch = selection.search
        scroll.scrollToItem(0)
    }
    fun choose(next: MarketPriceRequest, rememberPrevious: Boolean = false) {
        val normalized = next.normalizedMarketPriceRequest()
        if (normalized == null) { error = text("invalid"); return }
        if (rememberPrevious) previousPages = (previousPages + selection).takeLast(100) else previousPages = emptyList()
        selection = normalized
        revision++
        filter = "all"
        controls = false
    }
    if (!current()) return
    if (report == null) AitaBottomSheet(title = text("title"), iconPath = stateValues.drawablePathIconAnalytics, onDismiss = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = scroll, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(text("intro"), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
            }
            item {
                actionButton(Modifier.fillMaxWidth(), text = "${text(selection.country)} · ${selection.city.ifBlank { text("all_cities") }}",
                    iconPath = if (controls) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore,
                    enabledColor = stateValues.BackgroundColor,
                    textColor = stateValues.TextColor, autoLoading = false, confirmationRequired = false,
                    onClick = { controls = !controls })
            }
            if (controls) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (savedReports.isNotEmpty()) SimpleDropdownField(title = text("saved_reports"), selectedId = null,
                        options = savedReports.mapIndexed { index, saved -> DropdownOption(index.toString(),
                            listOf(text(saved.request.country), saved.request.city.ifBlank { text("all_cities") }, saved.request.search).filter { it.isNotBlank() }.joinToString(" · "),
                            subtitle = "${receiptUiDateTime(saved.checkedAtMillis)} · ${saved.itemCount} ${text("items")}") },
                        placeholder = text("saved_choose"), restoreLastChoice = false, onSelected = { id ->
                            savedReports.getOrNull(id.toIntOrNull() ?: -1)?.request?.let {
                                inputCountry = it.country; inputCity = it.city; inputSearch = it.search
                                choose(it, rememberPrevious = true)
                            }
                        })
                    SimpleDropdownField(title = text("country"), selectedId = inputCountry,
                        options = marketPriceCountries.map { DropdownOption(it, text(it)) }, placeholder = text("country"),
                        restoreLastChoice = false, onSelected = { inputCountry = it; inputCity = "" })
                    genericTextField(Modifier.fillMaxWidth(), titleText = text("city"), placeholderText = text("all_cities"),
                        valueInitial = inputCity, parentOwnsValue = true, retainTextAcrossRecreation = false, persistTextDraft = false,
                        autoFocus = false, enableVoiceInput = false, onValueChange = { value, apply -> inputCity = value; apply() })
                    val cities = page?.takeIf { it.request.country == inputCountry }?.cities.orEmpty()
                    if (cities.isNotEmpty()) SimpleDropdownField(title = "", selectedId = inputCity,
                        options = listOf(DropdownOption("", text("all_cities"))) + cities.map { DropdownOption(it, it) },
                        placeholder = text("city_help"), restoreLastChoice = false, onSelected = { inputCity = it })
                    genericTextField(Modifier.fillMaxWidth(), placeholderText = text("search"), valueInitial = inputSearch,
                        parentOwnsValue = true, retainTextAcrossRecreation = false, persistTextDraft = false, autoFocus = false,
                        enableVoiceInput = false, onSubmitInput = { choose(MarketPriceRequest(inputCountry, inputCity, inputSearch)) },
                        onValueChange = { value, apply -> inputSearch = value; apply() })
                    if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    actionButton(Modifier.fillMaxWidth(), text = text("compare"), iconRes = tabIconResource(AitaTabIcon.Compare),
                        loading = loading, autoLoading = false, confirmationRequired = false,
                        onClick = { choose(MarketPriceRequest(inputCountry, inputCity, inputSearch)) })
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actionButton(Modifier.weight(1f), text = text("refresh"), iconPath = stateValues.drawablePathIconRefresh,
                        loading = loading, autoLoading = false, confirmationRequired = false, onClick = { revision++ })
                    actionButton(Modifier.weight(1f), text = text("print"), iconRes = tabIconResource(AitaTabIcon.Receipt),
                        autoLoading = false, confirmationRequired = false, onClick = {
                            val data = page
                            if (data == null) error = text(if (loading) "loading" else "offline_empty")
                            else report = marketPriceReport(data, load.previous, load.cached, language,
                                stateValues.stores.orEmpty().findStoreOrBranch(store)?.name?.extractLocalizedString(language).orEmpty())
                        })
                }
            }
            if (error.isNotBlank() && !controls) item { Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
            load.message?.takeUnless { load.cached && it == marketPriceMessage("cached") }?.let { message ->
                item { Text(message.extractLocalizedString(language).orEmpty(), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
            }
            if (load.cached) item { Text(text("cached"), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize) }
            if (loading && page == null) item { Text(text("loading"), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.textSize) }
            item {
                actionButton(Modifier.fillMaxWidth(), text = "${text("watch_title")} · ${if (watch.state == null) "—" else watch.count.toString()} / $MARKET_PRICE_WATCH_MAX_ITEMS",
                    iconRes = tabIconResource(AitaTabIcon.Clock), enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                    autoLoading = false, confirmationRequired = false, onClick = { watchExpanded = !watchExpanded })
                if (watchExpanded) Box(Modifier.padding(top = 10.dp)) { MarketPriceWatchPanel(watch) }
            }
            page?.let { data ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${text("checked")}: ${receiptUiDateTime(data.checkedAtMillis)}", color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
                        if (data.request.search.isNotBlank()) Text("${text("search")}: ${data.request.search}", color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                        Text("${text("coverage")}: ${data.rows.count { it.statistics != null }} / ${data.rows.size}",
                            color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                    }
                }
                if (previousPages.isNotEmpty() || data.nextId != null) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (previousPages.isNotEmpty()) actionButton(Modifier.weight(1f), text = text("back"),
                            textSize = stateValues.smallTextSize, loading = loading, autoLoading = false, confirmationRequired = false,
                            onClick = { selection = previousPages.last(); previousPages = previousPages.dropLast(1); revision++; filter = "all" })
                        if (data.nextId != null) actionButton(Modifier.weight(1f), text = text("next"),
                            textSize = stateValues.smallTextSize, loading = loading, autoLoading = false, confirmationRequired = false,
                            onClick = { choose(data.request.copy(after = data.nextId), rememberPrevious = true) })
                    }
                }
                item {
                    tabRowWidget(Modifier.fillMaxWidth(), selectedIndexInitial = filter, compact = true,
                        textSize = stateValues.smallTextSize, tabs = listOf(
                            TabContent("all", text("all"), icon = AitaTabIcon.All) { filter = it },
                            TabContent("above", text("above"), icon = AitaTabIcon.Ascending) { filter = it },
                            TabContent("below", text("below"), icon = AitaTabIcon.Descending) { filter = it },
                            TabContent("no_data", text("no_data"), icon = AitaTabIcon.Info) { filter = it }))
                }
                val rows = data.rows.filter { row -> when (filter) {
                    "above" -> (marketPriceDifferenceBasisPoints(row.ownPriceMinor, row.statistics?.medianMinor) ?: 0) > 0
                    "below" -> (marketPriceDifferenceBasisPoints(row.ownPriceMinor, row.statistics?.medianMinor) ?: 0) < 0
                    "no_data" -> row.statistics == null
                    else -> true
                } }
                if (rows.isEmpty()) item { Text(text("empty"), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.textSize) }
                items(rows, key = { it.goodsItemId }) { row ->
                    MarketPriceInsightCard(row, data, load.previous, expanded == row.goodsItemId,
                        onExpand = { expanded = if (expanded == row.goodsItemId) null else row.goodsItemId },
                        onBarcode = { code -> choose(data.request.copy(itemId = row.goodsItemId, gtin = code, after = null), rememberPrevious = true) },
                        watch = watch)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (previousPages.isNotEmpty()) actionButton(Modifier.weight(1f), text = text("back"),
                            iconRes = tabIconResource(AitaTabIcon.Recent), loading = loading, autoLoading = false, confirmationRequired = false,
                            onClick = { selection = previousPages.last(); previousPages = previousPages.dropLast(1); revision++; filter = "all" })
                        if (data.nextId != null) actionButton(Modifier.weight(1f), text = text("next"), loading = loading,
                            autoLoading = false, confirmationRequired = false, onClick = { choose(data.request.copy(after = data.nextId), rememberPrevious = true) })
                    }
                }
            }
            item {
                actionButton(Modifier.fillMaxWidth(), text = text("method"), iconRes = tabIconResource(AitaTabIcon.Info),
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                    autoLoading = false, confirmationRequired = false, onClick = { method = !method })
                if (method) Text(text("method_help"), Modifier.padding(top = 8.dp), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
            }
        }
    }
    report?.let { AnalyticsReportBottomSheet(it) { report = null } }
}

@Composable
internal fun AppConfiguration.MarketPriceInsightCard(row: MarketPriceInsightRow, page: MarketPriceInsightsPage,
    previous: MarketPriceInsightsPage?, expanded: Boolean, onExpand: () -> Unit, onBarcode: (String) -> Unit,
    watch: MarketPriceWatchController? = null) {
    val language = stateValues.appLanguage
    fun text(key: String) = marketPriceText(key, language)
    val currency = row.ownCurrencyCode.orEmpty()
    val shape = RoundedCornerShape(stateValues.cornerRadius)
    Column(Modifier.fillMaxWidth().padding(2.dp).clip(shape).background(stateValues.BackgroundColor)
        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = .35f), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text(row.name.extractLocalizedString(language).orEmpty().ifBlank { row.barcodes.firstOrNull() ?: row.goodsItemId },
                modifier = Modifier.weight(1f), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            actionButton(Modifier.size(44.dp), text = "", iconContentDescription = text("details"),
                iconPath = if (expanded) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore,
                enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                autoLoading = false, confirmationRequired = false, onClick = onExpand)
        }
        row.basis?.let { basis ->
            Text("${marketPriceQuantityLabel(row, language)} · ${basis.gtin}", color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(text("own"), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
                Text(row.ownPriceMinor?.let { marketMoneyLabel(it, currency) } ?: "—", color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(text("median"), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
                Text(row.statistics?.let { marketMoneyLabel(it.medianMinor, currency) } ?: "—", color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            }
        }
        if (row.statistics != null) {
            Text(priceDifferenceText(row, language), color = stateValues.TextColor, fontSize = stateValues.textSize)
            PriceDistribution(row)
        } else Text(text(priceRowReason(row)), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
        Text("${text("groups")}: ${row.compatibleGroups}", color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
        if (row.sampleLimited) Text(text("limited"), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
        if (expanded) {
            Text(text(if (row.referenceKind == "shelf") "shelf" else if (row.referenceKind == "catalogue") "catalogue" else "no_price"),
                color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
            row.statistics?.let { stats ->
                Text("${text("typical")}: ${marketMoneyLabel(stats.lowerQuartileMinor, currency)} — ${marketMoneyLabel(stats.upperQuartileMinor, currency)}",
                    color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                Text("${text("range")}: ${marketMoneyLabel(stats.lowestMinor, currency)} — ${marketMoneyLabel(stats.highestMinor, currency)}",
                    color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
            }
            row.previousObservedMedian(previous, page)?.let { before ->
                Text("${text("previous")}: ${marketMoneyLabel(before, currency)} · ${receiptUiDateTime(requireNotNull(previous).checkedAtMillis)}",
                    color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                Text(text("history_hint"), color = stateValues.TextColor.copy(alpha = .65f), fontSize = stateValues.smallTextSize)
            }
            if (row.barcodes.size > 1) SimpleDropdownField(title = stateValues.stringBarcode, selectedId = row.basis?.gtin,
                options = row.barcodes.map { DropdownOption(it, it) }, placeholder = stateValues.stringBarcode,
                restoreLastChoice = false, onSelected = onBarcode)
            if (watch != null && row.basis != null) {
                watch.message?.let { Text(it.extractLocalizedString(language).orEmpty(), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize) }
                actionButton(Modifier.fillMaxWidth(), text = text(if (watch.follows(row)) "watch_off" else "watch_on"),
                    iconRes = tabIconResource(AitaTabIcon.Clock), loading = watch.busy, autoLoading = false, confirmationRequired = false,
                    onClick = { watch.toggle(row, page.request) })
            }
        }
    }
}

@Composable
private fun AppConfiguration.PriceDistribution(row: MarketPriceInsightRow) {
    val stats = row.statistics ?: return
    val own = row.ownPriceMinor ?: return
    val low = minOf(stats.lowestMinor, own)
    val high = maxOf(stats.highestMinor, own)
    val neutral = stateValues.PlaceholderTextColor
    val accent = stateValues.AccentColor
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        fun x(value: Long): Float = if (high == low) size.width / 2 else 6.dp.toPx() +
            ((value - low).toDouble() / (high - low)).toFloat() * (size.width - 12.dp.toPx()).coerceAtLeast(0f)
        val y = size.height / 2
        drawLine(neutral.copy(alpha = .3f), Offset(x(stats.lowestMinor), y), Offset(x(stats.highestMinor), y), 2.dp.toPx(), StrokeCap.Round)
        drawLine(neutral.copy(alpha = .55f), Offset(x(stats.lowerQuartileMinor), y), Offset(x(stats.upperQuartileMinor), y), 8.dp.toPx(), StrokeCap.Round)
        drawLine(neutral, Offset(x(stats.medianMinor), y - 7.dp.toPx()), Offset(x(stats.medianMinor), y + 7.dp.toPx()), 2.dp.toPx())
        drawCircle(accent, 5.dp.toPx(), Offset(x(own), y))
    }
}

internal fun marketPriceReport(page: MarketPriceInsightsPage, previous: MarketPriceInsightsPage?, cached: Boolean,
    language: String, storeName: String): AnalyticsReportSnapshotDataModel {
    fun text(key: String) = marketPriceText(key, language)
    return AnalyticsReportSnapshotDataModel(text("title"), storeName, receiptUiDateTime(page.checkedAtMillis),
        listOf(text(page.request.country), page.request.city.ifBlank { text("all_cities") }, page.request.search).filter { it.isNotBlank() }.joinToString(" · "), page.checkedAtMillis,
        listOf(AnalyticsReportSectionDataModel(text("method"), notes = listOfNotNull(
            text("method_help"), if (cached) text("cached") else null,
            "${text("coverage")}: ${page.rows.count { it.statistics != null }} / ${page.rows.size}"))) + page.rows.map { row ->
            val currency = row.ownCurrencyCode.orEmpty()
            AnalyticsReportSectionDataModel(row.name.extractLocalizedString(language).orEmpty().ifBlank { row.goodsItemId },
                rows = buildList {
                    add(AnalyticsReportRowDataModel(text("own"), row.ownPriceMinor?.let { marketMoneyLabel(it, currency) } ?: "—", text(if (row.referenceKind == "unpriced") "no_price" else row.referenceKind)))
                    row.statistics?.let {
                        add(AnalyticsReportRowDataModel(text("median"), marketMoneyLabel(it.medianMinor, currency), priceDifferenceText(row, language)))
                        add(AnalyticsReportRowDataModel(text("typical"), "${marketMoneyLabel(it.lowerQuartileMinor, currency)} — ${marketMoneyLabel(it.upperQuartileMinor, currency)}"))
                        add(AnalyticsReportRowDataModel(text("range"), "${marketMoneyLabel(it.lowestMinor, currency)} — ${marketMoneyLabel(it.highestMinor, currency)}"))
                    }
                    add(AnalyticsReportRowDataModel(text("groups"), row.compatibleGroups.toString()))
                    row.previousObservedMedian(previous, page)?.let { add(AnalyticsReportRowDataModel(text("previous"), marketMoneyLabel(it, currency),
                        "${receiptUiDateTime(requireNotNull(previous).checkedAtMillis)} · ${text("history_hint")}")) }
                }, notes = listOfNotNull(row.basis?.let { "${it.gtin} · ${marketPriceQuantityLabel(row, language)} · ${it.currencyCode}" },
                    if (row.statistics == null) text(priceRowReason(row)) else null, if (row.sampleLimited) text("limited") else null))
        }, keepSectionsTogether = true)
}
