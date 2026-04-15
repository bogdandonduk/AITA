package kz.aita.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppConfiguration.SplashScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    val imageRes by stateValues.drawableResAITALogo.collectAsState()

    LargeIconWithTitleWidget(
      modifier = Modifier
        .width(stateValues.boundWidgetWidth),
      imageUrl = stateValues.drawablePathAITALogo,
      imageRes = imageRes,
      title = stateValues.stringLogIn
    )
  }
}