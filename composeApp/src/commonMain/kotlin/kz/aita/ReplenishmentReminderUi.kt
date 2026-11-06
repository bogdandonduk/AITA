package kz.aita

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
internal fun AppConfiguration.ReplenishmentReminderCard(
    settings: ReplenishmentSettings,
    zone: String,
    locked: Boolean,
    onRestoreSettings: (ReplenishmentSettings) -> Unit,
) {
    val language = stateValues.appLanguage
    fun text(key: String) = replenishmentText(key, language)
    val owner = remember { captureReceiptActionOwner() }
    val inventoryScope = remember { inventoryViewScopeKey() }
    fun current() = owner.isCurrent() && inventoryViewScopeKey() == inventoryScope
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<ReplenishmentReminder?>(null) }
    var busy by remember { mutableStateOf(false) }
    var before by remember { mutableStateOf("3") }
    var error by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ReplenishmentReminderUpdate?>(null) }
    fun fetch() {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            try {
                val result = requestReplenishmentReminder()
                if (!current()) return@launch
                val saved = result.state
                if (saved != null) {
                    state = saved
                    before = saved.daysBeforeOrder.toString()
                    pending = null
                } else {
                    if (result.denied) state = null
                    error =
                        result.message?.extractLocalizedString(language)
                            ?: text("reminder_unavailable")
                }
            } finally {
                busy = false
            }
        }
    }
    fun save(enabled: Boolean) {
        if (busy) return
        val currentState =
            state
                ?: run {
                    error = text("reminder_load")
                    return
                }
        val days = before.toIntOrNull()
        val body =
            pending
                ?: run {
                    if (
                        enabled &&
                            (days == null ||
                                runCatching {
                                        replenishmentReminderDue(
                                            settings,
                                            zone,
                                            days,
                                            getCurrentTimeMillis(),
                                        )
                                    }
                                    .isFailure)
                    ) {
                        error = text("reminder_invalid")
                        return
                    }
                    ReplenishmentReminderUpdate(
                        newDiagnosticId(),
                        currentState.revision,
                        enabled,
                        settings,
                        zone,
                        days ?: 3,
                    )
                }
        pending = body
        busy = true
        error = ""
        scope.launch {
            try {
                val result = requestReplenishmentReminder(body)
                if (!current()) return@launch
                val saved = result.state
                when {
                    saved != null -> {
                        state = saved
                        pending = null
                        before = saved.daysBeforeOrder.toString()
                    }
                    else -> {
                        if (result.denied) {
                            state = null
                            pending = null
                        }
                        if (result.conflict || result.rejected) pending = null
                        error =
                            result.message?.extractLocalizedString(language)
                                ?: text("reminder_unavailable")
                    }
                }
            } finally {
                busy = false
            }
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .border(
                1.dp,
                stateValues.AccentColor.copy(alpha = .35f),
                RoundedCornerShape(stateValues.cornerRadius),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actionButton(
            Modifier.fillMaxWidth(),
            text = text("reminder_title"),
            autoLoading = false,
            confirmationRequired = false,
            onClick = {
                expanded = !expanded
                if (expanded && state == null) fetch()
            },
        )
        if (expanded) {
            Text(
                text("reminder_help"),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
            )
            state?.let { saved ->
                Text(
                    text(
                        if (!saved.enabled) "reminder_off"
                        else if (saved.firedAtMillis != null) "reminder_sent" else "reminder_on"
                    ),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                )
                if (saved.enabled) {
                    Text(
                        "${text(saved.settings.event)} · ${saved.settings.arrivalDate}\n${saved.localDueTime().orEmpty()} · ${saved.timeZoneId}",
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                    )
                    if (saved.settings != settings && !locked && pending == null)
                        actionButton(
                            Modifier.fillMaxWidth(),
                            text = text("reminder_restore"),
                            autoLoading = false,
                            confirmationRequired = false,
                            onClick = { onRestoreSettings(saved.settings) },
                        )
                }
            }
            if (pending == null) {
                Text(
                    text("reminder_days"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                )
                SimpleTextInput(
                    Modifier.fillMaxWidth(),
                    value = before,
                    placeholder = text("reminder_days"),
                    autoFocus = false,
                    keyboardType = KeyboardType.Number,
                    onValueChange = { if (!busy) before = it.take(2) },
                )
                Text(
                    "${text("event")}: ${text(settings.event)}${settings.arrivalDate.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                )
            } else
                Text(
                    text("reminder_pending"),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                )
            if (error.isNotBlank())
                Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actionButton(
                    Modifier.weight(1f),
                    text = text(if (pending == null) "reminder_save" else "retry"),
                    loading = busy,
                    autoLoading = false,
                    enabled = !busy,
                    confirmationRequired = false,
                    onClick = { save(true) },
                )
                actionButton(
                    Modifier.weight(1f),
                    text = text("refresh"),
                    autoLoading = false,
                    enabled = !busy,
                    confirmationRequired = false,
                    onClick = ::fetch,
                )
            }
            if (state?.enabled == true && pending == null)
                actionButton(
                    Modifier.fillMaxWidth(),
                    text = text("reminder_disable"),
                    autoLoading = false,
                    enabled = !busy,
                    confirmationRequired = false,
                    onClick = { save(false) },
                )
        }
    }
}

@Composable
internal fun AppConfiguration.ReplenishmentNotificationLink(notification: NotificationDataModel) {
    if (
        notification.category != "purchasing" ||
            notification.storeId != stateValues.activeStoreId ||
            !canUseReplenishment(stateValues.activeStoreId)
    )
        return
    var open by remember(notification.id) { mutableStateOf(false) }
    actionButton(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        text = replenishmentText("title", stateValues.appLanguage),
        iconPath = stateValues.drawablePathIconTransactionSupply,
        autoLoading = false,
        confirmationRequired = false,
        onClick = {
            if (notification.readAtMillis == null) markNotificationRead(notification.id)
            open = true
        },
    )
    if (open) ReplenishmentSheet { open = false }
}
