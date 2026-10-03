package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

internal fun marketPromotionPercent(offer: MarketOffer): Int? {
    val original = offer.originalPriceMinor ?: return null
    val current = offer.priceMinor ?: return null
    if (current < 0 || original <= current || original <= 0 || offer.currencyCode.isNullOrBlank() ||
        offer.pricedAmount?.let { it.isFinite() && it > 0.0 } != true) return null
    return kotlin.math.floor((1.0 - current.toDouble() / original.toDouble()) * 100.0 + 1e-9).toInt().takeIf { it in 1..100 }
}
internal fun rememberMarketSearch(previous: List<String>, query: String): List<String> {
    val clean = query.trim().take(120)
    if (clean.isBlank()) return previous.take(8)
    return (listOf(clean) + previous.filterNot { it.equals(clean, ignoreCase = true) }).take(8)
}

@Composable internal fun AppConfiguration.MarketPromotionPrice(offer: MarketOffer) {
    val percent = marketPromotionPercent(offer) ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("−$percent%", color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)
        Text(marketMoneyLabel(offer.originalPriceMinor!!, offer.currencyCode.orEmpty()),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize,
            textDecoration = TextDecoration.LineThrough)
    }
}

@Composable internal fun AppConfiguration.MarketSearchShortcuts(browse: BuyerBrowseNavigation, categoryName: String?) {
    val active = buildList<Pair<String, String>> {
        if (browse.search.value.isNotBlank()) add("search" to browse.search.value)
        if (browse.shopId.value == null && browse.city.value.isNotBlank()) add("city" to browse.city.value)
        if (browse.categoryId.value != null) add("category" to (categoryName ?: pass24Text("catalogue")))
    }
    if (active.isNotEmpty()) LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(active, key = { it.first }) { (kind, title) ->
            actionButton(text = title.take(60), iconPath = stateValues.drawablePathIconCancel,
                iconContentDescription = "$title · ${authUiText("Remove filter", "Убрать фильтр", "Сүзгіні жою", "Чыпканы алып салуу")}",
                enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                autoLoading = false, confirmationRequired = false, onClick = {
                    when (kind) {
                        "search" -> { browse.search.value = ""; browse.appliedSearch.value = "" }
                        "city" -> { browse.city.value = ""; browse.appliedCity.value = "" }
                        "category" -> browse.categoryId.value = null
                    }
                    browse.limit.value = MARKET_DISCOVERY_PAGE_SIZE; browse.scrollRestore = null
                })
        }
    }
    if (browse.search.value.isBlank() && browse.recentSearches.isNotEmpty()) {
        Text(pass27Text("recent_searches"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(browse.recentSearches) { query ->
                actionButton(text = query, enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                    autoLoading = false, confirmationRequired = false, onClick = {
                        browse.search.value = query; browse.appliedSearch.value = query
                        browse.limit.value = MARKET_DISCOVERY_PAGE_SIZE; browse.scrollRestore = null
                    })
            }
            item { actionButton(text = pass27Text("clear_recent"), iconPath = stateValues.drawablePathIconCancel,
                autoLoading = false, confirmationRequired = false, onClick = { browse.recentSearches = emptyList() }) }
        }
    }
}
