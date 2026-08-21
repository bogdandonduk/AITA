package kz.aita.server.payments

import java.sql.Connection
import kz.aita.payments.management.PaymentBalanceDto
import kz.aita.payments.management.PaymentTopUpInvoiceDto

internal object PaymentFinancialReadRepository {
    fun balance(connection: Connection, storeId: String): PaymentBalanceDto {
        val shape = TableShape.discover(connection, "aita_balance_accounts")
        val currency = shape.optional("currency")
        val balance = shape.required("balance_minor", "amount_minor", "balance")
        val revision = shape.optional("revision")
        val updated = shape.optional("updated_at")
        val sql = buildString {
            append("SELECT ")
            append(currency ?: "'KZT'")
            append(", ").append(balance)
            append(", ").append(revision ?: "0")
            append(", ").append(updated ?: "NULL")
            append(" FROM ${shape.table} WHERE CAST(${shape.required("store_id")} AS TEXT) = ?")
            if (currency != null) append(" AND UPPER($currency) = 'KZT'")
            append(" LIMIT 1")
        }
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, storeId)
            statement.executeQuery().use { rs ->
                if (!rs.next()) PaymentBalanceDto(storeId = storeId)
                else PaymentBalanceDto(
                    storeId = storeId,
                    currency = rs.getString(1) ?: "KZT",
                    balanceMinor = rs.getLong(2),
                    revision = rs.getLong(3),
                    updatedAtEpochMillis = rs.getTimestamp(4)?.toInstant()?.toEpochMilli(),
                )
            }
        }
    }

    fun invoices(connection: Connection, storeId: String, limit: Int): List<PaymentTopUpInvoiceDto> {
        val shape = TableShape.discover(connection, "aita_balance_invoices")
        fun expression(vararg names: String, fallback: String = "NULL") = shape.optional(*names) ?: fallback
        val id = shape.required("id")
        val amount = shape.required("amount_minor")
        val currency = expression("currency", fallback = "'KZT'")
        val status = shape.required("status")
        val url = expression("payment_url", "provider_payment_url")
        val qr = expression("qr_payload", "provider_qr_payload")
        val expires = expression("expires_at")
        val created = expression("created_at")
        val updated = expression("updated_at")
        val sql = """
            SELECT CAST($id AS TEXT), $amount, $currency, $status, $url, $qr, $expires, $created, $updated
            FROM ${shape.table}
            WHERE CAST(${shape.required("store_id")} AS TEXT) = ?
            ORDER BY ${shape.optional("created_at") ?: id} DESC
            LIMIT ?
        """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, storeId)
            statement.setInt(2, limit.coerceIn(1, 100))
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        PaymentTopUpInvoiceDto(
                            id = rs.getString(1),
                            storeId = storeId,
                            amountMinor = rs.getLong(2),
                            currency = rs.getString(3) ?: "KZT",
                            status = rs.getString(4),
                            paymentUrl = rs.getString(5)?.takeIf { it.startsWith("https://") },
                            qrPayload = rs.getString(6)?.take(16_384),
                            expiresAtEpochMillis = rs.getTimestamp(7)?.toInstant()?.toEpochMilli(),
                            createdAtEpochMillis = rs.getTimestamp(8)?.toInstant()?.toEpochMilli(),
                            updatedAtEpochMillis = rs.getTimestamp(9)?.toInstant()?.toEpochMilli(),
                        )
                    )
                }
            }
        }
    }
}

private data class TableShape(val table: String, val columns: Set<String>) {
    fun required(vararg candidates: String): String = optional(*candidates)
        ?: error("Table $table lacks ${candidates.joinToString()}")
    fun optional(vararg candidates: String): String? =
        candidates.firstNotNullOfOrNull { wanted -> columns.firstOrNull { it.equals(wanted, true) } }
            ?: columns.firstOrNull { column -> candidates.any { wanted -> column.contains(wanted, true) } }

    companion object {
        fun discover(connection: Connection, table: String): TableShape {
            val columns = linkedSetOf<String>()
            connection.prepareStatement(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?"
            ).use { statement ->
                statement.setString(1, table)
                statement.executeQuery().use { rs -> while (rs.next()) columns += rs.getString(1) }
            }
            require(columns.isNotEmpty()) { "Required payment table $table is missing" }
            return TableShape(table, columns)
        }
    }
}
