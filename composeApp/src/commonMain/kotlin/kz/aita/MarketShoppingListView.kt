package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun MarketShoppingQuotedLine.needsShoppingReview() = status != MARKET_QUOTE_ESTIMATED || subtotalMinor == null

/** Presentation filters never rewrite the snapshot used for totals, reviews or commands. */
@Stable
internal class MarketShoppingListView {
    var section by mutableStateOf("list")
    var search by mutableStateOf("")
    var attentionOnly by mutableStateOf(false)
    var shop by mutableStateOf<Pair<String, String>?>(null)
    val filtered get() = search.isNotBlank() || attentionOnly || shop != null

    fun rows(snapshot: MarketShoppingSnapshot?): List<MarketShoppingQuotedLine> {
        val words = search.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return snapshot?.lines.orEmpty().filter { row ->
            (shop == null || shop == (row.line.storeId to row.line.basis.currencyCode)) &&
                (!attentionOnly || row.needsShoppingReview()) &&
                words.all { word -> listOf(row.line.title, row.line.shopName, row.line.basis.gtin.orEmpty())
                    .any { it.lowercase().contains(word) } }
        }
    }
    fun clear() { search = ""; attentionOnly = false; shop = null }
    fun reviewShop(storeId: String, currency: String) {
        clear(); shop = storeId to currency; section = "list"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppConfiguration.MarketShoppingListControls(view: MarketShoppingListView, snapshot: MarketShoppingSnapshot?) {
    val lines = snapshot?.lines.orEmpty()
    val attention = lines.count { it.needsShoppingReview() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        aitaFormTextField(Modifier.fillMaxWidth(), view.search, { view.search = it.take(120) },
            marketBrowseText("market.list_search"), identityKey = "shopping-search:${snapshot?.userId}",
            autoFocus = false, parentOwnsValue = true)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (attention > 0 || view.attentionOnly) actionButton(
                text = marketBrowseText(if (view.attentionOnly) "market.list_show_all" else "market.list_attention", "count" to attention.toString()),
                autoLoading = false, confirmationRequired = false,
                onClick = { view.attentionOnly = !view.attentionOnly })
            if (view.filtered) actionButton(text = marketBrowseText("market.list_clear"),
                enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                autoLoading = false, confirmationRequired = false, onClick = view::clear)
        }
        view.shop?.let { selected ->
            val name = lines.firstOrNull { it.line.storeId == selected.first }?.line?.shopName.orEmpty()
            Text("$name · ${selected.second}", color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        }
        Text(marketBrowseText("market.list_showing", "count" to view.rows(snapshot).size.toString(), "total" to lines.size.toString()),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
    }
}
