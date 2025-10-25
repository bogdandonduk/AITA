package kz.aita.compose.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.kamel.image.config.LocalKamelConfig
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.render.kamelConfig
import kz.aita.compose.screen.menu.MenuScreen
import kz.aita.compose.screen.stock.StockScreen
import kz.aita.compose.screen.transaction.TransactionScreen
import kz.aita.compose.screen.userAuth.UserAuthScreen
import kz.aita.core.getFullDrawableResourceUrl
import kz.aita.core.io
import kz.aita.core.userRepository
import kz.aita.model.dataModel.NotificationType
import kz.aita.model.wrapper.DataState

@Composable
fun AppConfiguration.MainScreen() {
  Column(
    modifier = Modifier
      .background(stateValues.BackgroundColor)
      .windowInsetsPadding(WindowInsets.systemBars)
      .fillMaxSize()
  ) {
    val showNavigationBar = stateValues.navigationScreensMain.last().run {
      this !is NavigationScreenModel.Splash && this !is NavigationScreenModel.UserAuth
    }

    coroutineScope.launch(Dispatchers.io) {
      userRepository
        .userAccountState
        .value
        .collect {
          if (it is DataState.Empty) {
            Navigation.goMain(NavigationScreenModel.UserAuth.Main)
          } else if ((it is DataState.Success) && Navigation.Main.value.last().run { this is NavigationScreenModel.UserAuth || this is NavigationScreenModel.Splash })
            Navigation.goMain(NavigationScreenModel.Transaction.MainSale)
        }
    }

    Box(
      modifier = Modifier
        .weight(1f)
    ) {
      AnimatedContent(stateValues.navigationScreensMain, label = "") {
        when (stateValues.navigationScreensMain.last()) {
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
      Column(
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
          .height(if (stateValues.latestNotification != null) 80.dp else 56.dp)
          .wrapContentHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        stateValues.latestNotification?.run {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(stateValues.marginTextFieldGroup)
              .background(
                when (type) {
                  NotificationType.Neutral -> stateValues.PlaceholderTextColor
                  NotificationType.Positive -> stateValues.OkayColor
                  NotificationType.Negative -> stateValues.ErrorColor
                }
              ),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = message,
              color = stateValues.AccentTextColor,
              fontSize = stateValues.textSize,
              fontWeight = FontWeight.Bold
            )
          }
        }

        Row(
          modifier = Modifier
            .weight(1f)
            .run {
              if (stateValues.isNarrowScreen)
                fillMaxWidth()
              else
                width((stateValues.boundWidgetWidth * 2))
            }
        ) {
          with(stateValues.navigationScreensMain) {
            Navigation.bottomNavBarScreens.forEach { model ->
              val isSelected = model.route == stateValues.navigationScreensMain.last().route

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
                    indication = ripple(color = stateValues.TextColor)
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
      } else {
      stateValues.latestNotification?.run {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(
              when (type) {
                NotificationType.Neutral -> stateValues.PlaceholderTextColor
                NotificationType.Positive -> stateValues.OkayColor
                NotificationType.Negative -> stateValues.ErrorColor
              }
            ),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = message,
            color = stateValues.AccentTextColor,
            fontSize = stateValues.accentTextSize,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}