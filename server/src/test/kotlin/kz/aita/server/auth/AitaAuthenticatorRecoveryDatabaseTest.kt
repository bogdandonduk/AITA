package kz.aita.server.auth

import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kz.aita.auth.*
import kz.aita.server.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.time.Instant
import java.util.*
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

/** Opt-in REAL PostgreSQL + TokenService tests. No network/email worker is started.
 * A random schema is created/dropped only after current_database() starts with aita_test_.
 * Every connection uses that schema. Tests must run serially (Exposed's default database).
 */
class AitaAuthenticatorRecoveryDatabaseTest {
    private val password = "Aita-test-password-42!"
    private val mainEmail = "main@example.test"
    private val extraEmail = "extra@example.test"
    private val mainPhone = "+77771234567"
    private val extraPhone = "+79991234567"
    private val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private val config = AdvancedAuthConfig.load(readValue = { key -> mapOf(
        "AITA_RESEND_API_KEY" to "local-test-not-a-provider-key",
        "AITA_AUTH_EMAIL_FROM" to "AITA Test <security@example.test>"
    )[key] }, environmentName = "test")
    private val tokens = TokenService(JwtConfig("aita-test", "aita-test", "aita-test", "test-only-signing-secret-".repeat(4), 900_000L, 86_400_000L))
    private val device = mapOf("deviceId" to "test-device", "installationId" to "test-installation", "ip" to "192.0.2.20")

