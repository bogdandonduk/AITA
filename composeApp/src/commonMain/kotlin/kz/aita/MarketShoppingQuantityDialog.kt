package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Set an absolute count in one reviewed edit. No extrapolated price: quantity promotions and
 * availability are re-quoted by the existing server path after the list command is processed.
 */
@Composable
internal fun AppConfiguration.MarketShoppingQuantityDialog(
    review: MarketShoppingLineReview,
    shopping: MarketShoppingUiState,
    onDismiss: () -> Unit
) {
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val editor = remember(review, shopping) {
        MarketShoppingQuantityUiState(review, { shopping.snapshot }, { shopping.canChange },
            shopping::changeLine, { latestOnDismiss() })
    }
    DisposableEffect(editor) { onDispose { editor.dispose() } }
    val line = review.line
    val unit = line.unitName.visibleLocalizedString(stateValues.appLanguage, line.basis.unitId)
    val amount = line.basis.pricedAmount.toString().removeSuffix(".0")
    val entered = editor.enteredUnits
    Dialog(onDismissRequest = editor::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TransactionBarcodeModalGuard()
        Column(Modifier.fillMaxWidth().aitaWidthCap(560.dp).imePadding().padding(12.dp).heightIn(max = 620.dp)
            .aitaDialogEntrance().clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor.copy(alpha = 0.25f),
                RoundedCornerShape(stateValues.cornerRadius))
            .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(eventMessage("market.shopping_quantity_edit").visibleLocalizedString(stateValues.appLanguage, ""),
                color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(stateValues.AccentColor.copy(alpha = 0.07f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.title, color = stateValues.TextColor, fontSize = stateValues.textSize, fontWeight = FontWeight.Bold)
                Text(line.shopName, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                Text(eventMessage("market.shopping_quantity_current", "units" to line.units.toString(),
                    "amount" to amount, "unit" to unit).visibleLocalizedString(stateValues.appLanguage, ""),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
            aitaFormTextField(Modifier.fillMaxWidth(), editor.draft, editor::edit,
                eventMessage("market.shopping_quantity_label", "max" to MARKET_SHOPPING_MAX_UNITS.toString())
                    .visibleLocalizedString(stateValues.appLanguage, ""),
                identityKey = "shopping-quantity:${review.accountId}:${line.offerId}:${review.expectedRevision}",
                enabled = editor.editable, keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
                onImeAction = { editor.submit() }, autoFocus = true, parentOwnsValue = true,
                // Transient, parent-owned text: do not revive an abandoned generic field draft.
                sensitive = true)
            if (entered != null) Text(eventMessage("market.shopping_quantity_proposed", "units" to entered.toString(),
                "amount" to amount, "unit" to unit).visibleLocalizedString(stateValues.appLanguage, ""),
                color = stateValues.AccentColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
            else Text(eventMessage("market.shopping_quantity_invalid", "max" to MARKET_SHOPPING_MAX_UNITS.toString())
                .visibleLocalizedString(stateValues.appLanguage, ""), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            Text(eventMessage("market.shopping_quantity_help").visibleLocalizedString(stateValues.appLanguage, ""),
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            if (!editor.matches) Text(eventMessage("market.shopping_edit_changed").visibleLocalizedString(stateValues.appLanguage, ""),
                color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            else if (!editor.editable) Text(eventMessage("market.shopping_remove_wait").visibleLocalizedString(stateValues.appLanguage, ""),
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            actionButton(text = eventMessage("market.shopping_quantity_save").visibleLocalizedString(stateValues.appLanguage, ""),
                autoLoading = false, confirmationRequired = false, enabled = editor.canSubmit,
                onClick = { editor.submit() })
            actionButton(text = stateValues.stringCancel, autoLoading = false, confirmationRequired = false,
                enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, onClick = editor::dismiss)
        }
    }
}
