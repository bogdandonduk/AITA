package kz.aita.payments.management

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Transport-neutral payment-management API. The existing authenticated AITA network layer owns
 * bearer tokens, endpoint selection, retries, and offline policy; this client never stores tokens.
 */
class PaymentManagementClient(
    private val transport: PaymentManagementTransport,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    suspend fun integrations(storeId: String): PaymentIntegrationListDto =
        decode(
            transport.execute(
                PaymentManagementHttpRequest(
                    method = PaymentManagementHttpMethod.GET,
                    path = "/payments/integrations",
                    query = mapOf("storeId" to storeId.requireStoreId()),
                )
            )
        )

    suspend fun saveIntegration(
        provider: PaymentManagedProvider,
        request: PaymentIntegrationSecretPatchRequest,
    ): PaymentIntegrationMutationDto = decode(
        transport.execute(
            PaymentManagementHttpRequest(
                method = PaymentManagementHttpMethod.PUT,
                path = "/payments/integrations/${provider.name.lowercase()}",
                body = json.encodeToString(request),
                containsWriteOnlySecrets = request.secrets.isNotEmpty(),
                idempotencyKey = "payment-profile-${request.storeId}-${provider.name}-${request.environment.name}-${request.expectedRevision ?: 0}",
            )
        )
    )

    suspend fun deleteIntegration(
        provider: PaymentManagedProvider,
        request: PaymentIntegrationDeleteRequest,
    ): PaymentIntegrationMutationDto = decode(
        transport.execute(
            PaymentManagementHttpRequest(
                method = PaymentManagementHttpMethod.DELETE,
                path = "/payments/integrations/${provider.name.lowercase()}",
                body = json.encodeToString(request),
                idempotencyKey = "payment-profile-delete-${request.storeId}-${provider.name}-${request.environment.name}-${request.expectedRevision ?: 0}",
            )
        )
    )

    suspend fun testIntegration(
        provider: PaymentManagedProvider,
        request: PaymentIntegrationTestRequest,
    ): PaymentIntegrationMutationDto = decode(
        transport.execute(
            PaymentManagementHttpRequest(
                method = PaymentManagementHttpMethod.POST,
                path = "/payments/integrations/${provider.name.lowercase()}/test",
                body = json.encodeToString(request),
            )
        )
    )

    suspend fun balance(storeId: String): PaymentBalanceDto = decode(
        transport.execute(
            PaymentManagementHttpRequest(
                method = PaymentManagementHttpMethod.GET,
                path = "/payments/balance",
                query = mapOf("storeId" to storeId.requireStoreId()),
            )
        )
    )

    suspend fun topUps(storeId: String, limit: Int = 30): PaymentTopUpListDto = decode(
        transport.execute(
            PaymentManagementHttpRequest(
                method = PaymentManagementHttpMethod.GET,
                path = "/payments/topups",
                query = mapOf(
                    "storeId" to storeId.requireStoreId(),
                    "limit" to limit.coerceIn(1, 100).toString(),
                ),
            )
        )
    )

    private inline fun <reified T> decode(response: PaymentManagementHttpResponse): T {
        if (response.statusCode !in 200..299) {
            val safeError = runCatching { json.decodeFromString<PaymentApiErrorDto>(response.body) }.getOrNull()
            throw PaymentManagementApiException(
                statusCode = response.statusCode,
                code = safeError?.code ?: "PAYMENT_REQUEST_FAILED",
                safeMessage = safeError?.message ?: "Payment request failed",
                retryable = safeError?.retryable == true,
                currentRevision = safeError?.currentRevision,
            )
        }
        return json.decodeFromString(response.body)
    }
}

enum class PaymentManagementHttpMethod { GET, POST, PUT, DELETE }

data class PaymentManagementHttpRequest(
    val method: PaymentManagementHttpMethod,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val body: String? = null,
    /** The transport must never log or persist this request body when true. */
    val containsWriteOnlySecrets: Boolean = false,
    val idempotencyKey: String? = null,
)

data class PaymentManagementHttpResponse(
    val statusCode: Int,
    val body: String,
)

fun interface PaymentManagementTransport {
    suspend fun execute(request: PaymentManagementHttpRequest): PaymentManagementHttpResponse
}

class PaymentManagementApiException(
    val statusCode: Int,
    val code: String,
    val safeMessage: String,
    val retryable: Boolean,
    val currentRevision: Long?,
) : RuntimeException(safeMessage)

private fun String.requireStoreId(): String = trim().also {
    require(it.isNotEmpty() && it.length <= 128) { "A valid Store ID is required" }
}