    private inner class Fixture(val db: Database, val service: AitaAdvancedAuthService) {
        fun <T> sql(block: Transaction.() -> T): T = transaction(db = db) { maxAttempts = 1; block() }
        fun user(phone: String = mainPhone, alias: Boolean = false, enrolled: Boolean = false, required: Boolean = true): UUID = sql {
            val userId = UUID.randomUUID()
            Users.insert {
                it[id] = userId; it[publicId] = UUID.randomUUID().toString().take(12)
                it[phoneNumber] = phone; it[email] = mainEmail; it[firstName] = "Test"; it[lastName] = "Account"
                it[countryLocale] = "kz"; it[passwordHash] = Pw.hash(password.toCharArray())
            }
            if (alias || enrolled) {
                val now = System.currentTimeMillis()
                AuthSecurityProfiles.insert {
                    it[AuthSecurityProfiles.userId] = userId
                    it[createdAtMillis] = now; it[updatedAtMillis] = now
                    if (alias) { it[phoneLoginAlias] = extraPhone; it[phoneAliasVerifiedAtMillis] = now }
                    if (enrolled) { it[totpSecretCiphertext] = encrypt("totp-active:$userId", secret); it[totpEnabledAtMillis] = now }
                    it[totpRequiredForLogin] = required
                }
            }
            userId
        }
        fun email(flowId: String): JsonObject = sql {
            val challenge = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq UUID.fromString(flowId) }.single()
            val outbox = AuthEmailOutbox.selectAll().where { AuthEmailOutbox.challengeId eq challenge[AuthOneTimeChallenges.id] }.single()
            Json.parseToJsonElement(decrypt("auth-email:${outbox[AuthEmailOutbox.id]}", requireNotNull(outbox[AuthEmailOutbox.payloadCiphertext]))).jsonObject
        }
        fun code(flowId: String): String = Regex("(?<![0-9])[0-9]{6}(?![0-9])")
            .find(requireNotNull(email(flowId)["text"]).jsonPrimitive.content)?.value ?: error("No OTP in email fixture")
        suspend fun recovery(): AitaAuthFlowDataModel = service.requestAuthenticatorRecovery(
            AitaAuthenticatorRecoveryRequestDataModel(mainPhone, password), device.getValue("ip"))
    }

    private fun fixture(block: suspend Fixture.() -> Unit) {
        val url = System.getenv("AITA_AUTH_TEST_DB_URL").orEmpty()
        assumeTrue("Set AITA_AUTH_TEST_DB_URL to a dedicated aita_test_* PostgreSQL database", url.isNotBlank())
        require(url.startsWith("jdbc:postgresql:"))
        val props = Properties().apply {
            System.getenv("AITA_AUTH_TEST_DB_USER")?.let { setProperty("user", it) }
            System.getenv("AITA_AUTH_TEST_DB_PASSWORD")?.let { setProperty("password", it) }
            setProperty("connectTimeout", "5"); setProperty("socketTimeout", "20")
            setProperty("ApplicationName", "aita-auth-recovery-test")
        }
        DriverManager.getConnection(url, props).use { conn ->
            val name = conn.createStatement().use { st -> st.executeQuery("SELECT current_database()").use { rs -> rs.next(); rs.getString(1) } }
            require(name.startsWith("aita_test_")) { "Refusing to change a non-test database" }
        }
        synchronized(databaseTestLock) {
            val schema = "aita_auth_test_" + UUID.randomUUID().toString().replace("-", "")
            DriverManager.getConnection(url, props).use { it.createStatement().use { st -> st.execute("CREATE SCHEMA $schema") } }
            val isolated = Properties().apply { putAll(props); setProperty("currentSchema", schema) }
            val oldDefault = TransactionManager.defaultDatabase
            val db = Database.connect(getNewConnection = { DriverManager.getConnection(url, isolated) })
            TransactionManager.defaultDatabase = db
            try {
                transaction(db = db) {
                    SchemaUtils.create(Users, RefreshSessions, SecuritySessionEvents)
                    // Reproduce pre-V98 session shape, then execute the actual additive migrations.
                    exec("ALTER TABLE refresh_sessions DROP COLUMN security_invalidated")
                    listOf("V93__email_password_recovery_and_authenticator_security.sql",
                        "V94__authenticated_phone_login_alias_challenges.sql", "V95__authentication_email_delivery_snapshots.sql",
                        "V96__verified_additional_login_emails.sql", "V97__authenticator_login_requirement.sql",
                        "V98__authenticator_sign_in_and_email_recovery.sql").forEach { name ->
                        val sql = requireNotNull(javaClass.getResourceAsStream("/db/migration/$name")).bufferedReader().use { it.readText() }
                        exec(sql)
                    }
                }
                testApplication {
                    lateinit var service: AitaAdvancedAuthService
                    application { service = AitaAdvancedAuthService(tokens, config, this) }
                    startApplication()
                    block(Fixture(db, service))
                }
            } finally {
                TransactionManager.defaultDatabase = oldDefault
                TransactionManager.closeAndUnregister(db)
                DriverManager.getConnection(url, props).use { it.createStatement().use { st -> st.execute("DROP SCHEMA $schema CASCADE") } }
            }
        }
    }

    @Test fun mainPhoneWithNoExtraProfileQueuesCodeToMainEmail() = fixture {
        user()
        val flow = service.requestEmailCode(mainPhone, "PASSWORDLESS_LOGIN", "en", "192.0.2.1")
        assertEquals(mainEmail, email(flow.flowId)["to"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(AitaAuthNextStep.AUTHENTICATED, service.verifyEmailCode(AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId)), "PASSWORDLESS_LOGIN", device)?.nextStep)
    }

    @Test fun nationalMainAndDifferentExtraPhoneBothUseMainEmail() = fixture {
        user(phone = "7771234567", alias = true)
        for ((n, phone) in listOf(mainPhone, extraPhone).withIndex()) {
            val flow = service.requestEmailCode(phone, "PASSWORDLESS_LOGIN", "en", "192.0.2.${n + 2}")
            assertEquals(mainEmail, email(flow.flowId)["to"]!!.jsonArray.single().jsonPrimitive.content)
        }
    }

    @Test fun authenticatorFirstHonorsMandatoryPasswordAndSingleUseProof() = fixture {
        user(enrolled = true)
        val flow = assertNotNull(service.authenticatorLogin(AitaAuthenticatorLoginRequestDataModel(mainPhone, totp()), device))
        assertEquals(AitaAuthNextStep.PASSWORD_CONFIRMATION, flow.nextStep)
        assertNull(flow.tokenPair)
        assertNull(service.completeTotpLogin(AitaTotpLoginRequestDataModel(flow.flowId, totp()), device))
        assertNull(service.completeAuthenticatorPassword(AitaAuthenticatorPasswordRequestDataModel(flow.flowId, "wrong"), device))
        assertEquals(AitaAuthNextStep.AUTHENTICATED, service.completeAuthenticatorPassword(AitaAuthenticatorPasswordRequestDataModel(flow.flowId, password), device)?.nextStep)
        assertNull(service.completeAuthenticatorPassword(AitaAuthenticatorPasswordRequestDataModel(flow.flowId, password), device))
    }

    @Test fun optionalPolicyAllowsAuthenticatorAloneButNotAReplayedCode() = fixture {
        user(enrolled = true, required = false)
        val code = totp()
        assertEquals(AitaAuthNextStep.AUTHENTICATED, service.authenticatorLogin(AitaAuthenticatorLoginRequestDataModel(mainPhone, code), device)?.nextStep)
        assertNull(service.authenticatorLogin(AitaAuthenticatorLoginRequestDataModel(mainPhone, code), device))
    }

    @Test fun badRecoveryPasswordIsNeutralAndQueuesNoEmail() = fixture {
        user(enrolled = true)
        val flow = service.requestAuthenticatorRecovery(AitaAuthenticatorRecoveryRequestDataModel(mainPhone, "wrong"), "192.0.2.5")
        assertEquals(AitaAuthNextStep.EMAIL_CODE, flow.nextStep)
        assertTrue(flow.maskedDestination.isEmpty()); assertTrue(flow.recoveredUserId.isEmpty())
        assertEquals(0L, sql { AuthEmailOutbox.selectAll().count() })
        assertNull(service.confirmAuthenticatorRecovery(AitaEmailCodeVerifyRequestDataModel(flow.flowId, "000000")))
    }

    @Test fun recoveryFromExtraEmailStillSendsOnlyToMainEmail() = fixture {
        val owner = user(enrolled = true)
        sql { AuthLoginEmails.insert { it[emailNormalized] = extraEmail; it[userId] = owner; it[isPrimary] = false; it[verifiedAtMillis] = 1L; it[createdAtMillis] = 1L } }
        val flow = service.requestAuthenticatorRecovery(AitaAuthenticatorRecoveryRequestDataModel(extraEmail, password), "192.0.2.6")
        assertEquals(mainEmail, email(flow.flowId)["to"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test fun resetRevokesAllSessionsOldFactorsPendingProofsAndSendsNotice() = fixture {
        val owner = user(enrolled = true)
        val old = tokens.newPair(owner, device)
        val current = tokens.newPair(owner, device) // same-device replacement gives an already-revoked old token
        val login = assertNotNull(service.passwordLogin(AitaPasswordLoginRequestDataModel(mainPhone, password), device))
        sql {
            AuthRecoveryCodes.insert { it[id] = UUID.randomUUID(); it[userId] = owner; it[codeHash] = hmac("recovery:$owner", "ABCDEFABCDEF"); it[createdAtMillis] = 1L }
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq owner }) {
                it[totpPendingSecretCiphertext] = "pending"; it[totpPendingSetupId] = UUID.randomUUID(); it[totpPendingExpiresAtMillis] = System.currentTimeMillis() + 60_000
            }
        }
        val flow = recovery(); val code = code(flow.flowId)
        assertEquals(owner, service.confirmAuthenticatorRecovery(AitaEmailCodeVerifyRequestDataModel(flow.flowId, code)))
        assertNull(service.confirmAuthenticatorRecovery(AitaEmailCodeVerifyRequestDataModel(flow.flowId, code)))
        val settings = service.settings(owner)
        assertFalse(settings.authenticatorEnabled); assertEquals(0, settings.recoveryCodesRemaining)
        assertNull(service.completeTotpLogin(AitaTotpLoginRequestDataModel(login.flowId, totp()), device))
        sql {
            assertTrue(RefreshSessions.selectAll().all { it[RefreshSessions.securityInvalidated] && it[RefreshSessions.revokedAt] != null })
            assertNull(AuthSecurityProfiles.selectAll().single()[AuthSecurityProfiles.totpPendingSecretCiphertext])
            assertEquals(1L, AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.purpose eq "TOTP_RESET_NOTICE" }.count())
        }
        // A later legitimate sign-in must never allow refresh-recovery to revive a revoked pre-reset token.
        tokens.newPair(owner, device)
        assertFailsWith<IllegalAccessException> { tokens.rotate(old.refreshToken, device) }
        assertFailsWith<IllegalAccessException> { tokens.rotate(current.refreshToken, device) }
    }

    @Test fun ordinaryEmailLoginStillRequiresAuthenticatorAndCannotResetIt() = fixture {
        user(enrolled = true)
        val flow = service.requestEmailCode(mainPhone, "PASSWORDLESS_LOGIN", "en", "192.0.2.8")
        val input = AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId))
        assertNull(service.confirmAuthenticatorRecovery(input))
        assertEquals(AitaAuthNextStep.TOTP, service.verifyEmailCode(input, "PASSWORDLESS_LOGIN", device)?.nextStep)
    }

    @Test fun recoveryProofExpiresAndEmailChangeInvalidatesIt() = fixture {
        val owner = user(enrolled = true)
        val flow = recovery(); val code = code(flow.flowId)
        sql { Users.update({ Users.id eq owner }) { it[email] = "changed@example.test" } }
        assertNull(service.confirmAuthenticatorRecovery(AitaEmailCodeVerifyRequestDataModel(flow.flowId, code)))
        assertTrue(service.settings(owner).authenticatorEnabled)
        val fresh = recovery(); val freshCode = code(fresh.flowId)
        sql { AuthOneTimeChallenges.update({ AuthOneTimeChallenges.publicId eq UUID.fromString(fresh.flowId) }) { it[expiresAtMillis] = 1L } }
        assertNull(service.confirmAuthenticatorRecovery(AitaEmailCodeVerifyRequestDataModel(fresh.flowId, freshCode)))
    }

    @Test fun wrongEmailCodeExhaustsOnlyThisChallenge() = fixture {
        val owner = user(enrolled = true)
        val flow = recovery(); val correct = code(flow.flowId)
        val wrong = if (correct == "000000") "111111" else "000000"
        repeat(config.maxAttempts) { assertNull(service.confirmAuthenticatorRecovery(AitaEmailCodeVerifyRequestDataModel(flow.flowId, wrong))) }
        assertNull(service.confirmAuthenticatorRecovery(AitaEmailCodeVerifyRequestDataModel(flow.flowId, correct)))
        assertTrue(service.settings(owner).authenticatorEnabled)
    }

    @Test fun resendCannotRefreshAnOldPasswordProof() = fixture {
        user(enrolled = true)
        val flow = recovery()
        sql {
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.publicId eq UUID.fromString(flow.flowId) }) { it[resendAfterMillis] = 1L }
            AuthTotpRecoveryChallenges.update({ AuthTotpRecoveryChallenges.challengePublicId eq UUID.fromString(flow.flowId) }) { it[createdAtMillis] = 1L }
        }
        assertNull(service.resend(flow.flowId, "en", "192.0.2.9", "TOTP_RECOVERY"))
    }

    @Test fun recoveryCodeCanReplaceTotpButIsNeverReused() = fixture {
        val owner = user(enrolled = true, required = false)
        sql { AuthRecoveryCodes.insert { it[id] = UUID.randomUUID(); it[userId] = owner; it[codeHash] = hmac("recovery:$owner", "ABCDEFABCDEF"); it[createdAtMillis] = 1L } }
        val request = AitaAuthenticatorLoginRequestDataModel(mainPhone, "ABCDEF-ABCDEF")
        assertEquals(AitaAuthNextStep.AUTHENTICATED, service.authenticatorLogin(request, device)?.nextStep)
        assertNull(service.authenticatorLogin(request, device))
    }

    private fun totp(): String {
        val mac = Mac.getInstance("HmacSHA1"); mac.init(SecretKeySpec("12345678901234567890".toByteArray(), "HmacSHA1"))
        val step = Instant.now().toEpochMilli() / 30_000
        val digest = mac.doFinal(ByteArray(8) { (step ushr ((7 - it) * 8)).toByte() })
        val offset = digest.last().toInt() and 15
        val number = ((digest[offset].toInt() and 127) shl 24) or ((digest[offset + 1].toInt() and 255) shl 16) or
            ((digest[offset + 2].toInt() and 255) shl 8) or (digest[offset + 3].toInt() and 255)
        return (number % 1_000_000).toString().padStart(6, '0')
    }
    private fun hmac(context: String, text: String): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(config.codePepper, "HmacSHA256")); doFinal("$context\u0000$text".toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private fun encrypt(context: String, text: String): String {
        val iv = ByteArray(12).also(java.security.SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(config.encryptionKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(context.toByteArray())
        return "v1.${Base64.getUrlEncoder().withoutPadding().encodeToString(iv)}.${Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(text.toByteArray()))}"
    }
    private fun decrypt(context: String, value: String): String {
        val parts = value.split('.')
        require(parts.size == 3 && parts[0] == "v1")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(config.encryptionKey, "AES"), GCMParameterSpec(128, Base64.getUrlDecoder().decode(parts[1])))
        cipher.updateAAD(context.toByteArray())
        return cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])).toString(Charsets.UTF_8)
    }
    companion object { private val databaseTestLock = Any() }
}
