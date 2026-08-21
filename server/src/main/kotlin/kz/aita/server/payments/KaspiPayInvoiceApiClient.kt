package kz.aita.server.payments

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kz.aita.payments.AitaBalanceInvoiceStatus
import kz.aita.payments.AitaProviderEnvironment

data class KaspiPayInvoiceApiProfile(
    val provider: ProviderHttpProfile,
    val merchantId: String,
    val accessToken: String,
    val createInvoicePath: String,
    val invoiceStatusPathTemplate: String,
    val cancelInvoicePathTemplate: String? = null,
    val authHeaderName: String = "Authorization",
    val authScheme: String = "Bearer",
    val amountUnit: AmountUnit = AmountUnit.MINOR,
    val mapping: KaspiPayJsonMapping = KaspiPayJsonMapping(),
) {
    init {
        require(merchantId.isNotBlank()) { "Kaspi merchant ID is required." }
        require(accessToken.isNotBlank()) { "Kaspi access token is required." }
        require(createInvoicePath.startsWith('/')) { "Kaspi create-invoice path must start with '/'." }
        require(invoiceStatusPathTemplate.contains("{invoiceId}")) {
            "Kaspi status path must contain {invoiceId}."
        }
        require(authHeaderName.isNotBlank()) { "Kaspi auth header name is required." }
    }
}

enum class AmountUnit {
    MINOR,
    MAJOR,
}

data class KaspiPayJsonMapping(
    val externalInvoiceIdPaths: List<String> = listOf(
        "invoiceId",
        "id",
        "data.invoiceId",
        "data.id",
    ),
    val statusPaths: List<String> = listOf(
        "status",
        "data.status",
    ),
    val paymentUrlPaths: List<String> = listOf(
        "paymentUrl",
        "payUrl",
        "url",
        "data.paymentUrl",
        "data.payUrl",
        "data.url",
    ),
    val qrPayloadPaths: List<String> = listOf(
        "qrPayload",
        "qrCode",
        "data.qrPayload",
        "data.qrCode",
    ),
    val expiresAtPaths: List<String> = listOf(
        "expiresAt",
        "expiresAtEpochMillis",
        "data.expiresAt",
        "data.expiresAtEpochMillis",
    ),
    val statusAliases: Map<String, AitaBalanceInvoiceStatus> = defaultKaspiStatusAliases(),
)

@Serializable
private data class KaspiInvoiceCreateWireRequest(
    val merchantId: String,
    val amount: String,
    val currency: String,
    val invoiceId: String,
    val description: String,
    val returnUrl: String? = null,
)

class ConfigurableKaspiPayInvoiceGateway(
    private val profile: KaspiPayInvoiceApiProfile,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
    private val transport: JdkProviderHttpTransport = JdkProviderHttpTransport(profile.provider),
) : KaspiPayInvoiceGateway {
    override suspend fun createInvoice(
        command: KaspiCreateInvoiceCommand,
    ): KaspiInvoiceResult {
        val amountWire = when (profile.amountUnit) {
            AmountUnit.MINOR -> command.amount.minor.toString()
            AmountUnit.MAJOR -> formatMinorAsMajor(command.amount.minor)
        }
        val request = KaspiInvoiceCreateWireRequest(
            merchantId = command.merchantId,
            amount = amountWire,
            currency = command.amount.normalizedCurrency,
            invoiceId = command.idempotencyKey,
            description = command.description,
            returnUrl = command.returnUrl,
        )
        val response = transport.postJson(
            path = profile.createInvoicePath,
            body = json.encodeToString(request),
            headers = authHeaders(command.idempotencyKey),
        )
        return parseResponse(
            response = response,
            fallbackExternalId = command.idempotencyKey,
        )
    }

    override suspend fun getInvoice(
        externalInvoiceId: String,
    ): KaspiInvoiceResult {
        val response = transport.get(
            path = renderInvoicePath(profile.invoiceStatusPathTemplate, externalInvoiceId),
            headers = authHeaders(null),
        )
        return parseResponse(response, externalInvoiceId)
    }

    override suspend fun cancelInvoice(
        externalInvoiceId: String,
    ): KaspiInvoiceResult {
        val template = profile.cancelInvoicePathTemplate
            ?: throw ProviderTransportException(
                providerCode = "cancel_not_supported",
                message = "Kaspi invoice cancellation is not enabled for this merchant API profile.",
                retryable = false,
            )
        val response = transport.delete(
            path = renderInvoicePath(template, externalInvoiceId),
            headers = authHeaders(null),
        )
        return parseResponse(response, externalInvoiceId)
    }

    private fun authHeaders(
        idempotencyKey: String?,
    ): Map<String, String> = buildMap {
        val value = listOf(profile.authScheme.trim(), profile.accessToken.trim())
            .filter(String::isNotEmpty)
            .joinToString(" ")
        put(profile.authHeaderName.trim(), value)
        put("X-AITA-Merchant-Id", profile.merchantId.trim())
        idempotencyKey?.takeIf(String::isNotBlank)?.let {
            put("Idempotency-Key", it.trim())
        }
    }

    private fun parseResponse(
        response: ProviderHttpResponse,
        fallbackExternalId: String,
    ): KaspiInvoiceResult {
        if (response.statusCode !in 200..299) {
            throw ProviderTransportException(
                providerCode = "kaspi_http_${response.statusCode}",
                message = "Kaspi Pay returned HTTP ${response.statusCode}.",
                retryable = response.statusCode == 408 ||
                    response.statusCode == 429 ||
                    response.statusCode >= 500,
            )
        }
        val element = try {
            json.parseToJsonElement(response.body)
        } catch (throwable: Exception) {
            throw ProviderTransportException(
                providerCode = "kaspi_invalid_json",
                message = "Kaspi Pay returned an invalid JSON response.",
                cause = throwable,
                retryable = false,
            )
        }
        val externalId = firstString(element, profile.mapping.externalInvoiceIdPaths)
            ?: fallbackExternalId
        val rawStatus = firstString(element, profile.mapping.statusPaths)
            ?.trim()
            ?.uppercase()
        val status = rawStatus
            ?.let(profile.mapping.statusAliases::get)
            ?: AitaBalanceInvoiceStatus.AWAITING_PAYMENT
        return KaspiInvoiceResult(
            externalInvoiceId = externalId,
            status = status,
            paymentUrl = firstString(element, profile.mapping.paymentUrlPaths),
            qrPayload = firstString(element, profile.mapping.qrPayloadPaths),
            expiresAtEpochMillis = firstLong(element, profile.mapping.expiresAtPaths),
            providerPayload = response.body,
        )
    }
}

