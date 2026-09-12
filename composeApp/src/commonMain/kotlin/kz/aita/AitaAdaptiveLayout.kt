package kz.aita

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Release a parent's *minimum* width before capping the actual control. Merely appending
 * widthIn after fillMaxWidth/weight cannot defeat the parent's exact-width constraint.
 * The outer layout slot remains where the parent put it; the clickable surface is bounded.
 */
internal fun Modifier.aitaWidthCap(maximum: Dp = 420.dp,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally): Modifier =
    wrapContentWidth(alignment).widthIn(max = maximum)

internal fun menuPaneMaximumWidth(screen: NavigationScreenModel.Menu): Dp = when(screen) {
    NavigationScreenModel.Menu.Support, NavigationScreenModel.Menu.Analytics,
    NavigationScreenModel.Menu.TransactionHistory, NavigationScreenModel.Menu.OperationLogs -> 1200.dp
    NavigationScreenModel.Menu.List -> 360.dp
    else -> 960.dp
}
