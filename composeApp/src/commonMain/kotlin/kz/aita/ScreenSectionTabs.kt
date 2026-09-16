package kz.aita

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * AITA's existing tab appearance with stable section IDs and a single selected section.
 * Keep editable drafts, request jobs and dataset observation in the screen above this row.
 * [selectedId] / [onSelected] allow an explicit navigation action to reveal its destination.
 */
@Composable
internal fun AppConfiguration.sectionTabsWidget(
    stateKey: String,
    tabs: List<TabContent>,
    modifier: Modifier = Modifier.fillMaxWidth(),
    defaultId: String = tabs.firstOrNull()?.id.orEmpty(),
    selectedId: String? = null,
    onSelected: ((String) -> Unit)? = null
): String = key(stateKey) {
    var rememberedId by rememberSaveable { mutableStateOf(defaultId) }
    val resolvedId = resolveScreenSectionId(selectedId ?: rememberedId, tabs.map { it.id }, defaultId)
    tabRowWidget(
        modifier = modifier,
        tabs = tabs.map { tab ->
            TabContent(tab.id, tab.text, icon = tab.icon) { id ->
                rememberedId = id
                onSelected?.invoke(id)
                tab.onClick?.invoke(id)
            }
        },
        selectedIndexInitial = resolvedId,
        persistSelection = false,
        scrollable = tabs.size > 4 || (stateValues.isNarrowScreen && tabs.size > 2),
        textSize = stateValues.smallTextSize
    )
    resolvedId
}
