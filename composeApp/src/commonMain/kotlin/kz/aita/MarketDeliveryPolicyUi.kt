package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable internal fun AppConfiguration.MarketDeliveryPolicyEditor(store: String,modifier: Modifier = Modifier) {
    val account=stateValues.userAccount?.id
    val generation=currentAuthenticatedSessionGeneration()
    val owner=remember(account,generation,store) { captureMarketRequestScope(store) }
    val scope=rememberCoroutineScope()
    var policy by remember(account,generation,store) { mutableStateOf<MarketDeliveryPolicy?>(null) }
    var error by remember(account,generation,store) { mutableStateOf<List<LocalizedStringDataModel>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    var standard by rememberSaveable(account,store) { mutableStateOf(false) }
    var express by rememberSaveable(account,store) { mutableStateOf(false) }
    var pickup by rememberSaveable(account,store) { mutableStateOf(false) }
    var currency by rememberSaveable(account,store) { mutableStateOf("KZT") }
    var standardFee by rememberSaveable(account,store) { mutableStateOf("0") }
    var expressFee by rememberSaveable(account,store) { mutableStateOf("0") }
    var daysMin by rememberSaveable(account,store) { mutableStateOf("2") }
    var daysMax by rememberSaveable(account,store) { mutableStateOf("5") }
    var minutesMin by rememberSaveable(account,store) { mutableStateOf("45") }
    var minutesMax by rememberSaveable(account,store) { mutableStateOf("120") }
    var area by rememberSaveable(account,store) { mutableStateOf("") }
    var instructions by rememberSaveable(account,store) { mutableStateOf("") }
    var dirty by rememberSaveable(account,store) { mutableStateOf(false) }
    var editRevision by rememberSaveable(account,store) { mutableStateOf<Long?>(null) }
    LaunchedEffect(owner,refresh) {
        if(owner==null)return@LaunchedEffect
        busy=true
        try {
            val response=withTimeoutOrNull(15_000) { loadMarketDeliveryPolicy(owner,store,true) }
            val next=response?.payload
            if (owner.isCurrent()) {
                if(response?.negative==false && next!=null) {
                    error=null
                    if(dirty && policy==null && editRevision!=null) policy=next.copy(revision=editRevision!!)
                    if(!dirty) {
                        policy=next
                        editRevision=next.revision
                        standard=MarketDeliveryMode.STANDARD in next.modes;express=MarketDeliveryMode.EXPRESS in next.modes;pickup=MarketDeliveryMode.PICKUP in next.modes
                        currency=next.currency;standardFee=marketMoneyLabel(next.standardFeeMinor,"").trim();expressFee=marketMoneyLabel(next.expressFeeMinor,"").trim()
                        daysMin=next.standardMinDays.toString();daysMax=next.standardMaxDays.toString();minutesMin=next.expressMinMinutes.toString();minutesMax=next.expressMaxMinutes.toString()
                        area=next.serviceArea;instructions=next.instructions
                    }
                } else error=response?.message ?: eventMessage("delivery.refresh_failed")
            }
        } catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){if(owner.isCurrent())error=eventMessage("delivery.refresh_failed")}
        finally{busy=false}
    }
    fun edited(){dirty=true;saved=false;error=null}
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(deliveryText("request_help"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
        MarketPublishToggle(deliveryText("STANDARD"),standard,!busy){standard=it;edited()}
        MarketPublishToggle(deliveryText("EXPRESS"),express,!busy){express=it;edited()}
        MarketPublishToggle(deliveryText("PICKUP"),pickup,!busy){pickup=it;edited()}
        SimpleDropdownField(title=deliveryText("currency"),placeholder=deliveryText("currency"),selectedId=currency,
            options=listOf("KZT","KGS","TJS","UZS","RUB","USD","EUR").map{DropdownOption(it,it)},restoreLastChoice=false,
            onSelected={if(!busy){currency=it;edited()}})
        if(standard) DeliveryCard {
            Text(deliveryText("STANDARD"),color=stateValues.TextColor,fontSize=stateValues.textSize)
            aitaFormTextField(Modifier.fillMaxWidth(),standardFee,{standardFee=it.take(14);edited()},deliveryText("fee"),identityKey="delivery-standard:$store",keyboardType=KeyboardType.Decimal,parentOwnsValue=true,enabled=!busy)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                aitaFormTextField(Modifier.weight(1f),daysMin,{daysMin=it.filter(Char::isDigit).take(2);edited()},deliveryText("days_min"),identityKey="delivery-days-min:$store",keyboardType=KeyboardType.Number,parentOwnsValue=true,enabled=!busy)
                aitaFormTextField(Modifier.weight(1f),daysMax,{daysMax=it.filter(Char::isDigit).take(2);edited()},deliveryText("days_max"),identityKey="delivery-days-max:$store",keyboardType=KeyboardType.Number,parentOwnsValue=true,enabled=!busy)
            }
        }
        if(express) DeliveryCard {
            Text(deliveryText("EXPRESS"),color=stateValues.TextColor,fontSize=stateValues.textSize)
            aitaFormTextField(Modifier.fillMaxWidth(),expressFee,{expressFee=it.take(14);edited()},deliveryText("fee"),identityKey="delivery-express:$store",keyboardType=KeyboardType.Decimal,parentOwnsValue=true,enabled=!busy)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                aitaFormTextField(Modifier.weight(1f),minutesMin,{minutesMin=it.filter(Char::isDigit).take(4);edited()},deliveryText("minutes_min"),identityKey="delivery-minutes-min:$store",keyboardType=KeyboardType.Number,parentOwnsValue=true,enabled=!busy)
                aitaFormTextField(Modifier.weight(1f),minutesMax,{minutesMax=it.filter(Char::isDigit).take(4);edited()},deliveryText("minutes_max"),identityKey="delivery-minutes-max:$store",keyboardType=KeyboardType.Number,parentOwnsValue=true,enabled=!busy)
            }
        }
        aitaFormTextField(Modifier.fillMaxWidth(),area,{area=it.take(400);edited()},deliveryText("area"),identityKey="delivery-area:$store",parentOwnsValue=true,enabled=!busy)
        aitaFormTextField(Modifier.fillMaxWidth(),instructions,{instructions=it.take(1000);edited()},deliveryText("note"),identityKey="delivery-instructions:$store",parentOwnsValue=true,enabled=!busy)
        error?.let { Text(it.visibleLocalizedString(stateValues.appLanguage,""),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize) }
        if(saved)Text(deliveryText("saved"),color=stateValues.OkayColor,fontSize=stateValues.smallTextSize)
        actionButton(text=stateValues.stringConfirm,enabled=!busy,loading=busy,autoLoading=false,confirmationRequired=false,onClick={
            val original=policy
            val next=original?.copy(modes=buildSet { if(standard)add(MarketDeliveryMode.STANDARD);if(express)add(MarketDeliveryMode.EXPRESS);if(pickup)add(MarketDeliveryMode.PICKUP) },
                currency=currency,standardFeeMinor=if(standard) parseDeliveryFeeMinor(standardFee) ?: -1 else original.standardFeeMinor,
                expressFeeMinor=if(express) parseDeliveryFeeMinor(expressFee) ?: -1 else original.expressFeeMinor,
                standardMinDays=if(standard) daysMin.toIntOrNull() ?: -1 else original.standardMinDays,
                standardMaxDays=if(standard) daysMax.toIntOrNull() ?: -1 else original.standardMaxDays,
                expressMinMinutes=if(express) minutesMin.toIntOrNull() ?: -1 else original.expressMinMinutes,
                expressMaxMinutes=if(express) minutesMax.toIntOrNull() ?: -1 else original.expressMaxMinutes,serviceArea=area,instructions=instructions)
            if(owner==null || next==null)error=eventMessage("delivery.refresh_failed")
            else if(!next.isValid())error=eventMessage("delivery.policy_invalid")
            else {busy=true;scope.launch {try {
                val response=withTimeoutOrNull(15_000){saveMarketDeliveryPolicy(owner,next)}
                if(owner.isCurrent()) {
                    val savedPolicy=response?.payload
                    if(response?.negative==false && savedPolicy!=null){policy=savedPolicy;editRevision=savedPolicy.revision;dirty=false;saved=true;error=null}
                    else error=response?.message ?: eventMessage("delivery.refresh_failed")
                }
            }catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){if(owner.isCurrent())error=eventMessage("delivery.refresh_failed")}
            finally{busy=false}}}
        })
        actionButton(text=deliveryText("refresh"),iconPath=stateValues.drawablePathIconRefresh,enabled=!busy,loading=busy,autoLoading=false,confirmationRequired=dirty,onClick={dirty=false;refresh++})
    }
}
