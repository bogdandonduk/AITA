package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

internal fun AppConfiguration.deliveryText(key: String) = eventMessage("delivery.$key").visibleLocalizedString(stateValues.appLanguage, "")

@Composable internal fun AppConfiguration.DeliveryCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor)
        .border(stateValues.unfocusedBorderWidth,stateValues.PlaceholderTextColor.copy(alpha=0.35f),RoundedCornerShape(stateValues.cornerRadius)).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
}

@Composable internal fun AppConfiguration.MarketDeliveryPanel(modifier: Modifier = Modifier, view: String = "buyer", store: String? = null) {
    val account=stateValues.userAccount?.id
    val generation=currentAuthenticatedSessionGeneration()
    val owner=remember(account,generation,store) { captureMarketRequestScope(store) }
    val revision by MarketplaceSignals.revision.collectAsState()
    val ui=rememberCoroutineScope()
    var page by remember(account,generation,view,store) { mutableStateOf<MarketDeliveryPage?>(null) }
    var busy by remember(account,generation,view,store) { mutableStateOf(false) }
    var fresh by remember(account,generation,view,store) { mutableStateOf(false) }
    var error by remember(account,generation,view,store) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var pending by remember(account,generation,view,store) { mutableStateOf<MarketDeliverySendResult?>(null) }
    var refresh by remember(account,generation,view,store) { mutableStateOf(0) }
    var before by remember(account,generation,view,store) { mutableStateOf<String?>(null) }
    var opened by rememberSaveable(account,view,store) { mutableStateOf<String?>(null) }
    LaunchedEffect(owner,view,store) { if (owner != null) {
        val cached=cachedMarketDeliveryPage(owner,view,store)
        if(owner.isCurrent() && page==null) page=cached
    } }
    LaunchedEffect(owner,view,store,revision,refresh,before,opened) {
        if (owner == null || opened != null) return@LaunchedEffect
        busy=true
        try {
            pending=cachedMarketDeliveryCommand(owner)
            val result=withTimeoutOrNull(15_000) { loadMarketDeliveryPage(owner,view,store,before) }
            if (owner.isCurrent()) {
                fresh=result?.negative == false && result.payload != null
                if (fresh) { page=result?.payload;error=result?.message }
                else {
                    error=result?.message ?: eventMessage("delivery.refresh_failed")
                    if (result?.httpStatusCode in setOf(401,403) && result?.transportFailure == false) page=null
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (owner.isCurrent()) { fresh=false;error=eventMessage("delivery.refresh_failed") } }
        finally { busy=false }
    }
    LaunchedEffect(owner,opened) { while (isActive) { delay(30_000); if (opened == null && !busy) refresh++ } }
    LazyColumn(modifier,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item("controls") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if (!fresh && page != null) Text(deliveryText("cached"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage,""),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize) }
                actionButton(text=deliveryText("refresh"),iconPath=stateValues.drawablePathIconRefresh,
                    enabled=!busy,loading=busy,autoLoading=false,confirmationRequired=false,onClick={refresh++})
                if (pending?.pending != null) {
                    Text(deliveryText("pending"),color=stateValues.AccentColor,fontSize=stateValues.smallTextSize)
                    actionButton(text=deliveryText("retry"),enabled=!busy,loading=busy,autoLoading=false,confirmationRequired=false,onClick={
                        if (owner != null) { busy=true;ui.launch { try {
                            val result=sendMarketDeliveryCommand(owner)
                            if (owner.isCurrent()) { pending=result;error=result.error;refresh++ }
                        } finally { busy=false } } }
                    })
                }
                if (before != null) actionButton(text=stateValues.stringBack,autoLoading=false,confirmationRequired=false,onClick={before=null})
            }
        }
        if (!busy && page?.orders.isNullOrEmpty()) item("empty") { Text(deliveryText("empty"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.textSize) }
        items(page?.orders.orEmpty(),key={it.id}) { order ->
            DeliveryCard(Modifier.clickable { opened=order.id }) {
                Text(order.shopName,color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
                Text("${deliveryText(order.request.mode.name)} · ${deliveryText(order.status.name)}",color=stateValues.AccentColor,fontSize=stateValues.textSize)
                Text(receiptUiDateTime(order.createdAtMillis)+" · "+order.id.take(8).uppercase(),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                Text(order.request.lines.take(3).joinToString(" · ") { it.line.title },color=stateValues.TextColor,fontSize=stateValues.smallTextSize)
                order.totalMinor?.let { Text(marketMoneyLabel(it,order.request.currency),color=stateValues.TextColor,fontSize=stateValues.textSize,fontWeight=FontWeight.Bold) }
                if (!order.terminal) Text(deliveryText("estimate")+": "+receiptUiDateTime(order.estimatedFromMillis)+" — "+receiptUiDateTime(order.estimatedToMillis),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            }
        }
        page?.nextBefore?.let { cursor -> item("more") { actionButton(text=deliveryText("next"),autoLoading=false,confirmationRequired=false,onClick={before=cursor}) } }
    }
    opened?.let { id -> MarketDeliveryOrderSheet(id,store,page?.orders?.firstOrNull { it.id==id },onDismiss={opened=null;refresh++}) }
}

@OptIn(ExperimentalUuidApi::class)
@Composable private fun AppConfiguration.MarketDeliveryOrderSheet(id: String, store: String?, cached: MarketDeliveryOrder?, onDismiss: ()->Unit) {
    val account=stateValues.userAccount?.id
    val generation=currentAuthenticatedSessionGeneration()
    val owner=remember(account,generation,store) { captureMarketRequestScope(store) }
    val ui=rememberCoroutineScope()
    val revision by MarketplaceSignals.revision.collectAsState()
    var order by remember(account,generation,id) { mutableStateOf(cached) }
    var busy by remember { mutableStateOf(false) }
    var fresh by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var action by remember { mutableStateOf<MarketDeliveryAction?>(null) }
    var note by rememberSaveable(account,id) { mutableStateOf("") }
    var courierId by rememberSaveable(account,id) { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var from by rememberSaveable(account,id) { mutableStateOf("30") }
    var to by rememberSaveable(account,id) { mutableStateOf("60") }
    var sharing by remember { mutableStateOf(false) }
    var positionSending by remember { mutableStateOf(false) }
    var lastSent by remember { mutableStateOf(0L) }
    var clock by remember { mutableStateOf(getCurrentTimeMillis()) }
    var fetching by remember { mutableStateOf(false) }
    LaunchedEffect(owner,id,revision,refresh) {
        if (owner == null) return@LaunchedEffect
        fetching=true
        try {
            val result=withTimeoutOrNull(15_000) { loadMarketDeliveryOrder(owner,id) }
            if (owner.isCurrent()) {
                val next=result?.payload
                fresh=result?.negative == false && next?.isValidDeliveryOrder() == true
                if (fresh && next != null && order?.revision?.let { next.revision >= it } != false) {
                    order=next
                    if(action != null && action !in next.availableActions(account==next.buyerId,currentUserOwnsStore(next.branchStoreId),account==next.courierId)) { action=null;code="" }
                }
                else {
                    error=result?.message ?: eventMessage("delivery.refresh_failed")
                    if (result?.httpStatusCode in setOf(401,403,404) && result?.transportFailure == false) { order=null;sharing=false }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (owner.isCurrent()) { fresh=false;error=eventMessage("delivery.refresh_failed") } }
        finally { fetching=false }
    }
    LaunchedEffect(owner,id) { while (isActive) { delay(10_000);clock=getCurrentTimeMillis();if(!fetching && !busy)refresh++ } }
    val current=order
    val canTrack=current?.let { !it.terminal && it.courierId==account && it.courierAccepted && it.request.mode==MarketDeliveryMode.EXPRESS &&
        it.status in setOf(MarketDeliveryStatus.ON_THE_WAY,MarketDeliveryStatus.ARRIVED) } == true
    LaunchedEffect(canTrack) { if (!canTrack) sharing=false }
    CourierLocationSharing(sharing && canTrack, onPosition={ point ->
        val latest=order
        if (owner != null && latest != null && !positionSending && getCurrentTimeMillis()-lastSent>=15_000) {
            lastSent=getCurrentTimeMillis();positionSending=true
            ui.launch { try {
                val result=withTimeoutOrNull(12_000) { shareMarketCourierPosition(owner,latest,point) }
                if (owner.isCurrent() && result?.negative != false) {
                    error=result?.message ?: eventMessage("delivery.refresh_failed")
                    if (result?.httpStatusCode in setOf(401,403,409)) sharing=false
                }
            } finally { positionSending=false } }
        }
    }, onError={ key -> sharing=false;error=eventMessage("delivery.$key") })
    AitaBottomSheet(deliveryText("orders"),iconPath=marketIconPath(186),iconRes=tabIconResource(AitaTabIcon.Truck),onDismiss=onDismiss) {
        val actionFocus = LocalFocusManager.current
        Column(Modifier.fillMaxWidth().weight(1f,false).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if (!fresh) Text(deliveryText("cached"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            current?.let { value ->
                Text(value.shopName,color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
                Text(deliveryText(value.request.mode.name)+" · "+deliveryText(value.status.name),color=stateValues.AccentColor,fontSize=stateValues.textSize,fontWeight=FontWeight.Bold)
                Text(deliveryText("estimate")+": "+receiptUiDateTime(value.estimatedFromMillis)+" — "+receiptUiDateTime(value.estimatedToMillis),color=stateValues.TextColor,fontSize=stateValues.textSize)
                value.request.lines.forEach { row ->
                    Text("${row.line.title} · ${row.line.units} × ${row.line.basis.pricedAmount.toString().removeSuffix(".0")} ${row.line.unitName.visibleLocalizedString(stateValues.appLanguage,row.line.basis.unitId)} · ${marketMoneyLabel(row.unitPriceMinor,row.line.basis.currencyCode)}",
                        color=stateValues.TextColor,fontSize=stateValues.smallTextSize)
                }
                Text(deliveryText("fee")+": "+marketMoneyLabel(value.request.feeMinor,value.request.currency),color=stateValues.TextColor,fontSize=stateValues.textSize)
                value.totalMinor?.let { Text(marketMoneyLabel(it,value.request.currency),color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold) }
                Text(listOf(value.request.address.recipient,value.request.address.phone,value.request.address.address,value.request.address.note).filter(String::isNotBlank).joinToString("\n"),color=stateValues.TextColor,fontSize=stateValues.textSize)
                value.courierName?.let { Text(deliveryText("courier")+": "+it,color=stateValues.TextColor,fontSize=stateValues.textSize) }
                value.handoverCode?.let { handover ->
                    DeliveryCard {
                        Text(deliveryText("code")+": "+handover,color=stateValues.AccentColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
                        Text(deliveryText("code_help"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                    }
                }
                if (value.request.mode==MarketDeliveryMode.EXPRESS && !value.terminal) {
                    val position=value.position
                    Text(deliveryText(if (position?.isFresh(clock)==true) "location_fresh" else "location_stale"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                    if (position?.isFresh(clock)==true) actionButton(text=deliveryText("open_map"),autoLoading=false,confirmationRequired=false,onClick={
                        ui.launch { openExternalUrlPlatformAction?.invoke("https://www.openstreetmap.org/?mlat=${position.latitude}&mlon=${position.longitude}#map=16/${position.latitude}/${position.longitude}") }
                    })
                    if (canTrack) MarketPublishToggle(deliveryText("share_location"),sharing,true) { sharing=it }
                }
                value.events.forEach { event ->
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("•",color=stateValues.AccentColor,fontSize=stateValues.textSize)
                        Column {
                            Text(deliveryText("action.${event.action.name}"),color=stateValues.TextColor,fontSize=stateValues.smallTextSize)
                            Text(receiptUiDateTime(event.atMillis)+event.note.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty(),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                        }
                    }
                }
                val actions=value.availableActions(account==value.buyerId,currentUserOwnsStore(value.branchStoreId),account==value.courierId)
                if (action == null) actions.forEach { option ->
                    actionButton(text=deliveryText("action.${option.name}"),autoLoading=false,confirmationRequired=false,onClick={action=option;error=null;code=""})
                }
                action?.takeIf { it in actions }?.let { selected ->
                    DeliveryCard {
                        Text(deliveryText("action.${selected.name}"),color=stateValues.TextColor,fontSize=stateValues.textSize,fontWeight=FontWeight.Bold)
                        if (selected == MarketDeliveryAction.ASSIGN) {
                            aitaFormTextField(Modifier.fillMaxWidth(),courierId,{courierId=it.take(16)},deliveryText("courier_id"),identityKey="delivery-courier:$id",parentOwnsValue=true)
                            Text(deliveryText("courier_help"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                        }
                        if (selected in setOf(MarketDeliveryAction.CANCEL,MarketDeliveryAction.DELAY))
                            aitaFormTextField(Modifier.fillMaxWidth(),note,{note=it.take(500)},deliveryText("note"),identityKey="delivery-note:$id",parentOwnsValue=true)
                        if (selected == MarketDeliveryAction.DELIVER && account != value.buyerId)
                            aitaFormTextField(Modifier.fillMaxWidth(),code,{code=it.filter(Char::isDigit).take(6)},deliveryText("code"),identityKey="delivery-code:$id",keyboardType=KeyboardType.Number,sensitive=true,parentOwnsValue=true)
                        if (selected == MarketDeliveryAction.DELAY) {
                            Text(deliveryText("from_now"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                            aitaFormTextField(Modifier.fillMaxWidth(),from,{from=it.filter(Char::isDigit).take(6)},deliveryText("minutes_min"),identityKey="delivery-from:$id",keyboardType=KeyboardType.Number,parentOwnsValue=true)
                            aitaFormTextField(Modifier.fillMaxWidth(),to,{to=it.filter(Char::isDigit).take(6)},deliveryText("minutes_max"),identityKey="delivery-to:$id",keyboardType=KeyboardType.Number,parentOwnsValue=true)
                        }
                        error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage,""),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize) }
                        actionButton(text=stateValues.stringConfirm,enabled=!busy,loading=busy,autoLoading=false,confirmationRequired=false,onClick={
                            if (owner != null && !busy) {
                                actionFocus.clearFocus(force=true)
                                busy=true;error=null
                                val time=getCurrentTimeMillis()
                                val command=MarketDeliveryCommand(Uuid.random().toString(),selected,value.id,value.revision,courierPublicId=courierId.takeIf(String::isNotBlank),
                                    note=note,handoverCode=code.takeIf(String::isNotBlank),
                                    estimatedFromMillis=from.toLongOrNull()?.takeIf { it in 0..129600 }?.let { time+it*60_000 },
                                    estimatedToMillis=to.toLongOrNull()?.takeIf { it in 0..129600 }?.let { time+it*60_000 })
                                ui.launch { try {
                                    val result=sendMarketDeliveryCommand(owner,command)
                                    if (owner.isCurrent()) {
                                        error=result.error
                                        result.acknowledged?.let { order=it.order;action=null;code="";note="" }
                                        if (result.pending != null) { action=null;error=eventMessage("delivery.pending") }
                                        refresh++
                                    }
                                } finally { busy=false } }
                            }
                        })
                        actionButton(text=stateValues.stringCancel,autoLoading=false,confirmationRequired=false,onClick={action=null;code=""})
                    }
                }
            }
            if (action == null) error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage,""),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize) }
            actionButton(text=deliveryText("refresh"),iconPath=stateValues.drawablePathIconRefresh,autoLoading=false,confirmationRequired=false,onClick={refresh++})
        }
    }
}

/** Foreground-only, explicit opt-in. No tracking runs after this sheet is closed. */
@Composable internal expect fun CourierLocationSharing(enabled: Boolean,onPosition: (MarketCourierPosition)->Unit,onError: (String)->Unit)
