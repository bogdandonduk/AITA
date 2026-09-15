// THIS IS CommonMainCompose.kt split slice: Navigation
@file:OptIn(ExperimentalTime::class, ExperimentalFoundationApi::class)
package kz.aita

import aita.composeapp.generated.resources.Res
import aita.composeapp.generated.resources._9_0
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import kotlin.random.Random
import kotlin.time.ExperimentalTime

internal fun shouldShowStockAddEditBack(
    isVeryFirstScreen: Boolean,
    editedGoodsItemId: String?
): Boolean = !isVeryFirstScreen || !editedGoodsItemId.isNullOrBlank()

@Composable
fun AppConfiguration.StockAddEditGoodsItemScreen() {
    val addEditState by NavigationScreenModel.Stock.AddEditGoodsItem.state.collectAsState()

    val editedId = addEditState[
        NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID
    ]

    val existing = stateValues.stock.orEmpty().find { it.id == editedId }
    val isEditingStockItem = !editedId.isNullOrBlank()

    val defaultCurrency = stateValues.globalAppConfiguration
        .countries
        .firstOrNull()
        ?.currencies
        ?.firstOrNull()
        ?.code
        ?: "KZT"

    val defaultMeasurementUnitId = stateValues.globalAppConfiguration
        .goodsItemsQuantityUnits
        .firstOrNull()
        ?.id
        ?: "0"

    val addStockItemSessionId = addEditState["stock_add_edit_add_session_id"].orEmpty()
    val draftStorageKey = remember(existing?.id, addStockItemSessionId, stateValues.userAccount?.id, stateValues.activeStoreId) {
        listOf(
            "stock-add-edit-goods-item",
            stateValues.userAccount?.id?.takeIf { it.isNotBlank() } ?: "anonymous",
            stateValues.activeStoreId ?: "no-store",
            existing?.id ?: "new:${addStockItemSessionId.ifBlank { "default" }}"
        ).joinToString(":")
    }

    fun newDraft(): StockAddEditDraft {
        val rememberedCategoryId = addEditState[
            NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_LAST_SELECTED_CATEGORY_ID
        ]?.takeIf { rememberedId ->
            rememberedId.isNotBlank() && stateValues.goodsCategories.orEmpty().any { it.id == rememberedId }
        }

        return StockAddEditDraft(
            barcodes = listOf(""),
            barcodeTypes = listOf(GOODS_ITEM_BARCODE_TYPE_STANDARD),
            name = emptyLocalizedItemForCurrentLanguage(),
            description = emptyLocalizedItemForCurrentLanguage(),
            measurementUnitId = defaultMeasurementUnitId,
            categoryIds = rememberedCategoryId?.let(::listOf) ?: emptyList(),
            salePrices = listOf(
                PriceDataModel(
                    price = "",
                    currency = defaultCurrency,
                    supplierId = ""
                )
            ),
            returnPrices = listOf(
                PriceDataModel(
                    price = "",
                    currency = defaultCurrency,
                    supplierId = ""
                )
            ),
            supplyPrices = listOf(
                PriceDataModel(
                    price = "",
                    currency = defaultCurrency,
                    supplierId = ""
                )
            ),
            wholesalePrices = listOf(
                PriceDataModel(
                    price = "",
                    currency = defaultCurrency,
                    supplierId = ""
                )
            ),
            wholesaleMinQuantityText = "",
            isQuickItem = false,
            note = "",
            noteLocalized = emptyLocalizedItemForCurrentLanguage()
        )
    }

    val stockAddEditDraftIdentity = existing?.id ?: "new:${stateValues.userAccount?.id.orEmpty()}:${stateValues.activeStoreId.orEmpty()}:${addStockItemSessionId.ifBlank { "default" }}"
    var draft by remember(stockAddEditDraftIdentity, defaultCurrency, defaultMeasurementUnitId) {
        mutableStateOf(
            existing?.toStockAddEditDraft() ?: newDraft()
        )
    }

    var showGlobalGoodsSheet by rememberSaveable(existing?.id ?: "new_stock_item:$addStockItemSessionId") {
        mutableStateOf(false)
    }
    var showParentStoreStockSheet by rememberSaveable(existing?.id ?: "new_stock_item:$addStockItemSessionId", stateValues.activeStoreId ?: "no_store") {
        mutableStateOf(false)
    }
    var stockAddEditUndoDraft by remember(existing?.id ?: "new_stock_item:$addStockItemSessionId") {
        mutableStateOf<StockAddEditDraft?>(null)
    }

    var persistentDraftLoaded by rememberSaveable(draftStorageKey) {
        mutableStateOf(false)
    }

    LaunchedEffect(draftStorageKey, existing?.id, stateValues.goodsCategories.orEmpty().size) {
        persistentDraftLoaded = false
        val restored = getPersistentUiDraftValue
            ?.invoke(draftStorageKey)
            ?.toPersistentStockAddEditDraftOrNull()
        if (restored != null && (existing == null || restored.id == existing.id)) {
            draft = restored
        } else if (existing == null && draft.categoryIds.isEmpty()) {
            val restoredRootCategoryId = getPersistentUiDraftValue
                ?.invoke(stockAddEditLastCategoryStorageKey("root"))
                ?.takeIf { id ->
                    id.isNotBlank() && stateValues.goodsCategories.orEmpty().any { it.id == id }
                }
            val restoredSelectedCategoryId = getPersistentUiDraftValue
                ?.invoke(stockAddEditLastCategoryStorageKey("selected"))
                ?.takeIf { id ->
                    id.isNotBlank() && stateValues.goodsCategories.orEmpty().any { it.id == id }
                }
                ?: restoredRootCategoryId

            restoredRootCategoryId?.let {
                NavigationScreenModel.Stock.AddEditGoodsItem.setState(
                    NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_LAST_SELECTED_ROOT_CATEGORY_ID to it
                )
            }
            restoredSelectedCategoryId?.let {
                NavigationScreenModel.Stock.AddEditGoodsItem.setState(
                    NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_LAST_SELECTED_CATEGORY_ID to it
                )
                draft = draft.copy(categoryIds = listOf(it))
            }
        }
        persistentDraftLoaded = true
    }

    LaunchedEffect(addEditState["stock_add_edit_global_template"], existing?.id, persistentDraftLoaded, stateValues.goodsCategories.orEmpty().size) {
        if (!persistentDraftLoaded || existing != null) return@LaunchedEffect
        val rawTemplate = addEditState["stock_add_edit_global_template"]?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        runCatching { jsonBase.decodeFromString<GenericGoodsItemDataModel>(rawTemplate) }
            .getOrNull()
            ?.let { template ->
                stockAddEditUndoDraft = draft
                draft = applyGlobalGoodsTemplateToDraft(draft, template)
                NavigationScreenModel.Stock.AddEditGoodsItem.removeState("stock_add_edit_global_template")
                postInAppNotification(
                    "${localizedStringResource(1175, "Template applied")}: ${template.visibleGlobalGoodsName(stateValues.appLanguage)}",
                    NotificationType.Positive,
                    transient = true
                )
            }
    }

    LaunchedEffect(draft, draftStorageKey, persistentDraftLoaded) {
        if (persistentDraftLoaded) {
            setPersistentUiDraftValue?.invoke(draftStorageKey, draft.toPersistentDraftStateString())
        }
    }

    suspend fun clearPersistentStockAddEditDraft() {
        setPersistentUiDraftValue?.invoke(draftStorageKey, null)
        localizedGroupEditorPersistentKey("$draftStorageKey:name")?.let { setPersistentUiDraftValue?.invoke(it, null) }
        localizedGroupEditorPersistentKey("$draftStorageKey:description")?.let { setPersistentUiDraftValue?.invoke(it, null) }
        localizedGroupEditorPersistentKey("$draftStorageKey:note")?.let { setPersistentUiDraftValue?.invoke(it, null) }
    }

    var selectedTabId by rememberSaveable(existing?.id ?: "new_stock_item:$addStockItemSessionId") {
        mutableStateOf(addEditState["stock_add_edit_selected_tab"] ?: "info")
    }

    LaunchedEffect(addEditState["stock_add_edit_selected_tab"]) {
        addEditState["stock_add_edit_selected_tab"]?.let {
            selectedTabId = it
        }
    }

    var returnPriceManuallyEdited by rememberSaveable(existing?.id ?: "new_stock_item:$addStockItemSessionId") {
        mutableStateOf(
            existing?.let {
                it.returnPrices
                    .map { price -> price.price to price.currency }
                    .toSet() != it.salePrices
                    .map { price -> price.price to price.currency }
                    .toSet()
            } ?: false
        )
    }

    val canPopStockScreen = !Navigation.Stock.isVeryFirstScreen(stateValues.isNarrowScreen)
    val showStockEditorBack = shouldShowStockAddEditBack(
        isVeryFirstScreen = !canPopStockScreen,
        editedGoodsItemId = editedId
    )
    // A process-restored request is no longer running, so loading state must never be persisted.
    var isSavingStockItem by remember(stockAddEditDraftIdentity) { mutableStateOf(false) }
    var stockSaveError by remember(stockAddEditDraftIdentity) { mutableStateOf<String?>(null) }

    suspend fun resetStockEditorToFreshAdd(clearCurrentDraft: Boolean) {
        // Stop the persistence effect before rotating the editor identity. Otherwise a recomposition can
        // briefly write the just-cleared edit draft back under the old item key.
        persistentDraftLoaded = false
        if (clearCurrentDraft) clearPersistentStockAddEditDraft()
        NavigationScreenModel.Stock.AddEditGoodsItem.removeState(
            NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID
        )
        NavigationScreenModel.Stock.AddEditGoodsItem.removeState("stock_add_edit_global_template")
        NavigationScreenModel.Stock.AddEditGoodsItem.removeState("stock_add_edit_start_add_batch")
        NavigationScreenModel.Stock.AddEditGoodsItem.setState("stock_add_edit_selected_tab" to "info")
        NavigationScreenModel.Stock.AddEditGoodsItem.setState(
            "stock_add_edit_add_session_id" to
                "${getCurrentTimeMillis()}_${Random.nextInt(0, Int.MAX_VALUE)}"
        )
        selectedTabId = "info"
        draft = newDraft()
        stockAddEditUndoDraft = null
        stockSaveError = null
        isSavingStockItem = false
    }

    suspend fun leaveStockEditor() {
        if (canPopStockScreen) {
            Navigation.Stock.pop(stateValues.isNarrowScreen)
            NavigationScreenModel.Stock.AddEditGoodsItem.removeState(
                NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID
            )
        } else if (isEditingStockItem) {
            // Desktop keeps Add/Edit as the canonical right-pane root. A restored edit therefore
            // cannot be popped; turn that root back into a brand-new Add session instead.
            resetStockEditorToFreshAdd(clearCurrentDraft = true)
        }
    }

    fun stockSaveFailureText(state: DataState<GoodsItemDataModel>): String =
        state.message
            ?.extractLocalizedString(stateValues.appLanguage)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: when (stateValues.appLanguage.lowercase()) {
                "ru" -> "Не удалось сохранить товар"
                "kk" -> "Тауарды сақтау мүмкін болмады"
                "ky" -> "Товарды сактоо мүмкүн болгон жок"
                else -> "Could not save stock item"
            }

    val activeStoreForParentStock = stateValues.stores.findStoreOrBranchForUi(stateValues.activeStoreId)
    val canPullFromParentStoreStock = !stateValues.activeStoreId.isNullOrBlank() && existing == null
    val stockAddEditGoodsItemIdForCounts = existing?.id.orEmpty()
    val stockAddEditBatchesCount = stateValues.stockBatches.orEmpty()
        .count { it.goodsItemId == stockAddEditGoodsItemIdForCounts && it.isActive }
    val stockAddEditSupplierPricesPayload by supplierGoodsPricesState.payload.collectAsState()
    val stockAddEditSupplierPricesCount = stockAddEditSupplierPricesPayload.orEmpty()
        .count { it.goodsItemId == stockAddEditGoodsItemIdForCounts && it.isActive }
    val stockAddEditSupplierOrdersPayload by supplierOrdersState.payload.collectAsState()
    val stockAddEditSupplierOrderLinesPayload by supplierOrderLinesState.payload.collectAsState()
    val stockAddEditSupplierOrdersCount = stockAddEditSupplierOrdersPayload.orEmpty()
        .count { order ->
            order.isActive && stockAddEditSupplierOrderLinesPayload.orEmpty().any { line ->
                line.goodsItemId == stockAddEditGoodsItemIdForCounts && line.orderId == order.id && line.isActive
            }
        }
    val stockItemHistoryPayload by stockItemHistoryState.payload.collectAsState()
    val stockAddEditHistoryCount = stockItemHistoryPayload.orEmpty().size.takeIf { existing != null }
    val activeStoreIdForStockAddEditPermissions = existing?.storeId?.takeIf { it.isNotBlank() } ?: stateValues.activeStoreId
    val canCreateStockItemHere = currentUserHasStorePermission(activeStoreIdForStockAddEditPermissions, STORE_PERMISSION_STOCK_ITEM_CREATE)
    val canEditStockItemHere = currentUserHasStorePermission(activeStoreIdForStockAddEditPermissions, STORE_PERMISSION_STOCK_ITEM_EDIT)
    val canEditCoreStockItem = if (existing == null) canCreateStockItemHere else canEditStockItemHere
    val canManageStockPromotions = canEditCoreStockItem || currentUserHasStorePermission(activeStoreIdForStockAddEditPermissions, STORE_PERMISSION_STOCK_PROMOTIONS_MANAGE)
    val canWorkWithStockBatches = existing != null && listOf(
        STORE_PERMISSION_STOCK_BATCH_CREATE,
        STORE_PERMISSION_STOCK_BATCH_EDIT,
        STORE_PERMISSION_STOCK_BATCH_DELETE,
        STORE_PERMISSION_STOCK_BATCH_MOVE,
        STORE_PERMISSION_STOCK_BATCH_TRANSFER_DECIDE,
        STORE_PERMISSION_STOCK_BATCH_SET_ACTIVE_SHELF
    ).any { permission -> currentUserHasStorePermission(activeStoreIdForStockAddEditPermissions, permission) }
    val canWorkWithSupplierPrices = existing != null && (
        currentUserHasStorePermission(activeStoreIdForStockAddEditPermissions, STORE_PERMISSION_SUPPLIER_PRICES_MANAGE) ||
            currentUserCanViewSuppliers(activeStoreIdForStockAddEditPermissions)
        )
    val canWorkWithSupplierOrders = existing != null && currentUserCanViewSupplierOrders(activeStoreIdForStockAddEditPermissions)
    val canViewStockItemHistory = existing != null && currentUserCanViewStockHistory(activeStoreIdForStockAddEditPermissions)

    LaunchedEffect(existing?.id, activeStoreIdForStockAddEditPermissions, canViewStockItemHistory) {
        val storeId = activeStoreIdForStockAddEditPermissions?.takeIf { it.isNotBlank() }
        val itemId = existing?.id?.takeIf { it.isNotBlank() }
        if (storeId != null && itemId != null && canViewStockItemHistory) {
            getStockItemHistory(storeId, itemId)
        }
    }

    val stockAddEditTrailingIcons = buildList<Triple<String, DrawableResource, () -> Unit>> {
        if (canEditCoreStockItem) stockAddEditUndoDraft?.let { previousDraft ->
            add(
                Triple(
                    undoTemplateIconPath(),
                    undoTemplateIconFallback()
                ) {
                    draft = previousDraft
                    stockAddEditUndoDraft = null
                    postInAppNotification(
                        localizedStringResource(1185, "Template reverted"),
                        NotificationType.Neutral,
                        transient = true
                    )
                }
            )
        }
        if (canEditCoreStockItem && canPullFromParentStoreStock) {
            add(
                Triple(
                    parentStoreStockIconPath(),
                    parentStoreStockIconFallback()
                ) {
                    stateValues.activeStoreId?.let { activeStoreId ->
                        showParentStoreStockSheet = true
                        refreshParentStoreStock(activeStoreId, limit = 32, appendToSharedState = false)
                    }
                }
            )
        }
        if (canEditCoreStockItem) {
            add(
                Triple(
                    globalGoodsIconPath(),
                    globalGoodsIconFallback()
                ) {
                    showGlobalGoodsSheet = true
                    refreshGenericGoodsItems(limit = 32, appendToSharedState = true)
                }
            )
        }
    }

    val tabs = buildList {
        if (canEditCoreStockItem) {
            add(
                StockAddEditTabContent(
                    id = "info",
                    title = localizedStringResource(252, "Info"),
                    iconPath = stateValues.drawablePathIconEdit
                )
            )
            add(
                StockAddEditTabContent(
                    id = "conditions",
                    title = localizedStringResource(609, "Conditions"),
                    iconPath = stateValues.drawablePathIconCheck,
                    count = draft.conditions.size
                )
            )
            add(
                StockAddEditTabContent(
                    id = "prices",
                    title = localizedStringResource(253, "Generic prices"),
                    iconPath = stateValues.drawablePathIconFinances
                )
            )
        }
        if (canEditCoreStockItem) add(StockAddEditTabContent(
            id = "marketplace", title = marketProductText("market.profile_tab"),
            iconPath = marketIconPath(148), iconRes = marketIconFallback(148)))
        if (canManageStockPromotions) {
            add(
                StockAddEditTabContent(
                    id = "promos",
                    title = localizedStringResource(920, "Promos"),
                    iconPath = stateValues.drawablePathIconPromos,
                    iconRes = stateValues.drawableResIconPromos.value,
                    count = draft.promotions.size
                )
            )
        }
        if (canWorkWithStockBatches) {
            add(
                StockAddEditTabContent(
                    id = "batches",
                    title = localizedStringResource(138, "Batches"),
                    iconPath = stateValues.drawablePathIconStock,
                    count = stockAddEditBatchesCount
                )
            )
        }
        if (canViewStockItemHistory) {
            add(
                StockAddEditTabContent(
                    id = "history",
                    title = localizedStringResource(1330, "History"),
                    iconPath = stateValues.drawablePathIconStockHistory,
                    iconRes = stateValues.drawableResIconStockHistory.value,
                    count = stockAddEditHistoryCount
                )
            )
        }
        if (canWorkWithSupplierPrices) {
            add(
                StockAddEditTabContent(
                    id = "supplier_prices",
                    title = localizedStringResource(203, "Supplier prices"),
                    iconPath = stateValues.drawablePathIconSuppliers,
                    count = stockAddEditSupplierPricesCount
                )
            )
        }
        if (canWorkWithSupplierOrders) {
            add(
                StockAddEditTabContent(
                    id = "orders",
                    title = localizedStringResource(254, "Orders"),
                    iconPath = stateValues.drawablePathIconTransactionSupply,
                    count = stockAddEditSupplierOrdersCount
                )
            )
        }
        if (isEmpty()) {
            add(
                StockAddEditTabContent(
                    id = "restricted",
                    title = localizedStringResource(665, "You do not have permission for this action"),
                    iconPath = stateValues.drawablePathIconSecurity
                )
            )
        }
    }
    val visibleSelectedTabId = selectedTabId.takeIf { selected -> tabs.any { it.id == selected } } ?: tabs.first().id

    LaunchedEffect(visibleSelectedTabId) {
        if (selectedTabId != visibleSelectedTabId) {
            selectedTabId = visibleSelectedTabId
            NavigationScreenModel.Stock.AddEditGoodsItem.setState("stock_add_edit_selected_tab" to visibleSelectedTabId)
        }
    }

    stateValues.activeStoreId?.let { activeStoreIdForParentSheet ->
        if (showParentStoreStockSheet) {
            ParentStoreStockSelectionBottomSheet(
                activeStoreId = activeStoreIdForParentSheet,
                draft = draft,
                existing = existing,
                onDismiss = { showParentStoreStockSheet = false },
                onApply = { parentItem ->
                    stockAddEditUndoDraft = draft
                    draft = parentItem.toParentStoreStockTemplateDraft(draft, existing)
                    showParentStoreStockSheet = false
                    postInAppNotification(
                        "${localizedStringResource(1216, "Parent item applied")}: ${parentItem.visibleParentStoreStockName(stateValues.appLanguage)}",
                        NotificationType.Positive,
                        transient = true
                    )
                }
            )
        }
    }

    if (showGlobalGoodsSheet) {
        GlobalGoodsSelectionBottomSheet(
            draft = draft,
            onDismiss = { showGlobalGoodsSheet = false },
            onApply = { genericItem ->
                stockAddEditUndoDraft = draft
                draft = applyGlobalGoodsTemplateToDraft(draft, genericItem)
                showGlobalGoodsSheet = false
                postInAppNotification(
                    "${localizedStringResource(1175, "Template applied")}: ${genericItem.visibleGlobalGoodsName(stateValues.appLanguage)}",
                    NotificationType.Positive,
                    transient = true
                )
            }
        )
    }

    AitaScreenColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(
                title = if (isEditingStockItem) stateValues.stringEditGoodsItem else stateValues.stringAddGoodsItem,
                iconPath = if (isEditingStockItem) stateValues.drawablePathIconEdit else stateValues.drawablePathIconAdd,
                trailingIcons = stockAddEditTrailingIcons,
                onBack = if (showStockEditorBack) {
                    {
                        coroutineScope.launch {
                            leaveStockEditor()
                        }
                    }
                } else null
            )
        }
    ) {
        StockAddEditTabs(
            modifier = Modifier
                .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.92f),
            selectedId = visibleSelectedTabId,
            tabs = tabs,
            onSelected = {
                selectedTabId = it
                coroutineScope.launch {
                    NavigationScreenModel.Stock.AddEditGoodsItem.setState("stock_add_edit_selected_tab" to it)
                }
            }
        )

        when (visibleSelectedTabId) {
            "marketplace" -> LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                item { StockMarketplaceEditor(draft, existing?.imagePaths.orEmpty(), Modifier.fillMaxWidth(), onDraftChanged = { draft = it }) }
            }
            "conditions" -> {
                StockAddEditConditionsTab(
                    modifier = Modifier.weight(1f),
                    draft = draft,
                    onDraftChanged = {
                        draft = it
                    }
                )
            }

            "prices" -> {
                StockAddEditPricesTab(
                    modifier = Modifier.weight(1f),
                    draft = draft,
                    defaultCurrency = defaultCurrency,
                    returnPriceManuallyEdited = returnPriceManuallyEdited,
                    onReturnPriceManuallyEditedChanged = {
                        returnPriceManuallyEdited = it
                    },
                    onDraftChanged = {
                        draft = it
                    }
                )
            }

            "promos" -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(stateValues.marginTextField)
                ) {
                    item {
                        StockPromotionListEditor(
                            promotions = draft.promotions,
                            onPromotionsChanged = { draft = draft.copy(promotions = it.sanitizedStockPromotions()) },
                            quantityUnit = stateValues.globalAppConfiguration.goodsItemsQuantityUnits
                                .find { unit -> unit.id == draft.measurementUnitId }
                        )

                        Spacer(modifier = Modifier.height(stateValues.screenHeight / 5))
                    }
                }
            }

            "batches" -> {
                StockAddEditBatchesPage(
                    modifier = Modifier.weight(1f),
                    goodsItem = existing,
                    startAddingBatch = addEditState["stock_add_edit_start_add_batch"] == "true",
                    onStartAddingBatchConsumed = {
                        coroutineScope.launch {
                            NavigationScreenModel.Stock.AddEditGoodsItem.removeState("stock_add_edit_start_add_batch")
                        }
                    }
                )
            }

            "history" -> {
                StockAddEditHistoryTab(
                    modifier = Modifier.weight(1f),
                    goodsItem = existing
                )
            }

            "supplier_prices" -> {
                StockSupplierPricesPage(
                    modifier = Modifier.weight(1f),
                    goodsItem = existing
                )
            }

            "orders" -> {
                StockAddEditOrdersTab(
                    modifier = Modifier.weight(1f),
                    goodsItem = existing
                )
            }

            "restricted" -> {
                MessageText(
                    modifier = Modifier.weight(1f),
                    text = localizedStringResource(665, "You do not have permission for this action")
                )
            }

            else -> {
                StockAddEditInfoTab(
                    modifier = Modifier.weight(1f),
                    draft = draft,
                    draftPersistenceKey = draftStorageKey,
                    onDraftChanged = {
                        draft = it
                    }
                )
            }
        }

        val canSaveVisibleStockDraft = when (visibleSelectedTabId) {
            "info", "conditions", "prices" -> canEditCoreStockItem
            "promos" -> canManageStockPromotions
            else -> false
        }

        if (visibleSelectedTabId == "info" || visibleSelectedTabId == "conditions" || visibleSelectedTabId == "prices" || visibleSelectedTabId == "promos") {
            stockSaveError?.let { error ->
                Text(
                    text = error,
                    color = stateValues.ErrorColor,
                    fontSize = stateValues.smallTextSize,
                    modifier = Modifier
                        .fillMaxWidth(if (stateValues.isNarrowScreen) 1f else 0.92f)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            actionButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                text = stateValues.stringConfirm,
                loading = isSavingStockItem,
                loadingText = localizedStringResource(1141, "Please wait…"),
                enabled = !isSavingStockItem &&
                    canSaveVisibleStockDraft &&
                    (!isEditingStockItem || existing != null) &&
                    draft.isValidStockDraft(stateValues.globalAppConfiguration) &&
                    stateValues.activeStoreId != null,
                onClick = {
                    if (isSavingStockItem) return@actionButton
                    if (!canSaveVisibleStockDraft) {
                        postInAppNotification(currentUserPermissionDeniedMessage(), NotificationType.Negative, transient = true)
                        return@actionButton
                    }
                    val storeId = stateValues.activeStoreId ?: return@actionButton
                    val goodsItem = draft.toGoodsItem(storeId, stateValues.globalAppConfiguration, existing)
                    stockSaveError = null
                    isSavingStockItem = true

                    val onSaved: (DataState<GoodsItemDataModel>) -> Unit = { state ->
                        coroutineScope.launch {
                            if (state is DataState.Success) {
                                clearPersistentStockAddEditDraft()
                                isSavingStockItem = false
                                stockSaveError = null
                                if (canPopStockScreen) {
                                    Navigation.Stock.pop(stateValues.isNarrowScreen)
                                    NavigationScreenModel.Stock.AddEditGoodsItem.removeState(
                                        NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID
                                    )
                                } else {
                                    // The canonical wide-screen Add/Edit pane cannot pop itself. Rotate its
                                    // session key so the successful save visibly becomes a clean Add form.
                                    resetStockEditorToFreshAdd(clearCurrentDraft = false)
                                }
                            } else {
                                stockSaveError = stockSaveFailureText(state)
                                isSavingStockItem = false
                            }
                        }
                    }

                    if (existing == null) {
                        addGoodsItem(goodsItem, onSaved)
                    } else {
                        updateGoodsItem(goodsItem, onSaved)
                    }
                }
            )
        }
    }
}


