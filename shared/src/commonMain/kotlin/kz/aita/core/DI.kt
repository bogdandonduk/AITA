package kz.aita.core

import io.ktor.client.*
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kz.aita.KeyValueDatabase
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.GoodsCategoryRepository
import kz.aita.model.repository.StockRepository
import kz.aita.model.repository.StoreRepository
import kz.aita.model.repository.SupplierRepository
import kz.aita.model.repository.UserRepository
import kz.aita.model.repository.impl.ConfigurationRepositoryImpl
import kz.aita.model.repository.impl.GoodsCategoryRepositoryImpl
import kz.aita.model.repository.impl.StockRepositoryImpl
import kz.aita.model.repository.impl.StoreRepositoryImpl
import kz.aita.model.repository.impl.SupplierRepositoryImpl
import kz.aita.model.repository.impl.UserRepositoryImpl
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

              val newPair = runCatching {
                GenericRemoteService(httpClient)
                  .request<TokenPair, String>(
                    HttpMethod.Post,
                    endpointUrl = configurationRepository.globalAppConfigurationState.payloadValue.refreshPath,
                    body = current.refreshToken,
                    onFailure = {
                      it.printStackTrace()
                    }
                  )
              }.getOrNull()

              httpClient.close()

              if (newPair != null) {
                tokenStore?.set(newPair)
                BearerTokens(newPair.accessToken, newPair.refreshToken)
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
  UserRepositoryImpl(genericRemoteService, configurationRepository, tokenStore, userAccountStore)
}

val configurationRepository: ConfigurationRepository by lazy {
  ConfigurationRepositoryImpl(genericRemoteService)
}

val stockRepository: StockRepository by lazy {
  StockRepositoryImpl(genericRemoteService, configurationRepository)
}

val supplierRepository: SupplierRepository by lazy {
  SupplierRepositoryImpl(genericRemoteService, configurationRepository)
}

val storeRepository: StoreRepository by lazy {
  StoreRepositoryImpl(genericRemoteService, configurationRepository)
}

val goodsCategoryRepository: GoodsCategoryRepository by lazy {
  GoodsCategoryRepositoryImpl(genericRemoteService, genericLocalService)
}


