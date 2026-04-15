package kz.aita.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kz.aita.userRepository

@Composable
fun AppConfiguration.UserAuthScreen() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      item {
        if (stateValues.isNarrowScreen) {
          AnimatedContent(
            targetState = stateValues.navigationScreensUserAuthLeft.last()
          ) { model ->
            when (model) {
              is NavigationScreenModel.UserAuth.LogIn -> {
                UserAuthLogInScreen()
              }

              else -> {
                UserAuthSignUpScreen(userRepository = userRepository)
              }
            }
          }
        } else {
          val drawableResAITALogo by stateValues.drawableResAITALogo.collectAsState()

          LargeIconWithTitleWidget(
            modifier = Modifier
              .width(stateValues.boundWidgetWidth)
              .aspectRatio(1f, matchHeightConstraintsFirst = true),
            imageUrl = stateValues.drawablePathAITALogo,
            imageRes = drawableResAITALogo,
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
                  UserAuthSignUpScreen(userRepository)
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
                  UserAuthSignUpScreen(userRepository)
                }
                else -> { }
              }
            }
          }
        }
      }
    }
  }
}
