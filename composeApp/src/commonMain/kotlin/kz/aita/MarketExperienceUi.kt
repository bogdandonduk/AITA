package kz.aita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A small native illustration: theme-aware, static and free of image/network work. */
@Composable
internal fun AppConfiguration.MarketBagIllustration(modifier: Modifier = Modifier) {
    val ink = stateValues.TextColor
    val accent = stateValues.AccentColor
    Canvas(modifier) {
        val u = size.minDimension / 100f
        drawCircle(accent.copy(alpha = .10f), 48*u, Offset(50*u,50*u))
        drawRoundRect(accent.copy(alpha = .22f), Offset(21*u,37*u), Size(58*u,49*u), CornerRadius(12*u))
        drawRoundRect(ink.copy(alpha = .7f), Offset(21*u,37*u), Size(58*u,49*u), CornerRadius(12*u), style = Stroke(2*u))
        drawPath(Path().apply { moveTo(36*u,44*u); lineTo(36*u,31*u); cubicTo(36*u,12*u,64*u,12*u,64*u,31*u); lineTo(64*u,44*u) }, ink, style = Stroke(3*u, cap = StrokeCap.Round))
        drawPath(Path().apply { moveTo(38*u,62*u); lineTo(47*u,71*u); lineTo(63*u,54*u) }, ink, style = Stroke(3*u,cap = StrokeCap.Round))
        drawCircle(accent,4*u,Offset(86*u,28*u)); drawCircle(accent.copy(alpha=.45f),3*u,Offset(13*u,68*u))
    }
}

@Composable
internal fun AppConfiguration.MarketExperienceHero(saved: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(stateValues.cornerRadius))
        .background(stateValues.AccentColor.copy(alpha = .07f)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(marketBrowseText(if (saved) "market.saved_title" else "market.hero_title"), color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
            Text(marketBrowseText(if (saved) "market.saved_subtitle" else "market.hero_subtitle"), color = stateValues.TextColor.copy(alpha=.76f),
                fontSize = stateValues.smallTextSize)
        }
        if (stateValues.screenWidth > 520.dp) MarketBagIllustration(Modifier.size(104.dp))
    }
}

@Composable
internal fun AppConfiguration.MarketHeartButton(saved: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val fill = if (saved) stateValues.AccentColor else stateValues.BackgroundColor
    val ink = if (saved) stateValues.AccentTextColor else stateValues.TextColor
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(fill)
        .border(1.dp, stateValues.TextColor.copy(alpha=.08f),RoundedCornerShape(16.dp))
        .semantics { contentDescription = label }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        CpImage(Modifier.size(23.dp),marketIconPath(140),marketIconFallback(140),null,ink.copy(alpha=if(enabled) 1f else .4f))
    }
}

@Composable
internal fun AppConfiguration.MarketShopIdentity(shop: MarketStorefront, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(stateValues.AccentColor.copy(alpha=.12f)), contentAlignment = Alignment.Center) {
            Text(shop.displayName.trim().take(1).uppercase(),color=stateValues.TextColor,fontSize=stateValues.titleTextSize,fontWeight=FontWeight.Bold)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(shop.displayName,color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
            Text(shop.city,color=stateValues.TextColor.copy(alpha=.7f),fontSize=stateValues.smallTextSize)
        }
    }
}
