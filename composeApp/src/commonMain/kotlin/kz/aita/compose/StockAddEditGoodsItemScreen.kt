package kz.aita.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.extractLocalizedString
import kz.aita.getCurrency
import kz.aita.getFirstCurrencyByCountry


data class BatchPriceInfo(
  val supplierId: String,
  val supplyPrice: String,
  val salePrice: String,
  val returnPrice: String,
  val currency: String
)

@Composable
fun AppConfiguration.StockAddEditGoodsItemScreen() {

  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    val state by NavigationScreenModel.Stock.AddEditGoodsItem.state.collectAsState()

    val editedGoodsItem =
      state[NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID]?.run { stateValues.stock?.find { goodsItem -> goodsItem.id == this } }

    ScreenAppBarWidget(
      title = editedGoodsItem?.let { stateValues.stringEditGoodsItem } ?: stateValues.stringAddGoodsItem,
      iconPath = editedGoodsItem?.let { stateValues.drawablePathIconEdit } ?: stateValues.drawablePathIconAdd,
      onBack = if (!Navigation.Stock.isVeryFirstScreen(stateValues.isNarrowScreen)) {
        {
          coroutineScope.launch {
            Navigation.Stock.pop(stateValues.isNarrowScreen)
            if (editedGoodsItem != null)
              NavigationScreenModel.Stock.AddEditGoodsItem.removeState(NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID)
          }
        }
      } else null
    )

    var goAction: (() -> Unit)? = null

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(start = 8.dp, top = 24.dp, end = 8.dp)
    ) {
      item {
        val barcodeTextFieldGroupContent =
          domainSelectionTextFieldGroupWidget(
            titleText = stateValues.stringBarcode,
            placeholderText = stateValues.stringEnterBarcode,
            stateHost = NavigationScreenModel.Stock.AddEditGoodsItem,
            stateKey = NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_BARCODE,
            domains = emptyList(),
            addDomainActionButtonText = stateValues.stringAddBarcode,
            addSecondaryDomainActionButtonText = stateValues.stringAddBarcode,
            isFocusedInitial = true,
//            valueInitial = editedGoodsItem?.barcode?.map {
//              println("executing again $it")
//              DomainSelectionTextFieldGroupItemContent(
//                TextFieldValue(it, selection = TextRange(it.length)),
//                "",
//                "",
//                isContentValid = true
//              )
//            },
          )

        var name: String? by rememberSaveable {
          mutableStateOf(null)
        }

        var measurementUnitDropdownListSelectedInitial: String? by rememberSaveable {
          mutableStateOf(stateValues.globalAppConfiguration.goodsItemsQuantityUnits.takeIf { it.isNotEmpty() }
            ?.first()?.id)
        }

//        LaunchedEffect(barcodeTextFieldGroupContent.data) {
//          try {
//            barcodeTextFieldGroupContent.data.last().value.text.takeIf { it.length == 13 }?.run {
//              genericItemsRepository
//                .getGenericGoodsItems(this)
//                .collect {
//                  if (it is DataState.Success && it.payload.isNotEmpty()) {
//                    name = it.payload.first().name.extractLocalizedString(stateValues.appLanguage)
//                  }
//                }
//            }
//          } catch (thr: Throwable) {
//            thr.printStackTrace()
//          }
//        }

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextField)
        )

        val nameTextFieldContent =
          genericTextField(
            titleText = stateValues.stringName,
            placeholderText = stateValues.stringEnterName,
            valueInitial = name,
            stateHost = NavigationScreenModel.Stock.AddEditGoodsItem,
            stateKey = NavigationScreenModel.KEY_STATE_NAME,
          )

        var isQuickItem by rememberSaveable {
          mutableStateOf(false)
        }

        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(
            checked = isQuickItem,
            onCheckedChange = {
              isQuickItem = it
            },
            colors = CheckboxColors(
              checkedBoxColor = stateValues.AccentColor,
              checkedCheckmarkColor = stateValues.AccentTextColor,
              uncheckedBoxColor = stateValues.BackgroundColor,
              checkedBorderColor = stateValues.PlaceholderTextColor,
              uncheckedBorderColor = stateValues.PlaceholderTextColor,
              uncheckedCheckmarkColor = stateValues.PlaceholderTextColor,
              disabledBorderColor = stateValues.PlaceholderTextColor,
              disabledCheckedBoxColor = stateValues.PlaceholderTextColor,
              disabledUncheckedBoxColor = stateValues.PlaceholderTextColor,
              disabledIndeterminateBorderColor = stateValues.PlaceholderTextColor,
              disabledUncheckedBorderColor = stateValues.PlaceholderTextColor,
              disabledIndeterminateBoxColor = stateValues.PlaceholderTextColor,
            )
          )

          Spacer(Modifier.width(2.dp))

          Text(
            text = stateValues.stringQuick,
            color = stateValues.TextColor
          )
        }


        var categoryDropdownListContent: DropdownListWidgetContent? = null
        categoryDropdownListContent = stateValues.goodsCategories?.run {
          val content = dropdownListWidget(
            titleText = stateValues.stringCategory,
            domains = map {
              SelectableDomain(
                id = it.id,
                displayId = it.name,
                name = it.name,
                iconPath = null,
                iconRes = null,
              )
            },
            showName = false
          )

          Spacer(
            modifier = Modifier
              .height(stateValues.marginTextField)
          )

          content
        }

        LaunchedEffect(categoryDropdownListContent?.selectedId) {
          stateValues.goodsCategories?.find { it.id == categoryDropdownListContent?.selectedId }?.let {
            measurementUnitDropdownListSelectedInitial = it.quantityUnitId
          }
        }

        val measurementUnitDropdownListContent = dropdownListWidget(
          titleText = stateValues.stringMeasurementUnit,
          domains = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.map {
            SelectableDomain(
              id = it.id,
              displayId = it.immutableUnitName,
              name = it.immutableUnitName,
              iconPath = null,
              iconRes = null
            )
          },
          selectedInitial = measurementUnitDropdownListSelectedInitial,
          showName = false
        )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextField)
        )

        Text(
          text = "Batch data", // TODO
          fontSize = stateValues.accentTextSize,
          fontWeight = FontWeight.Bold,
          color = stateValues.TextColor,
          modifier = Modifier
            .fillMaxWidth()
        )

        Spacer(
          modifier = Modifier
            .height(4.dp)
        )

        val prices by rememberSaveable {
          mutableStateOf(
            editedGoodsItem?.let {
              mutableListOf<BatchPriceInfo>().apply {
                it.salePrices.forEach { item ->
                  if (find { item2 -> item2.supplierId == item.supplierId } != null)
                    indexOfFirst { item3 -> item3.supplierId == item.supplierId }.takeIf { v -> v != -1 }?.let { index ->
                      set(index, get(index).copy(salePrice = item.price))
                    }
                  else
                    add(
                      BatchPriceInfo(
                        supplierId = item.supplierId,
                        supplyPrice = "",
                        salePrice = item.price,
                        returnPrice = "",
                        currency = item.currency
                      )
                    )
                }

                it.supplyPrices.forEach { item ->
                  if (find { item2 -> item2.supplierId == item.supplierId } != null)
                    indexOfFirst { item3 -> item3.supplierId == item.supplierId }.takeIf { v -> v != -1 }?.let { index ->
                      set(index, get(index).copy(supplyPrice = item.price))
                    }
                  else
                    add(
                      BatchPriceInfo(
                        supplierId = item.supplierId,
                        supplyPrice = item.price,
                        salePrice = "",
                        returnPrice = "",
                        currency = item.currency
                      )
                    )
                }

                it.returnPrices.forEach { item ->
                  if (find { item2 -> item2.supplierId == item.supplierId } != null)
                    indexOfFirst { item3 -> item3.supplierId == item.supplierId }.takeIf { v -> v != -1 }?.let { index ->
                      set(index, get(index).copy(supplyPrice = item.price))
                    }
                  else
                    add(
                      BatchPriceInfo(
                        supplierId = item.supplierId,
                        supplyPrice = "",
                        salePrice = "",
                        returnPrice = item.price,
                        currency = item.currency
                      )
                    )
                }
              }
            } ?: emptyList()
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          actionButton(
            text = "",
            iconPath = stateValues.drawablePathIconAdd,
            iconRes = stateValues.drawableResIconAdd.value
          ) {

          }

          Spacer(modifier = Modifier.width(8.dp))

          LazyRow {
            items(prices) {
              StockBatchWidget(
                modifier = Modifier
                  .width(stateValues.screenWidth / 3),
                containedSupplierIds = rices.map { it.supplierId }
              )
            }
          }
        }

        goAction = {
//          editedGoodsItem?.let {
//
//          } ?: stockRepository
//            .addGoodsItem(
//              GoodsItemDataModel(
//                id = "",
//                userId = "",
//                storeId = stateValues.activeStoreId!!,
//                barcode = barcodeTextFieldGroupContent.data.map { it.value.text },
//                name = listOf(
//                  LocalizedStringDataModel(language = "main", nameTextFieldContent.value.text)
//                ),
//                measurementUnitId = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.find {
//                  it.id == measurementUnitDropdownListContent.selectedId
//                }!!.id,
//                categoryIds = categoryDropdownListContent?.selectedId?.let { listOf(it) } ?: emptyList(),
//                salePrices = saleData.data.map {
//                  PriceDataModel(
//                    price = it.value.text,
//                    currency = it.selectedSecondaryDomainId,
//                    supplierId = it.selectedDomainId
//                  )
//                },
//                supplyPrices = supplyData.data.map {
//                  PriceDataModel(
//                    price = it.value.text,
//                    currency = it.selectedSecondaryDomainId,
//                    supplierId = it.selectedDomainId
//                  )
//                },
//                returnPrices = returnData.data.map {
//                  PriceDataModel(
//                    price = it.value.text,
//                    currency = it.selectedSecondaryDomainId,
//                    supplierId = it.selectedDomainId
//                  )
//                },
//                createdAt = 0L,
//                isQuickItem = isQuickItem,
//                isActive = true
//              )
//            ) {
//              coroutineScope.launch {
//                Navigation.Stock.pop(stateValues.isNarrowScreen)
//                if (editedGoodsItem != null)
//                  NavigationScreenModel.Stock.AddEditGoodsItem.removeState(NavigationScreenModel.Stock.AddEditGoodsItem.KEY_STATE_EDITED_GOODS_ITEM_ID)
//              }
//            }
        }
      }

      item {
        Spacer(
          modifier = Modifier
            .height(stateValues.screenHeight / 4)
        )
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(4.dp)
    ) {
      Spacer(modifier = Modifier.height(4.dp))

      actionButton(
        text = editedGoodsItem?.let { stateValues.stringEditGoodsItem } ?: stateValues.stringAddGoodsItem,
        enabled = stateValues.latestNotification == null,
        onClick = {
          goAction?.invoke()
        }
      )

      Spacer(modifier = Modifier.height(4.dp))
    }
  }
}
