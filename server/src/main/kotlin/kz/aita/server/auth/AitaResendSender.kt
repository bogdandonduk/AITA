package kz.aita.server.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

internal fun buildResendAuthRequest(apiKey: String, workId: UUID, json: String): HttpRequest =
    HttpRequest.newBuilder(URI("https://api.resend.com/emails"))
        .timeout(Duration.ofSeconds(20))
        .header("Authorization", "Bearer $apiKey")
        .header("User-Agent", "AITA-Authentication/1.0")
        .header("Accept", "application/json")
        .header("Content-Type", "application/json; charset=utf-8")
        .header("Idempotency-Key", "aita-auth-$workId")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build()

/** One connection pool per application. Never call a blocking provider on the Compose/Ktor main thread. */
internal class AitaResendSender(private val apiKey: String) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    suspend fun send(workId: UUID, json: String): AuthEmailDeliveryResult = runInterruptible(Dispatchers.IO) {
        try {
            val response = client.send(buildResendAuthRequest(apiKey, workId, json), HttpResponse.BodyHandlers.ofString())
            val fields = runCatching { Json.parseToJsonElement(response.body().take(16_384)).jsonObject }.getOrNull()
            classifyResendDelivery(
                status = response.statusCode(),
                messageId = fields?.get("id")?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() },
                errorName = fields?.get("name")?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() },
                retryAfter = response.headers().firstValue("Retry-After").orElse(null)
            )
        } catch (_: IOException) {
            AuthEmailDeliveryResult(false, errorCode = "RESEND_TRANSPORT", retry = true, serviceWideFailure = true)
        }
        // InterruptedException/coroutine cancellation must escape. The durable lease is recoverable.
    }

    fun close() { client.shutdownNow() }
}
