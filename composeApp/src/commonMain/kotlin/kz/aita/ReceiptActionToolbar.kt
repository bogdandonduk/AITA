package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppConfiguration.ReceiptActionToolbar(
    activeAction: String?,
    onAction: (String, String) -> Unit,
    onFinish: (() -> Unit)? = null
) {
    val dark = isDarkAppTheme(stateValues.appThemeId)
    data class Action(val id: String, val label: String, val success: String, val family: Int, val res: DrawableResource)
    val actions = listOf(
        Action("pdf", stateValues.stringPdf, stateValues.stringReceiptPdfSaved, 126, if (dark) Res.drawable._126_1 else Res.drawable._126_0),
        Action("share", stateValues.stringShare, stateValues.stringReceiptShared, 127, if (dark) Res.drawable._127_1 else Res.drawable._127_0),
        Action("whatsapp", stateValues.stringWhatsApp, stateValues.stringReceiptSentToWhatsApp, 128, if (dark) Res.drawable._128_1 else Res.drawable._128_0),
        Action("print", stateValues.stringPrint, stateValues.stringReceiptSentToPrinter, 129, if (dark) Res.drawable._129_1 else Res.drawable._129_0)
    ) + if (onFinish != null) listOf(Action("finish", stateValues.stringQuit, "", 130, if (dark) Res.drawable._130_1 else Res.drawable._130_0)) else emptyList()
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            actions.forEach { action ->
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(action.label) } },
                    state = rememberTooltipState()
                ) {
                    actionButton(
                        modifier = Modifier.size(44.dp),
                        text = "",
                        iconPath = "svg/${action.family}_${if (dark) 1 else 0}.svg",
                        iconRes = action.res,
                        iconContentDescription = action.label,
                        iconTintColor = stateValues.AccentTextColor,
                        iconSizeOverride = 22.dp,
                        enabled = activeAction == null,
                        loading = activeAction == action.id,
                        autoLoading = false,
                        confirmationRequired = false,
                        onClick = { if (action.id == "finish") onFinish?.invoke() else onAction(action.id, action.success) }
                    )
                }
            }
        }
    }
}
