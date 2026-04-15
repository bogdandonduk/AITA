package kz.aita.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kz.aita.StateHost
import kz.aita.isNumericalDoubleString

@Composable
fun AppConfiguration.StockBatchWidget(
  modifier: Modifier = Modifier,
  stateHost: StateHost? = null,
  stateKey: String? = null,
  containedSupplierIds: List<String> = emptyList()
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(stateValues.cornerRadius))
      .border(width = stateValues.unfocusedBorderWidth, color = stateValues.TextColor, shape = RoundedCornerShape(stateValues.cornerRadius))
  ) {
    val supplierContent = stateValues.suppliers?.filter { !containedSupplierIds.contains(it.id) }?.run {
      dropdownListWidget(
        modifier = Modifier
          .padding(16.dp),
        titleText = stateValues.stringSupplier,
        domains = map {
          SelectableDomain(
            id = it.id,
            displayId = it.name,
            name = it.name,
            iconPath = null,
            iconRes = null,
          )
        },
        showName = false,
        search = Triple("search", stateHost, stateKey)
      )


      Spacer(modifier = Modifier.height(4.dp))

      val priceOnFilterValue = { text: String, _: String, _: String? ->
        text.isNumericalDoubleString()
      }
      val priceOnContentValidityCheck = { text: String, id: String, _: String? ->
        text.isNotEmpty() && id.isNumericalDoubleString()
      }

      Spacer(
        modifier = Modifier
          .height(stateValues.marginTextFieldGroup)
      )
    }
  }


}