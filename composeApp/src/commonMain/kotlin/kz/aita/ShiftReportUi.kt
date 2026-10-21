package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

internal fun AppConfiguration.pass29Text(key: String) = eventMessage(key).visibleLocalizedString(stateValues.appLanguage, "")
private data class ShiftReportRequest(val owner: MarketRequestScope, val kind: String, val shift: WorkshiftDataModel? = null)
private val shiftReportRequest = MutableStateFlow<ShiftReportRequest?>(null)
internal fun openShiftReport(kind: String, shift: WorkshiftDataModel? = null) {
    val owner = captureMarketRequestScope(activeStoreIdState.value) ?: return
    shiftReportRequest.value = ShiftReportRequest(owner, kind, shift)
}

@Composable
internal fun AppConfiguration.ShiftReportShortcuts() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(SHIFT_REPORT_X, SHIFT_REPORT_Z).forEach { kind ->
            Box(Modifier.weight(1f)) { actionButton(text = pass29Text("shift.${kind.lowercase()}"),
                iconPath = stateValues.drawablePathIconAnalyticsReport, enabledColor = stateValues.BackgroundColor,
                textColor = stateValues.TextColor, confirmationRequired = false, autoLoading = false,
                onClick = { openShiftReport(kind) }) }
        }
    }
}

@Composable
internal fun AppConfiguration.ShiftReportHost() {
    val closed by ShiftReports.closedOffer.collectAsState()
    val requested by shiftReportRequest.collectAsState()
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    LaunchedEffect(account, generation, stateValues.activeStoreId) {
        if (closed?.owner?.isCurrent() != true) ShiftReports.dismissOffer()
        if (requested?.owner?.isCurrent() != true) shiftReportRequest.value = null
    }
    val request = requested
    if (request != null && request.owner.isCurrent()) {
        ShiftReportChooser(request, onDismiss = { shiftReportRequest.value = null })
    } else closed?.takeIf { it.owner.isCurrent() }?.let { offer ->
        AitaBottomSheet(title = pass29Text("shift.closed"), iconPath = stateValues.drawablePathIconAnalyticsReport,
            onDismiss = { ShiftReports.dismissOffer() }) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(offer.shift.workerDisplayName + " · " + receiptUiDateTime(offer.shift.endedAtMillis!!),
                    color = stateValues.TextColor, fontSize = stateValues.textSize)
                actionButton(text = pass29Text("pass29.view"), confirmationRequired = false, autoLoading = false,
                    onClick = { openShiftReport(SHIFT_REPORT_Z, offer.shift); ShiftReports.dismissOffer() })
                actionButton(text = pass29Text("pass29.later"), confirmationRequired = false, autoLoading = false,
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                    onClick = { ShiftReports.dismissOffer() })
            }
        }
    }
}

