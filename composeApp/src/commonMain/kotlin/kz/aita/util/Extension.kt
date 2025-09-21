package kz.aita.util

import androidx.compose.ui.graphics.Color

fun String.toColor(): Color {
  return Color(this.toInt())
}