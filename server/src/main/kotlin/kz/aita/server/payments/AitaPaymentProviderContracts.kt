package kz.aita.server.payments

import kz.aita.payments.*

data class KaspiCreateInvoiceCommand(
    val merchantId: String,
    val amount: AitaMoney,
    val idempotencyKey: String,
    val description: String,
    val returnUrl: String? = null,
)

data class KaspiInvoiceResult(
    val externalInvoiceId: String,
    val status: AitaBalanceInvoiceStatus,
    val paymentUrl: String? = null,
    val qrPayload: String? = null,
    val expiresAtEpochMillis: Long? = null,
    val providerPayload: String,
)

interface KaspiPayInvoiceGateway {
    suspend fun createInvoice(command: KaspiCreateInvoiceCommand): KaspiInvoiceResult
    suspend fun getInvoice(externalInvoiceId: String): KaspiInvoiceResult
    suspend fun cancelInvoice(externalInvoiceId: String): KaspiInvoiceResult
}

data class WebkassaReceiptResult(
    val externalReceiptId: String?,
    val status: AitaFiscalReceiptStatus,
    val fiscalNumber: String? = null,
    val checkNumber: String? = null,
    val fiscalSign: String? = null,
    val ticketUrl: String? = null,
    val registeredAtEpochMillis: Long? = null,
    val providerPayload: String,
    val failureCode: String? = null,
    val failureMessage: String? = null,
)

interface WebkassaFiscalGateway {
    suspend fun verifyConnection()
    suspend fun registerReceipt(command: AitaFiscalReceiptCommand): WebkassaReceiptResult
    suspend fun getReceipt(externalReceiptId: String): WebkassaReceiptResult
}

data class ProviderHttpProfile(
    val environment: AitaProviderEnvironment,
    val baseUrl: String,
    val connectTimeoutMillis: Long = 10_000L,
    val requestTimeoutMillis: Long = 25_000L,
) {
    init {
        require(baseUrl.startsWith("https://") || environment == AitaProviderEnvironment.SANDBOX) {
            "Production payment providers must use HTTPS."
        }
    }
}

class ProviderTransportException(
    val providerCode: String,
    message: String,
    cause: Throwable? = null,
    val retryable: Boolean,
) : RuntimeException(message, cause)
