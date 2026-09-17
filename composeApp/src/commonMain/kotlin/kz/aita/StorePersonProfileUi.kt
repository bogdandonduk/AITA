package kz.aita

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.decodeToImageBitmap

internal fun AppConfiguration.storePeopleText(key:String)=eventMessage("people.ui.$key")
    .extractLocalizedString(stateValues.appLanguage).orEmpty()

@Composable internal fun AppConfiguration.StorePersonLink(store:String,person:String?,label:String?=null) {
    if(stateValues.appModeId!=APP_MODE_STORE || person==null || !validStorePersonId(person) || !validStorePersonId(store))return
    key(stateValues.userAccount?.id,currentAuthenticatedSessionGeneration(),store,person) {
        var opened by remember {mutableStateOf(false)}
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(RoundedCornerShape(stateValues.cornerRadius))
            .clickable(role=Role.Button){opened=true}.padding(vertical=10.dp,horizontal=4.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            CpImage(Modifier.size(22.dp),url=stateValues.drawablePathIconPerson,fallbackRes=stateValues.drawableResIconPerson.value,
                contentDescription=null,tintColor=stateValues.AccentColor)
            Text(label?.takeIf {it.isNotBlank()} ?: storePeopleText("view_profile"),Modifier.weight(1f),
                color=stateValues.AccentColor,fontSize=stateValues.smallTextSize,fontWeight=FontWeight.SemiBold)
            Text("›",color=stateValues.AccentColor,fontSize=stateValues.accentTextSize)
        }
        if(opened)StorePersonProfileDialog(store,person){opened=false}
    }
}

