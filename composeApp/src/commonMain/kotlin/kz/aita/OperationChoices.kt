package kz.aita

import androidx.compose.runtime.*
import kotlinx.coroutines.launch

internal fun AppConfiguration.operationChoiceKey(field: String): String =
    "operation-choice:${persistentUiDraftOwnerKey()}:${stateValues.activeStoreId ?: "no-store"}:$field"

internal fun AppConfiguration.saveOperationChoice(field: String, value: String) {
    val key = operationChoiceKey(field)
    coroutineScope.launch { writeAppStateDraft?.invoke(key, value) }
}

/** A fresh operation can inherit choices, never typed amounts or another account/store's data. */
internal suspend fun AppConfiguration.withLastStockChoices(draft: StockAddEditDraft): StockAddEditDraft {
    val ownerKey = operationChoiceKey("")
    val categoryKey = stockAddEditLastCategoryStorageKey("selected")
    val unit = readAppStateDraft?.invoke(ownerKey + "stock-unit")
        ?.takeIf { id -> stateValues.globalAppConfiguration.goodsItemsQuantityUnits.any { it.id == id } }
    val category = readAppStateDraft?.invoke(categoryKey)
        ?.takeIf { id -> id == "uncategorized" || stateValues.goodsCategories.orEmpty().any { it.id == id } }
    val currency = readAppStateDraft?.invoke(ownerKey + "stock-currency")
        ?.takeIf { id -> stockCurrencyDomains().any { it.id == id } }
    if (ownerKey != operationChoiceKey("")) return draft
    fun prices(values: List<PriceDataModel>) = if (currency == null) values else values.map { it.copy(currency = currency) }
    return draft.copy(measurementUnitId = unit ?: draft.measurementUnitId,
        categoryIds = if (category == null) draft.categoryIds else if (category == "uncategorized") emptyList() else listOf(category),
        salePrices = prices(draft.salePrices), supplyPrices = prices(draft.supplyPrices),
        returnPrices = prices(draft.returnPrices), wholesalePrices = prices(draft.wholesalePrices))
}

@Composable
internal fun AppConfiguration.rememberOperationChoiceHandler(
    field: String?, allowRestore: Boolean, selected: String?, options: List<String>, onSelected: (String) -> Unit
): (String) -> Unit {
    val storageKey = field?.let { operationChoiceKey(it) }
    val latestSelected by rememberUpdatedState(selected)
    val latestCallback by rememberUpdatedState(onSelected)
    var touched by remember(storageKey) { mutableStateOf(false) }
    var restored by remember(storageKey) { mutableStateOf(false) }
    LaunchedEffect(storageKey, options, allowRestore) {
        if (storageKey == null || !allowRestore || restored || touched || options.isEmpty()) return@LaunchedEffect
        val before = latestSelected
        val saved = readAppStateDraft?.invoke(storageKey)
        restored = true
        if (!touched && latestSelected == before && saved != null && saved in options) latestCallback(saved)
    }
    return { value ->
        touched = true
        latestCallback(value)
        if (storageKey != null) coroutineScope.launch { writeAppStateDraft?.invoke(storageKey, value) }
    }
}
