package kz.aita

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource

internal data class SupplierOrdersMetricUiModel(
    val filterId: String,
    val title: String,
    val value: Int,
    val iconPath: String,
    val iconRes: DrawableResource?,
    val selected: Boolean,
    val attention: Boolean = false
)

internal data class SupplierOrdersQuickFilterUiModel(
    val id: String,
    val title: String
)

internal fun SupplierDashboardReadinessDataModel.hasSupplierReadinessSignal(): Boolean =
    openOrderCount > 0 ||
            priceBookCoveredLineCount > 0 ||
            answerNeededOrderCount > 0 ||
            readyToPackOrderCount > 0 ||
            responseLineCount > 0 ||
            acceptedQuantityTotal > 0.0

@Composable
internal fun AppConfiguration.SupplierOrdersWorkspaceHeader(
    profileTitle: String,
    profileSubtitle: String,
    onCreateProfile: () -> Unit,
    onRefresh: () -> Unit,
    identityPresentation: SupplierIdentityPresentationUiModel? = null,
    onIdentitySelected: (String?) -> Unit = {}
) {
    var refreshCoolingDown by remember { mutableStateOf(false) }

    LaunchedEffect(refreshCoolingDown) {
        if (refreshCoolingDown) {
            delay(1_500L)
            refreshCoolingDown = false
        }
    }

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
                    .size(42.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(27.dp),
                    url = stateValues.drawablePathIconAppModeSupplier,
                    fallbackRes = stateValues.drawableResIconAppModeSupplier.value,
                    contentDescription = localizedStringResource(1366, "Supplier desk"),
                    tintColor = stateValues.AccentColor
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = profileTitle.ifBlank { localizedStringResource(1366, "Supplier desk") },
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profileSubtitle,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            actionButton(
                modifier = Modifier.size(40.dp),
                text = "",
                iconPath = stateValues.drawablePathIconSuppliers,
                iconRes = stateValues.drawableResIconSuppliers.value,
                iconContentDescription = localizedStringResource(1633, "Create another profile"),
                confirmationRequired = false,
                autoLoading = false,
                onClick = onCreateProfile
            )
            actionButton(
                modifier = Modifier.size(40.dp),
                enabled = !refreshCoolingDown,
                loading = refreshCoolingDown,
                autoLoading = false,
                text = "",
                iconPath = stateValues.drawablePathIconResponse,
                iconRes = stateValues.drawableResIconResponse.value,
                iconContentDescription = localizedStringResource(237, "Refresh"),
                confirmationRequired = false,
                onClick = {
                    if (!refreshCoolingDown) {
                        refreshCoolingDown = true
                        onRefresh()
                    }
                }
            )
        }

        identityPresentation?.let { presentation ->
            SupplierIdentityFocusSelector(
                presentation = presentation,
                onIdentitySelected = onIdentitySelected
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierOrdersMetricCard(
    metric: SupplierOrdersMetricUiModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = when {
        metric.selected -> stateValues.AccentColor
        metric.attention && metric.value > 0 -> stateValues.BorderlineBadColor
        else -> stateValues.PlaceholderTextColor
    }
    val iconColor = when {
        metric.attention && metric.value > 0 -> stateValues.BorderlineBadColor
        metric.selected -> stateValues.AccentColor
        else -> stateValues.IconTintColor
    }

    Row(
        modifier = modifier
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(
                if (metric.selected) stateValues.AccentColor.copy(alpha = 0.10f)
                else stateValues.BackgroundColor
            )
            .border(
                if (metric.selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                borderColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = onClick
            )
            .padding(horizontal = stateValues.marginTextField, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            CpImage(
                modifier = Modifier.size(21.dp),
                url = metric.iconPath,
                fallbackRes = metric.iconRes,
                contentDescription = metric.title,
                tintColor = iconColor
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metric.value.toString(),
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = metric.title,
                color = if (metric.selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = if (metric.selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierOrdersMetrics(
    metrics: List<SupplierOrdersMetricUiModel>,
    onSelected: (SupplierOrdersMetricUiModel) -> Unit
) {
    if (stateValues.isNarrowScreen) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            metrics.chunked(2).forEach { rowMetrics ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    rowMetrics.forEach { metric ->
                        SupplierOrdersMetricCard(
                            metric = metric,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelected(metric) }
                        )
                    }
                    if (rowMetrics.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            metrics.forEach { metric ->
                SupplierOrdersMetricCard(
                    metric = metric,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(metric) }
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierOrdersQuickFilterChip(
    filter: SupplierOrdersQuickFilterUiModel,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = filter.title,
        color = if (selected) stateValues.AccentTextColor else stateValues.TextColor,
        fontSize = stateValues.smallTextSize,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(if (selected) stateValues.AccentColor else stateValues.BackgroundColor)
            .border(
                if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
internal fun AppConfiguration.SupplierOrdersCompactCard(
    order: SupplierOrderDataModel,
    lines: List<SupplierOrderLineDataModel>,
    supplierIdentityTitle: String = "",
    onOpen: () -> Unit
) {
    val responseGaps = !order.status.isSupplierOrderClosed() &&
            SupplierOrderWithLinesDataModel(order, lines).hasSupplierResponseGapsForSupplierDesk()
    val readyToPack = order.isSupplierReadyToPackForSupplierDesk(lines)
    val issue = order.status == SupplierOrderStatusDataModel.IssueReported
    val accent = when {
        issue -> stateValues.ErrorColor
        responseGaps -> stateValues.BorderlineBadColor
        readyToPack -> stateValues.OkayColor
        else -> stateValues.AccentColor
    }
    val dueText = order.supplierDueAtMillis()
        ?.toStockDateInputText()
        ?.takeIf { it.isNotBlank() }
        ?: localizedStringResource(1663, "No promised date")
    val amountText = order.amount.supplierDeskMoneyText()
    val storeTitle = supplierDeskStoreTitle(order)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (issue || responseGaps) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (issue || responseGaps) accent else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accent),
                onClick = onOpen
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(23.dp),
                    url = if (readyToPack) stateValues.drawablePathIconSupplierDispatch else stateValues.drawablePathIconAppModeSupplier,
                    fallbackRes = if (readyToPack) stateValues.drawableResIconSupplierDispatch.value else stateValues.drawableResIconAppModeSupplier.value,
                    contentDescription = storeTitle,
                    tintColor = accent
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
                    text = "#${order.id.take(8)} • ${receiptUiDateTime(order.supplierDeskSortTime())}",
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = supplierOrderStatusTitle(order.status),
                color = accent,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(accent.copy(alpha = 0.10f))
                    .border(
                        stateValues.unfocusedBorderWidth,
                        accent.copy(alpha = 0.45f),
                        RoundedCornerShape(stateValues.cornerRadius)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }

        val detailChips = buildList {
            supplierIdentityTitle.trim().takeIf { it.isNotBlank() }?.let(::add)
            add("${localizedStringResource(1671, "Lines")}: ${lines.size}")
            add(dueText)
            amountText.takeIf { it.isNotBlank() }?.let(::add)
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            items(detailChips) { detail ->
                SupplierCatalogChip(text = detail)
            }
        }

        if (responseGaps || readyToPack || issue) {
            Text(
                text = when {
                    issue -> localizedStringResource(1637, "Resolve supplier issue")
                    responseGaps -> localizedStringResource(1691, "Response gaps")
                    else -> localizedStringResource(1689, "Ready to pack")
                },
                color = accent,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierOrdersFilterPanel(
    searchQuery: String,
    statusFilter: String,
    dueFilter: String,
    dueOptions: List<DropdownOption>,
    filteredCount: Int,
    totalCount: Int,
    expanded: Boolean,
    onSearchChanged: (String) -> Unit,
    onStatusChanged: (String) -> Unit,
    onDueChanged: (String) -> Unit,
    onExpandedChanged: (Boolean) -> Unit
) {
    val quickFilters = listOf(
        SupplierOrdersQuickFilterUiModel("open", localizedStringResource(1377, "Open")),
        SupplierOrdersQuickFilterUiModel("answer_gaps", localizedStringResource(1691, "Response gaps")),
        SupplierOrdersQuickFilterUiModel("ready_to_pack", localizedStringResource(1689, "Ready to pack")),
        SupplierOrdersQuickFilterUiModel(SupplierOrderStatusDataModel.InDelivery.name, localizedStringResource(1560, "In delivery")),
        SupplierOrdersQuickFilterUiModel("all", localizedStringResource(1378, "All"))
    )

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
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        SimpleTextInput(
            modifier = Modifier.fillMaxWidth(),
            value = searchQuery,
            placeholder = stateValues.stringSearchByAnyData,
            leadingIconPath = stateValues.drawablePathIconSearch,
            autoFocus = false,
            onValueChange = onSearchChanged
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            items(quickFilters, key = { it.id }) { filter ->
                SupplierOrdersQuickFilterChip(
                    filter = filter,
                    selected = statusFilter == filter.id,
                    onClick = { onStatusChanged(filter.id) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Text(
                text = "${localizedStringResource(254, "Orders")}: $filteredCount/$totalCount",
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                modifier = Modifier.weight(1f)
            )
            actionButton(
                modifier = Modifier.size(40.dp),
                text = "",
                iconPath = stateValues.drawablePathIconSettings,
                iconRes = stateValues.drawableResIconSettings.value,
                iconContentDescription = localizedStringResource(1274, "Filters"),
                confirmationRequired = false,
                autoLoading = false,
                enabledColor = if (expanded || dueFilter != "all") stateValues.AccentColor else stateValues.DisabledColor,
                onClick = { onExpandedChanged(!expanded) }
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
            ) {
                SimpleDropdownField(
                    title = localizedStringResource(1376, "Status filter"),
                    selectedId = statusFilter,
                    options = listOf(
                        DropdownOption("open", localizedStringResource(1377, "Open")),
                        DropdownOption("needs_attention", localizedStringResource(1371, "Needs attention")),
                        DropdownOption("answer_gaps", localizedStringResource(1691, "Response gaps")),
                        DropdownOption("ready_to_pack", localizedStringResource(1689, "Ready to pack")),
                        DropdownOption("all", localizedStringResource(1378, "All"))
                    ) + SupplierOrderStatusDataModel.entries
                        .filterNot { status -> status == SupplierOrderStatusDataModel.Draft }
                        .map { status -> DropdownOption(status.name, supplierOrderStatusTitle(status)) },
                    placeholder = localizedStringResource(1377, "Open"),
                    onSelected = onStatusChanged
                )
                SimpleDropdownField(
                    title = localizedStringResource(1667, "Delivery promise"),
                    selectedId = dueFilter,
                    options = dueOptions,
                    placeholder = localizedStringResource(1378, "All"),
                    onSelected = { onDueChanged(it.ifBlank { "all" }) }
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierOrdersWorkflowLinks() {
    if (stateValues.isNarrowScreen) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1338, "Catalog"),
                textSize = stateValues.smallTextSize,
                iconPath = stateValues.drawablePathIconSupplierCatalog,
                iconRes = stateValues.drawableResIconSupplierCatalog.value,
                confirmationRequired = false,
                autoLoading = false,
                onClick = { coroutineScope.launch { Navigation.goMain(NavigationScreenModel.Supplier.Catalog.Main) } }
            )
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1556, "Dispatch"),
                textSize = stateValues.smallTextSize,
                iconPath = stateValues.drawablePathIconSupplierDispatch,
                iconRes = stateValues.drawableResIconSupplierDispatch.value,
                confirmationRequired = false,
                autoLoading = false,
                onClick = { coroutineScope.launch { Navigation.goMain(NavigationScreenModel.Supplier.Dispatch.Main) } }
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1338, "Catalog"),
                iconPath = stateValues.drawablePathIconSupplierCatalog,
                iconRes = stateValues.drawableResIconSupplierCatalog.value,
                confirmationRequired = false,
                autoLoading = false,
                onClick = { coroutineScope.launch { Navigation.goMain(NavigationScreenModel.Supplier.Catalog.Main) } }
            )
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1556, "Dispatch"),
                iconPath = stateValues.drawablePathIconSupplierDispatch,
                iconRes = stateValues.drawableResIconSupplierDispatch.value,
                confirmationRequired = false,
                autoLoading = false,
                onClick = { coroutineScope.launch { Navigation.goMain(NavigationScreenModel.Supplier.Dispatch.Main) } }
            )
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1340, "Insights"),
                iconPath = stateValues.drawablePathIconSupplierDemandRadar,
                iconRes = stateValues.drawableResIconSupplierDemandRadar.value,
                confirmationRequired = false,
                autoLoading = false,
                onClick = { coroutineScope.launch { Navigation.goMain(NavigationScreenModel.Supplier.Analytics.Main) } }
            )
        }
    }
}