@Composable
private fun AppConfiguration.ShiftReportChooser(request: ShiftReportRequest, onDismiss: () -> Unit) {
    var kind by remember(request) { mutableStateOf(request.kind) }
    var shifts by remember(request) { mutableStateOf(listOfNotNull(request.shift)) }
    var selected by remember(request) { mutableStateOf(request.shift?.id) }
    var loading by remember(request) { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    var failure by remember(request) { mutableStateOf<String?>(null) }
    var report by remember(request) { mutableStateOf<ShiftReport?>(null) }
    var preview by remember(request) { mutableStateOf<AnalyticsReportSnapshotDataModel?>(null) }
    val available = shifts.filter { if (kind == SHIFT_REPORT_X) it.isActive && it.endedAtMillis == null else !it.isActive && it.endedAtMillis != null }
    LaunchedEffect(request, revision) {
        loading = true
        try {
            val cached = ShiftReports.cachedShifts(request.owner)
            if (request.owner.isCurrent()) shifts = (listOfNotNull(request.shift) + cached).distinctBy { it.id }
            val response = ShiftReports.loadShifts(request.owner)
            if (request.owner.isCurrent()) {
                val payload = response.payload
                if (!response.negative && payload != null) {
                    // Preserve an offline close until its queued operation reaches the cloud.
                    val localClosed = shifts.filter { !it.isActive && it.endedAtMillis != null }.associateBy { it.id }
                    shifts = (payload.map { localClosed[it.id]?.takeIf { closed -> it.isActive && closed.workerUserId == request.owner.accountId } ?: it } + shifts)
                        .distinctBy { it.id }.sortedByDescending { it.startedAtMillis }
                    failure = null
                } else {
                    failure = response.message?.visibleLocalizedString(stateValues.appLanguage, "")?.takeIf { it.isNotBlank() } ?: pass29Text("shift.unavailable")
                    if (response.httpStatusCode == 403) shifts = emptyList()
                }
            }
        } catch (c: CancellationException) { throw c }
        catch (_: Exception) { failure = pass29Text("shift.unavailable") }
        finally { loading = false }
    }
    LaunchedEffect(available, kind) {
        if (available.none { it.id == selected }) selected = available.firstOrNull()?.id
    }
    val shift = available.firstOrNull { it.id == selected }
    var reportLoading by remember { mutableStateOf(false) }
    LaunchedEffect(shift, kind, revision) {
        report = null
        if (shift == null) return@LaunchedEffect
        reportLoading = true
        try {
            val response = ShiftReports.load(request.owner, shift, kind)
            if (request.owner.isCurrent()) {
                report = response.payload
                failure = if (report != null) null else response.message?.visibleLocalizedString(stateValues.appLanguage, "") ?: pass29Text("shift.unavailable")
            }
        } catch (c: CancellationException) { throw c }
        catch (_: Exception) { failure = pass29Text("shift.unavailable") }
        finally { reportLoading = false }
    }
    preview?.let { AnalyticsReportBottomSheet(it, onDismiss); return }
    AitaBottomSheet(title = pass29Text("shift.title"), iconPath = stateValues.drawablePathIconAnalyticsReport, onDismiss = onDismiss) {
        tabRowWidget(tabs = listOf(SHIFT_REPORT_X, SHIFT_REPORT_Z).map { value ->
            TabContent(value, pass29Text("shift.${value.lowercase()}"), icon = AitaTabIcon.Calendar) { kind = it }
        }, selectedIndexInitial = kind)
        Text(pass29Text(if (kind == SHIFT_REPORT_X) "shift.x_note" else "shift.z_note"),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize, modifier = Modifier.padding(vertical = 12.dp))
        if (available.isNotEmpty()) AitaDropdownField(Modifier.fillMaxWidth(), pass29Text("shift.select"), selected,
            available.map { DropdownOption(it.id, "${it.workerDisplayName} · ${receiptUiDateTime(it.startedAtMillis)}",
                subtitle = it.endedAtMillis?.let(::receiptUiDateTime)) }, pass29Text("shift.select"), onSelected = { selected = it })
        if (loading || reportLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp), color = stateValues.AccentColor)
        failure?.let { Text(it, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize, modifier = Modifier.padding(vertical = 12.dp)) }
        if (!loading && available.isEmpty()) Text(pass29Text(if (kind == SHIFT_REPORT_X) "shift.none_x" else "shift.none_z"),
            color = stateValues.TextColor, fontSize = stateValues.textSize, modifier = Modifier.padding(vertical = 16.dp))
        val snapshot = report?.let { shiftReportSnapshot(it) }
        if (snapshot != null) AnalyticsReportPreview(snapshot, Modifier.weight(1f).padding(vertical = 12.dp))
        else Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { actionButton(text = pass29Text("pass29.refresh"), iconPath = stateValues.drawablePathIconRefresh,
                loading = loading || reportLoading, autoLoading = false, confirmationRequired = false,
                onClick = { if (!loading && !reportLoading) revision++ }) }
            if (snapshot != null) Box(Modifier.weight(1f)) { actionButton(text = pass29Text("pass29.view"),
                iconPath = stateValues.drawablePathIconAnalyticsReport, autoLoading = false, confirmationRequired = false,
                onClick = { preview = snapshot }) }
        }
    }
}

private fun AppConfiguration.shiftReportSnapshot(report: ShiftReport): AnalyticsReportSnapshotDataModel {
    fun row(title: String, value: String) = AnalyticsReportRowDataModel(title, value)
    return AnalyticsReportSnapshotDataModel(pass29Text("shift.${report.kind.lowercase()}"),
        report.shift.storeName.visibleLocalizedString(stateValues.appLanguage, report.shift.storePublicId),
        receiptUiDateTime(report.shift.startedAtMillis) + " — " + receiptUiDateTime(report.throughMillis),
        report.shift.workerDisplayName, report.generatedAtMillis,
        listOf(AnalyticsReportSectionDataModel(pass29Text("shift.receipts"),
            listOf(row(pass29Text("shift.receipts"), report.transactionCount.toString())),
            listOfNotNull(pass29Text("shift.operational"), pass29Text("shift.local").takeIf { report.localOnly }))) +
            report.totals.map { t -> AnalyticsReportSectionDataModel(t.currencyCode, listOf(
                row(stateValues.stringSale, t.sales.money(t.currencyCode)),
                row(stateValues.stringReturn, t.returns.money(t.currencyCode)),
                row(localizedStringResource(697, "Net revenue"), (t.sales - t.returns).money(t.currencyCode)),
                row(quickDiscountLabel(stateValues.appLanguage), t.discount.money(t.currencyCode)),
                row(stateValues.stringCash, if (t.paymentsKnown) (t.saleCash - t.returnCash).money(t.currencyCode) else "—"),
                row(stateValues.stringCashless, if (t.paymentsKnown) (t.saleCard - t.returnCard).money(t.currencyCode) else "—"),
                row(stateValues.stringDebt, if (t.paymentsKnown) t.debt.money(t.currencyCode) else "—"),
                row(stateValues.stringSupply, t.supplies.money(t.currencyCode))
            ), if (t.paymentsKnown) emptyList() else listOf(pass29Text("shift.payments_unknown"))) })
}
