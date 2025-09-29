package kz.aita.compose.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kz.aita.AppUIConfiguration
import kz.aita.compose.widget.LargeIconWithTitleWidget

@Composable
fun AppUIConfiguration.SplashScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    LargeIconWithTitleWidget(
      modifier = Modifier
        .width(stateValues.boundWidgetWidth),
      imageUrl = stateValues.drawablePathAITALogo,
      title = stateValues.stringLogIn
    )
  }
}