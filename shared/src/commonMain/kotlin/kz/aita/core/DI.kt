package kz.aita.core

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.AdminUserRepository
import kz.aita.model.repository.impl.ConfigurationRepositoryImpl
import kz.aita.model.repository.impl.AdminUserRepositoryImpl
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService

val httpClient by lazy {
  HttpClient(getHttpClientEngine()) {
    install(ContentNegotiation) {
      json(
        Json {
          prettyPrint = true
          isLenient = true
          ignoreUnknownKeys = true
        }
      )
    }

//    install(HttpCache) {
//      // Default is in-memory. For disk persistence, see platform notes below.
//    }
  }
}

val genericRemoteService: GenericRemoteService by lazy {
  GenericRemoteService(httpClient)
}

val genericLocalService: GenericLocalService by lazy {
  GenericLocalService(getKeyValueDatabase())
}

val adminUserRepository: AdminUserRepository by lazy {
  AdminUserRepositoryImpl(genericRemoteService, configurationRepository)
}

val configurationRepository: ConfigurationRepository by lazy {
  ConfigurationRepositoryImpl(genericRemoteService)
}