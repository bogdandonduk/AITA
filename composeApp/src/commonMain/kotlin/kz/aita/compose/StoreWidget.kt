package kz.aita.compose

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
import kz.aita.StoreDataModel
import kz.aita.extractLocalizedString

@Composable
fun AppConfiguration.StoreWidget(
  modifier: Modifier = Modifier,
  store: StoreDataModel,
  textColor: Color = stateValues.TextColor,
  onDelete: ((StoreDataModel) -> Unit)? = null,
  onEdit: ((StoreDataModel) -> Unit)? = null,
  onSetActive: ((StoreDataModel) -> Unit)? = null,
  onSetInactive: ((StoreDataModel) -> Unit)? = null
) {
  Row(
    modifier
      .padding(bottom = 4.dp)
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(
        if (onSetActive == null) stateValues.focusedBorderWidth else stateValues.unfocusedBorderWidth,
        if (onSetActive == null) stateValues.OkayColor else stateValues.PlaceholderTextColor,
        RoundedCornerShape(
          stateValues.cornerRadius
        )
      )
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(top = 8.dp, start = (if (onSetActive == null) 8 else 16).dp, end = 16.dp, bottom = 12.dp),
    ) {
      if (onSetActive == null)
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          actionButton(
            text = "",
            modifier = Modifier.padding(8.dp),
            enabled = false,
            disabledColor = stateValues.OkayColor,
            iconPath = stateValues.drawablePathIconCheck,
            iconContentDescription = stateValues.stringSelect,
          ) {

          }

          Text(
            text = stateValues.stringActiveStore,
            fontSize = stateValues.titleTextSize,
            fontWeight = FontWeight.Bold,
            color = stateValues.OkayColor
          )
        }

      Column(
        modifier = Modifier
          .padding(start = 8.dp, end = 8.dp),
      ) {
        store.name.extractLocalizedString(stateValues.appLanguage)?.run {
          Text(
            text = this,
            fontSize = stateValues.titleTextSize,
            fontWeight = FontWeight.Bold,
            color = textColor
          )
        }

        store.alias.takeIf { it.any { item -> item.value.isNotEmpty() && item.value.isNotBlank() } }
          ?.extractLocalizedString(stateValues.appLanguage)?.let {
          Text(
            text = it,
            fontSize = stateValues.textSize,
            color = textColor
          )
        }

        store.description.takeIf { it.any { item -> item.value.isNotEmpty() && item.value.isNotBlank() } }
          ?.extractLocalizedString(stateValues.appLanguage)?.let {
          Text(
            text = it,
            fontSize = stateValues.textSize,
            color = textColor
          )
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
      } ?: onSetInactive?.let {
        actionButton(
          text = "",
          enabledColor = stateValues.ErrorColor,
          iconPath = stateValues.drawablePathIconCancel,
          iconContentDescription = stateValues.stringMakeInactive
        ) {
          onSetInactive(store)
        }
      }
    }
  }
}
