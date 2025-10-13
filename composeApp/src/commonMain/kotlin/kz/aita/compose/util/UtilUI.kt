package kz.aita.compose.util

import aita.composeapp.generated.resources.Res
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.withStyle
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.GenericResponseDataModel
import kz.aita.model.dataModel.LocalizedStringGroupDataModel
import kz.aita.model.dataModel.StylizedColorGroupDataModel
import kz.aita.model.dataModel.StylizedDimensionGroupDataModel
import kz.aita.model.dataModel.StylizedDrawablePathsGroupDataModel


fun getTransformedTextWithSelectionFocusTextColor(
  textFieldValue: TextFieldValue,
  selectionFocusTextColor: Color
): TransformedText {
  return AnnotatedString.Builder()
    .apply {
      for (i in textFieldValue.text.indices) {
        if (i in textFieldValue.selection.min until textFieldValue.selection.max)
          withStyle(SpanStyle(color = selectionFocusTextColor)) { append(textFieldValue.text[i]) }
        else
          append(textFieldValue.text[i])
      }
    }
    .toAnnotatedString()
    .run {
      TransformedText(
        this,
        offsetMapping = OffsetMapping.Identity
      )
    }
}

fun getPasswordTransformedTextWithSelectionFocusTextColor(
  textFieldValue: TextFieldValue,
  selectionFocusTextColor: Color
): TransformedText {
  return AnnotatedString.Builder()
    .apply {
      val maskChar = '•'
      for (i in textFieldValue.text.indices) {
        if (i in textFieldValue.selection.min until textFieldValue.selection.max)
          withStyle(SpanStyle(color = selectionFocusTextColor)) { append (maskChar) }
        else
          append(maskChar)
      }
    }
    .toAnnotatedString()
    .run {
      TransformedText(
        this,
        offsetMapping = OffsetMapping.Identity
      )
    }
}

suspend fun loadResourceStrings(): List<LocalizedStringGroupDataModel> {
  return jsonBase.decodeFromString<GenericResponseDataModel<List<LocalizedStringGroupDataModel>>>(
    Res.readBytes("files/assets/values/strings.json").decodeToString()
  ).payload!!
}

suspend fun loadResourceDimensions(): List<StylizedDimensionGroupDataModel> {
  return jsonBase.decodeFromString<GenericResponseDataModel<List<StylizedDimensionGroupDataModel>>>(
    Res.readBytes("files/assets/values/dimensions.json").decodeToString()
  ).payload!!
}

suspend fun loadResourceColors(): List<StylizedColorGroupDataModel> {
  return jsonBase.decodeFromString<GenericResponseDataModel<List<StylizedColorGroupDataModel>>>(
    Res.readBytes("files/assets/values/colors.json").decodeToString()
  ).payload!!
}

suspend fun loadResourceDrawablePaths(): List<StylizedDrawablePathsGroupDataModel> {
  return jsonBase.decodeFromString<GenericResponseDataModel<List<StylizedDrawablePathsGroupDataModel>>>(
    Res.readBytes("files/assets/drawable/drawables.json").decodeToString()
  ).payload!!
}
