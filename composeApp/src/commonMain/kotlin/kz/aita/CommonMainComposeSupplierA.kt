// THIS IS CommonMainCompose.kt split slice: SupplierA
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

// Supplier customer/partner workspace moved to SupplierModeCustomers*.kt.

@Composable
internal fun AppConfiguration.SupplierDeskSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    iconPath: String,
    iconRes: DrawableResource?
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextFieldGroup),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            CpImage(
                modifier = Modifier.size(26.dp),
                url = iconPath,
                fallbackRes = iconRes,
                contentDescription = title,
                tintColor = stateValues.AccentColor
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                color = stateValues.AccentColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierFeaturePlanCard(
    feature: SupplierFeaturePlanUiModel,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .run { if (feature.implemented) this else foregroundTactileShadow(stateValues.cornerRadius, elevated = false) }
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (feature.implemented) stateValues.AccentColor.copy(alpha = 0.10f) else stateValues.BackgroundColor)
            .border(
                if (feature.implemented) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (feature.implemented) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(if (compact) stateValues.marginTextField else stateValues.marginTextFieldGroup),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        CpImage(
            modifier = Modifier.size(if (compact) 24.dp else 34.dp),
            url = feature.iconPath,
            fallbackRes = feature.iconRes,
            contentDescription = feature.title,
            tintColor = if (feature.implemented) stateValues.AccentColor else stateValues.IconTintColor
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                color = if (feature.implemented) stateValues.AccentColor else stateValues.TextColor,
                fontSize = if (compact) stateValues.textSize else stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = feature.subtitle,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (feature.implemented) {
            Text(
                text = localizedStringResource(1357, "LIVE"),
                color = stateValues.AccentTextColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

internal data class SupplierSubstituteOptionUiModel(
    val goodsItemId: String,
    val title: String,
    val subtitle: String
)

internal data class SupplierOrderLineResponseDraft(
    val acceptedQuantityText: String,
    val offeredPriceText: String,
    val commentText: String,
    val substituteGoodsItemId: String
)

internal fun String.filterSupplierDeskPriceInput(): String {
    var dotUsed = false
    return replace(',', '.')
        .filter { char ->
            when {
                char.isDigit() -> true
                char == '.' && !dotUsed -> {
                    dotUsed = true
                    true
                }
                else -> false
            }
        }
        .take(18)
}

internal fun PriceDataModel?.supplierDeskPriceInputText(): String = this
    ?.price
    ?.trim()
    ?.replace(',', '.')
    ?.takeIf { it.isNotBlank() }
    .orEmpty()

internal fun supplierDeskMatchingPriceBook(
    order: SupplierOrderDataModel,
    line: SupplierOrderLineDataModel,
    draft: SupplierOrderLineResponseDraft?,
    supplierPriceRows: List<SupplierGoodsPriceDataModel>
): SupplierGoodsPriceDataModel? {
    val targetGoodsItemId = draft?.substituteGoodsItemId?.takeIf { it.isNotBlank() && it != line.goodsItemId }
        ?: line.substituteGoodsItemId?.takeIf { it.isNotBlank() }
        ?: line.goodsItemId

    return supplierPriceRows
        .asSequence()
        .filter { price ->
            price.isActive &&
                    price.storeId == order.storeId &&
                    price.supplierId == order.supplierId &&
                    price.goodsItemId == targetGoodsItemId
        }
        .maxByOrNull { price -> price.lastUsedAtMillis ?: price.updatedAtMillis }
}

internal fun AppConfiguration.supplierOrderLineDraftUsingPriceBook(
    line: SupplierOrderLineDataModel,
    draft: SupplierOrderLineResponseDraft,
    priceBookPrice: SupplierGoodsPriceDataModel
): SupplierOrderLineResponseDraft = draft.copy(
    acceptedQuantityText = stockQuantityInputTextFromAmount(line.requestedQuantity.total, line.requestedQuantity),
    offeredPriceText = priceBookPrice.supplyPrice.supplierDeskPriceInputText()
)

internal fun AppConfiguration.supplierOrderLineResponseDraft(line: SupplierOrderLineDataModel): SupplierOrderLineResponseDraft {
    val responseComment = line.supplierCommentLocalized.extractLocalizedString(stateValues.appLanguage)
        ?: line.supplierCommentLocalized.extractLocalizedString("main")
        ?: line.supplierComment.orEmpty()

    return SupplierOrderLineResponseDraft(
        acceptedQuantityText = line.supplierAcceptedQuantity
            ?.let { quantity -> stockQuantityInputTextFromAmount(quantity.total, line.requestedQuantity) }
            .orEmpty(),
        offeredPriceText = line.supplierOfferedSupplyPrice.supplierDeskPriceInputText(),
        commentText = responseComment,
        substituteGoodsItemId = line.substituteGoodsItemId.orEmpty()
    )
}

internal fun AppConfiguration.supplierOrderLineWithResponseDraft(
    line: SupplierOrderLineDataModel,
    supplierId: String,
    draft: SupplierOrderLineResponseDraft
): SupplierOrderLineDataModel {
    val cleanComment = draft.commentText.trim().takeIf { it.isNotBlank() }
    val cleanPriceText = draft.offeredPriceText.filterSupplierDeskPriceInput()
    val priceCurrency = (line.supplierOfferedSupplyPrice ?: line.expectedSupplyPrice)?.currency?.takeIf { it.isNotBlank() } ?: "KZT"
    val acceptedQuantity = draft.acceptedQuantityText.trim()
        .takeIf { it.isNotBlank() }
        ?.let { raw -> parseStockQuantityInputText(raw, line.requestedQuantity) }
        ?.let { parsed -> line.requestedQuantity.withStockQuantityInputTotalValue(parsed) }

    return line.copy(
        supplierAcceptedQuantity = acceptedQuantity,
        supplierOfferedSupplyPrice = cleanPriceText.toDoubleOrNull()?.let { price ->
            PriceDataModel(
                price = price.roundMoney().toStockMoneyText(),
                currency = priceCurrency,
                supplierId = supplierId
            )
        },
        supplierComment = cleanComment,
        supplierCommentLocalized = cleanComment?.let { listOf(LocalizedStringDataModel(stateValues.appLanguage, it)) }.orEmpty(),
        substituteGoodsItemId = draft.substituteGoodsItemId.trim().takeIf { it.isNotBlank() && it != line.goodsItemId }
    )
}

internal fun AppConfiguration.patchSupplierDeskOrder(
    order: SupplierOrderDataModel,
    lines: List<SupplierOrderLineDataModel>,
    status: SupplierOrderStatusDataModel,
    comment: String,
    confirmedDeliveryDateText: String,
    paymentTermsText: String,
    externalReferenceText: String,
    lineDrafts: Map<String, SupplierOrderLineResponseDraft>
) {
    val cleanComment = comment.trim().takeIf { it.isNotBlank() }
    val cleanPaymentTerms = paymentTermsText.trim().takeIf { it.isNotBlank() }
    val cleanExternalReference = externalReferenceText.trim().takeIf { it.isNotBlank() }
    val patchedLines = lines.map { line ->
        lineDrafts[line.id]
            ?.let { draft -> supplierOrderLineWithResponseDraft(line, order.supplierId, draft) }
            ?: line
    }
    val responseCurrency = patchedLines.firstNotNullOfOrNull { line ->
        (line.supplierOfferedSupplyPrice ?: line.expectedSupplyPrice)?.currency?.takeIf { it.isNotBlank() }
    } ?: order.amount?.currency ?: "KZT"
    val responseAmount = patchedLines.sumOf { line ->
        val price = line.supplierOfferedSupplyPrice?.price?.toMoneyDouble() ?: 0.0
        val quantity = line.supplierAcceptedQuantity?.total?.coerceAtLeast(0.0) ?: 0.0
        price * quantity
    }.roundMoney()

    updateSupplierOrder(
        SupplierOrderWithLinesDataModel(
            order = order.copy(
                amount = responseAmount.takeIf { it > 0.0 }?.let { PriceDataModel(it.toStockMoneyText(), responseCurrency, order.supplierId) },
                confirmedDeliveryTimeMillis = stockDateInputTextToMillis(confirmedDeliveryDateText),
                status = status,
                supplierComment = cleanComment,
                supplierCommentLocalized = cleanComment?.let { listOf(LocalizedStringDataModel(stateValues.appLanguage, it)) }.orEmpty(),
                paymentTerms = cleanPaymentTerms,
                externalReference = cleanExternalReference,
                updatedAtMillis = getCurrentTimeMillis()
            ),
            lines = patchedLines
        )
    )
}

@Composable
internal fun AppConfiguration.SupplierOrderLineResponseEditor(
    line: SupplierOrderLineDataModel,
    draft: SupplierOrderLineResponseDraft,
    editable: Boolean,
    substituteOptions: List<SupplierSubstituteOptionUiModel> = emptyList(),
    priceBookPrice: SupplierGoodsPriceDataModel? = null,
    onDraftChanged: (SupplierOrderLineResponseDraft) -> Unit
) {
    val goodsTitle = supplierDeskLineTitle(line)
    val linePrice = line.expectedSupplyPrice.supplierDeskMoneyText()
    val barcode = line.goodsItemBarcodeSnapshots.firstOrNull().orEmpty()
    val quantityAllowsFraction = line.requestedQuantity.allowsFractionalStockQuantityInput()
    val lineNotesText = line.additionalNotesLocalized.extractLocalizedString(stateValues.appLanguage)
        ?: line.additionalNotesLocalized.extractLocalizedString("main")
        ?: line.additionalNotes
    val responseQuantityText = line.supplierAcceptedQuantity?.quantityText(stateValues.appLanguage).orEmpty()
    val responsePriceText = line.supplierOfferedSupplyPrice.supplierDeskMoneyText()
    val responseCommentText = line.supplierCommentLocalized.extractLocalizedString(stateValues.appLanguage)
        ?: line.supplierCommentLocalized.extractLocalizedString("main")
        ?: line.supplierComment
    val currentSubstituteText = supplierDeskSubstituteTitle(line)
    val availableSubstituteOptions = remember(substituteOptions, line.goodsItemId) {
        substituteOptions.filter { it.goodsItemId.isNotBlank() && it.goodsItemId != line.goodsItemId }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .padding(stateValues.marginTextField),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = goodsTitle,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = listOf(
                line.requestedQuantity.quantityText(stateValues.appLanguage),
                linePrice,
                barcode.takeIf { it.isNotBlank() }?.let { "#${it}" }.orEmpty()
            ).filter { it.isNotBlank() }.joinToString(" • "),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        lineNotesText?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                text = notes,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        priceBookPrice?.let { savedPrice ->
            StockCardInfoLine(
                localizedStringResource(1677, "Saved supplier price"),
                savedPrice.supplyPrice.supplierDeskMoneyText(),
                stateValues.AccentColor
            )
        }

        if (editable) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = localizedStringResource(1605, "Supplier response"),
                color = stateValues.AccentColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold
            )

            if (stateValues.isNarrowScreen) {
                Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = draft.acceptedQuantityText,
                        placeholder = localizedStringResource(1608, "Accepted quantity"),
                        keyboardType = if (quantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                        leadingIconPath = stateValues.drawablePathIconStock,
                        onTransformValue = { raw -> sanitizeStockQuantityInput(raw, quantityAllowsFraction) },
                        onValueChange = { value ->
                            if (value.isStockQuantityInputText(quantityAllowsFraction)) {
                                onDraftChanged(draft.copy(acceptedQuantityText = value))
                            }
                        }
                    )
                    SimpleTextInput(
                        modifier = Modifier.fillMaxWidth(),
                        value = draft.offeredPriceText,
                        placeholder = localizedStringResource(1609, "Offered supply price"),
                        keyboardType = KeyboardType.Decimal,
                        leadingIconPath = stateValues.drawablePathIconFinances,
                        onTransformValue = { it.filterSupplierDeskPriceInput() },
                        onValueChange = { value -> onDraftChanged(draft.copy(offeredPriceText = value.filterSupplierDeskPriceInput())) }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    SimpleTextInput(
                        modifier = Modifier.weight(1f),
                        value = draft.acceptedQuantityText,
                        placeholder = localizedStringResource(1608, "Accepted quantity"),
                        keyboardType = if (quantityAllowsFraction) KeyboardType.Decimal else KeyboardType.Number,
                        leadingIconPath = stateValues.drawablePathIconStock,
                        onTransformValue = { raw -> sanitizeStockQuantityInput(raw, quantityAllowsFraction) },
                        onValueChange = { value ->
                            if (value.isStockQuantityInputText(quantityAllowsFraction)) {
                                onDraftChanged(draft.copy(acceptedQuantityText = value))
                            }
                        }
                    )
                    SimpleTextInput(
                        modifier = Modifier.weight(1f),
                        value = draft.offeredPriceText,
                        placeholder = localizedStringResource(1609, "Offered supply price"),
                        keyboardType = KeyboardType.Decimal,
                        leadingIconPath = stateValues.drawablePathIconFinances,
                        onTransformValue = { it.filterSupplierDeskPriceInput() },
                        onValueChange = { value -> onDraftChanged(draft.copy(offeredPriceText = value.filterSupplierDeskPriceInput())) }
                    )
                }
            }

            SimpleTextInput(
                modifier = Modifier.fillMaxWidth(),
                value = draft.commentText,
                placeholder = localizedStringResource(1610, "Line comment"),
                singleLine = false,
                leadingIconPath = stateValues.drawablePathIconResponse,
                onValueChange = { onDraftChanged(draft.copy(commentText = it)) }
            )

            if (availableSubstituteOptions.isNotEmpty()) {
                SimpleDropdownField(
                    title = localizedStringResource(1658, "Substitute suggestion"),
                    selectedId = draft.substituteGoodsItemId,
                    options = listOf(DropdownOption("", localizedStringResource(1657, "No substitution"))) + availableSubstituteOptions.map { option ->
                        DropdownOption(
                            id = option.goodsItemId,
                            title = listOf(option.title, option.subtitle).filter { it.isNotBlank() }.joinToString(" • ")
                        )
                    },
                    placeholder = localizedStringResource(1657, "No substitution"),
                    onSelected = { selectedId -> onDraftChanged(draft.copy(substituteGoodsItemId = selectedId)) }
                )
                Text(
                    text = localizedStringResource(1659, "Use this when the requested SKU is unavailable but another store-known item can save the order."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }

            if (priceBookPrice != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    actionButton(
                        modifier = Modifier.weight(1f),
                        text = localizedStringResource(1695, "Use price book"),
                        iconPath = stateValues.drawablePathIconSupplierCatalog,
                        iconRes = stateValues.drawableResIconSupplierCatalog.value,
                        confirmationRequired = false,
                        onClick = {
                            onDraftChanged(supplierOrderLineDraftUsingPriceBook(line, draft, priceBookPrice))
                        }
                    )
                    actionButton(
                        modifier = Modifier.weight(1f),
                        text = localizedStringResource(1613, "Use request"),
                        iconPath = stateValues.drawablePathIconCheck,
                        confirmationRequired = false,
                        onClick = {
                            onDraftChanged(
                                draft.copy(
                                    acceptedQuantityText = stockQuantityInputTextFromAmount(line.requestedQuantity.total, line.requestedQuantity),
                                    offeredPriceText = line.expectedSupplyPrice.supplierDeskPriceInputText(),
                                    substituteGoodsItemId = ""
                                )
                            )
                        }
                    )
                }
            } else {
                actionButton(
                    text = localizedStringResource(1613, "Use request"),
                    iconPath = stateValues.drawablePathIconCheck,
                    fillMaxWidthIfTextPresent = false,
                    confirmationRequired = false,
                    onClick = {
                        onDraftChanged(
                            draft.copy(
                                acceptedQuantityText = stockQuantityInputTextFromAmount(line.requestedQuantity.total, line.requestedQuantity),
                                offeredPriceText = line.expectedSupplyPrice.supplierDeskPriceInputText(),
                                substituteGoodsItemId = ""
                            )
                        )
                    }
                )
            }

            actionButton(
                text = localizedStringResource(1758, "Decline line"),
                iconPath = stateValues.drawablePathIconCancel,
                iconRes = stateValues.drawableResIconCancel.value,
                enabledColor = stateValues.ErrorColor,
                fillMaxWidthIfTextPresent = false,
                confirmationRequired = false,
                onClick = {
                    onDraftChanged(
                        draft.copy(
                            acceptedQuantityText = stockQuantityInputTextFromAmount(0.0, line.requestedQuantity),
                            offeredPriceText = "",
                            commentText = draft.commentText.ifBlank { localizedStringResource(1760, "Unavailable for this delivery") },
                            substituteGoodsItemId = ""
                        )
                    )
                }
            )
        } else {
            StockCardInfoLine(localizedStringResource(1608, "Accepted quantity"), responseQuantityText.ifBlank { localizedStringResource(1759, "Not answered yet") }, stateValues.TextColor)
            StockCardInfoLine(localizedStringResource(1609, "Offered supply price"), responsePriceText.ifBlank { localizedStringResource(1759, "Not answered yet") }, stateValues.TextColor)
            responseCommentText?.takeIf { it.isNotBlank() }?.let {
                StockCardInfoLine(localizedStringResource(1610, "Line comment"), it, stateValues.TextColor)
            }
            currentSubstituteText.takeIf { it.isNotBlank() }?.let {
                StockCardInfoLine(localizedStringResource(1658, "Substitute suggestion"), it, stateValues.AccentColor)
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierOrderDeskCard(
    order: SupplierOrderDataModel,
    lines: List<SupplierOrderLineDataModel>,
    substituteOptions: List<SupplierSubstituteOptionUiModel> = emptyList(),
    supplierPriceRows: List<SupplierGoodsPriceDataModel> = emptyList(),
    supplierIdentityTitle: String = ""
) {
    val allowedStatuses = supplierDeskAllowedStatuses()
    val activeLines = remember(lines) { lines.filter { it.isActive } }
    var selectedStatusId by rememberSaveable(order.id, order.status.name) {
        mutableStateOf(
            when (order.status) {
                SupplierOrderStatusDataModel.Draft,
                SupplierOrderStatusDataModel.Sent -> SupplierOrderStatusDataModel.SeenBySupplier.name
                else -> order.status.name
            }
        )
    }
    var commentText by rememberSaveable(order.id, order.supplierComment.orEmpty()) {
        mutableStateOf(
            order.supplierCommentLocalized.extractLocalizedString(stateValues.appLanguage)
                ?: order.supplierCommentLocalized.extractLocalizedString("main")
                ?: order.supplierComment.orEmpty()
        )
    }
    var confirmedDeliveryDateText by rememberSaveable(order.id, order.confirmedDeliveryTimeMillis) {
        mutableStateOf(order.confirmedDeliveryTimeMillis.toStockDateInputText())
    }
    var paymentTermsText by rememberSaveable(order.id, order.paymentTerms.orEmpty()) { mutableStateOf(order.paymentTerms.orEmpty()) }
    var externalReferenceText by rememberSaveable(order.id, order.externalReference.orEmpty()) { mutableStateOf(order.externalReference.orEmpty()) }
    val lineDraftSeedKey = activeLines.joinToString("|") { line ->
        listOf(
            line.id,
            line.supplierAcceptedQuantity?.total?.toString().orEmpty(),
            line.supplierOfferedSupplyPrice?.price.orEmpty(),
            line.substituteGoodsItemId.orEmpty(),
            line.supplierComment.orEmpty(),
            line.supplierCommentLocalized.joinToString("/") { it.language + ":" + it.value }
        ).joinToString(":")
    }
    var lineDrafts by remember(order.id, lineDraftSeedKey, stateValues.appLanguage) {
        mutableStateOf(activeLines.associate { line -> line.id to supplierOrderLineResponseDraft(line) })
    }
    fun updateLineDraft(lineId: String, draft: SupplierOrderLineResponseDraft) {
        lineDrafts = lineDrafts + (lineId to draft)
    }

    fun priceBookForLine(line: SupplierOrderLineDataModel): SupplierGoodsPriceDataModel? =
        supplierDeskMatchingPriceBook(order, line, lineDrafts[line.id], supplierPriceRows)

    val priceBookMatchedLineCount = activeLines.count { line -> priceBookForLine(line) != null }

    val selectedStatus = runCatching { SupplierOrderStatusDataModel.valueOf(selectedStatusId) }.getOrDefault(order.status)
    val draftResponseLines = activeLines.map { line ->
        supplierOrderLineWithResponseDraft(
            line = line,
            supplierId = order.supplierId,
            draft = lineDrafts[line.id] ?: supplierOrderLineResponseDraft(line)
        )
    }
    val draftAnsweredLineCount = draftResponseLines.count { line -> line.supplierAcceptedQuantity != null }
    val draftDeclinedLineCount = draftResponseLines.count { line -> line.supplierDeskAcceptedQuantityTotal() == 0.0 }
    val draftMissingQuantityLineCount = draftResponseLines.count { line -> line.isMissingSupplierDeskAcceptedQuantity() }
    val draftMissingPriceLineCount = draftResponseLines.count { line -> line.isMissingSupplierDeskOfferedPriceForAcceptedQuantity() }
    val draftHasPositiveAcceptedLine = draftResponseLines.any { line -> line.hasPositiveSupplierDeskAcceptedQuantity() }
    val draftDeliveryTimeReady = stockDateInputTextToMillis(confirmedDeliveryDateText) != null
    val draftReadyToConfirm = activeLines.isNotEmpty() &&
            draftDeliveryTimeReady &&
            draftHasPositiveAcceptedLine &&
            draftResponseLines.all { line -> line.hasCompleteSupplierResponseLineForSupplierDesk() }
    val draftRequestedQuantityTotal = activeLines.sumOf { line -> line.requestedQuantity.total.coerceAtLeast(0.0) }.roundMoney()
    val draftAcceptedQuantityTotal = draftResponseLines.sumOf { line -> line.supplierAcceptedQuantity?.total?.coerceAtLeast(0.0) ?: 0.0 }.roundMoney()
    val draftAcceptedPercent = if (draftRequestedQuantityTotal > 0.0) {
        ((draftAcceptedQuantityTotal / draftRequestedQuantityTotal) * 100.0).roundToInt().coerceIn(0, 999)
    } else {
        0
    }
    val draftResponseCurrency = draftResponseLines.firstNotNullOfOrNull { line ->
        line.supplierOfferedSupplyPrice?.currency?.takeIf { it.isNotBlank() }
    } ?: order.amount?.currency ?: "KZT"
    val draftProjectedAmount = draftResponseLines.sumOf { line ->
        val quantity = line.supplierAcceptedQuantity?.total?.coerceAtLeast(0.0) ?: 0.0
        val price = line.supplierOfferedSupplyPrice?.price?.toMoneyDouble() ?: 0.0
        quantity * price
    }.roundMoney()
    val draftProjectedAmountText = draftProjectedAmount
        .takeIf { it > 0.0 }
        ?.let { amount -> "${amount.toStockMoneyText()} $draftResponseCurrency" }
        .orEmpty()
    val storeTitle = supplierDeskStoreTitle(order)
    val totalQuantityText = activeLines
        .joinToString(" • ") { line -> line.requestedQuantity.quantityText(stateValues.appLanguage) }
        .takeIf { it.isNotBlank() }
        ?: lines.size.toString()
    val amountText = order.amount.supplierDeskMoneyText()
    val isClosed = order.status.isSupplierOrderClosed()
    fun saveSupplierResponse(targetStatus: SupplierOrderStatusDataModel) {
        patchSupplierDeskOrder(
            order = order,
            lines = activeLines,
            status = targetStatus,
            comment = commentText,
            confirmedDeliveryDateText = confirmedDeliveryDateText,
            paymentTermsText = paymentTermsText,
            externalReferenceText = externalReferenceText,
            lineDrafts = lineDrafts
        )
    }

    fun fillQuickSupplierConfirmation() {
        val deliveryDateCandidate = order.confirmedDeliveryTimeMillis.toStockDateInputText()
            .ifBlank { order.desiredDeliveryTimeMillis.toStockDateInputText() }
            .ifBlank { getCurrentTimeMillis().toStockDateInputText() }
        confirmedDeliveryDateText = confirmedDeliveryDateText.ifBlank { deliveryDateCandidate }
        paymentTermsText = paymentTermsText.ifBlank { localizedStringResource(1652, "Payment after delivery, according to partner terms") }
        externalReferenceText = externalReferenceText.ifBlank { "SUP-${order.id.take(8).uppercase()}" }
        commentText = commentText.ifBlank { localizedStringResource(1653, "We can fulfill the requested lines. Quantities and prices are filled for your confirmation.") }
        selectedStatusId = SupplierOrderStatusDataModel.Confirmed.name
        lineDrafts = activeLines.associate { line ->
            val existingDraft = lineDrafts[line.id] ?: supplierOrderLineResponseDraft(line)
            line.id to existingDraft.copy(
                acceptedQuantityText = stockQuantityInputTextFromAmount(line.requestedQuantity.total, line.requestedQuantity),
                offeredPriceText = (line.supplierOfferedSupplyPrice ?: line.expectedSupplyPrice).supplierDeskPriceInputText()
            )
        }
        postInAppNotification(localizedStringResource(1654, "Supplier answer draft filled"), NotificationType.Positive, transient = true)
    }

    fun fillSupplierConfirmationFromPriceBook() {
        val deliveryDateCandidate = order.confirmedDeliveryTimeMillis.toStockDateInputText()
            .ifBlank { order.desiredDeliveryTimeMillis.toStockDateInputText() }
            .ifBlank { getCurrentTimeMillis().toStockDateInputText() }
        confirmedDeliveryDateText = confirmedDeliveryDateText.ifBlank { deliveryDateCandidate }
        paymentTermsText = paymentTermsText.ifBlank { localizedStringResource(1652, "Payment after delivery, according to partner terms") }
        externalReferenceText = externalReferenceText.ifBlank { "SUP-${order.id.take(8).uppercase()}" }
        commentText = commentText.ifBlank { localizedStringResource(1653, "We can fulfill the requested lines. Quantities and prices are filled for your confirmation.") }
        selectedStatusId = SupplierOrderStatusDataModel.Confirmed.name
        var appliedCount = 0
        lineDrafts = activeLines.associate { line ->
            val existingDraft = lineDrafts[line.id] ?: supplierOrderLineResponseDraft(line)
            val savedPrice = supplierDeskMatchingPriceBook(order, line, existingDraft, supplierPriceRows)
            if (savedPrice != null) appliedCount += 1
            line.id to if (savedPrice != null) {
                supplierOrderLineDraftUsingPriceBook(line, existingDraft, savedPrice)
            } else {
                existingDraft
            }
        }
        postInAppNotification(
            if (appliedCount > 0) localizedStringResource(1697, "Price book draft filled") else localizedStringResource(1698, "No matching saved price yet"),
            if (appliedCount > 0) NotificationType.Positive else NotificationType.Neutral,
            transient = true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (isClosed) stateValues.unfocusedBorderWidth else stateValues.focusedBorderWidth,
                if (isClosed) stateValues.PlaceholderTextColor else stateValues.AccentColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
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
                    .size(44.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(28.dp),
                    url = stateValues.drawablePathIconStores,
                    fallbackRes = stateValues.drawableResIconStores.value,
                    contentDescription = storeTitle,
                    tintColor = stateValues.AccentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = storeTitle,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${supplierOrderStatusTitle(order.status)} • ${receiptUiDateTime(order.supplierDeskSortTime())}",
                    color = if (isClosed) stateValues.PlaceholderTextColor else stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                order.storeAddressTextSnapshot.takeIf { it.isNotBlank() }?.let { address ->
                    Text(
                        text = address,
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = amountText.ifBlank { totalQuantityText },
                color = stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        supplierIdentityTitle.trim().takeIf { it.isNotBlank() }?.let { title ->
            StockCardInfoLine(
                localizedStringResource(1625, "Supplier identity"),
                title,
                stateValues.AccentColor
            )
        }
        StockCardInfoLine(localizedStringResource(960, "Ordered quantity"), totalQuantityText, stateValues.TextColor)
        order.desiredDeliveryTimeMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(956, "Desired delivery"), it, stateValues.TextColor)
        }
        order.confirmedDeliveryTimeMillis?.toStockDateInputText()?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1606, "Confirmed delivery"), it, stateValues.TextColor)
        }
        order.externalReference?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1607, "External reference"), it, stateValues.TextColor)
        }
        order.paymentTerms?.takeIf { it.isNotBlank() }?.let {
            StockCardInfoLine(localizedStringResource(1601, "Payment terms"), it, stateValues.TextColor)
        }
        val orderNotesText = order.additionalNotesLocalized.extractLocalizedString(stateValues.appLanguage)
            ?: order.additionalNotesLocalized.extractLocalizedString("main")
            ?: order.additionalNotes
        orderNotesText?.let { notes ->
            StockCardInfoLine(localizedStringResource(201, "Notes"), notes, stateValues.TextColor)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.DisabledColor.copy(alpha = 0.20f))
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = localizedStringResource(1358, "Requested goods"),
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
            )
            activeLines.forEach { line ->
                SupplierOrderLineResponseEditor(
                    line = line,
                    draft = lineDrafts[line.id] ?: supplierOrderLineResponseDraft(line),
                    editable = !isClosed,
                    substituteOptions = substituteOptions,
                    priceBookPrice = priceBookForLine(line),
                    onDraftChanged = { updateLineDraft(line.id, it) }
                )
            }
        }

        if (!isClosed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.08f))
                    .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor.copy(alpha = 0.55f), RoundedCornerShape(stateValues.cornerRadius))
                    .padding(stateValues.marginTextFieldGroup),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                Text(
                    text = localizedStringResource(1611, "Supplier editable answer"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(1612, "Stores see accepted quantity, price, delivery date and comments before goods move."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor.copy(alpha = 0.78f))
                        .border(
                            stateValues.unfocusedBorderWidth,
                            if (draftReadyToConfirm) stateValues.AccentColor.copy(alpha = 0.55f) else stateValues.PlaceholderTextColor.copy(alpha = 0.35f),
                            RoundedCornerShape(stateValues.cornerRadius)
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (draftReadyToConfirm) localizedStringResource(1768, "Ready to confirm") else localizedStringResource(1763, "Response progress"),
                        color = if (draftReadyToConfirm) stateValues.AccentColor else stateValues.TextColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold
                    )
                    if (stateValues.isNarrowScreen) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            SupplierCatalogChip(text = "${localizedStringResource(1764, "Answered")}: $draftAnsweredLineCount/${activeLines.size}")
                            SupplierCatalogChip(text = "${localizedStringResource(1765, "Declined")}: $draftDeclinedLineCount")
                            SupplierCatalogChip(text = "${localizedStringResource(1766, "Need quantity")}: $draftMissingQuantityLineCount")
                            SupplierCatalogChip(text = "${localizedStringResource(1767, "Need price")}: $draftMissingPriceLineCount")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1764, "Answered")}: $draftAnsweredLineCount/${activeLines.size}") }
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1765, "Declined")}: $draftDeclinedLineCount") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1766, "Need quantity")}: $draftMissingQuantityLineCount") }
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1767, "Need price")}: $draftMissingPriceLineCount") }
                        }
                    }
                    val acceptedProgressText = "${draftAcceptedQuantityTotal.toStockMoneyText()}/${draftRequestedQuantityTotal.toStockMoneyText()} • $draftAcceptedPercent%"
                    if (stateValues.isNarrowScreen) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            SupplierCatalogChip(text = "${localizedStringResource(1780, "Accepted now")}: $acceptedProgressText")
                            SupplierCatalogChip(text = "${localizedStringResource(1781, "Projected amount")}: ${draftProjectedAmountText.ifBlank { "—" }}")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1780, "Accepted now")}: $acceptedProgressText") }
                            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = "${localizedStringResource(1781, "Projected amount")}: ${draftProjectedAmountText.ifBlank { "—" }}") }
                        }
                    }
                    if (order.status == SupplierOrderStatusDataModel.Sent) {
                        Text(
                            text = localizedStringResource(1779, "Saving a new supplier answer marks the request as seen."),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                    }
                }

                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1655, "Fill quick confirmation"),
                    iconPath = stateValues.drawablePathIconResponse,
                    iconRes = stateValues.drawableResIconResponse.value,
                    confirmationRequired = false,
                    onClick = { fillQuickSupplierConfirmation() }
                )

                val fillFromPriceBookText = localizedStringResource(1696, "Fill from price book")
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "$fillFromPriceBookText • $priceBookMatchedLineCount/${activeLines.size}",
                    iconPath = stateValues.drawablePathIconSupplierCatalog,
                    iconRes = stateValues.drawableResIconSupplierCatalog.value,
                    confirmationRequired = false,
                    onClick = { fillSupplierConfirmationFromPriceBook() }
                )

                StockDatePartsEditor(
                    title = localizedStringResource(1606, "Confirmed delivery"),
                    dateText = confirmedDeliveryDateText,
                    onDateChanged = { confirmedDeliveryDateText = it }
                )

                if (stateValues.isNarrowScreen) {
                    Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                        SimpleTextInput(
                            modifier = Modifier.fillMaxWidth(),
                            value = externalReferenceText,
                            placeholder = localizedStringResource(1607, "External reference"),
                            leadingIconPath = stateValues.drawablePathIconReceipt,
                            onValueChange = { externalReferenceText = it.take(80) }
                        )
                        SimpleTextInput(
                            modifier = Modifier.fillMaxWidth(),
                            value = paymentTermsText,
                            placeholder = localizedStringResource(1601, "Payment terms"),
                            leadingIconPath = stateValues.drawablePathIconFinances,
                            onValueChange = { paymentTermsText = it.take(220) }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                    ) {
                        SimpleTextInput(
                            modifier = Modifier.weight(1f),
                            value = externalReferenceText,
                            placeholder = localizedStringResource(1607, "External reference"),
                            leadingIconPath = stateValues.drawablePathIconReceipt,
                            onValueChange = { externalReferenceText = it.take(80) }
                        )
                        SimpleTextInput(
                            modifier = Modifier.weight(1f),
                            value = paymentTermsText,
                            placeholder = localizedStringResource(1601, "Payment terms"),
                            leadingIconPath = stateValues.drawablePathIconFinances,
                            onValueChange = { paymentTermsText = it.take(220) }
                        )
                    }
                }
            }

            SimpleDropdownField(
                title = localizedStringResource(1359, "Supplier action"),
                selectedId = selectedStatusId,
                options = allowedStatuses.map { status ->
                    DropdownOption(
                        id = status.name,
                        title = supplierOrderStatusTitle(status)
                    )
                },
                placeholder = supplierOrderStatusTitle(order.status),
                onSelected = { selectedStatusId = it }
            )

            SimpleTextInput(
                modifier = Modifier.fillMaxWidth(),
                value = commentText,
                placeholder = localizedStringResource(1360, "Comment for the store"),
                singleLine = false,
                leadingIconPath = stateValues.drawablePathIconResponse,
                onValueChange = { commentText = it }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1361, "Save response"),
                    iconPath = stateValues.drawablePathIconCheck,
                    confirmationRequired = false,
                    onClick = { saveSupplierResponse(selectedStatus) }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1362, "Confirm"),
                    enabled = draftReadyToConfirm,
                    iconPath = stateValues.drawablePathIconTransactionSupply,
                    confirmationRequired = false,
                    onDisabledClick = { postInAppNotification(localizedStringResource(1774, "Fill quantity, price, and delivery date first"), NotificationType.Neutral, transient = true) },
                    onClick = { saveSupplierResponse(SupplierOrderStatusDataModel.Confirmed) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1363, "Packed"),
                    enabled = order.status == SupplierOrderStatusDataModel.Confirmed && draftReadyToConfirm,
                    iconPath = stateValues.drawablePathIconStock,
                    confirmationRequired = true,
                    onDisabledClick = { postInAppNotification(localizedStringResource(1775, "Confirm first, then pack"), NotificationType.Neutral, transient = true) },
                    onClick = { saveSupplierResponse(SupplierOrderStatusDataModel.Packed) }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1364, "In delivery"),
                    enabled = order.status == SupplierOrderStatusDataModel.Packed,
                    iconPath = stateValues.drawablePathIconStores,
                    confirmationRequired = true,
                    onDisabledClick = { postInAppNotification(localizedStringResource(1776, "Pack first, then start delivery"), NotificationType.Neutral, transient = true) },
                    onClick = { saveSupplierResponse(SupplierOrderStatusDataModel.InDelivery) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1614, "Mark issue"),
                    iconPath = stateValues.drawablePathIconResponse,
                    enabledColor = stateValues.BorderlineBadColor,
                    confirmationRequired = false,
                    onClick = { saveSupplierResponse(SupplierOrderStatusDataModel.IssueReported) }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(975, "Cancelled"),
                    iconPath = stateValues.drawablePathIconCancel,
                    enabledColor = stateValues.ErrorColor,
                    confirmationRequired = true,
                    onClick = { saveSupplierResponse(SupplierOrderStatusDataModel.Cancelled) }
                )
            }
        } else {
            Text(
                text = localizedStringResource(1365, "Closed orders stay here as a clean supplier-side fulfillment record."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}


internal data class SupplierProfileIdentityUiModel(
    val supplierId: String,
    val title: String,
    val subtitle: String,
    val orderCount: Int,
    val openOrderCount: Int,
    val catalogSkuCount: Int,
    val partnerCount: Int
)

internal fun AppConfiguration.buildSupplierProfileIdentityRows(
    dashboard: SupplierModeDashboardDataModel?,
    localProfiles: List<SupplierDataModel>,
    localProfilesLoaded: Boolean
): List<SupplierProfileIdentityUiModel> {
    val dashboardRowsById = dashboard?.supplierProfiles.orEmpty().mapNotNull { profile ->
        val normalizedId = normalizeSupplierProfileIdentityId(profile.supplierId) ?: return@mapNotNull null
        val title = profile.name.visibleLocalizedString(stateValues.appLanguage, "")
            .ifBlank { profile.supplierId.take(8) }
        val contact = (profile.phoneNumbers.asDisplayPhoneNumbers() + profile.emails)
            .filter { it.isNotBlank() }
            .distinct()
            .take(2)
            .joinToString(" • ")
            .ifBlank { localizedStringResource(1634, "No contact yet") }
        normalizedId to SupplierProfileIdentityUiModel(
            supplierId = profile.supplierId,
            title = title,
            subtitle = contact,
            orderCount = profile.orderCount,
            openOrderCount = profile.openOrderCount,
            catalogSkuCount = profile.catalogSkuCount,
            partnerCount = profile.partnerCount
        )
    }.toMap()

    val merged = linkedMapOf<String, SupplierProfileIdentityUiModel>()
    localProfiles.forEach { supplier ->
        val normalizedId = normalizeSupplierProfileIdentityId(supplier.id) ?: return@forEach
        val dashboardRow = dashboardRowsById[normalizedId]
        val contact = (supplier.phoneNumbers.orEmpty().asDisplayPhoneNumbers() + supplier.emails.orEmpty())
            .filter { it.isNotBlank() }
            .distinct()
            .take(2)
            .joinToString(" • ")
            .ifBlank { localizedStringResource(1634, "No contact yet") }
        merged[normalizedId] = dashboardRow?.copy(
            title = supplier.visibleSupplierName(stateValues.appLanguage),
            subtitle = contact
        ) ?: SupplierProfileIdentityUiModel(
            supplierId = supplier.id,
            title = supplier.visibleSupplierName(stateValues.appLanguage),
            subtitle = contact,
            orderCount = 0,
            openOrderCount = 0,
            catalogSkuCount = 0,
            partnerCount = 0
        )
    }
    if (supplierIdentityPresentationAllowsDashboardFallback(localProfilesLoaded)) {
        dashboardRowsById.forEach { (normalizedId, row) ->
            if (normalizedId !in merged) merged[normalizedId] = row
        }
    }

    return merged.values.sortedBy { it.title.lowercase() }
}

@Composable
internal fun AppConfiguration.SupplierProfileIdentityCard(
    modifier: Modifier = Modifier,
    dashboard: SupplierModeDashboardDataModel? = null,
    compact: Boolean = false,
    includeContractsOnRefresh: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    val activeSupplierProfileId by activeSupplierProfileIdState.collectAsState()
    val localProfilesLoaded = stateValues.suppliers != null
    val localProfiles = stateValues.suppliers.orEmpty().supplierProfilesOwnedBy(stateValues.userAccount?.id)
    val focusedSupplierId = resolveSupplierProfileFocus(activeSupplierProfileId, localProfiles)
    val identityPresentation = buildSupplierIdentityPresentation(
        localProfiles = localProfiles,
        dashboard = dashboard,
        activeSupplierId = focusedSupplierId,
        localProfilesLoaded = localProfilesLoaded
    )
    val rows = remember(dashboard, localProfiles, localProfilesLoaded, stateValues.appLanguage) {
        buildSupplierProfileIdentityRows(dashboard, localProfiles, localProfilesLoaded)
    }
    val hasProfiles = rows.isNotEmpty() ||
        (!localProfilesLoaded && dashboard?.supplierIds.orEmpty().isNotEmpty())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (hasProfiles) stateValues.BackgroundColor else stateValues.AccentColor.copy(alpha = 0.10f))
            .border(
                if (hasProfiles) stateValues.unfocusedBorderWidth else stateValues.focusedBorderWidth,
                if (hasProfiles) stateValues.PlaceholderTextColor else stateValues.AccentColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 38.dp else 46.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(if (compact) 24.dp else 30.dp),
                    url = stateValues.drawablePathIconSuppliers,
                    fallbackRes = stateValues.drawableResIconSuppliers.value,
                    contentDescription = localizedStringResource(1625, "Supplier identity"),
                    tintColor = stateValues.AccentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasProfiles) localizedStringResource(1625, "Supplier identity") else localizedStringResource(1627, "Set up your supplier profile"),
                    color = stateValues.TextColor,
                    fontSize = if (compact) stateValues.accentTextSize else stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (hasProfiles) localizedStringResource(1626, "This account can receive store orders through these supplier profiles.") else localizedStringResource(1628, "Create the business identity stores will order from. It is like hanging your sign above the warehouse door."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = if (compact) 3 else 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (hasProfiles) {
            SupplierCatalogChip(text = "${localizedStringResource(1630, "Active supplier profiles")}: ${rows.size.coerceAtLeast(dashboard?.supplierIds?.size ?: 0)}")

            if (identityPresentation.options.size > 1) {
                SupplierIdentityFocusSelector(
                    presentation = identityPresentation,
                    onIdentitySelected = { supplierId ->
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

            rows.take(if (compact) 2 else 4).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.06f))
                        .border(
                            stateValues.unfocusedBorderWidth,
                            stateValues.AccentColor.copy(alpha = 0.25f),
                            RoundedCornerShape(stateValues.cornerRadius)
                        )
                        .padding(horizontal = stateValues.marginTextField, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    CpImage(
                        modifier = Modifier.size(22.dp),
                        url = stateValues.drawablePathIconAppModeSupplier,
                        fallbackRes = stateValues.drawableResIconAppModeSupplier.value,
                        contentDescription = row.title,
                        tintColor = stateValues.AccentColor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.title,
                            color = stateValues.TextColor,
                            fontSize = stateValues.textSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = row.subtitle,
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val pulse = if (row.orderCount > 0) {
                        "${row.openOrderCount}/${row.orderCount}"
                    } else {
                        localizedStringResource(1631, "Orders will arrive here")
                    }
                    Text(
                        text = pulse,
                        color = stateValues.AccentColor,
                        fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!compact && rows.any { it.catalogSkuCount > 0 || it.partnerCount > 0 }) {
                val catalogTotal = rows.sumOf { it.catalogSkuCount }
                val partnerTotal = rows.sumOf { it.partnerCount }
                Text(
                    text = "${localizedStringResource(1408, "Catalog SKUs")}: $catalogTotal • ${localizedStringResource(1453, "Partner stores")}: $partnerTotal",
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = localizedStringResource(1635, "Incoming store orders start here after a store chooses this supplier."),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        if (stateValues.isNarrowScreen) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (hasProfiles) localizedStringResource(2495, "Manage profiles") else localizedStringResource(1624, "Create supplier profile"),
                    iconPath = stateValues.drawablePathIconSuppliers,
                    iconRes = stateValues.drawableResIconSuppliers.value,
                    confirmationRequired = false,
                    onClick = {
                        if (hasProfiles) {
                            coroutineScope.launch {
                                openSupplierProfilesWorkspace(stateValues.navigationScreensMain.lastOrNull()?.route)
                            }
                        } else {
                            coroutineScope.launch {
                                openSupplierProfileEditor(stateValues.navigationScreensMain.lastOrNull()?.route)
                            }
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1636, "Refresh supplier desk"),
                    iconPath = stateValues.drawablePathIconResponse,
                    iconRes = stateValues.drawableResIconResponse.value,
                    confirmationRequired = false,
                    onClick = {
                        refreshSupplierModeWorkspace(
                            includeContracts = includeContractsOnRefresh,
                            force = true
                        )
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
                    text = if (hasProfiles) localizedStringResource(2495, "Manage profiles") else localizedStringResource(1624, "Create supplier profile"),
                    iconPath = stateValues.drawablePathIconSuppliers,
                    iconRes = stateValues.drawableResIconSuppliers.value,
                    confirmationRequired = false,
                    onClick = {
                        if (hasProfiles) {
                            coroutineScope.launch {
                                openSupplierProfilesWorkspace(stateValues.navigationScreensMain.lastOrNull()?.route)
                            }
                        } else {
                            coroutineScope.launch {
                                openSupplierProfileEditor(stateValues.navigationScreensMain.lastOrNull()?.route)
                            }
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1636, "Refresh supplier desk"),
                    iconPath = stateValues.drawablePathIconResponse,
                    iconRes = stateValues.drawableResIconResponse.value,
                    confirmationRequired = false,
                    onClick = {
                        refreshSupplierModeWorkspace(
                            includeContracts = includeContractsOnRefresh,
                            force = true
                        )
                    }
                )
            }
        }
    }
}


// Supplier contracts workspace moved to SupplierModeContracts*.kt.
// Supplier dispatch workspace moved to SupplierModeDispatch*.kt.

@Composable
internal fun AppConfiguration.SupplierPlaceholderScreen(
    title: String,
    subtitle: String,
    iconPath: String,
    iconRes: DrawableResource?
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenAppBarWidget(
            title = title,
            iconPath = iconPath,
            iconRes = iconRes
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.68f)
                .align(Alignment.CenterHorizontally)
                .padding(stateValues.marginTextField),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
            contentPadding = PaddingValues(bottom = stateValues.screenHeight / 5)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(stateValues.focusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                        .padding(stateValues.marginTextFieldGroup),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CpImage(
                        modifier = Modifier.size(54.dp),
                        url = iconPath,
                        fallbackRes = iconRes,
                        contentDescription = title,
                        tintColor = stateValues.AccentColor
                    )
                    Text(
                        text = title,
                        color = stateValues.TextColor,
                        fontSize = stateValues.titleTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = subtitle,
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.textSize,
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(supplierMarketWinningFeatures().filterNot { it.implemented }) { feature ->
                SupplierFeaturePlanCard(feature = feature, compact = false)
            }
        }
    }
}


internal data class SupplierDemandRadarUiModel(
    val goodsItemId: String,
    val title: String,
    val subtitle: String,
    val totalQuantityText: String,
    val storesCount: Int,
    val openOrderCount: Int,
    val issueCount: Int,
    val latestStatus: SupplierOrderStatusDataModel,
    val lastActivityMillis: Long,
    val latestPriceText: String,
    val demandScore: Int,
    val searchKey: String
)

internal fun AppConfiguration.buildSupplierDemandRadarItems(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>
): List<SupplierDemandRadarUiModel> {
    val activeOrders = orders.filter { it.isActive }
    val ordersById = activeOrders.associateBy { it.id }
    return lines
        .filter { it.isActive && it.goodsItemId.isNotBlank() }
        .groupBy { it.goodsItemId }
        .mapNotNull { (goodsItemId, itemLines) ->
            val relatedOrders = itemLines
                .mapNotNull { ordersById[it.orderId] }
                .distinctBy { it.id }
            if (relatedOrders.isEmpty()) return@mapNotNull null

            val latestOrder = relatedOrders.maxByOrNull { it.supplierDeskSortTime() } ?: return@mapNotNull null
            val sampleLine = itemLines.maxByOrNull { line -> ordersById[line.orderId]?.supplierDeskSortTime() ?: 0L } ?: return@mapNotNull null
            val openOrders = relatedOrders.count { !it.status.isSupplierOrderClosed() }
            val issueCount = relatedOrders.count { it.status == SupplierOrderStatusDataModel.IssueReported || it.status == SupplierOrderStatusDataModel.Cancelled }
            val totalQuantity = itemLines.sumOf { it.requestedQuantity.total.coerceAtLeast(0.0) }
            val baseQuantity = sampleLine.requestedQuantity
            val totalQuantityText = if (totalQuantity > 0.0) {
                baseQuantity.copy(total = totalQuantity).quantityText(stateValues.appLanguage)
            } else {
                itemLines.size.toString()
            }
            val stores = relatedOrders.map { supplierDeskStoreTitle(it) }.filter { it.isNotBlank() }.distinct()
            val latestPrice = itemLines.asSequence()
                .mapNotNull { it.supplierOfferedSupplyPrice ?: it.expectedSupplyPrice }
                .firstOrNull()
                .supplierDeskMoneyText()
            val title = supplierDeskLineTitle(sampleLine)
            val demandScore = openOrders * 3 + issueCount * 2 + stores.size + itemLines.size

            SupplierDemandRadarUiModel(
                goodsItemId = goodsItemId,
                title = title,
                subtitle = stores.take(3).joinToString(" • ").ifBlank { goodsItemId.take(8) },
                totalQuantityText = totalQuantityText,
                storesCount = stores.size,
                openOrderCount = openOrders,
                issueCount = issueCount,
                latestStatus = latestOrder.status,
                lastActivityMillis = latestOrder.supplierDeskSortTime(),
                latestPriceText = latestPrice,
                demandScore = demandScore,
                searchKey = buildString {
                    append(goodsItemId).append(' ')
                    append(title).append(' ')
                    append(stores.joinToString(" ")).append(' ')
                    append(sampleLine.goodsItemBarcodeSnapshots.joinToString(" ")).append(' ')
                    append(latestOrder.status.name).append(' ')
                    append(supplierOrderStatusTitle(latestOrder.status))
                }.lowercase()
            )
        }
        .sortedWith(compareByDescending<SupplierDemandRadarUiModel> { it.demandScore }
            .thenByDescending { it.lastActivityMillis })
}

@Composable
internal fun AppConfiguration.SupplierDemandRadarCard(item: SupplierDemandRadarUiModel) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (item.openOrderCount > 0) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (item.openOrderCount > 0) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
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
                    .size(46.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(30.dp),
                    url = stateValues.drawablePathIconSupplierDemandRadar,
                    fallbackRes = stateValues.drawableResIconSupplierDemandRadar.value,
                    contentDescription = item.title,
                    tintColor = stateValues.AccentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = item.latestPriceText.ifBlank { item.totalQuantityText },
                color = stateValues.AccentColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        StockCardInfoLine(localizedStringResource(1542, "Prepare stock"), item.totalQuantityText, stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1422, "Stores asking"), item.storesCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1423, "Open requests"), item.openOrderCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1545, "Latest activity"), receiptUiDateTime(item.lastActivityMillis), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1552, "Demand score"), item.demandScore.toString(), stateValues.TextColor)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SupplierCatalogChip(text = supplierOrderStatusTitle(item.latestStatus))
            }
            if (item.issueCount > 0) {
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(text = "${localizedStringResource(1473, "Issues")}: ${item.issueCount}")
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    SupplierCatalogChip(text = localizedStringResource(1469, "Healthy rhythm"))
                }
            }
        }

        actionButton(
            modifier = Modifier.fillMaxWidth(),
            text = localizedStringResource(1546, "Open matching orders"),
            iconPath = stateValues.drawablePathIconAppModeSupplier,
            iconRes = stateValues.drawableResIconAppModeSupplier.value,
            confirmationRequired = false,
            onClick = {
                coroutineScope.launch {
                    seedSupplierOrdersInboxNavigation(searchQuery = item.goodsItemId)
                    Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                }
            }
        )
    }
}


