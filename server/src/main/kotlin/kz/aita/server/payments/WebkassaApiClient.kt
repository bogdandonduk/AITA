package kz.aita.server.payments

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kz.aita.payments.AitaFiscalReceiptCommand
import kz.aita.payments.AitaFiscalReceiptStatus
import kz.aita.payments.AitaFiscalReceiptType

data class WebkassaApiProfile(
    val provider: ProviderHttpProfile,
    val cashboxUniqueNumber: String,
    val apiToken: String,
    val registerCheckPath: String = "/api/Check",
    val checkStatusPathTemplate: String? = null,
    val saleOperationCode: Int,
    val saleReturnOperationCode: Int,
    val correctionOperationCode: Int? = null,
    val cashInOperationCode: Int? = null,
    val cashOutOperationCode: Int? = null,
    val mapping: WebkassaJsonMapping = WebkassaJsonMapping(),
) {
    init {
        require(cashboxUniqueNumber.isNotBlank()) { "Webkassa cashbox number is required." }
        require(apiToken.isNotBlank()) { "Webkassa API token is required." }
        require(registerCheckPath.startsWith('/')) { "Webkassa check path must start with '/'." }
    }
}

data class WebkassaJsonMapping(
    val successPaths: List<String> = listOf("Success", "success"),
    val externalReceiptIdPaths: List<String> = listOf(
        "Data.Id",
        "Data.CheckId",
        "data.id",
        "data.checkId",
        "id",
    ),
    val fiscalNumberPaths: List<String> = listOf(
        "Data.FiscalNumber",
        "data.fiscalNumber",
        "fiscalNumber",
    ),
    val checkNumberPaths: List<String> = listOf(
        "Data.CheckNumber",
        "data.checkNumber",
        "checkNumber",
    ),
    val fiscalSignPaths: List<String> = listOf(
        "Data.FiscalSign",
        "data.fiscalSign",
        "fiscalSign",
    ),
    val ticketUrlPaths: List<String> = listOf(
        "Data.TicketUrl",
        "Data.TicketURL",
        "data.ticketUrl",
        "ticketUrl",
    ),
    val errorCodePaths: List<String> = listOf(
        "Errors.0.Code",
        "errors.0.code",
        "error.code",
        "code",
    ),
    val errorMessagePaths: List<String> = listOf(
        "Errors.0.Text",
        "Errors.0.Message",
        "errors.0.message",
        "error.message",
        "message",
    ),
)

@Serializable
private data class WebkassaPositionWire(
    val Count: Double,
    val Price: Double,
    val TaxPercent: Double? = null,
    val TaxType: Int? = null,
    val PositionName: String,
    val UnitCode: Int? = null,
    val Barcode: String? = null,
)

@Serializable
private data class WebkassaPaymentWire(
    val Sum: Double,
    val PaymentType: Int,
)

@Serializable
private data class WebkassaCheckWireRequest(
    val Token: String,
    val CashboxUniqueNumber: String,
    val OperationType: Int,
    val Positions: List<WebkassaPositionWire>,
    val Payments: List<WebkassaPaymentWire>,
    val TotalPrice: Double,
    val ExternalCheckNumber: String,
    val CustomerEmail: String? = null,
    val CustomerPhone: String? = null,
)

