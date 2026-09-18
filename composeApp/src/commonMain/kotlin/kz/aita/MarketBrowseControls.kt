package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun AppConfiguration.marketBrowseText(key: String, vararg arguments: Pair<String, String>) =
    eventMessage(key, *arguments).visibleLocalizedString(stateValues.appLanguage, "")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppConfiguration.MarketBrowseControls(
    browse: BuyerBrowseNavigation,
    catalogue: MarketCategoryCatalogue?,
    onChooseCategory: () -> Unit
) {
    val category = catalogue?.categories?.firstOrNull { it.id == browse.categoryId.value }
    val categoryLabel = category?.name?.visibleLocalizedString(stateValues.appLanguage, "")?.substringAfterLast(" / ")
        ?: if (browse.categoryId.value == null) authUiText("All categories", "Все категории", "Барлық санаттар", "Бардык категориялар")
        else authUiText("Selected category", "Выбранная категория", "Таңдалған санат", "Тандалган категория")
    val byName = authUiText("By name", "По названию", "Атауы бойынша", "Аталышы боюнча")
    val filterLabel = marketBrowseText(if (browse.filtersExpanded) "market.browse_hide_filters" else "market.browse_filters") +
        if (browse.filterCount > 0) " · ${browse.filterCount}" else ""
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        aitaFormTextField(Modifier.fillMaxWidth(), browse.search.value, { browse.search.value = it.take(120) },
            authUiText("Product or barcode", "Товар или штрихкод", "Тауар не штрихкод", "Товар же штрихкод"),
            identityKey = "market-search:${stateValues.userAccount?.id}:${browse.savedOnly}", autoFocus = false, parentOwnsValue = true)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actionButton(text = filterLabel, enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                autoLoading = false, confirmationRequired = false, onClick = { browse.filtersExpanded = !browse.filtersExpanded })
            if (browse.search.value.isNotEmpty() || browse.filterCount > 0) actionButton(
                text = authUiText("Clear filters", "Сбросить фильтры", "Сүзгілерді тазалау", "Чыпкаларды тазалоо"),
                enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                autoLoading = false, confirmationRequired = false, onClick = browse::clearFilters)
        }
        if (!browse.filtersExpanded && browse.filterCount > 0) Text(buildList {
            if (browse.shopId.value == null && browse.city.value.isNotBlank()) add(browse.city.value)
            if (browse.categoryId.value != null) add(categoryLabel)
            if (browse.sort.value == MARKET_DISCOVERY_TITLE) add(byName)
        }.joinToString(" · "), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        if (browse.filtersExpanded) {
            if (browse.shopId.value == null) aitaFormTextField(Modifier.fillMaxWidth(), browse.city.value, { browse.city.value = it.take(100) },
                authUiText("City · optional", "Город · необязательно", "Қала · міндетті емес", "Шаар · милдеттүү эмес"),
                identityKey = "market-city:${stateValues.userAccount?.id}:${browse.savedOnly}", autoFocus = false, parentOwnsValue = true)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actionButton(text = categoryLabel, iconPath = marketIconPath(18), iconRes = marketIconFallback(18),
                    enabled = catalogue != null, autoLoading = false, confirmationRequired = false, onClick = onChooseCategory)
                if (browse.categoryId.value != null) actionButton(
                    text = authUiText("Clear category", "Сбросить категорию", "Санатты алып тастау", "Категорияны тазалоо"),
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                    autoLoading = false, confirmationRequired = false, onClick = {
                        browse.categoryId.value = null; browse.limit.value = MARKET_DISCOVERY_PAGE_SIZE
                    })
            }
            sectionTabsWidget("market-sort:${stateValues.userAccount?.id}:${browse.savedOnly}", listOf(
                TabContent(MARKET_DISCOVERY_RECENT, if (browse.query.savedOnly)
                    authUiText("Recently saved", "Недавно сохранённые", "Жақында сақталған", "Жакында сакталган")
                    else authUiText("Newest listings", "Новые предложения", "Жаңа ұсыныстар", "Эң жаңы жарыялар")),
                TabContent(MARKET_DISCOVERY_TITLE, byName)), selectedId = browse.sort.value, onSelected = {
                    browse.sort.value = it; browse.limit.value = MARKET_DISCOVERY_PAGE_SIZE
                })
        }
    }
}

@Composable
internal fun AppConfiguration.MarketBrowseListAction(shopping: MarketShoppingUiState, onOpen: () -> Unit) {
    val count = shopping.snapshot?.takeIf { it.userId == stateValues.userAccount?.id }?.lines?.size
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        actionButton(text = if (count == null) authUiText("Shopping list", "Список покупок", "Сатып алу тізімі", "Сатып алуу тизмеси")
            else marketBrowseText("market.browse_list_count", "count" to count.toString()),
            iconPath = marketIconPath(143), iconRes = marketIconFallback(143), autoLoading = false,
            enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
            confirmationRequired = false, onClick = onOpen)
    }
}
