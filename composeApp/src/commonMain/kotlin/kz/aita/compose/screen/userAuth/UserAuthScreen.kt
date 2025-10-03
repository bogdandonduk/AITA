package kz.aita.compose.screen.userAuth

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kz.aita.AppUIConfiguration
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.compose.widget.LargeIconWithTitleWidget
import kz.aita.core.adminUserRepository

@Composable
fun AppUIConfiguration.UserAuthScreen() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (stateValues.isNarrowScreen) {
      AnimatedContent(
        targetState = stateValues.navigationScreensUserAuthLeft.last()
      ) { model ->
        when (model) {
          is NavigationScreenModel.UserAuth.LogIn -> {
            UserAuthLogInScreen()
          }

          else -> {
            UserAuthSignUpScreen(adminUserRepository = adminUserRepository)
          }
        }
      }
    } else {
      LargeIconWithTitleWidget(
        modifier = Modifier
          .width(stateValues.boundWidgetWidth)
          .aspectRatio(1f, matchHeightConstraintsFirst = true),
        imageUrl = stateValues.drawablePathAITALogo
      )

      Row(
        modifier = Modifier
          .weight(1f)
      ) {
        AnimatedContent(
          modifier = Modifier
            .weight(1f),
          targetState = stateValues.navigationScreensUserAuthLeft.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.UserAuth.LogIn -> {
              UserAuthLogInScreen()
            }

            else -> {
              UserAuthSignUpScreen(adminUserRepository)
            }
          }
        }

        AnimatedContent(
          modifier = Modifier
            .weight(1f),
          targetState = stateValues.navigationScreensUserAuthRight.last()
        ) { model ->
          when (model) {
            is NavigationScreenModel.UserAuth.SignUp -> {
              UserAuthSignUpScreen(adminUserRepository)
            }
            else -> {}
          }
        }
      }
    }
  }
}
