package kz.aita.compose

import aita.composeapp.generated.resources.*
import androidx.compose.ui.graphics.Color
import kz.aita.AppLanguageDataModel
import kz.aita.CountryDataModel
import org.jetbrains.compose.resources.DrawableResource

fun String.toColor(): Color {
  return Color(toULong(radix = 16).toInt())
}

fun AppLanguageDataModel.mapIconRes(): DrawableResource {
  return when (language) {
    "en" -> Res.drawable.flag_en
    "ru" -> Res.drawable.flag_ru
    "tj" -> Res.drawable.flag_tj
    else -> Res.drawable.flag_kz
  }
}

fun CountryDataModel.mapIconRes(): DrawableResource {
  return when (locale) {
    "en" -> Res.drawable.flag_en
    "ru" -> Res.drawable.flag_ru
    "tj" -> Res.drawable.flag_tj
    else -> Res.drawable.flag_kz
  }
}

