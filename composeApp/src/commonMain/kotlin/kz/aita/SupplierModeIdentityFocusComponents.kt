package kz.aita

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun AppConfiguration.SupplierIdentityFocusSelector(
    presentation: SupplierIdentityPresentationUiModel,
    onIdentitySelected: (String?) -> Unit
) {
    if (presentation.options.size <= 1) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
    ) {
        Text(
            text = localizedStringResource(2482, "Working identity"),
            color = stateValues.TextColor,
            fontSize = stateValues.smallTextSize,
            fontWeight = FontWeight.Bold
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(stateValues.marginTextField)
        ) {
            items(
                items = presentation.options,
                key = { option -> option.supplierId?.let { "supplier:$it" } ?: "supplier:all" }
            ) { option ->
                TransactionHistoryFilterChip(
                    text = option.title,
                    selected = option.selected,
                    onClick = {
                        if (!option.selected) onIdentitySelected(option.supplierId)
                    }
                )
            }
        }
        Text(
            text = if (presentation.combined) {
                localizedStringResource(2487, "This view combines all of your supplier profiles.")
            } else {
                localizedStringResource(
                    2488,
                    "Only this profile's orders, offers, partners, agreements, delivery work, and insights are shown."
                )
            },
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )
    }
}
