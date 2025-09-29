package kz.aita.compose.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.Url
import kotlinx.coroutines.launch
import kz.aita.AppUIConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.render.kamelConfig
import kz.aita.core.getFullDrawableResourceUrl
import kz.aita.core.userRepository

@Composable
fun AppUIConfiguration.MainScreen() {
  Column(
    modifier = Modifier
      .windowInsetsPadding(WindowInsets.systemBars)
      .fillMaxSize()
  ) {
    val navigationScreens by Navigation.Main.collectAsState()
    val showNavigationBar = navigationScreens.last().run {
      this !is NavigationScreenModel.UserAuth
    }

    Box(
      modifier = Modifier
        .weight(1f)
    ) {
      AnimatedContent(navigationScreens, label = "") {
        when (navigationScreens.last()) {
          is NavigationScreenModel.Splash -> {
            SplashScreen()
          }
          is NavigationScreenModel.UserAuth ->
            UserAuthScreen()

          is NavigationScreenModel.Transaction.MainSale, NavigationScreenModel.Transaction.MainReturn, NavigationScreenModel.Transaction.MainSupply ->
            TransactionScreen()

          is NavigationScreenModel.Stock ->
            StockScreen()

          is NavigationScreenModel.Menu ->
            MenuScreen()

          else -> {}
        }
      }
    }

    if (showNavigationBar)
      Box(
        modifier = Modifier
          .clip(
            RoundedCornerShape(
              topStart = stateValues.cornerRadius,
              topEnd = stateValues.cornerRadius
            )
          )
          .border(
            stateValues.unfocusedBorderWidth,
            stateValues.PlaceholderTextColor,
            RoundedCornerShape(
              topStart = stateValues.cornerRadius,
              topEnd = stateValues.cornerRadius
            )
          )
          .fillMaxWidth()
          .height(56.dp)
          .wrapContentHeight(),
        contentAlignment = Alignment.Center
      ) {
        Row(
          modifier = Modifier
            .run {
              if (stateValues.isNarrowScreen)
                fillMaxWidth()
              else
                width((stateValues.boundWidgetWidth * 2))
            }
        ) {
          Navigation.bottomNavBarScreens.forEach { model ->
            val isSelected = model.route == navigationScreens.last().route

            val iconTintColor by animateColorAsState(
              targetValue = if (isSelected) stateValues.AccentColor else stateValues.IconTintColor,
              label = "",
            )

            Column(
              modifier = Modifier
                .weight(1f)
                .clickable(
                  onClick = {
                    coroutineScope.launch {
                      Navigation.goMain(model)
                    }
                  },
                  interactionSource = remember {
                    MutableInteractionSource()
                  },
                  indication = ripple(color = Color.Black)
                ),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally
            ) {

              CompositionLocalProvider(LocalKamelConfig provides kamelConfig) {
                KamelImage(
                  modifier = Modifier
                    .padding(top = 8.dp)
                    .weight(1f)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                  resource = {
                    asyncPainterResource(
                      data = Url(getFullDrawableResourceUrl(model.iconPath))
                    )
                  },
                  contentDescription = model.name,
                  colorFilter = ColorFilter.tint(iconTintColor)
                )
              }

              Text(
                text = model.name,
                color = iconTintColor,
                textAlign = TextAlign.Center,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                  .padding(4.dp)
              )
            }
          }
        }
      }
  }
}