@Composable private fun AppConfiguration.StorePersonProfileDialog(store:String,person:String,onClose:()->Unit) {
    val owner=stateValues.userAccount?.id ?: return
    val generation=currentAuthenticatedSessionGeneration()
    val signal by StorePeopleSignals.revision.collectAsState()
    val transport by cloudTransportStatusState.collectAsState()
    var days by remember {mutableIntStateOf(30)}
    var profile by remember(store,person,owner,generation,days) {mutableStateOf<StorePersonProfile?>(null)}
    var loading by remember {mutableStateOf(true)}
    var problem by remember {mutableStateOf(false)}
    LaunchedEffect(store,person,owner,generation,days,signal,transport) {
        // Retain a same-owner observation during live refresh, but never across periods or owners.
        // Every response rechecks server permissions; revoked access clears the observation.
        problem=false;loading=profile==null
        delay(180)
        while(isActive) {
            try {
                val response=withTimeoutOrNull(15_000) {StorePeopleClient.load(store,person,days,generation)}
                ensureActive()
                if(!authenticatedSessionGenerationIsCurrent(generation) || userAccountState.payloadValue?.id!=owner || appModeState.value!=APP_MODE_STORE)return@LaunchedEffect
                val value=response?.payload
                if(response!=null && !response.negative && value!=null && validStorePersonProfile(value,owner,store,person)){
                    profile=value;problem=false
                } else {
                    if(response?.httpStatusCode in setOf(400,401,403,404) || (response!=null && !response.negative))profile=null
                    problem=profile==null
                }
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){problem=profile==null}
            finally {loading=false}
            delay(60_000)
        }
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.padding(16.dp).widthIn(max=760.dp).fillMaxWidth().heightIn(max=stateValues.screenHeight*.88f)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth,stateValues.AccentColor.copy(alpha=.7f),RoundedCornerShape(stateValues.cornerRadius))) {
            Row(Modifier.fillMaxWidth().padding(start=18.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(storePeopleText("profile"),Modifier.weight(1f),color=stateValues.TextColor,fontWeight=FontWeight.Bold,fontSize=stateValues.accentTextSize)
                IconButton(onClick=onClose,modifier=Modifier.size(48.dp)) {
                    CpImage(Modifier.size(22.dp),url=stateValues.drawablePathIconCancel,fallbackRes=stateValues.drawableResIconCancel.value,
                        contentDescription=storePeopleText("close"),tintColor=stateValues.TextColor)
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                val loaded=profile
                if(loading) {
                    LoadingSkeleton(Modifier.fillMaxWidth(),layout=LoadingLayout.Form,rows=3)
                } else if(problem || loaded==null) {
                    Box(Modifier.fillMaxWidth().heightIn(min=180.dp),contentAlignment=Alignment.Center) {
                        Text(storePeopleText("unavailable"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.textSize,textAlign=TextAlign.Center)
                    }
                } else {
                    StorePersonAvatar(loaded.photo?.jpegBase64,loaded.displayName)
                    Text(loaded.displayName.ifBlank {storePeopleText("profile")},color=stateValues.TextColor,fontSize=stateValues.titleTextSize,
                        fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                    Text(loaded.storeName.extractLocalizedString(stateValues.appLanguage).orEmpty(),color=stateValues.PlaceholderTextColor,
                        fontSize=stateValues.smallTextSize,textAlign=TextAlign.Center)
                    Text(storePeopleText("role.${loaded.role.lowercase()}"),color=stateValues.AccentColor,fontSize=stateValues.smallTextSize,
                        modifier=Modifier.clip(RoundedCornerShape(14.dp)).background(stateValues.AccentColor.copy(alpha=.12f)).padding(horizontal=14.dp,vertical=6.dp))
                    loaded.jobTitle.extractLocalizedString(stateValues.appLanguage)?.takeIf {it.isNotBlank()}?.let {
                        Text(it,color=stateValues.TextColor,fontSize=stateValues.textSize,textAlign=TextAlign.Center)
                    }
                    loaded.joinedAtMillis?.let {Text(storePeopleText("joined")+": "+receiptUiDateTime(it),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)}
                    Text(storePeopleText("checked")+": "+receiptUiDateTime(loaded.checkedAtMillis),
                        color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                    val stats=loaded.statistics
                    if(stats==null)Text(storePeopleText("stats_private"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize,textAlign=TextAlign.Center)
                    else {
                        tabRowWidget(Modifier.fillMaxWidth(),tabs=listOf(7,30,90).map {day->
                            TabContent(day.toString(),"$day "+storePeopleText("days"),AitaTabIcon.Calendar){days=day}
                        },selectedIndexInitial=days.toString(),textSize=stateValues.smallTextSize)
                        StorePersonStatisticsContent(stats)
                    }
                }
            }
        }
    }
}

@Composable private fun AppConfiguration.StorePersonAvatar(encoded:String?,name:String) {
    val image by produceState<ImageBitmap?>(null,encoded) {
        value=null;value=withContext(Dispatchers.Default){runCatching {profilePhotoJpegBytes(encoded)?.decodeToImageBitmap()}.getOrNull()}
    }
    Box(Modifier.size(96.dp).clip(CircleShape).background(stateValues.AccentColor.copy(alpha=.12f))
        .border(2.dp,stateValues.AccentColor.copy(alpha=.8f),CircleShape),contentAlignment=Alignment.Center) {
        val bitmap=image
        if(bitmap!=null)Image(bitmap,name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else CpImage(Modifier.size(44.dp),url=stateValues.drawablePathIconPerson,fallbackRes=stateValues.drawableResIconPerson.value,
            contentDescription=name,tintColor=stateValues.PlaceholderTextColor)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AppConfiguration.StorePersonStatisticsContent(stats:StorePersonStatistics) {
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp),maxItemsInEachRow=3) {
        listOf("sales" to stats.sales,"returns" to stats.returns,"supplies" to stats.supplies,
            "active_days" to stats.activeDays,"operations" to stats.recordedOperations).forEach {(label,count)->
            Column(Modifier.weight(1f).widthIn(min=120.dp).clip(RoundedCornerShape(14.dp))
                .background(stateValues.AccentColor.copy(alpha=.07f)).padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(count.toString(),color=stateValues.AccentColor,fontSize=stateValues.titleTextSize,fontWeight=FontWeight.Bold)
                Text(storePeopleText(label),color=stateValues.TextColor,fontSize=stateValues.smallTextSize,textAlign=TextAlign.Center)
            }
        }
    }
    if(stats.days.isEmpty()) Text(storePeopleText("no_activity"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize,textAlign=TextAlign.Center)
    if(stats.days.isNotEmpty()) {
        Text(storePeopleText("daily"),color=stateValues.TextColor,fontSize=stateValues.textSize,fontWeight=FontWeight.Bold)
        val max=stats.days.maxOf {it.transactions}.coerceAtLeast(1)
        LazyRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(stats.days,key={it.dateUtc}) {day->
                Column(Modifier.width(65.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.height(80.dp).fillMaxWidth(),contentAlignment=Alignment.BottomCenter) {
                        Box(Modifier.width(24.dp).height((72f*day.transactions.toFloat()/max).coerceIn(2f,72f).dp)
                            .clip(RoundedCornerShape(topStart=6.dp,topEnd=6.dp)).background(stateValues.AccentColor))
                    }
                    Text(day.transactions.toString(),color=stateValues.TextColor,fontSize=stateValues.smallTextSize)
                    Text(day.dateUtc.takeLast(5),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                }
            }
        }
    }
    stats.currencies.forEach {c->
        Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth,stateValues.PlaceholderTextColor.copy(alpha=.35f),RoundedCornerShape(14.dp)).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(c.currency=="?")storePeopleText("unknown_currency") else c.currency,color=stateValues.AccentColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
            listOf("sales" to c.sales,"returns" to c.returns,"net_sales" to c.netSales,"average_sale" to c.averageSale,"supplies" to c.supplies).forEach {(key,value)->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(storePeopleText(key),Modifier.weight(1f),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                    Text(value,color=stateValues.TextColor,fontSize=stateValues.smallTextSize,textAlign=TextAlign.End)
                }
            }
        }
    }
    if(stats.incompletePriceLines>0)Text(storePeopleText("incomplete_totals")+" ("+stats.incompletePriceLines+")",color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize,textAlign=TextAlign.Center)
    Text(storePeopleText("totals_note"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize,textAlign=TextAlign.Center)
    stats.lastActivityMillis?.let {Text(storePeopleText("last_activity")+": "+receiptUiDateTime(it),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)}
}
