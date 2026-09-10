package kz.aita.server.auth

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.Properties
import java.util.UUID
import kotlin.test.*

/** Exercises the actual Exposed/PostgreSQL lookup, not a mock of its SQL functions.
 * Opt in with AITA_AUTH_TEST_DB_URL pointing to a dedicated database named aita_test_*.
 * Uses a random schema and rolls the complete fixture back; never reads production settings.
 */
class AitaPhoneLoginIdentityDatabaseTest {
    private fun fixture(block: Transaction.() -> Unit) {
        val url = System.getenv("AITA_AUTH_TEST_DB_URL").orEmpty()
        assumeTrue("Set a dedicated AITA_AUTH_TEST_DB_URL to run PostgreSQL tests", url.isNotBlank())
        require(url.startsWith("jdbc:postgresql:"))
        val properties = Properties().apply {
            System.getenv("AITA_AUTH_TEST_DB_USER")?.let { setProperty("user", it) }
            System.getenv("AITA_AUTH_TEST_DB_PASSWORD")?.let { setProperty("password", it) }
            setProperty("connectTimeout", "5")
            setProperty("socketTimeout", "15")
            setProperty("ApplicationName", "aita-phone-identity-test")
        }
        val database = Database.connect(getNewConnection = { DriverManager.getConnection(url, properties) })
        try {
            transaction(db = database) {
                maxAttempts = 1
                val name = exec("SELECT current_database()") { rs -> rs.next(); rs.getString(1) }
                require(name?.startsWith("aita_test_") == true) { "Refusing to modify a non-test database" }
                val schema = "aita_phone_" + UUID.randomUUID().toString().replace("-", "")
                try {
                    exec("CREATE SCHEMA $schema")
                    exec("SET LOCAL search_path TO $schema")
                    exec("SET LOCAL statement_timeout = '10s'")
                    exec("SET LOCAL lock_timeout = '2s'")
                    exec("CREATE TABLE users (id uuid PRIMARY KEY, phone_number varchar(32) NOT NULL, country_locale varchar(64) NOT NULL DEFAULT 'kz')")
                    exec("""CREATE TABLE auth_security_profiles (
                        user_id uuid PRIMARY KEY, phone_login_alias varchar(32), phone_alias_verified_at_millis bigint)""")
                    block()
                } finally { rollback() }
            }
        } finally { TransactionManager.closeAndUnregister(database) }
    }

    private fun primary(phone: String, owner: UUID = UUID.randomUUID()): UUID = owner.also {
        TransactionManager.current().exec("INSERT INTO users (id, phone_number) VALUES (?, ?)",
            listOf(UUIDColumnType() to owner, VarCharColumnType(32) to phone))
    }
    private fun extra(owner: UUID, phone: String, verified: Boolean = true) {
        TransactionManager.current().exec(
            "INSERT INTO auth_security_profiles (user_id, phone_login_alias, phone_alias_verified_at_millis) VALUES (?, ?, ?)",
            listOf(UUIDColumnType() to owner, VarCharColumnType(32) to phone, LongColumnType() to if (verified) 100L else null))
    }

    @Test fun displayFormattedPrimaryWorksForCanonicalPhoneLogin() = fixture {
        val owner = primary("+7 (777) 123-45-67")
        assertEquals(owner, resolvePhoneLoginUserInside("77771234567"))
        assertEquals(owner, resolvePhoneLoginUserInside("8 777 123 45 67"))
        assertFalse(phoneLoginIdentityHasOtherOwnerInside("+77771234567", owner))
        assertTrue(phoneLoginIdentityHasOtherOwnerInside("+77771234567", UUID.randomUUID()))
    }

    @Test fun mainPhoneWorksWithoutAnExtraPhoneOrSecurityProfile() = fixture {
        val owner = primary("77771234567")
        assertEquals(owner, resolvePhoneLoginUserInside("+77771234567"))
    }

    @Test fun legacyNationalMainPhoneUsesOnlyItsOwnCountry() = fixture {
        val owner = primary("7771234567")
        assertEquals(owner, resolvePhoneLoginUserInside("+77771234567"))
        TransactionManager.current().exec("UPDATE users SET country_locale='tj' WHERE id='$owner'")
        assertNull(resolvePhoneLoginUserInside("+77771234567"))
    }

    @Test fun nationalMainPhoneCannotHideInternationalCollision() = fixture {
        primary("7771234567")
        primary("77771234567")
        assertNull(resolvePhoneLoginUserInside("+77771234567"))
    }

    @Test fun exactMatchCannotHideAnotherFormattedOwner() = fixture {
        primary("77771234567")
        primary("+7 (777) 123-45-67")
        assertNull(resolvePhoneLoginUserInside("+77771234567"))
    }

    @Test fun verifiedExtraPhoneAndPrimaryNamespacesMustAgree() = fixture {
        val owner = primary("77771234567")
        extra(owner, "8 (777) 123-45-67")
        assertEquals(owner, resolvePhoneLoginUserInside("+77771234567"))
        val other = primary("79991234567")
        extra(other, "+7 777 123 45 67")
        assertNull(resolvePhoneLoginUserInside("+77771234567"))
    }

    @Test fun unverifiedExtraPhoneCannotLogInButRemainsReserved() = fixture {
        val owner = primary("79991234567")
        extra(owner, "+7 (777) 123-45-67", verified = false)
        assertNull(resolvePhoneLoginUserInside("+77771234567"))
        assertTrue(phoneLoginIdentityHasOtherOwnerInside("+77771234567"))
        assertFalse(phoneLoginIdentityHasOtherOwnerInside("+77771234567", owner))
    }

    @Test fun fullwidthAndNonbreakingSpaceStoredPhoneUsesSameNormalization() = fixture {
        val owner = primary("+７\u00a0７７７\u202f１２３-４５-６７")
        assertEquals(owner, resolvePhoneLoginUserInside("+77771234567"))
    }

    @Test fun explicitPlusEightAndMalformedStoredPhonesAreNeverTrunkAliases() = fixture {
        primary("+87771234567")
        primary("++77771234567")
        primary("7+7771234567")
        primary("7abc7771234567")
        assertNull(resolvePhoneLoginUserInside("+77771234567"))
        assertNull(resolvePhoneLoginUserInside("not a phone"))
    }
}
