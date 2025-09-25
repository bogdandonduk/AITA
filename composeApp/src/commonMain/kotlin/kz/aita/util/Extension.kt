package kz.aita.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun String.toColor(): Color {
  return Color(toULong(radix = 16).toInt())
}

fun Float.screenWidthDivideAsSp(screenWidth: Dp): TextUnit {
  return (screenWidth.value / this).sp
}

fun Float.screenWidthDivideAsDp(screenWidth: Dp): Dp {
  return (screenWidth.value / this).dp
}
