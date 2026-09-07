package kz.aita.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kz.aita.payments.management.*
import kz.aita.server.payments.*
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

private const val PAYMENT_MANAGEMENT_ROUTE_MARKER = "aita-payment-management-v1"
private const val PAYMENT_MAX_CONTENT_LENGTH = 65_536L

private data class PaymentRouteResponse(
    val body: Any,
    val status: HttpStatusCode = HttpStatusCode.OK,
)

private object PaymentRouteRateLimiter {
    private data class Window(var startedAt: Long, var count: Int)
    private val windows = ConcurrentHashMap<String, Window>()

    fun allow(key: String, max: Int, windowMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        val window = windows.compute(key) { _, old ->
            if (old == null || now - old.startedAt >= windowMillis) Window(now, 1)
            else old.apply { count += 1 }
        } ?: return false
        if (windows.size > 10_000) windows.entries.removeIf { now - it.value.startedAt > 3_600_000L }
        return window.count <= max
    }
}

fun Route.installAitaPaymentManagementRoutes() {
    if (!paymentFeatureEnabled()) return
    val repository by lazy { PaymentIntegrationManagementRepository(PaymentManagedSecretCipher.fromEnvironment()) }

    authenticate("auth-jwt") {
        get("/payments/capabilities") {
            withPaymentAccess(call, mutation = false) { _, _, _ ->
                PaymentRouteResponse(capabilities())
            }
        }

        get("/payments/integrations") {
            withPaymentAccess(call, mutation = false) { connection, _, storeId ->
                PaymentRouteResponse(
                    PaymentIntegrationListDto(
                        integrations = repository.list(connection, storeId),
                        capabilities = capabilities(),
                    )
                )
            }
        }

        put("/payments/integrations/{provider}") {
            if (rejectLargeBody(call)) return@put
            val request = runCatching { call.receive<PaymentIntegrationSecretPatchRequest>() }.getOrElse {
                return@put call.respond(HttpStatusCode.BadRequest, PaymentApiErrorDto("INVALID_BODY", "Invalid integration request"))
            }
            request.validationError()?.let { code ->
                return@put call.respond(HttpStatusCode.BadRequest, PaymentApiErrorDto(code, "Integration settings are invalid"))
            }
            val provider = parseProvider(call.parameters["provider"])
                ?: return@put call.respond(HttpStatusCode.BadRequest, PaymentApiErrorDto("INVALID_PROVIDER", "Unsupported payment provider"))

            withPaymentAccess(call, mutation = true, requestedStoreId = request.storeId) { connection, actor, storeId ->
                val allowed = allowedCredentialKeys(provider)
                if (allowed.isEmpty() && (request.secrets.isNotEmpty() || request.clearSecretKeys.isNotEmpty())) {
                    PaymentRouteResponse(
                        PaymentApiErrorDto(
                            "PROVIDER_CONTRACT_NOT_CONFIGURED",
                            "Credential fields are disabled until the provider contract is configured",
                        ),
                        HttpStatusCode.Conflict,
                    )
                } else {
                    val saved = repository.upsert(connection, actor, provider, request.copy(storeId = storeId), allowed)
                    PaymentRouteResponse(PaymentIntegrationMutationDto(saved, changed = true, message = "Integration settings saved"))
                }
            }
        }

        delete("/payments/integrations/{provider}") {
            if (rejectLargeBody(call)) return@delete
            val request = runCatching { call.receive<PaymentIntegrationDeleteRequest>() }.getOrElse {
                return@delete call.respond(HttpStatusCode.BadRequest, PaymentApiErrorDto("INVALID_BODY", "Invalid integration deletion request"))
            }
            val provider = parseProvider(call.parameters["provider"])
                ?: return@delete call.respond(HttpStatusCode.BadRequest, PaymentApiErrorDto("INVALID_PROVIDER", "Unsupported payment provider"))

            withPaymentAccess(call, mutation = true, requestedStoreId = request.storeId) { connection, actor, storeId ->
                val changed = repository.delete(connection, actor, storeId, provider, request.environment, request.expectedRevision)
                PaymentRouteResponse(
                    PaymentIntegrationMutationDto(
                        changed = changed,
                        message = if (changed) "Integration removed" else "Integration was already absent",
                    )
                )
            }
        }

        post("/payments/integrations/{provider}/test") {
            if (rejectLargeBody(call)) return@post
            val request = runCatching { call.receive<PaymentIntegrationTestRequest>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, PaymentApiErrorDto("INVALID_BODY", "Invalid integration test request"))
            }
            val provider = parseProvider(call.parameters["provider"])
                ?: return@post call.respond(HttpStatusCode.BadRequest, PaymentApiErrorDto("INVALID_PROVIDER", "Unsupported payment provider"))

            withPaymentAccess(call, mutation = true, requestedStoreId = request.storeId) { connection, actor, storeId ->
                val current = repository.find(connection, storeId, provider, request.environment)
                if (current == null) {
                    PaymentRouteResponse(PaymentApiErrorDto("NOT_CONFIGURED", "Integration is not configured"), HttpStatusCode.NotFound)
                } else {
                    val missing = requiredCredentialKeys(provider) - current.configuredSecretKeys.toSet() - current.settings.keys
                    val state = when {
                        !current.enabled -> PaymentVerificationState.DISABLED
                        missing.isNotEmpty() -> PaymentVerificationState.FAILED
                        else -> PaymentVerificationState.CONFIGURED_UNVERIFIED
                    }
                    val errorCode = when {
                        missing.isNotEmpty() -> "MISSING_REQUIRED_FIELDS"
                        provider == PaymentManagedProvider.KASPI_PAY -> "KASPI_CONTRACT_REQUIRED"
                        else -> "REMOTE_VERIFICATION_DISABLED"
                    }
                    val updated = repository.markVerification(connection, actor, storeId, provider, request.environment, state, errorCode)
                    PaymentRouteResponse(
                        PaymentIntegrationMutationDto(
                            integration = updated,
                            changed = true,
                            verificationAttempted = false,
                            message = if (missing.isNotEmpty())
                                "Required fields are missing"
                            else
                                "Configuration is stored; remote verification remains disabled until the official provider contract is configured",
                        )
                    )
                }
            }
        }

        get("/payments/balance") {
            withPaymentAccess(call, mutation = false) { connection, _, storeId ->
                PaymentRouteResponse(PaymentFinancialReadRepository.balance(connection, storeId))
            }
        }

        get("/payments/topups") {
            withPaymentAccess(call, mutation = false) { connection, _, storeId ->
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
                PaymentRouteResponse(
                    PaymentTopUpListDto(
                        balance = PaymentFinancialReadRepository.balance(connection, storeId),
                        invoices = PaymentFinancialReadRepository.invoices(connection, storeId, limit),
                        kaspiInvoiceCapability = capability(PaymentManagedProvider.KASPI_PAY, PaymentManagedEnvironment.PRODUCTION),
                    )
                )
            }
        }

        post("/payments/topups") {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                PaymentApiErrorDto(
                    code = "KASPI_INVOICE_CONTRACT_NOT_CONFIGURED",
                    message = "Kaspi invoice creation is intentionally disabled until the official merchant API contract is configured",
                    retryable = false,
                )
            )
        }
    }
}

