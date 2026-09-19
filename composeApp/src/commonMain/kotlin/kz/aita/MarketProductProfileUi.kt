package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.Url

internal fun AppConfiguration.marketProductText(key: String): String =
    eventMessage(key).visibleLocalizedString(stateValues.appLanguage, "")

/** Product pictures bypass numbered-icon filename resolution. This image-only client has no AITA
 * session headers. URLs never enter a backend downloader or its credentials/context. */
@Composable
internal fun AppConfiguration.MarketProductPhoto(url: String?, title: String, modifier: Modifier = Modifier, photoHeight: Dp = 170.dp) {
    val destination = remember(url) { url?.let(::marketPublicImageUrl) }
    Box(modifier.fillMaxWidth().height(photoHeight).clip(RoundedCornerShape(14.dp))
        .background(stateValues.PlaceholderTextColor.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        if (destination == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CpImage(Modifier.size(44.dp), marketIconPath(148), marketIconFallback(148), null,
                    stateValues.PlaceholderTextColor)
                Text(marketProductText("market.profile_photo_missing"), color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize)
            }
        } else CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
            KamelImage(resource = { asyncPainterResource(data = Url(destination)) },
                contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                onLoading = { LoadingSkeleton(Modifier.fillMaxSize(), layout = LoadingLayout.ProductPhoto, rows = 1) },
                onFailure = { Text(marketProductText("market.profile_photo_failed"), color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize) })
        }
    }
}

