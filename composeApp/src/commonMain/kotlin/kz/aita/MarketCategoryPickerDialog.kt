package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Browsing the taxonomy is local and reversible. Nothing changes until Apply; cancel retains the
 * current discovery scope. Unknown/deleted selected IDs are not converted silently to All. */
@Composable
internal fun AppConfiguration.MarketCategoryPickerDialog(
    catalogue: MarketCategoryCatalogue,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit
) {
    val tree = remember(catalogue) { MarketCategoryTree(catalogue.categories) }
    var chosen by remember { mutableStateOf(selectedId) }
    var search by remember { mutableStateOf("") }
    val language = stateValues.appLanguage
    fun name(category: MarketCategory) = category.name.visibleLocalizedString(language, category.id)
    val path = tree.pathTo(chosen)
    val scroll = rememberLazyListState()
    LaunchedEffect(chosen) { scroll.scrollToItem(0) }
    val rows = remember(tree, chosen, search, language) {
        val terms = search.lowercase().map { if (it.isWhitespace()) ' ' else it }.joinToString("").split(' ').filter { it.isNotBlank() }
        (if (terms.isEmpty()) tree.childrenOf(chosen) else catalogue.categories.filter { category ->
            val text = category.name.joinToString(" ") { it.value }.lowercase()
            terms.all { it in text }
        }).sortedWith(compareBy<MarketCategory> { name(it).lowercase() }.thenBy { it.id })
    }
    val validChoice = chosen == null || chosen in tree.byId
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).fillMaxWidth().aitaWidthCap(720.dp).heightIn(max = stateValues.screenHeight * 0.88f)
            .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.BackgroundColor).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CpImage(Modifier.size(32.dp), url = marketIconPath(18), fallbackRes = marketIconFallback(18), contentDescription = null, tintColor = stateValues.AccentColor)
                Text(authUiText("Categories", "Категории", "Санаттар", "Категориялар"), color = stateValues.TextColor, fontWeight = FontWeight.Bold, fontSize = stateValues.titleTextSize)
            }
            // Keep the field, breadcrumbs and explanation in the scrolling body. A short
            // desktop window or an open keyboard must not push the confirmation actions away.
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), state = scroll,
                verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                item(key = "category-controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        aitaFormTextField(Modifier.fillMaxWidth(), search, { search = it.take(120) }, authUiText("Find a category", "Найти категорию", "Санатты табу", "Категория табуу"),
                            identityKey = "market-category-search", sensitive = true, autoFocus = false, parentOwnsValue = true)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            actionButton(text = authUiText("All categories", "Все категории", "Барлық санаттар", "Бардык категориялар"), autoLoading = false, confirmationRequired = false,
                                fillMaxWidthIfTextPresent = false, enabledColor = if (chosen == null) stateValues.AccentColor else stateValues.BackgroundColor,
                                textColor = if (chosen == null) stateValues.AccentTextColor else stateValues.TextColor, onClick = { chosen = null; search = "" })
                            if (path.size > 4) Text("…", Modifier.padding(8.dp), color = stateValues.PlaceholderTextColor)
                            path.takeLast(4).forEach { category ->
                                actionButton(text = name(category).substringAfterLast(" / "), autoLoading = false, confirmationRequired = false,
                                    fillMaxWidthIfTextPresent = false, enabledColor = if (category.id == chosen) stateValues.AccentColor else stateValues.BackgroundColor,
                                    textColor = if (category.id == chosen) stateValues.AccentTextColor else stateValues.TextColor,
                                    onClick = { chosen = category.id; search = "" })
                            }
                        }
                        if (!validChoice) Text(authUiText("The selected category was removed. Choose another or All categories.",
                            "Выбранная категория удалена. Выберите другую или «Все категории».", "Таңдалған санат жойылды. Басқасын не «Барлық санаттар» тармағын таңдаңыз.", "Тандалган категория алынып салынды. Башкасын же «Бардык категорияларды» тандаңыз."),
                            color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    }
                }
                item(key = "category-help") {
                    Text(authUiText("Includes all subcategories. A category may have no published offers in your search area.",
                        "Включает все подкатегории. В вашей области поиска в категории может не быть опубликованных предложений.",
                        "Барлық ішкі санаттарды қамтиды. Іздеу аймағыңызда осы санатта жарияланған ұсыныстар болмауы мүмкін.", "Бардык ички категорияларды камтыйт. Издөө аймагыңызда категориянын жарыяланган сунуштары жок болушу мүмкүн."),
                        color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
                if (rows.isEmpty()) item {
                    Text(if (search.isNotBlank()) authUiText("No matching categories", "Категории не найдены", "Сәйкес санаттар табылмады", "Дал келген категориялар жок")
                        else authUiText("No subcategories. Apply this category to view its offers.", "Подкатегорий нет. Примените категорию, чтобы открыть предложения.", "Ішкі санаттар жоқ. Ұсыныстарын көру үшін осы санатты қолданыңыз.", "Ички категориялар жок. Сунуштарын көрүү үчүн ушул категорияны колдонуңуз."),
                        Modifier.padding(12.dp), color = stateValues.PlaceholderTextColor, fontSize = stateValues.textSize)
                }
                items(rows, key = { it.id }) { category ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(stateValues.AccentColor.copy(alpha = 0.06f)).clickable(role = Role.Button) { chosen = category.id; search = "" }
                        .padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (search.isBlank()) name(category).substringAfterLast(" / ") else name(category), Modifier.weight(1f),
                            color = stateValues.TextColor, fontSize = stateValues.textSize, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (tree.childrenOf(category.id).isNotEmpty()) Text("›", color = stateValues.AccentColor, fontSize = stateValues.accentTextSize)
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                actionButton(text = authUiText("Apply category", "Применить категорию", "Санатты қолдану", "Категорияны колдонуу"), enabled = validChoice,
                    fillMaxWidthIfTextPresent = false, autoLoading = false, confirmationRequired = false, onClick = { onSelected(chosen) })
                actionButton(text = authUiText("Cancel", "Отмена", "Бас тарту", "Жокко чыгаруу"), fillMaxWidthIfTextPresent = false,
                    enabledColor = stateValues.BackgroundColor, textColor = stateValues.TextColor, autoLoading = false, confirmationRequired = false, onClick = onDismiss)
            }
        }
    }
}
