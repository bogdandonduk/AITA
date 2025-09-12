package kz.aita.model.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

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
    contentType: ContentType? = ContentType.Application.Json
  ) : Response {
    return httpClient
      .request(serverUrl + endpointUrl) {
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
      }.body<Response>()
  }
}
