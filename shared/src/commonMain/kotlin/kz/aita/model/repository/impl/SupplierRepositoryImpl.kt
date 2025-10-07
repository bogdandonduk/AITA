package kz.aita.model.repository.impl

import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.repository.Repository
import kz.aita.model.repository.SupplierRepository
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.MutableDataStateFlow

class SupplierRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val genericLocalService: GenericLocalService
): Repository(), SupplierRepository {

  private val _storeSuppliersState = MutableDataStateFlow<List<UserAccountDataModel>>(this)
  override val suppliersState = _storeSuppliersState.asDataStateFlow()

  init {
    getSuppliers()
  }

  override fun getSuppliers() {

  }
}
