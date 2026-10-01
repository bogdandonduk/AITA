// THIS IS CommonMainCompose.kt split slice: MenuB
@file:OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
package kz.aita

import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.ExperimentalTime

@Composable
internal fun AppConfiguration.SecuritySessionInfoLine(
    title: String,
    value: String,
    accent: Boolean = false
) {
    if (value.isBlank()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = if (accent) stateValues.AccentColor else stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun AppConfiguration.SecuritySessionCard(session: SecuritySessionDataModel) {
    val title = session.deviceName
        .ifBlank { session.platformName }
        .ifBlank { localizedStringResource(230, "Unknown device") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .run { if (session.current) this else foregroundTactileShadow(stateValues.cornerRadius, elevated = false) }
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (session.current) stateValues.AccentColor.copy(alpha = 0.10f) else stateValues.BackgroundColor)
            .border(
                if (session.current) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (session.current) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CpImage(
                modifier = Modifier.size(30.dp),
                url = stateValues.drawablePathIconDevices,
                fallbackRes = stateValues.drawableResIconDevices.value,
                contentDescription = title,
                tintColor = stateValues.AccentColor
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(
                        session.platformName,
                        session.osName,
                        session.appVersion.takeIf { it.isNotBlank() }?.let { "AITA $it" }.orEmpty()
                    ).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { localizedStringResource(231, "Active session") },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (session.current) {
                Text(
                    text = localizedStringResource(232, "Current"),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        SecuritySessionInfoLine(localizedStringResource(233, "Signed in"), securitySessionDateTimeText(session.createdAtMillis))
        SecuritySessionInfoLine(localizedStringResource(234, "Expires"), securitySessionDateTimeText(session.expiresAtMillis))
        SecuritySessionInfoLine("IP", securitySessionDisplayIp(session.ipAddress))
        SecuritySessionInfoLine(localizedStringResource(235, "Language"), session.localeLanguage)
        // User-agent is intentionally hidden from the card; platform and app version above are readable enough.

        if (!session.current) {
            Spacer(modifier = Modifier.height(10.dp))
            actionButton(
                text = localizedStringResource(212, "Revoke session"),
                iconPath = stateValues.drawablePathIconDelete,
                enabledColor = stateValues.ErrorColor,
                confirmationRequired = true,
                onClick = { revokeSecuritySession(session.id) }
            )
        }
    }
}

internal fun AppConfiguration.securitySessionEventFallbackTitle(eventType: String): String = when (eventType) {
    "session_created" -> localizedStringResource(1129, "Session created")
    "session_refreshed" -> localizedStringResource(1130, "Session refreshed")
    "session_replaced" -> localizedStringResource(1131, "Older session replaced")
    "session_revoked" -> localizedStringResource(1132, "Session revoked")
    "session_revoked_others" -> localizedStringResource(1133, "Other session revoked")
    "session_logout" -> localizedStringResource(1134, "Logged out")
    "session_expired" -> localizedStringResource(1135, "Session expired")
    else -> localizedStringResource(1136, "Security event")
}

@Composable
internal fun AppConfiguration.SecuritySessionHistoryCard(event: SecuritySessionHistoryDataModel) {
    val title = event.title.visibleLocalizedString(
        stateValues.appLanguage,
        securitySessionEventFallbackTitle(event.eventType)
    )
    val device = event.deviceName
        .ifBlank { event.platformName }
        .ifBlank { localizedStringResource(230, "Unknown device") }
    val displayMetadata = securitySessionDisplayMetadata(event.metadata)
    val deviceDetails = listOf(
        event.platformName,
        event.osName,
        event.appVersion.takeIf { it.isNotBlank() }?.let { "AITA $it" }.orEmpty()
    ).filter { it.isNotBlank() }.distinct().joinToString(" • ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CpImage(
                modifier = Modifier.size(30.dp),
                url = stateValues.drawablePathIconSecurity,
                fallbackRes = stateValues.drawableResIconSecurity.value,
                contentDescription = title,
                tintColor = stateValues.AccentColor
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = securitySessionDateTimeText(event.createdAtMillis),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        SecuritySessionInfoLine(localizedStringResource(1124, "Event time"), securitySessionDateTimeText(event.createdAtMillis))
        SecuritySessionInfoLine(localizedStringResource(1126, "Device"), device)
        SecuritySessionInfoLine(localizedStringResource(235, "Language"), displayMetadata["localeLanguage"].orEmpty())
        SecuritySessionInfoLine("IP", securitySessionDisplayIp(event.ipAddress))
        SecuritySessionInfoLine(localizedStringResource(1127, "Details"), deviceDetails)
    }
}

@Composable
fun AppConfiguration.MenuSecurityScreen() {
    CompositionLocalProvider(LocalLoadingAnimationsEnabled provides false) { MenuSecurityContent() }
}

@Composable
private fun AppConfiguration.MenuSecurityContent() {
    val sessionsState by securitySessionsState.value.collectAsState()
    val historyState by securitySessionHistoryState.value.collectAsState()
    val sessions = (sessionsState as? DataState.Success<List<SecuritySessionDataModel>>)?.payload.orEmpty()
    val history = (historyState as? DataState.Success<List<SecuritySessionHistoryDataModel>>)?.payload.orEmpty()
    val currentSession = sessions.firstOrNull { it.current }
    val securityOwnerId = stateValues.userAccount?.id.orEmpty()
    var selectedSecurityTab by rememberNavigationSection("account:access", "signin")

    LaunchedEffect(selectedSecurityTab, securityOwnerId) {
        if (securityOwnerId.isBlank()) return@LaunchedEffect
        when (selectedSecurityTab) {
            "sessions" -> getSecuritySessions()
            "history" -> getSecuritySessionHistory()
        }
    }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = authUiText("Sign-in & security", "Вход и безопасность", "Кіру және қауіпсіздік", "Кирүү жана коопсуздук"),
                iconPath = stateValues.drawablePathIconSecurity,
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .padding(horizontal = stateValues.marginTextField, vertical = 8.dp)
        ) {
            val selectedTab = tabRowWidget(
                modifier = Modifier.fillMaxWidth(),
                selectedIndexInitial = selectedSecurityTab,
                tabs = listOf(
                    TabContent("signin", authUiText("Sign-in", "Вход", "Кіру", "Кирүү")),
                    TabContent("sessions", tabLabelWithCount(authUiText("Sessions", "Сессии", "Сессиялар", "Сессиялар"), sessions.size)),
                    TabContent("history", tabLabelWithCount(authUiText("History", "История", "Тарих", "Тарых"), history.size))
                ),
                unselectedContainerColor = stateValues.BackgroundColor
            )

            LaunchedEffect(selectedTab.id) {
                selectedSecurityTab = selectedTab.id
            }
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(
                NavigationScreenModel.Menu.Security,
                "$securityOwnerId:$selectedSecurityTab"
            ),
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .weight(1f).imePadding(),
            contentPadding = PaddingValues(start = stateValues.marginTextFieldGroup, end = stateValues.marginTextFieldGroup, top = stateValues.marginTextFieldGroup, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextFieldGroup)
        ) {
            if (selectedSecurityTab == "signin") {
                item {
                    key(securityOwnerId) {
                        AccountAuthenticationSettingsCard(
                            initiallyExpanded = true,
                            collapsible = false
                        )
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .foregroundTactileShadow(stateValues.cornerRadius)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(stateValues.BackgroundColor)
                            .border(
                                stateValues.unfocusedBorderWidth,
                                stateValues.PlaceholderTextColor,
                                RoundedCornerShape(stateValues.cornerRadius)
                            )
                            .padding(stateValues.marginTextFieldGroup)
                    ) {
                        Text(
                            text = localizedStringResource(226, "Account security"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.titleTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (selectedSecurityTab == "history") {
                                localizedStringResource(1123, "All sign-ins, refreshes and revocations are stored here.")
                            } else {
                                localizedStringResource(227, "Review where your account is signed in. Revoke sessions you do not recognize.")
                            },
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                        currentSession?.takeIf { selectedSecurityTab == "sessions" }?.let {
                            Spacer(modifier = Modifier.height(10.dp))
                            SecuritySessionInfoLine(
                                localizedStringResource(228, "This device"),
                                it.deviceName.ifBlank { it.platformName }.ifBlank { localizedStringResource(229, "Current session") },
                                accent = true
                            )
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(237, "Refresh"),
                            iconPath = stateValues.drawablePathIconSearch,
                            onClick = {
                                if (selectedSecurityTab == "history") getSecuritySessionHistory() else getSecuritySessions()
                            }
                        )
                        if (selectedSecurityTab == "sessions") {
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = localizedStringResource(238, "Revoke others"),
                                iconPath = stateValues.drawablePathIconDelete,
                                enabled = sessions.any { !it.current },
                                enabledColor = stateValues.ErrorColor,
                                confirmationRequired = true,
                                onClick = { revokeOtherSecuritySessions() }
                            )
                        }
                    }
                }

                if (selectedSecurityTab == "sessions") {
                    if (sessions.isEmpty()) {
                        item {
                            Text(
                                text = when (sessionsState) {
                                    is DataState.Empty -> localizedStringResource(239, "No active sessions loaded yet")
                                    else -> stateValues.stringNoMatches
                                },
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.textSize,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            )
                        }
                    } else {
                        items(sessions, key = { it.id }) { session ->
                            SecuritySessionCard(session)
                        }
                    }
                } else {
                    if (history.isEmpty()) {
                        item {
                            Text(
                                text = when (historyState) {
                                    is DataState.Empty -> localizedStringResource(1125, "No security history loaded yet")
                                    else -> stateValues.stringNoMatches
                                },
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.textSize,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            )
                        }
                    } else {
                        items(history, key = { it.id }) { event ->
                            SecuritySessionHistoryCard(event)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun AppConfiguration.MenuDevicesScreen() {
    val receiptPrinters by receiptPrinterDevicesState.collectAsState()
    val configuredReceiptPrinterId by configuredReceiptPrinterDeviceIdState.collectAsState()
    val labelPrinters by labelPrinterDevicesState.collectAsState()
    val configuredLabelPrinterId by configuredLabelPrinterDeviceIdState.collectAsState()
    val configuredLabelPrinterProtocol by configuredLabelPrinterProtocolState.collectAsState()
    val receiptPaperWidthMm by receiptPaperWidthMmState.collectAsState()
    LaunchedEffect(Unit) { loadReceiptPaperWidth(); if (preferHtmlDocumentPrinting) loadBrowserReceiptPrinterName() }
    val confirmedPrinterName by browserReceiptPrinterNameState.collectAsState()
    var confirmBrowserPrinter by remember { mutableStateOf(false) }
    var showStopPrintingHelp by remember { mutableStateOf(false) }
    var refreshingReceiptPrinters by remember { mutableStateOf(false) }
    var authorizingBluetooth by remember { mutableStateOf(false) }
    var printingReceipt by remember { mutableStateOf(false) }
    var savingReceiptPrinter by remember { mutableStateOf(false) }
    var receiptPrinterError by remember { mutableStateOf("") }
    var manualReceiptTarget by remember { mutableStateOf("") }
    var showManualReceiptTarget by remember { mutableStateOf(false) }
    val devicesScope = rememberCoroutineScope()
    var refreshingLabelPrinters by remember { mutableStateOf(false) }

    val refreshButtonText = localizedStringResource(1259, "Refresh printers")
    val refreshSuccessText = localizedStringResource(1270, "Receipt printers refreshed")
    val printerSelectedText = localizedStringResource(1260, "Receipt printer selected")
    val printerClearedText = localizedStringResource(1264, "Receipt printer cleared")
    val testReceiptTitle = localizedStringResource(1261, "Receipt printer test")
    val testReceiptButtonText = localizedStringResource(1262, "Send test receipt")
    val testReceiptSentText = localizedStringResource(1263, "Test receipt sent")
    val receiptPrinterNotConfiguredText = localizedStringResource(1038, "Receipt printer is not configured")
    val labelPrinterSelectedText = localizedStringResource(1280, "Label printer selected")
    val labelPrinterClearedText = localizedStringResource(1281, "Label printer cleared")
    val labelPrinterProtocolSelectedText = localizedStringResource(1298, "Label protocol selected")
    val testLabelSentText = localizedStringResource(1283, "Test label sent")
    val labelPrinterNotConfiguredText = localizedStringResource(1284, "Label printer is not configured")
    val labelPrintersRefreshedText = localizedStringResource(1302, "Label printers refreshed")

    fun refreshReceiptPrinters(showNotification: Boolean) {
        if (refreshingReceiptPrinters || printingReceipt || savingReceiptPrinter || authorizingBluetooth) return
        refreshingReceiptPrinters = true
        receiptPrinterError = ""
        refreshReceiptPrinterDevices(requestPermission = showNotification) { result ->
            devicesScope.launch {
                refreshingReceiptPrinters = false
                receiptPrinterError = if (result.success) "" else result.message
                if (showNotification) receiptActionNotification(result, refreshSuccessText)
            }
        }
    }

    fun selectReceiptPrinter(id: String?) {
        if (savingReceiptPrinter || printingReceipt || refreshingReceiptPrinters || authorizingBluetooth) return
        savingReceiptPrinter = true
        receiptPrinterError = ""
        configureReceiptPrinterDevice(id) { result ->
            devicesScope.launch {
                savingReceiptPrinter = false
                if (!result.success) receiptPrinterError = result.message
                else showManualReceiptTarget = false
                receiptActionNotification(result, if (id == null) printerClearedText else printerSelectedText)
            }
        }
    }

    fun sendTestReceipt() {
        if (printingReceipt || savingReceiptPrinter || authorizingBluetooth || configuredReceiptPrinterId.isNullOrBlank()) return
        printingReceipt = true
        receiptPrinterError = ""
        devicesScope.launch {
            try {
                val result = if (receiptUsesSystemDocumentPrinting()) {
                    printReceiptDocument(testReceiptTitle, receiptPrinterTestDocument(testReceiptTitle, receiptUiDateTime(getCurrentTimeMillis())))
                } else withContext(Dispatchers.Default) {
                    printReceiptEscPos(
                        buildReceiptPrinterTestEscPosBytes(title = testReceiptTitle, dateText = receiptUiDateTime(getCurrentTimeMillis())),
                        ReceiptTextLabelsDataModel(printerNotConfigured = receiptPrinterNotConfiguredText)
                    )
                }
                if (result.success && preferHtmlDocumentPrinting) confirmBrowserPrinter = true
                if (!result.success) receiptPrinterError = result.message
                receiptActionNotification(result, result.message.ifBlank { testReceiptSentText })
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (exception: Exception) {
                receiptPrinterError = exception.message ?: localizedStringResource(1254, "Thermal receipt printer")
            } finally { printingReceipt = false }
        }
    }

    fun refreshLabelPrinters(showNotification: Boolean) {
        if (refreshingLabelPrinters) return
        refreshingLabelPrinters = true
        refreshLabelPrinterDevices { result ->
            devicesScope.launch {
                refreshingLabelPrinters = false
                if (showNotification) receiptActionNotification(result, labelPrintersRefreshedText)
            }
        }
    }

    fun authorizeReceiptBluetooth() {
        val action = authorizeBluetoothReceiptPrintersAction ?: return
        if (printingReceipt || savingReceiptPrinter || refreshingReceiptPrinters || authorizingBluetooth) return
        authorizingBluetooth = true
        devicesScope.launch {
            var granted = false
            try {
                val result = action()
                granted = result.success
                receiptPrinterError = if (result.success) "" else result.message
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { receiptPrinterError = printerConnectionText("bluetooth_denied") }
            finally { authorizingBluetooth = false }
            if (granted) refreshReceiptPrinters(showNotification = false)
        }
    }

    LaunchedEffect(Unit) {
        refreshReceiptPrinters(showNotification = false)
        refreshLabelPrinters(showNotification = false)
    }

    if (confirmBrowserPrinter) BrowserPrinterSetupDialog { confirmBrowserPrinter = false }
    if (showStopPrintingHelp) ModalDialogWidget(title = deviceWorkflowText("stop_printing"),
        subTitle = deviceWorkflowText("stop_help"), onDismiss = { showStopPrintingHelp = false },
        negativeAction = { showStopPrintingHelp = false }, positiveAction = { showStopPrintingHelp = false })

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringDevices,
                iconPath = stateValues.drawablePathIconDevices,
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        val section = sectionTabsWidget(
            stateKey = "devices",
            tabs = listOf(
                TabContent("receipt", localizedStringResource(1254, "Thermal receipt printer")),
                TabContent("label", localizedStringResource(1276, "Sticky label printer")),
                TabContent("system", localizedStringResource(616, "Open system devices"))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Start)
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2),
        )

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Devices, section),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextFieldGroup),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            if (section == "system") {
                item(key = "MenuDevicesScreen:$section:0") {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(617, "Open Bluetooth/devices settings"),
                        subText = stateValues.stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters,
                        textSize = stateValues.titleTextSize,
                        subTextSize = stateValues.textSize
                    )
                }

                item(key = "MenuDevicesScreen:$section:1") {
                    DeviceSettingsCard(
                        title = localizedStringResource(1252, "A4 paper printer"),
                        subtitle = localizedStringResource(1253, "Analytics reports use the regular system print dialog for A4 paper printers."),
                        iconPath = stateValues.drawablePathIconAnalyticsReport,
                        iconRes = stateValues.drawableResIconAnalyticsReport.value
                    ) {
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = if (preferHtmlDocumentPrinting) deviceWorkflowText("system_print") else localizedStringResource(616, "Open system devices"),
                            iconPath = if (preferHtmlDocumentPrinting) stateValues.drawablePathIconAnalyticsReport else stateValues.drawablePathIconDevices,
                            iconRes = if (preferHtmlDocumentPrinting) stateValues.drawableResIconAnalyticsReport.value else stateValues.drawableResIconDevices.value,
                            confirmationRequired = false,
                            loading = printingReceipt,
                            autoLoading = false,
                            enabled = !printingReceipt,
                            onClick = {
                                if (!preferHtmlDocumentPrinting) openPlatformDevicesSettings()
                                else {
                                    printingReceipt = true
                                    devicesScope.launch {
                                        try {
                                            val title = localizedStringResource(1252, "A4 paper printer")
                                            receiptActionNotification(printHtmlDocument(title,
                                                systemPrinterTestDocument(title).toPrintHtml(title)), deviceWorkflowText("print_opened"))
                                        } finally { printingReceipt = false }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (section == "receipt") {
                item(key = "MenuDevicesScreen:$section:2") {
                    DeviceSettingsCard(
                        title = localizedStringResource(1254, "Thermal receipt printer"),
                        subtitle = if (preferHtmlDocumentPrinting) deviceWorkflowText("receipt_system_help") else deviceWorkflowText("receipt_driver_help"),
                        iconPath = stateValues.drawablePathIconReceipt,
                        iconRes = stateValues.drawableResIconReceipt.value
                    ) {
                        if (preferHtmlDocumentPrinting && confirmedPrinterName != null) {
                            Text("${deviceWorkflowText("saved_setup")}: $confirmedPrinterName", color = stateValues.TextColor,
                                fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                            Text(deviceWorkflowText("saved_setup_help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        }
                        if (!preferHtmlDocumentPrinting && chooseSystemReceiptPrinterAction != null) {
                            val savedSystemPrinter by systemReceiptPrinterNameState.collectAsState()
                            savedSystemPrinter?.let { Text(it, color = stateValues.TextColor, fontSize = stateValues.smallTextSize) }
                            actionButton(text = deviceWorkflowText("choose_printer"),
                                iconPath = stateValues.drawablePathIconReceipt, iconRes = stateValues.drawableResIconReceipt.value,
                                loading = savingReceiptPrinter, autoLoading = false, confirmationRequired = false,
                                enabled = !savingReceiptPrinter && !printingReceipt && !refreshingReceiptPrinters,
                                onClick = {
                                    savingReceiptPrinter = true
                                    devicesScope.launch {
                                        val result = try { chooseSystemReceiptPrinterAction!!.invoke() }
                                        finally { savingReceiptPrinter = false }
                                        if (result.success) selectReceiptPrinter(SYSTEM_DOCUMENT_PRINTER_ID)
                                        else { receiptPrinterError = result.message }
                                    }
                                })
                        }
                        if (receiptUsesSystemDocumentPrinting()) {
                            Text(deviceWorkflowText("receipt_paper"), color = stateValues.TextColor,
                                fontSize = stateValues.accentTextSize)
                            tabRowWidget(modifier = Modifier.fillMaxWidth(),
                                tabs = listOf(58, 80).map { width ->
                                    TabContent(width.toString(), "$width mm") {
                                        devicesScope.launch {
                                            try { saveReceiptPaperWidth(width) }
                                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                            catch (_: Exception) { receiptPrinterError = deviceWorkflowText("paper_save_failed") }
                                        }
                                    }
                                }, selectedIndexInitial = receiptPaperWidthMm.toString(), textSize = stateValues.smallTextSize)
                            Spacer(Modifier.height(stateValues.marginTextField))
                        }
                        if (stateValues.isNarrowScreen) {
                            Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = refreshButtonText,
                                    iconPath = stateValues.drawablePathIconRefresh,
                                    iconRes = stateValues.drawableResIconRefresh.value,
                                    loading = refreshingReceiptPrinters,
                                    enabled = !refreshingReceiptPrinters && !printingReceipt && !savingReceiptPrinter,
                                    autoLoading = false,
                                    confirmationRequired = false,
                                    onClick = { refreshReceiptPrinters(showNotification = true) }
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = testReceiptButtonText,
                                    loading = printingReceipt,
                                    iconPath = stateValues.drawablePathIconReceipt,
                                    iconRes = stateValues.drawableResIconReceipt.value,
                                    enabled = !printingReceipt && !savingReceiptPrinter && !refreshingReceiptPrinters && !configuredReceiptPrinterId.isNullOrBlank(),
                                    autoLoading = false,
                                    onDisabledClick = { postInAppNotification(receiptPrinterNotConfiguredText, NotificationType.Negative, transient = true) },
                                    confirmationRequired = false,
                                    onClick = ::sendTestReceipt
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = deviceWorkflowText("clear_selection"),
                                    iconPath = stateValues.drawablePathIconCancel,
                                    iconRes = stateValues.drawableResIconCancel.value,
                                    enabled = !printingReceipt && !savingReceiptPrinter && !refreshingReceiptPrinters && !configuredReceiptPrinterId.isNullOrBlank(),
                                    autoLoading = false,
                                    confirmationRequired = false,
                                    onClick = { selectReceiptPrinter(null) }
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                                horizontalAlignment = Alignment.Start
                            ) {
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = refreshButtonText,
                                    iconPath = stateValues.drawablePathIconRefresh,
                                    iconRes = stateValues.drawableResIconRefresh.value,
                                    loading = refreshingReceiptPrinters,
                                    enabled = !refreshingReceiptPrinters && !printingReceipt && !savingReceiptPrinter,
                                    autoLoading = false,
                                    confirmationRequired = false,
                                    onClick = { refreshReceiptPrinters(showNotification = true) }
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = testReceiptButtonText,
                                    loading = printingReceipt,
                                    iconPath = stateValues.drawablePathIconReceipt,
                                    iconRes = stateValues.drawableResIconReceipt.value,
                                    enabled = !printingReceipt && !savingReceiptPrinter && !refreshingReceiptPrinters && !configuredReceiptPrinterId.isNullOrBlank(),
                                    autoLoading = false,
                                    onDisabledClick = { postInAppNotification(receiptPrinterNotConfiguredText, NotificationType.Negative, transient = true) },
                                    confirmationRequired = false,
                                    onClick = ::sendTestReceipt
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = deviceWorkflowText("clear_selection"),
                                    iconPath = stateValues.drawablePathIconCancel,
                                    iconRes = stateValues.drawableResIconCancel.value,
                                    enabled = !printingReceipt && !savingReceiptPrinter && !refreshingReceiptPrinters && !configuredReceiptPrinterId.isNullOrBlank(),
                                    autoLoading = false,
                                    confirmationRequired = false,
                                    onClick = { selectReceiptPrinter(null) }
                                )
                            }
                        }

                        Text(deviceWorkflowText("clear_help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        actionButton(text = deviceWorkflowText("stop_printing"), autoLoading = false, confirmationRequired = false,
                            onClick = { showStopPrintingHelp = true })
                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                        Text(
                            text = if (preferHtmlDocumentPrinting) deviceWorkflowText("system_print") else localizedStringResource(1255, "Detected receipt printers"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.accentTextSize,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                        if (receiptPrinterError.isNotBlank()) Text(receiptPrinterError,
                            color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                        if (refreshingReceiptPrinters) Text(authUiText("Finding printers…", "Ищем принтеры…", "Принтерлер ізделуде…", "Принтерлер изделүүдө…"),
                            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        Text(if (preferHtmlDocumentPrinting) deviceWorkflowText("receipt_system_help") else printerConnectionText("help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                        if (authorizeBluetoothReceiptPrintersAction != null) {
                            AuthQuietAction(printerConnectionText("bluetooth_access"),
                                !printingReceipt && !savingReceiptPrinter && !refreshingReceiptPrinters && !authorizingBluetooth,
                                onClick = ::authorizeReceiptBluetooth)
                        }
                        if (getPlatformName().startsWith("jvm", ignoreCase = true)) {
                            AuthQuietAction(authUiText("Enter printer address", "Ввести адрес принтера", "Принтер мекенжайын енгізу", "Принтердин дарегин киргизиңиз"),
                                !printingReceipt && !savingReceiptPrinter) { showManualReceiptTarget = !showManualReceiptTarget }
                            if (showManualReceiptTarget) {
                                aitaFormTextField(value = manualReceiptTarget, onValueChange = { manualReceiptTarget = it },
                                    titleText = authUiText("Queue or address", "Очередь или адрес", "Кезек немесе мекенжай", "Кезек же дарек"),
                                    placeholderText = "print-service:XP-58 (copy 1)", identityKey = "receipt-manual-target",
                                    enabled = !savingReceiptPrinter && !printingReceipt, keyboardType = KeyboardType.Ascii)
                                Text("print-service:XP-58 (copy 1) · tcp://192.168.1.50:9100 · serial:COM3",
                                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                actionButton(text = authUiText("Use this printer", "Выбрать принтер", "Осы принтерді таңдау", "Бул принтерди колдонуу"),
                                    enabled = !savingReceiptPrinter && !printingReceipt && manualReceiptTarget.isNotBlank(),
                                    loading = savingReceiptPrinter, autoLoading = false) { selectReceiptPrinter(manualReceiptTarget) }
                            }
                        }

                        if (receiptPrinters.isEmpty() && !refreshingReceiptPrinters && receiptPrinterError.isBlank()) {
                            MessageText(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = stateValues.marginTextFieldGroup),
                                text = printerConnectionText("no_printers"),
                                subText = printerConnectionText("connect_help"),
                                textSize = stateValues.textSize,
                                subTextSize = stateValues.smallTextSize
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                                receiptPrinters.forEach { printer ->
                                    ThermalReceiptPrinterCard(
                                        printer = printer,
                                        selected = printer.id == configuredReceiptPrinterId || printer.configured,
                                        enabled = !printingReceipt && !savingReceiptPrinter && !refreshingReceiptPrinters,
                                        onSelect = { selectReceiptPrinter(printer.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (section == "label") {
                item(key = "MenuDevicesScreen:$section:3") {
                    DeviceSettingsCard(
                        title = localizedStringResource(1276, "Sticky label printer"),
                        subtitle = if (labelUsesSystemDocumentPrinting()) deviceWorkflowText("label_system_help") else localizedStringResource(1277, "Sticky item tags use TSPL, ZPL or CPCL label printers. They print barcode, item name and price onto small adhesive labels."),
                        iconPath = stateValues.drawablePathIconLabelPrinter,
                        iconRes = stateValues.drawableResIconLabelPrinter.value
                    ) {
                        if (stateValues.isNarrowScreen) {
                            Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = refreshButtonText,
                                    iconPath = stateValues.drawablePathIconRefresh,
                                    iconRes = stateValues.drawableResIconRefresh.value,
                                    loading = refreshingLabelPrinters,
                                    confirmationRequired = false,
                                    onClick = { refreshLabelPrinters(showNotification = true) }
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = localizedStringResource(1282, "Send test label"),
                                    iconPath = stateValues.drawablePathIconLabelPrinter,
                                    iconRes = stateValues.drawableResIconLabelPrinter.value,
                                    enabled = !configuredLabelPrinterId.isNullOrBlank(),
                                    onDisabledClick = { postInAppNotification(labelPrinterNotConfiguredText, NotificationType.Negative, transient = true) },
                                    confirmationRequired = false,
                                    onClick = {
                                        coroutineScope.launch {
                                            receiptActionNotification(
                                                printStockItemLabel(
                                                    StockItemLabelDataModel(
                                                        itemName = localizedStringResource(1289, "Sticky shelf tag"),
                                                        barcode = "123456789012",
                                                        priceText = "100 KZT",
                                                        storeName = "AITA",
                                                        copies = 1,
                                                        protocol = configuredLabelPrinterProtocol
                                                    ),
                                                    protocol = configuredLabelPrinterProtocol,
                                                    notConfiguredMessage = labelPrinterNotConfiguredText
                                                ),
                                                testLabelSentText
                                            )
                                        }
                                    }
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = localizedStringResource(1286, "Clear label printer"),
                                    iconPath = stateValues.drawablePathIconCancel,
                                    iconRes = stateValues.drawableResIconCancel.value,
                                    enabled = !configuredLabelPrinterId.isNullOrBlank(),
                                    confirmationRequired = false,
                                    onClick = {
                                        configureLabelPrinterDevice(null) { result ->
                                            coroutineScope.launch { receiptActionNotification(result, labelPrinterClearedText) }
                                        }
                                    }
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                                horizontalAlignment = Alignment.Start
                            ) {
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = refreshButtonText,
                                    iconPath = stateValues.drawablePathIconRefresh,
                                    iconRes = stateValues.drawableResIconRefresh.value,
                                    loading = refreshingLabelPrinters,
                                    confirmationRequired = false,
                                    onClick = { refreshLabelPrinters(showNotification = true) }
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = localizedStringResource(1282, "Send test label"),
                                    iconPath = stateValues.drawablePathIconLabelPrinter,
                                    iconRes = stateValues.drawableResIconLabelPrinter.value,
                                    enabled = !configuredLabelPrinterId.isNullOrBlank(),
                                    onDisabledClick = { postInAppNotification(labelPrinterNotConfiguredText, NotificationType.Negative, transient = true) },
                                    confirmationRequired = false,
                                    onClick = {
                                        coroutineScope.launch {
                                            receiptActionNotification(
                                                printStockItemLabel(
                                                    StockItemLabelDataModel(
                                                        itemName = localizedStringResource(1289, "Sticky shelf tag"),
                                                        barcode = "123456789012",
                                                        priceText = "100 KZT",
                                                        storeName = "AITA",
                                                        copies = 1,
                                                        protocol = configuredLabelPrinterProtocol
                                                    ),
                                                    protocol = configuredLabelPrinterProtocol,
                                                    notConfiguredMessage = labelPrinterNotConfiguredText
                                                ),
                                                testLabelSentText
                                            )
                                        }
                                    }
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = localizedStringResource(1286, "Clear label printer"),
                                    iconPath = stateValues.drawablePathIconCancel,
                                    iconRes = stateValues.drawableResIconCancel.value,
                                    enabled = !configuredLabelPrinterId.isNullOrBlank(),
                                    confirmationRequired = false,
                                    onClick = {
                                        configureLabelPrinterDevice(null) { result ->
                                            coroutineScope.launch { receiptActionNotification(result, labelPrinterClearedText) }
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                        if (!labelUsesSystemDocumentPrinting()) {
                            Text(
                                text = localizedStringResource(1287, "Label printer protocol"),
                                color = stateValues.TextColor,
                                fontSize = stateValues.accentTextSize,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(stateValues.marginTextField))

                            tabRowWidget(
                                modifier = Modifier.fillMaxWidth(),
                                tabs = listOf(
                                    TabContent(LABEL_PRINTER_PROTOCOL_AUTO, localizedStringResource(1294, "Auto protocol")) {
                                        configureLabelPrinterProtocol(it) { result -> coroutineScope.launch { receiptActionNotification(result, labelPrinterProtocolSelectedText) } }
                                    },
                                    TabContent(LABEL_PRINTER_PROTOCOL_TSPL, localizedStringResource(1295, "TSPL")) {
                                        configureLabelPrinterProtocol(it) { result -> coroutineScope.launch { receiptActionNotification(result, labelPrinterProtocolSelectedText) } }
                                    },
                                    TabContent(LABEL_PRINTER_PROTOCOL_ZPL, localizedStringResource(1296, "ZPL")) {
                                        configureLabelPrinterProtocol(it) { result -> coroutineScope.launch { receiptActionNotification(result, labelPrinterProtocolSelectedText) } }
                                    },
                                    TabContent(LABEL_PRINTER_PROTOCOL_CPCL, localizedStringResource(1297, "CPCL")) {
                                        configureLabelPrinterProtocol(it) { result -> coroutineScope.launch { receiptActionNotification(result, labelPrinterProtocolSelectedText) } }
                                    }
                                ),
                                selectedIndexInitial = normalizeLabelPrinterProtocol(configuredLabelPrinterProtocol),
                                textSize = stateValues.smallTextSize
                            )

                        }

                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                        Text(
                            text = if (preferHtmlDocumentPrinting) deviceWorkflowText("system_print") else localizedStringResource(1278, "Detected label printers"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.accentTextSize,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                        if (labelPrinters.isEmpty()) {
                            MessageText(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = stateValues.marginTextFieldGroup),
                                text = localizedStringResource(1279, "No paired sticky label printers found"),
                                subText = localizedStringResource(1266, "Pair or connect the printer in system settings, then refresh this list."),
                                textSize = stateValues.textSize,
                                subTextSize = stateValues.smallTextSize
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                                labelPrinters.forEach { printer ->
                                    StickyLabelPrinterCard(
                                        printer = printer,
                                        selected = printer.id == configuredLabelPrinterId || printer.configured,
                                        onSelect = {
                                            configureLabelPrinterDevice(printer.id) { result ->
                                                coroutineScope.launch { receiptActionNotification(result, labelPrinterSelectedText) }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.DeviceSettingsCard(
    title: String,
    subtitle: String,
    iconPath: String,
    iconRes: DrawableResource,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            CpImage(
                modifier = Modifier.size(28.dp),
                url = iconPath,
                fallbackRes = iconRes,
                contentDescription = title,
                tintColor = stateValues.AccentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )
                subtitle.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = stateValues.TextColor,
                        fontSize = stateValues.smallTextSize,
                        lineHeight = (stateValues.smallTextSize.value * 1.25f).sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
        content()
    }
}

@Composable
internal fun AppConfiguration.ThermalReceiptPrinterCard(
    printer: PlatformReceiptPrinterDataModel,
    selected: Boolean,
    enabled: Boolean = true,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (selected) stateValues.AccentColor.copy(alpha = 0.08f) else stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextField),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        CpImage(
            modifier = Modifier.size(24.dp),
            url = if (receiptPrinterTransport(printer.id) == "usb") usbReceiptIconPath() else stateValues.drawablePathIconReceipt,
            fallbackRes = if (receiptPrinterTransport(printer.id) == "usb") usbReceiptIconResource() else stateValues.drawableResIconReceipt.value,
            contentDescription = printer.name.ifBlank { printer.id },
            tintColor = if (selected) stateValues.AccentColor else stateValues.TextColor
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = printer.name.ifBlank { printer.id },
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(printerConnectionText("transport." + receiptPrinterTransport(printer.id)),
                color = stateValues.AccentColor, fontSize = stateValues.smallTextSize, fontWeight = FontWeight.SemiBold)
            printer.subtitle.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        actionButton(
            autoLoading = false,
            text = if (selected) localizedStringResource(1258, "Selected printer") else localizedStringResource(1257, "Use this printer"),
            iconPath = if (selected) stateValues.drawablePathIconCheck else null,
            iconRes = if (selected) stateValues.drawableResIconCheck.value else null,
            enabled = enabled && !selected && printer.available,
            confirmationRequired = false,
            onClick = onSelect
        )
    }
}


@Composable
internal fun AppConfiguration.StickyLabelPrinterCard(
    printer: PlatformLabelPrinterDataModel,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (selected) stateValues.AccentColor.copy(alpha = 0.08f) else stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextField),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        CpImage(
            modifier = Modifier.size(24.dp),
            url = stateValues.drawablePathIconLabelPrinter,
            fallbackRes = stateValues.drawableResIconLabelPrinter.value,
            contentDescription = printer.name.ifBlank { printer.id },
            tintColor = if (selected) stateValues.AccentColor else stateValues.TextColor
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = printer.name.ifBlank { printer.id },
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            printer.subtitle.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        actionButton(
            autoLoading = false,
            text = if (selected) localizedStringResource(1258, "Selected printer") else localizedStringResource(1303, "Use this label printer"),
            iconPath = if (selected) stateValues.drawablePathIconCheck else stateValues.drawablePathIconLabelPrinter,
            iconRes = if (selected) stateValues.drawableResIconCheck.value else stateValues.drawableResIconLabelPrinter.value,
            enabled = !selected,
            confirmationRequired = false,
            onClick = onSelect
        )
    }
}



internal data class SupportFaqEntry(
    val categoryId: Long,
    val questionId: Long,
    val questionFallback: String,
    val answerId: Long,
    val answerFallback: String
)

internal fun supportFaqEntries(): List<SupportFaqEntry> = listOf(
    SupportFaqEntry(819, 850, "What is AITA for?", 851, "AITA is a store operating system: it helps you run sales, returns, supplies, stock, workers, receipts, analytics, debts, subscriptions and devices from one account."),
    SupportFaqEntry(822, 852, "How do I create a store and branches?", 853, "Open Menu → Stores, add the main store first, then add branches under it. The main store can work like a root warehouse, while branches can sell from their own stock."),
    SupportFaqEntry(822, 854, "What does active store mean?", 855, "The active store is the store or branch the app is currently working with. Sales, stock, workers, debtors, cash register and analytics are loaded for that selected store."),
    SupportFaqEntry(823, 856, "How do workers and permissions work?", 857, "Owners can invite workers or accept employment requests. Each worker can have permissions for stock, sales, returns, supplies, workers, analytics, logs and cash operations."),
    SupportFaqEntry(822, 858, "Why do workers need a workshift?", 859, "A workshift is like a signed work session. It links transactions and cash actions to the exact worker and time, which makes reports, logs and responsibility clearer."),
    SupportFaqEntry(822, 860, "How do sales work?", 861, "In Sale, choose goods from stock or scan a barcode, set quantity, choose payment type, complete the payment and print/share the receipt. Stock is reduced automatically."),
    SupportFaqEntry(822, 862, "How do returns work?", 863, "In Return, choose returned goods and payment method. Stock is increased and the cash register is corrected according to how the refund was paid."),
    SupportFaqEntry(822, 864, "How do supply and acceptance transactions work?", 865, "Supply/acceptance adds goods to stock. It is useful when receiving goods from suppliers, correcting warehouse quantity, or creating batches with delivery data."),
    SupportFaqEntry(822, 866, "What are stock batches?", 867, "A batch is a separate delivery of the same goods item. Batches store quantity, supplier, supply price, sale price, dates, shelf priority, status and expiration information."),
    SupportFaqEntry(822, 868, "What is a shelf batch?", 869, "A shelf batch is the batch currently used for sales. It lets you sell from the right delivery first, especially when expiration dates and supplier prices matter."),
    SupportFaqEntry(822, 870, "How do suppliers and supplier prices work?", 871, "Suppliers can be attached to goods. Remembered supplier prices help prefill future batches, so repeated deliveries become faster and less error-prone."),
    SupportFaqEntry(822, 872, "How do supplier orders work?", 873, "Supplier orders are used to plan what you expect to receive from a supplier. When received, they can become stock batches and update warehouse quantities."),
    SupportFaqEntry(822, 874, "How do receipts, PDF, sharing, WhatsApp and printing work?", 875, "After completion, the centered receipt toolbar can save PDF, share, open WhatsApp where supported, or print. Save notifications show the actual folder and an Open action. Platform capabilities vary; check the result before sending or printing again."),
    SupportFaqEntry(822, 876, "How does the cash register work?", 877, "The cash register tracks expected cash in the drawer. Cash sales increase it, cash returns decrease it, and authorized users can register cash extractions."),
    SupportFaqEntry(822, 878, "How do debtors and partial payments work?", 879, "Debtors store customers or companies that owe money. You can track debt amount, due date, interest, payment history and partial repayments."),
    SupportFaqEntry(822, 880, "What does Analytics show?", 881, "Analytics summarizes revenue, returns, supply cost, estimated profit, payment mix, debt, top items, sales by time, stock risk, expiring batches, supplier activity and worker performance."),
    SupportFaqEntry(820, 882, "How do finances and subscriptions work?", 883, "Finances show wallet balance, top-ups, ledger entries and subscription charges. Store subscriptions unlock plan-based functionality and renewal state."),
    SupportFaqEntry(821, 884, "What happens if the server or internet is unavailable?", 885, "The top status bar shows server reachability, and AITA attempts recovery automatically. Cached data and supported queued operations may remain available; not every action works offline. Do not repeat an uncertain sale or delete local data before reconciliation."),
    SupportFaqEntry(821, 886, "How does realtime sync work?", 887, "The app keeps a WebSocket connection to the server. After changes, connected clients refresh affected data, so cashier, owner and branch devices stay closer to the same state."),
    SupportFaqEntry(821, 888, "How do scanners, printers and devices work?", 889, "Device screens and platform bridges handle barcode scanners, receipt printers, Bluetooth/system settings and platform-specific saving, sharing and printing."),
    SupportFaqEntry(821, 890, "What is local branch network?", 891, "Local branch network is meant for branch devices that can exchange queued local operations and snapshots when direct server availability is limited. It should still reconcile with the server as authority."),
    SupportFaqEntry(819, 892, "How do language and theme preferences work?", 893, "Language, theme and scale are applied together and saved locally, with account synchronization when available. A new selection takes precedence over older delayed responses. Changing appearance should not replace your screen or clear carts and drafts."),
    SupportFaqEntry(823, 894, "How is account security handled?", 895, "Security sessions show devices signed into the account. You can revoke unfamiliar sessions, and tokens are refreshed securely by platform storage."),
    SupportFaqEntry(821, 896, "What should I do if stock or transaction data looks wrong?", 897, "Refresh data, check active store/branch, check operation logs, review batches and transaction history, then contact support with store, time, item barcode and screenshots if needed."),
    SupportFaqEntry(822, 898, "How are goods categories and global goods used?", 899, "Generic categories and goods templates help start faster. Store-specific goods can still have their own names, barcodes, prices, units, conditions and supplier data."),
    SupportFaqEntry(822, 900, "How will manufacturers, suppliers, stores and buyers connect?", 901, "The project is being built as a chain: manufacturer/supplier data can feed stores, stores manage branches and stock, and future buyer flows can show goods and orders outside the current account."),
    SupportFaqEntry(819, 902, "How should I prepare before using AITA in a real store day?", 903, "Create the store and branches, add workers and permissions, add goods and batches, test scanner/printer, make a few test transactions, check receipts and confirm analytics/cash register behavior."),
    SupportFaqEntry(819, 904, "What should I include when contacting support?", 905, "Describe the action, store or branch, approximate time, client build, platform, barcode or transaction ID, and whether it repeats. Attach only relevant screenshots with private details hidden. Never include passwords, codes, tokens or authentication QR images."),
) + additionalSupportFaqEntries()


@Composable
internal fun AppConfiguration.SupportFaqCard(entry: SupportFaqEntry) {
    var expanded by rememberSaveable(entry.questionId) { mutableStateOf(false) }
    val question = localizedStringResource(entry.questionId, entry.questionFallback)
    val answer = localizedStringResource(entry.answerId, entry.answerFallback)
    val category = localizedStringResource(entry.categoryId, "General")

    val cardBorderColor by animateColorAsState(
        targetValue = if (expanded) stateValues.AccentColor else stateValues.PlaceholderTextColor,
        animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS),
        label = "supportFaqBorder"
    )
    val cardBackgroundColor by animateColorAsState(
        targetValue = if (expanded) stateValues.AccentColor.copy(alpha = 0.055f) else stateValues.BackgroundColor,
        animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS),
        label = "supportFaqBackground"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(cardBackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                cardBorderColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable { expanded = !expanded }
            .aitaContentMotion()
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Text(
            text = category,
            color = stateValues.AccentColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = question,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            CpImage(
                modifier = Modifier.size(20.dp),
                url = if (expanded) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore,
                fallbackRes = if (expanded) stateValues.drawableResIconExpandLess.value else stateValues.drawableResIconExpandMore.value,
                contentDescription = question,
                tintColor = stateValues.AccentColor
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                Spacer(modifier = Modifier.height(stateValues.marginTextField))
                Text(
                    text = answer,
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    lineHeight = (stateValues.textSize.value * 1.35f).sp
                )
            }
        }
    }
}

@Composable
fun AppConfiguration.MenuDebtorsScreen() {
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringDebtors,
                iconPath = stateValues.drawablePathIconDebtors,
                trailingIcons = listOf(
                    Triple(sortActionIconPath(), sortActionIconFallback()) {
                        sortMenuExpanded = !sortMenuExpanded
                    }
                ),
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        val storeId = stateValues.activeStoreId
        val debtors by debtorsState.payload.collectAsState()

        LaunchedEffect(storeId) {
            storeId?.let { getDebtors(it) }
        }

        var search by rememberSaveable { mutableStateOf("") }
        var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedTab by rememberNavigationSection("debtors:section", "open")
        var sortId by rememberSaveable { mutableStateOf("amount") }
        var sortAscending by rememberSaveable { mutableStateOf(false) }

        AnimatedVisibility(visible = sortMenuExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(stateValues.BackgroundColor)
                    .padding(horizontal = stateValues.marginTextField, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = localizedStringResource(512, "Sort by"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold
                )

                tabRowWidget(
                    modifier = Modifier.fillMaxWidth(),
                    tabs = listOf(
                        TabContent("name", stateValues.stringName) { sortId = it },
                        TabContent("created", localizedStringResource(514, "Time added")) { sortId = it },
                        TabContent("paid", localizedStringResource(1207, "Time paid")) { sortId = it },
                        TabContent("amount", localizedStringResource(581, "Amount")) { sortId = it }
                    ),
                    selectedIndexInitial = sortId,
                    compact = true, textSize = stateValues.smallTextSize
                )

                tabRowWidget(
                    modifier = Modifier.fillMaxWidth(),
                    tabs = listOf(
                        TabContent("asc", localizedStringResource(515, "Ascending")) { sortAscending = true },
                        TabContent("desc", localizedStringResource(516, "Descending")) { sortAscending = false }
                    ),
                    selectedIndexInitial = if (sortAscending) "asc" else "desc",
                    compact = true, textSize = stateValues.smallTextSize
                )
            }
        }

        fun DebtorDataModel.lastPaidAtMillis(): Long? = paymentHistory.maxOfOrNull { it.timeMillis }?.takeIf { it > 0L }

        val q = search.trim()
        fun debtorMatchesSearch(debtor: DebtorDataModel): Boolean =
            q.isBlank() || listOf(
                debtorDisplayName(debtor), debtor.firstName, debtor.lastName, debtor.companyName,
                debtor.phoneNumber.asDisplayPhoneNumber(), debtor.email, debtor.idNumber, debtor.companyIdNumber,
                debtor.currency, debtor.debtorType, debtor.transactionIds.joinToString(" ")
            ).any { it.contains(q, true) }

        val searchedDebtors = debtors.orEmpty().filter { debtorMatchesSearch(it) }

        Column(
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            TransactionPlainTextField(
                title = "",
                value = search,
                placeholder = stateValues.stringSearchByAnyData,
                leadingIconPath = stateValues.drawablePathIconSearch,
                stateHost = NavigationScreenModel.Menu.Debtors,
                stateKey = "menu_debtors_search",
                onValueChange = { search = it }
            )

            tabRowWidget(
                modifier = Modifier.fillMaxWidth(),
                tabs = listOf(
                    TabContent("open", tabLabelWithCount(localizedStringResource(1205, "Open debts"), searchedDebtors.count { it.debtAmount > 0.0 })) { selectedTab = it },
                    TabContent("all", tabLabelWithCount(localizedStringResource(1206, "All debts"), searchedDebtors.size)) { selectedTab = it }
                ),
                selectedIndexInitial = selectedTab
            )
        }

        val shownDebtors = searchedDebtors
            .filter { debtor -> selectedTab == "all" || debtor.debtAmount > 0.0 }
            .let { list ->
                val sorted = when (sortId) {
                    "name" -> list.sortedBy { debtorDisplayName(it).lowercase() }
                    "created" -> list.sortedBy { it.debtCreatedAtMillis }
                    "paid" -> list.sortedBy { it.lastPaidAtMillis() ?: Long.MAX_VALUE }
                    else -> list.sortedBy { debtWithInterest(it) }
                }
                if (sortAscending) sorted else sorted.reversed()
            }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Debtors, listOf(selectedTab, sortId, if (sortAscending) "asc" else "desc").joinToString("_")),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .padding(stateValues.marginTextField)
        ) {
            if (storeId == null) {
                item { MessageText(modifier = Modifier.fillParentMaxSize().fillMaxWidth(), text = stateValues.stringNoActiveStore) }
            } else if (shownDebtors.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillParentMaxSize().fillMaxWidth(),
                        text = if (q.isBlank()) stateValues.stringListEmpty else stateValues.stringNoMatches
                    )
                }
            } else {
                items(shownDebtors, key = { it.id }) { debtor ->
                    DebtorPaymentCard(
                        debtor = debtor,
                        onClick = {
                            coroutineScope.launch {
                                NavigationScreenModel.Menu.CloseDebt.setState("selected_debtor_id" to debtor.id)
                                Navigation.Menu.go(NavigationScreenModel.Menu.CloseDebt, forceSecond = true)
                            }
                        },
                        onDelete = { pendingDeleteId = debtor.id }
                    )
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                }
            }
            item { Spacer(modifier = Modifier.height(stateValues.screenHeight / 5)) }
        }

        pendingDeleteId?.let { debtorId ->
            Dialog(onDismissRequest = { pendingDeleteId = null }) {
                TransactionBarcodeModalGuard()
                Column(
                    modifier = Modifier
                        .aitaDialogEntrance()
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = true)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup)
                ) {
                    Text(localizedStringResource(381, "Delete debtor?"), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        actionButton(modifier = Modifier.fillMaxWidth(), text = stateValues.stringCancel, enabledColor = stateValues.DisabledColor) { pendingDeleteId = null }
                        actionButton(modifier = Modifier.fillMaxWidth(), text = stateValues.stringDelete, enabledColor = stateValues.ErrorColor) {
                            storeId?.let { deleteDebtor(it, debtorId) }
                            pendingDeleteId = null
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun AppConfiguration.MenuCloseDebtScreen() {
    val state by NavigationScreenModel.Menu.CloseDebt.state.collectAsState()
    val selectedId = state["selected_debtor_id"]
    val debtors by debtorsState.payload.collectAsState()
    val debtor = debtors.orEmpty().find { it.id == selectedId }
    val storeId = stateValues.activeStoreId
    val transactions by transactionsState.payload.collectAsState()

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringCloseDebt,
                iconPath = stateValues.drawablePathIconDebtors,
                onBack = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } }
            )
        }
    ) {
        if (storeId == null || debtor == null) {
            MessageText(modifier = Modifier.fillMaxSize(), text = stateValues.stringListEmpty)
            return@AitaScreenColumn
        }

        var receiptDialog by rememberSaveable(debtor.id) { mutableStateOf<String?>(null) }
        var selectedTab by rememberNavigationSection("debtor:${debtor.id}:section", "pay")
        var debtorType by rememberSaveable(debtor.id) { mutableStateOf(debtor.debtorType) }
        var firstName by rememberSaveable(debtor.id) { mutableStateOf(debtor.firstName) }
        var lastName by rememberSaveable(debtor.id) { mutableStateOf(debtor.lastName) }
        var companyName by rememberSaveable(debtor.id) { mutableStateOf(debtor.companyName) }
        var idNumber by rememberSaveable(debtor.id) { mutableStateOf(if (debtor.debtorType == "company") debtor.companyIdNumber else debtor.idNumber) }
        var phone by rememberSaveable(debtor.id) { mutableStateOf(debtor.phoneNumber) }
        var email by rememberSaveable(debtor.id) { mutableStateOf(debtor.email) }
        var debtAmountText by rememberSaveable(debtor.id) { mutableStateOf(moneyInputFromDouble(debtor.debtAmount)) }
        var finalDueDateText by rememberSaveable(debtor.id) { mutableStateOf(debtor.debtDueAtMillis.toStockDateInputText()) }
        var interestEnabled by rememberSaveable(debtor.id) { mutableStateOf(debtor.interest?.enabled == true) }
        var interestRateText by rememberSaveable(debtor.id) { mutableStateOf(debtor.interest?.ratePercent?.takeIf { it > 0.0 }?.toString().orEmpty()) }
        var interestUnit by rememberSaveable(debtor.id) { mutableStateOf(debtor.interest?.periodUnit ?: "month") }
        var paymentAmountText by rememberSaveable(debtor.id) { mutableStateOf(moneyInputFromDouble(debtor.debtAmount)) }
        var planAmountText by rememberSaveable(debtor.id) { mutableStateOf("") }
        var planDateText by rememberSaveable(debtor.id) { mutableStateOf("") }
        var planNoteLocalized by remember(debtor.id) { mutableStateOf(emptyLocalizedItemForCurrentLanguage()) }

        val currencySymbol = stateValues.globalAppConfiguration.countries.getCurrency(debtor.currency)?.symbol ?: debtor.currency
        val amountToPay = paymentAmountText.toMoneyDouble().coerceIn(0.0, debtWithInterest(debtor))

        fun updatedDebtorFromEditor(): DebtorDataModel {
            val due = stockDateInputTextToMillis(finalDueDateText)
            val interest = if (interestEnabled && interestRateText.toDoubleOrNull()?.let { it > 0.0 } == true) {
                DebtInterestDataModel(
                    enabled = true,
                    ratePercent = interestRateText.toDoubleOrNull() ?: 0.0,
                    periodUnit = interestUnit,
                    startsAtMillis = debtor.interest?.startsAtMillis ?: debtor.debtCreatedAtMillis.takeIf { it > 0L } ?: getCurrentTimeMillis()
                )
            } else null
            return debtor.copy(
                debtorType = debtorType,
                firstName = if (debtorType == "individual") firstName.trim() else "",
                lastName = if (debtorType == "individual") lastName.trim() else "",
                companyName = if (debtorType == "company") companyName.trim() else "",
                idNumber = if (debtorType == "individual") idNumber.trim() else "",
                companyIdNumber = if (debtorType == "company") idNumber.trim() else "",
                phoneNumber = phone.trim(),
                email = email.trim(),
                debtAmount = debtAmountText.toMoneyDouble().coerceAtLeast(0.0),
                originalDebtAmount = debtor.originalDebtAmount ?: debtor.debtAmount,
                debtCreatedAtMillis = debtor.debtCreatedAtMillis.takeIf { it > 0L } ?: getCurrentTimeMillis(),
                debtDueAtMillis = due,
                interest = interest
            )
        }

        fun saveDebtor(onDone: (() -> Unit)? = null) {
            updateDebtor(storeId, updatedDebtorFromEditor()) { result ->
                if (result is DataState.Success) onDone?.invoke()
            }
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.CloseDebt, "${debtor.id}:$selectedTab"),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.76f)
                .padding(stateValues.marginTextField)
        ) {
            item {
                DebtorPaymentCard(debtor = debtor, selected = true, onClick = {})
                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                val tab = tabRowWidget(
                    selectedIndexInitial = selectedTab,
                    tabs = listOf(
                        TabContent("pay", localizedStringResource(354, "Pay")) { selectedTab = it },
                        TabContent("edit", localizedStringResource(355, "Edit debt")) { selectedTab = it },
                        TabContent("plan", tabLabelWithCount(localizedStringResource(356, "Payment plan"), debtor.plannedPayments.count { !it.completed })) { selectedTab = it },
                        TabContent("history", tabLabelWithCount(localizedStringResource(257, "History"), debtor.paymentHistory.size + transactions.orEmpty().count { it.id in debtor.transactionIds })) { selectedTab = it }
                    )
                )
                selectedTab = tab.id
                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
            }

            when (selectedTab) {
                "edit" -> item {
                    SimpleDropdownField(
                        title = localizedStringResource(288, "Debtor form"),
                        selectedId = debtorType,
                        options = listOf(DropdownOption("individual", localizedStringResource(289, "Individual")), DropdownOption("company", localizedStringResource(290, "Company"))),
                        placeholder = localizedStringResource(288, "Debtor form"),
                        onSelected = { debtorType = it }
                    )
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    if (debtorType == "company") {
                        SimpleTextInput(Modifier.fillMaxWidth(), companyName, localizedStringResource(291, "Company name"), leadingIconPath = stateValues.drawablePathIconStores, stateHost = NavigationScreenModel.Menu.CloseDebt, stateKey = "menu_close_debt_${debtor.id}_company_name") { companyName = it }
                        Spacer(modifier = Modifier.height(stateValues.marginTextField))
                        SimpleTextInput(Modifier.fillMaxWidth(), idNumber, localizedStringResource(292, "Company ID / BIN"), keyboardType = KeyboardType.Number, leadingIconPath = stateValues.drawablePathIconStores, stateHost = NavigationScreenModel.Menu.CloseDebt, stateKey = "menu_close_debt_${debtor.id}_company_id_number") { idNumber = it.filter { c -> c.isDigit() }.take(32) }
                    } else {
                        SimpleTextInput(Modifier.fillMaxWidth(), firstName, stateValues.stringFirstName, leadingIconPath = stateValues.drawablePathIconPerson, stateHost = NavigationScreenModel.Menu.CloseDebt, stateKey = "menu_close_debt_${debtor.id}_first_name") { firstName = it }
                        Spacer(modifier = Modifier.height(stateValues.marginTextField))
                        SimpleTextInput(Modifier.fillMaxWidth(), lastName, stateValues.stringLastName, leadingIconPath = stateValues.drawablePathIconPerson, stateHost = NavigationScreenModel.Menu.CloseDebt, stateKey = "menu_close_debt_${debtor.id}_last_name") { lastName = it }
                        Spacer(modifier = Modifier.height(stateValues.marginTextField))
                        SimpleTextInput(Modifier.fillMaxWidth(), idNumber, localizedStringResource(293, "ID number"), keyboardType = KeyboardType.Number, leadingIconPath = stateValues.drawablePathIconPerson, stateHost = NavigationScreenModel.Menu.CloseDebt, stateKey = "menu_close_debt_${debtor.id}_id_number") { idNumber = it.filter { c -> c.isDigit() }.take(32) }
                    }
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    DebtorPhoneInput(value = phone, identityKey = "edit-debtor-phone:${stateValues.userAccount?.id}:${debtor.id}") { phone = it }
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    SimpleTextInput(Modifier.fillMaxWidth(), email, stateValues.stringEmail, keyboardType = KeyboardType.Email, leadingIconPath = stateValues.drawablePathIconEmail, stateHost = NavigationScreenModel.Menu.CloseDebt, stateKey = "menu_close_debt_${debtor.id}_email") { email = it }
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    TransactionPaymentAmountField(
                        title = localizedStringResource(362, "Debt amount"),
                        value = debtAmountText,
                        identityKey = "debtor:${debtor.id}:edit",
                        selected = true,
                        onSelected = {},
                        onValueChange = { debtAmountText = it },
                        leadingIconPath = stateValues.drawablePathIconFinances
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TransactionQuickAmountButtons(
                        targetAmount = debtor.debtAmount,
                        currencyCode = debtor.currency,
                        currencySymbol = currencySymbol,
                        includeExactRemaining = true,
                        currentAmount = debtAmountText.toMoneyDouble(),
                        onAmountSelected = { debtAmountText = moneyInputFromDouble(it) }
                    )
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    StockDatePartsEditor(localizedStringResource(314, "Final due date"), finalDueDateText) { finalDueDateText = it }
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    actionButton(
                        text = if (interestEnabled) localizedStringResource(810, "Disable interest") else localizedStringResource(809, "Enable interest"),
                        iconPath = stateValues.drawablePathIconFinances,
                        enabledColor = if (interestEnabled) stateValues.AccentColor else stateValues.DisabledColor,
                        onClick = { interestEnabled = !interestEnabled }
                    )
                    if (interestEnabled) {
                        Spacer(modifier = Modifier.height(stateValues.marginTextField))
                        Row(horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField), modifier = Modifier.fillMaxWidth()) {
                            SimpleTextInput(Modifier.weight(1f), interestRateText, localizedStringResource(811, "Rate %"), keyboardType = KeyboardType.Decimal, leadingIconPath = stateValues.drawablePathIconFinances, stateHost = NavigationScreenModel.Menu.CloseDebt, stateKey = "menu_close_debt_${debtor.id}_interest_rate") { interestRateText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.replace(',', '.') }
                            SimpleDropdownField(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(363, "Period"),
                                selectedId = interestUnit,
                                options = listOf(DropdownOption("day", localizedStringResource(367, "Day")), DropdownOption("week", localizedStringResource(368, "Week")), DropdownOption("month", localizedStringResource(369, "Month")), DropdownOption("year", localizedStringResource(370, "Year"))),
                                placeholder = localizedStringResource(363, "Period"),
                                onSelected = { interestUnit = it }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    actionButton(text = localizedStringResource(347, "Save debtor"), iconPath = stateValues.drawablePathIconCheck, onClick = { saveDebtor() })
                }

                "plan" -> item {
                    val openPlans = debtor.plannedPayments.filter { !it.completed }
                    val assumedRemaining = openPlans.fold(debtor.debtAmount) { acc, plan -> (acc - plan.amount).coerceAtLeast(0.0).roundMoney() }
                    DebtorInfoLine(localizedStringResource(371, "Remaining after open planned payments"), "${moneyInputFromDouble(assumedRemaining)} ${debtor.currency}", accent = true)
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    TransactionPaymentAmountField(
                        title = localizedStringResource(364, "Planned amount"),
                        value = planAmountText,
                        identityKey = "debtor:${debtor.id}:plan",
                        selected = true,
                        onSelected = {},
                        onValueChange = { planAmountText = it },
                        leadingIconPath = stateValues.drawablePathIconFinances
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DebtPercentQuickButtons(debtor.debtAmount, currencySymbol, planAmountText.toMoneyDouble()) { planAmountText = moneyInputFromDouble(it) }
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    StockDatePartsEditor(localizedStringResource(372, "Planned payment date"), planDateText) { planDateText = it }
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    StockLocalizedStringGroupEditor(
                        title = localizedStringResource(373, "Plan note"),
                        placeholder = stateValues.stringOptional,
                        values = planNoteLocalized,
                        addText = localizedStringResource(1072, "Add debt plan note translation"),
                        required = false,
                        singleLine = false,
                        adaptiveMultiline = true,
                        persistentKey = "debtor-plan-note:${debtor.id}",
                        onChanged = { planNoteLocalized = it }
                    )
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    actionButton(
                        text = localizedStringResource(348, "Add planned payment"),
                        iconPath = stateValues.drawablePathIconAdd,
                        enabled = planAmountText.toMoneyDouble() > 0.0 && planAmountText.toMoneyDouble() <= assumedRemaining + 0.01,
                        onClick = {
                            val amount = planAmountText.toMoneyDouble().coerceAtMost(assumedRemaining)
                            val pct = if (debtor.debtAmount > 0.0) (amount / debtor.debtAmount * 100.0).roundMoney() else null
                            val plan = DebtPartialPaymentPlanDataModel(
                                id = "plan_${getCurrentTimeMillis()}",
                                amount = amount,
                                percent = pct,
                                dueAtMillis = stockDateInputTextToMillis(planDateText),
                                note = planNoteLocalized.toStoredLocalizedNoteOrNull()
                            )
                            updateDebtor(storeId, debtor.copy(plannedPayments = debtor.plannedPayments + plan))
                            planAmountText = ""
                            planDateText = ""
                            planNoteLocalized = emptyLocalizedItemForCurrentLanguage()
                        }
                    )
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    openPlans.forEachIndexed { index, plan ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                                .clip(RoundedCornerShape(stateValues.cornerRadius))
                                .background(stateValues.BackgroundColor)
                                .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                                .padding(stateValues.marginTextFieldGroup)
                        ) {
                            DebtorInfoLine("${index + 1}. ${localizedStringResource(374, "Planned")}", "${moneyInputFromDouble(plan.amount)} ${debtor.currency}", accent = true)
                            plan.percent?.let { DebtorInfoLine(localizedStringResource(375, "Percent"), "${it}%") }
                            plan.dueAtMillis?.toStockDateInputText()?.let { DebtorInfoLine(stateValues.stringDate, it) }
                            plan.note.visibleStoredLocalizedNote(stateValues.appLanguage)?.let { DebtorInfoLine(localizedStringResource(266, "Note"), it) }
                        }
                        Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    }
                }

                "history" -> item {
                    val relatedTransactions = transactions.orEmpty().filter { it.id in debtor.transactionIds }
                    if (relatedTransactions.isNotEmpty()) {
                        Text(localizedStringResource(376, "Original debt transactions"), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                        relatedTransactions.forEach { tx ->
                            DebtorInfoLine(localizedStringResource(316, "Transaction"), "${tx.id} • ${receiptUiDateTime(tx.timeMillis)}")
                            tx.goodsInTransaction.forEach { item ->
                                DebtorInfoLine(localizedStringResource(365, "Goods item"), "${item.barcode} × ${item.quantity} = ${moneyInputFromDouble(item.quantity * item.pricePerUnit)}")
                            }
                        }
                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    }
                    Text(localizedStringResource(377, "Payment history"), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                    if (debtor.paymentHistory.isEmpty()) MessageText(Modifier.fillMaxWidth().heightIn(min = 160.dp), text = localizedStringResource(349, "No payment records yet"))
                    debtor.paymentHistory.sortedByDescending { it.timeMillis }.forEach { record ->
                        DebtorInfoLine(receiptUiDateTime(record.timeMillis), "${moneyInputFromDouble(record.amount)} ${record.currency} • ${record.paymentKind}", accent = true)
                        DebtorInfoLine(localizedStringResource(378, "Before / after"), "${moneyInputFromDouble(record.debtBefore)} → ${moneyInputFromDouble(record.debtAfter)}")
                        record.note.visibleStoredLocalizedNote(stateValues.appLanguage)?.let { DebtorInfoLine(localizedStringResource(266, "Note"), it) }
                    }
                }

                else -> item {
                    DebtorInfoLine(stateValues.stringDebt, "${moneyInputFromDouble(debtor.debtAmount)} ${debtor.currency}", accent = true)
                    val interest = estimatedDebtInterest(debtor)
                    if (interest > 0.0) DebtorInfoLine(localizedStringResource(379, "Interest estimate"), "${moneyInputFromDouble(interest)} ${debtor.currency}")
                    DebtorInfoLine(localizedStringResource(380, "Payable now"), "${moneyInputFromDouble(debtWithInterest(debtor))} ${debtor.currency}", accent = true)
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    TransactionPaymentAmountField(
                        title = stateValues.stringCloseDebt,
                        value = paymentAmountText,
                        identityKey = "debtor:${debtor.id}:payment",
                        selected = true,
                        onSelected = {},
                        onValueChange = { paymentAmountText = it },
                        leadingIconPath = stateValues.drawablePathIconDebtors
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TransactionQuickAmountButtons(
                        targetAmount = debtWithInterest(debtor),
                        currencyCode = debtor.currency,
                        currencySymbol = currencySymbol,
                        includeExactRemaining = true,
                        currentAmount = paymentAmountText.toMoneyDouble(),
                        onAmountSelected = { paymentAmountText = moneyInputFromDouble(it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DebtPercentQuickButtons(debtWithInterest(debtor), currencySymbol, paymentAmountText.toMoneyDouble()) { paymentAmountText = moneyInputFromDouble(it) }
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    TransactionNumpad { token -> paymentAmountText = paymentInputAppend(paymentAmountText, token) }
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(350, "Pay partial"),
                            iconPath = stateValues.drawablePathIconFinances,
                            enabled = amountToPay > 0.0,
                            onClick = {
                                val before = debtor.debtAmount
                                val paid = amountToPay.coerceAtMost(before)
                                payDebtorDebt(DebtPaymentRequestDataModel(debtor.id, storeId, paid, debtor.currency, paymentKind = "partial", timeMillis = getCurrentTimeMillis())) { result ->
                                    if (result is DataState.Success) receiptDialog = "partial:${paid}:${before}:${result.payload.debtAmount}"
                                }
                            }
                        )
                        actionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(351, "Pay full"),
                            iconPath = stateValues.drawablePathIconCheck,
                            enabled = debtor.debtAmount > 0.0,
                            onClick = {
                                val before = debtor.debtAmount
                                val paid = before
                                payDebtorDebt(DebtPaymentRequestDataModel(debtor.id, storeId, paid, debtor.currency, paymentKind = "full", timeMillis = getCurrentTimeMillis())) { result ->
                                    if (result is DataState.Success) receiptDialog = "full:${paid}:${before}:${result.payload.debtAmount}"
                                }
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(stateValues.screenHeight / 5)) }
        }

        receiptDialog?.let { raw ->
            val parts = raw.split(":")
            val kind = parts.getOrNull(0) ?: "partial"
            val paid = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val before = parts.getOrNull(2)?.toDoubleOrNull() ?: debtor.debtAmount
            val after = parts.getOrNull(3)?.toDoubleOrNull() ?: (before - paid).coerceAtLeast(0.0)
            DebtReceiptDialog(
                debtor = debtor,
                paidAmount = paid,
                debtBefore = before,
                debtAfter = after,
                paymentKind = kind,
                relatedTransactions = transactions.orEmpty().filter { it.id in debtor.transactionIds },
                onDismiss = { receiptDialog = null }
            )
        }
    }
}



@Composable
fun AppConfiguration.MenuAppThemeScreen() {
    AitaScreenColumn(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringAppTheme,
                iconPath = stateValues.drawablePathIconAppTheme,
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.AppTheme),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(
                    if (stateValues.isNarrowScreen) 1f else 0.6f
                )
        ) {
            items(availableAppThemes(stateValues.globalAppConfiguration.themes)) { theme ->
                AppThemeSettingsItemWidget(
                    id = theme.id,
                    name = theme.name.extractLocalizedString(stateValues.appLanguage) ?: theme.id.toString(),
                    isActive = stateValues.appThemeId == theme.id
                )
            }
        }
    }
}

@Composable
fun AppConfiguration.MenuAppScaleScreen() {
    AitaScreenColumn(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = localizedStringResource(910, "Interface scale"),
                iconPath = stateValues.drawablePathIconAppScale,
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.AppScale),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(
                    if (stateValues.isNarrowScreen) 1f else 0.6f
                ),
            contentPadding = PaddingValues(vertical = stateValues.marginTextField)
        ) {
            item {
                AppSizeModeSettingsItemWidget(
                    id = 0L,
                    name = localizedStringResource(911, "Default"),
                    description = localizedStringResource(915, "Current comfortable size"),
                    isActive = stateValues.appSizeModeId == 0L
                )
            }
            item {
                AppSizeModeSettingsItemWidget(
                    id = 1L,
                    name = localizedStringResource(912, "Big"),
                    description = localizedStringResource(916, "Larger text and controls for easier reading"),
                    isActive = stateValues.appSizeModeId == 1L
                )
            }
            item {
                AppSizeModeSettingsItemWidget(id = 2L, name = storePeopleText("large"),
                    description = storePeopleText("large_help"), isActive = stateValues.appSizeModeId == 2L)
            }
        }
    }
}


internal data class AppModeOptionUiModel(
    val modeId: Int,
    val title: String,
    val subtitle: String,
    val promise: String,
    val iconPath: String,
    val iconRes: DrawableResource?,
    val features: List<String>
)

internal fun AppConfiguration.appModeOptions(): List<AppModeOptionUiModel> = listOf(
    AppModeOptionUiModel(
        modeId = APP_MODE_STORE,
        title = localizedStringResource(724, "Store mode"),
        subtitle = localizedStringResource(1385, "Point of sale, stock, shifts, workers and store operations."),
        promise = localizedStringResource(1386, "Best for a shop team serving customers right now."),
        iconPath = stateValues.drawablePathIconAppModeStore,
        iconRes = stateValues.drawableResIconAppModeStore.value,
        features = listOf(
            localizedStringResource(86, "Sale"),
            localizedStringResource(87, "Return"),
            localizedStringResource(88, "Supply"),
            localizedStringResource(756, "Stock")
        )
    ),
    AppModeOptionUiModel(
        modeId = APP_MODE_BUYER,
        title = authUiText("Buyer · preview", "Покупатель · предварительная версия", "Сатып алушы · алдын ала нұсқа", "Сатып алуучу · алдын ала көрүнүш"),
        subtitle = authUiText("Browse published shop windows, save products and compare matching offers.",
            "Смотрите витрины, сохраняйте товары и сравнивайте предложения.", "Витриналарды қарап, тауарларды сақтаңыз және ұсыныстарды салыстырыңыз.", "Дүкөндөрдүн жарыяланган витриналарын карап, товарларды сактап, дал келген сунуштарды салыштырыңыз."),
        promise = authUiText("Discovery first. Ordering and online payment are not enabled yet.",
            "Сначала — выбор товаров. Заказ и онлайн-оплата пока не подключены.", "Әзірге — тауар таңдау. Тапсырыс пен онлайн төлем әлі қосылмаған.", "Азырынча издөө жана таанышуу гана. Тапшырык берүү жана онлайн төлөм иштетиле элек."),
        iconPath = marketIconPath(139),
        iconRes = marketIconFallback(139),
        features = listOf(authUiText("Shop windows", "Витрины", "Витриналар", "Дүкөн витриналары"),
            authUiText("Saved offers", "Сохранённое", "Сақталғандар", "Сакталган сунуштар"), authUiText("Comparison", "Сравнение", "Салыстыру", "Салыштыруу"))
    ),
    AppModeOptionUiModel(
        modeId = APP_MODE_SUPPLIER,
        title = localizedStringResource(726, "Supplier mode"),
        subtitle = localizedStringResource(1389, "Store demand inbox, fulfillment statuses, B2B replies and delivery rhythm."),
        promise = localizedStringResource(1390, "Best for wholesalers and distributors serving many stores."),
        iconPath = stateValues.drawablePathIconAppModeSupplier,
        iconRes = stateValues.drawableResIconAppModeSupplier.value,
        features = listOf(
            localizedStringResource(1341, "Smart order inbox"),
            localizedStringResource(1338, "Catalog"),
            localizedStringResource(1453, "Partner stores"),
            localizedStringResource(1340, "Insights")
        )
    ),
    AppModeOptionUiModel(
        modeId = APP_MODE_MANUFACTURER,
        title = localizedStringResource(727, "Producer mode"),
        subtitle = localizedStringResource(1391, "Production batches, upstream planning, quality and distributor bridge."),
        promise = localizedStringResource(1392, "Best for factories and makers feeding suppliers and stores."),
        iconPath = stateValues.drawablePathIconAppModeManufacturer,
        iconRes = stateValues.drawableResIconAppModeManufacturer.value,
        features = listOf(
            localizedStringResource(1393, "Production"),
            localizedStringResource(1394, "Batches"),
            localizedStringResource(1395, "Quality"),
            localizedStringResource(1431, "Manufacturer bridge")
        )
    )
)

internal fun appModeIsAvailableInCurrentRelease(optionModeId: Int, currentModeId: Int): Boolean =
    optionModeId in setOf(APP_MODE_STORE, APP_MODE_SUPPLIER, APP_MODE_BUYER)

internal fun AppConfiguration.availableAppModeOptions(currentModeId: Int): List<AppModeOptionUiModel> =
    appModeOptions().filter { option -> appModeIsAvailableInCurrentRelease(option.modeId, currentModeId) }

@Composable
internal fun AppConfiguration.AppModeSelectionCard(
    option: AppModeOptionUiModel, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Row(modifier
        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
        .clip(RoundedCornerShape(stateValues.cornerRadius))
        .background(stateValues.BackgroundColor)
        .border(if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
            if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor.copy(alpha = .55f),
            RoundedCornerShape(stateValues.cornerRadius))
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        CpImage(Modifier.size(36.dp), url = option.iconPath, fallbackRes = option.iconRes,
            contentDescription = null, tintColor = null)
        Text(accountPresentationText(when(option.modeId) {
            APP_MODE_STORE -> "store"; APP_MODE_BUYER -> "marketplace"; else -> "supplier"
        }), Modifier.weight(1f), color = if(selected) stateValues.AccentColor else stateValues.TextColor,
            style = androidx.compose.ui.text.TextStyle(fontFamily = LocalAitaFontFamily.current, background = Color.Transparent),
            fontSize = stateValues.accentTextSize, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppConfiguration.MenuAppModeScreen() {
    val options = availableAppModeOptions(stateValues.appModeId)
    AitaScreenColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, appBar = {
        ScreenAppBarWidget(title = stateValues.stringAppMode, iconPath = stateValues.drawablePathIconSwitch,
            iconRes = stateValues.drawableResIconSwitch.value,
            onBack = if(Navigation.Menu.isVeryFirstScreen(stateValues.isNarrowScreen)) null else {
                { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } }
            })
    }) {
        LazyColumn(state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.AppMode),
            modifier = Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
            items(options, key = { it.modeId }) { option ->
                AppModeSelectionCard(option, stateValues.appModeId == option.modeId, Modifier.fillMaxWidth()) {
                    if (stateValues.appModeId != option.modeId) {
                        setAppMode(option.modeId)
                        coroutineScope.launch {
                            Navigation.Menu.clearLeft(); Navigation.Menu.clearRight()
                            Navigation.goMain(defaultMainScreenForAppMode(option.modeId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppConfiguration.MenuAppLanguageScreen() {
    AitaScreenColumn(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringAppLanguage,
                iconPath = stateValues.drawablePathIconAppLanguage,
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.AppLanguage),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(
                    if (stateValues.isNarrowScreen) 1f else 0.6f
                )
        ) {
            item {
                val settingsIconRes by stateValues.drawableResIconSettings.collectAsState()

                AppLanguageSettingsItemWidget(
                    "system",
                    flagDrawablePath = stateValues.drawablePathIconSettings,
                    flagDrawableRes = settingsIconRes,
                    name = stateValues.stringSystemLanguage,
                    isActive = stateValues.appLanguagePreference == "system"
                )
            }

            items(stateValues.globalAppConfiguration.languages.withBundledAppLanguages()) { language ->
                AppLanguageSettingsItemWidget(
                    language = language.language,
                    name = language.name.extractLocalizedString(stateValues.appLanguage) ?: language.language,
                    flagDrawablePath = language.flagDrawablePath,
                    flagDrawableRes = language.mapIconRes(),
                    isActive = stateValues.appLanguagePreference == language.language
                )
            }
        }
    }
}

internal enum class MenuAnalyticsTab(val id: String) {
    Sales("sales"),
    Returns("returns"),
    Acceptance("acceptance"),
    Stock("stock"),
    Suppliers("suppliers"),
    Workers("workers"),
    CashRegister("cash_register");

    companion object {
        fun fromId(id: String): MenuAnalyticsTab {
            return entries.find { it.id == id } ?: Sales
        }
    }
}

internal enum class AnalyticsPeriodPreset(val id: String) {
    Today("today"),
    Week("week"),
    Month("month"),
    All("all");

    companion object {
        fun fromId(id: String): AnalyticsPeriodPreset {
            return entries.find { it.id == id } ?: Month
        }
    }
}

internal data class AnalyticsPeriod(
    val startMillis: Long,
    val endMillisExclusive: Long
)

internal const val ANALYTICS_SCOPE_ALL = "all"
internal const val ANALYTICS_SCOPE_GOODS_ITEM = "goods_item"
internal const val ANALYTICS_SCOPE_SUPPLIER = "supplier"
internal const val ANALYTICS_SCOPE_CATEGORY = "category"

internal data class AnalyticsHistoryRow(
    val title: String,
    val count: Int,
    val total: Double
)

internal data class AnalyticsSummaryCardData(
    val title: String,
    val value: String,
    val subtitle: String? = null
)

internal fun TransactionDataModel.analyticsTotal(): Double = goodsInTransaction.sumOf { it.quantity * it.pricePerUnit }
internal fun List<TransactionDataModel>.analyticsTyped(type: String): List<TransactionDataModel> = filter { it.type == type }

@Composable
internal fun AppConfiguration.analyticsReportTabTitle(tab: MenuAnalyticsTab): String = when (tab) {
    MenuAnalyticsTab.Sales -> stateValues.stringSale
    MenuAnalyticsTab.Returns -> stateValues.stringReturn
    MenuAnalyticsTab.Acceptance -> stateValues.stringSupply
    MenuAnalyticsTab.Stock -> stateValues.stringStock
    MenuAnalyticsTab.Suppliers -> stateValues.stringSuppliers
    MenuAnalyticsTab.Workers -> stateValues.stringWorkers
    MenuAnalyticsTab.CashRegister -> localizedStringResource(256, "Cash registers")
}

@Composable
internal fun AppConfiguration.analyticsReportScopeText(
    scopeType: String,
    selectedGoodsItemId: String,
    selectedSupplierId: String,
    selectedCategoryId: String
): String = when (scopeType) {
    ANALYTICS_SCOPE_GOODS_ITEM -> {
        val name = stateValues.stock.orEmpty().find { it.id == selectedGoodsItemId }?.name?.visibleLocalizedString(stateValues.appLanguage, selectedGoodsItemId.take(8))
            ?: selectedGoodsItemId.takeIf { it.isNotBlank() }
            ?: localizedStringResource(156, "Not specified")
        "${localizedStringResource(1165, "Goods item")}: $name"
    }
    ANALYTICS_SCOPE_SUPPLIER -> {
        val name = stateValues.suppliers.orEmpty().find { it.id == selectedSupplierId }?.name?.visibleLocalizedString(stateValues.appLanguage, selectedSupplierId.take(8))
            ?: selectedSupplierId.takeIf { it.isNotBlank() }
            ?: localizedStringResource(156, "Not specified")
        "${localizedStringResource(1166, "Supplier")}: $name"
    }
    ANALYTICS_SCOPE_CATEGORY -> {
        val name = stateValues.goodsCategories.orEmpty().find { it.id == selectedCategoryId }?.name?.visibleLocalizedString(stateValues.appLanguage, selectedCategoryId.take(8))
            ?: selectedCategoryId.takeIf { it.isNotBlank() }
            ?: localizedStringResource(156, "Not specified")
        "${localizedStringResource(1167, "Category")}: $name"
    }
    else -> localizedStringResource(1164, "All goods")
}

@Composable
internal fun AppConfiguration.buildAnalyticsReportSnapshotForUi(
    selectedTab: MenuAnalyticsTab,
    periodPresetId: String,
    startDateText: String,
    endDateText: String,
    analyticsScopeType: String,
    selectedAnalyticsGoodsItemId: String,
    selectedAnalyticsSupplierId: String,
    selectedAnalyticsCategoryId: String,
    scopedTransactions: List<TransactionDataModel>,
    preparedAnalytics: PreparedAnalytics,
    stock: List<GoodsItemDataModel>,
    batches: List<GoodsBatchDataModel>,
    suppliers: List<SupplierDataModel>,
    workers: List<StoreWorkerDataModel>,
    cashRegister: StoreCashRegisterDataModel?,
    cashRegisterEvents: List<CashRegisterEventDataModel>,
    dashboard: StoreAnalyticsDashboardDataModel?,
    currencyCode: String,
    generatedAtMillis: Long
): AnalyticsReportSnapshotDataModel {
    fun row(title: String, value: String, note: String = "") = AnalyticsReportRowDataModel(title, value, note)
    val reportCurrency = dashboard?.currencyCode?.takeIf { it.isNotBlank() } ?: currencyCode
    val sales = preparedAnalytics.type("purchase")
    val returns = preparedAnalytics.type("return")
    val supply = preparedAnalytics.type("accept")
    val periodText = if (periodPresetId == "all") localizedStringResource(425, "All period") else "$startDateText — $endDateText"
    val scopeText = analyticsReportScopeText(analyticsScopeType, selectedAnalyticsGoodsItemId, selectedAnalyticsSupplierId, selectedAnalyticsCategoryId)
    val storeName = stateValues.stores.orEmpty().findStoreOrBranchForUi(stateValues.activeStoreId)?.name?.visibleLocalizedString(stateValues.appLanguage, stateValues.activeStoreId.orEmpty().take(8))
        ?: localizedStringResource(38, "Store")

    val summaryRows = dashboard?.let { d ->
        listOf(
            row(localizedStringResource(694, "Gross sales"), d.grossSales.money(reportCurrency)),
            row(localizedStringResource(695, "Returns amount"), d.returnsAmount.money(reportCurrency)),
            row(localizedStringResource(697, "Net revenue"), d.netRevenue.money(reportCurrency)),
            row(localizedStringResource(673, "Gross profit estimate"), d.estimatedGrossProfit.money(reportCurrency), localizedStringResource(690, "Estimated from current/latest supply prices")),
            row(localizedStringResource(674, "Margin"), d.estimatedMarginPercent.percentText()),
            row(localizedStringResource(706, "Transactions"), d.transactionCount.toString()),
            row(stateValues.stringItems, d.soldQuantity.cleanNumber()),
            row(stateValues.stringCash, d.cashTotal.money(reportCurrency)),
            row(localizedStringResource(357, "Cashless"), d.cashlessTotal.money(reportCurrency)),
            row(stateValues.stringDebt, d.debtTotal.money(reportCurrency))
        )
    } ?: listOf(
        row(localizedStringResource(694, "Gross sales"), sales.total.money(reportCurrency)),
        row(localizedStringResource(695, "Returns amount"), returns.total.money(reportCurrency)),
        row(localizedStringResource(706, "Transactions"), preparedAnalytics.dashboard.transactionCount.toString()),
        row(stateValues.stringItems, preparedAnalytics.types.values.sumOf { it.quantity }.cleanNumber()),
        row(stateValues.stringCash, preparedAnalytics.types.values.sumOf { it.cash }.money(reportCurrency)),
        row(localizedStringResource(357, "Cashless"), preparedAnalytics.types.values.sumOf { it.card }.money(reportCurrency))
    )

    fun transactionRows(transactions: AnalyticsTypeSummary): List<AnalyticsReportRowDataModel> {
        val total = transactions.total
        val cash = transactions.cash
        val cashless = transactions.card
        val debt = (total - cash - cashless).coerceAtLeast(0.0)
        return listOf(
            row(localizedStringResource(706, "Transactions"), transactions.count.toString()),
            row(stateValues.stringTotal, total.money(reportCurrency)),
            row(stateValues.stringCash, if (transactions.paymentsKnown) cash.money(reportCurrency) else "—"),
            row(localizedStringResource(357, "Cashless"), if (transactions.paymentsKnown) cashless.money(reportCurrency) else "—"),
            row(stateValues.stringDebt, if (transactions.paymentsKnown) debt.money(reportCurrency) else "—"),
            row(stateValues.stringItems, transactions.quantity.cleanNumber())
        )
    }

    val selectedRows = when (selectedTab) {
        MenuAnalyticsTab.Sales -> transactionRows(sales)
        MenuAnalyticsTab.Returns -> transactionRows(returns) + listOf(
            row(localizedStringResource(1311, "Returned items"), (dashboard?.topReturnedItemsByQuantity?.size ?: 0).toString()),
            row(localizedStringResource(1312, "Return reasons"), dashboard?.topReturnedItemsByQuantity.orEmpty().flatMap { it.returnReasons }.map { it.reason }.distinct().size.toString())
        )
        MenuAnalyticsTab.Acceptance -> transactionRows(supply)
        MenuAnalyticsTab.Stock -> listOf(
            row(stateValues.stringItems, if (preparedAnalytics.stock.available) preparedAnalytics.stock.items.toString() else "—"),
            row(localizedStringResource(280, "Active items"), if (preparedAnalytics.stock.available) preparedAnalytics.stock.activeItems.toString() else "—"),
            row(stateValues.stringBatches, if (preparedAnalytics.stock.available) preparedAnalytics.stock.batches.toString() else "—"),
            row(localizedStringResource(284, "Active batches"), if (preparedAnalytics.stock.available) preparedAnalytics.stock.activeBatches.toString() else "—"),
            row(localizedStringResource(683, "Inventory value at sale price"), (dashboard?.stockValueAtSalePrice ?: 0.0).money(reportCurrency)),
            row(localizedStringResource(684, "Inventory value at supply cost"), (dashboard?.stockValueAtSupplyPrice ?: 0.0).money(reportCurrency)),
            row(localizedStringResource(685, "Low stock items"), (dashboard?.lowStockItemCount ?: 0).toString()),
            row(localizedStringResource(687, "Expired batches"), (dashboard?.expiredBatchCount ?: 0).toString())
        )
        MenuAnalyticsTab.Suppliers -> listOf(
            row(stateValues.stringSuppliers, if (preparedAnalytics.remoteOnly) "—" else suppliers.count { it.isActive }.toString()),
            row(localizedStringResource(706, "Transactions"), supply.count.toString()),
            row(localizedStringResource(707, "Accepted goods value"), (dashboard?.supplyCost ?: supply.total).money(reportCurrency))
        )
        MenuAnalyticsTab.Workers -> listOf(
            row(stateValues.stringWorkers, workers.count { it.isActive }.toString()),
            row(localizedStringResource(654, "Admin"), workers.count { it.roleId == WORKER_ROLE_ADMIN || it.roleId == WORKER_ROLE_OWNER }.toString()),
            row(localizedStringResource(655, "Worker"), workers.count { it.roleId == WORKER_ROLE_STANDARD }.toString()),
            row(localizedStringResource(706, "Transactions"), preparedAnalytics.dashboard.transactionCount.toString())
        )
        MenuAnalyticsTab.CashRegister -> if (!preparedAnalytics.cashAvailable) listOf(row(localizedStringResource(417, "Events"), "—")) else listOf(
            row(localizedStringResource(327, "Current amount"), (cashRegister?.currentAmount ?: 0.0).money(reportCurrency)),
            row(localizedStringResource(417, "Events"), cashRegisterEvents.size.toString()),
            row(stateValues.stringSale, preparedAnalytics.saleCash.money(reportCurrency)),
            row(stateValues.stringReturn, preparedAnalytics.returnCash.money(reportCurrency)),
            row(localizedStringResource(662, "Cash extractions"), preparedAnalytics.extractedCash.money(reportCurrency))
        )
    }

    val selectedNotes = when (selectedTab) {
        MenuAnalyticsTab.Sales -> dashboard?.topItemsByRevenue.orEmpty().take(5).mapIndexed { index, item ->
            "${index + 1}. ${item.name.visibleLocalizedString(stateValues.appLanguage, item.id.take(8))}: ${item.amount.money(item.currencyCode.ifBlank { reportCurrency })} · ${item.quantity.cleanNumber()}"
        }
        MenuAnalyticsTab.Returns -> dashboard?.topReturnedItemsByQuantity.orEmpty().take(8).mapIndexed { index, item ->
            val reasons = item.returnReasons.joinToString("; ") { reason ->
                val label = reason.reason.takeIf { it.isNotBlank() } ?: localizedStringResource(1313, "No reason provided")
                "$label × ${reason.quantity.cleanNumber()}"
            }.ifBlank { localizedStringResource(1313, "No reason provided") }
            "${index + 1}. ${item.name.visibleLocalizedString(stateValues.appLanguage, item.id.take(8))}: ${item.quantity.cleanNumber()} · $reasons"
        }
        else -> emptyList()
    }

    return AnalyticsReportSnapshotDataModel(
        title = localizedStringResource(1239, "Analytics report"),
        storeName = storeName,
        periodText = periodText,
        scopeText = scopeText,
        generatedAtMillis = generatedAtMillis,
        sections = listOf(
            AnalyticsReportSectionDataModel(localizedStringResource(412, "Summary"), summaryRows),
            AnalyticsReportSectionDataModel(analyticsReportTabTitle(selectedTab), selectedRows, selectedNotes),
            AnalyticsReportSectionDataModel(
                localizedStringResource(1274, "Filters"),
                listOf(
                    row(localizedStringResource(1271, "Report period"), periodText),
                    row(localizedStringResource(1272, "Report scope"), scopeText),
                    row(localizedStringResource(1273, "Selected analytics tab"), analyticsReportTabTitle(selectedTab)),
                    row(localizedStringResource(1275, "Generated"), receiptUiDateTime(generatedAtMillis))
                )
            )
        )
    )
}

@Composable
internal fun AppConfiguration.AnalyticsReportPreview(snapshot: AnalyticsReportSnapshotDataModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(Color.White)
            .border(stateValues.unfocusedBorderWidth, Color(0xFFE0E0E0), RoundedCornerShape(stateValues.cornerRadius))
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text(snapshot.title.uppercase(), color = Color.Black, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(snapshot.storeName, color = Color.Black, fontSize = stateValues.textSize, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Text("${snapshot.periodText} · ${snapshot.scopeText}", color = Color(0xFF333333), fontSize = stateValues.smallTextSize, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Text(receiptUiDateTime(snapshot.generatedAtMillis), color = Color(0xFF333333), fontSize = stateValues.smallTextSize, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222222)))
        Spacer(modifier = Modifier.height(12.dp))
        snapshot.sections.forEach { section ->
            Text(section.title, color = Color.Black, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
            section.rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    Text(row.title, color = Color.Black, fontSize = stateValues.smallTextSize, modifier = Modifier.weight(1f))
                    Text(row.value, color = Color.Black, fontSize = stateValues.smallTextSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
                }
                if (row.note.isNotBlank()) {
                    Text(row.note, color = Color(0xFF333333), fontSize = stateValues.smallTextSize, modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp))
                }
            }
            section.notes.forEach { note ->
                Text(note, color = Color(0xFF333333), fontSize = stateValues.smallTextSize, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222222)))
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun AppConfiguration.AnalyticsReportBottomSheet(snapshot: AnalyticsReportSnapshotDataModel, onDismiss: () -> Unit) {
    val fileName = remember(snapshot) { snapshot.analyticsReportPdfFileName() }
    var pdfCache by remember(snapshot) { mutableStateOf<ByteArray?>(null) }
    var activeExport by remember(snapshot) { mutableStateOf<String?>(null) }
    val exportScope = rememberCoroutineScope()
    val saveNotConfiguredText = localizedStringResource(1267, "PDF export is not configured for this platform")
    val shareNotConfiguredText = localizedStringResource(1268, "PDF sharing is not configured for this platform")
    val printNotConfiguredText = localizedStringResource(1269, "Paper document printing is not configured for this platform")
    val saveSuccessText = localizedStringResource(1246, "Report PDF saved")
    val shareSuccessText = localizedStringResource(1247, "Report PDF shared")
    val printSuccessText = localizedStringResource(1248, "Report opened for printing")

    fun export(action: String) {
        if (activeExport != null) return
        val owner = captureReceiptActionOwner()
        activeExport = action
        exportScope.launch {
            try {
                if (action == "print" && preferHtmlDocumentPrinting) {
                    val html = snapshot.buildAnalyticsReportPdfDocument().toPrintHtml(fileName)
                    if (!owner.isCurrent()) return@launch
                    receiptActionNotification(printHtmlDocument(fileName, html), printSuccessText, owner)
                    return@launch
                }
                val bytes = pdfCache ?: withContext(Dispatchers.Default) { snapshot.buildAnalyticsReportPdfBytes() }.also { pdfCache = it }
                if (!owner.isCurrent()) return@launch
                val result = when (action) {
                    "pdf" -> savePdfDocument(fileName, bytes, saveNotConfiguredText)
                    "share" -> sharePdfDocument(fileName, bytes, shareNotConfiguredText)
                    else -> printPdfDocument(fileName, bytes, printNotConfiguredText)
                }
                receiptActionNotification(result, when (action) { "pdf" -> saveSuccessText; "share" -> shareSuccessText; else -> printSuccessText }, owner)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { if (owner.isCurrent()) postInAppNotification(deviceWorkflowText("report_failed"), NotificationType.Negative, transient = true) }
            finally { activeExport = null }
        }
    }

    AitaBottomSheet(
        title = localizedStringResource(1239, "Analytics report"),
        iconPath = stateValues.drawablePathIconAnalyticsReport,
        iconRes = stateValues.drawableResIconAnalyticsReport.value,
        onDismiss = onDismiss
    ) {
        MessageText(
            modifier = Modifier.fillMaxWidth(),
            text = localizedStringResource(1240, "Printable summary"),
            subText = if (preferHtmlDocumentPrinting) deviceWorkflowText("system_print_help") else localizedStringResource(1249, "Transaction receipts use ESC/POS thermal printers. Analytics reports use A4 paper printing."),
            textSize = stateValues.textSize,
            subTextSize = stateValues.smallTextSize
        )
        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
        AnalyticsReportPreview(snapshot = snapshot, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

        if (stateValues.isNarrowScreen) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1243, "Save report PDF"),
                    iconPath = stateValues.drawablePathIconReceipt,
                    iconRes = stateValues.drawableResIconReceipt.value,
                    confirmationRequired = false,
                    enabled = activeExport == null,
                    loading = activeExport == "pdf",
                    autoLoading = false,
                    onClick = { export("pdf") }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1244, "Share report PDF"),
                    iconPath = stateValues.drawablePathIconShare,
                    iconRes = stateValues.drawableResIconShare.value,
                    confirmationRequired = false,
                    enabled = activeExport == null,
                    loading = activeExport == "share",
                    autoLoading = false,
                    onClick = { export("share") }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1245, "Print report"),
                    iconPath = stateValues.drawablePathIconDevices,
                    iconRes = stateValues.drawableResIconDevices.value,
                    confirmationRequired = false,
                    enabled = activeExport == null,
                    loading = activeExport == "print",
                    autoLoading = false,
                    onClick = { export("print") }
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                horizontalAlignment = Alignment.Start
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1243, "Save report PDF"),
                    iconPath = stateValues.drawablePathIconReceipt,
                    iconRes = stateValues.drawableResIconReceipt.value,
                    confirmationRequired = false,
                    enabled = activeExport == null,
                    loading = activeExport == "pdf",
                    autoLoading = false,
                    onClick = { export("pdf") }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1244, "Share report PDF"),
                    iconPath = stateValues.drawablePathIconShare,
                    iconRes = stateValues.drawableResIconShare.value,
                    confirmationRequired = false,
                    enabled = activeExport == null,
                    loading = activeExport == "share",
                    autoLoading = false,
                    onClick = { export("share") }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1245, "Print report"),
                    iconPath = stateValues.drawablePathIconDevices,
                    iconRes = stateValues.drawableResIconDevices.value,
                    confirmationRequired = false,
                    enabled = activeExport == null,
                    loading = activeExport == "print",
                    autoLoading = false,
                    onClick = { export("print") }
                )
            }
        }
    }
}


@Composable
fun AppConfiguration.MenuAnalyticsScreen() {
    var showAnalyticsReportSheet by rememberSaveable { mutableStateOf(false) }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringAnalytics,
                iconPath = stateValues.drawablePathIconAnalytics,
                trailingIcons = listOf(
                    Triple(stateValues.drawablePathIconAnalyticsReport, stateValues.drawableResIconAnalyticsReport.value) {
                        showAnalyticsReportSheet = true
                    }
                ),
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        val activeStoreId = stateValues.activeStoreId
        if (!currentUserCanViewAnalytics(activeStoreId)) {
            MessageText(text = if (activeStoreId.isNullOrBlank())
                authUiText("Select a store to view analytics", "Выберите магазин для просмотра аналитики", "Аналитиканы көру үшін дүкенді таңдаңыз", "Талдоону көрүү үчүн дүкөндү тандаңыз")
                else authUiText("Analytics access is not available", "Нет доступа к аналитике", "Аналитикаға қолжетімділік жоқ", "Талдоого жетүү мүмкүн эмес"))
            return@AitaScreenColumn
        }

        LaunchedEffect(activeStoreId) {
            AnalyticsWorkspace.refreshServerTotalsIfNeeded()
            activeStoreId?.let { storeId ->
                if (currentUserCanViewTransactionHistory(storeId)) getTransactions(storeId)
                if (currentUserCanViewStock(storeId)) { getStock(storeId); getStockBatches(storeId) }
                if (currentUserCanViewSuppliers(storeId)) getSuppliers()
                if (currentUserCanViewCashRegister(storeId)) getCashRegister(storeId)
                if (currentUserCanViewWorkers(storeId)) getStoreWorkers(storeId)
                if (currentUserCanDecideWorkerRequests(storeId)) getIncomingWorkerRequests(storeId)
                getMyWorkerMemberships()
            }
        }

        val cashRegisterPayload by cashRegisterState.payload.collectAsState()
        val cashRegister = cashRegisterPayload?.takeIf { it.storeId == activeStoreId }

        val initialSelection = remember(activeStoreId) { AnalyticsWorkspace.selectionForCurrentStore() }
        val initialPeriod = remember(activeStoreId) {
            if (initialSelection.periodId == "custom") {
                initialSelection.customStartMillis.takeIf { it > 0L }?.toStockDateInputText().orEmpty() to
                    initialSelection.customEndMillis.takeIf { it < Long.MAX_VALUE && it > 0L }?.let { (it - 1L).toStockDateInputText() }.orEmpty()
            } else transactionHistoryPresetDates(initialSelection.periodId)
        }
        var periodPresetId by rememberSaveable(activeStoreId) { mutableStateOf(initialSelection.periodId) }
        var startDateText by rememberSaveable(activeStoreId) { mutableStateOf(initialPeriod.first) }
        var endDateText by rememberSaveable(activeStoreId) { mutableStateOf(initialPeriod.second) }
        var calendarTarget by rememberSaveable { mutableStateOf<String?>(null) }

        calendarTarget?.let { target ->
            TransactionHistoryCalendarDialog(
                title = if (target == "start") localizedStringResource(403, "Pick start date") else localizedStringResource(404, "Pick end date"),
                selectedDateText = if (target == "start") startDateText else endDateText,
                onDismiss = { calendarTarget = null },
                onDateSelected = { selectedDate ->
                    periodPresetId = "custom"
                    if (target == "start") {
                        startDateText = selectedDate
                        val startMillis = stockDateInputTextToMillis(selectedDate)
                        val endMillis = stockDateInputTextToMillis(endDateText)
                        if (startMillis != null && endMillis != null && startMillis > endMillis) {
                            endDateText = selectedDate
                        }
                    } else {
                        endDateText = selectedDate
                        val startMillis = stockDateInputTextToMillis(startDateText)
                        val endMillis = stockDateInputTextToMillis(selectedDate)
                        if (startMillis != null && endMillis != null && endMillis < startMillis) {
                            startDateText = selectedDate
                        }
                    }
                }
            )
        }

        var analyticsScopeType by rememberSaveable(activeStoreId) { mutableStateOf(when {
            initialSelection.goodsItemId != null -> ANALYTICS_SCOPE_GOODS_ITEM
            initialSelection.supplierId != null -> ANALYTICS_SCOPE_SUPPLIER
            initialSelection.categoryId != null -> ANALYTICS_SCOPE_CATEGORY
            else -> ANALYTICS_SCOPE_ALL
        }) }
        var selectedAnalyticsGoodsItemId by rememberSaveable(activeStoreId) { mutableStateOf(initialSelection.goodsItemId.orEmpty()) }
        var selectedAnalyticsSupplierId by rememberSaveable(activeStoreId) { mutableStateOf(initialSelection.supplierId.orEmpty()) }
        var selectedAnalyticsCategoryId by rememberSaveable(activeStoreId) { mutableStateOf(initialSelection.categoryId.orEmpty()) }

        val stockForAnalytics = stateValues.stock.orEmpty()
        val stockBatchesForAnalytics = stateValues.stockBatches.orEmpty()
        val suppliersForAnalytics = stateValues.suppliers.orEmpty()
        val categoriesForAnalytics = stateValues.goodsCategories.orEmpty()

        val analyticsGoodsItemFilter = selectedAnalyticsGoodsItemId.takeIf { analyticsScopeType == ANALYTICS_SCOPE_GOODS_ITEM && it.isNotBlank() }
        val analyticsSupplierFilter = selectedAnalyticsSupplierId.takeIf { analyticsScopeType == ANALYTICS_SCOPE_SUPPLIER && it.isNotBlank() }
        val analyticsCategoryFilter = selectedAnalyticsCategoryId.takeIf { analyticsScopeType == ANALYTICS_SCOPE_CATEGORY && it.isNotBlank() }

        val startMillis = stockDateInputTextToMillis(startDateText)
        val endExclusiveMillis = stockDateInputTextToLocalDate(endDateText)
            ?.let { transactionHistoryPlusDays(it, 1).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() }

        val analyticsSelection = AnalyticsSelection(
            periodId = periodPresetId,
            customStartMillis = if (periodPresetId == "custom") startMillis ?: 0L else 0L,
            customEndMillis = if (periodPresetId == "custom") endExclusiveMillis ?: Long.MAX_VALUE else Long.MAX_VALUE,
            goodsItemId = analyticsGoodsItemFilter, supplierId = analyticsSupplierFilter, categoryId = analyticsCategoryFilter
        )
        val preparedState by AnalyticsWorkspace.state.collectAsState()
        val analyticsFailed by AnalyticsWorkspace.failure.collectAsState()
        val prepared = preparedState?.takeIf { AnalyticsWorkspace.isCurrent(it, analyticsSelection) }
        val scopedTransactions = prepared?.transactions.orEmpty()
        val scopedCashRegisterEvents = prepared?.events.orEmpty()
        val analyticsDashboard = prepared?.dashboard

        LaunchedEffect(periodPresetId, prepared?.window) {
            // Relative periods advance while the app stays open; keep their displayed dates in sync.
            if (periodPresetId != "custom") {
                val range = transactionHistoryPresetDates(periodPresetId)
                startDateText = range.first
                endDateText = range.second
            }
        }
        LaunchedEffect(activeStoreId, analyticsSelection) {
            AnalyticsWorkspace.select(analyticsSelection)
        }

        var analyticsSection by rememberNavigationSection("analytics:section", MenuAnalyticsTab.Sales.id)
        val filters: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth()) {
        tabRowWidget(
            selectedIndexInitial = analyticsSection,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = stateValues.marginTextField, vertical = 2.dp),
            tabs = listOf(
                TabContent(MenuAnalyticsTab.Sales.id, stateValues.stringSale) { analyticsSection = it },
                TabContent(MenuAnalyticsTab.Returns.id, stateValues.stringReturn) { analyticsSection = it },
                TabContent(MenuAnalyticsTab.Acceptance.id, stateValues.stringSupply) { analyticsSection = it },
                TabContent(MenuAnalyticsTab.Stock.id, stateValues.stringStock) { analyticsSection = it },
                TabContent(MenuAnalyticsTab.Suppliers.id, stateValues.stringSuppliers) { analyticsSection = it },
                TabContent(MenuAnalyticsTab.Workers.id, stateValues.stringWorkers) { analyticsSection = it },
                TabContent(MenuAnalyticsTab.CashRegister.id, localizedStringResource(256, "Cash registers")) { analyticsSection = it }
            )
        )

        val periodOptions = listOf(
            "today" to localizedStringResource(260, "Today"),
            "7" to localizedStringResource(261, "7 days"),
            "30" to localizedStringResource(262, "30 days"),
            "month" to localizedStringResource(387, "This month"),
            "year" to localizedStringResource(388, "This year"),
            "all" to localizedStringResource(425, "All period"),
            "custom" to localizedStringResource(386, "Custom period")
        )

        tabRowWidget(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = stateValues.marginTextField),
            tabs = periodOptions.map { option ->
                TabContent(option.first, option.second, icon = AitaTabIcon.Calendar) { selectedPeriodId ->
                    periodPresetId = selectedPeriodId
                    if (selectedPeriodId != "custom") {
                        val range = transactionHistoryPresetDates(selectedPeriodId)
                        startDateText = range.first
                        endDateText = range.second
                    }
                }
            },
            selectedIndexInitial = periodPresetId,
            textSize = stateValues.smallTextSize
        )

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Start)
                .padding(horizontal = stateValues.marginTextField),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            verticalAlignment = Alignment.Top
        ) {
            TransactionHistoryDateButton(
                modifier = Modifier.weight(1f),
                title = localizedStringResource(396, "From"),
                dateText = startDateText,
                onClick = {
                    periodPresetId = "custom"
                    calendarTarget = "start"
                }
            )

            TransactionHistoryDateButton(
                modifier = Modifier.weight(1f),
                title = localizedStringResource(397, "To"),
                dateText = endDateText,
                onClick = {
                    periodPresetId = "custom"
                    calendarTarget = "end"
                }
            )
        }

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = stateValues.marginTextField, vertical = 2.dp),
            text = localizedStringResource(1163, "Analytics scope"),
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )

        val analyticsScopeOptions = listOf(
            ANALYTICS_SCOPE_ALL to localizedStringResource(1164, "All goods"),
            ANALYTICS_SCOPE_GOODS_ITEM to localizedStringResource(1165, "Goods item"),
            ANALYTICS_SCOPE_SUPPLIER to localizedStringResource(1166, "Supplier"),
            ANALYTICS_SCOPE_CATEGORY to localizedStringResource(1167, "Category")
        )

        tabRowWidget(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = stateValues.marginTextField),
            tabs = analyticsScopeOptions.map { option ->
                TabContent(option.first, option.second) { selectedScopeId ->
                    analyticsScopeType = selectedScopeId
                }
            },
            selectedIndexInitial = analyticsScopeType,
            textSize = stateValues.smallTextSize
        )

        val analyticsDomains = key(activeStoreId, analyticsScopeType, stateValues.appLanguage) {
            analyticsScopeDomains(analyticsScopeType, stockForAnalytics, suppliersForAnalytics, categoriesForAnalytics)
        }

        when (analyticsScopeType) {
            ANALYTICS_SCOPE_GOODS_ITEM -> {
                if (analyticsDomains.isNotEmpty()) {
                    val selectedInitial = selectedAnalyticsGoodsItemId.takeIf { selected -> analyticsDomains.any { it.id == selected } }
                        ?: analyticsDomains.first().id
                    val selector = dropdownListWidget(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = stateValues.marginTextField, vertical = 4.dp),
                        titleText = localizedStringResource(1168, "Select exact analytics target"),
                        domains = analyticsDomains,
                        selectedInitial = selectedInitial,
                        showId = true,
                        showName = true,
                        search = Triple(stateValues.stringSearchByAnyData, NavigationScreenModel.Menu.Analytics, "analytics_scope_goods_item_search")
                    )
                    LaunchedEffect(selector.selectedId) { selectedAnalyticsGoodsItemId = selector.selectedId }
                }
            }
            ANALYTICS_SCOPE_SUPPLIER -> {
                if (analyticsDomains.isNotEmpty()) {
                    val selectedInitial = selectedAnalyticsSupplierId.takeIf { selected -> analyticsDomains.any { it.id == selected } }
                        ?: analyticsDomains.first().id
                    val selector = dropdownListWidget(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = stateValues.marginTextField, vertical = 4.dp),
                        titleText = localizedStringResource(1168, "Select exact analytics target"),
                        domains = analyticsDomains,
                        selectedInitial = selectedInitial,
                        showId = true,
                        showName = true,
                        search = Triple(stateValues.stringSearchByAnyData, NavigationScreenModel.Menu.Analytics, "analytics_scope_supplier_search")
                    )
                    LaunchedEffect(selector.selectedId) { selectedAnalyticsSupplierId = selector.selectedId }
                }
            }
            ANALYTICS_SCOPE_CATEGORY -> {
                if (analyticsDomains.isNotEmpty()) {
                    val selectedInitial = selectedAnalyticsCategoryId.takeIf { selected -> analyticsDomains.any { it.id == selected } }
                        ?: analyticsDomains.first().id
                    val selector = dropdownListWidget(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = stateValues.marginTextField, vertical = 4.dp),
                        titleText = localizedStringResource(1168, "Select exact analytics target"),
                        domains = analyticsDomains,
                        selectedInitial = selectedInitial,
                        showId = true,
                        showName = true,
                        search = Triple(stateValues.stringSearchByAnyData, NavigationScreenModel.Menu.Analytics, "analytics_scope_category_search")
                    )
                    LaunchedEffect(selector.selectedId) { selectedAnalyticsCategoryId = selector.selectedId }
                }
            }
        }

        Spacer(modifier = Modifier.height(stateValues.marginTextField))

            }
        }

        val selectedTab = MenuAnalyticsTab.fromId(analyticsSection)
        val currencyCode = cashRegister?.currencyCode?.takeIf { it.isNotBlank() } ?: currentAnalyticsCurrencyCode()
        val workersPayload by storeWorkerMembershipsState.payload.collectAsState()
        val workers = workersPayload.orEmpty()
        val analyticsReportGeneratedAt = remember(
            showAnalyticsReportSheet,
            selectedTab,
            periodPresetId,
            startDateText,
            endDateText,
            analyticsScopeType,
            selectedAnalyticsGoodsItemId,
            selectedAnalyticsSupplierId,
            selectedAnalyticsCategoryId
        ) { getCurrentTimeMillis() }
        if (prepared == null) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item("filters") { filters() }
                item("status") {
            MessageText(
                modifier = Modifier.fillMaxWidth().padding(stateValues.marginTextFieldGroup),
                text = if (analyticsFailed) authUiText("Could not prepare analytics", "Не удалось подготовить аналитику", "Аналитиканы дайындау мүмкін болмады", "Талдоону даярдоо мүмкүн болгон жок")
                    else authUiText("Preparing analytics…", "Подготавливаем аналитику…", "Аналитика дайындалуда…", "Талдоо даярдалууда…")
            )
            if (analyticsFailed) actionButton(text = authUiText("Retry", "Повторить", "Қайталау", "Кайталоо"), onClick = { AnalyticsWorkspace.retry() })
                }
            }
        } else {
            if (prepared.remoteOnly) MessageText(
                modifier = Modifier.fillMaxWidth().padding(stateValues.marginTextField),
                text = authUiText("Server totals; detailed records require additional access", "Итоги сервера; подробные записи требуют дополнительных прав", "Сервер қорытындылары; толық жазбаларға қосымша рұқсат қажет", "Сервердин жалпы жыйынтыктары; толук жазуулар үчүн кошумча укук керек")
            )
            if (showAnalyticsReportSheet) {
                val analyticsReportSnapshot = buildAnalyticsReportSnapshotForUi(
                    selectedTab = selectedTab,
                    periodPresetId = periodPresetId,
                    startDateText = startDateText,
                    endDateText = endDateText,
                    analyticsScopeType = analyticsScopeType,
                    selectedAnalyticsGoodsItemId = selectedAnalyticsGoodsItemId,
                    selectedAnalyticsSupplierId = selectedAnalyticsSupplierId,
                    selectedAnalyticsCategoryId = selectedAnalyticsCategoryId,
                    scopedTransactions = scopedTransactions,
                    preparedAnalytics = prepared,
                    stock = stockForAnalytics,
                    batches = stockBatchesForAnalytics,
                    suppliers = suppliersForAnalytics,
                    workers = workers,
                    cashRegister = cashRegister,
                    cashRegisterEvents = scopedCashRegisterEvents,
                    dashboard = analyticsDashboard,
                    currencyCode = currencyCode,
                    generatedAtMillis = analyticsReportGeneratedAt
                )
                AnalyticsReportBottomSheet(snapshot = analyticsReportSnapshot, onDismiss = { showAnalyticsReportSheet = false })
            }

            when (selectedTab) {
                MenuAnalyticsTab.Sales -> {
                    MenuAnalyticsTransactionScreen(
                        title = stateValues.stringSale,
                        transactionType = "purchase",
                        transactions = scopedTransactions,
                        currencyCode = currencyCode,
                        emptyText = localizedStringResource(258, "No sales in this period"),
                        analyticsDashboard = analyticsDashboard,
                        summary = prepared.type("purchase"), header = filters
                    )
                }

                MenuAnalyticsTab.Returns -> {
                    MenuAnalyticsTransactionScreen(
                        title = stateValues.stringReturn,
                        transactionType = "return",
                        transactions = scopedTransactions,
                        currencyCode = currencyCode,
                        emptyText = localizedStringResource(275, "No returns in this period"),
                        analyticsDashboard = analyticsDashboard,
                        summary = prepared.type("return"), header = filters
                    )
                }

                MenuAnalyticsTab.Acceptance -> {
                    MenuAnalyticsTransactionScreen(
                        title = stateValues.stringSupply,
                        transactionType = "accept",
                        transactions = scopedTransactions,
                        currencyCode = currencyCode,
                        emptyText = localizedStringResource(276, "No supply transactions in this period"),
                        analyticsDashboard = analyticsDashboard,
                        summary = prepared.type("accept"), header = filters
                    )
                }

                MenuAnalyticsTab.Stock -> MenuAnalyticsStockScreen(analyticsDashboard, prepared.stock, header = filters)

                MenuAnalyticsTab.Suppliers -> {
                    MenuAnalyticsSuppliersScreen(
                        prepared = prepared, header = filters,
                        dashboard = analyticsDashboard,
                        currencyCode = currencyCode
                    )
                }

                MenuAnalyticsTab.Workers -> {
                    MenuAnalyticsWorkersScreen(
                        workers = workers,
                        prepared = prepared, header = filters,
                        currencyCode = currencyCode
                    )
                }

                MenuAnalyticsTab.CashRegister -> {
                    if (!prepared.cashAvailable || !currentUserCanViewCashRegister(activeStoreId)) MessageText(text = authUiText("Detailed cash records are not available", "Подробные записи кассы недоступны", "Кассаның толық жазбалары қолжетімсіз", "Накталай акчанын толук жазуулары жеткиликсиз"))
                    else MenuAnalyticsCashRegisterScreen(
                        header = filters,
                        currentAmount = cashRegister?.currentAmount ?: 0.0,
                        events = scopedCashRegisterEvents,
                        currencyCode = currencyCode,
                        prepared = prepared
                    )
                }
            }
        }
    }
}

internal fun AppConfiguration.analyticsReturnReasonsSubtitle(item: AnalyticsRankedItemDataModel, currencyCode: String): String {
    val reasons = item.returnReasons
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" • ") { reason ->
            val label = reason.reason.takeIf { it.isNotBlank() } ?: localizedStringResource(1313, "No reason provided")
            val quantityText = reason.quantity.cleanNumber()
            val amountText = reason.amount.takeIf { it > 0.0 }?.let { " · ${it.money(item.currencyCode.ifBlank { currencyCode })}" }.orEmpty()
            "$label × $quantityText$amountText"
        }

    return listOfNotNull(
        "${localizedStringResource(706, "Transactions")}: ${item.transactionCount}",
        reasons?.let { "${localizedStringResource(1312, "Return reasons")}: $it" }
    ).joinToString("\n")
}

@Composable
internal fun AppConfiguration.MenuAnalyticsTransactionScreen(
    title: String,
    transactionType: String,
    transactions: List<TransactionDataModel>,
    currencyCode: String,
    emptyText: String,
    analyticsDashboard: StoreAnalyticsDashboardDataModel? = null,
    summary: AnalyticsTypeSummary,
    header: @Composable () -> Unit = {}
) {
    val totalCash = summary.cash
    val totalCard = summary.card
    val total = summary.total
    val paidTotal = totalCash + totalCard
    val debtTotal = summary.debt
    val average = summary.average
    val totalGoodsQuantity = summary.quantity
    val averageItems = summary.averageItems
    val historyRows = summary.history

    val dashboard = analyticsDashboard
    val salesDashboardCards = if (transactionType == "purchase" && dashboard != null) {
        listOf(
            AnalyticsSummaryCardData(
                title = localizedStringResource(694, "Gross sales"),
                value = dashboard.grossSales.money(dashboard.currencyCode.ifBlank { currencyCode }),
                subtitle = localizedStringResource(357, "Cash + cashless")
            ),
            AnalyticsSummaryCardData(
                title = localizedStringResource(697, "Net revenue"),
                value = dashboard.netRevenue.money(dashboard.currencyCode.ifBlank { currencyCode }),
                subtitle = "${localizedStringResource(695, "Returns amount")}: ${dashboard.returnsAmount.money(dashboard.currencyCode.ifBlank { currencyCode })}"
            ),
            AnalyticsSummaryCardData(
                title = localizedStringResource(673, "Gross profit estimate"),
                value = dashboard.estimatedGrossProfit.money(dashboard.currencyCode.ifBlank { currencyCode }),
                subtitle = localizedStringResource(690, "Estimated from current/latest supply prices")
            ),
            AnalyticsSummaryCardData(
                title = localizedStringResource(674, "Margin"),
                value = dashboard.estimatedMarginPercent.percentText()
            ),
            AnalyticsSummaryCardData(
                title = localizedStringResource(677, "Cashless share"),
                value = dashboard.cashlessSharePercent.percentText(),
                subtitle = "${stateValues.stringCash}: ${dashboard.cashSharePercent.percentText()} · ${stateValues.stringDebt}: ${dashboard.debtSharePercent.percentText()}"
            ),
            AnalyticsSummaryCardData(
                title = localizedStringResource(676, "Debt amount"),
                value = dashboard.debtTotal.money(dashboard.currencyCode.ifBlank { currencyCode })
            ),
            AnalyticsSummaryCardData(
                title = localizedStringResource(359, "Average transaction"),
                value = dashboard.averageSale.money(dashboard.currencyCode.ifBlank { currencyCode })
            ),
            AnalyticsSummaryCardData(
                title = localizedStringResource(710, "Average items"),
                value = dashboard.averageItemsPerSale.cleanNumber()
            ),
            AnalyticsSummaryCardData(
                title = localizedStringResource(689, "Sell-through estimate"),
                value = dashboard.sellThroughPercentEstimate.percentText(),
                subtitle = localizedStringResource(698, "Stock health")
            ),
            AnalyticsSummaryCardData(
                title = localizedStringResource(678, "Best hour"),
                value = dashboard.salesByHour.maxByOrNull { it.amount }?.label ?: "—"
            )
        )
    } else {
        emptyList()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        var section by rememberNavigationSection("section:analytics:$transactionType:${stateValues.activeStoreId.orEmpty()}", "overview")
        val tabs: @Composable () -> Unit = {
            sectionTabsWidget(
            selectedId = section, onSelected = { section = it },
            stateKey = "analytics:$transactionType:${stateValues.activeStoreId.orEmpty()}",
            tabs = buildList {
                add(TabContent("overview", authUiText("Overview", "Обзор", "Шолу", "Жалпы көрүнүш")))
                if (transactionType == "purchase" && dashboard != null) {
                    add(TabContent("revenue", localizedStringResource(680, "Top items by revenue")))
                    add(TabContent("quantity", localizedStringResource(681, "Top items by quantity")))
                    add(TabContent("days", localizedStringResource(691, "Sales by day")))
                    add(TabContent("hours", localizedStringResource(692, "Sales by hour")))
                }
                if (transactionType == "return" && dashboard != null) {
                    add(TabContent("return_quantity", localizedStringResource(1311, "Returned items")))
                    add(TabContent("return_amount", localizedStringResource(1315, "Returned amount")))
                }
                add(TabContent("history", localizedStringResource(257, "History")))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2),
        )
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Analytics, "transaction_${transactionType}_$section"),
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                .padding(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item(key = "analytics-filters") { header(); tabs() }
            item(key = "MenuAnalyticsTransactionScreen:$section:0") {
                Text(
                    text = title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = stateValues.marginTextField)
                )
            }

            if (section == "overview") {
                item(key = "MenuAnalyticsTransactionScreen:$section:1") {
                    AnalyticsCardsGrid(
                        cards = if (salesDashboardCards.isNotEmpty()) {
                            salesDashboardCards
                        } else {
                            listOf(
                                AnalyticsSummaryCardData(
                                    title = stateValues.stringTotal,
                                    value = total.money(currencyCode),
                                    subtitle = localizedStringResource(357, "Cash + cashless")
                                ),
                                AnalyticsSummaryCardData(title = stateValues.stringCash, value = if (summary.paymentsKnown) totalCash.money(currencyCode) else "—"),
                                AnalyticsSummaryCardData(title = stateValues.stringCashless, value = if (summary.paymentsKnown) totalCard.money(currencyCode) else "—"),
                                AnalyticsSummaryCardData(title = localizedStringResource(676, "Debt amount"), value = if (summary.paymentsKnown) debtTotal.money(currencyCode) else "—"),
                                AnalyticsSummaryCardData(title = localizedStringResource(358, "Transactions"), value = summary.count.toString()),
                                AnalyticsSummaryCardData(title = localizedStringResource(359, "Average transaction"), value = average.money(currencyCode)),
                                AnalyticsSummaryCardData(title = localizedStringResource(710, "Average items"), value = averageItems.cleanNumber()),
                                AnalyticsSummaryCardData(title = stateValues.stringItems, value = totalGoodsQuantity.cleanNumber())
                            )
                        }
                    )
                }
            }

            if (transactionType == "purchase" && dashboard != null) {
                if (section == "revenue") {
                    item(key = "MenuAnalyticsTransactionScreen:$section:2") {
                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                        AnalyticsRankedItemsSection(
                            title = localizedStringResource(680, "Top items by revenue"),
                            items = dashboard.topItemsByRevenue,
                            valueTitle = localizedStringResource(693, "Revenue"),
                            currencyCode = dashboard.currencyCode.ifBlank { currencyCode },
                            valueSelector = { it.amount },
                            subtitleSelector = { item ->
                                "${localizedStringResource(705, "Sold quantity")}: ${item.quantity.cleanNumber()} · ${localizedStringResource(706, "Transactions")}: ${item.transactionCount}"
                            }
                        )
                    }
                }

                if (section == "quantity") {
                    item(key = "MenuAnalyticsTransactionScreen:$section:3") {
                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                        AnalyticsRankedItemsSection(
                            title = localizedStringResource(681, "Top items by quantity"),
                            items = dashboard.topItemsByQuantity,
                            valueTitle = localizedStringResource(271, "Quantity"),
                            currencyCode = "",
                            valueSelector = { it.quantity },
                            subtitleSelector = { item ->
                                "${localizedStringResource(693, "Revenue")}: ${item.amount.money(item.currencyCode.ifBlank { currencyCode })}"
                            }
                        )
                    }
                }

                if (section == "days") {
                    item(key = "MenuAnalyticsTransactionScreen:$section:4") {
                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                        AnalyticsBucketSection(
                            title = localizedStringResource(691, "Sales by day"),
                            buckets = dashboard.salesByDay,
                            currencyCode = dashboard.currencyCode.ifBlank { currencyCode }
                        )
                    }
                }

                if (section == "hours") {
                    item(key = "MenuAnalyticsTransactionScreen:$section:5") {
                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                        AnalyticsBucketSection(
                            title = localizedStringResource(692, "Sales by hour"),
                            buckets = dashboard.salesByHour.sortedByDescending { it.amount }.take(8),
                            currencyCode = dashboard.currencyCode.ifBlank { currencyCode }
                        )
                    }
                }
            }

            if (transactionType == "return" && dashboard != null) {
                if (section == "return_quantity") {
                    item(key = "MenuAnalyticsTransactionScreen:$section:6") {
                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                        AnalyticsRankedItemsSection(
                            title = localizedStringResource(1311, "Returned items"),
                            items = dashboard.topReturnedItemsByQuantity,
                            valueTitle = localizedStringResource(1314, "Returned quantity"),
                            currencyCode = "",
                            valueSelector = { it.quantity },
                            subtitleSelector = { item -> analyticsReturnReasonsSubtitle(item, dashboard.currencyCode.ifBlank { currencyCode }) }
                        )
                    }
                }

                if (section == "return_amount") {
                    item(key = "MenuAnalyticsTransactionScreen:$section:7") {
                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                        AnalyticsRankedItemsSection(
                            title = localizedStringResource(1315, "Returned amount"),
                            items = dashboard.topReturnedItemsByAmount,
                            valueTitle = localizedStringResource(1315, "Returned amount"),
                            currencyCode = dashboard.currencyCode.ifBlank { currencyCode },
                            valueSelector = { it.amount },
                            subtitleSelector = { item ->
                                "${localizedStringResource(1314, "Returned quantity")}: ${item.quantity.cleanNumber()}\n${analyticsReturnReasonsSubtitle(item, dashboard.currencyCode.ifBlank { currencyCode })}"
                            }
                        )
                    }
                }
            }

            if (section == "history") {
                item(key = "MenuAnalyticsTransactionScreen:$section:8") {
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    Text(
                        text = localizedStringResource(257, "History"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = stateValues.marginTextField)
                    )
                }

                if (!summary.historyKnown || summary.count == 0) {
                    item(key = "MenuAnalyticsTransactionScreen:$section:9") {
                        MessageText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = stateValues.marginTextFieldGroup),
                            text = if (summary.historyKnown) emptyText else authUiText("Detailed history is not available", "Подробная история недоступна", "Толық тарих қолжетімсіз", "Толук тарых жеткиликсиз")
                        )
                    }
                } else {
                    items(historyRows) { row ->
                        AnalyticsHistoryRowWidget(row = AnalyticsHistoryRow(row.title, row.count, row.total), currencyCode = currencyCode)
                        Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.MenuAnalyticsStockScreen(
    dashboard: StoreAnalyticsDashboardDataModel?,
    summary: AnalyticsStockSummary,
    header: @Composable () -> Unit = {}
) {
    val activeItems = summary.activeItems
    val inactiveItems = summary.items - activeItems
    val quickItems = summary.quickItems
    val activeBatches = summary.activeBatches
    val inactiveBatches = summary.batches - activeBatches
    val currencyCode = dashboard?.currencyCode?.takeIf { it.isNotBlank() } ?: currentAnalyticsCurrencyCode()

    Column(modifier = Modifier.fillMaxWidth()) {
        var section by rememberNavigationSection("section:analytics:stock:${stateValues.activeStoreId.orEmpty()}", "overview")
        val tabs: @Composable () -> Unit = {
            sectionTabsWidget(
            selectedId = section, onSelected = { section = it },
            stateKey = "analytics:stock:${stateValues.activeStoreId.orEmpty()}",
            tabs = listOf(
                TabContent("overview", authUiText("Overview", "Обзор", "Шолу", "Жалпы көрүнүш")),
                TabContent("slow_moving", localizedStringResource(682, "Slow-moving inventory"))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2),
        )
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Analytics, "stock_$section"),
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                .padding(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item(key = "analytics-filters") { header(); tabs() }
            item(key = "MenuAnalyticsStockScreen:$section:0") {
                Text(
                    text = stateValues.stringStock,
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = stateValues.marginTextField)
                )
            }

            if (section == "overview") {
                item(key = "MenuAnalyticsStockScreen:$section:1") {
                    AnalyticsCardsGrid(
                        cards = listOf(
                            AnalyticsSummaryCardData(title = stateValues.stringItems, value = if (summary.available) summary.items.toString() else "—", subtitle = localizedStringResource(282, "All stock items")),
                            AnalyticsSummaryCardData(title = localizedStringResource(280, "Active items"), value = if (summary.available) activeItems.toString() else "—"),
                            AnalyticsSummaryCardData(title = localizedStringResource(281, "Inactive items"), value = if (summary.available) inactiveItems.toString() else "—"),
                            AnalyticsSummaryCardData(title = stateValues.stringQuick, value = if (summary.available) quickItems.toString() else "—", subtitle = localizedStringResource(283, "Quick-sale items")),
                            AnalyticsSummaryCardData(title = stateValues.stringBatches, value = if (summary.available) summary.batches.toString() else "—"),
                            AnalyticsSummaryCardData(title = localizedStringResource(284, "Active batches"), value = if (summary.available) activeBatches.toString() else "—"),
                            AnalyticsSummaryCardData(title = localizedStringResource(285, "Inactive batches"), value = if (summary.available) inactiveBatches.toString() else "—"),
                            AnalyticsSummaryCardData(title = localizedStringResource(683, "Inventory value at sale price"), value = (dashboard?.stockValueAtSalePrice ?: 0.0).money(currencyCode)),
                            AnalyticsSummaryCardData(title = localizedStringResource(684, "Inventory value at supply cost"), value = (dashboard?.stockValueAtSupplyPrice ?: 0.0).money(currencyCode)),
                            AnalyticsSummaryCardData(title = localizedStringResource(685, "Low stock items"), value = (dashboard?.lowStockItemCount ?: 0).toString()),
                            AnalyticsSummaryCardData(title = localizedStringResource(686, "Out of stock items"), value = (dashboard?.outOfStockItemCount ?: 0).toString()),
                            AnalyticsSummaryCardData(title = localizedStringResource(687, "Expired batches"), value = (dashboard?.expiredBatchCount ?: 0).toString()),
                            AnalyticsSummaryCardData(title = localizedStringResource(688, "Expiring soon"), value = (dashboard?.expiringSoonBatchCount ?: 0).toString(), subtitle = localizedStringResource(699, "Inventory risk")),
                            AnalyticsSummaryCardData(title = localizedStringResource(689, "Sell-through estimate"), value = (dashboard?.sellThroughPercentEstimate ?: 0.0).percentText())
                        )
                    )
                }
            }

            if (section == "slow_moving") {
                if (dashboard?.slowMovingItems.isNullOrEmpty()) {
                    item(key = "MenuAnalyticsStockScreen:$section:2") { MessageText(Modifier.fillParentMaxSize(), stateValues.stringListEmpty) }
                }
                dashboard?.slowMovingItems?.takeIf { it.isNotEmpty() }?.let { items ->
                    item(key = "MenuAnalyticsStockScreen:$section:3") {
                        Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                        AnalyticsRankedItemsSection(
                            title = localizedStringResource(682, "Slow-moving inventory"),
                            items = items,
                            valueTitle = localizedStringResource(271, "Quantity"),
                            currencyCode = "",
                            valueSelector = { it.quantity },
                            subtitleSelector = { item ->
                                "${localizedStringResource(683, "Inventory value at sale price")}: ${item.amount.money(item.currencyCode.ifBlank { currencyCode })}"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.MenuAnalyticsSuppliersScreen(
    prepared: PreparedAnalytics,
    dashboard: StoreAnalyticsDashboardDataModel?,
    currencyCode: String,
    header: @Composable () -> Unit = {}
) {
    val supplierRows = prepared.suppliers.map { item ->
        if (item.name.isNotEmpty()) item else item.copy(name = listOf(LocalizedStringDataModel("main",
            if (item.id == "unknown") localizedStringResource(156, "Not specified") else item.id)))
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        var section by rememberNavigationSection("section:analytics:suppliers:${stateValues.activeStoreId.orEmpty()}", "overview")
        val tabs: @Composable () -> Unit = {
            sectionTabsWidget(
            selectedId = section, onSelected = { section = it },
            stateKey = "analytics:suppliers:${stateValues.activeStoreId.orEmpty()}",
            tabs = listOf(
                TabContent("overview", authUiText("Overview", "Обзор", "Шолу", "Жалпы көрүнүш")),
                TabContent("rankings", localizedStringResource(703, "Top performers"))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2),
        )
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Analytics, "suppliers_$section"),
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                .padding(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item(key = "analytics-filters") { header(); tabs() }
            item(key = "MenuAnalyticsSuppliersScreen:$section:0") {
                Text(
                    text = stateValues.stringSuppliers,
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = stateValues.marginTextField)
                )
            }

            if (section == "overview") {
                item(key = "MenuAnalyticsSuppliersScreen:$section:1") {
                    AnalyticsCardsGrid(
                        cards = listOf(
                            AnalyticsSummaryCardData(title = localizedStringResource(300, "Acceptance total"), value = (dashboard?.supplyCost ?: prepared.type("accept").total).money(currencyCode)),
                            AnalyticsSummaryCardData(title = stateValues.stringSuppliers, value = if (prepared.remoteOnly) "—" else supplierRows.size.toString()),
                            AnalyticsSummaryCardData(title = stateValues.stringItems, value = prepared.supplierQuantity.cleanNumber()),
                            AnalyticsSummaryCardData(title = localizedStringResource(358, "Transactions"), value = prepared.type("accept").count.toString())
                        )
                    )
                }
            }

            if (section == "rankings") {
                item(key = "MenuAnalyticsSuppliersScreen:$section:2") {
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    AnalyticsRankedItemsSection(
                        title = localizedStringResource(703, "Top performers"),
                        items = supplierRows,
                        valueTitle = localizedStringResource(300, "Acceptance total"),
                        currencyCode = currencyCode,
                        valueSelector = { it.amount },
                        subtitleSelector = { item -> "${localizedStringResource(271, "Quantity")}: ${item.quantity.cleanNumber()} · ${localizedStringResource(706, "Transactions")}: ${item.transactionCount}" }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.MenuAnalyticsWorkersScreen(
    workers: List<StoreWorkerDataModel>,
    prepared: PreparedAnalytics,
    currencyCode: String,
    header: @Composable () -> Unit = {}
) {
    val salesByWorkshift = prepared.workshifts.map { item -> item.copy(
        name = listOf(LocalizedStringDataModel("main", "${localizedStringResource(661, "Active workshift")} #${item.id}")),
        subtitle = "${localizedStringResource(706, "Transactions")}: ${item.transactionCount}"
    ) }

    Column(modifier = Modifier.fillMaxWidth()) {
        var section by rememberNavigationSection("section:analytics:workers:${stateValues.activeStoreId.orEmpty()}", "overview")
        val tabs: @Composable () -> Unit = {
            sectionTabsWidget(
            selectedId = section, onSelected = { section = it },
            stateKey = "analytics:workers:${stateValues.activeStoreId.orEmpty()}",
            tabs = listOf(
                TabContent("overview", authUiText("Overview", "Обзор", "Шолу", "Жалпы көрүнүш")),
                TabContent("performance", localizedStringResource(702, "Performance"))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2),
        )
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Analytics, "workers_$section"),
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                .padding(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item(key = "analytics-filters") { header(); tabs() }
            item(key = "MenuAnalyticsWorkersScreen:$section:0") {
                Text(
                    text = stateValues.stringWorkers,
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = stateValues.marginTextField)
                )
            }

            if (section == "overview") {
                item(key = "MenuAnalyticsWorkersScreen:$section:1") {
                    AnalyticsCardsGrid(
                        cards = listOf(
                            AnalyticsSummaryCardData(title = localizedStringResource(429, "Active workers"), value = workers.count { it.isActive }.toString(), subtitle = localizedStringResource(430, "Employees connected to this store")),
                            AnalyticsSummaryCardData(title = localizedStringResource(431, "Admins"), value = workers.count { it.roleId == WORKER_ROLE_ADMIN }.toString()),
                            AnalyticsSummaryCardData(title = localizedStringResource(432, "Standard workers"), value = workers.count { it.roleId == WORKER_ROLE_STANDARD }.toString()),
                            AnalyticsSummaryCardData(title = localizedStringResource(358, "Transactions"), value = prepared.type("purchase").count.toString()),
                            AnalyticsSummaryCardData(title = localizedStringResource(303, "Revenue / worker"), value = prepared.type("purchase").total.money(currencyCode), subtitle = localizedStringResource(302, "Use workshifts, sales per worker, and salary here"))
                        )
                    )
                }
            }

            if (section == "performance") {
                item(key = "MenuAnalyticsWorkersScreen:$section:2") {
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    AnalyticsRankedItemsSection(
                        title = localizedStringResource(702, "Performance"),
                        items = salesByWorkshift,
                        valueTitle = localizedStringResource(693, "Revenue"),
                        currencyCode = currencyCode,
                        valueSelector = { it.amount },
                        subtitleSelector = { it.subtitle }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.MenuAnalyticsCashRegisterScreen(
    currentAmount: Double,
    events: List<CashRegisterEventDataModel>,
    currencyCode: String,
    prepared: PreparedAnalytics,
    header: @Composable () -> Unit = {}
) {
    val saleCashTotal = prepared.saleCash
    val returnCashTotal = prepared.returnCash
    val extractedTotal = prepared.extractedCash

    var extractingCash by remember(stateValues.activeStoreId, stateValues.userAccount?.id) { mutableStateOf(false) }
    var extractionAmountText by rememberSaveable { mutableStateOf("") }
    var extractionNoteLocalized by remember { mutableStateOf(emptyLocalizedItemForCurrentLanguage()) }
    val canExtract = currentUserCanExtractCashRegister(stateValues.activeStoreId)
    val extractionAmount = extractionAmountText.toMoneyDouble()

    Column(modifier = Modifier.fillMaxWidth()) {
        var section by rememberNavigationSection("section:analytics:cash:${stateValues.activeStoreId.orEmpty()}", "overview")
        val tabs: @Composable () -> Unit = {
            sectionTabsWidget(
            selectedId = section, onSelected = { section = it },
            stateKey = "analytics:cash:${stateValues.activeStoreId.orEmpty()}",
            tabs = listOf(
                TabContent("overview", localizedStringResource(443, "Balance")),
                TabContent("extract", localizedStringResource(437, "Extract cash")),
                TabContent("history", localizedStringResource(257, "History"))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2),
        )
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Analytics, "cash_register_$section"),
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                .padding(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item(key = "analytics-filters") { header(); tabs() }
            item(key = "MenuAnalyticsCashRegisterScreen:$section:0") {
                Text(
                    text = localizedStringResource(256, "Cash registers"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = stateValues.marginTextField)
                )
            }

            if (section == "overview") {
                item(key = "MenuAnalyticsCashRegisterScreen:$section:1") {
                    AnalyticsCardsGrid(
                        cards = listOf(
                            AnalyticsSummaryCardData(
                                title = localizedStringResource(277, "Current amount"),
                                value = currentAmount.money(currencyCode),
                                subtitle = localizedStringResource(433, "Cash physically expected in the drawer")
                            ),
                            AnalyticsSummaryCardData(
                                title = localizedStringResource(434, "Cash from sales"),
                                value = saleCashTotal.money(currencyCode)
                            ),
                            AnalyticsSummaryCardData(
                                title = localizedStringResource(435, "Cash paid for returns"),
                                value = returnCashTotal.money(currencyCode)
                            ),
                            AnalyticsSummaryCardData(
                                title = localizedStringResource(278, "Extracted"),
                                value = extractedTotal.money(currencyCode)
                            ),
                            AnalyticsSummaryCardData(
                                title = localizedStringResource(436, "Cash events"),
                                value = events.size.toString()
                            )
                        )
                    )
                }
            }

            if (section == "extract") {
                item(key = "MenuAnalyticsCashRegisterScreen:$section:2") {
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                    Text(
                        text = localizedStringResource(437, "Extract cash"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                    if (!canExtract) {
                        MessageText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = stateValues.marginTextField),
                            text = localizedStringResource(438, "You do not have permission to extract cash from this register")
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                            horizontalAlignment = Alignment.Start
                        ) {
                            SimpleTextInput(
                                modifier = Modifier.fillMaxWidth(),
                                value = extractionAmountText,
                                placeholder = localizedStringResource(439, "Amount to extract"),
                                keyboardType = KeyboardType.Decimal,
                                leadingIconPath = stateValues.drawablePathIconFinances,
                                stateHost = NavigationScreenModel.Menu.Analytics,
                                stateKey = "menu_analytics_cash_register_extraction_amount",
                                onTransformValue = { value -> paymentInputNormalize(value) },
                                onValueChange = { extractionAmountText = paymentInputNormalize(it) }
                            )

                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !extractingCash && extractionAmount > 0.0 && extractionAmount <= currentAmount + 0.01,
                                loading = extractingCash,
                                autoLoading = false,
                                text = localizedStringResource(440, "Extract"),
                                iconPath = stateValues.drawablePathIconCheck,
                                confirmationRequired = true,
                                onClick = {
                                    if (extractingCash) return@actionButton
                                    stateValues.activeStoreId?.let { storeId ->
                                        val submittedAmountText = extractionAmountText
                                        val submittedNote = extractionNoteLocalized
                                        extractingCash = true
                                        extractCashRegister(
                                            CashRegisterExtractionRequestDataModel(
                                                storeId = storeId,
                                                amount = extractionAmount,
                                                note = extractionNoteLocalized.toStoredLocalizedNoteOrNull(),
                                                timeMillis = getCurrentTimeMillis()
                                            )
                                        ) { result ->
                                            extractingCash = false
                                            if (result is DataState.Success && stateValues.activeStoreId == storeId &&
                                                extractionAmountText == submittedAmountText && extractionNoteLocalized == submittedNote) {
                                                extractionAmountText = ""
                                                extractionNoteLocalized = emptyLocalizedItemForCurrentLanguage()
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(stateValues.marginTextField))

                        StockLocalizedStringGroupEditor(
                            title = localizedStringResource(441, "Extraction note"),
                            placeholder = stateValues.stringOptional,
                            values = extractionNoteLocalized,
                            addText = localizedStringResource(1073, "Add cash extraction note translation"),
                            required = false,
                            singleLine = false,
                            adaptiveMultiline = true,
                            persistentKey = "cash-extraction-note:${stateValues.activeStoreId.orEmpty()}",
                            onChanged = { extractionNoteLocalized = it }
                        )
                    }
                }
            }

            if (section == "history") {
                item(key = "MenuAnalyticsCashRegisterScreen:$section:3") {
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                    Text(
                        text = localizedStringResource(257, "History"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = stateValues.marginTextField)
                    )
                }

                if (events.isEmpty()) {
                    item(key = "MenuAnalyticsCashRegisterScreen:$section:4") {
                        MessageText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = stateValues.marginTextFieldGroup),
                            localizedStringResource(442, "No cash register events in this period")
                        )
                    }
                } else {
                    items(events, key = { it.id }) { event ->
                        CashRegisterEventCard(event, currencyCode)
                        Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.CashRegisterEventCard(
    event: CashRegisterEventDataModel,
    currencyCode: String
) {
    val eventColor = when (event.type) {
        CASH_REGISTER_EVENT_SALE_CASH_IN -> stateValues.OkayColor
        CASH_REGISTER_EVENT_RETURN_CASH_OUT -> stateValues.ErrorColor
        CASH_REGISTER_EVENT_EXTRACTION -> stateValues.AccentColor
        else -> stateValues.TextColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(stateValues.unfocusedBorderWidth, eventColor, RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = cashRegisterEventTitle(event.type),
                color = eventColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = event.amount.money(currencyCode),
                color = eventColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = receiptUiDateTime(event.timeMillis),
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize
        )

        Text(
            text = "${localizedStringResource(443, "Balance")}: ${event.balanceBefore.money(currencyCode)} → ${event.balanceAfter.money(currencyCode)}",
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize
        )

        if (event.userName.isNotBlank()) {
            Text(
                text = "${stateValues.stringCashier}: ${event.userName}",
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        event.transactionId?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "${stateValues.stringTransactionId}: ${it.take(8).uppercase()}",
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        event.note.visibleStoredLocalizedNote(stateValues.appLanguage)?.let {
            Text(
                text = "${localizedStringResource(201, "Notes")}: $it",
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}

internal fun AppConfiguration.cashRegisterEventTitle(type: String): String {
    return when (type) {
        CASH_REGISTER_EVENT_SALE_CASH_IN -> localizedStringResource(444, "Sale cash received")
        CASH_REGISTER_EVENT_RETURN_CASH_OUT -> localizedStringResource(445, "Return cash paid out")
        CASH_REGISTER_EVENT_EXTRACTION -> localizedStringResource(446, "Cash extraction")
        else -> localizedStringResource(447, "Cash register adjustment")
    }
}

@Composable
internal fun AppConfiguration.MenuAnalyticsSimpleScreen(
    title: String,
    cards: List<AnalyticsSummaryCardData>
) {
    LazyColumn(
        state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Analytics, title),
        modifier = Modifier
//      .weight(1f) h1
            .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
            .padding(stateValues.marginTextField)
    ) {
        item {
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = stateValues.marginTextField)
            )
        }

        item {
            AnalyticsCardsGrid(cards)
        }

        item {
            Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
        }
    }
}

@Composable
internal fun AppConfiguration.AnalyticsPeriodSelector(
    selectedPeriodId: String,
    onSelected: (String) -> Unit
) {
    val presets = listOf(
        AnalyticsPeriodPreset.Today to localizedStringResource(260, "Today"),
        AnalyticsPeriodPreset.Week to localizedStringResource(261, "7 days"),
        AnalyticsPeriodPreset.Month to localizedStringResource(262, "30 days"),
        AnalyticsPeriodPreset.All to stateValues.stringAll
    )

    tabRowWidget(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = stateValues.marginTextField),
        tabs = presets.map { (preset, title) ->
            TabContent(preset.id, title, icon = AitaTabIcon.Calendar) { selectedPresetId ->
                onSelected(selectedPresetId)
            }
        },
        selectedIndexInitial = selectedPeriodId,
        textSize = stateValues.smallTextSize
    )

    Spacer(modifier = Modifier.height(stateValues.marginTextField))
}

@Composable
internal fun AppConfiguration.AnalyticsPill(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(
                width = stateValues.unfocusedBorderWidth,
                color = if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                shape = RoundedCornerShape(stateValues.cornerRadius)
            )
            .background(
                if (selected) stateValues.AccentColor else stateValues.BackgroundColor
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    color = if (selected) stateValues.AccentTextColor else stateValues.TextColor
                ),
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppConfiguration.AnalyticsCardsGrid(
    cards: List<AnalyticsSummaryCardData>
) {
    if (stateValues.isNarrowScreen) {
        Column(
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            cards.forEach { card ->
                AnalyticsSummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                    card = card
                )
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            cards.chunked(3).forEach { rowCards ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    rowCards.forEach { card ->
                        AnalyticsSummaryCard(
                            modifier = Modifier.weight(1f),
                            card = card
                        )
                    }

                    repeat(3 - rowCards.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.AnalyticsSummaryCard(
    modifier: Modifier = Modifier,
    card: AnalyticsSummaryCardData
) {
    Column(
        modifier = modifier
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(
                width = stateValues.unfocusedBorderWidth,
                color = stateValues.TextColor,
                shape = RoundedCornerShape(stateValues.cornerRadius)
            )
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Text(
            text = card.title,
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = card.value,
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold
        )

        card.subtitle?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = it,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}


internal fun Double.percentText(): String = "${cleanNumber()}%"

@Composable
internal fun AppConfiguration.AnalyticsRankedItemsSection(
    title: String,
    items: List<AnalyticsRankedItemDataModel>,
    valueTitle: String,
    currencyCode: String,
    valueSelector: (AnalyticsRankedItemDataModel) -> Double,
    subtitleSelector: (AnalyticsRankedItemDataModel) -> String = { it.subtitle }
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Text(
            text = title,
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold
        )

        if (items.isEmpty()) {
            MessageText(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(704, "No analytics data yet")
            )
        } else {
            items.take(10).forEachIndexed { index, item ->
                val itemName = item.name.visibleLocalizedString(stateValues.appLanguage, item.id.ifBlank { "#${index + 1}" })
                val value = valueSelector(item)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .padding(stateValues.marginTextFieldGroup),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = stateValues.AccentColor,
                        fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = itemName,
                            color = stateValues.TextColor,
                            fontSize = stateValues.textSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        subtitleSelector(item).takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = valueTitle,
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (currencyCode.isBlank()) value.cleanNumber() else value.money(currencyCode),
                            color = stateValues.TextColor,
                            fontSize = stateValues.textSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.AnalyticsBucketSection(
    title: String,
    buckets: List<AnalyticsBucketDataModel>,
    currencyCode: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Text(
            text = title,
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold
        )

        if (buckets.isEmpty()) {
            MessageText(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(704, "No analytics data yet")
            )
        } else {
            buckets.forEach { bucket ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .padding(stateValues.marginTextFieldGroup),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bucket.label,
                            color = stateValues.TextColor,
                            fontSize = stateValues.textSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${localizedStringResource(706, "Transactions")}: ${bucket.transactionCount} · ${stateValues.stringItems}: ${bucket.quantity.cleanNumber()}",
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                    }

                    Text(
                        text = bucket.amount.money(currencyCode),
                        color = stateValues.TextColor,
                        fontSize = stateValues.textSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.AnalyticsHistoryRowWidget(
    row: AnalyticsHistoryRow,
    currencyCode: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(
                width = stateValues.unfocusedBorderWidth,
                color = stateValues.AccentColor,
                shape = RoundedCornerShape(stateValues.cornerRadius)
            )
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextFieldGroup),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = row.title,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${row.count} " + localizedStringResource(706, "Transactions"),
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        Text(
            text = row.total.money(currencyCode),
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

internal fun AnalyticsPeriodPreset.toAnalyticsPeriod(): AnalyticsPeriod {
    val now = kotlin.time.Clock.System.now()
    val nowMillis = now.toEpochMilliseconds()

    return when (this) {
        AnalyticsPeriodPreset.Today -> {
            val start = todayStartMillis()
            AnalyticsPeriod(
                startMillis = start,
                endMillisExclusive = nowMillis + 1
            )
        }

        AnalyticsPeriodPreset.Week -> {
            AnalyticsPeriod(
                startMillis = nowMillis - 7L * 24L * 60L * 60L * 1000L,
                endMillisExclusive = nowMillis + 1
            )
        }

        AnalyticsPeriodPreset.Month -> {
            AnalyticsPeriod(
                startMillis = nowMillis - 30L * 24L * 60L * 60L * 1000L,
                endMillisExclusive = nowMillis + 1
            )
        }

        AnalyticsPeriodPreset.All -> {
            AnalyticsPeriod(
                startMillis = 0L,
                endMillisExclusive = Long.MAX_VALUE
            )
        }
    }
}
internal fun todayStartMillis(): Long {
    val timeZone = TimeZone.currentSystemDefault()
    val today = kotlin.time.Clock.System.now()
        .toLocalDateTime(timeZone)
        .date

    return today
        .atStartOfDayIn(timeZone)
        .toEpochMilliseconds()
}

internal fun List<TransactionDataModel>.toMonthlyAnalyticsHistoryRows(): List<AnalyticsHistoryRow> {
    return groupBy { it.timeMillis.monthLabel() }
        .map { (month, transactions) ->
            AnalyticsHistoryRow(
                title = month,
                count = transactions.size,
                total = transactions.sumOf { it.paidCash + it.paidCard }
            )
        }
}

internal fun Long.monthLabel(): String {
    val date = Instant
        .fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())

    val month = date.monthNumber.toString().padStart(2, '0')
    val year = date.year.toString()

    return "$month.$year"
}

internal fun Double.money(currencyCode: String): String {
    return if (currencyCode.isBlank()) {
        fixed2()
    } else {
        "${fixed2()} $currencyCode"
    }
}

internal fun Double.aitaMoney(currencyCode: String): String {
    val nationalCurrencyCode = currencyCode.ifBlank { "KZT" }
    return "${fixed2()} AITA $nationalCurrencyCode"
}

internal fun Double.fixed2(): String {
    val negative = this < 0
    val scaled = round(abs(this) * 100.0).toLong()

    val whole = scaled / 100
    val cents = (scaled % 100).toString().padStart(2, '0')

    return "${if (negative) "-" else ""}$whole.$cents"
}

internal fun Double.cleanNumber(): String {
    return if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        fixed2()
    }
}

internal fun AppConfiguration.currentAnalyticsCurrencyCode(): String {
    val countryLocale = stateValues.userAccount?.countryLocale

    return stateValues.globalAppConfiguration
        .countries
        .find { it.locale.equals(countryLocale, ignoreCase = true) }
        ?.currencies
        ?.firstOrNull()
        ?.code
        ?: ""
}

//@Composable
//fun AppConfiguration.MenuAnalyticsScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    ScreenAppBarWidget(
//      title = stateValues.stringAnalytics,
//      iconPath = stateValues.drawablePathIconAnalytics,
//      onBack = {
//        coroutineScope.launch {
//          Navigation.Menu.pop(stateValues.isNarrowScreen)
//        }
//      }
//    )
//  }
//}

@Composable
fun AppConfiguration.MenuAddEditWorkerScreen() {
    AitaScreenColumn(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = stateValues.stringWorkers,
                iconPath = stateValues.drawablePathIconWorkers,
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.AddEditWorker),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.6f)
                .padding(horizontal = stateValues.marginTextField, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextFieldGroup)
        ) {
            item {
                MessageText(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(472, "My work"),
                    subText = localizedStringResource(475, "Request employment in a store")
                )
            }

            item {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stateValues.stringWorkers,
                    iconPath = stateValues.drawablePathIconWorkers,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            Navigation.Menu.go(NavigationScreenModel.Menu.Workers, stateValues.isNarrowScreen)
                        }
                    }
                )
            }

            item {
                Text(
                    text = localizedStringResource(477, "Send request") + " • " + localizedStringResource(467, "Allowed actions"),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(modifier = Modifier.height(stateValues.screenHeight / 5)) }
        }
    }
}

@Composable
fun AppConfiguration.MenuAddEditSupplierScreen() {
    val supplierState by NavigationScreenModel.Menu.AddEditSupplier.state.collectAsState()
    val suppliers by suppliersState.payload.collectAsState()
    val editedSupplierId = supplierState["edited_supplier_id"].orEmpty()
    val editorSessionKey = remember(editedSupplierId) { getCurrentTimeMillis().toString() }
    val editorPhoneStateKey = supplierProfileEditorPhoneStateKey(
        editedSupplierId.takeIf { it.isNotBlank() },
        editorSessionKey
    )
    val editedSupplier = editedSupplierId.takeIf { it.isNotBlank() }?.let { id ->
        suppliers.orEmpty().firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    LaunchedEffect(editedSupplierId) {
        if (suppliers == null) getSuppliers()
    }

    LaunchedEffect(editedSupplierId, suppliers) {
        if (editedSupplierId.isNotBlank() && suppliers != null && editedSupplier == null) {
            postInAppNotification(
                localizedStringResource(2513, "That supplier profile is no longer available."),
                NotificationType.Neutral,
                transient = true
            )
            NavigationScreenModel.Menu.AddEditSupplier.removeState(editorPhoneStateKey)
            Navigation.Menu.pop(stateValues.isNarrowScreen)
        }
    }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = if (editedSupplierId.isBlank()) stateValues.stringAddSupplier else stateValues.stringEditSupplier,
                iconPath = stateValues.drawablePathIconSuppliers,
                onBack = {
                    coroutineScope.launch {
                        NavigationScreenModel.Menu.AddEditSupplier.removeState(editorPhoneStateKey)
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                    }
                }
            )
        }
    ) {
        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.AddEditSupplier),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.62f)
                .padding(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            if (editedSupplierId.isNotBlank() && suppliers == null) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(2515, "Loading supplier profile…")
                    )
                }
            } else if (editedSupplierId.isBlank() || editedSupplier != null) {
                item {
                    SupplierProfileEditorContent(
                        editedSupplier = editedSupplier,
                        stateHost = NavigationScreenModel.Menu.AddEditSupplier,
                        editorSessionKey = editorSessionKey,
                        onCancel = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } },
                        onSaved = {
                            coroutineScope.launch {
                                NavigationScreenModel.Menu.AddEditSupplier.setState("edited_supplier_id" to "")
                                Navigation.Menu.pop(stateValues.isNarrowScreen)
                            }
                        }
                    )
                }
            }
        }
    }
}

internal fun localizedStoreTextItems(values: List<LocalizedStringDataModel>): List<DomainSelectionTextFieldGroupItemContent> {
    return values
        .takeIf { it.isNotEmpty() }
        ?.map {
            DomainSelectionTextFieldGroupItemContent(
                value = TextFieldValue(it.value),
                selectedDomainId = "",
                selectedSecondaryDomainId = it.language,
                isContentValid = true
            )
        }
        ?: listOf(
            DomainSelectionTextFieldGroupItemContent(
                value = TextFieldValue(""),
                selectedDomainId = "",
                selectedSecondaryDomainId = "main",
                isContentValid = true
            )
        )
}

@Composable
internal fun AppConfiguration.storeLanguageDomains(): List<SelectableDomain> = mutableListOf<SelectableDomain>().apply {
    add(
        SelectableDomain(
            id = "main",
            displayId = localizedStringResource(671, "Default"),
            name = localizedStringResource(671, "Default"),
            iconPath = null,
            iconRes = null
        )
    )

    stateValues.globalAppConfiguration.languages.withBundledAppLanguages().forEach { language ->
        add(
            SelectableDomain(
                id = language.language,
                displayId = language.name,
                name = language.name,
                iconPath = language.flagDrawablePath,
                iconRes = language.mapIconRes()
            )
        )
    }
}

@Composable
fun AppConfiguration.MenuAddEditStoreScreen() {
    val editedStoreId = NavigationScreenModel.Menu.AddEditStore.state.value[NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID]
    val parentStoreIdFromState = NavigationScreenModel.Menu.AddEditStore.state.value[NavigationScreenModel.Menu.AddEditStore.KEY_STATE_PARENT_STORE_ID]
    val editedStore = stateValues.stores.findStoreOrBranchForUi(editedStoreId)
    val parentStore = stateValues.stores.findStoreOrBranchForUi(parentStoreIdFromState ?: editedStore?.parentStoreId)
    val isBranchEditor = parentStore != null || editedStore?.isBranchStore() == true

    LaunchedEffect(editedStore?.id, parentStore?.id) {
        listOfNotNull(editedStore?.id, parentStore?.id)
            .takeIf { it.isNotEmpty() }
            ?.let { refreshStoreAddressLocalizations(storeIds = it) }
    }

    fun clearStoreEditorState() {
        coroutineScope.launch {
            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID)
            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_PARENT_STORE_ID)
            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_ALIAS)
            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_DESCRIPTION)
            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_ADDRESS)
            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_LEGAL_ID)
            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.KEY_STATE_NAME)
            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.KEY_STATE_PHONE_NUMBER)
            NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.KEY_STATE_EMAIL)
        }
    }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = when {
                    editedStore != null && isBranchEditor -> localizedStringResource(534, "Edit branch")
                    editedStore != null -> stateValues.stringEditStore
                    isBranchEditor -> localizedStringResource(533, "Add branch")
                    else -> stateValues.stringAddStore
                },
                iconPath = stateValues.drawablePathIconAdd,
                onBack = {
                    coroutineScope.launch {
                        Navigation.Menu.pop(stateValues.isNarrowScreen)
                        clearStoreEditorState()
                    }
                }
            )
        }
    ) {
        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.AddEditStore),
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.6f)
                .weight(1f)
                .padding(start = 8.dp, top = 24.dp, end = 8.dp),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 4)
        ) {
            item {
                val outerSpace = 16.dp
                val languageDomains = storeLanguageDomains()

                if (isBranchEditor && parentStore != null) {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(535, "Parent store"),
                        subText = parentStore.name.extractLocalizedString(stateValues.appLanguage).orEmpty()
                    )

                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                }

                val branchTypeContent = if (isBranchEditor) {
                    dropdownListWidget(
                        titleText = eventMessage("store.branch_type").visibleLocalizedString(stateValues.appLanguage, ""),
                        domains = StoreBranchType.entries.map { type ->
                            val title = eventMessage(if (type == StoreBranchType.INTERNET) "store.branch.internet" else "store.branch.physical")
                                .visibleLocalizedString(stateValues.appLanguage, "")
                            SelectableDomain(id = type.name, displayId = listOf(LocalizedStringDataModel("main", title)),
                                name = listOf(LocalizedStringDataModel("main", title)), iconPath = null, iconRes = null)
                        },
                        selectedInitial = editedStore?.effectiveBranchType()?.name ?: StoreBranchType.PHYSICAL.name
                    )
                } else null
                Text(
                    text = eventMessage(if (isBranchEditor) "store.branch_help" else "store.management_help")
                        .visibleLocalizedString(stateValues.appLanguage, ""),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize,
                    modifier = Modifier.fillMaxWidth().padding(bottom = stateValues.marginTextFieldGroup)
                )

                val nameData = domainSelectionTextFieldGroupWidget(
                    titleText = if (isBranchEditor) localizedStringResource(531, "Branch name") else stateValues.stringName,
                    placeholderText = if (isBranchEditor) localizedStringResource(536, "Enter branch name") else stateValues.stringEnterName,
                    stateHost = NavigationScreenModel.Menu.AddEditStore,
                    stateKey = NavigationScreenModel.KEY_STATE_NAME,
                    valueInitial = localizedStoreTextItems(editedStore?.name.orEmpty()),
                    domains = emptyList(),
                    secondaryDomains = languageDomains,
                    secondaryDomainsShowName = false,
                    addDomainActionButtonText = "",
                    addSecondaryDomainActionButtonText = if (isBranchEditor) localizedStringResource(537, "Add branch name translation") else stateValues.stringAddName,
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                val aliasData = domainSelectionTextFieldGroupWidget(
                    titleText = stateValues.stringAlias,
                    placeholderText = stateValues.stringEnterAlias,
                    stateHost = NavigationScreenModel.Menu.AddEditStore,
                    stateKey = NavigationScreenModel.Menu.AddEditStore.KEY_STATE_ALIAS,
                    valueInitial = localizedStoreTextItems(editedStore?.alias.orEmpty()),
                    domains = emptyList(),
                    secondaryDomains = languageDomains,
                    secondaryDomainsShowName = false,
                    addDomainActionButtonText = "",
                    addSecondaryDomainActionButtonText = stateValues.stringAddTranslation,
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                val descriptionData = domainSelectionTextFieldGroupWidget(
                    titleText = stateValues.stringDescription,
                    placeholderText = stateValues.stringEnterDescription,
                    stateHost = NavigationScreenModel.Menu.AddEditStore,
                    stateKey = NavigationScreenModel.Menu.AddEditStore.KEY_STATE_DESCRIPTION,
                    valueInitial = localizedStoreTextItems(editedStore?.description.orEmpty()),
                    domains = emptyList(),
                    secondaryDomains = languageDomains,
                    secondaryDomainsShowName = false,
                    addDomainActionButtonText = "",
                    addSecondaryDomainActionButtonText = stateValues.stringAddTranslation
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                val phoneNumberTextFieldContent = countrySelectionPhoneNumberTextField(
                    valueInitial = editedStore?.phoneNumbers?.takeIf { it.isNotEmpty() }?.first(),
                    stateHost = NavigationScreenModel.Menu.AddEditStore,
                    stateKey = NavigationScreenModel.KEY_STATE_PHONE_NUMBER
                )

                val phoneQuickFillOptions = listOfNotNull(
                    if (isBranchEditor) {
                        storePhoneQuickFillOption(
                            id = "parent_store_phone",
                            label = localizedStringResource(1304, "Parent store phone"),
                            rawPhoneNumber = parentStore?.phoneNumbers.orEmpty().firstOrNull(),
                            countries = stateValues.globalAppConfiguration.countries
                        )
                    } else null,
                    storePhoneQuickFillOption(
                        id = "my_phone",
                        label = localizedStringResource(1305, "My phone"),
                        rawPhoneNumber = stateValues.userAccount?.phoneNumber,
                        countries = stateValues.globalAppConfiguration.countries
                    )
                )

                StorePhoneQuickFillButtons(phoneNumberTextFieldContent, phoneQuickFillOptions)

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                val emailTextFieldContent = emailTextField(
                    valueInitial = editedStore?.emails?.takeIf { it.isNotEmpty() }?.first(),
                    stateHost = NavigationScreenModel.Menu.AddEditStore,
                    stateKey = NavigationScreenModel.KEY_STATE_EMAIL
                )

                val emailQuickFillButtons = listOfNotNull(
                    if (isBranchEditor) {
                        parentStore?.emails.orEmpty().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { email ->
                            StoreContactQuickFillButton(
                                id = "parent_store_email",
                                text = "${localizedStringResource(1306, "Parent store email")}: $email",
                                value = email
                            )
                        }
                    } else null,
                    stateValues.userAccount?.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let { email ->
                        StoreContactQuickFillButton(
                            id = "my_email",
                            text = "${localizedStringResource(1307, "My email")}: $email",
                            value = email
                        )
                    }
                )

                StoreEmailQuickFillButtons(emailTextFieldContent, emailQuickFillButtons)
                val storeEmailConfirmation = rememberContactEmailConfirmation(
                    kz.aita.auth.AitaContactPurpose.STORE_CONTACT, editedStore?.id.orEmpty(),
                    listOf(emailTextFieldContent.value.text), editedStore?.emails.orEmpty(),
                    parentId = if (editedStore == null && isBranchEditor) parentStore?.id.orEmpty() else "")
                var storeSaveInProgress by remember(editedStore?.id, parentStore?.id) { mutableStateOf(false) }
                ContactEmailConfirmationContent(storeEmailConfirmation, enabled = !storeSaveInProgress)

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                val selectedCountry = stateValues.globalAppConfiguration.countries.withSupportedCountries().run {
                    countryByPhoneSelection(phoneNumberTextFieldContent.selectedSecondaryId) ?: first()
                }

                val addressPickerContent = storeVerifiedAddressPicker(
                    initialLocation = editedStore?.location,
                    initialAddress = editedStore?.displayAddress(stateValues.appLanguage).orEmpty(),
                    countryCode = selectedCountry.locale
                )
                val addressTextFieldContent = addressPickerContent.textField
                val addressSelectionRequiredText = localizedStringResource(
                    2310,
                    "Choose one of the verified address suggestions before saving"
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                val countryForms = stateValues.globalAppConfiguration.companyFormsForCountry(selectedCountry.locale)
                val companyFormDropdownListContent = if (!isBranchEditor) key(selectedCountry.locale) {
                    dropdownListWidget(
                        titleText = stateValues.stringCompanyForm,
                        domains = countryForms.map {
                            SelectableDomain(
                                id = it.id,
                                displayId = it.name,
                                name = it.name,
                                iconPath = null,
                                iconRes = null
                            )
                        },
                        showName = false,
                        selectedInitial = editedStore?.companyForms?.firstOrNull()?.id?.takeIf { id -> countryForms.any { it.id == id } } ?: countryForms.firstOrNull()?.id
                    )
                } else null

                if (!isBranchEditor) {
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                    val legalFormat = stateValues.globalAppConfiguration.storeLegalIdFormat(selectedCountry.locale, companyFormDropdownListContent?.selectedId)
                        ?: stateValues.globalAppConfiguration.legalIdFormatForCountry(selectedCountry.locale)
                    val legalName = legalFormat.name.extractLocalizedString(stateValues.appLanguage) ?: localizedStringResource(523, "Legal ID")
                    val legalPlaceholder = legalFormat.placeholder.extractLocalizedString(stateValues.appLanguage) ?: localizedStringResource(524, "Enter legal ID")

                    val legalIdTextFieldContent = genericTextField(
                        titleText = "${localizedStringResource(523, "Legal ID")} • $legalName",
                        placeholderText = legalPlaceholder,
                        valueInitial = editedStore?.legalId,
                        stateHost = NavigationScreenModel.Menu.AddEditStore,
                        stateKey = NavigationScreenModel.Menu.AddEditStore.KEY_STATE_LEGAL_ID,
                        keyboardType = KeyboardType.Number,
                        leadingIconPath = stateValues.drawablePathIconStores,
                        contentInvalidText = localizedStringResource(525, "Legal ID format is invalid"),
                        onTransformValue = { raw ->
                            raw
                                .let { if (legalFormat.digitsOnly) it.filter { char -> char.isDigit() } else it.trim() }
                                .let { value -> legalFormat.maxLength?.let { value.take(it) } ?: legalFormat.length?.let { value.take(it) } ?: value }
                        },
                        onContentValidityCheck = { it.matchesLegalIdFormat(legalFormat) }
                    )

                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                    Text(
                        text = localizedStringResource(526, "Legal ID is selected by the store country"),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(outerSpace))

                    fun buildStoreModel(location: LocationDataModel): StoreDataModel {
                        val address = location.displayAddress(stateValues.appLanguage)

                        val companyForm = countryForms.find { it.id == companyFormDropdownListContent?.selectedId }

                        return StoreDataModel(
                            id = editedStore?.id.orEmpty(),
                            publicId = editedStore?.publicId.orEmpty(),
                            parentStoreId = null,
                            userIds = editedStore?.userIds ?: emptyList(),
                            storeTypeIds = editedStore?.storeTypeIds ?: emptyList(),
                            name = nameData.data.map { LocalizedStringDataModel(it.selectedSecondaryDomainId, it.value.text.trim()) }
                                .filter { it.language.isNotBlank() && it.value.isNotBlank() }
                                .distinctBy { it.language },
                            alias = aliasData.data.map { LocalizedStringDataModel(it.selectedSecondaryDomainId, it.value.text.trim()) }
                                .filter { it.language.isNotBlank() && it.value.isNotBlank() }
                                .distinctBy { it.language },
                            description = descriptionData.data.map { LocalizedStringDataModel(it.selectedSecondaryDomainId, it.value.text.trim()) }
                                .filter { it.language.isNotBlank() && it.value.isNotBlank() }
                                .distinctBy { it.language },
                            companyForms = listOfNotNull(companyForm),
                            location = location,
                            address = address,
                            legalIdTypeId = legalFormat.id,
                            legalId = legalIdTextFieldContent.value.text.trim(),
                            phoneNumbers = listOf(selectedCountry.phoneNumberCode.lowercase() + phoneNumberTextFieldContent.value.text.trim().lowercase()),
                            emails = listOf(emailTextFieldContent.value.text.trim().lowercase()),
                            contactVerificationId = storeEmailConfirmation.draftId,
                            contactEmailProofs = storeEmailConfirmation.proofs,
                            countryLocales = listOf(selectedCountry.locale),
                            createdAt = editedStore?.createdAt ?: 0L,
                            branches = editedStore?.branches.orEmpty(),
                            architectureVersion = 2
                        )
                    }

                    StoreAddressInlineValidationMessage(addressPickerContent.state)

                    actionButton(
                        text = if (editedStore != null) stateValues.stringEditStore else stateValues.stringAddStore,
                        enabled = !storeSaveInProgress,
                        loading = storeSaveInProgress, autoLoading = false
                    ) {
                        softKeyboardController?.hide()
                        if (!storeEmailConfirmation.ready) {
                            storeEmailConfirmation.error = contactText("required_before_save")
                            postInAppNotification(storeEmailConfirmation.error, NotificationType.Negative, transient = true)
                            return@actionButton
                        }
                        val fullPhone = selectedCountry.phoneNumberCode + phoneNumberTextFieldContent.value.text.trim()
                        if (storeCountryFromPhones(listOf(fullPhone))?.locale != selectedCountry.locale) {
                            postInAppNotification(eventMessage("store.phone_country").visibleLocalizedString(stateValues.appLanguage, ""), NotificationType.Negative, transient = true)
                            phoneNumberTextFieldContent.checkContentValidity()
                            return@actionButton
                        }
                        addressTextFieldContent.checkContentValidity()
                        phoneNumberTextFieldContent.checkContentValidity()
                        emailTextFieldContent.checkContentValidity()
                        legalIdTextFieldContent.checkContentValidity()

                        val hasName = nameData.data.any { it.value.text.trim().isNotEmpty() }
                        if (!hasName) {
                            postInAppNotification(localizedStringResource(527, "Store name is required"), NotificationType.Negative)
                            return@actionButton
                        }

                        val verifiedLocation = addressPickerContent.locationForSave(stateValues.appLanguage)
                        if (verifiedLocation == null) {
                            addressPickerContent.state.requireSuggestionSelection(eventMessage("address.entry_required").extractLocalizedString(stateValues.appLanguage).orEmpty())
                            addressTextFieldContent.checkContentValidity()
                            return@actionButton
                        }
                        addressPickerContent.state.clearValidation()

                        if (!storeSaveInProgress && storeEmailConfirmation.ready && addressTextFieldContent.isContentValid && phoneNumberTextFieldContent.isContentValid && emailTextFieldContent.isContentValid && legalIdTextFieldContent.isContentValid) {
                            storeSaveInProgress = true
                            val body = buildStoreModel(verifiedLocation)
                            if (editedStore != null) {
                                updateStore(body) { result ->
                                    coroutineScope.launch {
                                        storeSaveInProgress = false
                                        if (result is DataState.Success) {
                                            ContactConfirmationMemory.forget(storeEmailConfirmation)
                                            Navigation.Menu.pop()
                                            clearStoreEditorState()
                                        }
                                    }
                                }
                            } else {
                                addStore(body) { result ->
                                    coroutineScope.launch {
                                        storeSaveInProgress = false
                                        if (result is DataState.Success) {
                                            ContactConfirmationMemory.forget(storeEmailConfirmation)
                                            Navigation.Menu.pop()
                                            clearStoreEditorState()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(outerSpace))

                    fun buildBranchModel(location: LocationDataModel): StoreDataModel {
                        val address = location.displayAddress(stateValues.appLanguage)

                        return StoreDataModel(
                            id = editedStore?.id.orEmpty(),
                            publicId = editedStore?.publicId.orEmpty(),
                            parentStoreId = parentStore?.id ?: editedStore?.parentStoreId,
                            userIds = editedStore?.userIds ?: parentStore?.userIds ?: emptyList(),
                            storeTypeIds = editedStore?.storeTypeIds ?: parentStore?.storeTypeIds ?: emptyList(),
                            name = nameData.data.map { LocalizedStringDataModel(it.selectedSecondaryDomainId, it.value.text.trim()) }
                                .filter { it.language.isNotBlank() && it.value.isNotBlank() }
                                .distinctBy { it.language },
                            alias = aliasData.data.map { LocalizedStringDataModel(it.selectedSecondaryDomainId, it.value.text.trim()) }
                                .filter { it.language.isNotBlank() && it.value.isNotBlank() }
                                .distinctBy { it.language },
                            description = descriptionData.data.map { LocalizedStringDataModel(it.selectedSecondaryDomainId, it.value.text.trim()) }
                                .filter { it.language.isNotBlank() && it.value.isNotBlank() }
                                .distinctBy { it.language },
                            companyForms = emptyList(),
                            location = location,
                            address = address,
                            legalIdTypeId = "",
                            legalId = "",
                            phoneNumbers = listOf(selectedCountry.phoneNumberCode.lowercase() + phoneNumberTextFieldContent.value.text.trim().lowercase()),
                            emails = listOf(emailTextFieldContent.value.text.trim().lowercase()),
                            contactVerificationId = storeEmailConfirmation.draftId,
                            contactEmailProofs = storeEmailConfirmation.proofs,
                            countryLocales = listOf(selectedCountry.locale),
                            createdAt = editedStore?.createdAt ?: 0L,
                            branches = emptyList(),
                            branchType = branchTypeContent?.selectedId?.let { StoreBranchType.valueOf(it) } ?: StoreBranchType.PHYSICAL,
                            architectureVersion = 2
                        )
                    }

                    StoreAddressInlineValidationMessage(addressPickerContent.state)

                    actionButton(
                        text = if (editedStore != null) localizedStringResource(534, "Edit branch") else localizedStringResource(533, "Add branch"),
                        enabled = !storeSaveInProgress,
                        loading = storeSaveInProgress, autoLoading = false
                    ) {
                        softKeyboardController?.hide()
                        if (!storeEmailConfirmation.ready) {
                            storeEmailConfirmation.error = contactText("required_before_save")
                            postInAppNotification(storeEmailConfirmation.error, NotificationType.Negative, transient = true)
                            return@actionButton
                        }
                        val fullPhone = selectedCountry.phoneNumberCode + phoneNumberTextFieldContent.value.text.trim()
                        if (storeCountryFromPhones(listOf(fullPhone))?.locale != selectedCountry.locale) {
                            postInAppNotification(eventMessage("store.phone_country").visibleLocalizedString(stateValues.appLanguage, ""), NotificationType.Negative, transient = true)
                            phoneNumberTextFieldContent.checkContentValidity()
                            return@actionButton
                        }
                        addressTextFieldContent.checkContentValidity()
                        phoneNumberTextFieldContent.checkContentValidity()
                        emailTextFieldContent.checkContentValidity()

                        val hasName = nameData.data.any { it.value.text.trim().isNotEmpty() }
                        if (!hasName) {
                            postInAppNotification(localizedStringResource(528, "Branch name is required"), NotificationType.Negative)
                            return@actionButton
                        }

                        val verifiedLocation = addressPickerContent.locationForSave(stateValues.appLanguage)
                        if (verifiedLocation == null) {
                            addressPickerContent.state.requireSuggestionSelection(eventMessage("address.entry_required").extractLocalizedString(stateValues.appLanguage).orEmpty())
                            addressTextFieldContent.checkContentValidity()
                            return@actionButton
                        }
                        addressPickerContent.state.clearValidation()

                        if (!storeSaveInProgress && storeEmailConfirmation.ready && addressTextFieldContent.isContentValid && phoneNumberTextFieldContent.isContentValid && emailTextFieldContent.isContentValid) {
                            storeSaveInProgress = true
                            val body = buildBranchModel(verifiedLocation)
                            if (editedStore != null) {
                                updateStore(body) { result ->
                                    coroutineScope.launch {
                                        storeSaveInProgress = false
                                        if (result is DataState.Success) {
                                            ContactConfirmationMemory.forget(storeEmailConfirmation)
                                            Navigation.Menu.pop()
                                            clearStoreEditorState()
                                        }
                                    }
                                }
                            } else {
                                addStore(body) { result ->
                                    coroutineScope.launch {
                                        storeSaveInProgress = false
                                        if (result is DataState.Success) {
                                            ContactConfirmationMemory.forget(storeEmailConfirmation)
                                            Navigation.Menu.pop()
                                            clearStoreEditorState()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isBranchEditor && editedStore != null) {
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                    Text(
                        text = localizedStringResource(532, "Branches"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(533, "Add branch"),
                        iconPath = stateValues.drawablePathIconAdd,
                        confirmationRequired = false,
                        onClick = {
                            coroutineScope.launch {
                                NavigationScreenModel.Menu.AddEditStore.removeState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID)
                                NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_PARENT_STORE_ID to editedStore.id)
                                Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore)
                            }
                        }
                    )

                    editedStore.branches.forEach { branch ->
                        Spacer(modifier = Modifier.height(stateValues.marginTextField))
                        StoreWidget(
                            store = branch,
                            onDelete = { deleteStore(it) },
                            onEdit = {
                                coroutineScope.launch {
                                    NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_EDITED_STORE_ID to branch.id)
                                    NavigationScreenModel.Menu.AddEditStore.setState(NavigationScreenModel.Menu.AddEditStore.KEY_STATE_PARENT_STORE_ID to editedStore.id)
                                    Navigation.Menu.go(NavigationScreenModel.Menu.AddEditStore)
                                }
                            },
                            onSetActive = if (branch.id != stateValues.activeStoreId) {
                                { setActiveStoreId(branch.id) }
                            } else null,
                            onSetInactive = if (branch.id == stateValues.activeStoreId) {
                                { setActiveStoreId(null) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MenuAddEditGoodsCategoryScreen() {
}

@Composable
internal fun AppConfiguration.LocalAppPreferencesPriorityEffect() {
    val userAccountId = stateValues.userAccount?.id
    val reachable = stateValues.cloudTransportStatus == CLOUD_TRANSPORT_STATUS_REACHABLE
    LaunchedEffect(reachable, userAccountId) {
        // Reconnect retries an account-owned pending write, never sets three old UI values.
        if (reachable && userAccountId != null) syncUserPreferencesToServer(postFailure = false)
    }
}

@Composable
internal fun AppConfiguration.CloudConnectionStatusBanner() {
    // Resume a sleeping retry on all Compose targets, including after visiting an authenticator.
    // This does not cancel a healthy socket or animate the connection banner.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        notifyCloudConnectionMayBeAvailable()
    }
    val userAccount = stateValues.userAccount
    val manualRefreshInProgress = stateValues.cloudConnectionManualRefreshInProgress
    val refreshIconRes by stateValues.drawableResIconRefresh.collectAsState()
    val transportStatus = stateValues.cloudTransportStatus
    val transportReachable = transportStatus == CLOUD_TRANSPORT_STATUS_REACHABLE
    val transportUnavailable = transportStatus == CLOUD_TRANSPORT_STATUS_UNAVAILABLE
    val localNetwork = stateValues.localNetworkState
    val localMode = userAccount != null && localNetwork.enabled &&
        !transportReachable && !transportUnavailable

    // cloudTransportStatus is already a presentation-grade, hysteresis-controlled state. Keep the
    // banner itself deliberately still: no crossfade, slide, size motion or color interpolation can
    // turn a legitimate status transition into visual flicker.
    val displayedStatusKey = when {
        transportUnavailable -> "unavailable"
        transportReachable -> "connected"
        localMode -> "local"
        else -> "checking"
    }
    val backgroundColor = when (displayedStatusKey) {
        "connected" -> stateValues.OkayColor
        "unavailable" -> stateValues.ErrorColor
        else -> stateValues.AccentColor
    }
    val refreshInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.width(28.dp))

        Text(
            modifier = Modifier.weight(1f),
            text = when (displayedStatusKey) {
                "connected" -> localizedStringResource(1138, "Server connected")
                "local" -> localizedStringResource(914, "Server is not connected. Branch local network mode is active.")
                "unavailable" -> storePeopleText("offline")
                else -> localizedStringResource(1139, "Checking server connection…")
            },
            color = stateValues.AccentTextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .aitaClickable(
                    enabled = !manualRefreshInProgress,
                    interactionSource = refreshInteractionSource,
                    indication = ripple(color = stateValues.AccentTextColor, radius = 14.dp),
                    onClick = { refreshCloudConnectionManually() }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = manualRefreshInProgress,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS)) +
                        scaleIn(initialScale = 0.72f, animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS)) +
                                scaleOut(targetScale = 0.82f, animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS))
                        )
                },
                label = "cloudConnectionRefreshState"
            ) { refreshing ->
                if (refreshing) {
                    AitaBusyIndicator(
                        modifier = Modifier.size(16.dp),
                        color = stateValues.AccentTextColor
                    )
                } else {
                    CpImage(
                        modifier = Modifier.size(18.dp),
                        url = stateValues.drawablePathIconRefresh,
                        fallbackRes = refreshIconRes,
                        contentDescription = localizedStringResource(237, "Refresh"),
                        tintColor = stateValues.AccentTextColor
                    )
                }
            }
        }
    }
}

internal fun String.normalizedNotificationPopupKey(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

internal fun String.isSessionRefreshPopupText(): Boolean {
    val normalized = normalizedNotificationPopupKey()
    if (normalized.isBlank()) return false
    return listOf(
        "cloud session needs refresh",
        "session needs refresh",
        "you remain signed in locally",
        "облачный сеанс",
        "сеанс нужно обновить",
        "остаётесь в аккаунте локально",
        "бұлттық сеанс",
        "сеансты жаңарту",
        "жергілікті түрде аккаунтта"
    ).any { marker -> normalized.contains(marker) }
}

internal fun String.isServerUnavailablePopupText(): Boolean {
    val normalized = normalizedNotificationPopupKey()
    if (normalized.isBlank()) return false
    return listOf(
        "can't reach aita server",
        "can’t reach aita server",
        "can’t reach server",
        "can't reach server",
        "cannot reach server",
        "cannot connect to server",
        "server unavailable",
        "server is unavailable",
        "server is offline",
        "server is not connected",
        "live updates disconnected",
        "connection unavailable",
        "using cached data",
        "while reconnecting",
        "reconnecting",
        "keeping you signed in offline",
        "security sessions will refresh",
        "server address opened another page",
        "server address did not answer as aita",
        "not the aita server",
        "does not look like aita",
        "tried http://",
        "tried https://",
        "client error:",
        "connect timeout",
        "connection timeout",
        "connect_timeout",
        "connecttimeoutexception",
        "connectexception",
        "sockettimeoutexception",
        "timeout has expired",
        "server request failed. please check the server connection",
        "network request failed",
        "connection refused",
        "connection reset",
        "connection aborted",
        "connection closed prematurely",
        "broken pipe",
        "unexpected end of stream",
        "eofexception",
        "failed to connect",
        "network unreachable",
        "host unreachable",
        "temporary failure in name resolution",
        "unable to resolve host",
        "no address associated with hostname",
        "unknownhostexception",
        "unresolvedaddress",
        "socketexception",
        "url=http://",
        "url=https://",
        "[url=",
        "io.ktor.client.network.sockets",
        "сервер aita недоступ",
        "сервер недоступ",
        "сервер офлайн",
        "сервер не подключ",
        "нет соединения",
        "нет ответа от сервера",
        "адрес сервера открыл другую страницу",
        "адрес сервера ответил не как aita",
        "пробовали http://",
        "пробовали https://",
        "таймаут подключения",
        "ошибка подключения",
        "запрос к серверу не выполнен",
        "проверьте соединение с сервером",
        "соединение сброшено",
        "остаётесь в аккаунте офлайн",
        "сеансы безопасности обновятся",
        "aita сервері қолжетімсіз",
        "сервер қолжетімсіз",
        "сервер офлайн",
        "сервер қосылмаған",
        "серверге сұрау орындалмады",
        "сервер байланысын тексеріп",
        "қосылым үзілді",
        "қосылу уақыты",
        "қосылым қатесі"
    ).any { marker -> normalized.contains(marker) }
}

internal fun String.isServerRecoveryPopupText(): Boolean {
    val normalized = normalizedNotificationPopupKey()
    if (normalized.isBlank()) return false
    return listOf(
        "server is back online",
        "server back online",
        "live updates connected",
        "cloud connection restored",
        "server connection restored",
        "server connection available",
        "server connected",
        "онлайн-обновления подключены",
        "соединение восстановлено",
        "связь с сервером восстановлена",
        "сервер доступен",
        "сервер подключ",
        "сервер снова онлайн",
        "нақты уақыттағы жаңартулар қосылды",
        "сервермен байланыс қалпына",
        "сервер қайта онлайн",
        "сервер қосылды",
        "сервер қолжетімді"
    ).any { marker -> normalized.contains(marker) }
}

internal fun String.notificationPopupSemanticKey(): String {
    val normalized = normalizedNotificationPopupKey()
    if (normalized.isBlank()) return ""
    return when {
        normalized.isServerUnavailablePopupText() -> "server-unavailable"
        normalized.isServerRecoveryPopupText() -> "server-recovered"
        normalized.isSessionRefreshPopupText() -> "session-refresh"
        else -> normalized
    }
}

internal fun NotificationDataModel.popupDeduplicationKey(): String {
    val combined = listOf(title, message, category, source).joinToString(" ")
    if (combined.isServerUnavailablePopupText() || combined.isServerRecoveryPopupText()) {
        return "connection|${combined.notificationPopupSemanticKey()}"
    }
    if (combined.isSessionRefreshPopupText()) {
        return "session|${combined.notificationPopupSemanticKey()}"
    }
    return listOf(
        type.toString(),
        title.notificationPopupSemanticKey(),
        message.notificationPopupSemanticKey(),
        category.notificationPopupSemanticKey(),
        source.normalizedNotificationPopupKey()
    ).joinToString("|")
}

internal fun NotificationDataModel.isConnectionStatusPopupNoise(): Boolean {
    val normalizedCategory = category.normalizedNotificationPopupKey()
    val normalizedSource = source.normalizedNotificationPopupKey()
    if (normalizedCategory == "connection" || normalizedSource == "connection") return true

    val combined = listOf(title, message, category, source)
        .joinToString(" ")
        .normalizedNotificationPopupKey()
    return combined.isServerUnavailablePopupText() || combined.isServerRecoveryPopupText()
}

internal fun List<NotificationDataModel>.compactForPopupDisplay(): List<NotificationDataModel> {
    val seen = mutableSetOf<String>()
    return filter { seen.add(it.popupDeduplicationKey()) }
}

@Composable
fun AppConfiguration.MainScreen() {
    val locallyHydrated by localApplicationHydratedState.collectAsState()
    val modeReady by accountAppModeReadyState.collectAsState()
    val navigationReady by AppStateWorkspace.readyScope.collectAsState()
    LaunchedEffect(Unit) { Navigation.startAppNavigationPersistence() }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        coroutineScope.launch { AppStateWorkspace.flush() }
    }
    // Keep the same splash until account mode, store and local navigation have been adopted.
    // A published account alone is not permission to render the default Marketplace workspace.
    if (!locallyHydrated || (stateValues.userAccount != null &&
        (modeReady != (stateValues.userAccount!!.id to currentAuthenticatedSessionGeneration()) ||
            navigationReady == null || !AppStateWorkspace.readyForCurrentScope()))) {
        SplashScreen()
        return
    }
    AppUpdateEffects()
    val subscriptionGate = rememberStoreWorkspaceGate()
    val subscriptionAccess = subscriptionGate == StoreSubscriptionGate.Active
    val accountForSubscription = stateValues.userAccount?.id
    val storeForSubscription = stateValues.activeStoreId
    val sessionForSubscription = currentAuthenticatedSessionGeneration()
    val buyerNavigation = remember(accountForSubscription, sessionForSubscription) {
        BuyerMarketNavigation(captureMarketRequestScope())
    }
    BuyerNavigationPersistence(buyerNavigation)
    var denialOpened by remember(accountForSubscription, storeForSubscription, sessionForSubscription) {
        mutableStateOf(false)
    }
    LaunchedEffect(accountForSubscription, storeForSubscription, sessionForSubscription,
        stateValues.appModeId, subscriptionGate, stateValues.navigationScreensMain.last().route) {
        Navigation.awaitAppNavigationRestore()
        if (stateValues.appModeId != APP_MODE_STORE || accountForSubscription.isNullOrBlank() ||
            storeForSubscription.isNullOrBlank() ||
            !authenticatedSessionGenerationIsCurrent(sessionForSubscription) ||
            stateValues.userAccount?.id != accountForSubscription || stateValues.activeStoreId != storeForSubscription) return@LaunchedEffect
        if (subscriptionAccess) {
            denialOpened = false
            if (currentStoreModel(storeForSubscription)?.isManagementStore() == true &&
                Navigation.Main.value.last() is NavigationScreenModel.Transaction) {
                Navigation.goMain(NavigationScreenModel.Stock.Main)
            }
        } else if (subscriptionGate == StoreSubscriptionGate.Required) {
            val route = Navigation.Main.value.last()
            if (route is NavigationScreenModel.UserAuth || route is NavigationScreenModel.Splash) return@LaunchedEffect
            // Do not steal focus back from Support, Account or renewal after the initial redirect.
            // A stale deep-link to business data is still redirected on every attempt.
            if (!denialOpened || route is NavigationScreenModel.Stock || route is NavigationScreenModel.Transaction) {
                denialOpened = true
                Navigation.showSubscriptionRecovery()
            }
        }
    }
    val showNavigationBar = stateValues.navigationScreensMain.last().run {
        this !is NavigationScreenModel.Splash && this !is NavigationScreenModel.UserAuth
    }

    // The grounded connection banner is the single visual source of truth for automatic
    // disconnect/recovery state. Keeping those transient status events out of popup cards avoids
    // duplicate flashes and notification fatigue while preserving actionable notifications.
    val activeNotifications = stateValues.activeNotifications
        .filterNot { it.isConnectionStatusPopupNoise() || it.isSubscriptionAccessNotice() }
        .compactForPopupDisplay()
    val visibleNotifications = activeNotifications.take(if (stateValues.isNarrowScreen) 1 else 5)
    val workshiftStartDialogVisible by workshiftStartDialogVisibleState.collectAsState()
    val activeWorkshiftForGate by activeWorkshiftState.payload.collectAsState()
    val myMembershipsForGate by myWorkerMembershipsState.payload.collectAsState()

    LaunchedEffect(stateValues.activeStoreId, stateValues.userAccount?.id, stateValues.appModeId, subscriptionAccess, myMembershipsForGate?.size, activeWorkshiftForGate?.id) {
        val storeId = stateValues.activeStoreId
        if (stateValues.userAccount != null && !storeId.isNullOrBlank()) {
            getMyWorkerMemberships()
            if (stateValues.appModeId == APP_MODE_STORE && subscriptionAccess) getCurrentWorkshift(storeId)
        }
    }

    LaunchedEffect(stateValues.activeStoreId) {
        if (stateValues.activeStoreId.isNullOrBlank()) {
            Navigation.resetStoreScopedNavigationForNoActiveStore()
        }
    }

    Column(
        modifier = Modifier
            .background(stateValues.BackgroundColor.softAppBackgroundColor())
            .windowInsetsPadding(WindowInsets.systemBars)
            .fillMaxSize()
    ) {
        LaunchedEffect(Unit) {
            Navigation.startAppNavigationPersistence()
            Navigation.awaitAppNavigationRestore()
            userAccountState.value.collect {
                if (it is DataState.Empty) {
                    val hasStoredTokens = withContext(Dispatchers.ourIo) { getStoredUserAuthTokens?.invoke() != null }
                    if (!hasStoredTokens) {
                        Navigation.goMain(NavigationScreenModel.UserAuth.Main)
                    }
                } else if ((it is DataState.Success) && Navigation.Main.value.last()
                        .run { this is NavigationScreenModel.UserAuth || this is NavigationScreenModel.Splash }
                ) Navigation.goMain(defaultMainScreenForAppMode(stateValues.appModeId))
            }
        }

        CloudConnectionStatusBanner()
        LocalAppPreferencesPriorityEffect()

        LaunchedEffect(stateValues.appModeId, stateValues.navigationScreensMain.last().route) {
            Navigation.awaitAppNavigationRestore()
            val current = stateValues.navigationScreensMain.last()
            if (!current.isMainScreenCompatibleWithAppMode(stateValues.appModeId)) {
                Navigation.goMain(defaultMainScreenForAppMode(stateValues.appModeId))
            }
        }

        if (stateValues.isNarrowScreen && visibleNotifications.isNotEmpty()) {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleNotifications.forEach { notification ->
                        NotificationPopupCard(
                            notification = notification,
                            compact = true,
                            onDismiss = { dismissInAppNotification(notification.id) },
                            onOpenHistory = {
                                if (notification.category == MISSED_NOTIFICATION_CATEGORY) {
                                    requestUnreadNotifications()
                                    dismissInAppNotification(notification.id, markAsRead = false)
                                } else markNotificationRead(notification.id)
                                coroutineScope.launch {
                                    Navigation.goMain(NavigationScreenModel.Menu.Main)
                                    Navigation.Menu.go(NavigationScreenModel.Menu.Notifications, stateValues.isNarrowScreen)
                                }
                            }
                        )
                    }
                }
            }
        }

        val bottomNavigationItems = when (stateValues.appModeId) {
            APP_MODE_STORE -> filteredMainBottomDestinations(subscriptionAccess)
            APP_MODE_SUPPLIER, APP_MODE_MANUFACTURER -> Navigation.bottomNavBarScreensSupplier
            else -> Navigation.bottomNavBarScreensBuyer
        }
        val mainDestination = stateValues.navigationScreensMain.last()
        val mainMotionTarget = AitaSceneMotionTarget(
            family = "main:${stateValues.appModeId}",
            selection = mainDestination.route,
            stack = listOf(mainDestination.route),
            selectionIndex = bottomNavigationItems.indexOfFirst { it.route == mainDestination.route }.takeIf { it >= 0 }
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()
        ) {
            Box(Modifier.fillMaxSize().aitaSceneMotion(mainMotionTarget)) {
                // Route-only identity: preferences and refreshed data never recreate this tree.
                // Do not keep an outgoing live transaction/auth/supplier owner for animation.
                key(mainDestination.route) {
                    if (stateValues.appModeId == APP_MODE_STORE && !subscriptionAccess &&
                        (mainDestination is NavigationScreenModel.Stock || mainDestination is NavigationScreenModel.Transaction)) {
                        SubscriptionRequiredPane()
                    } else if (stateValues.appModeId == APP_MODE_STORE && mainDestination is NavigationScreenModel.Transaction &&
                        !currentStoreSupportsTransactions(stateValues.activeStoreId)) {
                        StockScreen()
                    } else when (mainDestination) {
                        is NavigationScreenModel.Splash -> SplashScreen()
                        is NavigationScreenModel.UserAuth -> UserAuthScreen()
                        is NavigationScreenModel.Notifications -> NotificationsScreen()
                        is NavigationScreenModel.Transaction.MainSale, NavigationScreenModel.Transaction.MainReturn, NavigationScreenModel.Transaction.MainSupply -> TransactionScreen()
                        is NavigationScreenModel.Stock -> StockScreen()
                        is NavigationScreenModel.Supplier -> SupplierScreen()
                        NavigationScreenModel.Buyer.Main.Shopping -> BuyerShoppingListScreen(buyerNavigation)
                        is NavigationScreenModel.Buyer -> BuyerMarketplaceScreen(buyerNavigation)
                        is NavigationScreenModel.Menu -> MenuScreen()
                        else -> {}
                    }
                }
            }

            val blockingWorkshiftGate = shouldBlockAppForWorkshift() &&
                stateValues.navigationScreensMain.last() is NavigationScreenModel.Transaction
            if (subscriptionAccess && stateValues.appModeId == APP_MODE_STORE && (blockingWorkshiftGate || workshiftStartDialogVisible)) {
                WorkshiftGateOverlay(blockingTransactionScreen = blockingWorkshiftGate)
            }

            if (!stateValues.isNarrowScreen && visibleNotifications.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .widthIn(min = 320.dp, max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    visibleNotifications.forEachIndexed { index, notification ->
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInHorizontally(initialOffsetX = { it + 80 }) + fadeIn(tween(180)),
                            exit = slideOutHorizontally(targetOffsetX = { it + 80 }) + fadeOut(tween(180))
                        ) {
                            NotificationPopupCard(
                                notification = notification,
                                compact = false,
                                onDismiss = { dismissInAppNotification(notification.id) },
                                onOpenHistory = {
                                    if (notification.category == MISSED_NOTIFICATION_CATEGORY) {
                                        requestUnreadNotifications()
                                        dismissInAppNotification(notification.id, markAsRead = false)
                                    } else markNotificationRead(notification.id)
                                    coroutineScope.launch {
                                        Navigation.goMain(NavigationScreenModel.Menu.Main)
                                        Navigation.Menu.go(NavigationScreenModel.Menu.Notifications, stateValues.isNarrowScreen)
                                    }
                                }
                            )
                        }
                    }

                    if (activeNotifications.size > 5) {
                        Text(
                            text = "+${activeNotifications.size - 5} ${localizedStringResource(183, "more notifications queued")}",
                            color = stateValues.TextColor,
                            fontSize = stateValues.smallTextSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                                .clip(RoundedCornerShape(stateValues.cornerRadius))
                                .background(stateValues.BackgroundColor)
                                .border(
                                    stateValues.unfocusedBorderWidth,
                                    stateValues.PlaceholderTextColor,
                                    RoundedCornerShape(stateValues.cornerRadius)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        val normalSlotCount = when (stateValues.appModeId) {
            APP_MODE_STORE -> Navigation.bottomNavBarScreensStore.size
            APP_MODE_SUPPLIER, APP_MODE_MANUFACTURER -> Navigation.bottomNavBarScreensSupplier.size
            else -> Navigation.bottomNavBarScreensBuyer.size
        }.coerceAtLeast(1)
        val bottomNavigationCompact = stateValues.screenWidth < 390.dp || normalSlotCount >= 6
        val bottomNavigationIconSize = when {
            stateValues.screenWidth < 340.dp && normalSlotCount > 5 -> 20.dp
            bottomNavigationCompact -> 22.dp
            else -> 24.dp
        }
        val bottomNavigationTextMaxLines = if (bottomNavigationCompact) 1 else 2
        val bottomNavigationHeight = if (bottomNavigationCompact) 62.dp else 58.dp

        if (showNavigationBar) Column(
            modifier = Modifier
                .padding(top = 4.dp)
                .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                .clip(
                    RoundedCornerShape(
                        topStart = stateValues.cornerRadius,
                        topEnd = stateValues.cornerRadius
                    )
                )
                .background(stateValues.BackgroundColor)
                .border(
                    stateValues.unfocusedBorderWidth,
                    stateValues.PlaceholderTextColor,
                    RoundedCornerShape(
                        topStart = stateValues.cornerRadius,
                        topEnd = stateValues.cornerRadius
                    )
                )
                .fillMaxWidth()
                .height(bottomNavigationHeight)
                .wrapContentHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.weight(1f).run {
                    if (stateValues.isNarrowScreen) fillMaxWidth()
                    else aitaWidthCap(720.dp).fillMaxWidth()
                }, horizontalArrangement = Arrangement.Center) {
                val items = bottomNavigationItems
                // Preserve a destination's normal share even when permissions leave only Menu.
                val missingSlots = (normalSlotCount - items.size).coerceAtLeast(0)
                if (missingSlots > 0) Spacer(Modifier.weight(missingSlots / 2f).fillMaxHeight())

                items.forEach { model ->
                    val isSelected = model.route == stateValues.navigationScreensMain.last().route

                    val iconTintColor by animateColorAsState(
                        targetValue = if (isSelected) stateValues.AccentColor else stateValues.IconTintColor,
                        animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS),
                        label = "bottomNavigationTint"
                    )
                    val itemContainerColor by animateColorAsState(
                        targetValue = if (isSelected) stateValues.AccentColor.copy(alpha = 0.11f) else Color.Transparent,
                        animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS),
                        label = "bottomNavigationContainer"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(itemContainerColor)
                            .aitaClickable(
                                onClick = {
                                    if (stateValues.appModeId != APP_MODE_STORE || model in filteredMainBottomDestinations()) {
                                        coroutineScope.launch { Navigation.goMain(model) }
                                    } else {
                                        postInAppNotification(currentUserPermissionDeniedMessage(), NotificationType.Negative)
                                    }
                                },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = stateValues.TextColor)
                            )
                            .padding(horizontal = 2.dp, vertical = 1.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.requiredSize(bottomNavigationIconSize)) {
                            CpImage(
                                modifier = Modifier.fillMaxSize().aitaSelectionMotion(selected = isSelected, selectedScale = 1.11f),
                                url = model.iconPath, fallbackRes = model.iconRes,
                                contentDescription = model.name, tintColor = iconTintColor
                            )
                            if (model == NavigationScreenModel.Menu.Main) {
                                MenuUpdateMarker(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-3).dp))
                                MenuNotificationsMarker(Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-3).dp))
                            }
                        }

                        Text(
                            text = model.name,
                            color = iconTintColor,
                            textAlign = TextAlign.Center,
                            fontSize = stateValues.smallTextSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                            maxLines = bottomNavigationTextMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (missingSlots > 0) Spacer(Modifier.weight(missingSlots / 2f).fillMaxHeight())
            }
        }

        val simpleDialogContentL by simpleDialogContent.collectAsState()

        simpleDialogContentL?.let {
            SimpleDialogWidget(
                title = it.first, positiveAction = it.second, negativeAction = it.third
            )
        }
    }
}


@Composable
internal fun AppConfiguration.WorkshiftGateOverlay(
    blockingTransactionScreen: Boolean = false
) {
    val activeStoreName = stateValues.stores.orEmpty()
        .findStoreOrBranch(stateValues.activeStoreId)
        ?.name
        ?.extractLocalizedString(stateValues.appLanguage)
        .orEmpty()
    val membership = activeStoreWorkerMembership()
    val workerIdentifier = membership?.let { worker ->
        worker.userPublicId.ifBlank { worker.phoneNumber.asDisplayPhoneNumber().ifBlank { worker.email.ifBlank { worker.id } } }
    }.orEmpty()

    fun closeWorkshiftDialog() {
        hideWorkshiftStartDialog()
        if (blockingTransactionScreen) {
            coroutineScope.launch {
                Navigation.goMain(NavigationScreenModel.Menu.Main)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(stateValues.BackgroundColor.copy(alpha = 0.94f))
            .aitaClickable(enabled = false) {}
            .padding(stateValues.marginTextFieldGroup),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.42f)
                .foregroundTactileShadow(stateValues.cornerRadius, elevated = true)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.BackgroundColor)
                .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                .padding(stateValues.marginTextFieldGroup),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CpImage(
                modifier = Modifier.size(44.dp),
                url = stateValues.drawablePathIconWorkers,
                fallbackRes = stateValues.drawableResIconWorkers.value,
                contentDescription = localizedStringResource(643, "Start workshift"),
                tintColor = stateValues.AccentColor
            )

            Text(
                text = localizedStringResource(644, "Workshift required"),
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = activeStoreName.ifBlank { stateValues.activeStoreId.orEmpty() },
                color = stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = localizedStringResource(645, "Start a workshift before taking payments or changing cash. You can still browse the store and prepare work without starting a shift."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.textSize,
                textAlign = TextAlign.Center
            )

            if (membership?.hasWorkshiftPassword == false) {
                Text(
                    text = localizedStringResource(1089, "Set your workshift password in Workers → My work, then start the shift here."),
                    color = stateValues.ErrorColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1090, "Open My work"),
                    iconPath = stateValues.drawablePathIconWorkers,
                    confirmationRequired = false,
                    onClick = {
                        hideWorkshiftStartDialog()
                        coroutineScope.launch {
                            Navigation.goMain(NavigationScreenModel.Menu.Main)
                            Navigation.Menu.go(NavigationScreenModel.Menu.Workers, stateValues.isNarrowScreen)
                        }
                    }
                )
            } else if (membership != null) {
                val startPasswordField = passwordTextField(
                    modifier = Modifier.fillMaxWidth(),
                    stateHost = NavigationScreenModel.Menu.Main,
                    stateKey = "keyState_startWorkshiftPassword_${stateValues.activeStoreId.orEmpty()}",
                    titleText = localizedStringResource(1078, "Workshift password"),
                    placeholderText = localizedStringResource(649, "Enter workshift password")
                )

                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1091, "Start shift"),
                    iconPath = stateValues.drawablePathIconCheck,
                    confirmationRequired = false,
                    enabled = workerIdentifier.isNotBlank() && startPasswordField.value.text.isNotBlank() && !stateValues.workshiftLoginInProgress,
                    loading = stateValues.workshiftLoginInProgress,
                    loadingText = localizedStringResource(908, "Starting workshift…"),
                    onClick = {
                        stateValues.activeStoreId?.let { storeId ->
                            startWorkshift(
                                storeId = storeId,
                                workerIdentifier = workerIdentifier,
                                password = startPasswordField.value.text
                            ) { result ->
                                if (result is DataState.Success) {
                                    startPasswordField.reset()
                                    hideWorkshiftStartDialog()
                                    getCurrentWorkshift(storeId)
                                }
                            }
                        }
                    }
                )
            }

            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(8, "Cancel"),
                iconPath = stateValues.drawablePathIconCancel,
                enabledColor = stateValues.PlaceholderTextColor,
                confirmationRequired = false,
                onClick = { closeWorkshiftDialog() }
            )
        }
    }
}

@Composable
internal fun AppConfiguration.NotificationPopupCard(
    notification: NotificationDataModel,
    compact: Boolean,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val deviceState by deviceFileNotifications.collectAsState()
    if (isDeviceFileNotification(notification) && deviceState.entries.none { it.notification.id == notification.id && it.owner.isCurrent() }) return
    val color = when (notification.type) {
        NotificationType.Positive -> stateValues.OkayColor
        NotificationType.Negative -> stateValues.ErrorColor
        NotificationType.Neutral -> stateValues.AccentColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = true)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.focusedBorderWidth,
                color,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = color)
            ) { onOpenHistory() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )

        Column(modifier = Modifier.weight(1f)) {
            localizedNotificationTitle(notification)
                .takeIf { !isNotificationTypeOnlyTitle(it, notification.type) }
                ?.let { popupTitle ->
                    Text(
                        text = popupTitle,
                        color = color,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            Text(
                text = localizedNotificationMessage(notification),
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                maxLines = if (isDeviceFileNotification(notification)) Int.MAX_VALUE else if (compact) 3 else 5,
                overflow = TextOverflow.Ellipsis
            )
            DeviceNotificationLink(notification)
            Text(
                text = receiptUiDateTime(notification.createdAtMillis.takeIf { it > 0L } ?: getCurrentTimeMillis()),
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(color)
                .aitaClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = stateValues.AccentTextColor)
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            CpImage(
                modifier = Modifier.size(20.dp),
                url = stateValues.drawablePathIconCancel,
                fallbackRes = stateValues.drawableResIconCancel.value,
                contentDescription = stateValues.stringCancel,
                tintColor = stateValues.AccentTextColor
            )
        }
    }
}



@Composable
fun AppConfiguration.NotificationsScreen(
    onBack: (() -> Unit)? = null
) {
    var search by rememberSaveable { mutableStateOf("") }
    val unreadRequest by unreadNotificationsOpenRequest.collectAsState()
    var selectedCategory by rememberNavigationSection("notifications:section", if (unreadRequest > 0) "unread" else "all")
    LaunchedEffect(unreadRequest) {
        if (unreadRequest > 0) { selectedCategory = "unread"; unreadNotificationsOpenRequest.value = 0 }
    }
    val prepared by LiveCollectionWorkspace.notifications.collectAsState()
    val projection = prepared?.takeIf { it.account == stateValues.userAccount?.id && it.generation == currentAuthenticatedSessionGeneration() }
    val searchedNotifications = projection?.searched.orEmpty()
    val filtered = projection?.visible.orEmpty()
    val notificationListState = rememberPersistentLazyListState(NavigationScreenModel.Notifications, "notifications_scroll")
    LaunchedEffect(search, selectedCategory) {
        LiveCollectionWorkspace.selectNotifications(NotificationSelection(search, selectedCategory))
    }
    LaunchedEffect(stateValues.userAccount?.id) {
        if (stateValues.userAccount != null) getNotifications()
    }
    LaunchedEffect(notificationListState, selectedCategory, stateValues.userAccount?.id) {
        // Never auto-remove rows from the Unread tab. Elsewhere mark only rows actually seen.
        if (selectedCategory != "unread") {
            snapshotFlow { notificationListState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
                .collectLatest { visibleIds ->
                    delay(700)
                    val current = LiveCollectionWorkspace.notifications.value
                    if (current != null && current.selection.category == selectedCategory && current.account == stateValues.userAccount?.id) {
                        val unread = current.visible.filter { it.id in visibleIds && it.readAtMillis == null }
                        markNotificationsRead(unread.filterNot(::isDeviceFileNotification).map { it.id })
                        unread.filter(::isDeviceFileNotification).forEach { markDeviceFileNotificationsRead(it.id) }
                    }
                }
        }
    }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        appBar = {
            ScreenAppBarWidget(
                title = localizedStringResource(177, "Notifications"),
                iconPath = stateValues.drawablePathIconTransactionHistory,
                onBack = onBack
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .align(Alignment.CenterHorizontally)
                .padding(stateValues.marginTextField)
        ) {
            val notificationSearchContent = searchTextField(
                valueInitial = search,
                stateHost = NavigationScreenModel.Notifications,
                stateKey = "notifications_search",
                modifier = Modifier.fillMaxWidth(),
                updateIsFocusedAction = null
            )

            LaunchedEffect(notificationSearchContent.value.text) {
                search = notificationSearchContent.value.text
            }

            Spacer(modifier = Modifier.height(8.dp))

            val notificationTab = tabRowWidget(
                modifier = Modifier.fillMaxWidth(),
                selectedIndexInitial = selectedCategory,
                tabs = listOf(
                    TabContent("all", tabLabelWithCount(stateValues.stringAll, searchedNotifications.size)) { selectedCategory = it },
                    TabContent("unread", tabLabelWithCount(localizedStringResource(178, "Unread"), searchedNotifications.count { it.readAtMillis == null })) { selectedCategory = it },
                    TabContent("positive", tabLabelWithCount(localizedStringResource(179, "Positive"), searchedNotifications.count { it.type == NotificationType.Positive })) { selectedCategory = it },
                    TabContent("negative", tabLabelWithCount(localizedStringResource(180, "Negative"), searchedNotifications.count { it.type == NotificationType.Negative })) { selectedCategory = it },
                    TabContent("neutral", tabLabelWithCount(localizedStringResource(181, "Neutral"), searchedNotifications.count { it.type == NotificationType.Neutral })) { selectedCategory = it }
                ),
                unselectedContainerColor = stateValues.BackgroundColor
            )

            LaunchedEffect(notificationTab.id) {
                selectedCategory = notificationTab.id
            }
        }

        LazyColumn(
            state = notificationListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (projection == null) {
                item { LoadingSkeleton(layout = LoadingLayout.Notification, modifier = Modifier.fillMaxWidth(), rows = 4) }
            } else if (filtered.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(vertical = stateValues.marginTextFieldGroup),
                        text = if (search.isBlank()) stateValues.stringListEmpty else localizedStringResource(193, "No notifications match this search")
                    )
                }
            } else {
                items(filtered, key = { it.id }) { notification ->
                    NotificationHistoryCard(notification)
                }
            }

            item { Spacer(modifier = Modifier.height(stateValues.screenHeight / 5)) }
        }
    }
}

internal fun AppConfiguration.notificationTypeLabel(type: NotificationType): String {
    return when (type) {
        NotificationType.Positive -> localizedStringResource(179, "Positive")
        NotificationType.Negative -> localizedStringResource(180, "Negative")
        NotificationType.Neutral -> localizedStringResource(181, "Neutral")
    }
}

internal fun AppConfiguration.isNotificationTypeOnlyTitle(title: String, type: NotificationType): Boolean {
    val normalized = title.trim()
    if (normalized.isBlank()) return true

    val technicalTypeLabels = when (type) {
        NotificationType.Positive -> setOf("positive", "положительные", "положительное", "жағымды")
        NotificationType.Negative -> setOf("negative", "отрицательные", "отрицательное", "жағымсыз")
        NotificationType.Neutral -> setOf("neutral", "нейтральные", "нейтральное", "бейтарап")
    }

    return normalized.equals(notificationTypeLabel(type), ignoreCase = true) || normalized.lowercase() in technicalTypeLabels
}

internal fun AppConfiguration.eventPresentationLanguage(): String =
    stateValues.appLanguage.let { if (it == "system") getSystemLocaleLanguage() else it }

internal fun AppConfiguration.eventPresentationResourceValues(id: Long): List<LocalizedStringDataModel>? =
    currentEventResourceCatalogue().values(id)
        ?: bundledLocalizedStringFallbacks[id]?.map { (language, value) -> LocalizedStringDataModel(language, value) }

internal fun AppConfiguration.localizedNotificationTitle(notification: NotificationDataModel): String {
    val reference = notification.titleTemplate ?: notification.titleTranslations.eventMessageReferenceOrNull()
        ?: legacyEventMessageReference(notification.title) ?: currentEventResourceCatalogue().referenceFor(notification.title)
    return EventMessages.render(reference, eventPresentationLanguage(), ::eventPresentationResourceValues)
        ?: notification.localizedEventTitle(eventPresentationLanguage(), ::eventPresentationResourceValues)
}

internal fun AppConfiguration.localizedNotificationMessage(notification: NotificationDataModel): String {
    val reference = notification.messageTemplate ?: notification.messageTranslations.eventMessageReferenceOrNull()
        ?: legacyEventMessageReference(notification.message) ?: currentEventResourceCatalogue().referenceFor(notification.message)
    EventMessages.render(reference, eventPresentationLanguage(), ::eventPresentationResourceValues)?.let { return it }
    if (notification.messageTranslations.isNotEmpty()) {
        return notification.localizedEventMessage(eventPresentationLanguage(), ::eventPresentationResourceValues)
    }
    return localizedNotificationMessage(notification.message)
}

internal fun AppConfiguration.localizedNotificationMessage(message: String): String {
    val normalized = message.trim()
    if (normalized.isBlank()) return message
    val reference = legacyEventMessageReference(normalized) ?: currentEventResourceCatalogue().referenceFor(normalized)
    EventMessages.render(reference, eventPresentationLanguage(), ::eventPresentationResourceValues)?.let { return it }
    val notificationKey = normalized.normalizedNotificationPopupKey()
    if (notificationKey.isServerUnavailablePopupText()) {
        return localizedStringResource(1140, "Can’t reach server. Check Wi‑Fi or server address.")
    }
    if (notificationKey.isServerRecoveryPopupText()) return localizedStringResource(1138, "Server connected")
    if (notificationKey.isSessionRefreshPopupText()) {
        return localizedStringResource(91, "Cloud sign-in expired. Sign in again to sync. Your local data stays available.")
    }
    return message
}


internal fun AppConfiguration.localizedNotificationSource(source: String): String {
    return when (source.lowercase()) {
        "app", "device" -> stateValues.stringAppName
        "server" -> localizedStringResource(242, "Server")
        else -> source
    }
}

internal fun AppConfiguration.localizedNotificationCategory(category: String, type: NotificationType): String? {
    val clean = category.trim()
    if (clean.isBlank()) return null

    val lower = clean.lowercase()
    return when (lower) {
        "device_file" -> null
        "security" -> localizedStringResource(226, "Account security")
        "positive", "положительные", "положительное", "жағымды" -> notificationTypeLabel(NotificationType.Positive)
        "negative", "отрицательные", "отрицательное", "жағымсыз" -> notificationTypeLabel(NotificationType.Negative)
        "neutral", "нейтральные", "нейтральное", "бейтарап" -> notificationTypeLabel(NotificationType.Neutral)
        else -> clean.takeUnless { isNotificationTypeOnlyTitle(it, type) } ?: notificationTypeLabel(type)
    }
}

@Composable
internal fun AppConfiguration.NotificationHistoryCard(notification: NotificationDataModel) {
    val color = when (notification.type) {
        NotificationType.Positive -> stateValues.OkayColor
        NotificationType.Negative -> stateValues.ErrorColor
        NotificationType.Neutral -> stateValues.AccentColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                color,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = color)
            ) {
                if (isDeviceFileNotification(notification)) markDeviceFileNotificationsRead(notification.id)
                else if (notification.readAtMillis == null) markNotificationRead(notification.id)
            }
            .padding(stateValues.marginTextFieldGroup)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )

            Text(
                modifier = Modifier.weight(1f),
                text = localizedNotificationTitle(notification)
                    .takeIf { !isNotificationTypeOnlyTitle(it, notification.type) }
                    ?: notificationTypeLabel(notification.type),
                color = color,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (notification.readAtMillis == null) {
                Text(
                    text = localizedStringResource(182, "NEW"),
                    color = stateValues.AccentTextColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(color)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = localizedNotificationMessage(notification),
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

        DeviceNotificationLink(notification)

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = listOfNotNull(
                localizedNotificationCategory(notification.category, notification.type),
                localizedNotificationSource(notification.source).takeIf { it.isNotBlank() },
                receiptUiDateTime(notification.createdAtMillis.takeIf { it > 0L } ?: getCurrentTimeMillis()),
                notification.readAtMillis?.let { "${localizedStringResource(190, "read")} ${receiptUiDateTime(it)}" }
            ).joinToString(" • "),
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize
        )
    }
}

internal val _simpleDialogContent =
    MutableStateFlow<Triple<String, Pair<String, () -> Unit>, Pair<String, () -> Unit>>?>(null)
val simpleDialogContent = _simpleDialogContent.asStateFlow()

suspend fun postSimpleDialogContent(
    title: String, positiveAction: Pair<String, () -> Unit>, negativeAction: Pair<String, () -> Unit>
) {
    _simpleDialogContent.emit(Triple(title, positiveAction, negativeAction))
}

suspend fun resetSimpleDialogContent() {
    _simpleDialogContent.emit(null)
}

internal val workshiftStartDialogVisibleState = MutableStateFlow(false)

fun showWorkshiftStartDialog() {
    workshiftStartDialogVisibleState.value = true
}

fun hideWorkshiftStartDialog() {
    workshiftStartDialogVisibleState.value = false
}

@Composable
fun AppConfiguration.LargeIconWithTitleWidget(
    modifier: Modifier = Modifier,
    imageUrl: String,
    imageRes: DrawableResource,
    title: String = "",
    titleTextSize: TextUnit = stateValues.titleTextSize,
    titleTextColor: Color = stateValues.TextColor,
    contentDescription: String = title,
    iconSize: Dp? = null,
    titleGap: Dp = stateValues.screenHeight / 36
) {
    val rawIconSize = iconSize ?: if (stateValues.isNarrowScreen) {
        stateValues.boundWidgetWidth * 0.46f
    } else {
        stateValues.boundWidgetWidth * 0.54f
    }
    val resolvedIconSize = when {
        rawIconSize < 96.dp -> 96.dp
        rawIconSize > 220.dp -> 220.dp
        else -> rawIconSize
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        CpImage(
            modifier = Modifier.size(resolvedIconSize),
            url = imageUrl,
            fallbackRes = imageRes,
            contentDescription = contentDescription
        )

        if (title.isNotEmpty()) {
            Spacer(modifier = Modifier.height(titleGap))

            Text(
                text = title,
                style = TextStyle(fontFamily = LocalAitaFontFamily.current,
                    color = titleTextColor,
                    fontSize = titleTextSize,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