private suspend fun withPaymentAccess(
    call: ApplicationCall,
    mutation: Boolean,
    requestedStoreId: String? = null,
    block: (Connection, String, String) -> PaymentRouteResponse,
) {
    val principal = call.principal<JWTPrincipal>()
        ?: return call.respond(HttpStatusCode.Unauthorized, PaymentApiErrorDto("AUTH_REQUIRED", "Authentication is required"))
    val actor = PaymentStoreAccess.subject(principal)
        ?: return call.respond(HttpStatusCode.Unauthorized, PaymentApiErrorDto("INVALID_SUBJECT", "Authenticated subject is missing"))
    val storeId = (requestedStoreId ?: call.request.queryParameters["storeId"]).orEmpty().trim()
    if (storeId.isEmpty() || storeId.length > 128) {
        return call.respond(HttpStatusCode.BadRequest, PaymentApiErrorDto("INVALID_STORE_ID", "A valid Store ID is required"))
    }
    val rateClass = if (mutation) "write" else "read"
    if (!PaymentRouteRateLimiter.allow("$actor|$rateClass", if (mutation) 30 else 180, 60_000L)) {
        return call.respond(HttpStatusCode.TooManyRequests, PaymentApiErrorDto("RATE_LIMITED", "Too many payment-management requests", retryable = true))
    }

    val connection = runCatching { PaymentManagementDatabase.connection() }.getOrElse {
        return call.respond(HttpStatusCode.ServiceUnavailable, PaymentApiErrorDto("PAYMENT_DATABASE_UNAVAILABLE", "Payment management is temporarily unavailable", retryable = true))
    }

    val response = connection.use { db ->
        db.autoCommit = false
        try {
            if (!PaymentStoreAccess.canAccess(principal, storeId, db)) {
                db.rollback()
                return@use PaymentRouteResponse(PaymentApiErrorDto("STORE_ACCESS_DENIED", "You do not have access to this Store"), HttpStatusCode.Forbidden)
            }
            val result = block(db, actor, storeId)
            if (result.status.value in 200..399) db.commit() else db.rollback()
            result
        } catch (cancelled: CancellationException) {
            db.rollback()
            throw cancelled
        } catch (conflict: PaymentRevisionConflict) {
            db.rollback()
            PaymentRouteResponse(
                PaymentApiErrorDto("REVISION_CONFLICT", "Integration settings changed on another device", currentRevision = conflict.currentRevision),
                HttpStatusCode.Conflict,
            )
        } catch (invalid: IllegalArgumentException) {
            db.rollback()
            PaymentRouteResponse(
                PaymentApiErrorDto("INVALID_INTEGRATION_SETTINGS", invalid.message?.take(240) ?: "Integration settings are invalid"),
                HttpStatusCode.BadRequest,
            )
        } catch (_: Throwable) {
            db.rollback()
            PaymentRouteResponse(
                PaymentApiErrorDto("PAYMENT_OPERATION_FAILED", "Payment operation could not be completed", retryable = true),
                HttpStatusCode.InternalServerError,
            )
        }
    }
    call.respond(response.status, response.body)
}

