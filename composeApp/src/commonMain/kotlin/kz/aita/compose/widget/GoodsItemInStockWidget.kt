package com.aita.retail.app.system.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.Dimension
import com.aita.retail.R
import com.aita.retail.app.system.ui.dataModel.QuantityWithUnitDataModelUI
import com.aita.retail.app.system.ui.theme.AccentColor
import com.aita.retail.app.system.ui.theme.AccentTextColor
import com.aita.retail.app.system.ui.theme.BackgroundColor
import com.aita.retail.app.system.ui.theme.DarkColorDynamic
import com.aita.retail.app.system.ui.theme.GreenColor
import com.aita.retail.app.system.ui.theme.RedColor
import com.aita.retail.app.system.ui.theme.SecondaryColor
import com.aita.retail.app.system.ui.theme.TextColor
import com.aita.retail.app.system.ui.theme.defaultTextSize
import com.aita.retail.app.system.ui.theme.largeCornerRadius
import com.aita.retail.app.system.ui.theme.largeTextSize
import com.aita.retail.app.system.ui.theme.mediumIconSize
import com.aita.retail.app.system.ui.theme.mediumOffset
import com.aita.retail.app.system.ui.theme.mediumTextSize
import com.aita.retail.app.system.ui.theme.smallIconSize
import com.aita.retail.app.system.ui.theme.smallOffset
import com.aita.retail.app.system.ui.theme.smallTextPadding
import com.aita.retail.app.system.ui.theme.xLargeOffset
import com.aita.retail.app.system.ui.theme.xSmallTextPadding
import kz.aita.AppConfiguration
import kz.aita.compose.widget.actionButton
import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.model.dataModel.QuantityDataModel

@Composable
fun AppConfiguration.GoodsItemInStockWidget(
  modifier: Modifier = Modifier,
  index: Int? = null,
  goodsItem: GoodsItemDataModel,
//  soldForPeriod: QuantityDataModel? = null,
//  returnedForPeriod: QuantityDataModel? = null,
  onDeleteAction: (String) -> Unit,
) {
  Column(
    modifier
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        stateValues.PlaceholderTextColor,
        RoundedCornerShape(
          stateValues.cornerRadius
        )
      )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {

      val quantityMarkerColor = when {
        goodsItem.quantity.total <= 9 -> stateValues.ErrorColor
        goodsItem.quantity.total <= 19 -> stateValues.BorderlineBadColor
        else -> stateValues.OkayColor
      }

      val soldQuantityMarkerColor = when {
        (soldForPeriod?.total ?: goodsItem.quantity.total) <= 30 -> stateValues.ErrorColor
        (soldForPeriod?.total ?: goodsItem.quantity.total) <= 50 -> stateValues.BorderlineBadColor
        else -> stateValues.OkayColor
      }

      val returnedQuantityMarkerColor = when {
        (returnedForPeriod?.total ?: goodsItem.quantity.total) >= 30 -> stateValues.ErrorColor
        (returnedForPeriod?.total ?: goodsItem.quantity.total) >= 15 -> stateValues.BorderlineBadColor
        else -> stateValues.OkayColor
      }

      Row {
        Spacer(
          Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(1000.dp))
            .background(quantityMarkerColor)
        )

        Text(
          text = index?.run { "${index + 1}.  ${goodsItem.name}" } ?: goodsItem.name,
          modifier = Modifier
            .padding(start = 4.dp),
          fontSize = stateValues.accentTextSize,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Start,
          color = stateValues.TextColor
        )
      }

      actionButton(
        text = "",
        iconPath = stateValues.drawablePathIconCancel,
        iconContentDescription = stateValues.drawablePathIconCancel,
      ) {
        onDeleteAction(goodsItem.barcode)
      }
    }
  }
}
