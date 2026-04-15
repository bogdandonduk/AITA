package kz.aita.compose

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
import kz.aita.GoodsItemDataModel
import kz.aita.GoodsItemInCartDataModel
import kz.aita.extractLocalizedString

@Composable
fun AppConfiguration.GoodsItemInCartWidget(
  modifier: Modifier = Modifier,
  index: Int? = null,
  goodsItemInCart: GoodsItemInCartDataModel,
  goodsItem: GoodsItemDataModel,
  textColor: Color = stateValues.TextColor,
  onClick: ((GoodsItemDataModel) -> Unit)? = null,
  onDelete: ((GoodsItemDataModel) -> Unit)? = null,
  increaseQuantityAction: () -> Unit,
  decreaseQuantityAction: () -> Unit
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
        .padding(16.dp),
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

      Spacer(
        modifier = Modifier
          .height(6.dp)
      )

      Row(
        modifier = Modifier
          .height((stateValues.textFieldHeight.value / 1.2).dp)
      ) {
        actionButton(
          fillMaxHeight = true,
          text = "",
          enabledColor = stateValues.DisabledColor,
          iconPath = stateValues.drawablePathIconSubtract,
          iconContentDescription = stateValues.stringSubtract,
          onClick = decreaseQuantityAction
        )

        Spacer(modifier = Modifier.width(2.dp))

        Box(
          modifier = Modifier
            .height(stateValues.textFieldHeight)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius)),
          contentAlignment = Alignment.Center
        ) {
          Text(
            modifier = Modifier
              .padding(horizontal = stateValues.textFieldIconPadding),
            text = "${goodsItemInCart.quantity.total.run { if (goodsItemInCart.quantity.roundTotal) toInt() else this }} ${goodsItemInCart.quantity.immutableUnitName.extractLocalizedString(stateValues.appLanguage)}",
            fontWeight = FontWeight.Bold,
            color = stateValues.TextColor
          )
        }

        Spacer(modifier = Modifier.width(2.dp))

        // TODO: Add red and green quantity dependent coloring of buttons

        actionButton(
          fillMaxHeight = true,
          text = "",
          enabledColor = stateValues.DisabledColor,
          iconPath = stateValues.drawablePathIconAdd,
          iconContentDescription = stateValues.stringAdd,
          onClick = increaseQuantityAction
        )
      }
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
          iconPath = stateValues.drawablePathIconCancel,
          iconContentDescription = stateValues.stringDelete,
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
