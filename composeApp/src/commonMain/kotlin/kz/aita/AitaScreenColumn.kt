package kz.aita

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Navigation hosts choose a readable BODY width; they must never constrain a screen's bar. */
internal val LocalAitaScreenContentMaximumWidth = staticCompositionLocalOf { 1600.dp }

/**
 * The app bar occupies the entire screen/pane. Only the content below it is centered and capped.
 * Keeping the cap here (instead of on a navigation host) also works for split desktop panes.
 * The content retains a ColumnScope and bounded height, so existing weighted lists/forms keep
 * their scrolling, footer placement and alignment without an extra scroll container.
 */
@Composable
internal fun AitaScreenColumn(
    modifier: Modifier = Modifier.fillMaxSize(),
    maximumContentWidth: Dp = LocalAitaScreenContentMaximumWidth.current,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    appBar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        appBar()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .aitaWidthCap(maximumContentWidth),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}
