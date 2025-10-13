package kz.aita.core

import io.ktor.client.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kz.aita.KeyValueDatabase
import kz.aita.model.dataModel.NotificationType
import kz.aita.model.repository.*
import kz.aita.model.repository.impl.*
import kz.aita.model.service.GenericLocalService
import kz.aita.model.service.GenericRemoteService
import kz.aita.model.wrapper.TokenPair

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
          explicitNulls = true
          encodeDefaults = true
        }
      )
    }

    expectSuccess = false

    install(HttpCache)
//
//    install(Logging) {
//      level = LogLevel.ALL
//    }
    install(Auth) {
      bearer {
        sendWithoutRequest {
          it.url.host.equals(Url(configurationRepository.globalAppConfigurationState.payloadValue.serverUrl).host, true)
              && !it.url.encodedPath.startsWith("/auth")
        }

        loadTokens {
          withContext(Dispatchers.io) {
            tokenStore?.get()?.let { BearerTokens(it.accessToken, it.refreshToken) }
          }
        }

        refreshTokens {
          withContext(Dispatchers.io) {

            tokenRefreshMutex.withLock {
              val current = tokenStore?.get()

              current ?: return@withLock null

              val httpClient = HttpClient(getHttpClientEngine()) {
                install(ContentNegotiation) {
                  json(jsonBase)
                }
              }

              val response = GenericRemoteService(httpClient)
                .request<TokenPair, String>(
                  HttpMethod.Post,
                  endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.refreshPath,
                  body = current.refreshToken
                )

              if (response.negative) {
                notificationRepository.postNotification(configurationRepository.stringRawSessionTimeExpiredLoggingOutState.value, NotificationType.Negative)
                delay(3000)
                userRepository.forceLogOut()
              }

              httpClient.close()

              if (response.payload != null) {
                tokenStore?.set(response.payload)
                BearerTokens(response.payload.accessToken, response.payload.refreshToken)
              } else {
                tokenStore?.set(null)
                null
              }
            }
          }
        }
      }
    }
  }
}

val genericRemoteService: GenericRemoteService by lazy {
  GenericRemoteService(httpClient)
}

val genericLocalService: GenericLocalService by lazy {
  GenericLocalService(KeyValueDatabase(sqlDelightDriver!!))
}

val userRepository: UserRepository by lazy {
  UserRepositoryImpl(
    genericRemoteService,
    configurationRepository,
    notificationRepository,
    tokenStore,
    userAccountStore
  )
}

val configurationRepository: ConfigurationRepository by lazy {
  ConfigurationRepositoryImpl(genericRemoteService)
}

val stockRepository: StockRepository by lazy {
  StockRepositoryImpl(genericRemoteService, configurationRepository, tokenStore)
}

val supplierRepository: SupplierRepository by lazy {
  SupplierRepositoryImpl(genericRemoteService, configurationRepository, tokenStore)
}

val storeRepository: StoreRepository by lazy {
  StoreRepositoryImpl(genericRemoteService, genericLocalService, configurationRepository, tokenStore)
}

val goodsCategoryRepository: GoodsCategoryRepository by lazy {
  GoodsCategoryRepositoryImpl(genericRemoteService, genericLocalService)
}

val notificationRepository: NotificationRepository by lazy {
  NotificationRepositoryImpl()
}

val genericGoodsItemsRepository: GenericGoodsItemsRepository by lazy {
  GenericGoodsItemsRepositoryImpl(genericRemoteService, configurationRepository)
}