@Composable
internal fun AppConfiguration.MarketProductFields(value: MarketProductDetails, identity: String, enabled: Boolean,
    photosEnabled: Boolean = enabled, onChanged: (MarketProductDetails) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(marketProductText("market.profile_unknown"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        MarketEditorField(value.imageUrls.joinToString("\n"), marketProductText("market.profile_photos"), "$identity:photos", photosEnabled,
            multiline = true) { text -> onChanged(value.copy(imageUrls = text.take(7300).split('\n'))) }
        Text(marketProductText("market.profile_photo_help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        if (value.hasInvalidMarketProductInput()) Text(marketProductText("market.profile_invalid"),
            color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        MarketEditorField(value.brand, marketProductText("market.profile_brand"), "$identity:brand", enabled) { onChanged(value.copy(brand = it.take(120))) }
        MarketEditorField(value.manufacturer, marketProductText("market.profile_manufacturer"), "$identity:manufacturer", enabled) { onChanged(value.copy(manufacturer = it.take(160))) }
        MarketEditorField(value.countryOfOrigin, marketProductText("market.profile_country"), "$identity:country", enabled) { onChanged(value.copy(countryOfOrigin = it.take(100))) }
        MarketEditorField(value.article, marketProductText("market.profile_article"), "$identity:article", enabled) { onChanged(value.copy(article = it.take(120))) }
        MarketEditorField(value.ingredients, marketProductText("market.profile_ingredients"), "$identity:ingredients", enabled, true) { onChanged(value.copy(ingredients = it.take(2000))) }
        MarketEditorField(value.allergens, marketProductText("market.profile_allergens"), "$identity:allergens", enabled, true) { onChanged(value.copy(allergens = it.take(1000))) }
        MarketEditorField(value.storageInstructions, marketProductText("market.profile_storage"), "$identity:storage", enabled, true) { onChanged(value.copy(storageInstructions = it.take(1000))) }
        value.attributes.forEachIndexed { index, attribute ->
            Column(Modifier.fillMaxWidth().border(1.dp, stateValues.PlaceholderTextColor.copy(alpha = .2f), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fun change(next: MarketProductAttribute) = onChanged(value.copy(attributes = value.attributes.mapIndexed { i, old -> if (i == index) next else old }))
                MarketEditorField(attribute.name, marketProductText("market.profile_attribute_name"), "$identity:attr:$index:name", enabled) { change(attribute.copy(name = it.take(80))) }
                MarketEditorField(attribute.value, marketProductText("market.profile_attribute_value"), "$identity:attr:$index:value", enabled, true) { change(attribute.copy(value = it.take(400))) }
                actionButton(text = marketProductText("market.profile_attribute_remove"), enabled = enabled,
                    autoLoading = false, confirmationRequired = false, onClick = {
                        onChanged(value.copy(attributes = value.attributes.filterIndexed { i, _ -> i != index }))
                    })
            }
        }
        if (value.attributes.size < MARKET_PRODUCT_MAX_ATTRIBUTES) actionButton(text = marketProductText("market.profile_attribute_add"),
            enabled = enabled, autoLoading = false, confirmationRequired = false,
            onClick = { onChanged(value.copy(attributes = value.attributes + MarketProductAttribute())) })
    }
}

@Composable
internal fun AppConfiguration.StockMarketplaceEditor(draft: StockAddEditDraft, images: List<String>, modifier: Modifier = Modifier,
    onDraftChanged: (StockAddEditDraft) -> Unit) {
    // Keep incomplete edited rows intact; normalization occurs only at the save boundary.
    val saved = draft.marketplaceProfile ?: StockMarketplaceProfile()
    val profile = if (saved.automaticFromStock) saved.copy(name = draft.name, description = draft.description,
        product = saved.product.copy(imageUrls = images.mapNotNull(::marketPublicImageUrl).distinct().take(MARKET_PRODUCT_MAX_IMAGES))) else saved
    val identity = "stock-profile:${stateValues.userAccount?.id}:${stateValues.activeStoreId}:${draft.id}"
    fun change(next: StockMarketplaceProfile) = onDraftChanged(draft.copy(marketplaceProfile = next))
    val activeStore = stateValues.stores.findStoreOrBranchForUi(stateValues.activeStoreId)
    val accountId = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    var showParentProfile by remember(identity, generation) { mutableStateOf(false) }
    // Both stock editors provide the bounded outer list and own vertical scrolling.
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(marketProductText(if (activeStore?.isManagementStore() == true) "market.profile_parent_generic" else "market.profile_private"),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        if (activeStore?.isInternetBranch() == true) actionButton(
            text = marketProductText("market.profile_parent"), iconPath = parentStoreStockIconPath(),
            iconRes = parentStoreStockIconFallback(), autoLoading = false, confirmationRequired = false,
            onClick = { showParentProfile = true })
        MarketPublishToggle(marketProductText("market.profile_auto"), profile.automaticFromStock, true) { automatic ->
            change(profile.copy(automaticFromStock = automatic))
        }
        MarketEditorField(profile.name.marketplaceProfileDraftText(stateValues.appLanguage), marketProductText("market.profile_title"),
            "$identity:title", !profile.automaticFromStock) { change(profile.copy(name = profile.name.withMarketplaceProfileDraftText(stateValues.appLanguage, it.take(180)))) }
        MarketEditorField(profile.description.marketplaceProfileDraftText(stateValues.appLanguage), marketProductText("market.profile_description"),
            "$identity:description", !profile.automaticFromStock, true) { change(profile.copy(description = profile.description.withMarketplaceProfileDraftText(stateValues.appLanguage, it.take(2000)))) }
        MarketProductFields(profile.product, identity, true, photosEnabled = !profile.automaticFromStock) { change(profile.copy(product = it)) }
        Text(marketProductText("market.profile_review"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Spacer(Modifier.height(40.dp))
    }
    if (showParentProfile && activeStore?.isInternetBranch() == true) ParentStoreStockSelectionBottomSheet(
        activeStoreId = activeStore.id, draft = draft,
        existing = stateValues.stock.orEmpty().firstOrNull { it.id == draft.id }, profileOnly = true,
        onDismiss = { showParentProfile = false },
        onApply = { parentItem ->
            if (stateValues.userAccount?.id == accountId && currentAuthenticatedSessionGeneration() == generation &&
                stateValues.activeStoreId == activeStore.id && parentItem.storeId == activeStore.parentStoreId) {
                change(parentItem.marketplaceProfileForBranchCopy())
                showParentProfile = false
                postInAppNotification(marketProductText("market.profile_parent_applied"), NotificationType.Positive, transient = true)
            }
        })
}

@Composable
internal fun AppConfiguration.MarketProductFacts(product: MarketProductDetails) {
    val facts = listOf("profile_brand" to product.brand, "profile_manufacturer" to product.manufacturer,
        "profile_country" to product.countryOfOrigin, "profile_article" to product.article,
        "profile_ingredients" to product.ingredients, "profile_allergens" to product.allergens,
        "profile_storage" to product.storageInstructions).filter { it.second.isNotBlank() }
    if (facts.isEmpty() && product.attributes.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(marketProductText("market.profile_facts"),color=stateValues.TextColor,fontWeight=FontWeight.Bold,fontSize=stateValues.accentTextSize)
        val rows=facts.map { marketProductText("market.${it.first}") to it.second } + product.attributes.map { it.name to it.value }
        rows.forEachIndexed { index, (name,value) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(stateValues.TextColor.copy(alpha=if(index % 2 == 0) .035f else 0f)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(name,color=stateValues.TextColor.copy(alpha=.6f),fontSize=stateValues.smallTextSize)
                Text(value,color=stateValues.TextColor,fontSize=stateValues.textSize)
            }
        }
    }
}

@Composable
internal fun AppConfiguration.StockMarketplaceBadge(item: GoodsItemDataModel) {
    val observed by MarketStockPublicationWorkspace.snapshot.collectAsState()
    val snapshot = observed?.takeIf { it.scope.isCurrent() && it.status.storeId == stateValues.activeStoreId && it.status.marketplaceEnabled } ?: return
    val published = remember(snapshot, item.id, item.barcodeModels, item.barcodes, item.measurementUnitId) { snapshot.published(item) }
    val label = marketProductText(if (published) "market.profile_published" else "market.profile_unpublished")
    var expanded by remember(item.id, snapshot.scope.accountId, snapshot.status.storeId) { mutableStateOf(false) }
    val ink = if (published && !snapshot.stale) stateValues.OkayColor else stateValues.PlaceholderTextColor
    Box(Modifier.padding(start = 8.dp).size(36.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = .10f))
        .semantics { contentDescription = label }.clickable { expanded = true }, contentAlignment = Alignment.Center) {
        CpImage(Modifier.size(24.dp), marketIconPath(148), marketIconFallback(148), null, ink)
        Box(Modifier.align(Alignment.BottomEnd).padding(3.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(ink))
    }
    if (expanded) Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).fillMaxWidth().aitaWidthCap(520.dp).clip(RoundedCornerShape(18.dp))
            .background(stateValues.BackgroundColor).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(label, color = ink, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
            Text(marketProductText("market.profile_snapshot") + "\n" + receiptUiDateTime(snapshot.status.checkedAtMillis),
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            Text(marketProductText("market.profile_review"), color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
            actionButton(text = authUiText("Close", "Закрыть", "Жабу", "Жабуу"), autoLoading = false, confirmationRequired = false, onClick = { expanded = false })
        }
    }
}

@Composable
internal fun AppConfiguration.MarketProductGallery(product: MarketProductDetails, title: String) {
    var selected by remember(product.imageUrls) { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        MarketProductPhoto(product.imageUrls.getOrNull(selected),title,photoHeight=260.dp)
        if(product.imageUrls.size > 1) {
            androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(product.imageUrls.size) { index ->
                    val label=marketBrowseText("market.photo_number","number" to (index+1).toString(),"total" to product.imageUrls.size.toString())
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                        .border(if(selected==index) 2.dp else 1.dp,if(selected==index) stateValues.AccentColor else stateValues.TextColor.copy(alpha=.1f),RoundedCornerShape(12.dp))
                        .semantics { contentDescription=label }.clickable { selected=index }.padding(4.dp)) {
                        // Only the selected full photo is decoded. Loading six originals just
                        // to draw tiny previews can exhaust a tablet's native image memory.
                        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            CpImage(Modifier.size(24.dp),marketIconPath(148),marketIconFallback(148),null,stateValues.TextColor.copy(alpha=.65f))
                            Text((index+1).toString(),color=stateValues.TextColor,fontSize=stateValues.smallTextSize,
                                fontWeight=if(selected==index) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            Text(marketBrowseText("market.photo_number","number" to (selected+1).toString(),"total" to product.imageUrls.size.toString()),
                color=stateValues.TextColor.copy(alpha=.6f),fontSize=stateValues.smallTextSize)
        }
    }
}

@Composable
internal fun AppConfiguration.MarketBranchAvailabilityContent(branches: List<MarketBranchAvailability>, truncated: Boolean) {
    if (branches.isEmpty() && !truncated) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(marketProductText("market.profile_branches"), color = stateValues.TextColor, fontWeight = FontWeight.Bold, fontSize = stateValues.textSize)
        Text(marketProductText("market.profile_branch_notice"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        branches.forEach { branch ->
            Column(Modifier.fillMaxWidth().border(1.dp, stateValues.PlaceholderTextColor.copy(alpha = .2f), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(branch.name.visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.TextColor, fontWeight = FontWeight.Bold)
                Text(branch.publicAddress, color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                Text(marketProductText(if (branch.availability == MARKET_AVAILABILITY_RECORDED) "market.profile_branch_recorded" else "market.profile_branch_confirm"),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
        }
        if (truncated) Text(marketProductText("market.profile_branch_limited"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
    }
}
