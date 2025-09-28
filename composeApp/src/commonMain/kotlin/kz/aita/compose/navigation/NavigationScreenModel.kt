package kz.aita.compose.navigation

import androidx.compose.ui.graphics.painter.Painter

sealed class NavigationScreenModel(
  val route: String,
  val name: String = route,
  val iconResId: Painter? = null
) {
  
  sealed class UserAuth(route: String): NavigationScreenModel(route = route) {

    object LogIn: UserAuth("UserAuthLogInNavigationScreenModelRoute")
    object SignUp: UserAuth("UserAuthLogInNavigationScreenModelRoute")
  }
}
