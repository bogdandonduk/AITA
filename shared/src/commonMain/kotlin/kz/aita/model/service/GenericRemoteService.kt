package kz.aita.model.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex

class GenericRemoteService(
  val httpClient: HttpClient
) {
  suspend inline fun <reified Response, reified Body> request(
    method: HttpMethod,
    serverUrl: String,
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