private suspend fun rejectLargeBody(call: ApplicationCall): Boolean {
    val length = call.request.headers["Content-Length"]?.toLongOrNull() ?: return false
    if (length <= PAYMENT_MAX_CONTENT_LENGTH) return false
    call.respond(HttpStatusCode.PayloadTooLarge, PaymentApiErrorDto("PAYLOAD_TOO_LARGE", "Payment-management payload is too large"))
    return true
}

private fun paymentFeatureEnabled(): Boolean =
    System.getenv("AITA_PAYMENT_MANAGEMENT_ENABLED")?.trim()?.equals("false", true) != true

private fun parseProvider(raw: String?): PaymentManagedProvider? = runCatching {
    PaymentManagedProvider.valueOf(raw.orEmpty().trim().replace('-', '_').uppercase())
}.getOrNull()

private fun configuredKeys(variable: String): Set<String> =
    System.getenv(variable).orEmpty().split(',', ';').map(String::trim).filter(String::isNotEmpty).toSet()

private fun allowedCredentialKeys(provider: PaymentManagedProvider): Set<String> = when (provider) {
    PaymentManagedProvider.WEBKASSA -> configuredKeys("AITA_WEBKASSA_ALLOWED_CREDENTIAL_KEYS")
    PaymentManagedProvider.KASPI_PAY -> configuredKeys("AITA_KASPI_ALLOWED_CREDENTIAL_KEYS")
}

private fun requiredCredentialKeys(provider: PaymentManagedProvider): Set<String> = when (provider) {
    PaymentManagedProvider.WEBKASSA -> configuredKeys("AITA_WEBKASSA_REQUIRED_CREDENTIAL_KEYS")
    PaymentManagedProvider.KASPI_PAY -> configuredKeys("AITA_KASPI_REQUIRED_CREDENTIAL_KEYS")
}

private fun capabilities(): List<PaymentCapabilityDto> = PaymentManagedProvider.entries.flatMap { provider ->
    PaymentManagedEnvironment.entries.map { environment -> capability(provider, environment) }
}

private fun capability(provider: PaymentManagedProvider, environment: PaymentManagedEnvironment): PaymentCapabilityDto {
    val credentialManagementEnabled = allowedCredentialKeys(provider).isNotEmpty()
    val reasonCode = when {
        provider == PaymentManagedProvider.KASPI_PAY -> "KASPI_CONTRACT_REQUIRED"
        !credentialManagementEnabled -> "CREDENTIAL_SCHEMA_NOT_CONFIGURED"
        else -> "REMOTE_VERIFICATION_DISABLED"
    }
    return PaymentCapabilityDto(
        provider = provider,
        environment = environment,
        credentialManagementEnabled = credentialManagementEnabled,
        remoteVerificationEnabled = false,
        invoiceCreationEnabled = false,
        reasonCode = reasonCode,
        message = when (reasonCode) {
            "KASPI_CONTRACT_REQUIRED" -> "Kaspi invoice operations remain disabled until the official merchant API contract is configured"
            "CREDENTIAL_SCHEMA_NOT_CONFIGURED" -> "Allowed credential fields must be configured on the server"
            else -> "Credentials can be stored safely; remote verification is disabled"
        },
    )
}
