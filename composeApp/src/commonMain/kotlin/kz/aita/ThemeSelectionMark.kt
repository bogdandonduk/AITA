package kz.aita

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Fixed icon geometry, independent of label wrapping, parent height and user text scale. */
@Composable internal fun ThemeSelectionMark(content:@Composable (Modifier)->Unit) {
    Box(Modifier.size(44.dp),contentAlignment=Alignment.Center) {content(Modifier.size(22.dp))}
}
