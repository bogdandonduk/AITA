package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Unlike a generic confirmation, this owns a frozen row, quantity, owner and list revision.
 * Price-only refreshes are harmless. Changed intent disables removal instead of rebinding it.
 * The entire small panel scrolls on short windows/large text sizes; dismissal never sends a write.
 */
@Composable
internal fun AppConfiguration.MarketShoppingRemoveDialog(
    review: MarketShoppingLineReview,
    shopping: MarketShoppingUiState,
    onDismiss: () -> Unit
) {
    val line = review.line
    val matches = review.matches(shopping.snapshot)
    val unit = line.unitName.visibleLocalizedString(stateValues.appLanguage, line.basis.unitId)
    val amount = line.basis.pricedAmount.toString().removeSuffix(".0")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().aitaWidthCap(560.dp).padding(12.dp).heightIn(max = 560.dp)
            .aitaDialogEntrance().clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.25f),
                RoundedCornerShape(stateValues.cornerRadius))
            .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(eventMessage("market.shopping_remove_title").visibleLocalizedString(stateValues.appLanguage, ""),
                color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.07f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.title, color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                Text(line.shopName, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                Text("${line.units} × $amount $unit", color = stateValues.AccentColor, fontSize = stateValues.accentTextSize)
            }
            Text(eventMessage("market.shopping_remove_review").visibleLocalizedString(stateValues.appLanguage, ""),
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            if (!matches) Text(eventMessage("market.shopping_edit_changed").visibleLocalizedString(stateValues.appLanguage, ""),
                color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            else if (!shopping.canChange) Text(eventMessage("market.shopping_remove_wait").visibleLocalizedString(stateValues.appLanguage, ""),
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            actionButton(text = stateValues.stringCancel, autoLoading = false, confirmationRequired = false,
                enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, onClick = onDismiss)
            actionButton(text = stateValues.stringDelete, autoLoading = false, confirmationRequired = false,
                enabled = matches && shopping.canChange, onClick = {
                    // Recheck at the callback boundary, not only in the rendered enabled flag.
                    if (shopping.changeLine(review, 0)) onDismiss()
                })
        }
    }
}
