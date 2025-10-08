package kz.aita.compose.screen.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.*
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.render.kamelConfig
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.core.getFullDrawableResourceUrl

@Composable
fun AppConfiguration.MenuListScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    ScreenAppBarWidget(
      title = stateValues.stringMenu,
      iconPath = stateValues.drawablePathIconMenu
    )

    LazyColumn(
      modifier = Modifier
        .weight(1f)
    ) {
      items(Navigation.Menu.listScreens) { model ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable(
              interactionSource = remember {
                MutableInteractionSource()
              },
              indication = ripple(color = stateValues.TextColor)
            ) {
              coroutineScope.launch {
                Navigation.Menu.go(model, stateValues.isNarrowScreen)
              }
            },
          verticalAlignment = Alignment.CenterVertically
        ) {
          val isActive = stateValues.run {
            if (isNarrowScreen)
              navigationScreensMenuLeft
            else
              navigationScreensMenuRight
          }.last().route == model.route

          CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
            KamelImage(
              modifier = Modifier
                .padding(8.dp)
                .aspectRatio(1f, matchHeightConstraintsFirst = true),
              resource = {
                asyncPainterResource(
                  data = Url(getFullDrawableResourceUrl(model.iconPath))
                )
              },
              contentDescription = stateValues.stringBack,
              colorFilter = if (isActive)
                ColorFilter.tint(stateValues.AccentColor)
              else null
            )
          }

          Text(
            text = model.name,
            color = if (isActive)
              stateValues.AccentColor
            else
              stateValues.TextColor,
            fontWeight = if (isActive)
              FontWeight.Bold
            else
              FontWeight.Normal
          )
        }
      }
    }
  }
}