package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kz.aita.auth.*
import kotlin.time.TimeSource

internal data class AuthFlowCountdown(val resendSeconds: Long, val expiresSeconds: Long, val hasExpiry: Boolean) {
    val expired: Boolean get() = hasExpiry && expiresSeconds <= 0L
}

/** Monotonic elapsed time prevents device clock changes from making resend/expiry flicker. */
@Composable
internal fun rememberAuthFlowCountdown(flow: AitaAuthFlowDataModel?): AuthFlowCountdown {
    val epoch = remember(flow) { getCurrentTimeMillis() }
    val mark = remember(flow) { TimeSource.Monotonic.markNow() }
    val resend = remember(flow) { flow?.let { aitaAuthResendDelayMillis(it.resendAfterMillis, it.serverTimeMillis, epoch) } ?: 0L }
    val expires = remember(flow) { flow?.let { aitaAuthExpiryDelayMillis(it.expiresAtMillis, it.serverTimeMillis, epoch) } ?: 0L }
    var elapsed by remember(flow) { mutableLongStateOf(0L) }
    LaunchedEffect(flow) {
        while (flow != null && elapsed < maxOf(resend, expires)) {
            delay(500L)
            elapsed = mark.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L)
        }
    }
    return AuthFlowCountdown(aitaAuthCountdownSeconds(resend - elapsed), aitaAuthCountdownSeconds(expires - elapsed), (flow?.expiresAtMillis ?: 0L) > 0L)
}

@Composable
internal fun AppConfiguration.AuthQuietAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(enabled = enabled, onClick = onClick) {
        Text(text, color = if (enabled) stateValues.AccentColor else stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun AppConfiguration.AuthEmailCodeEntry(
    value: String,
    flow: AitaAuthFlowDataModel?,
    busy: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    resendEnabled: Boolean = true,
    confirmText: String = authUiText("Continue", "Продолжить", "Жалғастыру"),
    identity: String = "auth-email-code"
) {
    val countdown = rememberAuthFlowCountdown(flow)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        aitaFormTextField(
            modifier = Modifier.fillMaxWidth(), value = value, onValueChange = onValueChange,
            titleText = authUiText("Email code", "Код из письма", "Email коды"), placeholderText = "000000",
            identityKey = identity, enabled = !busy, sensitive = true,
            keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go,
            onImeAction = { if (!busy && !countdown.expired && value.length == 6) onSubmit() },
            leadingIconPath = stateValues.drawablePathIconEmail,
            onTransformValue = { aitaAuthCodeDigits(it).take(6) }
        )
        actionButton(modifier = Modifier.fillMaxWidth(), text = confirmText,
            enabled = !busy && !countdown.expired && value.length == 6 && !flow?.flowId.isNullOrBlank(),
            loading = busy, autoLoading = false, onClick = onSubmit)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = when {
                    countdown.expired -> authUiText("Code expired", "Код истёк", "Код мерзімі аяқталды")
                    countdown.hasExpiry -> authUiText("Valid", "Действует", "Жарамды") + " " + authClock(countdown.expiresSeconds)
                    else -> ""
                }, color = if (countdown.expired) stateValues.ErrorColor else stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize, modifier = Modifier.weight(1f)
            )
            AuthQuietAction(
                text = authUiText("Resend", "Ещё код", "Қайта жіберу") +
                    (if (countdown.resendSeconds > 0L) " · " + authClock(countdown.resendSeconds) else ""),
                enabled = !busy && resendEnabled && countdown.resendSeconds == 0L,
                onClick = onResend
            )
        }
    }
}

private fun authClock(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
