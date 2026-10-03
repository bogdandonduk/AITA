package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun AppConfiguration.CartQuickDiscount(slot: Int, percent: Double) {
    var open by remember(slot) { mutableStateOf(false) }
    val checkouts by cartCheckoutsState.collectAsState()
    if ("0:$slot" in checkouts) return
    actionButton(text = "${quickDiscountLabel(stateValues.appLanguage)} · ${percent.moneyText()}%",
        autoLoading = false, confirmationRequired = false, onClick = { open = true })
    if (open) AitaBottomSheet(title = quickDiscountLabel(stateValues.appLanguage), onDismiss = { open = false }) {
        var draft by remember(slot) { mutableStateOf(percent.moneyText()) }
        var error by remember { mutableStateOf(false) }
        Text(pass27Text("discount_help"), color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
        val input = genericTextField(titleText = "%", valueInitial = draft,
            keyboardType = KeyboardType.Decimal, placeholderText = "0–100", onValueChange = { raw, apply ->
                draft = raw; error = false; apply()
            })
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(0, 5, 10, 15, 20, 25, 50)) { value ->
                actionButton(text = "$value%", autoLoading = false, confirmationRequired = false,
                    onClick = { setCartQuickDiscount(slot, value.toDouble()); open = false })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (error) Text(pass27Text("discount_error"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        actionButton(text = pass27Text("apply"), autoLoading = false, confirmationRequired = false, onClick = {
            val value = input.value.text.trim().replace(',', '.').toDoubleOrNull()
            if (value == null || !validQuickDiscount(value)) error = true
            else { setCartQuickDiscount(slot, value); open = false }
        })
    }
}
