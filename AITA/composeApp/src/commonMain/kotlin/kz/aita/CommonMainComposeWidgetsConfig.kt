// THIS IS CommonMainCompose.kt split slice: WidgetsConfig
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

val kamelConfig = KamelConfig {
    httpUrlFetcher {
        httpCache(cacheSize)

        install(HttpRequestRetry) {
            maxRetries = 2
            retryIf { _, response -> !response.status.isSuccess() }
        }
    }
    fileFetcher()
    takeFrom(KamelConfig.Core)

    svgDecoder()
    imageBitmapDecoder()
}

internal fun localDrawableResourceForPath(
    url: String,
    fallbackRes: DrawableResource?
): DrawableResource? {
    val fileName = url
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')
        .substringBeforeLast('.')

    return when (fileName) {
        "0_0" -> Res.drawable._0_0
        "0_1" -> Res.drawable._0_1
        "1_0" -> Res.drawable._1_0
        "1_1" -> Res.drawable._1_1
        "2_0" -> Res.drawable._2_0
        "2_1" -> Res.drawable._2_1
        "3_0" -> Res.drawable._3_0
        "3_1" -> Res.drawable._3_1
        "4_0" -> Res.drawable._4_0
        "4_1" -> Res.drawable._4_1
        "5_0" -> Res.drawable._5_0
        "5_1" -> Res.drawable._5_1
        "6_0" -> Res.drawable._6_0
        "6_1" -> Res.drawable._6_1
        "7_0" -> Res.drawable._7_0
        "7_1" -> Res.drawable._7_1
        "8_0" -> Res.drawable._8_0
        "8_1" -> Res.drawable._8_1
        "9_0" -> Res.drawable._9_0
        "9_1" -> Res.drawable._9_1
        "10_0" -> Res.drawable._10_0
        "10_1" -> Res.drawable._10_1
        "11_0" -> Res.drawable._11_0
        "11_1" -> Res.drawable._11_1
        "12_0" -> Res.drawable._12_0
        "12_1" -> Res.drawable._12_1
        "13_0" -> Res.drawable._13_0
        "13_1" -> Res.drawable._13_1
        "14_0" -> Res.drawable._14_0
        "14_1" -> Res.drawable._14_1
        "15_0" -> Res.drawable._15_0
        "15_1" -> Res.drawable._15_1
        "16_0" -> Res.drawable._16_0
        "16_1" -> Res.drawable._16_1
        "17_0" -> Res.drawable._17_0
        "17_1" -> Res.drawable._17_1
        "18_0" -> Res.drawable._18_0
        "18_1" -> Res.drawable._18_1
        "19_0" -> Res.drawable._19_0
        "19_1" -> Res.drawable._19_1
        "20_0" -> Res.drawable._20_0
        "20_1" -> Res.drawable._20_1
        "21_0" -> Res.drawable._21_0
        "21_1" -> Res.drawable._21_1
        "22_0" -> Res.drawable._22_0
        "22_1" -> Res.drawable._22_1
        "23_0" -> Res.drawable._23_0
        "23_1" -> Res.drawable._23_1
        "24_0" -> Res.drawable._24_0
        "24_1" -> Res.drawable._24_1
        "25_0" -> Res.drawable._25_0
        "25_1" -> Res.drawable._25_1
        "26_0" -> Res.drawable._26_0
        "26_1" -> Res.drawable._26_1
        "27_0" -> Res.drawable._27_0
        "27_1" -> Res.drawable._27_1
        "28_0" -> Res.drawable._28_0
        "28_1" -> Res.drawable._28_1
        "29_0" -> Res.drawable._29_0
        "29_1" -> Res.drawable._29_1
        "30_0" -> Res.drawable._30_0
        "30_1" -> Res.drawable._30_1
        "31_0" -> Res.drawable._31_0
        "31_1" -> Res.drawable._31_1
        "32_0" -> Res.drawable._32_0
        "32_1" -> Res.drawable._32_1
        "33_0" -> Res.drawable._33_0
        "33_1" -> Res.drawable._33_1
        "34_0" -> Res.drawable._34_0
        "34_1" -> Res.drawable._34_1
        "35_0" -> Res.drawable._35_0
        "35_1" -> Res.drawable._35_1
        "36_0" -> Res.drawable._36_0
        "36_1" -> Res.drawable._36_1
        "37_0" -> Res.drawable._37_0
        "37_1" -> Res.drawable._37_1
        "38_0" -> Res.drawable._38_0
        "38_1" -> Res.drawable._38_1
        "39_0" -> Res.drawable._39_0
        "39_1" -> Res.drawable._39_1
        "40_0" -> Res.drawable._40_0
        "40_1" -> Res.drawable._40_1
        "41_0" -> Res.drawable._41_0
        "41_1" -> Res.drawable._41_1
        "42_0" -> Res.drawable._42_0
        "42_1" -> Res.drawable._42_1
        "43_0" -> Res.drawable._43_0
        "43_1" -> Res.drawable._43_1
        "44_0" -> Res.drawable._44_0
        "44_1" -> Res.drawable._44_1
        "45_0" -> Res.drawable._45_0
        "45_1" -> Res.drawable._45_1
        "46_0" -> Res.drawable._46_0
        "46_1" -> Res.drawable._46_1
        "47_0" -> Res.drawable._47_0
        "47_1" -> Res.drawable._47_1
        "48_0" -> Res.drawable._48_0
        "48_1" -> Res.drawable._48_1
        "49_0" -> Res.drawable._49_0
        "49_1" -> Res.drawable._49_1
        "50_0" -> Res.drawable._50_0
        "50_1" -> Res.drawable._50_1
        "51_0" -> Res.drawable._51_0
        "51_1" -> Res.drawable._51_1
        "52_0" -> Res.drawable._52_0
        "52_1" -> Res.drawable._52_1
        "53_0" -> Res.drawable._53_0
        "53_1" -> Res.drawable._53_1
        "54_0" -> Res.drawable._54_0
        "54_1" -> Res.drawable._54_1
        "55_0" -> Res.drawable._55_0
        "55_1" -> Res.drawable._55_1
        "56_0" -> Res.drawable._56_0
        "56_1" -> Res.drawable._56_1
        "57_0" -> Res.drawable._57_0
        "57_1" -> Res.drawable._57_1
        "58_0" -> Res.drawable._58_0
        "58_1" -> Res.drawable._58_1
        "59_0" -> Res.drawable._59_0
        "59_1" -> Res.drawable._59_1
        "60_0" -> Res.drawable._60_0
        "60_1" -> Res.drawable._60_1
        "61_0" -> Res.drawable._61_0
        "61_1" -> Res.drawable._61_1
        "62_0" -> Res.drawable._62_0
        "62_1" -> Res.drawable._62_1
        "63_0" -> Res.drawable._63_0
        "63_1" -> Res.drawable._63_1
        "64_0" -> Res.drawable._64_0
        "64_1" -> Res.drawable._64_1
        "65_0" -> Res.drawable._65_0
        "65_1" -> Res.drawable._65_1
        "66_0" -> Res.drawable._66_0
        "66_1" -> Res.drawable._66_1
        "67_0" -> Res.drawable._67_0
        "67_1" -> Res.drawable._67_1
        "68_0" -> Res.drawable._68_0
        "68_1" -> Res.drawable._68_1
        "69_0" -> Res.drawable._69_0
        "69_1" -> Res.drawable._69_1
        "70_0" -> Res.drawable._70_0
        "70_1" -> Res.drawable._70_1
        "71_0" -> Res.drawable._71_0
        "71_1" -> Res.drawable._71_1
        "72_0" -> Res.drawable._72_0
        "72_1" -> Res.drawable._72_1
        "73_0" -> Res.drawable._73_0
        "73_1" -> Res.drawable._73_1
        "74_0" -> Res.drawable._74_0
        "74_1" -> Res.drawable._74_1
        "75_0" -> Res.drawable._75_0
        "75_1" -> Res.drawable._75_1
        "76_0" -> Res.drawable._76_0
        "76_1" -> Res.drawable._76_1
        "77_0" -> Res.drawable._77_0
        "77_1" -> Res.drawable._77_1
        "78_0" -> Res.drawable._78_0
        "78_1" -> Res.drawable._78_1
        "79_0" -> Res.drawable._79_0
        "79_1" -> Res.drawable._79_1
        "80_0" -> Res.drawable._80_0
        "80_1" -> Res.drawable._80_1
        "81_0" -> Res.drawable._81_0
        "81_1" -> Res.drawable._81_1
        "82_0" -> Res.drawable._82_0
        "82_1" -> Res.drawable._82_1
        "83_0" -> Res.drawable._83_0
        "83_1" -> Res.drawable._83_1
        "84_0" -> Res.drawable._84_0
        "84_1" -> Res.drawable._84_1
        "85_0" -> Res.drawable._85_0
        "85_1" -> Res.drawable._85_1
        "86_0" -> Res.drawable._86_0
        "86_1" -> Res.drawable._86_1
        "87_0" -> Res.drawable._87_0
        "87_1" -> Res.drawable._87_1
        "88_0" -> Res.drawable._88_0
        "88_1" -> Res.drawable._88_1
        "89_0" -> Res.drawable._89_0
        "89_1" -> Res.drawable._89_1
        "90_0" -> Res.drawable._90_0
        "90_1" -> Res.drawable._90_1
        "91_0" -> Res.drawable._91_0
        "91_1" -> Res.drawable._91_1
        "92_0" -> Res.drawable._92_0
        "92_1" -> Res.drawable._92_1
        "93_0" -> Res.drawable._93_0
        "93_1" -> Res.drawable._93_1
        "94_0" -> Res.drawable._94_0
        "94_1" -> Res.drawable._94_1
        "95_0" -> Res.drawable._95_0
        "95_1" -> Res.drawable._95_1
        "96_0" -> Res.drawable._96_0
        "96_1" -> Res.drawable._96_1
        "97_0" -> Res.drawable._97_0
        "97_1" -> Res.drawable._97_1
        "98_0" -> Res.drawable._98_0
        "98_1" -> Res.drawable._98_1
        "99_0" -> Res.drawable._99_0
        "99_1" -> Res.drawable._99_1
        "100_0" -> Res.drawable._100_0
        "100_1" -> Res.drawable._100_1
        "101_0" -> Res.drawable._101_0
        "101_1" -> Res.drawable._101_1
        "102_0" -> Res.drawable._102_0
        "102_1" -> Res.drawable._102_1
        "103_0" -> Res.drawable._103_0
        "103_1" -> Res.drawable._103_1
        "104_0" -> Res.drawable._104_0
        "104_1" -> Res.drawable._104_1
        "105_0" -> Res.drawable._105_0
        "105_1" -> Res.drawable._105_1
        "106_0" -> Res.drawable._106_0
        "106_1" -> Res.drawable._106_1
        "107_0" -> Res.drawable._107_0
        "107_1" -> Res.drawable._107_1
        "108_0" -> Res.drawable._108_0
        "108_1" -> Res.drawable._108_1
        "109_0" -> Res.drawable._109_0
        "109_1" -> Res.drawable._109_1
        "110_0" -> Res.drawable._110_0
        "110_1" -> Res.drawable._110_1
        "111_0" -> Res.drawable._111_0
        "111_1" -> Res.drawable._111_1
        "112_0" -> Res.drawable._112_0
        "112_1" -> Res.drawable._112_1
        "113_0" -> Res.drawable._113_0
        "113_1" -> Res.drawable._113_1
        "114_0" -> Res.drawable._114_0
        "114_1" -> Res.drawable._114_1
        "115_0" -> Res.drawable._115_0
        "115_1" -> Res.drawable._115_1
        "116_0" -> Res.drawable._116_0
        "116_1" -> Res.drawable._116_1
        "117_0" -> Res.drawable._117_0
        "117_1" -> Res.drawable._117_1
        "118_0" -> Res.drawable._118_0
        "118_1" -> Res.drawable._118_1
        "119_0" -> Res.drawable._119_0
        "119_1" -> Res.drawable._119_1
        "120_0" -> Res.drawable._120_0
        "120_1" -> Res.drawable._120_1
        "121_0" -> Res.drawable._121_0
        "121_1" -> Res.drawable._121_1
        "122_0" -> Res.drawable._122_0
        "122_1" -> Res.drawable._122_1
        "123_0" -> Res.drawable._123_0
        "123_1" -> Res.drawable._123_1
        "124_0" -> Res.drawable._124_0
        "124_1" -> Res.drawable._124_1
        "125_0" -> Res.drawable._125_0
        "125_1" -> Res.drawable._125_1
        "126_0" -> Res.drawable._126_0
        "126_1" -> Res.drawable._126_1
        "127_0" -> Res.drawable._127_0
        "127_1" -> Res.drawable._127_1
        "128_0" -> Res.drawable._128_0
        "128_1" -> Res.drawable._128_1
        "129_0" -> Res.drawable._129_0
        "129_1" -> Res.drawable._129_1
        "130_0" -> Res.drawable._130_0
        "130_1" -> Res.drawable._130_1
        "131_0" -> Res.drawable._131_0
        "131_1" -> Res.drawable._131_1
        "132_0" -> Res.drawable._132_0
        "132_1" -> Res.drawable._132_1
        "133_0" -> Res.drawable._133_0
        "133_1" -> Res.drawable._133_1
        "134_0" -> Res.drawable._134_0
        "134_1" -> Res.drawable._134_1
        "135_0" -> Res.drawable._135_0
        "135_1" -> Res.drawable._135_1
        else -> fallbackRes
    }
}

internal fun shouldPreferLocalDrawable(
    url: String,
    localRes: DrawableResource?
): Boolean {
    if (localRes == null) return false

    val fileName = url
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')

    return Regex("^\\d+_[01]\\.svg$").matches(fileName) ||
            fileName.startsWith("flag_")
}

var renderAndroidVectorDrawable: (@Composable (Modifier, String, String?, ContentScale, ColorFilter?) -> Boolean)? = null

internal fun androidVectorDrawableResourceNameForPath(url: String): String? {
    val fileName = url
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')

    if (!Regex("^\\d+_[01]\\.svg$").matches(fileName)) return null

    return fileName.substringBeforeLast('.')
}

@Composable
fun CpImage(
    modifier: Modifier = Modifier,
    url: String,
    fallbackRes: DrawableResource?,
    contentDescription: String?,
    tintColor: Color? = null
) {
    val normalizedUrl = remember(url) { url.trim() }
    val localRes = remember(normalizedUrl, fallbackRes) {
        localDrawableResourceForPath(normalizedUrl, fallbackRes)
    }

    var failedUrl by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(normalizedUrl) {
        if (failedUrl != null && failedUrl != normalizedUrl)
            failedUrl = null
    }

    val cf = tintColor?.let { ColorFilter.tint(it) }
    val safeModifier = if (normalizedUrl.substringAfterLast('/').startsWith("13_")) {
        modifier.padding(2.dp)
    } else {
        modifier
    }

    val preferLocal = shouldPreferLocalDrawable(normalizedUrl, localRes)
    val remoteUrl = remember(normalizedUrl) { getFullDrawableRemoteResourceUrl(normalizedUrl) }
    val androidVectorResourceName = remember(normalizedUrl) {
        androidVectorDrawableResourceNameForPath(normalizedUrl)
    }
    val androidVectorRenderer = renderAndroidVectorDrawable
    val androidOwnsThisLocalSvg = androidVectorRenderer != null && androidVectorResourceName != null

    if (androidVectorRenderer != null && androidVectorResourceName != null) {
        val rendered = androidVectorRenderer(
            safeModifier,
            androidVectorResourceName,
            contentDescription,
            ContentScale.Fit,
            cf
        )

        if (rendered) return
    }

    if (!androidOwnsThisLocalSvg && localRes != null && (preferLocal || failedUrl == normalizedUrl)) {
        Image(
            modifier = safeModifier,
            painter = painterResource(localRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            colorFilter = cf,
        )
        return
    }

    CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
        KamelImage(
            modifier = safeModifier,
            resource = {
                asyncPainterResource(
                    data = Url(remoteUrl)
                )
            },
            contentScale = ContentScale.Fit,
            contentDescription = contentDescription,
            colorFilter = cf,
            onFailure = {
                failedUrl = normalizedUrl
            }
        )
    }
}

class ImeWithAction(
    val ime: ImeAction,
    private val action: (() -> Unit)? = null
) {

    companion object {

        val Default: ImeWithAction = ImeWithAction(ImeAction.Companion.Next)
    }

    fun getKeyboardActions(): KeyboardActions {
        return action?.run {
            when (ime.toString()) {
                "Go" -> KeyboardActions(
                    onGo = {
                        action()
                    }
                )
                "Search" -> KeyboardActions(
                    onSearch = {
                        action()
                    }
                )
                "Send" -> KeyboardActions(
                    onSend = {
                        action()
                    }
                )
                "Previous" -> KeyboardActions(
                    onPrevious = {
                        action()
                    }
                )
                "Next" -> KeyboardActions(
                    onNext = {
                        action()
                    }
                )
                "Done" -> KeyboardActions(
                    onDone = {
                        action()
                    }
                )
                else -> KeyboardActions.Companion.Default
            }
        } ?: KeyboardActions.Companion.Default
    }
}


@Composable
internal fun AppConfiguration.StockBatchShelfPreviewCard(
    batch: GoodsBatchDataModel,
    goodsItem: GoodsItemDataModel,
    batches: List<GoodsBatchDataModel>,
    index: Int,
    draggedBatchId: String?,
    dragTargetIndex: Int?,
    onDragStart: (String) -> Unit,
    onDragTargetChanged: (Int) -> Unit,
    onDragFinished: (Int, Int) -> Unit,
    onDragCancelled: () -> Unit
) {
    val isActiveShelf = batch.id == goodsItem.activeShelfBatchId
    val supplierName = stateValues.suppliers
        .orEmpty()
        .find { it.id == batch.supplierId }
        ?.name
        ?.extractLocalizedString(stateValues.appLanguage)
        ?: localizedStringResource(638, "No supplier selected")

    val salePrice = batch.salePriceOverride ?: goodsItem.salePrices.firstOrNull()
    val returnPrice = batch.returnPriceOverride ?: goodsItem.returnPrices.firstOrNull()
    val isDragging = draggedBatchId == batch.id
    val draggedIndex = draggedBatchId?.let { id -> batches.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
    val density = LocalDensity.current
    val cardStepPx = with(density) { 208.dp.toPx() }

    var dragOffsetPx by remember(batch.id) { mutableStateOf(0f) }

    val targetPushedOffset = when {
        draggedIndex == null || dragTargetIndex == null || isDragging -> 0f
        draggedIndex < dragTargetIndex && index in (draggedIndex + 1)..dragTargetIndex -> -cardStepPx
        draggedIndex > dragTargetIndex && index in dragTargetIndex until draggedIndex -> cardStepPx
        else -> 0f
    }

    val animatedPushedOffset by animateFloatAsState(
        targetValue = targetPushedOffset,
        animationSpec = tween(160),
        label = "batchShelfPreviewPushedOffset"
    )

    fun dragBounds(): ClosedFloatingPointRange<Float> {
        if (batches.isEmpty()) return 0f..0f
        val min = -index * cardStepPx
        val max = (batches.lastIndex - index) * cardStepPx
        return min..max
    }

    fun currentTargetIndex(): Int {
        if (batches.isEmpty()) return index
        val deltaSlots = round(dragOffsetPx / cardStepPx).toInt()
        return (index + deltaSlots).coerceIn(0, batches.lastIndex)
    }

    val cardShape = RoundedCornerShape(stateValues.cornerRadius)

    Column(
        modifier = Modifier
            .widthIn(min = 190.dp, max = 260.dp)
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                translationX = if (isDragging) dragOffsetPx else animatedPushedOffset
                scaleX = if (isDragging) 1.035f else 1f
                scaleY = if (isDragging) 1.035f else 1f
                alpha = if (isDragging) 0.97f else 1f
                shadowElevation = if (isDragging) with(density) { 6.dp.toPx() } else 0f
                shape = cardShape
                clip = true
            }
            .background(stateValues.BackgroundColor)
            .border(
                if (isActiveShelf || isDragging) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                when {
                    isDragging -> stateValues.AccentColor
                    isActiveShelf -> stateValues.AccentColor
                    else -> stateValues.PlaceholderTextColor
                },
                cardShape
            )
            .pointerInput(batch.id, batches.size, index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragOffsetPx = 0f
                        onDragStart(batch.id)
                        onDragTargetChanged(index)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val bounds = dragBounds()
                        dragOffsetPx = (dragOffsetPx + dragAmount.x).coerceIn(bounds.start, bounds.endInclusive)
                        onDragTargetChanged(currentTargetIndex())
                    },
                    onDragEnd = {
                        val target = currentTargetIndex()
                        dragOffsetPx = 0f
                        onDragFinished(index, target)
                    },
                    onDragCancel = {
                        dragOffsetPx = 0f
                        onDragCancelled()
                    }
                )
            }
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor)
            ) {
                stateValues.activeStoreId?.let { storeId ->
                    setActiveShelfBatch(
                        batch = batch,
                        storeId = storeId,
                        previousActiveShelfBatchId = goodsItem.activeShelfBatchId
                    )
                }
            }
            .padding(10.dp)
    ) {
        Text(
            text = "#${index + 1}${if (isActiveShelf) " • ${localizedStringResource(812, "active")}" else ""}",
            color = if (isActiveShelf || isDragging) stateValues.AccentColor else stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${batch.quantity.total} ${batch.quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage).orEmpty()}",
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = supplierName,
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val shelfPreviewPriceLines = buildList {
            goodsItem.promotedPriceForTransaction(
                transactionTypeIndex = 0,
                saleMethodId = SALE_METHOD_RETAIL,
                quantityTotal = 1.0,
                batch = batch
            ).takeIf { salePrice != null || it.hasPriceChange }?.let { promotedSalePrice ->
                add(StockPromotedPriceDisplayLine(stateValues.stringSalePrice, promotedSalePrice))
            }

            goodsItem.promotedPriceForTransaction(
                transactionTypeIndex = 1,
                saleMethodId = SALE_METHOD_RETAIL,
                quantityTotal = 1.0,
                batch = batch
            ).takeIf { returnPrice != null || it.hasPriceChange }?.let { promotedReturnPrice ->
                add(StockPromotedPriceDisplayLine(stateValues.stringReturnPrice, promotedReturnPrice))
            }

            add(
                StockPromotedPriceDisplayLine(
                    title = stateValues.stringSupplyPrice,
                    promotedPrice = goodsItem.promotedPriceForTransaction(
                        transactionTypeIndex = 2,
                        saleMethodId = SALE_METHOD_RETAIL,
                        quantityTotal = batch.quantity.total.takeIf { it > 0.0 } ?: 1.0,
                        batch = batch
                    )
                )
            )
        }

        StockCompactPromotionPriceInfoLines(shelfPreviewPriceLines, stateValues.TextColor)

        batch.expirationDateMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(
                title = stateValues.stringDate,
                value = it,
                textColor = stateValues.TextColor
            )
        }

        StockCardInfoLine(
            title = localizedStringResource(200, "Status"),
            value = stockBatchStatusText(batch.status),
            textColor = stateValues.TextColor
        )

        batch.deliveredAtMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(
                title = localizedStringResource(342, "Delivered"),
                value = it,
                textColor = stateValues.TextColor
            )
        }

        batch.manufacturedAtMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(
                title = localizedStringResource(343, "Made"),
                value = it,
                textColor = stateValues.TextColor
            )
        }

        batch.shelfPosition?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(
                title = localizedStringResource(344, "Shelf position"),
                value = it,
                textColor = stateValues.TextColor
            )
        }

        StockCardInfoLine(
            title = localizedStringResource(345, "Priority"),
            value = batch.shelfPriority.toString(),
            textColor = stateValues.TextColor
        )

        if (batch.discounts.isNotEmpty()) {
            StockCardInfoLine(
                title = localizedStringResource(346, "Discounts"),
                value = batch.discounts.size.toString(),
                textColor = stateValues.TextColor
            )
        }

        if (batch.promotions.isNotEmpty()) {
            StockCardInfoLine(
                title = localizedStringResource(920, "Promos"),
                value = batch.promotions.count { it.isActiveAt() }.takeIf { it > 0 }?.toString() ?: batch.promotions.size.toString(),
                textColor = stateValues.TextColor
            )
        }

        val visibleBatchNotes = batch.additionalNotesLocalized.extractLocalizedString(stateValues.appLanguage)
            ?: batch.additionalNotesLocalized.extractLocalizedString("main")
            ?: batch.additionalNotes

        visibleBatchNotes?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(
                title = localizedStringResource(201, "Notes"),
                value = it,
                textColor = stateValues.TextColor
            )
        }
    }
}



