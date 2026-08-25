// THIS IS CommonMainCompose.kt split slice: MenuB
@file:OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import io.kamel.core.config.*
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.kamel.image.config.imageBitmapDecoder
import io.kamel.image.config.svgDecoder
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import kz.aita.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.text.equals
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
        SecuritySessionInfoLine("IP", session.ipAddress)
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
    val details = event.details.visibleLocalizedString(stateValues.appLanguage, "")
    val metadataText = event.metadata
        .filterValues { it.isNotBlank() }
        .entries
        .joinToString(" • ") { "${it.key}: ${it.value}" }

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
        SecuritySessionInfoLine(localizedStringResource(235, "Language"), event.metadata["localeLanguage"].orEmpty())
        SecuritySessionInfoLine("IP", event.ipAddress)
        SecuritySessionInfoLine(localizedStringResource(1128, "Session ID"), event.sessionId?.take(8).orEmpty())
        SecuritySessionInfoLine(localizedStringResource(1127, "Details"), details)
        SecuritySessionInfoLine(localizedStringResource(1137, "Related data"), metadataText)
    }
}

@Composable
fun AppConfiguration.MenuSecurityScreen() {
    val sessionsState by securitySessionsState.value.collectAsState()
    val historyState by securitySessionHistoryState.value.collectAsState()
    val sessions = (sessionsState as? DataState.Success<List<SecuritySessionDataModel>>)?.payload.orEmpty()
    val history = (historyState as? DataState.Success<List<SecuritySessionHistoryDataModel>>)?.payload.orEmpty()
    val currentSession = sessions.firstOrNull { it.current }
    var selectedSecurityTab by rememberSaveable { mutableStateOf("sessions") }

    LaunchedEffect(Unit) {
        getSecuritySessions()
        getSecuritySessionHistory()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenAppBarWidget(
            title = localizedStringResource(209, "Security"),
            iconPath = stateValues.drawablePathIconPassword,
            onBack = {
                coroutineScope.launch {
                    Navigation.Menu.pop(stateValues.isNarrowScreen)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .padding(horizontal = stateValues.marginTextField, vertical = 8.dp)
        ) {
            val selectedTab = tabRowWidget(
                modifier = Modifier.fillMaxWidth(),
                selectedIndexInitial = selectedSecurityTab,
                tabs = listOf(
                    TabContent("sessions", tabLabelWithCount(localizedStringResource(1121, "Active sessions"), sessions.size)),
                    TabContent("history", tabLabelWithCount(localizedStringResource(1122, "Security history"), history.size))
                ),
                unselectedContainerColor = stateValues.BackgroundColor
            )

            LaunchedEffect(selectedTab.id) {
                selectedSecurityTab = selectedTab.id
            }
        }

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Security, selectedSecurityTab),
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .weight(1f),
            contentPadding = PaddingValues(stateValues.marginTextFieldGroup),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextFieldGroup)
        ) {
            item {
                AccountAuthenticationSettingsCard()
            }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actionButton(
                        modifier = Modifier.weight(1f),
                        text = localizedStringResource(237, "Refresh"),
                        iconPath = stateValues.drawablePathIconSearch,
                        onClick = {
                            if (selectedSecurityTab == "history") getSecuritySessionHistory() else getSecuritySessions()
                        }
                    )
                    if (selectedSecurityTab == "sessions") {
                        actionButton(
                            modifier = Modifier.weight(1f),
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


@Composable
fun AppConfiguration.MenuDevicesScreen() {
    val receiptPrinters by receiptPrinterDevicesState.collectAsState()
    val configuredReceiptPrinterId by configuredReceiptPrinterDeviceIdState.collectAsState()
    val labelPrinters by labelPrinterDevicesState.collectAsState()
    val configuredLabelPrinterId by configuredLabelPrinterDeviceIdState.collectAsState()
    val configuredLabelPrinterProtocol by configuredLabelPrinterProtocolState.collectAsState()
    var refreshingReceiptPrinters by remember { mutableStateOf(false) }
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
        refreshingReceiptPrinters = true
        refreshReceiptPrinterDevices { result ->
            coroutineScope.launch {
                refreshingReceiptPrinters = false
                if (showNotification) receiptActionNotification(result, refreshSuccessText)
            }
        }
    }

    fun refreshLabelPrinters(showNotification: Boolean) {
        refreshingLabelPrinters = true
        refreshLabelPrinterDevices { result ->
            coroutineScope.launch {
                refreshingLabelPrinters = false
                if (showNotification) receiptActionNotification(result, labelPrintersRefreshedText)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshReceiptPrinters(showNotification = false)
        refreshLabelPrinters(showNotification = false)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenAppBarWidget(
            title = stateValues.stringDevices,
            iconPath = stateValues.drawablePathIconDevices,
            onBack = {
                coroutineScope.launch {
                    Navigation.Menu.pop(stateValues.isNarrowScreen)
                }
            }
        )

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Devices),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextFieldGroup),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item {
                MessageText(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(617, "Open Bluetooth/devices settings"),
                    subText = stateValues.stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters,
                    textSize = stateValues.titleTextSize,
                    subTextSize = stateValues.textSize
                )
            }

            item {
                DeviceSettingsCard(
                    title = localizedStringResource(1252, "A4 paper printer"),
                    subtitle = localizedStringResource(1253, "Analytics reports use the regular system print dialog for A4 paper printers."),
                    iconPath = stateValues.drawablePathIconAnalyticsReport,
                    iconRes = stateValues.drawableResIconAnalyticsReport.value
                ) {
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(616, "Open system devices"),
                        iconPath = stateValues.drawablePathIconDevices,
                        iconRes = stateValues.drawableResIconDevices.value,
                        confirmationRequired = false,
                        onClick = { openPlatformDevicesSettings() }
                    )
                }
            }

            item {
                DeviceSettingsCard(
                    title = localizedStringResource(1254, "Thermal receipt printer"),
                    subtitle = localizedStringResource(1249, "Transaction receipts use ESC/POS thermal printers. Analytics reports use A4 paper printing."),
                    iconPath = stateValues.drawablePathIconReceipt,
                    iconRes = stateValues.drawableResIconReceipt.value
                ) {
                    if (stateValues.isNarrowScreen) {
                        Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = refreshButtonText,
                                iconPath = stateValues.drawablePathIconRefresh,
                                iconRes = stateValues.drawableResIconRefresh.value,
                                loading = refreshingReceiptPrinters,
                                confirmationRequired = false,
                                onClick = { refreshReceiptPrinters(showNotification = true) }
                            )
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = testReceiptButtonText,
                                iconPath = stateValues.drawablePathIconReceipt,
                                iconRes = stateValues.drawableResIconReceipt.value,
                                enabled = !configuredReceiptPrinterId.isNullOrBlank(),
                                onDisabledClick = { postInAppNotification(receiptPrinterNotConfiguredText, NotificationType.Negative, transient = true) },
                                confirmationRequired = false,
                                onClick = {
                                    coroutineScope.launch {
                                        receiptActionNotification(
                                            printReceiptEscPos(
                                                buildReceiptPrinterTestEscPosBytes(
                                                    title = testReceiptTitle,
                                                    dateText = receiptUiDateTime(getCurrentTimeMillis())
                                                ),
                                                ReceiptTextLabelsDataModel(printerNotConfigured = receiptPrinterNotConfiguredText)
                                            ),
                                            testReceiptSentText
                                        )
                                    }
                                }
                            )
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = localizedStringResource(1265, "Clear receipt printer"),
                                iconPath = stateValues.drawablePathIconCancel,
                                iconRes = stateValues.drawableResIconCancel.value,
                                enabled = !configuredReceiptPrinterId.isNullOrBlank(),
                                confirmationRequired = false,
                                onClick = {
                                    configureReceiptPrinterDevice(null) { result ->
                                        coroutineScope.launch {
                                            receiptActionNotification(result, printerClearedText)
                                        }
                                    }
                                }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            actionButton(
                                modifier = Modifier.weight(1f),
                                text = refreshButtonText,
                                iconPath = stateValues.drawablePathIconRefresh,
                                iconRes = stateValues.drawableResIconRefresh.value,
                                loading = refreshingReceiptPrinters,
                                confirmationRequired = false,
                                onClick = { refreshReceiptPrinters(showNotification = true) }
                            )
                            actionButton(
                                modifier = Modifier.weight(1f),
                                text = testReceiptButtonText,
                                iconPath = stateValues.drawablePathIconReceipt,
                                iconRes = stateValues.drawableResIconReceipt.value,
                                enabled = !configuredReceiptPrinterId.isNullOrBlank(),
                                onDisabledClick = { postInAppNotification(receiptPrinterNotConfiguredText, NotificationType.Negative, transient = true) },
                                confirmationRequired = false,
                                onClick = {
                                    coroutineScope.launch {
                                        receiptActionNotification(
                                            printReceiptEscPos(
                                                buildReceiptPrinterTestEscPosBytes(
                                                    title = testReceiptTitle,
                                                    dateText = receiptUiDateTime(getCurrentTimeMillis())
                                                ),
                                                ReceiptTextLabelsDataModel(printerNotConfigured = receiptPrinterNotConfiguredText)
                                            ),
                                            testReceiptSentText
                                        )
                                    }
                                }
                            )
                            actionButton(
                                modifier = Modifier.weight(1f),
                                text = localizedStringResource(1265, "Clear receipt printer"),
                                iconPath = stateValues.drawablePathIconCancel,
                                iconRes = stateValues.drawableResIconCancel.value,
                                enabled = !configuredReceiptPrinterId.isNullOrBlank(),
                                confirmationRequired = false,
                                onClick = {
                                    configureReceiptPrinterDevice(null) { result ->
                                        coroutineScope.launch {
                                            receiptActionNotification(result, printerClearedText)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                    Text(
                        text = localizedStringResource(1255, "Detected receipt printers"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.accentTextSize,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(stateValues.marginTextField))

                    if (receiptPrinters.isEmpty()) {
                        MessageText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = stateValues.marginTextFieldGroup),
                            text = localizedStringResource(1256, "No paired thermal receipt printers found"),
                            subText = localizedStringResource(1266, "Pair or connect the printer in system settings, then refresh this list."),
                            textSize = stateValues.textSize,
                            subTextSize = stateValues.smallTextSize
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                            receiptPrinters.forEach { printer ->
                                ThermalReceiptPrinterCard(
                                    printer = printer,
                                    selected = printer.id == configuredReceiptPrinterId || printer.configured,
                                    onSelect = {
                                        configureReceiptPrinterDevice(printer.id) { result ->
                                            coroutineScope.launch {
                                                receiptActionNotification(result, printerSelectedText)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                DeviceSettingsCard(
                    title = localizedStringResource(1276, "Sticky label printer"),
                    subtitle = localizedStringResource(1277, "Sticky item tags use TSPL, ZPL or CPCL label printers. They print barcode, item name and price onto small adhesive labels."),
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            actionButton(
                                modifier = Modifier.weight(1f),
                                text = refreshButtonText,
                                iconPath = stateValues.drawablePathIconRefresh,
                                iconRes = stateValues.drawableResIconRefresh.value,
                                loading = refreshingLabelPrinters,
                                confirmationRequired = false,
                                onClick = { refreshLabelPrinters(showNotification = true) }
                            )
                            actionButton(
                                modifier = Modifier.weight(1f),
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
                                modifier = Modifier.weight(1f),
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

                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                    Text(
                        text = localizedStringResource(1278, "Detected label printers"),
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
    onSelect: () -> Unit
) {
    Row(
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        CpImage(
            modifier = Modifier.size(24.dp),
            url = stateValues.drawablePathIconReceipt,
            fallbackRes = stateValues.drawableResIconReceipt.value,
            contentDescription = printer.name.ifBlank { printer.id },
            tintColor = if (selected) stateValues.AccentColor else stateValues.TextColor
        )

        Column(modifier = Modifier.weight(1f)) {
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
            text = if (selected) localizedStringResource(1258, "Selected printer") else localizedStringResource(1257, "Use this printer"),
            iconPath = if (selected) stateValues.drawablePathIconCheck else stateValues.drawablePathIconDevices,
            iconRes = if (selected) stateValues.drawableResIconCheck.value else stateValues.drawableResIconDevices.value,
            enabled = !selected,
            confirmationRequired = false,
            fillMaxWidthIfTextPresent = false,
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
    Row(
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        CpImage(
            modifier = Modifier.size(24.dp),
            url = stateValues.drawablePathIconLabelPrinter,
            fallbackRes = stateValues.drawableResIconLabelPrinter.value,
            contentDescription = printer.name.ifBlank { printer.id },
            tintColor = if (selected) stateValues.AccentColor else stateValues.TextColor
        )

        Column(modifier = Modifier.weight(1f)) {
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
            text = if (selected) localizedStringResource(1258, "Selected printer") else localizedStringResource(1303, "Use this label printer"),
            iconPath = if (selected) stateValues.drawablePathIconCheck else stateValues.drawablePathIconLabelPrinter,
            iconRes = if (selected) stateValues.drawableResIconCheck.value else stateValues.drawableResIconLabelPrinter.value,
            enabled = !selected,
            confirmationRequired = false,
            fillMaxWidthIfTextPresent = false,
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
    SupportFaqEntry(822, 874, "How do receipts, PDF, sharing, WhatsApp and printing work?", 875, "After a transaction, receipt actions can save PDF, open the platform share sheet, send through WhatsApp when available, or send the receipt to a printer/device bridge."),
    SupportFaqEntry(822, 876, "How does the cash register work?", 877, "The cash register tracks expected cash in the drawer. Cash sales increase it, cash returns decrease it, and authorized users can register cash extractions."),
    SupportFaqEntry(822, 878, "How do debtors and partial payments work?", 879, "Debtors store customers or companies that owe money. You can track debt amount, due date, interest, payment history and partial repayments."),
    SupportFaqEntry(822, 880, "What does Analytics show?", 881, "Analytics summarizes revenue, returns, supply cost, estimated profit, payment mix, debt, top items, sales by time, stock risk, expiring batches, supplier activity and worker performance."),
    SupportFaqEntry(820, 882, "How do finances and subscriptions work?", 883, "Finances show wallet balance, top-ups, ledger entries and subscription charges. Store subscriptions unlock plan-based functionality and renewal state."),
    SupportFaqEntry(821, 884, "What happens if the server or internet is unavailable?", 885, "The app keeps cached data where possible and shows connection notifications. When the server returns, realtime sync refreshes stores, stock, transactions, workers, finance and messages."),
    SupportFaqEntry(821, 886, "How does realtime sync work?", 887, "The app keeps a WebSocket connection to the server. After changes, connected clients refresh affected data, so cashier, owner and branch devices stay closer to the same state."),
    SupportFaqEntry(821, 888, "How do scanners, printers and devices work?", 889, "Device screens and platform bridges handle barcode scanners, receipt printers, Bluetooth/system settings and platform-specific saving, sharing and printing."),
    SupportFaqEntry(821, 890, "What is local branch network?", 891, "Local branch network is meant for branch devices that can exchange queued local operations and snapshots when direct server availability is limited. It should still reconcile with the server as authority."),
    SupportFaqEntry(819, 892, "How do language and theme preferences work?", 893, "Language and theme are saved locally and can also be synced with the user account. Login-screen choices can intentionally override saved account preferences."),
    SupportFaqEntry(823, 894, "How is account security handled?", 895, "Security sessions show devices signed into the account. You can revoke unfamiliar sessions, and tokens are refreshed securely by platform storage."),
    SupportFaqEntry(821, 896, "What should I do if stock or transaction data looks wrong?", 897, "Refresh data, check active store/branch, check operation logs, review batches and transaction history, then contact support with store, time, item barcode and screenshots if needed."),
    SupportFaqEntry(822, 898, "How are goods categories and global goods used?", 899, "Generic categories and goods templates help start faster. Store-specific goods can still have their own names, barcodes, prices, units, conditions and supplier data."),
    SupportFaqEntry(822, 900, "How will manufacturers, suppliers, stores and buyers connect?", 901, "The project is being built as a chain: manufacturer/supplier data can feed stores, stores manage branches and stock, and future buyer flows can show goods and orders outside the current account."),
    SupportFaqEntry(819, 902, "How should I prepare before using AITA in a real store day?", 903, "Create the store and branches, add workers and permissions, add goods and batches, test scanner/printer, make a few test transactions, check receipts and confirm analytics/cash register behavior."),
    SupportFaqEntry(819, 904, "What should I include when contacting support?", 905, "Send what you were doing, store/branch, approximate time, device/platform, barcode or transaction id, screenshots, and whether the issue repeats after refresh or reconnect."),
)

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
internal fun AppConfiguration.SupportMessageBubble(message: SupportMessageDataModel) {
    val mine = message.senderRole == "customer"
    val alignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (mine) stateValues.AccentColor.copy(alpha = 0.18f) else stateValues.BackgroundColor
    val borderColor = if (mine) stateValues.AccentColor else stateValues.PlaceholderTextColor

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 0.86f else 0.68f)
                .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(bubbleColor)
                .border(stateValues.unfocusedBorderWidth, borderColor, RoundedCornerShape(stateValues.cornerRadius))
                .padding(stateValues.marginTextField)
        ) {
            Text(
                text = if (mine) localizedStringResource(834, "You") else message.senderDisplayName.ifBlank { localizedStringResource(835, "Support team") },
                color = if (mine) stateValues.AccentColor else stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.body,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = receiptUiDateTime(message.createdAtMillis),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AppConfiguration.MenuSupportScreen() {
    val ticketsState by supportTicketsState.value.collectAsState()
    val tickets = (ticketsState as? DataState.Success<List<SupportTicketDataModel>>)?.payload.orEmpty()
    val messagesState by supportMessagesState.value.collectAsState()
    val messages = (messagesState as? DataState.Success<List<SupportMessageDataModel>>)?.payload.orEmpty()
    val sending by supportMessageSendingState.collectAsState()
    val activeTicketId by activeSupportTicketIdState.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf("faq") }
    var faqSearch by rememberSaveable { mutableStateOf("") }
    var draftMessage by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("general") }
    var composingNewTicket by rememberSaveable { mutableStateOf(false) }
    val faqEntries = supportFaqEntries()

    val selectedTicket = if (composingNewTicket) null else (
            tickets.firstOrNull { it.id == activeTicketId }
                ?: tickets.firstOrNull { it.status != "closed" }
                ?: tickets.firstOrNull()
            )

    LaunchedEffect(Unit) { getSupportTickets() }
    LaunchedEffect(tickets.joinToString("|") { it.id }, composingNewTicket) {
        if (!composingNewTicket && activeTicketId == null) {
            val firstTicketId = tickets.firstOrNull { it.status != "closed" }?.id ?: tickets.firstOrNull()?.id
            firstTicketId?.let { activeSupportTicketIdState.emit(it) }
        }
    }
    LaunchedEffect(selectedTicket?.id, composingNewTicket) {
        if (!composingNewTicket) {
            selectedTicket?.let {
                activeSupportTicketIdState.emit(it.id)
                getSupportMessages(it.id, markRead = true)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenAppBarWidget(
            title = localizedStringResource(813, "Support"),
            iconPath = stateValues.drawablePathIconSupport,
            onBack = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } }
        )

        tabRowWidget(
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2),
            tabs = listOf(
                TabContent("faq", tabLabelWithCount(localizedStringResource(814, "FAQ"), faqEntries.size)) { selectedTab = it },
                TabContent("chat", tabLabelWithCount(localizedStringResource(815, "Support chat"), tickets.size)) { selectedTab = it }
            ),
            selectedIndexInitial = selectedTab
        )

        if (selectedTab == "faq") {
            val allEntries = faqEntries
            val query = faqSearch.trim()
            val filtered = allEntries.filter { entry ->
                val question = localizedStringResource(entry.questionId, entry.questionFallback)
                val answer = localizedStringResource(entry.answerId, entry.answerFallback)
                val category = localizedStringResource(entry.categoryId, "General")
                query.isBlank() || listOf(question, answer, category).any { it.contains(query, ignoreCase = true) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                    .padding(horizontal = stateValues.marginTextField)
            ) {
                TransactionPlainTextField(
                    title = "",
                    value = faqSearch,
                    placeholder = localizedStringResource(816, "Search FAQ"),
                    leadingIconPath = stateValues.drawablePathIconSearch,
                    stateHost = NavigationScreenModel.Menu.Support,
                    stateKey = "menu_support_faq_search",
                    onValueChange = { faqSearch = it }
                )
            }

            LazyColumn(
                state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Support, "faq"),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                    .padding(stateValues.marginTextField),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                if (filtered.isEmpty()) {
                    item { MessageText(modifier = Modifier.fillParentMaxSize(), text = localizedStringResource(841, "Nothing found in FAQ")) }
                } else {
                    items(filtered, key = { it.questionId }) { entry -> SupportFaqCard(entry) }
                }
                item { Spacer(modifier = Modifier.height(stateValues.screenHeight / 5)) }
            }
        } else {
            val categories = listOf(
                "general" to localizedStringResource(819, "General"),
                "billing" to localizedStringResource(820, "Billing"),
                "technical" to localizedStringResource(821, "Technical"),
                "operations" to localizedStringResource(822, "Store operations"),
                "account" to localizedStringResource(823, "Account and security")
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                    .weight(1f)
            ) {
                if (tickets.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2),
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        items(tickets, key = { it.id }) { ticket ->
                            actionButton(
                                text = ticket.subject,
                                subText = "${ticket.publicId} • ${if (ticket.status == "closed") localizedStringResource(832, "Closed") else localizedStringResource(831, "Open")}",
                                iconPath = stateValues.drawablePathIconSupport,
                                iconRes = stateValues.drawableResIconSupport.value,
                                enabledColor = if (!composingNewTicket && ticket.id == selectedTicket?.id) stateValues.AccentColor else stateValues.DisabledColor,
                                fillMaxWidthIfTextPresent = false,
                                onClick = {
                                    composingNewTicket = false
                                    coroutineScope.launch {
                                        activeSupportTicketIdState.emit(ticket.id)
                                        getSupportMessages(ticket.id, markRead = true)
                                    }
                                }
                            )
                        }
                    }
                }

                selectedTicket?.let { ticket ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = stateValues.marginTextField)
                            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                            .clip(RoundedCornerShape(stateValues.cornerRadius))
                            .background(stateValues.BackgroundColor)
                            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                            .padding(stateValues.marginTextField)
                    ) {
                        Text(
                            text = ticket.subject,
                            color = stateValues.TextColor,
                            fontSize = stateValues.accentTextSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${ticket.publicId} • ${localizedStringResource(842, "Last update")}: ${receiptUiDateTime(ticket.updatedAtMillis)}",
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                            actionButton(
                                text = localizedStringResource(826, "New question"),
                                iconPath = stateValues.drawablePathIconAdd,
                                fillMaxWidthIfTextPresent = false,
                                onClick = {
                                    composingNewTicket = true
                                    coroutineScope.launch {
                                        activeSupportTicketIdState.emit(null)
                                        supportMessagesState.emit(DataState.Success(emptyList()))
                                    }
                                }
                            )
                            if (ticket.status == "closed") {
                                actionButton(
                                    text = localizedStringResource(828, "Reopen request"),
                                    iconPath = stateValues.drawablePathIconSwitch,
                                    fillMaxWidthIfTextPresent = false,
                                    onClick = { reopenSupportTicket(ticket.id) }
                                )
                            } else {
                                actionButton(
                                    text = localizedStringResource(827, "Close request"),
                                    iconPath = stateValues.drawablePathIconCancel,
                                    fillMaxWidthIfTextPresent = false,
                                    enabledColor = stateValues.ErrorColor,
                                    confirmationRequired = true,
                                    onClick = { closeSupportTicket(ticket.id) }
                                )
                            }
                        }
                    }
                } ?: Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2)
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup)
                ) {
                    Text(localizedStringResource(817, "Ask support"), color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(localizedStringResource(830, "We usually answer inside this chat. Describe what happened and add IDs, barcode, store or screenshots if useful."), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    Text(localizedStringResource(818, "Choose topic"), color = stateValues.TextColor, fontSize = stateValues.smallTextSize, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    tabRowWidget(
                        modifier = Modifier.fillMaxWidth(),
                        tabs = categories.map { (id, title) ->
                            TabContent(id, title) { selectedCategory = it }
                        },
                        selectedIndexInitial = selectedCategory,
                        textSize = stateValues.smallTextSize
                    )
                }

                LazyColumn(
                    state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Support, selectedTicket?.id ?: "new_ticket"),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(stateValues.marginTextField),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    if (selectedTicket == null) {
                        item { MessageText(modifier = Modifier.fillParentMaxSize(), text = localizedStringResource(817, "Ask support"), subText = localizedStringResource(824, "Type your message")) }
                    } else if (messages.isEmpty()) {
                        item { MessageText(modifier = Modifier.fillParentMaxSize(), text = localizedStringResource(829, "No support messages yet")) }
                    } else {
                        items(messages, key = { it.id }) { message -> SupportMessageBubble(message) }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = stateValues.marginTextField, vertical = stateValues.marginTextField / 2)
                ) {
                    if (selectedTicket?.status == "closed") {
                        Text(
                            text = localizedStringResource(837, "This request is closed. Reopen it to send a new message."),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        SimpleTextInput(
                            modifier = Modifier.weight(1f),
                            value = draftMessage,
                            placeholder = localizedStringResource(824, "Type your message"),
                            singleLine = false,
                            leadingIconPath = stateValues.drawablePathIconSupport,
                            stateHost = NavigationScreenModel.Menu.Support,
                            stateKey = "menu_support_draft_message",
                            onValueChange = { draftMessage = it.take(4000) }
                        )
                        actionButton(
                            text = "",
                            iconPath = stateValues.drawablePathIconCheck,
                            iconContentDescription = localizedStringResource(825, "Send"),
                            enabled = draftMessage.isNotBlank() && !sending && selectedTicket?.status != "closed",
                            loading = sending,
                            loadingText = localizedStringResource(907, "Sending…"),
                            onDisabledClick = {
                                if (draftMessage.isBlank()) postInAppNotification(localizedStringResource(824, "Type your message"), NotificationType.Neutral)
                            },
                            onClick = {
                                val textToSend = draftMessage.trim()
                                if (textToSend.isNotBlank()) {
                                    val currentTicket = selectedTicket
                                    draftMessage = ""
                                    if (currentTicket == null) {
                                        createSupportTicket(
                                            SupportTicketCreateRequestDataModel(
                                                subject = textToSend.take(80),
                                                initialMessage = textToSend,
                                                category = selectedCategory,
                                                storeId = stateValues.activeStoreId,
                                                metadata = mapOf("clientLanguage" to stateValues.appLanguage),
                                                clientMessageId = "support_${getCurrentTimeMillis()}_${textToSend.hashCode()}"
                                            )
                                        ) { state ->
                                            if (state is DataState.Success) coroutineScope.launch { composingNewTicket = false }
                                        }
                                    } else {
                                        sendSupportMessage(
                                            SupportMessageSendRequestDataModel(
                                                ticketId = currentTicket.id,
                                                body = textToSend,
                                                metadata = mapOf("clientLanguage" to stateValues.appLanguage),
                                                clientMessageId = "support_${getCurrentTimeMillis()}_${textToSend.hashCode()}"
                                            )
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppConfiguration.MenuDebtorsScreen() {
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

        val storeId = stateValues.activeStoreId
        val debtors by debtorsState.payload.collectAsState()

        LaunchedEffect(storeId) {
            storeId?.let { getDebtors(it) }
        }

        var search by rememberSaveable { mutableStateOf("") }
        var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedTab by rememberSaveable { mutableStateOf("open") }
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
                    textSize = stateValues.smallTextSize
                )

                tabRowWidget(
                    modifier = Modifier.fillMaxWidth(),
                    tabs = listOf(
                        TabContent("asc", localizedStringResource(515, "Ascending")) { sortAscending = true },
                        TabContent("desc", localizedStringResource(516, "Descending")) { sortAscending = false }
                    ),
                    selectedIndexInitial = if (sortAscending) "asc" else "desc",
                    textSize = stateValues.smallTextSize
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        actionButton(modifier = Modifier.weight(1f), text = stateValues.stringCancel, enabledColor = stateValues.DisabledColor) { pendingDeleteId = null }
                        actionButton(modifier = Modifier.weight(1f), text = stateValues.stringDelete, enabledColor = stateValues.ErrorColor) {
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val state by NavigationScreenModel.Menu.CloseDebt.state.collectAsState()
        val selectedId = state["selected_debtor_id"]
        val debtors by debtorsState.payload.collectAsState()
        val debtor = debtors.orEmpty().find { it.id == selectedId }
        val storeId = stateValues.activeStoreId
        val transactions by transactionsState.payload.collectAsState()

        ScreenAppBarWidget(
            title = stateValues.stringCloseDebt,
            iconPath = stateValues.drawablePathIconDebtors,
            onBack = { coroutineScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } }
        )

        if (storeId == null || debtor == null) {
            MessageText(modifier = Modifier.fillMaxSize(), text = stateValues.stringListEmpty)
            return@Column
        }

        var receiptDialog by rememberSaveable(debtor.id) { mutableStateOf<String?>(null) }
        var selectedTab by rememberSaveable(debtor.id) { mutableStateOf("pay") }
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
                    SimpleTextInput(Modifier.fillMaxWidth(), phone, stateValues.stringPhoneNumber, keyboardType = KeyboardType.Phone, leadingIconPath = stateValues.drawablePathIconPerson, stateHost = NavigationScreenModel.Menu.CloseDebt, stateKey = "menu_close_debt_${debtor.id}_phone") { phone = it }
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    SimpleTextInput(Modifier.fillMaxWidth(), email, stateValues.stringEmail, keyboardType = KeyboardType.Email, leadingIconPath = stateValues.drawablePathIconEmail, stateHost = NavigationScreenModel.Menu.CloseDebt, stateKey = "menu_close_debt_${debtor.id}_email") { email = it }
                    Spacer(modifier = Modifier.height(stateValues.marginTextField))
                    TransactionPaymentAmountField(
                        title = localizedStringResource(362, "Debt amount"),
                        value = debtAmountText,
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
                    if (debtor.paymentHistory.isEmpty()) MessageText(text = localizedStringResource(349, "No payment records yet"))
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        actionButton(
                            modifier = Modifier.weight(1f),
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
                            modifier = Modifier.weight(1f),
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
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenAppBarWidget(
            title = stateValues.stringAppTheme,
            iconPath = stateValues.drawablePathIconAppTheme,
            onBack = {
                coroutineScope.launch {
                    Navigation.Menu.pop(stateValues.isNarrowScreen)
                }
            }
        )

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.AppTheme),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(
                    if (stateValues.isNarrowScreen) 1f else 0.6f
                )
        ) {
            items(stateValues.globalAppConfiguration.themes) { theme ->
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
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenAppBarWidget(
            title = localizedStringResource(910, "Interface scale"),
            iconPath = stateValues.drawablePathIconAppScale,
            onBack = {
                coroutineScope.launch {
                    Navigation.Menu.pop(stateValues.isNarrowScreen)
                }
            }
        )

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
        title = localizedStringResource(725, "Buyer mode"),
        subtitle = localizedStringResource(1387, "Personal buying, carts, order history and marketplace discovery."),
        promise = localizedStringResource(1388, "Best for the end user choosing and tracking goods."),
        iconPath = stateValues.drawablePathIconAppModeBuyer,
        iconRes = stateValues.drawableResIconAppModeBuyer.value,
        features = listOf(
            stateValues.stringSearchByAnyData,
            stateValues.stringCart,
            localizedStringResource(254, "Orders"),
            localizedStringResource(177, "Notifications")
        )
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
    optionModeId in setOf(
        APP_MODE_STORE,
        APP_MODE_BUYER,
        APP_MODE_SUPPLIER,
        APP_MODE_MANUFACTURER
    )

internal fun AppConfiguration.availableAppModeOptions(currentModeId: Int): List<AppModeOptionUiModel> =
    appModeOptions().filter { option -> appModeIsAvailableInCurrentRelease(option.modeId, currentModeId) }

@Composable
internal fun AppConfiguration.AppModeFeatureChip(
    text: String,
    selected: Boolean
) {
    Text(
        text = text,
        color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
        fontSize = stateValues.smallTextSize,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (selected) stateValues.AccentColor else stateValues.DisabledColor.copy(alpha = 0.20f))
            .border(
                stateValues.unfocusedBorderWidth,
                if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor.copy(alpha = 0.55f),
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
internal fun AppConfiguration.AppModeSelectionCard(
    option: AppModeOptionUiModel,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .run { if (selected) this else foregroundTactileShadow(stateValues.cornerRadius, elevated = false) }
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (selected) stateValues.AccentColor.copy(alpha = 0.10f) else stateValues.BackgroundColor)
            .border(
                if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = if (selected) stateValues.AccentColor else stateValues.TextColor)
            ) { onClick() }
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(if (selected) stateValues.AccentColor.copy(alpha = 0.16f) else stateValues.DisabledColor.copy(alpha = 0.18f))
                    .border(
                        stateValues.unfocusedBorderWidth,
                        if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor.copy(alpha = 0.55f),
                        RoundedCornerShape(stateValues.cornerRadius)
                    ),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(40.dp),
                    url = option.iconPath,
                    fallbackRes = option.iconRes,
                    contentDescription = option.title,
                    tintColor = null
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    color = if (selected) stateValues.AccentColor else stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = option.subtitle,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (selected) {
                CpImage(
                    modifier = Modifier.size(22.dp),
                    url = stateValues.drawablePathIconCheck,
                    fallbackRes = stateValues.drawableResIconCheck.value,
                    contentDescription = localizedStringResource(1396, "Selected"),
                    tintColor = stateValues.AccentColor
                )
            }
        }

        Text(
            text = option.promise,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            option.features.take(2).forEach { chip ->
                AppModeFeatureChip(text = chip, selected = selected)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            option.features.drop(2).take(2).forEach { chip ->
                AppModeFeatureChip(text = chip, selected = selected)
            }
        }
    }
}

@Composable
fun AppConfiguration.MenuAppModeScreen() {
    val options = availableAppModeOptions(stateValues.appModeId)

    fun selectAppMode(modeId: Int) {
        if (stateValues.appModeId == modeId) return
        setAppMode(modeId)
        coroutineScope.launch {
            Navigation.Menu.clearLeft()
            Navigation.Menu.clearRight()
            Navigation.goMain(defaultMainScreenForAppMode(modeId))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenAppBarWidget(
            title = stateValues.stringAppMode,
            iconPath = stateValues.drawablePathIconSwitch,
            iconRes = stateValues.drawableResIconSwitch.value,
            onBack = {
                coroutineScope.launch {
                    Navigation.Menu.pop(stateValues.isNarrowScreen)
                }
            }
        )

        LazyColumn(
            state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.AppMode),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.78f),
            contentPadding = PaddingValues(
                start = stateValues.marginTextField,
                end = stateValues.marginTextField,
                top = stateValues.marginTextField,
                bottom = stateValues.screenHeight / 6
            ),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.10f))
                        .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CpImage(
                        modifier = Modifier.size(58.dp),
                        url = options.firstOrNull { it.modeId == stateValues.appModeId }?.iconPath ?: stateValues.drawablePathIconSwitch,
                        fallbackRes = options.firstOrNull { it.modeId == stateValues.appModeId }?.iconRes ?: stateValues.drawableResIconSwitch.value,
                        contentDescription = stateValues.stringAppMode,
                        tintColor = null
                    )
                    Text(
                        text = localizedStringResource(1397, "Choose how AITA behaves today"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                if (stateValues.isNarrowScreen) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        options.forEach { option ->
                            AppModeSelectionCard(
                                option = option,
                                selected = stateValues.appModeId == option.modeId,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectAppMode(option.modeId) }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        options.chunked(2).forEach { rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                            ) {
                                rowOptions.forEach { option ->
                                    AppModeSelectionCard(
                                        option = option,
                                        selected = stateValues.appModeId == option.modeId,
                                        modifier = Modifier.weight(1f),
                                        onClick = { selectAppMode(option.modeId) }
                                    )
                                }
                                if (rowOptions.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (stateValues.appModeId == APP_MODE_SUPPLIER || stateValues.appModeId == APP_MODE_MANUFACTURER) {
                item(key = "app-mode-supplier-workspace-overview") {
                    SupplierWorkspaceMenuTile()
                }
            }

        }
    }
}

@Composable
fun AppConfiguration.MenuAppLanguageScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenAppBarWidget(
            title = stateValues.stringAppLanguage,
            iconPath = stateValues.drawablePathIconAppLanguage,
            onBack = {
                coroutineScope.launch {
                    Navigation.Menu.pop(stateValues.isNarrowScreen)
                }
            }
        )

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
                    isActive = stateValues.appLanguage == "system"
                )
            }

            items(stateValues.globalAppConfiguration.languages) { language ->
                AppLanguageSettingsItemWidget(
                    language = language.language,
                    name = language.name.extractLocalizedString(stateValues.appLanguage) ?: language.language,
                    flagDrawablePath = language.flagDrawablePath,
                    flagDrawableRes = language.mapIconRes(),
                    isActive = stateValues.appLanguage == language.language
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
    val sales = scopedTransactions.analyticsTyped("purchase")
    val returns = scopedTransactions.analyticsTyped("return")
    val supply = scopedTransactions.analyticsTyped("accept")
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
        row(localizedStringResource(694, "Gross sales"), sales.sumOf { it.analyticsTotal() }.money(reportCurrency)),
        row(localizedStringResource(695, "Returns amount"), returns.sumOf { it.analyticsTotal() }.money(reportCurrency)),
        row(localizedStringResource(706, "Transactions"), scopedTransactions.size.toString()),
        row(stateValues.stringItems, scopedTransactions.flatMap { it.goodsInTransaction }.sumOf { it.quantity }.cleanNumber()),
        row(stateValues.stringCash, scopedTransactions.sumOf { it.paidCash }.money(reportCurrency)),
        row(localizedStringResource(357, "Cashless"), scopedTransactions.sumOf { it.paidCard }.money(reportCurrency))
    )

    fun transactionRows(transactions: List<TransactionDataModel>): List<AnalyticsReportRowDataModel> {
        val total = transactions.sumOf { it.analyticsTotal() }
        val cash = transactions.sumOf { it.paidCash }
        val cashless = transactions.sumOf { it.paidCard }
        val debt = (total - cash - cashless).coerceAtLeast(0.0)
        return listOf(
            row(localizedStringResource(706, "Transactions"), transactions.size.toString()),
            row(stateValues.stringTotal, total.money(reportCurrency)),
            row(stateValues.stringCash, cash.money(reportCurrency)),
            row(localizedStringResource(357, "Cashless"), cashless.money(reportCurrency)),
            row(stateValues.stringDebt, debt.money(reportCurrency)),
            row(stateValues.stringItems, transactions.flatMap { it.goodsInTransaction }.sumOf { it.quantity }.cleanNumber())
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
            row(stateValues.stringItems, stock.size.toString()),
            row(localizedStringResource(280, "Active items"), stock.count { it.isActive }.toString()),
            row(stateValues.stringBatches, batches.size.toString()),
            row(localizedStringResource(284, "Active batches"), batches.count { it.isActive }.toString()),
            row(localizedStringResource(683, "Inventory value at sale price"), (dashboard?.stockValueAtSalePrice ?: 0.0).money(reportCurrency)),
            row(localizedStringResource(684, "Inventory value at supply cost"), (dashboard?.stockValueAtSupplyPrice ?: 0.0).money(reportCurrency)),
            row(localizedStringResource(685, "Low stock items"), (dashboard?.lowStockItemCount ?: 0).toString()),
            row(localizedStringResource(687, "Expired batches"), (dashboard?.expiredBatchCount ?: 0).toString())
        )
        MenuAnalyticsTab.Suppliers -> listOf(
            row(stateValues.stringSuppliers, suppliers.count { it.isActive }.toString()),
            row(localizedStringResource(706, "Transactions"), supply.size.toString()),
            row(localizedStringResource(707, "Accepted goods value"), (dashboard?.supplyCost ?: supply.sumOf { it.analyticsTotal() }).money(reportCurrency))
        )
        MenuAnalyticsTab.Workers -> listOf(
            row(stateValues.stringWorkers, workers.count { it.isActive }.toString()),
            row(localizedStringResource(654, "Admin"), workers.count { it.roleId == WORKER_ROLE_ADMIN || it.roleId == WORKER_ROLE_OWNER }.toString()),
            row(localizedStringResource(655, "Worker"), workers.count { it.roleId == WORKER_ROLE_STANDARD }.toString()),
            row(localizedStringResource(706, "Transactions"), scopedTransactions.size.toString())
        )
        MenuAnalyticsTab.CashRegister -> listOf(
            row(localizedStringResource(327, "Current amount"), (cashRegister?.currentAmount ?: 0.0).money(reportCurrency)),
            row(localizedStringResource(417, "Events"), cashRegisterEvents.size.toString()),
            row(stateValues.stringSale, sales.sumOf { it.paidCash }.money(reportCurrency)),
            row(stateValues.stringReturn, returns.sumOf { it.paidCash }.money(reportCurrency)),
            row(localizedStringResource(662, "Cash extractions"), cashRegisterEvents.filter { it.type == CASH_REGISTER_EVENT_EXTRACTION }.sumOf { it.amount }.money(reportCurrency))
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
    val pdfBytes = remember(snapshot) { snapshot.buildAnalyticsReportPdfBytes() }
    val saveNotConfiguredText = localizedStringResource(1267, "PDF export is not configured for this platform")
    val shareNotConfiguredText = localizedStringResource(1268, "PDF sharing is not configured for this platform")
    val printNotConfiguredText = localizedStringResource(1269, "Paper document printing is not configured for this platform")
    val saveSuccessText = localizedStringResource(1246, "Report PDF saved")
    val shareSuccessText = localizedStringResource(1247, "Report PDF shared")
    val printSuccessText = localizedStringResource(1248, "Report opened for printing")

    AitaBottomSheet(
        title = localizedStringResource(1239, "Analytics report"),
        iconPath = stateValues.drawablePathIconAnalyticsReport,
        iconRes = stateValues.drawableResIconAnalyticsReport.value,
        onDismiss = onDismiss
    ) {
        MessageText(
            modifier = Modifier.fillMaxWidth(),
            text = localizedStringResource(1240, "Printable summary"),
            subText = localizedStringResource(1249, "Transaction receipts use ESC/POS thermal printers. Analytics reports use A4 paper printing."),
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
                    onClick = {
                        coroutineScope.launch {
                            receiptActionNotification(savePdfDocument(fileName, pdfBytes, saveNotConfiguredText), saveSuccessText)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1244, "Share report PDF"),
                    iconPath = stateValues.drawablePathIconShare,
                    iconRes = stateValues.drawableResIconShare.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            receiptActionNotification(sharePdfDocument(fileName, pdfBytes, shareNotConfiguredText), shareSuccessText)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1245, "Print report"),
                    iconPath = stateValues.drawablePathIconDevices,
                    iconRes = stateValues.drawableResIconDevices.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            receiptActionNotification(printPdfDocument(fileName, pdfBytes, printNotConfiguredText), printSuccessText)
                        }
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1243, "Save report PDF"),
                    iconPath = stateValues.drawablePathIconReceipt,
                    iconRes = stateValues.drawableResIconReceipt.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            receiptActionNotification(savePdfDocument(fileName, pdfBytes, saveNotConfiguredText), saveSuccessText)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1244, "Share report PDF"),
                    iconPath = stateValues.drawablePathIconShare,
                    iconRes = stateValues.drawableResIconShare.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            receiptActionNotification(sharePdfDocument(fileName, pdfBytes, shareNotConfiguredText), shareSuccessText)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1245, "Print report"),
                    iconPath = stateValues.drawablePathIconDevices,
                    iconRes = stateValues.drawableResIconDevices.value,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            receiptActionNotification(printPdfDocument(fileName, pdfBytes, printNotConfiguredText), printSuccessText)
                        }
                    }
                )
            }
        }
    }
}


@Composable
fun AppConfiguration.MenuAnalyticsScreen() {
    var showAnalyticsReportSheet by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

        val activeStoreId = stateValues.activeStoreId

        LaunchedEffect(activeStoreId) {
            activeStoreId?.let { storeId ->
                getTransactions(storeId)
                getStock(storeId)
                getStockBatches(storeId)
                getSuppliers()
                getCashRegister(storeId)
                getStoreWorkers(storeId)
                getIncomingWorkerRequests(storeId)
                getMyWorkerMemberships()
            }
        }

        val transactionsPayload by transactionsState.payload.collectAsState()
        val transactions = transactionsPayload.orEmpty()
        val cashRegisterPayload by cashRegisterState.payload.collectAsState()
        val cashRegisterEventsPayload by cashRegisterEventsState.payload.collectAsState()
        val cashRegister = cashRegisterPayload
        val cashRegisterEvents = cashRegisterEventsPayload.orEmpty()
        val serverAnalyticsPayload by storeAnalyticsDashboardState.payload.collectAsState()

        val initialPeriod = remember { transactionHistoryPresetDates("30") }
        var periodPresetId by rememberSaveable { mutableStateOf("30") }
        var startDateText by rememberSaveable { mutableStateOf(initialPeriod.first) }
        var endDateText by rememberSaveable { mutableStateOf(initialPeriod.second) }
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

        var analyticsScopeType by rememberSaveable { mutableStateOf(ANALYTICS_SCOPE_ALL) }
        var selectedAnalyticsGoodsItemId by rememberSaveable { mutableStateOf("") }
        var selectedAnalyticsSupplierId by rememberSaveable { mutableStateOf("") }
        var selectedAnalyticsCategoryId by rememberSaveable { mutableStateOf("") }

        val stockForAnalytics = stateValues.stock.orEmpty()
        val stockBatchesForAnalytics = stateValues.stockBatches.orEmpty()
        val suppliersForAnalytics = stateValues.suppliers.orEmpty().filter { it.isActive }
        val categoriesForAnalytics = stateValues.goodsCategories.orEmpty()

        val analyticsGoodsItemFilter = selectedAnalyticsGoodsItemId.takeIf { analyticsScopeType == ANALYTICS_SCOPE_GOODS_ITEM && it.isNotBlank() }
        val analyticsSupplierFilter = selectedAnalyticsSupplierId.takeIf { analyticsScopeType == ANALYTICS_SCOPE_SUPPLIER && it.isNotBlank() }
        val analyticsCategoryFilter = selectedAnalyticsCategoryId.takeIf { analyticsScopeType == ANALYTICS_SCOPE_CATEGORY && it.isNotBlank() }

        val startMillis = stockDateInputTextToMillis(startDateText)
        val endExclusiveMillis = stockDateInputTextToLocalDate(endDateText)
            ?.let { transactionHistoryPlusDays(it, 1).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() }

        val scopedTransactions = remember(transactions, activeStoreId, periodPresetId, startDateText, endDateText, stockForAnalytics, stockBatchesForAnalytics, analyticsGoodsItemFilter, analyticsSupplierFilter, analyticsCategoryFilter) {
            val periodTransactions = transactions
                .filter { transaction -> activeStoreId == null || transaction.storeId == activeStoreId }
                .filter { transaction ->
                    if (periodPresetId == "all") {
                        true
                    } else {
                        val afterStart = startMillis?.let { transaction.timeMillis >= it } ?: true
                        val beforeEnd = endExclusiveMillis?.let { transaction.timeMillis < it } ?: true
                        afterStart && beforeEnd
                    }
                }
            analyticsScopedTransactions(
                transactions = periodTransactions,
                stock = stockForAnalytics,
                batches = stockBatchesForAnalytics,
                goodsItemIdFilter = analyticsGoodsItemFilter,
                supplierIdFilter = analyticsSupplierFilter,
                categoryIdFilter = analyticsCategoryFilter
            )
        }

        val scopedCashRegisterEvents = remember(cashRegisterEvents, activeStoreId, periodPresetId, startDateText, endDateText) {
            cashRegisterEvents
                .filter { event -> activeStoreId == null || event.storeId == activeStoreId }
                .filter { event ->
                    if (periodPresetId == "all") {
                        true
                    } else {
                        val afterStart = startMillis?.let { event.timeMillis >= it } ?: true
                        val beforeEnd = endExclusiveMillis?.let { event.timeMillis < it } ?: true
                        afterStart && beforeEnd
                    }
                }
        }

        val analyticsStartMillis = if (periodPresetId == "all") 0L else startMillis ?: 0L
        val analyticsEndMillis = if (periodPresetId == "all") Long.MAX_VALUE else endExclusiveMillis ?: Long.MAX_VALUE

        LaunchedEffect(activeStoreId, analyticsStartMillis, analyticsEndMillis, analyticsGoodsItemFilter, analyticsSupplierFilter, analyticsCategoryFilter) {
            activeStoreId?.takeIf { currentUserCanViewAnalytics(it) }?.let { storeId ->
                getStoreAnalytics(
                    storeId = storeId,
                    startMillis = analyticsStartMillis,
                    endMillisExclusive = analyticsEndMillis,
                    goodsItemIdFilter = analyticsGoodsItemFilter,
                    supplierIdFilter = analyticsSupplierFilter,
                    categoryIdFilter = analyticsCategoryFilter
                )
            }
        }

        val localAnalyticsDashboard = remember(
            activeStoreId,
            analyticsStartMillis,
            analyticsEndMillis,
            transactions,
            stockForAnalytics,
            stockBatchesForAnalytics,
            cashRegister?.currencyCode,
            analyticsGoodsItemFilter,
            analyticsSupplierFilter,
            analyticsCategoryFilter
        ) {
            activeStoreId?.let { storeId ->
                buildStoreAnalyticsDashboard(
                    storeId = storeId,
                    startMillis = analyticsStartMillis,
                    endMillisExclusive = analyticsEndMillis,
                    transactions = transactions,
                    stock = stockForAnalytics,
                    batches = stockBatchesForAnalytics,
                    fallbackCurrencyCode = cashRegister?.currencyCode?.takeIf { it.isNotBlank() } ?: currentAnalyticsCurrencyCode(),
                    goodsItemIdFilter = analyticsGoodsItemFilter,
                    supplierIdFilter = analyticsSupplierFilter,
                    categoryIdFilter = analyticsCategoryFilter
                )
            }
        }

        val analyticsDashboard = serverAnalyticsPayload
            ?.takeIf { dashboard ->
                dashboard.storeId == activeStoreId &&
                        dashboard.startMillis == analyticsStartMillis &&
                        dashboard.endMillisExclusive == analyticsEndMillis &&
                        dashboard.goodsItemIdFilter.orEmpty() == analyticsGoodsItemFilter.orEmpty() &&
                        dashboard.supplierIdFilter.orEmpty() == analyticsSupplierFilter.orEmpty() &&
                        dashboard.categoryIdFilter.orEmpty() == analyticsCategoryFilter.orEmpty()
            }
            ?: localAnalyticsDashboard

        val selectedTabContent = tabRowWidget(
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                .padding(stateValues.marginTextField),
            tabs = listOf(
                TabContent(MenuAnalyticsTab.Sales.id, stateValues.stringSale),
                TabContent(MenuAnalyticsTab.Returns.id, stateValues.stringReturn),
                TabContent(MenuAnalyticsTab.Acceptance.id, stateValues.stringSupply),
                TabContent(MenuAnalyticsTab.Stock.id, stateValues.stringStock),
                TabContent(MenuAnalyticsTab.Suppliers.id, stateValues.stringSuppliers),
                TabContent(MenuAnalyticsTab.Workers.id, stateValues.stringWorkers),
                TabContent(MenuAnalyticsTab.CashRegister.id, localizedStringResource(256, "Cash registers"))
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
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                .padding(horizontal = stateValues.marginTextField),
            tabs = periodOptions.map { option ->
                TabContent(option.first, option.second) { selectedPeriodId ->
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
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
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
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
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
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                .padding(horizontal = stateValues.marginTextField),
            tabs = analyticsScopeOptions.map { option ->
                TabContent(option.first, option.second) { selectedScopeId ->
                    analyticsScopeType = selectedScopeId
                }
            },
            selectedIndexInitial = analyticsScopeType,
            textSize = stateValues.smallTextSize
        )

        val analyticsGoodsItemDomains = remember(stockForAnalytics, stateValues.appLanguage) {
            stockForAnalytics
                .sortedBy { item -> item.name.extractLocalizedString(stateValues.appLanguage) ?: item.id }
                .map { item ->
                    SelectableDomain(
                        id = item.id,
                        displayId = item.allBarcodeValues().firstOrNull().orEmpty().ifBlank { item.id.take(8) }.toLocalizedSingleMain(),
                        name = item.name.ifEmpty { listOf(LocalizedStringDataModel("main", item.id.take(8))) },
                        iconPath = stateValues.drawablePathIconStock,
                        iconRes = stateValues.drawableResIconStock.value
                    )
                }
        }
        val analyticsSupplierDomains = remember(suppliersForAnalytics, stateValues.appLanguage) {
            suppliersForAnalytics
                .sortedBy { supplier -> supplier.name.extractLocalizedString(stateValues.appLanguage) ?: supplier.id }
                .map { supplier ->
                    SelectableDomain(
                        id = supplier.id,
                        displayId = supplier.id.take(8).toLocalizedSingleMain(),
                        name = supplier.name.ifEmpty { listOf(LocalizedStringDataModel("main", supplier.id.take(8))) },
                        iconPath = stateValues.drawablePathIconSuppliers,
                        iconRes = stateValues.drawableResIconSuppliers.value
                    )
                }
        }
        val analyticsCategoryDomains = remember(categoriesForAnalytics, stateValues.appLanguage) {
            categoriesForAnalytics
                .sortedBy { category -> category.name.extractLocalizedString(stateValues.appLanguage) ?: category.id }
                .map { category ->
                    SelectableDomain(
                        id = category.id,
                        displayId = category.id.take(8).toLocalizedSingleMain(),
                        name = category.name.ifEmpty { listOf(LocalizedStringDataModel("main", category.id.take(8))) },
                        iconPath = stateValues.drawablePathIconGoodsCategories,
                        iconRes = stateValues.drawableResIconGoodsCategories.value
                    )
                }
        }

        when (analyticsScopeType) {
            ANALYTICS_SCOPE_GOODS_ITEM -> {
                if (analyticsGoodsItemDomains.isNotEmpty()) {
                    val selectedInitial = selectedAnalyticsGoodsItemId.takeIf { selected -> analyticsGoodsItemDomains.any { it.id == selected } }
                        ?: analyticsGoodsItemDomains.first().id
                    val selector = dropdownListWidget(
                        modifier = Modifier
                            .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                            .padding(horizontal = stateValues.marginTextField, vertical = 4.dp),
                        titleText = localizedStringResource(1168, "Select exact analytics target"),
                        domains = analyticsGoodsItemDomains,
                        selectedInitial = selectedInitial,
                        showId = true,
                        showName = true,
                        search = Triple(stateValues.stringSearchByAnyData, NavigationScreenModel.Menu.Analytics, "analytics_scope_goods_item_search")
                    )
                    LaunchedEffect(selector.selectedId) { selectedAnalyticsGoodsItemId = selector.selectedId }
                }
            }
            ANALYTICS_SCOPE_SUPPLIER -> {
                if (analyticsSupplierDomains.isNotEmpty()) {
                    val selectedInitial = selectedAnalyticsSupplierId.takeIf { selected -> analyticsSupplierDomains.any { it.id == selected } }
                        ?: analyticsSupplierDomains.first().id
                    val selector = dropdownListWidget(
                        modifier = Modifier
                            .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                            .padding(horizontal = stateValues.marginTextField, vertical = 4.dp),
                        titleText = localizedStringResource(1168, "Select exact analytics target"),
                        domains = analyticsSupplierDomains,
                        selectedInitial = selectedInitial,
                        showId = true,
                        showName = true,
                        search = Triple(stateValues.stringSearchByAnyData, NavigationScreenModel.Menu.Analytics, "analytics_scope_supplier_search")
                    )
                    LaunchedEffect(selector.selectedId) { selectedAnalyticsSupplierId = selector.selectedId }
                }
            }
            ANALYTICS_SCOPE_CATEGORY -> {
                if (analyticsCategoryDomains.isNotEmpty()) {
                    val selectedInitial = selectedAnalyticsCategoryId.takeIf { selected -> analyticsCategoryDomains.any { it.id == selected } }
                        ?: analyticsCategoryDomains.first().id
                    val selector = dropdownListWidget(
                        modifier = Modifier
                            .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
                            .padding(horizontal = stateValues.marginTextField, vertical = 4.dp),
                        titleText = localizedStringResource(1168, "Select exact analytics target"),
                        domains = analyticsCategoryDomains,
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

        val selectedTab = MenuAnalyticsTab.fromId(selectedTabContent.id)
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

        if (showAnalyticsReportSheet) {
            AnalyticsReportBottomSheet(
                snapshot = analyticsReportSnapshot,
                onDismiss = { showAnalyticsReportSheet = false }
            )
        }

        when (selectedTab) {
            MenuAnalyticsTab.Sales -> {
                MenuAnalyticsTransactionScreen(
                    title = stateValues.stringSale,
                    transactionType = "purchase",
                    transactions = scopedTransactions,
                    currencyCode = currencyCode,
                    emptyText = localizedStringResource(258, "No sales in this period"),
                    analyticsDashboard = analyticsDashboard
                )
            }

            MenuAnalyticsTab.Returns -> {
                MenuAnalyticsTransactionScreen(
                    title = stateValues.stringReturn,
                    transactionType = "return",
                    transactions = scopedTransactions,
                    currencyCode = currencyCode,
                    emptyText = localizedStringResource(275, "No returns in this period"),
                    analyticsDashboard = analyticsDashboard
                )
            }

            MenuAnalyticsTab.Acceptance -> {
                MenuAnalyticsTransactionScreen(
                    title = stateValues.stringSupply,
                    transactionType = "accept",
                    transactions = scopedTransactions,
                    currencyCode = currencyCode,
                    emptyText = localizedStringResource(276, "No supply transactions in this period"),
                    analyticsDashboard = analyticsDashboard
                )
            }

            MenuAnalyticsTab.Stock -> MenuAnalyticsStockScreen(analyticsDashboard)

            MenuAnalyticsTab.Suppliers -> {
                MenuAnalyticsSuppliersScreen(
                    transactions = scopedTransactions,
                    dashboard = analyticsDashboard,
                    currencyCode = currencyCode
                )
            }

            MenuAnalyticsTab.Workers -> {
                MenuAnalyticsWorkersScreen(
                    workers = workers,
                    transactions = scopedTransactions,
                    currencyCode = currencyCode
                )
            }

            MenuAnalyticsTab.CashRegister -> {
                MenuAnalyticsCashRegisterScreen(
                    currentAmount = cashRegister?.currentAmount ?: 0.0,
                    events = scopedCashRegisterEvents,
                    currencyCode = currencyCode
                )
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
    analyticsDashboard: StoreAnalyticsDashboardDataModel? = null
) {
    val typedTransactions = remember(transactions, transactionType) {
        transactions.filter { it.type == transactionType }
    }

    val totalCash = remember(typedTransactions) { typedTransactions.sumOf { it.paidCash } }
    val totalCard = remember(typedTransactions) { typedTransactions.sumOf { it.paidCard } }
    val total = remember(typedTransactions) { typedTransactions.sumOf { tx -> tx.goodsInTransaction.sumOf { it.quantity * it.pricePerUnit } } }
    val paidTotal = totalCash + totalCard
    val debtTotal = (total - paidTotal).coerceAtLeast(0.0)
    val average = if (typedTransactions.isNotEmpty()) total / typedTransactions.size else 0.0
    val totalGoodsQuantity = remember(typedTransactions) {
        typedTransactions.flatMap { it.goodsInTransaction }.sumOf { it.quantity }
    }
    val averageItems = if (typedTransactions.isNotEmpty()) totalGoodsQuantity / typedTransactions.size else 0.0
    val historyRows = remember(typedTransactions) { typedTransactions.toMonthlyAnalyticsHistoryRows() }

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

    LazyColumn(
        state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Analytics, "transaction_$transactionType"),
        modifier = Modifier
            .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
            .padding(stateValues.marginTextField),
        contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
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
                        AnalyticsSummaryCardData(title = stateValues.stringCash, value = totalCash.money(currencyCode)),
                        AnalyticsSummaryCardData(title = stateValues.stringCashless, value = totalCard.money(currencyCode)),
                        AnalyticsSummaryCardData(title = localizedStringResource(676, "Debt amount"), value = debtTotal.money(currencyCode)),
                        AnalyticsSummaryCardData(title = localizedStringResource(358, "Transactions"), value = typedTransactions.size.toString()),
                        AnalyticsSummaryCardData(title = localizedStringResource(359, "Average transaction"), value = average.money(currencyCode)),
                        AnalyticsSummaryCardData(title = localizedStringResource(710, "Average items"), value = averageItems.cleanNumber()),
                        AnalyticsSummaryCardData(title = stateValues.stringItems, value = totalGoodsQuantity.cleanNumber())
                    )
                }
            )
        }

        if (transactionType == "purchase" && dashboard != null) {
            item {
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

            item {
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

            item {
                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                AnalyticsBucketSection(
                    title = localizedStringResource(691, "Sales by day"),
                    buckets = dashboard.salesByDay,
                    currencyCode = dashboard.currencyCode.ifBlank { currencyCode }
                )
            }

            item {
                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                AnalyticsBucketSection(
                    title = localizedStringResource(692, "Sales by hour"),
                    buckets = dashboard.salesByHour.sortedByDescending { it.amount }.take(8),
                    currencyCode = dashboard.currencyCode.ifBlank { currencyCode }
                )
            }
        }

        if (transactionType == "return" && dashboard != null) {
            item {
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

            item {
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

        item {
            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
            Text(
                text = localizedStringResource(257, "History"),
                color = stateValues.TextColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = stateValues.marginTextField)
            )
        }

        if (typedTransactions.isEmpty()) {
            item {
                MessageText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = stateValues.marginTextFieldGroup),
                    emptyText
                )
            }
        } else {
            items(historyRows) { row ->
                AnalyticsHistoryRowWidget(row = row, currencyCode = currencyCode)
                Spacer(modifier = Modifier.height(stateValues.marginTextField))
            }
        }
    }
}

@Composable
internal fun AppConfiguration.MenuAnalyticsStockScreen(
    dashboard: StoreAnalyticsDashboardDataModel?
) {
    val stock = stateValues.stock.orEmpty()
    val batches = stateValues.stockBatches.orEmpty()

    val activeItems = stock.count { it.isActive }
    val inactiveItems = stock.size - activeItems
    val quickItems = stock.count { it.isQuickItem }
    val activeBatches = batches.count { it.isActive }
    val inactiveBatches = batches.size - activeBatches
    val currencyCode = dashboard?.currencyCode?.takeIf { it.isNotBlank() } ?: currentAnalyticsCurrencyCode()

    LazyColumn(
        state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Analytics, "stock"),
        modifier = Modifier
            .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
            .padding(stateValues.marginTextField),
        contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
    ) {
        item {
            Text(
                text = stateValues.stringStock,
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = stateValues.marginTextField)
            )
        }

        item {
            AnalyticsCardsGrid(
                cards = listOf(
                    AnalyticsSummaryCardData(title = stateValues.stringItems, value = stock.size.toString(), subtitle = localizedStringResource(282, "All stock items")),
                    AnalyticsSummaryCardData(title = localizedStringResource(280, "Active items"), value = activeItems.toString()),
                    AnalyticsSummaryCardData(title = localizedStringResource(281, "Inactive items"), value = inactiveItems.toString()),
                    AnalyticsSummaryCardData(title = stateValues.stringQuick, value = quickItems.toString(), subtitle = localizedStringResource(283, "Quick-sale items")),
                    AnalyticsSummaryCardData(title = stateValues.stringBatches, value = batches.size.toString()),
                    AnalyticsSummaryCardData(title = localizedStringResource(284, "Active batches"), value = activeBatches.toString()),
                    AnalyticsSummaryCardData(title = localizedStringResource(285, "Inactive batches"), value = inactiveBatches.toString()),
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

        dashboard?.slowMovingItems?.takeIf { it.isNotEmpty() }?.let { items ->
            item {
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

@Composable
internal fun AppConfiguration.MenuAnalyticsSuppliersScreen(
    transactions: List<TransactionDataModel>,
    dashboard: StoreAnalyticsDashboardDataModel?,
    currencyCode: String
) {
    val supplierMap = stateValues.suppliers.orEmpty().associateBy { it.id }
    val acceptanceTransactions = remember(transactions) { transactions.filter { it.type == "accept" } }
    val supplierRows = remember(acceptanceTransactions, supplierMap, stateValues.appLanguage) {
        acceptanceTransactions
            .flatMap { tx -> tx.goodsInTransaction.map { line -> tx to line } }
            .groupBy { (_, line) -> line.supplierIdText?.takeIf { it.isNotBlank() } ?: line.supplierId?.toString().orEmpty().ifBlank { "unknown" } }
            .map { (supplierId, pairs) ->
                val supplier = supplierMap[supplierId]
                AnalyticsRankedItemDataModel(
                    id = supplierId,
                    name = supplier?.name ?: listOf(LocalizedStringDataModel("main", if (supplierId == "unknown") localizedStringResource(156, "Not specified") else supplierId)),
                    subtitle = "${localizedStringResource(706, "Transactions")}: ${pairs.map { it.first.id }.distinct().size}",
                    quantity = pairs.sumOf { it.second.quantity },
                    transactionCount = pairs.map { it.first.id }.distinct().size,
                    amount = pairs.sumOf { it.second.quantity * it.second.pricePerUnit },
                    currencyCode = currencyCode
                )
            }
            .sortedByDescending { it.amount }
    }

    LazyColumn(
        state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Analytics, "suppliers"),
        modifier = Modifier
            .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
            .padding(stateValues.marginTextField),
        contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
    ) {
        item {
            Text(
                text = stateValues.stringSuppliers,
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = stateValues.marginTextField)
            )
        }

        item {
            AnalyticsCardsGrid(
                cards = listOf(
                    AnalyticsSummaryCardData(title = localizedStringResource(300, "Acceptance total"), value = (dashboard?.supplyCost ?: supplierRows.sumOf { it.amount }).money(currencyCode)),
                    AnalyticsSummaryCardData(title = stateValues.stringSuppliers, value = supplierRows.size.toString()),
                    AnalyticsSummaryCardData(title = stateValues.stringItems, value = supplierRows.sumOf { it.quantity }.cleanNumber()),
                    AnalyticsSummaryCardData(title = localizedStringResource(358, "Transactions"), value = acceptanceTransactions.size.toString())
                )
            )
        }

        item {
            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
            AnalyticsRankedItemsSection(
                title = localizedStringResource(703, "Top performers"),
                items = supplierRows,
                valueTitle = localizedStringResource(300, "Acceptance total"),
                currencyCode = currencyCode,
                valueSelector = { it.amount },
                subtitleSelector = { item -> "${localizedStringResource(271, "Quantity")}: ${item.quantity.cleanNumber()} · ${item.subtitle}" }
            )
        }
    }
}

@Composable
internal fun AppConfiguration.MenuAnalyticsWorkersScreen(
    workers: List<StoreWorkerDataModel>,
    transactions: List<TransactionDataModel>,
    currencyCode: String
) {
    val salesByWorkshift = remember(transactions) {
        transactions
            .filter { it.type == "purchase" }
            .groupBy { it.workshiftId }
            .map { (workshiftId, txs) ->
                AnalyticsRankedItemDataModel(
                    id = workshiftId.toString(),
                    name = listOf(LocalizedStringDataModel("main", "${localizedStringResource(661, "Active workshift")} #$workshiftId")),
                    subtitle = "${localizedStringResource(706, "Transactions")}: ${txs.size}",
                    quantity = txs.flatMap { it.goodsInTransaction }.sumOf { it.quantity },
                    transactionCount = txs.size,
                    amount = txs.sumOf { tx -> tx.goodsInTransaction.sumOf { it.quantity * it.pricePerUnit } },
                    currencyCode = currencyCode
                )
            }
            .sortedByDescending { it.amount }
            .take(10)
    }

    LazyColumn(
        state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Analytics, "workers"),
        modifier = Modifier
            .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
            .padding(stateValues.marginTextField),
        contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
    ) {
        item {
            Text(
                text = stateValues.stringWorkers,
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = stateValues.marginTextField)
            )
        }

        item {
            AnalyticsCardsGrid(
                cards = listOf(
                    AnalyticsSummaryCardData(title = localizedStringResource(429, "Active workers"), value = workers.count { it.isActive }.toString(), subtitle = localizedStringResource(430, "Employees connected to this store")),
                    AnalyticsSummaryCardData(title = localizedStringResource(431, "Admins"), value = workers.count { it.roleId == WORKER_ROLE_ADMIN }.toString()),
                    AnalyticsSummaryCardData(title = localizedStringResource(432, "Standard workers"), value = workers.count { it.roleId == WORKER_ROLE_STANDARD }.toString()),
                    AnalyticsSummaryCardData(title = localizedStringResource(358, "Transactions"), value = transactions.count { it.type == "purchase" }.toString()),
                    AnalyticsSummaryCardData(title = localizedStringResource(303, "Revenue / worker"), value = transactions.filter { it.type == "purchase" }.sumOf { tx -> tx.goodsInTransaction.sumOf { it.quantity * it.pricePerUnit } }.money(currencyCode), subtitle = localizedStringResource(302, "Use workshifts, sales per worker, and salary here"))
                )
            )
        }

        item {
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

@Composable
internal fun AppConfiguration.MenuAnalyticsCashRegisterScreen(
    currentAmount: Double,
    events: List<CashRegisterEventDataModel>,
    currencyCode: String
) {
    val saleCashTotal = remember(events) {
        events.filter { it.type == CASH_REGISTER_EVENT_SALE_CASH_IN }.sumOf { it.amount }
    }
    val returnCashTotal = remember(events) {
        events.filter { it.type == CASH_REGISTER_EVENT_RETURN_CASH_OUT }.sumOf { it.amount }
    }
    val extractedTotal = remember(events) {
        events.filter { it.type == CASH_REGISTER_EVENT_EXTRACTION }.sumOf { it.amount }
    }

    var extractionAmountText by rememberSaveable { mutableStateOf("") }
    var extractionNoteLocalized by remember { mutableStateOf(emptyLocalizedItemForCurrentLanguage()) }
    val canExtract = currentUserCanExtractCashRegister(stateValues.activeStoreId)
    val extractionAmount = extractionAmountText.toMoneyDouble()

    LazyColumn(
        state = rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Analytics, "cash_register"),
        modifier = Modifier
            .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
            .padding(stateValues.marginTextField),
        contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
    ) {
        item {
            Text(
                text = localizedStringResource(256, "Cash registers"),
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = stateValues.marginTextField)
            )
        }

        item {
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

        item {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                    verticalAlignment = Alignment.Top
                ) {
                    SimpleTextInput(
                        modifier = Modifier.weight(1f),
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
                        modifier = Modifier.weight(1f),
                        enabled = extractionAmount > 0.0 && extractionAmount <= currentAmount + 0.01,
                        text = localizedStringResource(440, "Extract"),
                        iconPath = stateValues.drawablePathIconCheck,
                        confirmationRequired = true,
                        onClick = {
                            stateValues.activeStoreId?.let { storeId ->
                                extractCashRegister(
                                    CashRegisterExtractionRequestDataModel(
                                        storeId = storeId,
                                        amount = extractionAmount,
                                        note = extractionNoteLocalized.toStoredLocalizedNoteOrNull(),
                                        timeMillis = getCurrentTimeMillis()
                                    )
                                ) {
                                    extractionAmountText = ""
                                    extractionNoteLocalized = emptyLocalizedItemForCurrentLanguage()
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

        item {
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
            item {
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
            .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.8f)
            .padding(horizontal = stateValues.marginTextField),
        tabs = presets.map { (preset, title) ->
            TabContent(preset.id, title) { selectedPresetId ->
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
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenAppBarWidget(
            title = stateValues.stringWorkers,
            iconPath = stateValues.drawablePathIconWorkers,
            onBack = {
                coroutineScope.launch {
                    Navigation.Menu.pop(stateValues.isNarrowScreen)
                }
            }
        )

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

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

    stateValues.globalAppConfiguration.languages.forEach { language ->
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                val selectedCountry = stateValues.globalAppConfiguration.countries.run {
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

                val companyFormDropdownListContent = if (!isBranchEditor) {
                    dropdownListWidget(
                        titleText = stateValues.stringCompanyForm,
                        domains = stateValues.globalAppConfiguration.companyForms.map {
                            SelectableDomain(
                                id = it.id,
                                displayId = it.name,
                                name = it.name,
                                iconPath = null,
                                iconRes = null
                            )
                        },
                        showName = false,
                        selectedInitial = editedStore?.companyForms?.takeIf { it.isNotEmpty() }?.first()?.id
                    )
                } else null

                if (!isBranchEditor) {
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                    val legalFormat = stateValues.globalAppConfiguration.legalIdFormatForCountry(selectedCountry.locale)
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

                        val companyForm = stateValues.globalAppConfiguration.companyForms
                            .find { it.id == companyFormDropdownListContent?.selectedId }
                            ?: stateValues.globalAppConfiguration.companyForms.firstOrNull()

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
                            countryLocales = listOf(selectedCountry.locale),
                            createdAt = editedStore?.createdAt ?: 0L,
                            branches = editedStore?.branches.orEmpty()
                        )
                    }

                    StoreAddressInlineValidationMessage(addressPickerContent.state)

                    actionButton(
                        text = if (editedStore != null) stateValues.stringEditStore else stateValues.stringAddStore,
                        enabled = stateValues.latestNotification == null
                    ) {
                        softKeyboardController?.hide()
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
                            addressPickerContent.state.requireSuggestionSelection(addressSelectionRequiredText)
                            addressTextFieldContent.checkContentValidity()
                            return@actionButton
                        }
                        addressPickerContent.state.clearValidation()

                        if (addressTextFieldContent.isContentValid && phoneNumberTextFieldContent.isContentValid && emailTextFieldContent.isContentValid && legalIdTextFieldContent.isContentValid) {
                            val body = buildStoreModel(verifiedLocation)
                            if (editedStore != null) {
                                updateStore(body) {
                                    coroutineScope.launch {
                                        Navigation.Menu.pop()
                                        clearStoreEditorState()
                                    }
                                }
                            } else {
                                addStore(body) {
                                    coroutineScope.launch {
                                        Navigation.Menu.pop()
                                        clearStoreEditorState()
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
                            countryLocales = listOf(selectedCountry.locale),
                            createdAt = editedStore?.createdAt ?: 0L,
                            branches = emptyList()
                        )
                    }

                    StoreAddressInlineValidationMessage(addressPickerContent.state)

                    actionButton(
                        text = if (editedStore != null) localizedStringResource(534, "Edit branch") else localizedStringResource(533, "Add branch"),
                        enabled = stateValues.latestNotification == null
                    ) {
                        softKeyboardController?.hide()
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
                            addressPickerContent.state.requireSuggestionSelection(addressSelectionRequiredText)
                            addressTextFieldContent.checkContentValidity()
                            return@actionButton
                        }
                        addressPickerContent.state.clearValidation()

                        if (addressTextFieldContent.isContentValid && phoneNumberTextFieldContent.isContentValid && emailTextFieldContent.isContentValid) {
                            val body = buildBranchModel(verifiedLocation)
                            if (editedStore != null) {
                                updateStore(body) {
                                    coroutineScope.launch {
                                        Navigation.Menu.pop()
                                        clearStoreEditorState()
                                    }
                                }
                            } else {
                                addStore(body) {
                                    coroutineScope.launch {
                                        Navigation.Menu.pop()
                                        clearStoreEditorState()
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

internal const val LOCAL_APP_PREFERENCES_DIRTY_DRAFT_KEY = "app.preferences.last.explicit.local.v1"
internal const val LOCAL_APP_PREFERENCES_SEPARATOR = "\u001F"

internal data class LocalAppPreferencesSnapshot(
    val language: String,
    val themeId: Long,
    val sizeModeId: Long,
    val changedAtMillis: Long
)

internal fun LocalAppPreferencesSnapshot.toPersistentString(): String =
    listOf(language, themeId.toString(), sizeModeId.toString(), changedAtMillis.toString())
        .joinToString(LOCAL_APP_PREFERENCES_SEPARATOR)

internal fun String?.toLocalAppPreferencesSnapshotOrNull(): LocalAppPreferencesSnapshot? {
    val parts = this?.split(LOCAL_APP_PREFERENCES_SEPARATOR) ?: return null
    if (parts.size < 4) return null
    return LocalAppPreferencesSnapshot(
        language = parts[0].ifBlank { DEFAULT_APP_LANGUAGE },
        themeId = parts[1].toLongOrNull() ?: DEFAULT_APP_THEME_ID,
        sizeModeId = parts[2].toLongOrNull() ?: DEFAULT_APP_SIZE_MODE_ID,
        changedAtMillis = parts[3].toLongOrNull() ?: 0L
    )
}

internal fun AppConfiguration.markExplicitLocalAppPreferences(
    language: String = stateValues.appLanguage,
    themeId: Long = stateValues.appThemeId,
    sizeModeId: Long = stateValues.appSizeModeId
) {
    val snapshot = LocalAppPreferencesSnapshot(
        language = normalizeAppLanguagePreference(language),
        themeId = normalizeAppThemePreference(themeId),
        sizeModeId = normalizeAppSizeModePreference(sizeModeId),
        changedAtMillis = getCurrentTimeMillis()
    )

    coroutineScope.launch {
        setPersistentUiDraftValue?.invoke(LOCAL_APP_PREFERENCES_DIRTY_DRAFT_KEY, snapshot.toPersistentString())
    }
}

@Composable
internal fun AppConfiguration.LocalAppPreferencesPriorityEffect() {
    val userAccountId = stateValues.userAccount?.id
    val serverGroundedReachable = stateValues.cloudTransportStatus == CLOUD_TRANSPORT_STATUS_REACHABLE

    LaunchedEffect(serverGroundedReachable, userAccountId) {
        if (!serverGroundedReachable || userAccountId == null) return@LaunchedEffect
        val snapshot = getPersistentUiDraftValue
            ?.invoke(LOCAL_APP_PREFERENCES_DIRTY_DRAFT_KEY)
            .toLocalAppPreferencesSnapshotOrNull()
            ?: return@LaunchedEffect

        setAppLocale(snapshot.language)
        setAppTheme(snapshot.themeId)
        setAppSizeMode(snapshot.sizeModeId)
    }

    LaunchedEffect(
        stateValues.userAccount?.appLanguage,
        stateValues.userAccount?.appThemeId,
        stateValues.userAccount?.appSizeModeId
    ) {
        val snapshot = getPersistentUiDraftValue
            ?.invoke(LOCAL_APP_PREFERENCES_DIRTY_DRAFT_KEY)
            .toLocalAppPreferencesSnapshotOrNull()
            ?: return@LaunchedEffect
        val account = stateValues.userAccount ?: return@LaunchedEffect
        val accountMatchesLocal = normalizeAppLanguagePreference(account.appLanguage) == snapshot.language &&
                normalizeAppThemePreference(account.appThemeId) == snapshot.themeId &&
                normalizeAppSizeModePreference(account.appSizeModeId) == snapshot.sizeModeId
        if (accountMatchesLocal) {
            setPersistentUiDraftValue?.invoke(LOCAL_APP_PREFERENCES_DIRTY_DRAFT_KEY, null)
        }
    }
}

@Composable
internal fun AppConfiguration.CloudConnectionStatusBanner() {
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
            .height(32.dp)
            .background(backgroundColor)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.width(28.dp))

        Text(
            modifier = Modifier.weight(1f),
            text = when (displayedStatusKey) {
                "connected" -> localizedStringResource(1138, "Server connected.")
                "local" -> localizedStringResource(914, "Server is not connected. Branch local network mode is active.")
                "unavailable" -> localizedStringResource(1140, "Can’t reach AITA server. Check Wi‑Fi or server address.")
                else -> localizedStringResource(1139, "Checking server connection…")
            },
            color = stateValues.AccentTextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
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
    val showNavigationBar = stateValues.navigationScreensMain.last().run {
        this !is NavigationScreenModel.Splash && this !is NavigationScreenModel.UserAuth
    }

    // The grounded connection banner is the single visual source of truth for automatic
    // disconnect/recovery state. Keeping those transient status events out of popup cards avoids
    // duplicate flashes and notification fatigue while preserving actionable notifications.
    val activeNotifications = stateValues.activeNotifications
        .filterNot { it.isConnectionStatusPopupNoise() }
        .compactForPopupDisplay()
    val visibleNotifications = activeNotifications.take(if (stateValues.isNarrowScreen) 1 else 5)
    val workshiftStartDialogVisible by workshiftStartDialogVisibleState.collectAsState()
    val activeWorkshiftForGate by activeWorkshiftState.payload.collectAsState()
    val myMembershipsForGate by myWorkerMembershipsState.payload.collectAsState()

    LaunchedEffect(stateValues.activeStoreId, stateValues.userAccount?.id, myMembershipsForGate?.size, activeWorkshiftForGate?.id) {
        val storeId = stateValues.activeStoreId
        if (stateValues.userAccount != null && !storeId.isNullOrBlank()) {
            getMyWorkerMemberships()
            getCurrentWorkshift(storeId)
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
                                markNotificationRead(notification.id)
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

        Box(
            modifier = Modifier.weight(1f)
        ) {
            AnimatedContent(
                targetState = stateValues.navigationScreensMain,
                transitionSpec = { aitaStackContentTransform() },
                label = "mainNavigation"
            ) { navigationStack ->
                when (navigationStack.last()) {
                    is NavigationScreenModel.Splash -> SplashScreen()
                    is NavigationScreenModel.UserAuth -> UserAuthScreen()
                    is NavigationScreenModel.Notifications -> NotificationsScreen()
                    is NavigationScreenModel.Transaction.MainSale, NavigationScreenModel.Transaction.MainReturn, NavigationScreenModel.Transaction.MainSupply -> TransactionScreen()
                    is NavigationScreenModel.Stock -> StockScreen()
                    is NavigationScreenModel.Supplier -> SupplierScreen()
                    is NavigationScreenModel.Menu -> MenuScreen()
                    else -> {}
                }
            }

            val blockingWorkshiftGate = shouldBlockAppForWorkshift() &&
                    stateValues.navigationScreensMain.last() is NavigationScreenModel.Transaction
            if (blockingWorkshiftGate || workshiftStartDialogVisible) {
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
                                    markNotificationRead(notification.id)
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

        val bottomNavigationItems = when (stateValues.appModeId) {
            APP_MODE_STORE -> filteredMainBottomDestinations()
            APP_MODE_SUPPLIER, APP_MODE_MANUFACTURER -> Navigation.bottomNavBarScreensSupplier
            else -> Navigation.bottomNavBarScreensBuyer
        }
        val bottomNavigationCompact = stateValues.screenWidth < 390.dp || bottomNavigationItems.size >= 6
        val bottomNavigationIconSize = when {
            stateValues.screenWidth < 340.dp && bottomNavigationItems.size > 5 -> 20.dp
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
                    else width((stateValues.boundWidgetWidth * 2.2f))
                }) {
                val items = bottomNavigationItems

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
                        CpImage(
                            modifier = Modifier
                                .requiredSize(bottomNavigationIconSize)
                                .aitaSelectionMotion(selected = isSelected, selectedScale = 1.11f),
                            url = model.iconPath,
                            fallbackRes = model.iconRes,
                            contentDescription = model.name,
                            tintColor = iconTintColor
                        )

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
            notification.title
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
                text = localizedNotificationMessage(notification.message),
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                maxLines = if (compact) 3 else 5,
                overflow = TextOverflow.Ellipsis
            )
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
    val notifications = stateValues.notifications.orEmpty()
    var search by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("all") }

    LaunchedEffect(stateValues.userAccount?.id) {
        if (stateValues.userAccount != null) getNotifications()
    }

    val searchedNotifications = notifications
        .filter { notification ->
            val q = search.trim()
            q.isBlank() || listOf(
                notification.message,
                localizedNotificationMessage(notification.message),
                notification.title,
                notification.category,
                notificationTypeLabel(notification.type),
                localizedNotificationSource(notification.source),
                notification.metadata.values.joinToString(" "),
                notification.createdAtMillis.toString()
            ).any { it.contains(q, ignoreCase = true) }
        }

    val filtered = searchedNotifications
        .filter { notification ->
            when (selectedCategory) {
                "positive" -> notification.type == NotificationType.Positive
                "negative" -> notification.type == NotificationType.Negative
                "neutral" -> notification.type == NotificationType.Neutral
                "unread" -> notification.readAtMillis == null
                else -> true
            }
        }
        .sortedByDescending { it.createdAtMillis }

    LaunchedEffect(filtered.map { it.id to it.readAtMillis }) {
        if (filtered.any { it.readAtMillis == null }) {
            delay(700)
            markAllNotificationsRead()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        ScreenAppBarWidget(
            title = localizedStringResource(177, "Notifications"),
            iconPath = stateValues.drawablePathIconTransactionHistory,
            onBack = onBack
        )

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
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.72f)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filtered.isEmpty()) {
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

internal fun AppConfiguration.localizedNotificationMessage(message: String): String {
    val normalized = message.trim()
    if (normalized.isBlank()) return message

    // Old notifications are stored as plain text. If the text matches any loaded
    // localized resource value, render it in the currently selected app language.
    stateValues.strings.orEmpty().firstOrNull { group ->
        group.values.any { it.value.trim() == normalized }
    }?.let { matchedGroup ->
        return localizedStringResource(matchedGroup.id, normalized)
    }

    val notificationKey = normalized.normalizedNotificationPopupKey()
    if (notificationKey.isServerUnavailablePopupText()) {
        return localizedStringResource(1140, "Can’t reach AITA server. Check Wi‑Fi or server address.")
    }
    if (notificationKey.isServerRecoveryPopupText()) {
        return localizedStringResource(1138, "Server connected.")
    }
    if (notificationKey.isSessionRefreshPopupText()) {
        return localizedStringResource(91, "Cloud sign-in expired. Sign in again to sync. Your local data stays available.")
    }

    return when (normalized) {
        "Goods item added", "Товар добавлен", "Тауар қосылды" -> localizedStringResource(1032, normalized)
        "Stock item added" -> localizedStringResource(1033, normalized)
        "Batch added", "Batch Added", "Партия добавлена", "Партия қосылды" -> localizedStringResource(1034, normalized)
        "Batch updated", "Batch Updated", "Партия обновлена", "Партия жаңартылды" -> localizedStringResource(1035, normalized)
        "Shelf batch selected", "Партия на полке выбрана", "Сөредегі партия таңдалды" -> localizedStringResource(1036, normalized)
        "Transaction completed", "Транзакция завершена", "Транзакция аяқталды" -> localizedStringResource(1037, normalized)
        "Receipt printer is not configured", "Принтер чеков не настроен", "Түбіртек принтері бапталмаған" -> localizedStringResource(1038, normalized)
        "Printer rejected the receipt", "Принтер отклонил чек", "Принтер түбіртекті қабылдамады" -> localizedStringResource(1039, normalized)
        "Could not print receipt", "Не удалось напечатать чек", "Түбіртекті басып шығару мүмкін болмады" -> localizedStringResource(1040, normalized)
        "Android Bluetooth ESC/POS receipt printer is not configured", "Bluetooth ESC/POS-принтер чеков на Android не настроен", "Android Bluetooth ESC/POS түбіртек принтері бапталмаған" -> localizedStringResource(1041, normalized)
        "Desktop ESC/POS receipt printer is not configured", "Настольный ESC/POS-принтер чеков не настроен", "Desktop ESC/POS түбіртек принтері бапталмаған" -> localizedStringResource(1042, normalized)
        "Receipt sent to printer", "Sent to printer", "Чек отправлен на принтер", "Түбіртек принтерге жіберілді" -> localizedStringResource(1043, normalized)

        "Cannot reach server. Keeping you signed in offline." -> localizedStringResource(214, normalized)
        "Сервер недоступен. Вы остаётесь в аккаунте офлайн." -> localizedStringResource(214, normalized)
        "Сервер қолжетімсіз. Сіз офлайн режимде аккаунтта қаласыз." -> localizedStringResource(214, normalized)

        "Cannot reach server. Security sessions will refresh when connection returns." -> localizedStringResource(215, normalized)
        "Сервер недоступен. Сеансы безопасности обновятся после восстановления соединения." -> localizedStringResource(215, normalized)
        "Сервер қолжетімсіз. Қауіпсіздік сеанстары байланыс қалпына келгенде жаңартылады." -> localizedStringResource(215, normalized)

        "Session revoked", "Сеанс завершён", "Сеанс тоқтатылды" -> localizedStringResource(216, normalized)
        "Other sessions revoked", "Другие сеансы завершены", "Басқа сеанстар тоқтатылды" -> localizedStringResource(217, normalized)
        "Active sessions loaded", "Активные сеансы загружены", "Белсенді сеанстар жүктелді" -> localizedStringResource(218, normalized)
        "Login is already in progress", "Вход уже выполняется", "Кіру қазірдің өзінде орындалып жатыр" -> localizedStringResource(219, normalized)
        "Logged out locally", "Вы вышли локально", "Сіз жергілікті түрде шықтыңыз" -> localizedStringResource(220, normalized)
        "Logged out locally; server session cleanup failed", "Вы вышли локально; не удалось завершить сеанс на сервере", "Сіз жергілікті түрде шықтыңыз; сервердегі сеансты аяқтау мүмкін болмады" -> localizedStringResource(221, normalized)
        "Logged out locally; server session cleanup is queued", "Выход выполнен локально; завершение серверного сеанса поставлено в очередь", "Жергілікті түрде шығу орындалды; сервердегі сеансты аяқтау кезекке қойылды" -> localizedStringResource(1148, normalized)
        "Server could not refresh session. Keeping local login active.", "Сервер не смог обновить сеанс. Локальный вход сохранён.", "Сервер сеансты жаңарта алмады. Жергілікті кіру сақталды." -> localizedStringResource(1149, normalized)
        "Login failed: empty token response", "Не удалось войти: сервер не вернул токены", "Кіру орындалмады: сервер токендерді қайтармады" -> localizedStringResource(222, normalized)
        "Cannot reach server", "Сервер недоступен", "Сервер қолжетімсіз" -> localizedStringResource(223, normalized)
        "Completing transaction", "Завершение операции", "Операция аяқталуда" -> localizedStringResource(224, normalized)
        "Server response could not be read", "Не удалось прочитать ответ сервера", "Сервер жауабын оқу мүмкін болмады" -> localizedStringResource(225, normalized)
        "Session id is required", "Нужен id сеанса", "Сеанс id қажет" -> localizedStringResource(240, normalized)
        "Use logout to revoke the current session", "Чтобы завершить текущий сеанс, выйдите из аккаунта", "Ағымдағы сеансты тоқтату үшін аккаунттан шығыңыз" -> localizedStringResource(241, normalized)

        "Authentication failed", "Incorrect password", "Please log in first" -> stateValues.stringAuthenticationFailed
        else -> normalized
    }
}


internal fun AppConfiguration.localizedNotificationSource(source: String): String {
    return when (source.lowercase()) {
        "app" -> stateValues.stringAppName
        "server" -> localizedStringResource(242, "Server")
        else -> source
    }
}

internal fun AppConfiguration.localizedNotificationCategory(category: String, type: NotificationType): String? {
    val clean = category.trim()
    if (clean.isBlank()) return null

    val lower = clean.lowercase()
    return when (lower) {
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
                if (notification.readAtMillis == null) markNotificationRead(notification.id)
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
                text = notification.title
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
            text = localizedNotificationMessage(notification.message),
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

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
                style = TextStyle(
                    color = titleTextColor,
                    fontSize = titleTextSize,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
