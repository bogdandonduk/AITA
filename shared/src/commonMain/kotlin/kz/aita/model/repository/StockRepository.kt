package kz.aita.model.repository

import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.model.dataModel.StoreDataModel
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.DataStateFlow

interface StockRepository {

    val stockState: DataStateFlow<List<GoodsItemDataModel>>

    fun getStock(storeId: String)

    fun addGoodsItem(goodsItem: GoodsItemDataModel, onCompleted: ((DataState<GoodsItemDataModel>) -> Unit)? = null)

    fun updateGoodsItem(goodsItem: GoodsItemDataModel, onCompleted: ((DataState<GoodsItemDataModel>) -> Unit)? = null)

    fun deleteGoodsItem(id: String, storeId: String, onCompleted: (() -> Unit)? = null)
}