@Composable
fun AppConfiguration.GoodsItemInStockWidget(
    modifier: Modifier = Modifier,
    index: Int? = null,
    goodsItem: GoodsItemDataModel,
    batches: List<GoodsBatchDataModel> = emptyList(),
    textColor: Color = stateValues.TextColor,
    soldForPeriod: QuantityDataModel? = null,
    returnedForPeriod: QuantityDataModel? = null,
    showBatches: Boolean = true,
    transactionTypeIndex: Int? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectionToggle: ((GoodsItemDataModel) -> Unit)? = null,
    onLongPress: ((GoodsItemDataModel) -> Unit)? = null,
    onClick: ((GoodsItemDataModel) -> Unit)? = null,
    onDelete: ((GoodsItemDataModel) -> Unit)? = null,
    onEdit: ((GoodsItemDataModel) -> Unit)? = null,
    onAddBatch: ((GoodsItemDataModel) -> Unit)? = null,
    onPrintLabel: ((GoodsItemDataModel) -> Unit)? = null
) {
    val itemName = goodsItem.name.visibleLocalizedString(stateValues.appLanguage, "Unnamed item")
    val restrictionBadges = transactionTypeIndex?.let { transactionRestrictionBadgesFor(goodsItem, it) }.orEmpty()

    val standardBarcodesText = goodsItem.standardBarcodeValues()
        .filter { it.isNotBlank() }
        .joinToString(", ")
    val internalBarcodesText = goodsItem.internalBarcodeValues()
        .filter { it.isNotBlank() }
        .joinToString(", ")
    val internalBarcodeLabel = localizedStringResource(1154, "Internal")
    val barcodesText = listOf(
        standardBarcodesText.takeIf { it.isNotBlank() },
        internalBarcodesText.takeIf { it.isNotBlank() }?.let { "$internalBarcodeLabel: $it" }
    ).filterNotNull().joinToString(" • ")

    val categoriesText = goodsItem.categoryIds
        .mapNotNull { categoryId ->
            stateValues.goodsCategories
                .orEmpty()
                .find { it.id == categoryId }
                ?.name
                ?.visibleGoodsCategoryName(stateValues.appLanguage, categoryId)
        }
        .joinToString(", ")

    val shelfBatches = batches.sortedForShelf(goodsItem)
    val totalQuantity = shelfBatches.sumOf { it.quantity.total }
    val quantityUnitText = shelfBatches
        .firstOrNull()
        ?.quantity
        ?.immutableUnitName
        ?.extractLocalizedString(stateValues.appLanguage)
        .orEmpty()

    val activeBatch = shelfBatches.find { it.id == goodsItem.activeShelfBatchId }
        ?: shelfBatches.bestBatchForSale(goodsItem)

    val expirationStatusText = activeBatch?.expirationDateMillis?.toStockDateInputText()?.let {
        "${localizedStringResource(234, "Expires")}: $it"
    }

    val cardShape = RoundedCornerShape(stateValues.cornerRadius)
    val targetCardBorderColor = when {
        selected -> stateValues.AccentColor
        selectionMode -> stateValues.PlaceholderTextColor.copy(alpha = 0.55f)
        else -> stateValues.PlaceholderTextColor
    }
    val cardBorderColor by animateColorAsState(
        targetValue = targetCardBorderColor,
        animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS),
        label = "goodsCardBorder"
    )
    val cardBackgroundColor by animateColorAsState(
        targetValue = if (selected) stateValues.AccentColor.copy(alpha = 0.14f) else stateValues.BackgroundColor,
        animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS),
        label = "goodsCardBackground"
    )

    val cardInteractionSource = remember { MutableInteractionSource() }
    val cardInteractionEnabled = onClick != null || onLongPress != null || onSelectionToggle != null

    Row(
        modifier
            .padding(bottom = 4.dp)
            .fillMaxWidth()
            .heightIn(min = stateValues.textFieldHeight * 1.25f)
            .then(
                if (selectionMode || selected) Modifier else Modifier.foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            )
            .clip(cardShape)
            .background(cardBackgroundColor)
            .border(
                if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                cardBorderColor,
                cardShape
            )
            .aitaInteractiveMotion(
                interactionSource = cardInteractionSource,
                enabled = cardInteractionEnabled
            )
            .combinedClickable(
                enabled = cardInteractionEnabled,
                interactionSource = cardInteractionSource,
                indication = ripple(color = if (selected || selectionMode) stateValues.AccentColor else textColor),
                onLongClick = { onLongPress?.invoke(goodsItem) },
                onClick = {
                    if (selectionMode && onSelectionToggle != null) {
                        onSelectionToggle(goodsItem)
                    } else {
                        onClick?.invoke(goodsItem)
                    }
                }
            )
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    AitaRoundCheckbox(
                        checked = selected,
                        onCheckedChange = { onSelectionToggle?.invoke(goodsItem) },
                        containerSize = 34.dp,
                        circleSize = 22.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (index != null) {
                    Text(
                        text = "${index + 1}.",
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                TransactionRestrictionBadges(restrictionBadges, textColor)
                if (restrictionBadges.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    modifier = Modifier.weight(1f),
                    text = itemName,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (goodsItem.isQuickItem) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = stateValues.stringQuick,
                        color = stateValues.AccentColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (barcodesText.isNotBlank()) {
                StockCardInfoLine(
                    title = stateValues.stringBarcode,
                    value = barcodesText,
                    textColor = textColor
                )
            }

            if (categoriesText.isNotBlank()) {
                StockCardInfoLine(
                    title = stateValues.stringCategory,
                    value = categoriesText,
                    textColor = textColor
                )
            }

            StockCardInfoLine(
                title = stateValues.stringStock,
                value = if (shelfBatches.isEmpty()) {
                    localizedStringResource(199, "No batches yet")
                } else {
                    "$totalQuantity $quantityUnitText • ${shelfBatches.size} ${localizedStringResource(138, "Batches")}"
                },
                textColor = if (shelfBatches.isEmpty()) stateValues.ErrorColor else textColor
            )

            activeBatch?.let { batch ->
                if (transactionTypeIndex == null) {
                    StockCardInfoLine(
                        title = localizedStringResource(361, "Shelf"),
                        value = listOfNotNull(
                            if (batch.id == goodsItem.activeShelfBatchId) localizedStringResource(812, "active") else localizedStringResource(671, "Default"),
                            "#${shelfBatches.indexOfFirst { candidate -> candidate.id == batch.id } + 1}",
                            expirationStatusText
                        ).joinToString(" • "),
                        textColor = stateValues.AccentColor
                    )
                }

                val activeBatchSupplier = stateValues.suppliers
                    .orEmpty()
                    .find { it.id == batch.supplierId }
                    ?.name
                    ?.extractLocalizedString(stateValues.appLanguage)
                    ?: batch.supplierId?.takeIf { it.isNotBlank() }
                    ?: localizedStringResource(638, "No supplier selected")

                val activeBatchInfo = listOfNotNull(
                    activeBatchSupplier,
                    batch.quantity.quantityText(stateValues.appLanguage),
                    batch.deliveredAtMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(342, "Delivered")} $it" },
                    batch.expirationDateMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let { "${localizedStringResource(234, "Expires")} $it" },
                    batch.shelfPosition?.takeIf { transactionTypeIndex == null && it.isNotBlank() }?.let { "${localizedStringResource(344, "Shelf position")} $it" }
                )

                StockCardInfoLine(
                    title = localizedStringResource(125, "Current batch data"),
                    value = activeBatchInfo.joinToString(" • "),
                    textColor = stateValues.AccentColor
                )
            }

            expirationReminderTextForItem(goodsItem, shelfBatches)?.let { reminder ->
                StockCardInfoLine(
                    title = localizedStringResource(1309, "Expires very soon"),
                    value = reminder.substringAfter(": ", reminder),
                    textColor = stateValues.ErrorColor
                )
            }

            val promoQuantityTotal = activeBatch?.quantity?.total?.takeIf { it > 0.0 } ?: 1.0

            fun promotedStockPriceLineOrNull(
                title: String,
                typeIndex: Int,
                saleMethodId: String = SALE_METHOD_RETAIL,
                shouldShow: Boolean
            ): StockPromotedPriceDisplayLine? {
                val promotedPrice = goodsItem.promotedPriceForTransaction(
                    transactionTypeIndex = typeIndex,
                    saleMethodId = saleMethodId,
                    quantityTotal = promoQuantityTotal,
                    batch = activeBatch
                )

                return if (shouldShow || promotedPrice.hasPriceChange) {
                    StockPromotedPriceDisplayLine(title, promotedPrice)
                } else {
                    null
                }
            }

            when (transactionTypeIndex) {
                0 -> promotedStockPriceLineOrNull(
                    title = stateValues.stringSale,
                    typeIndex = 0,
                    saleMethodId = SALE_METHOD_RETAIL,
                    shouldShow = goodsItem.salePrices.isNotEmpty() || activeBatch?.salePriceOverride != null
                )?.let { StockCompactPromotionPriceInfoLines(listOf(it), textColor) }

                1 -> promotedStockPriceLineOrNull(
                    title = stateValues.stringReturn,
                    typeIndex = 1,
                    shouldShow = goodsItem.returnPrices.isNotEmpty() || activeBatch?.returnPriceOverride != null
                )?.let { StockCompactPromotionPriceInfoLines(listOf(it), textColor) }

                2 -> promotedStockPriceLineOrNull(
                    title = stateValues.stringSupply,
                    typeIndex = 2,
                    shouldShow = goodsItem.supplyPrices.isNotEmpty() || activeBatch != null
                )?.let { StockCompactPromotionPriceInfoLines(listOf(it), textColor) }

                else -> {
                    StockCompactPromotionPriceInfoLines(
                        listOfNotNull(
                            promotedStockPriceLineOrNull(
                                title = stateValues.stringSale,
                                typeIndex = 0,
                                saleMethodId = SALE_METHOD_RETAIL,
                                shouldShow = goodsItem.salePrices.isNotEmpty() || activeBatch?.salePriceOverride != null
                            ),
                            promotedStockPriceLineOrNull(
                                title = stateValues.stringReturn,
                                typeIndex = 1,
                                shouldShow = goodsItem.returnPrices.isNotEmpty() || activeBatch?.returnPriceOverride != null
                            ),
                            promotedStockPriceLineOrNull(
                                title = stateValues.stringSupply,
                                typeIndex = 2,
                                shouldShow = goodsItem.supplyPrices.isNotEmpty() || activeBatch != null
                            )
                        ),
                        textColor
                    )
                }
            }

            if (goodsItem.promotions.isNotEmpty()) {
                StockCardInfoLine(
                    title = localizedStringResource(920, "Promos"),
                    value = goodsItem.promotions.count { it.isActiveAt() }.takeIf { it > 0 }?.toString() ?: goodsItem.promotions.size.toString(),
                    textColor = textColor
                )
            }

            goodsItem.note?.takeIf { it.isNotBlank() }?.let {
                StockCardInfoLine(
                    title = localizedStringResource(266, "Note"),
                    value = it,
                    textColor = stateValues.TextColor
                )
            }

            if (showBatches && shelfBatches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))

                var draggedBatchId by remember(goodsItem.id) { mutableStateOf<String?>(null) }
                var dragTargetIndex by remember(goodsItem.id) { mutableStateOf<Int?>(null) }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    itemsIndexed(shelfBatches, key = { _, batch -> batch.id }) { batchIndex, batch ->
                        StockBatchShelfPreviewCard(
                            batch = batch,
                            goodsItem = goodsItem,
                            batches = shelfBatches,
                            index = batchIndex,
                            draggedBatchId = draggedBatchId,
                            dragTargetIndex = dragTargetIndex,
                            onDragStart = { id ->
                                draggedBatchId = id
                                dragTargetIndex = batchIndex
                            },
                            onDragTargetChanged = { target ->
                                dragTargetIndex = target
                            },
                            onDragFinished = { from, to ->
                                draggedBatchId = null
                                dragTargetIndex = null
                                if (from != to) {
                                    reorderShelfBatches(
                                        goodsItem = goodsItem,
                                        batches = shelfBatches,
                                        fromIndex = from,
                                        toIndex = to
                                    )
                                }
                            },
                            onDragCancelled = {
                                draggedBatchId = null
                                dragTargetIndex = null
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(end = 12.dp, top = 14.dp, start = 4.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!selectionMode) {
                onAddBatch?.let {
                    actionButton(
                        text = "",
                        iconPath = stateValues.drawablePathIconAdd,
                        iconContentDescription = localizedStringResource(194, "Add batch"),
                    ) {
                        onAddBatch(goodsItem)
                    }
                }

                onPrintLabel?.let {
                    actionButton(
                        text = "",
                        iconPath = stateValues.drawablePathIconPrintTag,
                        iconRes = stateValues.drawableResIconPrintTag.value,
                        iconContentDescription = localizedStringResource(1288, "Print item label"),
                        confirmationRequired = false,
                    ) {
                        onPrintLabel(goodsItem)
                    }
                }

                onEdit?.let {
                    actionButton(
                        text = "",
                        iconPath = stateValues.drawablePathIconEdit,
                        iconContentDescription = stateValues.drawablePathIconEdit,
                    ) {
                        onEdit(goodsItem)
                    }
                }

                onDelete?.let {
                    actionButton(
                        text = "",
                        enabledColor = stateValues.ErrorColor,
                        iconPath = stateValues.drawablePathIconDelete,
                        iconContentDescription = stateValues.drawablePathIconDelete,
                    ) {
                        onDelete(goodsItem)
                    }
                }
            }
        }
    }
}



@Composable
internal fun AppConfiguration.StockCardInfoLine(
    title: String,
    value: String,
    textColor: Color
) {
    if (value.isBlank()) return

    Text(
        text = buildAnnotatedString {
            append("$title: ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(value)
            }
        },
        fontSize = stateValues.textSize,
        color = textColor,
        fontWeight = accentTextWeight(textColor, stateValues.AccentColor),
        style = TextStyle(shadow = accentTextShadow(textColor, stateValues.AccentColor)),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun AppConfiguration.StockScreenScaffold(
    title: String,
    iconPath: String? = stateValues.drawablePathIconStock,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        ScreenAppBarWidget(
            title = title,
            iconPath = iconPath,
            onBack = if (!Navigation.Stock.isVeryFirstScreen(stateValues.isNarrowScreen)) {
                {
                    coroutineScope.launch {
                        Navigation.Stock.pop(stateValues.isNarrowScreen)
                    }
                }
            } else null
        )

        content()
    }
}

@Composable
internal fun AppConfiguration.rememberSelectedStockItem(
    stateHost: StateHost,
    stateKey: String
): GoodsItemDataModel? {
    val screenState by stateHost.state.collectAsState()
    val goodsItemId = screenState[stateKey]

    return stateValues.stock
        .orEmpty()
        .find { it.id == goodsItemId }
}

@Composable
fun AppConfiguration.StockGoodsItemDetailsScreen() {
    val goodsItem = rememberSelectedStockItem(
        stateHost = NavigationScreenModel.Stock.GoodsItemDetails,
        stateKey = NavigationScreenModel.Stock.GoodsItemDetails.KEY_STATE_GOODS_ITEM_ID
    )

    StockScreenScaffold(
        title = localizedStringResource(365, "Goods item")
    ) {
        if (goodsItem == null) {
            MessageText(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(353, "Goods item was not found")
            )
            return@StockScreenScaffold
        }

        var showLabelPrintSheet by rememberSaveable(goodsItem.id) { mutableStateOf(false) }
        val batches = stateValues.stockBatches.orEmpty().filter { it.goodsItemId == goodsItem.id && it.isActive }
        if (showLabelPrintSheet) {
            StockItemLabelPrintBottomSheet(
                goodsItem = goodsItem,
                batches = batches,
                onDismiss = { showLabelPrintSheet = false }
            )
        }
        val activeBatch = batches.sortedForShelf(goodsItem).firstOrNull { it.id == goodsItem.activeShelfBatchId }
            ?: batches.sortedForShelf(goodsItem).firstOrNull()
        val itemName = goodsItem.name.extractLocalizedString(stateValues.appLanguage)
            ?: goodsItem.name.firstOrNull()?.value
            ?: "Unnamed item"

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(stateValues.marginTextField)
        ) {
            item {
                Text(
                    text = itemName,
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))

                StockCardInfoLine(stateValues.stringBarcode, goodsItem.allBarcodeValues().joinToString(", "), stateValues.TextColor)
                StockCardInfoLine(
                    stateValues.stringCategory,
                    goodsItem.categoryIds.mapNotNull { goodsCategoryName(it) }.joinToString(", "),
                    stateValues.TextColor
                )
                StockCardInfoLine(stateValues.stringMeasurementUnit, goodsItem.measurementUnitId, stateValues.TextColor)
                StockCardInfoLine(localizedStringResource(138, "Batches"), batches.size.toString(), stateValues.TextColor)

                StockCompactPromotionPriceInfoLines(
                    listOfNotNull(
                        goodsItem.promotedPriceForTransaction(0, SALE_METHOD_RETAIL, 1.0, activeBatch)
                            .takeIf { goodsItem.salePrices.isNotEmpty() || activeBatch?.salePriceOverride != null || it.hasPriceChange }
                            ?.let { StockPromotedPriceDisplayLine(stateValues.stringSale, it) },
                        goodsItem.promotedPriceForTransaction(1, SALE_METHOD_RETAIL, 1.0, activeBatch)
                            .takeIf { goodsItem.returnPrices.isNotEmpty() || activeBatch?.returnPriceOverride != null || it.hasPriceChange }
                            ?.let { StockPromotedPriceDisplayLine(stateValues.stringReturn, it) },
                        goodsItem.promotedPriceForTransaction(2, SALE_METHOD_RETAIL, 1.0, activeBatch)
                            .takeIf { goodsItem.supplyPrices.isNotEmpty() || activeBatch != null || it.hasPriceChange }
                            ?.let { StockPromotedPriceDisplayLine(stateValues.stringSupply, it) }
                    ),
                    stateValues.TextColor
                )

                if (goodsItem.promotions.isNotEmpty()) {
                    StockCardInfoLine(
                        localizedStringResource(920, "Promos"),
                        goodsItem.promotions.count { it.isActiveAt() }.takeIf { it > 0 }?.toString() ?: goodsItem.promotions.size.toString(),
                        stateValues.TextColor
                    )
                }

                goodsItem.description.takeIf { it.isNotEmpty() }?.let {
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    Text(stateValues.stringDescription, color = stateValues.TextColor, fontWeight = FontWeight.Bold)
                    it.forEach { line ->
                        Text(
                            text = "${line.language}: ${line.value}",
                            color = stateValues.TextColor,
                            fontSize = stateValues.textSize
                        )
                    }
                }

                goodsItem.note?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(stateValues.marginTextFieldGroup))
                    Text(localizedStringResource(266, "Note"), color = stateValues.TextColor, fontWeight = FontWeight.Bold)
                    Text(it, color = stateValues.TextColor, fontSize = stateValues.textSize)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actionButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(138, "Batches")
            ) {
                coroutineScope.launch {
                    NavigationScreenModel.Stock.GoodsItemBatches.setState(
                        NavigationScreenModel.Stock.GoodsItemBatches.KEY_STATE_GOODS_ITEM_ID to goodsItem.id
                    )
                    Navigation.Stock.go(NavigationScreenModel.Stock.GoodsItemBatches, remove = true, forceSecond = true)
                }
            }

            actionButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(203, "Supplier prices")
            ) {
                coroutineScope.launch {
                    NavigationScreenModel.Stock.GoodsItemSupplierPrices.setState(
                        NavigationScreenModel.Stock.GoodsItemSupplierPrices.KEY_STATE_GOODS_ITEM_ID to goodsItem.id
                    )
                    Navigation.Stock.go(NavigationScreenModel.Stock.GoodsItemSupplierPrices, remove = true, forceSecond = true)
                }
            }

            actionButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(1288, "Print item label"),
                iconPath = stateValues.drawablePathIconLabelPrinter,
                iconRes = stateValues.drawableResIconLabelPrinter.value,
                confirmationRequired = false,
                onClick = { showLabelPrintSheet = true }
            )
        }
    }
}

@Composable
fun AppConfiguration.StockGoodsItemBatchesScreen() {
    val goodsItem = rememberSelectedStockItem(
        stateHost = NavigationScreenModel.Stock.GoodsItemBatches,
        stateKey = NavigationScreenModel.Stock.GoodsItemBatches.KEY_STATE_GOODS_ITEM_ID
    )

    StockScreenScaffold(
        title = localizedStringResource(138, "Batches")
    ) {
        StockAddEditBatchesPage(
            modifier = Modifier.weight(1f),
            goodsItem = goodsItem
        )
    }
}

@Composable
fun AppConfiguration.StockGoodsItemSupplierPricesScreen() {
    val goodsItem = rememberSelectedStockItem(
        stateHost = NavigationScreenModel.Stock.GoodsItemSupplierPrices,
        stateKey = NavigationScreenModel.Stock.GoodsItemSupplierPrices.KEY_STATE_GOODS_ITEM_ID
    )

    StockScreenScaffold(
        title = localizedStringResource(203, "Supplier prices")
    ) {
        StockSupplierPricesPage(
            modifier = Modifier.weight(1f),
            goodsItem = goodsItem
        )
    }
}

@Composable
fun AppConfiguration.StockGoodsItemOrdersScreen() {
    val goodsItem = rememberSelectedStockItem(
        stateHost = NavigationScreenModel.Stock.GoodsItemOrders,
        stateKey = NavigationScreenModel.Stock.GoodsItemOrders.KEY_STATE_GOODS_ITEM_ID
    )

    StockScreenScaffold(
        title = localizedStringResource(254, "Orders")
    ) {
        SupplierOrdersForGoodsItemContent(
            modifier = Modifier.weight(1f),
            goodsItem = goodsItem
        )
    }
}


