package kz.aita.model.repository

import kz.aita.model.dataModel.response.GoodsItemDataModel
import kz.aita.model.wrapper.DataStateFlow

interface StockRepository {

    val stockState: DataStateFlow<List<GoodsItemDataModel>>

    suspend fun getStock()

    suspend fun addGoodsItem()

    suspend fun deleteGoodsItem()
}