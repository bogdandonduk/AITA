package kz.aita.model.repository

import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.wrapper.DataStateFlow

interface SupplierRepository {

  val suppliersState: DataStateFlow<List<UserAccountDataModel>>

  fun getSuppliers()
}
