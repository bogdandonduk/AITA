package kz.aita

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prepare only the visible selector. Its 10k-item sort must not block navigation/composition. */
@Composable
internal fun AppConfiguration.analyticsScopeDomains(
    scope: String,
    stock: List<GoodsItemDataModel>,
    suppliers: List<SupplierDataModel>,
    categories: List<GenericGoodsCategoryDataModel>
): List<SelectableDomain> {
    if (scope == ANALYTICS_SCOPE_ALL) return emptyList()
    val language = stateValues.appLanguage
    val path = when (scope) {
        ANALYTICS_SCOPE_GOODS_ITEM -> stateValues.drawablePathIconStock
        ANALYTICS_SCOPE_SUPPLIER -> stateValues.drawablePathIconSuppliers
        else -> stateValues.drawablePathIconGoodsCategories
    }
    val icon = when (scope) {
        ANALYTICS_SCOPE_GOODS_ITEM -> stateValues.drawableResIconStock.value
        ANALYTICS_SCOPE_SUPPLIER -> stateValues.drawableResIconSuppliers.value
        else -> stateValues.drawableResIconGoodsCategories.value
    }
    val domains by produceState(emptyList<SelectableDomain>(), scope, stock, suppliers, categories, language, path, icon) {
        value = withContext(Dispatchers.Default) {
            when (scope) {
                ANALYTICS_SCOPE_GOODS_ITEM -> stock.sortedBy { it.name.extractLocalizedString(language) ?: it.id }.map { item ->
                    SelectableDomain(id = item.id,
                        displayId = item.allBarcodeValues().firstOrNull().orEmpty().ifBlank { item.id.take(8) }.toLocalizedSingleMain(),
                        name = item.name.ifEmpty { listOf(LocalizedStringDataModel("main", item.id.take(8))) },
                        iconPath = path, iconRes = icon)
                }
                ANALYTICS_SCOPE_SUPPLIER -> suppliers.filter { it.isActive }
                    .sortedBy { it.name.extractLocalizedString(language) ?: it.id }.map { supplier ->
                        SelectableDomain(id = supplier.id, displayId = supplier.id.take(8).toLocalizedSingleMain(),
                            name = supplier.name.ifEmpty { listOf(LocalizedStringDataModel("main", supplier.id.take(8))) },
                            iconPath = path, iconRes = icon)
                    }
                else -> categories.sortedBy { it.name.extractLocalizedString(language) ?: it.id }.map { category ->
                    SelectableDomain(id = category.id, displayId = category.id.take(8).toLocalizedSingleMain(),
                        name = category.name.ifEmpty { listOf(LocalizedStringDataModel("main", category.id.take(8))) },
                        iconPath = path, iconRes = icon)
                }
            }
        }
    }
    return domains
}
