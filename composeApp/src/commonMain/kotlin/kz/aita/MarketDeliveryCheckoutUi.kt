package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable internal fun AppConfiguration.MarketDeliveryCheckout(snapshot: MarketShoppingSnapshot,storefront: String,currency: String,
    onDismiss: ()->Unit,onSent: ()->Unit) {
    val account=stateValues.userAccount?.id
    val generation=currentAuthenticatedSessionGeneration()
    val owner=remember(account,generation) { captureMarketRequestScope() }
    val scope=rememberCoroutineScope()
    val host=NavigationScreenModel.Buyer.Main.Shopping
    val draftKey="delivery-draft:$account:$storefront:$currency"
    var draft by remember(draftKey) { mutableStateOf(runCatching {
        host.state.value[draftKey]?.takeIf { it.length<10_000 }?.let { jsonBase.decodeFromString<MarketDeliveryCheckoutDraft>(it) }
    }.getOrNull() ?: MarketDeliveryCheckoutDraft(address=MarketDeliveryAddress(
        recipient=listOf(stateValues.userAccount?.firstName.orEmpty(),stateValues.userAccount?.lastName.orEmpty()).filter(String::isNotBlank).joinToString(" "),
        phone=stateValues.userAccount?.phoneNumber.orEmpty()))) }
    fun change(value: MarketDeliveryCheckoutDraft) { draft=value;host.setStateNow(draftKey to jsonBase.encodeToString(value)) }
    var policy by remember(account,generation,storefront) { mutableStateOf<MarketDeliveryPolicy?>(null) }
    var fresh by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val rows=snapshot.lines.filter { it.line.storeId==storefront && it.line.basis.currencyCode==currency && !it.line.collected }
    val quoted=rows.mapNotNull { row -> row.unitPriceMinor?.takeIf { row.status==MARKET_QUOTE_ESTIMATED }?.let { MarketDeliveryLine(row.line,it) } }
    val subtotal=marketDeliveryItemsTotal(quoted).takeIf { quoted.size==rows.size }
    LaunchedEffect(owner,storefront,refresh) {
        if(owner==null)return@LaunchedEffect
        busy=true
        val key="market-delivery-policy-v1:${owner.accountId}:$storefront"
        try {
            if(policy==null) policy=runCatching { getLocalKv(key)?.let { jsonBase.decodeFromString<MarketDeliveryPolicy>(it) }?.takeIf { it.isValid() } }.getOrNull()
            val response=withTimeoutOrNull(15_000){loadMarketDeliveryPolicy(owner,storefront)}
            if(owner.isCurrent()) {
                val next=response?.payload
                fresh=response?.negative==false && next?.isValid()==true
                if(fresh && next!=null){
                    policy=next;error=null
                    if(draft.mode !in next.modes && next.modes.isNotEmpty()) change(draft.copy(mode=next.modes.minBy { it.ordinal }))
                    runCatching { putLocalKv(key,jsonBase.encodeToString(next)) }
                }
                else {
                    error=response?.message ?: eventMessage("delivery.refresh_failed")
                    if(response?.httpStatusCode in setOf(401,403,404) && response?.transportFailure==false){policy=null;putLocalKv(key,null)}
                }
            }
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){if(owner.isCurrent()){fresh=false;error=eventMessage("delivery.refresh_failed")}}
        finally{busy=false}
    }
    AitaBottomSheet(deliveryText("checkout_review"),iconPath=marketIconPath(186),iconRes=tabIconResource(AitaTabIcon.Truck),onDismiss=onDismiss) {
        val formFocus = LocalFocusManager.current
        Column(Modifier.fillMaxWidth().weight(1f,false).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(rows.firstOrNull()?.line?.shopName.orEmpty(),color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
            Text(deliveryText("request_help"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            if(!fresh)Text(deliveryText("cached"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            rows.forEach { row -> Text("${row.line.title} · ${row.line.units} × ${row.line.basis.pricedAmount.toString().removeSuffix(".0")} ${row.line.unitName.visibleLocalizedString(stateValues.appLanguage,row.line.basis.unitId)}",
                color=stateValues.TextColor,fontSize=stateValues.textSize) }
            val options=policy?.takeIf { it.currency==currency }?.modes.orEmpty().sortedBy { it.ordinal }
            if(options.isEmpty())Text(deliveryText("off"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            else {
                sectionTabsWidget("delivery-mode:$draftKey",options.map{TabContent(it.name,deliveryText(it.name),icon=if(it==MarketDeliveryMode.EXPRESS)AitaTabIcon.Quick else if(it==MarketDeliveryMode.PICKUP)AitaTabIcon.Store else AitaTabIcon.Truck)},
                    selectedId=draft.mode.name,onSelected={value->change(draft.copy(mode=MarketDeliveryMode.valueOf(value)))})
                policy?.let { configured ->
                    if(configured.serviceArea.isNotBlank())Text(configured.serviceArea,color=stateValues.TextColor,fontSize=stateValues.textSize)
                    if(configured.instructions.isNotBlank())Text(configured.instructions,color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                    val estimate=when(draft.mode){
                        MarketDeliveryMode.STANDARD->"${configured.standardMinDays}–${configured.standardMaxDays} · ${deliveryText("days")}"
                        MarketDeliveryMode.EXPRESS->"${configured.expressMinMinutes}–${configured.expressMaxMinutes} · ${deliveryText("minutes")}"
                        MarketDeliveryMode.PICKUP->deliveryText("PICKUP")
                    }
                    Text(estimate,color=stateValues.AccentColor,fontSize=stateValues.textSize)
                }
                aitaFormTextField(Modifier.fillMaxWidth(),draft.address.recipient,{change(draft.copy(address=draft.address.copy(recipient=it.take(120))))},deliveryText("recipient"),identityKey="delivery-recipient:$draftKey",parentOwnsValue=true,enabled=!busy)
                aitaFormTextField(Modifier.fillMaxWidth(),draft.address.phone,{change(draft.copy(address=draft.address.copy(phone=it.take(25))))},deliveryText("phone"),identityKey="delivery-phone:$draftKey",keyboardType=KeyboardType.Phone,parentOwnsValue=true,enabled=!busy)
                if(draft.mode!=MarketDeliveryMode.PICKUP) aitaFormTextField(Modifier.fillMaxWidth(),draft.address.address,{change(draft.copy(address=draft.address.copy(address=it.take(500))))},deliveryText("address"),identityKey="delivery-address:$draftKey",parentOwnsValue=true,enabled=!busy)
                aitaFormTextField(Modifier.fillMaxWidth(),draft.address.note,{change(draft.copy(address=draft.address.copy(note=it.take(500))))},deliveryText("note"),identityKey="delivery-checkout-note:$draftKey",parentOwnsValue=true,enabled=!busy)
                policy?.let { configured ->
                    Text(deliveryText("fee")+": "+marketMoneyLabel(configured.fee(draft.mode),currency),color=stateValues.TextColor,fontSize=stateValues.textSize)
                    subtotal?.takeIf { it<=Long.MAX_VALUE-configured.fee(draft.mode) }?.let { total ->
                        Text(marketMoneyLabel(total+configured.fee(draft.mode),currency),color=stateValues.AccentColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
                    }
                }
            }
            error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage,""),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize) }
            actionButton(text=deliveryText("action.REQUEST"),enabled=!busy,loading=busy,autoLoading=false,confirmationRequired=false,onClick={
                val configured=policy
                when {
                    owner==null || configured==null -> error=eventMessage("delivery.refresh_failed")
                    draft.mode !in options -> error=eventMessage("delivery.off")
                    subtotal==null -> error=eventMessage("delivery.changed")
                    !draft.address.isValid(draft.mode) -> error=eventMessage("delivery.invalid")
                    else -> {
                        formFocus.clearFocus(force=true)
                        busy=true;error=null
                        val request=MarketDeliveryRequest(storefront,snapshot.revision,configured.revision,draft.mode,draft.address,quoted,currency,configured.fee(draft.mode))
                        val command=MarketDeliveryCommand(Uuid.random().toString(),MarketDeliveryAction.REQUEST,request=request)
                        scope.launch {try {
                            val result=sendMarketDeliveryCommand(owner,command)
                            if(owner.isCurrent()) {
                                error=result.error
                                if(result.acknowledged?.commandId==command.commandId || result.pending?.commandId==command.commandId){host.removeState(draftKey);onSent()}
                            }
                        }finally{busy=false}}
                    }
                }
            })
            actionButton(text=deliveryText("refresh"),iconPath=stateValues.drawablePathIconRefresh,enabled=!busy,loading=busy,autoLoading=false,confirmationRequired=false,onClick={refresh++})
        }
    }
}
