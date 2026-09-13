package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Composable
internal fun AppConfiguration.MarketPublishToggle(title: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title,Modifier.weight(1f),color=stateValues.TextColor,fontSize=stateValues.textSize)
        Switch(checked=checked,onCheckedChange=change,enabled=enabled,colors=SwitchDefaults.colors(checkedTrackColor=stateValues.AccentColor))
    }
}

@Composable
internal fun AppConfiguration.MarketPublicationScreen() {
    val account=stateValues.userAccount?.id
    val store=stateValues.activeStoreId
    val generation=currentAuthenticatedSessionGeneration()
    val uiScope=rememberCoroutineScope()
    val host=NavigationScreenModel.Menu.ShopWindow
    val draftKey="market-editor:$account:$store"
    var draft by remember(account,store) { mutableStateOf(runCatching {
        host.state.value[draftKey]?.takeIf { it.length<=20_000 }?.let { jsonBase.decodeFromString<MarketEditorDraft>(it) }
            ?.takeIf { candidate ->
                candidate.storefront?.storeId.let { it==null || it==store } &&
                    candidate.listing?.storeId.let { it==null || it==store }
            }
    }.getOrNull() ?: MarketEditorDraft()) }
    fun edit(next: MarketEditorDraft) { draft=next; host.setStateNow(draftKey to jsonBase.encodeToString(next)) }
    var dashboard by remember(account,store,generation) { mutableStateOf<MarketPublicationDashboard?>(null) }
    var loading by remember(account,store,generation) { mutableStateOf(false) }
    var saving by remember(account,store,generation) { mutableStateOf(false) }
    // A reload that started before a successful write must not restore that older revision.
    var publicationAckRevision by remember(account,store,generation) { mutableStateOf(0L) }
    var refresh by remember(account,store) { mutableStateOf(0) }
    var feedback by remember(account,store,generation) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var feedbackNegative by remember(account,store,generation) { mutableStateOf(false) }
    var selectItem by remember(account,store) { mutableStateOf(false) }
    var itemSearch by remember(account,store) { mutableStateOf("") }
    val remoteRevision by MarketplaceSignals.revision.collectAsState()
    var loadedRemoteRevision by remember(account,store) { mutableStateOf(remoteRevision) }
    val dirty=draft.storefrontDirty || draft.listingDirty

    fun accept(value: MarketPublicationDashboard, clearStore: Boolean = false, clearListing: Boolean = false) {
        dashboard=value; loadedRemoteRevision=remoteRevision
        val prior=draft.listing
        edit(draft.copy(
            storefront=if(clearStore || !draft.storefrontDirty) value.storefront else draft.storefront,
            listing=if(clearListing || !draft.listingDirty) prior?.let { selected ->
                value.listings.firstOrNull { it.goodsItemId==selected.goodsItemId } ?: selected
            } else prior,
            storefrontDirty=if(clearStore) false else draft.storefrontDirty,
            listingDirty=if(clearListing) false else draft.listingDirty))
    }
    LaunchedEffect(account,store,generation,refresh) {
        if(store==null) return@LaunchedEffect
        val owner=captureMarketRequestScope(store) ?: return@LaunchedEffect
        loading=true
        val readRevision=publicationAckRevision
        try {
            val result=loadMarketPublication(owner)
            if(owner.isCurrent() && readRevision==publicationAckRevision) {
                val data=result.payload
                if(!result.negative && data!=null) { accept(data); feedback=null }
                else { feedback=result.message ?: eventMessage("market.unavailable"); feedbackNegative=true }
            }
            if(owner.isCurrent()) getStock(store)
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(_:Exception) {
            if(owner.isCurrent() && readRevision==publicationAckRevision) {
                feedback=eventMessage("market.refresh_failed"); feedbackNegative=true
            }
        } finally { loading=false }
    }
    val subscriptionAccess=rememberStoreSubscriptionAccess(store)
    if(!subscriptionAccess) { SubscriptionRequiredPane(); return }
    AitaScreenColumn(
        Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,
        appBar = {
            ScreenAppBarWidget(title=authUiText("Shop window","Витрина","Витрина", "Дүкөн витринасы"),iconPath=marketIconPath(142),
                onBack={ uiScope.launch { Navigation.Menu.pop(stateValues.isNarrowScreen) } })
        }
    ) {
        val section=sectionTabsWidget(stateKey="market-publisher:$account:$store",tabs=listOf(
            TabContent("storefront",authUiText("Storefront","Магазин","Дүкен", "Витрина")),
            TabContent("listings",authUiText("Listings","Товары","Тауарлар", "Жарыялар"))),modifier=Modifier.fillMaxWidth().padding(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().aitaWidthCap(760.dp).padding(horizontal=16.dp),
            contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item("notice") {
                Text(authUiText("Opt-in preview. Publish only public shop information. A listing shares its title, description, barcode, categories and current retail price/availability—not your costs, private notes or exact stock count. It does not accept orders.",
                    "Предварительная версия с добровольной публикацией. Указывайте только публичные данные. Витрина показывает название, описание, штрихкод, категории, розничную цену и наличие — не закупочные цены, заметки или точные остатки. Заказы пока не принимаются.",
                    "Ерікті жариялауы бар алдын ала нұсқа. Тек жария деректерді көрсетіңіз. Витрина атауды, сипаттаманы, штрихкодты, санаттарды, бөлшек бағаны және бар-жоғын көрсетеді — өзіндік құнды, жеке жазбаларды не нақты қор санын емес. Тапсырыс әзірге қабылданбайды.", "Ыктыярдуу алдын ала мүмкүнчүлүк. Дүкөн жөнүндө коомдук маалыматты гана жарыялаңыз. Жарыя аталышын, сүрөттөмөсүн, штрихкодун, категорияларын жана учурдагы чекене баасын/бар-жогун бөлүшөт; чыгымдарыңызды, купуя эскертмелерди же так товар санын эмес. Ал тапшырык кабыл албайт."),
                    color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            }
            if(feedback!=null) item("feedback") { Text(feedback.orEmpty().visibleLocalizedString(stateValues.appLanguage,""),
                color=if(feedbackNegative) stateValues.ErrorColor else stateValues.OkayColor,fontSize=stateValues.smallTextSize) }
            if(remoteRevision!=loadedRemoteRevision && dashboard!=null) item("changed") {
                Text(authUiText("Updates are available. Reload when ready; your draft has not been overwritten.",
                    "Есть обновления. Перезагрузите данные, когда будете готовы: ваш черновик не перезаписан.",
                    "Жаңартулар бар. Дайын болғанда қайта жүктеңіз: жобаңыз өзгертілген жоқ.", "Жаңыртуулар бар. Даяр болгондо кайра жүктөңүз; долбооруңуздун үстүнөн жазылган жок."),color=stateValues.AccentColor,fontSize=stateValues.smallTextSize)
            }
            if(dashboard==null) item("loading") {
                if(loading) LoadingSkeleton(Modifier.fillMaxWidth(),rows=4)
                else actionButton(text=authUiText("Reload","Загрузить снова","Қайта жүктеу", "Кайра жүктөө"),autoLoading=false,confirmationRequired=false,onClick={ refresh++ })
            } else if(section=="storefront") {
                val value=draft.storefront ?: dashboard!!.storefront
                item("name") { MarketEditorField(value.displayName,authUiText("Public shop name","Публичное название","Дүкеннің жария атауы", "Дүкөндүн коомдук аталышы"),"$draftKey:shop",!saving) {
                    edit(draft.copy(storefront=(draft.storefront ?: value).copy(displayName=it.take(120)),storefrontDirty=true)) } }
                item("city") { MarketEditorField(value.city,authUiText("City","Город","Қала", "Шаар"),"$draftKey:city",!saving) {
                    edit(draft.copy(storefront=(draft.storefront ?: value).copy(city=it.take(100)),storefrontDirty=true)) } }
                item("address") { MarketEditorField(value.publicAddress,authUiText("Public pickup address","Публичный адрес самовывоза","Алып кету мекенжайы", "Коомдук алып кетүү дареги"),"$draftKey:address",!saving) {
                    edit(draft.copy(storefront=(draft.storefront ?: value).copy(publicAddress=it.take(400)),storefrontDirty=true)) } }
                item("pickup") { MarketEditorField(value.pickupNote,authUiText("Pickup instructions","Условия самовывоза","Алып кету нұсқаулары", "Алып кетүү көрсөтмөлөрү"),"$draftKey:pickup",!saving,multiline=true) {
                    edit(draft.copy(storefront=(draft.storefront ?: value).copy(pickupNote=it.take(1000)),storefrontDirty=true)) } }
                item("published") { MarketPublishToggle(authUiText("Visible in Buyer mode","Видно в режиме покупателя","Сатып алушы режимінде көрінеді", "Сатып алуучу режиминде көрүнөт"),value.published,!saving) {
                    edit(draft.copy(storefront=(draft.storefront ?: value).copy(published=it),storefrontDirty=true)) } }
                item("save") { actionButton(text=authUiText("Save storefront","Сохранить витрину","Витринаны сақтау", "Витринаны сактоо"),iconPath=marketIconPath(142),iconRes=marketIconFallback(142),
                    enabled=!saving && !loading,loading=saving,autoLoading=false,confirmationRequired=value.published && dashboard?.storefront?.published!=true,onClick={
                        val owner=captureMarketRequestScope(store)
                        if(owner!=null && !saving && !loading) { val snapshot=value; saving=true; uiScope.launch {
                            try {
                                val result=saveMarketStorefront(owner,snapshot)
                                if(owner.isCurrent()) {
                                    val data=result.payload
                                    feedbackNegative=result.negative; feedback=result.message ?: if(result.negative) eventMessage("market.changed") else eventMessage("market.saved")
                                    if(!result.negative && data!=null) { publicationAckRevision++; accept(data,clearStore=true); MarketplaceSignals.changed() }
                                }
                            } catch(cancelled:CancellationException) { throw cancelled }
                            catch(_:Exception) { if(owner.isCurrent()) { feedback=eventMessage("market.refresh_failed"); feedbackNegative=true } }
                            finally { saving=false }
                        } }
                    }) }
            } else {
                item("choose") { actionButton(text=authUiText("Choose stock item","Выбрать товар со склада","Қордан тауар таңдау", "Кампадагы товарды тандаңыз"),enabled=!saving && dashboard!!.storefront.revision>0L,
                    iconPath=stateValues.drawablePathIconStock,autoLoading=false,confirmationRequired=draft.listingDirty,onClick={ selectItem=true }) }
                if(dashboard!!.storefront.revision==0L) item("first") { Text(authUiText("Save the storefront first, even as an unpublished draft.","Сначала сохраните магазин — можно без публикации.","Алдымен дүкенді сақтаңыз — жарияламауға да болады.", "Адегенде витринаны, жок дегенде жарыяланбаган долбоор катары сактаңыз."),color=stateValues.PlaceholderTextColor) }
                draft.listing?.let { listing ->
                    item("title") { MarketEditorField(listing.title,authUiText("Public product title","Публичное название товара","Тауардың жария атауы", "Товардын коомдук аталышы"),"$draftKey:${listing.goodsItemId}:title",!saving) {
                        edit(draft.copy(listing=(draft.listing?.takeIf { it.goodsItemId == listing.goodsItemId } ?: listing).copy(title=it.take(180)),listingDirty=true)) } }
                    item("description") { MarketEditorField(listing.description,authUiText("Public description","Публичное описание","Жария сипаттама", "Коомдук сүрөттөмө"),"$draftKey:${listing.goodsItemId}:description",!saving,multiline=true) {
                        edit(draft.copy(listing=(draft.listing?.takeIf { it.goodsItemId == listing.goodsItemId } ?: listing).copy(description=it.take(2000)),listingDirty=true)) } }
                    item("barcode") {
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(authUiText("Comparable barcode","Штрихкод для сравнения","Салыстыру штрихкоды", "Салыштырууга жарактуу штрихкод")+": "+listing.gtin.orEmpty().ifBlank { "—" },
                                color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                            val sourceItem=stateValues.stock.orEmpty().firstOrNull { it.id==listing.goodsItemId }
                            val sourceGtin=sourceItem?.standardBarcodeValues()?.firstNotNullOfOrNull(::marketCanonicalGtin)
                            if(sourceItem!=null && sourceGtin!=listing.gtin) actionButton(
                                text=authUiText("Use current stock barcode","Использовать текущий штрихкод","Қазіргі қор штрихкодын пайдалану", "Товардын учурдагы штрихкодун колдонуу"),
                                enabled=!saving,autoLoading=false,confirmationRequired=true,onClick={
                                    val current=draft.listing?.takeIf { it.goodsItemId==listing.goodsItemId } ?: listing
                                    edit(draft.copy(listing=current.copy(gtin=sourceGtin),listingDirty=true))
                                })
                        }
                    }
                    item("listingVisible") { MarketPublishToggle(authUiText("Publish this product","Опубликовать этот товар","Осы тауарды жариялау", "Бул товарды жарыялоо"),listing.published,!saving) {
                        edit(draft.copy(listing=(draft.listing?.takeIf { it.goodsItemId == listing.goodsItemId } ?: listing).copy(published=it),listingDirty=true)) } }
                    item("listingSave") { actionButton(text=authUiText("Save listing","Сохранить товар","Тауарды сақтау", "Жарыяны сактоо"),enabled=!saving && !loading,loading=saving,autoLoading=false,
                        confirmationRequired=listing.published && dashboard!!.listings.none { it.id==listing.id && it.published },onClick={
                            val owner=captureMarketRequestScope(store)
                            if(owner!=null && !saving && !loading) { val snapshot=listing; saving=true; uiScope.launch {
                                try {
                                    val result=saveMarketListing(owner,snapshot)
                                    if(owner.isCurrent()) {
                                        val data=result.payload
                                        feedbackNegative=result.negative; feedback=result.message ?: if(result.negative) eventMessage("market.changed") else eventMessage("market.saved")
                                        if(!result.negative && data!=null) { publicationAckRevision++; accept(data,clearListing=true); MarketplaceSignals.changed() }
                                    }
                                } catch(cancelled:CancellationException) { throw cancelled }
                                catch(_:Exception) { if(owner.isCurrent()) { feedback=eventMessage("market.refresh_failed"); feedbackNegative=true } }
                                finally { saving=false }
                            } }
                        }) }
                }
                items(dashboard!!.listings,key={ "listing:${it.id}" }) { listing ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                        .border(stateValues.unfocusedBorderWidth,stateValues.PlaceholderTextColor.copy(alpha=0.3f),RoundedCornerShape(stateValues.cornerRadius)).padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(listing.title,color=stateValues.TextColor,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(if(listing.published) authUiText("Published","Опубликован","Жарияланған", "Жарыяланган") else authUiText("Hidden","Скрыт","Жасырылған", "Жашырылган"),
                                color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                        }
                        actionButton(text=stateValues.stringEdit,fillMaxWidthIfTextPresent=false,enabled=!saving,autoLoading=false,confirmationRequired=draft.listingDirty,
                            onClick={ edit(draft.copy(listing=listing,listingDirty=false)) })
                    }
                }
            }
            item("refresh") { actionButton(text=if(dirty) authUiText("Discard draft and reload","Сбросить черновик и обновить","Жобаны тастап, жаңарту", "Долбоорду таштап, кайра жүктөө") else authUiText("Reload","Обновить","Жаңарту", "Кайра жүктөө"),
                enabled=!saving && !loading,autoLoading=false,confirmationRequired=dirty,onClick={ edit(MarketEditorDraft()); refresh++ }) }
        }
    }
    if(selectItem) Dialog(onDismissRequest={ selectItem=false }) {
        Column(Modifier.fillMaxWidth().aitaWidthCap(640.dp).heightIn(max=stateValues.screenHeight*0.8f)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor).padding(16.dp)) {
            MarketEditorField(itemSearch,authUiText("Find stock item","Найти товар","Тауар іздеу", "Кампадагы товарды табуу"),"$draftKey:select",true) { itemSearch=it.take(120) }
            val items=stateValues.stock.orEmpty().filter { it.isActive && (itemSearch.isBlank() ||
                it.name.any { name -> name.value.contains(itemSearch,true) } || it.standardBarcodeValues().any { code -> code.contains(itemSearch,true) }) }.take(100)
            LazyColumn(Modifier.weight(1f,fill=false).fillMaxWidth()) {
                items(items,key={ it.id }) { item -> Text(item.name.visibleLocalizedString(stateValues.appLanguage,item.id),
                    Modifier.fillMaxWidth().clickable {
                        val existing=dashboard?.listings?.firstOrNull { it.goodsItemId==item.id }
                        edit(draft.copy(listing=existing ?: MarketListing("",store.orEmpty(),item.id,item.name.visibleLocalizedString(stateValues.appLanguage,item.id),
                            gtin=item.standardBarcodeValues().firstNotNullOfOrNull(::marketCanonicalGtin)),listingDirty=existing==null))
                        selectItem=false
                    }.padding(12.dp),color=stateValues.TextColor,fontSize=stateValues.textSize) }
            }
            Text(authUiText("Showing up to 100 matches. Refine the search to find another product.","До 100 совпадений. Уточните поиск, чтобы найти другой товар.","100 сәйкестікке дейін. Басқа тауарды табу үшін іздеуді нақтылаңыз.", "Эң көп 100 дал келүү көрсөтүлөт. Башка товарды табуу үчүн издөөнү тактаңыз."),
                color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            actionButton(text=authUiText("Close","Закрыть","Жабу", "Жабуу"),autoLoading=false,confirmationRequired=false,onClick={ selectItem=false })
        }
    }
}

@Composable
private fun AppConfiguration.MarketEditorField(value: String, title: String, id: String, enabled: Boolean,
    multiline: Boolean = false, onChanged: (String) -> Unit) {
    // Exactly one account/location-scoped journal owns text; no stale second field draft.
    genericTextField(modifier=Modifier.fillMaxWidth(),valueInitial=value,parentOwnsValue=true,identityKey=id,
        titleText=title,placeholderText=title,enabled=enabled,autoFocus=false,retainTextAcrossRecreation=false,persistTextDraft=false,
        singleLine=!multiline,wide=multiline,keyboardType=KeyboardType.Text,enableVoiceInput=false,
        onValueChange={ text, apply -> onChanged(text); apply() })
}
