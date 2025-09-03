package kz.aita.navigation

import androidx.compose.ui.graphics.painter.Painter

sealed class NavigationScreenModel(
  val route: String,
  val name: String = route,
  val iconResId: Painter? = null
) {
  
  object UserAuth : NavigationScreenModel(route = "UserAuthNavigationScreenModelRoute")
}