//@Composable
//fun AppConfiguration.StockAddEditGoodsItemScreen() {
//  Column(
//    modifier = Modifier
//      .fillMaxSize()
//  ) {
//    val state by NavigationScreenModel.Stock.AddEditGoodsItem.state.collectAsState()
//
//    val editedGoodsItem =
//      state[NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID]?.run { stateValues.stock?.find { goodsItem -> goodsItem.id == this } }
//
//    ScreenAppBarWidget(
//      title = editedGoodsItem?.let { stateValues.stringEditGoodsItem } ?: stateValues.stringAddGoodsItem,
//      iconPath = editedGoodsItem?.let { stateValues.drawablePathIconEdit } ?: stateValues.drawablePathIconAdd,
//      onBack = if (!Navigation.Stock.isVeryFirstScreen(stateValues.isNarrowScreen)) {
//        {
//          coroutineScope.launch {
//            Navigation.Stock.pop(stateValues.isNarrowScreen)
//            if (editedGoodsItem != null)
//              NavigationScreenModel.Stock.AddEditGoodsItem.removeState(NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID)
//          }
//        }
//      } else null
//    )
//
//    var goAction: (() -> Unit)? = null
//
//    var barcodeTextFieldValues by rememberSaveable {
//      mutableStateOf(
//        mutableListOf<String>()
//          .apply {
//            add("")
//          }
//      )
//    }
//
//    LazyColumn(
//      modifier = Modifier
//        .fillMaxWidth()
//        .weight(1f)
//        .padding(start = 8.dp, top = 24.dp, end = 8.dp)
//    ) {
//      item {
//        Text(
//          modifier = Modifier
//            .padding(bottom = 4.dp),
//          text = stateValues.stringBarcode,
//          style = TextStyle(
//            color = stateValues.TextColor,
//            fontSize = stateValues.accentTextSize,
//            fontWeight = FontWeight.Bold
//          )
//        )
//
//        barcodeTextFieldValues.forEachIndexed { index, _ ->
//          Row {
//            genericTextField(
//              placeholderText = if (index == 0) stateValues.stringEnterBarcode else stateValues.stringEnterBarcode + " ${index + 1}",
//              onValueChange = { value, action ->
//                barcodeTextFieldValues[index] = value
//                action()
//              }
//            )
//
//            Spacer(modifier = Modifier.width(1.dp))
//
//            actionButton(
//              text = "",
//              iconPath = stateValues.drawablePathIconSubtract,
//              iconRes = stateValues.drawableResIconSubtract.value
//            ) {
//
//            }
//          }
//
//          Spacer(modifier = Modifier.height(2.dp))
//        }
//
//        Spacer(modifier = Modifier.height(4.dp))
//
//        actionButton(
//          text = stateValues.stringAddBarcode
//        ) {
//          barcodeTextFieldValues = mutableListOf<String>().apply {
//            addAll(barcodeTextFieldValues)
//            add("")
//          }
//
//          println("barcodes $barcodeTextFieldValues")
//
//        }
//
//        var name: String? by rememberSaveable {
//          mutableStateOf(null)
//        }
//
//        var measurementUnitDropdownListSelectedInitial: String? by rememberSaveable {
//          mutableStateOf(stateValues.globalAppConfiguration.goodsItemsQuantityUnits.takeIf { it.isNotEmpty() }
//            ?.first()?.id)
//        }
//
////        LaunchedEffect(barcodeTextFieldGroupContent.data) {
////          try {
////            barcodeTextFieldGroupContent.data.last().value.text.takeIf { it.length == 13 }?.run {
////              genericItemsRepository
////                .getGenericGoodsItems(this)
////                .collect {
////                  if (it is DataState.Success && it.payload.isNotEmpty()) {
////                    name = it.payload.first().name.extractLocalizedString(stateValues.appLanguage)
////                  }
////                }
////            }
////          } catch (thr: Throwable) {
////            thr.printStackTrace()
////          }
////        }
//
//        Spacer(
//          modifier = Modifier
//            .height(stateValues.marginTextField)
//        )
//
//        val nameTextFieldContent =
//          genericTextField(
//            titleText = stateValues.stringName,
//            placeholderText = stateValues.stringEnterName,
//            valueInitial = name,
//            stateHost = NavigationScreenModel.Stock.AddEditGoodsItem,
//            stateKey = NavigationScreenModel.KEY_STATE_NAME,
//          )
//
//        var isQuickItem by rememberSaveable {
//          mutableStateOf(false)
//        }
//
//        Row(
//          verticalAlignment = Alignment.CenterVertically
//        ) {
//          Checkbox(
//            checked = isQuickItem,
//            onCheckedChange = {
//              isQuickItem = it
//            },
//            colors = CheckboxColors(
//              checkedBoxColor = stateValues.AccentColor,
//              checkedCheckmarkColor = stateValues.AccentTextColor,
//              uncheckedBoxColor = stateValues.BackgroundColor,
//              checkedBorderColor = stateValues.PlaceholderTextColor,
//              uncheckedBorderColor = stateValues.PlaceholderTextColor,
//              uncheckedCheckmarkColor = stateValues.PlaceholderTextColor,
//              disabledBorderColor = stateValues.PlaceholderTextColor,
//              disabledCheckedBoxColor = stateValues.PlaceholderTextColor,
//              disabledUncheckedBoxColor = stateValues.PlaceholderTextColor,
//              disabledIndeterminateBorderColor = stateValues.PlaceholderTextColor,
//              disabledUncheckedBorderColor = stateValues.PlaceholderTextColor,
//              disabledIndeterminateBoxColor = stateValues.PlaceholderTextColor,
//            )
//          )
//
//          Spacer(Modifier.width(2.dp))
//
//          Text(
//            text = stateValues.stringQuick,
//            color = stateValues.TextColor
//          )
//        }
//
//        var categoryDropdownListContent: DropdownListWidgetContent? = null
//        categoryDropdownListContent = stateValues.goodsCategories?.run {
//          val content = dropdownListWidget(
//            titleText = stateValues.stringCategory,
//            domains = map {
//              SelectableDomain(
//                id = it.id,
//                displayId = it.name,
//                name = it.name,
//                iconPath = null,
//                iconRes = null,
//              )
//            },
//            showName = false
//          )
//
//          Spacer(
//            modifier = Modifier
//              .height(stateValues.marginTextField)
//          )
//
//          content
//        }
//
//        LaunchedEffect(categoryDropdownListContent?.selectedId) {
//          stateValues.goodsCategories?.find { it.id == categoryDropdownListContent?.selectedId }?.let {
//            measurementUnitDropdownListSelectedInitial = it.quantityUnitId
//          }
//        }
//
//        val measurementUnitDropdownListContent = dropdownListWidget(
//          titleText = stateValues.stringMeasurementUnit,
//          domains = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.map {
//            SelectableDomain(
//              id = it.id,
//              displayId = it.immutableUnitName,
//              name = it.immutableUnitName,
//              iconPath = null,
//              iconRes = null
//            )
//          },
//          selectedInitial = measurementUnitDropdownListSelectedInitial,
//          showName = false
//        )
//
//        Spacer(
//          modifier = Modifier
//            .height(stateValues.marginTextField)
//        )
//
//        Text(
//          text = localizedStringResource(341, "Batch data"), // TODO
//          fontSize = stateValues.accentTextSize,
//          fontWeight = FontWeight.Bold,
//          color = stateValues.TextColor,
//          modifier = Modifier
//            .fillMaxWidth()
//        )
//
//        Spacer(
//          modifier = Modifier
//            .height(4.dp)
//        )
//
//        val prices by rememberSaveable {
//          mutableStateOf(
//            editedGoodsItem?.let {
//              mutableListOf<BatchPriceInfo>().apply {
//                it.salePrices.forEach { item ->
//                  if (find { item2 -> item2.supplierId == item.supplierId } != null)
//                    indexOfFirst { item3 -> item3.supplierId == item.supplierId }.takeIf { v -> v != -1 }?.let { index ->
//                      set(index, get(index).copy(salePrice = item.price))
//                    }
//                  else
//                    add(
//                      BatchPriceInfo(
//                        supplierId = item.supplierId,
//                        supplyPrice = "",
//                        salePrice = item.price,
//                        returnPrice = "",
//                        currency = item.currency
//                      )
//                    )
//                }
//
//                it.supplyPrices.forEach { item ->
//                  if (find { item2 -> item2.supplierId == item.supplierId } != null)
//                    indexOfFirst { item3 -> item3.supplierId == item.supplierId }.takeIf { v -> v != -1 }?.let { index ->
//                      set(index, get(index).copy(supplyPrice = item.price))
//                    }
//                  else
//                    add(
//                      BatchPriceInfo(
//                        supplierId = item.supplierId,
//                        supplyPrice = item.price,
//                        salePrice = "",
//                        returnPrice = "",
//                        currency = item.currency
//                      )
//                    )
//                }
//
//                it.returnPrices.forEach { item ->
//                  if (find { item2 -> item2.supplierId == item.supplierId } != null)
//                    indexOfFirst { item3 -> item3.supplierId == item.supplierId }.takeIf { v -> v != -1 }?.let { index ->
//                      set(index, get(index).copy(supplyPrice = item.price))
//                    }
//                  else
//                    add(
//                      BatchPriceInfo(
//                        supplierId = item.supplierId,
//                        supplyPrice = "",
//                        salePrice = "",
//                        returnPrice = item.price,
//                        currency = item.currency
//                      )
//                    )
//                }
//              }
//            } ?: emptyList()
//          )
//        }
//
//        Row(
//          verticalAlignment = Alignment.CenterVertically
//        ) {
//          actionButton(
//            text = "",
//            iconPath = stateValues.drawablePathIconAdd,
//            iconRes = stateValues.drawableResIconAdd.value
//          ) {
//
//          }
//
//          Spacer(modifier = Modifier.width(8.dp))
//
//          LazyRow {
//            items(prices) {
//              StockBatchWidget(
//                modifier = Modifier
//                  .width(stateValues.screenWidth / 3),
//                containedSupplierIds = prices.map { it.supplierId }
//              )
//            }
//          }
//        }
//
//        goAction = {
////          editedGoodsItem?.let {
////
////          } ?: stockRepository
////            .addGoodsItem(
////              GoodsItemDataModel(
////                id = "",
////                userId = "",
////                storeId = stateValues.activeStoreId!!,
////                barcode = barcodeTextFieldGroupContent.data.map { it.value.text },
////                name = listOf(
////                  LocalizedStringDataModel(language = "main", nameTextFieldContent.value.text)
////                ),
////                measurementUnitId = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.find {
////                  it.id == measurementUnitDropdownListContent.selectedId
////                }!!.id,
////                categoryIds = categoryDropdownListContent?.selectedId?.let { listOf(it) } ?: emptyList(),
////                salePrices = saleData.data.map {
////                  PriceDataModel(
////                    price = it.value.text,
////                    currency = it.selectedSecondaryDomainId,
////                    supplierId = it.selectedDomainId
////                  )
////                },
////                supplyPrices = supplyData.data.map {
////                  PriceDataModel(
////                    price = it.value.text,
////                    currency = it.selectedSecondaryDomainId,
////                    supplierId = it.selectedDomainId
////                  )
////                },
////                returnPrices = returnData.data.map {
////                  PriceDataModel(
////                    price = it.value.text,
////                    currency = it.selectedSecondaryDomainId,
////                    supplierId = it.selectedDomainId
////                  )
////                },
////                createdAt = 0L,
////                isQuickItem = isQuickItem,
////                isActive = true
////              )
////            ) {
////              coroutineScope.launch {
////                Navigation.Stock.pop(stateValues.isNarrowScreen)
////                if (editedGoodsItem != null)
////                  NavigationScreenModel.Stock.AddEditGoodsItem.removeState(NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID)
////              }
////            }
//        }
//      }
//
//      item {
//        Spacer(
//          modifier = Modifier
//            .height(stateValues.screenHeight / 4)
//        )
//      }
//    }
//
//    Column(
//      modifier = Modifier
//        .fillMaxWidth()
//        .padding(4.dp)
//    ) {
//      Spacer(modifier = Modifier.height(4.dp))
//
//      actionButton(
//        text = editedGoodsItem?.let { stateValues.stringEditGoodsItem } ?: stateValues.stringAddGoodsItem,
//        enabled = stateValues.latestNotification == null,
//        onClick = {
//          goAction?.invoke()
//        }
//      )
//
//      Spacer(modifier = Modifier.height(4.dp))
//    }
//  }
//}

data class DropdownOption(
    val id: String,
    val title: String,
    val subtitle: String? = null
)

