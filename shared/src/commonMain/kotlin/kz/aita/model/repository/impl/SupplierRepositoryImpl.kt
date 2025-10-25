package kz.aita.model.repository.impl

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.aita.core.DataStore
import kz.aita.core.io
import kz.aita.model.dataModel.SupplierDataModel
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.Repository
import kz.aita.model.repository.SupplierRepository
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.DataState
import kz.aita.model.wrapper.MutableDataStateFlow
import kz.aita.model.wrapper.TokenPair

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
    if (!getSuppliersMutex.isLocked)
      launch(Dispatchers.io) {
        getSuppliersMutex.withLock {
          val response = genericRemoteService
            .request<List<SupplierDataModel>, Unit>(
              HttpMethod.Get,
              endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.getSuppliersPath
            )

            if (!response.negative){
              _suppliersState.emit(DataState.Success(response.payload!!, response.message))
            }
        }
      }
  }
}
