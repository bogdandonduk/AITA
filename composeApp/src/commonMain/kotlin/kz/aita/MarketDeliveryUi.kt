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
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.channels.Channel
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

@OptIn(ExperimentalUuidApi::class)
@Composable internal fun AppConfiguration.MarketDeliveryOrderSheet(id: String, store: String?, cached: MarketDeliveryOrder?, onDismiss: ()->Unit) {
    val account=stateValues.userAccount?.id
    val generation=currentAuthenticatedSessionGeneration()
    val owner=remember(account,generation,store) { captureMarketRequestScope(store) }
    val ui=rememberCoroutineScope()
    val revision by MarketplaceSignals.revision.collectAsState()
    var order by remember(account,generation,id) { mutableStateOf(cached) }
    var busy by remember { mutableStateOf(false) }
    var fresh by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var commandError by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var mutationEpoch by remember { mutableStateOf(0L) }
    var pending by remember { mutableStateOf<MarketDeliveryCommand?>(null) }
    var detail by rememberSaveable(account,id) { mutableStateOf("overview") }
    val reads = remember(owner,id) { Channel<Unit>(Channel.CONFLATED) }
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
    DisposableEffect(reads) { onDispose { reads.close() } }
    LaunchedEffect(owner,id,revision,refresh) { reads.trySend(Unit) }
    LaunchedEffect(owner,id) {
        val owned = owner ?: return@LaunchedEffect
        for (ignored in reads) {
            delay(200)
            awaitClientBackgroundWork()
            while (reads.tryReceive().isSuccess) { /* keep one trailing read after a burst */ }
            fetching=true
            val readEpoch=mutationEpoch
            try {
                val saved = cachedMarketDeliveryCommand(owned)
                val result=withTimeoutOrNull(15_000) { loadMarketDeliveryOrder(owned,id) }
                if (owned.isCurrent()) {
                    if(readEpoch==mutationEpoch && !busy) pending=saved.pending?.takeIf { (it.orderId ?: it.commandId)==id }
                    val next=result?.payload
                    fresh=result?.negative == false && next?.isValidDeliveryOrder() == true
                    if (fresh && next != null) {
                        clock=getCurrentTimeMillis()
                        if (order?.revision?.let { next.revision >= it } != false) {
                            order=next
                            if(action != null && action !in next.availableActions(account==next.buyerId,currentUserOwnsStore(next.branchStoreId),account==next.courierId)) { action=null;code="" }
                        }
                        // Do not erase validation feedback while the user is correcting a form.
                        if(readEpoch==mutationEpoch && !busy && action==null && pending==null && !commandError) error=null
                    } else {
                        if(readEpoch==mutationEpoch && !busy && !commandError) error=result?.message ?: eventMessage("delivery.refresh_failed")
                        if (result?.httpStatusCode in setOf(401,403,404) && result?.transportFailure == false) { order=null;sharing=false }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owned.isCurrent()) { fresh=false;if(readEpoch==mutationEpoch && !busy && !commandError)error=eventMessage("delivery.refresh_failed") } }
            finally { fetching=false }
        }
    }
    LaunchedEffect(owner,id) { while (isActive) { delay(10_000);awaitClientBackgroundWork();clock=getCurrentTimeMillis();if(!fetching && !busy)reads.trySend(Unit) } }
    val current=order
    val canTrack=fresh && owner?.isCurrent()==true && current?.let { !it.terminal && it.courierId==account && it.courierAccepted && it.request.mode==MarketDeliveryMode.EXPRESS &&
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
            if (!fresh && current != null) Text(deliveryText("cached"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            current?.let { value ->
                Text(value.shopName,color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
                Text(deliveryText(value.request.mode.name)+" · "+deliveryText(value.status.name),color=stateValues.AccentColor,fontSize=stateValues.textSize,fontWeight=FontWeight.Bold)
                MarketDeliveryOrderReference(value)
                sectionTabsWidget("delivery-detail:$account:$id", listOf(
                    TabContent("overview",deliveryText("overview"),icon=AitaTabIcon.Truck),
                    TabContent("items",deliveryText("items"),icon=AitaTabIcon.Stock),
                    TabContent("timeline",deliveryText("timeline"),icon=AitaTabIcon.Recent)),selectedId=detail,onSelected={detail=it;action=null;code=""})
                if(detail=="items") MarketDeliveryItems(value)
                else if(detail=="timeline") MarketDeliveryTimeline(value)
                else {
                if(!value.terminal) Text(deliveryText("estimate")+": "+receiptUiDateTime(value.estimatedFromMillis)+" — "+receiptUiDateTime(value.estimatedToMillis),color=stateValues.TextColor,fontSize=stateValues.textSize)
                value.totalMinor?.let { Text(marketMoneyLabel(it,value.request.currency),color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold) }
                if(value.contactsRemoved) Text(deliveryText("contacts_removed"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                else SelectionContainer { Text(listOf(value.request.address.recipient,value.request.address.phone,value.request.address.address,value.request.address.note).filter(String::isNotBlank).joinToString("\n"),color=stateValues.TextColor,fontSize=stateValues.textSize) }
                value.courierName?.let { Text(deliveryText("courier")+": "+it,color=stateValues.TextColor,fontSize=stateValues.textSize) }
                if(value.courierId!=null && !value.courierAccepted && !value.terminal) Text(deliveryText("courier_not_accepted"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
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
                if(pending!=null) DeliveryCard {
                    Text(deliveryText("pending_order"),color=stateValues.AccentColor,fontSize=stateValues.smallTextSize)
                    actionButton(text=deliveryText("retry"),enabled=!busy,loading=busy,autoLoading=false,confirmationRequired=false,onClick={
                        if(owner!=null && !busy) { mutationEpoch++;busy=true;ui.launch { try {
                            val result=sendMarketDeliveryCommand(owner)
                            if(owner.isCurrent()) {
                                pending=result.pending?.takeIf { (it.orderId ?: it.commandId)==id };error=result.error;commandError=result.error!=null
                                result.acknowledged?.order?.takeIf { it.id==id && it.revision >= (order?.revision ?: -1) }?.let { order=it }
                                refresh++
                            }
                        } finally { busy=false } } }
                    })
                }
                val actions=value.availableActions(account==value.buyerId,currentUserOwnsStore(value.branchStoreId),account==value.courierId)
                if (action == null && pending == null) actions.forEach { option ->
                    actionButton(text=deliveryText("action.${option.name}"),autoLoading=false,confirmationRequired=false,onClick={action=option;error=null;commandError=false;code=""})
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
                            aitaFormTextField(Modifier.fillMaxWidth(),code,{code=it.filter { digit -> digit in '0'..'9' }.take(6)},deliveryText("code"),identityKey="delivery-code:$id",keyboardType=KeyboardType.Number,sensitive=true,parentOwnsValue=true)
                        if (selected == MarketDeliveryAction.DELAY) {
                            Text(deliveryText("from_now"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                            aitaFormTextField(Modifier.fillMaxWidth(),from,{from=it.filter(Char::isDigit).take(6)},deliveryText("minutes_min"),identityKey="delivery-from:$id",keyboardType=KeyboardType.Number,parentOwnsValue=true)
                            aitaFormTextField(Modifier.fillMaxWidth(),to,{to=it.filter(Char::isDigit).take(6)},deliveryText("minutes_max"),identityKey="delivery-to:$id",keyboardType=KeyboardType.Number,parentOwnsValue=true)
                        }
                        error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage,""),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize) }
                        actionButton(text=stateValues.stringConfirm,enabled=!busy,loading=busy,autoLoading=false,confirmationRequired=false,onClick={
                            if (owner != null && !busy) {
                                val prepared=MarketDeliveryActionDraft(note,courierId,code,from,to)
                                    .prepare(value,selected,account==value.buyerId,Uuid.random().toString(),getCurrentTimeMillis())
                                if(prepared is MarketDeliveryActionPreparation.Invalid) {
                                    error=eventMessage(prepared.messageKey);commandError=true
                                } else if(prepared is MarketDeliveryActionPreparation.Ready) {
                                actionFocus.clearFocus(force=true)
                                mutationEpoch++;busy=true;error=null;commandError=false
                                val command=prepared.command
                                ui.launch { try {
                                    val result=sendMarketDeliveryCommand(owner,command)
                                    if (owner.isCurrent()) {
                                        error=result.error
                                        commandError=result.error!=null
                                        result.acknowledged?.takeIf { it.order.id==id }?.let {
                                            if(it.order.revision >= (order?.revision ?: -1)) order=it.order
                                            action=null;code="";note=""
                                        }
                                        pending=result.pending?.takeIf { (it.orderId ?: it.commandId)==id }
                                        if (result.pending != null) { action=null;error=eventMessage("delivery.pending") }
                                        refresh++
                                    }
                                } finally { busy=false } }
                                }
                            }
                        })
                        actionButton(text=stateValues.stringCancel,enabled=!busy,autoLoading=false,confirmationRequired=false,onClick={action=null;code=""})
                    }
                }
            }
            }
            if (action == null) error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage,""),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize) }
            actionButton(text=deliveryText("refresh"),iconPath=stateValues.drawablePathIconRefresh,enabled=!fetching && !busy,loading=fetching,autoLoading=false,confirmationRequired=false,onClick={refresh++})
        }
    }
}

/** Foreground-only, explicit opt-in. No tracking runs after this sheet is closed. */
@Composable internal expect fun CourierLocationSharing(enabled: Boolean,onPosition: (MarketCourierPosition)->Unit,onError: (String)->Unit)
