package kz.aita.compose.util

import androidx.compose.ui.graphics.Color
import kz.aita.model.dataModel.CountryDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel

fun String.toColor(): Color {
  return Color(toULong(radix = 16).toInt())
}