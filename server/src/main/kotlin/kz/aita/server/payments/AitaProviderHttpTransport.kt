package kz.aita.server.payments

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProviderHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, List<String>>,
)

class JdkProviderHttpTransport(
    private val profile: ProviderHttpProfile,
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(profile.connectTimeoutMillis))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) {
    suspend fun postJson(
        path: String,
        body: String,
        headers: Map<String, String>,
    ): ProviderHttpResponse = execute(
        builder = requestBuilder(path, headers)
            .POST(HttpRequest.BodyPublishers.ofString(body)),
    )

    suspend fun get(
        path: String,
        headers: Map<String, String>,
    ): ProviderHttpResponse = execute(
        builder = requestBuilder(path, headers).GET(),
    )

    suspend fun delete(
        path: String,
        headers: Map<String, String>,
    ): ProviderHttpResponse = execute(
        builder = requestBuilder(path, headers).DELETE(),
    )

    private fun requestBuilder(
        path: String,
        headers: Map<String, String>,
    ): HttpRequest.Builder {
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        val base = profile.baseUrl.trimEnd('/')
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(base + normalizedPath))
            .timeout(Duration.ofMillis(profile.requestTimeoutMillis))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "AITA-Server/PaymentIntegration")
        headers.forEach { (name, value) ->
            require(name.none { it == '\r' || it == '\n' }) { "Invalid provider header name." }
            require(value.none { it == '\r' || it == '\n' }) { "Invalid provider header value." }
            builder.header(name, value)
        }
        return builder
    }

    private suspend fun execute(
        builder: HttpRequest.Builder,
    ): ProviderHttpResponse = withContext(Dispatchers.IO) {
        try {
            val response = client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            ProviderHttpResponse(
                statusCode = response.statusCode(),
                body = response.body(),
                headers = response.headers().map(),
            )
        } catch (throwable: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ProviderTransportException(
                providerCode = "transport_interrupted",
                message = "Provider request was interrupted.",
                cause = throwable,
                retryable = true,
            )
        } catch (throwable: Exception) {
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            throw ProviderTransportException(
                providerCode = "transport_failure",
                message = throwable.message ?: "Provider request failed.",
                cause = throwable,
                retryable = true,
            )
        }
    }
}
