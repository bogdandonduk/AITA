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
import kz.aita.model.dataModel.GoodsItemInCartDataModel
import kz.aita.model.dataModel.QuantityDataModel

@Composable
fun AppConfiguration.GoodsItemInCartWidget(
  modifier: Modifier = Modifier,
  index: Int? = null,
  goodsItemInCart: GoodsItemInCartDataModel,
  goodsItem: GoodsItemDataModel,
  textColor: Color = stateValues.TextColor,
  onClick: ((GoodsItemDataModel) -> Unit)? = null,
  onDelete: ((GoodsItemDataModel) -> Unit)? = null
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
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(top = 8.dp, bottom = 12.dp),
    ) {
      Text(
        text = index?.run { "${index + 1}.  ${goodsItem.name.extractLocalizedString(stateValues.appLanguage)}" } ?: goodsItem.name.extractLocalizedString(stateValues.appLanguage)!!,
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold,
        color = textColor
      )

      Spacer(
        modifier = Modifier
          .height(6.dp)
      )

      goodsItem.barcode.run {
        if (size == 1) {
          this[0]
        } else {
          foldIndexed("") { index, acc, item ->
            if (index == 0)
              item
            else
              "$acc, $item"
          }
        }
      }.run {
        Text(
          text = this,
          fontSize = stateValues.textSize,
          color = textColor
        )
      }

      stateValues.goodsCategories?.run {
        if (goodsItem.categoryIds.size == 1) {
          "${stateValues.goodsCategories?.find { it.id == goodsItem.categoryIds[0] }?.name?.extractLocalizedString(stateValues.appLanguage)}"
        } else {
          goodsItem.categoryIds.foldIndexed("") { index, acc, item ->
            if (index == 0)
              "${stateValues.goodsCategories?.find { it.id == item }?.name?.extractLocalizedString(stateValues.appLanguage)}"
            else
              "$acc, ${stateValues.goodsCategories?.find { it.id == item }?.name?.extractLocalizedString(stateValues.appLanguage)}"
          }
        }
      }?.run {
        Text(
          text = this,
          fontSize = stateValues.textSize,
          color = textColor
        )
      }

      goodsItem.salePrices.run {
          if (size == 1) {
          "${stateValues.suppliers?.find { it.id == goodsItem.salePrices[0].supplierId }?.name?.extractLocalizedString(stateValues.appLanguage)}"
        } else {
          foldIndexed("") { index, acc, item ->
            if (index == 0) {
              "${stateValues.suppliers?.find { it.id == item.supplierId }?.name?.extractLocalizedString(stateValues.appLanguage)}"
            } else
              "$acc, ${stateValues.suppliers?.find { it.id == item.supplierId }?.name?.extractLocalizedString(stateValues.appLanguage)}"
          }
        }
      }.run {
        Text(
          text = this,
          fontSize = stateValues.textSize,
          color = textColor
        )
      }

      Spacer(
        modifier = Modifier
          .height(6.dp)
      )

//      Text(
//        text = "${stateValues.stringSale}: ${goodsItem.salePrices} ${goodsItem.saleCurrencyToSupplierIds}",
//        fontSize = stateValues.accentTextSize,
//        fontWeight = FontWeight.Bold,
//        color = textColor
//      )
//
//      if (goodsItem.returnPrices != goodsItem.salePrices) {
//        Text(
//          text = "${stateValues.stringReturn}: ${goodsItem.returnPrices} ${goodsItem.returnCurrencyToSupplierIds}",
//          fontSize = stateValues.accentTextSize,
//          fontWeight = FontWeight.Bold,
//          color = textColor
//        )
//      }
//
//      Text(
//        text = "${stateValues.stringSupply}: ${goodsItem.supplyPrices} ${goodsItem.saleCurrencyToSupplierIds}",
//        fontSize = stateValues.accentTextSize,
//        fontWeight = FontWeight.Bold,
//        color = textColor
//      )

      Spacer(
        modifier = Modifier
          .height(4.dp)
      )

      Text(
        text = "${goodsItemInCart.quantity.total.run { if (goodsItem.quantity.roundTotal) toInt() else this }} ${goodsItemInCart.quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage)}",
        fontSize = stateValues.titleTextSize,
        fontWeight = FontWeight.Bold,
        color = stateValues.TextColor
      )
    }

    Column(
      modifier = Modifier
        .padding(end = 16.dp, top = 16.dp, start = 8.dp, bottom = 16.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      onDelete?.let {
        actionButton(
          text = "",
          enabledColor = stateValues.ErrorColor,
          iconPath = stateValues.drawablePathIconDelete,
          iconContentDescription = stateValues.drawablePathIconDelete,
        ) {
          onDelete(goodsItem)
        }
      }

//      Spacer(
//        modifier = Modifier
//          .height(stateValues.marginTextField)
//      )
//
//      onEdit?.let {
//        actionButton(
//          text = "",
//          iconPath = stateValues.drawablePathIconEdit,
//          iconContentDescription = stateValues.drawablePathIconEdit,
//        ) {
//          onEdit(goodsItem)
//        }
//      }
    }
  }
}
