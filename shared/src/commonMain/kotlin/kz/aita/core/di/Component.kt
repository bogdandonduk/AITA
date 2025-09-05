package kz.aita.core.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kz.aita.core.remote.getHttpClientEngine
import kz.aita.model.repository.ConfigurationRepository
import kz.aita.model.repository.UserRepository
import kz.aita.model.repository.impl.ConfigurationRepositoryImpl
import kz.aita.model.repository.impl.UserRepositoryImpl
import kz.aita.model.service.GenericRemoteService

val httpClient by lazy {
  HttpClient(getHttpClientEngine()) {
    install(ContentNegotiation) {
      json(Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
      })
    }
  }
}

val genericRemoteService: GenericRemoteService by lazy {
  GenericRemoteService(httpClient)
}

val userRepository: UserRepository by lazy {
  UserRepositoryImpl(genericRemoteService)
}

val configurationRepository: ConfigurationRepository by lazy {
  ConfigurationRepositoryImpl(genericRemoteService)
}