package kz.aita.theme

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow

val AccentColor = MutableStateFlow(Color(0xffffba24))
val DarkColor = MutableStateFlow(Color(0xff32363E))
val DarkColorDynamic = MutableStateFlow(Color(0xff32363E))
val GreenColor = MutableStateFlow(Color(0xff6BB522))
val WhatsAppColor = MutableStateFlow(Color(0xff36bb22))
val GreenColorSemiTransparent = MutableStateFlow(Color(0xbb6BB522))
val RedColor = MutableStateFlow(Color(0xffEC493C))
val GrayColor = MutableStateFlow(Color(0xffA7A7A7))
val GrayColorSemiTransparent = MutableStateFlow(Color(0x33A7A7A7))
val AccentColorSemiTransparent = MutableStateFlow(Color(0xaaffba24)) // 0xaa10ab9d
val SecondaryColor = MutableStateFlow(Color(0xffF0F0F0))
val TextColor = MutableStateFlow(Color(0xff404040))
val ActionTextColor = MutableStateFlow(Color(0xaa0000ff))
val TextColorSemiTransparent = MutableStateFlow(Color(0xaa404040))
val AccentTextColor = MutableStateFlow(Color.White)
val BackgroundColor = MutableStateFlow(Color.White)

suspend fun reinitColors(isDarkTheme: () -> Boolean) {
  val isDarkTheme = isDarkTheme()

  BackgroundColor.emit(if (isDarkTheme) Color(0xff32363E) else Color.White)
  SecondaryColor.emit(if (isDarkTheme) Color.DarkGray else Color(0xffF0F0F0))
  TextColor.emit(if (isDarkTheme) Color.White else Color(0xff404040))
  TextColorSemiTransparent.emit(if (isDarkTheme) Color(0xaaffffff) else Color(0xaa404040))
  GreenColor.emit(if (isDarkTheme) Color.Green else Color(0xff6BB522))
  DarkColorDynamic.emit(if (isDarkTheme) Color(0xffF0F0F0) else Color(0xff32363E))
  ActionTextColor.emit(if (isDarkTheme) Color(0xaa00FFFF) else Color(0xaa0000ff))
}