internal data class SupplierTermsGuardUiModel(
    val contract: SupplierPartnershipContractDataModel,
    val title: String,
    val subtitle: String,
    val statusText: String,
    val effectText: String,
    val affectedOpenOrders: Int,
    val affectedLineCount: Int,
    val coveredGoodsText: String,
    val terms: List<String>,
    val priceTermsText: String,
    val searchKey: String,
    val orderSearchQuery: String,
    val brief: String,
    val statusColor: Color
)

internal fun AppConfiguration.supplierTermsGuardEffectText(contract: SupplierPartnershipContractDataModel): String = when (contract.status) {
    SUPPLIER_CONTRACT_STATUS_ACTIVE -> localizedStringResource(1589, "Active terms protecting supply")
    SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER -> localizedStringResource(1590, "Waiting for supplier acceptance")
    SUPPLIER_CONTRACT_STATUS_PENDING_STORE -> localizedStringResource(1591, "Waiting for store acceptance")
    else -> supplierContractStatusTitle(contract.status)
}

internal fun AppConfiguration.buildSupplierTermsGuardItems(
    orders: List<SupplierOrderDataModel>,
    lines: List<SupplierOrderLineDataModel>,
    contracts: List<SupplierPartnershipContractDataModel>
): List<SupplierTermsGuardUiModel> {
    val activeOrders = orders.filter { it.isActive }
    val linesByOrder = lines.filter { it.isActive }.groupBy { it.orderId }
    val guardStatuses = setOf(
        SUPPLIER_CONTRACT_STATUS_ACTIVE,
        SUPPLIER_CONTRACT_STATUS_PENDING_SUPPLIER,
        SUPPLIER_CONTRACT_STATUS_PENDING_STORE
    )

    return contracts
        .filter { contract -> contract.isActive && contract.status in guardStatuses }
        .map { contract ->
            val contractGoodsIds = contract.goodsItemIds.toSet()
            val relatedOrders = activeOrders.filter { order ->
                order.storeId == contract.storeId && order.supplierId == contract.supplierId
            }
            val relatedLines = relatedOrders
                .flatMap { order -> linesByOrder[order.id].orEmpty() }
                .filter { line -> contract.scopeType == SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP || line.goodsItemId in contractGoodsIds }
            val affectedOrders = relatedOrders.filter { order ->
                contract.scopeType == SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP || linesByOrder[order.id].orEmpty().any { it.goodsItemId in contractGoodsIds }
            }
            val openAffectedOrders = affectedOrders.count { !it.status.isSupplierOrderClosed() }
            val goodsTitles = relatedLines
                .map { supplierDeskLineTitle(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(4)
            val fallbackGoodsTitles = contract.priceTerms
                .map { term -> term.goodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, term.goodsItemId.take(8)) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(4)
            val coveredGoodsText = (goodsTitles.ifEmpty { fallbackGoodsTitles })
                .joinToString(" • ")
                .ifBlank { supplierContractScopeTitle(contract.scopeType) }
            val conditionTerms = contract.conditions
                .mapNotNull { raw -> runCatching { visibleStockConditionText(raw.toStockConditionDataModel()) }.getOrNull() }
                .filter { it.isNotBlank() }
            val deliveryText = contract.deliverySchedule.visibleLocalizedString(stateValues.appLanguage, "")
                .takeIf { it.isNotBlank() }
                ?.let { "${localizedStringResource(1600, "Delivery terms")}: $it" }
            val paymentText = contract.paymentSchedule.visibleLocalizedString(stateValues.appLanguage, "")
                .takeIf { it.isNotBlank() }
                ?.let { "${localizedStringResource(1601, "Payment terms")}: $it" }
            val customText = contract.customTerms.visibleLocalizedString(stateValues.appLanguage, "")
                .takeIf { it.isNotBlank() }
                ?.let { "${localizedStringResource(1599, "Custom terms")}: $it" }
            val priceTermsText = contract.priceTerms
                .filter { it.isActive }
                .take(3)
                .joinToString(" • ") { term ->
                    listOfNotNull(
                        term.goodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, term.goodsItemId.take(8)),
                        term.supplyPrice.supplierDeskMoneyText(),
                        term.minOrderQuantity?.quantityText(stateValues.appLanguage)
                    ).filter { it.isNotBlank() }.joinToString(" ")
                }
            val terms = (conditionTerms + listOfNotNull(deliveryText, paymentText, customText))
                .distinct()
                .take(6)
            val title = supplierVisibleContractTitle(contract)
            val subtitle = listOf(
                supplierContractScopeTitle(contract.scopeType),
                supplierContractStatusTitle(contract.status),
                "rev.${contract.revision}"
            ).joinToString(" • ")
            val effect = supplierTermsGuardEffectText(contract)
            val orderSearchQuery = (listOf(contract.storeId, contract.supplierId) + contract.goodsItemIds).filter { it.isNotBlank() }.joinToString(" ")
            val brief = buildString {
                append(localizedStringResource(1597, "Terms brief")).append('\n')
                append(title).append('\n')
                append(subtitle).append('\n')
                append(localizedStringResource(1585, "Operational effect")).append(": ").append(effect).append('\n')
                append(localizedStringResource(1586, "Affected open orders")).append(": ").append(openAffectedOrders).append('\n')
                if (coveredGoodsText.isNotBlank()) append(localizedStringResource(1587, "Covered goods")).append(": ").append(coveredGoodsText).append('\n')
                if (priceTermsText.isNotBlank()) append(localizedStringResource(1598, "Price terms")).append(": ").append(priceTermsText).append('\n')
                terms.forEach { append("• ").append(it).append('\n') }
            }.trim()
            SupplierTermsGuardUiModel(
                contract = contract,
                title = title,
                subtitle = subtitle,
                statusText = supplierContractStatusTitle(contract.status),
                effectText = effect,
                affectedOpenOrders = openAffectedOrders,
                affectedLineCount = relatedLines.size,
                coveredGoodsText = coveredGoodsText,
                terms = terms,
                priceTermsText = priceTermsText,
                searchKey = buildString {
                    append(contract.id).append(' ')
                    append(title).append(' ')
                    append(subtitle).append(' ')
                    append(effect).append(' ')
                    append(coveredGoodsText).append(' ')
                    append(priceTermsText).append(' ')
                    append(terms.joinToString(" ")).append(' ')
                    append(orderSearchQuery)
                }.lowercase(),
                orderSearchQuery = orderSearchQuery,
                brief = brief,
                statusColor = supplierContractStatusColor(contract.status)
            )
        }
        .sortedWith(compareByDescending<SupplierTermsGuardUiModel> { if (it.contract.status != SUPPLIER_CONTRACT_STATUS_ACTIVE) 1 else 0 }
            .thenByDescending { it.affectedOpenOrders }
            .thenByDescending { it.contract.updatedAtMillis })
}

@Composable
internal fun AppConfiguration.SupplierTermsGuardCard(item: SupplierTermsGuardUiModel) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, item.statusColor.copy(alpha = 0.75f), RoundedCornerShape(stateValues.cornerRadius))
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
                    .size(46.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(item.statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(30.dp),
                    url = stateValues.drawablePathIconSupplierTermsGuard,
                    fallbackRes = stateValues.drawableResIconSupplierTermsGuard.value,
                    contentDescription = localizedStringResource(1583, "Supplier terms guard"),
                    tintColor = item.statusColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    color = item.statusColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = item.effectText,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

        StockCardInfoLine(localizedStringResource(1586, "Affected open orders"), item.affectedOpenOrders.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1604, "Related order lines"), item.affectedLineCount.toString(), stateValues.TextColor)
        StockCardInfoLine(localizedStringResource(1587, "Covered goods"), item.coveredGoodsText, stateValues.TextColor)
        item.priceTermsText.takeIf { it.isNotBlank() }?.let { text ->
            StockCardInfoLine(localizedStringResource(1598, "Price terms"), text, stateValues.TextColor)
        }

        item.terms.take(4).forEach { term ->
            Text(
                text = "• $term",
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) { SupplierCatalogChip(text = item.statusText) }
            Box(modifier = Modifier.weight(1f)) {
                SupplierCatalogChip(
                    text = if (item.contract.status == SUPPLIER_CONTRACT_STATUS_ACTIVE) {
                        localizedStringResource(1602, "Guarded orders")
                    } else {
                        localizedStringResource(1588, "Blocking supply until accepted")
                    }
                )
            }
        }

        if (stateValues.isNarrowScreen) {
            Column(verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1594, "Open contract board"),
                    iconPath = stateValues.drawablePathIconSupplierContracts,
                    iconRes = stateValues.drawableResIconSupplierContracts.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { coroutineScope.launch { Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main) } }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1595, "Open affected orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = item.orderSearchQuery)
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = localizedStringResource(1596, "Copy terms brief"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { copyTextToClipboard(item.brief) }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1594, "Open contract board"),
                    iconPath = stateValues.drawablePathIconSupplierContracts,
                    iconRes = stateValues.drawableResIconSupplierContracts.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { coroutineScope.launch { Navigation.goMain(NavigationScreenModel.Supplier.Contracts.Main) } }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1595, "Open affected orders"),
                    iconPath = stateValues.drawablePathIconAppModeSupplier,
                    iconRes = stateValues.drawableResIconAppModeSupplier.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = {
                        coroutineScope.launch {
                            seedSupplierOrdersInboxNavigation(searchQuery = item.orderSearchQuery)
                            Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                        }
                    }
                )
                actionButton(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(1596, "Copy terms brief"),
                    iconPath = stateValues.drawablePathIconClipboard,
                    iconRes = stateValues.drawableResIconClipboard.value,
                    textSize = stateValues.smallTextSize,
                    confirmationRequired = false,
                    onClick = { copyTextToClipboard(item.brief) }
                )
            }
        }
    }
}


