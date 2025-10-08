package kz.aita.model.repository

import kz.aita.model.dataModel.SupplierDataModel
import kz.aita.model.wrapper.DataStateFlow

interface SupplierRepository {

  val suppliersState: DataStateFlow<List<SupplierDataModel>>

  fun getSuppliers()
}
