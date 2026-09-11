package kz.aita.server.auth

import io.ktor.server.testing.testApplication
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kz.aita.auth.*
import kz.aita.server.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.exceptions.ExposedSQLException
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
        fun user(phone: String = mainPhone, alias: Boolean = false, enrolled: Boolean = false, required: Boolean = true, mail: String = mainEmail): UUID = sql {
            val userId = UUID.randomUUID()
            Users.insert {
                it[id] = userId; it[publicId] = UUID.randomUUID().toString().take(12)
                it[phoneNumber] = phone; it[email] = mail; it[firstName] = "Test"; it[lastName] = "Account"
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
        suspend fun emailPolicy(id: UUID) {
            service.settings(id) // Materialize a missing security profile.
            sql { AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq id }) { it[emailRequiredForLogin] = true; it[totpRequiredForLogin] = false } }
        }
        fun addExtra(id: UUID, address: String = extraEmail) = sql {
            AuthLoginEmails.insert {
                it[emailNormalized] = address; it[userId] = id; it[isPrimary] = false
                it[verifiedAtMillis] = System.currentTimeMillis(); it[createdAtMillis] = System.currentTimeMillis()
            }
        }
        suspend fun proof(id: UUID, action: AitaSecurityEmailAction, target: String): AitaSecurityEmailProof {
            val flow = assertNotNull(service.requestSecurityEmail(id, AitaSecurityEmailRequest(action, target, password,
                service.settings(id).securityRevision), device.getValue("ip")))
            return AitaSecurityEmailProof(flow.flowId, code(flow.flowId))
        }
        suspend fun emailLogin(destination: AitaEmailDestination = AitaEmailDestination.MAIN): AitaAuthFlowDataModel {
            val parent = assertNotNull(service.passwordLogin(AitaPasswordLoginRequestDataModel(mainPhone, password), device))
            assertEquals(AitaAuthNextStep.EMAIL_DESTINATION, parent.nextStep)
            assertNull(parent.tokenPair)
            return assertNotNull(service.requestLoginEmailFactor(AitaLoginEmailFactorRequest(parent.flowId, destination), device.getValue("ip")))
        }
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
                        "V98__authenticator_sign_in_and_email_recovery.sql",
                        "V99__email_second_factor_and_single_extra_email.sql").forEach { name ->
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
    @Test fun passwordAndMainEmailCompleteOnce() = fixture {
        val id = user(); emailPolicy(id)
        val flow = emailLogin()
        val request = AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId))
        assertNotNull(service.verifyLoginEmailFactor(request, device)?.tokenPair)
        assertNull(service.verifyLoginEmailFactor(request, device))
    }

    @Test fun passwordAndExtraEmailUseThatVerifiedDestination() = fixture {
        val id = user(); emailPolicy(id); addExtra(id)
        val flow = emailLogin(AitaEmailDestination.EXTRA)
        assertEquals(extraEmail, email(flow.flowId)["to"]!!.jsonArray.single().jsonPrimitive.content)
        assertNotNull(service.verifyLoginEmailFactor(AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId)), device)?.tokenPair)
    }

    @Test fun anonymousPhoneChoiceHasNoAccountSpecificDisclosure() = fixture {
        val id = user(); addExtra(id)
        val flow = service.requestEmailCode(mainPhone, "PASSWORDLESS_LOGIN", "en", "192.0.2.7", AitaEmailDestination.EXTRA)
        assertTrue(flow.maskedDestination.isBlank()); assertTrue(flow.emailDestinations.isEmpty())
        assertEquals(extraEmail, email(flow.flowId)["to"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test fun missingExtraDoesNotSendToMainOrRevealItsAbsence() = fixture {
        user()
        val flow = service.requestEmailCode(mainPhone, "PASSWORDLESS_LOGIN", "en", "192.0.2.7", AitaEmailDestination.EXTRA)
        assertEquals(AitaAuthNextStep.EMAIL_CODE, flow.nextStep)
        assertTrue(flow.maskedDestination.isBlank()); assertTrue(flow.emailDestinations.isEmpty())
        assertEquals(0L, sql { AuthEmailOutbox.selectAll().count() })
    }

    @Test fun emailFirstStillRequiresPasswordWhenEmailPolicyIsOn() = fixture {
        val id = user(); emailPolicy(id)
        val first = service.requestEmailCode(mainPhone, "PASSWORDLESS_LOGIN", "en", "192.0.2.7")
        val next = assertNotNull(service.verifyEmailCode(AitaEmailCodeVerifyRequestDataModel(first.flowId, code(first.flowId)), "PASSWORDLESS_LOGIN", device))
        assertEquals(AitaAuthNextStep.PASSWORD_CONFIRMATION, next.nextStep); assertNull(next.tokenPair)
        assertNull(service.completeAuthenticatorPassword(AitaAuthenticatorPasswordRequestDataModel(next.flowId, "wrong"), device))
        assertNotNull(service.completeAuthenticatorPassword(AitaAuthenticatorPasswordRequestDataModel(next.flowId, password), device)?.tokenPair)
    }

    @Test fun emailChallengeCannotSkipMandatoryAuthenticatorOrBeConsumedByWrongEndpoint() = fixture {
        user(enrolled = true)
        val first = service.requestEmailCode(mainPhone, "PASSWORDLESS_LOGIN", "en", "192.0.2.7")
        val next = assertNotNull(service.verifyEmailCode(AitaEmailCodeVerifyRequestDataModel(first.flowId, code(first.flowId)), "PASSWORDLESS_LOGIN", device))
        assertEquals(AitaAuthNextStep.TOTP, next.nextStep)
        assertNull(service.completeAuthenticatorPassword(AitaAuthenticatorPasswordRequestDataModel(next.flowId, password), device))
        assertNotNull(service.completeTotpLogin(AitaTotpLoginRequestDataModel(next.flowId, totp()), device)?.tokenPair)
    }

    @Test fun changingPolicyRequiresBoundMainEmailProof() = fixture {
        val id = user(); emailPolicy(id)
        assertNull(service.updateLoginPolicy(id, AitaLoginPolicyRequest(AitaLoginSecondFactor.NONE, password, expectedSecurityRevision = 1)))
        val confirmed = proof(id, AitaSecurityEmailAction.LOGIN_POLICY, "NONE")
        assertFalse(assertNotNull(service.updateLoginPolicy(id, AitaLoginPolicyRequest(AitaLoginSecondFactor.NONE, password,
            expectedSecurityRevision = 1, emailProof = confirmed))).emailRequiredForLogin)
        assertNull(service.updateLoginPolicy(id, AitaLoginPolicyRequest(AitaLoginSecondFactor.EMAIL, password,
            expectedSecurityRevision = 2, emailProof = confirmed)))
    }

    @Test fun securityProofCannotAuthorizeAnotherActionOrTarget() = fixture {
        val id = user(); emailPolicy(id)
        val confirmed = proof(id, AitaSecurityEmailAction.LOGIN_POLICY, "EMAIL")
        assertNull(service.updateLoginPolicy(id, AitaLoginPolicyRequest(AitaLoginSecondFactor.NONE, password,
            expectedSecurityRevision = 1, emailProof = confirmed)))
        assertNull(service.requestEmailAlias(id, AitaEmailAliasRequestDataModel(extraEmail, password, emailProof = confirmed), "192.0.2.8"))
    }

    @Test fun addingExtraEmailChecksMainAndExtraNamespaces() = fixture {
        val first = user(); addExtra(first)
        val other = user(phone = "+77771234568", mail = "other@example.test")
        for (address in listOf(mainEmail, extraEmail)) {
            val failure = assertFailsWith<AitaAuthContactConflictException> {
                service.requestEmailAlias(other, AitaEmailAliasRequestDataModel(address, password), "192.0.2.8")
            }
            assertEquals(AuthContactConflict.EMAIL, failure.conflict)
        }
    }

    @Test fun addingExtraPhoneChecksMainAndExtraNamespaces() = fixture {
        user(alias = true)
        val other = user(phone = "+77771234568", mail = "other@example.test")
        for (phone in listOf(mainPhone, extraPhone)) {
            val failure = assertFailsWith<AitaAuthContactConflictException> {
                service.requestPhoneAlias(other, AitaPhoneAliasRequestDataModel(AitaPhoneAliasAction.ADD_OR_REPLACE, phone, password), "192.0.2.8")
            }
            assertEquals(AuthContactConflict.PHONE, failure.conflict)
        }
    }

    @Test fun claimedEmailInTransitReportsConflictWithoutChangingOwner() = fixture {
        val first = user()
        val flow = assertNotNull(service.requestEmailAlias(first, AitaEmailAliasRequestDataModel(extraEmail, password), "192.0.2.8"))
        val other = user(phone = "+77771234568", mail = extraEmail)
        assertEquals(AuthContactConflict.EMAIL, assertFailsWith<AitaAuthContactConflictException> {
            service.confirmEmailAlias(first, AitaEmailAliasConfirmRequestDataModel(flow.flowId, code(flow.flowId)))
        }.conflict)
        assertEquals(other, sql { AuthLoginEmails.selectAll().where { AuthLoginEmails.emailNormalized eq extraEmail }.single()[AuthLoginEmails.userId] })
    }

    @Test fun claimedPhoneInTransitReportsConflictWithoutChangingAlias() = fixture {
        val first = user()
        val flow = assertNotNull(service.requestPhoneAlias(first, AitaPhoneAliasRequestDataModel(AitaPhoneAliasAction.ADD_OR_REPLACE, extraPhone, password), "192.0.2.8"))
        user(phone = extraPhone, mail = "other@example.test")
        assertEquals(AuthContactConflict.PHONE, assertFailsWith<AitaAuthContactConflictException> {
            service.confirmPhoneAlias(first, AitaPhoneAliasConfirmRequestDataModel(flow.flowId, code(flow.flowId)))
        }.conflict)
        assertNull(service.settings(first).phoneLoginAlias)
    }

    @Test fun databaseRejectsSecondExtraEvenWithoutServiceCode() = fixture {
        val first = user(); addExtra(first)
        assertEquals("23505", assertFailsWith<ExposedSQLException> { addExtra(first, "second@example.test") }.sqlState)
        assertEquals(listOf(extraEmail), service.settings(first).additionalLoginEmails)
    }

    @Test fun databasePrimaryRegistrationCannotStealAnExtraEmail() = fixture {
        val first = user(); addExtra(first)
        assertEquals("23505", assertFailsWith<ExposedSQLException> { user(phone = "+77771234568", mail = extraEmail) }.sqlState)
        assertEquals(first, sql { AuthLoginEmails.selectAll().where { AuthLoginEmails.emailNormalized eq extraEmail }.single()[AuthLoginEmails.userId] })
    }

    @Test fun securityProofCannotBeUsedByAnotherAccount() = fixture {
        val first = user(); emailPolicy(first)
        val other = user(phone = "+77771234568", mail = "other@example.test"); emailPolicy(other)
        val confirmed = proof(first, AitaSecurityEmailAction.LOGIN_POLICY, "NONE")
        assertNull(service.updateLoginPolicy(other, AitaLoginPolicyRequest(AitaLoginSecondFactor.NONE, password,
            expectedSecurityRevision = 1, emailProof = confirmed)))
    }

    @Test fun securityRevisionChangeInvalidatesEmailLogin() = fixture {
        val first = user(); emailPolicy(first)
        val flow = emailLogin()
        sql { AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq first }) { it[securityRevision] = 2L } }
        assertNull(service.verifyLoginEmailFactor(AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId)), device))
    }

    @Test fun twoAccountsCannotConcurrentlyConfirmTheSameExtraEmail() = fixture {
        val first = user()
        val other = user(phone = "+77771234568", mail = "other@example.test")
        val a = assertNotNull(service.requestEmailAlias(first, AitaEmailAliasRequestDataModel(extraEmail, password), "192.0.2.8"))
        val b = assertNotNull(service.requestEmailAlias(other, AitaEmailAliasRequestDataModel(extraEmail, password), "192.0.2.9"))
        val requests = listOf(first to AitaEmailAliasConfirmRequestDataModel(a.flowId, code(a.flowId)),
            other to AitaEmailAliasConfirmRequestDataModel(b.flowId, code(b.flowId)))
        val start = CompletableDeferred<Unit>()
        val results = coroutineScope {
            val jobs = requests.map { (id, request) -> async(Dispatchers.IO) {
                start.await()
                try { service.confirmEmailAlias(id, request) != null }
                catch (conflict: AitaAuthContactConflictException) { assertEquals(AuthContactConflict.EMAIL, conflict.conflict); false }
            } }
            start.complete(Unit)
            withTimeout(15_000L) { jobs.awaitAll() }
        }
        assertEquals(1, results.count { it })
        assertEquals(1L, sql { AuthLoginEmails.selectAll().where { AuthLoginEmails.emailNormalized eq extraEmail }.count() })
    }

    @Test fun emailFactorDoesNotAllowAnUnverifiedMainEmailReplacement() = fixture {
        val id = user(); emailPolicy(id)
        val request = profileRequest(id, "typo@example.test")
        assertEquals(AuthContactConflict.MAIN_EMAIL_VERIFICATION, assertFailsWith<AitaAuthContactConflictException> {
            sql { service.verifyProfileSecurityInside(Users.selectAll().where { Users.id eq id }.single(), request) }
        }.conflict)
        assertEquals(mainEmail, service.settings(id).email)
    }

    @Test fun verifiedExtraCanBecomeMainAfterCurrentMainEmailConfirmation() = fixture {
        val id = user(); emailPolicy(id); addExtra(id)
        val confirmed = proof(id, AitaSecurityEmailAction.PROFILE, aitaProfileSecurityTarget(mainPhone, extraEmail, true))
        sql {
            assertTrue(service.verifyProfileSecurityInside(Users.selectAll().where { Users.id eq id }.single(), profileRequest(id, extraEmail, confirmed)))
            Users.update({ Users.id eq id }) { it[email] = extraEmail }
        }
        val settings = service.settings(id)
        assertEquals(extraEmail, settings.email)
        assertTrue(settings.emailRequiredForLogin)
        assertTrue(settings.additionalLoginEmails.isEmpty())
    }

    private fun profileRequest(id: UUID, email: String, proof: AitaSecurityEmailProof? = null) = kz.aita.UserAccountUpdateDataModel(
        account = kz.aita.UserAccountDataModel(id = id.toString(), phoneNumber = mainPhone, email = email,
            firstName = "Test", lastName = "Account", countryLocale = "kz", workerAccountIds = null, supplierAccountIds = null,
            createdAt = 0L, isActive = true), password = password, newPassword = null, emailProof = proof)

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
