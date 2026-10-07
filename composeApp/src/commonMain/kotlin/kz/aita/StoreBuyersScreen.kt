package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable
internal fun AppConfiguration.CommercePendingStatus() {
    val state by StoreCommerceClient.state.collectAsState()
    val scope=rememberCoroutineScope()
    val current=StoreCommerceClient.current(state)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    current.error.takeIf {it.isNotEmpty()}?.let {Text(it.extractLocalizedString(stateValues.appLanguage).orEmpty(),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)}
    current.pending.forEach {command->
        val label=command.buyer?.buyer?.name ?: command.batchBefore?.let {batch->
            stateValues.stock.orEmpty().find {it.id==batch.goodsItemId}?.name?.extractLocalizedString(stateValues.appLanguage)
        } ?: commerceText("writeoffs")
        Text(label,color=stateValues.TextColor,fontSize=stateValues.smallTextSize,fontWeight=FontWeight.Bold)
        Text(if(command.rejected) commerceText("pending_conflict") else commerceText("pending"),
            color=if(command.rejected) stateValues.ErrorColor else stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
        if(command.failure.isNotEmpty()) Text(command.failure.extractLocalizedString(stateValues.appLanguage).orEmpty(),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
        if(command.rejected) actionButton(text=commerceText("discard"),confirmationRequired=true,autoLoading=false,
            onClick={scope.launch {StoreCommerceClient.discardRejected(command.id)}})
    }
    }
}

@Composable
internal fun AppConfiguration.MenuBuyersScreen() {
    val store=stateValues.activeStoreId
    val state by StoreCommerceClient.state.collectAsState()
    val scope=rememberCoroutineScope()
    var tab by rememberSaveable(store) {mutableStateOf("active")}
    var query by rememberSaveable(store) {mutableStateOf("")}
    var editing by remember(store) {mutableStateOf<StoreBuyer?>(null)}
    var busy by remember {mutableStateOf(false)}
    LaunchedEffect(store) {StoreCommerceClient.start();StoreCommerceClient.refreshBuyers()}
    editing?.let {buyer->BuyerEditor(buyer,onDismiss={editing=null})}
    AitaScreenColumn(Modifier.fillMaxSize(),appBar={ScreenAppBarWidget(title=commerceText("buyers"),iconPath=stateValues.drawablePathIconUserAccount,
        trailingIconDescriptions=mapOf(stateValues.drawablePathIconAdd to commerceText("add_buyer")),
        trailingIcons=if(canManageStoreBuyers(store)) listOf(Triple(stateValues.drawablePathIconAdd,stateValues.drawableResIconAdd.value) {
            store?.let {editing=StoreBuyer(newDiagnosticId(),it,"")}
        }) else emptyList(),onBack={scope.launch {Navigation.Menu.pop(stateValues.isNarrowScreen)}})}) {
        if(!canViewStoreBuyers(store)) {Text(commerceText("denied"),color=stateValues.TextColor);return@AitaScreenColumn}
        val data=StoreCommerceClient.current(state)
        val own=StoreCommerceClient.effectiveBuyers(state)
        val options=listOf("active" to commerceText("active"),"archive" to commerceText("archived"))+
            if(data.directory?.parentBuyers?.isNotEmpty()==true) listOf("parent" to commerceText("parent_buyers")) else emptyList()
        sectionTabsWidget("buyers:$store",tabs=options.map {(id,title)->TabContent(id,title,icon=when(id) {
            "parent"->AitaTabIcon.Store;"archive"->AitaTabIcon.Inbox;else->AitaTabIcon.Person
        })},selectedId=tab,onSelected={tab=it})
        Spacer(Modifier.height(8.dp))
        SimpleTextInput(Modifier.fillMaxWidth(),query,commerceText("search"),autoFocus=false,onValueChange={query=it})
        val rows=(if(tab=="parent") data.directory?.parentBuyers.orEmpty().filter {p->own.none {it.sourceParentBuyerId==p.id}} else own.filter {it.isActive==(tab=="active")})
            .filter {query.isBlank() || listOf(it.name,it.phone,it.email).any {field->field.contains(query,true)}}.sortedBy {it.name.lowercase()}
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=10.dp)) {
            item {CommercePendingStatus()}
            if(tab=="parent") item {Text(commerceText("parent_help"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)}
            if(rows.isEmpty()) item {Text(commerceText("empty"),color=stateValues.TextColor)}
            items(rows,key={it.id}) {buyer ->
                BuyerCard(buyer) {
                    if(canManageStoreBuyers(store)) actionButton(text=commerceText(if(tab=="parent") "pull" else "edit_buyer"),autoLoading=false,confirmationRequired=false,onClick={
                        editing=if(tab=="parent") buyer.copy(id=newDiagnosticId(),storeId=requireNotNull(store),sourceParentBuyerId=buyer.id,revision=0,updatedAtMillis=0) else buyer
                    })
                }
            }
            item {
                actionButton(text=commerceText("refresh"),iconPath=stateValues.drawablePathIconRefresh,loading=busy,enabled=!busy,autoLoading=false,confirmationRequired=false,onClick={
                    busy=true;scope.launch {try {StoreCommerceClient.flush();StoreCommerceClient.refreshBuyers()} finally {busy=false}}
                })
            }
        }
    }
}