class ConfigurableWebkassaFiscalGateway(
    private val profile: WebkassaApiProfile,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
    private val transport: JdkProviderHttpTransport = JdkProviderHttpTransport(profile.provider),
) : WebkassaFiscalGateway {
    override suspend fun verifyConnection() {
        require(profile.apiToken.isNotBlank())
        require(profile.cashboxUniqueNumber.isNotBlank())
    }

    override suspend fun registerReceipt(
        command: AitaFiscalReceiptCommand,
    ): WebkassaReceiptResult {
        kz.aita.payments.requireValidAitaFiscalReceipt(command)
        val operationCode = operationCode(command.type)
        val request = WebkassaCheckWireRequest(
            Token = profile.apiToken,
            CashboxUniqueNumber = profile.cashboxUniqueNumber,
            OperationType = operationCode,
            Positions = command.lines.map { line ->
                WebkassaPositionWire(
                    Count = line.quantityMilli.toDouble() / 1_000.0,
                    Price = line.unitPriceMinor.toDouble() / 100.0,
                    TaxPercent = line.taxPercent,
                    PositionName = line.name,
                    UnitCode = line.unitCode,
                    Barcode = line.barcode,
                )
            },
            Payments = command.payments.map { payment ->
                WebkassaPaymentWire(
                    Sum = payment.amountMinor.toDouble() / 100.0,
                    PaymentType = payment.typeCode,
                )
            },
            TotalPrice = command.totalMinor.toDouble() / 100.0,
            ExternalCheckNumber = command.idempotencyKey,
            CustomerEmail = command.customerEmail,
            CustomerPhone = command.customerPhone,
        )
        val response = transport.postJson(
            path = profile.registerCheckPath,
            body = json.encodeToString(request),
            headers = mapOf("Idempotency-Key" to command.idempotencyKey),
        )
        return parseReceiptResponse(response)
    }

    override suspend fun getReceipt(
        externalReceiptId: String,
    ): WebkassaReceiptResult {
        val template = profile.checkStatusPathTemplate
            ?: throw ProviderTransportException(
                providerCode = "status_not_supported",
                message = "Webkassa status polling is not enabled for this API profile.",
                retryable = false,
            )
        val path = template.replace("{receiptId}", externalReceiptId)
        val response = transport.get(
            path = path,
            headers = emptyMap(),
        )
        return parseReceiptResponse(response)
    }

    private fun operationCode(
        type: AitaFiscalReceiptType,
    ): Int = when (type) {
        AitaFiscalReceiptType.SALE -> profile.saleOperationCode
        AitaFiscalReceiptType.SALE_RETURN -> profile.saleReturnOperationCode
        AitaFiscalReceiptType.CORRECTION -> profile.correctionOperationCode
        AitaFiscalReceiptType.CASH_IN -> profile.cashInOperationCode
        AitaFiscalReceiptType.CASH_OUT -> profile.cashOutOperationCode
    } ?: throw ProviderTransportException(
        providerCode = "operation_not_configured",
        message = "Webkassa operation $type is not enabled for this cashbox profile.",
        retryable = false,
    )

    private fun parseReceiptResponse(
        response: ProviderHttpResponse,
    ): WebkassaReceiptResult {
        val element = try {
            json.parseToJsonElement(response.body)
        } catch (throwable: Exception) {
            throw ProviderTransportException(
                providerCode = "webkassa_invalid_json",
                message = "Webkassa returned an invalid JSON response.",
                cause = throwable,
                retryable = false,
            )
        }
        val success = firstWebkassaBoolean(element, profile.mapping.successPaths)
            ?: (response.statusCode in 200..299)
        val errorCode = firstWebkassaString(element, profile.mapping.errorCodePaths)
        val errorMessage = firstWebkassaString(element, profile.mapping.errorMessagePaths)
        if (response.statusCode !in 200..299 || !success) {
            throw ProviderTransportException(
                providerCode = errorCode ?: "webkassa_http_${response.statusCode}",
                message = errorMessage ?: "Webkassa rejected the fiscal receipt.",
                retryable = response.statusCode == 408 ||
                    response.statusCode == 429 ||
                    response.statusCode >= 500,
            )
        }
        return WebkassaReceiptResult(
            externalReceiptId = firstWebkassaString(element, profile.mapping.externalReceiptIdPaths),
            status = AitaFiscalReceiptStatus.REGISTERED,
            fiscalNumber = firstWebkassaString(element, profile.mapping.fiscalNumberPaths),
            checkNumber = firstWebkassaString(element, profile.mapping.checkNumberPaths),
            fiscalSign = firstWebkassaString(element, profile.mapping.fiscalSignPaths),
            ticketUrl = firstWebkassaString(element, profile.mapping.ticketUrlPaths),
            providerPayload = response.body,
        )
    }
}

private fun firstWebkassaString(
    root: JsonElement,
    paths: List<String>,
): String? = paths.firstNotNullOfOrNull { path ->
    webkassaJsonPath(root, path)
        ?.let { it as? JsonPrimitive }
        ?.contentOrNull
        ?.takeIf(String::isNotBlank)
}

private fun firstWebkassaBoolean(
    root: JsonElement,
    paths: List<String>,
): Boolean? = paths.firstNotNullOfOrNull { path ->
    val raw = (webkassaJsonPath(root, path) as? JsonPrimitive)?.contentOrNull
    when (raw?.trim()?.lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
}

private fun webkassaJsonPath(
    root: JsonElement,
    path: String,
): JsonElement? {
    var current: JsonElement = root
    for (segment in path.split('.').filter(String::isNotBlank)) {
        current = when (val value = current) {
            is JsonObject -> value[segment] ?: return null
            is kotlinx.serialization.json.JsonArray -> {
                val index = segment.toIntOrNull() ?: return null
                value.getOrNull(index) ?: return null
            }
            else -> return null
        }
    }
    return current
}
