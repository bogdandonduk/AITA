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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource

@Composable
internal fun AppConfiguration.SupplierContractsFilterPanel(
    searchQuery: String,
    filterId: String,
    sortId: String,
    resultCount: Int,
    totalCount: Int,
    expanded: Boolean,
    onSearchChanged: (String) -> Unit,
    onFilterChanged: (String) -> Unit,
    onSortChanged: (String) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
    onClear: () -> Unit
) {
    val quickFilters = listOf(
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CONTRACT_FILTER_ALL,
            localizedStringResource(2398, "All agreements")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CONTRACT_FILTER_PENDING,
            localizedStringResource(2399, "In negotiation")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CONTRACT_FILTER_WAITING_ME,
            localizedStringResource(2400, "Waiting for me")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CONTRACT_FILTER_WAITING_OTHER,
            localizedStringResource(2401, "Waiting for partner")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CONTRACT_FILTER_ACTIVE,
            localizedStringResource(1488, "Active contract")
        ),
        SupplierOrdersQuickFilterUiModel(
            SUPPLIER_CONTRACT_FILTER_DECLINED,
            localizedStringResource(1489, "Declined")
        )
    )
    val hasNonDefaultFilter = searchQuery.isNotBlank() ||
            filterId != SUPPLIER_CONTRACT_FILTER_ALL ||
            sortId != SUPPLIER_CONTRACT_SORT_ACTION

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
            SimpleTextInput(
                modifier = Modifier.weight(1f),
                value = searchQuery,
                placeholder = stateValues.stringSearchByAnyData,
                leadingIconPath = stateValues.drawablePathIconSearch,
                onValueChange = onSearchChanged
            )
            actionButton(
                modifier = Modifier.size(40.dp),
                fillMaxWidthIfTextPresent = false,
                text = "",
                iconPath = stateValues.drawablePathIconSettings,
                iconRes = stateValues.drawableResIconSettings.value,
                iconContentDescription = localizedStringResource(1274, "Filters"),
                confirmationRequired = false,
                autoLoading = false,
                enabledColor = if (expanded || sortId != SUPPLIER_CONTRACT_SORT_ACTION) {
                    stateValues.AccentColor
                } else {
                    stateValues.DisabledColor
                },
                onClick = { onExpandedChanged(!expanded) }
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            items(quickFilters, key = { it.id }) { filter ->
                SupplierOrdersQuickFilterChip(
                    filter = filter,
                    selected = filterId == filter.id,
                    onClick = { onFilterChanged(filter.id) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            Text(
                text = "${localizedStringResource(2343, "Results")}: $resultCount / $totalCount",
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                modifier = Modifier.weight(1f)
            )
            if (hasNonDefaultFilter) {
                actionButton(
                    modifier = Modifier.size(40.dp),
                    fillMaxWidthIfTextPresent = false,
                    text = "",
                    iconPath = stateValues.drawablePathIconCancel,
                    iconRes = stateValues.drawableResIconCancel.value,
                    iconContentDescription = localizedStringResource(2344, "Clear filters"),
                    confirmationRequired = false,
                    autoLoading = false,
                    onClick = onClear
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            SimpleDropdownField(
                title = localizedStringResource(2402, "Sort agreements"),
                selectedId = sortId,
                options = listOf(
                    DropdownOption(
                        SUPPLIER_CONTRACT_SORT_ACTION,
                        localizedStringResource(2367, "Action first")
                    ),
                    DropdownOption(
                        SUPPLIER_CONTRACT_SORT_RECENT,
                        localizedStringResource(2368, "Most recent")
                    ),
                    DropdownOption(
                        SUPPLIER_CONTRACT_SORT_PARTNER,
                        localizedStringResource(2403, "Partner A–Z")
                    ),
                    DropdownOption(
                        SUPPLIER_CONTRACT_SORT_REVISION,
                        localizedStringResource(2404, "Newest revision")
                    )
                ),
                placeholder = localizedStringResource(2367, "Action first"),
                onSelected = { onSortChanged(normalizedSupplierContractSort(it)) }
            )
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierContractsWorkflowLinks() {
    val coroutineScope = rememberCoroutineScope()

    @Composable
    fun WorkflowButton(
        modifier: Modifier,
        text: String,
        iconPath: String,
        iconRes: DrawableResource?,
        onClick: suspend () -> Unit
    ) {
        actionButton(
            modifier = modifier,
            text = text,
            iconPath = iconPath,
            iconRes = iconRes,
            confirmationRequired = false,
            autoLoading = false,
            onClick = { coroutineScope.launch { onClick() } }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            WorkflowButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(254, "Orders"),
                iconPath = stateValues.drawablePathIconAppModeSupplier,
                iconRes = stateValues.drawableResIconAppModeSupplier.value
            ) {
                seedSupplierOrdersInboxNavigation()
                Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
            }
            WorkflowButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(1453, "Partner stores"),
                iconPath = stateValues.drawablePathIconSupplierPartners,
                iconRes = stateValues.drawableResIconSupplierPartners.value
            ) {
                seedSupplierCustomersNavigation()
                Navigation.goMain(NavigationScreenModel.Supplier.Customers.Main)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            WorkflowButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(1338, "Catalog"),
                iconPath = stateValues.drawablePathIconSupplierCatalog,
                iconRes = stateValues.drawableResIconSupplierCatalog.value
            ) {
                seedSupplierCatalogNavigation()
                Navigation.goMain(NavigationScreenModel.Supplier.Catalog.Main)
            }
            WorkflowButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(1373, "Dispatch"),
                iconPath = stateValues.drawablePathIconSupplierDispatch,
                iconRes = stateValues.drawableResIconSupplierDispatch.value
            ) {
                seedSupplierDispatchNavigation()
                Navigation.goMain(NavigationScreenModel.Supplier.Dispatch.Main)
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierContractRelationshipLinks(
    item: SupplierContractWorkspaceUiModel
) {
    val coroutineScope = rememberCoroutineScope()
    val contract = item.contract
    val relationshipSearch = contract.storePublicIdSnapshot
        .ifBlank {
            contract.storeNameSnapshot.visibleLocalizedString(stateValues.appLanguage, "")
        }
        .ifBlank { contract.storeId }
    val catalogSearch = contract.priceTerms
        .firstOrNull { it.isActive }
        ?.goodsItemNameSnapshot
        .orEmpty()
        .visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { contract.goodsItemIds.firstOrNull().orEmpty() }
        .ifBlank { relationshipSearch }

    SupplierContractSectionCard(
        title = localizedStringResource(2441, "Continue this relationship"),
        iconPath = stateValues.drawablePathIconSupplierPartners,
        iconRes = stateValues.drawableResIconSupplierPartners.value
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actionButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(254, "Orders"),
                iconPath = stateValues.drawablePathIconAppModeSupplier,
                iconRes = stateValues.drawableResIconAppModeSupplier.value,
                confirmationRequired = false,
                autoLoading = false,
                onClick = {
                    coroutineScope.launch {
                        seedSupplierOrdersInboxNavigation(searchQuery = relationshipSearch)
                        Navigation.goMain(NavigationScreenModel.Supplier.Orders.Main)
                    }
                }
            )
            actionButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(1453, "Partner stores"),
                iconPath = stateValues.drawablePathIconSupplierPartners,
                iconRes = stateValues.drawableResIconSupplierPartners.value,
                confirmationRequired = false,
                autoLoading = false,
                onClick = {
                    coroutineScope.launch {
                        seedSupplierCustomersNavigation(searchQuery = relationshipSearch)
                        Navigation.goMain(NavigationScreenModel.Supplier.Customers.Main)
                    }
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            actionButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(1338, "Catalog"),
                iconPath = stateValues.drawablePathIconSupplierCatalog,
                iconRes = stateValues.drawableResIconSupplierCatalog.value,
                confirmationRequired = false,
                autoLoading = false,
                onClick = {
                    coroutineScope.launch {
                        seedSupplierCatalogNavigation(searchQuery = catalogSearch)
                        Navigation.goMain(NavigationScreenModel.Supplier.Catalog.Main)
                    }
                }
            )
            actionButton(
                modifier = Modifier.weight(1f),
                text = localizedStringResource(1373, "Dispatch"),
                iconPath = stateValues.drawablePathIconSupplierDispatch,
                iconRes = stateValues.drawableResIconSupplierDispatch.value,
                confirmationRequired = false,
                autoLoading = false,
                onClick = {
                    coroutineScope.launch {
                        seedSupplierDispatchNavigation(searchQuery = relationshipSearch)
                        Navigation.goMain(NavigationScreenModel.Supplier.Dispatch.Main)
                    }
                }
            )
        }
    }
}

private fun AppConfiguration.supplierContractWorkspaceAccent(item: SupplierContractWorkspaceUiModel) = when {
    item.waitsForMe -> stateValues.BorderlineBadColor
    item.waitsForOtherSide -> stateValues.AccentColor
    item.isActiveAgreement -> stateValues.OkayColor
    item.isDeclined -> stateValues.DisabledColor
    else -> stateValues.IconTintColor
}

@Composable
internal fun AppConfiguration.SupplierContractCompactCard(
    item: SupplierContractWorkspaceUiModel,
    actorSide: String,
    onOpen: () -> Unit
) {
    val contract = item.contract
    val accent = supplierContractWorkspaceAccent(item)
    val secondaryLine = listOfNotNull(
        supplierContractScopeTitle(contract.scopeType).takeIf { it.isNotBlank() },
        supplierContractStatusTitle(contract.status).takeIf { it.isNotBlank() },
        localizedStringResource(2405, "Revision") + " ${contract.revision}"
    ).joinToString(" • ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                if (item.waitsForMe) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                if (item.waitsForMe) accent else stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .aitaClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = stateValues.AccentColor),
                onClick = onOpen
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
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(27.dp),
                    url = stateValues.drawablePathIconSupplierContracts,
                    fallbackRes = stateValues.drawableResIconSupplierContracts.value,
                    contentDescription = supplierVisibleContractTitle(contract),
                    tintColor = accent
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = supplierVisibleContractTitle(contract),
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.partnerTitle,
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.partnerSubtitle.takeIf { it.isNotBlank() && it != item.partnerTitle }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (item.latestActivityMillis > 0L) {
                Text(
                    text = receiptUiDateTime(item.latestActivityMillis),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1
                )
            }
        }

        Text(
            text = secondaryLine,
            color = accent,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = supplierVisibleContractSummary(contract),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.textSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            item {
                SupplierCatalogChip(text = supplierContractActionHint(contract, actorSide))
            }
            if (item.selectedGoodsCount > 0) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1510, "Select goods")}: ${item.selectedGoodsCount}"
                    )
                }
            }
            if (item.activePriceTermCount > 0) {
                item {
                    SupplierCatalogChip(
                        text = "${localizedStringResource(1506, "Price lines")}: ${item.activePriceTermCount}"
                    )
                }
            }
            if (item.blocksSupply) {
                item {
                    SupplierCatalogChip(text = localizedStringResource(1528, "This contract blocks supply until accepted"))
                }
            }
        }

        actionButton(
            modifier = Modifier.fillMaxWidth(),
            text = localizedStringResource(2406, "Open agreement"),
            iconPath = stateValues.drawablePathIconSupplierContracts,
            iconRes = stateValues.drawableResIconSupplierContracts.value,
            confirmationRequired = false,
            autoLoading = false,
            onClick = onOpen
        )
    }
}

