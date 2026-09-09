package kz.aita.server.auth

import org.junit.Assume.assumeTrue
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import java.util.UUID
import kotlin.test.*

/** Opt-in PostgreSQL integration tests. They NEVER use AITA's production connection settings.
 * AITA_AUTH_TEST_DB_URL must point to a separately provisioned database named aita_test_*.
 * Every fixture, including its random schema, is rolled back, even when assertions fail.
 */
class AitaAdditionalEmailMigrationTest {
    private fun migration(connection: Connection, name: String) {
        val script = requireNotNull(javaClass.classLoader.getResourceAsStream("db/migration/$name"))
            .bufferedReader().use { it.readText() }
        connection.createStatement().use { it.execute(script) }
    }

    private fun fixture(
        primaries: List<String> = listOf("Primary@Example.COM", "other@example.com"),
        applyV96: Boolean = true,
        block: (Connection, List<UUID>) -> Unit
    ) {
        val url = System.getenv("AITA_AUTH_TEST_DB_URL").orEmpty()
        assumeTrue("Set a dedicated AITA_AUTH_TEST_DB_URL to run PostgreSQL tests", url.isNotBlank())
        require(url.startsWith("jdbc:postgresql:")) { "Only a dedicated PostgreSQL test database is supported" }
        val properties = Properties().apply {
            System.getenv("AITA_AUTH_TEST_DB_USER")?.let { setProperty("user", it) }
            System.getenv("AITA_AUTH_TEST_DB_PASSWORD")?.let { setProperty("password", it) }
            setProperty("connectTimeout", "5")
            setProperty("socketTimeout", "15")
            setProperty("ApplicationName", "aita-additional-email-migration-test")
        }
        DriverManager.getConnection(url, properties).use { connection ->
            val database = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT current_database()").use { result -> result.next(); result.getString(1) }
            }
            require(database.startsWith("aita_test_")) { "Refusing to modify a non-test database" }
            connection.autoCommit = false
            try {
                val schema = "aita_email_" + UUID.randomUUID().toString().replace("-", "")
                connection.createStatement().use { statement ->
                    statement.execute("CREATE SCHEMA $schema")
                    statement.execute("SET LOCAL search_path TO $schema")
                    statement.execute("SET LOCAL statement_timeout = '10s'")
                    statement.execute("SET LOCAL lock_timeout = '2s'")
                    statement.execute("CREATE TABLE users (id uuid PRIMARY KEY, email varchar(254) NOT NULL)")
                }
                val ids = primaries.map { email ->
                    UUID.randomUUID().also { id ->
                        connection.prepareStatement("INSERT INTO users VALUES (?, ?)").use {
                            it.setObject(1, id); it.setString(2, email); it.executeUpdate()
                        }
                    }
                }
                migration(connection, "V93__email_password_recovery_and_authenticator_security.sql")
                migration(connection, "V94__authenticated_phone_login_alias_challenges.sql")
                migration(connection, "V95__authentication_email_delivery_snapshots.sql")
                if (applyV96) migration(connection, "V96__verified_additional_login_emails.sql")
                block(connection, ids)
            } finally { connection.rollback() }
        }
    }

    private fun scalar(c: Connection, sql: String): String? = c.createStatement().use {
        it.executeQuery(sql).use { rows -> if (rows.next()) rows.getString(1) else null }
    }
    private fun exec(c: Connection, sql: String) { c.createStatement().use { it.execute(sql) } }
    private fun rejects(c: Connection, sqlState: String, block: () -> Unit) {
        val savepoint = c.setSavepoint()
        val error = assertFailsWith<SQLException>(block = block)
        assertEquals(sqlState, error.sqlState)
        c.rollback(savepoint)
    }
    private fun alias(c: Connection, owner: UUID, address: String, verified: String = "123") = exec(c,
        "INSERT INTO auth_login_emails VALUES ('$address', '$owner', false, $verified, 123)")

    @Test fun existingPrimaryIdentityIsNormalizedAndRetained() = fixture { c, ids ->
        assertEquals(ids.first().toString(), scalar(c, "SELECT user_id FROM auth_login_emails WHERE email_normalized='primary@example.com' AND is_primary"))
        assertEquals("2", scalar(c, "SELECT count(*) FROM auth_login_emails"))
    }

    @Test fun pendingAliasCannotEnterLoginRegistry() = fixture { c, ids ->
        rejects(c, "23514") { alias(c, ids.first(), "unverified@example.com", "NULL") }
        assertEquals("0", scalar(c, "SELECT count(*) FROM auth_login_emails WHERE NOT is_primary"))
    }

    @Test fun primaryAndAliasShareOneGlobalUniqueNamespace() = fixture { c, ids ->
        rejects(c, "23505") { alias(c, ids.first(), "other@example.com") }
        alias(c, ids.first(), "alias@example.com")
        rejects(c, "23505") { alias(c, ids.last(), "alias@example.com") }
        rejects(c, "23505") { exec(c, "INSERT INTO users VALUES ('${UUID.randomUUID()}', 'ALIAS@EXAMPLE.COM')") }
        assertEquals(ids.first().toString(), scalar(c, "SELECT user_id FROM auth_login_emails WHERE email_normalized='alias@example.com'"))
    }

    @Test fun conflictingPrimaryUpdateRollsBackWithoutLosingOldIdentity() = fixture { c, ids ->
        alias(c, ids.first(), "alias@example.com")
        rejects(c, "23505") { exec(c, "UPDATE users SET email='alias@example.com' WHERE id='${ids.last()}'") }
        assertEquals("other@example.com", scalar(c, "SELECT email FROM users WHERE id='${ids.last()}'"))
        assertEquals(ids.last().toString(), scalar(c, "SELECT user_id FROM auth_login_emails WHERE email_normalized='other@example.com'"))
    }

    @Test fun primaryUpdateRebindsIdentityAndInvalidatesSecurityRevision() = fixture { c, ids ->
        val id = ids.first()
        exec(c, "INSERT INTO auth_security_profiles(user_id, email_verified_at_millis, created_at_millis, updated_at_millis) VALUES ('$id', 100, 100, 100)")
        alias(c, id, "alias@example.com")
        exec(c, "UPDATE users SET email='ALIAS@example.com' WHERE id='$id'")
        assertEquals("true", scalar(c, "SELECT is_primary::text FROM auth_login_emails WHERE email_normalized='alias@example.com'"))
        assertNull(scalar(c, "SELECT email_normalized FROM auth_login_emails WHERE email_normalized='primary@example.com'"))
        assertEquals("2", scalar(c, "SELECT security_revision FROM auth_security_profiles WHERE user_id='$id'"))
        assertNull(scalar(c, "SELECT email_verified_at_millis FROM auth_security_profiles WHERE user_id='$id'"))
    }

    @Test fun emailAliasPurposeAndPendingBindingAreAcceptedWithoutLoginIdentity() = fixture { c, ids ->
        val id = UUID.randomUUID(); val publicId = UUID.randomUUID()
        exec(c, """INSERT INTO auth_one_time_challenges
            (id, public_id, user_id, purpose, identifier_hash, request_ip_hash, code_hash, code_ciphertext,
             expires_at_millis, resend_after_millis, created_at_millis, updated_at_millis)
            VALUES ('$id', '$publicId', '${ids.first()}', 'EMAIL_ALIAS', '${"a".repeat(64)}', '${"b".repeat(64)}',
                    '${"c".repeat(64)}', 'dummy-encrypted', 999999, 100, 100, 100)""")
        exec(c, "INSERT INTO auth_email_alias_challenges VALUES ('$publicId', '${ids.first()}', 'pending@example.com', '${"d".repeat(64)}', NULL, 100)")
        assertEquals("0", scalar(c, "SELECT count(*) FROM auth_login_emails WHERE email_normalized='pending@example.com'"))
        rejects(c, "23514") { exec(c, "UPDATE auth_one_time_challenges SET purpose='UNRECOGNIZED' WHERE id='$id'") }
        exec(c, "DELETE FROM auth_one_time_challenges WHERE id='$id'")
        assertEquals("0", scalar(c, "SELECT count(*) FROM auth_email_alias_challenges"))
    }

    @Test fun legacyNormalizedCollisionStopsMigrationInsteadOfChoosingAccount() =
        fixture(listOf("same@example.com", "SAME@example.com"), applyV96 = false) { c, _ ->
            rejects(c, "P0001") { migration(c, "V96__verified_additional_login_emails.sql") }
            assertEquals("2", scalar(c, "SELECT count(*) FROM users"))
        }

    @Test fun deletingAccountRemovesItsLoginIdentities() = fixture { c, ids ->
        alias(c, ids.first(), "alias@example.com")
        exec(c, "DELETE FROM users WHERE id='${ids.first()}'")
        assertEquals("1", scalar(c, "SELECT count(*) FROM auth_login_emails"))
    }
}
