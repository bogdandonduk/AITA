package kz.aita

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Same compact controls for a cart and a single line; no permanent full-width action below the cart. */
@Composable
internal fun AppConfiguration.DiscountMiniEditor(title: String, percent: Double, onApply: (Double) -> Unit) {
    var draft by remember(percent) { mutableStateOf(if (percent == 0.0) "" else percent.moneyText()) }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,color=stateValues.TextColor,fontSize=stateValues.accentTextSize)
        val field=genericTextField(titleText="%",valueInitial=draft,keyboardType=KeyboardType.Decimal,
            placeholderText="0–100",onValueChange={raw,apply->draft=raw;error=false;apply()})
        StockQuantityQuickFillButtons(
            quantityUnit = QuantityDataModel(id = "percent", total = 0.0, pricedAmount = 1.0,
                roundTotal = true, immutableUnitName = listOf(LocalizedStringDataModel("main", "%"))),
            currentText = draft, shortcutAmounts = listOf(0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 50.0),
            onAmountSelected = { selected -> draft = selected; field.replaceText(selected); error = false }
        )
        if(error) Text(pass27Text("discount_error"),color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
        actionButton(text=commerceText("apply"),autoLoading=false,confirmationRequired=false,onClick={
            val value=field.value.text.trim().replace(',','.').let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() }
            if(value==null || !validQuickDiscount(value)) error=true else onApply(value)
        })
    }
}

@Composable
internal fun AppConfiguration.ItemDiscountControls(slot:Int,goodsId:String,onToggle:()->Unit) {
    val discounts by cartQuickDiscountsState.collectAsState()
    val pending by cartCheckoutsState.collectAsState()
    val percent=discounts["0:$slot:$goodsId"] ?: 0.0
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(percent>0) Text("${percent.moneyText()}%",color=stateValues.AccentColor,fontSize=stateValues.smallTextSize)
        actionButton(modifier=Modifier.size(40.dp),text="",iconPath=stateValues.drawablePathIconPromos,
            iconRes=stateValues.drawableResIconPromos.value,iconContentDescription=commerceText("item_discount"),
            enabled="0:$slot" !in pending,autoLoading=false,confirmationRequired=false,onClick=onToggle)
    }

}

@Composable
internal fun AppConfiguration.CartDiscountPanel(slot:Int,onDismiss:()->Unit) {
    val discounts by cartQuickDiscountsState.collectAsState()
    val pending by cartCheckoutsState.collectAsState()
    if("0:$slot" in pending) return
    Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        DiscountMiniEditor(commerceText("cart_discount"),discounts["0:$slot"] ?: 0.0) {setCartQuickDiscount(slot,it);onDismiss()}
        Text(commerceText("discount_help"),color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize)
    }
}
