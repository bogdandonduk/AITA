// THIS IS CommonMainCompose.kt split slice: SupplierInsights
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
internal fun AppConfiguration.SupplierBackorderWatchCardGuideVerifyReleaseContent(
    item: SupplierDashboardBackorderDataModel
) {
    val storePreviewText = item.storePreview.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.storePreview.visibleLocalizedString("main", "") }
    val recoveryVerificationHintText = item.recoveryVerificationHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryVerificationHint.visibleLocalizedString("main", "") }
    val recoveryVerificationChecklistText = item.recoveryVerificationChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryVerificationChecklist.visibleLocalizedString("main", "") }
    val recoveryVerificationScriptText = item.recoveryVerificationScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryVerificationScript.visibleLocalizedString("main", "") }
    val recoveryApprovalHintText = item.recoveryApprovalHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryApprovalHint.visibleLocalizedString("main", "") }
    val recoveryApprovalChecklistText = item.recoveryApprovalChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryApprovalChecklist.visibleLocalizedString("main", "") }
    val recoveryApprovalScriptText = item.recoveryApprovalScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryApprovalScript.visibleLocalizedString("main", "") }
    val recoveryExecutionHintText = item.recoveryExecutionHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExecutionHint.visibleLocalizedString("main", "") }
    val recoveryExecutionChecklistText = item.recoveryExecutionChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExecutionChecklist.visibleLocalizedString("main", "") }
    val recoveryExecutionScriptText = item.recoveryExecutionScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryExecutionScript.visibleLocalizedString("main", "") }
    val recoveryReleaseHintText = item.recoveryReleaseHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReleaseHint.visibleLocalizedString("main", "") }
    val recoveryReleaseChecklistText = item.recoveryReleaseChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReleaseChecklist.visibleLocalizedString("main", "") }
    val recoveryReleaseScriptText = item.recoveryReleaseScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReleaseScript.visibleLocalizedString("main", "") }
    val nextRecoveryStepText = item.nextRecoveryStep.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.nextRecoveryStep.visibleLocalizedString("main", "") }

    if (recoveryVerificationHintText.isNotBlank() || recoveryVerificationChecklistText.isNotBlank() || recoveryVerificationScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.065f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.26f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryVerification,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryVerification.value,
                    contentDescription = localizedStringResource(2120, "Recovery verification"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryVerificationTitle(item.recoveryVerificationLane)} • ${localizedStringResource(2128, "Verification score")} ${item.recoveryVerificationScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryVerificationHintText.isNotBlank()) {
                Text(
                    text = recoveryVerificationHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryVerificationChecklistText.isNotBlank()) {
                Text(
                    text = recoveryVerificationChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryVerificationScriptText.isNotBlank()) {
                Text(
                    text = recoveryVerificationScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }


    if (recoveryApprovalHintText.isNotBlank() || recoveryApprovalChecklistText.isNotBlank() || recoveryApprovalScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.070f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.28f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryApproval,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryApproval.value,
                    contentDescription = localizedStringResource(2141, "Recovery approval"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryApprovalTitle(item.recoveryApprovalLane)} • ${localizedStringResource(2149, "Approval score")} ${item.recoveryApprovalScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryApprovalHintText.isNotBlank()) {
                Text(
                    text = recoveryApprovalHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryApprovalChecklistText.isNotBlank()) {
                Text(
                    text = recoveryApprovalChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryApprovalScriptText.isNotBlank()) {
                Text(
                    text = recoveryApprovalScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (recoveryExecutionHintText.isNotBlank() || recoveryExecutionChecklistText.isNotBlank() || recoveryExecutionScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.075f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryExecution,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryExecution.value,
                    contentDescription = localizedStringResource(2162, "Recovery execution"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryExecutionTitle(item.recoveryExecutionLane)} • ${localizedStringResource(2169, "Execution score")} ${item.recoveryExecutionScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryExecutionHintText.isNotBlank()) {
                Text(
                    text = recoveryExecutionHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryExecutionChecklistText.isNotBlank()) {
                Text(
                    text = recoveryExecutionChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryExecutionScriptText.isNotBlank()) {
                Text(
                    text = recoveryExecutionScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (recoveryReleaseHintText.isNotBlank() || recoveryReleaseChecklistText.isNotBlank() || recoveryReleaseScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.075f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryRelease,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryRelease.value,
                    contentDescription = localizedStringResource(2181, "Recovery release"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryReleaseTitle(item.recoveryReleaseLane)} • ${localizedStringResource(2188, "Release score")} ${item.recoveryReleaseScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryReleaseHintText.isNotBlank()) {
                Text(
                    text = recoveryReleaseHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryReleaseChecklistText.isNotBlank()) {
                Text(
                    text = recoveryReleaseChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryReleaseScriptText.isNotBlank()) {
                Text(
                    text = recoveryReleaseScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    nextRecoveryStepText.takeIf { it.isNotBlank() }?.let { step ->
        StockCardInfoLine(localizedStringResource(1814, "Next recovery step"), step, stateValues.TextColor)
    }
    storePreviewText.takeIf { it.isNotBlank() }?.let { preview ->
        StockCardInfoLine(localizedStringResource(1789, "Internal stores"), preview, stateValues.TextColor)
    }
    item.earliestDueAtMillis?.takeIf { it > 0L }?.let { due ->
        StockCardInfoLine(localizedStringResource(1723, "Earliest due"), receiptUiDateTime(due), stateValues.TextColor)
    }
    StockCardInfoLine(localizedStringResource(1722, "Priority score"), item.priorityScore.toString(), stateValues.TextColor)
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardGuidePlanAttentionContent(
    item: SupplierDashboardBackorderDataModel
) {
    val attentionText = item.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.attentionSummary.visibleLocalizedString("main", "") }
    val recoveryHintText = item.recoveryHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryHint.visibleLocalizedString("main", "") }
    val recoveryUrgencyHintText = item.recoveryUrgencyHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryUrgencyHint.visibleLocalizedString("main", "") }
    val recoveryChecklistText = item.recoveryChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryChecklist.visibleLocalizedString("main", "") }

    if (recoveryHintText.isNotBlank() || recoveryUrgencyHintText.isNotBlank() || recoveryChecklistText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.08f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierBackorderRecovery,
                    fallbackRes = stateValues.drawableResIconSupplierBackorderRecovery.value,
                    contentDescription = localizedStringResource(1815, "Recovery checklist"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = localizedStringResource(1815, "Recovery checklist"),
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryHintText.isNotBlank()) {
                Text(
                    text = recoveryHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryUrgencyHintText.isNotBlank()) {
                Text(
                    text = recoveryUrgencyHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryChecklistText.isNotBlank()) {
                Text(
                    text = recoveryChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (attentionText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.ErrorColor.copy(alpha = 0.06f))
                .border(stateValues.unfocusedBorderWidth, stateValues.ErrorColor.copy(alpha = 0.28f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = localizedStringResource(1756, "Attention notes"),
                color = stateValues.ErrorColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = attentionText,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardGuideSealCloseoutContent(
    item: SupplierDashboardBackorderDataModel
) {
    val recoverySealHintText = item.recoverySealHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealHint.visibleLocalizedString("main", "") }
    val recoverySealChecklistText = item.recoverySealChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealChecklist.visibleLocalizedString("main", "") }
    val recoverySealScriptText = item.recoverySealScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoverySealScript.visibleLocalizedString("main", "") }
    val recoveryCloseoutHintText = item.recoveryCloseoutHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCloseoutHint.visibleLocalizedString("main", "") }
    val recoveryCloseoutChecklistText = item.recoveryCloseoutChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCloseoutChecklist.visibleLocalizedString("main", "") }
    val recoveryCloseoutScriptText = item.recoveryCloseoutScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryCloseoutScript.visibleLocalizedString("main", "") }

    if (recoverySealHintText.isNotBlank() || recoverySealChecklistText.isNotBlank() || recoverySealScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.08f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoverySeal,
                    fallbackRes = stateValues.drawableResIconSupplierRecoverySeal.value,
                    contentDescription = localizedStringResource(2201, "Recovery seal"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoverySealTitle(item.recoverySealLane)} • ${localizedStringResource(2208, "Seal score")} ${item.recoverySealScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoverySealHintText.isNotBlank()) {
                Text(
                    text = recoverySealHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoverySealChecklistText.isNotBlank()) {
                Text(
                    text = recoverySealChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoverySealScriptText.isNotBlank()) {
                Text(
                    text = recoverySealScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (recoveryCloseoutHintText.isNotBlank() || recoveryCloseoutChecklistText.isNotBlank() || recoveryCloseoutScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.08f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryCloseout,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryCloseout.value,
                    contentDescription = localizedStringResource(2221, "Recovery closeout"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryCloseoutTitle(item.recoveryCloseoutLane)} • ${localizedStringResource(2228, "Closeout score")} ${item.recoveryCloseoutScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryCloseoutHintText.isNotBlank()) {
                Text(
                    text = recoveryCloseoutHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryCloseoutChecklistText.isNotBlank()) {
                Text(
                    text = recoveryCloseoutChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryCloseoutScriptText.isNotBlank()) {
                Text(
                    text = recoveryCloseoutScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardGuideReopenAuditContent(
    item: SupplierDashboardBackorderDataModel
) {
    val recoveryReopenHintText = item.recoveryReopenHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReopenHint.visibleLocalizedString("main", "") }
    val recoveryReopenChecklistText = item.recoveryReopenChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReopenChecklist.visibleLocalizedString("main", "") }
    val recoveryReopenScriptText = item.recoveryReopenScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReopenScript.visibleLocalizedString("main", "") }
    val recoveryReopenAtText = item.recoveryReopenAtMillis
        ?.takeIf { it > 0L }
        ?.let { receiptUiDateTime(it) }
        .orEmpty()
    val recoveryReconciliationHintText = item.recoveryReconciliationHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReconciliationHint.visibleLocalizedString("main", "") }
    val recoveryReconciliationChecklistText = item.recoveryReconciliationChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReconciliationChecklist.visibleLocalizedString("main", "") }
    val recoveryReconciliationScriptText = item.recoveryReconciliationScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryReconciliationScript.visibleLocalizedString("main", "") }
    val recoveryAuditHintText = item.recoveryAuditHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAuditHint.visibleLocalizedString("main", "") }
    val recoveryAuditChecklistText = item.recoveryAuditChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAuditChecklist.visibleLocalizedString("main", "") }
    val recoveryAuditScriptText = item.recoveryAuditScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.recoveryAuditScript.visibleLocalizedString("main", "") }

    if (recoveryReopenHintText.isNotBlank() || recoveryReopenChecklistText.isNotBlank() || recoveryReopenScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.08f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryReopen,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryReopen.value,
                    contentDescription = localizedStringResource(2241, "Recovery reopen"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = buildString {
                        append(supplierBackorderRecoveryReopenTitle(item.recoveryReopenLane))
                        append(" • ").append(localizedStringResource(2248, "Reopen score")).append(' ').append(item.recoveryReopenScore).append("/100")
                        if (recoveryReopenAtText.isNotBlank()) append(" • ").append(localizedStringResource(2249, "Reopen checkpoint")).append(' ').append(recoveryReopenAtText)
                    },
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryReopenHintText.isNotBlank()) {
                Text(
                    text = recoveryReopenHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryReopenChecklistText.isNotBlank()) {
                Text(
                    text = recoveryReopenChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryReopenScriptText.isNotBlank()) {
                Text(
                    text = recoveryReopenScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (recoveryReconciliationHintText.isNotBlank() || recoveryReconciliationChecklistText.isNotBlank() || recoveryReconciliationScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.08f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryReconciliation,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryReconciliation.value,
                    contentDescription = localizedStringResource(2262, "Recovery reconciliation"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryReconciliationTitle(item.recoveryReconciliationLane)} • ${localizedStringResource(2269, "Reconcile score")} ${item.recoveryReconciliationScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryReconciliationHintText.isNotBlank()) {
                Text(
                    text = recoveryReconciliationHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryReconciliationChecklistText.isNotBlank()) {
                Text(
                    text = recoveryReconciliationChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryReconciliationScriptText.isNotBlank()) {
                Text(
                    text = recoveryReconciliationScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (recoveryAuditHintText.isNotBlank() || recoveryAuditChecklistText.isNotBlank() || recoveryAuditScriptText.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.08f))
                .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(
                    modifier = Modifier.size(24.dp),
                    url = stateValues.drawablePathIconSupplierRecoveryAudit,
                    fallbackRes = stateValues.drawableResIconSupplierRecoveryAudit.value,
                    contentDescription = localizedStringResource(2281, "Recovery audit"),
                    tintColor = stateValues.AccentColor
                )
                Text(
                    text = "${supplierBackorderRecoveryAuditTitle(item.recoveryAuditLane)} • ${localizedStringResource(2288, "Audit score")} ${item.recoveryAuditScore}/100",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }
            if (recoveryAuditHintText.isNotBlank()) {
                Text(
                    text = recoveryAuditHintText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryAuditChecklistText.isNotBlank()) {
                Text(
                    text = recoveryAuditChecklistText,
                    color = stateValues.TextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recoveryAuditScriptText.isNotBlank()) {
                Text(
                    text = recoveryAuditScriptText,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal data class SupplierBackorderCopyActionModel(
    val labelId: Long,
    val labelFallback: String,
    val iconPath: String?,
    val iconRes: DrawableResource?,
    val copyText: () -> String
)

internal fun AppConfiguration.supplierBackorderCopyActions(
    item: SupplierDashboardBackorderDataModel
): List<SupplierBackorderCopyActionModel> =
    supplierBackorderCopyActionsIntro(item) +
        supplierBackorderCopyActionsGuard(item) +
        supplierBackorderCopyActionsRecovery(item) +
        supplierBackorderCopyActionsGate(item)

internal fun AppConfiguration.supplierBackorderCopyActionsIntro(
    item: SupplierDashboardBackorderDataModel
): List<SupplierBackorderCopyActionModel> = listOf(
    SupplierBackorderCopyActionModel(
        labelId = 1798L,
        labelFallback = "Copy shortage brief",
        iconPath = stateValues.drawablePathIconClipboard,
        iconRes = stateValues.drawableResIconClipboard.value,
        copyText = { supplierBackorderBrief(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 1856L,
        labelFallback = "Copy contact script",
        iconPath = stateValues.drawablePathIconSupplierRecoveryContact,
        iconRes = stateValues.drawableResIconSupplierRecoveryContact.value,
        copyText = { supplierBackorderContactScript(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 1867L,
        labelFallback = "Copy risk note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryRisk,
        iconRes = stateValues.drawableResIconSupplierRecoveryRisk.value,
        copyText = { supplierBackorderRiskNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 1876L,
        labelFallback = "Copy confidence note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryConfidence,
        iconRes = stateValues.drawableResIconSupplierRecoveryConfidence.value,
        copyText = { supplierBackorderConfidenceNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 1883L,
        labelFallback = "Copy follow-up note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryFollowUp,
        iconRes = stateValues.drawableResIconSupplierRecoveryFollowUp.value,
        copyText = { supplierBackorderFollowUpNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 1892L,
        labelFallback = "Copy handoff note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryHandoff,
        iconRes = stateValues.drawableResIconSupplierRecoveryHandoff.value,
        copyText = { supplierBackorderHandoffNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 1903L,
        labelFallback = "Copy close-gate note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryClosure,
        iconRes = stateValues.drawableResIconSupplierRecoveryClosure.value,
        copyText = { supplierBackorderClosureNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 1915L,
        labelFallback = "Copy ledger note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryLedger,
        iconRes = stateValues.drawableResIconSupplierRecoveryLedger.value,
        copyText = { supplierBackorderLedgerNote(item) }
    )
)

internal fun AppConfiguration.supplierBackorderCopyActionsGuard(
    item: SupplierDashboardBackorderDataModel
): List<SupplierBackorderCopyActionModel> = listOf(
    SupplierBackorderCopyActionModel(
        labelId = 1928L,
        labelFallback = "Copy triage note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryTriage,
        iconRes = stateValues.drawableResIconSupplierRecoveryTriage.value,
        copyText = { supplierBackorderTriageNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 1941L,
        labelFallback = "Copy command note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryCommand,
        iconRes = stateValues.drawableResIconSupplierRecoveryCommand.value,
        copyText = { supplierBackorderCommandNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 1954L,
        labelFallback = "Copy promise note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryPromiseShield,
        iconRes = stateValues.drawableResIconSupplierRecoveryPromiseShield.value,
        copyText = { supplierBackorderPromiseShieldNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 1978L,
        labelFallback = "Copy wave note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryWave,
        iconRes = stateValues.drawableResIconSupplierRecoveryWave.value,
        copyText = { supplierBackorderWaveNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 1992L,
        labelFallback = "Copy aging note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryAging,
        iconRes = stateValues.drawableResIconSupplierRecoveryAging.value,
        copyText = { supplierBackorderAgingNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2009L,
        labelFallback = "Copy bottleneck note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryBottleneck,
        iconRes = stateValues.drawableResIconSupplierRecoveryBottleneck.value,
        copyText = { supplierBackorderBottleneckNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2021L,
        labelFallback = "Copy load note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryLoad,
        iconRes = stateValues.drawableResIconSupplierRecoveryLoad.value,
        copyText = { supplierBackorderLoadNote(item) }
    )
)

internal fun AppConfiguration.supplierBackorderCopyActionsRecovery(
    item: SupplierDashboardBackorderDataModel
): List<SupplierBackorderCopyActionModel> = listOf(
    SupplierBackorderCopyActionModel(
        labelId = 2037L,
        labelFallback = "Copy impact note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryImpact,
        iconRes = stateValues.drawableResIconSupplierRecoveryImpact.value,
        copyText = { supplierBackorderImpactNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2054L,
        labelFallback = "Copy commit note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryCommit,
        iconRes = stateValues.drawableResIconSupplierRecoveryCommit.value,
        copyText = { supplierBackorderCommitNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2070L,
        labelFallback = "Copy allocation note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryAllocation,
        iconRes = stateValues.drawableResIconSupplierRecoveryAllocation.value,
        copyText = { supplierBackorderAllocationNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2089L,
        labelFallback = "Copy exception note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryException,
        iconRes = stateValues.drawableResIconSupplierRecoveryException.value,
        copyText = { supplierBackorderExceptionNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2111L,
        labelFallback = "Copy cause note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryCause,
        iconRes = stateValues.drawableResIconSupplierRecoveryCause.value,
        copyText = { supplierBackorderCauseNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2130L,
        labelFallback = "Copy verification note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryVerification,
        iconRes = stateValues.drawableResIconSupplierRecoveryVerification.value,
        copyText = { supplierBackorderVerificationNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2151L,
        labelFallback = "Copy approval note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryApproval,
        iconRes = stateValues.drawableResIconSupplierRecoveryApproval.value,
        copyText = { supplierBackorderApprovalNote(item) }
    )
)

internal fun AppConfiguration.supplierBackorderCopyActionsGate(
    item: SupplierDashboardBackorderDataModel
): List<SupplierBackorderCopyActionModel> = listOf(
    SupplierBackorderCopyActionModel(
        labelId = 2171L,
        labelFallback = "Copy execution note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryExecution,
        iconRes = stateValues.drawableResIconSupplierRecoveryExecution.value,
        copyText = { supplierBackorderExecutionNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2192L,
        labelFallback = "Copy release note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryRelease,
        iconRes = stateValues.drawableResIconSupplierRecoveryRelease.value,
        copyText = { supplierBackorderReleaseNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2211L,
        labelFallback = "Copy seal note",
        iconPath = stateValues.drawablePathIconSupplierRecoverySeal,
        iconRes = stateValues.drawableResIconSupplierRecoverySeal.value,
        copyText = { supplierBackorderSealNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2231L,
        labelFallback = "Copy closeout note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryCloseout,
        iconRes = stateValues.drawableResIconSupplierRecoveryCloseout.value,
        copyText = { supplierBackorderCloseoutNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2252L,
        labelFallback = "Copy reopen note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryReopen,
        iconRes = stateValues.drawableResIconSupplierRecoveryReopen.value,
        copyText = { supplierBackorderReopenNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2272L,
        labelFallback = "Copy reconcile note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryReconciliation,
        iconRes = stateValues.drawableResIconSupplierRecoveryReconciliation.value,
        copyText = { supplierBackorderReconciliationNote(item) }
    ),
    SupplierBackorderCopyActionModel(
        labelId = 2291L,
        labelFallback = "Copy audit note",
        iconPath = stateValues.drawablePathIconSupplierRecoveryAudit,
        iconRes = stateValues.drawableResIconSupplierRecoveryAudit.value,
        copyText = { supplierBackorderAuditNote(item) }
    )
)

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardActions(
    item: SupplierDashboardBackorderDataModel
) {
    if (stateValues.isNarrowScreen) {
        SupplierBackorderWatchCardNarrowActions(item = item)
    } else {
        SupplierBackorderWatchCardWideActions(item = item)
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardNarrowActions(
    item: SupplierDashboardBackorderDataModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
        SupplierBackorderOpenOrdersActionButton(
            modifier = Modifier.fillMaxWidth(),
            item = item
        )
        SupplierBackorderCopyActionColumn(
            actions = supplierBackorderCopyActions(item),
            textSize = stateValues.textSize
        )
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchCardWideActions(
    item: SupplierDashboardBackorderDataModel
) {
    val actions = supplierBackorderCopyActions(item)

    Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
        SupplierBackorderWideFirstActionRow(
            item = item,
            actions = actions.take(2)
        )
        SupplierBackorderCopyActionRows(
            actions = actions.drop(2),
            rowSizes = listOf(3, 3, 3, 3, 3, 4, 4, 4),
            textSize = stateValues.smallTextSize
        )
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderOpenOrdersActionButton(
    modifier: Modifier,
    item: SupplierDashboardBackorderDataModel,
    textSize: TextUnit = stateValues.textSize
) {
    val coroutineScope = rememberCoroutineScope()

    actionButton(
        modifier = modifier,
        text = localizedStringResource(1800, "Open shortage orders"),
        iconPath = stateValues.drawablePathIconAppModeSupplier,
        iconRes = stateValues.drawableResIconAppModeSupplier.value,
        textSize = textSize,
        confirmationRequired = false,
        onClick = {
            coroutineScope.launch {
                seedSupplierOrdersInboxNavigation(
                    searchQuery = item.goodsItemId,
                    dueFilter = item.supplierBackorderDueFilterSeed(),
                    statusFilter = "open"
                )
                Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
            }
        }
    )
}

@Composable
internal fun AppConfiguration.SupplierBackorderCopyActionColumn(
    actions: List<SupplierBackorderCopyActionModel>,
    textSize: TextUnit
) {
    actions.forEach { action ->
        SupplierBackorderCopyActionButton(
            modifier = Modifier.fillMaxWidth(),
            action = action,
            textSize = textSize
        )
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderWideFirstActionRow(
    item: SupplierDashboardBackorderDataModel,
    actions: List<SupplierBackorderCopyActionModel>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        SupplierBackorderOpenOrdersActionButton(
            modifier = Modifier.weight(1f),
            item = item,
            textSize = stateValues.smallTextSize
        )
        actions.forEach { action ->
            SupplierBackorderCopyActionButton(
                modifier = Modifier.weight(1f),
                action = action,
                textSize = stateValues.smallTextSize
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderCopyActionRows(
    actions: List<SupplierBackorderCopyActionModel>,
    rowSizes: List<Int>,
    textSize: TextUnit
) {
    var startIndex = 0
    rowSizes.forEach { rowSize ->
        val rowActions = actions.drop(startIndex).take(rowSize)
        if (rowActions.isNotEmpty()) {
            SupplierBackorderCopyActionRow(
                actions = rowActions,
                textSize = textSize
            )
        }
        startIndex += rowSize
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderCopyActionRow(
    actions: List<SupplierBackorderCopyActionModel>,
    textSize: TextUnit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        actions.forEach { action ->
            SupplierBackorderCopyActionButton(
                modifier = Modifier.weight(1f),
                action = action,
                textSize = textSize
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderCopyActionButton(
    modifier: Modifier,
    action: SupplierBackorderCopyActionModel,
    textSize: TextUnit
) {
    actionButton(
        modifier = modifier,
        text = localizedStringResource(action.labelId, action.labelFallback),
        iconPath = action.iconPath,
        iconRes = action.iconRes,
        textSize = textSize,
        confirmationRequired = false,
        onClick = { copyTextToClipboard(action.copyText()) }
    )
}

@Composable
internal fun AppConfiguration.SupplierBackorderWatchListItem(
    item: SupplierDashboardBackorderDataModel
) {
    SupplierBackorderWatchCard(item)
}


internal data class SupplierBackorderSummarySection(
    val title: String,
    val chips: List<String>
)

internal fun List<SupplierDashboardBackorderDataModel>.topRecoveryLane(
    fallback: String,
    selector: (SupplierDashboardBackorderDataModel) -> String
): String = groupingBy { item -> selector(item).ifBlank { fallback } }
    .eachCount()
    .maxByOrNull { entry -> entry.value }
    ?.key
    .orEmpty()

internal fun AppConfiguration.supplierBackorderSummaryFlowSection(
    backorderWatchItems: List<SupplierDashboardBackorderDataModel>,
    recoveryDesk: SupplierDashboardRecoveryDeskDataModel,
    supplierBackorderNow: Long
): SupplierBackorderSummarySection {
    val shortQuantity = backorderWatchItems.sumOf { item -> item.missingQuantityTotal }.roundMoney()
    val affectedOrders = backorderWatchItems.sumOf { item -> item.affectedOrderCount }
    val storeContactCount = backorderWatchItems.count { item -> item.recoveryOwnerLane == "store_contact" }
    val upstreamCount = backorderWatchItems.count { item -> item.recoveryOwnerLane == "upstream_sourcing" }
    val promiseClockCount = backorderWatchItems.count { item -> item.recoverySlaLane == "call_now" || item.recoverySlaLane == "commit_today" }
    val dueCommitCount = recoveryDesk.dueCommitCount.takeIf { it > 0 } ?: backorderWatchItems.count { item ->
        item.recoveryCommitLane == "commit_store_today" ||
            (item.recoveryCommitByMillis?.let { commitAt -> commitAt <= supplierBackorderNow + AITA_SUPPLIER_UI_DAY_MILLIS } == true)
    }

    return SupplierBackorderSummarySection(
        title = localizedStringResource(1794, "Backorder watch"),
        chips = listOf(
            "${localizedStringResource(1797, "Affected orders")}: $affectedOrders",
            "${localizedStringResource(1796, "Short qty")}: ${shortQuantity.toStockMoneyText()}",
            "${localizedStringResource(1818, "Store contact")}: $storeContactCount",
            "${localizedStringResource(1819, "Upstream sourcing")}: $upstreamCount",
            "${localizedStringResource(1847, "Promise clock")}: $promiseClockCount",
            "${localizedStringResource(2051, "Due commits")}: $dueCommitCount"
        )
    )
}

internal fun AppConfiguration.supplierBackorderSummaryRiskSection(
    backorderWatchItems: List<SupplierDashboardBackorderDataModel>,
    recoveryDesk: SupplierDashboardRecoveryDeskDataModel
): SupplierBackorderSummarySection {
    val highRiskCount = backorderWatchItems.count { item -> item.recoveryRiskLane == "critical_recovery" || item.recoveryRiskScore >= 78 }
    val lowConfidenceCount = backorderWatchItems.count { item -> item.recoveryConfidenceLane == "blocked_until_decision" || item.recoveryConfidenceScore < 45 }
    val decisionNeededCount = backorderWatchItems.count { item -> item.recoveryOutcomeLane == "substitute_offer" || item.recoveryOutcomeLane == "cancel_review" }
    val packHoldCount = backorderWatchItems.count { item -> item.recoveryEscalationLane == "pack_hold" }
    val staleAgingCount = recoveryDesk.staleRecoveryCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryAgingLane == "stale_blocker" || item.recoveryAgingScore >= 75 }
    val oldestAgingHours = recoveryDesk.oldestRecoveryAgeHours.takeIf { it > 0 } ?: (backorderWatchItems.maxOfOrNull { item -> item.recoveryAgingHours } ?: 0)

    return SupplierBackorderSummarySection(
        title = localizedStringResource(1864, "Risk score"),
        chips = listOf(
            "${localizedStringResource(1868, "Critical risks")}: $highRiskCount",
            "${localizedStringResource(1877, "Low confidence")}: $lowConfidenceCount",
            "${localizedStringResource(1840, "Decision needed")}: $decisionNeededCount",
            "${localizedStringResource(1841, "Pack hold")}: $packHoldCount",
            "${localizedStringResource(1993, "Stale age")}: $staleAgingCount",
            "${localizedStringResource(1996, "Oldest age")}: ${oldestAgingHours}h"
        )
    )
}

internal fun AppConfiguration.supplierBackorderSummaryGuardSection(
    backorderWatchItems: List<SupplierDashboardBackorderDataModel>,
    recoveryDesk: SupplierDashboardRecoveryDeskDataModel
): SupplierBackorderSummarySection {
    val topBottleneckLane = recoveryDesk.topBottleneckLane.ifBlank { backorderWatchItems.topRecoveryLane("watch_bottleneck") { item -> item.recoveryBottleneckLane } }
    val topLoadLane = recoveryDesk.topLoadLane.ifBlank { backorderWatchItems.topRecoveryLane("watch_load") { item -> item.recoveryLoadLane } }
    val topImpactLane = recoveryDesk.topImpactLane.ifBlank { backorderWatchItems.topRecoveryLane("impact_watch") { item -> item.recoveryImpactLane } }
    val heavyLoadCount = recoveryDesk.heavyLoadCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryLoadLane == "heavy_load" || item.recoveryLoadScore >= 78 }
    val highImpactCount = recoveryDesk.highImpactCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryImpactLane == "customer_promise_impact" || item.recoveryImpactScore >= 72 }
    val allocationPressureCount = recoveryDesk.allocationPressureCount.takeIf { it > 0 } ?: backorderWatchItems.count { item ->
        item.recoveryAllocationLane == "fair_split_needed" || item.recoveryAllocationLane == "priority_allocation" || item.recoveryAllocationScore >= 68
    }

    return SupplierBackorderSummarySection(
        title = localizedStringResource(2008, "Bottleneck guard"),
        chips = listOf(
            "${localizedStringResource(2010, "Top bottleneck")}: ${supplierBackorderRecoveryBottleneckTitle(topBottleneckLane)}",
            "${localizedStringResource(2022, "Top load")}: ${supplierBackorderRecoveryLoadTitle(topLoadLane)}",
            "${localizedStringResource(2041, "Top impact")}: ${supplierBackorderRecoveryImpactTitle(topImpactLane)}",
            "${localizedStringResource(2024, "Heavy loads")}: $heavyLoadCount",
            "${localizedStringResource(2039, "High impact")}: $highImpactCount",
            "${localizedStringResource(2072, "Allocation pressure")}: $allocationPressureCount"
        )
    )
}

internal fun AppConfiguration.supplierBackorderSummaryGateSection(
    backorderWatchItems: List<SupplierDashboardBackorderDataModel>,
    recoveryDesk: SupplierDashboardRecoveryDeskDataModel
): SupplierBackorderSummarySection {
    val exceptionPressureCount = recoveryDesk.exceptionPressureCount.takeIf { it > 0 } ?: backorderWatchItems.count { item ->
        item.recoveryExceptionLane == "exception_stop_pack" ||
            item.recoveryExceptionLane == "exception_cancel_review" ||
            item.recoveryExceptionLane == "exception_substitute" ||
            item.recoveryExceptionLane == "exception_sourcing" ||
            item.recoveryExceptionLane == "exception_allocation" ||
            item.recoveryExceptionScore >= 70
    }
    val causePressureCount = recoveryDesk.causePressureCount.takeIf { it > 0 } ?: backorderWatchItems.count { item ->
        item.recoveryCauseLane == "zero_acceptance_cause" ||
            item.recoveryCauseLane == "exception_cause" ||
            item.recoveryCauseLane == "promise_conflict_cause" ||
            item.recoveryCauseLane == "allocation_cause" ||
            item.recoveryCauseLane == "partial_capacity_cause" ||
            item.recoveryCauseScore >= 68
    }
    val verificationBlockerCount = recoveryDesk.verificationBlockerCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryVerificationLane == "verify_blocked" || item.recoveryVerificationScore >= 76 }
    val approvalBlockerCount = recoveryDesk.approvalBlockerCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryApprovalLane == "approval_blocked" || item.recoveryApprovalScore >= 78 }
    val executionBlockerCount = recoveryDesk.executionBlockerCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryExecutionLane == "execution_blocked" || item.recoveryExecutionScore >= 78 }
    val releaseBlockerCount = recoveryDesk.releaseBlockerCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryReleaseLane == "release_blocked" || item.recoveryReleaseScore >= 78 }

    return SupplierBackorderSummarySection(
        title = localizedStringResource(2149, "Approval gate"),
        chips = listOf(
            "${localizedStringResource(2091, "Exception pressure")}: $exceptionPressureCount",
            "${localizedStringResource(2113, "Cause pressure")}: $causePressureCount",
            "${localizedStringResource(2132, "Verification blockers")}: $verificationBlockerCount",
            "${localizedStringResource(2154, "Approval blockers")}: $approvalBlockerCount",
            "${localizedStringResource(2174, "Execution blockers")}: $executionBlockerCount",
            "${localizedStringResource(2194, "Release blockers")}: $releaseBlockerCount"
        )
    )
}

internal fun AppConfiguration.supplierBackorderSummaryCloseSection(
    backorderWatchItems: List<SupplierDashboardBackorderDataModel>,
    recoveryDesk: SupplierDashboardRecoveryDeskDataModel
): SupplierBackorderSummarySection {
    val sealBlockerCount = recoveryDesk.sealBlockerCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoverySealLane == "seal_blocked" || item.recoverySealScore >= 78 }
    val closeoutBlockerCount = recoveryDesk.closeoutBlockerCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryCloseoutLane == "closeout_blocked" || item.recoveryCloseoutScore >= 78 }
    val reopenBlockerCount = recoveryDesk.reopenBlockerCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryReopenLane == "reopen_blocked" || item.recoveryReopenScore >= 78 }
    val reconciliationBlockerCount = recoveryDesk.reconciliationBlockerCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryReconciliationLane == "reconcile_blocked" || item.recoveryReconciliationScore >= 80 }
    val auditBlockerCount = recoveryDesk.auditBlockerCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryAuditLane == "audit_blocked" || item.recoveryAuditScore >= 86 }
    val auditReadyCount = recoveryDesk.auditReadyCount.takeIf { it > 0 } ?: backorderWatchItems.count { item -> item.recoveryAuditLane == "audit_ready" }

    return SupplierBackorderSummarySection(
        title = localizedStringResource(2209, "Seal guard"),
        chips = listOf(
            "${localizedStringResource(2214, "Seal blockers")}: $sealBlockerCount",
            "${localizedStringResource(2234, "Closeout blockers")}: $closeoutBlockerCount",
            "${localizedStringResource(2255, "Reopen blockers")}: $reopenBlockerCount",
            "${localizedStringResource(2275, "Reconcile blockers")}: $reconciliationBlockerCount",
            "${localizedStringResource(2293, "Audit blockers")}: $auditBlockerCount",
            "${localizedStringResource(2297, "Ready audits")}: $auditReadyCount"
        )
    )
}

internal fun AppConfiguration.supplierBackorderSummarySections(
    backorderWatchItems: List<SupplierDashboardBackorderDataModel>,
    recoveryDesk: SupplierDashboardRecoveryDeskDataModel,
    supplierBackorderNow: Long
): List<SupplierBackorderSummarySection> = listOf(
    supplierBackorderSummaryFlowSection(backorderWatchItems, recoveryDesk, supplierBackorderNow),
    supplierBackorderSummaryRiskSection(backorderWatchItems, recoveryDesk),
    supplierBackorderSummaryGuardSection(backorderWatchItems, recoveryDesk),
    supplierBackorderSummaryGateSection(backorderWatchItems, recoveryDesk),
    supplierBackorderSummaryCloseSection(backorderWatchItems, recoveryDesk)
)

@Composable
internal fun AppConfiguration.SupplierInsightsBackorderWatchSummaryCard(
    recoveryDesk: SupplierDashboardRecoveryDeskDataModel,
    recoveryWaves: List<SupplierDashboardRecoveryWaveDataModel>,
    backorderWatchItems: List<SupplierDashboardBackorderDataModel>,
    supplierBackorderNow: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.ErrorColor.copy(alpha = 0.07f))
            .border(stateValues.unfocusedBorderWidth, stateValues.ErrorColor.copy(alpha = 0.60f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        SupplierInsightsBackorderWatchSummaryHeader()
        if (recoveryDesk.shortageCount > 0 || recoveryDesk.recoveryDeskLane.isNotBlank()) {
            SupplierInsightsRecoveryDeskCompactPanel(
                recoveryDesk = recoveryDesk,
                recoveryWaves = recoveryWaves,
                backorderWatchItems = backorderWatchItems,
                supplierBackorderNow = supplierBackorderNow
            )
        }
        supplierBackorderSummarySections(backorderWatchItems, recoveryDesk, supplierBackorderNow).forEach { section ->
            SupplierInsightsBackorderSummarySectionCard(section)
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierInsightsBackorderWatchSummaryHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        CpImage(
            modifier = Modifier.size(40.dp),
            url = stateValues.drawablePathIconSupplierBackorderRecovery,
            fallbackRes = stateValues.drawableResIconSupplierBackorderRecovery.value,
            contentDescription = localizedStringResource(1816, "Backorder recovery"),
            tintColor = stateValues.ErrorColor
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = localizedStringResource(1794, "Backorder watch"),
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = localizedStringResource(1795, "Server watches accepted-vs-requested gaps after supplier answers, so shortages become a clear upstream or store negotiation queue."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierInsightsRecoveryDeskCompactPanel(
    recoveryDesk: SupplierDashboardRecoveryDeskDataModel,
    recoveryWaves: List<SupplierDashboardRecoveryWaveDataModel>,
    backorderWatchItems: List<SupplierDashboardBackorderDataModel>,
    supplierBackorderNow: Long
) {
    val recoveryDeskHintText = recoveryDesk.recoveryDeskHint.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { recoveryDesk.recoveryDeskHint.visibleLocalizedString("main", "") }
    val recoveryDeskChecklistText = recoveryDesk.recoveryDeskChecklist.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { recoveryDesk.recoveryDeskChecklist.visibleLocalizedString("main", "") }
    val recoveryDeskScriptText = recoveryDesk.recoveryDeskScript.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { recoveryDesk.recoveryDeskScript.visibleLocalizedString("main", "") }
    val recoveryDeskNextFollowUpText = recoveryDesk.nextFollowUpAtMillis
        ?.takeIf { it > 0L }
        ?.let { receiptUiDateTime(it) }
        .orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = 0.10f))
            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.42f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SupplierInsightsRecoveryDeskCompactHeader(recoveryDesk)
        if (recoveryDeskHintText.isNotBlank()) {
            SupplierBackorderBoundedText(recoveryDeskHintText, stateValues.TextColor, maxLines = 4)
        }
        SupplierBackorderChipGrid(
            chips = supplierBackorderSummaryFlowSection(backorderWatchItems, recoveryDesk, supplierBackorderNow).chips.take(4)
        )
        if (recoveryDesk.topGoodsItemId.isNotBlank()) {
            StockCardInfoLine(localizedStringResource(1970, "Top recovery"), supplierRecoveryDeskTopTitle(recoveryDesk), stateValues.TextColor)
        }
        if (recoveryDeskNextFollowUpText.isNotBlank()) {
            StockCardInfoLine(localizedStringResource(1972, "Next desk follow-up"), recoveryDeskNextFollowUpText, stateValues.TextColor)
        }
        if (recoveryDeskChecklistText.isNotBlank()) {
            SupplierBackorderBoundedText(recoveryDeskChecklistText, stateValues.TextColor, maxLines = 5)
        }
        if (recoveryDeskScriptText.isNotBlank()) {
            SupplierBackorderBoundedText(recoveryDeskScriptText, stateValues.PlaceholderTextColor, maxLines = 4)
        }
        if (recoveryWaves.isNotEmpty()) {
            SupplierInsightsRecoveryWaveMiniBoard(recoveryWaves)
        }
        actionButton(
            modifier = Modifier.fillMaxWidth(),
            text = localizedStringResource(1967, "Copy desk note"),
            iconPath = stateValues.drawablePathIconSupplierRecoveryDesk,
            iconRes = stateValues.drawableResIconSupplierRecoveryDesk.value,
            confirmationRequired = false,
            onClick = { copyTextToClipboard(supplierRecoveryDeskNote(recoveryDesk)) }
        )
    }
}

@Composable
internal fun AppConfiguration.SupplierInsightsRecoveryDeskCompactHeader(
    recoveryDesk: SupplierDashboardRecoveryDeskDataModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CpImage(
            modifier = Modifier.size(30.dp),
            url = stateValues.drawablePathIconSupplierRecoveryDesk,
            fallbackRes = stateValues.drawableResIconSupplierRecoveryDesk.value,
            contentDescription = localizedStringResource(1958, "Recovery desk"),
            tintColor = stateValues.AccentColor
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${localizedStringResource(1958, "Recovery desk")} • ${supplierBackorderRecoveryDeskTitle(recoveryDesk.recoveryDeskLane)}",
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${localizedStringResource(1794, "Backorder watch")} ${recoveryDesk.shortageCount} • ${localizedStringResource(1864, "Risk score")} ${recoveryDesk.averageRiskScore}/100",
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderBoundedText(
    text: String,
    color: Color,
    maxLines: Int
) {
    Text(
        text = text,
        color = color,
        fontSize = stateValues.smallTextSize,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun AppConfiguration.SupplierInsightsBackorderSummarySectionCard(
    section: SupplierBackorderSummarySection
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor.copy(alpha = 0.62f))
            .border(stateValues.unfocusedBorderWidth, stateValues.ErrorColor.copy(alpha = 0.22f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = section.title,
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        SupplierBackorderChipGrid(section.chips)
    }
}

@Composable
internal fun AppConfiguration.SupplierBackorderChipGrid(
    chips: List<String>
) {
    chips.chunked(2).forEach { rowChips ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rowChips.forEach { chip ->
                Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = chip) }
            }
            if (rowChips.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierInsightsRecoveryWaveMiniBoard(
    recoveryWaves: List<SupplierDashboardRecoveryWaveDataModel>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor.copy(alpha = 0.78f))
            .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.30f), RoundedCornerShape(stateValues.cornerRadius))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CpImage(
                modifier = Modifier.size(24.dp),
                url = stateValues.drawablePathIconSupplierRecoveryWave,
                fallbackRes = stateValues.drawableResIconSupplierRecoveryWave.value,
                contentDescription = localizedStringResource(1974, "Wave board"),
                tintColor = stateValues.AccentColor
            )
            Text(
                text = localizedStringResource(1974, "Wave board"),
                color = stateValues.AccentColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
            )
        }
        recoveryWaves.take(4).forEach { wave -> SupplierInsightsRecoveryWaveMiniCard(wave) }
    }
}

@Composable
internal fun AppConfiguration.SupplierInsightsRecoveryWaveMiniCard(
    wave: SupplierDashboardRecoveryWaveDataModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.AccentColor.copy(alpha = 0.055f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "${supplierBackorderRecoveryWaveTitle(wave.recoveryWaveLane)} • ${wave.shortageCount}",
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        SupplierBackorderChipGrid(
            listOf(
                "${localizedStringResource(1796, "Short qty")}: ${wave.shortQuantityTotal.roundMoney().toStockMoneyText()}",
                "${localizedStringResource(1982, "Max risk")}: ${wave.maxRiskScore}",
                "${localizedStringResource(1983, "Max priority")}: ${wave.maxPriorityScore}",
                "${localizedStringResource(1871, "Ready to recover")}: ${wave.readyCount}"
            )
        )
        if (wave.topGoodsItemId.isNotBlank()) {
            StockCardInfoLine(localizedStringResource(1981, "Top wave item"), supplierRecoveryWaveTopTitle(wave), stateValues.TextColor)
        }
        wave.nextFollowUpAtMillis?.takeIf { it > 0L }?.let { followUp ->
            StockCardInfoLine(localizedStringResource(1882, "Next follow-up"), receiptUiDateTime(followUp), stateValues.TextColor)
        }
        wave.recoveryWaveHint.visibleLocalizedString(stateValues.appLanguage, "")
            .ifBlank { wave.recoveryWaveHint.visibleLocalizedString("main", "") }
            .takeIf { it.isNotBlank() }
            ?.let { hint -> SupplierBackorderBoundedText(hint, stateValues.PlaceholderTextColor, maxLines = 3) }
        actionButton(
            modifier = Modifier.fillMaxWidth(),
            text = localizedStringResource(1978, "Copy wave note"),
            iconPath = stateValues.drawablePathIconSupplierRecoveryWave,
            iconRes = stateValues.drawableResIconSupplierRecoveryWave.value,
            confirmationRequired = false,
            onClick = { copyTextToClipboard(supplierRecoveryWaveNote(wave)) }
        )
    }
}

@Composable
internal fun AppConfiguration.SupplierInsightsScreen() {
    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val contracts by supplierPartnershipContractsState.payload.collectAsState()
    val supplierDashboard by supplierModeDashboardState.payload.collectAsState()
    val activeSupplierProfileId by activeSupplierProfileIdState.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var backorderWaveFilter by rememberSaveable { mutableStateOf("all") }
    val coroutineScope = rememberCoroutineScope()

    val localProfiles = stateValues.suppliers.orEmpty()
        .supplierProfilesOwnedBy(stateValues.userAccount?.id)
    val focusedSupplierId = resolveSupplierProfileFocus(activeSupplierProfileId, localProfiles)
    val identityPresentation = buildSupplierIdentityPresentation(
        localProfiles = localProfiles,
        dashboard = supplierDashboard,
        activeSupplierId = focusedSupplierId,
        localProfilesLoaded = stateValues.suppliers != null
    )

    LaunchedEffect(stateValues.userAccount?.id) {
        if (stateValues.userAccount != null) {
            refreshSupplierModeWorkspace(includeContracts = true)
        }
    }

    LaunchedEffect(focusedSupplierId) {
        searchQuery = ""
        backorderWaveFilter = "all"
    }

    val activeOrders = remember(orders, focusedSupplierId) {
        orders.orEmpty()
            .supplierOrdersForIdentity(focusedSupplierId)
            .filter { it.isActive && it.status != SupplierOrderStatusDataModel.Draft }
    }
    val activeLines = remember(lines, activeOrders) {
        val activeOrderIds = activeOrders.map { it.id }.toSet()
        lines.orEmpty().filter { line -> line.isActive && line.orderId in activeOrderIds }
    }
    val activeContracts = remember(contracts, focusedSupplierId) {
        contracts.orEmpty()
            .supplierContractsForIdentity(focusedSupplierId)
            .filter { it.isActive }
    }
    val radarItems = remember(activeOrders, activeLines, stateValues.appLanguage) {
        buildSupplierDemandRadarItems(activeOrders, activeLines)
    }
    val normalizedSearch = searchQuery.trim().lowercase()
    val visibleRadarItems = remember(radarItems, normalizedSearch) {
        radarItems.filter { normalizedSearch.isBlank() || it.searchKey.contains(normalizedSearch) }
    }
    val termsGuardItems = remember(activeOrders, activeLines, activeContracts, stateValues.appLanguage) {
        buildSupplierTermsGuardItems(activeOrders, activeLines, activeContracts)
    }
    val visibleTermsGuardItems = remember(termsGuardItems, normalizedSearch) {
        termsGuardItems.filter { normalizedSearch.isBlank() || it.searchKey.contains(normalizedSearch) }
    }
    val manufacturerBridgeItems = remember(supplierDashboard, stateValues.appLanguage) {
        supplierDashboard?.manufacturerBridge.orEmpty()
    }
    val visibleManufacturerBridgeItems = remember(manufacturerBridgeItems, normalizedSearch, stateValues.appLanguage) {
        manufacturerBridgeItems.filter { item ->
            normalizedSearch.isBlank() || supplierManufacturerBridgeSearchKey(item).contains(normalizedSearch)
        }
    }
    val backorderWatchItems = remember(supplierDashboard, stateValues.appLanguage) {
        supplierDashboard?.backorderWatch.orEmpty()
    }
    val recoveryDesk = supplierDashboard?.recoveryDesk ?: SupplierDashboardRecoveryDeskDataModel()
    val recoveryWaves = recoveryDesk.recoveryWaves
    LaunchedEffect(backorderWaveFilter, recoveryWaves) {
        if (backorderWaveFilter != "all" && recoveryWaves.none { it.recoveryWaveLane == backorderWaveFilter }) {
            backorderWaveFilter = "all"
        }
    }
    val visibleBackorderWatchItems = remember(backorderWatchItems, normalizedSearch, backorderWaveFilter, stateValues.appLanguage) {
        backorderWatchItems.filter { item ->
            val waveMatches = backorderWaveFilter == "all" || item.recoveryWaveLane == backorderWaveFilter
            waveMatches && (normalizedSearch.isBlank() || supplierBackorderSearchKey(item).contains(normalizedSearch))
        }
    }
    val activeLinesByOrder = remember(activeLines) { activeLines.groupBy { it.orderId } }
    val openOrdersCount = supplierDashboard?.openOrderCount ?: activeOrders.count {
        !it.status.isClosedForSupplierDesk()
    }
    val attentionCount = supplierDashboard?.actionRequiredOrderCount ?: activeOrders.count { order ->
        order.needsSupplierActionForSupplierDesk(activeLinesByOrder[order.id].orEmpty())
    }
    val pendingContractCount = supplierDashboard?.pendingContractCount ?: activeContracts.count { it.status == SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER || it.status == SUPPLIER_CONTRACT_STATUS_PENDING_STORE }
    val activeContractCount = supplierDashboard?.activeContractCount ?: activeContracts.count { it.status == SUPPLIER_CONTRACT_STATUS_ACTIVE }
    val guardedOpenOrdersCount = termsGuardItems.sumOf { it.affectedOpenOrders }
    val supplierProfileCount = if (focusedSupplierId.isNullOrBlank()) {
        identityPresentation.profileCount
    } else {
        identityPresentation.profileCount.coerceAtMost(1)
    }
    val manufacturerBridgeCount = manufacturerBridgeItems.size
    val manufacturerBridgePriority = manufacturerBridgeItems.maxOfOrNull { it.priorityScore } ?: 0
    val backorderWatchCount = backorderWatchItems.size
    val backorderShortQuantity = backorderWatchItems.sumOf { it.missingQuantityTotal }.roundMoney()
    val supplierBackorderNow = supplierDashboard?.generatedAtMillis?.takeIf { it > 0L } ?: getCurrentTimeMillis()
    val supplierStatusMixText = supplierDashboard?.statusBuckets
        ?.take(4)
        ?.joinToString(" • ") { bucket -> "${supplierOrderStatusTitle(bucket.status)} ${bucket.orderCount}" }
        .orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenAppBarWidget(
            title = localizedStringResource(1353, "Demand radar"),
            iconPath = stateValues.drawablePathIconSupplierDemandRadar,
            iconRes = stateValues.drawableResIconSupplierDemandRadar.value
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.74f)
                .align(Alignment.CenterHorizontally)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            if (identityPresentation.profileCount == 0) {
                item(key = "supplier-insights-profile-empty") {
                    SupplierProfileIdentityCard(
                        dashboard = supplierDashboard,
                        compact = true,
                        includeContractsOnRefresh = true
                    )
                }
            } else {
                item(key = "supplier-insights-identity") {
                    SupplierOrdersWorkspaceHeader(
                        profileTitle = identityPresentation.title,
                        profileSubtitle = identityPresentation.subtitle,
                        onCreateProfile = {
                            coroutineScope.launch {
                                openSupplierProfileEditor(NavigationScreenModel.Supplier.Analytics.Main.route)
                            }
                        },
                        onRefresh = {
                            refreshSupplierModeWorkspace(includeContracts = true, force = true)
                        },
                        identityPresentation = identityPresentation,
                        onIdentitySelected = { supplierId ->
                            searchQuery = ""
                            backorderWaveFilter = "all"
                            coroutineScope.launch {
                                setActiveSupplierProfileId(supplierId)
                                postInAppNotification(
                                    localizedStringResource(2489, "Supplier identity changed"),
                                    NotificationType.Positive,
                                    transient = true
                                )
                            }
                        }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.10f))
                        .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        CpImage(
                            modifier = Modifier.size(46.dp),
                            url = stateValues.drawablePathIconSupplierDemandRadar,
                            fallbackRes = stateValues.drawableResIconSupplierDemandRadar.value,
                            contentDescription = localizedStringResource(1353, "Demand radar"),
                            tintColor = stateValues.AccentColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localizedStringResource(1531, "Demand radar live"),
                                color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = localizedStringResource(1532, "Store demand turns into an early-warning board: which items to prepare, which stores need confirmation, and which contracts may block supply."),
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize
                            )
                        }
                    }

                    if (stateValues.isNarrowScreen) {
                        Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1533, "Open pipeline"),
                                value = openOrdersCount.toString(),
                                subtitle = localizedStringResource(1534, "Orders not closed yet"),
                                iconPath = stateValues.drawablePathIconAppModeSupplier,
                                iconRes = stateValues.drawableResIconAppModeSupplier.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1535, "Issue watch"),
                                value = attentionCount.toString(),
                                subtitle = localizedStringResource(1536, "Orders needing supplier attention"),
                                iconPath = stateValues.drawablePathIconResponse,
                                iconRes = stateValues.drawableResIconResponse.value
                            )
                            SupplierDeskSummaryCard(
                                title = localizedStringResource(1537, "Contract blockers"),
                                value = pendingContractCount.toString(),
                                subtitle = localizedStringResource(1538, "Pending negotiated terms"),
                                iconPath = stateValues.drawablePathIconSupplierContracts,
                                iconRes = stateValues.drawableResIconSupplierContracts.value
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1533, "Open pipeline"),
                                value = openOrdersCount.toString(),
                                subtitle = localizedStringResource(1534, "Orders not closed yet"),
                                iconPath = stateValues.drawablePathIconAppModeSupplier,
                                iconRes = stateValues.drawableResIconAppModeSupplier.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1535, "Issue watch"),
                                value = attentionCount.toString(),
                                subtitle = localizedStringResource(1536, "Orders needing supplier attention"),
                                iconPath = stateValues.drawablePathIconResponse,
                                iconRes = stateValues.drawableResIconResponse.value
                            )
                            SupplierDeskSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = localizedStringResource(1537, "Contract blockers"),
                                value = pendingContractCount.toString(),
                                subtitle = localizedStringResource(1538, "Pending negotiated terms"),
                                iconPath = stateValues.drawablePathIconSupplierContracts,
                                iconRes = stateValues.drawableResIconSupplierContracts.value
                            )
                        }
                    }

                    StockCardInfoLine(localizedStringResource(1555, "Active contracts"), activeContractCount.toString(), stateValues.TextColor)
                    StockCardInfoLine(localizedStringResource(1602, "Guarded orders"), guardedOpenOrdersCount.toString(), stateValues.TextColor)
                    if (supplierDashboard != null) {
                        StockCardInfoLine(localizedStringResource(1619, "Supplier profiles"), supplierProfileCount.toString(), stateValues.TextColor)
                        supplierStatusMixText.takeIf { it.isNotBlank() }?.let { statusMix ->
                            StockCardInfoLine(localizedStringResource(1622, "Status mix"), statusMix, stateValues.TextColor)
                        }
                        if (backorderWatchCount > 0) {
                            StockCardInfoLine(localizedStringResource(1794, "Backorder watch"), "$backorderWatchCount • ${backorderShortQuantity.toStockMoneyText()}", stateValues.TextColor)
                        }
                        supplierDashboard?.generatedAtMillis?.takeIf { it > 0L }?.let { generatedAt ->
                            StockCardInfoLine(localizedStringResource(1617, "Server pulse"), receiptUiDateTime(generatedAt), stateValues.TextColor)
                        }
                    }
                    Text(
                        text = localizedStringResource(1603, "The terms guard connects contracts with live supplier orders, like a traffic light before goods move."),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Text(
                        text = localizedStringResource(1548, "Supplier next moves"),
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold
                    )
                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = searchQuery,
                        placeholder = stateValues.stringSearchByAnyData,
                        leadingIconPath = stateValues.drawablePathIconSearch,
                        onValueChange = { searchQuery = it }
                    )
                    if (stateValues.isNarrowScreen) {
                        Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = localizedStringResource(1549, "Confirm waiting orders"),
                                iconPath = stateValues.drawablePathIconAppModeSupplier,
                                iconRes = stateValues.drawableResIconAppModeSupplier.value,
                                confirmationRequired = false,
                                onClick = {
                                    coroutineScope.launch {
                                        seedSupplierOrdersInboxNavigation(statusFilter = SupplierOrderStatusDataModel.Sent.name)
                                        Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                                    }
                                }
                            )
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = localizedStringResource(1547, "Review contracts"),
                                iconPath = stateValues.drawablePathIconSupplierContracts,
                                iconRes = stateValues.drawableResIconSupplierContracts.value,
                                confirmationRequired = false,
                                onClick = { coroutineScope.launch { Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main) } }
                            )
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = localizedStringResource(1453, "Partner stores"),
                                iconPath = stateValues.drawablePathIconSupplierPartners,
                                iconRes = stateValues.drawableResIconSupplierPartners.value,
                                confirmationRequired = false,
                                onClick = {
                                    coroutineScope.launch {
                                        seedSupplierCustomersNavigation(searchQuery = searchQuery)
                                        Navigation.goMain(NavigationScreenModel.Supplier.Customers.Main)
                                    }
                                }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                        ) {
                            actionButton(
                                modifier = Modifier.weight(1f),
                                text = localizedStringResource(1549, "Confirm waiting orders"),
                                iconPath = stateValues.drawablePathIconAppModeSupplier,
                                iconRes = stateValues.drawableResIconAppModeSupplier.value,
                                confirmationRequired = false,
                                onClick = {
                                    coroutineScope.launch {
                                        seedSupplierOrdersInboxNavigation(statusFilter = SupplierOrderStatusDataModel.Sent.name)
                                        Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                                    }
                                }
                            )
                            actionButton(
                                modifier = Modifier.weight(1f),
                                text = localizedStringResource(1547, "Review contracts"),
                                iconPath = stateValues.drawablePathIconSupplierContracts,
                                iconRes = stateValues.drawableResIconSupplierContracts.value,
                                confirmationRequired = false,
                                onClick = { coroutineScope.launch { Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main) } }
                            )
                            actionButton(
                                modifier = Modifier.weight(1f),
                                text = localizedStringResource(1453, "Partner stores"),
                                iconPath = stateValues.drawablePathIconSupplierPartners,
                                iconRes = stateValues.drawableResIconSupplierPartners.value,
                                confirmationRequired = false,
                                onClick = {
                                    coroutineScope.launch {
                                        seedSupplierCustomersNavigation(searchQuery = searchQuery)
                                        Navigation.goMain(NavigationScreenModel.Supplier.Customers.Main)
                                    }
                                }
                            )
                        }
                    }
                }
            }


            item {
                Text(
                    text = localizedStringResource(1583, "Supplier terms guard"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (termsGuardItems.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1593, "Create or accept supplier contracts and this board will show which real orders they unlock or block.")
                    )
                }
            } else if (visibleTermsGuardItems.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1380, "No orders match this filter")
                    )
                }
            } else {
                items(visibleTermsGuardItems, key = { it.contract.id }) { item ->
                    SupplierTermsGuardCard(item)
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.08f))
                        .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.70f), RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        CpImage(
                            modifier = Modifier.size(40.dp),
                            url = stateValues.drawablePathIconAppModeManufacturer,
                            fallbackRes = stateValues.drawableResIconAppModeManufacturer.value,
                            contentDescription = localizedStringResource(1710, "Manufacturer bridge"),
                            tintColor = null
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localizedStringResource(1710, "Manufacturer bridge"),
                                color = stateValues.TextColor,
                                fontSize = stateValues.titleTextSize,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = localizedStringResource(1711, "Server converts store demand into upstream production signals: what to quote, what to produce, and what may become backorder pressure."),
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1712, "Factory queue")}: $manufacturerBridgeCount") }
                        Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1722, "Priority score")}: $manufacturerBridgePriority") }
                    }
                }
            }

            if (manufacturerBridgeItems.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1726, "No manufacturer bridge signals yet")
                    )
                }
            } else if (visibleManufacturerBridgeItems.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1380, "No orders match this filter")
                    )
                }
            } else {
                items(visibleManufacturerBridgeItems, key = { it.bridgeId.ifBlank { it.goodsItemId } }) { item ->
                    SupplierManufacturerBridgeCard(item)
                }
            }

            item {
                SupplierInsightsBackorderWatchSummaryCard(
                    recoveryDesk = recoveryDesk,
                    recoveryWaves = recoveryWaves,
                    backorderWatchItems = backorderWatchItems,
                    supplierBackorderNow = supplierBackorderNow
                )
            }

            if (recoveryWaves.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = localizedStringResource(1984, "Filter by wave"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.smallTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                TransactionHistoryFilterChip(
                                    text = localizedStringResource(1979, "All waves"),
                                    selected = backorderWaveFilter == "all",
                                    onClick = { backorderWaveFilter = "all" }
                                )
                            }
                            items(recoveryWaves, key = { it.recoveryWaveLane }) { wave ->
                                TransactionHistoryFilterChip(
                                    text = "${supplierBackorderRecoveryWaveTitle(wave.recoveryWaveLane)} ${wave.shortageCount}",
                                    selected = backorderWaveFilter == wave.recoveryWaveLane,
                                    onClick = { backorderWaveFilter = wave.recoveryWaveLane }
                                )
                            }
                        }
                    }
                }
            }

            if (backorderWatchItems.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1799, "No answered shortage pressure yet")
                    )
                }
            } else if (visibleBackorderWatchItems.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1380, "No orders match this filter")
                    )
                }
            } else {
                items(visibleBackorderWatchItems, key = { it.backorderId.ifBlank { it.goodsItemId } }) { item ->
                    SupplierBackorderWatchListItem(item)
                }
            }

            item {
                Text(
                    text = localizedStringResource(1551, "Watch these SKUs first"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (radarItems.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1541, "Demand will appear after stores send supplier orders.")
                    )
                }
            } else if (visibleRadarItems.isEmpty()) {
                item {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = localizedStringResource(1380, "No orders match this filter")
                    )
                }
            } else {
                items(visibleRadarItems, key = { it.goodsItemId }) { item ->
                    SupplierDemandRadarCard(item)
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierScreen() {
    when (stateValues.navigationScreensMain.last()) {
        is NavigationScreenModel.Supplier.Orders -> SupplierOrdersInboxScreen()
        is NavigationScreenModel.Supplier.Catalog -> SupplierCatalogScreen()
        is NavigationScreenModel.Supplier.Contracts -> SupplierContractsScreen()
        is NavigationScreenModel.Supplier.Dispatch -> SupplierDispatchScreen()
        is NavigationScreenModel.Supplier.Customers -> SupplierCustomersScreen()
        is NavigationScreenModel.Supplier.Analytics -> SupplierInsightsScreen()
        is NavigationScreenModel.Supplier.Identity -> SupplierProfilesScreen()
        else -> SupplierOrdersInboxScreen()
    }
}

@Composable
internal fun AppConfiguration.SupplierOrdersForGoodsItemContent(
    modifier: Modifier = Modifier,
    goodsItem: GoodsItemDataModel?
) {
    val activeStoreId = stateValues.activeStoreId
    val defaultCurrency = stateValues.globalAppConfiguration.countries
        .withTajikistanFallback()
        .find { it.locale.equals(stateValues.userAccount?.countryLocale, true) }
        ?.currencies
        ?.firstOrNull()
        ?.code
        ?: "KZT"
    val defaultUnit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
        .find { it.id == goodsItem?.measurementUnitId }
        ?: stateValues.globalAppConfiguration.goodsItemsQuantityUnits.firstOrNull()
        ?: QuantityDataModel("0", "unit".toLocalizedSingleMain(), total = 1.0, pricedAmount = 1.0, roundTotal = false)
    val supplierOrderQuantityAllowsFraction = defaultUnit.allowsFractionalStockQuantityInput()

    LaunchedEffect(activeStoreId) {
        activeStoreId?.let {
            getSupplierOrders(it)
            getSuppliers()
        }
    }

    val orders by supplierOrdersState.payload.collectAsState()
    val lines by supplierOrderLinesState.payload.collectAsState()
    val itemOrders = remember(goodsItem?.id, orders, lines) {
        val itemId = goodsItem?.id.orEmpty()
        orders.orEmpty()
            .filter { order -> order.isActive && lines.orEmpty().any { it.orderId == order.id && it.goodsItemId == itemId && it.isActive } }
            .sortedByDescending { it.updatedAtMillis.takeIf { value -> value > 0L } ?: it.orderedAtMillis }
    }

    var selectedSupplierId by rememberSaveable(goodsItem?.id, stateValues.suppliers?.size ?: 0) {
        mutableStateOf(stateValues.suppliers.orEmpty().firstOrNull()?.id.orEmpty())
    }
    var quantityText by rememberSaveable(goodsItem?.id) { mutableStateOf("1") }
    var expectedPriceText by rememberSaveable(goodsItem?.id) {
        mutableStateOf(goodsItem?.supplyPrices?.firstOrNull()?.price.orEmpty())
    }
    var desiredDeliveryDateText by rememberSaveable(goodsItem?.id) { mutableStateOf("") }
    var desiredExpirationDateText by rememberSaveable(goodsItem?.id) { mutableStateOf("") }
    var notesLocalized by remember(goodsItem?.id, stateValues.appLanguage) { mutableStateOf(emptyLocalizedItemForCurrentLanguage()) }

    LaunchedEffect(stateValues.suppliers, selectedSupplierId) {
        if (selectedSupplierId.isBlank()) {
            selectedSupplierId = stateValues.suppliers.orEmpty().firstOrNull()?.id.orEmpty()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(stateValues.marginTextField),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (goodsItem == null || goodsItem.id.isBlank() || activeStoreId == null) {
            item {
                MessageText(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(272, "Save the goods item first, then supplier orders can be attached to it.")
                )
            }
            return@LazyColumn
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.BackgroundColor)
                    .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                    .padding(stateValues.marginTextFieldGroup)
            ) {
                Text(
                    text = localizedStringResource(963, "Store and supplier order bridge"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = localizedStringResource(964, "This order keeps store-side data ready for the future supplier app: supplier, quantities, expected price, delivery dates, notes and receiving batches."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                SimpleDropdownField(
                    title = stateValues.stringSupplier,
                    selectedId = selectedSupplierId,
                    options = stateValues.suppliers.orEmpty().map { supplier ->
                        DropdownOption(
                            id = supplier.id,
                            title = supplier.name.visibleLocalizedString(stateValues.appLanguage, supplier.id)
                        )
                    },
                    placeholder = stateValues.stringSelectSupplier,
                    onSelected = { selectedSupplierId = it }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    SimpleTextInput(
                        modifier = Modifier.weight(1f),
                        value = quantityText,
                        placeholder = localizedStringResource(271, "Quantity"),
                        keyboardType = if (supplierOrderQuantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                        leadingIconPath = stateValues.drawablePathIconStock,
                        onTransformValue = { raw -> sanitizeStockQuantityInput(raw, supplierOrderQuantityAllowsFraction) },
                        onValueChange = { value ->
                            if (value.isStockQuantityInputText(supplierOrderQuantityAllowsFraction)) {
                                quantityText = value
                            }
                        }
                    )

                    SimpleTextInput(
                        modifier = Modifier.weight(1f),
                        value = expectedPriceText,
                        placeholder = localizedStringResource(958, "Expected supply price"),
                        keyboardType = KeyboardType.Decimal,
                        leadingIconPath = stateValues.drawablePathIconFinances,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.replace(',', '.').isNumericalDoubleString()) {
                                expectedPriceText = value.replace(',', '.')
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                StockDateRangeEditor(
                    title = localizedStringResource(363, "Period"),
                    startTitle = localizedStringResource(956, "Desired delivery"),
                    endTitle = localizedStringResource(957, "Desired expiration"),
                    startDateText = desiredDeliveryDateText,
                    endDateText = desiredExpirationDateText,
                    onStartDateChanged = { desiredDeliveryDateText = it },
                    onEndDateChanged = { desiredExpirationDateText = it }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextField))

                StockLocalizedStringGroupEditor(
                    title = localizedStringResource(201, "Notes"),
                    placeholder = stateValues.stringOptional,
                    values = notesLocalized,
                    addText = localizedStringResource(980, "Add order note translation"),
                    required = false,
                    singleLine = false,
                    adaptiveMultiline = true,
                    onChanged = { notesLocalized = it }
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                actionButton(
                    text = localizedStringResource(955, "Send to supplier"),
                    iconPath = stateValues.drawablePathIconTransactionSupply,
                    enabled = selectedSupplierId.isNotBlank() && parseStockQuantityInputText(quantityText, defaultUnit)?.let { it > 0.0 } == true
                ) {
                    val now = getCurrentTimeMillis()
                    val quantity = defaultUnit.withStockQuantityInputTotalValue(parseStockQuantityInputText(quantityText, defaultUnit) ?: 1.0)
                    val note = notesLocalized.extractLocalizedString("main")
                        ?: notesLocalized.firstOrNull { it.value.isNotBlank() }?.value
                    val order = SupplierOrderDataModel(
                        storeId = activeStoreId,
                        supplierId = selectedSupplierId,
                        amount = expectedPriceText.toMoneyDouble().takeIf { it > 0.0 }?.let { amount ->
                            PriceDataModel((amount * quantity.total).roundMoney().toStockMoneyText(), defaultCurrency, selectedSupplierId)
                        },
                        orderedAtMillis = now,
                        desiredDeliveryTimeMillis = stockDateInputTextToMillis(desiredDeliveryDateText),
                        additionalNotes = note,
                        additionalNotesLocalized = notesLocalized.filter { it.value.isNotBlank() },
                        status = SupplierOrderStatusDataModel.Sent,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                        isActive = true
                    )
                    val line = SupplierOrderLineDataModel(
                        orderId = "",
                        goodsItemId = goodsItem.id,
                        requestedQuantity = quantity,
                        expectedSupplyPrice = PriceDataModel(expectedPriceText.ifBlank { "0" }, defaultCurrency, selectedSupplierId),
                        desiredExpirationDateMillis = stockDateInputTextToMillis(desiredExpirationDateText),
                        additionalNotes = note,
                        additionalNotesLocalized = notesLocalized.filter { it.value.isNotBlank() },
                        isActive = true
                    )

                    addSupplierOrder(SupplierOrderWithLinesDataModel(order, listOf(line))) { result ->
                        if (result is DataState.Success) {
                            quantityText = "1"
                            expectedPriceText = goodsItem.supplyPrices.firstOrNull()?.price.orEmpty()
                            desiredDeliveryDateText = ""
                            desiredExpirationDateText = ""
                            notesLocalized = emptyLocalizedItemForCurrentLanguage()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(254, "Orders"),
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))
        }

        if (itemOrders.isEmpty()) {
            item {
                MessageText(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(962, "No supplier orders yet")
                )
            }
        } else {
            items(itemOrders, key = { it.id }) { order ->
                val orderLines = lines.orEmpty().filter { it.orderId == order.id && it.isActive }
                SupplierOrderCard(
                    order = order,
                    lines = orderLines,
                    goodsItem = goodsItem,
                    onReceive = {
                        val receivedLines = orderLines
                            .filter { it.goodsItemId == goodsItem.id }
                            .map { line ->
                                val receivedGoodsItemId = line.substituteGoodsItemId?.takeIf { it.isNotBlank() } ?: line.goodsItemId
                                val receivedGoodsItem = stateValues.stock.orEmpty().firstOrNull { stockItem -> stockItem.id == receivedGoodsItemId } ?: goodsItem
                                ReceiveSupplierOrderLineDataModel(
                                    orderLineId = line.id,
                                    goodsItemId = receivedGoodsItemId,
                                    receivedQuantity = line.supplierAcceptedQuantity ?: line.requestedQuantity,
                                    actualSupplyPrice = line.supplierOfferedSupplyPrice ?: line.expectedSupplyPrice ?: PriceDataModel(expectedPriceText.ifBlank { "0" }, defaultCurrency, selectedSupplierId),
                                    expirationDateMillis = line.desiredExpirationDateMillis,
                                    discounts = emptyList(),
                                    promotions = receivedGoodsItem.promotions,
                                    notes = line.supplierComment ?: line.additionalNotes,
                                    notesLocalized = line.supplierCommentLocalized.ifEmpty { line.additionalNotesLocalized }
                                )
                            }
                        receiveSupplierOrder(ReceiveSupplierOrderRequestDataModel(order.id, receivedLines))
                    },
                    onCancel = { deleteSupplierOrder(order.id) }
                )
                Spacer(modifier = Modifier.height(stateValues.marginTextField))
            }
        }

        item { Spacer(modifier = Modifier.height(stateValues.screenHeight / 5)) }
    }
}

@Composable
internal fun AppConfiguration.StockAddEditHistoryTab(
    modifier: Modifier = Modifier,
    goodsItem: GoodsItemDataModel?
) {
    val logs = stockItemHistoryState.payload.collectAsState().value.orEmpty()
    val activeStoreId = goodsItem?.storeId?.takeIf { it.isNotBlank() } ?: stateValues.activeStoreId
    val canViewHistory = currentUserCanViewStockHistory(activeStoreId)

    LaunchedEffect(activeStoreId, goodsItem?.id, canViewHistory) {
        val storeId = activeStoreId?.takeIf { it.isNotBlank() }
        val itemId = goodsItem?.id?.takeIf { it.isNotBlank() }
        if (storeId != null && itemId != null && canViewHistory) {
            getStockItemHistory(storeId, itemId)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(stateValues.marginTextField),
        contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.55f), RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.BackgroundColor)
                    .padding(stateValues.marginTextFieldGroup),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    CpImage(
                        modifier = Modifier.size(30.dp),
                        url = stateValues.drawablePathIconStockHistory,
                        fallbackRes = stateValues.drawableResIconStockHistory.value,
                        contentDescription = localizedStringResource(1331, "Stock item history"),
                        tintColor = stateValues.AccentColor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = localizedStringResource(1331, "Stock item history"),
                            color = stateValues.TextColor,
                            fontSize = stateValues.accentTextSize,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = localizedStringResource(1333, "Every saved change to item data, prices, promotions, conditions and batches is collected here."),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                    }
                }
            }
        }

        when {
            goodsItem == null -> item {
                MessageText(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1334, "Save the item first to unlock its history."),
                    subText = localizedStringResource(1333, "Every saved change to item data, prices, promotions, conditions and batches is collected here."),
                    subTextSize = stateValues.smallTextSize
                )
            }

            !canViewHistory -> item {
                MessageText(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(665, "You do not have permission for this action"),
                    subText = localizedStringResource(1332, "View stock history"),
                    textColor = stateValues.ErrorColor,
                    subTextColor = stateValues.PlaceholderTextColor,
                    subTextSize = stateValues.smallTextSize
                )
            }

            logs.isEmpty() -> item {
                MessageText(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1335, "No stock item history yet"),
                    subText = localizedStringResource(1333, "Every saved change to item data, prices, promotions, conditions and batches is collected here."),
                    subTextSize = stateValues.smallTextSize
                )
            }

            else -> items(logs, key = { it.id }) { log ->
                OperationLogCard(log)
            }
        }
    }
}

@Composable
internal fun AppConfiguration.StockAddEditOrdersTab(
    modifier: Modifier = Modifier,
    goodsItem: GoodsItemDataModel?
) {
    SupplierOrdersForGoodsItemContent(
        modifier = modifier,
        goodsItem = goodsItem
    )
}
