package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable internal fun AppConfiguration.MarketDeliveryOrderReference(order: MarketDeliveryOrder) {
    Text(deliveryText("order_reference"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SelectionContainer(Modifier.weight(1f)) { Text(order.id, color = stateValues.TextColor, fontSize = stateValues.smallTextSize) }
        ClipboardCopyButton(textToCopy = order.id)
    }
}

@Composable internal fun AppConfiguration.MarketDeliveryItems(order: MarketDeliveryOrder) {
    order.request.lines.forEach { row ->
        DeliveryCard {
            Text(row.line.title, color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
            Text("${row.line.units} × ${row.line.basis.pricedAmount.toString().removeSuffix(".0")} ${row.line.unitName.visibleLocalizedString(stateValues.appLanguage, row.line.basis.unitId)}",
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            Text(marketMoneyLabel(row.unitPriceMinor, order.request.currency), color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
            marketShoppingSubtotal(row.unitPriceMinor, row.line.units)?.let { Text(marketMoneyLabel(it, order.request.currency),
                color = stateValues.AccentColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold) }
        }
    }
    DeliveryCard {
        order.itemsMinor?.let { Text(deliveryText("item_total") + ": " + marketMoneyLabel(it, order.request.currency), color = stateValues.TextColor, fontSize = stateValues.textSize) }
        Text(deliveryText("fee") + ": " + marketMoneyLabel(order.request.feeMinor, order.request.currency), color = stateValues.TextColor, fontSize = stateValues.textSize)
        order.totalMinor?.let { Text(marketMoneyLabel(it, order.request.currency), color = stateValues.AccentColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold) }
    }
}

@Composable internal fun AppConfiguration.MarketDeliveryTimeline(order: MarketDeliveryOrder) {
    if (order.events.size >= 100) Text(deliveryText("recent_events"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
    order.events.asReversed().forEach { event ->
        DeliveryCard {
            Text(deliveryText("action.${event.action.name}"), color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
            Text(receiptUiDateTime(event.atMillis), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            if (event.note.isNotBlank()) SelectionContainer { Text(event.note, color = stateValues.TextColor, fontSize = stateValues.textSize) }
            val from = event.estimatedFromMillis
            val to = event.estimatedToMillis
            if (from != null && to != null)
                Text(deliveryText("estimate") + ": " + receiptUiDateTime(from) + " — " + receiptUiDateTime(to),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        }
    }
}
