// THIS IS CommonMainCompose.kt split slice: Core restored for split compile stability
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

data class CameraPermissionRequestText(
    val rationaleTitle: String,
    val rationaleSubtitle: String,
    val deniedTitle: String,
    val deniedSubtitle: String,
    val settingsTitle: String,
    val settingsSubtitle: String
)

data class VoiceInputPermissionRequestText(
    val rationaleTitle: String,
    val rationaleSubtitle: String,
    val deniedTitle: String,
    val deniedSubtitle: String,
    val settingsTitle: String,
    val settingsSubtitle: String,
    val listeningTitle: String,
    val listeningSubtitle: String,
    val languageTags: List<String> = emptyList(),
    val primaryLanguageTag: String = ""
)

data class VoiceInputCallbacks(
    val onPartialText: (String) -> Unit = {},
    val onFinalText: (String) -> Unit = {},
    val onAmplitude: (Float) -> Unit = {},
    val onDetectedLanguage: (String) -> Unit = {},
    val onDenied: () -> Unit = {},
    val onError: (String) -> Unit = {},
    val onFinished: () -> Unit = {}
)

enum class PlatformPermissionKind {
    Camera,
    Microphone,
    SpeechRecognition,
    AppSettings
}

enum class PlatformPermissionState {
    Granted,
    NotDetermined,
    Denied,
    PermanentlyDenied,
    Unavailable
}

typealias BarcodeCameraScannerComposable = @Composable AppConfiguration.(Modifier, (String) -> Unit, () -> Unit) -> Unit

var barcodeCameraScannerContent: BarcodeCameraScannerComposable? = null
var requestCameraScannerPermission: (suspend (CameraPermissionRequestText, () -> Unit, () -> Unit) -> Unit)? = null
var getCameraScannerPermissionState: (suspend () -> PlatformPermissionState)? = null

var startPlatformVoiceInput: (suspend (VoiceInputPermissionRequestText, VoiceInputCallbacks) -> Unit)? = null
var stopPlatformVoiceInput: (() -> Unit)? = null
var getVoiceInputPermissionState: (suspend () -> PlatformPermissionState)? = null
var isPlatformVoiceInputAvailable: (() -> Boolean)? = null
var openPlatformAppSettings: (suspend (PlatformPermissionKind) -> ReceiptPlatformActionResult)? = null

var forceHidePlatformSoftKeyboard: (() -> Unit)? = null

internal const val AITA_MOTION_FAST_MILLIS = 140
internal const val AITA_MOTION_NORMAL_MILLIS = 190

internal fun Modifier.aitaContentMotion(): Modifier =
    animateContentSize(animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS))

internal fun aitaVisibilityEnter(): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS)) +
            expandVertically(animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS), expandFrom = Alignment.Top)

internal fun aitaVisibilityExit(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS)) +
            shrinkVertically(animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS), shrinkTowards = Alignment.Top)

internal fun platformSupportsCameraBarcodeScanner(): Boolean {
    val platformName = runCatching { getPlatformName() }.getOrDefault("")
    return platformName.contains("android", ignoreCase = true) ||
            platformName.contains("ios", ignoreCase = true)
}

internal fun platformSupportsVoiceInput(): Boolean =
    startPlatformVoiceInput != null && (isPlatformVoiceInputAvailable?.invoke() ?: true)

internal fun normalizeVisibleBarcodeFieldInput(previousValue: String, rawValue: String): String {
    val rawStored = rawValue.toStoredGoodsItemBarcode()
    val previousStored = previousValue.toStoredGoodsItemBarcode()

    if (previousStored.isNotBlank() && rawStored.startsWith(previousStored) && rawStored.length > previousStored.length) {
        val appendedPart = rawStored.removePrefix(previousStored).toStoredGoodsItemBarcode()
        if (appendedPart.length >= 6 || appendedPart.looksLikeCompleteRetailBarcodeInput()) {
            return appendedPart
        }
    }

    return if (rawStored.length > 32) rawStored.takeLast(32) else rawStored
}


