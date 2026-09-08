package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun AppConfiguration.InventoryLoadFeedback(
    modifier: Modifier = Modifier,
    status: InventoryLoadStatus,
    emptyText: String? = null,
    compact: Boolean = false
) {
    val message = status.failure?.extractLocalizedString(stateValues.appLanguage)
        ?: if (status.loading || emptyText == null) localizedStringResource(1141, "Please wait…") else emptyText
    Column(
        modifier = modifier.padding(if (compact) 8.dp else 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (status.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compact) 18.dp else 26.dp),
                color = stateValues.AccentColor,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = message,
            color = if (status.failure != null) stateValues.ErrorColor else stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            textAlign = TextAlign.Center
        )
        if (!status.loading) {
            Spacer(Modifier.height(8.dp))
            actionButton(
                text = localizedStringResource(237, "Refresh"),
                fillMaxWidthIfTextPresent = false,
                autoLoading = false,
                confirmationRequired = false,
                onClick = {
                    stateValues.activeStoreId?.let { storeId ->
                        getStock(storeId)
                        getStockBatches(storeId)
                    }
                }
            )
        }
    }
}
