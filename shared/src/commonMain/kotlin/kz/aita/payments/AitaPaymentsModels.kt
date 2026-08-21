package kz.aita.payments

import kotlinx.serialization.Serializable

@Serializable
enum class AitaPaymentProvider {
    WEBKASSA,
    KASPI_PAY,
}

@Serializable
enum class AitaProviderEnvironment {
    SANDBOX,
    PRODUCTION,
}

@Serializable
enum class AitaIntegrationHealth {
    DISCONNECTED,
    CONFIGURED,
    VERIFYING,
    ACTIVE,
    DEGRADED,
    REVOKED,
}

@Serializable
enum class AitaBalanceInvoiceStatus {
    CREATING,
    AWAITING_PAYMENT,
    PAID,
    EXPIRED,
    CANCELLED,
    FAILED,
    REFUNDED,
}

@Serializable
enum class AitaBalanceEntryType {
    TOP_UP,
    CHARGE,
    REFUND,
    MANUAL_ADJUSTMENT,
    REVERSAL,
}

@Serializable
enum class AitaFiscalReceiptType {
    SALE,
    SALE_RETURN,
    CORRECTION,
    CASH_IN,
    CASH_OUT,
}

@Serializable
enum class AitaFiscalReceiptStatus {
    PENDING,
    REGISTERED,
    FAILED,
    CANCELLED,
}

@Serializable
data class AitaMoney(
    val minor: Long,
    val currency: String = "KZT",
) {
    init {
        require(currency.trim().length == 3) { "Currency must be a three-letter ISO code." }
    }

    val normalizedCurrency: String
        get() = currency.trim().uppercase()

    fun requirePositive(): AitaMoney {
        require(minor > 0L) { "Money amount must be positive." }
        return copy(currency = normalizedCurrency)
    }
}

@Serializable
data class AitaIntegrationSummary(
    val provider: AitaPaymentProvider,
    val environment: AitaProviderEnvironment,
    val health: AitaIntegrationHealth,
    val displayName: String,
    val merchantReferenceMasked: String? = null,
    val cashboxReferenceMasked: String? = null,
    val configuredAtEpochMillis: Long? = null,
    val lastVerifiedAtEpochMillis: Long? = null,
    val lastSuccessfulCallAtEpochMillis: Long? = null,
    val lastFailureAtEpochMillis: Long? = null,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
)

@Serializable
data class SaveWebkassaIntegrationRequest(
    val environment: AitaProviderEnvironment,
    val apiBaseUrl: String? = null,
    val login: String? = null,
    val password: String? = null,
    val apiToken: String? = null,
    val cashboxUniqueNumber: String,
    val saleOperationCode: Int? = null,
    val returnOperationCode: Int? = null,
)

@Serializable
data class SaveKaspiPayIntegrationRequest(
    val environment: AitaProviderEnvironment,
    val apiBaseUrl: String,
    val merchantId: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val createInvoicePath: String,
    val invoiceStatusPathTemplate: String,
    val cancelInvoicePathTemplate: String? = null,
    val authHeaderName: String = "Authorization",
    val authScheme: String = "Bearer",
)

@Serializable
data class CreateAitaBalanceTopUpRequest(
    val amount: AitaMoney,
    val idempotencyKey: String,
    val description: String? = null,
    val returnUrl: String? = null,
)

@Serializable
data class AitaBalanceInvoiceView(
    val id: String,
    val provider: AitaPaymentProvider,
    val status: AitaBalanceInvoiceStatus,
    val amount: AitaMoney,
    val externalInvoiceId: String? = null,
    val paymentUrl: String? = null,
    val qrPayload: String? = null,
    val expiresAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val paidAtEpochMillis: Long? = null,
    val failureCode: String? = null,
    val failureMessage: String? = null,
)

