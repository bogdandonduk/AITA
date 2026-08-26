package kz.aita.server.payments

import java.sql.Connection
import java.time.Instant
import java.util.*

/** Result of an idempotent balance mutation. */
data class BalanceCreditResult(
    val ledgerEntryId: UUID,
    val balanceAfterMinor: Long,
    val newlyCredited: Boolean,
)

/**
 * JDBC implementation deliberately accepts a connection factory so it can share AITA's existing
 * pool without owning another pool. Every mutation is serialized by a PostgreSQL row lock and an
 * immutable ledger idempotency key.
 */
class PaymentLedgerRepository(
    private val connectionFactory: () -> Connection,
) {
    fun creditPaidInvoice(
        storeId: UUID,
        invoiceId: UUID,
        amountMinor: Long,
        currency: String,
        providerEventId: String,
        now: Instant = Instant.now(),
    ): BalanceCreditResult {
        require(amountMinor > 0L)
        require(currency == "KZT")
        require(providerEventId.isNotBlank())
        connectionFactory().use { connection ->
            val oldAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                advisoryLock(connection, "balance:$storeId")
                existingLedger(connection, invoiceId)?.let {
                    connection.commit()
                    return BalanceCreditResult(it.first, it.second, newlyCredited = false)
                }
                ensureAccount(connection, storeId, currency, now)
                val current = lockAccount(connection, storeId, currency)
                val next = Math.addExact(current, amountMinor)
                val ledgerId = UUID.randomUUID()
                connection.prepareStatement(
                    """
                    INSERT INTO aita_balance_ledger_entries
                        (id, store_id, delta_minor, balance_after_minor, currency, kind,
                         reference_type, reference_id, provider_event_id, created_at)
                    VALUES (?, ?, ?, ?, ?, 'TOP_UP', 'BALANCE_INVOICE', ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, ledgerId)
                    statement.setObject(2, storeId)
                    statement.setLong(3, amountMinor)
                    statement.setLong(4, next)
                    statement.setString(5, currency)
                    statement.setString(6, invoiceId.toString())
                    statement.setString(7, providerEventId)
                    statement.setObject(8, now)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE aita_balance_accounts
                    SET available_minor = ?, revision = revision + 1, updated_at = ?
                    WHERE store_id = ? AND currency = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, next)
                    statement.setObject(2, now)
                    statement.setObject(3, storeId)
                    statement.setString(4, currency)
                    check(statement.executeUpdate() == 1) { "balance_account_update_failed" }
                }
                connection.prepareStatement(
                    """
                    UPDATE aita_balance_invoices
                    SET state = 'CREDITED', credited_at = COALESCE(credited_at, ?), updated_at = ?
                    WHERE id = ? AND store_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, now)
                    statement.setObject(2, now)
                    statement.setObject(3, invoiceId)
                    statement.setObject(4, storeId)
                    check(statement.executeUpdate() == 1) { "balance_invoice_not_found" }
                }
                connection.commit()
                return BalanceCreditResult(ledgerId, next, newlyCredited = true)
            } catch (throwable: Throwable) {
                runCatching { connection.rollback() }
                throw throwable
            } finally {
                connection.autoCommit = oldAutoCommit
            }
        }
    }

    private fun advisoryLock(connection: Connection, key: String) {
        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { check(it.next()) }
        }
    }

    private fun existingLedger(connection: Connection, invoiceId: UUID): Pair<UUID, Long>? =
        connection.prepareStatement(
            """
            SELECT id, balance_after_minor
            FROM aita_balance_ledger_entries
            WHERE reference_type = 'BALANCE_INVOICE' AND reference_id = ?
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, invoiceId.toString())
            statement.executeQuery().use { result ->
                if (!result.next()) null else result.getObject("id", UUID::class.java) to result.getLong("balance_after_minor")
            }
        }

    private fun ensureAccount(connection: Connection, storeId: UUID, currency: String, now: Instant) {
        connection.prepareStatement(
            """
            INSERT INTO aita_balance_accounts (store_id, currency, available_minor, revision, created_at, updated_at)
            VALUES (?, ?, 0, 0, ?, ?)
            ON CONFLICT (store_id, currency) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, storeId)
            statement.setString(2, currency)
            statement.setObject(3, now)
            statement.setObject(4, now)
            statement.executeUpdate()
        }
    }

    private fun lockAccount(connection: Connection, storeId: UUID, currency: String): Long =
        connection.prepareStatement(
            """
            SELECT available_minor FROM aita_balance_accounts
            WHERE store_id = ? AND currency = ? FOR UPDATE
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, storeId)
            statement.setString(2, currency)
            statement.executeQuery().use { result ->
                check(result.next()) { "balance_account_missing" }
                result.getLong("available_minor")
            }
        }
}
