package kz.aita

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun supplyQuickFillBase(prices: List<PriceDataModel>, currency: String): Double? =
    prices.firstOrNull { it.currency == currency }?.price?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0.0 && it <= 1_000_000_000_000.0 }

@Composable internal fun AppConfiguration.SupplyPriceQuickFills(
    salePrices: List<PriceDataModel>, currency: String, current: String, onSelected: (String) -> Unit
) {
    val base = supplyQuickFillBase(salePrices, currency) ?: return
    Column {
        Spacer(Modifier.height(6.dp))
        Text(deviceWorkflowText("sale_price_percent", stateValues.appLanguage),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        Spacer(Modifier.height(6.dp))
        DebtPercentQuickButtons(base, currency, current.toDoubleOrNull() ?: 0.0,
            percents = listOf(50.0, 60.0, 70.0, 80.0, 90.0, 100.0)) { onSelected(moneyInputFromDouble(it)) }
        Spacer(Modifier.height(6.dp))
    }
}
