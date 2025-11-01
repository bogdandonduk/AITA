package kz.aita.compose.screen.stock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.AppConfiguration
import kz.aita.compose.navigation.Navigation
import kz.aita.compose.navigation.NavigationScreenModel
import kz.aita.core.isNumericalDoubleString
import kz.aita.core.toLocalizedSingleMain
import kz.aita.compose.widget.DomainSelectionTextFieldGroupItemContent
import kz.aita.compose.widget.DropdownListWidgetContent
import kz.aita.compose.widget.ScreenAppBarWidget
import kz.aita.compose.widget.SelectableDomain
import kz.aita.compose.widget.actionButton
import kz.aita.compose.widget.domainSelectionTextFieldGroupWidget
import kz.aita.compose.widget.dropdownListWidget
import kz.aita.compose.widget.genericTextField
import kz.aita.core.extractLocalizedString
import kz.aita.core.genericItemsRepository
import kz.aita.core.stockRepository
import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.PriceDataModel
import kz.aita.model.wrapper.DataState

@Composable
fun AppConfiguration.StockAddEditGoodsItemScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    val state by NavigationScreenModel.Stock.AddEditGoodsItem.state.collectAsState()

    val editedGoodsItem =
      state["state_editedGoodsItemId"]?.run { stateValues.stock?.find { goodsItem -> goodsItem.id == this } }

    ScreenAppBarWidget(
      title = editedGoodsItem?.let { stateValues.stringEditGoodsItem } ?: stateValues.stringAddGoodsItem,
      iconPath = editedGoodsItem?.let { stateValues.drawablePathIconEdit } ?: stateValues.drawablePathIconAdd,
      onBack = if (!Navigation.Stock.isVeryFirstScreen(stateValues.isNarrowScreen)) {
        {
          coroutineScope.launch {
            Navigation.Stock.pop(stateValues.isNarrowScreen)
            if (editedGoodsItem != null)
              NavigationScreenModel.Stock.AddEditGoodsItem.removeState("state_editedGoodsItemId")
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
            stateKey = "barcode",
            domains = emptyList<SelectableDomain>(),
            addDomainActionButtonText = stateValues.stringAddBarcode,
            addSecondaryDomainActionButtonText = stateValues.stringAddBarcode,
            isFocusedInitial = true,
            valueInitial = editedGoodsItem?.barcode?.map {
              DomainSelectionTextFieldGroupItemContent(
                TextFieldValue(it, selection = TextRange(it.length)),
                "",
                ""
              )
            },
          )

        var name: String? by rememberSaveable {
          mutableStateOf(null)
        }

        var measurementUnitDropdownListSelectedInitial: String? by rememberSaveable {
          mutableStateOf(stateValues.globalAppConfiguration.goodsItemsQuantityUnits.takeIf { it.isNotEmpty() }
            ?.first()?.id)
        }

        LaunchedEffect(barcodeTextFieldGroupContent.data) {
          try {
            barcodeTextFieldGroupContent.data.last().value.text.takeIf { it.length == 13 }?.run {
              genericItemsRepository
                .getGenericGoodsItems(this)
                .collect {
                  if (it is DataState.Success && it.payload.isNotEmpty()) {
                    name = it.payload.first().name.extractLocalizedString(stateValues.appLanguage)
                  }
                }
            }
          } catch (thr: Throwable) {
            thr.printStackTrace()
          }
        }

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
            stateKey = "name",
          )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextFieldGroup)
        )

        var categoryDropdownListContent: DropdownListWidgetContent? = null
        categoryDropdownListContent = stateValues.goodsCategories?.run {
          val content = dropdownListWidget(
            titleText = stateValues.stringCategory,
            domains = map {
              SelectableDomain(
                id = it.id,
                displayId = it.name,
                name = it.name,
                iconPath = null
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
              iconPath = null
            )
          },
          selectedInitial = measurementUnitDropdownListSelectedInitial,
          showName = false
        )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextFieldGroup)
        )

        val priceOnFilterValue = { text: String, _: String ->
          text.isNumericalDoubleString()
        }
        val priceOnContentValidityCheck = { text: String, id: String ->
          text.isNotEmpty() && id.isNumericalDoubleString()
        }

        val supplyData = domainSelectionTextFieldGroupWidget(
          titleText = stateValues.stringSupplyData,
          placeholderText = stateValues.stringEnterSupplyPrice,
          stateHost = NavigationScreenModel.Stock.AddEditGoodsItem,
          stateKey = "supply_data",
          domains = mutableListOf<SelectableDomain>().apply {
            stateValues.suppliers?.forEach { supplier ->
              add(
                SelectableDomain(
                  id = supplier.id,
                  displayId = supplier.name,
                  name = supplier.name,
                  iconPath = null
                )
              )
            }
          },
          secondaryDomains = mutableListOf<SelectableDomain>().apply {
            stateValues.globalAppConfiguration.countries.forEach {
              it.currencies.forEach { currency ->
                add(
                  SelectableDomain(
                    id = currency.code,
                    displayId = currency.symbol.toLocalizedSingleMain(),
                    name = currency.name,
                    iconPath = it.flagDrawablePath
                  )
                )
              }
            }
          },
          keyboardType = KeyboardType.Number,
          onFilterValue = priceOnFilterValue,
          onContentValidityCheck = priceOnContentValidityCheck,
          addDomainActionButtonText = stateValues.stringAddSupplyData,
          addSecondaryDomainActionButtonText = stateValues.stringAddSupplyData
        )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextFieldGroup)
        )

        var saleDataInitial by rememberSaveable {
          mutableStateOf(emptyList<DomainSelectionTextFieldGroupItemContent>())
        }

        val saleData = domainSelectionTextFieldGroupWidget(
          titleText = stateValues.stringSaleData,
          placeholderText = stateValues.stringEnterSalePrice,
          valueInitial = saleDataInitial,
          stateHost = NavigationScreenModel.Stock.AddEditGoodsItem,
          stateKey = "sale_data",
          domains = mutableListOf<SelectableDomain>().apply {
            stateValues.suppliers?.forEach { supplier ->
              add(
                SelectableDomain(
                  id = supplier.id,
                  displayId = supplier.name,
                  name = supplier.name,
                  iconPath = null
                )
              )
            }
          },
          secondaryDomains = mutableListOf<SelectableDomain>().apply {
            stateValues.globalAppConfiguration.countries.forEach {
              it.currencies.forEach { currency ->
                add(
                  SelectableDomain(
                    id = currency.code,
                    displayId = currency.symbol.toLocalizedSingleMain(),
                    name = currency.name,
                    iconPath = it.flagDrawablePath
                  )
                )
              }
            }
          },
          keyboardType = KeyboardType.Number,
          onFilterValue = priceOnFilterValue,
          onContentValidityCheck = priceOnContentValidityCheck,
          addDomainActionButtonText = stateValues.stringAddSaleData,
          addSecondaryDomainActionButtonText = stateValues.stringAddSaleData
        )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextFieldGroup)
        )

        var returnDataInitial by rememberSaveable {
          mutableStateOf(emptyList<DomainSelectionTextFieldGroupItemContent>())
        }

        val returnData = domainSelectionTextFieldGroupWidget(
          titleText = stateValues.stringReturnData,
          placeholderText = stateValues.stringEnterReturnPrice,
          valueInitial = returnDataInitial,
          stateHost = NavigationScreenModel.Stock.AddEditGoodsItem,
          stateKey = "return_data",
          domains = mutableListOf<SelectableDomain>().apply {
            stateValues.suppliers?.forEach { supplier ->
              add(
                SelectableDomain(
                  id = supplier.id,
                  displayId = supplier.name,
                  name = supplier.name,
                  iconPath = null
                )
              )
            }
          },
          secondaryDomains = mutableListOf<SelectableDomain>().apply {
            stateValues.globalAppConfiguration.countries.forEach {
              it.currencies.forEach { currency ->
                add(
                  SelectableDomain(
                    id = currency.code,
                    displayId = currency.symbol.toLocalizedSingleMain(),
                    name = currency.name,
                    iconPath = it.flagDrawablePath
                  )
                )
              }
            }
          },
          keyboardType = KeyboardType.Number,
          onFilterValue = priceOnFilterValue,
          onContentValidityCheck = priceOnContentValidityCheck,
          addDomainActionButtonText = stateValues.stringAddReturnData,
          addSecondaryDomainActionButtonText = stateValues.stringAddReturnData,
        )

        Spacer(
          modifier = Modifier
            .height(stateValues.marginTextFieldGroup)
        )

        LaunchedEffect(supplyData) {
          saleDataInitial = supplyData.data.mapIndexed { index, item ->
            item.copy(
              value = try {
                saleData.data[index].value.takeIf { !it.text.equals(item.value) } ?: item.value
              } catch (_: Throwable) {
                item.value
              })
          }
        }

        LaunchedEffect(saleData) {
          returnDataInitial = saleData.data
        }

        goAction = {
          editedGoodsItem?.let {

          } ?: stockRepository
            .addGoodsItem(
              GoodsItemDataModel(
                id = "",
                userId = "",
                storeId = stateValues.activeStoreId!!,
                barcode = barcodeTextFieldGroupContent.data.apply { println("barcodes are $this") }.map { it.value.text },
                name = listOf(
                  LocalizedStringDataModel(language = "main", nameTextFieldContent.value.text)
                ),
                quantity = stateValues.globalAppConfiguration.goodsItemsQuantityUnits.find { it.id == measurementUnitDropdownListContent.selectedId }!!
                  .copy(total = 0.0),
                categoryIds = categoryDropdownListContent?.selectedId?.let { listOf(it) } ?: emptyList(),
                salePrices = saleData.data.map {
                  PriceDataModel(
                    price = it.value.text,
                    currency = it.selectedSecondaryDomainId,
                    supplierId = it.selectedDomainId
                  )
                },
                supplyPrices = supplyData.data.map {
                  PriceDataModel(
                    price = it.value.text,
                    currency = it.selectedSecondaryDomainId,
                    supplierId = it.selectedDomainId
                  )
                },
                returnPrices = returnData.data.map {
                  PriceDataModel(
                    price = it.value.text,
                    currency = it.selectedSecondaryDomainId,
                    supplierId = it.selectedDomainId
                  )
                },
                createdAt = 0L,
                isActive = true
              )
            ) {
              coroutineScope.launch {
                Navigation.Stock.pop(stateValues.isNarrowScreen)
                if (editedGoodsItem != null)
                  NavigationScreenModel.Stock.AddEditGoodsItem.removeState("state_editedGoodsItemId")
              }
            }
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
    ) {
      Spacer(modifier = Modifier.height(4.dp))

      actionButton(
        text = editedGoodsItem?.let { stateValues.stringEditGoodsItem } ?: stateValues.stringAddGoodsItem,
        enabled = stateValues.latestNotification == null,
        onClick = {
          println("so go action is $goAction")

          goAction?.invoke()
        }
      )

      Spacer(modifier = Modifier.height(4.dp))
    }
  }
}
