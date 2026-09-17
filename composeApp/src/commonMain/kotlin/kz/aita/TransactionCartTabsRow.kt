package kz.aita

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable internal fun AppConfiguration.TransactionCartTabsRow(type: Int, selected: Int, supplierId: String?, onSupplier: () -> Unit, onClear: (Int) -> Unit) {
    val counts by DynamicCarts.counts.collectAsState()
    val hydrated by cartPersistenceHydratedState.collectAsState()
    val navigationReady by transactionNavigationRestoredState.collectAsState()
    val bookState by DynamicCarts.state.collectAsState()
    val count = counts[type]
    val ready = hydrated && navigationReady
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()
    val cartLabel = localizedStringResource(95, "Cart")
    var adding by remember(bookState.owner) { mutableStateOf(false) }
    LaunchedEffect(selected, count) { if (selected in 0 until count) scroll.animateScrollToItem(selected) }
    Column(Modifier.fillMaxWidth()) {
        if (bookState.failed) {
            Text(checkoutText("restore_error"), Modifier.padding(8.dp), color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
            actionButton(Modifier.padding(horizontal = 8.dp), text = checkoutText("retry"), iconPath = stateValues.drawablePathIconRefresh,
                autoLoading = false, confirmationRequired = false, onClick = { scope.launch { DynamicCarts.retryRestore() } })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (type == 2) {
                val supplier = stateValues.suppliers.orEmpty().find { it.id == supplierId }
                val title = supplier?.visibleSupplierName(stateValues.appLanguage)?.takeIf { it.isNotBlank() } ?: checkoutText("select_supplier")
                Row(Modifier.widthIn(max = if (stateValues.isNarrowScreen) 150.dp else 250.dp).heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(stateValues.cornerRadius)).background(stateValues.AccentColor.copy(alpha = .12f))
                    .border(stateValues.unfocusedBorderWidth, stateValues.AccentColor, RoundedCornerShape(stateValues.cornerRadius))
                    .clickable(enabled = ready, role = Role.Button, onClick = onSupplier).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CpImage(Modifier.size(20.dp), url = stateValues.drawablePathIconSuppliers, fallbackRes = stateValues.drawableResIconSuppliers.value,
                        contentDescription = null, tintColor = stateValues.AccentColor)
                    Text(title, Modifier.weight(1f, fill = false), color = stateValues.AccentColor, fontSize = stateValues.smallTextSize,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            LazyRow(Modifier.weight(1f), state = scroll, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items((0 until count).toList(), key = { it }) { index ->
                    val cart by getCartState(type, index).collectAsState()
                    val active = selected == index
                    val tint = if (active) stateValues.AccentTextColor else stateValues.TextColor
                    Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(stateValues.cornerRadius))
                        .background(if (active) stateValues.AccentColor else stateValues.BackgroundColor)
                        .border(stateValues.unfocusedBorderWidth, if (active) stateValues.AccentColor else stateValues.PlaceholderTextColor,
                            RoundedCornerShape(stateValues.cornerRadius))
                        .semantics { this.selected = active; contentDescription = "$cartLabel ${index + 1}" }
                        .clickable(enabled = ready, role = Role.Tab) { scope.launch { Navigation.transactionWorkspace(type).setClientId(index) } }
                        .padding(start = 10.dp, end = if (cart.isEmpty()) 10.dp else 0.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CpImage(Modifier.size(20.dp), url = stateValues.drawablePathIconCart, fallbackRes = stateValues.drawableResIconCart.value,
                            contentDescription = null, tintColor = tint)
                        Text((index + 1).toString(), fontWeight = FontWeight.Bold, color = tint, fontSize = stateValues.smallTextSize)
                        if (cart.isNotEmpty()) {
                            Text("(${cart.size})", color = tint, fontSize = stateValues.smallTextSize)
                            IconButton(enabled = ready, onClick = { onClear(index) }, modifier = Modifier.size(44.dp)) {
                                CpImage(Modifier.size(16.dp), url = stateValues.drawablePathIconCancel, fallbackRes = stateValues.drawableResIconCancel.value,
                                    contentDescription = localizedStringResource(1176, "Clear cart?"), tintColor = tint)
                            }
                        }
                    }
                }
                item("add") {
                    IconButton(enabled = ready && !adding && count < MAX_CART_SLOTS, onClick = {
                        adding = true
                        scope.launch {
                            try { DynamicCarts.add(type)?.let { Navigation.transactionWorkspace(type).setClientId(it) } }
                            catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                            catch (_: Exception) { postInAppNotification(checkoutText("add_error"), NotificationType.Negative, transient = true) }
                            finally { adding = false }
                        }
                    }, modifier = Modifier.size(44.dp).border(stateValues.unfocusedBorderWidth, stateValues.AccentColor,
                        RoundedCornerShape(stateValues.cornerRadius))) {
                        CpImage(Modifier.size(22.dp), url = stateValues.drawablePathIconAdd, fallbackRes = stateValues.drawableResIconAdd.value,
                            contentDescription = checkoutText("add_cart"), tintColor = stateValues.AccentColor)
                    }
                }
            }
        }
        if (count >= MAX_CART_SLOTS) Text(checkoutText("limit"), Modifier.padding(horizontal = 8.dp),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
    }
}
internal fun AppConfiguration.checkoutText(key: String) =
    eventMessage("checkout.ui.$key").extractLocalizedString(stateValues.appLanguage).orEmpty()
