package kz.aita.model.repository.impl

import kotlinx.coroutines.launch
import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.QuantityDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.StockRepository
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow

class StockRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val genericLocalService: GenericLocalService,
  private val configurationRepository: ConfigurationRepository
): Repository(), StockRepository {

  private val _stockState = MutableDataStateFlow<List<GoodsItemDataModel>>(
    this
  )
  override val stockState = _stockState.asDataStateFlow()

  init {
    getStock()
  }
  override fun getStock() {
    launch {
      _stockState.emit(
        DataState.Success(
          listOf(
            GoodsItemDataModel(
              0,
              "792649190623",
              "Yoghurt кокосовый",
              quantity = configurationRepository
                .globalAppConfigurationState
                .payloadValue
                .goodsItemsQuantityUnits
                .find {
                  it.matchesName("pc.")
                }!!.copy(total = 19.0),
              categoryName = "Category",
              supplierName = "Supplier",
              salePrice = 500.0,
              supplyPrice = 300.0,
              saleCurrency = "₸",
              supplyCurrency = "₸"
            ),
            GoodsItemDataModel(
              1,
              "5411188081852",
              "Alpro молоко соевое ванильное",
              quantity = configurationRepository
                .globalAppConfigurationState
                .payloadValue
                .goodsItemsQuantityUnits
                .find {
                  it.matchesName("pc.")
                }!!.copy(total = 43.0),
              categoryName = "Category",
              supplierName = "Supplier",
              salePrice = 1800.0,
              supplyPrice = 1200.0,
              saleCurrency = "₸",
              supplyCurrency = "₸"
            )
          )
        )
      )
    }
  }

  override fun addGoodsItem(
    goodsItem: GoodsItemDataModel
  ) {
    launch {
      _stockState.emit(
        DataState.Success(
          stockState.payloadValue?.let { payload ->
            mutableListOf<GoodsItemDataModel>().apply {
              addAll(payload)
              add(goodsItem)
            }
          } ?: listOf(goodsItem)
        )
      )
    }
  }

  override fun deleteGoodsItem(id: Long) {
    launch {
      _stockState.payloadValue?.let { payload ->
        payload.indexOfFirst { it.id == id }
          .takeIf { it != -1 }
          ?.let { index ->
            _stockState.emit(
              DataState.Success(
                mutableListOf<GoodsItemDataModel>().apply {
                  addAll(payload)
                  removeAt(index)
                }.toList()
              )
            )
          }
      }
    }
  }
}
