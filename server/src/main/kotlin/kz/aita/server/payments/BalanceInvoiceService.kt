package kz.aita.server.payments

import java.sql.Connection
import java.time.Instant
import java.util.UUID

enum class InternalBalanceInvoiceState {
    CREATED,
    PROVIDER_PENDING,
    WAITING_PAYMENT,
    PAID,
    CREDITED,
    EXPIRED,
    CANCELLED,
    FAILED,
}

data class InternalBalanceInvoice(
    val id: UUID,
    val storeId: UUID,
    val amountMinor: Long,
    val currency: String,
    val state: InternalBalanceInvoiceState,
    val provider: String,
    val providerInvoiceId: String?,
    val paymentUrl: String?,
    val qrPayload: String?,
    val expiresAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

class BalanceInvoiceService(
    private val connectionFactory: () -> Connection,
    private val ledgerRepository: PaymentLedgerRepository,
    private val outboxRepository: PaymentOperationOutboxRepository,
) {
    fun createInvoice(
        storeId: UUID,
        amountMinor: Long,
        currency: String,
        idempotencyKey: String,
        provider: String = "KASPI_PAY",
        environment: String,
        returnUrl: String? = null,
        now: Instant = Instant.now(),
    ): InternalBalanceInvoice {
        require(amountMinor > 0L)
        require(currency == "KZT")
        require(idempotencyKey.length in 16..128)
        require(provider.matches(SAFE_CODE))
        require(environment.matches(SAFE_CODE))
        require(returnUrl == null || returnUrl.startsWith("https://") || returnUrl.startsWith("http://localhost"))
        val invoice = connectionFactory().use { connection ->
            val oldAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                advisoryLock(connection, "topup:$storeId:$idempotencyKey")
                findByIdempotency(connection, storeId, idempotencyKey)?.let {
                    connection.commit()
                    return@use it
                }
                val id = UUID.randomUUID()
                connection.prepareStatement(
                    """
                    INSERT INTO aita_balance_invoices
                        (id, store_id, amount_minor, currency, state, provider, environment,
                         idempotency_key, return_url, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'CREATED', ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, id)
                    statement.setObject(2, storeId)
                    statement.setLong(3, amountMinor)
                    statement.setString(4, currency)
                    statement.setString(5, provider)
                    statement.setString(6, environment)
                    statement.setString(7, idempotencyKey)
                    statement.setString(8, returnUrl)
                    statement.setObject(9, now)
                    statement.setObject(10, now)
                    statement.executeUpdate()
                }
                val created = get(connection, id) ?: error("balance_invoice_creation_failed")
                connection.commit()
                created
            } catch (throwable: Throwable) {
                runCatching { connection.rollback() }
                throw throwable
            } finally {
                connection.autoCommit = oldAutoCommit
            }
        }
        if (invoice.state == InternalBalanceInvoiceState.CREATED) {
            outboxRepository.enqueue(
                storeId = storeId,
                provider = provider,
                environment = environment,
                operationKind = "CREATE_BALANCE_INVOICE",
                aggregateType = "BALANCE_INVOICE",
                aggregateId = invoice.id.toString(),
                idempotencyKey = "create:$idempotencyKey",
                requestPayload = buildCreatePayload(invoice, returnUrl),
                now = now,
            )
            markProviderPending(invoice.id, now)
        }
        return get(invoice.id) ?: invoice
    }

    fun attachProviderInvoice(
        invoiceId: UUID,
        providerInvoiceId: String,
        paymentUrl: String?,
        qrPayload: String?,
        expiresAt: Instant?,
        now: Instant = Instant.now(),
    ): InternalBalanceInvoice = mutate(invoiceId) { connection, current ->
        require(current.state in setOf(InternalBalanceInvoiceState.CREATED, InternalBalanceInvoiceState.PROVIDER_PENDING))
        require(providerInvoiceId.length in 1..240)
        require(paymentUrl == null || paymentUrl.startsWith("https://"))
        connection.prepareStatement(
            """
            UPDATE aita_balance_invoices
            SET state = 'WAITING_PAYMENT', provider_invoice_id = ?, payment_url = ?,
                qr_payload = ?, expires_at = ?, updated_at = ?, safe_error_code = NULL,
                safe_error_message = NULL
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, providerInvoiceId)
            statement.setString(2, paymentUrl)
            statement.setString(3, qrPayload?.take(16_384))
            statement.setObject(4, expiresAt)
            statement.setObject(5, now)
            statement.setObject(6, invoiceId)
            statement.executeUpdate()
        }
    }

    fun recordPaid(
        invoiceId: UUID,
        providerEventId: String,
        paidAmountMinor: Long,
        paidCurrency: String,
        now: Instant = Instant.now(),
    ): BalanceCreditResult {
        val invoice = get(invoiceId) ?: error("balance_invoice_not_found")
        require(invoice.state !in TERMINAL_UNPAID) { "balance_invoice_not_creditable" }
        require(paidAmountMinor == invoice.amountMinor) { "paid_amount_mismatch" }
        require(paidCurrency == invoice.currency) { "paid_currency_mismatch" }
        connectionFactory().use { connection ->
            connection.prepareStatement(
                """
                UPDATE aita_balance_invoices
                SET state = CASE WHEN state = 'CREDITED' THEN state ELSE 'PAID' END,
                    paid_at = COALESCE(paid_at, ?), updated_at = ?
                WHERE id = ? AND state NOT IN ('EXPIRED', 'CANCELLED', 'FAILED')
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, now)
                statement.setObject(2, now)
                statement.setObject(3, invoiceId)
                check(statement.executeUpdate() == 1) { "balance_invoice_not_creditable" }
            }
        }
        return ledgerRepository.creditPaidInvoice(
            storeId = invoice.storeId,
            invoiceId = invoice.id,
            amountMinor = invoice.amountMinor,
            currency = invoice.currency,
            providerEventId = providerEventId,
            now = now,
        )
    }

    fun markExpired(invoiceId: UUID, now: Instant = Instant.now()): InternalBalanceInvoice =
        markTerminal(invoiceId, InternalBalanceInvoiceState.EXPIRED, now)

    fun markCancelled(invoiceId: UUID, now: Instant = Instant.now()): InternalBalanceInvoice =
        markTerminal(invoiceId, InternalBalanceInvoiceState.CANCELLED, now)

    fun get(invoiceId: UUID): InternalBalanceInvoice? = connectionFactory().use { get(it, invoiceId) }

    private fun markProviderPending(invoiceId: UUID, now: Instant) {
        connectionFactory().use { connection ->
            connection.prepareStatement(
                "UPDATE aita_balance_invoices SET state = 'PROVIDER_PENDING', updated_at = ? WHERE id = ? AND state = 'CREATED'",
            ).use { statement ->
                statement.setObject(1, now)
                statement.setObject(2, invoiceId)
                statement.executeUpdate()
            }
        }
    }

    private fun markTerminal(invoiceId: UUID, state: InternalBalanceInvoiceState, now: Instant): InternalBalanceInvoice =
        mutate(invoiceId) { connection, current ->
            if (current.state in setOf(
                    InternalBalanceInvoiceState.PAID,
                    InternalBalanceInvoiceState.CREDITED,
                )
            ) {
                error("paid_invoice_is_immutable")
            }
            if (current.state in TERMINAL_UNPAID && current.state != state) {
                error("terminal_invoice_is_immutable")
            }
            connection.prepareStatement(
                "UPDATE aita_balance_invoices SET state = ?, updated_at = ? WHERE id = ?",
            ).use { statement ->
                statement.setString(1, state.name)
                statement.setObject(2, now)
                statement.setObject(3, invoiceId)
                statement.executeUpdate()
            }
        }

    private fun mutate(
        invoiceId: UUID,
        block: (Connection, InternalBalanceInvoice) -> Unit,
    ): InternalBalanceInvoice = connectionFactory().use { connection ->
        val oldAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            advisoryLock(connection, "invoice:$invoiceId")
            val current = get(connection, invoiceId) ?: error("balance_invoice_not_found")
            block(connection, current)
            val updated = get(connection, invoiceId) ?: error("balance_invoice_not_found")
            connection.commit()
            updated
        } catch (throwable: Throwable) {
            runCatching { connection.rollback() }
            throw throwable
        } finally {
            connection.autoCommit = oldAutoCommit
        }
    }

    private fun findByIdempotency(connection: Connection, storeId: UUID, key: String): InternalBalanceInvoice? =
        connection.prepareStatement(
            "SELECT * FROM aita_balance_invoices WHERE store_id = ? AND idempotency_key = ? LIMIT 1",
        ).use { statement ->
            statement.setObject(1, storeId)
            statement.setString(2, key)
            statement.executeQuery().use { if (it.next()) it.toInvoice() else null }
        }

    private fun get(connection: Connection, invoiceId: UUID): InternalBalanceInvoice? =
        connection.prepareStatement("SELECT * FROM aita_balance_invoices WHERE id = ? LIMIT 1").use { statement ->
            statement.setObject(1, invoiceId)
            statement.executeQuery().use { if (it.next()) it.toInvoice() else null }
        }

    private fun java.sql.ResultSet.toInvoice(): InternalBalanceInvoice = InternalBalanceInvoice(
        id = getObject("id", UUID::class.java),
        storeId = getObject("store_id", UUID::class.java),
        amountMinor = getLong("amount_minor"),
        currency = getString("currency"),
        state = InternalBalanceInvoiceState.valueOf(getString("state")),
        provider = getString("provider"),
        providerInvoiceId = getString("provider_invoice_id"),
        paymentUrl = getString("payment_url"),
        qrPayload = getString("qr_payload"),
        expiresAt = getObject("expires_at", Instant::class.java),
        createdAt = getObject("created_at", Instant::class.java),
        updatedAt = getObject("updated_at", Instant::class.java),
    )

    private fun advisoryLock(connection: Connection, key: String) {
        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { check(it.next()) }
        }
    }

    private fun buildCreatePayload(invoice: InternalBalanceInvoice, returnUrl: String?): String = buildString {
        append('{')
        append("\"invoiceId\":\"").append(invoice.id).append("\",")
        append("\"amountMinor\":").append(invoice.amountMinor).append(',')
        append("\"currency\":\"").append(invoice.currency).append('\"')
        returnUrl?.let { append(",\"returnUrl\":\"").append(escapeJson(it)).append('\"') }
        append('}')
    }

    private fun escapeJson(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private val SAFE_CODE = Regex("[A-Z0-9_.-]{1,80}")
        private val TERMINAL_UNPAID = setOf(
            InternalBalanceInvoiceState.EXPIRED,
            InternalBalanceInvoiceState.CANCELLED,
            InternalBalanceInvoiceState.FAILED,
        )
    }
}
