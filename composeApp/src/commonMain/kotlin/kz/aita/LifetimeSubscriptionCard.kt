package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A permanent entitlement, not a billing panel. No refresh, wallet or paid-renewal controls. */
@Composable
internal fun AppConfiguration.LifetimeSubscriptionCard() {
    val ice = Color(0xFFE3FCFF)
    val cyan = Color(0xFF71EEFF)
    val secondary = Color(0xFFABE4F3)
    val shape = RoundedCornerShape(stateValues.cornerRadius)
    val theme = appDrawableThemeId(stateValues.appThemeId)
    val title = authUiText("Lifetime access", "Бессрочный доступ", "Мерзімсіз қолжетімділік", "Мөөнөтсүз мүмкүнчүлүк")

    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
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