@Composable
fun AppConfiguration.genericTextField(
    modifier: Modifier = Modifier,

    titleText: String = "",

    stateHost: StateHost? = null,
    stateKey: String? = null,
    identityKey: String? = null,

    enabled: Boolean = true,
    readOnly: Boolean = false,
    wide: Boolean = false,
    singleLine: Boolean = true,
    adaptiveMultiline: Boolean = false,
    valueInitial: String? = null,

    textSize: TextUnit = stateValues.textSize,
    textColor: Color = stateValues.TextColor,

    titleTextSize: TextUnit = stateValues.accentTextSize,
    titleTextColor: Color = textColor,

    isFocusedInitial: Boolean = false,
    autoFocus: Boolean = true,
    updateIsFocusedAction: ((FocusState) -> Unit)? = null,
    forceRefocus: Boolean = false,
    placeholderText: String = "",
    placeholderTextSize: TextUnit = stateValues.textSize,
    placeholderTextColor: Color = stateValues.PlaceholderTextColor,

    selectionFocusTextColor: Color = stateValues.AccentTextColor,
    selectionBackgroundColor: Color = stateValues.AccentColor,

    focusedBorderWidth: Dp = stateValues.focusedBorderWidth,
    unfocusedBorderWidth: Dp = stateValues.unfocusedBorderWidth,

    focusedBorderColor: Color = stateValues.AccentColor,
    unfocusedBorderColor: Color = textColor,

    backgroundColor: Color = stateValues.BackgroundColor,

    cornerRadius: Dp = stateValues.cornerRadius,
    cornerShape: Shape = RoundedCornerShape(cornerRadius),
    keyboardType: KeyboardType = KeyboardType.Text,
    imeWithAction: ImeWithAction? = null,

    leadingIconPath: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    leadingIconContentDescription: String = placeholderText,
    trailingIcon: @Composable (() -> Unit)? = null,
    trailingIconExtraLeadingPath: String? = null,
    trailingIconExtraLeadingContentDescription: String = placeholderText,
    trailingIconExtraLeadingOnClick: ((String, (String, Boolean) -> Unit) -> Unit)? = null,
    trailingIconExtraPath: String? = null,
    trailingIconExtraContentDescription: String = placeholderText,
    trailingIconExtraOnClick: (() -> Unit)? = null,
    captureTransactionBarcodeInput: Boolean = false,
    enableVoiceInput: Boolean = true,
    visualTransformation: (TextFieldValue) -> TransformedText = {
        getTransformedTextWithSelectionFocusTextColor(it, selectionFocusTextColor)
    },
    showClearButton: Boolean = true,
    contentInvalidText: String? = null,
    onContentValidityCheck: ((String) -> Boolean)? = null,
    onFilterValue: ((String) -> Boolean)? = null,
    onTransformValue: ((String) -> String)? = null,
    successHighlightPulseKey: Int = 0,
    onValueChange: ((String, () -> Unit) -> Unit)? = null
): GenericTextFieldContent {

    val state: Map<String, String>? by stateHost?.state?.collectAsState() ?: remember {
        mutableStateOf(emptyMap())
    }

    val stateValue = state?.get(stateKey)
    val persistentTextDraftKey = if (shouldPersistUiTextDraft(keyboardType, stateKey)) {
        persistentUiDraftKey(stateHost, stateKey)
    } else {
        null
    }
    val persistentTextDraftMetaKey = if (persistentTextDraftKey != null) {
        persistentUiDraftKey(stateHost, stateKey, suffix = "meta")
    } else {
        null
    }
    val textFieldMetaStateKey = if (persistentTextDraftKey != null) persistentUiTextFieldMetaStateKey(stateKey) else null
    val stateMetaValue = textFieldMetaStateKey?.let { state?.get(it) }

    val initialTextFieldText = stateValue ?: valueInitial ?: ""
    val initialTextFieldMeta = decodePersistentTextFieldMeta(stateMetaValue, initialTextFieldText.length)
    val textFieldIdentityKey = identityKey
        ?: persistentTextDraftKey
        ?: stateKey
        ?: listOf(titleText, placeholderText, leadingIconPath.orEmpty()).joinToString("|")

    var textFieldValue by rememberSaveable(textFieldIdentityKey, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialTextFieldText, selection = initialTextFieldMeta.selection))
    }

    var persistentTextDraftLoaded by rememberSaveable(textFieldIdentityKey, persistentTextDraftKey ?: "no_persistent_text_draft") {
        mutableStateOf(false)
    }

    var pendingPersistentTextDraft by remember(persistentTextDraftKey) { mutableStateOf<String?>(null) }
    var pendingPersistentTextDraftVersion by remember(persistentTextDraftKey) { mutableStateOf(0) }
    var pendingPersistentTextFieldMeta by remember(persistentTextDraftMetaKey) { mutableStateOf<String?>(null) }
    var pendingPersistentTextFieldMetaVersion by remember(persistentTextDraftMetaKey) { mutableStateOf(0) }

    fun savePersistentTextDraft(nextText: String?) {
        if (persistentTextDraftKey == null) return
        pendingPersistentTextDraft = nextText
        pendingPersistentTextDraftVersion += 1
    }

    fun savePersistentTextFieldMeta(nextValue: TextFieldValue, focused: Boolean) {
        if (textFieldMetaStateKey == null && persistentTextDraftMetaKey == null) return
        val encoded = encodePersistentTextFieldMeta(nextValue, focused)

        textFieldMetaStateKey?.let { keyName ->
            coroutineScope.launch {
                stateHost?.setState(keyName to encoded)
            }
        }

        if (persistentTextDraftMetaKey != null) {
            pendingPersistentTextFieldMeta = encoded
            pendingPersistentTextFieldMetaVersion += 1
        }
    }

    LaunchedEffect(persistentTextDraftKey, pendingPersistentTextDraftVersion) {
        val key = persistentTextDraftKey ?: return@LaunchedEffect
        if (pendingPersistentTextDraftVersion <= 0) return@LaunchedEffect
        val textSnapshot = pendingPersistentTextDraft
        // Debounce per-keystroke disk / keychain / encrypted-storage writes.
        // The field state updates immediately; persistence is flushed after the user pauses.
        delay(360)
        setPersistentUiDraftValue?.invoke(key, textSnapshot)
    }

    LaunchedEffect(persistentTextDraftMetaKey, pendingPersistentTextFieldMetaVersion) {
        val key = persistentTextDraftMetaKey ?: return@LaunchedEffect
        if (pendingPersistentTextFieldMetaVersion <= 0) return@LaunchedEffect
        val metaSnapshot = pendingPersistentTextFieldMeta
        delay(360)
        setPersistentUiDraftValue?.invoke(key, metaSnapshot)
    }

    LaunchedEffect(persistentTextDraftKey, persistentTextDraftMetaKey) {
        persistentTextDraftLoaded = false
        val key = persistentTextDraftKey ?: return@LaunchedEffect
        val stored = getPersistentUiDraftValue?.invoke(key)
        val storedMeta = persistentTextDraftMetaKey?.let { getPersistentUiDraftValue?.invoke(it) }
        persistentTextDraftLoaded = true
        if (stored != null && stored != stateValue && stored != textFieldValue.text) {
            val externalInitial = stateValue ?: valueInitial
            if (externalInitial == null || textFieldValue.text == externalInitial || textFieldValue.text.isBlank()) {
                val restoredMeta = decodePersistentTextFieldMeta(storedMeta, stored.length)
                textFieldValue = TextFieldValue(stored, selection = restoredMeta.selection)
                stateKey?.let { keyName ->
                    stateHost?.setState(keyName to stored)
                }
                textFieldMetaStateKey?.let { keyName ->
                    stateHost?.setState(keyName to encodePersistentTextFieldMeta(textFieldValue.copy(text = stored, selection = restoredMeta.selection), restoredMeta.focused))
                }
            }
        } else if (storedMeta != null) {
            textFieldMetaStateKey?.let { keyName ->
                stateHost?.setState(keyName to storedMeta)
            }
        }
    }

    var lastLocalTextEditMillis by remember(textFieldIdentityKey) {
        mutableStateOf(0L)
    }

    var isFocused by rememberSaveable(textFieldIdentityKey) {
        mutableStateOf(isFocusedInitial || (initialTextFieldMeta.focused && platformAllowsAutomaticTextFieldFocus()))
    }

    var focusRequester by remember(textFieldIdentityKey) {
        mutableStateOf(FocusRequester())
    }

    var restoredPersistentFocusKey by rememberSaveable(textFieldMetaStateKey ?: "no_persistent_text_field_meta") {
        mutableStateOf("")
    }

    LaunchedEffect(stateMetaValue, enabled, readOnly) {
        val encoded = stateMetaValue ?: return@LaunchedEffect
        val meta = decodePersistentTextFieldMeta(encoded, textFieldValue.text.length)
        if (!isFocused && textFieldValue.selection != meta.selection) {
            textFieldValue = textFieldValue.copy(selection = meta.selection)
        }
        if (!isFocused && meta.focused && platformAllowsAutomaticTextFieldFocus() && enabled && !readOnly) {
            val focusKey = listOf(textFieldMetaStateKey.orEmpty(), encoded, textFieldValue.text).joinToString("|")
            if (focusKey != restoredPersistentFocusKey) {
                restoredPersistentFocusKey = focusKey
                delay(280)
                isFocused = true
                focusRequester.requestFocus()
            }
        }
    }

    var isContentValid by rememberSaveable(textFieldIdentityKey) {
        mutableStateOf(true)
    }

    var voicePermissionDialogState by rememberSaveable(textFieldIdentityKey) {
        mutableStateOf<PlatformPermissionState?>(null)
    }
    val voicePermissionRequestText = voiceInputPermissionTexts(this)

    var isVoiceListening by remember(textFieldIdentityKey) {
        mutableStateOf(false)
    }

    var voiceLevel by remember(textFieldIdentityKey) {
        mutableStateOf(0f)
    }

    var voiceStatusText by remember(textFieldIdentityKey) {
        mutableStateOf("")
    }

    var successHighlightActive by remember(textFieldIdentityKey) {
        mutableStateOf(false)
    }

    val automaticFocusScopeKey = stateHost.autoFocusScopeKey()
    val automaticFocusFieldKey = stateKey ?: listOf(titleText, placeholderText, leadingIconPath.orEmpty()).joinToString("|")

    LaunchedEffect(successHighlightPulseKey) {
        if (successHighlightPulseKey != 0) {
            successHighlightActive = true
            delay(560)
            successHighlightActive = false
        }
    }

    fun applyExternalTextReplacement(rawText: String, applyTransform: Boolean = true) {
        val nextText = if (applyTransform) onTransformValue?.invoke(rawText) ?: rawText else rawText
        if (onFilterValue != null && !onFilterValue(nextText)) return

        val nextValue = TextFieldValue(nextText, selection = TextRange(nextText.length))

        val applyChange: () -> Unit = {
            lastLocalTextEditMillis = getCurrentTimeMillis()
            textFieldValue = nextValue
            val key = stateKey
            if (key != null) {
                coroutineScope.launch {
                    stateHost?.setState(key to nextText)
                }
            }
            savePersistentTextDraft(nextText)
            savePersistentTextFieldMeta(nextValue, isFocused)
            Unit
        }

        if (onValueChange != null) {
            onValueChange(nextText, applyChange)
        } else {
            applyChange()
        }
    }

    val voiceInputAvailable = enableVoiceInput &&
            !captureTransactionBarcodeInput &&
            enabled &&
            !readOnly &&
            platformSupportsVoiceInput() &&
            keyboardType != KeyboardType.Password

    fun postVoiceInputUnavailable() {
        postInAppNotification(
            localizedStringResourceMessage(
                id = 1009,
                main = "Voice input is not available on this device",
                ru = "Голосовой ввод недоступен на этом устройстве",
                kk = "Бұл құрылғыда дауыспен енгізу қолжетімсіз"
            ),
            NotificationType.Negative,
            transient = true
        )
    }

    fun startVoiceInputForThisField() {
        val starter = startPlatformVoiceInput
        if (starter == null || !platformSupportsVoiceInput()) {
            postVoiceInputUnavailable()
            return
        }

        isVoiceListening = true
        voiceLevel = 0.18f
        voiceStatusText = localizedStringResource(1007, "Listening…")

        coroutineScope.launch {
            starter(
                voicePermissionRequestText,
                VoiceInputCallbacks(
                    onPartialText = { partial ->
                        if (partial.isNotBlank()) {
                            voiceStatusText = partial
                            applyExternalTextReplacement(partial)
                        }
                    },
                    onFinalText = { finalText ->
                        if (finalText.isNotBlank()) {
                            voiceStatusText = finalText
                            applyExternalTextReplacement(finalText)
                        }
                    },
                    onAmplitude = { level ->
                        voiceLevel = level.coerceIn(0f, 1f)
                    },
                    onDetectedLanguage = { languageTag ->
                        val displayName = voiceInputLanguageDisplayName(languageTag)
                        if (displayName.isNotBlank()) {
                            voiceStatusText = "${localizedStringResource(1057, "Detected language")}: $displayName"
                        }
                    },
                    onDenied = {
                        isVoiceListening = false
                        voiceLevel = 0f
                        postInAppNotification(
                            localizedStringResourceMessage(
                                id = 1005,
                                main = "Microphone access was denied",
                                ru = "Доступ к микрофону запрещён",
                                kk = "Микрофонға рұқсат берілмеді"
                            ),
                            NotificationType.Negative,
                            transient = true
                        )
                    },
                    onError = { message ->
                        isVoiceListening = false
                        voiceLevel = 0f
                        postInAppNotification(
                            listOf(
                                LocalizedStringDataModel("main", message.ifBlank { localizedStringResource(1009, "Voice input is not available on this device") }),
                                LocalizedStringDataModel("en", message.ifBlank { localizedStringResource(1009, "Voice input is not available on this device") }),
                                LocalizedStringDataModel("ru", message.ifBlank { "Голосовой ввод недоступен" }),
                                LocalizedStringDataModel("kk", message.ifBlank { "Дауыспен енгізу қолжетімсіз" })
                            ),
                            NotificationType.Negative,
                            transient = true
                        )
                    },
                    onFinished = {
                        isVoiceListening = false
                        voiceLevel = 0f
                    }
                )
            )
        }
    }

    val openVoicePermissionSettings: () -> Unit = {
        coroutineScope.launch {
            openPlatformAppSettings?.invoke(PlatformPermissionKind.Microphone)
        }
    }

    voicePermissionDialogState?.let { permissionState ->
        VoiceInputPermissionDialog(
            texts = voicePermissionRequestText,
            permissionState = permissionState,
            onDismiss = { voicePermissionDialogState = null },
            onConfirm = {
                voicePermissionDialogState = null
                isFocused = true
                focusRequester.requestFocus()
                if (permissionState.needsSettingsText()) {
                    openVoicePermissionSettings()
                } else {
                    startVoiceInputForThisField()
                }
            }
        )
    }

    LaunchedEffect(valueInitial) {
        if (valueInitial != null && valueInitial != textFieldValue.text) {
            val cameRightAfterLocalEdit = getCurrentTimeMillis() - lastLocalTextEditMillis < 450L
            val externalClearAfterSuccessfulAction = valueInitial.isEmpty()
            if (externalClearAfterSuccessfulAction || (!isFocused && !cameRightAfterLocalEdit)) {
                val nextValue = TextFieldValue(valueInitial, selection = TextRange(valueInitial.length))
                textFieldValue = nextValue
                if (externalClearAfterSuccessfulAction) savePersistentTextDraft("")
                savePersistentTextFieldMeta(nextValue, isFocused)
            }
        }
    }

    LaunchedEffect(stateValue) {
        if (stateKey != null && stateValue != null && stateValue != textFieldValue.text) {
            val cameRightAfterLocalEdit = getCurrentTimeMillis() - lastLocalTextEditMillis < 450L
            val externalClearAfterSuccessfulAction = stateValue.isEmpty()
            if (externalClearAfterSuccessfulAction || (!isFocused && !cameRightAfterLocalEdit)) {
                val nextValue = TextFieldValue(stateValue, selection = TextRange(stateValue.length))
                textFieldValue = nextValue
                if (externalClearAfterSuccessfulAction) savePersistentTextDraft("")
                savePersistentTextFieldMeta(nextValue, isFocused)
            }
        }
    }

    Column(
        modifier = modifier
    ) {
        val titleTextPresent = titleText.isNotEmpty() && titleText.isNotBlank()

        if (titleTextPresent)
            Text(
                modifier = Modifier
                    .padding(bottom = 4.dp),
                text = titleText,
                style = TextStyle(
                    color = titleTextColor,
                    fontSize = titleTextSize,
                    fontWeight = FontWeight.Bold
                )
            )

        val textSelectionColors = TextSelectionColors(
            handleColor = selectionBackgroundColor,
            backgroundColor = selectionBackgroundColor
        )

        val adaptiveVisualLineCount = if (adaptiveMultiline) {
            textFieldValue.text
                .split('\n')
                .sumOf { line -> (line.length / 34) + 1 }
                .coerceIn(1, 6)
        } else {
            1
        }

        val adaptiveTextFieldHeight = if (adaptiveMultiline) {
            val rawHeight = (stateValues.textFieldHeight.value + (adaptiveVisualLineCount - 1) * 24f).dp
            if (rawHeight > stateValues.wideTextFieldHeight) stateValues.wideTextFieldHeight else rawHeight
        } else {
            stateValues.textFieldHeight
        }
        val animatedTextFieldHeight by animateDpAsState(
            targetValue = when {
                adaptiveMultiline -> adaptiveTextFieldHeight
                wide -> stateValues.wideTextFieldHeight
                else -> stateValues.textFieldHeight
            },
            animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS),
            label = "textFieldHeight"
        )
        val animatedBorderWidth by animateDpAsState(
            targetValue = if (isFocused) focusedBorderWidth else unfocusedBorderWidth,
            animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS),
            label = "textFieldBorderWidth"
        )
        val animatedBorderColor by animateColorAsState(
            targetValue = when {
                successHighlightActive -> stateValues.OkayColor
                isFocused -> focusedBorderColor
                else -> unfocusedBorderColor
            },
            animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS),
            label = "textFieldBorderColor"
        )
        val animatedBackgroundColor by animateColorAsState(
            targetValue = backgroundColor,
            animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS),
            label = "textFieldBackgroundColor"
        )
        val animatedTextInputHighlightColor by animateColorAsState(
            targetValue = if (successHighlightActive) stateValues.OkayColor.copy(alpha = 0.20f) else Color.Transparent,
            animationSpec = tween(durationMillis = if (successHighlightActive) AITA_MOTION_FAST_MILLIS else 420),
            label = "textFieldSuccessHighlightColor"
        )
        val animatedVoiceLevel by animateFloatAsState(
            targetValue = if (isVoiceListening) voiceLevel.coerceAtLeast(0.12f) else 0f,
            animationSpec = tween(durationMillis = 90),
            label = "voiceInputLevel"
        )
        val animatedVoiceBorderColor by animateColorAsState(
            targetValue = if (isVoiceListening) stateValues.AccentColor else textColor.copy(alpha = 0.66f),
            animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS),
            label = "voiceInputBorderColor"
        )

        CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = onValueChange@ { rawValue ->
                    lastLocalTextEditMillis = getCurrentTimeMillis()
                    val nextText = onTransformValue?.invoke(rawValue.text) ?: rawValue.text
                    val nextSelection = if (nextText == rawValue.text) {
                        rawValue.selection
                    } else {
                        TextRange(
                            rawValue.selection.start.coerceIn(0, nextText.length),
                            rawValue.selection.end.coerceIn(0, nextText.length)
                        )
                    }
                    val nextValue = rawValue.copy(
                        text = nextText,
                        selection = nextSelection
                    )

                    val barcodeHandler = if (captureTransactionBarcodeInput) activeTransactionBarcodeHandler else null
                    if (barcodeHandler != null && barcodeHandler(nextText)) {
                        val emptyValue = TextFieldValue("")
                        textFieldValue = emptyValue
                        stateKey?.run {
                            coroutineScope.launch {
                                stateHost?.setState(stateKey to "")
                            }
                        }
                        savePersistentTextDraft("")
                        savePersistentTextFieldMeta(emptyValue, isFocused)
                        return@onValueChange
                    }

                    if (onValueChange != null) {
                        onValueChange(nextText) {
                            if (onFilterValue == null || onFilterValue(nextText)) {
                                textFieldValue = nextValue

                                stateKey?.run {
                                    coroutineScope.launch {
                                        stateHost?.setState(stateKey to nextText)
                                    }
                                }
                                savePersistentTextDraft(nextText)
                                savePersistentTextFieldMeta(nextValue, isFocused)
                            }
                        }
                    } else {
                        if (onFilterValue == null || onFilterValue(nextText)) {
                            textFieldValue = nextValue
                            stateKey?.run {
                                coroutineScope.launch {
                                    stateHost?.setState(stateKey to nextText)
                                }
                            }
                            savePersistentTextDraft(nextText)
                            savePersistentTextFieldMeta(nextValue, isFocused)
                        }
                    }
                },
                enabled = enabled,
                readOnly = readOnly,
                modifier = Modifier
                    .height(animatedTextFieldHeight)
                    .aitaContentMotion()
                    .run {
                        if (focusedBorderWidth.value > 0f || unfocusedBorderWidth.value > 0f)
                            foregroundTactileShadow(cornerRadius, elevated = false)
                        else
                            this
                    }
                    .clip(cornerShape)
                    .background(animatedBackgroundColor)
                    .border(
                        width = animatedBorderWidth,
                        color = animatedBorderColor,
                        shape = cornerShape
                    )
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        savePersistentTextFieldMeta(textFieldValue, it.isFocused)

                        updateIsFocusedAction?.invoke(it)
                    },
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = keyboardType,
                    imeAction = (imeWithAction ?: ImeWithAction(ime = ImeAction.Default)).ime
                ),
                keyboardActions = (imeWithAction ?: ImeWithAction(ime = ImeAction.Default)).getKeyboardActions(),
                textStyle = TextStyle(
                    fontSize = textSize,
                    lineHeight = (textSize.value * 1.28f).sp,
                    color = textColor
                ),
                visualTransformation = {
                    visualTransformation(textFieldValue)
                },
                singleLine = singleLine,
                cursorBrush = SolidColor(selectionBackgroundColor),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            leadingIcon?.invoke() ?: leadingIconPath?.run {
                                CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
                                    CpImage(
                                        modifier = Modifier
                                            .padding(
                                                start = 12.dp,
                                                top = stateValues.textFieldIconPadding,
                                                bottom = stateValues.textFieldIconPadding
                                            )
                                            .size(stateValues.iconSize),
                                        url = leadingIconPath,
                                        fallbackRes = Res.drawable._9_0,
                                        contentDescription = leadingIconContentDescription,
                                        tintColor = textColor
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .run {
//                    if (wide)
//                      fillMaxHeight().padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
//                    else
                                        when {
                                            adaptiveMultiline -> fillMaxHeight().padding(start = 12.dp, top = if (adaptiveVisualLineCount <= 1) 0.dp else 10.dp, bottom = if (adaptiveVisualLineCount <= 1) 0.dp else 10.dp)
                                            wide -> fillMaxHeight().padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                                            else -> fillMaxHeight().padding(start = 12.dp)
                                        }
                                    }
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .background(animatedTextInputHighlightColor),
                                contentAlignment = if (!adaptiveMultiline || adaptiveVisualLineCount <= 1) Alignment.CenterStart else Alignment.TopStart
                            ) {
                                Text(
                                    text = if (textFieldValue.text.isEmpty()) placeholderText else "",
                                    fontSize = placeholderTextSize,
                                    color = placeholderTextColor,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                innerTextField()
                            }

                            CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AnimatedVisibility(
                                        visible = isFocused && voiceInputAvailable,
                                        enter = fadeIn(animationSpec = tween(AITA_MOTION_FAST_MILLIS)) + expandHorizontally(animationSpec = tween(AITA_MOTION_FAST_MILLIS)),
                                        exit = fadeOut(animationSpec = tween(AITA_MOTION_FAST_MILLIS)) + shrinkHorizontally(animationSpec = tween(AITA_MOTION_FAST_MILLIS))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .padding(vertical = stateValues.textFieldIconPadding * 0.72f, horizontal = 4.dp)
                                                .clip(RoundedCornerShape(cornerRadius))
                                                .background(if (isVoiceListening) stateValues.AccentColor.copy(alpha = 0.16f) else Color.Transparent)
                                                .border(
                                                    width = if (isVoiceListening) 1.4.dp else 1.dp,
                                                    color = animatedVoiceBorderColor,
                                                    shape = RoundedCornerShape(cornerRadius)
                                                )
                                                .aitaClickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = ripple(color = textColor, radius = cornerRadius),
                                                    onClick = {
                                                        isFocused = true
                                                        focusRequester.requestFocus()
                                                        if (isVoiceListening) {
                                                            stopPlatformVoiceInput?.invoke()
                                                            isVoiceListening = false
                                                            voiceLevel = 0f
                                                        } else {
                                                            coroutineScope.launch {
                                                                when (val permissionState = getVoiceInputPermissionState?.invoke()) {
                                                                    null, PlatformPermissionState.Granted -> startVoiceInputForThisField()
                                                                    PlatformPermissionState.Unavailable -> postVoiceInputUnavailable()
                                                                    else -> voicePermissionDialogState = permissionState
                                                                }
                                                            }
                                                        }
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CpImage(
                                                modifier = Modifier
                                                    .padding(horizontal = 9.dp)
                                                    .size(stateValues.iconSize),
                                                url = stateValues.drawablePathIconVoiceInput,
                                                fallbackRes = Res.drawable._52_0,
                                                contentDescription = localizedStringResource(1002, "Voice input"),
                                                tintColor = if (isVoiceListening) stateValues.AccentColor else textColor
                                            )
                                        }
                                    }

                                    if (trailingIcon != null) {
                                        trailingIcon.invoke()
                                    } else {
                                        trailingIconExtraLeadingPath?.run {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .width(stateValues.textFieldHeight)
                                                    .aitaClickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = ripple(color = textColor, radius = cornerRadius),
                                                        onClick = {
                                                            focusRequester.requestFocus()
                                                            isFocused = true
                                                            trailingIconExtraLeadingOnClick?.invoke(textFieldValue.text, ::applyExternalTextReplacement)
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CpImage(
                                                    modifier = Modifier.size(stateValues.iconSize),
                                                    url = this@run,
                                                    fallbackRes = Res.drawable._64_0,
                                                    contentDescription = trailingIconExtraLeadingContentDescription,
                                                    tintColor = textColor
                                                )
                                            }
                                        }

                                        trailingIconExtraPath?.run {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .width(stateValues.textFieldHeight)
                                                    .aitaClickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = ripple(color = textColor, radius = cornerRadius),
                                                        onClick = {
                                                            focusRequester.requestFocus()
                                                            isFocused = true
                                                            trailingIconExtraOnClick?.invoke()
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CpImage(
                                                    modifier = Modifier.size(stateValues.iconSize),
                                                    url = this@run,
                                                    fallbackRes = Res.drawable._9_0,
                                                    contentDescription = trailingIconExtraContentDescription,
                                                    tintColor = textColor
                                                )
                                            }
                                        }
                                    }

                                    if (showClearButton && textFieldValue.text.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(stateValues.textFieldHeight)
                                                .aitaClickable(
                                                    interactionSource = remember {
                                                        MutableInteractionSource()
                                                    },
                                                    indication = ripple(color = textColor, radius = cornerRadius)
                                                ) {
                                                    val applyClear = {
                                                        lastLocalTextEditMillis = getCurrentTimeMillis()
                                                        val emptyValue = TextFieldValue("")
                                                        textFieldValue = emptyValue

                                                        stateKey?.run {
                                                            coroutineScope.launch {
                                                                stateHost?.setState(stateKey to "")
                                                            }
                                                        }
                                                        savePersistentTextDraft("")
                                                        savePersistentTextFieldMeta(emptyValue, true)
                                                    }

                                                    if (onValueChange != null) {
                                                        onValueChange("") {
                                                            applyClear()
                                                        }
                                                    } else {
                                                        applyClear()
                                                    }

                                                    isFocused = true
                                                    focusRequester.requestFocus()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CpImage(
                                                modifier = Modifier
                                                    .size(stateValues.iconSize),
                                                url = stateValues.drawablePathIconCancel,
                                                fallbackRes = Res.drawable._9_0,
                                                contentDescription = stateValues.stringClear,
                                                tintColor = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = isVoiceListening && voiceInputAvailable,
            enter = fadeIn(animationSpec = tween(AITA_MOTION_FAST_MILLIS)) + expandVertically(animationSpec = tween(AITA_MOTION_FAST_MILLIS)),
            exit = fadeOut(animationSpec = tween(AITA_MOTION_FAST_MILLIS)) + shrinkVertically(animationSpec = tween(AITA_MOTION_FAST_MILLIS))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(stateValues.PlaceholderTextColor.copy(alpha = 0.16f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedVoiceLevel.coerceIn(0.08f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(stateValues.AccentColor.copy(alpha = 0.86f))
                    )
                }

                if (voiceStatusText.isNotBlank()) {
                    Text(
                        modifier = Modifier.padding(top = 3.dp),
                        text = voiceStatusText,
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (!isContentValid && contentInvalidText != null && contentInvalidText.isNotEmpty() && contentInvalidText.isNotBlank()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                text = contentInvalidText,
                style = TextStyle(
                    color = stateValues.ErrorColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
        }

        LaunchedEffect(isFocusedInitial, automaticFocusScopeKey, automaticFocusFieldKey) {
            if (isFocusedInitial && platformAllowsAutomaticTextFieldFocus()) {
                if (autoFocusedTextFieldKeysByScope[automaticFocusScopeKey] == null) {
                    autoFocusedTextFieldKeysByScope[automaticFocusScopeKey] = automaticFocusFieldKey
                }

                if (autoFocusedTextFieldKeysByScope[automaticFocusScopeKey] == automaticFocusFieldKey) {
                    delay(300)
                    isFocused = true
                    focusRequester.requestFocus()
                }
            }
        }

        LaunchedEffect(autoFocus, enabled, readOnly, stateHost, stateKey, titleText, placeholderText, automaticFocusScopeKey, automaticFocusFieldKey) {
            if (autoFocus && platformAllowsAutomaticTextFieldFocus() && enabled && !readOnly && !isFocusedInitial) {
                if (autoFocusedTextFieldKeysByScope[automaticFocusScopeKey] == null) {
                    autoFocusedTextFieldKeysByScope[automaticFocusScopeKey] = automaticFocusFieldKey
                    delay(260)
                    isFocused = true
                    focusRequester.requestFocus()
                }
            }
        }

        LaunchedEffect(forceRefocus, isFocused) {
            if (forceRefocus && platformAllowsAutomaticTextFieldFocus() && !isFocused) {
                isFocused = true
                focusRequester.requestFocus()
            }
        }

        LaunchedEffect(isFocused) {
            if (!isFocused && isVoiceListening) {
                stopPlatformVoiceInput?.invoke()
                isVoiceListening = false
                voiceLevel = 0f
            }
        }
    }

    val content = GenericTextFieldContent(
        value = textFieldValue,
        isFocused = isFocused,
        focusRequester = focusRequester,
        isContentValid = isContentValid,
        onContentValidityCheck = onContentValidityCheck?.run {
            {
                val value = this(textFieldValue.text)
                isContentValid = value
                value
            }
        },
        onReset = {
            val emptyValue = TextFieldValue()
            textFieldValue = emptyValue
            stateKey?.let { keyName ->
                coroutineScope.launch {
                    stateHost?.setState(keyName to "")
                }
            }
            savePersistentTextDraft("")
            savePersistentTextFieldMeta(emptyValue, false)
        },
        onReplaceText = { rawText, applyTransform ->
            applyExternalTextReplacement(rawText, applyTransform)
        }
    )

    onContentValidityCheck?.let {
        LaunchedEffect(textFieldValue, isContentValid) {
            if (!isContentValid) {
                isContentValid = onContentValidityCheck(textFieldValue.text)
            }
        }
    }

    return content
}

class GenericTextFieldContent(
    var value: TextFieldValue,
    var isFocused: Boolean,
    var focusRequester: FocusRequester,
    var isContentValid: Boolean,
    val onContentValidityCheck: ((String) -> Boolean)? = null,
    val onReset: (() -> Unit)? = null,
    val onReplaceText: ((String, Boolean) -> Unit)? = null
) {

    fun checkContentValidity() {
        isContentValid = onContentValidityCheck?.invoke(value.text) ?: true
    }

    fun reset() {
        onReset?.invoke()
    }

    fun replaceText(rawText: String, applyTransform: Boolean = true) {
        value = TextFieldValue(rawText, selection = TextRange(rawText.length))
        onReplaceText?.invoke(rawText, applyTransform)
    }
}

fun String.toColor(): Color {
    return Color(toULong(radix = 16).toInt())
}

fun AppLanguageDataModel.mapIconRes(): DrawableResource {
    return when (language) {
        "en" -> Res.drawable.flag_en
        "ru" -> Res.drawable.flag_ru
        "tj" -> Res.drawable.flag_tj
        else -> Res.drawable.flag_kz
    }
}

fun CountryDataModel.mapIconRes(): DrawableResource {
    return when (locale) {
        "en" -> Res.drawable.flag_en
        "ru" -> Res.drawable.flag_ru
        "tj" -> Res.drawable.flag_tj
        else -> Res.drawable.flag_kz
    }
}

@Composable
fun AppConfiguration.responseText(
    invalidText: String,
    color: Color = stateValues.ErrorColor,
    showIf: () -> Boolean
): ResponseTextContent {
    var show by rememberSaveable {
        mutableStateOf(false)
    }

    show = showIf()

    if (show) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            text = invalidText,
            style = TextStyle(
                color = color,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }

    return ResponseTextContent(
        isActual = !show,
        onActualityCheck = {
            val value = showIf()
            show = value
            value
        }
    )
}

class ResponseTextContent(
    var isActual: Boolean,
    private val onActualityCheck: () -> Boolean
) {

    fun checkContentValidity() {
        isActual = onActualityCheck.invoke()
    }
}

@Composable
fun AppConfiguration.emailTextField(
    modifier: Modifier = Modifier,
    stateHost: StateHost,
    stateKey: String,
    valueInitial: String? = null,
    imeWithAction: ImeWithAction? = null,
): GenericTextFieldContent {

    return genericTextField(
        modifier = modifier,
        valueInitial = valueInitial,
        titleText = stateValues.stringEmail,
        stateHost = stateHost,
        stateKey = stateKey,
        placeholderText = stateValues.stringEnterEmailAddress,
        leadingIconPath = stateValues.drawablePathIconEmail,
        keyboardType = KeyboardType.Email,
        imeWithAction = imeWithAction ?: ImeWithAction.Default,
        contentInvalidText = stateValues.stringEmailMustBe,
        onContentValidityCheck = {
            it.checkAsEmail()
        }
    )
}

@Composable
fun AppConfiguration.dropdownListWidget(
    modifier: Modifier = Modifier,
    titleText: String,
    textColor: Color = stateValues.TextColor,
    titleTextSize: TextUnit = stateValues.accentTextSize,
    titleTextColor: Color = textColor,
    showId: Boolean = true,
    showName: Boolean = false,
    domains: List<SelectableDomain>,
    selectedInitial: String? = null,
    cornerRadius: Dp = stateValues.cornerRadius,
    search: Triple<String?, StateHost?, String?>? = null,
    onSelected: ((String) -> Unit)? = null
): DropdownListWidgetContent {

    val domainsKey = remember(domains) {
        domains.joinToString(separator = "|") { it.id }
    }

    fun normalizedSelectedId(source: String?): String {
        return domains.firstOrNull { it.id.equals(source, ignoreCase = true) }?.id
            ?: domains.firstOrNull()?.id
            ?: ""
    }

    var selectedId by rememberSaveable(domainsKey) {
        mutableStateOf(normalizedSelectedId(selectedInitial))
    }

    LaunchedEffect(selectedInitial, domainsKey) {
        val nextSelectedId = normalizedSelectedId(selectedInitial)
        if (nextSelectedId.isNotBlank() && nextSelectedId != selectedId) {
            selectedId = nextSelectedId
        }
    }

    val selected = remember(selectedId, domainsKey) {
        domains.find { it.id.equals(selectedId, ignoreCase = true) }
            ?: domains.firstOrNull()
    }

    val isDomainSelectionDropdownExpandedState = remember {
        MutableTransitionState(false)
            .apply {
                targetState = false
            }
    }

    Column(
        modifier = modifier
    ) {
        val titleTextPresent = titleText.isNotEmpty() && titleText.isNotBlank()

        if (titleTextPresent)
            Text(
                modifier = Modifier
                    .padding(bottom = 4.dp),
                text = titleText,
                style = TextStyle(
                    color = titleTextColor,
                    fontSize = titleTextSize,
                    fontWeight = FontWeight.Bold
                )
            )

        selected?.let { selectedDomain ->
            selectableDomainWidget(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(2.dp)
                    .foregroundTactileShadow(cornerRadius, elevated = false)
                    .clip(RoundedCornerShape(cornerRadius))
                    .height(stateValues.textFieldHeight)
                    .background(stateValues.BackgroundColor)
                    .border(
                        width = if (isDomainSelectionDropdownExpandedState.targetState) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                        color = if (isDomainSelectionDropdownExpandedState.targetState) stateValues.AccentColor else textColor,
                        shape = RoundedCornerShape(cornerRadius)
                    ),
                textColor = textColor,
                domain = selectedDomain,
                showId = showId,
                showName = showName,
                showExpansion = true
            ) {
                isDomainSelectionDropdownExpandedState.targetState =
                    !isDomainSelectionDropdownExpandedState.targetState
            }
        } ?: MessageText(
            modifier = Modifier
                .fillMaxWidth()
                .height(stateValues.textFieldHeight),
            text = stateValues.stringListEmpty,
            textSize = stateValues.textSize
        )

        if (domains.isNotEmpty()) {
            Spacer(modifier = Modifier.height(1.dp))

            AnimatedVisibility(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 0.dp, max = stateValues.screenHeight / 4),
                visibleState = isDomainSelectionDropdownExpandedState,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .padding(2.dp)
                        .foregroundTactileShadow(cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .fillMaxWidth()
                        .border(
                            width = stateValues.focusedBorderWidth,
                            color = stateValues.AccentColor,
                            shape = RoundedCornerShape(cornerRadius)
                        )
                ) {
                    val searchTextFieldContent: GenericTextFieldContent? = if (search != null) {
                        Spacer(modifier = Modifier.height(1.dp))

                        searchTextField(
                            modifier = Modifier
                                .fillMaxWidth(),
                            stateHost = search.second,
                            stateKey = search.third,
                            focusedBorderWidth = 0.dp,
                            unfocusedBorderWidth = 0.dp,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    } else null

                    if (search != null) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(textColor)
                                .height(stateValues.unfocusedBorderWidth)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(1.dp))
                    }

                    val query = searchTextFieldContent?.value?.text.orEmpty()
                    val visibleDomains = if (query.isNotBlank()) {
                        domains.filter { it.searchContains(query) }
                    } else {
                        domains
                    }

                    if (visibleDomains.isEmpty()) {
                        MessageText(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(stateValues.textFieldHeight),
                            text = stateValues.stringNoMatches,
                            textSize = stateValues.textSize
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = stateValues.screenHeight / 4)
                        ) {
                            items(
                                items = visibleDomains,
                                key = { it.id }
                            ) { domain ->
                                selectableDomainWidget(
                                    modifier = Modifier
                                        .fillParentMaxWidth(),
                                    textColor = textColor,
                                    domain = domain,
                                    showId = showId,
                                    showName = showName
                                ) {
                                    selectedId = domain.id
                                    onSelected?.invoke(domain.id)

                                    isDomainSelectionDropdownExpandedState.targetState =
                                        !isDomainSelectionDropdownExpandedState.targetState
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    return DropdownListWidgetContent(selectedId = selectedId)
}

class DropdownListWidgetContent(var selectedId: String)

@Composable
fun AppConfiguration.domainSelectionTextFieldGroupWidget(
    modifier: Modifier = Modifier,
    titleText: String,
    placeholderText: String,
    stateHost: StateHost? = null,
    stateKey: String? = null,
    valueInitial: List<DomainSelectionTextFieldGroupItemContent>? = null,
    domains: List<SelectableDomain>,
    secondaryDomains: List<SelectableDomain>? = null,
    secondaryDomainsShowId: Boolean = true,
    secondaryDomainsShowName: Boolean = true,
    addDomainActionButtonText: String,
    addSecondaryDomainActionButtonText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isFocusedInitial: Boolean = false,
    onFilterValue: ((String, String, String?) -> Boolean)? = null,
    onContentValidityCheck: ((String, String, String?) -> Boolean)? = null,
): DomainSelectionTextFieldGroupWidgetContent {
    var isContentValid by rememberSaveable {
        mutableStateOf(true)
    }

    val groupStateKeyBase = stateKey ?: titleText.takeIf { it.isNotBlank() }
    val persistentGroupKey = localizedGroupEditorPersistentKey(
        persistentUiDraftKey(
            stateHost = stateHost,
            stateKey = groupStateKeyBase,
            suffix = "domain-selection-group"
        )
    )
    val fallbackSecondaryDomainId = secondaryDomains?.firstOrNull()?.id ?: ""

    fun fallbackGroupData(): List<DomainSelectionTextFieldGroupItemContent> = listOf(
        DomainSelectionTextFieldGroupItemContent(
            value = TextFieldValue(""),
            selectedDomainId = domains.firstOrNull()?.id ?: "",
            selectedSecondaryDomainId = fallbackSecondaryDomainId,
            isContentValid = isContentValid,
            onContentValidityCheck = onContentValidityCheck?.let { validator ->
                { value ->
                    val selectedDomainId = domains.firstOrNull()?.id ?: ""
                    val selectedSecondaryDomainId = secondaryDomains?.firstOrNull()?.id ?: ""
                    val valid = validator(value, selectedDomainId, selectedSecondaryDomainId)
                    isContentValid = valid
                    valid
                }
            }
        )
    )

    fun List<DomainSelectionTextFieldGroupItemContent>.savedProjection(): List<Triple<String, String, String>> =
        map {
            Triple(
                it.selectedDomainId,
                it.selectedSecondaryDomainId,
                it.value.text.trim()
            )
        }
            .filter { it.first.isNotBlank() || it.second.isNotBlank() || it.third.isNotBlank() }
            .filter { it.third.isNotBlank() }
            .distinctBy { it.first + "|" + it.second }

    fun List<DomainSelectionTextFieldGroupItemContent>.fullProjection(): List<Triple<String, String, String>> =
        map {
            Triple(
                it.selectedDomainId,
                it.selectedSecondaryDomainId,
                it.value.text
            )
        }

    var persistentGroupLoaded by remember(persistentGroupKey) {
        mutableStateOf(persistentGroupKey == null)
    }
    var persistentGroupRestored by remember(persistentGroupKey) {
        mutableStateOf(false)
    }

    var data: List<DomainSelectionTextFieldGroupItemContent> by rememberSaveable(persistentGroupKey) {
        mutableStateOf(
            valueInitial?.takeIf { it.isNotEmpty() } ?: fallbackGroupData()
        )
    }

    LaunchedEffect(persistentGroupKey, fallbackSecondaryDomainId) {
        persistentGroupLoaded = false
        persistentGroupRestored = false
        val restored = persistentGroupKey
            ?.let { getPersistentUiDraftValue?.invoke(it) }
            ?.toLocalizedGroupEditorItemsOrNull(fallbackSecondaryDomainId)
            ?.takeIf { it.isNotEmpty() }
        if (restored != null) {
            data = restored
            persistentGroupRestored = true
        }
        persistentGroupLoaded = true
    }

    LaunchedEffect(valueInitial, persistentGroupLoaded, persistentGroupRestored) {
        if (!persistentGroupLoaded || persistentGroupRestored) return@LaunchedEffect
        val next = valueInitial?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        if (data.savedProjection() != next.savedProjection() && data.fullProjection() != next.fullProjection()) {
            data = next
        }
    }

    LaunchedEffect(data, persistentGroupLoaded, persistentGroupKey) {
        if (persistentGroupLoaded) {
            persistentGroupKey?.let { key ->
                // Localized rows can update on every typed symbol. Persist after a tiny pause
                // instead of doing a storage write for every recomposition/change.
                delay(360)
                setPersistentUiDraftValue?.invoke(key, data.toLocalizedGroupEditorStateString())
            }
        }
    }

    var availableDomains by rememberSaveable { mutableStateOf(domains) }
    var availableSecondaryDomains by rememberSaveable { mutableStateOf(secondaryDomains) }

    LaunchedEffect(data) {
        availableDomains = domains.filter { domain -> data.find { it.selectedDomainId == domain.id } == null }
        availableSecondaryDomains =
            secondaryDomains?.filter { secondaryDomain -> data.find { it.selectedSecondaryDomainId == secondaryDomain.id } == null }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        data.forEachIndexed { index, item ->
            val rowStateKey = groupStateKeyBase?.let { "${it}_$index" }
            val instance = domainSelectionTextField(
                titleText = if (data.size == 1 && index == 0) titleText else "$titleText ${index + 1}",
                placeholderText = placeholderText,
                valueInitial = item.value.text,
                stateHost = stateHost,
                stateKey = rowStateKey,
                titleIconButtonPath = if (data.size == 1) null else stateValues.drawablePathIconDelete,
                onTitleIconButtonClick = if (data.size == 1) null else {
                    {
                        data = data.toMutableList().apply {
                            removeAt(index)

                            if (index != data.lastIndex) {
                                coroutineScope.launch {
                                    for (i in (index + 1)..data.lastIndex) {
                                        groupStateKeyBase?.let { keyBase ->
                                            stateHost?.state?.value["${keyBase}_$i"]?.run {
                                                stateHost.setState("${keyBase}_${i - 1}" to this)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                domains = (listOfNotNull(domains.find { it.id == item.selectedDomainId }) + availableDomains)
                    .distinctBy { it.id },
                selectedInitial = item.selectedDomainId,
                secondaryDomains = secondaryDomains?.let { allSecondaryDomains ->
                    (listOfNotNull(allSecondaryDomains.find { it.id == item.selectedSecondaryDomainId }) + availableSecondaryDomains.orEmpty())
                        .distinctBy { it.id }
                },
                selectedSecondaryInitial = item.selectedSecondaryDomainId,
                displayFullDomain = true,
                secondaryDomainsShowId = secondaryDomainsShowId,
                secondaryDomainsShowName = secondaryDomainsShowName,
                keyboardType = keyboardType,
                isFocusedInitial = isFocusedInitial && index == data.lastIndex,
                onFilterValue = onFilterValue,
                onContentValidityCheck = onContentValidityCheck
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))

            LaunchedEffect(instance.value.text) {
                if (data.getOrNull(index)?.value?.text != instance.value.text) {
                    data = data.toMutableList().apply {
                        if (index in indices) set(index, get(index).copy(value = instance.value))
                    }
                }
            }
            LaunchedEffect(instance.selectedId) {
                try {
                    data = data.toMutableList().apply {
                        if (index in indices) set(index, get(index).copy(selectedDomainId = instance.selectedId))
                    }
                } catch (thr: Throwable) {

                }
            }
            LaunchedEffect(instance.selectedSecondaryId) {
                instance.selectedSecondaryId?.let {
                    try {
                        data = data.toMutableList().apply {
                            if (index in indices) set(index, get(index).copy(selectedSecondaryDomainId = it))
                        }
                    } catch (thr: Throwable) {

                    }
                }
            }
        }

        if (availableDomains.isNotEmpty())
            actionButton(
                modifier = Modifier
                    .fillMaxWidth(),
                text = addDomainActionButtonText,
                iconPath = stateValues.drawablePathIconAdd
            ) {
                data = data.toMutableList().apply {
                    add(
                        DomainSelectionTextFieldGroupItemContent(
                            TextFieldValue(),
                            availableDomains.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
                            availableSecondaryDomains?.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
                            isContentValid
                        )
                    )
                }
            }

        if (addSecondaryDomainActionButtonText != null && !addSecondaryDomainActionButtonText.equals(
                addDomainActionButtonText,
                true
            ) && availableSecondaryDomains?.isNotEmpty() == true
        )
            actionButton(
                modifier = Modifier
                    .fillMaxWidth(),
                text = addSecondaryDomainActionButtonText,
                iconPath = stateValues.drawablePathIconAdd
            ) {
                data = data.toMutableList().apply {
                    add(
                        DomainSelectionTextFieldGroupItemContent(
                            TextFieldValue(),
                            availableDomains.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
                            availableSecondaryDomains?.takeIf { it.isNotEmpty() }?.first()?.id ?: "",
                            isContentValid
                        )
                    )
                }
            }
    }

    return DomainSelectionTextFieldGroupWidgetContent(data)
}

data class DomainSelectionTextFieldGroupWidgetContent(
    val data: List<DomainSelectionTextFieldGroupItemContent>
)

data class DomainSelectionTextFieldGroupItemContent(
    var value: TextFieldValue,
    var selectedDomainId: String,
    var selectedSecondaryDomainId: String,
    var isContentValid: Boolean,
    val onContentValidityCheck: ((String) -> Boolean)? = null
) {

    fun checkContentValidity() {
        isContentValid = onContentValidityCheck?.invoke(value.text) ?: true
    }
}

@Composable
fun AppConfiguration.domainSelectionTextField(
    modifier: Modifier = Modifier,
    valueInitial: String? = null,
    titleText: String = "",
    stateHost: StateHost? = null,
    stateKey: String? = null,
    identityKey: String? = null,
    placeholderText: String = "",
    titleIconButtonPath: String? = null,
    titleIconButtonRes: DrawableResource? = null,
    onTitleIconButtonClick: (() -> Unit)? = null,
    lockedDomainId: String? = null,
    lockedSecondaryDomainId: String? = null,
    domains: List<SelectableDomain>,
    secondaryDomains: List<SelectableDomain>? = null,
    selectedInitial: String = try {
        domains.first().id
    } catch (_: Throwable) {
        ""
    },
    selectedSecondaryInitial: String? = secondaryDomains?.first()?.id,
    selectionEnabled: Boolean = true,
    selectionSecondaryEnabled: Boolean = true,
    displayFullDomain: Boolean = false,
    imeWithAction: ImeWithAction? = null,
    cornerRadius: Dp = stateValues.cornerRadius,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    adaptiveMultiline: Boolean = false,
    isFocusedInitial: Boolean = false,
    secondaryDomainsShowId: Boolean = true,
    secondaryDomainsShowName: Boolean = true,
    contentInvalidText: String? = null,
    onContentValidityCheck: ((String, String, String?) -> Boolean)? = null,
    onFilterValue: ((String, String, String?) -> Boolean)? = null,
    onValueChange: ((String, String, String?, () -> Unit) -> Unit)? = null
): DomainSelectionTextFieldContent {
    val primaryDomainIdsKey = remember(domains) { domains.joinToString("|") { it.id } }
    val secondaryDomainIdsKey = remember(secondaryDomains) { secondaryDomains.orEmpty().joinToString("|") { it.id } }
    val domainFieldIdentityKey = identityKey
        ?: stateKey
        ?: listOf(titleText, placeholderText, primaryDomainIdsKey, secondaryDomainIdsKey).joinToString("|")
    val persistentSelectedDomainKey = persistentUiDraftKey(stateHost, stateKey, "selected-domain")
    val persistentSelectedSecondaryDomainKey = persistentUiDraftKey(stateHost, stateKey, "selected-secondary-domain")

    var selectedId by rememberSaveable(domainFieldIdentityKey, primaryDomainIdsKey) {
        mutableStateOf(lockedDomainId ?: selectedInitial)
    }

    LaunchedEffect(persistentSelectedDomainKey, lockedDomainId, primaryDomainIdsKey) {
        if (lockedDomainId != null) return@LaunchedEffect
        val restored = persistentSelectedDomainKey
            ?.let { getPersistentUiDraftValue?.invoke(it) }
            ?.takeIf { restoredId -> domains.any { it.id.equals(restoredId, ignoreCase = true) } }
        if (!restored.isNullOrBlank() && restored != selectedId) {
            selectedId = restored
        }
    }

    LaunchedEffect(selectedId, persistentSelectedDomainKey, lockedDomainId) {
        if (lockedDomainId == null && !persistentSelectedDomainKey.isNullOrBlank() && selectedId.isNotBlank()) {
            setPersistentUiDraftValue?.invoke(persistentSelectedDomainKey, selectedId)
        }
    }

    var selected by remember(domainFieldIdentityKey, primaryDomainIdsKey) {
        mutableStateOf(
            domains.find { it.id.equals(selectedId, true) } ?: try {
                domains.first()
            } catch (thr: Throwable) {
                null
            }
        )
    }

    LaunchedEffect(selectedId) {
        domains.find { it.id.equals(selectedId, true) }?.run {
            selected = this
        }
    }

    LaunchedEffect(selectedInitial, lockedDomainId, persistentSelectedDomainKey) {
        val initialDomainId = selectedInitial.takeIf { it.isNotEmpty() } ?: try {
            domains.first().id
        } catch (thr: Throwable) {
            ""
        }
        selectedId = when {
            lockedDomainId != null -> lockedDomainId
            persistentSelectedDomainKey != null && selectedId.isNotBlank() -> selectedId
            else -> initialDomainId
        }
    }

    var selectedSecondaryId by rememberSaveable(domainFieldIdentityKey, secondaryDomainIdsKey) {
        mutableStateOf(lockedSecondaryDomainId ?: selectedSecondaryInitial)
    }

    LaunchedEffect(persistentSelectedSecondaryDomainKey, lockedSecondaryDomainId, secondaryDomainIdsKey) {
        if (lockedSecondaryDomainId != null) return@LaunchedEffect
        val restored = persistentSelectedSecondaryDomainKey
            ?.let { getPersistentUiDraftValue?.invoke(it) }
            ?.takeIf { restoredId -> secondaryDomains.orEmpty().any { it.id.equals(restoredId, ignoreCase = true) } }
        if (!restored.isNullOrBlank() && restored != selectedSecondaryId) {
            selectedSecondaryId = restored
        }
    }

    LaunchedEffect(selectedSecondaryId, persistentSelectedSecondaryDomainKey, lockedSecondaryDomainId) {
        val cleanSelectedSecondaryId = selectedSecondaryId
        if (lockedSecondaryDomainId == null && !persistentSelectedSecondaryDomainKey.isNullOrBlank() && !cleanSelectedSecondaryId.isNullOrBlank()) {
            setPersistentUiDraftValue?.invoke(persistentSelectedSecondaryDomainKey, cleanSelectedSecondaryId)
        }
    }

    var selectedSecondary by remember(domainFieldIdentityKey, secondaryDomainIdsKey) {
        mutableStateOf(secondaryDomains?.find { it.id.equals(selectedSecondaryId, true) } ?: secondaryDomains?.first())
    }

    LaunchedEffect(selectedSecondaryId) {
        secondaryDomains?.find { it.id.equals(selectedSecondaryId, true) }?.run {
            selectedSecondary = this
        }
    }

    LaunchedEffect(selectedSecondaryInitial, lockedSecondaryDomainId, persistentSelectedSecondaryDomainKey) {
        selectedSecondaryId = when {
            lockedSecondaryDomainId != null -> lockedSecondaryDomainId
            persistentSelectedSecondaryDomainKey != null && !selectedSecondaryId.isNullOrBlank() -> selectedSecondaryId
            else -> selectedSecondaryInitial
        }
    }

    val isDomainSelectionDropdownExpandedState = remember(domainFieldIdentityKey) {
        MutableTransitionState(false)
            .apply {
                targetState = false
            }
    }

    val isSecondaryDomainSelectionDropdownExpandedState = remember(domainFieldIdentityKey) {
        MutableTransitionState(false)
            .apply {
                targetState = false
            }
    }

    var textFieldContent: GenericTextFieldContent? = null

    Column(modifier) {
        val titleTextPresent = titleText.isNotEmpty() && titleText.isNotBlank()

        if (titleTextPresent || titleIconButtonPath != null)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (titleIconButtonPath != null && titleIconButtonRes != null)
                    CpImage(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(stateValues.iconSize)
                            .aitaClickable(
                                interactionSource = remember {
                                    MutableInteractionSource()
                                },
                                indication = ripple(color = stateValues.TextColor, radius = cornerRadius),
                                onClick = onTitleIconButtonClick ?: {}
                            ),
                        url = titleIconButtonPath,
                        fallbackRes = titleIconButtonRes,
                        contentDescription = titleText
                    )

                if (titleTextPresent)
                    Text(
                        modifier = Modifier
                            .padding(bottom = 4.dp),
                        text = titleText,
                        style = TextStyle(
                            color = stateValues.TextColor,
                            fontSize = stateValues.accentTextSize,
                            fontWeight = FontWeight.Bold
                        )
                    )
            }

        var isFocused by rememberSaveable(domainFieldIdentityKey) {
            mutableStateOf(isFocusedInitial)
        }

        Column(
            modifier = Modifier
                .foregroundTactileShadow(cornerRadius, elevated = false)
                .clip(RoundedCornerShape(cornerRadius))
                .background(stateValues.BackgroundColor)
                .border(
                    width = if (isFocused) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                    color = if (isFocused) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                    shape = RoundedCornerShape(cornerRadius)
                )
        ) {
            if (selectionSecondaryEnabled && !secondaryDomains.isNullOrEmpty() && lockedSecondaryDomainId == null) {
                AnimatedVisibility(
                    modifier = Modifier
                        .fillMaxWidth(),
                    visibleState = isSecondaryDomainSelectionDropdownExpandedState,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        var searchTextFieldContent: GenericTextFieldContent? = null

                        searchTextFieldContent = searchTextField(
                            modifier = Modifier
                                .fillMaxWidth(),
                            stateHost = stateHost,
                            stateKey = stateKey?.let { "${it}_secondary_domain_search" },
                            focusedBorderWidth = 0.dp,
                            unfocusedBorderWidth = 0.dp,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )

                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(stateValues.PlaceholderTextColor)
                                .height(stateValues.unfocusedBorderWidth)
                        )

                        val items = secondaryDomains
                            .takeIf { it.isNotEmpty() && searchTextFieldContent!!.value.text.isNotEmpty() }
                            ?.search<SelectableDomain>(query = searchTextFieldContent!!.value.text)
                            ?.first ?: secondaryDomains


                        if (items.isEmpty()) {
                            MessageText(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                text = stateValues.stringNoMatches
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = stateValues.screenHeight / 4)
                            ) {

                                items(items) { domain ->
                                    selectableDomainWidget(
                                        modifier = Modifier
                                            .fillParentMaxWidth(),
                                        domain = domain,
                                        showId = secondaryDomainsShowId,
                                        showName = secondaryDomainsShowName
                                    ) {

                                        if (lockedSecondaryDomainId == null) {
                                            selectedSecondaryId = domain.id

                                            isSecondaryDomainSelectionDropdownExpandedState.targetState =
                                                !isSecondaryDomainSelectionDropdownExpandedState.targetState
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(stateValues.PlaceholderTextColor)
                                .height(stateValues.unfocusedBorderWidth)
                        )
                    }
                }
            }

            textFieldContent = genericTextField(
                modifier = Modifier
                    .fillMaxWidth(),
                valueInitial = valueInitial,
                titleText = "",
                stateHost = stateHost,
                stateKey = stateKey,
                identityKey = domainFieldIdentityKey,
                placeholderText = placeholderText,
                isFocusedInitial = isFocusedInitial,
                leadingIcon = selectedSecondary?.run {
                    {
                        selectableDomainWidget(
                            domain = this,
                            state = isSecondaryDomainSelectionDropdownExpandedState,
                            showExpansion = lockedSecondaryDomainId == null,
                            showId = secondaryDomainsShowId,
                            showName = false,
                            onClick = selectionSecondaryEnabled.takeIf { it }?.run {
                                {
                                    isSecondaryDomainSelectionDropdownExpandedState.targetState =
                                        !isSecondaryDomainSelectionDropdownExpandedState.targetState
                                }
                            }
                        )
                    }
                },
                cornerShape = RectangleShape,
                keyboardType = keyboardType,
                imeWithAction = imeWithAction,
                wide = !singleLine && !adaptiveMultiline,
                singleLine = singleLine,
                adaptiveMultiline = adaptiveMultiline,
                focusedBorderWidth = 0.dp,
                unfocusedBorderWidth = 0.dp,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                contentInvalidText = contentInvalidText,
                onContentValidityCheck = onContentValidityCheck?.run {
                    {
                        invoke(it, selectedId, selectedSecondaryId)
                    }
                },
                onFilterValue = onFilterValue?.run {
                    {
                        invoke(it, selectedId, selectedSecondaryId)
                    }
                },
                onValueChange = onValueChange?.run {
                    { value, action ->
                        invoke(value, selectedId, selectedSecondaryId, action)
                    }
                }
            )

            LaunchedEffect(textFieldContent.isFocused) {
                isFocused = textFieldContent.isFocused
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(stateValues.PlaceholderTextColor)
                    .height(stateValues.unfocusedBorderWidth)
            )

            if (displayFullDomain && domains.isNotEmpty() && selected != null) {
                selectableDomainWidget(
                    modifier = Modifier
                        .fillMaxWidth(),
                    domain = selected!!,
                    showName = secondaryDomainsShowName,
                    state = isDomainSelectionDropdownExpandedState,
                    showExpansion = true
                ) {
                    isDomainSelectionDropdownExpandedState.targetState =
                        !isDomainSelectionDropdownExpandedState.targetState
                }
            }

            if (selectionEnabled && domains.isNotEmpty()) {
                AnimatedVisibility(
                    modifier = Modifier
                        .fillMaxWidth(),
                    visibleState = isDomainSelectionDropdownExpandedState,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        var searchTextFieldContent: GenericTextFieldContent? = null

                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(stateValues.PlaceholderTextColor)
                                .height(stateValues.unfocusedBorderWidth)
                        )

                        searchTextFieldContent = searchTextField(
                            modifier = Modifier
                                .fillMaxWidth(),
                            stateHost = stateHost,
                            stateKey = stateKey?.let { "${it}_domain_search" },
                            focusedBorderWidth = 0.dp,
                            unfocusedBorderWidth = 0.dp,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )

                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(stateValues.PlaceholderTextColor)
                                .height(stateValues.unfocusedBorderWidth)
                        )

                        val items = domains
                            .takeIf { it.isNotEmpty() && searchTextFieldContent.value.text.isNotEmpty() }
                            ?.search<SelectableDomain>(query = searchTextFieldContent.value.text)
                            ?.first ?: domains

                        if (items.isEmpty()) {
                            MessageText(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                text = stateValues.stringNoMatches
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = stateValues.screenHeight / 4)
                            ) {

                                items(items) { domain ->
                                    selectableDomainWidget(
                                        modifier = Modifier
                                            .fillParentMaxWidth(),
                                        domain = domain,
                                        showName = secondaryDomainsShowName
                                    ) {
                                        if (lockedDomainId == null) {
                                            selectedId = domain.id

                                            isDomainSelectionDropdownExpandedState.targetState =
                                                !isDomainSelectionDropdownExpandedState.targetState
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    return DomainSelectionTextFieldContent(
        value = textFieldContent!!.value,
        isFocused = textFieldContent.isFocused,
        selectedId = selectedId,
        selectedSecondaryId = selectedSecondaryId,
        isContentValid = textFieldContent.isContentValid,
        onContentValidityCheck = textFieldContent.onContentValidityCheck,
        onReplaceText = textFieldContent.onReplaceText,
        onSelectedSecondaryIdChange = { nextSelectedSecondaryId ->
            if (lockedSecondaryDomainId == null) {
                val cleanId = nextSelectedSecondaryId?.takeIf { candidate ->
                    secondaryDomains.orEmpty().any { it.id.equals(candidate, ignoreCase = true) }
                }
                if (!cleanId.isNullOrBlank()) {
                    selectedSecondaryId = cleanId
                    isSecondaryDomainSelectionDropdownExpandedState.targetState = false
                }
            }
        }
    )
}

class DomainSelectionTextFieldContent(
    var value: TextFieldValue,
    var selectedId: String,
    var selectedSecondaryId: String?,
    var isFocused: Boolean,
    var isContentValid: Boolean,
    val onContentValidityCheck: ((String) -> Boolean)? = null,
    val onReplaceText: ((String, Boolean) -> Unit)? = null,
    val onSelectedSecondaryIdChange: ((String?) -> Unit)? = null
) {

    fun checkContentValidity() {
        isContentValid = onContentValidityCheck?.invoke(value.text) ?: true
    }

    fun replaceText(rawText: String, applyTransform: Boolean = true) {
        value = TextFieldValue(rawText, selection = TextRange(rawText.length))
        onReplaceText?.invoke(rawText, applyTransform)
    }

    fun replaceSelectedSecondaryId(rawId: String?) {
        selectedSecondaryId = rawId
        onSelectedSecondaryIdChange?.invoke(rawId)
    }
}

object AppConfiguration {

    interface StateValues {
        val latestNotification: NotificationDataModel?
        val activeNotifications: List<NotificationDataModel>
        val notificationsState: DataState<List<NotificationDataModel>>
        val notifications: List<NotificationDataModel>?
        val realtimeUpdatesConnected: Boolean
        val cloudTransportStatus: Int
        val cloudConnectionManualRefreshInProgress: Boolean
        val localNetworkState: LocalNetworkStateDataModel
        val logInInProgress: Boolean
        val signUpInProgress: Boolean
        val completeTransactionInProgress: Boolean
        val workshiftLoginInProgress: Boolean
        val activeWorkshiftState: DataState<WorkshiftDataModel>
        val activeWorkshift: WorkshiftDataModel?
        val userAccountState: DataState<UserAccountDataModel>
        val userAccount: UserAccountDataModel?

        //    val activeModeId: String?
        val stockState: DataState<List<GoodsItemDataModel>>
        val stock: List<GoodsItemDataModel>?
        val stockBatches: List<GoodsBatchDataModel>?

        val storesState: DataState<List<StoreDataModel>>
        val stores: List<StoreDataModel>?
        val activeStoreId: String?
        val goodsCategoriesState: DataState<List<GenericGoodsCategoryDataModel>>
        val goodsCategories: List<GenericGoodsCategoryDataModel>?

        val suppliersState: DataState<List<SupplierDataModel>>
        val suppliers: List<SupplierDataModel>?

        val navigationScreensMain: List<NavigationScreenModel>
        val navigationTransactionSaleClientId: Int
        val navigationTransactionReturnClientId: Int
        val navigationTransactionSupplyClientId: Int
        val navigationScreensTransactionSaleLeftClient1: List<NavigationScreenModel>
        val navigationScreensTransactionSaleLeftClient2: List<NavigationScreenModel>
        val navigationScreensTransactionSaleLeftClient3: List<NavigationScreenModel>
        val navigationScreensTransactionSaleLeftClient4: List<NavigationScreenModel>
        val navigationScreensTransactionSaleLeftClient5: List<NavigationScreenModel>
        val navigationScreensTransactionSaleRightClient1: List<NavigationScreenModel>
        val navigationScreensTransactionSaleRightClient2: List<NavigationScreenModel>
        val navigationScreensTransactionSaleRightClient3: List<NavigationScreenModel>
        val navigationScreensTransactionSaleRightClient4: List<NavigationScreenModel>
        val navigationScreensTransactionSaleRightClient5: List<NavigationScreenModel>
        val navigationScreensTransactionReturnLeftClient1: List<NavigationScreenModel>
        val navigationScreensTransactionReturnLeftClient2: List<NavigationScreenModel>
        val navigationScreensTransactionReturnLeftClient3: List<NavigationScreenModel>
        val navigationScreensTransactionReturnLeftClient4: List<NavigationScreenModel>
        val navigationScreensTransactionReturnLeftClient5: List<NavigationScreenModel>
        val navigationScreensTransactionReturnRightClient1: List<NavigationScreenModel>
        val navigationScreensTransactionReturnRightClient2: List<NavigationScreenModel>
        val navigationScreensTransactionReturnRightClient3: List<NavigationScreenModel>
        val navigationScreensTransactionReturnRightClient4: List<NavigationScreenModel>
        val navigationScreensTransactionReturnRightClient5: List<NavigationScreenModel>
        val navigationScreensTransactionSupplyLeftClient1: List<NavigationScreenModel>
        val navigationScreensTransactionSupplyLeftClient2: List<NavigationScreenModel>
        val navigationScreensTransactionSupplyLeftClient3: List<NavigationScreenModel>
        val navigationScreensTransactionSupplyLeftClient4: List<NavigationScreenModel>
        val navigationScreensTransactionSupplyLeftClient5: List<NavigationScreenModel>
        val navigationScreensTransactionSupplyRightClient1: List<NavigationScreenModel>
        val navigationScreensTransactionSupplyRightClient2: List<NavigationScreenModel>
        val navigationScreensTransactionSupplyRightClient3: List<NavigationScreenModel>
        val navigationScreensTransactionSupplyRightClient4: List<NavigationScreenModel>
        val navigationScreensTransactionSupplyRightClient5: List<NavigationScreenModel>
        val navigationScreensStockLeft: List<NavigationScreenModel>
        val navigationScreensStockRight: List<NavigationScreenModel>
        val navigationScreensMenuLeft: List<NavigationScreenModel>
        val navigationScreensMenuRight: List<NavigationScreenModel>

        val navigationScreensUserAuthLeft: List<NavigationScreenModel>
        val navigationScreensUserAuthRight: List<NavigationScreenModel>

        val globalAppConfiguration: GlobalAppConfigurationDataModel
        val strings: List<LocalizedStringGroupDataModel>?
        val dimensions: List<StylizedDimensionGroupDataModel>?
        val colors: List<StylizedColorGroupDataModel>?
        val drawables: List<StylizedDrawablePathsGroupDataModel>?

        val appLanguage: String
        val appThemeId: Long
        val appSizeModeId: Long
        val appModeId: Int

        val stringAppName: String
        val stringLogIn: String
        val stringPhoneNumber: String
        val stringEnterPhoneNumber: String
        val stringEmail: String
        val stringEnterEmailAddress: String
        val stringPassword: String
        val stringEnterPassword: String
        val stringCancel: String
        val stringClear: String
        val stringAuthenticationFailed: String
        val stringPhoneNumberMustBe: String
        val stringEmailMustBe: String
        val stringPasswordMustBe: String
        val stringRepeatPassword: String
        val stringPasswordsMustMatch: String
        val stringFirstName: String
        val stringLastName: String
        val stringEnterFirstName: String
        val stringEnterLastName: String
        val stringUserWithThisPhoneNumberIsAlreadyRegistered: String
        val stringUserWithThisEmailAddressIsAlreadyRegistered: String
        val stringSignUp: String
        val stringConfirm: String
        val stringSale: String
        val stringReturn: String
        val stringSupply: String
        val stringStock: String
        val stringMenu: String
        val stringBack: String
        val stringAddGoodsItem: String
        val stringEditGoodsItem: String
        val stringUserAccount: String
        val stringGoodsCategories: String
        val stringAddGoodsCategory: String
        val stringEditGoodsCategory: String
        val stringStores: String
        val stringAddStore: String
        val stringEditStore: String
        val stringSubscription: String
        val stringSubscriptionPlans: String
        val stringTransactionHistory: String
        val stringReceipt: String
        val stringAnalytics: String
        val stringWorkers: String
        val stringAddWorker: String
        val stringEditWorker: String
        val stringSuppliers: String
        val stringAddSupplier: String
        val stringEditSupplier: String
        val stringDebtors: String
        val stringCloseDebt: String
        val stringDevices: String
        val stringAppLanguage: String
        val stringAppTheme: String
        val stringSelect: String
        val stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered: String
        val stringFirstNameCannotBeEmptyOrJustWhitespaces: String
        val stringLastNameCannotBeEmptyOrJustWhitespaces: String
        val stringSystemLanguage: String
        val stringBluetoothPermissionRequired: String
        val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String
        val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettings: String
        val stringBluetoothDisabled: String
        val stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String
        val stringSearchByAnyData: String
        val stringListEmpty: String
        val stringNoMatches: String
        val stringName: String
        val stringBarcode: String
        val stringSupplyPrice: String
        val stringSalePrice: String
        val stringReturnPrice: String
        val stringCategory: String
        val stringSupplier: String
        val stringEnterName: String
        val stringEnterBarcode: String
        val stringEnterSupplyPrice: String
        val stringEnterSalePrice: String
        val stringEnterReturnPrice: String
        val stringSelectCategory: String
        val stringSelectSupplier: String
        val stringEdit: String
        val stringChangePassword: String
        val stringNewPassword: String
        val stringEnterNewPassword: String
        val stringRepeatNewPassword: String
        val stringConfirmationPassword: String
        val stringRequiredToEditAccount: String
        val stringAccountSuccessfullyUpdated: String
        val stringLoggingOut: String
        val stringSessionTimeExpiredLoggingOut: String
        val stringAlias: String
        val stringDescription: String
        val stringEnterAlias: String
        val stringEnterDescription: String
        val stringOptional: String
        val stringLoggingIn: String
        val stringSigningUp: String
        val stringCompanyForm: String
        val stringMeasurementUnit: String

        val stringNoActiveStore: String
        val stringSelectInMenu: String
        val stringSupplyData: String
        val stringSaleData: String
        val stringReturnData: String
        val stringAddSupplyData: String
        val stringAddSaleData: String
        val stringAddReturnData: String

        val stringAddBarcode: String
        val stringAddName: String
        val stringPayment: String
        val stringAll: String
        val stringQuick: String
        val stringCategories: String
        val stringMain: String
        val stringAddTranslation: String
        val stringSetActive: String
        val stringOutOfStock: String
        val stringDelete: String
        val stringCash: String
        val stringCashless: String
        val stringMixed: String
        val stringAdd: String
        val stringSubtract: String
        val stringCurrentBatchData: String
        val stringEnterQuantity: String
        val stringAddQuantityData: String
        val stringShelfBatch: String
        val stringActiveStore: String
        val stringMakeInactive: String
        val stringCartEmpty: String
        val stringComplete: String
        val stringNoActiveWorkshift: String
        val stringCart: String
        val stringAppMode: String
        val stringFinances: String
        val stringItems: String
        val stringBatches: String
        val stringStandardPricesForSuppliers: String
        val stringEditableForIndividualBatches: String
        val stringBatchesData: String
        val stringReceiptNumber: String
        val stringTransactionId: String
        val stringDate: String
        val stringCashier: String
        val stringStore: String
        val stringAddress: String
        val stringPhone: String
        val stringTotal: String
        val stringPaid: String
        val stringDebt: String
        val stringDebtor: String
        val stringDebtorPhone: String
        val stringChange: String
        val stringVat: String
        val stringVatNotSpecified: String
        val stringFiscalStatus: String
        val stringNonFiscalSoftwareReceipt: String
        val stringThankYou: String
        val stringNoItems: String
        val stringNoName: String
        val stringPdf: String
        val stringShare: String
        val stringWhatsApp: String
        val stringPrint: String
        val stringQuit: String
        val stringReceiptPdfSaved: String
        val stringReceiptShared: String
        val stringReceiptSentToWhatsApp: String
        val stringReceiptSentToPrinter: String
        val stringReceiptActionFailed: String
        val stringGoodsReceiptTitle: String
        val stringSaleReceiptTitle: String
        val stringReturnReceiptTitle: String
        val stringSupplyReceiptTitle: String
        val stringDraft: String

        val screenWidth: Dp
        val screenHeight: Dp
        val wideScreenMinWidth: Float
        val boundWidgetWidth: Dp

        val isNarrowScreen: Boolean

        val textSize: TextUnit
        val titleTextSize: TextUnit
        val smallTextSize: TextUnit
        val accentTextSize: TextUnit

        val focusedBorderWidth: Dp
        val unfocusedBorderWidth: Dp
        val cornerRadius: Dp
        val iconSize: Dp
        val textFieldHeightMultiplierRelativeToTextSize: Float
        val textFieldHeight: Dp
        val wideTextFieldHeight: Dp

        val textFieldIconPadding: Dp

        val marginTextField: Dp
        val marginTextFieldGroup: Dp

        val AccentColor: Color
        val BackgroundColor: Color
        val TextColor: Color
        val AccentTextColor: Color
        val PlaceholderTextColor: Color
        val DisabledColor: Color
        val ErrorColor: Color

        val IconTintColor: Color
        val OkayColor: Color
        val BorderlineBadColor: Color

        val drawablePathAITALogo: String
        val drawableResAITALogo: StateFlow<DrawableResource>

        val drawablePathIconPassword: String
        val drawableResIconPassword: StateFlow<DrawableResource>

        val drawablePathIconSecurity: String
        val drawableResIconSecurity: StateFlow<DrawableResource>

        val drawablePathIconResponse: String
        val drawableResIconResponse: StateFlow<DrawableResource>

        val drawablePathIconCancel: String
        val drawableResIconCancel: StateFlow<DrawableResource>

        val drawablePathIconEyeHide: String
        val drawableResIconEyeHide: StateFlow<DrawableResource>

        val drawablePathIconEyeShow: String
        val drawableResIconEyeShow: StateFlow<DrawableResource>

        val drawablePathIconEmail: String
        val drawableResIconEmail: StateFlow<DrawableResource>
        val drawablePathIconPhone: String
        val drawableResIconPhone: StateFlow<DrawableResource>

        val drawablePathIconExpandMore: String
        val drawableResIconExpandMore: StateFlow<DrawableResource>

        val drawablePathIconExpandLess: String
        val drawableResIconExpandLess: StateFlow<DrawableResource>

        val drawablePathIconPerson: String
        val drawableResIconPerson: StateFlow<DrawableResource>

        val drawablePathIconTransactionSale: String
        val drawableResIconTransactionSale: StateFlow<DrawableResource>

        val drawablePathIconTransactionReturn: String
        val drawableResIconTransactionReturn: StateFlow<DrawableResource>

        val drawablePathIconTransactionSupply: String
        val drawableResIconTransactionSupply: StateFlow<DrawableResource>

        val drawablePathIconTransactionSelection: String
        val drawableResIconTransactionSelection: StateFlow<DrawableResource>

        val drawablePathIconStock: String
        val drawableResIconStock: StateFlow<DrawableResource>

        val drawablePathIconMenu: String
        val drawableResIconMenu: StateFlow<DrawableResource>

        val drawablePathIconBackArrow: String
        val drawableResIconBackArrow: StateFlow<DrawableResource>

        val drawablePathIconAdd: String
        val drawableResIconAdd: StateFlow<DrawableResource>

        val drawablePathIconUserAccount: String
        val drawableResIconUserAccount: StateFlow<DrawableResource>

        val drawablePathIconGoodsCategories: String
        val drawableResIconGoodsCategories: StateFlow<DrawableResource>

        val drawablePathIconStores: String
        val drawableResIconStores: StateFlow<DrawableResource>

        val drawablePathIconTransactionHistory: String
        val drawableResIconTransactionHistory: StateFlow<DrawableResource>

        val drawablePathIconLog: String
        val drawableResIconLog: StateFlow<DrawableResource>

        val drawablePathIconPromos: String
        val drawableResIconPromos: StateFlow<DrawableResource>

        val drawablePathIconAnalytics: String
        val drawableResIconAnalytics: StateFlow<DrawableResource>

        val drawablePathIconAnalyticsReport: String
        val drawableResIconAnalyticsReport: StateFlow<DrawableResource>

        val drawablePathIconLabelPrinter: String
        val drawableResIconLabelPrinter: StateFlow<DrawableResource>

        val drawablePathIconBarcodeGenerate: String
        val drawableResIconBarcodeGenerate: StateFlow<DrawableResource>

        val drawablePathIconPrintTag: String
        val drawableResIconPrintTag: StateFlow<DrawableResource>

        val drawablePathIconWorkerRoleTemplates: String
        val drawableResIconWorkerRoleTemplates: StateFlow<DrawableResource>

        val drawablePathIconStockHistory: String
        val drawableResIconStockHistory: StateFlow<DrawableResource>

        val drawablePathIconAppModeStore: String
        val drawableResIconAppModeStore: StateFlow<DrawableResource>

        val drawablePathIconAppModeBuyer: String
        val drawableResIconAppModeBuyer: StateFlow<DrawableResource>

        val drawablePathIconAppModeSupplier: String
        val drawableResIconAppModeSupplier: StateFlow<DrawableResource>

        val drawablePathIconAppModeManufacturer: String
        val drawableResIconAppModeManufacturer: StateFlow<DrawableResource>

        val drawablePathIconSupplierCatalog: String
        val drawableResIconSupplierCatalog: StateFlow<DrawableResource>

        val drawablePathIconSupplierContracts: String
        val drawableResIconSupplierContracts: StateFlow<DrawableResource>

        val drawablePathIconSupplierPartners: String
        val drawableResIconSupplierPartners: StateFlow<DrawableResource>

        val drawablePathIconSupplierDemandRadar: String
        val drawableResIconSupplierDemandRadar: StateFlow<DrawableResource>

        val drawablePathIconSupplierBackorderRecovery: String
        val drawableResIconSupplierBackorderRecovery: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryOwner: String
        val drawableResIconSupplierRecoveryOwner: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryClock: String
        val drawableResIconSupplierRecoveryClock: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryProof: String
        val drawableResIconSupplierRecoveryProof: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryResolution: String
        val drawableResIconSupplierRecoveryResolution: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryContact: String
        val drawableResIconSupplierRecoveryContact: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryRisk: String
        val drawableResIconSupplierRecoveryRisk: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryConfidence: String
        val drawableResIconSupplierRecoveryConfidence: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryFollowUp: String
        val drawableResIconSupplierRecoveryFollowUp: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryHandoff: String
        val drawableResIconSupplierRecoveryHandoff: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryClosure: String
        val drawableResIconSupplierRecoveryClosure: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryLedger: String
        val drawableResIconSupplierRecoveryLedger: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryTriage: String
        val drawableResIconSupplierRecoveryTriage: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryCommand: String
        val drawableResIconSupplierRecoveryCommand: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryPromiseShield: String
        val drawableResIconSupplierRecoveryPromiseShield: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryDesk: String
        val drawableResIconSupplierRecoveryDesk: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryWave: String
        val drawableResIconSupplierRecoveryWave: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryAging: String
        val drawableResIconSupplierRecoveryAging: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryBottleneck: String
        val drawableResIconSupplierRecoveryBottleneck: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryLoad: String
        val drawableResIconSupplierRecoveryLoad: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryImpact: String
        val drawableResIconSupplierRecoveryImpact: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryCommit: String
        val drawableResIconSupplierRecoveryCommit: StateFlow<DrawableResource>

        val drawablePathIconSupplierRecoveryAllocation: String
        val drawableResIconSupplierRecoveryAllocation: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryException: String
        val drawableResIconSupplierRecoveryException: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryCause: String
        val drawableResIconSupplierRecoveryCause: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryVerification: String
        val drawableResIconSupplierRecoveryVerification: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryApproval: String
        val drawableResIconSupplierRecoveryApproval: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryExecution: String
        val drawableResIconSupplierRecoveryExecution: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryRelease: String
        val drawableResIconSupplierRecoveryRelease: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoverySeal: String
        val drawableResIconSupplierRecoverySeal: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryCloseout: String
        val drawableResIconSupplierRecoveryCloseout: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryReopen: String
        val drawableResIconSupplierRecoveryReopen: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryReconciliation: String
        val drawableResIconSupplierRecoveryReconciliation: StateFlow<DrawableResource>
        val drawablePathIconSupplierRecoveryAudit: String
        val drawableResIconSupplierRecoveryAudit: StateFlow<DrawableResource>

        val drawablePathIconSupplierDispatch: String
        val drawableResIconSupplierDispatch: StateFlow<DrawableResource>

        val drawablePathIconSupplierTermsGuard: String
        val drawableResIconSupplierTermsGuard: StateFlow<DrawableResource>

        val drawablePathIconBuyerAgeRestriction: String
        val drawableResIconBuyerAgeRestriction: StateFlow<DrawableResource>

        val drawablePathIconTransactionTimeRestriction: String
        val drawableResIconTransactionTimeRestriction: StateFlow<DrawableResource>

        val drawablePathIconWorkers: String
        val drawableResIconWorkers: StateFlow<DrawableResource>

        val drawablePathIconSuppliers: String
        val drawableResIconSuppliers: StateFlow<DrawableResource>

        val drawablePathIconDebtors: String
        val drawableResIconDebtors: StateFlow<DrawableResource>

        val drawablePathIconDevices: String
        val drawableResIconDevices: StateFlow<DrawableResource>

        val drawablePathIconAppLanguage: String
        val drawableResIconAppLanguage: StateFlow<DrawableResource>

        val drawablePathIconAppTheme: String
        val drawableResIconAppTheme: StateFlow<DrawableResource>

        val drawablePathIconAppScale: String
        val drawableResIconAppScale: StateFlow<DrawableResource>

        val drawablePathIconCheck: String
        val drawableResIconCheck: StateFlow<DrawableResource>

        val drawablePathIconEdit: String
        val drawableResIconEdit: StateFlow<DrawableResource>

        val drawablePathIconSettings: String
        val drawableResIconSettings: StateFlow<DrawableResource>

        val drawablePathIconSearch: String
        val drawableResIconSearch: StateFlow<DrawableResource>

        val drawablePathIconBarcodeCamScanner: String
        val drawableResIconBarcodeCamScanner: StateFlow<DrawableResource>

        val drawablePathIconBarcodeScanner: String
        val drawableResIconBarcodeScanner: StateFlow<DrawableResource>

        val drawablePathIconBarcodeType: String
        val drawableResIconBarcodeType: StateFlow<DrawableResource>

        val drawablePathIconVoiceInput: String
        val drawableResIconVoiceInput: StateFlow<DrawableResource>

        val drawablePathIconDelete: String
        val drawableResIconDelete: StateFlow<DrawableResource>

        val drawablePathIconExit: String
        val drawableResIconExit: StateFlow<DrawableResource>

        val drawablePathIconSwitch: String
        val drawableResIconSwitch: StateFlow<DrawableResource>

        val drawablePathIconSort: String
        val drawableResIconSort: StateFlow<DrawableResource>

        val drawablePathIconCart: String
        val drawableResIconCart: StateFlow<DrawableResource>

        val drawablePathIconAddCart: String
        val drawableResIconAddCart: StateFlow<DrawableResource>

        val drawablePathIconSubtract: String
        val drawableResIconSubtract: StateFlow<DrawableResource>

        val drawablePathIconReceipt: String
        val drawableResIconReceipt: StateFlow<DrawableResource>

        val drawablePathIconFinances: String
        val drawableResIconFinances: StateFlow<DrawableResource>

        val drawablePathIconClipboard: String
        val drawableResIconClipboard: StateFlow<DrawableResource>

        val drawablePathIconSupport: String
        val drawableResIconSupport: StateFlow<DrawableResource>

        val drawablePathIconSubscription: String
        val drawableResIconSubscription: StateFlow<DrawableResource>

        val drawablePathIconThemeLight: String
        val drawableResIconThemeLight: StateFlow<DrawableResource>

        val drawablePathIconThemeDark: String
        val drawableResIconThemeDark: StateFlow<DrawableResource>

        val drawablePathIconShare: String
        val drawableResIconShare: StateFlow<DrawableResource>

        val drawablePathIconWhatsApp: String
        val drawableResIconWhatsApp: StateFlow<DrawableResource>

        val drawablePathIconRefresh: String
        val drawableResIconRefresh: StateFlow<DrawableResource>


        suspend fun updateDrawableResources()
    }

    private val _screenWidthState = MutableStateFlow(0f.dp)
    private val _screenHeightState = MutableStateFlow(0f.dp)
    private val _wideScreenMinWidthState = MutableStateFlow(600f)
    private val _boundWidgetWidthState = MutableStateFlow(280f.dp)

    private val _isNarrowScreenState = MutableStateFlow(false)

    private val _textSizeState = MutableStateFlow(14.sp)
    private val _titleTextSizeState = MutableStateFlow(20.sp)
    private val _accentTextSizeState = MutableStateFlow(16.sp)
    private val _smallTextSizeState = MutableStateFlow(12.sp)
    private val _focusedBorderWidthState = MutableStateFlow(1.dp)
    private val _unfocusedBorderWidthState = MutableStateFlow(0.5.dp)
    private val _cornerRadiusState = MutableStateFlow(14.dp)
    private val _iconSizeState = MutableStateFlow(24.dp)
    private val _textFieldHeightMultiplierRelativeToTextSizeState = MutableStateFlow(2.6f)
    private val _textFieldHeightState =
        MutableStateFlow(((_textSizeState.value.value * _textFieldHeightMultiplierRelativeToTextSizeState.value)).dp)
    private val _wideTextFieldHeightState =
        MutableStateFlow((((_textSizeState.value.value * 4) * _textFieldHeightMultiplierRelativeToTextSizeState.value)).dp)
    private val _textFieldIconPaddingState = MutableStateFlow((9.dp))

    private val _marginTextFieldState = MutableStateFlow((8.dp))
    private val _marginTextFieldGroupState = MutableStateFlow((24.dp))

    private val _AccentColorState = MutableStateFlow(Color(0xffffba24))
    private val _BackgroundColorState = MutableStateFlow(Color(0xffffffff))
    private val _TextColorState = MutableStateFlow(Color(0x00000000))
    private val _AccentTextColorState = MutableStateFlow(Color(0xffffffff))
    private val _PlaceholderTextColorState = MutableStateFlow(Color(0xaa000000))
    private val _DisabledColorState = MutableStateFlow(Color(0xffa7a7a7))
    private val _ErrorColorState = MutableStateFlow(Color(0xffff0000))
    private val _IconTintColorState = MutableStateFlow(Color(0xffffffff))

    private val _OkayColorState = MutableStateFlow(Color(0xff6bb522))
    private val _BorderlineBadColorState = MutableStateFlow(Color(0xffffa500))

    lateinit var stateValues: StateValues

    var softKeyboardController: SoftwareKeyboardController? = null

    lateinit var coroutineScope: CoroutineScope

    @Composable
    operator fun invoke(
        content: @Composable AppConfiguration.() -> Unit,
        vararg keys: Any
    ) {

        stateValues = object : StateValues {
            override val latestNotification: NotificationDataModel? by latestInAppNotificationState.collectAsState()
            override val activeNotifications: List<NotificationDataModel> by activeInAppNotificationsState.collectAsState()
            override val notificationsState: DataState<List<NotificationDataModel>> by kz.aita.notificationsState.value.collectAsState()
            override val notifications: List<NotificationDataModel>? by kz.aita.notificationsState.payload.collectAsState()
            override val realtimeUpdatesConnected: Boolean by realtimeUpdatesConnectedState.collectAsState()
            override val cloudTransportStatus: Int by cloudConnectionPresentationStatusState.collectAsState()
            override val cloudConnectionManualRefreshInProgress: Boolean by cloudConnectionManualRefreshInProgressState.collectAsState()
            override val localNetworkState: LocalNetworkStateDataModel by kz.aita.localNetworkState.collectAsState()
            override val logInInProgress: Boolean by logInInProgressState.collectAsState()
            override val signUpInProgress: Boolean by signUpInProgressState.collectAsState()
            override val completeTransactionInProgress: Boolean by completeTransactionInProgressState.collectAsState()
            override val workshiftLoginInProgress: Boolean by workshiftLoginInProgressState.collectAsState()
            override val activeWorkshiftState: DataState<WorkshiftDataModel> by kz.aita.activeWorkshiftState.value.collectAsState()
            override val activeWorkshift: WorkshiftDataModel? by kz.aita.activeWorkshiftState.payload.collectAsState()

            override val userAccountState: DataState<UserAccountDataModel> by kz.aita.userAccountState.value.collectAsState()
            override val userAccount: UserAccountDataModel? by kz.aita.userAccountState.payload.collectAsState()

            override val stockState: DataState<List<GoodsItemDataModel>> by kz.aita.stockState.value.collectAsState()
            override val stock: List<GoodsItemDataModel>? by kz.aita.stockState.payload.collectAsState()
            override val stockBatches: List<GoodsBatchDataModel>? by stockBatchesState.payload.collectAsState()

            override val storesState: DataState<List<StoreDataModel>> by kz.aita.storesState.value.collectAsState()
            override val stores: List<StoreDataModel>? by kz.aita.storesState.payload.collectAsState()
            override val activeStoreId: String? by kz.aita.activeStoreIdState.collectAsState()

            override val goodsCategoriesState: DataState<List<GenericGoodsCategoryDataModel>> by genericGoodsCategoriesState.value.collectAsState()
            override val goodsCategories: List<GenericGoodsCategoryDataModel>? by genericGoodsCategoriesState.payload.collectAsState()

            override val suppliersState: DataState<List<SupplierDataModel>> by kz.aita.suppliersState.value.collectAsState()
            override val suppliers: List<SupplierDataModel>? by kz.aita.suppliersState.payload.collectAsState()

            override val navigationScreensMain: List<NavigationScreenModel> by Navigation.Main.collectAsState()
            override val navigationTransactionSaleClientId: Int by Navigation.TransactionSale.ClientId.collectAsState()
            override val navigationTransactionReturnClientId: Int by Navigation.TransactionReturn.ClientId.collectAsState()
            override val navigationTransactionSupplyClientId: Int by Navigation.TransactionSupply.ClientId.collectAsState()

            override val navigationScreensTransactionSaleLeftClient1: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient1.collectAsState()
            override val navigationScreensTransactionSaleLeftClient2: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient2.collectAsState()
            override val navigationScreensTransactionSaleLeftClient3: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient3.collectAsState()
            override val navigationScreensTransactionSaleLeftClient4: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient4.collectAsState()
            override val navigationScreensTransactionSaleLeftClient5: List<NavigationScreenModel> by Navigation.TransactionSale.LeftClient5.collectAsState()

            override val navigationScreensTransactionSaleRightClient1: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient1.collectAsState()
            override val navigationScreensTransactionSaleRightClient2: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient2.collectAsState()
            override val navigationScreensTransactionSaleRightClient3: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient3.collectAsState()
            override val navigationScreensTransactionSaleRightClient4: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient4.collectAsState()
            override val navigationScreensTransactionSaleRightClient5: List<NavigationScreenModel> by Navigation.TransactionSale.RightClient5.collectAsState()

            override val navigationScreensTransactionReturnLeftClient1: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient1.collectAsState()
            override val navigationScreensTransactionReturnLeftClient2: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient2.collectAsState()
            override val navigationScreensTransactionReturnLeftClient3: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient3.collectAsState()
            override val navigationScreensTransactionReturnLeftClient4: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient4.collectAsState()
            override val navigationScreensTransactionReturnLeftClient5: List<NavigationScreenModel> by Navigation.TransactionReturn.LeftClient5.collectAsState()

            override val navigationScreensTransactionReturnRightClient1: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient1.collectAsState()
            override val navigationScreensTransactionReturnRightClient2: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient2.collectAsState()
            override val navigationScreensTransactionReturnRightClient3: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient3.collectAsState()
            override val navigationScreensTransactionReturnRightClient4: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient4.collectAsState()
            override val navigationScreensTransactionReturnRightClient5: List<NavigationScreenModel> by Navigation.TransactionReturn.RightClient5.collectAsState()

            override val navigationScreensTransactionSupplyLeftClient1: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient1.collectAsState()
            override val navigationScreensTransactionSupplyLeftClient2: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient2.collectAsState()
            override val navigationScreensTransactionSupplyLeftClient3: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient3.collectAsState()
            override val navigationScreensTransactionSupplyLeftClient4: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient4.collectAsState()
            override val navigationScreensTransactionSupplyLeftClient5: List<NavigationScreenModel> by Navigation.TransactionSupply.LeftClient5.collectAsState()

            override val navigationScreensTransactionSupplyRightClient1: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient1.collectAsState()
            override val navigationScreensTransactionSupplyRightClient2: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient2.collectAsState()
            override val navigationScreensTransactionSupplyRightClient3: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient3.collectAsState()
            override val navigationScreensTransactionSupplyRightClient4: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient4.collectAsState()
            override val navigationScreensTransactionSupplyRightClient5: List<NavigationScreenModel> by Navigation.TransactionSupply.RightClient5.collectAsState()

            override val navigationScreensStockLeft: List<NavigationScreenModel> by Navigation.Stock.Left.collectAsState()
            override val navigationScreensStockRight: List<NavigationScreenModel> by Navigation.Stock.Right.collectAsState()

            override val navigationScreensMenuLeft: List<NavigationScreenModel> by Navigation.Menu.Left.collectAsState()
            override val navigationScreensMenuRight: List<NavigationScreenModel> by Navigation.Menu.Right.collectAsState()
            override val navigationScreensUserAuthLeft: List<NavigationScreenModel> by Navigation.UserAuth.Left.collectAsState()
            override val navigationScreensUserAuthRight: List<NavigationScreenModel> by Navigation.UserAuth.Right.collectAsState()

            override val globalAppConfiguration: GlobalAppConfigurationDataModel by globalAppConfigurationState.payload.collectAsState()
            override val strings: List<LocalizedStringGroupDataModel>? by stringsState.payload.collectAsState()
            override val dimensions: List<StylizedDimensionGroupDataModel>? by dimensionsState.payload.collectAsState()
            override val colors: List<StylizedColorGroupDataModel>? by colorsState.payload.collectAsState()
            override val drawables: List<StylizedDrawablePathsGroupDataModel>? by drawablesState.payload.collectAsState()

            override val appModeId: Int by appModeState.collectAsState()
            override val appLanguage: String by appLanguageState.collectAsState()
            override val appThemeId: Long by appThemeIdState.collectAsState()
            override val appSizeModeId: Long by appSizeModeIdState.collectAsState()

            override val stringAppName: String by stringAppNameState.collectAsState()
            override val stringLogIn: String by stringLogInState.collectAsState()
            override val stringPhoneNumber: String by stringPhoneNumberState.collectAsState()
            override val stringEnterPhoneNumber: String by stringEnterPhoneNumberState.collectAsState()
            override val stringEmail: String by stringEmailState.collectAsState()
            override val stringEnterEmailAddress: String by stringEnterEmailAddressState.collectAsState()
            override val stringPassword: String by stringPasswordState.collectAsState()
            override val stringEnterPassword: String by stringEnterPasswordState.collectAsState()
            override val stringCancel: String by stringCancelState.collectAsState()
            override val stringClear: String by stringClearState.collectAsState()
            override val stringAuthenticationFailed: String by stringAuthenticationFailedState.collectAsState()
            override val stringPhoneNumberMustBe: String by stringPhoneNumberMustBeState.collectAsState()
            override val stringEmailMustBe: String by stringEmailMustBeState.collectAsState()
            override val stringPasswordMustBe: String by stringPasswordMustBeState.collectAsState()
            override val stringRepeatPassword: String by stringRepeatPasswordState.collectAsState()
            override val stringPasswordsMustMatch: String by stringPasswordsMustMatchState.collectAsState()
            override val stringFirstName: String by stringFirstNameState.collectAsState()
            override val stringLastName: String by stringLastNameState.collectAsState()
            override val stringEnterFirstName: String by stringEnterFirstNameState.collectAsState()
            override val stringEnterLastName: String by stringEnterLastNameState.collectAsState()
            override val stringUserWithThisPhoneNumberIsAlreadyRegistered: String by stringUserWithThisPhoneNumberIsAlreadyRegisteredState.collectAsState()
            override val stringUserWithThisEmailAddressIsAlreadyRegistered: String by stringUserWithThisEmailAddressIsAlreadyRegisteredState.collectAsState()
            override val stringSignUp: String by stringSignUpState.collectAsState()
            override val stringConfirm: String by stringConfirmState.collectAsState()
            override val stringSale: String by stringSaleState.collectAsState()
            override val stringReturn: String by stringReturnState.collectAsState()
            override val stringSupply: String by stringSupplyState.collectAsState()
            override val stringStock: String by stringStockState.collectAsState()
            override val stringMenu: String by stringMenuState.collectAsState()
            override val stringBack: String by stringBackState.collectAsState()
            override val stringAddGoodsItem: String by stringAddGoodsItemState.collectAsState()
            override val stringEditGoodsItem: String by stringEditGoodsItemState.collectAsState()
            override val stringUserAccount: String by stringUserAccountState.collectAsState()
            override val stringGoodsCategories: String by stringGoodsCategoriesState.collectAsState()
            override val stringAddGoodsCategory: String by stringAddGoodsCategoryState.collectAsState()
            override val stringEditGoodsCategory: String by stringEditGoodsCategoryState.collectAsState()
            override val stringStores: String by stringStoresState.collectAsState()
            override val stringAddStore: String by stringAddStoreState.collectAsState()
            override val stringEditStore: String by stringEditStoreState.collectAsState()
            override val stringSubscription: String by stringSubscriptionState.collectAsState()
            override val stringSubscriptionPlans: String by stringSubscriptionPlansState.collectAsState()
            override val stringTransactionHistory: String by stringTransactionHistoryState.collectAsState()
            override val stringReceipt: String by stringReceiptState.collectAsState()
            override val stringAnalytics: String by stringAnalyticsState.collectAsState()
            override val stringWorkers: String by stringWorkersState.collectAsState()
            override val stringAddWorker: String by stringAddWorkerState.collectAsState()
            override val stringEditWorker: String by stringEditWorkerState.collectAsState()
            override val stringSuppliers: String by stringSuppliersState.collectAsState()
            override val stringAddSupplier: String by stringAddSupplierState.collectAsState()
            override val stringEditSupplier: String by stringEditSupplierState.collectAsState()
            override val stringDebtors: String by stringDebtorsState.collectAsState()
            override val stringCloseDebt: String by stringCloseDebtState.collectAsState()
            override val stringDevices: String by stringDevicesState.collectAsState()
            override val stringAppLanguage: String by stringAppLanguageState.collectAsState()
            override val stringAppTheme: String by stringAppThemeState.collectAsState()
            override val stringSelect: String by stringSelectState.collectAsState()
            override val stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegistered: String by stringUserWithThisPhoneNumberAndEmailAddressIsAlreadyRegisteredState.collectAsState()
            override val stringFirstNameCannotBeEmptyOrJustWhitespaces: String by stringFirstNameCannotBeEmptyOrJustWhitespacesState.collectAsState()
            override val stringLastNameCannotBeEmptyOrJustWhitespaces: String by stringLastNameCannotBeEmptyOrJustWhitespacesState.collectAsState()
            override val stringSystemLanguage: String by stringSystemLanguageState.collectAsState()
            override val stringBluetoothPermissionRequired: String by stringBluetoothPermissionRequiredState.collectAsState()
            override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String by stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.collectAsState()
            override val stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettings: String by stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettingsState.collectAsState()
            override val stringBluetoothDisabled: String by stringBluetoothDisabledState.collectAsState()
            override val stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters: String by stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersState.collectAsState()
            override val stringSearchByAnyData: String by stringSearchByAnyDataState.collectAsState()
            override val stringListEmpty: String by stringListEmptyState.collectAsState()
            override val stringNoMatches: String by stringNoMatchesState.collectAsState()
            override val stringName: String by stringNameState.collectAsState()
            override val stringBarcode: String by stringBarcodeState.collectAsState()
            override val stringSupplyPrice: String by stringSupplyPriceState.collectAsState()
            override val stringSalePrice: String by stringSalePriceState.collectAsState()
            override val stringReturnPrice: String by stringReturnPriceState.collectAsState()
            override val stringCategory: String by stringCategoryState.collectAsState()
            override val stringSupplier: String by stringSupplierState.collectAsState()
            override val stringEnterName: String by stringEnterNameState.collectAsState()
            override val stringEnterBarcode: String by stringEnterBarcodeState.collectAsState()
            override val stringEnterSupplyPrice: String by stringEnterSupplyPriceState.collectAsState()
            override val stringEnterSalePrice: String by stringEnterSalePriceState.collectAsState()
            override val stringEnterReturnPrice: String by stringEnterReturnPriceState.collectAsState()
            override val stringSelectCategory: String by stringSelectCategoryState.collectAsState()
            override val stringSelectSupplier: String by stringSelectSupplierState.collectAsState()
            override val stringEdit: String by stringEditState.collectAsState()
            override val stringChangePassword: String by stringChangePasswordState.collectAsState()
            override val stringNewPassword: String by stringNewPasswordState.collectAsState()
            override val stringEnterNewPassword: String by stringEnterNewPasswordState.collectAsState()
            override val stringRepeatNewPassword: String by stringRepeatNewPasswordState.collectAsState()
            override val stringConfirmationPassword: String by stringConfirmationPasswordState.collectAsState()
            override val stringRequiredToEditAccount: String by stringRequiredToEditAccountState.collectAsState()
            override val stringAccountSuccessfullyUpdated: String by stringAccountSuccessfullyUpdatedState.collectAsState()
            override val stringLoggingOut: String by stringLoggingOutState.collectAsState()
            override val stringSessionTimeExpiredLoggingOut: String by stringSessionTimeExpiredLoggingOutState.collectAsState()
            override val stringAlias: String by stringAliasState.collectAsState()
            override val stringDescription: String by stringDescriptionState.collectAsState()
            override val stringEnterAlias: String by stringEnterAliasState.collectAsState()
            override val stringEnterDescription: String by stringEnterDescriptionState.collectAsState()
            override val stringOptional: String by stringOptionalState.collectAsState()
            override val stringLoggingIn: String by stringLoggingInState.collectAsState()
            override val stringSigningUp: String by stringSigningUpState.collectAsState()
            override val stringCompanyForm: String by stringCompanyFormState.collectAsState()
            override val stringMeasurementUnit: String by stringMeasurementUnitState.collectAsState()
            override val stringNoActiveStore: String by stringNoActiveStoreState.collectAsState()
            override val stringSelectInMenu: String by stringSelectInMenuState.collectAsState()
            override val stringSupplyData: String by stringSupplyDataState.collectAsState()
            override val stringSaleData: String by stringSaleDataState.collectAsState()
            override val stringReturnData: String by stringReturnDataState.collectAsState()
            override val stringAddSupplyData: String by stringAddSupplyDataState.collectAsState()
            override val stringAddSaleData: String by stringAddSaleDataState.collectAsState()
            override val stringAddReturnData: String by stringAddReturnDataState.collectAsState()
            override val stringAddBarcode: String by stringAddBarcodeState.collectAsState()
            override val stringAddName: String by stringAddNameState.collectAsState()
            override val stringPayment: String by stringPaymentState.collectAsState()
            override val stringAll: String by stringAllState.collectAsState()
            override val stringQuick: String by stringQuickState.collectAsState()
            override val stringCategories: String by stringCategoriesState.collectAsState()
            override val stringMain: String by stringMainState.collectAsState()
            override val stringAddTranslation: String by stringAddTranslationState.collectAsState()
            override val stringSetActive: String by stringSetActiveState.collectAsState()
            override val stringOutOfStock: String by stringOutOfStockState.collectAsState()
            override val stringDelete: String by stringDeleteState.collectAsState()
            override val stringCash: String by stringCashState.collectAsState()
            override val stringCashless: String by stringCashlessState.collectAsState()
            override val stringMixed: String by stringMixedState.collectAsState()
            override val stringAdd: String by stringAddState.collectAsState()
            override val stringSubtract: String by stringSubtractState.collectAsState()
            override val stringCurrentBatchData: String by stringCurrentQuantityDataState.collectAsState()
            override val stringEnterQuantity: String by stringEnterQuantityState.collectAsState()
            override val stringAddQuantityData: String by stringAddQuantityDataState.collectAsState()
            override val stringShelfBatch: String by stringShelfBatchState.collectAsState()
            override val stringActiveStore: String by stringActiveStoreState.collectAsState()
            override val stringMakeInactive: String by stringMakeInactiveState.collectAsState()
            override val stringCartEmpty: String by stringCartEmptyState.collectAsState()
            override val stringComplete: String by stringCompleteState.collectAsState()
            override val stringNoActiveWorkshift: String by stringNoActiveWorkshiftState.collectAsState()
            override val stringCart: String by stringCartState.collectAsState()
            override val stringAppMode: String by stringAppModeState.collectAsState()
            override val stringFinances: String by stringFinancesState.collectAsState()
            override val stringItems: String by stringItemsState.collectAsState()
            override val stringBatches: String by stringBatchesState.collectAsState()
            override val stringStandardPricesForSuppliers: String by stringStandardPricesForSuppliersState.collectAsState()
            override val stringEditableForIndividualBatches: String by stringEditableForIndividualBatchesState.collectAsState()
            override val stringBatchesData: String by stringBatchesDataState.collectAsState()
            override val stringReceiptNumber: String by stringReceiptNumberState.collectAsState()
            override val stringTransactionId: String by stringTransactionIdState.collectAsState()
            override val stringDate: String by stringDateState.collectAsState()
            override val stringCashier: String by stringCashierState.collectAsState()
            override val stringStore: String by stringStoreState.collectAsState()
            override val stringAddress: String by stringAddressState.collectAsState()
            override val stringPhone: String by stringPhoneState.collectAsState()
            override val stringTotal: String by stringTotalState.collectAsState()
            override val stringPaid: String by stringPaidState.collectAsState()
            override val stringDebt: String by stringDebtState.collectAsState()
            override val stringDebtor: String by stringDebtorState.collectAsState()
            override val stringDebtorPhone: String by stringDebtorPhoneState.collectAsState()
            override val stringChange: String by stringChangeState.collectAsState()
            override val stringVat: String by stringVatState.collectAsState()
            override val stringVatNotSpecified: String by stringVatNotSpecifiedState.collectAsState()
            override val stringFiscalStatus: String by stringFiscalStatusState.collectAsState()
            override val stringNonFiscalSoftwareReceipt: String by stringNonFiscalSoftwareReceiptState.collectAsState()
            override val stringThankYou: String by stringThankYouState.collectAsState()
            override val stringNoItems: String by stringNoItemsState.collectAsState()
            override val stringNoName: String by stringNoNameState.collectAsState()
            override val stringPdf: String by stringPdfState.collectAsState()
            override val stringShare: String by stringShareState.collectAsState()
            override val stringWhatsApp: String by stringWhatsAppState.collectAsState()
            override val stringPrint: String by stringPrintState.collectAsState()
            override val stringQuit: String by stringQuitState.collectAsState()
            override val stringReceiptPdfSaved: String by stringReceiptPdfSavedState.collectAsState()
            override val stringReceiptShared: String by stringReceiptSharedState.collectAsState()
            override val stringReceiptSentToWhatsApp: String by stringReceiptSentToWhatsAppState.collectAsState()
            override val stringReceiptSentToPrinter: String by stringReceiptSentToPrinterState.collectAsState()
            override val stringReceiptActionFailed: String by stringReceiptActionFailedState.collectAsState()
            override val stringGoodsReceiptTitle: String by stringGoodsReceiptTitleState.collectAsState()
            override val stringSaleReceiptTitle: String by stringSaleReceiptTitleState.collectAsState()
            override val stringReturnReceiptTitle: String by stringReturnReceiptTitleState.collectAsState()
            override val stringSupplyReceiptTitle: String by stringSupplyReceiptTitleState.collectAsState()
            override val stringDraft: String by stringDraftState.collectAsState()

            override val screenWidth: Dp by _screenWidthState.collectAsState()
            override val screenHeight: Dp by _screenHeightState.collectAsState()
            override val wideScreenMinWidth: Float by _wideScreenMinWidthState.collectAsState()
            override val boundWidgetWidth: Dp by _boundWidgetWidthState.collectAsState()
            override val isNarrowScreen: Boolean by _isNarrowScreenState.collectAsState()
            override val textSize: TextUnit by _textSizeState.collectAsState()
            override val titleTextSize: TextUnit by _titleTextSizeState.collectAsState()
            override val accentTextSize: TextUnit by _accentTextSizeState.collectAsState()
            override val smallTextSize: TextUnit by _smallTextSizeState.collectAsState()
            override val focusedBorderWidth: Dp by _focusedBorderWidthState.collectAsState()
            override val unfocusedBorderWidth: Dp by _unfocusedBorderWidthState.collectAsState()
            override val cornerRadius: Dp by _cornerRadiusState.collectAsState()
            override val iconSize: Dp by _iconSizeState.collectAsState()
            override val textFieldHeightMultiplierRelativeToTextSize: Float by _textFieldHeightMultiplierRelativeToTextSizeState.collectAsState()
            override val textFieldHeight: Dp by _textFieldHeightState.collectAsState()
            override val wideTextFieldHeight: Dp by _wideTextFieldHeightState.collectAsState()
            override val textFieldIconPadding: Dp by _textFieldIconPaddingState.collectAsState()
            override val marginTextField: Dp by _marginTextFieldState.collectAsState()
            override val marginTextFieldGroup: Dp by _marginTextFieldGroupState.collectAsState()

            override val AccentColor: Color by _AccentColorState.collectAsState()
            override val BackgroundColor: Color by _BackgroundColorState.collectAsState()
            override val TextColor: Color by _TextColorState.collectAsState()
            override val AccentTextColor: Color by _AccentTextColorState.collectAsState()
            override val PlaceholderTextColor: Color by _PlaceholderTextColorState.collectAsState()
            override val DisabledColor: Color by _DisabledColorState.collectAsState()
            override val ErrorColor: Color by _ErrorColorState.collectAsState()
            override val IconTintColor: Color by _IconTintColorState.collectAsState()
            override val OkayColor: Color by _OkayColorState.collectAsState()
            override val BorderlineBadColor: Color by _BorderlineBadColorState.collectAsState()

            override val drawablePathAITALogo: String by drawablePathAITALogoState.collectAsState()
            private val _drawableResAITALogo = MutableStateFlow(Res.drawable._0_0)
            override val drawableResAITALogo = _drawableResAITALogo.asStateFlow()

            override val drawablePathIconPassword: String by drawablePathIconPasswordState.collectAsState()
            private val _drawableResIconPassword = MutableStateFlow(Res.drawable._1_0)
            override val drawableResIconPassword = _drawableResIconPassword.asStateFlow()

            override val drawablePathIconSecurity: String by drawablePathIconSecurityState.collectAsState()
            private val _drawableResIconSecurity = MutableStateFlow(Res.drawable._1_0)
            override val drawableResIconSecurity: StateFlow<DrawableResource> = _drawableResIconSecurity.asStateFlow()

            override val drawablePathIconResponse: String by drawablePathIconResponseState.collectAsState()
            private val _drawableResIconResponse = MutableStateFlow(Res.drawable._53_0)
            override val drawableResIconResponse: StateFlow<DrawableResource> = _drawableResIconResponse.asStateFlow()

            override val drawablePathIconCancel: String by drawablePathIconCancelState.collectAsState()
            private val _drawableResIconCancel = MutableStateFlow(Res.drawable._2_0)
            override val drawableResIconCancel = _drawableResIconCancel.asStateFlow()

            override val drawablePathIconEyeHide: String by drawablePathIconEyeHideState.collectAsState()
            private val _drawableResIconEyeHide = MutableStateFlow(Res.drawable._3_0)
            override val drawableResIconEyeHide = _drawableResIconEyeHide.asStateFlow()

            override val drawablePathIconEyeShow: String by drawablePathIconEyeShowState.collectAsState()
            private val _drawableResIconEyeShow = MutableStateFlow(Res.drawable._4_0)
            override val drawableResIconEyeShow = _drawableResIconEyeShow.asStateFlow()

            override val drawablePathIconEmail: String by drawablePathIconEmailState.collectAsState()
            private val _drawableResIconEmail = MutableStateFlow(Res.drawable._5_0)
            override val drawableResIconEmail = _drawableResIconEmail.asStateFlow()

            override val drawablePathIconPhone: String by drawablePathIconPhoneState.collectAsState()
            private val _drawableResIconPhone = MutableStateFlow(Res.drawable._6_0)
            override val drawableResIconPhone: StateFlow<DrawableResource> = _drawableResIconPhone.asStateFlow()

            override val drawablePathIconExpandMore: String by drawablePathIconExpandMoreState.collectAsState()
            private val _drawableResIconExpandMore = MutableStateFlow(Res.drawable._7_0)
            override val drawableResIconExpandMore: StateFlow<DrawableResource> = _drawableResIconExpandMore.asStateFlow()

            override val drawablePathIconExpandLess: String by drawablePathIconExpandLessState.collectAsState()
            private val _drawableResIconExpandLess = MutableStateFlow(Res.drawable._8_0)
            override val drawableResIconExpandLess: StateFlow<DrawableResource> = _drawableResIconExpandLess.asStateFlow()

            override val drawablePathIconPerson: String by drawablePathIconPersonState.collectAsState()
            private val _drawableResIconPerson = MutableStateFlow(Res.drawable._9_0)
            override val drawableResIconPerson: StateFlow<DrawableResource> = _drawableResIconPerson.asStateFlow()

            override val drawablePathIconTransactionSale: String by drawablePathIconTransactionSaleState.collectAsState()
            private val _drawableResIconTransactionSale = MutableStateFlow(Res.drawable._10_0)
            override val drawableResIconTransactionSale: StateFlow<DrawableResource> =
                _drawableResIconTransactionSale.asStateFlow()

            override val drawablePathIconTransactionReturn: String by drawablePathIconTransactionReturnState.collectAsState()
            private val _drawableResIconTransactionReturn = MutableStateFlow(Res.drawable._11_0)
            override val drawableResIconTransactionReturn: StateFlow<DrawableResource> =
                _drawableResIconTransactionReturn.asStateFlow()

            override val drawablePathIconTransactionSupply: String by drawablePathIconTransactionSupplyState.collectAsState()
            private val _drawableResIconTransactionSupply = MutableStateFlow(Res.drawable._12_0)
            override val drawableResIconTransactionSupply: StateFlow<DrawableResource> =
                _drawableResIconTransactionSupply.asStateFlow()

            override val drawablePathIconTransactionSelection: String by drawablePathIconTransactionSelectionState.collectAsState()
            private val _drawableResIconTransactionSelection = MutableStateFlow(Res.drawable._57_0)
            override val drawableResIconTransactionSelection: StateFlow<DrawableResource> =
                _drawableResIconTransactionSelection.asStateFlow()

            override val drawablePathIconStock: String by drawablePathIconStockState.collectAsState()
            private val _drawableResIconStock = MutableStateFlow(Res.drawable._13_0)
            override val drawableResIconStock: StateFlow<DrawableResource> = _drawableResIconStock.asStateFlow()

            override val drawablePathIconMenu: String by drawablePathIconMenuState.collectAsState()
            private val _drawableResIconMenu = MutableStateFlow(Res.drawable._14_0)
            override val drawableResIconMenu: StateFlow<DrawableResource> = _drawableResIconMenu.asStateFlow()

            override val drawablePathIconBackArrow: String by drawablePathIconBackArrowState.collectAsState()
            private val _drawableResIconBackArrow = MutableStateFlow(Res.drawable._15_0)
            override val drawableResIconBackArrow: StateFlow<DrawableResource> = _drawableResIconBackArrow.asStateFlow()

            override val drawablePathIconAdd: String by drawablePathIconAddState.collectAsState()
            private val _drawableResIconAdd = MutableStateFlow(Res.drawable._16_0)
            override val drawableResIconAdd: StateFlow<DrawableResource> = _drawableResIconAdd.asStateFlow()

            override val drawablePathIconUserAccount: String by drawablePathIconUserAccountState.collectAsState()
            private val _drawableResIconUserAccount = MutableStateFlow(Res.drawable._17_0)
            override val drawableResIconUserAccount: StateFlow<DrawableResource> = _drawableResIconUserAccount.asStateFlow()

            override val drawablePathIconGoodsCategories: String by drawablePathIconGoodsCategoriesState.collectAsState()
            private val _drawableResIconGoodsCategories = MutableStateFlow(Res.drawable._18_0)
            override val drawableResIconGoodsCategories: StateFlow<DrawableResource> =
                _drawableResIconGoodsCategories.asStateFlow()

            override val drawablePathIconStores: String by drawablePathIconStoresState.collectAsState()
            private val _drawableResIconStores = MutableStateFlow(Res.drawable._19_0)
            override val drawableResIconStores: StateFlow<DrawableResource> = _drawableResIconStores.asStateFlow()

            override val drawablePathIconTransactionHistory: String by drawablePathIconTransactionHistoryState.collectAsState()
            private val _drawableResIconTransactionHistory = MutableStateFlow(Res.drawable._20_0)
            override val drawableResIconTransactionHistory: StateFlow<DrawableResource> =
                _drawableResIconTransactionHistory.asStateFlow()

            override val drawablePathIconLog: String by drawablePathIconLogState.collectAsState()
            private val _drawableResIconLog = MutableStateFlow(Res.drawable._49_0)
            override val drawableResIconLog: StateFlow<DrawableResource> = _drawableResIconLog.asStateFlow()

            override val drawablePathIconPromos: String by drawablePathIconPromosState.collectAsState()
            private val _drawableResIconPromos = MutableStateFlow(Res.drawable._50_0)
            override val drawableResIconPromos: StateFlow<DrawableResource> = _drawableResIconPromos.asStateFlow()

            override val drawablePathIconAnalytics: String by drawablePathIconAnalyticsState.collectAsState()
            private val _drawableResIconAnalytics = MutableStateFlow(Res.drawable._21_0)
            override val drawableResIconAnalytics: StateFlow<DrawableResource> = _drawableResIconAnalytics.asStateFlow()

            override val drawablePathIconAnalyticsReport: String by drawablePathIconAnalyticsReportState.collectAsState()
            private val _drawableResIconAnalyticsReport = MutableStateFlow(Res.drawable._62_0)
            override val drawableResIconAnalyticsReport: StateFlow<DrawableResource> = _drawableResIconAnalyticsReport.asStateFlow()

            override val drawablePathIconLabelPrinter: String by drawablePathIconLabelPrinterState.collectAsState()
            private val _drawableResIconLabelPrinter = MutableStateFlow(Res.drawable._63_0)
            override val drawableResIconLabelPrinter: StateFlow<DrawableResource> = _drawableResIconLabelPrinter.asStateFlow()

            override val drawablePathIconBarcodeGenerate: String by drawablePathIconBarcodeGenerateState.collectAsState()
            private val _drawableResIconBarcodeGenerate = MutableStateFlow(Res.drawable._64_0)
            override val drawableResIconBarcodeGenerate: StateFlow<DrawableResource> = _drawableResIconBarcodeGenerate.asStateFlow()

            override val drawablePathIconPrintTag: String by drawablePathIconPrintTagState.collectAsState()
            private val _drawableResIconPrintTag = MutableStateFlow(Res.drawable._65_0)
            override val drawableResIconPrintTag: StateFlow<DrawableResource> = _drawableResIconPrintTag.asStateFlow()

            override val drawablePathIconWorkerRoleTemplates: String by drawablePathIconWorkerRoleTemplatesState.collectAsState()
            private val _drawableResIconWorkerRoleTemplates = MutableStateFlow(Res.drawable._66_0)
            override val drawableResIconWorkerRoleTemplates: StateFlow<DrawableResource> = _drawableResIconWorkerRoleTemplates.asStateFlow()

            override val drawablePathIconStockHistory: String by drawablePathIconStockHistoryState.collectAsState()
            private val _drawableResIconStockHistory = MutableStateFlow(Res.drawable._67_0)
            override val drawableResIconStockHistory: StateFlow<DrawableResource> = _drawableResIconStockHistory.asStateFlow()

            override val drawablePathIconAppModeStore: String by drawablePathIconAppModeStoreState.collectAsState()
            private val _drawableResIconAppModeStore = MutableStateFlow(Res.drawable._68_0)
            override val drawableResIconAppModeStore: StateFlow<DrawableResource> = _drawableResIconAppModeStore.asStateFlow()

            override val drawablePathIconAppModeBuyer: String by drawablePathIconAppModeBuyerState.collectAsState()
            private val _drawableResIconAppModeBuyer = MutableStateFlow(Res.drawable._69_0)
            override val drawableResIconAppModeBuyer: StateFlow<DrawableResource> = _drawableResIconAppModeBuyer.asStateFlow()

            override val drawablePathIconAppModeSupplier: String by drawablePathIconAppModeSupplierState.collectAsState()
            private val _drawableResIconAppModeSupplier = MutableStateFlow(Res.drawable._70_0)
            override val drawableResIconAppModeSupplier: StateFlow<DrawableResource> = _drawableResIconAppModeSupplier.asStateFlow()

            override val drawablePathIconAppModeManufacturer: String by drawablePathIconAppModeManufacturerState.collectAsState()
            private val _drawableResIconAppModeManufacturer = MutableStateFlow(Res.drawable._71_0)
            override val drawableResIconAppModeManufacturer: StateFlow<DrawableResource> = _drawableResIconAppModeManufacturer.asStateFlow()

            override val drawablePathIconSupplierCatalog: String by drawablePathIconSupplierCatalogState.collectAsState()
            private val _drawableResIconSupplierCatalog = MutableStateFlow(Res.drawable._72_0)
            override val drawableResIconSupplierCatalog: StateFlow<DrawableResource> = _drawableResIconSupplierCatalog.asStateFlow()

            override val drawablePathIconSupplierContracts: String by drawablePathIconSupplierContractsState.collectAsState()
            private val _drawableResIconSupplierContracts = MutableStateFlow(Res.drawable._76_0)
            override val drawableResIconSupplierContracts: StateFlow<DrawableResource> = _drawableResIconSupplierContracts.asStateFlow()

            override val drawablePathIconSupplierPartners: String by drawablePathIconSupplierPartnersState.collectAsState()
            private val _drawableResIconSupplierPartners = MutableStateFlow(Res.drawable._75_0)
            override val drawableResIconSupplierPartners: StateFlow<DrawableResource> = _drawableResIconSupplierPartners.asStateFlow()

            override val drawablePathIconSupplierDemandRadar: String by drawablePathIconSupplierDemandRadarState.collectAsState()
            private val _drawableResIconSupplierDemandRadar = MutableStateFlow(Res.drawable._77_0)
            override val drawableResIconSupplierDemandRadar: StateFlow<DrawableResource> = _drawableResIconSupplierDemandRadar.asStateFlow()

            override val drawablePathIconSupplierBackorderRecovery: String by drawablePathIconSupplierBackorderRecoveryState.collectAsState()
            private val _drawableResIconSupplierBackorderRecovery = MutableStateFlow(Res.drawable._91_0)
            override val drawableResIconSupplierBackorderRecovery: StateFlow<DrawableResource> = _drawableResIconSupplierBackorderRecovery.asStateFlow()

            override val drawablePathIconSupplierRecoveryOwner: String by drawablePathIconSupplierRecoveryOwnerState.collectAsState()
            private val _drawableResIconSupplierRecoveryOwner = MutableStateFlow(Res.drawable._92_0)
            override val drawableResIconSupplierRecoveryOwner: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryOwner.asStateFlow()

            override val drawablePathIconSupplierRecoveryClock: String by drawablePathIconSupplierRecoveryClockState.collectAsState()
            private val _drawableResIconSupplierRecoveryClock = MutableStateFlow(Res.drawable._93_0)
            override val drawableResIconSupplierRecoveryClock: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryClock.asStateFlow()

            override val drawablePathIconSupplierRecoveryProof: String by drawablePathIconSupplierRecoveryProofState.collectAsState()
            private val _drawableResIconSupplierRecoveryProof = MutableStateFlow(Res.drawable._94_0)
            override val drawableResIconSupplierRecoveryProof: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryProof.asStateFlow()

            override val drawablePathIconSupplierRecoveryResolution: String by drawablePathIconSupplierRecoveryResolutionState.collectAsState()
            private val _drawableResIconSupplierRecoveryResolution = MutableStateFlow(Res.drawable._95_0)
            override val drawableResIconSupplierRecoveryResolution: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryResolution.asStateFlow()

            override val drawablePathIconSupplierRecoveryContact: String by drawablePathIconSupplierRecoveryContactState.collectAsState()
            private val _drawableResIconSupplierRecoveryContact = MutableStateFlow(Res.drawable._96_0)
            override val drawableResIconSupplierRecoveryContact: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryContact.asStateFlow()

            override val drawablePathIconSupplierRecoveryRisk: String by drawablePathIconSupplierRecoveryRiskState.collectAsState()
            private val _drawableResIconSupplierRecoveryRisk = MutableStateFlow(Res.drawable._97_0)
            override val drawableResIconSupplierRecoveryRisk: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryRisk.asStateFlow()

            override val drawablePathIconSupplierRecoveryConfidence: String by drawablePathIconSupplierRecoveryConfidenceState.collectAsState()
            private val _drawableResIconSupplierRecoveryConfidence = MutableStateFlow(Res.drawable._98_0)
            override val drawableResIconSupplierRecoveryConfidence: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryConfidence.asStateFlow()

            override val drawablePathIconSupplierRecoveryFollowUp: String by drawablePathIconSupplierRecoveryFollowUpState.collectAsState()
            private val _drawableResIconSupplierRecoveryFollowUp = MutableStateFlow(Res.drawable._99_0)
            override val drawableResIconSupplierRecoveryFollowUp: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryFollowUp.asStateFlow()

            override val drawablePathIconSupplierRecoveryHandoff: String by drawablePathIconSupplierRecoveryHandoffState.collectAsState()
            private val _drawableResIconSupplierRecoveryHandoff = MutableStateFlow(Res.drawable._100_0)
            override val drawableResIconSupplierRecoveryHandoff: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryHandoff.asStateFlow()

            override val drawablePathIconSupplierRecoveryClosure: String by drawablePathIconSupplierRecoveryClosureState.collectAsState()
            private val _drawableResIconSupplierRecoveryClosure = MutableStateFlow(Res.drawable._101_0)
            override val drawableResIconSupplierRecoveryClosure: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryClosure.asStateFlow()

            override val drawablePathIconSupplierRecoveryLedger: String by drawablePathIconSupplierRecoveryLedgerState.collectAsState()
            private val _drawableResIconSupplierRecoveryLedger = MutableStateFlow(Res.drawable._102_0)
            override val drawableResIconSupplierRecoveryLedger: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryLedger.asStateFlow()

            override val drawablePathIconSupplierRecoveryTriage: String by drawablePathIconSupplierRecoveryTriageState.collectAsState()
            private val _drawableResIconSupplierRecoveryTriage = MutableStateFlow(Res.drawable._103_0)
            override val drawableResIconSupplierRecoveryTriage: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryTriage.asStateFlow()

            override val drawablePathIconSupplierRecoveryCommand: String by drawablePathIconSupplierRecoveryCommandState.collectAsState()
            private val _drawableResIconSupplierRecoveryCommand = MutableStateFlow(Res.drawable._104_0)
            override val drawableResIconSupplierRecoveryCommand: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryCommand.asStateFlow()

            override val drawablePathIconSupplierRecoveryPromiseShield: String by drawablePathIconSupplierRecoveryPromiseShieldState.collectAsState()
            private val _drawableResIconSupplierRecoveryPromiseShield = MutableStateFlow(Res.drawable._105_0)
            override val drawableResIconSupplierRecoveryPromiseShield: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryPromiseShield.asStateFlow()

            override val drawablePathIconSupplierRecoveryDesk: String by drawablePathIconSupplierRecoveryDeskState.collectAsState()
            private val _drawableResIconSupplierRecoveryDesk = MutableStateFlow(Res.drawable._106_0)
            override val drawableResIconSupplierRecoveryDesk: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryDesk.asStateFlow()

            override val drawablePathIconSupplierRecoveryWave: String by drawablePathIconSupplierRecoveryWaveState.collectAsState()
            private val _drawableResIconSupplierRecoveryWave = MutableStateFlow(Res.drawable._107_0)
            override val drawableResIconSupplierRecoveryWave: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryWave.asStateFlow()
            override val drawablePathIconSupplierRecoveryAging: String by drawablePathIconSupplierRecoveryAgingState.collectAsState()
            private val _drawableResIconSupplierRecoveryAging = MutableStateFlow(Res.drawable._108_0)
            override val drawableResIconSupplierRecoveryAging: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryAging.asStateFlow()
            override val drawablePathIconSupplierRecoveryBottleneck: String by drawablePathIconSupplierRecoveryBottleneckState.collectAsState()
            private val _drawableResIconSupplierRecoveryBottleneck = MutableStateFlow(Res.drawable._109_0)
            override val drawableResIconSupplierRecoveryBottleneck: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryBottleneck.asStateFlow()
            override val drawablePathIconSupplierRecoveryLoad: String by drawablePathIconSupplierRecoveryLoadState.collectAsState()
            private val _drawableResIconSupplierRecoveryLoad = MutableStateFlow(Res.drawable._110_0)
            override val drawableResIconSupplierRecoveryLoad: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryLoad.asStateFlow()
            override val drawablePathIconSupplierRecoveryImpact: String by drawablePathIconSupplierRecoveryImpactState.collectAsState()
            private val _drawableResIconSupplierRecoveryImpact = MutableStateFlow(Res.drawable._111_0)
            override val drawableResIconSupplierRecoveryImpact: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryImpact.asStateFlow()
            override val drawablePathIconSupplierRecoveryCommit: String by drawablePathIconSupplierRecoveryCommitState.collectAsState()
            private val _drawableResIconSupplierRecoveryCommit = MutableStateFlow(Res.drawable._112_0)
            override val drawableResIconSupplierRecoveryCommit: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryCommit.asStateFlow()
            override val drawablePathIconSupplierRecoveryAllocation: String by drawablePathIconSupplierRecoveryAllocationState.collectAsState()
            private val _drawableResIconSupplierRecoveryAllocation = MutableStateFlow(Res.drawable._113_0)
            override val drawableResIconSupplierRecoveryAllocation: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryAllocation.asStateFlow()
            override val drawablePathIconSupplierRecoveryException: String by drawablePathIconSupplierRecoveryExceptionState.collectAsState()
            private val _drawableResIconSupplierRecoveryException = MutableStateFlow(Res.drawable._114_0)
            override val drawableResIconSupplierRecoveryException: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryException.asStateFlow()
            override val drawablePathIconSupplierRecoveryCause: String by drawablePathIconSupplierRecoveryCauseState.collectAsState()
            private val _drawableResIconSupplierRecoveryCause = MutableStateFlow(Res.drawable._115_0)
            override val drawableResIconSupplierRecoveryCause: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryCause.asStateFlow()
            override val drawablePathIconSupplierRecoveryVerification: String by drawablePathIconSupplierRecoveryVerificationState.collectAsState()
            private val _drawableResIconSupplierRecoveryVerification = MutableStateFlow(Res.drawable._116_0)
            override val drawableResIconSupplierRecoveryVerification: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryVerification.asStateFlow()
            override val drawablePathIconSupplierRecoveryApproval: String by drawablePathIconSupplierRecoveryApprovalState.collectAsState()
            private val _drawableResIconSupplierRecoveryApproval = MutableStateFlow(Res.drawable._117_0)
            override val drawableResIconSupplierRecoveryApproval: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryApproval.asStateFlow()
            override val drawablePathIconSupplierRecoveryExecution: String by drawablePathIconSupplierRecoveryExecutionState.collectAsState()
            private val _drawableResIconSupplierRecoveryExecution = MutableStateFlow(Res.drawable._118_0)
            override val drawableResIconSupplierRecoveryExecution: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryExecution.asStateFlow()
            override val drawablePathIconSupplierRecoveryRelease: String by drawablePathIconSupplierRecoveryReleaseState.collectAsState()
            private val _drawableResIconSupplierRecoveryRelease = MutableStateFlow(Res.drawable._119_0)
            override val drawableResIconSupplierRecoveryRelease: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryRelease.asStateFlow()
            override val drawablePathIconSupplierRecoverySeal: String by drawablePathIconSupplierRecoverySealState.collectAsState()
            private val _drawableResIconSupplierRecoverySeal = MutableStateFlow(Res.drawable._120_0)
            override val drawableResIconSupplierRecoverySeal: StateFlow<DrawableResource> = _drawableResIconSupplierRecoverySeal.asStateFlow()
            override val drawablePathIconSupplierRecoveryCloseout: String by drawablePathIconSupplierRecoveryCloseoutState.collectAsState()
            private val _drawableResIconSupplierRecoveryCloseout = MutableStateFlow(Res.drawable._121_0)
            override val drawableResIconSupplierRecoveryCloseout: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryCloseout.asStateFlow()
            override val drawablePathIconSupplierRecoveryReopen: String by drawablePathIconSupplierRecoveryReopenState.collectAsState()
            private val _drawableResIconSupplierRecoveryReopen = MutableStateFlow(Res.drawable._122_0)
            override val drawableResIconSupplierRecoveryReopen: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryReopen.asStateFlow()
            override val drawablePathIconSupplierRecoveryReconciliation: String by drawablePathIconSupplierRecoveryReconciliationState.collectAsState()
            private val _drawableResIconSupplierRecoveryReconciliation = MutableStateFlow(Res.drawable._123_0)
            override val drawableResIconSupplierRecoveryReconciliation: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryReconciliation.asStateFlow()
            override val drawablePathIconSupplierRecoveryAudit: String by drawablePathIconSupplierRecoveryAuditState.collectAsState()
            private val _drawableResIconSupplierRecoveryAudit = MutableStateFlow(Res.drawable._124_0)
            override val drawableResIconSupplierRecoveryAudit: StateFlow<DrawableResource> = _drawableResIconSupplierRecoveryAudit.asStateFlow()

            override val drawablePathIconSupplierDispatch: String by drawablePathIconSupplierDispatchState.collectAsState()
            private val _drawableResIconSupplierDispatch = MutableStateFlow(Res.drawable._78_0)
            override val drawableResIconSupplierDispatch: StateFlow<DrawableResource> = _drawableResIconSupplierDispatch.asStateFlow()

            override val drawablePathIconSupplierTermsGuard: String by drawablePathIconSupplierTermsGuardState.collectAsState()
            private val _drawableResIconSupplierTermsGuard = MutableStateFlow(Res.drawable._89_0)
            override val drawableResIconSupplierTermsGuard: StateFlow<DrawableResource> = _drawableResIconSupplierTermsGuard.asStateFlow()

            override val drawablePathIconBuyerAgeRestriction: String by drawablePathIconBuyerAgeRestrictionState.collectAsState()
            private val _drawableResIconBuyerAgeRestriction = MutableStateFlow(Res.drawable._73_0)
            override val drawableResIconBuyerAgeRestriction: StateFlow<DrawableResource> = _drawableResIconBuyerAgeRestriction.asStateFlow()

            override val drawablePathIconTransactionTimeRestriction: String by drawablePathIconTransactionTimeRestrictionState.collectAsState()
            private val _drawableResIconTransactionTimeRestriction = MutableStateFlow(Res.drawable._74_0)
            override val drawableResIconTransactionTimeRestriction: StateFlow<DrawableResource> = _drawableResIconTransactionTimeRestriction.asStateFlow()

            override val drawablePathIconWorkers: String by drawablePathIconWorkersState.collectAsState()
            private val _drawableResIconWorkers = MutableStateFlow(Res.drawable._22_0)
            override val drawableResIconWorkers: StateFlow<DrawableResource> = _drawableResIconWorkers.asStateFlow()

            override val drawablePathIconSuppliers: String by drawablePathIconSuppliersState.collectAsState()
            private val _drawableResIconSuppliers = MutableStateFlow(Res.drawable._23_0)
            override val drawableResIconSuppliers: StateFlow<DrawableResource> = _drawableResIconSuppliers.asStateFlow()

            override val drawablePathIconDebtors: String by drawablePathIconDebtorsState.collectAsState()
            private val _drawableResIconDebtors = MutableStateFlow(Res.drawable._24_0)
            override val drawableResIconDebtors: StateFlow<DrawableResource> = _drawableResIconDebtors.asStateFlow()

            override val drawablePathIconDevices: String by drawablePathIconDevicesState.collectAsState()
            private val _drawableResIconDevices = MutableStateFlow(Res.drawable._25_0)
            override val drawableResIconDevices: StateFlow<DrawableResource> = _drawableResIconDevices.asStateFlow()

            override val drawablePathIconAppLanguage: String by drawablePathIconAppLanguageState.collectAsState()
            private val _drawableResIconAppLanguage = MutableStateFlow(Res.drawable._26_0)
            override val drawableResIconAppLanguage: StateFlow<DrawableResource> = _drawableResIconAppLanguage.asStateFlow()

            override val drawablePathIconAppTheme: String by drawablePathIconAppThemeState.collectAsState()
            private val _drawableResIconAppTheme = MutableStateFlow(Res.drawable._27_0)
            override val drawableResIconAppTheme: StateFlow<DrawableResource> = _drawableResIconAppTheme.asStateFlow()

            override val drawablePathIconAppScale: String by drawablePathIconAppScaleState.collectAsState()
            private val _drawableResIconAppScale = MutableStateFlow(Res.drawable._48_0)
            override val drawableResIconAppScale: StateFlow<DrawableResource> = _drawableResIconAppScale.asStateFlow()

            override val drawablePathIconCheck: String by drawablePathIconCheckState.collectAsState()
            private val _drawableResIconCheck = MutableStateFlow(Res.drawable._28_0)
            override val drawableResIconCheck: StateFlow<DrawableResource> = _drawableResIconCheck.asStateFlow()

            override val drawablePathIconEdit: String by drawablePathIconEditState.collectAsState()
            private val _drawableResIconEdit = MutableStateFlow(Res.drawable._29_0)
            override val drawableResIconEdit: StateFlow<DrawableResource> = _drawableResIconEdit.asStateFlow()

            override val drawablePathIconSettings: String by drawablePathIconSettingsState.collectAsState()
            private val _drawableResIconSettings = MutableStateFlow(Res.drawable._30_0)
            override val drawableResIconSettings: StateFlow<DrawableResource> = _drawableResIconSettings.asStateFlow()

            override val drawablePathIconSearch: String by drawablePathIconSearchState.collectAsState()
            private val _drawableResIconSearch = MutableStateFlow(Res.drawable._31_0)
            override val drawableResIconSearch: StateFlow<DrawableResource> = _drawableResIconSearch.asStateFlow()

            override val drawablePathIconBarcodeCamScanner: String by drawablePathIconBarcodeCamScannerState.collectAsState()
            private val _drawableResIconBarcodeCamScanner = MutableStateFlow(Res.drawable._32_0)
            override val drawableResIconBarcodeCamScanner: StateFlow<DrawableResource> =
                _drawableResIconBarcodeCamScanner.asStateFlow()

            override val drawablePathIconBarcodeScanner: String by drawablePathIconBarcodeScannerState.collectAsState()
            private val _drawableResIconBarcodeScanner = MutableStateFlow(Res.drawable._51_0)
            override val drawableResIconBarcodeScanner: StateFlow<DrawableResource> =
                _drawableResIconBarcodeScanner.asStateFlow()

            override val drawablePathIconBarcodeType: String by drawablePathIconBarcodeTypeState.collectAsState()
            private val _drawableResIconBarcodeType = MutableStateFlow(Res.drawable._55_0)
            override val drawableResIconBarcodeType: StateFlow<DrawableResource> =
                _drawableResIconBarcodeType.asStateFlow()

            override val drawablePathIconVoiceInput: String by drawablePathIconVoiceInputState.collectAsState()
            private val _drawableResIconVoiceInput = MutableStateFlow(Res.drawable._52_0)
            override val drawableResIconVoiceInput: StateFlow<DrawableResource> =
                _drawableResIconVoiceInput.asStateFlow()

            override val drawablePathIconDelete: String by drawablePathIconDeleteState.collectAsState()
            private val _drawableResIconDelete = MutableStateFlow(Res.drawable._33_0)
            override val drawableResIconDelete: StateFlow<DrawableResource> = _drawableResIconDelete.asStateFlow()

            override val drawablePathIconExit: String by drawablePathIconExitState.collectAsState()
            private val _drawableResIconExit = MutableStateFlow(Res.drawable._34_0)
            override val drawableResIconExit: StateFlow<DrawableResource> = _drawableResIconExit.asStateFlow()

            override val drawablePathIconSwitch: String by drawablePathIconSwitchState.collectAsState()
            private val _drawableResIconSwitch = MutableStateFlow(Res.drawable._35_0)
            override val drawableResIconSwitch: StateFlow<DrawableResource> = _drawableResIconSwitch.asStateFlow()

            override val drawablePathIconSort: String by drawablePathIconSortState.collectAsState()
            private val _drawableResIconSort = MutableStateFlow(Res.drawable._61_0)
            override val drawableResIconSort: StateFlow<DrawableResource> = _drawableResIconSort.asStateFlow()

            override val drawablePathIconCart: String by drawablePathIconCartState.collectAsState()
            private val _drawableResIconCart = MutableStateFlow(Res.drawable._36_0)
            override val drawableResIconCart: StateFlow<DrawableResource> = _drawableResIconCart.asStateFlow()

            override val drawablePathIconAddCart: String by drawablePathIconAddCartState.collectAsState()
            private val _drawableResIconAddCart = MutableStateFlow(Res.drawable._37_0)
            override val drawableResIconAddCart: StateFlow<DrawableResource> = _drawableResIconAddCart.asStateFlow()

            override val drawablePathIconSubtract: String by drawablePathIconSubtractState.collectAsState()
            private val _drawableResIconSubtract = MutableStateFlow(Res.drawable._38_0)
            override val drawableResIconSubtract: StateFlow<DrawableResource> = _drawableResIconSubtract.asStateFlow()

            override val drawablePathIconReceipt: String by drawablePathIconReceiptState.collectAsState()
            private val _drawableResIconReceipt = MutableStateFlow(Res.drawable._39_0)
            override val drawableResIconReceipt: StateFlow<DrawableResource> = _drawableResIconReceipt.asStateFlow()

            override val drawablePathIconFinances: String by drawablePathIconFinancesState.collectAsState()
            private val _drawableResIconFinances = MutableStateFlow(Res.drawable._40_0)
            override val drawableResIconFinances = _drawableResIconFinances.asStateFlow()

            override val drawablePathIconClipboard: String by drawablePathIconClipboardState.collectAsState()
            private val _drawableResIconClipboard = MutableStateFlow(Res.drawable._41_0)
            override val drawableResIconClipboard: StateFlow<DrawableResource> = _drawableResIconClipboard.asStateFlow()

            override val drawablePathIconSupport: String by drawablePathIconSupportState.collectAsState()
            private val _drawableResIconSupport = MutableStateFlow(Res.drawable._42_0)
            override val drawableResIconSupport: StateFlow<DrawableResource> = _drawableResIconSupport.asStateFlow()

            override val drawablePathIconSubscription: String by drawablePathIconSubscriptionState.collectAsState()
            private val _drawableResIconSubscription = MutableStateFlow(Res.drawable._43_0)
            override val drawableResIconSubscription: StateFlow<DrawableResource> = _drawableResIconSubscription.asStateFlow()

            override val drawablePathIconThemeLight: String by drawablePathIconThemeLightState.collectAsState()
            private val _drawableResIconThemeLight = MutableStateFlow(Res.drawable._44_0)
            override val drawableResIconThemeLight: StateFlow<DrawableResource> = _drawableResIconThemeLight.asStateFlow()

            override val drawablePathIconThemeDark: String by drawablePathIconThemeDarkState.collectAsState()
            private val _drawableResIconThemeDark = MutableStateFlow(Res.drawable._45_0)
            override val drawableResIconThemeDark: StateFlow<DrawableResource> = _drawableResIconThemeDark.asStateFlow()

            override val drawablePathIconShare: String by drawablePathIconShareState.collectAsState()
            private val _drawableResIconShare = MutableStateFlow(Res.drawable._46_0)
            override val drawableResIconShare: StateFlow<DrawableResource> = _drawableResIconShare.asStateFlow()

            override val drawablePathIconWhatsApp: String by drawablePathIconWhatsAppState.collectAsState()
            private val _drawableResIconWhatsApp = MutableStateFlow(Res.drawable._47_0)
            override val drawableResIconWhatsApp: StateFlow<DrawableResource> = _drawableResIconWhatsApp.asStateFlow()

            override val drawablePathIconRefresh: String by drawablePathIconRefreshState.collectAsState()
            private val _drawableResIconRefresh = MutableStateFlow(Res.drawable._54_0)
            override val drawableResIconRefresh: StateFlow<DrawableResource> = _drawableResIconRefresh.asStateFlow()


            override suspend fun updateDrawableResources() {
                _drawableResAITALogo.emit(if (stateValues.appThemeId == 1L) Res.drawable._0_1 else Res.drawable._0_0)

                _drawableResIconPassword.emit(if (stateValues.appThemeId == 1L) Res.drawable._1_1 else Res.drawable._1_0)

                _drawableResIconSecurity.emit(if (stateValues.appThemeId == 1L) Res.drawable._1_1 else Res.drawable._1_0)

                _drawableResIconResponse.emit(if (stateValues.appThemeId == 1L) Res.drawable._53_1 else Res.drawable._53_0)

                _drawableResIconCancel.emit(if (stateValues.appThemeId == 1L) Res.drawable._2_1 else Res.drawable._2_0)

                _drawableResIconEyeHide.emit(if (stateValues.appThemeId == 1L) Res.drawable._3_1 else Res.drawable._3_0)

                _drawableResIconEyeShow.emit(if (stateValues.appThemeId == 1L) Res.drawable._4_1 else Res.drawable._4_0)

                _drawableResIconEmail.emit(if (stateValues.appThemeId == 1L) Res.drawable._5_1 else Res.drawable._5_0)

                _drawableResIconPhone.emit(if (stateValues.appThemeId == 1L) Res.drawable._6_1 else Res.drawable._6_0)

                _drawableResIconExpandMore.emit(if (stateValues.appThemeId == 1L) Res.drawable._7_1 else Res.drawable._7_0)

                _drawableResIconExpandLess.emit(if (stateValues.appThemeId == 1L) Res.drawable._8_1 else Res.drawable._8_0)

                _drawableResIconPerson.emit(if (stateValues.appThemeId == 1L) Res.drawable._9_1 else Res.drawable._9_0)

                _drawableResIconTransactionSale.emit(if (stateValues.appThemeId == 1L) Res.drawable._10_1 else Res.drawable._10_0)

                _drawableResIconTransactionReturn.emit(if (stateValues.appThemeId == 1L) Res.drawable._11_1 else Res.drawable._11_0)

                _drawableResIconTransactionSupply.emit(if (stateValues.appThemeId == 1L) Res.drawable._12_1 else Res.drawable._12_0)

                _drawableResIconTransactionSelection.emit(if (stateValues.appThemeId == 1L) Res.drawable._57_1 else Res.drawable._57_0)

                _drawableResIconStock.emit(if (stateValues.appThemeId == 1L) Res.drawable._13_1 else Res.drawable._13_0)

                _drawableResIconMenu.emit(if (stateValues.appThemeId == 1L) Res.drawable._14_1 else Res.drawable._14_0)

                _drawableResIconBackArrow.emit(if (stateValues.appThemeId == 1L) Res.drawable._15_1 else Res.drawable._15_0)

                _drawableResIconAdd.emit(if (stateValues.appThemeId == 1L) Res.drawable._16_1 else Res.drawable._16_0)

                _drawableResIconUserAccount.emit(if (stateValues.appThemeId == 1L) Res.drawable._17_1 else Res.drawable._17_0)

                _drawableResIconGoodsCategories.emit(if (stateValues.appThemeId == 1L) Res.drawable._18_1 else Res.drawable._18_0)

                _drawableResIconStores.emit(if (stateValues.appThemeId == 1L) Res.drawable._19_1 else Res.drawable._19_0)

                _drawableResIconTransactionHistory.emit(if (stateValues.appThemeId == 1L) Res.drawable._20_1 else Res.drawable._20_0)

                _drawableResIconLog.emit(if (stateValues.appThemeId == 1L) Res.drawable._49_1 else Res.drawable._49_0)

                _drawableResIconPromos.emit(if (stateValues.appThemeId == 1L) Res.drawable._50_1 else Res.drawable._50_0)

                _drawableResIconAnalytics.emit(if (stateValues.appThemeId == 1L) Res.drawable._21_1 else Res.drawable._21_0)
                _drawableResIconAnalyticsReport.emit(if (stateValues.appThemeId == 1L) Res.drawable._62_1 else Res.drawable._62_0)
                _drawableResIconLabelPrinter.emit(if (stateValues.appThemeId == 1L) Res.drawable._63_1 else Res.drawable._63_0)
                _drawableResIconBarcodeGenerate.emit(if (stateValues.appThemeId == 1L) Res.drawable._64_1 else Res.drawable._64_0)
                _drawableResIconPrintTag.emit(if (stateValues.appThemeId == 1L) Res.drawable._65_1 else Res.drawable._65_0)
                _drawableResIconWorkerRoleTemplates.emit(if (stateValues.appThemeId == 1L) Res.drawable._66_1 else Res.drawable._66_0)
                _drawableResIconStockHistory.emit(if (stateValues.appThemeId == 1L) Res.drawable._67_1 else Res.drawable._67_0)
                _drawableResIconAppModeStore.emit(if (stateValues.appThemeId == 1L) Res.drawable._68_1 else Res.drawable._68_0)
                _drawableResIconAppModeBuyer.emit(if (stateValues.appThemeId == 1L) Res.drawable._69_1 else Res.drawable._69_0)
                _drawableResIconAppModeSupplier.emit(if (stateValues.appThemeId == 1L) Res.drawable._70_1 else Res.drawable._70_0)
                _drawableResIconAppModeManufacturer.emit(if (stateValues.appThemeId == 1L) Res.drawable._71_1 else Res.drawable._71_0)
                _drawableResIconSupplierCatalog.emit(if (stateValues.appThemeId == 1L) Res.drawable._72_1 else Res.drawable._72_0)
                _drawableResIconSupplierContracts.emit(if (stateValues.appThemeId == 1L) Res.drawable._76_1 else Res.drawable._76_0)
                _drawableResIconSupplierPartners.emit(if (stateValues.appThemeId == 1L) Res.drawable._75_1 else Res.drawable._75_0)
                _drawableResIconSupplierDemandRadar.emit(if (stateValues.appThemeId == 1L) Res.drawable._77_1 else Res.drawable._77_0)
                _drawableResIconSupplierBackorderRecovery.emit(if (stateValues.appThemeId == 1L) Res.drawable._91_1 else Res.drawable._91_0)
                _drawableResIconSupplierRecoveryOwner.emit(if (stateValues.appThemeId == 1L) Res.drawable._92_1 else Res.drawable._92_0)
                _drawableResIconSupplierRecoveryClock.emit(if (stateValues.appThemeId == 1L) Res.drawable._93_1 else Res.drawable._93_0)
                _drawableResIconSupplierRecoveryProof.emit(if (stateValues.appThemeId == 1L) Res.drawable._94_1 else Res.drawable._94_0)
                _drawableResIconSupplierRecoveryResolution.emit(if (stateValues.appThemeId == 1L) Res.drawable._95_1 else Res.drawable._95_0)
                _drawableResIconSupplierRecoveryContact.emit(if (stateValues.appThemeId == 1L) Res.drawable._96_1 else Res.drawable._96_0)
                _drawableResIconSupplierRecoveryRisk.emit(if (stateValues.appThemeId == 1L) Res.drawable._97_1 else Res.drawable._97_0)
                _drawableResIconSupplierRecoveryConfidence.emit(if (stateValues.appThemeId == 1L) Res.drawable._98_1 else Res.drawable._98_0)
                _drawableResIconSupplierRecoveryFollowUp.emit(if (stateValues.appThemeId == 1L) Res.drawable._99_1 else Res.drawable._99_0)
                _drawableResIconSupplierRecoveryHandoff.emit(if (stateValues.appThemeId == 1L) Res.drawable._100_1 else Res.drawable._100_0)
                _drawableResIconSupplierRecoveryClosure.emit(if (stateValues.appThemeId == 1L) Res.drawable._101_1 else Res.drawable._101_0)
                _drawableResIconSupplierRecoveryLedger.emit(if (stateValues.appThemeId == 1L) Res.drawable._102_1 else Res.drawable._102_0)
                _drawableResIconSupplierRecoveryTriage.emit(if (stateValues.appThemeId == 1L) Res.drawable._103_1 else Res.drawable._103_0)
                _drawableResIconSupplierRecoveryCommand.emit(if (stateValues.appThemeId == 1L) Res.drawable._104_1 else Res.drawable._104_0)
                _drawableResIconSupplierRecoveryPromiseShield.emit(if (stateValues.appThemeId == 1L) Res.drawable._105_1 else Res.drawable._105_0)
                _drawableResIconSupplierRecoveryDesk.emit(if (stateValues.appThemeId == 1L) Res.drawable._106_1 else Res.drawable._106_0)
                _drawableResIconSupplierRecoveryWave.emit(if (stateValues.appThemeId == 1L) Res.drawable._107_1 else Res.drawable._107_0)
                _drawableResIconSupplierRecoveryAging.emit(if (stateValues.appThemeId == 1L) Res.drawable._108_1 else Res.drawable._108_0)
                _drawableResIconSupplierRecoveryBottleneck.emit(if (stateValues.appThemeId == 1L) Res.drawable._109_1 else Res.drawable._109_0)
                _drawableResIconSupplierRecoveryLoad.emit(if (stateValues.appThemeId == 1L) Res.drawable._110_1 else Res.drawable._110_0)
                _drawableResIconSupplierRecoveryImpact.emit(if (stateValues.appThemeId == 1L) Res.drawable._111_1 else Res.drawable._111_0)
                _drawableResIconSupplierRecoveryCommit.emit(if (stateValues.appThemeId == 1L) Res.drawable._112_1 else Res.drawable._112_0)
                _drawableResIconSupplierRecoveryAllocation.emit(if (stateValues.appThemeId == 1L) Res.drawable._113_1 else Res.drawable._113_0)
                _drawableResIconSupplierRecoveryException.emit(if (stateValues.appThemeId == 1L) Res.drawable._114_1 else Res.drawable._114_0)
                _drawableResIconSupplierRecoveryCause.emit(if (stateValues.appThemeId == 1L) Res.drawable._115_1 else Res.drawable._115_0)
                _drawableResIconSupplierRecoveryVerification.emit(if (stateValues.appThemeId == 1L) Res.drawable._116_1 else Res.drawable._116_0)
                _drawableResIconSupplierRecoveryApproval.emit(if (stateValues.appThemeId == 1L) Res.drawable._117_1 else Res.drawable._117_0)
                _drawableResIconSupplierRecoveryExecution.emit(if (stateValues.appThemeId == 1L) Res.drawable._118_1 else Res.drawable._118_0)
                _drawableResIconSupplierRecoveryRelease.emit(if (stateValues.appThemeId == 1L) Res.drawable._119_1 else Res.drawable._119_0)
                _drawableResIconSupplierRecoverySeal.emit(if (stateValues.appThemeId == 1L) Res.drawable._120_1 else Res.drawable._120_0)
                _drawableResIconSupplierRecoveryCloseout.emit(if (stateValues.appThemeId == 1L) Res.drawable._121_1 else Res.drawable._121_0)
                _drawableResIconSupplierRecoveryReopen.emit(if (stateValues.appThemeId == 1L) Res.drawable._122_1 else Res.drawable._122_0)
                _drawableResIconSupplierRecoveryReconciliation.emit(if (stateValues.appThemeId == 1L) Res.drawable._123_1 else Res.drawable._123_0)
                _drawableResIconSupplierRecoveryAudit.emit(if (stateValues.appThemeId == 1L) Res.drawable._124_1 else Res.drawable._124_0)
                _drawableResIconSupplierDispatch.emit(if (stateValues.appThemeId == 1L) Res.drawable._78_1 else Res.drawable._78_0)
                _drawableResIconSupplierTermsGuard.emit(if (stateValues.appThemeId == 1L) Res.drawable._89_1 else Res.drawable._89_0)
                _drawableResIconBuyerAgeRestriction.emit(if (stateValues.appThemeId == 1L) Res.drawable._73_1 else Res.drawable._73_0)
                _drawableResIconTransactionTimeRestriction.emit(if (stateValues.appThemeId == 1L) Res.drawable._74_1 else Res.drawable._74_0)

                _drawableResIconWorkers.emit(if (stateValues.appThemeId == 1L) Res.drawable._22_1 else Res.drawable._22_0)

                _drawableResIconSuppliers.emit(if (stateValues.appThemeId == 1L) Res.drawable._23_1 else Res.drawable._23_0)

                _drawableResIconDebtors.emit(if (stateValues.appThemeId == 1L) Res.drawable._24_1 else Res.drawable._24_0)

                _drawableResIconDevices.emit(if (stateValues.appThemeId == 1L) Res.drawable._25_1 else Res.drawable._25_0)

                _drawableResIconAppLanguage.emit(if (stateValues.appThemeId == 1L) Res.drawable._26_1 else Res.drawable._26_0)

                _drawableResIconAppTheme.emit(if (stateValues.appThemeId == 1L) Res.drawable._27_1 else Res.drawable._27_0)

                _drawableResIconAppScale.emit(if (stateValues.appThemeId == 1L) Res.drawable._48_1 else Res.drawable._48_0)

                _drawableResIconCheck.emit(if (stateValues.appThemeId == 1L) Res.drawable._28_1 else Res.drawable._28_0)

                _drawableResIconEdit.emit(if (stateValues.appThemeId == 1L) Res.drawable._29_1 else Res.drawable._29_0)

                _drawableResIconSettings.emit(if (stateValues.appThemeId == 1L) Res.drawable._30_1 else Res.drawable._30_0)

                _drawableResIconSearch.emit(if (stateValues.appThemeId == 1L) Res.drawable._31_1 else Res.drawable._31_0)

                _drawableResIconBarcodeCamScanner.emit(if (stateValues.appThemeId == 1L) Res.drawable._32_1 else Res.drawable._32_0)

                _drawableResIconBarcodeScanner.emit(if (stateValues.appThemeId == 1L) Res.drawable._51_1 else Res.drawable._51_0)

                _drawableResIconBarcodeType.emit(if (stateValues.appThemeId == 1L) Res.drawable._55_1 else Res.drawable._55_0)

                _drawableResIconVoiceInput.emit(if (stateValues.appThemeId == 1L) Res.drawable._52_1 else Res.drawable._52_0)

                _drawableResIconDelete.emit(if (stateValues.appThemeId == 1L) Res.drawable._33_1 else Res.drawable._33_0)

                _drawableResIconExit.emit(if (stateValues.appThemeId == 1L) Res.drawable._34_1 else Res.drawable._34_0)

                _drawableResIconSwitch.emit(if (stateValues.appThemeId == 1L) Res.drawable._35_1 else Res.drawable._35_0)
                _drawableResIconSort.emit(if (stateValues.appThemeId == 1L) Res.drawable._61_1 else Res.drawable._61_0)

                _drawableResIconCart.emit(if (stateValues.appThemeId == 1L) Res.drawable._36_1 else Res.drawable._36_0)

                _drawableResIconAddCart.emit(if (stateValues.appThemeId == 1L) Res.drawable._37_1 else Res.drawable._37_0)

                _drawableResIconSubtract.emit(if (stateValues.appThemeId == 1L) Res.drawable._38_1 else Res.drawable._38_0)

                _drawableResIconReceipt.emit(if (stateValues.appThemeId == 1L) Res.drawable._39_1 else Res.drawable._39_0)
                _drawableResIconFinances.emit(if (stateValues.appThemeId == 1L) Res.drawable._40_1 else Res.drawable._40_0)
                _drawableResIconClipboard.emit(if (stateValues.appThemeId == 1L) Res.drawable._41_1 else Res.drawable._41_0)
                _drawableResIconSupport.emit(if (stateValues.appThemeId == 1L) Res.drawable._42_1 else Res.drawable._42_0)
                _drawableResIconSubscription.emit(if (stateValues.appThemeId == 1L) Res.drawable._43_1 else Res.drawable._43_0)
                _drawableResIconThemeLight.emit(if (stateValues.appThemeId == 1L) Res.drawable._44_1 else Res.drawable._44_0)
                _drawableResIconThemeDark.emit(if (stateValues.appThemeId == 1L) Res.drawable._45_1 else Res.drawable._45_0)
                _drawableResIconShare.emit(if (stateValues.appThemeId == 1L) Res.drawable._46_1 else Res.drawable._46_0)
                _drawableResIconWhatsApp.emit(if (stateValues.appThemeId == 1L) Res.drawable._47_1 else Res.drawable._47_0)
                _drawableResIconRefresh.emit(if (stateValues.appThemeId == 1L) Res.drawable._54_1 else Res.drawable._54_0)
            }
        }

        softKeyboardController = LocalSoftwareKeyboardController.current
        coroutineScope = rememberCoroutineScope()

        key(stateValues.appLanguage, stateValues.appThemeId, stateValues.appSizeModeId, *keys) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val currentAutoFocusNavigationKey = listOf(
                    stateValues.navigationScreensMain.routesAutoFocusKey(),
                    stateValues.navigationScreensMenuLeft.routesAutoFocusKey(),
                    stateValues.navigationScreensMenuRight.routesAutoFocusKey(),
                    stateValues.navigationScreensStockLeft.routesAutoFocusKey(),
                    stateValues.navigationScreensStockRight.routesAutoFocusKey(),
                    stateValues.navigationScreensUserAuthLeft.routesAutoFocusKey(),
                    stateValues.navigationScreensUserAuthRight.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSaleLeftClient1.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSaleRightClient1.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSaleLeftClient2.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSaleRightClient2.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSaleLeftClient3.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSaleRightClient3.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSaleLeftClient4.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSaleRightClient4.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSaleLeftClient5.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSaleRightClient5.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionReturnLeftClient1.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionReturnRightClient1.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionReturnLeftClient2.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionReturnRightClient2.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionReturnLeftClient3.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionReturnRightClient3.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionReturnLeftClient4.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionReturnRightClient4.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionReturnLeftClient5.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionReturnRightClient5.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSupplyLeftClient1.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSupplyRightClient1.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSupplyLeftClient2.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSupplyRightClient2.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSupplyLeftClient3.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSupplyRightClient3.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSupplyLeftClient4.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSupplyRightClient4.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSupplyLeftClient5.routesAutoFocusKey(),
                    stateValues.navigationScreensTransactionSupplyRightClient5.routesAutoFocusKey()
                ).joinToString("|")

                if (autoFocusNavigationKey != currentAutoFocusNavigationKey) {
                    autoFocusNavigationKey = currentAutoFocusNavigationKey
                    autoFocusedTextFieldKeysByScope.clear()
                }

                content()

                LaunchedEffect(maxWidth, maxHeight, stateValues.wideScreenMinWidth) {
                    _screenWidthState.emit(maxWidth)
                    _screenHeightState.emit(maxHeight)
                    _isNarrowScreenState.emit(maxWidth.value < stateValues.wideScreenMinWidth)
                }

                LaunchedEffect(Unit) {
                    val resourceStrings = loadResourceStrings()
                    val resourceDimensions = loadResourceDimensions()
                    val resourceColors = loadResourceColors()
                    val resourceDrawables = loadResourceDrawablePaths()

                    updateStrings(
                        strings = stringsState.payloadValue ?: resourceStrings,
                        resourceStrings = resourceStrings
                    )

                    updateDimensions(
                        dimensions = dimensionsState.payloadValue ?: resourceDimensions,
                        resourceDimensions = resourceDimensions
                    )

                    updateColors(
                        colors = colorsState.payloadValue ?: resourceColors,
                        resourceColors = resourceColors
                    )

                    updateDrawables(
                        drawables = drawablesState.payloadValue ?: resourceDrawables,
                        resourceDrawables = resourceDrawables
                    )

                    stateValues.updateDrawableResources()
                    Navigation.startAppNavigationPersistence()
                    Navigation.startTransactionNavigationPersistence()

                    launch {
                        _isNarrowScreenState.collect { isNarrow ->
                            Navigation.awaitAppNavigationRestore()
                            Navigation.awaitTransactionNavigationRestore()
                            Navigation.TransactionSale.init(isNarrow)
                            Navigation.TransactionReturn.init(isNarrow)
                            Navigation.TransactionSupply.init(isNarrow)
                            Navigation.Stock.init(isNarrow)
                            Navigation.Menu.init(isNarrow)
                            Navigation.UserAuth.init(isNarrow)
                        }
                    }

                    launch {
                        stringsState.payload.collect { strings ->
                            strings?.let { updateStrings(it, resourceStrings) }
                        }
                    }

                    launch {
                        appLanguageState.collect {
                            updateStrings(
                                strings = stringsState.payloadValue ?: resourceStrings,
                                resourceStrings = resourceStrings
                            )
                        }
                    }

                    launch {
                        dimensionsState.payload.collect { dimensions ->
                            dimensions?.let { updateDimensions(it, resourceDimensions) }
                        }
                    }

                    launch {
                        colorsState.payload.collect { colors ->
                            colors?.let { updateColors(it, resourceColors) }
                        }
                    }

                    launch {
                        drawablesState.payload.collect { drawables ->
                            drawables?.let {
                                updateDrawables(it, resourceDrawables)
                                stateValues.updateDrawableResources()
                            }
                        }
                    }

                    launch {
                        appThemeIdState.collect {
                            updateColors(
                                colors = colorsState.payloadValue ?: resourceColors,
                                resourceColors = resourceColors
                            )

                            updateDrawables(
                                drawables = drawablesState.payloadValue ?: resourceDrawables,
                                resourceDrawables = resourceDrawables
                            )

                            stateValues.updateDrawableResources()
                        }
                    }

                    launch {
                        appSizeModeIdState.collect {
                            updateDimensions(
                                dimensions = dimensionsState.payloadValue ?: resourceDimensions,
                                resourceDimensions = resourceDimensions
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun updateDimensions(
        dimensions: List<StylizedDimensionGroupDataModel>,
        resourceDimensions: List<StylizedDimensionGroupDataModel>
    ) {
        val sizeModeId = normalizeAppSizeModePreference(stateValues.appSizeModeId)

        fun dimensionValue(id: Long, default: Float): Float {
            return dimensions.extractValue(id, sizeModeId)
                ?: resourceDimensions.extractValue(id, sizeModeId)
                ?: dimensions.extractValue(id, DEFAULT_APP_SIZE_MODE_ID)
                ?: resourceDimensions.extractValue(id, DEFAULT_APP_SIZE_MODE_ID)
                ?: default
        }

        _wideScreenMinWidthState.emit(dimensionValue(4L, 600f))
        _boundWidgetWidthState.emit(dimensionValue(9L, if (sizeModeId == 1L) 340f else 300f).dp)

        _textSizeState.emit(dimensionValue(0L, if (sizeModeId == 1L) 16f else 14f).sp)
        _titleTextSizeState.emit(dimensionValue(1L, if (sizeModeId == 1L) 23f else 20f).sp)
        _accentTextSizeState.emit(dimensionValue(2L, if (sizeModeId == 1L) 18f else 16f).sp)
        _smallTextSizeState.emit(dimensionValue(3L, if (sizeModeId == 1L) 14f else 12f).sp)

        _focusedBorderWidthState.emit(dimensionValue(5L, 1f).dp)
        _unfocusedBorderWidthState.emit(dimensionValue(6L, 0.5f).dp)

        _cornerRadiusState.emit(dimensionValue(7L, if (sizeModeId == 1L) 16f else 14f).dp)
        _iconSizeState.emit(dimensionValue(8L, if (sizeModeId == 1L) 28f else 24f).dp)
        _textFieldHeightMultiplierRelativeToTextSizeState.emit(dimensionValue(10L, if (sizeModeId == 1L) 2.75f else 2.6f))
        _textFieldHeightState.emit((_textSizeState.value.value * _textFieldHeightMultiplierRelativeToTextSizeState.value).dp)
        _wideTextFieldHeightState.emit((_textSizeState.value.value * 4f * _textFieldHeightMultiplierRelativeToTextSizeState.value).dp)
        _textFieldIconPaddingState.emit(dimensionValue(11L, if (sizeModeId == 1L) 10f else 9f).dp)
        _marginTextFieldState.emit(dimensionValue(12L, if (sizeModeId == 1L) 10f else 8f).dp)
        _marginTextFieldGroupState.emit(dimensionValue(13L, if (sizeModeId == 1L) 28f else 24f).dp)
    }


    private suspend fun updateColors(
        colors: List<StylizedColorGroupDataModel>,
        resourceColors: List<StylizedColorGroupDataModel>
    ) {
        val themeId = appThemeIdState.value

        fun colorHex(id: Long, fallback: String): String {
            return colors.extractColor(id, themeId)
                ?: resourceColors.extractColor(id, themeId)
                ?: colors.extractColor(id, 0L)
                ?: resourceColors.extractColor(id, 0L)
                ?: fallback
        }

        _AccentColorState.emit(colorHex(0L, "#ffffba24").toColor())
        _BackgroundColorState.emit(colorHex(1L, if (themeId == 1L) "#ff111111" else "#ffffffff").toColor())
        _TextColorState.emit(colorHex(2L, if (themeId == 1L) "#ffffffff" else "#ff000000").toColor())
        _AccentTextColorState.emit(colorHex(3L, "#ffffffff").toColor())
        _PlaceholderTextColorState.emit(colorHex(4L, if (themeId == 1L) "#aaffffff" else "#aa000000").toColor())
        _DisabledColorState.emit(colorHex(5L, "#ffa7a7a7").toColor())
        _ErrorColorState.emit(colorHex(6L, "#ffff0000").toColor())
        _IconTintColorState.emit(colorHex(7L, if (themeId == 1L) "#ffffffff" else "#ff000000").toColor())
        _OkayColorState.emit(colorHex(8L, "#ff6bb522").toColor())
        _BorderlineBadColorState.emit(colorHex(9L, "#ffffa500").toColor())
    }
}

internal fun List<CountryDataModel>.withTajikistanFallback(): List<CountryDataModel> {
    if (any { it.locale.equals("tj", true) || it.phoneNumberCode == "992" })
        return this

    val tajikistan = CountryDataModel(
        locale = "tj",
        language = "tj",
        name = listOf(
            LocalizedStringDataModel("main", "Tajikistan"),
            LocalizedStringDataModel("en", "Tajikistan"),
            LocalizedStringDataModel("ru", "Таджикистан"),
            LocalizedStringDataModel("kk", "Тәжікстан")
        ),
        flagDrawablePath = "png/flag_tj.png",
        cities = listOf(
            CityDataModel(
                name = listOf(
                    LocalizedStringDataModel("main", "Dushanbe"),
                    LocalizedStringDataModel("en", "Dushanbe"),
                    LocalizedStringDataModel("ru", "Душанбе"),
                    LocalizedStringDataModel("kk", "Душанбе")
                ),
                centerLatitude = 38.5606,
                centerLongitude = 68.7778,
                swLatitude = 38.4950,
                swLongitude = 68.6800,
                neLatitude = 38.6100,
                neLongitude = 68.8800
            ),
            CityDataModel(
                name = listOf(
                    LocalizedStringDataModel("main", "Khujand"),
                    LocalizedStringDataModel("en", "Khujand"),
                    LocalizedStringDataModel("ru", "Худжанд"),
                    LocalizedStringDataModel("kk", "Худжанд")
                ),
                centerLatitude = 40.2894,
                centerLongitude = 69.6270,
                swLatitude = 40.2560,
                swLongitude = 69.5900,
                neLatitude = 40.3050,
                neLongitude = 69.7300
            )
        ),
        phoneNumberCode = "992",
        phoneNumberSize = 9,
        currencies = listOf(
            CurrencyDataModel(
                code = "TJS",
                symbol = "SM",
                name = listOf(
                    LocalizedStringDataModel("main", "Somoni"),
                    LocalizedStringDataModel("en", "Somoni"),
                    LocalizedStringDataModel("ru", "Сомони"),
                    LocalizedStringDataModel("kk", "Сомони")
                )
            )
        ),
        cashlessPaymentOptions = listOf(
            PaymentOptionDataModel(
                id = "0",
                name = listOf(
                    LocalizedStringDataModel("main", "Card"),
                    LocalizedStringDataModel("en", "Card"),
                    LocalizedStringDataModel("ru", "Карта"),
                    LocalizedStringDataModel("kk", "Карта")
                )
            )
        ),
        preferredCashlessPaymentOptionId = "0"
    )

    return this + tajikistan
}

@Composable
fun AppConfiguration.countrySelectionPhoneNumberTextField(
    countries: List<CountryDataModel> = stateValues.globalAppConfiguration.countries.withTajikistanFallback(),
    valueInitial: String? = null,
    stateHost: StateHost,
    stateKey: String,
    lockedId: String? = null,
    titleText: String = stateValues.stringPhoneNumber,
    placeholderText: String = stateValues.stringEnterPhoneNumber,
    imeWithAction: ImeWithAction? = null
): DomainSelectionTextFieldContent {
    val phoneCountries = countries.withTajikistanFallback()
    val detectedCountry = valueInitial?.removePrefix("+")?.let { normalized ->
        phoneCountries
            .sortedByDescending { it.phoneNumberCode.length }
            .find { normalized.startsWith(it.phoneNumberCode) }
    }
    val defaultCountry = detectedCountry
        ?: phoneCountries.find { it.locale.equals("kz", true) }
        ?: phoneCountries.find { it.phoneNumberCode == "7" }
        ?: phoneCountries.firstOrNull()
    val selectedSecondaryInitial = lockedId?.let { if (it.startsWith("+")) it else "+$it" }
        ?: defaultCountry?.let { "+${it.phoneNumberCode}" }

    return domainSelectionTextField(
        domains = emptyList(),
        secondaryDomains = phoneCountries
            .sortedWith(
                compareBy<CountryDataModel> { it.locale != defaultCountry?.locale }
                    .thenBy { it.name.visibleLocalizedString(stateValues.appLanguage, it.locale) }
            )
            .map {
                SelectableDomain(
                    id = "+${it.phoneNumberCode}",
                    displayId = "+${it.phoneNumberCode}".toLocalizedSingleMain(),
                    name = it.name,
                    iconPath = it.flagDrawablePath,
                    iconRes = it.mapIconRes()
                )
            },
        selectedSecondaryInitial = selectedSecondaryInitial,
        lockedSecondaryDomainId = lockedId?.let { if (it.startsWith("+")) it else "+$it" },
        valueInitial = valueInitial?.run {
            phoneCountries
                .sortedByDescending { it.phoneNumberCode.length }
                .find { startsWith(it.phoneNumberCode) || startsWith("+${it.phoneNumberCode}") }
                ?.takeIf { countryMatch ->
                    removePrefix("+").length > countryMatch.phoneNumberCode.length
                }?.let { countryMatch ->
                    removePrefix("+").substringAfter(countryMatch.phoneNumberCode)
                } ?: this
        },
        titleText = titleText,
        stateHost = stateHost,
        stateKey = stateKey,
        placeholderText = placeholderText,
        keyboardType = KeyboardType.Phone,
        imeWithAction = imeWithAction,
        contentInvalidText = stateValues.stringPhoneNumberMustBe,
        onContentValidityCheck = { text, _, selectedSecondaryId ->
            countries.withTajikistanFallback().find { "+${it.phoneNumberCode}" == selectedSecondaryId }?.run {
                text.checkAsPhoneNumber(this)
            } == true
        },
        onFilterValue = { text, _, selectedSecondaryId ->
            countries.withTajikistanFallback().find { "+${it.phoneNumberCode}" == selectedSecondaryId }?.run {
                text.filterAsPhoneNumber(this)
            } == true
        }
    )
}

@Composable
fun AppConfiguration.AppSizeModeSettingsItemWidget(
    id: Long,
    name: String,
    description: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .heightIn(min = stateValues.textFieldHeight * 1.15f)
            .fillMaxWidth()
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.TextColor),
                onClick = {
                    markExplicitLocalAppPreferences(sizeModeId = id)
                    setAppSizeMode(id)
                }
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CpImage(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(stateValues.iconSize),
                url = stateValues.drawablePathIconAppScale,
                fallbackRes = stateValues.drawableResIconAppScale.value,
                contentDescription = name,
                tintColor = if (isActive) stateValues.AccentColor else stateValues.TextColor
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = stateValues.textSize,
                    color = if (isActive) stateValues.AccentColor else stateValues.TextColor,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = description,
                    fontSize = stateValues.smallTextSize,
                    color = stateValues.PlaceholderTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isActive) {
            CpImage(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(stateValues.iconSize),
                url = stateValues.drawablePathIconCheck,
                fallbackRes = stateValues.drawableResIconCheck.value,
                contentDescription = name,
                tintColor = stateValues.AccentColor
            )
        }
    }
}

@Composable
fun AppConfiguration.AppThemeSettingsItemWidget(
    id: Long,
    name: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .height(42.dp)
            .fillMaxWidth()
            .aitaClickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = ripple(color = stateValues.TextColor),
                onClick = {
                    markExplicitLocalAppPreferences(themeId = id)
                    coroutineScope.launch {
                        setAppTheme(id)
                    }
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val themeIconPath = if (id == 1L) stateValues.drawablePathIconThemeDark else stateValues.drawablePathIconThemeLight
        val themeIconRes = if (id == 1L) stateValues.drawableResIconThemeDark.value else stateValues.drawableResIconThemeLight.value

        Row(verticalAlignment = Alignment.CenterVertically) {
            CpImage(
                modifier = Modifier
                    .padding(start = 12.dp, end = 4.dp)
                    .size(22.dp),
                url = themeIconPath,
                fallbackRes = themeIconRes,
                contentDescription = name,
                tintColor = if (isActive) stateValues.AccentColor else stateValues.TextColor
            )

            Text(
                text = name,
                modifier = Modifier
                    .padding(12.dp),
                fontSize = stateValues.textSize,
                color = if (isActive)
                    stateValues.AccentColor
                else
                    stateValues.TextColor,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }

        if (isActive) {
            Row {
                val drawableResIconCheck by stateValues.drawableResIconCheck.collectAsState()

                CpImage(
                    modifier = Modifier
                        .padding(stateValues.textFieldIconPadding)
                        .fillMaxHeight()
                        .aspectRatio(1f, matchHeightConstraintsFirst = true),
                    url = stateValues.drawablePathIconCheck,
                    fallbackRes = drawableResIconCheck,
                    contentDescription = name,
                    tintColor = stateValues.AccentColor
                )

                Spacer(modifier = Modifier.width(12.dp))
            }
        }

    }
}

@Composable
fun AppConfiguration.AppLanguageSettingsItemWidget(
    language: String,
    name: String,
    flagDrawablePath: String,
    flagDrawableRes: DrawableResource,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .height(42.dp)
            .fillMaxWidth()
            .aitaClickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = ripple(color = stateValues.TextColor),
                onClick = {
                    markExplicitLocalAppPreferences(language = language)
                    coroutineScope.launch {
                        setAppLocale(language)
                    }
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row {
            CpImage(
                modifier = Modifier
                    .padding(stateValues.textFieldIconPadding)
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                url = flagDrawablePath,
                fallbackRes = flagDrawableRes,
                contentDescription = name
            )

            Text(
                text = name,
                modifier = Modifier
                    .padding(12.dp),
                fontSize = stateValues.textSize,
                color = if (isActive)
                    stateValues.AccentColor
                else
                    stateValues.TextColor,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }

        if (isActive) {
            Row {
                val iconRes by stateValues.drawableResIconCheck.collectAsState()

                CpImage(
                    modifier = Modifier
                        .padding(stateValues.textFieldIconPadding)
                        .fillMaxHeight()
                        .aspectRatio(1f, matchHeightConstraintsFirst = true),
                    url = stateValues.drawablePathIconCheck,
                    fallbackRes = iconRes,
                    contentDescription = name,
                    tintColor = stateValues.AccentColor
                )

                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

@Composable
fun AppConfiguration.actionButton(
    modifier: Modifier = Modifier,
    fillMaxHeight: Boolean = false,
    fillMaxWidthIfTextPresent: Boolean = true,

    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String? = null,
    autoLoading: Boolean = true,

    enabledColor: Color = stateValues.AccentColor,
    disabledColor: Color = stateValues.DisabledColor,

    text: String,
    textColor: Color = stateValues.AccentTextColor,
    textSize: TextUnit = stateValues.textSize,

    subText: String = "",
    subTextColor: Color = textColor,
    subTextSize: TextUnit = stateValues.smallTextSize,

    cornerRadius: Dp = stateValues.cornerRadius,

    icon: @Composable (() -> Unit)? = null,
    iconAfterText: Boolean = false,

    iconPath: String? = null,
    iconRes: DrawableResource? = null,
    iconContentDescription: String = text,
    iconTintColor: Color? = textColor,

    confirmationRequired: Boolean? = null,

    onDisabledClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): ActionButtonContent {
    var isEnabled by rememberSaveable {
        mutableStateOf(enabled)
    }

    LaunchedEffect(enabled) {
        isEnabled = enabled
    }

    val actionButtonScope = rememberCoroutineScope()
    val activeNetworkOperations by activeNetworkOperationsState.collectAsState()
    var autoLoadingActive by remember {
        mutableStateOf(false)
    }
    var autoLoadingStartNetworkOperations by remember {
        mutableStateOf<Int?>(null)
    }
    var autoLoadingNetworkObserved by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(loading) {
        if (!loading) {
            autoLoadingActive = false
            autoLoadingStartNetworkOperations = null
            autoLoadingNetworkObserved = false
        }
    }

    LaunchedEffect(activeNetworkOperations, autoLoadingStartNetworkOperations) {
        val startedAt = autoLoadingStartNetworkOperations
        if (startedAt != null) {
            if (activeNetworkOperations > startedAt) autoLoadingNetworkObserved = true
            if (autoLoadingNetworkObserved && activeNetworkOperations <= startedAt && !loading) {
                autoLoadingActive = false
                autoLoadingStartNetworkOperations = null
                autoLoadingNetworkObserved = false
            }
        }
    }

    fun startAutoLoadingPulse() {
        if (!autoLoading || text.isBlank() || !fillMaxWidthIfTextPresent) return
        val startedAt = activeNetworkOperations
        autoLoadingActive = true
        autoLoadingStartNetworkOperations = startedAt
        autoLoadingNetworkObserved = false
        actionButtonScope.launch {
            delay(1_400)
            if (autoLoadingStartNetworkOperations == startedAt && !autoLoadingNetworkObserved && !loading) {
                autoLoadingActive = false
                autoLoadingStartNetworkOperations = null
            }
        }
    }

    var confirmationDialogShown by rememberSaveable {
        mutableStateOf(false)
    }

    val effectiveLoading = loading || autoLoadingActive
    val originalTextPresent = text.isNotEmpty() && text.isNotBlank()
    val genericLoadingText = localizedStringResource(1141, "Please wait…")
    val displayedText = if (effectiveLoading && originalTextPresent) loadingText ?: genericLoadingText else if (effectiveLoading) "" else text
    val visuallyEnabled = isEnabled && !effectiveLoading

    val backgroundColor by animateColorAsState(
        targetValue = if (visuallyEnabled) enabledColor else disabledColor,
        animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS),
        label = "actionButtonBackground"
    )

    val textPresent = displayedText.isNotEmpty() && displayedText.isNotBlank()
    val subTextPresent = subText.isNotEmpty() && subText.isNotBlank() && !effectiveLoading

    val textHeight = if (!textPresent) 0f else textSize.value
    val subTextHeight = if (subTextPresent) subTextSize.value else 0f
    val height = if (!textPresent) stateValues.textFieldHeight else (textHeight + subTextHeight + 24).dp
    val animatedButtonHeight by animateDpAsState(
        targetValue = height,
        animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS),
        label = "actionButtonHeight"
    )

    val inferredIconPath = iconPath ?: when (text) {
        stateValues.stringAdd -> stateValues.drawablePathIconAdd
        stateValues.stringCancel -> stateValues.drawablePathIconCancel
        stateValues.stringDelete -> stateValues.drawablePathIconDelete
        stateValues.stringEdit -> stateValues.drawablePathIconEdit
        stateValues.stringBack -> stateValues.drawablePathIconBackArrow
        stateValues.stringLogIn -> stateValues.drawablePathIconUserAccount
        stateValues.stringSignUp -> stateValues.drawablePathIconPerson
        stateValues.stringSelect -> stateValues.drawablePathIconCheck
        stateValues.stringSelectInMenu -> stateValues.drawablePathIconMenu
        stateValues.stringComplete -> stateValues.drawablePathIconCheck
        stateValues.stringPdf -> stateValues.drawablePathIconReceipt
        stateValues.stringShare -> stateValues.drawablePathIconShare
        stateValues.stringWhatsApp -> stateValues.drawablePathIconWhatsApp
        stateValues.stringPrint -> stateValues.drawablePathIconDevices
        stateValues.stringQuit -> stateValues.drawablePathIconExit
        else -> null
    }

    val actionNeedsConfirmation = confirmationRequired ?: (
            text == stateValues.stringDelete ||
                    iconPath == stateValues.drawablePathIconDelete ||
                    inferredIconPath == stateValues.drawablePathIconDelete ||
                    iconContentDescription == stateValues.stringDelete
            )

    val resolvedIconRes = iconRes
    val iconPresent = effectiveLoading || icon != null || inferredIconPath != null
    val iconSize = when {
        inferredIconPath == stateValues.drawablePathIconBackArrow -> 18.dp
        textPresent -> 20.dp
        else -> 22.dp
    }

    Row(
        modifier = modifier
            .run {
                if (!textPresent || !fillMaxWidthIfTextPresent)
                    wrapContentWidth()
                else
                    fillMaxWidth()
            }
            .run {
                if (fillMaxHeight)
                    fillMaxHeight()
                else
                    height(animatedButtonHeight)
            }
            .aitaContentMotion()
            .foregroundTactileShadow(cornerRadius = cornerRadius, elevated = false)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .run {
                if (visuallyEnabled)
                    aitaClickable(
                        onClick = {
                            if (actionNeedsConfirmation)
                                confirmationDialogShown = true
                            else {
                                startAutoLoadingPulse()
                                onClick()
                            }
                        },
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = ripple(color = textColor)
                    )
                else if (!effectiveLoading)
                    onDisabledClick?.let { disabledClick ->
                        aitaClickable(
                            onClick = disabledClick,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = stateValues.ErrorColor)
                        )
                    } ?: this
                else
                    this
            }
            .run {
                onLongClick?.let {
                    pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                it()
                            }
                        )
                    }
                } ?: this
            }
            .padding(horizontal = if (textPresent) 10.dp else 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        @Composable
        fun ActionButtonIconSlot() {
            AnimatedContent(
                targetState = effectiveLoading,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS)) +
                            scaleIn(initialScale = 0.68f, animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS)) +
                                    scaleOut(targetScale = 0.76f, animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS))
                        )
                },
                label = "actionButtonIconState"
            ) { showLoading ->
                if (showLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize),
                        color = textColor,
                        strokeWidth = 2.dp
                    )
                } else {
                    icon?.invoke() ?: inferredIconPath?.run {
                        CpImage(
                            modifier = Modifier.size(iconSize),
                            url = this,
                            fallbackRes = resolvedIconRes,
                            contentDescription = iconContentDescription,
                            tintColor = iconTintColor
                        )
                    }
                }
            }
        }

        if (iconPresent && !iconAfterText) {
            ActionButtonIconSlot()
        }

        if (iconPresent && textPresent && !iconAfterText)
            Spacer(modifier = Modifier.width(8.dp))

        if (textPresent) {
            Column(
                modifier = Modifier.run {
                    if (!fillMaxWidthIfTextPresent)
                        wrapContentWidth()
                    else
                        weight(1f)
                },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = displayedText,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS)) +
                                slideInVertically(
                                    animationSpec = tween(durationMillis = AITA_MOTION_NORMAL_MILLIS),
                                    initialOffsetY = { height -> height / 3 }
                                ))
                            .togetherWith(
                                fadeOut(animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS)) +
                                        slideOutVertically(
                                            animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS),
                                            targetOffsetY = { height -> -height / 3 }
                                        )
                            )
                    },
                    label = "actionButtonTextState"
                ) { animatedText ->
                    Text(
                        text = animatedText,
                        color = textColor,
                        fontWeight = accentTextWeight(textColor, stateValues.AccentColor, FontWeight.Bold),
                        fontSize = textSize,
                        style = TextStyle(shadow = accentTextShadow(textColor, stateValues.AccentColor)),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedVisibility(
                    visible = subTextPresent,
                    enter = aitaVisibilityEnter(),
                    exit = aitaVisibilityExit()
                ) {
                    Text(
                        text = subText,
                        color = subTextColor,
                        fontSize = subTextSize,
                        fontWeight = accentTextWeight(subTextColor, stateValues.AccentColor),
                        style = TextStyle(shadow = accentTextShadow(subTextColor, stateValues.AccentColor)),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (iconPresent && textPresent && iconAfterText)
            Spacer(modifier = Modifier.width(8.dp))

        if (iconPresent && iconAfterText) {
            ActionButtonIconSlot()
        }
    }

    if (confirmationDialogShown) {
        ModalDialogWidget(
            title = stateValues.stringConfirm,
            subTitle = if (text == stateValues.stringDelete || inferredIconPath == stateValues.drawablePathIconDelete)
                "${stateValues.stringDelete}?"
            else
                "${stateValues.stringConfirm}?",
            negativeButtonText = stateValues.stringCancel,
            positiveButtonText = if (text == stateValues.stringDelete || inferredIconPath == stateValues.drawablePathIconDelete)
                stateValues.stringDelete
            else
                stateValues.stringConfirm,
            onDismiss = { confirmationDialogShown = false },
            negativeAction = { confirmationDialogShown = false },
            positiveAction = {
                confirmationDialogShown = false
                startAutoLoadingPulse()
                onClick()
            }
        )
    }

    return ActionButtonContent(isEnabled)
}

data class ActionButtonContent(
    var enabled: Boolean
)
