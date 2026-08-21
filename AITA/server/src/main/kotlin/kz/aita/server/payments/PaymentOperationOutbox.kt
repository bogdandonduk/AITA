package kz.aita.server.payments

import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class DurablePaymentOperationState {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class DurablePaymentOperation(
    val id: UUID,
    val storeId: UUID,
    val provider: String,
    val environment: String,
    val operationKind: String,
    val aggregateType: String,
    val aggregateId: String,
    val idempotencyKey: String,
    val requestPayload: String,
    val attemptCount: Int,
)

data class ProviderOperationExecutionResult(
    val successful: Boolean,
    val retryable: Boolean,
    val httpStatus: Int? = null,
    val providerReference: String? = null,
    val safeErrorCode: String? = null,
    val safeErrorMessage: String? = null,
    val safeProviderResponse: String? = null,
)

fun interface DurablePaymentOperationHandler {
    suspend fun execute(operation: DurablePaymentOperation): ProviderOperationExecutionResult
}

class PaymentOperationOutboxRepository(
    private val connectionFactory: () -> Connection,
) {
    fun enqueue(
        storeId: UUID,
        provider: String,
        environment: String,
        operationKind: String,
        aggregateType: String,
        aggregateId: String,
        idempotencyKey: String,
        requestPayload: String,
        now: Instant = Instant.now(),
    ): UUID {
        require(provider.matches(SAFE_CODE))
        require(environment.matches(SAFE_CODE))
        require(operationKind.matches(SAFE_CODE))
        require(aggregateType.matches(SAFE_CODE))
        require(aggregateId.length in 1..160)
        require(idempotencyKey.length in 16..180)
        require(requestPayload.toByteArray().size <= MAX_PAYLOAD_BYTES)
        connectionFactory().use { connection ->
            val id = UUID.randomUUID()
            connection.prepareStatement(
                """
                INSERT INTO payment_provider_operation_outbox
                    (id, store_id, provider, environment, operation_kind, aggregate_type,
                     aggregate_id, idempotency_key, request_payload, state, attempt_count,
                     next_attempt_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', 0, ?, ?, ?)
                ON CONFLICT (store_id, provider, environment, idempotency_key)
                DO UPDATE SET updated_at = EXCLUDED.updated_at
                RETURNING id
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, id)
                statement.setObject(2, storeId)
                statement.setString(3, provider)
                statement.setString(4, environment)
                statement.setString(5, operationKind)
                statement.setString(6, aggregateType)
                statement.setString(7, aggregateId)
                statement.setString(8, idempotencyKey)
                statement.setString(9, requestPayload)
                statement.setObject(10, now)
                statement.setObject(11, now)
                statement.setObject(12, now)
                statement.executeQuery().use { result ->
                    check(result.next()) { "payment_operation_enqueue_failed" }
                    return result.getObject(1, UUID::class.java)
                }
            }
        }
    }

    fun claimBatch(
        workerId: String,
        limit: Int,
        now: Instant = Instant.now(),
        staleAfter: Duration = Duration.ofMinutes(10),
    ): List<DurablePaymentOperation> {
        require(workerId.length in 1..160)
        require(limit in 1..100)
        connectionFactory().use { connection ->
            val oldAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    UPDATE payment_provider_operation_outbox
                    SET state = 'RETRY_WAIT', locked_at = NULL, locked_by = NULL,
                        next_attempt_at = ?, updated_at = ?,
                        last_safe_error_code = COALESCE(last_safe_error_code, 'stale_worker_lease')
                    WHERE state = 'PROCESSING' AND locked_at < ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, now)
                    statement.setObject(2, now)
                    statement.setObject(3, now.minus(staleAfter))
                    statement.executeUpdate()
                }
                val rows = mutableListOf<DurablePaymentOperation>()
                connection.prepareStatement(
                    """
                    SELECT id, store_id, provider, environment, operation_kind, aggregate_type,
                           aggregate_id, idempotency_key, request_payload::text, attempt_count
                    FROM payment_provider_operation_outbox
                    WHERE state IN ('PENDING', 'RETRY_WAIT') AND next_attempt_at <= ?
                    ORDER BY next_attempt_at, created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, now)
                    statement.setInt(2, limit)
                    statement.executeQuery().use { result ->
                        while (result.next()) {
                            rows += DurablePaymentOperation(
                                id = result.getObject("id", UUID::class.java),
                                storeId = result.getObject("store_id", UUID::class.java),
                                provider = result.getString("provider"),
                                environment = result.getString("environment"),
                                operationKind = result.getString("operation_kind"),
                                aggregateType = result.getString("aggregate_type"),
                                aggregateId = result.getString("aggregate_id"),
                                idempotencyKey = result.getString("idempotency_key"),
                                requestPayload = result.getString("request_payload"),
                                attemptCount = result.getInt("attempt_count"),
                            )
                        }
                    }
                }
                if (rows.isNotEmpty()) {
                    connection.prepareStatement(
                        """
                        UPDATE payment_provider_operation_outbox
                        SET state = 'PROCESSING', locked_at = ?, locked_by = ?,
                            attempt_count = attempt_count + 1, updated_at = ?
                        WHERE id = ANY (?) AND state IN ('PENDING', 'RETRY_WAIT')
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setObject(1, now)
                        statement.setString(2, workerId)
                        statement.setObject(3, now)
                        statement.setArray(4, connection.createArrayOf("uuid", rows.map { it.id }.toTypedArray()))
                        check(statement.executeUpdate() == rows.size) { "payment_operation_claim_race" }
                    }
                }
                connection.commit()
                return rows.map { it.copy(attemptCount = it.attemptCount + 1) }
            } catch (throwable: Throwable) {
                runCatching { connection.rollback() }
                throw throwable
            } finally {
                connection.autoCommit = oldAutoCommit
            }
        }
    }

    fun complete(operationId: UUID, result: ProviderOperationExecutionResult, now: Instant = Instant.now()) {
        require(result.successful)
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                UPDATE payment_provider_operation_outbox
                SET state = 'SUCCEEDED', locked_at = NULL, locked_by = NULL,
                    last_http_status = ?, provider_reference = ?, provider_response = ?::jsonb,
                    last_safe_error_code = NULL, last_safe_error_message = NULL,
                    completed_at = ?, updated_at = ?
                WHERE id = ? AND state = 'PROCESSING'
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, result.httpStatus)
                statement.setString(2, result.providerReference?.take(240))
                statement.setString(3, result.safeProviderResponse?.let(PaymentSecretRedactor::redactText)?.take(MAX_RESPONSE_CHARS) ?: "{}")
                statement.setObject(4, now)
                statement.setObject(5, now)
                statement.setObject(6, operationId)
                check(statement.executeUpdate() == 1) { "payment_operation_completion_race" }
            }
        }
    }

    fun failOrRetry(
        operationId: UUID,
        result: ProviderOperationExecutionResult,
        nextAttemptAt: Instant?,
        now: Instant = Instant.now(),
    ) {
        require(!result.successful)
        val retry = result.retryable && nextAttemptAt != null
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                UPDATE payment_provider_operation_outbox
                SET state = ?, locked_at = NULL, locked_by = NULL, next_attempt_at = ?,
                    last_http_status = ?, last_safe_error_code = ?, last_safe_error_message = ?,
                    provider_response = ?::jsonb, completed_at = ?, updated_at = ?
                WHERE id = ? AND state = 'PROCESSING'
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, if (retry) "RETRY_WAIT" else "FAILED")
                statement.setObject(2, nextAttemptAt ?: now)
                statement.setObject(3, result.httpStatus)
                statement.setString(4, result.safeErrorCode?.take(160) ?: "provider_operation_failed")
                statement.setString(5, PaymentSecretRedactor.redactText(result.safeErrorMessage.orEmpty()).take(1_024))
                statement.setString(6, result.safeProviderResponse?.let(PaymentSecretRedactor::redactText)?.take(MAX_RESPONSE_CHARS) ?: "{}")
                statement.setObject(7, if (retry) null else now)
                statement.setObject(8, now)
                statement.setObject(9, operationId)
                check(statement.executeUpdate() == 1) { "payment_operation_failure_race" }
            }
        }
    }

    companion object {
        private val SAFE_CODE = Regex("[A-Z0-9_.-]{1,80}")
        private const val MAX_PAYLOAD_BYTES = 256 * 1024
        private const val MAX_RESPONSE_CHARS = 256 * 1024
    }
}
