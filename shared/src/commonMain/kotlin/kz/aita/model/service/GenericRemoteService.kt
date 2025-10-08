package kz.aita.model.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kz.aita.core.configurationRepository

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
    contentType: ContentType? = ContentType.Application.Json,
    onFailure: (Exception) -> Unit
  ): Response? {
      return httpClient
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
        }.run {
          if (status.isSuccess()) {
            if (status == HttpStatusCode.NoContent || contentLength() == 0L) {
              onFailure(Exception(bodyAsText()))
              null
            } else {
              body<Response>()
            }
          } else {
            onFailure(Exception(bodyAsText()))
            null
          }
        }
  }
}
