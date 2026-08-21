package kz.aita.payments.management

import kotlinx.serialization.Serializable

@Serializable
enum class PaymentManagedProvider {
    WEBKASSA,
    KASPI_PAY,
}

@Serializable
enum class PaymentManagedEnvironment {
    TEST,
    PRODUCTION,
}

@Serializable
enum class PaymentVerificationState {
    NOT_CONFIGURED,
    CONFIGURED_UNVERIFIED,
    VERIFIED,
    FAILED,
    DISABLED,
}

@Serializable
data class PaymentCapabilityDto(
    val provider: PaymentManagedProvider,
    val environment: PaymentManagedEnvironment,
    val credentialManagementEnabled: Boolean,
    val remoteVerificationEnabled: Boolean,
    val invoiceCreationEnabled: Boolean,
    val reasonCode: String? = null,
    val message: String? = null,
)

@Serializable
data class PaymentIntegrationSummaryDto(
    val storeId: String,
    val provider: PaymentManagedProvider,
    val environment: PaymentManagedEnvironment,
    val displayName: String? = null,
    val enabled: Boolean = false,
    val revision: Long = 0,
    val verificationState: PaymentVerificationState = PaymentVerificationState.NOT_CONFIGURED,
    val configuredSecretKeys: List<String> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val lastVerifiedAtEpochMillis: Long? = null,
    val lastErrorCode: String? = null,
    val updatedAtEpochMillis: Long? = null,
)

@Serializable
data class PaymentIntegrationListDto(
    val integrations: List<PaymentIntegrationSummaryDto> = emptyList(),
    val capabilities: List<PaymentCapabilityDto> = emptyList(),
)

@Serializable
data class PaymentIntegrationSecretPatchRequest(
    val storeId: String,
    val environment: PaymentManagedEnvironment = PaymentManagedEnvironment.PRODUCTION,
    val expectedRevision: Long? = null,
    val displayName: String? = null,
    val enabled: Boolean = true,
    val settings: Map<String, String> = emptyMap(),
    val secrets: Map<String, String> = emptyMap(),
    val clearSecretKeys: Set<String> = emptySet(),
)

@Serializable
data class PaymentIntegrationDeleteRequest(
    val storeId: String,
    val environment: PaymentManagedEnvironment = PaymentManagedEnvironment.PRODUCTION,
    val expectedRevision: Long? = null,
)

@Serializable
data class PaymentIntegrationTestRequest(
    val storeId: String,
    val environment: PaymentManagedEnvironment = PaymentManagedEnvironment.PRODUCTION,
)

@Serializable
data class PaymentIntegrationMutationDto(
    val integration: PaymentIntegrationSummaryDto? = null,
    val changed: Boolean = false,
    val verificationAttempted: Boolean = false,
    val message: String? = null,
)

@Serializable
data class PaymentBalanceDto(
    val storeId: String,
    val currency: String = "KZT",
    val balanceMinor: Long = 0,
    val revision: Long = 0,
    val updatedAtEpochMillis: Long? = null,
)

@Serializable
data class PaymentTopUpInvoiceDto(
    val id: String,
    val storeId: String,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val paymentUrl: String? = null,
    val qrPayload: String? = null,
    val expiresAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long? = null,
)

@Serializable
data class PaymentTopUpListDto(
    val balance: PaymentBalanceDto,
    val invoices: List<PaymentTopUpInvoiceDto> = emptyList(),
    val kaspiInvoiceCapability: PaymentCapabilityDto,
)

@Serializable
data class PaymentCreateTopUpRequest(
    val storeId: String,
    val amountMinor: Long,
    val currency: String = "KZT",
    val idempotencyKey: String,
)

@Serializable
data class PaymentApiErrorDto(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val currentRevision: Long? = null,
)

private val paymentKeyRegex = Regex("^[A-Za-z][A-Za-z0-9_.-]{0,63}$")
private val paymentIdempotencyRegex = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$")

fun PaymentIntegrationSecretPatchRequest.validationError(): String? {
    if (storeId.isBlank() || storeId.length > 128) return "INVALID_STORE_ID"
    if (displayName != null && displayName.trim().length > 160) return "DISPLAY_NAME_TOO_LONG"
    if (settings.size > 32 || secrets.size > 16 || clearSecretKeys.size > 16) return "TOO_MANY_FIELDS"
    if ((settings.keys + secrets.keys + clearSecretKeys).any { !paymentKeyRegex.matches(it) }) return "INVALID_FIELD_NAME"
    if (settings.values.any { it.length > 2_048 }) return "SETTING_TOO_LONG"
    if (secrets.values.any { it.isBlank() || it.length > 16_384 }) return "INVALID_SECRET_VALUE"
    if (secrets.keys.any { it in clearSecretKeys }) return "SECRET_SET_AND_CLEARED"
    if (expectedRevision != null && expectedRevision < 0) return "INVALID_REVISION"
    return null
}

fun PaymentCreateTopUpRequest.validationError(): String? {
    if (storeId.isBlank() || storeId.length > 128) return "INVALID_STORE_ID"
    if (currency.uppercase() != "KZT") return "UNSUPPORTED_CURRENCY"
    if (amountMinor <= 0 || amountMinor > 1_000_000_000_00L) return "INVALID_AMOUNT"
    if (!paymentIdempotencyRegex.matches(idempotencyKey)) return "INVALID_IDEMPOTENCY_KEY"
    return null
}
