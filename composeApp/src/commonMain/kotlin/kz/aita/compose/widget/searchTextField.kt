package kz.aita.compose.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration

@Composable
fun AppConfiguration.searchTextField(
  modifier: Modifier = Modifier,
  valueInitial: String? = null,
  barcodeCamScanner: Boolean = false
): GenericTextFieldContent {
  var textFieldContent: GenericTextFieldContent? = null

  Column(
    modifier = modifier
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
      trailingIconExtraPath = if (barcodeCamScanner) stateValues.drawablePathIconBarcodeCamScanner else null,
      trailingIconExtraOnClick = if (barcodeCamScanner) {
        {
          barcodeCamScanningExpansionState.targetState =
            !barcodeCamScanningExpansionState.targetState
        }
      } else null
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