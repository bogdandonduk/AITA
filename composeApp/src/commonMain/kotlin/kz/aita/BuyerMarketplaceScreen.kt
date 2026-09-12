package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

internal fun AppConfiguration.marketPriceLabel(offer: MarketOffer): String {
    val minor = offer.priceMinor ?: return authUiText("Confirm price", "Уточните цену", "Бағасын нақтылаңыз")
    val money = "${minor / 100}.${(minor % 100).toString().padStart(2,'0')} ${offer.currencyCode.orEmpty()}"
    val amount = offer.pricedAmount?.toString()?.removeSuffix(".0").orEmpty()
    val unit = offer.unitName.visibleLocalizedString(stateValues.appLanguage,authUiText("unit","ед.","бірл."))
    return "$money / $amount $unit"
}

@Composable
internal fun AppConfiguration.BuyerMarketplaceScreen() {
    val account = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val savedOnly = stateValues.navigationScreensMain.last() == NavigationScreenModel.Buyer.Main.Saved
    val revision by MarketplaceSignals.revision.collectAsState()
    val uiScope = rememberCoroutineScope()
    var search by remember(account) { mutableStateOf("") }
    var city by remember(account) { mutableStateOf("") }
    var compareTo by remember(account,savedOnly) { mutableStateOf<MarketOffer?>(null) }
    var opened by remember(account,savedOnly) { mutableStateOf<MarketOffer?>(null) }
    var page by remember(account,generation,savedOnly,search,city,compareTo?.id) { mutableStateOf<MarketPage?>(null) }
    var loading by remember(account,generation,savedOnly,search,city,compareTo?.id) { mutableStateOf(false) }
    var failure by remember(account,generation,savedOnly,search,city,compareTo?.id) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var refresh by remember(account) { mutableStateOf(0) }
    var wantedPages by remember(account,savedOnly,search,city,compareTo?.id) { mutableStateOf(1) }
    var savingId by remember(account,generation) { mutableStateOf<String?>(null) }
    var saveFailure by remember(account,generation) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    val gridState = rememberLazyGridState()
    val compareKey = compareTo?.comparisonKey()

    val requests=remember(account,generation,savedOnly,search,city,compareKey) { Channel<Unit>(Channel.CONFLATED) }
    DisposableEffect(requests) { onDispose { requests.close() } }
    val latestWantedPages by rememberUpdatedState(wantedPages)
    LaunchedEffect(requests,revision,refresh,wantedPages) { requests.trySend(Unit) }
    LaunchedEffect(account,savedOnly,search,city,compareKey) { gridState.scrollToItem(0) }
    LaunchedEffect(requests) {
        val owner = captureMarketRequestScope() ?: return@LaunchedEffect
        for (ignored in requests) {
            delay(220)
            loading = true
            try {
                var result = if(savedOnly && compareTo==null) loadMarketSaved(owner)
                    else loadMarketOffers(owner,if(compareTo==null) search else "",city,gtin=compareTo?.gtin)
                var combined = result.payload
                if(!result.negative && combined!=null && !(savedOnly && compareTo==null)) {
                    // Refresh the whole visible window. Keep at most one trailing read while in flight.
                    var loaded = 1
                    val targetPages=latestWantedPages
                    while(loaded < targetPages && owner.isCurrent()) {
                        val current=combined ?: break
                        val cursor=current.nextId ?: break
                        val next = loadMarketOffers(owner,if(compareTo==null) search else "",city,cursor,compareTo?.gtin)
                        val payload = next.payload
                        if(next.negative || payload==null) { result=next; break }
                        combined = current.copy(offers=(current.offers+payload.offers).distinctBy { it.id },nextId=payload.nextId,
                            checkedAtMillis=payload.checkedAtMillis)
                        loaded++
                    }
                }
                if(owner.isCurrent()) {
                    val value=combined
                    if(!result.negative && value!=null) {
                        page=value; failure=null
                        opened?.let { selected -> opened=value.offers.firstOrNull { it.id==selected.id } }
                    } else failure=result.message ?: eventMessage("market.unavailable")
                }
            } finally { loading=false }
        }
    }
    LaunchedEffect(account,generation) {
        while(isActive) { delay(30_000); refresh++ }
    }
    fun setSaved(offer: MarketOffer) {
        if(savingId!=null) return
        val owner=captureMarketRequestScope() ?: return
        savingId=offer.id; saveFailure=null
        uiScope.launch {
            try {
                val result=updateMarketSaved(owner,offer.id,!offer.saved)
                if(owner.isCurrent()) {
                    val value=result.payload
                    if(!result.negative && value!=null) {
                        val savedIds=value.offers.map { it.id }.toSet()
                        if(savedOnly && compareTo==null) page=value else page=page?.let { current -> current.copy(offers=current.offers.map { it.copy(saved=it.id in savedIds) }) }
                        opened=opened?.let { it.copy(saved=it.id in savedIds) }
                        MarketplaceSignals.changed()
                    } else saveFailure=result.message ?: eventMessage("market.unavailable")
                }
            } finally { savingId=null }
        }
    }
    Column(Modifier.fillMaxSize().aitaWidthCap(1440.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        ScreenAppBarWidget(title=if(savedOnly) authUiText("Saved offers","Сохранённое","Сақталғандар") else "AITA Market",
            iconPath=marketIconPath(if(savedOnly) 140 else 139))
        Text(authUiText("Preview · discover real shop windows. Online checkout is not enabled yet.",
            "Предварительная версия · реальные витрины магазинов. Онлайн-покупка пока недоступна.",
            "Алдын ала нұсқа · дүкендердің нақты витриналары. Онлайн сатып алу әзірге қосылмаған."),
            Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
        if(compareTo!=null) {
            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(authUiText("Same barcode · same selling unit", "Один штрихкод · одна единица продажи", "Бір штрихкод · бір сату бірлігі"),color=stateValues.AccentColor,fontWeight=FontWeight.Bold)
                    Text(authUiText("Check the model and packaging. Barcode agreement is not manufacturer verification.",
                        "Сверьте модель и упаковку. Совпадение штрихкода — не проверка производителем.",
                        "Модель мен қаптаманы тексеріңіз. Штрихкод сәйкестігі өндірушінің растауы емес."),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                }
                actionButton(text=stateValues.stringBack,fillMaxWidthIfTextPresent=false,autoLoading=false,confirmationRequired=false,onClick={ compareTo=null })
            }
        } else if(!savedOnly) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
                if(maxWidth>720.dp) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    aitaFormTextField(Modifier.weight(2f),search,{ search=it.take(120) },authUiText("Product or barcode","Товар или штрихкод","Тауар не штрихкод"),identityKey="market-search:$account",autoFocus=false)
                    aitaFormTextField(Modifier.weight(1f),city,{ city=it.take(100) },authUiText("City · optional","Город · необязательно","Қала · міндетті емес"),identityKey="market-city:$account",autoFocus=false)
                } else Column {
                    aitaFormTextField(Modifier.fillMaxWidth(),search,{ search=it.take(120) },authUiText("Product or barcode","Товар или штрихкод","Тауар не штрихкод"),identityKey="market-search:$account",autoFocus=false)
                    aitaFormTextField(Modifier.fillMaxWidth(),city,{ city=it.take(100) },authUiText("City · optional","Город · необязательно","Қала · міндетті емес"),identityKey="market-city:$account",autoFocus=false)
                }
            }
        }
        if(failure!=null || saveFailure!=null) Text((saveFailure ?: failure).orEmpty().visibleLocalizedString(stateValues.appLanguage,""),
            Modifier.fillMaxWidth().padding(12.dp),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
        val offers=page?.offers.orEmpty().let { rows -> if(compareKey==null) rows else rows.filter { it.comparisonKey()==compareKey }.sortedBy { it.priceMinor } }
        if(page==null && loading) {
            LoadingSkeleton(Modifier.fillMaxWidth().padding(16.dp),rows=5)
            Spacer(Modifier.weight(1f))
        } else if(offers.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                CpImage(Modifier.size(64.dp),url=marketIconPath(if(savedOnly) 140 else 139),fallbackRes=marketIconFallback(if(savedOnly) 140 else 139),
                    contentDescription=null,tintColor=stateValues.AccentColor)
                Text(if(page==null) authUiText("Connect to load shop windows","Подключитесь, чтобы загрузить витрины","Витриналарды жүктеу үшін қосылыңыз")
                    else authUiText("No matching published offers yet","Подходящих опубликованных предложений пока нет","Сәйкес жарияланған ұсыныстар әзірге жоқ"),
                    Modifier.padding(16.dp),color=stateValues.TextColor,fontSize=stateValues.accentTextSize)
                if(!savedOnly) Text(authUiText("Store owners publish deliberately from Menu → Shop window.","Владельцы публикуют товары через «Меню → Витрина».","Иелері тауарларды «Мәзір → Витрина» арқылы жариялайды."),
                    Modifier.padding(16.dp),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            }
        } else LazyVerticalGrid(columns=GridCells.Adaptive(250.dp),state=gridState,modifier=Modifier.weight(1f).fillMaxWidth(),
            contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(offers,key={ it.id }) { offer ->
                MarketOfferCard(offer,savingId!=null,onOpen={ opened=offer },onSaved={ setSaved(offer) },onCompare={ compareTo=offer })
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            actionButton(text=authUiText("Refresh","Обновить","Жаңарту"),fillMaxWidthIfTextPresent=false,autoLoading=false,
                enabled=!loading,loading=loading,confirmationRequired=false,onClick={ refresh++ })
            if(page?.nextId!=null && wantedPages<10) {
                Spacer(Modifier.width(12.dp))
                actionButton(text=authUiText("More offers","Ещё предложения","Тағы ұсыныстар"),fillMaxWidthIfTextPresent=false,enabled=!loading,
                    autoLoading=false,confirmationRequired=false,onClick={ wantedPages++ })
            }
        }
        if(savedOnly && (page?.unavailableSavedCount ?: 0)>0) actionButton(
            modifier=Modifier.padding(horizontal=12.dp,vertical=6.dp),text=authUiText("Remove unavailable saved offers (${page?.unavailableSavedCount})",
                "Убрать недоступные предложения (${page?.unavailableSavedCount})","Қолжетімсіз ұсыныстарды жою (${page?.unavailableSavedCount})"),
            enabled=savingId==null,autoLoading=false,confirmationRequired=true,onClick={
                val owner=captureMarketRequestScope()
                if(owner!=null && savingId==null) { savingId="clear"; uiScope.launch {
                    try { val result=clearUnavailableMarketSaved(owner)
                        if(owner.isCurrent()) { if(!result.negative) { page=result.payload; MarketplaceSignals.changed() } else saveFailure=result.message }
                    } finally { savingId=null }
                } }
            })
    }
    opened?.let { offer -> Dialog(onDismissRequest={ opened=null }) {
        Column(Modifier.fillMaxWidth().aitaWidthCap(680.dp).heightIn(max=stateValues.screenHeight*0.82f)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor).padding(20.dp)
            .verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SelectionContainer { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(offer.title,color=stateValues.TextColor,fontSize=stateValues.titleTextSize,fontWeight=FontWeight.Bold)
                Text(marketPriceLabel(offer),color=stateValues.AccentColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
                if(offer.description.isNotBlank()) Text(offer.description,color=stateValues.TextColor,fontSize=stateValues.textSize)
                Text("${offer.storefront.displayName}\n${offer.storefront.city}\n${offer.storefront.publicAddress}",color=stateValues.TextColor,fontSize=stateValues.textSize)
                if(offer.storefront.pickupNote.isNotBlank()) Text(offer.storefront.pickupNote,color=stateValues.TextColor,fontSize=stateValues.textSize)
                Text(authUiText("Inventory checked","Остатки проверены","Қор тексерілді")+": "+receiptUiDateTime(offer.checkedAtMillis),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                Text(authUiText("This is an inventory snapshot, not a reservation. Confirm the final price and availability at the shop. No order or payment is created here.",
                    "Это снимок остатков, не резерв. Уточните цену и наличие в магазине. Заказ и оплата здесь не создаются.",
                    "Бұл — қор деректерінің сәттік көрінісі, резерв емес. Баға мен бар-жоғын дүкеннен нақтылаңыз. Мұнда тапсырыс пен төлем жасалмайды."),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            } }
            actionButton(text=authUiText("Close","Закрыть","Жабу"),autoLoading=false,confirmationRequired=false,onClick={ opened=null })
        }
    } }
}

@Composable
private fun AppConfiguration.MarketOfferCard(offer: MarketOffer, saving: Boolean, onOpen: () -> Unit, onSaved: () -> Unit, onCompare: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min=270.dp).foregroundTactileShadow(stateValues.cornerRadius,elevated=false)
        .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor)
        .border(stateValues.unfocusedBorderWidth,stateValues.PlaceholderTextColor.copy(alpha=0.30f),RoundedCornerShape(stateValues.cornerRadius))
        .clickable(onClick=onOpen).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            CpImage(Modifier.size(42.dp),url=marketIconPath(139),fallbackRes=marketIconFallback(139),contentDescription=null,tintColor=stateValues.AccentColor)
            Spacer(Modifier.weight(1f))
            actionButton(text="",iconPath=marketIconPath(140),iconRes=marketIconFallback(140),
                iconContentDescription=if(offer.saved) authUiText("Unsave","Не сохранять","Сақтаудан алып тастау") else authUiText("Save offer","Сохранить","Сақтау"),
                enabledColor=if(offer.saved) stateValues.AccentColor else stateValues.BackgroundColor,
                textColor=if(offer.saved) stateValues.AccentTextColor else stateValues.TextColor,
                enabled=!saving,autoLoading=false,confirmationRequired=false,onClick=onSaved)
        }
        Text(offer.title,color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold,maxLines=3,overflow=TextOverflow.Ellipsis)
        Text(marketPriceLabel(offer),color=changedValueColor(marketPriceLabel(offer),"market:${offer.id}:${stateValues.appLanguage}",stateValues.AccentColor),
            fontSize=stateValues.textSize,fontWeight=FontWeight.Bold)
        Text("${offer.storefront.displayName} · ${offer.storefront.city}",color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text(if(offer.availability==MARKET_AVAILABILITY_RECORDED) authUiText("Recorded in stock · not reserved","Есть в учёте · не зарезервировано","Есепте бар · резервтелмеген")
            else authUiText("Confirm availability","Уточните наличие","Бар-жоғын нақтылаңыз"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
        if(offer.comparisonKey()!=null) actionButton(text=authUiText("Compare offers","Сравнить предложения","Ұсыныстарды салыстыру"),
            iconPath=marketIconPath(141),iconRes=marketIconFallback(141),autoLoading=false,confirmationRequired=false,onClick=onCompare)
    }
}