internal fun AppConfiguration.supplierManufacturerBridgeTitle(item: SupplierDashboardManufacturerBridgeDataModel): String =
    item.goodsItemNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.barcodeSnapshots.firstOrNull().orEmpty() }
        .ifBlank { item.goodsItemId.take(8) }

internal fun AppConfiguration.supplierManufacturerBridgeQuantityText(
    total: Double,
    unitId: String?
): String {
    if (!total.isFinite() || total <= 0.0) return "0"
    val unit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
        .find { it.id == unitId }
        ?: stateValues.globalAppConfiguration.goodsItemsQuantityUnits.firstOrNull()
    return unit
        ?.copy(total = total)
        ?.quantityText(stateValues.appLanguage)
        ?: total.toStockMoneyText()
}

internal fun AppConfiguration.supplierManufacturerBridgeActionTitle(action: String): String = when (action) {
    "quote" -> localizedStringResource(1716, "Quote upstream")
    "produce" -> localizedStringResource(1717, "Produce / reserve")
    "ship" -> localizedStringResource(1718, "Ship to stores")
    "price_book" -> localizedStringResource(1719, "Add factory price")
    "backorder" -> localizedStringResource(1720, "Watch backorder")
    else -> localizedStringResource(1721, "Demand is stable")
}

internal fun AppConfiguration.supplierManufacturerBridgeSearchKey(item: SupplierDashboardManufacturerBridgeDataModel): String = buildString {
    append(item.bridgeId).append(' ')
    append(item.goodsItemId).append(' ')
    append(item.orderIds.joinToString(" ")).append(' ')
    append(item.quoteNeededOrderIds.joinToString(" ")).append(' ')
    append(item.productionOrderIds.joinToString(" ")).append(' ')
    append(item.shipmentOrderIds.joinToString(" ")).append(' ')
    append(supplierManufacturerBridgeTitle(item)).append(' ')
    append(item.barcodeSnapshots.joinToString(" ")).append(' ')
    append(item.storePreview.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.storePreview.visibleLocalizedString("main", "")).append(' ')
    append(item.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")).append(' ')
    append(item.attentionSummary.visibleLocalizedString("main", "")).append(' ')
    append(item.suggestedAction).append(' ')
    append(supplierManufacturerBridgeActionTitle(item.suggestedAction)).append(' ')
    append(item.requestedQuantityTotal).append(' ')
    append(item.acceptedQuantityTotal).append(' ')
    append(item.missingQuantityTotal)
}.lowercase()

internal fun AppConfiguration.supplierManufacturerBridgeBrief(item: SupplierDashboardManufacturerBridgeDataModel): String = buildString {
    append(localizedStringResource(1710, "Manufacturer bridge")).append('\n')
    append(supplierManufacturerBridgeTitle(item)).append('\n')
    if (item.barcodeSnapshots.isNotEmpty()) append(localizedStringResource(69, "Barcode")).append(": ").append(item.barcodeSnapshots.joinToString(", ")).append('\n')
    append(localizedStringResource(1424, "Total requested")).append(": ").append(supplierManufacturerBridgeQuantityText(item.requestedQuantityTotal, item.measurementUnitIdSnapshot)).append('\n')
    append(localizedStringResource(1713, "Accepted qty")).append(": ").append(supplierManufacturerBridgeQuantityText(item.acceptedQuantityTotal, item.measurementUnitIdSnapshot)).append('\n')
    append(localizedStringResource(1714, "Missing qty")).append(": ").append(supplierManufacturerBridgeQuantityText(item.missingQuantityTotal, item.measurementUnitIdSnapshot)).append('\n')
    append(localizedStringResource(1422, "Stores asking")).append(": ").append(item.storeCount).append('\n')
    append(localizedStringResource(1423, "Open requests")).append(": ").append(item.openOrderCount).append('\n')
    append(localizedStringResource(1715, "Response coverage")).append(": ").append(item.responseCoveragePercent).append('%').append('\n')
    append(localizedStringResource(1783, "Factory staging")).append(": ")
        .append(localizedStringResource(1784, "Quote")).append(' ').append(item.quoteNeededOrderIds.size).append(" • ")
        .append(localizedStringResource(1785, "Produce")).append(' ').append(item.productionOrderIds.size).append(" • ")
        .append(localizedStringResource(1786, "Ship")).append(' ').append(item.shipmentOrderIds.size).append('\n')
    item.attentionSummary.visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { item.attentionSummary.visibleLocalizedString("main", "") }
        .takeIf { it.isNotBlank() }
        ?.let { attention -> append(localizedStringResource(1756, "Attention notes")).append(": ").append(attention).append('\n') }
    append(localizedStringResource(1787, "Upstream privacy")).append(": ").append(localizedStringResource(1788, "Store names are omitted from this copied factory brief.")).append('\n')
    item.estimatedAcceptedAmount.supplierDeskMoneyText().takeIf { it.isNotBlank() }?.let { amount ->
        append(localizedStringResource(1727, "Factory value")).append(": ").append(amount).append('\n')
    }
    item.earliestDueAtMillis?.takeIf { it > 0L }?.let { due ->
        append(localizedStringResource(1723, "Earliest due")).append(": ").append(receiptUiDateTime(due)).append('\n')
    }
    append(localizedStringResource(1722, "Priority score")).append(": ").append(item.priorityScore).append('\n')
    append(localizedStringResource(1650, "Actions")).append(": ").append(supplierManufacturerBridgeActionTitle(item.suggestedAction))
}