@Composable
internal fun AppConfiguration.SupplierContractEditorCard(
    actorSide: String,
    existingContract: SupplierPartnershipContractDataModel?,
    partners: List<SupplierContractPartnerUiModel>,
    goodsOptions: List<SupplierContractGoodsUiModel>,
    onClose: () -> Unit,
    onSaved: (SupplierPartnershipContractDataModel) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val editorKey = existingContract?.let { "${it.id}:${it.revision}" } ?: "new"
    val existingPartnerKey = existingContract
        ?.let { supplierContractRelationshipKey(it.storeId, it.supplierId) }
        ?.let { (storeId, supplierId) -> "$storeId|$supplierId" }
        .orEmpty()

    var selectedPartnerKey by remember(editorKey) {
        mutableStateOf(existingPartnerKey)
    }
    var selectedScope by remember(editorKey) {
        mutableStateOf(existingContract?.scopeType ?: SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP)
    }
    var selectedGoodsIds by remember(editorKey) {
        mutableStateOf(
            normalizedSupplierContractGoodsIds(
                existingContract?.let { contract ->
                    contract.goodsItemIds + contract.priceTerms
                        .filter { it.isActive }
                        .map { it.goodsItemId }
                }.orEmpty()
            )
        )
    }
    var title by remember(editorKey, stateValues.appLanguage) {
        mutableStateOf(
            existingContract?.title?.takeIf { it.isNotEmpty() }
                ?: listOf(
                    LocalizedStringDataModel(
                        "main",
                        localizedStringResource(1479, "Supplier contracts")
                    )
                )
        )
    }
    var summary by remember(editorKey, stateValues.appLanguage) {
        mutableStateOf(
            existingContract?.summary?.takeIf { it.isNotEmpty() }
                ?: listOf(
                    LocalizedStringDataModel(
                        "main",
                        localizedStringResource(1516, "Store requirements / supplier terms")
                    )
                )
        )
    }
    var customTerms by remember(editorKey, stateValues.appLanguage) {
        mutableStateOf(
            existingContract?.customTerms?.takeIf { it.isNotEmpty() }
                ?: emptyLocalizedItemForCurrentLanguage()
        )
    }
    var deliverySchedule by remember(editorKey, stateValues.appLanguage) {
        mutableStateOf(
            existingContract?.deliverySchedule?.takeIf { it.isNotEmpty() }
                ?: emptyLocalizedItemForCurrentLanguage()
        )
    }
    var paymentSchedule by remember(editorKey, stateValues.appLanguage) {
        mutableStateOf(
            existingContract?.paymentSchedule?.takeIf { it.isNotEmpty() }
                ?: emptyLocalizedItemForCurrentLanguage()
        )
    }
    var conditions by remember(editorKey) {
        mutableStateOf(
            existingContract?.conditions?.takeIf { it.isNotEmpty() }
                ?: listOf(defaultMarginLimitStockCondition("30").toStoredStockCondition())
        )
    }
    var goodsSearchQuery by remember(editorKey) { mutableStateOf("") }
    var saving by remember(editorKey) { mutableStateOf(false) }
    var resultMessage by remember(editorKey) { mutableStateOf("") }
    var resultPositive by remember(editorKey) { mutableStateOf(false) }

    LaunchedEffect(editorKey, partners.map { it.key }) {
        val partnerKeys = partners.map { it.key }.toSet()
        val nextPartnerKey = when {
            existingPartnerKey.isNotBlank() -> existingPartnerKey
            selectedPartnerKey in partnerKeys -> selectedPartnerKey
            partners.size == 1 -> partners.single().key
            else -> ""
        }
        if (selectedPartnerKey != nextPartnerKey) {
            selectedPartnerKey = nextPartnerKey
            selectedGoodsIds = emptySet()
            goodsSearchQuery = ""
            resultMessage = ""
        }
    }

    val selectedPartner = partners.firstOrNull { it.key == selectedPartnerKey }
        ?: existingContract?.let { contract ->
            val key = existingPartnerKey
            SupplierContractPartnerUiModel(
                key = key,
                storeId = contract.storeId.trim().lowercase(),
                supplierId = contract.supplierId.trim().lowercase(),
                title = if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) {
                    contract.supplierNameSnapshot.visibleLocalizedString(
                        stateValues.appLanguage,
                        contract.supplierId.take(12)
                    )
                } else {
                    contract.storeNameSnapshot.visibleLocalizedString(
                        stateValues.appLanguage,
                        contract.storePublicIdSnapshot.ifBlank { contract.storeId.take(12) }
                    )
                },
                subtitle = if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) {
                    contract.storeNameSnapshot.visibleLocalizedString(
                        stateValues.appLanguage,
                        contract.storePublicIdSnapshot
                    )
                } else {
                    contract.supplierNameSnapshot.visibleLocalizedString(
                        stateValues.appLanguage,
                        contract.supplierId.take(12)
                    )
                }
            )
        }
    val selectableGoods = goodsOptions
        .filter { option ->
            selectedPartner?.let { partner ->
                option.storeId.equals(partner.storeId, ignoreCase = true) &&
                        option.supplierId.equals(partner.supplierId, ignoreCase = true)
            } ?: false
        }
        .distinctBy { it.goodsItemId.trim().lowercase() }
    val normalizedGoodsSearch = goodsSearchQuery.trim().lowercase()
    val visibleGoods = selectableGoods
        .filter { goods ->
            normalizedGoodsSearch.isBlank() || buildString {
                append(goods.title).append(' ')
                append(goods.subtitle).append(' ')
                append(goods.goodsItemId).append(' ')
                append(goods.priceText).append(' ')
            }.lowercase().contains(normalizedGoodsSearch)
        }
        .sortedWith(
            compareByDescending<SupplierContractGoodsUiModel> {
                it.goodsItemId.trim().lowercase() in selectedGoodsIds
            }.thenBy { it.title.lowercase() }
        )
    val selectedPriceTerms = remember(
        existingContract,
        selectableGoods,
        selectedGoodsIds,
        deliverySchedule,
        summary
    ) {
        buildSupplierContractDraftPriceTerms(
            existingContract = existingContract,
            selectableGoods = selectableGoods,
            selectedGoodsIds = selectedGoodsIds,
            deliverySchedule = deliverySchedule,
            summary = summary
        )
    }

    fun validationMessage(): String? = when {
        selectedPartner == null -> localizedStringResource(2407, "Choose a partner before sending the proposal")
        selectedScope == SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM && selectedGoodsIds.size != 1 ->
            localizedStringResource(2412, "Choose exactly one product for an item agreement")
        selectedScope == SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP && selectedGoodsIds.isEmpty() ->
            localizedStringResource(2413, "Choose at least one product for a group agreement")
        else -> null
    }

    fun saveContract() {
        if (saving) return
        val partner = selectedPartner ?: return
        val normalizedScope = when (selectedScope) {
            SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM -> SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM
            SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP -> SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP
            else -> SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP
        }

        saving = true
        resultMessage = ""
        resultPositive = false

        upsertSupplierContract(
            SupplierPartnershipContractDataModel(
                id = existingContract?.id.orEmpty(),
                storeId = partner.storeId,
                supplierId = partner.supplierId,
                authorUserId = existingContract?.authorUserId
                    .orEmpty()
                    .ifBlank { stateValues.userAccount?.id.orEmpty() },
                lastEditorUserId = stateValues.userAccount?.id.orEmpty(),
                authorSide = actorSide,
                scopeType = normalizedScope,
                goodsItemIds = if (normalizedScope == SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP) {
                    emptyList()
                } else {
                    selectedGoodsIds.sorted()
                },
                title = title,
                summary = summary,
                conditions = conditions,
                customTerms = customTerms,
                deliverySchedule = deliverySchedule,
                paymentSchedule = paymentSchedule,
                priceTerms = selectedPriceTerms,
                status = existingContract?.status ?: SUPPLIER_CONTRACT_STATUS_PENDING_STORE,
                revision = existingContract?.revision ?: 0,
                createdAtMillis = existingContract?.createdAtMillis ?: 0L,
                updatedAtMillis = existingContract?.updatedAtMillis ?: 0L,
                isActive = true
            )
        ) { result ->
            coroutineScope.launch {
                saving = false
                when (result) {
                    is DataState.Success -> {
                        resultPositive = true
                        resultMessage = localizedStringResource(2423, "Proposal sent")
                        onSaved(result.payload)
                    }
                    is DataState.Empty -> {
                        resultPositive = false
                        resultMessage = result.message.orEmpty().visibleLocalizedString(
                            stateValues.appLanguage,
                            localizedStringResource(2409, "Could not save supplier contract")
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.focusedBorderWidth,
                stateValues.AccentColor,
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
                    .size(44.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CpImage(
                    modifier = Modifier.size(27.dp),
                    url = stateValues.drawablePathIconSupplierContracts,
                    fallbackRes = stateValues.drawableResIconSupplierContracts.value,
                    contentDescription = localizedStringResource(1479, "Supplier contracts"),
                    tintColor = stateValues.AccentColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = existingContract?.let {
                        localizedStringResource(1495, "Counter / edit proposal")
                    } ?: localizedStringResource(1521, "New proposal"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedStringResource(
                        2414,
                        "Editing creates a new revision and asks the other side to accept it."
                    ),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
        }

        if (partners.isEmpty() && existingContract == null) {
            MessageText(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(
                    1530,
                    "No partner store yet. Supplier contracts appear after at least one store order or saved supplier link."
                )
            )
        } else {
            if (existingContract == null) {
                SimpleDropdownField(
                    title = if (actorSide == SUPPLIER_CONTRACT_SIDE_STORE) {
                        localizedStringResource(1509, "Select supplier")
                    } else {
                        localizedStringResource(1508, "Select partner store")
                    },
                    selectedId = selectedPartnerKey,
                    options = partners.map { DropdownOption(it.key, it.title, it.subtitle) },
                    placeholder = stateValues.stringSearchByAnyData,
                    onSelected = { key ->
                        selectedPartnerKey = key
                        selectedGoodsIds = emptySet()
                        goodsSearchQuery = ""
                        resultMessage = ""
                    }
                )
            } else {
                SupplierContractPartnerSummary(
                    title = selectedPartner?.title.orEmpty(),
                    subtitle = selectedPartner?.subtitle.orEmpty()
                )
            }

            SimpleDropdownField(
                title = localizedStringResource(1507, "Contract scope"),
                selectedId = selectedScope,
                options = listOf(
                    DropdownOption(
                        SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP,
                        localizedStringResource(1491, "Partnership-wide"),
                        localizedStringResource(2415, "Applies to the whole Store–Supplier relationship")
                    ),
                    DropdownOption(
                        SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM,
                        localizedStringResource(1492, "Goods item"),
                        localizedStringResource(2416, "Applies to one selected product")
                    ),
                    DropdownOption(
                        SUPPLIER_CONTRACT_SCOPE_GOODS_GROUP,
                        localizedStringResource(1493, "Goods group"),
                        localizedStringResource(2417, "Applies to several selected products")
                    )
                ),
                placeholder = localizedStringResource(1507, "Contract scope"),
                onSelected = { scope ->
                    selectedScope = scope
                    if (scope == SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM && selectedGoodsIds.size > 1) {
                        selectedGoodsIds = selectedGoodsIds.firstOrNull()?.let(::setOf).orEmpty()
                    }
                    resultMessage = ""
                }
            )

            if (selectedScope != SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP) {
                Text(
                    text = localizedStringResource(2418, "Products covered by this agreement"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.textSize,
                    fontWeight = FontWeight.Bold
                )
                SimpleTextInput(
                    modifier = Modifier.fillMaxWidth(),
                    value = goodsSearchQuery,
                    placeholder = stateValues.stringSearchByAnyData,
                    leadingIconPath = stateValues.drawablePathIconSearch,
                    onValueChange = { goodsSearchQuery = it }
                )
                Text(
                    text = "${localizedStringResource(2419, "Selected")}: ${selectedGoodsIds.size}",
                    color = if (selectedGoodsIds.isEmpty()) {
                        stateValues.BorderlineBadColor
                    } else {
                        stateValues.OkayColor
                    },
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )

                when {
                    selectableGoods.isEmpty() -> {
                        MessageText(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(
                                2420,
                                "No products are connected to this relationship yet. Add an order or a saved offer first."
                            )
                        )
                    }
                    visibleGoods.isEmpty() -> {
                        MessageText(
                            modifier = Modifier.fillMaxWidth(),
                            text = localizedStringResource(2421, "No product matches this search")
                        )
                    }
                    else -> {
                        visibleGoods.forEach { goods ->
                            val goodsId = goods.goodsItemId.trim().lowercase()
                            val selected = goodsId in selectedGoodsIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                                    .background(
                                        if (selected) {
                                            stateValues.AccentColor.copy(alpha = 0.08f)
                                        } else {
                                            stateValues.BackgroundColor.softAppBackgroundColor()
                                        }
                                    )
                                    .border(
                                        if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                                        if (selected) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                                        RoundedCornerShape(stateValues.cornerRadius)
                                    )
                                    .aitaClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(color = stateValues.AccentColor)
                                    ) {
                                        selectedGoodsIds = if (selected) {
                                            selectedGoodsIds - goodsId
                                        } else if (selectedScope == SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM) {
                                            setOf(goodsId)
                                        } else {
                                            selectedGoodsIds + goodsId
                                        }
                                        resultMessage = ""
                                    }
                                    .padding(stateValues.marginTextField),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                            ) {
                                AitaRoundCheckbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        selectedGoodsIds = if (checked) {
                                            if (selectedScope == SUPPLIER_CONTRACT_SCOPE_GOODS_ITEM) {
                                                setOf(goodsId)
                                            } else {
                                                selectedGoodsIds + goodsId
                                            }
                                        } else {
                                            selectedGoodsIds - goodsId
                                        }
                                        resultMessage = ""
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = goods.title,
                                        color = stateValues.TextColor,
                                        fontSize = stateValues.textSize,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = listOf(
                                            goods.subtitle,
                                            goods.priceText,
                                            goods.quantityText
                                        ).filter { it.isNotBlank() }.joinToString(" • "),
                                        color = stateValues.PlaceholderTextColor,
                                        fontSize = stateValues.smallTextSize,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedPriceTerms.isNotEmpty()) {
                Text(
                    text = localizedStringResource(
                        2422,
                        "Existing product price lines will stay attached to this partnership agreement."
                    ),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }

            LocalizedStringListEditor(
                title = localizedStringResource(1522, "Proposal title"),
                values = title,
                onChanged = { title = it; resultMessage = "" }
            )
            LocalizedStringListEditor(
                title = localizedStringResource(1523, "Short summary"),
                values = summary,
                onChanged = { summary = it; resultMessage = "" }
            )
            StockConditionListEditor(
                title = localizedStringResource(1503, "Contract terms"),
                values = conditions,
                onChanged = { conditions = it; resultMessage = "" }
            )
            LocalizedStringListEditor(
                title = localizedStringResource(1524, "Custom written terms"),
                values = customTerms,
                onChanged = { customTerms = it; resultMessage = "" }
            )
            LocalizedStringListEditor(
                title = localizedStringResource(1504, "Delivery schedule"),
                values = deliverySchedule,
                onChanged = { deliverySchedule = it; resultMessage = "" }
            )
            LocalizedStringListEditor(
                title = localizedStringResource(1505, "Payment schedule"),
                values = paymentSchedule,
                onChanged = { paymentSchedule = it; resultMessage = "" }
            )

            if (selectedPriceTerms.isNotEmpty()) {
                SupplierContractSectionCard(
                    title = localizedStringResource(1506, "Price lines"),
                    iconPath = stateValues.drawablePathIconFinances,
                    iconRes = stateValues.drawableResIconFinances.value
                ) {
                    selectedPriceTerms.forEach { term ->
                        SupplierContractPriceTermRow(term)
                    }
                }
            }

            resultMessage.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    color = if (resultPositive) stateValues.OkayColor else stateValues.ErrorColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold
                )
            }

            val currentValidationMessage = validationMessage()
            if (stateValues.isNarrowScreen) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    actionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stateValues.stringCancel,
                        iconPath = stateValues.drawablePathIconCancel,
                        enabledColor = stateValues.DisabledColor,
                        confirmationRequired = false,
                        autoLoading = false,
                        enabled = !saving,
                        onClick = onClose
                    )
                    SupplierContractSaveButton(
                        saving = saving,
                        enabled = currentValidationMessage == null && !saving,
                        validationMessage = currentValidationMessage,
                        onSave = ::saveContract
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
                ) {
                    actionButton(
                        modifier = Modifier.weight(1f),
                        text = stateValues.stringCancel,
                        iconPath = stateValues.drawablePathIconCancel,
                        enabledColor = stateValues.DisabledColor,
                        confirmationRequired = false,
                        autoLoading = false,
                        enabled = !saving,
                        onClick = onClose
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        SupplierContractSaveButton(
                            saving = saving,
                            enabled = currentValidationMessage == null && !saving,
                            validationMessage = currentValidationMessage,
                            onSave = ::saveContract
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppConfiguration.SupplierContractSaveButton(
    saving: Boolean,
    enabled: Boolean,
    validationMessage: String?,
    onSave: () -> Unit
) {
    actionButton(
        modifier = Modifier.fillMaxWidth(),
        text = localizedStringResource(1515, "Save and send proposal"),
        loadingText = localizedStringResource(2424, "Sending proposal…"),
        iconPath = stateValues.drawablePathIconSupplierContracts,
        iconRes = stateValues.drawableResIconSupplierContracts.value,
        enabled = enabled,
        loading = saving,
        autoLoading = false,
        confirmationRequired = true,
        onDisabledClick = {
            validationMessage?.let {
                postInAppNotification(it, NotificationType.Neutral, transient = true)
            }
        },
        onClick = onSave
    )
}

@Composable
private fun AppConfiguration.SupplierContractPartnerSummary(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor.softAppBackgroundColor())
            .border(
                stateValues.unfocusedBorderWidth,
                stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextField),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        CpImage(
            modifier = Modifier.size(25.dp),
            url = stateValues.drawablePathIconSupplierPartners,
            fallbackRes = stateValues.drawableResIconSupplierPartners.value,
            contentDescription = title,
            tintColor = stateValues.AccentColor
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AppConfiguration.SupplierContractDetail(
    item: SupplierContractWorkspaceUiModel,
    actorSide: String,
    onEdit: () -> Unit,
    onArchived: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val contract = item.contract
    var operation by remember(contract.id) { mutableStateOf<String?>(null) }
    var resultMessage by remember(contract.id) { mutableStateOf("") }
    var resultPositive by remember(contract.id) { mutableStateOf(false) }

    fun completeMutation(result: DataState<SupplierPartnershipContractDataModel>) {
        coroutineScope.launch {
            operation = null
            when (result) {
                is DataState.Success -> {
                    resultPositive = true
                    resultMessage = result.message.orEmpty().visibleLocalizedString(
                        stateValues.appLanguage,
                        localizedStringResource(2425, "Agreement updated")
                    )
                }
                is DataState.Empty -> {
                    resultPositive = false
                    resultMessage = result.message.orEmpty().visibleLocalizedString(
                        stateValues.appLanguage,
                        localizedStringResource(2411, "Could not update supplier contract")
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        SupplierContractOverviewCard(item, actorSide)
        SupplierContractAcceptanceCard(contract)

        val customTerms = contract.customTerms.visibleLocalizedString(stateValues.appLanguage, "")
        val delivery = contract.deliverySchedule.visibleLocalizedString(stateValues.appLanguage, "")
        val payment = contract.paymentSchedule.visibleLocalizedString(stateValues.appLanguage, "")
        if (
            contract.conditions.isNotEmpty() ||
            customTerms.isNotBlank() ||
            delivery.isNotBlank() ||
            payment.isNotBlank()
        ) {
            SupplierContractSectionCard(
                title = localizedStringResource(1503, "Contract terms"),
                iconPath = stateValues.drawablePathIconEdit,
                iconRes = stateValues.drawableResIconEdit.value
            ) {
                contract.conditions.forEach { raw ->
                    Text(
                        text = "• ${visibleStockConditionText(raw.toStockConditionDataModel())}",
                        color = stateValues.TextColor,
                        fontSize = stateValues.smallTextSize
                    )
                }
                customTerms.takeIf { it.isNotBlank() }?.let {
                    SupplierContractLabeledText(
                        label = localizedStringResource(1524, "Custom written terms"),
                        value = it
                    )
                }
                delivery.takeIf { it.isNotBlank() }?.let {
                    SupplierContractLabeledText(
                        label = localizedStringResource(1504, "Delivery schedule"),
                        value = it
                    )
                }
                payment.takeIf { it.isNotBlank() }?.let {
                    SupplierContractLabeledText(
                        label = localizedStringResource(1505, "Payment schedule"),
                        value = it
                    )
                }
            }
        }

        SupplierContractSectionCard(
            title = localizedStringResource(2418, "Products covered by this agreement"),
            iconPath = stateValues.drawablePathIconSupplierCatalog,
            iconRes = stateValues.drawableResIconSupplierCatalog.value
        ) {
            when {
                contract.scopeType == SUPPLIER_CONTRACT_SCOPE_PARTNERSHIP && contract.priceTerms.none { it.isActive } -> {
                    Text(
                        text = localizedStringResource(2415, "Applies to the whole Store–Supplier relationship"),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize
                    )
                }
                contract.priceTerms.any { it.isActive } -> {
                    contract.priceTerms.filter { it.isActive }.forEach { term ->
                        SupplierContractPriceTermRow(term)
                    }
                }
                contract.goodsItemIds.isNotEmpty() -> {
                    contract.goodsItemIds.forEach { goodsItemId ->
                        SupplierCatalogChip(text = goodsItemId.take(16))
                    }
                }
                else -> {
                    Text(
                        text = localizedStringResource(2426, "No product-specific price lines"),
                        color = stateValues.PlaceholderTextColor,
                        fontSize = stateValues.smallTextSize
                    )
                }
            }
        }

        resultMessage.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                color = if (resultPositive) stateValues.OkayColor else stateValues.ErrorColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold
            )
        }

        actionButton(
            modifier = Modifier.fillMaxWidth(),
            text = localizedStringResource(1495, "Counter / edit proposal"),
            subText = localizedStringResource(2414, "Editing creates a new revision and asks the other side to accept it."),
            iconPath = stateValues.drawablePathIconEdit,
            iconRes = stateValues.drawableResIconEdit.value,
            enabledColor = stateValues.AccentColor,
            enabled = operation == null,
            confirmationRequired = false,
            autoLoading = false,
            onClick = onEdit
        )

        if (contract.requiresAcceptanceFrom(actorSide)) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1496, "Accept contract"),
                loadingText = localizedStringResource(2427, "Accepting agreement…"),
                iconPath = stateValues.drawablePathIconCheck,
                iconRes = stateValues.drawableResIconCheck.value,
                enabledColor = stateValues.OkayColor,
                enabled = operation == null,
                loading = operation == "accept",
                autoLoading = false,
                confirmationRequired = true,
                onClick = {
                    operation = "accept"
                    resultMessage = ""
                    acceptSupplierContract(contract.id, contract.revision, ::completeMutation)
                }
            )
        } else if (contract.waitsForOtherContractSide(actorSide)) {
            MessageText(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1527, "Waiting for the other side"),
                subText = localizedStringResource(
                    2428,
                    "The partner must accept this exact revision before it becomes active."
                ),
                subTextSize = stateValues.smallTextSize
            )
        }

        if (contract.canBeDeclinedByContractParty()) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1497, "Decline contract"),
                loadingText = localizedStringResource(2429, "Declining proposal…"),
                iconPath = stateValues.drawablePathIconCancel,
                iconRes = stateValues.drawableResIconCancel.value,
                enabledColor = stateValues.ErrorColor,
                enabled = operation == null,
                loading = operation == "decline",
                autoLoading = false,
                confirmationRequired = true,
                onClick = {
                    operation = "decline"
                    resultMessage = ""
                    declineSupplierContract(contract.id, contract.revision, ::completeMutation)
                }
            )
        }

        if (contract.canBeArchivedByContractParty()) {
            actionButton(
                modifier = Modifier.fillMaxWidth(),
                text = localizedStringResource(1498, "Archive contract"),
                loadingText = localizedStringResource(2430, "Archiving agreement…"),
                iconPath = stateValues.drawablePathIconDelete,
                iconRes = stateValues.drawableResIconDelete.value,
                enabledColor = stateValues.DisabledColor,
                enabled = operation == null,
                loading = operation == "archive",
                autoLoading = false,
                confirmationRequired = true,
                onClick = {
                    operation = "archive"
                    resultMessage = ""
                    archiveSupplierContract(contract.id, contract.revision) { result ->
                        coroutineScope.launch {
                            operation = null
                            when (result) {
                                is DataState.Success -> {
                                    resultPositive = true
                                    resultMessage = result.message.orEmpty().visibleLocalizedString(
                                        stateValues.appLanguage,
                                        localizedStringResource(2431, "Agreement archived")
                                    )
                                    onArchived()
                                }
                                is DataState.Empty -> {
                                    resultPositive = false
                                    resultMessage = result.message.orEmpty().visibleLocalizedString(
                                        stateValues.appLanguage,
                                        localizedStringResource(2411, "Could not update supplier contract")
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun AppConfiguration.SupplierContractOverviewCard(
    item: SupplierContractWorkspaceUiModel,
    actorSide: String
) {
    val contract = item.contract
    val accent = supplierContractWorkspaceAccent(item)
    SupplierContractSectionCard(
        title = supplierVisibleContractTitle(contract),
        iconPath = stateValues.drawablePathIconSupplierContracts,
        iconRes = stateValues.drawableResIconSupplierContracts.value,
        accent = accent
    ) {
        Text(
            text = item.partnerTitle,
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        item.partnerSubtitle.takeIf { it.isNotBlank() && it != item.partnerTitle }?.let {
            Text(
                text = it,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = supplierVisibleContractSummary(contract),
            color = stateValues.TextColor,
            fontSize = stateValues.textSize
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            item { SupplierCatalogChip(text = supplierContractStatusTitle(contract.status)) }
            item { SupplierCatalogChip(text = supplierContractScopeTitle(contract.scopeType)) }
            item {
                SupplierCatalogChip(
                    text = localizedStringResource(2405, "Revision") + " ${contract.revision}"
                )
            }
            if (item.blocksSupply) {
                item {
                    SupplierCatalogChip(text = localizedStringResource(1528, "This contract blocks supply until accepted"))
                }
            }
        }
        Text(
            text = supplierContractActionHint(contract, actorSide),
            color = accent,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )
        if (item.latestActivityMillis > 0L) {
            Text(
                text = "${localizedStringResource(2432, "Last changed")}: ${receiptUiDateTime(item.latestActivityMillis)}",
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}

@Composable
private fun AppConfiguration.SupplierContractAcceptanceCard(
    contract: SupplierPartnershipContractDataModel
) {
    val storeAccepted = contract.status == SUPPLIER_CONTRACT_STATUS_ACTIVE ||
            contract.storeAcceptedAtMillis != null
    val supplierAccepted = contract.status == SUPPLIER_CONTRACT_STATUS_ACTIVE ||
            contract.supplierAcceptedAtMillis != null

    SupplierContractSectionCard(
        title = localizedStringResource(2433, "Acceptance progress"),
        iconPath = stateValues.drawablePathIconCheck,
        iconRes = stateValues.drawableResIconCheck.value
    ) {
        SupplierContractAcceptanceRow(
            title = localizedStringResource(2434, "Store acceptance"),
            accepted = storeAccepted,
            acceptedAtMillis = contract.storeAcceptedAtMillis
        )
        SupplierContractAcceptanceRow(
            title = localizedStringResource(2435, "Supplier acceptance"),
            accepted = supplierAccepted,
            acceptedAtMillis = contract.supplierAcceptedAtMillis
        )
    }
}

@Composable
private fun AppConfiguration.SupplierContractAcceptanceRow(
    title: String,
    accepted: Boolean,
    acceptedAtMillis: Long?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor.softAppBackgroundColor())
            .padding(stateValues.marginTextField),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        CpImage(
            modifier = Modifier.size(22.dp),
            url = if (accepted) stateValues.drawablePathIconCheck else stateValues.drawablePathIconResponse,
            fallbackRes = if (accepted) {
                stateValues.drawableResIconCheck.value
            } else {
                stateValues.drawableResIconResponse.value
            },
            contentDescription = title,
            tintColor = if (accepted) stateValues.OkayColor else stateValues.BorderlineBadColor
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (accepted) {
                    acceptedAtMillis?.takeIf { it > 0L }?.let(::receiptUiDateTime)
                        ?: localizedStringResource(2436, "Accepted")
                } else {
                    localizedStringResource(2437, "Not accepted yet")
                },
                color = if (accepted) stateValues.OkayColor else stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}

@Composable
private fun AppConfiguration.SupplierContractPriceTermRow(
    term: SupplierContractPriceTermDataModel
) {
    val title = term.goodsItemNameSnapshot
        .visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { term.goodsItemId.take(16) }
    val price = term.supplyPrice.supplierDeskMoneyText()
    val suggestedSalePrice = term.suggestedSalePrice.supplierDeskMoneyText()
    val quantityText = listOfNotNull(
        term.minOrderQuantity?.quantityText(stateValues.appLanguage)?.takeIf { it.isNotBlank() },
        term.packageQuantity?.quantityText(stateValues.appLanguage)?.takeIf { it.isNotBlank() }
    ).joinToString(" • ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor.softAppBackgroundColor())
            .padding(stateValues.marginTextField),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(quantityText, suggestedSalePrice)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                    .ifBlank { term.goodsItemId.take(16) },
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = price.ifBlank { localizedStringResource(2337, "Price missing") },
            color = if (price.isNotBlank()) stateValues.OkayColor else stateValues.BorderlineBadColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun AppConfiguration.SupplierContractLabeledText(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor.softAppBackgroundColor())
            .padding(stateValues.marginTextField),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize
        )
    }
}

@Composable
private fun AppConfiguration.SupplierContractSectionCard(
    title: String,
    iconPath: String,
    iconRes: DrawableResource?,
    accent: androidx.compose.ui.graphics.Color = stateValues.AccentColor,
    content: @Composable () -> Unit
) {
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
            CpImage(
                modifier = Modifier.size(23.dp),
                url = iconPath,
                fallbackRes = iconRes,
                contentDescription = title,
                tintColor = accent
            )
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        content()
    }
}
