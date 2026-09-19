package kz.aita

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** One horizontal line at every width. Never turns navigation into a vertical stack. */
@Suppress("UNUSED_PARAMETER") // scrollable remains a source-compatible parameter; all tab rows now scroll.
@Composable
internal fun AppConfiguration.AitaTabChips(
    tabs: List<TabContent>, selectedId: String, enabled: Boolean, scrollable: Boolean,
    cornerRadius: Dp, textSize: TextUnit, selectedContainerColor: Color, unselectedContainerColor: Color,
    selectedTextColor: Color, unselectedTextColor: Color, compact: Boolean = false, onSelected: (TabContent) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val chipMaximum = minOf(360.dp, (maxWidth - 8.dp).coerceAtLeast(48.dp))
        @Composable fun Chip(tab: TabContent) {
            val selected = tab.id == selectedId
            val maximum = if(tab.actions.isEmpty()) chipMaximum else maxOf(chipMaximum, (112 + 47 * tab.actions.size).dp)
            val container by animateColorAsState(if (selected) selectedContainerColor else unselectedContainerColor)
            val ink by animateColorAsState(if (selected) selectedTextColor else unselectedTextColor)
            val shape = RoundedCornerShape(cornerRadius)
            Row(
                Modifier.widthIn(min = minOf(64.dp, maximum), max = maximum).heightIn(min = 48.dp).padding(2.dp)
                    .foregroundTactileShadow(cornerRadius, elevated = selected)
                    .clip(shape).background(if (!selected && container == Color.Transparent) stateValues.BackgroundColor else container)
                    .border(if (selected) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
                        if (selected) selectedContainerColor else stateValues.PlaceholderTextColor.copy(alpha = .65f), shape)
                    .alpha(if (enabled) 1f else .55f)
                    .selectable(selected = selected, enabled = enabled, role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = ink),
                        onClick = { onSelected(tab) })
                    .padding(horizontal = 10.dp, vertical = if (tab.actions.isEmpty()) 7.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CpImage(Modifier.size(19.dp), url = marketIconPath(tab.icon.family), fallbackRes = tabIconResource(tab.icon),
                    contentDescription = null, tintColor = ink)
                Text(tab.text, modifier = Modifier.weight(1f, fill = false), color = ink, fontSize = textSize, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                tab.actions.forEach { action -> key(action.id) {
                    IconButton(onClick = action.onClick, enabled = enabled && action.enabled, modifier = Modifier.size(40.dp)) {
                        CpImage(Modifier.size(19.dp), url = marketIconPath(action.icon.family), fallbackRes = tabIconResource(action.icon),
                            contentDescription = action.label, tintColor = ink.copy(alpha = if (action.enabled) 1f else .4f))
                    }
                } }
            }
        }
        HorizontalAitaTabRail(tabs.map {it.id},selectedId,compact) {id->Chip(tabs.first {it.id==id})}
    }
}

/** Shared horizontal geometry also exercised by the offscreen Compose measurement tests. */
@Composable internal fun HorizontalAitaTabRail(ids:List<String>,selectedId:String,compact:Boolean=false,
    content:@Composable (String)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val state=rememberLazyListState()
        val widthPixels=with(LocalDensity.current) { maxWidth.roundToPx() }
        LaunchedEffect(selectedId,ids,widthPixels) {
            val index=ids.indexOf(selectedId)
            if(index<0)return@LaunchedEffect
            // A resize can recompose this effect before LazyRow has measured the new
            // width. Never mistake visibility in the old, wider viewport for success.
            val layout=snapshotFlow {state.layoutInfo}.first {
                it.viewportSize.width==widthPixels && it.totalItemsCount==ids.size
            }
            val visible=layout.visibleItemsInfo.firstOrNull {it.key==selectedId}
            val fullyVisible=visible!=null && visible.offset>=layout.viewportStartOffset &&
                visible.offset+visible.size<=layout.viewportEndOffset
            if(!fullyVisible)state.scrollToItem(index)
        }
        LazyRow(Modifier.fillMaxWidth().selectableGroup(),state=state,
            horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.Start),
            contentPadding=PaddingValues(vertical=if(compact)0.dp else 2.dp)) {
            items(ids,key={it}) {id->content(id)}
        }
    }
}