@Serializable
data class AitaBalanceView(
    val available: AitaMoney,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class AitaBalanceLedgerEntryView(
    val id: String,
    val type: AitaBalanceEntryType,
    val amount: AitaMoney,
    val balanceAfter: AitaMoney,
    val referenceType: String,
    val referenceId: String,
    val createdAtEpochMillis: Long,
    val description: String? = null,
)

@Serializable
data class AitaFiscalLine(
    val lineId: String,
    val name: String,
    val quantityMilli: Long,
    val unitPriceMinor: Long,
    val totalMinor: Long,
    val barcode: String? = null,
    val unitCode: Int? = null,
    val taxPercent: Double? = null,
)

@Serializable
data class AitaFiscalPayment(
    val typeCode: Int,
    val amountMinor: Long,
)

@Serializable
data class AitaFiscalReceiptCommand(
    val storeId: String,
    val transactionId: String,
    val type: AitaFiscalReceiptType,
    val idempotencyKey: String,
    val currency: String = "KZT",
    val lines: List<AitaFiscalLine>,
    val payments: List<AitaFiscalPayment>,
    val totalMinor: Long,
    val customerEmail: String? = null,
    val customerPhone: String? = null,
    val originalReceiptId: String? = null,
)

@Serializable
data class AitaFiscalReceiptView(
    val id: String,
    val transactionId: String,
    val provider: AitaPaymentProvider = AitaPaymentProvider.WEBKASSA,
    val type: AitaFiscalReceiptType,
    val status: AitaFiscalReceiptStatus,
    val externalReceiptId: String? = null,
    val fiscalNumber: String? = null,
    val checkNumber: String? = null,
    val fiscalSign: String? = null,
    val ticketUrl: String? = null,
    val registeredAtEpochMillis: Long? = null,
    val failureCode: String? = null,
    val failureMessage: String? = null,
)

@Serializable
data class AitaProviderCallbackAck(
    val accepted: Boolean,
    val duplicate: Boolean = false,
    val message: String? = null,
)

fun normalizeAitaCurrency(value: String): String {
    val normalized = value.trim().uppercase()
    require(normalized.matches(Regex("[A-Z]{3}"))) { "Currency must be a three-letter ISO code." }
    return normalized
}

fun canTransitionAitaBalanceInvoice(
    from: AitaBalanceInvoiceStatus,
    to: AitaBalanceInvoiceStatus,
): Boolean = when (from) {
    AitaBalanceInvoiceStatus.CREATING ->
        to in setOf(
            AitaBalanceInvoiceStatus.AWAITING_PAYMENT,
            AitaBalanceInvoiceStatus.PAID,
            AitaBalanceInvoiceStatus.FAILED,
            AitaBalanceInvoiceStatus.CANCELLED,
        )

    AitaBalanceInvoiceStatus.AWAITING_PAYMENT ->
        to in setOf(
            AitaBalanceInvoiceStatus.PAID,
            AitaBalanceInvoiceStatus.EXPIRED,
            AitaBalanceInvoiceStatus.CANCELLED,
            AitaBalanceInvoiceStatus.FAILED,
        )

    AitaBalanceInvoiceStatus.PAID ->
        to == AitaBalanceInvoiceStatus.REFUNDED

    AitaBalanceInvoiceStatus.FAILED,
    AitaBalanceInvoiceStatus.EXPIRED,
    AitaBalanceInvoiceStatus.CANCELLED,
    AitaBalanceInvoiceStatus.REFUNDED,
    -> false
}

fun requireValidAitaFiscalReceipt(command: AitaFiscalReceiptCommand): AitaFiscalReceiptCommand {
    require(command.idempotencyKey.isNotBlank()) { "Fiscal receipt idempotency key is required." }
    require(command.transactionId.isNotBlank()) { "Transaction ID is required." }
    require(command.lines.isNotEmpty()) { "At least one fiscal line is required." }
    require(command.totalMinor > 0L) { "Receipt total must be positive." }
    require(command.lines.all { it.quantityMilli > 0L && it.unitPriceMinor >= 0L && it.totalMinor >= 0L }) {
        "Fiscal lines contain invalid quantities or amounts."
    }
    require(command.lines.sumOf { it.totalMinor } == command.totalMinor) {
        "Fiscal line total does not match receipt total."
    }
    require(command.payments.isNotEmpty() && command.payments.all { it.amountMinor > 0L }) {
        "At least one positive fiscal payment is required."
    }
    require(command.payments.sumOf { it.amountMinor } == command.totalMinor) {
        "Fiscal payment total does not match receipt total."
    }
    normalizeAitaCurrency(command.currency)
    return command
}
