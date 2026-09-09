package kz.aita.server.auth

import org.junit.Assume.assumeTrue
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import java.util.UUID
import kotlin.test.*

/** Only a separately provisioned aita_test_* PostgreSQL database; every fixture is rolled back. */
class AitaAuthenticatorLoginPolicyMigrationTest {
    private fun sql(c: Connection, text: String) { c.createStatement().use { it.execute(text) } }
    private fun migration(c: Connection, name: String) {
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream("db/migration/$name"))
            .bufferedReader().use { it.readText() }
        sql(c, text)
    }
    private fun scalar(c: Connection, text: String): String? = c.createStatement().use {
        it.executeQuery(text).use { rows -> if (rows.next()) rows.getString(1) else null }
    }
    private fun fixture(block: (Connection, UUID) -> Unit) {
        val url = System.getenv("AITA_AUTH_TEST_DB_URL").orEmpty()
        assumeTrue("Set a dedicated AITA_AUTH_TEST_DB_URL to run PostgreSQL tests", url.isNotBlank())
        require(url.startsWith("jdbc:postgresql:"))
        val properties = Properties().apply {
            System.getenv("AITA_AUTH_TEST_DB_USER")?.let { setProperty("user", it) }
            System.getenv("AITA_AUTH_TEST_DB_PASSWORD")?.let { setProperty("password", it) }
            setProperty("connectTimeout", "5"); setProperty("socketTimeout", "15")
            setProperty("ApplicationName", "aita-totp-policy-migration-test")
        }
        DriverManager.getConnection(url, properties).use { c ->
            require(scalar(c, "SELECT current_database()")?.startsWith("aita_test_") == true) {
                "Refusing to modify a non-test database"
            }
            c.autoCommit = false
            try {
                val schema = "aita_totp_" + UUID.randomUUID().toString().replace("-", "")
                sql(c, "CREATE SCHEMA $schema")
                sql(c, "SET LOCAL search_path TO $schema")
                sql(c, "SET LOCAL statement_timeout = '10s'")
                sql(c, "SET LOCAL lock_timeout = '2s'")
                sql(c, "CREATE TABLE users (id uuid PRIMARY KEY)")
                val id = UUID.randomUUID()
                sql(c, "INSERT INTO users VALUES ('$id')")
                migration(c, "V93__email_password_recovery_and_authenticator_security.sql")
                sql(c, "INSERT INTO auth_security_profiles (user_id, totp_enabled_at_millis, created_at_millis, updated_at_millis) VALUES ('$id', 100, 100, 100)")
                migration(c, "V97__authenticator_login_requirement.sql")
                block(c, id)
            } finally { c.rollback() }
        }
    }
    @Test fun enrolledAccountsRemainLoginProtectedAfterUpgrade() = fixture { c, id ->
        assertEquals("true", scalar(c, "SELECT totp_required_for_login::text FROM auth_security_profiles WHERE user_id='$id'"))
        assertEquals("100", scalar(c, "SELECT totp_enabled_at_millis FROM auth_security_profiles WHERE user_id='$id'"))
    }
    @Test fun optionalLoginDoesNotRemoveAuthenticatorEnrollment() = fixture { c, id ->
        sql(c, "UPDATE auth_security_profiles SET totp_required_for_login=false WHERE user_id='$id'")
        assertEquals("false", scalar(c, "SELECT totp_required_for_login::text FROM auth_security_profiles WHERE user_id='$id'"))
        assertEquals("100", scalar(c, "SELECT totp_enabled_at_millis FROM auth_security_profiles WHERE user_id='$id'"))
        sql(c, "UPDATE auth_security_profiles SET totp_required_for_login=true WHERE user_id='$id'")
        assertEquals("true", scalar(c, "SELECT totp_required_for_login::text FROM auth_security_profiles WHERE user_id='$id'"))
    }
    @Test fun loginPolicyCannotBeNull() = fixture { c, id ->
        val savepoint = c.setSavepoint()
        val failure = assertFailsWith<SQLException> {
            sql(c, "UPDATE auth_security_profiles SET totp_required_for_login=NULL WHERE user_id='$id'")
        }
        assertEquals("23502", failure.sqlState)
        c.rollback(savepoint)
        assertEquals("true", scalar(c, "SELECT totp_required_for_login::text FROM auth_security_profiles WHERE user_id='$id'"))
    }
}
