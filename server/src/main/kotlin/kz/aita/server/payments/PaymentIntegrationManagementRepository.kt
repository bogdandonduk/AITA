package kz.aita.server.payments

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kz.aita.payments.management.*
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

internal class PaymentRevisionConflict(val currentRevision: Long) : RuntimeException("Payment integration revision conflict")
internal class PaymentCredentialSchemaException(message: String) : RuntimeException(message)

internal class PaymentIntegrationManagementRepository(
    private val cipher: PaymentManagedSecretCipher,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun list(connection: Connection, storeId: String): List<PaymentIntegrationSummaryDto> {
        val sql = """
            SELECT store_id::text, provider, environment, display_name, enabled, revision,
                   verification_state, settings_json::text, last_verified_at, last_error_code, updated_at
            FROM payment_integration_profile_metadata
            WHERE store_id::text = ?
            ORDER BY provider, environment
        """.trimIndent()
        val secretKeys = credentialKeyIndex(connection, storeId)
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, storeId)
            statement.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toSummary(secretKeys)) } }
        }
    }

    fun find(connection: Connection, storeId: String, provider: PaymentManagedProvider, environment: PaymentManagedEnvironment): PaymentIntegrationSummaryDto? =
        list(connection, storeId).firstOrNull { it.provider == provider && it.environment == environment }

    fun upsert(
        connection: Connection,
        actorId: String,
        provider: PaymentManagedProvider,
        request: PaymentIntegrationSecretPatchRequest,
        allowedSecretKeys: Set<String>,
    ): PaymentIntegrationSummaryDto {
        val storeId = request.storeId.trim()
        val environment = request.environment
        val current = lockMetadata(connection, storeId, provider, environment)
        val currentRevision = current?.revision ?: 0L
        request.expectedRevision?.let { if (it != currentRevision) throw PaymentRevisionConflict(currentRevision) }
        val disallowed = (request.secrets.keys + request.clearSecretKeys) - allowedSecretKeys
        require(disallowed.isEmpty()) { "Credential fields are not allowed for this provider: ${disallowed.sorted().joinToString()}" }

        val normalizedSettings = request.settings.mapKeys { it.key.trim() }.mapValues { it.value.trim() }
        val nextRevision = Math.addExact(currentRevision, 1L)
        val now = Instant.now()
        val upsertSql = """
            INSERT INTO payment_integration_profile_metadata
                (id, store_id, provider, environment, display_name, enabled, revision, verification_state,
                 settings_json, last_verified_at, last_error_code, created_at, updated_at)
            VALUES (?, ?::uuid, ?, ?, ?, ?, ?, ?, ?::jsonb, NULL, NULL, ?, ?)
            ON CONFLICT (store_id, provider, environment) DO UPDATE SET
                display_name = EXCLUDED.display_name,
                enabled = EXCLUDED.enabled,
                revision = EXCLUDED.revision,
                verification_state = EXCLUDED.verification_state,
                settings_json = EXCLUDED.settings_json,
                last_verified_at = NULL,
                last_error_code = NULL,
                updated_at = EXCLUDED.updated_at
        """.trimIndent()
        connection.prepareStatement(upsertSql).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setString(2, storeId)
            statement.setString(3, provider.name)
            statement.setString(4, environment.name)
            statement.setString(5, request.displayName?.trim()?.takeIf(String::isNotEmpty))
            statement.setBoolean(6, request.enabled)
            statement.setLong(7, nextRevision)
            statement.setString(8, if (request.enabled) PaymentVerificationState.CONFIGURED_UNVERIFIED.name else PaymentVerificationState.DISABLED.name)
            statement.setString(9, json.encodeToString(normalizedSettings))
            statement.setTimestamp(10, Timestamp.from(now))
            statement.setTimestamp(11, Timestamp.from(now))
            statement.executeUpdate()
        }

        val shape = CredentialTableShape.discover(connection)
        request.clearSecretKeys.forEach { deleteCredential(connection, shape, storeId, provider, environment, it) }
        request.secrets.forEach { (rawName, rawValue) ->
            val name = rawName.trim()
            val encrypted = cipher.encrypt(storeId, provider.name, environment.name, name, rawValue.toCharArray())
            upsertCredential(connection, shape, storeId, provider, environment, name, encrypted, actorId, now)
        }
        audit(connection, storeId, provider, environment, actorId, "UPSERT", nextRevision, null)
        return find(connection, storeId, provider, environment) ?: error("Integration metadata disappeared after save")
    }

    fun delete(
        connection: Connection,
        actorId: String,
        storeId: String,
        provider: PaymentManagedProvider,
        environment: PaymentManagedEnvironment,
        expectedRevision: Long?,
    ): Boolean {
        val current = lockMetadata(connection, storeId, provider, environment) ?: return false
        expectedRevision?.let { if (it != current.revision) throw PaymentRevisionConflict(current.revision) }
        val shape = CredentialTableShape.discover(connection)
        deleteAllCredentials(connection, shape, storeId, provider, environment)
        connection.prepareStatement(
            "DELETE FROM payment_integration_profile_metadata WHERE store_id::text = ? AND provider = ? AND environment = ?"
        ).use { statement ->
            statement.setString(1, storeId)
            statement.setString(2, provider.name)
            statement.setString(3, environment.name)
            statement.executeUpdate()
        }
        audit(connection, storeId, provider, environment, actorId, "DELETE", current.revision, null)
        return true
    }

    fun markVerification(
        connection: Connection,
        actorId: String,
        storeId: String,
        provider: PaymentManagedProvider,
        environment: PaymentManagedEnvironment,
        state: PaymentVerificationState,
        errorCode: String?,
    ): PaymentIntegrationSummaryDto? {
        val current = lockMetadata(connection, storeId, provider, environment) ?: return null
        val nextRevision = Math.addExact(current.revision, 1L)
        connection.prepareStatement(
            """
            UPDATE payment_integration_profile_metadata
            SET revision = ?, verification_state = ?, last_verified_at = ?, last_error_code = ?, updated_at = ?
            WHERE store_id::text = ? AND provider = ? AND environment = ?
            """.trimIndent()
        ).use { statement ->
            val now = Timestamp.from(Instant.now())
            statement.setLong(1, nextRevision)
            statement.setString(2, state.name)
            statement.setTimestamp(3, now)
            statement.setString(4, errorCode)
            statement.setTimestamp(5, now)
            statement.setString(6, storeId)
            statement.setString(7, provider.name)
            statement.setString(8, environment.name)
            statement.executeUpdate()
        }
        audit(connection, storeId, provider, environment, actorId, "VERIFY", nextRevision, errorCode)
        return find(connection, storeId, provider, environment)
    }

    private fun lockMetadata(connection: Connection, storeId: String, provider: PaymentManagedProvider, environment: PaymentManagedEnvironment): PaymentIntegrationSummaryDto? {
        connection.prepareStatement(
            """
            SELECT store_id::text, provider, environment, display_name, enabled, revision,
                   verification_state, settings_json::text, last_verified_at, last_error_code, updated_at
            FROM payment_integration_profile_metadata
            WHERE store_id::text = ? AND provider = ? AND environment = ?
            FOR UPDATE
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, storeId)
            statement.setString(2, provider.name)
            statement.setString(3, environment.name)
            statement.executeQuery().use { rs ->
                if (!rs.next()) return null
                return rs.toSummary(emptyMap())
            }
        }
    }

    private fun ResultSet.toSummary(secretKeys: Map<String, List<String>>): PaymentIntegrationSummaryDto {
        val storeId = getString(1)
        val provider = PaymentManagedProvider.valueOf(getString(2))
        val environment = PaymentManagedEnvironment.valueOf(getString(3))
        val settings = runCatching {
            json.parseToJsonElement(getString(8) ?: "{}").jsonObject.mapValues { it.value.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
        return PaymentIntegrationSummaryDto(
            storeId = storeId,
            provider = provider,
            environment = environment,
            displayName = getString(4),
            enabled = getBoolean(5),
            revision = getLong(6),
            verificationState = runCatching { PaymentVerificationState.valueOf(getString(7)) }.getOrDefault(PaymentVerificationState.CONFIGURED_UNVERIFIED),
            configuredSecretKeys = secretKeys["$provider|$environment"].orEmpty().sorted(),
            settings = settings,
            lastVerifiedAtEpochMillis = getTimestamp(9)?.toInstant()?.toEpochMilli(),
            lastErrorCode = getString(10),
            updatedAtEpochMillis = getTimestamp(11)?.toInstant()?.toEpochMilli(),
        )
    }

    private fun credentialKeyIndex(connection: Connection, storeId: String): Map<String, List<String>> {
        val shape = runCatching { CredentialTableShape.discover(connection) }.getOrNull() ?: return emptyMap()
        val sql = "SELECT ${shape.provider}, ${shape.environment}, ${shape.name} FROM ${shape.table} WHERE CAST(${shape.storeId} AS TEXT) = ?" +
            shape.active?.let { " AND COALESCE($it, TRUE) = TRUE" }.orEmpty()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, storeId)
            statement.executeQuery().use { rs ->
                buildMap<String, MutableList<String>> {
                    while (rs.next()) getOrPut("${rs.getString(1)}|${rs.getString(2)}") { mutableListOf() }.add(rs.getString(3))
                }
            }
        }
    }

    private fun upsertCredential(
        connection: Connection,
        shape: CredentialTableShape,
        storeId: String,
        provider: PaymentManagedProvider,
        environment: PaymentManagedEnvironment,
        name: String,
        encrypted: String,
        actorId: String,
        now: Instant,
    ) {
        deleteCredential(connection, shape, storeId, provider, environment, name)
        val values = linkedMapOf<String, Any?>()
        shape.id?.let { values[it] = UUID.randomUUID() }
        values[shape.storeId] = storeId
        values[shape.provider] = provider.name
        values[shape.environment] = environment.name
        values[shape.name] = name
        values[shape.encryptedValue] = encrypted
        shape.keyVersion?.let { values[it] = System.getenv("AITA_INTEGRATION_MASTER_KEY_VERSION")?.toIntOrNull() ?: 1 }
        shape.active?.let { values[it] = true }
        shape.createdAt?.let { values[it] = Timestamp.from(now) }
        shape.updatedAt?.let { values[it] = Timestamp.from(now) }
        shape.actor?.let { column ->
            values[column] = if (shape.columnTypes[column]?.contains("uuid", ignoreCase = true) == true) {
                runCatching { UUID.fromString(actorId) }.getOrNull()
            } else {
                actorId.take(256)
            }
        }
        val columns = values.keys.joinToString()
        val placeholders = values.keys.joinToString { column -> if (column == shape.storeId && shape.columnTypes[column]?.contains("uuid") == true) "?::uuid" else "?" }
        connection.prepareStatement("INSERT INTO ${shape.table} ($columns) VALUES ($placeholders)").use { statement ->
            values.values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    private fun deleteCredential(connection: Connection, shape: CredentialTableShape, storeId: String, provider: PaymentManagedProvider, environment: PaymentManagedEnvironment, name: String) {
        connection.prepareStatement(
            "DELETE FROM ${shape.table} WHERE CAST(${shape.storeId} AS TEXT) = ? AND ${shape.provider} = ? AND ${shape.environment} = ? AND ${shape.name} = ?"
        ).use { statement ->
            statement.setString(1, storeId)
            statement.setString(2, provider.name)
            statement.setString(3, environment.name)
            statement.setString(4, name)
            statement.executeUpdate()
        }
    }

    private fun deleteAllCredentials(connection: Connection, shape: CredentialTableShape, storeId: String, provider: PaymentManagedProvider, environment: PaymentManagedEnvironment) {
        connection.prepareStatement(
            "DELETE FROM ${shape.table} WHERE CAST(${shape.storeId} AS TEXT) = ? AND ${shape.provider} = ? AND ${shape.environment} = ?"
        ).use { statement ->
            statement.setString(1, storeId)
            statement.setString(2, provider.name)
            statement.setString(3, environment.name)
            statement.executeUpdate()
        }
    }

    private fun audit(connection: Connection, storeId: String, provider: PaymentManagedProvider, environment: PaymentManagedEnvironment, actorId: String, action: String, revision: Long, errorCode: String?) {
        connection.prepareStatement(
            """
            INSERT INTO payment_integration_audit_events
                (id, store_id, provider, environment, actor_subject, action, revision, safe_error_code, created_at)
            VALUES (?, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setString(2, storeId)
            statement.setString(3, provider.name)
            statement.setString(4, environment.name)
            statement.setString(5, actorId.take(256))
            statement.setString(6, action)
            statement.setLong(7, revision)
            statement.setString(8, errorCode?.take(128))
            statement.setTimestamp(9, Timestamp.from(Instant.now()))
            statement.executeUpdate()
        }
    }
}