fun defaultKaspiStatusAliases(): Map<String, AitaBalanceInvoiceStatus> = mapOf(
    "NEW" to AitaBalanceInvoiceStatus.AWAITING_PAYMENT,
    "CREATED" to AitaBalanceInvoiceStatus.AWAITING_PAYMENT,
    "PENDING" to AitaBalanceInvoiceStatus.AWAITING_PAYMENT,
    "WAITING" to AitaBalanceInvoiceStatus.AWAITING_PAYMENT,
    "WAITING_FOR_PAYMENT" to AitaBalanceInvoiceStatus.AWAITING_PAYMENT,
    "PAID" to AitaBalanceInvoiceStatus.PAID,
    "SUCCESS" to AitaBalanceInvoiceStatus.PAID,
    "COMPLETED" to AitaBalanceInvoiceStatus.PAID,
    "EXPIRED" to AitaBalanceInvoiceStatus.EXPIRED,
    "CANCELLED" to AitaBalanceInvoiceStatus.CANCELLED,
    "CANCELED" to AitaBalanceInvoiceStatus.CANCELLED,
    "FAILED" to AitaBalanceInvoiceStatus.FAILED,
    "ERROR" to AitaBalanceInvoiceStatus.FAILED,
    "REFUNDED" to AitaBalanceInvoiceStatus.REFUNDED,
)

private fun renderInvoicePath(
    template: String,
    externalInvoiceId: String,
): String = template.replace(
    "{invoiceId}",
    URLEncoder.encode(externalInvoiceId, StandardCharsets.UTF_8),
)

private fun formatMinorAsMajor(
    minor: Long,
): String {
    val absolute = kotlin.math.abs(minor)
    val major = absolute / 100L
    val fraction = absolute % 100L
    val sign = if (minor < 0L) "-" else ""
    return "$sign$major.${fraction.toString().padStart(2, '0')}"
}

private fun firstString(
    root: JsonElement,
    paths: List<String>,
): String? = paths.firstNotNullOfOrNull { path ->
    jsonPath(root, path)
        ?.let { it as? JsonPrimitive }
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
}

private fun firstLong(
    root: JsonElement,
    paths: List<String>,
): Long? = paths.firstNotNullOfOrNull { path ->
    val primitive = jsonPath(root, path) as? JsonPrimitive
    primitive?.contentOrNull?.toLongOrNull()
}

private fun jsonPath(
    root: JsonElement,
    path: String,
): JsonElement? {
    var current: JsonElement = root
    path.split('.')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .forEach { segment ->
            current = when (val value = current) {
                is JsonObject -> value[segment] ?: return null
                is JsonArray -> {
                    val index = segment.toIntOrNull() ?: return null
                    value.getOrNull(index) ?: return null
                }
                else -> return null
            }
        }
    return current
}
