package kz.aita

import kotlinx.serialization.Serializable

/**
 * Provider-neutral management models. Provider tokens are deliberately never represented in a
 * response model: after a secret has crossed the authenticated write boundary it is write-only.
 */
@Serializable
enum class PaymentIntegrationProvider {
    WEBKASSA,
    KASPI_PAY,
}

@Serializable
enum class PaymentIntegrationEnvironment {
    TEST,
    PRODUCTION,
}

@Serializable
enum class PaymentIntegrationConnectionState {
    NOT_CONFIGURED,
    CONFIGURED,
    VERIFYING,
    CONNECTED,
    DEGRADED,
    DISABLED,
}

@Serializable
data class PaymentIntegrationCredentialWriteRequest(
    val storeId: String,
    val provider: PaymentIntegrationProvider,
    val environment: PaymentIntegrationEnvironment,
    val enabled: Boolean = true,
    val publicValues: Map<String, String> = emptyMap(),
    val secretValues: Map<String, String> = emptyMap(),
    val expectedRevision: Long? = null,
)

@Serializable
data class PaymentIntegrationSummary(
    val storeId: String,
    val provider: PaymentIntegrationProvider,
    val environment: PaymentIntegrationEnvironment,
    val enabled: Boolean,
    val state: PaymentIntegrationConnectionState,
    val revision: Long,
    val publicValues: Map<String, String> = emptyMap(),
    val configuredSecretNames: Set<String> = emptySet(),
    val lastVerifiedAtEpochMs: Long? = null,
    val lastSuccessfulAtEpochMs: Long? = null,
    val safeErrorCode: String? = null,
    val safeErrorMessage: String? = null,
)

@Serializable
data class PaymentIntegrationVerificationResult(
    val provider: PaymentIntegrationProvider,
    val environment: PaymentIntegrationEnvironment,
    val successful: Boolean,
    val checkedAtEpochMs: Long,
    val safeErrorCode: String? = null,
    val safeErrorMessage: String? = null,
)

@Serializable
data class BalanceTopUpCreateRequest(
    val storeId: String,
    val amountMinor: Long,
    val currency: String = "KZT",
    val idempotencyKey: String,
    val returnUrl: String? = null,
)

@Serializable
data class BalanceTopUpInvoiceView(
    val id: String,
    val storeId: String,
    val amountMinor: Long,
    val currency: String,
    val state: String,
    val provider: PaymentIntegrationProvider,
    val paymentUrl: String? = null,
    val qrPayload: String? = null,
    val expiresAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val safeErrorCode: String? = null,
    val safeErrorMessage: String? = null,
)

@Serializable
data class BalanceAccountView(
    val storeId: String,
    val availableMinor: Long,
    val currency: String,
    val revision: Long,
    val updatedAtEpochMs: Long,
)

@Serializable
data class BalanceLedgerEntryView(
    val id: String,
    val storeId: String,
    val deltaMinor: Long,
    val balanceAfterMinor: Long,
    val currency: String,
    val kind: String,
    val referenceType: String,
    val referenceId: String,
    val createdAtEpochMs: Long,
)

fun normalizeOperationsPaymentCurrency(value: String): String = value.trim().uppercase()

fun validateOperationsBalanceTopUpRequest(request: BalanceTopUpCreateRequest): String? {
    if (request.storeId.isBlank()) return "store_id_required"
    if (request.amountMinor <= 0L) return "amount_must_be_positive"
    if (normalizeOperationsPaymentCurrency(request.currency) != "KZT") return "unsupported_currency"
    if (request.idempotencyKey.trim().length !in 16..128) return "invalid_idempotency_key"
    val url = request.returnUrl?.trim().orEmpty()
    if (url.isNotEmpty() && !(url.startsWith("https://") || url.startsWith("http://localhost"))) {
        return "invalid_return_url"
    }
    return null
}
