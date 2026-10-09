package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
internal fun AppConfiguration.ParentStockBulkActions(storeId:String, parentId:String?, selected:List<GoodsItemDataModel>, multi:Boolean,
    onMulti:()->Unit, onAdded:(Set<String>)->Unit) {
    var busy by remember {mutableStateOf(false)}
    var detail by remember {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    val scope=rememberCoroutineScope()
    fun start(all:Boolean) {
        if(busy) return
        val owner=captureReceiptActionOwner()
        busy=true; error=""; detail=""
        scope.launch {
            var added=0;var skipped=0
            val completed=mutableSetOf<String>()
            suspend fun importPage(items:List<GoodsItemDataModel>):Boolean {
                for(source in items) {
                    if(!owner.isCurrent()) return false
                    if(source.id in completed || source.storeId!=parentId) continue
                    val codes=source.allBarcodeValues().map {it.normalizedBarcodeToken()}.toSet()
                    val exists=stockState.payloadValue.orEmpty().any {it.storeId==storeId && it.isActive && it.allBarcodeValues().any {code->code.normalizedBarcodeToken() in codes}}
                    if(exists) {skipped++;completed+=source.id}
                    else {
                        val result=CompletableDeferred<DataState<GoodsItemDataModel>>()
                        val item=source.copy(id=newDiagnosticId(),storeId=storeId,userId=stateValues.userAccount?.id.orEmpty(),
                            activeShelfBatchId=null,createdAtMillis=0,updatedAtMillis=0,updateOperationId=null,expectedUpdatedAtMillis=null)
                        addGoodsItem(item) {result.complete(it)}
                        val saved=result.await()
                        if(saved !is DataState.Success) {error=saved.message?.extractLocalizedString(stateValues.appLanguage)?.takeIf { it.isNotBlank() } ?: checkoutText("save_error");return false}
                        added++;completed+=source.id
                    }
                    detail="${inventoryExperienceText("added")}: $added · ${inventoryExperienceText("skipped")}: $skipped"
                    onAdded(completed.toSet())
                }
                return true
            }
            try {
                if(!all) importPage(selected)
                else {
                    var cursor:String?=null
                    val pageSize=stateValues.globalAppConfiguration.pagingMaxPageSize.coerceIn(1,200)
                    while(owner.isCurrent()) {
                        var page:DataState<List<GoodsItemDataModel>> = DataState.Empty()
                        getParentStoreStock(storeId,limit=pageSize,updateSharedState=false,stableOrder=true,afterId=cursor).collect {page=it}
                        val rows=(page as? DataState.Success)?.payload
                        if(rows==null) {error=page.message?.extractLocalizedString(stateValues.appLanguage)?.takeIf { it.isNotBlank() } ?: checkoutText("save_error");break}
                        if(!importPage(rows) || rows.size<pageSize) break
                        cursor=rows.last().id
                    }
                }
            } catch(cancel:CancellationException) {throw cancel}
            catch(failure:Exception) {RuntimeDiagnostics.capture(failure,"parent_stock_import");error=checkoutText("save_error")}
            finally {busy=false}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            actionButton(modifier=Modifier.weight(1f),text=inventoryExperienceText("select_many"),autoLoading=false,confirmationRequired=false,
                enabled=!busy,onClick=onMulti)
            actionButton(modifier=Modifier.weight(1f),text=inventoryExperienceText("add_all"),loading=busy,enabled=!busy,autoLoading=false,
                confirmationRequired=true,onClick={start(true)})
        }
        if(multi) actionButton(modifier=Modifier.fillMaxWidth(),text="${inventoryExperienceText("add_selected")} (${selected.size})",
            enabled=!busy,loading=busy,autoLoading=false,confirmationRequired=false,onClick={if(selected.isNotEmpty())start(false)})
        if(detail.isNotEmpty()) Text(detail,color=stateValues.AccentColor,fontSize=stateValues.smallTextSize)
        if(error.isNotEmpty()) Text(error,color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
    }
}
