package kz.aita.compose.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration
import kz.aita.core.extractLocalizedString
import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.model.dataModel.QuantityDataModel

@Composable
fun AppConfiguration.GoodsItemInStockWidget(
  modifier: Modifier = Modifier,
  index: Int? = null,
  goodsItem: GoodsItemDataModel,
  textColor: Color = stateValues.TextColor,
  soldForPeriod: QuantityDataModel? = null,
  returnedForPeriod: QuantityDataModel? = null,
  onClick: ((GoodsItemDataModel) -> Unit)? = null,
  onDelete: (GoodsItemDataModel) -> Unit,
  onEdit: (GoodsItemDataModel) -> Unit,
) {
  Row(
    modifier
      .padding(bottom = 4.dp)
      .fillMaxHeight()
      .run {
        onClick?.run {
          clickable(
            interactionSource = remember {
              MutableInteractionSource()
            },
            indication = ripple(color = textColor, radius = stateValues.cornerRadius),
            onClick = {
              this(goodsItem)
            }
          )
        } ?: this
      }
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        stateValues.PlaceholderTextColor,
        RoundedCornerShape(
          stateValues.cornerRadius
        )
      )
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

    Column(
      modifier = Modifier
        .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp)
    ) {
      Spacer(
        Modifier
          .size(12.dp)
          .clip(RoundedCornerShape(stateValues.cornerRadius))
          .background(quantityMarkerColor)
      )
    }

    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(top = 8.dp, bottom = 12.dp),
    ) {
      Text(
        text = index?.run { "${index + 1}.  ${goodsItem.name}" } ?: goodsItem.name,
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold,
        color = textColor
      )

      Spacer(
        modifier = Modifier
          .height(6.dp)
      )

      Text(
        text = goodsItem.barcode,
        fontSize = stateValues.textSize,
        color = textColor
      )

      Text(
        text = "Category",
        fontSize = stateValues.textSize,
        color = textColor
      )

      Text(
        text = "Supplier",
        fontSize = stateValues.textSize,
        color = textColor
      )

      Spacer(
        modifier = Modifier
          .height(6.dp)
      )

      Text(
        text = "${stateValues.stringSale}: ${goodsItem.salePricesToSupplierIds} ${goodsItem.saleCurrencyToSupplierIds}",
        fontSize = stateValues.accentTextSize,
        fontWeight = FontWeight.Bold,
        color = textColor
      )

      if (goodsItem.returnPricesToSupplierIds != goodsItem.salePricesToSupplierIds) {
        Text(
          text = "${stateValues.stringReturn}: ${goodsItem.returnPricesToSupplierIds} ${goodsItem.returnCurrencyToSupplierIds}",
          fontSize = stateValues.accentTextSize,
          fontWeight = FontWeight.Bold,
          color = textColor
        )
      }

      Text(
        text = "${stateValues.stringSupply}: ${goodsItem.supplyPricesToSupplierIds} ${goodsItem.saleCurrencyToSupplierIds}",
        fontSize = stateValues.accentTextSize,
        fontWeight = FontWeight.Bold,
        color = textColor
      )

      Spacer(
        modifier = Modifier
          .height(4.dp)
      )

      Text(
        text = "${goodsItem.quantity.total.run { if (goodsItem.quantity.roundTotal) toInt() else this }} ${goodsItem.quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage)}",
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold,
        color = quantityMarkerColor
      )
    }

    Column(
      modifier = Modifier
        .padding(end = 16.dp, top = 16.dp, start = 8.dp, bottom = 16.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      actionButton(
        text = "",
        enabledColor = stateValues.ErrorColor,
        iconPath = stateValues.drawablePathIconDelete,
        iconContentDescription = stateValues.drawablePathIconDelete,
      ) {
        onDelete(goodsItem)
      }

      Spacer(
        modifier = Modifier
          .height(16.dp)
      )

      actionButton(
        text = "",
        iconPath = stateValues.drawablePathIconEdit,
        iconContentDescription = stateValues.drawablePathIconEdit,
      ) {
        onEdit(goodsItem)
      }
    }
  }
}