fun getTransformedTextWithSelectionFocusTextColor(
    textFieldValue: TextFieldValue,
    selectionFocusTextColor: Color
): TransformedText {
    return AnnotatedString.Builder()
        .apply {
            for (i in textFieldValue.text.indices) {
                if (i in textFieldValue.selection.min until textFieldValue.selection.max)
                    withStyle(SpanStyle(color = selectionFocusTextColor)) { append(textFieldValue.text[i]) }
                else
                    append(textFieldValue.text[i])
            }
        }
        .toAnnotatedString()
        .run {
            TransformedText(
                this,
                offsetMapping = OffsetMapping.Identity
            )
        }
}

fun getPasswordTransformedTextWithSelectionFocusTextColor(
    textFieldValue: TextFieldValue,
    selectionFocusTextColor: Color
): TransformedText {
    return AnnotatedString.Builder()
        .apply {
            val maskChar = '•'
            for (i in textFieldValue.text.indices) {
                if (i in textFieldValue.selection.min until textFieldValue.selection.max)
                    withStyle(SpanStyle(color = selectionFocusTextColor)) { append (maskChar) }
                else
                    append(maskChar)
            }
        }
        .toAnnotatedString()
        .run {
            TransformedText(
                this,
                offsetMapping = OffsetMapping.Identity
            )
        }
}

internal inline fun <reified T> decodeBundledResourcePayload(raw: String): T {
    val trimmed = raw.trim()

    val payloadFromAitaEnvelope = runCatching<T?> {
        val root = jsonBase.decodeFromString<kotlinx.serialization.json.JsonElement>(trimmed)
        val envelope = root.jsonObject
        val payloadElement = envelope["payload"]

        when {
            (payloadElement == null || payloadElement is JsonNull) && envelope.containsKey("negative") && T::class == List::class -> emptyList<Any>() as T
            payloadElement == null || payloadElement is JsonNull -> null
            payloadElement is JsonPrimitive -> {
                val content = payloadElement.contentOrNull
                if (!content.isNullOrBlank() && content.trim().let { candidate ->
                        candidate.startsWith("{") || candidate.startsWith("[") || candidate.startsWith("\"")
                    }
                ) {
                    jsonBase.decodeFromString<T>(content)
                } else {
                    jsonBase.decodeFromJsonElement<T>(payloadElement)
                }
            }
            else -> jsonBase.decodeFromJsonElement<T>(payloadElement)
        }
    }.getOrNull()

    if (payloadFromAitaEnvelope != null) return payloadFromAitaEnvelope

    runCatching {
        jsonBase.decodeFromString<ResponseDataModel<T>>(trimmed).payload
    }.getOrNull()?.let { return it }

    return jsonBase.decodeFromString<T>(trimmed)
}

suspend fun loadResourceStrings(): List<LocalizedStringGroupDataModel> {
    return runCatching {
        decodeBundledResourcePayload<List<LocalizedStringGroupDataModel>>(
            Res.readBytes("files/assets/values/strings.json").decodeToString()
        )
    }.getOrDefault(emptyList())
}

internal val autoFocusedTextFieldKeysByScope = mutableMapOf<String, String>()
internal var autoFocusNavigationKey: String = ""

internal const val PERSISTENT_UI_DRAFT_PREFIX = "aita-ui-draft-v1"

internal fun StateHost?.persistentUiDraftScopeKey(): String = when (this) {
    is NavigationScreenModel -> route
    null -> "global"
    else -> toString()
}

internal fun AppConfiguration.persistentUiDraftOwnerKey(): String =
    stateValues.userAccount?.id?.takeIf { it.isNotBlank() } ?: "anonymous"

