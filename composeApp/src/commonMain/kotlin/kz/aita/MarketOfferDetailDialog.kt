package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Details own a fresh, authorized read, not a pointer into whichever grid page is currently loaded. */
@Composable
internal fun AppConfiguration.MarketOfferDetailDialog(
    offerId: String,
    shopping: MarketShoppingUiState,
    onDismiss: () -> Unit,
    onVisitShop: (MarketStorefront) -> Unit,
    onCompare: ((MarketOffer) -> Unit)? = null
) {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val owner = remember(account, generation, offerId) { captureMarketRequestScope() }
    var offer by remember(account, generation, offerId) { mutableStateOf<MarketOffer?>(null) }
    var branches by remember(account, generation, offerId) { mutableStateOf<List<MarketBranchAvailability>>(emptyList()) }
    var branchesTruncated by remember(account, generation, offerId) { mutableStateOf(false) }
    var loading by remember(account, generation, offerId) { mutableStateOf(true) }
    var fresh by remember(account, generation, offerId) { mutableStateOf(false) }
    var unavailable by remember(account, generation, offerId) { mutableStateOf(false) }
    var error by remember(account, generation, offerId) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    val remote by MarketplaceSignals.revision.collectAsState()
    val requests = remember(account, generation, offerId) { Channel<Unit>(Channel.CONFLATED) }
    val scope = rememberCoroutineScope()
    val latestShopping by rememberUpdatedState(shopping)
    val fence = remember(account, generation, offerId) { MarketOfferDetailReadFence() }
    var loadedStamp by remember(account, generation, offerId) { mutableStateOf<MarketOfferDetailReadFence.Stamp?>(null) }
    var receivedAt by remember(account, generation, offerId) { mutableStateOf<TimeMark?>(null) }
    fun requestRefresh() {
        // Disable old actions immediately, even before the actor consumes a conflated request.
        fence.invalidate(); fresh = false; requests.trySend(Unit)
    }
    fun detailIsCurrent(revision: Long): Boolean = owner?.isCurrent() == true && fresh && !unavailable && offer != null &&
        fence.canUse(loadedStamp, offerId, revision, receivedAt?.elapsedNow()?.inWholeMilliseconds ?: -1L, loading)
    fun displayedOfferIsCurrent(displayed: MarketOffer): Boolean =
        displayed == offer && detailIsCurrent(MarketplaceSignals.revision.value)
    DisposableEffect(requests) { onDispose { requests.close() } }
    LaunchedEffect(requests, remote) { requestRefresh() }
    LaunchedEffect(requests) {
        val owned = owner ?: run { loading = false; return@LaunchedEffect }
        for (ignored in requests) {
            delay(100)
            while (requests.tryReceive().isSuccess) { /* keep a trailing request only for changes during I/O */ }
            val stamp = fence.capture(offerId, MarketplaceSignals.revision.value)
            loading = true; fresh = false
            try {
                val response = loadMarketOfferDetail(owned, offerId)
                if (owned.isCurrent()) {
                    if (!fence.isCurrent(stamp, offerId, MarketplaceSignals.revision.value)) {
                        // An invalidation can precede the Compose effect that enqueues its read.
                        // Never publish this superseded success OR error; retain one trailing read.
                        requests.trySend(Unit)
                    } else {
                        val data = response.payload
                        if (!response.negative && data != null) {
                            offer = data.offer; branches = data.branchAvailability; branchesTruncated = data.branchAvailabilityTruncated; unavailable = false; error = null
                            loadedStamp = stamp; receivedAt = TimeSource.Monotonic.markNow(); fresh = true
                        } else {
                            error = response.message ?: eventMessage("market.detail_failed")
                            // A missing new route is an upgrade problem, not a withdrawn offer.
                            if (!response.transportFailure && response.httpStatusCode == 404 &&
                                response.message?.eventMessageReferenceOrNull()?.key == "market.unavailable") {
                                offer = null; branches = emptyList(); branchesTruncated = false; loadedStamp = null; receivedAt = null; unavailable = true
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (owned.isCurrent()) {
                    if (!fence.isCurrent(stamp, offerId, MarketplaceSignals.revision.value)) requests.trySend(Unit)
                    else error = eventMessage("market.detail_failed")
                }
            } finally { loading = false }
        }
    }
    LaunchedEffect(requests) { while (isActive) { delay(MARKET_OFFER_DETAIL_FRESH_MILLIS); awaitClientBackgroundWork(); requestRefresh() } }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.padding(12.dp).fillMaxWidth().aitaWidthCap(760.dp).heightIn(max=stateValues.screenHeight*.90f)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val current=offer
            val detailReady=detailIsCurrent(remote)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(current?.storefront?.displayName ?: "AITA Market",Modifier.weight(1f),color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
                actionButton(text="",iconPath=stateValues.drawablePathIconCancel,iconContentDescription=authUiText("Close","Закрыть","Жабу","Жабуу"),
                    autoLoading=false,confirmationRequired=false,enabledColor=stateValues.BackgroundColor,textColor=stateValues.TextColor,onClick=onDismiss)
            }
            val section=if(current!=null) sectionTabsWidget(stateKey="market-detail:$account:$offerId",tabs=listOf(
                TabContent("product",marketProductText("market.profile_facts"),icon=AitaTabIcon.Info),
                TabContent("locations",tabLabelWithCount(marketProductText("market.locations_tab"),branches.size+1),icon=AitaTabIcon.Branches)),modifier=Modifier.fillMaxWidth()) else "product"
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                if (current == null && loading) LoadingSkeleton(Modifier.fillMaxWidth(),layout=LoadingLayout.OfferDetail,rows=1)
                else if(current==null) Text(if(unavailable) authUiText("This offer is no longer available","Предложение больше недоступно","Ұсыныс енді қолжетімсіз","Бул сунуш эми жеткиликсиз")
                    else authUiText("Connect to view this offer","Подключитесь, чтобы открыть предложение","Ұсынысты көру үшін қосылыңыз","Бул сунушту көрүү үчүн туташыңыз"),
                    color=stateValues.TextColor,fontSize=stateValues.titleTextSize,fontWeight=FontWeight.Bold)
                else {
                    if(section=="product") {
                    MarketProductGallery(current.product,current.title)
                    SelectionContainer {
                        Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            if(current.product.brand.isNotBlank()) Text(current.product.brand,color=stateValues.TextColor.copy(alpha=.65f),fontSize=stateValues.smallTextSize)
                            Text(current.title,color=stateValues.TextColor,fontSize=stateValues.titleTextSize,fontWeight=FontWeight.Bold)
                            MarketPromotionPrice(current)
                            Text(marketPriceLabel(current),color=stateValues.TextColor,fontSize=stateValues.titleTextSize,fontWeight=FontWeight.Bold)
                            if(current.description.isNotBlank()) Text(current.description,color=stateValues.TextColor,fontSize=stateValues.textSize)
                            MarketProductFacts(current.product)
                        }
                    }
                    } else {
                    Text(current.title,color=stateValues.TextColor,fontSize=stateValues.textSize,fontWeight=FontWeight.Bold)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha=.06f)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        MarketShopIdentity(current.storefront)
                        SelectionContainer { Text(current.storefront.publicAddress,color=stateValues.TextColor,fontSize=stateValues.textSize) }
                        if(current.storefront.pickupNote.isNotBlank()) Text(current.storefront.pickupNote,color=stateValues.TextColor.copy(alpha=.7f),fontSize=stateValues.smallTextSize)
                        actionButton(text=authUiText("Visit shop","Открыть магазин","Дүкенге өту","Дүкөнгө өтүү"),iconPath=marketIconPath(139),iconRes=marketIconFallback(139),
                            enabled=detailReady,autoLoading=false,confirmationRequired=false,enabledColor=stateValues.BackgroundColor,textColor=stateValues.TextColor,onClick={
                                if(displayedOfferIsCurrent(current)) onVisitShop(current.storefront) else requestRefresh()
                            })
                    }
                    SelectionContainer { MarketBranchAvailabilityContent(branches,branchesTruncated) }
                    }
                    Text(marketBrowseText("market.availability_note")+"\n"+receiptUiDateTime(current.checkedAtMillis),color=stateValues.TextColor.copy(alpha=.65f),fontSize=stateValues.smallTextSize)
                    if(!detailReady) Text(eventMessage("market.detail_stale").visibleLocalizedString(stateValues.appLanguage,""),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                    if(onCompare!=null && current.comparisonSelection()!=null) actionButton(text=authUiText("Compare offers","Сравнить предложения","Ұсыныстарды салыстыру","Сунуштарды салыштыруу"),
                        iconPath=marketIconPath(141),iconRes=marketIconFallback(141),enabled=detailReady,autoLoading=false,confirmationRequired=false,
                        enabledColor=stateValues.BackgroundColor,textColor=stateValues.TextColor,onClick={if(displayedOfferIsCurrent(current)) onCompare(current) else requestRefresh()})
                }
                error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage,""),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize) }
                MarketShoppingFeedback(shopping)
                actionButton(text=authUiText("Refresh","Обновить","Жаңарту","Жаңыртуу"),autoLoading=false,enabled=!loading,loading=loading,confirmationRequired=false,
                    enabledColor=stateValues.BackgroundColor,textColor=stateValues.TextColor,onClick={requestRefresh()})
            }
            if(current!=null) {
                val inList=shopping.contains(offerId)
                actionButton(text=if(inList) authUiText("Open your list","Открыть список покупок","Тізімді ашу","Тизмеңизди ачуу") else authUiText("Add to shopping list","В список покупок","Сатып алу тізіміне қосу","Сатып алуу тизмесине кошуу"),
                    iconPath=marketIconPath(143),iconRes=marketIconFallback(143),autoLoading=false,confirmationRequired=false,
                    enabled=inList || (detailReady && shopping.canChange && current.shoppingBasis()!=null),onClick={
                        if(latestShopping.contains(offerId)) scope.launch {
                            if(owner?.isCurrent()==true) {Navigation.goMain(NavigationScreenModel.Buyer.Main.Shopping);onDismiss()}
                        } else if(displayedOfferIsCurrent(current) && latestShopping.canChange && current.shoppingBasis()!=null)
                            latestShopping.add(offerId,1,current.shoppingBasis())
                        else requestRefresh()
                    })
            }
        }
    }
}