@Composable
private fun AppConfiguration.BuyerCard(buyer:StoreBuyer,actions:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth,stateValues.PlaceholderTextColor,RoundedCornerShape(stateValues.cornerRadius))
        .background(stateValues.BackgroundColor,RoundedCornerShape(stateValues.cornerRadius)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(buyer.name,color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
        listOf(buyer.phone,buyer.email).filter {it.isNotBlank()}.forEach {Text(it,color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)}
        if(buyer.discountPercent>0) Text("${buyer.promoTitle.ifBlank {commerceText("promo")}} · ${buyer.discountPercent.moneyText()}%"+
            (buyer.promoEndsAtMillis?.let {" · ${commerceText("promo_end")}: ${(it-1).toStockDateInputText()}"} ?: ""),
            color=if(buyer.activeDiscount()>0) stateValues.AccentColor else stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
        actions()
    }
}

@Composable
private fun AppConfiguration.BuyerEditor(original:StoreBuyer,onDismiss:()->Unit) {
    var name by rememberSaveable(original.id) {mutableStateOf(original.name)}
    var phone by rememberSaveable(original.id) {mutableStateOf(original.phone)}
    var email by rememberSaveable(original.id) {mutableStateOf(original.email)}
    var note by rememberSaveable(original.id) {mutableStateOf(original.note)}
    var promo by rememberSaveable(original.id) {mutableStateOf(original.promoTitle)}
    var discount by rememberSaveable(original.id) {mutableStateOf(original.discountPercent.moneyText())}
    var end by rememberSaveable(original.id) {mutableStateOf(original.promoEndsAtMillis?.let {(it-1).toStockDateInputText()}.orEmpty())}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    val scope=rememberCoroutineScope()
    fun save(active:Boolean=original.isActive) {
        if(busy) return
        val percent=discount.trim().replace(',','.').toDoubleOrNull()
        val endMillis=end.trim().takeIf {it.isNotBlank()}?.let {stockDateInputTextToMillis(it)}
        val value=original.copy(name=name.trim(),phone=phone.trim(),email=email.trim(),note=note.trim(),promoTitle=promo.trim(),
            discountPercent=percent ?: -1.0,promoEndsAtMillis=endMillis?.let {it+86_400_000L},isActive=active)
        if(!value.valid() || end.isNotBlank() && endMillis==null) {error=commerceText("bad_buyer");return}
        busy=true;error=""
        scope.launch {try {
            val result=StoreCommerceClient.saveBuyer(value)
            if(result.negative) error=result.message?.extractLocalizedString(stateValues.appLanguage).orEmpty() else onDismiss()
        } catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){error=commerceText("failed")}
        finally {busy=false}}
    }
    AitaBottomSheet(title=commerceText(if(original.revision==0L) "add_buyer" else "edit_buyer"),onDismiss={if(!busy) onDismiss()}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SimpleTextInput(Modifier.fillMaxWidth(),name,commerceText("name"),autoFocus=false,onValueChange={name=it;error=""})
            SimpleTextInput(Modifier.fillMaxWidth(),phone,commerceText("phone"),keyboardType=KeyboardType.Phone,autoFocus=false,onValueChange={phone=it;error=""})
            SimpleTextInput(Modifier.fillMaxWidth(),email,commerceText("email"),keyboardType=KeyboardType.Email,autoFocus=false,onValueChange={email=it;error=""})
            SimpleTextInput(Modifier.fillMaxWidth(),note,commerceText("note"),autoFocus=false,onValueChange={note=it})
            Text(commerceText("promo"),color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
            Text(commerceText("promo_help"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            SimpleTextInput(Modifier.fillMaxWidth(),promo,commerceText("promo_title"),autoFocus=false,onValueChange={promo=it})
            Text("${quickDiscountLabel()} · %",color=stateValues.TextColor,fontSize=stateValues.smallTextSize)
            SimpleTextInput(Modifier.fillMaxWidth(),discount,"${quickDiscountLabel()} · %",keyboardType=KeyboardType.Decimal,autoFocus=false,onValueChange={discount=it;error=""})
            SimpleTextInput(Modifier.fillMaxWidth(),end,"${commerceText("promo_end")} · dd.MM.yyyy",autoFocus=false,onValueChange={end=it;error=""})
            if(error.isNotBlank()) Text(error,color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
            actionButton(text=commerceText("save"),loading=busy,enabled=!busy,autoLoading=false,confirmationRequired=false,onClick={save()})
            if(original.revision>0) actionButton(text=commerceText(if(original.isActive) "archive" else "restore"),loading=busy,enabled=!busy,
                autoLoading=false,confirmationRequired=true,onClick={save(!original.isActive)})
        }
    }
}

@Composable
internal fun AppConfiguration.CartBuyerSheet(slot:Int,onDismiss:()->Unit) {
    val state by StoreCommerceClient.state.collectAsState()
    val scope=rememberCoroutineScope()
    var query by rememberSaveable {mutableStateOf("")}
    var editing by remember {mutableStateOf<StoreBuyer?>(null)}
    LaunchedEffect(stateValues.activeStoreId) {StoreCommerceClient.start();StoreCommerceClient.refreshBuyers()}
    if(editing!=null) {BuyerEditor(requireNotNull(editing),onDismiss={editing=null});return}
    AitaBottomSheet(title=commerceText("choose_buyer"),onDismiss=onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            SimpleTextInput(Modifier.fillMaxWidth(),query,commerceText("search"),autoFocus=false,onValueChange={query=it})
            actionButton(text=commerceText("no_buyer"),autoLoading=false,confirmationRequired=false,onClick={setCartBuyer(slot,null);onDismiss()})
            val buyers=StoreCommerceClient.effectiveBuyers(state).filter {it.isActive && (query.isBlank() || listOf(it.name,it.phone,it.email).any {v->v.contains(query,true)})}
            LazyColumn(Modifier.heightIn(max=340.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item {CommercePendingStatus()}
                if(buyers.isEmpty()) item {Text(commerceText("empty"),color=stateValues.TextColor)}
                items(buyers,key={it.id}) {buyer -> BuyerCard(buyer) {
                    actionButton(text=commerceText("choose_buyer"),autoLoading=false,confirmationRequired=false,onClick={setCartBuyer(slot,buyer);onDismiss()})
                }}
            }
            if(canManageStoreBuyers(stateValues.activeStoreId)) actionButton(text=commerceText("add_buyer"),autoLoading=false,confirmationRequired=false,onClick={
                stateValues.activeStoreId?.let {editing=StoreBuyer(newDiagnosticId(),it,"")}
            })
        }
    }
}