internal fun AppConfiguration.persistentUiDraftKey(
    stateHost: StateHost?,
    stateKey: String?,
    suffix: String = "value"
): String? {
    val cleanStateKey = stateKey?.takeIf { it.isNotBlank() } ?: return null
    return listOf(
        PERSISTENT_UI_DRAFT_PREFIX,
        persistentUiDraftOwnerKey(),
        stateValues.activeStoreId ?: "no-store",
        stateHost.persistentUiDraftScopeKey(),
        cleanStateKey,
        suffix
    ).joinToString(":")
}

internal fun AppConfiguration.localizedGroupEditorPersistentKey(persistentKey: String?): String? {
    val cleanKey = persistentKey?.takeIf { it.isNotBlank() } ?: return null
    return listOf(
        PERSISTENT_UI_DRAFT_PREFIX,
        persistentUiDraftOwnerKey(),
        stateValues.activeStoreId ?: "no-store",
        "localized-group",
        cleanKey
    ).joinToString(":")
}

internal fun shouldPersistUiTextDraft(keyboardType: KeyboardType, stateKey: String?): Boolean {
    val key = stateKey.orEmpty().lowercase()
    if (keyboardType == KeyboardType.Password) return false
    if ("password" in key || "token" in key || "secret" in key || "auth" in key) return false
    return stateKey?.isNotBlank() == true
}

internal const val PERSISTENT_TEXT_FIELD_META_STATE_SUFFIX = "__aita_text_field_meta"

internal data class PersistentTextFieldMeta(
    val selection: TextRange,
    val focused: Boolean
)

internal fun persistentUiTextFieldMetaStateKey(stateKey: String?): String? =
    stateKey
        ?.takeIf { it.isNotBlank() }
        ?.let { "${it}$PERSISTENT_TEXT_FIELD_META_STATE_SUFFIX" }

internal fun encodePersistentTextFieldMeta(value: TextFieldValue, focused: Boolean): String =
    listOf(
        value.selection.start.coerceIn(0, value.text.length).toString(),
        value.selection.end.coerceIn(0, value.text.length).toString(),
        if (focused) "1" else "0"
    ).joinToString(":")

internal fun decodePersistentTextFieldMeta(encoded: String?, textLength: Int): PersistentTextFieldMeta {
    val parts = encoded
        ?.split(':')
        ?.takeIf { it.size >= 2 }
        ?: return PersistentTextFieldMeta(TextRange(textLength.coerceAtLeast(0)), false)

    val safeLength = textLength.coerceAtLeast(0)
    val start = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, safeLength) ?: safeLength
    val end = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, safeLength) ?: start
    val focused = parts.getOrNull(2) == "1"

    return PersistentTextFieldMeta(TextRange(start, end), focused)
}

internal fun platformAllowsAutomaticTextFieldFocus(): Boolean {
    val platformName = runCatching { getPlatformName() }.getOrDefault("")
    return platformName.contains("desktop", ignoreCase = true) ||
            platformName.contains("jvm", ignoreCase = true)
}

@Composable
internal fun AppConfiguration.stockBatchMovementIconPath(): String {
    val normalizedThemeId = normalizeAppThemePreference(stateValues.appThemeId)
    return stateValues.drawables.orEmpty().extractPath(59L, normalizedThemeId)
        ?: "svg/59_${normalizedThemeId}.svg"
}

internal fun AppConfiguration.stockBatchMovementIconFallback(): DrawableResource =
    if (normalizeAppThemePreference(stateValues.appThemeId) == 1L) Res.drawable._59_1 else Res.drawable._59_0

@Composable
internal fun AppConfiguration.sortActionIconPath(): String {
    val normalizedThemeId = normalizeAppThemePreference(stateValues.appThemeId)
    return stateValues.drawables.orEmpty().extractPath(61L, normalizedThemeId)
        ?: "svg/61_${normalizedThemeId}.svg"
}

internal fun AppConfiguration.sortActionIconFallback(): DrawableResource =
    if (normalizeAppThemePreference(stateValues.appThemeId) == 1L) Res.drawable._61_1 else Res.drawable._61_0

