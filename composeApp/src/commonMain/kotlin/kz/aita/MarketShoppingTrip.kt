package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun AppConfiguration.MarketTripProgressBar(progress: MarketTripProgress) {
    Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(marketBrowseText("market.trip_progress", "done" to progress.collected.toString(), "total" to progress.total.toString()),
            color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(stateValues.TextColor.copy(alpha=.09f))) {
            if (progress.fraction > 0f) Box(Modifier.fillMaxWidth(progress.fraction).fillMaxHeight().background(stateValues.AccentColor))
        }
    }
}

@Composable
internal fun AppConfiguration.MarketShoppingTrip(state: MarketShoppingUiState, modifier: Modifier = Modifier, onBrowse: () -> Unit) {
    val snapshot = state.snapshot
    var showRemaining by remember(state) { mutableStateOf(false) }
    var review by remember(state) { mutableStateOf<MarketChecklistReview?>(null) }
    val progress = snapshot?.tripProgress() ?: MarketTripProgress(0,0)
    val collected = snapshot?.lines.orEmpty().filter { it.line.collected }.map { it.line.offerId }
    val groups = snapshot?.lines.orEmpty().groupBy { it.line.storeId }
    LazyColumn(modifier, contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="trip-header") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha=.07f)).padding(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    MarketBagIllustration(Modifier.size(72.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(marketBrowseText(if(progress.total == 0) "market.trip_empty" else if(progress.remaining == 0) "market.trip_ready" else "market.trip_title"),
                            color=stateValues.TextColor,fontSize=stateValues.titleTextSize,fontWeight=FontWeight.Bold)
                        Text(marketBrowseText(if(progress.total > 0 && progress.remaining == 0) "market.trip_ready_hint" else "market.trip_subtitle"),
                            color=stateValues.TextColor.copy(alpha=.75f),fontSize=stateValues.smallTextSize)
                    }
                }
                if (progress.total > 0) MarketTripProgressBar(progress)
                else actionButton(text=marketBrowseText("market.browse_continue"),autoLoading=false,confirmationRequired=false,onClick=onBrowse)
                Text(marketBrowseText("market.trip_personal"),color=stateValues.TextColor.copy(alpha=.65f),fontSize=stateValues.smallTextSize)
            }
        }
        if (progress.total > 0) item(key="trip-filter") {
            sectionTabsWidget("trip-filter",listOf(TabContent("all",marketBrowseText("market.trip_all")),TabContent("left",marketBrowseText("market.trip_left"),icon=AitaTabIcon.Checklist)),
                selectedId=if(showRemaining) "left" else "all",onSelected={showRemaining=it=="left"})
        }
        groups.forEach { (store, rows) ->
            val visible=rows.filter { !showRemaining || !it.line.collected }
            if (visible.isNotEmpty()) {
                item(key="trip-shop:$store") {
                    Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        CpImage(Modifier.size(24.dp),marketIconPath(139),marketIconFallback(139),null,stateValues.TextColor)
                        Text(rows.first().line.shopName,Modifier.weight(1f),color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
                        Text("${rows.count { it.line.collected }}/${rows.size}",color=stateValues.TextColor.copy(alpha=.7f),fontSize=stateValues.smallTextSize)
                    }
                }
                items(visible,key={it.line.offerId}) { row ->
                    val line=row.line
                    val intent=snapshot?.reviewChecklist(listOf(line.offerId),if(line.collected) MARKET_CHECKLIST_UNCHECK else MARKET_CHECKLIST_COLLECT)
                    val enabled=state.canChange && review == null && intent?.matches(state.snapshot)==true
                    val label=marketBrowseText(if(line.collected) "market.trip_uncheck" else "market.trip_collect") + ": " + line.title
                    fun toggle() { if(enabled && intent != null) state.changeChecklist(intent) }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(if(line.collected) stateValues.AccentColor.copy(alpha=.06f) else stateValues.BackgroundColor)
                        .border(1.dp,stateValues.TextColor.copy(alpha=.10f),RoundedCornerShape(stateValues.cornerRadius))
                        .semantics { contentDescription=label }.toggleable(value=line.collected,enabled=enabled,role=Role.Checkbox,onValueChange={toggle()}).padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Checkbox(checked=line.collected,onCheckedChange=null,colors=CheckboxDefaults.colors(
                            checkedColor=stateValues.AccentColor,checkmarkColor=stateValues.AccentTextColor,uncheckedColor=stateValues.TextColor.copy(alpha=.5f)))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(line.title,color=stateValues.TextColor.copy(alpha=if(line.collected) .6f else 1f),fontSize=stateValues.accentTextSize,
                                fontWeight=FontWeight.Medium,textDecoration=if(line.collected) TextDecoration.LineThrough else TextDecoration.None)
                            val amount=line.basis.pricedAmount.toString().removeSuffix(".0")
                            Text("${line.units} × $amount ${line.unitName.visibleLocalizedString(stateValues.appLanguage, "")}",
                                color=stateValues.TextColor.copy(alpha=.65f),fontSize=stateValues.smallTextSize)
                        }
                    }
                }
            }
        }
        if (collected.isNotEmpty()) item(key="trip-cleanup") {
            Column(Modifier.fillMaxWidth().padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                actionButton(text=marketBrowseText("market.trip_remove","count" to collected.size.toString()),autoLoading=false,confirmationRequired=false,
                    enabled=state.canChange,onClick={ review=snapshot?.reviewChecklist(collected,MARKET_CHECKLIST_REMOVE) })
                actionButton(text=marketBrowseText("market.trip_reset"),autoLoading=false,confirmationRequired=false,enabled=state.canChange,
                    enabledColor=stateValues.BackgroundColor,textColor=stateValues.TextColor,onClick={ review=snapshot?.reviewChecklist(collected,MARKET_CHECKLIST_UNCHECK) })
            }
        }
    }
    review?.let { frozen ->
        Dialog(onDismissRequest={review=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Column(Modifier.padding(16.dp).fillMaxWidth().aitaWidthCap(540.dp).heightIn(max=stateValues.screenHeight*.82f)
                .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(marketBrowseText(if(frozen.action==MARKET_CHECKLIST_REMOVE) "market.trip_remove_review" else "market.trip_reset_review","count" to frozen.lines.size.toString()),
                    color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    frozen.lines.forEach { Text("${it.units} × ${it.title}",color=stateValues.TextColor,fontSize=stateValues.textSize) }
                }
                if (!frozen.matches(state.snapshot)) Text(marketBrowseText("market.checklist_changed"),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
                actionButton(text=marketBrowseText("market.confirm"),autoLoading=false,confirmationRequired=false,enabled=state.canChange && frozen.matches(state.snapshot),
                    onClick={state.changeChecklist(frozen);review=null})
                actionButton(text=marketBrowseText("market.cancel"),autoLoading=false,confirmationRequired=false,
                    enabledColor=stateValues.BackgroundColor,textColor=stateValues.TextColor,onClick={review=null})
            }
        }
    }
}
