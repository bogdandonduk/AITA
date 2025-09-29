package kz.aita.core

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.UserRepository
import kz.aita.model.repository.impl.ConfigurationRepositoryImpl
import kz.aita.model.repository.impl.UserRepositoryImpl
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

val userRepository: UserRepository by lazy {
  UserRepositoryImpl(genericRemoteService)
}

val configurationRepository: ConfigurationRepository by lazy {
  ConfigurationRepositoryImpl(genericRemoteService)
}