@Composable
fun AppConfiguration.LocalizedStringListEditor(
    title: String,
    values: List<LocalizedStringDataModel>,
    onChanged: (List<LocalizedStringDataModel>) -> Unit
) {
    Column {
        Text(
            text = title,
            color = stateValues.TextColor,
            fontSize = stateValues.textSize,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        stateValues.globalAppConfiguration.languages.withBundledAppLanguages().forEach { language ->
            val current = values.find { it.language == language.language }?.value.orEmpty()

            SimpleTextInput(
                modifier = Modifier.fillMaxWidth(),
                value = current,
                placeholder = language.name.extractLocalizedString(stateValues.appLanguage)
                    ?: language.language,
                onValueChange = { newValue ->
                    val mutable = values.toMutableList()
                    val index = mutable.indexOfFirst { it.language == language.language }

                    if (index == -1) {
                        mutable.add(
                            LocalizedStringDataModel(
                                language = language.language,
                                value = newValue
                            )
                        )
                    } else {
                        mutable[index] = mutable[index].copy(value = newValue)
                    }

                    onChanged(mutable)
                }
            )

            Spacer(modifier = Modifier.height(stateValues.marginTextField))
        }
    }
}

@Composable
fun AppConfiguration.SimpleTextInput(
    modifier: Modifier = Modifier,
    value: String,
    placeholder: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIconPath: String? = null,
    isFocusedInitial: Boolean = false,
    autoFocus: Boolean = true,
    stateHost: StateHost? = null,
    stateKey: String? = null,
    onTransformValue: ((String) -> String)? = null,
    onValueChange: (String) -> Unit
): GenericTextFieldContent {
    val textFieldContent = genericTextField(
        modifier = modifier,
        stateHost = stateHost,
        stateKey = stateKey,
        valueInitial = value,
        placeholderText = placeholder,
        leadingIcon = leadingIconPath?.let { path ->
            {
                CpImage(
                    modifier = Modifier
                        .padding(start = 12.dp, top = 7.dp, bottom = 7.dp)
                        .size(18.dp),
                    url = path,
                    fallbackRes = Res.drawable._9_0,
                    contentDescription = placeholder,
                    tintColor = stateValues.AccentColor
                )
            }
        },
        keyboardType = keyboardType,
        isFocusedInitial = isFocusedInitial,
        autoFocus = autoFocus,
        imeWithAction = ImeWithAction(if (singleLine) ImeAction.Next else ImeAction.Default),
        wide = !singleLine,
        singleLine = singleLine,
        adaptiveMultiline = !singleLine,
        showClearButton = true,
        onTransformValue = onTransformValue,
        onValueChange = { newValue, applyChange ->
            onValueChange(newValue)
            applyChange()
        }
    )

    LaunchedEffect(stateHost, stateKey, textFieldContent.value.text, value) {
        if (stateHost != null && !stateKey.isNullOrBlank() && textFieldContent.value.text != value) {
            onValueChange(textFieldContent.value.text)
        }
    }

    return textFieldContent
}




internal fun cameraPermissionTexts(configuration: AppConfiguration): CameraPermissionRequestText = with(configuration) {
    CameraPermissionRequestText(
        rationaleTitle = localizedStringResource(982, "Camera barcode scanner"),
        rationaleSubtitle = localizedStringResource(983, "Camera permission lets this scanner read item and transaction barcodes from the camera preview. AITA uses the camera only after you tap the camera scanner button."),
        deniedTitle = localizedStringResource(984, "Camera access was denied"),
        deniedSubtitle = localizedStringResource(983, "Camera permission lets this scanner read item and transaction barcodes from the camera preview. AITA uses the camera only after you tap the camera scanner button."),
        settingsTitle = localizedStringResource(984, "Camera access was denied"),
        settingsSubtitle = localizedStringResource(985, "Camera access is blocked. Open app settings, allow camera access for AITA, then return and tap the camera scanner again.")
    )
}

internal fun String.toVoiceInputLanguageTag(): String {
    val clean = trim().replace('_', '-').takeIf { it.isNotBlank() } ?: return ""
    val lower = clean.lowercase()
    return when (lower) {
        "main", "system", "default" -> ""
        "ru", "ru-ru" -> "ru-RU"
        "ky", "ky-kg" -> "ky-KG"
        "tg", "tg-tj" -> "tg-TJ"
        "uz", "uz-uz", "uz-latn", "uz-latn-uz" -> "uz-UZ"
        "kk", "kk-kz", "kz", "kz-kz" -> "kk-KZ"
        "en", "en-us", "en-gb" -> if (lower == "en-gb") "en-GB" else "en-US"
        else -> {
            val parts = clean.split('-').filter { it.isNotBlank() }
            when (parts.size) {
                0 -> ""
                1 -> parts[0].lowercase()
                else -> parts[0].lowercase() + "-" + parts[1].uppercase()
            }
        }
    }
}

internal fun AppConfiguration.voiceInputLanguageTags(): List<String> = buildList<String> {
    fun addCandidate(raw: String) {
        val tag = raw.toVoiceInputLanguageTag()
        if (tag.isNotBlank() && !any { existing -> existing.equals(tag, ignoreCase = true) }) add(tag)
    }

    addCandidate(stateValues.appLanguage)
    stateValues.globalAppConfiguration.languages.withBundledAppLanguages().forEach { language ->
        addCandidate(language.language)
    }
    addCandidate("ru")
    addCandidate("kk")
    addCandidate("en")
}.ifEmpty { listOf("en-US") }

internal fun AppConfiguration.voiceInputLanguageDisplayName(languageTag: String): String {
    val tag = languageTag.toVoiceInputLanguageTag().ifBlank { languageTag.trim() }
    val code = tag.substringBefore('-').lowercase()
    val configured = stateValues.globalAppConfiguration.languages.withBundledAppLanguages().firstOrNull { language ->
        val configuredTag = language.language.toVoiceInputLanguageTag()
        configuredTag.equals(tag, ignoreCase = true) ||
            configuredTag.substringBefore('-').equals(code, ignoreCase = true) ||
            language.language.equals(code, ignoreCase = true)
    }

    return configured
        ?.name
        ?.visibleLocalizedString(stateValues.appLanguage, tag)
        ?.takeIf { it.isNotBlank() }
        ?: when (code) {
            "ru" -> if (stateValues.appLanguage.equals("ru", ignoreCase = true)) "Русский" else "Russian"
            "kk" -> if (stateValues.appLanguage.equals("ru", ignoreCase = true)) "Казахский" else "Kazakh"
            "en" -> if (stateValues.appLanguage.equals("ru", ignoreCase = true)) "Английский" else "English"
            else -> tag
        }
}

internal fun voiceInputPermissionTexts(configuration: AppConfiguration): VoiceInputPermissionRequestText = with(configuration) {
    val languageTags = voiceInputLanguageTags()
    VoiceInputPermissionRequestText(
        rationaleTitle = localizedStringResource(1002, "Voice input"),
        rationaleSubtitle = localizedStringResource(1004, "Microphone permission lets voice input fill the text field you are editing. AITA listens only after you tap the microphone button."),
        deniedTitle = localizedStringResource(1005, "Microphone access was denied"),
        deniedSubtitle = localizedStringResource(1004, "Microphone permission lets voice input fill the text field you are editing. AITA listens only after you tap the microphone button."),
        settingsTitle = localizedStringResource(1005, "Microphone access was denied"),
        settingsSubtitle = localizedStringResource(1006, "Microphone access is blocked. Open app settings, allow microphone and speech recognition for AITA, then return and tap the microphone again."),
        listeningTitle = localizedStringResource(1007, "Listening…"),
        listeningSubtitle = localizedStringResource(1008, "Say the text for this field. You can stop listening at any time."),
        languageTags = languageTags,
        primaryLanguageTag = languageTags.firstOrNull().orEmpty()
    )
}

internal fun PlatformPermissionState?.isGrantedOrUnknown(): Boolean =
    this == null || this == PlatformPermissionState.Granted

internal fun PlatformPermissionState?.needsSettingsText(): Boolean =
    this == PlatformPermissionState.PermanentlyDenied

internal fun cameraPermissionDialogTitle(
    texts: CameraPermissionRequestText,
    permissionState: PlatformPermissionState?
): String = when (permissionState) {
    PlatformPermissionState.PermanentlyDenied -> texts.settingsTitle
    PlatformPermissionState.Denied -> texts.deniedTitle
    else -> texts.rationaleTitle
}

internal fun cameraPermissionDialogSubtitle(
    texts: CameraPermissionRequestText,
    permissionState: PlatformPermissionState?
): String = when (permissionState) {
    PlatformPermissionState.PermanentlyDenied -> texts.settingsSubtitle
    PlatformPermissionState.Denied -> texts.deniedSubtitle
    else -> texts.rationaleSubtitle
}

internal fun voicePermissionDialogTitle(
    texts: VoiceInputPermissionRequestText,
    permissionState: PlatformPermissionState?
): String = when (permissionState) {
    PlatformPermissionState.PermanentlyDenied -> texts.settingsTitle
    PlatformPermissionState.Denied -> texts.deniedTitle
    else -> texts.rationaleTitle
}

internal fun voicePermissionDialogSubtitle(
    texts: VoiceInputPermissionRequestText,
    permissionState: PlatformPermissionState?
): String = when (permissionState) {
    PlatformPermissionState.PermanentlyDenied -> texts.settingsSubtitle
    PlatformPermissionState.Denied -> texts.deniedSubtitle
    else -> texts.rationaleSubtitle
}

@Composable
internal fun AppConfiguration.permissionPositiveButtonText(permissionState: PlatformPermissionState?): String =
    if (permissionState.needsSettingsText()) localizedStringResource(1010, "Open app settings") else stateValues.stringConfirm

@Composable
internal fun AppConfiguration.CameraScannerPermissionDialog(
    texts: CameraPermissionRequestText = cameraPermissionTexts(this),
    permissionState: PlatformPermissionState? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ModalDialogWidget(
        title = cameraPermissionDialogTitle(texts, permissionState),
        subTitle = cameraPermissionDialogSubtitle(texts, permissionState),
        negativeButtonText = stateValues.stringCancel,
        positiveButtonText = permissionPositiveButtonText(permissionState),
        onDismiss = onDismiss,
        negativeAction = onDismiss,
        positiveAction = onConfirm
    )
}

@Composable
internal fun AppConfiguration.VoiceInputPermissionDialog(
    texts: VoiceInputPermissionRequestText = voiceInputPermissionTexts(this),
    permissionState: PlatformPermissionState? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ModalDialogWidget(
        title = voicePermissionDialogTitle(texts, permissionState),
        subTitle = voicePermissionDialogSubtitle(texts, permissionState),
        negativeButtonText = stateValues.stringCancel,
        positiveButtonText = permissionPositiveButtonText(permissionState),
        onDismiss = onDismiss,
        negativeAction = onDismiss,
        positiveAction = onConfirm
    )
}

@Composable
internal fun AppConfiguration.InlineBarcodeCameraScanner(
    modifier: Modifier = Modifier,
    visible: Boolean,
    onBarcodeDetected: (String) -> Unit,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = stateValues.marginTextField / 2),
        visible = visible && platformSupportsCameraBarcodeScanner() && barcodeCameraScannerContent != null,
        enter = aitaVisibilityEnter(),
        exit = aitaVisibilityExit()
    ) {
        val platformScanner = barcodeCameraScannerContent
        if (platformScanner != null) {
            platformScanner(
                Modifier
                    .fillMaxWidth()
                    .height(if (stateValues.isNarrowScreen) 260.dp else 320.dp),
                onBarcodeDetected,
                onClose
            )
        }
    }
}

@Composable
fun AppConfiguration.BarcodeTextInput(
    modifier: Modifier = Modifier,
    titleText: String = "",
    value: String,
    placeholderText: String = stateValues.stringEnterBarcode,
    isFocusedInitial: Boolean = false,
    forceRefocus: Boolean = false,
    scannerVisible: Boolean? = null,
    onScannerVisibleChange: ((Boolean) -> Unit)? = null,
    showGenerateBarcodeButton: Boolean = true,
    existingBarcodeValues: List<String> = emptyList(),
    onGeneratedBarcode: ((String) -> Unit)? = null,
    onValueChange: (String) -> Unit
): GenericTextFieldContent {
    var localScannerVisible by remember { mutableStateOf(false) }
    var cameraPermissionDialogState by remember { mutableStateOf<PlatformPermissionState?>(null) }
    var lastCameraBarcode by remember { mutableStateOf("") }
    var lastCameraBarcodeMillis by remember { mutableStateOf(0L) }
    var barcodeFillHighlightPulseKey by remember { mutableStateOf(0) }
    val cameraPermissionRequestText = cameraPermissionTexts(this)

    val cameraAvailable = platformSupportsCameraBarcodeScanner() && barcodeCameraScannerContent != null
    val visible = scannerVisible ?: localScannerVisible
    val setVisible: (Boolean) -> Unit = { next ->
        if (onScannerVisibleChange != null) {
            onScannerVisibleChange(next)
        } else {
            localScannerVisible = next
        }
    }

    val openScannerAfterPermission: () -> Unit = {
        setVisible(true)
    }

    val deniedCameraAction: () -> Unit = {
        postInAppNotification(
            listOf(
                LocalizedStringDataModel("main", localizedStringResource(984, "Camera access was denied")),
                LocalizedStringDataModel("en", "Camera access was denied"),
                LocalizedStringDataModel("ru", "Доступ к камере запрещён"),
                LocalizedStringDataModel("kk", "Камераға рұқсат берілмеді"),
                LocalizedStringDataModel("ky", "Камерага кирүүгө уруксат берилген жок")
            ),
            NotificationType.Negative,
            transient = true
        )
    }

    val requestCameraAndOpen: () -> Unit = {
        val requester = requestCameraScannerPermission
        if (requester == null) {
            openScannerAfterPermission()
        } else {
            coroutineScope.launch {
                requester(
                    cameraPermissionRequestText,
                    openScannerAfterPermission,
                    deniedCameraAction
                )
            }
        }
    }

    val openCameraPermissionSettings: () -> Unit = {
        coroutineScope.launch {
            openPlatformAppSettings?.invoke(PlatformPermissionKind.Camera)
        }
    }

    cameraPermissionDialogState?.let { permissionState ->
        CameraScannerPermissionDialog(
            texts = cameraPermissionRequestText,
            permissionState = permissionState,
            onDismiss = { cameraPermissionDialogState = null },
            onConfirm = {
                cameraPermissionDialogState = null
                if (permissionState.needsSettingsText()) {
                    openCameraPermissionSettings()
                } else {
                    requestCameraAndOpen()
                }
            }
        )
    }

    val content = genericTextField(
        modifier = modifier,
        titleText = titleText,
        valueInitial = value,
        placeholderText = placeholderText,
        leadingIconPath = stateValues.drawablePathIconBarcodeScanner,
        leadingIconContentDescription = localizedStringResource(1001, "Handheld barcode scanner"),
        trailingIconExtraLeadingPath = if (showGenerateBarcodeButton) stateValues.drawablePathIconBarcodeGenerate else null,
        trailingIconExtraLeadingContentDescription = localizedStringResource(1316, "Generate barcode"),
        trailingIconExtraLeadingOnClick = if (showGenerateBarcodeButton) {
            { currentText, replaceText ->
                val generatedBarcode = generateInternalEan13Barcode(
                    buildList {
                        addAll(stateValues.stock.orEmpty().flatMap { it.allBarcodeValues() })
                        addAll(genericGoodsItemsState.payloadValue.orEmpty().flatMap { it.barcode.orEmpty() })
                        addAll(existingBarcodeValues)
                        add(currentText.ifBlank { value })
                    }.filter { it.isNotBlank() }
                )
                barcodeFillHighlightPulseKey += 1
                replaceText(generatedBarcode, false)
                if (onGeneratedBarcode != null) {
                    onGeneratedBarcode(generatedBarcode)
                } else {
                    onValueChange(generatedBarcode)
                }
                postInAppNotification(
                    localizedStringResource(1317, "Generated internal EAN-13 barcode"),
                    NotificationType.Positive,
                    transient = true
                )
            }
        } else null,
        trailingIconExtraPath = if (cameraAvailable) stateValues.drawablePathIconBarcodeCamScanner else null,
        trailingIconExtraContentDescription = localizedStringResource(982, "Camera barcode scanner"),
        trailingIconExtraOnClick = if (cameraAvailable) {
            {
                if (visible) {
                    setVisible(false)
                } else {
                    coroutineScope.launch {
                        when (val permissionState = getCameraScannerPermissionState?.invoke()) {
                            null, PlatformPermissionState.Granted -> requestCameraAndOpen()
                            PlatformPermissionState.Unavailable -> deniedCameraAction()
                            else -> cameraPermissionDialogState = permissionState
                        }
                    }
                }
            }
        } else null,
        keyboardType = KeyboardType.Text,
        imeWithAction = ImeWithAction(ImeAction.Next),
        isFocusedInitial = isFocusedInitial,
        forceRefocus = forceRefocus,
        showClearButton = true,
        successHighlightPulseKey = barcodeFillHighlightPulseKey,
        contentInvalidText = stateValues.stringBarcode,
        onContentValidityCheck = { it.isNotBlank() },
        onTransformValue = { raw -> normalizeVisibleBarcodeFieldInput(value, raw) },
        onFilterValue = { candidate -> candidate.all { char -> char.isDigit() || char.isLetter() } },
        onValueChange = { candidate, applyChange ->
            if (candidate.all { char -> char.isDigit() || char.isLetter() }) {
                applyChange()
                onValueChange(candidate.toStoredGoodsItemBarcode())
            }
        }
    )

    val onCameraBarcodeDetected: (String) -> Unit = { raw ->
        val candidate = (raw.transactionBarcodeCandidate() ?: raw.trim()).toStoredGoodsItemBarcode()
        if (candidate.isNotBlank()) {
            val now = getCurrentTimeMillis()
            val repeatedTooSoon = candidate.normalizedTransactionBarcode() == lastCameraBarcode.normalizedTransactionBarcode() &&
                now - lastCameraBarcodeMillis < 3_000L

            if (!repeatedTooSoon) {
                lastCameraBarcode = candidate
                lastCameraBarcodeMillis = now
                barcodeFillHighlightPulseKey += 1
                content.replaceText(candidate, applyTransform = false)
                setVisible(false)
            }
        }
    }

    InlineBarcodeCameraScanner(
        visible = visible,
        onBarcodeDetected = onCameraBarcodeDetected,
        onClose = { setVisible(false) }
    )

    return content
}


@Composable
fun AppConfiguration.BarcodeListEditor(
    title: String,
    barcodes: List<String>,
    barcodeTypes: List<String> = barcodes.map { GOODS_ITEM_BARCODE_TYPE_STANDARD },
    onChanged: (List<String>) -> Unit,
    onTypesChanged: (List<String>) -> Unit = {},
    onBarcodesAndTypesChanged: ((List<String>, List<String>) -> Unit)? = null
) {
    var focusTargetIndex by rememberSaveable {
        mutableStateOf(0)
    }
    var cameraScannerIndex by rememberSaveable {
        mutableStateOf<Int?>(null)
    }

    LaunchedEffect(focusTargetIndex, barcodes.size) {
        if (focusTargetIndex >= 0) {
            delay(650)
            focusTargetIndex = -1
        }
    }

    Column {
        val currentBarcodes = barcodes.ifEmpty { listOf("") }
        val currentBarcodeTypes = barcodeTypes.alignedStockBarcodeTypes(currentBarcodes)
        val barcodeTypeIconRes by stateValues.drawableResIconBarcodeType.collectAsState()

        fun emitBarcodeEditorState(nextBarcodesRaw: List<String>, nextTypesRaw: List<String>) {
            val nextBarcodes = nextBarcodesRaw.ifEmpty { listOf("") }
            val nextTypes = nextTypesRaw.alignedStockBarcodeTypes(nextBarcodes)
            if (onBarcodesAndTypesChanged != null) {
                onBarcodesAndTypesChanged(nextBarcodes, nextTypes)
            } else {
                onChanged(nextBarcodes)
                onTypesChanged(nextTypes)
            }
        }

        currentBarcodes.forEachIndexed { index, barcode ->
            BarcodeTextInput(
                modifier = Modifier.fillMaxWidth(),
                titleText = if (index == 0) title else "$title ${index + 1}",
                value = barcode,
                placeholderText = stateValues.stringEnterBarcode,
                isFocusedInitial = index == focusTargetIndex,
                forceRefocus = index == focusTargetIndex,
                scannerVisible = cameraScannerIndex == index,
                existingBarcodeValues = currentBarcodes,
                onScannerVisibleChange = { visible ->
                    cameraScannerIndex = if (visible) index else null
                    if (visible) focusTargetIndex = index
                },
                onGeneratedBarcode = { generatedBarcode ->
                    val nextBarcodes = currentBarcodes.toMutableList().also {
                        while (it.size <= index) it.add("")
                        it[index] = generatedBarcode
                    }
                    val nextTypes = currentBarcodeTypes.toMutableList().also {
                        while (it.size <= index) it.add(GOODS_ITEM_BARCODE_TYPE_STANDARD)
                        it[index] = GOODS_ITEM_BARCODE_TYPE_INTERNAL
                    }
                    focusTargetIndex = index
                    emitBarcodeEditorState(nextBarcodes, nextTypes)
                },
                onValueChange = { storedValue ->
                    val previousBarcode = currentBarcodes.getOrNull(index).orEmpty()
                    val currentType = currentBarcodeTypes
                        .getOrNull(index)
                        ?.normalizedGoodsItemBarcodeType(previousBarcode)
                        ?: GOODS_ITEM_BARCODE_TYPE_STANDARD
                    val nextBarcodes = currentBarcodes.toMutableList().also {
                        while (it.size <= index) it.add("")
                        it[index] = storedValue
                    }
                    val nextTypes = currentBarcodeTypes.toMutableList().also {
                        while (it.size <= index) it.add(GOODS_ITEM_BARCODE_TYPE_STANDARD)
                        it[index] = currentType
                    }
                    emitBarcodeEditorState(nextBarcodes, nextTypes)
                }
            )

            val selectedType = currentBarcodeTypes.getOrNull(index)?.normalizedGoodsItemBarcodeType(barcode)
                ?: GOODS_ITEM_BARCODE_TYPE_STANDARD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuthTinyChoiceChip(
                    selected = selectedType == GOODS_ITEM_BARCODE_TYPE_STANDARD,
                    label = localizedStringResource(1153, "Standard"),
                    iconPath = stateValues.drawablePathIconBarcodeType,
                    iconRes = barcodeTypeIconRes,
                    onClick = {
                        val nextTypes = currentBarcodeTypes.toMutableList().also {
                            while (it.size <= index) it.add(GOODS_ITEM_BARCODE_TYPE_STANDARD)
                            it[index] = GOODS_ITEM_BARCODE_TYPE_STANDARD
                        }
                        emitBarcodeEditorState(currentBarcodes, nextTypes)
                    }
                )
                AuthTinyChoiceChip(
                    selected = selectedType == GOODS_ITEM_BARCODE_TYPE_INTERNAL,
                    label = localizedStringResource(1154, "Internal"),
                    iconPath = stateValues.drawablePathIconBarcodeType,
                    iconRes = barcodeTypeIconRes,
                    onClick = {
                        val nextTypes = currentBarcodeTypes.toMutableList().also {
                            while (it.size <= index) it.add(GOODS_ITEM_BARCODE_TYPE_STANDARD)
                            it[index] = GOODS_ITEM_BARCODE_TYPE_INTERNAL
                        }
                        emitBarcodeEditorState(currentBarcodes, nextTypes)
                    }
                )
            }

            Spacer(modifier = Modifier.height(stateValues.marginTextField))
        }

        actionButton(
            autoLoading = false,
            text = stateValues.stringAddBarcode,
            iconPath = stateValues.drawablePathIconAdd
        ) {
            val nextIndex = currentBarcodes.size
            val nextBarcodes = currentBarcodes + ""
            val nextTypes = currentBarcodeTypes + GOODS_ITEM_BARCODE_TYPE_STANDARD
            focusTargetIndex = nextIndex
            emitBarcodeEditorState(nextBarcodes, nextTypes)
        }
    }
}

