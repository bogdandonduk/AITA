package kz.aita.model.repository.impl

import kz.aita.model.dataModel.GoodsItemDataModel
import kz.aita.model.repository.Repository
import kz.aita.model.repository.StockRepository
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.MutableDataStateFlow

class StockRepositoryImpl(
    private val genericRemoteService: GenericRemoteService,
    private val genericLocalService: GenericLocalService
) : Repository(), StockRepository {

    private val _stockState = MutableDataStateFlow<List<GoodsItemDataModel>>(this)
    override val stockState = _stockState.asDataStateFlow()

    override suspend fun getStock() {
        TODO("Not yet implemented")
    }

    override suspend fun addGoodsItem() {
        TODO("Not yet implemented")
    }

    override suspend fun deleteGoodsItem() {
        TODO("Not yet implemented")
    }
}
