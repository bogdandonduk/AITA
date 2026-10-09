package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

@Composable
internal fun AppConfiguration.CompactQuickStockAdd(request: QuickStockAddSheetRequest, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val owner = remember { captureReceiptActionOwner() }
    val configuration = stateValues.globalAppConfiguration
    val defaultCurrency = configuration.countries.withSupportedCountries().firstOrNull {
        it.locale.equals(stateValues.userAccount?.countryLocale, true)
    }?.currencies?.firstOrNull()?.code ?: "KZT"
    var draft by remember(request) { mutableStateOf(StockAddEditDraft(barcodes=listOf(request.barcode),
        name=emptyLocalizedItemForCurrentLanguage(), measurementUnitId=configuration.goodsItemsQuantityUnits.firstOrNull()?.id ?: "0", isQuickItem=true)) }
    var price by remember { mutableStateOf(PriceDataModel("",defaultCurrency,"")) }
    var quantity by remember { mutableStateOf("1") }
    var kind by remember { mutableStateOf(StockBatchKindDataModel.UNIVERSAL) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<GoodsItemDataModel?>(null) }
    val batchId = remember { newDiagnosticId() }
    val unit = configuration.goodsItemsQuantityUnits.firstOrNull { it.id==draft.measurementUnitId }
        ?: configuration.goodsItemsQuantityUnits.first()
    val title = when(request.transactionTypeIndex) { 1 -> stateValues.stringReturnPrice; 2 -> stateValues.stringSupplyPrice; else -> stateValues.stringSalePrice }
    AitaBottomSheet(title=localizedStringResource(636,"Quick add item"),iconPath=stockAddIconPath(),onDismiss={if(!saving)onDismiss()}) {
        LazyColumn(Modifier.weight(1f, fill=false).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if (created == null) {
            item { BarcodeListEditor(stateValues.stringBarcode,draft.visibleBarcodes(),draft.visibleBarcodeTypes(),
                onChanged={draft=draft.copy(barcodes=it)},onTypesChanged={draft=draft.copy(barcodeTypes=it)},
                onBarcodesAndTypesChanged={codes,types->draft=draft.copy(barcodes=codes,barcodeTypes=types)}) }
            item { StockLocalizedStringGroupEditor(title=stateValues.stringName,placeholder=stateValues.stringEnterName,values=draft.name,
                addText=stateValues.stringAddTranslation,required=true,onChanged={draft=draft.copy(name=it)}) }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.Bottom) {
                    Column(Modifier.weight(2f), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(title, color=stateValues.TextColor, fontSize=stateValues.textSize, fontWeight=FontWeight.Bold)
                        SimpleTextInput(value=price.price, placeholder="0", keyboardType=KeyboardType.Decimal, onValueChange={
                            val number=it.replace(',', '.')
                            if(number.isEmpty() || number.isNumericalDoubleString()) price=price.copy(price=number)
                        })
                    }
                    SimpleDropdownField(modifier=Modifier.weight(1f), title=localizedStringResource(268,"Currency"),
                        placeholder=localizedStringResource(269,"Select currency"), selectedId=price.currency,
                        options=configuration.countries.withSupportedCountries().flatMap {it.currencies}.distinctBy {it.code}
                            .map {DropdownOption(it.code,it.code)}, onSelected={price=price.copy(currency=it)})
                }
            }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.Bottom) {
                    SimpleDropdownField(modifier=Modifier.weight(1f),title=stateValues.stringMeasurementUnit,placeholder=stateValues.stringMeasurementUnit,selectedId=draft.measurementUnitId,
                        options=configuration.goodsItemsQuantityUnits.map {DropdownOption(it.id,it.immutableUnitName.extractLocalizedString(stateValues.appLanguage).orEmpty())},
                        onSelected={draft=draft.copy(measurementUnitId=it)})
                    SimpleDropdownField(modifier=Modifier.weight(1f),title=returnFlowText("batch_kind"),placeholder=returnFlowText("batch_kind"),selectedId=kind.name,
                        options=listOf(StockBatchKindDataModel.UNIVERSAL,StockBatchKindDataModel.UNLIMITED).map { DropdownOption(it.name,returnFlowText(it.name.lowercase())) },
                        onSelected={kind=StockBatchKindDataModel.valueOf(it)})
                }
            }
            item {
                if(kind==StockBatchKindDataModel.UNLIMITED) Text(inventoryExperienceText("unlimited_help"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
                Text(inventoryExperienceText(if(request.transactionTypeIndex==0 && kind!=StockBatchKindDataModel.UNLIMITED) "quick_quantity" else "operation_quantity"),color=stateValues.TextColor)
                SimpleTextInput(value=quantity,placeholder=localizedStringResource(271,"Quantity"),keyboardType=if(unit.roundTotal)KeyboardType.Number else KeyboardType.Decimal,
                    onTransformValue={sanitizeStockQuantityInput(it,!unit.roundTotal)},onValueChange={quantity=it})
                Spacer(Modifier.height(6.dp))
                StockQuantityQuickFillButtons(unit,quantity,onAmountSelected={quantity=it})
            }
            } else item {
                Text(created!!.name.extractLocalizedString(stateValues.appLanguage).orEmpty(),color=stateValues.TextColor)
                Text("${price.price} ${price.currency} · $quantity ${unit.immutableUnitName.extractLocalizedString(stateValues.appLanguage).orEmpty()}",color=stateValues.PlaceholderTextColor)
                Text(inventoryExperienceText("quick_retry"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
            }
        }
        if(error.isNotEmpty()) Text(error,color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
        actionButton(modifier=Modifier.fillMaxWidth().padding(top=8.dp),text=localizedStringResource(637,"Save item and add to cart"),
            loading=saving,enabled=!saving,autoLoading=false,confirmationRequired=false,onClick={
                val complete=draft.copy(salePrices=listOf(price),returnPrices=listOf(price),supplyPrices=listOf(price))
                val count=parseStockQuantityInputText(quantity,unit)
                error=complete.stockDraftErrors(configuration).joinToString("\n") {stockEditingMessage(it).extractLocalizedString(stateValues.appLanguage).orEmpty()}
                if(count==null || !count.isFinite() || count<=0 || count>1_000_000_000.0) error=commerceText("bad_quantity")
                if(error.isNotEmpty() || count==null) return@actionButton
                saving=true
                scope.launch {
                    try {
                        if(!owner.isCurrent()) return@launch
                        var item=created
                        if(item==null) {
                            val result=CompletableDeferred<DataState<GoodsItemDataModel>>()
                            addGoodsItem(complete.toGoodsItem(stateValues.activeStoreId.orEmpty(),configuration,null)) {result.complete(it)}
                            val value=result.await()
                            if(value !is DataState.Success) {error=value.message?.extractLocalizedString(stateValues.appLanguage)?.takeIf { it.isNotBlank() } ?: checkoutText("save_error");return@launch}
                            item=value.payload;created=item
                        }
                        if(!owner.isCurrent()) return@launch
                        val batch=GoodsBatchDataModel(id=batchId,goodsItemId=item.id,storeId=item.storeId,kind=StockBatchKindDataModel.UNIVERSAL,unlimitedQuantity=kind==StockBatchKindDataModel.UNLIMITED,
                            quantity=unit.copy(total=if(kind==StockBatchKindDataModel.UNLIMITED) 1.0 else if(request.transactionTypeIndex==0)count else 0.0),
                            supplyPrice=price,status=StockBatchStatusDataModel.Delivered)
                        val savedBatch=CompletableDeferred<DataState<List<GoodsBatchDataModel>>>()
                        addGoodsBatches(listOf(batch)) {savedBatch.complete(it)}
                        val result=savedBatch.await()
                        if(result !is DataState.Success) {error=result.message?.extractLocalizedString(stateValues.appLanguage)?.takeIf { it.isNotBlank() } ?: checkoutText("save_error");return@launch}
                        if(!owner.isCurrent()) return@launch
                        val cartResult=CompletableDeferred<Boolean>()
                        addGoodsItemToTransactionCart(item,request.transactionTypeIndex,request.clientId,configuration,
                            getCartState(request.transactionTypeIndex,request.clientId).value,unit.copy(total=count)) {cartResult.complete(it)}
                        if(cartResult.await()) {request.onAdded();closeQuickStockAddSheet();requestTransactionBarcodeFocus()}
                        else error=checkoutText("save_error")
                    } catch(cancel:CancellationException) {throw cancel}
                    catch(failure:Exception) {RuntimeDiagnostics.capture(failure,"quick_stock_add");error=checkoutText("save_error")}
                    finally {saving=false}
                }
            })
    }
}