@Composable
fun AppConfiguration.SimpleDropdownField(
    modifier: Modifier = Modifier,
    title: String,
    selectedId: String?,
    options: List<DropdownOption>,
    placeholder: String,
    onSelected: (String) -> Unit
) {
    var expanded by rememberSaveable {
        mutableStateOf(false)
    }

    val selected = options.find { it.id == selectedId }
    val dropdownBorderWidth by animateDpAsState(
        targetValue = if (expanded) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
        animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS),
        label = "dropdownBorderWidth"
    )
    val dropdownBorderColor by animateColorAsState(
        targetValue = if (expanded) stateValues.AccentColor else stateValues.PlaceholderTextColor,
        animationSpec = tween(durationMillis = AITA_MOTION_FAST_MILLIS),
        label = "dropdownBorderColor"
    )

    Column(modifier = modifier.aitaContentMotion()) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp)
                .height(stateValues.textFieldHeight)
                .aitaContentMotion()
                .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.BackgroundColor)
                .border(
                    dropdownBorderWidth,
                    dropdownBorderColor,
                    RoundedCornerShape(stateValues.cornerRadius)
                )
                .aitaClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = stateValues.AccentColor)
                ) {
                    expanded = !expanded
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected?.title?.takeIf { it.isNotBlank() } ?: placeholder,
                        color = if (selected == null) stateValues.PlaceholderTextColor else stateValues.TextColor,
                        fontSize = stateValues.textSize,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    selected?.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = stateValues.TextColor,
                            fontSize = stateValues.smallTextSize,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = if (expanded) "▲" else "▼",
                    color = stateValues.AccentColor,
                    fontSize = stateValues.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = accentTextShadow(stateValues.AccentColor, stateValues.AccentColor))
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = aitaVisibilityEnter(),
            exit = aitaVisibilityExit()
        ) {
            Column(modifier = Modifier.fillMaxWidth().aitaContentMotion()) {
                Spacer(modifier = Modifier.height(2.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp)
                        .heightIn(max = stateValues.screenHeight / 4)
                        .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
                        .clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.BackgroundColor)
                        .border(
                            stateValues.unfocusedBorderWidth,
                            stateValues.AccentColor,
                            RoundedCornerShape(stateValues.cornerRadius)
                        )
                ) {
                    items(options) { option ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 38.dp)
                                .aitaClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = stateValues.AccentColor)
                                ) {
                                    onSelected(option.id)
                                    expanded = false
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = option.title,
                                color = stateValues.TextColor,
                                fontSize = stateValues.textSize,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            option.subtitle?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    color = stateValues.TextColor,
                                    fontSize = stateValues.smallTextSize,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
fun AppConfiguration.SimpleDialogWidget(
    modifier: Modifier = Modifier,
    title: String,
    positiveAction: Pair<String, () -> Unit>,
    negativeAction: Pair<String, () -> Unit>
) {
    Column(
        modifier = modifier
            .fillMaxWidth(0.4f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = stateValues.titleTextSize
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actionButton(text = positiveAction.first, onClick = positiveAction.second)
            actionButton(text = negativeAction.first, onClick = negativeAction.second)
        }
    }
}

@Composable
fun AppConfiguration.selectableDomainWidget(
    modifier: Modifier = Modifier,
    domain: SelectableDomain,
    state: MutableTransitionState<Boolean>? = null,
    showId: Boolean = true,
    showName: Boolean = false,
    showExpansion: Boolean = false,
    reverseExpandIconPosition: Boolean = false,
    textColor: Color = stateValues.TextColor,
    onClick: (() -> Unit)? = null
): SelectableDomainWidgetContent {

    var expanded by rememberSaveable {
        mutableStateOf(state?.targetState == true)
    }

    LaunchedEffect(expanded) {
        state?.targetState = expanded
    }

    LaunchedEffect(state?.targetState) {
        expanded = state?.targetState == true
    }

    val drawableResIconExpandLess by stateValues.drawableResIconExpandLess.collectAsState()
    val drawableResIconExpandMore by stateValues.drawableResIconExpandMore.collectAsState()

    Row(
        modifier = modifier
            .height(stateValues.textFieldHeight)
            .run {
                onClick?.let {
                    aitaClickable(
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = ripple(color = textColor),
                        onClick = {
                            expanded = !expanded

                            it()
                        }
                    )
                } ?: this
            }
            .padding(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (showExpansion && !reverseExpandIconPosition) {
            CpImage(
                modifier = Modifier
                    .padding(
                        start = stateValues.textFieldIconPadding,
                        top = stateValues.textFieldIconPadding,
                        bottom = stateValues.textFieldIconPadding
                    )
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                url = if (expanded) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore,
                fallbackRes = if (expanded) drawableResIconExpandLess else drawableResIconExpandMore,
                contentDescription = if (showName)
                    domain.name?.extractLocalizedString(stateValues.appLanguage) ?: domain.displayId.extractLocalizedString(
                        stateValues.appLanguage
                    )
                else
                    domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.name?.extractLocalizedString(
                        stateValues.appLanguage
                    ),
                tintColor = textColor
            )
        }

        domain.iconPath?.let {
            CpImage(
                modifier = Modifier
                    .padding(stateValues.textFieldIconPadding)
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                url = it,
                fallbackRes = domain.iconRes,
                contentDescription = if (showName)
                    domain.name?.extractLocalizedString(stateValues.appLanguage) ?: domain.displayId.extractLocalizedString(
                        stateValues.appLanguage
                    )
                else
                    domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.name?.extractLocalizedString(
                        stateValues.appLanguage
                    )
            )
        }

        if (showId)
            Text(
                text = domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.id,
                color = textColor,
                modifier = Modifier
                    .padding(horizontal = if (!showExpansion) 16.dp else 8.dp, vertical = 8.dp),
                overflow = TextOverflow.Ellipsis
            )


        if (showName && domain.name != null) {
            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = domain.name.extractLocalizedString(stateValues.appLanguage) ?: "",
                color = textColor,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (reverseExpandIconPosition) {
            CpImage(
                modifier = Modifier
                    .padding(
                        start = stateValues.textFieldIconPadding,
                        top = stateValues.textFieldIconPadding,
                        bottom = stateValues.textFieldIconPadding
                    )
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                url = if (!showExpansion) {
                    ""
                } else {
                    if (expanded) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore
                },
                fallbackRes = if (!showExpansion) {
                    null
                } else {
                    if (expanded) drawableResIconExpandLess else drawableResIconExpandMore
                },
                contentDescription = if (showName)
                    domain.name?.extractLocalizedString(stateValues.appLanguage) ?: domain.displayId.extractLocalizedString(
                        stateValues.appLanguage
                    )
                else
                    domain.displayId.extractLocalizedString(stateValues.appLanguage) ?: domain.name?.extractLocalizedString(
                        stateValues.appLanguage
                    ),
                tintColor = textColor
            )
        }
    }

    return SelectableDomainWidgetContent(expanded)
}

data class SelectableDomainWidgetContent(
    var expanded: Boolean
)

class SelectableDomain(
    val id: String,
    val displayId: List<LocalizedStringDataModel>,
    val name: List<LocalizedStringDataModel>?,
    val iconPath: String?,
    val iconRes: DrawableResource?
) : Searchable {

    constructor(
        id: String,
        displayId: String,
        name: String,
        iconPath: String?,
        iconRes: DrawableResource?
    ) : this(id, displayId.toLocalizedSingleMain(), name.toLocalizedSingleMain(), iconPath, iconRes)

    override val exactSearchOperands: List<String> = mutableListOf<String>().apply {
        addAll(displayId.map { it.value })
        name?.let { localizedNames -> addAll(localizedNames.map { it.value }) }
    }

    override val containsSearchOperands: List<String> = mutableListOf<String>().apply {
        addAll(displayId.map { it.value })
        name?.let { localizedNames -> addAll(localizedNames.map { it.value }) }
    }
    override val uniqueSearchOperands: List<String> = mutableListOf<String>().apply {
        addAll(displayId.map { it.value })
        name?.let { localizedNames -> addAll(localizedNames.map { it.value }) }
    }
}

@Composable
fun AppConfiguration.ScreenAppBarWidget(
    modifier: Modifier = Modifier,
    title: String,
    iconPath: String? = null,
    iconRes: DrawableResource? = null,
    textColor: Color = stateValues.TextColor,
    cornerRadius: Dp = stateValues.cornerRadius,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingIcons: List<Triple<String, DrawableResource, () -> Unit>> = emptyList(),
    onBack: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .foregroundTactileShadow(cornerRadius = cornerRadius, elevated = false)
                .run {
                    if (stateValues.isNarrowScreen)
                        clip(
                            RoundedCornerShape(
                                bottomStart = cornerRadius,
                                bottomEnd = cornerRadius
                            )
                        )
                    else
                        this
                }
                .background(stateValues.BackgroundColor)
                .run {
                    if (stateValues.isNarrowScreen)
                        border(
                            stateValues.unfocusedBorderWidth,
                            stateValues.PlaceholderTextColor,
                            RoundedCornerShape(
                                bottomStart = cornerRadius,
                                bottomEnd = cornerRadius
                            )
                        )
                    else
                        this
                }
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            onBack?.run {
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(cornerRadius))
                        .aitaClickable(
                            interactionSource = remember {
                                MutableInteractionSource()
                            },
                            indication = ripple(color = textColor, radius = cornerRadius),
                            onClick = this
                        )
                ) {
                    val iconRes by stateValues.drawableResIconBackArrow.collectAsState()

                    CpImage(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(18.dp),
                        url = stateValues.drawablePathIconBackArrow,
                        fallbackRes = iconRes,
                        contentDescription = stateValues.stringBack,
                        tintColor = textColor
                    )
                }
            }

            leadingContent?.invoke()

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconPath != null) {
                    CpImage(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(22.dp)
                            .align(Alignment.CenterVertically),
                        url = iconPath,
                        fallbackRes = iconRes,
                        contentDescription = title,
                        tintColor = textColor
                    )
                }

                Text(
                    text = title,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = stateValues.accentTextSize,
                    color = textColor,
                    style = TextStyle(shadow = null)
                )
            }

            trailingIcons.takeIf { it.isNotEmpty() }?.run {
                forEach {
                    Spacer(modifier = Modifier.width(8.dp))

                    actionButton(
                        modifier = Modifier.padding(vertical = 5.dp),
                        text = "",
                        iconPath = it.first,
                        iconRes = it.second,
                        autoLoading = false,
                        onClick = it.third
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))
            }
        }

        if (!stateValues.isNarrowScreen)
            Spacer(
                modifier = Modifier
                    .background(stateValues.PlaceholderTextColor)
                    .fillMaxWidth()
                    .height(stateValues.unfocusedBorderWidth)
            )
    }
}

@Composable
fun AppConfiguration.BarcodeCameraScannerFallbackPane(
    modifier: Modifier,
    onClose: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .padding(stateValues.marginTextField),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = localizedStringResource(982, "Camera barcode scanner"),
            color = stateValues.TextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(stateValues.marginTextField / 2))
        actionButton(
            autoLoading = false,
            text = localizedStringResource(989, "Close scanner"),
            onClick = onClose
        )
    }
}

@Composable
fun AppConfiguration.searchTextField(
    modifier: Modifier = Modifier,
    valueInitial: String? = null,
    retainTextAcrossRecreation: Boolean = true,
    persistTextDraft: Boolean = true,
    stateHost: StateHost?,
    stateKey: String?,
    isFocusedInitial: Boolean = false,
    autoFocus: Boolean = true,
    updateIsFocusedAction: ((FocusState) -> Unit)? = null,
    forceRefocus: Boolean = false,
    barcodeCamScanner: Boolean = false,
    captureTransactionBarcodeInput: Boolean = false,
    focusedBorderWidth: Dp = stateValues.focusedBorderWidth,
    unfocusedBorderWidth: Dp = stateValues.unfocusedBorderWidth,
    focusedBorderColor: Color = stateValues.AccentColor,
    unfocusedBorderColor: Color = stateValues.PlaceholderTextColor
): GenericTextFieldContent {
    var textFieldContent: GenericTextFieldContent? = null
    val appConfiguration = this

    Column(
        modifier = modifier
    ) {
        val cameraScannerAvailable = barcodeCamScanner && platformSupportsCameraBarcodeScanner() && barcodeCameraScannerContent != null
        var barcodeCamScannerVisible by remember { mutableStateOf(false) }
        var cameraPermissionDialogState by remember { mutableStateOf<PlatformPermissionState?>(null) }
        var lastCameraBarcode by remember { mutableStateOf("") }
        var lastCameraBarcodeMillis by remember { mutableStateOf(0L) }
        var barcodeFillHighlightPulseKey by remember { mutableStateOf(0) }
        val cameraPermissionRequestText = cameraPermissionTexts(appConfiguration)

        LaunchedEffect(cameraScannerAvailable) {
            if (!cameraScannerAvailable) {
                barcodeCamScannerVisible = false
                cameraPermissionDialogState = null
            }
        }

        val openScannerAfterPermission: () -> Unit = {
            barcodeCamScannerVisible = true
            coroutineScope.launch {
                delay(100)
                if (captureTransactionBarcodeInput) requestTransactionBarcodeFocus()
            }
        }

        val closeScanner: () -> Unit = {
            barcodeCamScannerVisible = false
            coroutineScope.launch {
                delay(120)
                if (captureTransactionBarcodeInput) requestTransactionBarcodeFocus()
            }
        }

        val deniedCameraAction: () -> Unit = {
            postInAppNotification(
                listOf(
                    LocalizedStringDataModel("main", localizedStringResource(984, "Camera access was denied")),
                    LocalizedStringDataModel("en", "Camera access was denied"),
                    LocalizedStringDataModel("ru", "Доступ к камере запрещён"),
                    LocalizedStringDataModel("kk", "Камераға рұқсат берілмеді"),
                    LocalizedStringDataModel("ky", "Камерага кирүүгө уруксат берилген жок")
                ),
                NotificationType.Negative,
                transient = true
            )
            coroutineScope.launch {
                delay(120)
                if (captureTransactionBarcodeInput) requestTransactionBarcodeFocus()
            }
        }

        val requestCameraAndOpen: () -> Unit = {
            val requester = requestCameraScannerPermission
            if (requester == null) {
                openScannerAfterPermission()
            } else {
                coroutineScope.launch {
                    requester(
                        cameraPermissionRequestText,
                        openScannerAfterPermission,
                        deniedCameraAction
                    )
                }
            }
        }

        val openCameraPermissionSettings: () -> Unit = {
            coroutineScope.launch {
                openPlatformAppSettings?.invoke(PlatformPermissionKind.Camera)
                delay(120)
                if (captureTransactionBarcodeInput) requestTransactionBarcodeFocus()
            }
        }

        cameraPermissionDialogState?.let { permissionState ->
            CameraScannerPermissionDialog(
                texts = cameraPermissionRequestText,
                permissionState = permissionState,
                onDismiss = {
                    cameraPermissionDialogState = null
                    if (captureTransactionBarcodeInput) requestTransactionBarcodeFocus()
                },
                onConfirm = {
                    cameraPermissionDialogState = null
                    if (permissionState.needsSettingsText()) {
                        openCameraPermissionSettings()
                    } else {
                        requestCameraAndOpen()
                    }
                }
            )
        }

        textFieldContent = genericTextField(
            valueInitial = valueInitial,
            retainTextAcrossRecreation = retainTextAcrossRecreation,
            persistTextDraft = persistTextDraft,
            stateHost = stateHost,
            stateKey = stateKey,
            isFocusedInitial = isFocusedInitial,
            autoFocus = autoFocus,
            updateIsFocusedAction = updateIsFocusedAction,
            forceRefocus = forceRefocus,
            placeholderText = stateValues.stringSearchByAnyData,
            focusedBorderWidth = focusedBorderWidth,
            unfocusedBorderWidth = unfocusedBorderWidth,
            focusedBorderColor = focusedBorderColor,
            unfocusedBorderColor = unfocusedBorderColor,
            leadingIconPath = stateValues.drawablePathIconSearch,
            trailingIconExtraPath = if (cameraScannerAvailable) stateValues.drawablePathIconBarcodeCamScanner else null,
            trailingIconExtraContentDescription = localizedStringResource(982, "Camera barcode scanner"),
            successHighlightPulseKey = barcodeFillHighlightPulseKey,
            trailingIconExtraOnClick = if (cameraScannerAvailable) {
                {
                    if (barcodeCamScannerVisible) {
                        closeScanner()
                    } else {
                        coroutineScope.launch {
                            when (val permissionState = getCameraScannerPermissionState?.invoke()) {
                                null, PlatformPermissionState.Granted -> requestCameraAndOpen()
                                PlatformPermissionState.Unavailable -> deniedCameraAction()
                                else -> cameraPermissionDialogState = permissionState
                            }
                        }
                    }
                }
            } else null,
            captureTransactionBarcodeInput = captureTransactionBarcodeInput
        )

        val onCameraBarcodeDetected: (String) -> Unit = { raw ->
            val candidate = raw.transactionBarcodeCandidate() ?: raw.trim()
            if (candidate.isNotBlank()) {
                val now = getCurrentTimeMillis()
                val repeatedTooSoon = candidate.normalizedTransactionBarcode() == lastCameraBarcode.normalizedTransactionBarcode() &&
                    now - lastCameraBarcodeMillis < 3_000L

                if (!repeatedTooSoon) {
                    lastCameraBarcode = candidate
                    lastCameraBarcodeMillis = now
                    barcodeFillHighlightPulseKey += 1

                    if (captureTransactionBarcodeInput) {
                        val handled = activeTransactionBarcodeHandler?.invoke(candidate) == true
                        if (!handled) {
                            postInAppNotification(
                                eventMessage("message.barcode", "candidate" to (candidate).toString()),
                                NotificationType.Neutral,
                                transient = true
                            )
                        }
                    } else {
                        textFieldContent?.replaceText(candidate, applyTransform = false)
                    }
                    closeScanner()
                }
            }

            if (captureTransactionBarcodeInput) requestTransactionBarcodeFocus()
        }

        AnimatedVisibility(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = stateValues.marginTextField / 2),
            visible = cameraScannerAvailable && barcodeCamScannerVisible,
            enter = aitaVisibilityEnter(),
            exit = aitaVisibilityExit()
        ) {
            val platformScanner = barcodeCameraScannerContent
            if (platformScanner != null) {
                platformScanner(
                    Modifier
                        .fillMaxWidth()
                        .height(if (stateValues.isNarrowScreen) 260.dp else 320.dp),
                    onCameraBarcodeDetected,
                    closeScanner
                )
            } else {
                BarcodeCameraScannerFallbackPane(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (stateValues.isNarrowScreen) 260.dp else 320.dp),
                    onClose = closeScanner
                )
            }
        }
    }

    return textFieldContent!!
}

@Composable
fun AppConfiguration.repeatedPasswordTextFieldGroup(
    modifier: Modifier = Modifier,
    stateHost: StateHost,
    stateKey: String,
    repeatedStateKey: String,
    passwordTitleText: String? = null,
    passwordPlaceholderText: String? = null,
    repeatPasswordTitleText: String? = null,
    repeatPasswordPlaceholderText: String? = null,
    imeWithAction: ImeWithAction? = null,
    retainTextAcrossRecreation: Boolean = false,
    persistTextDraft: Boolean = false
): Pair<GenericTextFieldContent, GenericTextFieldContent> {

    val showPasswordState = if (retainTextAcrossRecreation) {
        rememberSaveable(stateKey, repeatedStateKey) { mutableStateOf(false) }
    } else {
        remember(stateKey, repeatedStateKey) { mutableStateOf(false) }
    }
    var showPassword by showPasswordState

    val passwordTextFieldContent = genericTextField(
        modifier = modifier,
        stateHost = stateHost,
        stateKey = stateKey,
        retainTextAcrossRecreation = retainTextAcrossRecreation,
        persistTextDraft = persistTextDraft,
        titleText = passwordTitleText ?: stateValues.stringPassword,
        placeholderText = passwordPlaceholderText ?: stateValues.stringEnterPassword,
        leadingIconPath = stateValues.drawablePathIconPassword,
        keyboardType = KeyboardType.Password,
        imeWithAction = imeWithAction ?: ImeWithAction.Default,
        trailingIconExtraPath = if (showPassword) stateValues.drawablePathIconEyeHide else stateValues.drawablePathIconEyeShow,
        trailingIconExtraOnClick = {
            showPassword = !showPassword
        },
        contentInvalidText = stateValues.stringPasswordMustBe,
        onContentValidityCheck = {
            it.checkAsPassword()
        },
        visualTransformation =
            if (showPassword) {
                {
                    getTransformedTextWithSelectionFocusTextColor(
                        it,
                        stateValues.AccentTextColor
                    )
                }
            } else {
                {
                    getPasswordTransformedTextWithSelectionFocusTextColor(
                        it,
                        stateValues.AccentTextColor
                    )
                }
            }
    )

    Spacer(modifier = Modifier
        .height(stateValues.marginTextField)
    )

    val repeatedPasswordTextFieldContent = genericTextField(
        modifier = modifier,
        stateHost = stateHost,
        stateKey = repeatedStateKey,
        retainTextAcrossRecreation = retainTextAcrossRecreation,
        persistTextDraft = persistTextDraft,
        titleText = repeatPasswordTitleText ?: stateValues.stringRepeatPassword,
        placeholderText = repeatPasswordPlaceholderText ?: stateValues.stringRepeatPassword,
        leadingIconPath = stateValues.drawablePathIconPassword,
        keyboardType = KeyboardType.Password,
        imeWithAction = imeWithAction ?: ImeWithAction.Default,
        contentInvalidText = stateValues.stringPasswordsMustMatch,
        onContentValidityCheck = {
            it == passwordTextFieldContent.value.text
        },
        visualTransformation =
            if (showPassword) {
                {
                    getTransformedTextWithSelectionFocusTextColor(
                        it,
                        stateValues.AccentTextColor
                    )
                }
            } else {
                {
                    getPasswordTransformedTextWithSelectionFocusTextColor(
                        it,
                        stateValues.AccentTextColor
                    )
                }
            }
    )

    return Pair(passwordTextFieldContent, repeatedPasswordTextFieldContent)
}

@Composable
fun AppConfiguration.passwordTextField(
    modifier: Modifier = Modifier,
    titleText: String? = null,
    placeholderText: String? = null,
    stateHost: StateHost,
    stateKey: String,
    contentInvalidText: String? = null,
    imeWithAction: ImeWithAction? = null,
    onContentValidityCheck: ((String) -> Boolean)? = null,
    retainTextAcrossRecreation: Boolean = false,
    persistTextDraft: Boolean = false
): GenericTextFieldContent {

    val showPasswordState = if (retainTextAcrossRecreation) {
        rememberSaveable(stateKey) { mutableStateOf(false) }
    } else {
        remember(stateKey) { mutableStateOf(false) }
    }
    var showPassword by showPasswordState

    return genericTextField(
        modifier = modifier,
        stateHost = stateHost,
        stateKey = stateKey,
        retainTextAcrossRecreation = retainTextAcrossRecreation,
        persistTextDraft = persistTextDraft,
        titleText = titleText ?: stateValues.stringPassword,
        placeholderText = placeholderText ?: stateValues.stringEnterPassword,
        leadingIconPath = stateValues.drawablePathIconPassword,
        keyboardType = KeyboardType.Password,
        imeWithAction = imeWithAction ?: ImeWithAction.Default,
        trailingIconExtraPath = if (showPassword) stateValues.drawablePathIconEyeHide else stateValues.drawablePathIconEyeShow,
        trailingIconExtraOnClick = {
            showPassword = !showPassword
        },
        contentInvalidText = contentInvalidText ?: stateValues.stringPasswordMustBe,
        onContentValidityCheck = {
            onContentValidityCheck?.invoke(it) ?: it.checkAsPassword()
        },
        visualTransformation =
            if (showPassword) {
                {
                    getTransformedTextWithSelectionFocusTextColor(
                        it,
                        stateValues.AccentTextColor
                    )
                }
            } else {
                {
                    getPasswordTransformedTextWithSelectionFocusTextColor(
                        it,
                        stateValues.AccentTextColor
                    )
                }
            }
    )
}