@Composable
internal fun AppConfiguration.nextPageIconPath(): String {
    val normalizedThemeId = normalizeAppThemePreference(stateValues.appThemeId)
    return stateValues.drawables.orEmpty().extractPath(90L, normalizedThemeId)
        ?: "svg/90_${normalizedThemeId}.svg"
}

internal fun AppConfiguration.nextPageIconFallback(): DrawableResource =
    if (normalizeAppThemePreference(stateValues.appThemeId) == 1L) Res.drawable._90_1 else Res.drawable._90_0

internal fun StateHost?.autoFocusScopeKey(): String = this?.toString() ?: "global"

internal object SupplierPickerAutoFocusStateHost : StateHost()
internal object CartQuantityBottomSheetAutoFocusStateHost : StateHost()
internal object CartReturnPriceBottomSheetAutoFocusStateHost : StateHost()

internal fun List<NavigationScreenModel>.routesAutoFocusKey(): String =
    joinToString(">") { it.route }

internal val bundledLocalizedStringFallbacks: Map<Long, Map<String, String>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    buildBundledLocalizedStringFallbacks()
}

internal fun buildBundledLocalizedStringFallbacks(): Map<Long, Map<String, String>> =
    buildMap {
        putBundledLocalizedStringFallbacksPart0()
        putBundledLocalizedStringFallbacksPart1()
        putBundledLocalizedStringFallbacksPart2()
        putBundledLocalizedStringFallbacksPart3()
        putBundledLocalizedStringFallbacksPart4()
        putBundledLocalizedStringFallbacksPart5()
        putBundledLocalizedStringFallbacksPart6()
        putBundledLocalizedStringFallbacksPart7()
        putBundledLocalizedStringFallbacksPart8()
        putBundledLocalizedStringFallbacksPart9()
        putBundledLocalizedStringFallbacksPart10()
        putBundledLocalizedStringFallbacksPart11()
        putBundledLocalizedStringFallbacksPart12()
        putBundledLocalizedStringFallbacksPart13()
        putBundledLocalizedStringFallbacksPart14()
        putBundledLocalizedStringFallbacksPart15()
        putBundledLocalizedStringFallbacksPart16()
        putBundledLocalizedStringFallbacksPart17()
        putBundledLocalizedStringFallbacksPart18()
        putBundledLocalizedStringFallbacksPart19()
        putBundledLocalizedStringFallbacksPart20()
        putBundledLocalizedStringFallbacksPart21()
        putBundledLocalizedStringFallbacksPart22()
        putBundledLocalizedStringFallbacksPart23()
        putBundledLocalizedStringFallbacksPart24()
        putBundledLocalizedStringFallbacksPart25()
        putBundledLocalizedStringFallbacksPart26()
        putBundledLocalizedStringFallbacksPart27()
        putBundledLocalizedStringFallbacksPart28()
        putBundledLocalizedStringFallbacksPart29()
        putBundledLocalizedStringFallbacksPart30()
        putBundledLocalizedStringFallbacksPart31()
        putBundledLocalizedStringFallbacksPart32()
        putBundledLocalizedStringFallbacksPart33()
        putBundledLocalizedStringFallbacksPart34()
        putBundledLocalizedStringFallbacksPart35()
        putBundledLocalizedStringFallbacksPart36()
        putBundledLocalizedStringFallbacksPart37()
        putBundledLocalizedStringFallbacksPart38()
        putBundledLocalizedStringFallbacksPart39()
        putBundledLocalizedStringFallbacksPart40()
        putBundledLocalizedStringFallbacksPart41()
        putBundledLocalizedStringFallbacksPart42()
        putBundledLocalizedStringFallbacksPart43()
        putBundledLocalizedStringFallbacksPart44()
        putBundledLocalizedStringFallbacksPart45()
        putBundledLocalizedStringFallbacksPart46()
        putBundledLocalizedStringFallbacksPart47()
        putBundledLocalizedStringFallbacksPart48()
        putBundledLocalizedStringFallbacksPart49()
    }

