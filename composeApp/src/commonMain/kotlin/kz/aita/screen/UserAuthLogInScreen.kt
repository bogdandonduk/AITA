package kz.aita.screen

import aita.composeapp.generated.resources.Res
import aita.composeapp.generated.resources.aita_logo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kz.aita.core.configuration.AppUIConfiguration
import kz.aita.widget.LargeIconWithTitleWidget
import org.jetbrains.compose.resources.painterResource

@Composable
fun AppUIConfiguration.UserAuthLogInScreen() {
  LazyColumn(
    modifier = Modifier
      .background(BackgroundColor)
      .fillMaxSize()
  ) {
    item {
      Column {

        LargeIconWithTitleWidget(
          icon = painterResource(Res.drawable.aita_logo)
        )
      }
    }
  }
}