sealed class NavigationScreenModel(
    val route: String,
    open val name: String = route,
    open val iconPath: String = "",
    open val iconRes: DrawableResource? = null
): StateHost() {

    companion object {
        const val KEY_STATE_SEARCH_QUERY: String = "keyState_searchQuery"
        const val KEY_STATE_NAME: String = "keyState_name"
        const val KEY_STATE_PHONE_NUMBER: String = "keyState_phoneNumber"
        const val KEY_STATE_EMAIL: String = "keyState_email"
        const val KEY_STATE_FIRST_NAME: String = "keyState_firstName"
        const val KEY_STATE_LAST_NAME: String = "keyState_lastName"
        const val KEY_STATE_PASSWORD: String = "keyState_password"
        const val KEY_STATE_REPEATED_PASSWORD: String = "keyState_repeatedPassword"
        const val KEY_STATE_QUANTITY: String = "keyState_quantity"
    }

    sealed class Buyer(route: String): NavigationScreenModel(route) {

        sealed class Main(route: String): Buyer(route) {
            data object Home: Main("BuyerMainHomeNavigationScreenModelRoute") {
                override val name get() = with(AppConfiguration) { authUiText("Market", "Маркет", "Маркет", "Маркет") }
                override val iconPath get() = AppConfiguration.marketIconPath(139)
                override val iconRes get() = AppConfiguration.marketIconFallback(139)
            }
            data object Saved: Main("BuyerMainSavedNavigationScreenModelRoute") {
                override val name get() = with(AppConfiguration) { authUiText("Saved", "Сохранённое", "Сақталғандар", "Сакталды") }
                override val iconPath get() = AppConfiguration.marketIconPath(140)
                override val iconRes get() = AppConfiguration.marketIconFallback(140)
            }
            data object Shopping: Main("BuyerMainShoppingNavigationScreenModelRoute") {
                override val name get() = with(AppConfiguration) { authUiText("List", "Покупки", "Тізім", "Тизме") }
                override val iconPath get() = AppConfiguration.marketIconPath(143)
                override val iconRes get() = AppConfiguration.marketIconFallback(143)
            }
            data object Search: Main("BuyerMainSearchNavigationScreenModelRoute")
        }

        sealed class Cart(route: String): Buyer(route) {
            data object Main: Cart("BuyerCartMainNavigationScreenModelRoute") {
                override val name: String
                    get() = AppConfiguration.stateValues.stringCart
                override val iconPath: String
                    get() {
                        return AppConfiguration.stateValues.drawablePathIconTransactionSale
                    }
                override val iconRes: DrawableResource
                    get() {
                        return AppConfiguration.stateValues.drawableResIconTransactionSale.value
                    }
            }
        }

        sealed class Orders(route: String): Buyer(route) {
            data object Main: Orders("BuyerOrdersMainNavigationScreenModelRoute") {
                override val name: String
                    get() = AppConfiguration.stateValues.strings.extractString(254, AppConfiguration.stateValues.appLanguage)
                        ?: AppConfiguration.stateValues.strings.extractString(254, "main")
                        ?: "Orders"
                override val iconPath: String
                    get() {
                        return AppConfiguration.stateValues.drawablePathIconTransactionHistory
                    }
                override val iconRes: DrawableResource
                    get() {
                        return AppConfiguration.stateValues.drawableResIconTransactionHistory.value
                    }
            }
        }
    }

    sealed class Supplier(route: String): NavigationScreenModel(route) {

        sealed class Orders(route: String): Supplier(route) {
            data object Main: Orders("SupplierOrdersMainNavigationScreenModelRoute") {
                override val name: String
                    get() = with(AppConfiguration) { localizedStringResource(1337, "Orders") }
                override val iconPath: String
                    get() = AppConfiguration.stateValues.drawablePathIconSupplierRecoveryLedger
                override val iconRes: DrawableResource
                    get() = AppConfiguration.stateValues.drawableResIconSupplierRecoveryLedger.value
            }
        }

        sealed class Catalog(route: String): Supplier(route) {
            data object Main: Catalog("SupplierCatalogMainNavigationScreenModelRoute") {
                override val name: String
                    get() = with(AppConfiguration) { localizedStringResource(1338, "Catalog") }
                override val iconPath: String
                    get() = AppConfiguration.stateValues.drawablePathIconSupplierCatalog
                override val iconRes: DrawableResource
                    get() = AppConfiguration.stateValues.drawableResIconSupplierCatalog.value
            }
        }

        sealed class Dispatch(route: String): Supplier(route) {
            data object Main: Dispatch("SupplierDispatchMainNavigationScreenModelRoute") {
                override val name: String
                    get() = with(AppConfiguration) { localizedStringResource(1556, "Dispatch") }
                override val iconPath: String
                    get() = AppConfiguration.stateValues.drawablePathIconSupplierDispatch
                override val iconRes: DrawableResource
                    get() = AppConfiguration.stateValues.drawableResIconSupplierDispatch.value
            }
        }

        sealed class Customers(route: String): Supplier(route) {
            data object Main: Customers("SupplierCustomersMainNavigationScreenModelRoute") {
                override val name: String
                    get() = with(AppConfiguration) { localizedStringResource(1453, "Partner stores") }
                override val iconPath: String
                    get() = AppConfiguration.stateValues.drawablePathIconSupplierPartners
                override val iconRes: DrawableResource
                    get() = AppConfiguration.stateValues.drawableResIconSupplierPartners.value
            }
        }

        sealed class Contracts(route: String): Supplier(route) {
            data object Main: Contracts("SupplierContractsMainNavigationScreenModelRoute") {
                override val name: String
                    get() = with(AppConfiguration) { localizedStringResource(1479, "Supplier contracts") }
                override val iconPath: String
                    get() = AppConfiguration.stateValues.drawablePathIconSupplierContracts
                override val iconRes: DrawableResource
                    get() = AppConfiguration.stateValues.drawableResIconSupplierContracts.value
            }
        }

        sealed class Analytics(route: String): Supplier(route) {
            data object Main: Analytics("SupplierAnalyticsMainNavigationScreenModelRoute") {
                override val name: String
                    get() = with(AppConfiguration) { localizedStringResource(1340, "Insights") }
                override val iconPath: String
                    get() = AppConfiguration.stateValues.drawablePathIconSupplierDemandRadar
                override val iconRes: DrawableResource
                    get() = AppConfiguration.stateValues.drawableResIconSupplierDemandRadar.value
            }
        }

        sealed class Identity(route: String): Supplier(route) {
            data object Main: Identity("SupplierIdentityMainNavigationScreenModelRoute") {
                override val name: String
                    get() = with(AppConfiguration) { localizedStringResource(2490, "Supplier profiles") }
                override val iconPath: String
                    get() = AppConfiguration.stateValues.drawablePathIconSupplierRecoveryOwner
                override val iconRes: DrawableResource
                    get() = AppConfiguration.stateValues.drawableResIconSupplierRecoveryOwner.value
            }
        }
    }


    data object Notifications: NavigationScreenModel("NotificationsNavigationScreenModelRoute") {
        override val iconPath: String
            get() = AppConfiguration.stateValues.drawablePathIconTransactionHistory
        override val iconRes: DrawableResource
            get() = AppConfiguration.stateValues.drawableResIconTransactionHistory.value
        override val name: String
            get() = with(AppConfiguration) { localizedStringResource(177, "Notifications") }
    }

    sealed class Transaction(route: String): NavigationScreenModel(route) {

        data object MainSale: Transaction("TransactionMainSaleNavigationScreenModelRoute") {
            override val iconPath: String
                get() {
                    return AppConfiguration.stateValues.drawablePathIconTransactionSale
                }
            override val iconRes: DrawableResource
                get() {
                    return AppConfiguration.stateValues.drawableResIconTransactionSale.value
                }
            override val name: String
                get() = AppConfiguration.stateValues.stringSale
        }

        data object MainReturn: Transaction("TransactionMainReturnNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconTransactionReturn
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconTransactionReturn.value
            override val name: String
                get() = AppConfiguration.stateValues.stringReturn
        }

        data object MainSupply: Transaction("TransactionMainSupplyNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconTransactionSupply
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconTransactionSupply.value
            override val name: String
                get() = AppConfiguration.stateValues.stringSupply
        }

        data object Cart: Transaction("TransactionCartScreenNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconCart
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconCart.value
            override val name: String
                get() = AppConfiguration.stateValues.stringCart
        }

        data object Selection: Transaction("TransactionSelectionNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconTransactionSelection
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconTransactionSelection.value
            override val name: String
                get() = AppConfiguration.stateValues.stringSelect
        }

        data object Payment: Transaction("TransactionPaymentNavigationScreenModelRoute") {
            override val iconPath: String
                get() = ""
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconCart.value
            override val name: String
                get() = AppConfiguration.stateValues.stringPayment
        }

        data object ReceiptPreview: Transaction("TransactionReceiptPreviewNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconReceipt
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconReceipt.value
            override val name: String
                get() = AppConfiguration.stateValues.stringReceipt
        }
    }

    sealed class Stock(route: String): NavigationScreenModel(route) {

        data object Main: Stock("StockNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconStock
            override val name: String
                get() = AppConfiguration.stateValues.stringStock
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconStock.value
        }

        data object Warehouse: Stock("StockWarehouseNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconStock
            override val name: String
                get() = AppConfiguration.stateValues.stringStock
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconStock.value
        }

        data object AddEditGoodsItem: Stock("StockAddGoodsItemNavigationScreenModelRoute") {
            const val KEY_STATE_EDITED_GOODS_ITEM_ID: String = "keyState_editedGoodsItemId"
            const val KEY_STATE_BARCODE: String = "keyState_barcode"
            const val KEY_STATE_SALE_DATA: String = "keyState_saleData"
            const val KEY_STATE_RETURN_DATA: String = "keyState_returnData"
            const val KEY_STATE_SUPPLY_DATA: String = "keyState_supplyData"
            const val KEY_STATE_LAST_SELECTED_ROOT_CATEGORY_ID: String = "keyState_lastSelectedRootCategoryId"
            const val KEY_STATE_LAST_SELECTED_CATEGORY_ID: String = "keyState_lastSelectedCategoryId"

            const val KEY_STATE_QUANTITY_DATA: String = "keyState_quantityData"
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconAdd.value
        }

        data object GoodsItemDetails: Stock("StockGoodsItemDetailsNavigationScreenModelRoute") {
            const val KEY_STATE_GOODS_ITEM_ID: String = "keyState_goodsItemId"
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconStock.value
        }

        data object GoodsItemBatches: Stock("StockGoodsItemBatchesNavigationScreenModelRoute") {
            const val KEY_STATE_GOODS_ITEM_ID: String = "keyState_goodsItemId"
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconStock.value
        }

        data object GoodsItemSupplierPrices: Stock("StockGoodsItemSupplierPricesNavigationScreenModelRoute") {
            const val KEY_STATE_GOODS_ITEM_ID: String = "keyState_goodsItemId"
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconSuppliers.value
        }

        data object GoodsItemOrders: Stock("StockGoodsItemOrdersNavigationScreenModelRoute") {
            const val KEY_STATE_GOODS_ITEM_ID: String = "keyState_goodsItemId"
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconClipboard.value
        }
    }

    sealed class Menu(route: String): NavigationScreenModel(route) {

        data object Main: Menu("MenuNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconMenu
            override val name: String
                get() = AppConfiguration.stateValues.stringMenu
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconMenu.value
        }

        data object List: Menu("MenuListNavigationScreenModelRoute") {
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconMenu.value
        }

        data object UserAccount: Menu("MenuUserAccountNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconUserAccount
            override val name: String
                get() = AppConfiguration.stateValues.stringUserAccount
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconUserAccount.value
            const val KEY_STATE_CONFIRMATION_PASSWORD: String = "keyState_confirmationPassword"
        }

        /** Personal identity, not the permission-gated store Workers management screen. */
        data object Work: Menu("MenuWorkNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconWorkers
            override val name: String
                get() = with(AppConfiguration) { localizedStringResource(2658, "Work") }
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconWorkers.value
        }

        data object Notifications: Menu("MenuNotificationsNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconTransactionHistory
            override val name: String
                get() = with(AppConfiguration) { localizedStringResource(177, "Notifications") }
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconTransactionHistory.value
        }

        data object Finances: Menu("MenuFinancesNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconFinances
            override val name: String
                get() = AppConfiguration.stateValues.stringFinances
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconFinances.value
        }

        data object AppMode: Menu("MenuAppModeNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconSwitch
            override val name: String
                get() = AppConfiguration.stateValues.stringAppMode
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconSwitch.value
        }

        data object ShopWindow: Menu("MenuShopWindowNavigationScreenModelRoute") {
            override val name get() = with(AppConfiguration) { authUiText("Shop window", "Витрина", "Витрина", "Дүкөн витринасы") }
            override val iconPath get() = AppConfiguration.marketIconPath(142)
            override val iconRes get() = AppConfiguration.marketIconFallback(142)
        }

        data object StoreSubscription: Menu("MenuStoreSubscriptionNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconSubscription
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconSubscription.value
            override val name: String
                get() = AppConfiguration.stateValues.stringSubscription
        }

        data object StoreSubscriptionPlans: Menu("MenuStoreSubscriptionPlansNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconSubscription
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconSubscription.value
            override val name: String
                get() = AppConfiguration.stateValues.stringSubscriptionPlans
        }

        data object Workers: Menu("MenuWorkersNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconWorkers
            override val name: String
                get() = AppConfiguration.stateValues.stringWorkers
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconWorkers.value
        }
        data object AddEditWorker: Menu("MenuAddEditWorkerNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconWorkers
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconWorkers.value
            override val name: String
                get() = AppConfiguration.stateValues.stringAddWorker
        }

        data object Stores: Menu("MenuStoresNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconStores
            override val name: String
                get() = AppConfiguration.stateValues.stringStores
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconStores.value
        }
        data object AddEditStore: Menu("MenuAddEditStoreNavigationScreenModelRoute") {
            const val KEY_STATE_EDITED_STORE_ID: String = "keyState_editedStoreId"
            const val KEY_STATE_PARENT_STORE_ID: String = "keyState_parentStoreId"
            const val KEY_STATE_ALIAS: String = "keyState_alias"
            const val KEY_STATE_DESCRIPTION: String = "keyState_description"
            const val KEY_STATE_ADDRESS: String = "keyState_address"
            const val KEY_STATE_LEGAL_ID: String = "keyState_legalId"

            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconStores.value
        }

        data object Analytics: Menu("MenuAnalyticsNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconAnalytics
            override val name: String
                get() = AppConfiguration.stateValues.stringAnalytics
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconAnalytics.value
        }

        data object TransactionHistory: Menu("MenuTransactionHistoryNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconTransactionHistory
            override val name: String
                get() = AppConfiguration.stateValues.stringTransactionHistory
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconTransactionHistory.value
        }
        data object TransactionHistoryReceiptPreview: Menu("MenuTransactionHistoryReceiptPreviewNavigationScreenModelRoute") {
            const val KEY_STATE_TRANSACTION_ID: String = "keyState_transactionId"

            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconReceipt
            override val name: String
                get() = with(AppConfiguration) { localizedStringResource(418, "Receipt preview") }
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconReceipt.value
        }

        data object OperationLogs: Menu("MenuOperationLogsNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconLog
            override val name: String
                get() = with(AppConfiguration) { localizedStringResource(662, "Operation logs") }
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconLog.value
        }

        data object Debtors: Menu("MenuDebtorsNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconDebtors
            override val name: String
                get() = AppConfiguration.stateValues.stringDebtors
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconDebtors.value
        }
        data object CloseDebt: Menu("MenuCloseDebtNavigationScreenModelRoute") {
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconDebtors.value
        }

        data object Suppliers: Menu("MenuSuppliersNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconSuppliers
            override val name: String
                get() = AppConfiguration.stateValues.stringSuppliers
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconSuppliers.value
        }
        data object AddEditSupplier: Menu("MenuAddEditSupplierNavigationScreenModelRoute") {
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconSuppliers.value
        }

        data object GoodsCategories: Menu("MenuGoodsCategoriesNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconGoodsCategories
            override val name: String
                get() = AppConfiguration.stateValues.stringGoodsCategories
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconGoodsCategories.value
        }
        data object AddEditGoodsCategory: Menu("MenuAddEditGoodsCategoryNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconGoodsCategories
            override val name: String
                get() = AppConfiguration.stateValues.stringGoodsCategories
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconGoodsCategories.value
        }

        data object Devices: Menu("MenuDevicesNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconDevices
            override val name: String
                get() = AppConfiguration.stateValues.stringDevices
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconDevices.value
        }
        data object Security: Menu("MenuSecurityNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconSecurity
            override val name: String
                get() = with(AppConfiguration) {
                    authUiText("Sign-in & security", "Вход и безопасность", "Кіру және қауіпсіздік", "Кирүү жана коопсуздук")
                }
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconSecurity.value
        }
        data object Support: Menu("MenuSupportNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconSupport
            override val name: String
                get() = with(AppConfiguration) { localizedStringResource(813, "Support") }
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconSupport.value
        }
        data object AppLanguage: Menu("MenuAppLanguageNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconAppLanguage
            override val name: String
                get() = AppConfiguration.stateValues.stringAppLanguage
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconAppLanguage.value
        }
        data object AppTheme: Menu("MenuAppThemeNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconAppTheme
            override val name: String
                get() = AppConfiguration.stateValues.stringAppTheme
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconAppTheme.value
        }
        data object AppScale: Menu("MenuAppScaleNavigationScreenModelRoute") {
            override val iconPath: String
                get() = AppConfiguration.stateValues.drawablePathIconAppScale
            override val name: String
                get() = with(AppConfiguration) { localizedStringResource(910, "Interface scale") }
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconAppScale.value
        }
    }

    sealed class UserAuth(route: String): NavigationScreenModel(route) {

        data object Main: UserAuth("UserAuthNavigationScreenModelRoute") {
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconPassword.value
        }

        data object LogIn: UserAuth("UserAuthLogInNavigationScreenModelRoute") {
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconPerson.value
        }

        data object SignUp: UserAuth("UserAuthSignUpNavigationScreenModelRoute") {
            override val iconRes: DrawableResource
                get() = AppConfiguration.stateValues.drawableResIconAdd.value
        }
    }

    object Splash: NavigationScreenModel("SplashNavigationScreenModelRoute") {
        override val iconPath: String
            get() = AppConfiguration.stateValues.drawablePathAITALogo
        override val iconRes: DrawableResource
            get() = AppConfiguration.stateValues.drawableResAITALogo.value
    }
}

@kotlinx.serialization.Serializable
internal data class PersistedTransactionNavigationSectionDataModel(
    val clientId: Int = 0,
    val left: List<List<String>> = emptyList(),
    val right: List<List<String>> = emptyList()
)

@kotlinx.serialization.Serializable
internal data class PersistedTransactionNavigationStateDataModel(
    val sale: PersistedTransactionNavigationSectionDataModel = PersistedTransactionNavigationSectionDataModel(),
    val returns: PersistedTransactionNavigationSectionDataModel = PersistedTransactionNavigationSectionDataModel(),
    val supply: PersistedTransactionNavigationSectionDataModel = PersistedTransactionNavigationSectionDataModel()
)


