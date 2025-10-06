package kz.aita.core

import io.ktor.client.*
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import kz.aita.KeyValueDatabase
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.StockRepository
import kz.aita.model.repository.UserRepository
import kz.aita.model.repository.impl.ConfigurationRepositoryImpl
import kz.aita.model.repository.impl.StockRepositoryImpl
import kz.aita.model.repository.impl.UserRepositoryImpl
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService

val cacheSize = 4000L * 1024 * 1024
val cacheMaxAgeSec = 30 * 24 * 3600

val tokenRefreshMutex = Mutex()

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

    expectSuccess = false

    install(HttpCache)

//    install(Auth) {
//      bearer {
//        sendWithoutRequest {
//          it.url.host.equals(configurationRepository.globalAppConfigurationState.payloadValue.serverUrl, true)
//              && !it.url.encodedPath.startsWith("auth")
//        }
//
//        loadTokens {
//
//        }
//
//        refreshTokens {
//
//        }
//      }
//    }
  }
}

val genericRemoteService: GenericRemoteService by lazy {
  GenericRemoteService(httpClient)
}

val genericLocalService: GenericLocalService by lazy {
  GenericLocalService(KeyValueDatabase(sqlDelightDriver!!))
}

val userRepository: UserRepository by lazy {
  UserRepositoryImpl(genericRemoteService, configurationRepository)
}

val configurationRepository: ConfigurationRepository by lazy {
  ConfigurationRepositoryImpl(genericRemoteService)
}

val stockRepository: StockRepository by lazy {
  StockRepositoryImpl(genericRemoteService, genericLocalService)
}
