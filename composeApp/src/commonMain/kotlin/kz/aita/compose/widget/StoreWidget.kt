package kz.aita.compose.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kz.aita.AppConfiguration
import kz.aita.core.extractLocalizedString
import kz.aita.model.dataModel.StoreDataModel

@Composable
fun AppConfiguration.StoreWidget(
  modifier: Modifier = Modifier,
  store: StoreDataModel,
  isActive: Boolean = false,
  textColor: Color = stateValues.TextColor,
  onDelete: ((StoreDataModel) -> Unit)? = null,
  onEdit: ((StoreDataModel) -> Unit)? = null,
  onSetActive: ((StoreDataModel) -> Unit)? = null
) {
  Row(
    modifier
      .padding(bottom = 4.dp)
      .fillMaxHeight()
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        stateValues.unfocusedBorderWidth,
        stateValues.PlaceholderTextColor,
        RoundedCornerShape(
          stateValues.cornerRadius
        )
      )
  ) {
    if (isActive)
      Column(
        modifier = Modifier
          .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
      ) {
        Spacer(
          Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.OkayColor)
        )
      }

    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(top = 8.dp, start = (if (isActive) 8 else 16).dp, end = 16.dp, bottom = 12.dp),
    ) {
      store.name.extractLocalizedString(stateValues.appLanguage)?.run {
        Text(
          text = this,
          fontSize = stateValues.titleTextSize,
          fontWeight = FontWeight.Bold,
          color = textColor
        )
      }

      Column(
        modifier = Modifier
          .padding(top = 6.dp),
      ) {
        store.alias.takeIf { it.any  { item -> item.value.isNotEmpty() && item.value.isNotBlank() } }?.extractLocalizedString(stateValues.appLanguage)?.let {
          Text(
            text = it,
            fontSize = stateValues.textSize,
            color = textColor
          )
        }

        store.description.takeIf { it.any  { item -> item.value.isNotEmpty() && item.value.isNotBlank() } }?.extractLocalizedString(stateValues.appLanguage)?.let {
          Text(
            text = it,
            fontSize = stateValues.textSize,
            color = textColor
          )
        }
      }

      val companyFormsText = store.companyForms.takeIf { it.isNotEmpty() }?.let { companyForms ->
        StringBuilder()
          .also {
            companyForms.forEachIndexed { index, companyForm ->
              val name = companyForm.name.extractLocalizedString(stateValues.appLanguage)

              name?.run {
                if (index == companyForms.lastIndex)
                  it.append(name)
                else
                  it.append("$name, ")
              }
            }
          }
          .toString()
      }

      Text(
        text = companyFormsText.takeIf { it?.isNotEmpty() == true } ?: "No company form specified",
        fontSize = stateValues.textSize,
        fontWeight = FontWeight.Bold,
        color = textColor
      )

      Text(
        text = store.location.name,
        fontSize = stateValues.textSize,
        fontWeight = FontWeight.Bold,
        color = textColor
      )

      Spacer(
        modifier = Modifier
          .height(stateValues.marginTextField)
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
          onDelete(store)
        }
      }

      Spacer(
        modifier = Modifier
          .height(stateValues.marginTextField)
      )

      onEdit?.let {
        actionButton(
          text = "",
          iconPath = stateValues.drawablePathIconEdit,
          iconContentDescription = stateValues.drawablePathIconEdit,
        ) {
          onEdit(store)
        }
      }

      Spacer(
        modifier = Modifier
          .height(stateValues.marginTextField)
      )

      onSetActive?.let {
        actionButton(
          text = "",
          enabledColor = stateValues.OkayColor,
          iconPath = stateValues.drawablePathIconCheck,
          iconContentDescription = stateValues.stringSelect,
        ) {
          onSetActive(store)
        }
      }
    }
  }
}
