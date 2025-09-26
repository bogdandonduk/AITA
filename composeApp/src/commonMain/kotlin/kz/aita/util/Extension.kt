package kz.aita.util

import androidx.compose.ui.graphics.Color

fun String.toColor(): Color {
  return Color(toULong(radix = 16).toInt())
}

fun String.checkAsEmail(): Boolean {
  return isNotEmpty() && isNotBlank() && !contains(" ") &&
      contains("@") && contains(".") &&
      Regex("^[a-zA-Z0-9]").matches(first().toString()) &&
      filter { it == '@' }.length == 1 && lastIndexOf(".") > lastIndexOf("@") &&
      lastIndexOf(".") != lastIndex
}

fun String.checkAsPassword(): Boolean {
  return length >= 8
}