private data class CredentialTableShape(
    val table: String,
    val id: String?,
    val storeId: String,
    val provider: String,
    val environment: String,
    val name: String,
    val encryptedValue: String,
    val keyVersion: String?,
    val active: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val actor: String?,
    val columnTypes: Map<String, String>,
) {
    companion object {
        fun discover(connection: Connection): CredentialTableShape {
            val table = listOf("payment_provider_credentials", "payment_integration_credentials")
                .firstOrNull { candidate ->
                    connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL").use { statement ->
                        statement.setString(1, candidate)
                        statement.executeQuery().use { it.next() && it.getBoolean(1) }
                    }
                } ?: throw PaymentCredentialSchemaException("Payment credential table is missing")
            val columns = linkedMapOf<String, String>()
            connection.prepareStatement(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?"
            ).use { statement ->
                statement.setString(1, table.substringAfterLast('.'))
                statement.executeQuery().use { rs -> while (rs.next()) columns[rs.getString(1)] = rs.getString(2) }
            }
            fun optional(vararg candidates: String): String? {
                val direct = candidates.firstNotNullOfOrNull { wanted -> columns.keys.firstOrNull { it.equals(wanted, true) } }
                if (direct != null) return direct
                return columns.keys.firstOrNull { column -> candidates.any { wanted -> column.contains(wanted, true) } }
            }
            fun required(vararg candidates: String): String = optional(*candidates)
                ?: throw PaymentCredentialSchemaException("Payment credential table lacks ${candidates.joinToString()}")
            return CredentialTableShape(
                table = table,
                id = optional("id"),
                storeId = required("store_id", "storeid"),
                provider = required("provider"),
                environment = required("environment", "mode"),
                name = required("credential_name", "secret_name", "key_name", "name"),
                encryptedValue = required("encrypted_value", "cipher_text", "ciphertext", "secret_ciphertext"),
                keyVersion = optional("key_version", "encryption_key_version"),
                active = optional("active", "is_active", "enabled"),
                createdAt = optional("created_at", "createdat"),
                updatedAt = optional("updated_at", "updatedat"),
                actor = optional("created_by_user_id", "created_by", "actor_subject"),
                columnTypes = columns,
            )
        }
    }
}
