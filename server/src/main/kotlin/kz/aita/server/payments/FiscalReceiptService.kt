package kz.aita.server.payments

import java.sql.Connection
import java.time.Instant
import java.util.*

enum class InternalFiscalReceiptState {
    PENDING,
    QUEUED,
    PROCESSING,
    FISCALIZED,
    RETRY_WAIT,
    FAILED,
    CANCELLED,
}

data class InternalFiscalReceipt(
    val id: UUID,
    val storeId: UUID,
    val transactionReference: String,
    val operation: String,
    val state: InternalFiscalReceiptState,
    val idempotencyKey: String,
    val requestPayload: String,
    val originalReceiptId: UUID?,
)

class FiscalReceiptService(
    private val connectionFactory: () -> Connection,
    private val outboxRepository: PaymentOperationOutboxRepository,
) {
    fun enqueue(
        storeId: UUID,
        transactionReference: String,
        operation: String,
        idempotencyKey: String,
        requestPayload: String,
        originalReceiptId: UUID? = null,
        environment: String,
        now: Instant = Instant.now(),
    ): InternalFiscalReceipt {
        require(transactionReference.length in 1..200)
        require(operation in setOf("SALE", "RETURN"))
        require(idempotencyKey.length in 16..180)
        require(requestPayload.toByteArray().size <= 512 * 1024)
        if (operation == "RETURN") require(originalReceiptId != null) { "original_receipt_required" }
        val receipt = connectionFactory().use { connection ->
            val oldAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                advisoryLock(connection, "fiscal:$storeId:$idempotencyKey")
                findByIdempotency(connection, storeId, idempotencyKey)?.let {
                    connection.commit()
                    return@use it
                }
                if (originalReceiptId != null) {
                    val original = get(connection, originalReceiptId) ?: error("original_receipt_not_found")
                    require(original.storeId == storeId) { "original_receipt_store_mismatch" }
                    require(original.operation == "SALE") { "original_receipt_must_be_sale" }
                    require(original.state == InternalFiscalReceiptState.FISCALIZED) { "original_receipt_not_fiscalized" }
                }
                val id = UUID.randomUUID()
                connection.prepareStatement(
                    """
                    INSERT INTO fiscal_receipts
                        (id, store_id, transaction_reference, operation, state, idempotency_key,
                         request_payload, original_receipt_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'PENDING', ?, ?::jsonb, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, id)
                    statement.setObject(2, storeId)
                    statement.setString(3, transactionReference)
                    statement.setString(4, operation)
                    statement.setString(5, idempotencyKey)
                    statement.setString(6, requestPayload)
                    statement.setObject(7, originalReceiptId)
                    statement.setObject(8, now)
                    statement.setObject(9, now)
                    statement.executeUpdate()
                }
                val created = get(connection, id) ?: error("fiscal_receipt_creation_failed")
                connection.commit()
                created
            } catch (throwable: Throwable) {
                runCatching { connection.rollback() }
                throw throwable
            } finally {
                connection.autoCommit = oldAutoCommit
            }
        }
        if (receipt.state == InternalFiscalReceiptState.PENDING) {
            outboxRepository.enqueue(
                storeId = storeId,
                provider = "WEBKASSA",
                environment = environment,
                operationKind = if (operation == "SALE") "FISCALIZE_SALE" else "FISCALIZE_RETURN",
                aggregateType = "FISCAL_RECEIPT",
                aggregateId = receipt.id.toString(),
                idempotencyKey = "fiscal:$idempotencyKey",
                requestPayload = requestPayload,
                now = now,
            )
            markQueued(receipt.id, now)
        }
        return get(receipt.id) ?: receipt
    }

    fun markFiscalized(
        receiptId: UUID,
        providerReceiptId: String,
        fiscalDocumentNumber: String?,
        fiscalSign: String?,
        receiptUrl: String?,
        safeProviderResponse: String,
        now: Instant = Instant.now(),
    ): InternalFiscalReceipt {
        get(receiptId)?.takeIf { it.state == InternalFiscalReceiptState.FISCALIZED }?.let { return it }
        return mutate(receiptId) { connection, current ->
        require(current.state != InternalFiscalReceiptState.CANCELLED)
        require(providerReceiptId.length in 1..240)
        require(receiptUrl == null || receiptUrl.startsWith("https://"))
        connection.prepareStatement(
            """
            UPDATE fiscal_receipts
            SET state = 'FISCALIZED', provider_receipt_id = ?, fiscal_document_number = ?,
                fiscal_sign = ?, receipt_url = ?, provider_response = ?::jsonb,
                fiscalized_at = ?, updated_at = ?, safe_error_code = NULL, safe_error_message = NULL
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, providerReceiptId)
            statement.setString(2, fiscalDocumentNumber?.take(240))
            statement.setString(3, fiscalSign?.take(512))
            statement.setString(4, receiptUrl)
            statement.setString(5, safeProviderResponse.take(256 * 1024))
            statement.setObject(6, now)
            statement.setObject(7, now)
            statement.setObject(8, receiptId)
            statement.executeUpdate()
        }
    }
    }

    fun get(receiptId: UUID): InternalFiscalReceipt? = connectionFactory().use { get(it, receiptId) }

    private fun markQueued(receiptId: UUID, now: Instant) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                "UPDATE fiscal_receipts SET state = 'QUEUED', updated_at = ? WHERE id = ? AND state = 'PENDING'",
            ).use { statement ->
                statement.setObject(1, now)
                statement.setObject(2, receiptId)
                statement.executeUpdate()
            }
        }
    }

    private fun mutate(
        receiptId: UUID,
        block: (Connection, InternalFiscalReceipt) -> Unit,
    ): InternalFiscalReceipt = connectionFactory().use { connection ->
        val oldAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            advisoryLock(connection, "fiscal:$receiptId")
            val current = get(connection, receiptId) ?: error("fiscal_receipt_not_found")
            block(connection, current)
            val updated = get(connection, receiptId) ?: error("fiscal_receipt_not_found")
            connection.commit()
            updated
        } catch (throwable: Throwable) {
            runCatching { connection.rollback() }
            throw throwable
        } finally {
            connection.autoCommit = oldAutoCommit
        }
    }

    private fun findByIdempotency(connection: Connection, storeId: UUID, key: String): InternalFiscalReceipt? =
        connection.prepareStatement(
            "SELECT * FROM fiscal_receipts WHERE store_id = ? AND idempotency_key = ? LIMIT 1",
        ).use { statement ->
            statement.setObject(1, storeId)
            statement.setString(2, key)
            statement.executeQuery().use { if (it.next()) it.toReceipt() else null }
        }

    private fun get(connection: Connection, receiptId: UUID): InternalFiscalReceipt? =
        connection.prepareStatement("SELECT * FROM fiscal_receipts WHERE id = ? LIMIT 1").use { statement ->
            statement.setObject(1, receiptId)
            statement.executeQuery().use { if (it.next()) it.toReceipt() else null }
        }

    private fun java.sql.ResultSet.toReceipt(): InternalFiscalReceipt = InternalFiscalReceipt(
        id = getObject("id", UUID::class.java),
        storeId = getObject("store_id", UUID::class.java),
        transactionReference = getString("transaction_reference"),
        operation = getString("operation"),
        state = InternalFiscalReceiptState.valueOf(getString("state")),
        idempotencyKey = getString("idempotency_key"),
        requestPayload = getString("request_payload"),
        originalReceiptId = getObject("original_receipt_id", UUID::class.java),
    )

    private fun advisoryLock(connection: Connection, key: String) {
        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { check(it.next()) }
        }
    }
}
