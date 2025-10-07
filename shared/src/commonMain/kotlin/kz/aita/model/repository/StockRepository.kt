package kz.aita.model.repository

import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.model.wrapper.DataStateFlow

interface StockRepository {

    val stockState: DataStateFlow<List<GoodsItemDataModel>>

    fun getStock()

    fun addGoodsItem(goodsItem: GoodsItemDataModel)

    fun deleteGoodsItem(id: String)
}