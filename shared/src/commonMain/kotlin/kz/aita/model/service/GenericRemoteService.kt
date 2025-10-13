package kz.aita.model.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kz.aita.core.configurationRepository
import kz.aita.core.extractString
import kz.aita.model.dataModel.GenericResponseDataModel
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.repository.ConfigurationRepository

class GenericRemoteService(
  val httpClient: HttpClient
) {
  suspend inline fun <reified Response, reified Body> request(
    method: HttpMethod,
    serverUrl: String = configurationRepository.globalAppConfigurationState.payloadValue.serverUrl,
    endpointUrl: String,
    query: Map<String, Any?> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
    body: Body? = null,
    contentType: ContentType? = ContentType.Application.Json
  ): GenericResponseDataModel<Response> {
      return try {
        val response = httpClient
          .request("$serverUrl/$endpointUrl") {
            this.method = method

            headers.forEach { (key, value) ->
              this.headers.append(key, value)
            }

            query.forEach { (key, value) ->
              value?.let {
                parameter(key, it)
              }
            }

            body?.let { body ->
              contentType?.let {
                this.contentType(it)
              }

              setBody(body)
            }
          }

        if (response.status == HttpStatusCode.Unauthorized) {
          println("we here broooo")
          GenericResponseDataModel(
            message = configurationRepository.stringRawAuthenticationFailedState.value,
            payload = null,
            negative = true
          )
        } else {
          response.body<GenericResponseDataModel<Response>>()
        }
      } catch (throwable: Throwable) {

        GenericResponseDataModel(
          throwable.message?.let { listOf(LocalizedStringDataModel("main", it)) },
          null,
          true
        )
      }
  }
}
