package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** Home collections contain only public offers in the current, server-filtered result window. */
internal fun marketHomeShops(offers: List<MarketOffer>) = offers.map { it.storefront }.distinctBy { it.storeId }.take(8)
internal fun marketHomeDiscoveries(offers: List<MarketOffer>) = offers.sortedByDescending { it.sourceUpdatedAtMillis }.take(6)

@Composable
internal fun AppConfiguration.MarketHomeWelcome(onCatalogue: () -> Unit, onShops: () -> Unit) {
    val shape = RoundedCornerShape(stateValues.cornerRadius)
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(Brush.horizontalGradient(listOf(stateValues.AccentColor.copy(alpha = .16f), stateValues.BackgroundColor)))
        .border(1.dp, stateValues.AccentColor.copy(alpha = .3f), shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AITA MARKET", color = stateValues.AccentColor, fontSize = stateValues.smallTextSize, fontWeight = FontWeight.Bold)
                Text(pass24Text("discover"), color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
                Text(pass24Text("discover_note"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
            if (!stateValues.isNarrowScreen) MarketBagIllustration(Modifier.padding(start = 16.dp).size(112.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { actionButton(text = pass24Text("catalogue_short"), iconPath = marketIconPath(18),
                iconRes = marketIconFallback(18), autoLoading = false, confirmationRequired = false, onClick = onCatalogue) }
            Box(Modifier.weight(1f)) { actionButton(text = pass24Text("shops_short"), iconPath = marketIconPath(139),
                iconRes = marketIconFallback(139), enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor,
                autoLoading = false, confirmationRequired = false, onClick = onShops) }
        }
    }
}

@Composable
internal fun AppConfiguration.MarketHomeCatalogue(catalogue: MarketCategoryCatalogue?, onSelect: (String) -> Unit) {
    val roots = remember(catalogue) { catalogue?.categories.orEmpty().filter { it.ancestorIds.isEmpty() }.take(16) }
    if (roots.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarketHomeHeading(pass24Text("catalogue"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(2.dp)) {
            items(roots, key = { it.id }) { category ->
                val title = category.name.visibleLocalizedString(stateValues.appLanguage, "")
                Column(Modifier.width(148.dp).heightIn(min = 112.dp).clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.BackgroundColor).border(1.dp, stateValues.AccentColor.copy(alpha = .35f), RoundedCornerShape(stateValues.cornerRadius))
                    .clickable(role = Role.Button) { onSelect(category.id) }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CpImage(Modifier.size(26.dp), marketIconPath(18), marketIconFallback(18), null, stateValues.AccentColor)
                    Text(title, color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun AppConfiguration.MarketHomeCollections(offers: List<MarketOffer>, onOffer: (String) -> Unit, onShop: (MarketStorefront) -> Unit) {
    val shops = remember(offers) { marketHomeShops(offers) }
    val discoveries = remember(offers) { marketHomeDiscoveries(offers) }
    if (discoveries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        MarketHomeHeading(pass24Text("new"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(2.dp)) {
            items(discoveries, key = { it.id }) { offer ->
                Column(Modifier.width(224.dp).clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor)
                    .border(1.dp, stateValues.TextColor.copy(alpha = .12f), RoundedCornerShape(stateValues.cornerRadius))
                    .clickable(role = Role.Button) { onOffer(offer.id) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarketProductPhoto(offer.product.imageUrls.firstOrNull(), offer.title, photoHeight = 132.dp)
                    Text(offer.title, color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(marketPriceLabel(offer), color = stateValues.TextColor, fontSize = stateValues.smallTextSize, maxLines = 2)
                    Text(offer.storefront.displayName, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        MarketHomeHeading(pass24Text("shops"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(2.dp)) {
            items(shops, key = { it.storeId }) { shop ->
                Box(Modifier.width(276.dp).clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.AccentColor.copy(alpha = .06f))
                    .border(1.dp, stateValues.TextColor.copy(alpha = .1f), RoundedCornerShape(stateValues.cornerRadius))
                    .clickable(role = Role.Button) { onShop(shop) }.padding(14.dp)) { MarketShopIdentity(shop) }
            }
        }
    }
}

@Composable internal fun AppConfiguration.MarketHomeHeading(title: String) {
    Text(title, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
}

@Composable
internal fun AppConfiguration.MarketHomePromotions(offers: List<MarketOffer>, onOffer: (String) -> Unit) {
    val promotions = remember(offers) { offers.filter { (it.originalPriceMinor ?: -1L) > (it.priceMinor ?: Long.MAX_VALUE) }.take(8) }
    if (promotions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarketHomeHeading(localizedStringResource(920, "Promos"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(2.dp)) {
            items(promotions, key = { it.id }) { offer ->
                Row(Modifier.width(320.dp).clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.AccentColor.copy(alpha = .12f))
                    .border(1.dp, stateValues.AccentColor.copy(alpha = .45f), RoundedCornerShape(stateValues.cornerRadius))
                    .clickable(role = Role.Button) { onOffer(offer.id) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(offer.title, color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(marketMoneyLabel(offer.originalPriceMinor!!, offer.currencyCode.orEmpty()), color = stateValues.PlaceholderTextColor,
                            textDecoration = TextDecoration.LineThrough, fontSize = stateValues.smallTextSize)
                        Text(marketPriceLabel(offer), color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                        Text(offer.storefront.displayName, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize, maxLines = 1)
                    }
                    CpImage(Modifier.size(38.dp), stateValues.drawablePathIconPromos, stateValues.drawableResIconPromos.value, null, stateValues.AccentColor)
                }
            }
        }
    }
}
