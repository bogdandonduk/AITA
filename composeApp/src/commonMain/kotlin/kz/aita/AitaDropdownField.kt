package kz.aita

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Shared presentation only: each caller retains ownership of selection and persistence. */
@Composable
internal fun AppConfiguration.AitaDropdownField(
    modifier: Modifier = Modifier,
    title: String,
    selectedId: String?,
    options: List<DropdownOption>,
    placeholder: String,
    enabled: Boolean = true,
    textColor: Color = stateValues.TextColor,
    titleTextSize: TextUnit = stateValues.accentTextSize,
    titleTextColor: Color = textColor,
    cornerRadius: Dp = stateValues.cornerRadius,
    search: Triple<String?, StateHost?, String?>? = null,
    matches: (DropdownOption, String) -> Boolean = { option, query ->
        option.title.contains(query, true) || option.subtitle.orEmpty().contains(query, true) || option.id.contains(query, true)
    },
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val canExpand = enabled && options.isNotEmpty()
    LaunchedEffect(canExpand) { if (!canExpand) expanded = false }
    val selectedOption = options.firstOrNull { it.id == selectedId }
    val borderWidth by animateDpAsState(
        if (expanded) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
        tween(AITA_MOTION_FAST_MILLIS), label = "dropdown-outline-width")
    val borderColor by animateColorAsState(
        if (expanded) stateValues.AccentColor else stateValues.PlaceholderTextColor,
        tween(AITA_MOTION_FAST_MILLIS), label = "dropdown-outline-color")
    val shape = RoundedCornerShape(cornerRadius)
    val expandLess by stateValues.drawableResIconExpandLess.collectAsState()
    val expandMore by stateValues.drawableResIconExpandMore.collectAsState()
    val expandedDescription = tutorialText(if (expanded) "expanded" else "collapsed")

    Column(modifier.onPreviewKeyEvent { event ->
        if (expanded && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
            expanded = false
            true
        } else false
    }) {
        if (title.isNotBlank()) Text(title, Modifier.padding(bottom = 4.dp), color = titleTextColor,
            fontSize = titleTextSize, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(2.dp).heightIn(min = stateValues.textFieldHeight)
            .foregroundTactileShadow(cornerRadius, elevated = false).clip(shape)
            .background(stateValues.BackgroundColor).border(borderWidth, borderColor, shape)
            .semantics { stateDescription = expandedDescription
                if (!canExpand) disabled() }
            .aitaClickable(enabled = canExpand, role = Role.Button, indication = ripple(color = stateValues.AccentColor)) {
                expanded = !expanded
            }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CpImage(Modifier.size(stateValues.iconSize),
                url = if (expanded) stateValues.drawablePathIconExpandLess else stateValues.drawablePathIconExpandMore,
                fallbackRes = if (expanded) expandLess else expandMore, contentDescription = null,
                tintColor = if (canExpand) textColor else stateValues.PlaceholderTextColor)
            if (selectedOption == null) Text(placeholder, Modifier.weight(1f), color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.textSize, maxLines = 2, overflow = TextOverflow.Ellipsis)
            else DropdownOptionLabel(selectedOption, Modifier.weight(1f),
                if (enabled) textColor else stateValues.PlaceholderTextColor)
        }
        AnimatedVisibility(expanded && canExpand, enter = aitaVisibilityEnter(), exit = aitaVisibilityExit()) {
            Column(Modifier.fillMaxWidth().padding(2.dp)
                .foregroundTactileShadow(cornerRadius, elevated = false).clip(shape)
                .background(stateValues.BackgroundColor)
                .border(stateValues.focusedBorderWidth, stateValues.AccentColor, shape)) {
                // Short enumerations deliberately omit search; category/unit selectors opt in.
                val searchContent = if (search != null) searchTextField(
                    modifier = Modifier.fillMaxWidth(), stateHost = search.second, stateKey = search.third,
                    focusedBorderWidth = 0.dp, unfocusedBorderWidth = 0.dp,
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent) else null
                if (search != null) Spacer(Modifier.fillMaxWidth().height(stateValues.unfocusedBorderWidth)
                    .background(stateValues.PlaceholderTextColor))
                val query = searchContent?.value?.text.orEmpty()
                val visible = if (query.isBlank()) options else options.filter { matches(it, query) }
                if (visible.isEmpty()) MessageText(Modifier.fillMaxWidth().heightIn(min = stateValues.textFieldHeight),
                    text = stateValues.stringNoMatches, textSize = stateValues.textSize)
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = (stateValues.screenHeight / 3).coerceIn(120.dp, 320.dp)),
                    contentPadding = PaddingValues(3.dp)) {
                    items(visible) { option ->
                        val isSelected = option.id == selectedId
                        val available = enabled && option.enabled
                        Row(Modifier.fillMaxWidth().heightIn(min = stateValues.textFieldHeight)
                            .clip(RoundedCornerShape((cornerRadius - 3.dp).coerceAtLeast(0.dp)))
                            .background(if (isSelected) stateValues.AccentColor.copy(alpha = .08f) else Color.Transparent)
                            .semantics { selected = isSelected; if (!available) disabled() }
                            .aitaClickable(enabled = available, role = Role.Button, indication = ripple(color = stateValues.AccentColor)) {
                                expanded = false
                                onSelected(option.id)
                            }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            DropdownOptionLabel(option, Modifier.weight(1f), when {
                                !available -> stateValues.PlaceholderTextColor
                                isSelected -> stateValues.AccentColor
                                else -> textColor
                            }, isSelected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppConfiguration.DropdownOptionLabel(option: DropdownOption, modifier: Modifier, color: Color, selected: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        option.iconPath?.let { CpImage(Modifier.size(stateValues.iconSize), url = it,
            fallbackRes = option.iconRes, contentDescription = null) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(option.title, color = color, fontSize = stateValues.textSize,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            option.subtitle?.takeIf { it.isNotBlank() && it != option.title }?.let {
                Text(it, color = color, fontSize = stateValues.smallTextSize, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
