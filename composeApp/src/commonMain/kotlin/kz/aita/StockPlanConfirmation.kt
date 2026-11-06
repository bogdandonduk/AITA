package kz.aita

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keep review and confirmation in one dialog, including on browser accessibility surfaces. */
@Composable
internal fun AppConfiguration.StockPlanConfirmation(
    message: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .padding(top = 8.dp)
            .border(
                1.dp,
                stateValues.AccentColor.copy(alpha = .5f),
                RoundedCornerShape(stateValues.cornerRadius),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(message, color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actionButton(
                Modifier.weight(1f),
                text = stateValues.stringCancel,
                autoLoading = false,
                confirmationRequired = false,
                onClick = onCancel,
            )
            actionButton(
                Modifier.weight(1f),
                text = stateValues.stringConfirm,
                autoLoading = false,
                confirmationRequired = false,
                onClick = onConfirm,
            )
        }
    }
}
