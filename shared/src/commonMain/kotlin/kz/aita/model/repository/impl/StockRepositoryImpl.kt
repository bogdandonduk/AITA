package kz.aita.model.repository.impl

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.aita.core.io
import kz.aita.core.notificationRepository
import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.model.dataModel.NotificationType
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.StockRepository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow

class StockRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val configurationRepository: ConfigurationRepository
) : Repository(), StockRepository {

  private val _stockState = MutableDataStateFlow<List<GoodsItemDataModel>>(this)
  override val stockState = _stockState.asDataStateFlow()

  private val getStockMutex = Mutex()
  private val addGoodsItemMutex = Mutex()
  private val updateGoodsItemMutex = Mutex()
  private val deleteGoodsItemMutex = Mutex()

  override fun getStock(storeId: String) {
    if (!getStockMutex.isLocked)
      launch(Dispatchers.io) {
        getStockMutex.withLock {
          val response = genericRemoteService
            .request<List<GoodsItemDataModel>, Unit>(
              HttpMethod.Get,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getStockPath,
              headers = mapOf("store_id" to storeId)
            )

          if (!response.negative)
            _stockState.emit(DataState.Success(response.payload!!, response.message))
        }
      }
  }

  override fun updateGoodsItem(
    goodsItem: GoodsItemDataModel,
    onCompleted: ((DataState<GoodsItemDataModel>) -> Unit)?
  ) {
    if (!updateGoodsItemMutex.isLocked)
      launch(Dispatchers.io) {
        updateGoodsItemMutex.withLock {
          val response = genericRemoteService
            .request<GoodsItemDataModel, GoodsItemDataModel>(
              HttpMethod.Put,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.updateGoodsItemPath,
              body = goodsItem
            )

          if (response.negative) {
            notificationRepository.post(response.message, NotificationType.Negative)

            onCompleted?.invoke(DataState.Empty())
          } else {
            notificationRepository.post(response.message, NotificationType.Positive)

            _stockState.emit(
              DataState.Success(
                mutableListOf<GoodsItemDataModel>().also { newList ->
                  (_stockState.value.value as? DataState.Success)?.payload?.run {
                    newList.addAll(this)
                  }

                  _stockState.payloadValue?.indexOfFirst { it.id == response.payload!!.id }?.let { index ->
                    newList[index] = response.payload!!
                  } ?: newList.add(response.payload!!)
                }
              )
            )

            onCompleted?.invoke(DataState.Success(response.payload!!))
          }
        }
      }
  }

  override fun addGoodsItem(goodsItem: GoodsItemDataModel, onCompleted: ((DataState<GoodsItemDataModel>) -> Unit)?) {
    println("being added $goodsItem")

    if (!addGoodsItemMutex.isLocked)
      launch(Dispatchers.io) {
        addGoodsItemMutex.withLock {
          val response = genericRemoteService
            .request<GoodsItemDataModel, GoodsItemDataModel>(
              HttpMethod.Post,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.addGoodsItemPath,
              body = goodsItem
            )

          if (response.negative) {
            notificationRepository.post(response.message, NotificationType.Negative)

            onCompleted?.invoke(DataState.Empty())
          } else {
            notificationRepository.post(response.message, NotificationType.Positive)

            _stockState.emit(
              DataState.Success(
                mutableListOf<GoodsItemDataModel>().also { newList ->
                  (_stockState.value.value as? DataState.Success)?.payload?.run {
                    newList.addAll(this)
                  }

                  newList.add(response.payload!!)
                }
              )
            )

            onCompleted?.invoke(DataState.Success(response.payload!!))
          }
        }
      }
  }

  override fun deleteGoodsItem(id: String, storeId: String, onCompleted: (() -> Unit)?) {
    if (!deleteGoodsItemMutex.isLocked)
      launch(Dispatchers.io) {
        deleteGoodsItemMutex.withLock {
          val response = genericRemoteService
            .request<String, String>(
              HttpMethod.Delete,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.deleteGoodsItemPath,
              body = id,
              headers = mapOf("store_id" to storeId)
            )

          if (response.negative) {
            notificationRepository.post(response.message, NotificationType.Negative)

            onCompleted?.invoke()
          } else {
            notificationRepository.post(response.message, NotificationType.Positive)

            _stockState.emit(
              DataState.Success(
                mutableListOf<GoodsItemDataModel>().also { newList ->
                  (_stockState.value.value as? DataState.Success)?.payload?.forEach {
                    if (it.id != response.payload)
                      newList.add(it)
                  }
                }
              )
            )

            onCompleted?.invoke()
          }
        }
      }
  }
}
