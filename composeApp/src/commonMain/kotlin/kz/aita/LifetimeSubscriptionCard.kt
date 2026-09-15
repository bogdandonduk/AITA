package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A permanent entitlement, not a billing panel. No refresh, wallet or paid-renewal controls. */
@Composable
internal fun AppConfiguration.LifetimeSubscriptionCard() {
    val ice = Color(0xFFE3FCFF)
    val cyan = Color(0xFF71EEFF)
    val secondary = Color(0xFFABE4F3)
    val shape = RoundedCornerShape(stateValues.cornerRadius)
    val glow = if (LocalWindowInfo.current.isWindowFocused) {
        val transition = rememberInfiniteTransition(label = "lifetimeDiamond")
        transition.animateFloat(
            initialValue = 0.45f, targetValue = 0.90f,
            animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "lifetimeUnderglow"
        )
    } else remember { mutableStateOf(0.60f) }
    val theme = normalizeAppThemePreference(stateValues.appThemeId)
    val title = authUiText("Lifetime access", "Бессрочный доступ", "Мерзімсіз қолжетімділік", "Мөөнөтсүз мүмкүнчүлүк")

    // The glow has its own space INSIDE the lazy-list item, outside the clipped card surface.
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize()) {
            val bottom = size.height - 28.dp.toPx()
            val alpha = glow.value // Read animation in the draw phase, not the whole screen.
            val glowHeight = 50.dp.toPx()
            drawOval(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, cyan.copy(alpha = alpha * 0.32f), Color.Transparent),
                    startY = bottom - glowHeight / 2f, endY = bottom + glowHeight / 2f
                ),
                topLeft = Offset(size.width * 0.10f, bottom - glowHeight / 2f),
                size = Size(size.width * 0.80f, glowHeight)
            )
            drawOval(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xFF26BFFF).copy(alpha = alpha * 0.56f), Color.Transparent),
                    startY = bottom - 10.dp.toPx(), endY = bottom + 12.dp.toPx()
                ),
                topLeft = Offset(size.width * 0.22f, bottom - 10.dp.toPx()),
                size = Size(size.width * 0.56f, 22.dp.toPx())
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(bottom = 36.dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF073554), Color(0xFF08677B), Color(0xFF082B4D))))
                .border(1.dp, Brush.linearGradient(listOf(ice, cyan.copy(alpha = 0.50f), Color(0xFF21B4DA))), shape)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(62.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF031D35).copy(alpha = 0.42f))
                    .border(1.dp, cyan.copy(alpha = 0.30f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    CpImage(
                        modifier = Modifier.size(46.dp),
                        url = stateValues.drawables.orEmpty().extractPath(144L, theme) ?: "svg/144_${theme}.svg",
                        fallbackRes = if (theme == 1L) Res.drawable._144_1 else Res.drawable._144_0,
                        contentDescription = title,
                        tintColor = null
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(title, color = ice, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
                    Text(authUiText("Lifetime access · no expiry", "Бессрочный доступ · без срока окончания",
                        "Мерзімсіз қолжетімділік", "Мөөнөтсүз мүмкүнчүлүк · бүтүү күнү жок"),
                        color = cyan, fontSize = stateValues.smallTextSize, fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, cyan.copy(alpha = 0.60f), Color.Transparent))))
            Text(authUiText("Unlocked by promo code · no renewal charges", "Активирован промокодом · без списаний за продление",
                "Промокодпен қосылған · ұзарту төлемі жоқ", "Промокод менен ачылды · узартуу акысы алынбайт"),
                color = ice, fontSize = stateValues.textSize, fontWeight = FontWeight.Medium)
            Text(authUiText("Access belongs to this location only. Branches subscribe separately.",
                "Доступ действует только для этой точки. У каждого филиала своя подписка.",
                "Қолжетімділік тек осы нүктеге арналған. Әр филиалға бөлек жазылым қажет.",
                "Мүмкүнчүлүк ушул жайга гана таандык. Филиалдар өзүнчө жазылат."),
                color = secondary, fontSize = stateValues.smallTextSize)
        }
    }
}
