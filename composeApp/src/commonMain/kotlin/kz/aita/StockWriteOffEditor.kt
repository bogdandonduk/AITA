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

@Composable
internal fun AppConfiguration.StockWriteOffEditor(batch:GoodsBatchDataModel,onDismiss:()->Unit,onSaved:()->Unit) {
    var amount by rememberSaveable(batch.id) {mutableStateOf("")}
    var reason by rememberSaveable(batch.id) {mutableStateOf(WriteOffReason.DAMAGED.name)}
    var note by rememberSaveable(batch.id) {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val current=stateValues.stockBatches?.find {it.id==batch.id} ?: batch
    AitaBottomSheet(title=commerceText("writeoffs"),onDismiss={if(!busy) onDismiss()}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(commerceText("writeoff_help"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            Text("${commerceText("available")}: ${current.quantity.quantityText(stateValues.appLanguage)}",color=stateValues.TextColor)
            SimpleTextInput(Modifier.fillMaxWidth(),amount,commerceText("quantity"),autoFocus=false,
                keyboardType=if(current.quantity.allowsFractionalStockQuantityInput()) KeyboardType.Decimal else KeyboardType.Number,
                onValueChange={amount=it;error=""})
            StockQuantityQuickFillButtons(current.quantity,amount,shortcutAmounts=
                ((if(current.quantity.roundTotal) listOf(1.0,2.0,5.0,10.0) else listOf(0.1,0.25,0.5,1.0)) + current.quantity.total)
                    .distinct().filter {writeOffRemaining(current.quantity,it)!=null}.sorted(),onAmountSelected={amount=it;error=""})
            AitaDropdownField(title=commerceText("reason"),selectedId=reason,
                options=WriteOffReason.entries.map {DropdownOption(it.name,commerceText(it.name))},placeholder=commerceText("reason"),onSelected={reason=it;error=""})
            SimpleTextInput(Modifier.fillMaxWidth(),note,commerceText("note"),autoFocus=false,onValueChange={note=it;error=""})
            if(error.isNotBlank()) Text(error,color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
            actionButton(text=commerceText("writeoff"),iconPath=stateValues.drawablePathIconSubtract,enabled=!busy,loading=busy,autoLoading=false,confirmationRequired=true,onClick={
                val number=parseStockQuantityInputText(amount,current.quantity)
                if(reason==WriteOffReason.OTHER.name && note.isBlank()) error=commerceText("other_note")
                else if(number==null || writeOffRemaining(current.quantity,number)==null) error=commerceText("bad_quantity")
                else {
                    busy=true;error=""
                    scope.launch {try {
                        val result=StoreCommerceClient.writeOff(current,number,WriteOffReason.valueOf(reason),note)
                        if(result.negative) error=result.message?.extractLocalizedString(stateValues.appLanguage).orEmpty() else onSaved()
                    } catch(cancel:CancellationException){throw cancel}
                    catch(_:Exception){error=commerceText("failed")}
                    finally {busy=false}}
                }
            })
        }
    }
}
