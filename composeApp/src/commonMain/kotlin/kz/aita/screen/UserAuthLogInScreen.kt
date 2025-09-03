package kz.aita.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kz.aita.theme.AccentColor
import kz.aita.theme.AccentTextColor
import kz.aita.theme.BackgroundColor
import kz.aita.theme.SecondaryColor
import kz.aita.theme.TextColor
import kz.aita.theme.TextColorSemiTransparent

@Composable
fun UserAuthLogInScreen() {
  val BackgroundColor by BackgroundColor.collectAsState()
  val SecondaryColor by SecondaryColor.collectAsState()
  val AccentColor by AccentColor.collectAsState()
  val AccentTextColor by AccentTextColor.collectAsState()
  val TextColor by TextColor.collectAsState()
  val TextColorSemiTransparent by TextColorSemiTransparent.collectAsState()

  LazyColumn(
    modifier = Modifier
      .background(AccentColor)
      .fillMaxSize()
  ) {
    item {
      Column {

      }
    }
  }
}