@kotlinx.serialization.Serializable
internal data class PersistedSplitNavigationStackDataModel(
    val left: List<String> = emptyList(),
    val right: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
internal data class PersistedAppNavigationStateDataModel(
    val main: List<String> = emptyList(),
    val stock: PersistedSplitNavigationStackDataModel = PersistedSplitNavigationStackDataModel(),
    val menu: PersistedSplitNavigationStackDataModel = PersistedSplitNavigationStackDataModel(),
    val userAuth: PersistedSplitNavigationStackDataModel = PersistedSplitNavigationStackDataModel(),
    val stateHosts: Map<String, Map<String, String>> = emptyMap(),
    val updatedAtMillis: Long = 0L
)

internal const val APP_NAVIGATION_CACHE_KEY = "cache_json:app_navigation_state_v1"
internal val appNavigationRestoredState = MutableStateFlow(false)

internal fun persistentAppNavigationScreens(): List<NavigationScreenModel> = listOf(
    NavigationScreenModel.Splash,
    NavigationScreenModel.Notifications,
    NavigationScreenModel.Transaction.MainSale,
    NavigationScreenModel.Transaction.MainReturn,
    NavigationScreenModel.Transaction.MainSupply,
    NavigationScreenModel.Transaction.Cart,
    NavigationScreenModel.Transaction.Selection,
    NavigationScreenModel.Transaction.Payment,
    NavigationScreenModel.Transaction.ReceiptPreview,
    NavigationScreenModel.Stock.Main,
    NavigationScreenModel.Stock.Warehouse,
    NavigationScreenModel.Stock.AddEditGoodsItem,
    NavigationScreenModel.Stock.GoodsItemDetails,
    NavigationScreenModel.Stock.GoodsItemBatches,
    NavigationScreenModel.Stock.GoodsItemSupplierPrices,
    NavigationScreenModel.Stock.GoodsItemOrders,
    NavigationScreenModel.Menu.Main,
    NavigationScreenModel.Menu.List,
    NavigationScreenModel.Menu.UserAccount,
    NavigationScreenModel.Menu.Work,
    NavigationScreenModel.Menu.Notifications,
    NavigationScreenModel.Menu.Finances,
    NavigationScreenModel.Menu.AppMode,
    NavigationScreenModel.Menu.StoreSubscription,
    NavigationScreenModel.Menu.StoreSubscriptionPlans,
    NavigationScreenModel.Menu.ShopWindow,
    NavigationScreenModel.Menu.Workers,
    NavigationScreenModel.Menu.AddEditWorker,
    NavigationScreenModel.Menu.Stores,
    NavigationScreenModel.Menu.AddEditStore,
    NavigationScreenModel.Menu.Analytics,
    NavigationScreenModel.Menu.TransactionHistory,
    NavigationScreenModel.Menu.TransactionHistoryReceiptPreview,
    NavigationScreenModel.Menu.OperationLogs,
    NavigationScreenModel.Menu.Debtors,
    NavigationScreenModel.Menu.CloseDebt,
    NavigationScreenModel.Menu.Suppliers,
    NavigationScreenModel.Menu.AddEditSupplier,
    NavigationScreenModel.Menu.GoodsCategories,
    NavigationScreenModel.Menu.AddEditGoodsCategory,
    NavigationScreenModel.Menu.Devices,
    NavigationScreenModel.Menu.Security,
    NavigationScreenModel.Menu.Support,
    NavigationScreenModel.Menu.AppLanguage,
    NavigationScreenModel.Menu.AppTheme,
    NavigationScreenModel.Menu.AppScale,
    NavigationScreenModel.UserAuth.Main,
    NavigationScreenModel.UserAuth.LogIn,
    NavigationScreenModel.UserAuth.SignUp,
    NavigationScreenModel.Buyer.Main.Home,
    NavigationScreenModel.Buyer.Main.Search,
    NavigationScreenModel.Buyer.Main.Saved,
    NavigationScreenModel.Buyer.Main.Shopping,
    NavigationScreenModel.Buyer.Cart.Main,
    NavigationScreenModel.Buyer.Orders.Main,
    NavigationScreenModel.Supplier.Orders.Main,
    NavigationScreenModel.Supplier.Catalog.Main,
    NavigationScreenModel.Supplier.Contracts.Main,
    NavigationScreenModel.Supplier.Dispatch.Main,
    NavigationScreenModel.Supplier.Customers.Main,
    NavigationScreenModel.Supplier.Analytics.Main,
    NavigationScreenModel.Supplier.Identity.Main
)

internal fun persistentAppRouteToScreen(route: String): NavigationScreenModel? =
    persistentAppNavigationScreens().firstOrNull { it.route == route }

internal fun NavigationScreenModel.toPersistentAppRoute(): String = route

internal fun Map<String, String>.withoutSensitiveTransientUiState(): Map<String, String> =
    filterKeys { key ->
        val normalized = key.lowercase()
        !normalized.contains("password") && !normalized.contains("confirmation")
    }

internal fun persistentAppStateHostsSnapshot(): Map<String, Map<String, String>> =
    persistentAppNavigationScreens()
        .associate { screen -> screen.route to screen.state.value.withoutSensitiveTransientUiState() }
        .filterValues { it.isNotEmpty() }

internal suspend fun restorePersistentAppStateHosts(stateHosts: Map<String, Map<String, String>>) {
    stateHosts.forEach { (route, stateMap) ->
        val host = persistentAppRouteToScreen(route) ?: return@forEach
        stateMap.withoutSensitiveTransientUiState().forEach { (key, value) ->
            if (key.isNotBlank()) host.setState(key to value)
        }
    }
}

internal fun List<NavigationScreenModel>.toPersistentAppRoutes(): List<String> =
    map { it.toPersistentAppRoute() }

internal fun defaultMainScreenForAppMode(modeId: Int): NavigationScreenModel = when (modeId) {
    APP_MODE_SUPPLIER -> NavigationScreenModel.Supplier.Orders.Main
    APP_MODE_BUYER -> NavigationScreenModel.Buyer.Main.Home
    APP_MODE_MANUFACTURER -> NavigationScreenModel.Supplier.Catalog.Main
    else -> NavigationScreenModel.Transaction.MainSale
}

internal fun NavigationScreenModel.isMainScreenCompatibleWithAppMode(modeId: Int): Boolean = when {
    this is NavigationScreenModel.Splash -> true
    this is NavigationScreenModel.UserAuth -> true
    this is NavigationScreenModel.Menu -> true
    this is NavigationScreenModel.Notifications -> true
    this is NavigationScreenModel.Supplier -> modeId == APP_MODE_SUPPLIER || modeId == APP_MODE_MANUFACTURER
    this is NavigationScreenModel.Buyer -> modeId == APP_MODE_BUYER
    this is NavigationScreenModel.Transaction -> modeId == APP_MODE_STORE
    this is NavigationScreenModel.Stock -> modeId == APP_MODE_STORE
    else -> true
}

internal fun List<String>?.toPersistentMainStack(): List<NavigationScreenModel> {
    val modeId = appModeState.value
    val restoredCurrent = orEmpty()
        .mapNotNull { persistentAppRouteToScreen(it) }
        .filterNot { it.route == NavigationScreenModel.Splash.route }
        .lastOrNull { it.isMainScreenCompatibleWithAppMode(modeId) }
        ?: defaultMainScreenForAppMode(modeId)

    return listOf(restoredCurrent)
}

internal fun List<String>?.toPersistentStockStack(defaultFirst: NavigationScreenModel.Stock): List<NavigationScreenModel.Stock> {
    val restoredCurrent = orEmpty()
        .mapNotNull { persistentAppRouteToScreen(it) as? NavigationScreenModel.Stock }
        .lastOrNull()
        ?: defaultFirst

    return if (restoredCurrent.route == defaultFirst.route) {
        listOf(defaultFirst)
    } else {
        listOf(defaultFirst, restoredCurrent)
    }
}

/**
 * Respect the release's app-mode availability flag for both menu rows and restored routes.
 * When disabled, indirect callers must not reopen the selector either.
 */
internal fun NavigationScreenModel.Menu.isTemporarilyHiddenFromUi(): Boolean =
    this == NavigationScreenModel.Menu.AppMode && !APP_MODE_SELECTION_PUBLICLY_ENABLED

internal fun List<String>?.toPersistentMenuStack(defaultFirst: NavigationScreenModel.Menu): List<NavigationScreenModel.Menu> {
    val restoredCurrent = orEmpty()
        .mapNotNull { persistentAppRouteToScreen(it) as? NavigationScreenModel.Menu }
        .filterNot { it.isTemporarilyHiddenFromUi() }
        .lastOrNull()
        ?: defaultFirst

    return if (restoredCurrent.route == defaultFirst.route) {
        listOf(defaultFirst)
    } else {
        listOf(defaultFirst, restoredCurrent)
    }
}

internal fun List<String>?.toPersistentUserAuthStack(defaultFirst: NavigationScreenModel.UserAuth): List<NavigationScreenModel.UserAuth> {
    val restoredCurrent = orEmpty()
        .mapNotNull { persistentAppRouteToScreen(it) as? NavigationScreenModel.UserAuth }
        .lastOrNull()
        ?: defaultFirst

    return if (restoredCurrent.route == defaultFirst.route) {
        listOf(defaultFirst)
    } else {
        listOf(defaultFirst, restoredCurrent)
    }
}

internal fun NavigationScreenModel.Transaction.toPersistentTransactionRoute(): String = route

internal fun persistentTransactionRouteToScreen(route: String): NavigationScreenModel.Transaction? = when (route) {
    NavigationScreenModel.Transaction.Cart.route -> NavigationScreenModel.Transaction.Cart
    NavigationScreenModel.Transaction.Selection.route -> NavigationScreenModel.Transaction.Selection
    NavigationScreenModel.Transaction.Payment.route -> NavigationScreenModel.Transaction.Payment
    NavigationScreenModel.Transaction.ReceiptPreview.route -> NavigationScreenModel.Transaction.ReceiptPreview
    else -> null
}

internal fun List<NavigationScreenModel.Transaction>.toPersistentTransactionRoutes(): List<String> =
    map { it.toPersistentTransactionRoute() }

internal fun <T : NavigationScreenModel> List<T>.toCompactPersistentRoutes(defaultFirst: T): List<String> {
    val current = lastOrNull()
    return when {
        current == null -> listOf(defaultFirst.route)
        current.route == defaultFirst.route -> listOf(defaultFirst.route)
        else -> listOf(defaultFirst.route, current.route)
    }
}

internal fun List<NavigationScreenModel>.toCompactPersistentMainRoutes(): List<String> =
    lastOrNull()
        ?.takeIf { it.route != NavigationScreenModel.Splash.route }
        ?.let { listOf(it.route) }
        ?: listOf(defaultMainScreenForAppMode(appModeState.value).route)

internal fun List<String>?.toPersistentTransactionStack(
    defaultFirst: NavigationScreenModel.Transaction
): List<NavigationScreenModel.Transaction> {
    val restoredCurrent = orEmpty()
        .mapNotNull { persistentTransactionRouteToScreen(it) }
        .lastOrNull()
        ?: defaultFirst

    return if (restoredCurrent.route == defaultFirst.route) {
        listOf(defaultFirst)
    } else {
        listOf(defaultFirst, restoredCurrent)
    }
}

internal const val TRANSACTION_NAVIGATION_CACHE_KEY = "cache_json:transaction_navigation_state_v1"
internal val transactionNavigationRestoredState = MutableStateFlow(false)

object Navigation {
    val bottomNavBarScreensStore = listOf(
        NavigationScreenModel.Transaction.MainSale,
        NavigationScreenModel.Transaction.MainReturn,
        NavigationScreenModel.Transaction.MainSupply,
        NavigationScreenModel.Stock.Main,
        NavigationScreenModel.Menu.Main
    )

    val bottomNavBarScreensBuyer = listOf(
        NavigationScreenModel.Buyer.Main.Home,
        NavigationScreenModel.Buyer.Main.Saved,
        NavigationScreenModel.Buyer.Main.Shopping,
        NavigationScreenModel.Menu.Main
    )

    val bottomNavBarScreensSupplier = listOf(
        NavigationScreenModel.Supplier.Orders.Main,
        NavigationScreenModel.Supplier.Catalog.Main,
        NavigationScreenModel.Supplier.Contracts.Main,
        NavigationScreenModel.Supplier.Dispatch.Main,
        NavigationScreenModel.Supplier.Customers.Main,
        NavigationScreenModel.Menu.Main
    )

    private val _Main =
        MutableStateFlow<List<NavigationScreenModel>>(
            listOf(
                NavigationScreenModel.Splash
            )
        )
    val Main = _Main.asStateFlow()

    private var appNavigationPersistenceStarted = false

    private fun appNavigationSnapshot(): PersistedAppNavigationStateDataModel =
        PersistedAppNavigationStateDataModel(
            main = Main.value.toCompactPersistentMainRoutes(),
            stock = Stock.persistentSnapshot(),
            menu = Menu.persistentSnapshot(),
            userAuth = UserAuth.persistentSnapshot(),
            stateHosts = persistentAppStateHostsSnapshot()
        )

    private suspend fun restorePersistedAppNavigation() {
        try {
            val snapshot = runCatching {
                getLocalKv(APP_NAVIGATION_CACHE_KEY)
                    ?.let { jsonBase.decodeFromString<PersistedAppNavigationStateDataModel>(it) }
            }.getOrNull() ?: return

            restorePersistentAppStateHosts(snapshot.stateHosts)
            _Main.emit(snapshot.main.toPersistentMainStack())
            Stock.restorePersistentSnapshot(snapshot.stock)
            Menu.restorePersistentSnapshot(snapshot.menu)
            UserAuth.restorePersistentSnapshot(snapshot.userAuth)
        } finally {
            appNavigationRestoredState.emit(true)
        }
    }

    suspend fun awaitAppNavigationRestore() {
        while (!appNavigationRestoredState.value) {
            delay(10)
        }
    }

    fun startAppNavigationPersistence() {
        if (appNavigationPersistenceStarted) return
        appNavigationPersistenceStarted = true

        CoroutineScope(Dispatchers.ourIo).launch {
            restorePersistedAppNavigation()
            var lastRaw = ""
            while (true) {
                val raw = runCatching { jsonBase.encodeToString(appNavigationSnapshot()) }.getOrNull().orEmpty()
                if (raw.isNotBlank() && raw != lastRaw) {
                    putLocalKv(APP_NAVIGATION_CACHE_KEY, raw)
                    lastRaw = raw
                }
                delay(320)
            }
        }
    }

    suspend fun showSubscriptionRecovery() {
        // Replace both saved menu panes, not the customer's carts/editor state hosts.
        Menu.clearLeft()
        val recovery = if (activeStoreIdState.value.isNullOrBlank()) NavigationScreenModel.Menu.Stores
            else NavigationScreenModel.Menu.StoreSubscriptionPlans
        if (AppConfiguration.stateValues.isNarrowScreen) {
            Menu.clearRight()
            Menu.goLeft(recovery)
        } else Menu.clearRight(recovery)
        _Main.emit(listOf(NavigationScreenModel.Menu.Main))
    }

    suspend fun goMain(model: NavigationScreenModel) {
        if (appModeState.value == APP_MODE_STORE &&
            (model is NavigationScreenModel.Stock || model is NavigationScreenModel.Transaction) &&
            currentStoreSubscriptionGate(activeStoreIdState.value) == StoreSubscriptionGate.Required) {
            showSubscriptionRecovery()
            return
        }
        if (model.route != _Main.value.last().route)
            _Main.emit(listOf(model))
    }

    fun getCurrentTransactionScreens(transactionTypeIndex: Int, clientId: Int, isNarrowScreen: Boolean): StateFlow<List<NavigationScreenModel.Transaction>> {
        return when (transactionTypeIndex) {
            0 -> {
                when (clientId) {
                    0 -> if (isNarrowScreen) TransactionSale.LeftClient1 else TransactionSale.RightClient1
                    1 -> if (isNarrowScreen) TransactionSale.LeftClient2 else TransactionSale.RightClient2
                    2 -> if (isNarrowScreen) TransactionSale.LeftClient3 else TransactionSale.RightClient3
                    3 -> if (isNarrowScreen) TransactionSale.LeftClient4 else TransactionSale.RightClient4
                    else -> if (isNarrowScreen) TransactionSale.LeftClient5 else TransactionSale.RightClient5
                }
            }
            1 -> {
                when (clientId) {
                    0 -> if (isNarrowScreen) TransactionReturn.LeftClient1 else TransactionReturn.RightClient1
                    1 -> if (isNarrowScreen) TransactionReturn.LeftClient2 else TransactionReturn.RightClient2
                    2 -> if (isNarrowScreen) TransactionReturn.LeftClient3 else TransactionReturn.RightClient3
                    3 -> if (isNarrowScreen) TransactionReturn.LeftClient4 else TransactionReturn.RightClient4
                    else -> if (isNarrowScreen) TransactionReturn.LeftClient5 else TransactionReturn.RightClient5
                }
            }
            else -> {
                when (clientId) {
                    0 -> if (isNarrowScreen) TransactionSupply.LeftClient1 else TransactionSupply.RightClient1
                    1 -> if (isNarrowScreen) TransactionSupply.LeftClient2 else TransactionSupply.RightClient2
                    2 -> if (isNarrowScreen) TransactionSupply.LeftClient3 else TransactionSupply.RightClient3
                    3 -> if (isNarrowScreen) TransactionSupply.LeftClient4 else TransactionSupply.RightClient4
                    else -> if (isNarrowScreen) TransactionSupply.LeftClient5 else TransactionSupply.RightClient5
                }
            }
        }
    }

    private var transactionNavigationPersistenceStarted = false

    private fun transactionNavigationSnapshot(): PersistedTransactionNavigationStateDataModel =
        PersistedTransactionNavigationStateDataModel(
            sale = TransactionSale.persistentSnapshot(),
            returns = TransactionReturn.persistentSnapshot(),
            supply = TransactionSupply.persistentSnapshot()
        )

    private suspend fun restorePersistedTransactionNavigation() {
        try {
            val snapshot = runCatching {
                getLocalKv(TRANSACTION_NAVIGATION_CACHE_KEY)
                    ?.let { jsonBase.decodeFromString<PersistedTransactionNavigationStateDataModel>(it) }
            }.getOrNull() ?: return

            TransactionSale.restorePersistentSnapshot(snapshot.sale)
            TransactionReturn.restorePersistentSnapshot(snapshot.returns)
            TransactionSupply.restorePersistentSnapshot(snapshot.supply)
        } finally {
            transactionNavigationRestoredState.emit(true)
        }
    }

    suspend fun awaitTransactionNavigationRestore() {
        while (!transactionNavigationRestoredState.value) {
            delay(10)
        }
    }

    fun startTransactionNavigationPersistence() {
        if (transactionNavigationPersistenceStarted) return
        transactionNavigationPersistenceStarted = true

        CoroutineScope(Dispatchers.ourIo).launch {
            restorePersistedTransactionNavigation()
            var lastRaw = ""
            while (true) {
                val raw = runCatching { jsonBase.encodeToString(transactionNavigationSnapshot()) }.getOrNull().orEmpty()
                if (raw.isNotBlank() && raw != lastRaw) {
                    putLocalKv(TRANSACTION_NAVIGATION_CACHE_KEY, raw)
                    lastRaw = raw
                }
                delay(220)
            }
        }
    }

    suspend fun resetStoreScopedNavigationForNoActiveStore() {
        TransactionSale.setClientId(0)
        TransactionSale.clearLeftClient1()
        TransactionSale.clearLeftClient2()
        TransactionSale.clearLeftClient3()
        TransactionSale.clearLeftClient4()
        TransactionSale.clearLeftClient5()
        TransactionSale.clearRightClient1()
        TransactionSale.clearRightClient2()
        TransactionSale.clearRightClient3()
        TransactionSale.clearRightClient4()
        TransactionSale.clearRightClient5()

        TransactionReturn.setClientId(0)
        TransactionReturn.clearLeftClient1()
        TransactionReturn.clearLeftClient2()
        TransactionReturn.clearLeftClient3()
        TransactionReturn.clearLeftClient4()
        TransactionReturn.clearLeftClient5()
        TransactionReturn.clearRightClient1()
        TransactionReturn.clearRightClient2()
        TransactionReturn.clearRightClient3()
        TransactionReturn.clearRightClient4()
        TransactionReturn.clearRightClient5()

        TransactionSupply.setClientId(0)
        TransactionSupply.clearLeftClient1()
        TransactionSupply.clearLeftClient2()
        TransactionSupply.clearLeftClient3()
        TransactionSupply.clearLeftClient4()
        TransactionSupply.clearLeftClient5()
        TransactionSupply.clearRightClient1()
        TransactionSupply.clearRightClient2()
        TransactionSupply.clearRightClient3()
        TransactionSupply.clearRightClient4()
        TransactionSupply.clearRightClient5()

        Stock.clearLeft()
        Stock.clearRight()

        if (Main.value.last() is NavigationScreenModel.Stock) {
            goMain(NavigationScreenModel.Transaction.MainSale)
        }
    }

    object TransactionSale {

        suspend fun go(
            model: NavigationScreenModel.Transaction,
            isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen,
            remove: Boolean = false
        ) {
            if (isNarrowScreen) {
                when (ClientId.value) {
                    0 -> goLeftClient1(model, remove)
                    1 -> goLeftClient2(model, remove)
                    2 -> goLeftClient3(model, remove)
                    3 -> goLeftClient4(model, remove)
                    else -> goLeftClient5(model, remove)
                }
            } else {
                when (ClientId.value) {
                    0 -> goRightClient1(model, remove)
                    1 -> goRightClient2(model, remove)
                    2 -> goRightClient3(model, remove)
                    3 -> goRightClient4(model, remove)
                    else -> goRightClient5(model, remove)
                }
            }
        }

        suspend fun pop(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (isNarrowScreen) {
                when (ClientId.value) {
                    0 -> popLeftClient1(navigateAfterwards)
                    1 -> popLeftClient2(navigateAfterwards)
                    2 -> popLeftClient3(navigateAfterwards)
                    3 -> popLeftClient4(navigateAfterwards)
                    else -> popLeftClient5(navigateAfterwards)
                }
            } else {
                when (ClientId.value) {
                    0 -> popRightClient1(navigateAfterwards)
                    1 -> popRightClient2(navigateAfterwards)
                    2 -> popRightClient3(navigateAfterwards)
                    3 -> popRightClient4(navigateAfterwards)
                    else -> popRightClient5(navigateAfterwards)
                }
            }
        }

        suspend fun clear(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen) {
            if (isNarrowScreen) {
                when (ClientId.value) {
                    0 -> clearLeftClient1()
                    1 -> clearLeftClient2()
                    2 -> clearLeftClient3()
                    3 -> clearLeftClient4()
                    else -> clearLeftClient5()
                }
            } else {
                when (ClientId.value) {
                    0 -> clearRightClient1()
                    1 -> clearRightClient2()
                    2 -> clearRightClient3()
                    3 -> clearRightClient4()
                    else -> clearRightClient5()
                }
            }
        }

        fun isVeryFirstScreen(isNarrowScreen: Boolean, clientId: Int): Boolean {
            return (
                if (isNarrowScreen)
                    when (clientId) {
                        0 -> _LeftClient1
                        1 -> _LeftClient2
                        2 -> _LeftClient3
                        3 -> _LeftClient4
                        else -> _LeftClient5
                    }
                else
                    when (clientId) {
                        0 -> _RightClient1
                        1 -> _RightClient2
                        2 -> _RightClient3
                        3 -> _RightClient4
                        else -> _RightClient5
                    }
                ).value.size == 1
        }

        fun isVeryFirstScreenLeft(clientId: Int): Boolean {
            return (when (clientId) {
                0 -> _LeftClient1
                1 -> _LeftClient2
                2 -> _LeftClient3
                3 -> _LeftClient4
                else -> _LeftClient5
            }).value.size == 1
        }

        fun isVeryFirstScreenRight(clientId: Int): Boolean {
            return (when (clientId) {
                0 -> _RightClient1
                1 -> _RightClient2
                2 -> _RightClient3
                3 -> _RightClient4
                else -> _RightClient5
            }).value.size == 1
        }

        private val _ClientId =
            MutableStateFlow(0)
        val ClientId =
            _ClientId.asStateFlow()

        suspend fun setClientId(id: Int) {
            _ClientId.emit(id)
        }

        private val _LeftClient1 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient1 =
            _LeftClient1.asStateFlow()

        private val _LeftClient2 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient2 =
            _LeftClient2.asStateFlow()

        private val _LeftClient3 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient3 =
            _LeftClient3.asStateFlow()

        private val _LeftClient4 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient4 =
            _LeftClient4.asStateFlow()

        private val _LeftClient5 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient5 =
            _LeftClient5.asStateFlow()

        private val _RightClient1 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient1 =
            _RightClient1.asStateFlow()

        private val _RightClient2 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient2 =
            _RightClient2.asStateFlow()

        private val _RightClient3 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient3 =
            _RightClient3.asStateFlow()

        private val _RightClient4 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient4 =
            _RightClient4.asStateFlow()

        private val _RightClient5 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient5 =
            _RightClient5.asStateFlow()

        internal fun persistentSnapshot(): PersistedTransactionNavigationSectionDataModel =
            PersistedTransactionNavigationSectionDataModel(
                clientId = ClientId.value.coerceIn(0, 4),
                left = listOf(
                    LeftClient1.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient2.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient3.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient4.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient5.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart)
                ),
                right = listOf(
                    RightClient1.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient2.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient3.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient4.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient5.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection)
                )
            )

        internal suspend fun restorePersistentSnapshot(snapshot: PersistedTransactionNavigationSectionDataModel) {
            _ClientId.emit(snapshot.clientId.coerceIn(0, 4))
            _LeftClient1.emit(snapshot.left.getOrNull(0).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient2.emit(snapshot.left.getOrNull(1).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient3.emit(snapshot.left.getOrNull(2).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient4.emit(snapshot.left.getOrNull(3).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient5.emit(snapshot.left.getOrNull(4).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _RightClient1.emit(snapshot.right.getOrNull(0).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient2.emit(snapshot.right.getOrNull(1).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient3.emit(snapshot.right.getOrNull(2).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient4.emit(snapshot.right.getOrNull(3).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient5.emit(snapshot.right.getOrNull(4).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
        }

        suspend fun goLeftClient1(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient1.value.last()::class)
                _LeftClient1.emit(
                    _LeftClient1
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient1.value.last()::class == _LeftClient1.value.last()::class)
                popRightClient1()

            val oldSize = _LeftClient1.value.size

            _LeftClient1.emit(
                _LeftClient1.value.toMutableList()
                    .apply {
                        if (_LeftClient1.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient1.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient1(this@run)
            }
        }

        suspend fun clearLeftClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient1.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient2(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient2.value.last()::class)
                _LeftClient2.emit(
                    _LeftClient2
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient2.value.last()::class == _LeftClient2.value.last()::class)
                popRightClient2()

            val oldSize = _LeftClient2.value.size

            _LeftClient2.emit(
                _LeftClient2.value.toMutableList()
                    .apply {
                        if (_LeftClient2.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient2.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient2(this@run)
            }
        }

        suspend fun clearLeftClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient2.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient3(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient3.value.last()::class)
                _LeftClient3.emit(
                    _LeftClient3
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient3.value.last()::class == _LeftClient3.value.last()::class)
                popRightClient3()

            val oldSize = _LeftClient3.value.size

            _LeftClient3.emit(
                _LeftClient3.value.toMutableList()
                    .apply {
                        if (_LeftClient3.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient3.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient3(this@run)
            }
        }

        suspend fun clearLeftClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient3.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient4(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient4.value.last()::class)
                _LeftClient4.emit(
                    _LeftClient4
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient4.value.last()::class == _LeftClient4.value.last()::class)
                popRightClient4()

            val oldSize = _LeftClient4.value.size

            _LeftClient4.emit(
                _LeftClient4.value.toMutableList()
                    .apply {
                        if (_LeftClient4.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient4.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient4(this@run)
            }
        }

        suspend fun clearLeftClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient4.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient5(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient5.value.last()::class)
                _LeftClient5.emit(
                    _LeftClient5
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient5.value.last()::class == _LeftClient5.value.last()::class)
                popRightClient5()

            val oldSize = _LeftClient5.value.size

            _LeftClient5.emit(
                _LeftClient5.value.toMutableList()
                    .apply {
                        if (_LeftClient5.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient5.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient5(this@run)
            }
        }

        suspend fun clearLeftClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient5.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient1(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient1.value.last()::class)
                _RightClient1.emit(
                    _RightClient1
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient1.value.size

            _RightClient1.emit(
                _RightClient1.value.toMutableList()
                    .apply {
                        if (_RightClient1.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient1.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient1(this@run)
            }
        }

        suspend fun clearRightClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient1.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient2(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient2.value.last()::class)
                _RightClient2.emit(
                    _RightClient2
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient2.value.size

            _RightClient2.emit(
                _RightClient2.value.toMutableList()
                    .apply {
                        if (_RightClient2.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient2.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient2(this@run)
            }
        }

        suspend fun clearRightClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient2.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient3(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient3.value.last()::class)
                _RightClient3.emit(
                    _RightClient3
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient3.value.size

            _RightClient3.emit(
                _RightClient3.value.toMutableList()
                    .apply {
                        if (_RightClient3.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient3.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient3(this@run)
            }
        }

        suspend fun clearRightClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient3.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient4(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient4.value.last()::class)
                _RightClient4.emit(
                    _RightClient4
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient4.value.size

            _RightClient4.emit(
                _RightClient4.value.toMutableList()
                    .apply {
                        if (_RightClient4.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient4.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient4(this@run)
            }
        }

        suspend fun clearRightClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient4.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient5(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient5.value.last()::class)
                _RightClient5.emit(
                    _RightClient5
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient5.value.size

            _RightClient5.emit(
                _RightClient5.value.toMutableList()
                    .apply {
                        if (_RightClient5.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient5.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient5(this@run)
            }
        }

        suspend fun clearRightClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient5.emit(
                listOf(model)
            )
        }

        suspend fun init(isNarrowScreen: Boolean) {
            TransactionSale.run {
                if (isNarrowScreen) {
                    if (RightClient1.value.size > 1) {
                        _LeftClient1.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient1.value.subList(1, RightClient1.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient1()

                    if (RightClient2.value.size > 1) {
                        _LeftClient2.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient2.value.subList(1, RightClient2.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient2()

                    if (RightClient3.value.size > 1) {
                        _LeftClient3.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient3.value.subList(1, RightClient3.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient3()

                    if (RightClient4.value.size > 1) {
                        _LeftClient4.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient4.value.subList(1, RightClient4.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient4()

                    if (RightClient5.value.size > 1) {
                        _LeftClient5.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient5.value.subList(1, RightClient5.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient5()

                } else {
                    if (LeftClient1.value.size > 1) {
                        _RightClient1.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient1.value.subList(1, LeftClient1.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient1()

                    if (LeftClient2.value.size > 1) {
                        _RightClient2.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient2.value.subList(1, LeftClient2.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient2()

                    if (LeftClient3.value.size > 1) {
                        _RightClient3.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient3.value.subList(1, LeftClient3.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient3()

                    if (LeftClient4.value.size > 1) {
                        _RightClient4.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient4.value.subList(1, LeftClient4.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient4()

                    if (LeftClient5.value.size > 1) {
                        _RightClient5.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient5.value.subList(1, LeftClient5.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient5()
                }
            }
        }
    }

    object TransactionReturn {

        suspend fun go(
            model: NavigationScreenModel.Transaction,
            isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen,
            remove: Boolean = false
        ) {
            if (isNarrowScreen) {
                when (ClientId.value) {
                    0 -> goLeftClient1(model, remove)
                    1 -> goLeftClient2(model, remove)
                    2 -> goLeftClient3(model, remove)
                    3 -> goLeftClient4(model, remove)
                    else -> goLeftClient5(model, remove)
                }
            } else {
                when (ClientId.value) {
                    0 -> goRightClient1(model, remove)
                    1 -> goRightClient2(model, remove)
                    2 -> goRightClient3(model, remove)
                    3 -> goRightClient4(model, remove)
                    else -> goRightClient5(model, remove)
                }
            }
        }

        suspend fun pop(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (isNarrowScreen) {
                when (ClientId.value) {
                    0 -> popLeftClient1(navigateAfterwards)
                    1 -> popLeftClient2(navigateAfterwards)
                    2 -> popLeftClient3(navigateAfterwards)
                    3 -> popLeftClient4(navigateAfterwards)
                    else -> popLeftClient5(navigateAfterwards)
                }
            } else {
                when (ClientId.value) {
                    0 -> popRightClient1(navigateAfterwards)
                    1 -> popRightClient2(navigateAfterwards)
                    2 -> popRightClient3(navigateAfterwards)
                    3 -> popRightClient4(navigateAfterwards)
                    else -> popRightClient5(navigateAfterwards)
                }
            }
        }

        suspend fun clear(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen) {
            if (isNarrowScreen) {
                when (ClientId.value) {
                    0 -> clearLeftClient1()
                    1 -> clearLeftClient2()
                    2 -> clearLeftClient3()
                    3 -> clearLeftClient4()
                    else -> clearLeftClient5()
                }
            } else {
                when (ClientId.value) {
                    0 -> clearRightClient1()
                    1 -> clearRightClient2()
                    2 -> clearRightClient3()
                    3 -> clearRightClient4()
                    else -> clearRightClient5()
                }
            }
        }

        fun isVeryFirstScreen(isNarrowScreen: Boolean, clientId: Int): Boolean {
            return (
                if (isNarrowScreen)
                    when (clientId) {
                        0 -> _LeftClient1
                        1 -> _LeftClient2
                        2 -> _LeftClient3
                        3 -> _LeftClient4
                        else -> _LeftClient5
                    }
                else
                    when (clientId) {
                        0 -> _RightClient1
                        1 -> _RightClient2
                        2 -> _RightClient3
                        3 -> _RightClient4
                        else -> _RightClient5
                    }
                ).value.size == 1
        }

        fun isVeryFirstScreenLeft(clientId: Int): Boolean {
            return (when (clientId) {
                0 -> _LeftClient1
                1 -> _LeftClient2
                2 -> _LeftClient3
                3 -> _LeftClient4
                else -> _LeftClient5
            }).value.size == 1
        }

        fun isVeryFirstScreenRight(clientId: Int): Boolean {
            return (when (clientId) {
                0 -> _RightClient1
                1 -> _RightClient2
                2 -> _RightClient3
                3 -> _RightClient4
                else -> _RightClient5
            }).value.size == 1
        }

        private val _ClientId =
            MutableStateFlow(0)
        val ClientId =
            _ClientId.asStateFlow()

        suspend fun setClientId(id: Int) {
            _ClientId.emit(id)
        }

        private val _LeftClient1 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient1 =
            _LeftClient1.asStateFlow()

        private val _LeftClient2 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient2 =
            _LeftClient2.asStateFlow()

        private val _LeftClient3 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient3 =
            _LeftClient3.asStateFlow()

        private val _LeftClient4 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient4 =
            _LeftClient4.asStateFlow()

        private val _LeftClient5 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient5 =
            _LeftClient5.asStateFlow()

        private val _RightClient1 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient1 =
            _RightClient1.asStateFlow()

        private val _RightClient2 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient2 =
            _RightClient2.asStateFlow()

        private val _RightClient3 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient3 =
            _RightClient3.asStateFlow()

        private val _RightClient4 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient4 =
            _RightClient4.asStateFlow()

        private val _RightClient5 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient5 =
            _RightClient5.asStateFlow()

        internal fun persistentSnapshot(): PersistedTransactionNavigationSectionDataModel =
            PersistedTransactionNavigationSectionDataModel(
                clientId = ClientId.value.coerceIn(0, 4),
                left = listOf(
                    LeftClient1.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient2.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient3.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient4.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient5.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart)
                ),
                right = listOf(
                    RightClient1.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient2.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient3.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient4.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient5.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection)
                )
            )

        internal suspend fun restorePersistentSnapshot(snapshot: PersistedTransactionNavigationSectionDataModel) {
            _ClientId.emit(snapshot.clientId.coerceIn(0, 4))
            _LeftClient1.emit(snapshot.left.getOrNull(0).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient2.emit(snapshot.left.getOrNull(1).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient3.emit(snapshot.left.getOrNull(2).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient4.emit(snapshot.left.getOrNull(3).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient5.emit(snapshot.left.getOrNull(4).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _RightClient1.emit(snapshot.right.getOrNull(0).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient2.emit(snapshot.right.getOrNull(1).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient3.emit(snapshot.right.getOrNull(2).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient4.emit(snapshot.right.getOrNull(3).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient5.emit(snapshot.right.getOrNull(4).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
        }

        suspend fun goLeftClient1(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient1.value.last()::class)
                _LeftClient1.emit(
                    _LeftClient1
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient1.value.last()::class == _LeftClient1.value.last()::class)
                popRightClient1()

            val oldSize = _LeftClient1.value.size

            _LeftClient1.emit(
                _LeftClient1.value.toMutableList()
                    .apply {
                        if (_LeftClient1.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient1.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient1(this@run)
            }
        }

        suspend fun clearLeftClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient1.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient2(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient2.value.last()::class)
                _LeftClient2.emit(
                    _LeftClient2
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient2.value.last()::class == _LeftClient2.value.last()::class)
                popRightClient2()

            val oldSize = _LeftClient2.value.size

            _LeftClient2.emit(
                _LeftClient2.value.toMutableList()
                    .apply {
                        if (_LeftClient2.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient2.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient2(this@run)
            }
        }

        suspend fun clearLeftClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient2.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient3(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient3.value.last()::class)
                _LeftClient3.emit(
                    _LeftClient3
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient3.value.last()::class == _LeftClient3.value.last()::class)
                popRightClient3()

            val oldSize = _LeftClient3.value.size

            _LeftClient3.emit(
                _LeftClient3.value.toMutableList()
                    .apply {
                        if (_LeftClient3.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient3.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient3(this@run)
            }
        }

        suspend fun clearLeftClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient3.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient4(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient4.value.last()::class)
                _LeftClient4.emit(
                    _LeftClient4
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient4.value.last()::class == _LeftClient4.value.last()::class)
                popRightClient4()

            val oldSize = _LeftClient4.value.size

            _LeftClient4.emit(
                _LeftClient4.value.toMutableList()
                    .apply {
                        if (_LeftClient4.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient4.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient4(this@run)
            }
        }

        suspend fun clearLeftClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient4.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient5(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient5.value.last()::class)
                _LeftClient5.emit(
                    _LeftClient5
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient5.value.last()::class == _LeftClient5.value.last()::class)
                popRightClient5()

            val oldSize = _LeftClient5.value.size

            _LeftClient5.emit(
                _LeftClient5.value.toMutableList()
                    .apply {
                        if (_LeftClient5.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient5.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient5(this@run)
            }
        }

        suspend fun clearLeftClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient5.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient1(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient1.value.last()::class)
                _RightClient1.emit(
                    _RightClient1
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient1.value.size

            _RightClient1.emit(
                _RightClient1.value.toMutableList()
                    .apply {
                        if (_RightClient1.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient1.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient1(this@run)
            }
        }

        suspend fun clearRightClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient1.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient2(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient2.value.last()::class)
                _RightClient2.emit(
                    _RightClient2
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient2.value.size

            _RightClient2.emit(
                _RightClient2.value.toMutableList()
                    .apply {
                        if (_RightClient2.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient2.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient2(this@run)
            }
        }

        suspend fun clearRightClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient2.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient3(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient3.value.last()::class)
                _RightClient3.emit(
                    _RightClient3
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient3.value.size

            _RightClient3.emit(
                _RightClient3.value.toMutableList()
                    .apply {
                        if (_RightClient3.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient3.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient3(this@run)
            }
        }

        suspend fun clearRightClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient3.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient4(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient4.value.last()::class)
                _RightClient4.emit(
                    _RightClient4
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient4.value.size

            _RightClient4.emit(
                _RightClient4.value.toMutableList()
                    .apply {
                        if (_RightClient4.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient4.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient4(this@run)
            }
        }

        suspend fun clearRightClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient4.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient5(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient5.value.last()::class)
                _RightClient5.emit(
                    _RightClient5
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient5.value.size

            _RightClient5.emit(
                _RightClient5.value.toMutableList()
                    .apply {
                        if (_RightClient5.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient5.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient5(this@run)
            }
        }

        suspend fun clearRightClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient5.emit(
                listOf(model)
            )
        }

        suspend fun init(isNarrowScreen: Boolean) {
            TransactionReturn.run {
                if (isNarrowScreen) {
                    if (RightClient1.value.size > 1) {
                        _LeftClient1.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient1.value.subList(1, RightClient1.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient1()

                    if (RightClient2.value.size > 1) {
                        _LeftClient2.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient2.value.subList(1, RightClient2.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient2()

                    if (RightClient3.value.size > 1) {
                        _LeftClient3.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient3.value.subList(1, RightClient3.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient3()

                    if (RightClient4.value.size > 1) {
                        _LeftClient4.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient4.value.subList(1, RightClient4.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient4()

                    if (RightClient5.value.size > 1) {
                        _LeftClient5.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient5.value.subList(1, RightClient5.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient5()

                } else {
                    if (LeftClient1.value.size > 1) {
                        _RightClient1.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient1.value.subList(1, LeftClient1.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient1()

                    if (LeftClient2.value.size > 1) {
                        _RightClient2.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient2.value.subList(1, LeftClient2.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient2()

                    if (LeftClient3.value.size > 1) {
                        _RightClient3.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient3.value.subList(1, LeftClient3.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient3()

                    if (LeftClient4.value.size > 1) {
                        _RightClient4.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient4.value.subList(1, LeftClient4.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient4()

                    if (LeftClient5.value.size > 1) {
                        _RightClient5.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient5.value.subList(1, LeftClient5.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient5()
                }
            }
        }
    }

    object TransactionSupply {

        suspend fun go(
            model: NavigationScreenModel.Transaction,
            isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen,
            remove: Boolean = false
        ) {
            if (isNarrowScreen) {
                when (ClientId.value) {
                    0 -> goLeftClient1(model, remove)
                    1 -> goLeftClient2(model, remove)
                    2 -> goLeftClient3(model, remove)
                    3 -> goLeftClient4(model, remove)
                    else -> goLeftClient5(model, remove)
                }
            } else {
                when (ClientId.value) {
                    0 -> goRightClient1(model, remove)
                    1 -> goRightClient2(model, remove)
                    2 -> goRightClient3(model, remove)
                    3 -> goRightClient4(model, remove)
                    else -> goRightClient5(model, remove)
                }
            }
        }

        suspend fun pop(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (isNarrowScreen) {
                when (ClientId.value) {
                    0 -> popLeftClient1(navigateAfterwards)
                    1 -> popLeftClient2(navigateAfterwards)
                    2 -> popLeftClient3(navigateAfterwards)
                    3 -> popLeftClient4(navigateAfterwards)
                    else -> popLeftClient5(navigateAfterwards)
                }
            } else {
                when (ClientId.value) {
                    0 -> popRightClient1(navigateAfterwards)
                    1 -> popRightClient2(navigateAfterwards)
                    2 -> popRightClient3(navigateAfterwards)
                    3 -> popRightClient4(navigateAfterwards)
                    else -> popRightClient5(navigateAfterwards)
                }
            }
        }

        suspend fun clear(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen) {
            if (isNarrowScreen) {
                when (ClientId.value) {
                    0 -> clearLeftClient1()
                    1 -> clearLeftClient2()
                    2 -> clearLeftClient3()
                    3 -> clearLeftClient4()
                    else -> clearLeftClient5()
                }
            } else {
                when (ClientId.value) {
                    0 -> clearRightClient1()
                    1 -> clearRightClient2()
                    2 -> clearRightClient3()
                    3 -> clearRightClient4()
                    else -> clearRightClient5()
                }
            }
        }

        fun isVeryFirstScreen(isNarrowScreen: Boolean, clientId: Int): Boolean {
            return (
                if (isNarrowScreen)
                    when (clientId) {
                        0 -> _LeftClient1
                        1 -> _LeftClient2
                        2 -> _LeftClient3
                        3 -> _LeftClient4
                        else -> _LeftClient5
                    }
                else
                    when (clientId) {
                        0 -> _RightClient1
                        1 -> _RightClient2
                        2 -> _RightClient3
                        3 -> _RightClient4
                        else -> _RightClient5
                    }
                ).value.size == 1
        }

        fun isVeryFirstScreenLeft(clientId: Int): Boolean {
            return (when (clientId) {
                0 -> _LeftClient1
                1 -> _LeftClient2
                2 -> _LeftClient3
                3 -> _LeftClient4
                else -> _LeftClient5
            }).value.size == 1
        }

        fun isVeryFirstScreenRight(clientId: Int): Boolean {
            return (when (clientId) {
                0 -> _RightClient1
                1 -> _RightClient2
                2 -> _RightClient3
                3 -> _RightClient4
                else -> _RightClient5
            }).value.size == 1
        }

        private val _ClientId =
            MutableStateFlow(0)
        val ClientId =
            _ClientId.asStateFlow()

        suspend fun setClientId(id: Int) {
            _ClientId.emit(id)
        }

        private val _LeftClient1 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient1 =
            _LeftClient1.asStateFlow()

        private val _LeftClient2 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient2 =
            _LeftClient2.asStateFlow()

        private val _LeftClient3 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient3 =
            _LeftClient3.asStateFlow()

        private val _LeftClient4 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient4 =
            _LeftClient4.asStateFlow()

        private val _LeftClient5 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Cart))
        val LeftClient5 =
            _LeftClient5.asStateFlow()

        private val _RightClient1 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient1 =
            _RightClient1.asStateFlow()

        private val _RightClient2 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient2 =
            _RightClient2.asStateFlow()

        private val _RightClient3 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient3 =
            _RightClient3.asStateFlow()

        private val _RightClient4 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient4 =
            _RightClient4.asStateFlow()

        private val _RightClient5 =
            MutableStateFlow<List<NavigationScreenModel.Transaction>>(listOf(NavigationScreenModel.Transaction.Selection))
        val RightClient5 =
            _RightClient5.asStateFlow()

        internal fun persistentSnapshot(): PersistedTransactionNavigationSectionDataModel =
            PersistedTransactionNavigationSectionDataModel(
                clientId = ClientId.value.coerceIn(0, 4),
                left = listOf(
                    LeftClient1.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient2.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient3.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient4.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart),
                    LeftClient5.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart)
                ),
                right = listOf(
                    RightClient1.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient2.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient3.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient4.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection),
                    RightClient5.value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection)
                )
            )

        internal suspend fun restorePersistentSnapshot(snapshot: PersistedTransactionNavigationSectionDataModel) {
            _ClientId.emit(snapshot.clientId.coerceIn(0, 4))
            _LeftClient1.emit(snapshot.left.getOrNull(0).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient2.emit(snapshot.left.getOrNull(1).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient3.emit(snapshot.left.getOrNull(2).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient4.emit(snapshot.left.getOrNull(3).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _LeftClient5.emit(snapshot.left.getOrNull(4).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart))
            _RightClient1.emit(snapshot.right.getOrNull(0).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient2.emit(snapshot.right.getOrNull(1).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient3.emit(snapshot.right.getOrNull(2).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient4.emit(snapshot.right.getOrNull(3).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
            _RightClient5.emit(snapshot.right.getOrNull(4).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection))
        }

        suspend fun goLeftClient1(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient1.value.last()::class)
                _LeftClient1.emit(
                    _LeftClient1
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient1.value.last()::class == _LeftClient1.value.last()::class)
                popRightClient1()

            val oldSize = _LeftClient1.value.size

            _LeftClient1.emit(
                _LeftClient1.value.toMutableList()
                    .apply {
                        if (_LeftClient1.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient1.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient1(this@run)
            }
        }

        suspend fun clearLeftClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient1.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient2(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient2.value.last()::class)
                _LeftClient2.emit(
                    _LeftClient2
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient2.value.last()::class == _LeftClient2.value.last()::class)
                popRightClient2()

            val oldSize = _LeftClient2.value.size

            _LeftClient2.emit(
                _LeftClient2.value.toMutableList()
                    .apply {
                        if (_LeftClient2.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient2.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient2(this@run)
            }
        }

        suspend fun clearLeftClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient2.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient3(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient3.value.last()::class)
                _LeftClient3.emit(
                    _LeftClient3
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient3.value.last()::class == _LeftClient3.value.last()::class)
                popRightClient3()

            val oldSize = _LeftClient3.value.size

            _LeftClient3.emit(
                _LeftClient3.value.toMutableList()
                    .apply {
                        if (_LeftClient3.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient3.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient3(this@run)
            }
        }

        suspend fun clearLeftClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient3.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient4(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient4.value.last()::class)
                _LeftClient4.emit(
                    _LeftClient4
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient4.value.last()::class == _LeftClient4.value.last()::class)
                popRightClient4()

            val oldSize = _LeftClient4.value.size

            _LeftClient4.emit(
                _LeftClient4.value.toMutableList()
                    .apply {
                        if (_LeftClient4.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient4.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient4(this@run)
            }
        }

        suspend fun clearLeftClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient4.emit(
                listOf(model)
            )
        }

        suspend fun goLeftClient5(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _LeftClient5.value.last()::class)
                _LeftClient5.emit(
                    _LeftClient5
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeftClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            if (_RightClient5.value.last()::class == _LeftClient5.value.last()::class)
                popRightClient5()

            val oldSize = _LeftClient5.value.size

            _LeftClient5.emit(
                _LeftClient5.value.toMutableList()
                    .apply {
                        if (_LeftClient5.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_LeftClient5.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeftClient5(this@run)
            }
        }

        suspend fun clearLeftClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) {
            _LeftClient5.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient1(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient1.value.last()::class)
                _RightClient1.emit(
                    _RightClient1
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient1(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient1.value.size

            _RightClient1.emit(
                _RightClient1.value.toMutableList()
                    .apply {
                        if (_RightClient1.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient1.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient1(this@run)
            }
        }

        suspend fun clearRightClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient1.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient2(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient2.value.last()::class)
                _RightClient2.emit(
                    _RightClient2
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient2(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient2.value.size

            _RightClient2.emit(
                _RightClient2.value.toMutableList()
                    .apply {
                        if (_RightClient2.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient2.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient2(this@run)
            }
        }

        suspend fun clearRightClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient2.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient3(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient3.value.last()::class)
                _RightClient3.emit(
                    _RightClient3
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient3(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient3.value.size

            _RightClient3.emit(
                _RightClient3.value.toMutableList()
                    .apply {
                        if (_RightClient3.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient3.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient3(this@run)
            }
        }

        suspend fun clearRightClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient3.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient4(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient4.value.last()::class)
                _RightClient4.emit(
                    _RightClient4
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient4(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient4.value.size

            _RightClient4.emit(
                _RightClient4.value.toMutableList()
                    .apply {
                        if (_RightClient4.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient4.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient4(this@run)
            }
        }

        suspend fun clearRightClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient4.emit(
                listOf(model)
            )
        }

        suspend fun goRightClient5(
            model: NavigationScreenModel.Transaction,
            remove: Boolean = false
        ) {
            if (model::class != _RightClient5.value.last()::class)
                _RightClient5.emit(
                    _RightClient5
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRightClient5(navigateAfterwards: NavigationScreenModel.Transaction? = null) {
            val oldSize = _RightClient5.value.size

            _RightClient5.emit(
                _RightClient5.value.toMutableList()
                    .apply {
                        if (_RightClient5.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_RightClient5.value.size == oldSize)
                    delay(30)

                delay(300)

                goRightClient5(this@run)
            }
        }

        suspend fun clearRightClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) {
            _RightClient5.emit(
                listOf(model)
            )
        }

        suspend fun init(isNarrowScreen: Boolean) {
            TransactionSupply.run {
                if (isNarrowScreen) {
                    if (RightClient1.value.size > 1) {
                        _LeftClient1.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient1.value.subList(1, RightClient1.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient1()

                    if (RightClient2.value.size > 1) {
                        _LeftClient2.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient2.value.subList(1, RightClient2.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient2()

                    if (RightClient3.value.size > 1) {
                        _LeftClient3.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient3.value.subList(1, RightClient3.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient3()

                    if (RightClient4.value.size > 1) {
                        _LeftClient4.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient4.value.subList(1, RightClient4.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient4()

                    if (RightClient5.value.size > 1) {
                        _LeftClient5.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Cart)
                                addAll(
                                    RightClient5.value.subList(1, RightClient5.value.size)
                                )
                            }
                        )
                    }
                    clearRightClient5()

                } else {
                    if (LeftClient1.value.size > 1) {
                        _RightClient1.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient1.value.subList(1, LeftClient1.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient1()

                    if (LeftClient2.value.size > 1) {
                        _RightClient2.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient2.value.subList(1, LeftClient2.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient2()

                    if (LeftClient3.value.size > 1) {
                        _RightClient3.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient3.value.subList(1, LeftClient3.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient3()

                    if (LeftClient4.value.size > 1) {
                        _RightClient4.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient4.value.subList(1, LeftClient4.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient4()

                    if (LeftClient5.value.size > 1) {
                        _RightClient5.emit(
                            mutableListOf<NavigationScreenModel.Transaction>().apply {
                                add(NavigationScreenModel.Transaction.Selection)
                                addAll(
                                    LeftClient5.value.subList(1, LeftClient5.value.size)
                                )
                            }
                        )
                    }
                    clearLeftClient5()
                }
            }
        }
    }

    object Stock {

        fun isVeryFirstScreen(isNarrowScreen: Boolean): Boolean {
            return (if (isNarrowScreen) _Left else _Right).value.size == 1
        }

        fun isVeryFirstScreenLeft(): Boolean {
            return _Left.value.size == 1
        }

        fun isVeryFirstScreenRight(): Boolean {
            return _Right.value.size == 1
        }

        private val _Left =
            MutableStateFlow<List<NavigationScreenModel.Stock>>(listOf(NavigationScreenModel.Stock.Warehouse))
        val Left =
            _Left.asStateFlow()

        private val _Right =
            MutableStateFlow<List<NavigationScreenModel.Stock>>(listOf(NavigationScreenModel.Stock.AddEditGoodsItem))
        val Right =
            _Right.asStateFlow()

        internal fun persistentSnapshot(): PersistedSplitNavigationStackDataModel =
            PersistedSplitNavigationStackDataModel(
                left = Left.value.toCompactPersistentRoutes(NavigationScreenModel.Stock.Warehouse),
                right = Right.value.toCompactPersistentRoutes(NavigationScreenModel.Stock.AddEditGoodsItem)
            )

        internal suspend fun restorePersistentSnapshot(snapshot: PersistedSplitNavigationStackDataModel) {
            _Left.emit(snapshot.left.toPersistentStockStack(NavigationScreenModel.Stock.Warehouse))
            _Right.emit(snapshot.right.toPersistentStockStack(NavigationScreenModel.Stock.AddEditGoodsItem))
        }

        suspend fun go(
            model: NavigationScreenModel.Stock,
            isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen,
            remove: Boolean = false,
            forceSecond: Boolean = false
        ) {
            if (isNarrowScreen)
                goLeft(model, remove, forceSecond)
            else
                goRight(model, remove, forceSecond)
        }

        suspend fun pop(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, navigateAfterwards: NavigationScreenModel.Stock? = null) {
            if (isNarrowScreen)
                popLeft(navigateAfterwards)
            else
                popRight(navigateAfterwards)
        }

        suspend fun goLeft(
            model: NavigationScreenModel.Stock,
            remove: Boolean = false,
            forceSecond: Boolean = false
        ) {
            if (model::class != _Left.value.last()::class || forceSecond)
                _Left.emit(
                    _Left
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeft(navigateAfterwards: NavigationScreenModel.Stock? = null) {
            if (_Right.value.last()::class == _Left.value.last()::class)
                popRight()

            val oldSize = _Left.value.size

            _Left.emit(
                _Left.value.toMutableList()
                    .apply {
                        if (_Left.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_Left.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeft(this@run)
            }
        }

        suspend fun clearLeft(model: NavigationScreenModel.Stock = NavigationScreenModel.Stock.Warehouse) {
            _Left.emit(
                listOf(model)
            )
        }

        suspend fun goRight(
            model: NavigationScreenModel.Stock,
            remove: Boolean = false,
            forceSecond: Boolean = false
        ) {
            if (model::class != _Right.value.last()::class || forceSecond) {
                _Right.emit(
                    _Right
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
            }
        }

        suspend fun popRight(navigateAfterwards: NavigationScreenModel.Stock? = null) {
            val oldSize = _Right.value.size

            _Right.emit(
                _Right.value.toMutableList()
                    .apply {
                        if (_Right.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_Right.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeft(this@run)
            }
        }

        suspend fun clearRight(model: NavigationScreenModel.Stock = NavigationScreenModel.Stock.AddEditGoodsItem) {
            _Right.emit(
                listOf(model)
            )
        }

        suspend fun init(isNarrowScreen: Boolean) {
            Stock.run {
                if (isNarrowScreen) {
                    if (Right.value.size > 1) {
                        _Left.emit(
                            mutableListOf<NavigationScreenModel.Stock>().apply {
                                add(NavigationScreenModel.Stock.Warehouse)
                                addAll(Right.value.subList(1, Right.value.size))
                            }
                        )
                    }
                    clearRight()
                } else {
                    if (_Left.value.size > 1) {
                        _Right.emit(
                            mutableListOf<NavigationScreenModel.Stock>().apply {
                                add(NavigationScreenModel.Stock.AddEditGoodsItem)
                                addAll(_Left.value.subList(1, _Left.value.size))
                            }
                        )
                    }
                    clearLeft()
                }
            }
        }
    }

    object Menu {

        val listScreens = listOf(
            NavigationScreenModel.Menu.UserAccount,
            NavigationScreenModel.Menu.AppMode,
            NavigationScreenModel.Menu.Work,
            NavigationScreenModel.Menu.Notifications,
            NavigationScreenModel.Menu.Finances,
            NavigationScreenModel.Menu.StoreSubscription,
            NavigationScreenModel.Menu.ShopWindow,
            NavigationScreenModel.Menu.Stores,
            NavigationScreenModel.Menu.TransactionHistory,
            NavigationScreenModel.Menu.OperationLogs,
            NavigationScreenModel.Menu.Analytics,
            NavigationScreenModel.Menu.Workers,
            NavigationScreenModel.Menu.Suppliers,
            NavigationScreenModel.Menu.Debtors,
            NavigationScreenModel.Menu.Security,
            NavigationScreenModel.Menu.Devices,
            NavigationScreenModel.Menu.Support,
            NavigationScreenModel.Menu.AppLanguage,
            NavigationScreenModel.Menu.AppTheme,
            NavigationScreenModel.Menu.AppScale
        )

        private val _Left =
            MutableStateFlow<List<NavigationScreenModel.Menu>>(listOf(NavigationScreenModel.Menu.List))
        val Left =
            _Left.asStateFlow()

        fun isVeryFirstScreen(isNarrowScreen: Boolean): Boolean {
            return (if (isNarrowScreen) _Left else _Right).value.size == 1
        }

        fun isVeryFirstScreenLeft(): Boolean {
            return _Left.value.size == 1
        }

        fun isVeryFirstScreenRight(): Boolean {
            return _Right.value.size == 1
        }

        private val _Right =
            MutableStateFlow<List<NavigationScreenModel.Menu>>(listOf(NavigationScreenModel.Menu.UserAccount))
        val Right =
            _Right.asStateFlow()

        internal fun persistentSnapshot(): PersistedSplitNavigationStackDataModel =
            PersistedSplitNavigationStackDataModel(
                left = Left.value
                    .filterNot { it.isTemporarilyHiddenFromUi() }
                    .ifEmpty { listOf(NavigationScreenModel.Menu.List) }
                    .toCompactPersistentRoutes(NavigationScreenModel.Menu.List),
                right = Right.value
                    .filterNot { it.isTemporarilyHiddenFromUi() }
                    .ifEmpty { listOf(NavigationScreenModel.Menu.UserAccount) }
                    .toCompactPersistentRoutes(NavigationScreenModel.Menu.UserAccount)
            )

        internal suspend fun restorePersistentSnapshot(snapshot: PersistedSplitNavigationStackDataModel) {
            _Left.emit(snapshot.left.toPersistentMenuStack(NavigationScreenModel.Menu.List))
            _Right.emit(snapshot.right.toPersistentMenuStack(NavigationScreenModel.Menu.UserAccount))
        }

        suspend fun go(
            model: NavigationScreenModel.Menu,
            isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen,
            remove: Boolean = false,
            forceSecond: Boolean = false
        ) {
            if (model.isTemporarilyHiddenFromUi()) return
            if (isNarrowScreen)
                goLeft(model, remove, forceSecond)
            else
                goRight(model, remove, forceSecond)
        }

        suspend fun pop(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, navigateAfterwards: NavigationScreenModel.Menu? = null) {
            if (isNarrowScreen)
                popLeft(navigateAfterwards)
            else
                popRight(navigateAfterwards)
        }

        suspend fun goLeft(
            model: NavigationScreenModel.Menu,
            remove: Boolean = false,
            forceSecond: Boolean = false
        ) {
            if (model.isTemporarilyHiddenFromUi()) return
            if (model::class != _Left.value.last()::class || forceSecond)
                _Left.emit(
                    _Left
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeft(navigateAfterwards: NavigationScreenModel.Menu? = null) {
            if (_Right.value.last()::class == _Left.value.last()::class)
                popRight()

            val oldSize = _Left.value.size

            _Left.emit(
                _Left.value.toMutableList()
                    .apply {
                        if (_Left.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_Left.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeft(this@run)
            }
        }

        suspend fun clearLeft(model: NavigationScreenModel.Menu = NavigationScreenModel.Menu.List) {
            val visibleModel = model.takeUnless { it.isTemporarilyHiddenFromUi() }
                ?: NavigationScreenModel.Menu.List
            _Left.emit(listOf(visibleModel))
        }

        suspend fun goRight(
            model: NavigationScreenModel.Menu,
            remove: Boolean = false,
            forceSecond: Boolean = false
        ) {
            if (model.isTemporarilyHiddenFromUi()) return
            if (model::class != _Right.value.last()::class || forceSecond)
                _Right.emit(
                    _Right
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRight(navigateAfterwards: NavigationScreenModel.Menu? = null) {
            val oldSize = _Right.value.size

            _Right.emit(
                _Right.value.toMutableList()
                    .apply {
                        if (_Right.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_Right.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeft(this@run)
            }
        }

        suspend fun clearRight(model: NavigationScreenModel.Menu = NavigationScreenModel.Menu.UserAccount) {
            val visibleModel = model.takeUnless { it.isTemporarilyHiddenFromUi() }
                ?: NavigationScreenModel.Menu.UserAccount
            _Right.emit(listOf(visibleModel))
        }

        suspend fun init(isNarrowScreen: Boolean) {
            Menu.run {
                if (isNarrowScreen) {
                    if (Right.value.size > 1) {
                        _Left.emit(
                            mutableListOf<NavigationScreenModel.Menu>().apply {
                                add(NavigationScreenModel.Menu.List)
                                addAll(
                                    Right.value
                                        .drop(1)
                                        .filterNot { it.isTemporarilyHiddenFromUi() }
                                )
                            }
                        )
                    }
                    clearRight()
                } else {
                    if (_Left.value.size > 1) {
                        _Right.emit(
                            mutableListOf<NavigationScreenModel.Menu>().apply {
                                add(NavigationScreenModel.Menu.UserAccount)
                                addAll(
                                    _Left.value
                                        .drop(1)
                                        .filterNot { it.isTemporarilyHiddenFromUi() }
                                )
                            }
                        )
                    }
                    clearLeft()
                }
            }
        }
    }

    object UserAuth {

        fun isVeryFirstScreen(isNarrowScreen: Boolean): Boolean {
            return (if (isNarrowScreen) _Left else _Right).value.size == 1
        }

        fun isVeryFirstScreenLeft(): Boolean {
            return _Left.value.size == 1
        }

        fun isVeryFirstScreenRight(): Boolean {
            return _Right.value.size == 1
        }

        private val _Left =
            MutableStateFlow<List<NavigationScreenModel.UserAuth>>(listOf(NavigationScreenModel.UserAuth.LogIn))
        val Left =
            _Left.asStateFlow()

        private val _Right =
            MutableStateFlow<List<NavigationScreenModel.UserAuth>>(listOf(NavigationScreenModel.UserAuth.SignUp))
        val Right =
            _Right.asStateFlow()

        internal fun persistentSnapshot(): PersistedSplitNavigationStackDataModel =
            PersistedSplitNavigationStackDataModel(
                left = Left.value.toCompactPersistentRoutes(NavigationScreenModel.UserAuth.LogIn),
                right = Right.value.toCompactPersistentRoutes(NavigationScreenModel.UserAuth.SignUp)
            )

        internal suspend fun restorePersistentSnapshot(snapshot: PersistedSplitNavigationStackDataModel) {
            _Left.emit(snapshot.left.toPersistentUserAuthStack(NavigationScreenModel.UserAuth.LogIn))
            _Right.emit(snapshot.right.toPersistentUserAuthStack(NavigationScreenModel.UserAuth.SignUp))
        }

        suspend fun goLeft(
            model: NavigationScreenModel.UserAuth,
            remove: Boolean = false
        ) {
            if (model::class != _Left.value.last()::class)
                _Left.emit(
                    _Left
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popLeft(navigateAfterwards: NavigationScreenModel.UserAuth? = null) {
            if (_Right.value.last()::class == _Left.value.last()::class)
                popRight()

            val oldSize = _Left.value.size

            _Left.emit(
                _Left.value.toMutableList()
                    .apply {
                        if (_Left.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_Left.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeft(this@run)
            }
        }

        suspend fun clearLeft(model: NavigationScreenModel.UserAuth = NavigationScreenModel.UserAuth.LogIn) {
            _Left.emit(
                listOf(model)
            )
        }

        suspend fun goRight(
            model: NavigationScreenModel.UserAuth,
            remove: Boolean = false
        ) {
            if (model::class != _Right.value.last()::class)
                _Right.emit(
                    _Right
                        .value.toMutableList()
                        .apply {
                            if (remove)
                                removeAt(lastIndex)

                            add(model)
                        }
                )
        }

        suspend fun popRight(navigateAfterwards: NavigationScreenModel.UserAuth? = null) {
            val oldSize = _Right.value.size

            _Right.emit(
                _Right.value.toMutableList()
                    .apply {
                        if (_Right.value.size > 1)
                            removeAt(lastIndex)
                    }
            )

            navigateAfterwards?.run {
                while (_Right.value.size == oldSize)
                    delay(30)

                delay(300)

                goLeft(this@run)
            }
        }

        suspend fun clearRight(model: NavigationScreenModel.UserAuth = NavigationScreenModel.UserAuth.SignUp) {
            _Right.emit(
                listOf(model)
            )
        }

        suspend fun init(isNarrowScreen: Boolean) {
            UserAuth.run {
                if (isNarrowScreen) {
                    if (Right.value.size > 1) {
                        _Left.emit(
                            mutableListOf<NavigationScreenModel.UserAuth>().apply {
                                add(NavigationScreenModel.UserAuth.LogIn)
                                addAll(Right.value.subList(1, Right.value.size))
                            }
                        )
                    }
                    clearRight()
                } else {
                    if (Left.value.size > 1) {
                        _Right.emit(
                            mutableListOf<NavigationScreenModel.UserAuth>().apply {
                                add(NavigationScreenModel.UserAuth.SignUp)
                                addAll(_Left.value.subList(1, _Left.value.size))
                            }
                        )
                    }
                    clearLeft()
                }
            }
        }
    }
}
