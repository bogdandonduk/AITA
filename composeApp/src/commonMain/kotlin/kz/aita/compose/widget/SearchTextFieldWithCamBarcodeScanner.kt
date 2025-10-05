package kz.aita.compose.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration

@Composable
fun AppConfiguration.SearchTextFieldWithCamBarcodeScanner(
  modifier: Modifier = Modifier,
  valueInitial: String? = null,
): GenericTextFieldContent {
  var textFieldContent: GenericTextFieldContent? = null

  Column(
    modifier = modifier
      .padding(8.dp)
  ) {
    val barcodeCamScanningExpansionState = remember {
      MutableTransitionState(false)
        .apply {
          targetState = false
        }
    }

    textFieldContent = genericTextField(
      valueInitial = valueInitial,
      placeholderText = stateValues.stringSearchByAnyData,
      leadingIconPath = stateValues.drawablePathIconSearch,
      trailingIconExtraPath = stateValues.drawablePathIconBarcodeCamScanner,
      isFocusedInitial = true,
      trailingIconExtraOnClick = {
        barcodeCamScanningExpansionState.targetState =
          !barcodeCamScanningExpansionState.targetState
      }
    )

    AnimatedVisibility(
      modifier = Modifier
        .clip(RoundedCornerShape(stateValues.cornerRadius))
        .wrapContentHeight()
        .height(100.dp),
      visibleState = barcodeCamScanningExpansionState,
      enter = expandVertically(animationSpec = tween(100)),
      exit = shrinkVertically(animationSpec = tween(100))
    ) {

    }
  }

  return textFieldContent!!
}