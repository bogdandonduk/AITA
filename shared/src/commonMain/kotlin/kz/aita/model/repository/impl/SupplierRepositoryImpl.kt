package kz.aita.model.repository.impl

import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kz.aita.core.io
import kz.aita.model.dataModel.SupplierDataModel
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.SupplierRepository
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow

class SupplierRepositoryImpl(
  private val genericRemoteService: GenericRemoteService,
  private val configurationRepository: ConfigurationRepository
): Repository(), SupplierRepository {

  private val _suppliersState = MutableDataStateFlow<List<SupplierDataModel>>(this)
  override val suppliersState = _suppliersState.asDataStateFlow()

  private val getSuppliersMutex = Mutex()

  init {
    getSuppliers()
  }

  override fun getSuppliers() {
    launch(Dispatchers.io) {
      if (!getSuppliersMutex.tryLock())
        return@launch

      _suppliersState.emit(DataState.Progress())

      genericRemoteService
        .request<List<SupplierDataModel>, Unit>(
          HttpMethod.Get,
          endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.suppliersPath,
          onFailure = {
            _suppliersState.emit(DataState.Failure(it))
            getSuppliersMutex.unlock()
            it.printStackTrace()
          }
        )?.run {
          _suppliersState.emit(DataState.Success(this))
          getSuppliersMutex.unlock()
        }
    }
  }
}
