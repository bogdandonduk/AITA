package kz.aita

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun AppConfiguration.WriteOffAnalyticsScreen(prepared:PreparedAnalytics,header:@Composable ()->Unit) {
    val state by StoreCommerceClient.state.collectAsState()
    val data=StoreCommerceClient.current(state)
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    val d=prepared.dashboard
    val rows=remember(data.writeOffs,prepared) {scopedWriteOffs(data.writeOffs,d.storeId,d.startMillis,d.endMillisExclusive,
        d.goodsItemIdFilter,d.supplierIdFilter,d.categoryIdFilter).sortedByDescending {it.timeMillis}}
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(8.dp)) {
        item {header()}
        item {CommercePendingStatus()}
        item {
            Text("${commerceText("writeoff_count")}: ${d.writeOffCount?.toString() ?: "—"}",color=stateValues.TextColor,fontSize=stateValues.accentTextSize)
            d.writeOffCosts.forEach {(currency,cost)->Text("${commerceText("writeoff_cost")}: ${cost.moneyText()} $currency",color=stateValues.AccentColor)}
        }
        if(rows.isEmpty()) item {Text(if(data.historyLoaded) commerceText("empty") else commerceText("failed"),color=stateValues.PlaceholderTextColor)}
        items(rows,key={it.command.id}) {record->
            Column(Modifier.fillMaxWidth().border(stateValues.unfocusedBorderWidth,stateValues.PlaceholderTextColor,RoundedCornerShape(stateValues.cornerRadius))
                .padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(record.goodsName.extractLocalizedString(stateValues.appLanguage).orEmpty(),color=stateValues.TextColor,fontSize=stateValues.accentTextSize)
                Text("-${record.quantityUnit.quantityText(stateValues.appLanguage)} · ${commerceText(record.command.reason.name)}",color=stateValues.TextColor)
                Text("${record.cost.moneyText()} ${record.supplyPrice.currency} · ${record.timeMillis.toStockDateInputText()}",color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                if(record.command.note.isNotBlank()) Text(record.command.note,color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            }
        }
        item {actionButton(text=commerceText("refresh"),iconPath=stateValues.drawablePathIconRefresh,enabled=!busy,loading=busy,
            autoLoading=false,confirmationRequired=false,onClick={busy=true;scope.launch {try {StoreCommerceClient.flush();StoreCommerceClient.refreshWriteOffs()} finally {busy=false}}})}
    }
}
