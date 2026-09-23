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
                    SchemaUtils.create(Users, RefreshSessions, SecuritySessionEvents, Notifications)
                    // Reproduce pre-V98 session shape, then execute the actual additive migrations.
                    exec("ALTER TABLE refresh_sessions DROP COLUMN security_invalidated")
                    listOf("V63__security_session_history_63918.sql", "V64__refresh_session_same_device_uniqueness_58264.sql",
                        "V93__email_password_recovery_and_authenticator_security.sql",
                        "V94__authenticated_phone_login_alias_challenges.sql", "V95__authentication_email_delivery_snapshots.sql",
                        "V96__verified_additional_login_emails.sql", "V97__authenticator_login_requirement.sql",
                        "V98__authenticator_sign_in_and_email_recovery.sql",
                        "V99__email_second_factor_and_single_extra_email.sql",
                        "V111__scoped_contact_email_confirmation.sql", "V119__new_device_sign_in_notices.sql", "V127__distinct_device_security_sessions.sql", "V130__email_resend_response_recovery.sql").forEach { name ->
                        val sql = requireNotNull(javaClass.getResourceAsStream("/db/migration/$name")).bufferedReader().use { it.readText() }
                        exec(sql)
                    }
                }
                testApplication {
                    lateinit var service: AitaAdvancedAuthService
                    application {
                        service = AitaAdvancedAuthService(tokens, config, this)
                        tokens.onOtherDeviceSignInInsideTransaction = service::notifyOtherDeviceSignInInside
                    }
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

    @Test fun identicalBrowserNamesKeepDistinctInstallationsSignedIn() = fixture {
        val id = user()
        val firstMeta = device + mapOf("deviceName" to "Windows Chrome", "platformName" to "Web", "osName" to "Windows")
        val secondMeta = firstMeta + ("installationId" to "second-computer")
        val first = tokens.newPair(id, firstMeta)
        val second = tokens.newPair(id, secondMeta)
        assertEquals(2, loadSecuritySessionsForUser(id, null).size)
        tokens.rotate(first.refreshToken, firstMeta)
        tokens.rotate(second.refreshToken, secondMeta)
        assertEquals(2, loadSecuritySessionsForUser(id, null).size)
        tokens.newPair(id, secondMeta)
        assertEquals(2, loadSecuritySessionsForUser(id, null).size, "A new login replaces only the same installation")
    }

    @Test fun explicitRevocationIsDistinguishedFromNormalRotationAcrossLineage() = fixture {
        val owner = user()
        val original = tokens.newPair(owner, device)
        val rotated = tokens.rotate(original.refreshToken, device)
        val originalId = UUID.fromString(com.auth0.jwt.JWT.decode(original.accessToken).getClaim("sessionId").asString())
        val rotatedId = UUID.fromString(com.auth0.jwt.JWT.decode(rotated.accessToken).getClaim("sessionId").asString())
        sql {
            assertEquals(setOf(originalId, rotatedId), securitySessionLineageInsideTransaction(owner, originalId))
            assertFalse(refreshSessionWasSecurityRevokedInsideTransaction(original.refreshToken))
            assertFalse(refreshSessionWasSecurityRevokedInsideTransaction("unknown-token"))
            RefreshSessions.update({ RefreshSessions.id eq rotatedId }) { it[RefreshSessions.securityInvalidated] = true; it[RefreshSessions.revokedAt] = Instant.now() }
            assertTrue(refreshSessionWasSecurityRevokedInsideTransaction(original.refreshToken))
            assertTrue(refreshSessionWasSecurityRevokedInsideTransaction(rotated.refreshToken))
            assertTrue(securitySessionLineageInsideTransaction(UUID.randomUUID(), originalId).isEmpty())
        }
        val replacement = tokens.newPair(owner, device)
        sql { assertFalse(refreshSessionWasSecurityRevokedInsideTransaction(replacement.refreshToken)) }
    }

    @Test fun rotatedHistoryCannotHideOtherActiveSessions() = fixture {
        val id = user()
        val first = tokens.newPair(id, device)
        val other = device + ("installationId" to "second-computer")
        var rotating = tokens.newPair(id, other)
        repeat(55) { rotating = tokens.rotate(rotating.refreshToken, other) }
        val currentId = UUID.fromString(com.auth0.jwt.JWT.decode(first.accessToken).getClaim("sessionId").asString())
        val sessions = loadSecuritySessionsForUser(id, currentId)
        assertEquals(2, sessions.size)
        assertTrue(sessions.single { it.id == currentId.toString() }.current)
        assertTrue(sessions.all { it.active })
    }

    @Test fun securityListExcludesExpiredAndInvalidatedTokensBeforeLimit() = fixture {
        val id = user()
        tokens.newPair(id, device)
        tokens.newPair(id, device + ("installationId" to "expired"))
        tokens.newPair(id, device + ("installationId" to "invalidated"))
        sql {
            RefreshSessions.selectAll().where { RefreshSessions.userId eq id }.toList().forEach { row ->
                when (row[RefreshSessions.meta]?.get("installationId")) {
                    "expired" -> RefreshSessions.update({ RefreshSessions.id eq row[RefreshSessions.id] }) { it[expiresAt] = Instant.now().minusSeconds(1) }
                    "invalidated" -> RefreshSessions.update({ RefreshSessions.id eq row[RefreshSessions.id] }) { it[RefreshSessions.securityInvalidated] = true }
                }
            }
        }
        assertEquals(1, loadSecuritySessionsForUser(id, null).size)
    }

    @Test fun otherDeviceLoginPersistsOneAlertAndOneEncryptedEmailWithoutRepeatingOnRefresh() = fixture {
        val id = user()
        sql { Users.update({ Users.id eq id }) { it[appLanguage] = "en" } }
        service.settings(id)
        sql { AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq id }) { it[emailVerifiedAtMillis] = System.currentTimeMillis() } }
        tokens.newPair(id, device)
        assertEquals(0, sql { Notifications.selectAll().count().toInt() })
        val nextDevice = device + ("installationId" to "other-installation")
        val signedIn = tokens.newPair(id, nextDevice)
        val notice = sql { Notifications.selectAll().single().toNotificationDataModel() }
        assertEquals("security", notice.category)
        assertEquals("other-installation", notice.metadata["originInstallationId"])
        assertEquals(id.toString(), notice.userId)
        val flowId = sql { AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.purpose eq AUTH_PURPOSE_SIGN_IN_NOTICE }.single()[AuthOneTimeChallenges.publicId].toString() }
        val mail = email(flowId)
        assertEquals(mainEmail, mail["to"]!!.jsonArray.single().jsonPrimitive.content)
        assertTrue(mail["text"]!!.jsonPrimitive.content.contains("Your AITA account was signed in."))
        assertFalse(mail["text"]!!.jsonPrimitive.content.contains("another device"))
        assertFalse(mail["text"]!!.jsonPrimitive.content.contains(signedIn.refreshToken))
        tokens.rotate(signedIn.refreshToken, nextDevice)
        assertEquals(1, sql { Notifications.selectAll().count().toInt() })
        assertEquals(1, sql { AuthEmailOutbox.selectAll().count().toInt() })
    }
    @Test fun rejectedLoginCannotCreateAlertsAndUnverifiedEmailIsNeverMailed() = fixture {
        val id = user()
        tokens.newPair(id, device)
        assertNull(tokens.newPairAfterVerification(id, device + ("installationId" to "other")) { false })
        assertEquals(0, sql { Notifications.selectAll().count().toInt() })
        tokens.newPair(id, device + ("installationId" to "other"))
        assertEquals(1, sql { Notifications.selectAll().count().toInt() })
        assertEquals(0, sql { AuthEmailOutbox.selectAll().count().toInt() })
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
        assertFailsWith<AitaContactVerificationRequiredException> {
            sql { service.requireContactProofsInside(id, AitaContactTarget(AitaContactPurpose.ACCOUNT_CONTACT, id.toString()),
                listOf(request.account.email), listOf(mainEmail), emptyList()) }
        }
        assertEquals(mainEmail, service.settings(id).email)
    }

    @Test fun verifiedExtraCanBecomeMainAfterCurrentMainEmailConfirmation() = fixture {
        val id = user(); emailPolicy(id); addExtra(id)
        val confirmed = proof(id, AitaSecurityEmailAction.PROFILE, aitaProfileSecurityTarget(mainPhone, extraEmail, true))
        val target = AitaContactTarget(AitaContactPurpose.ACCOUNT_CONTACT, id.toString())
        val flow = assertNotNull(service.requestContactCode(id, AitaContactCodeRequest(target, extraEmail), device.getValue("ip")))
        val receipt = assertNotNull(service.verifyContactCode(id, AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId)), device.getValue("ip")))
        sql {
            service.lockContactActorInside(id)
            val ids = service.requireContactProofsInside(id, target, listOf(extraEmail), listOf(mainEmail), listOf(receipt.proof))
            assertTrue(service.verifyProfileSecurityInside(Users.selectAll().where { Users.id eq id }.single(), profileRequest(id, extraEmail, confirmed)))
            Users.update({ Users.id eq id }) { it[email] = extraEmail }
            service.consumeContactProofsInside(ids, id)
            service.markPrimaryEmailVerifiedInside(id, extraEmail)
        }
        val settings = service.settings(id)
        assertEquals(extraEmail, settings.email)
        assertTrue(settings.emailRequiredForLogin)
        assertTrue(settings.additionalLoginEmails.isEmpty())
    }


    private fun registrationTarget() = aitaContactDraftTarget(AitaContactPurpose.REGISTRATION, "", UUID.randomUUID().toString())

    @Test fun failedFirstSessionRollsBackRegistrationAndLeavesEmailProofReusable() = fixture {
        val target = registrationTarget()
        val flow = assertNotNull(service.requestContactCode(null, AitaContactCodeRequest(target, extraEmail), "192.0.2.90"))
        val proof = assertNotNull(service.verifyContactCode(null,
            AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId)), "192.0.2.90")).proof
        val accountId = UUID.randomUUID()
        val hash = Pw.hash(password.toCharArray())
        suspend fun register() = tokens.newPairForRegistration(accountId, device) {
            val proofs = service.requireContactProofsInside(null, target, listOf(extraEmail), emptyList(), listOf(proof))
            Users.insert {
                it[id] = accountId; it[publicId] = "NEW-ACCOUNT"; it[phoneNumber] = mainPhone
                it[email] = extraEmail; it[firstName] = "Test"; it[lastName] = "Registration"
                it[countryLocale] = "kz"; it[passwordHash] = hash
            }
            service.consumeContactProofsInside(proofs, accountId)
            service.markPrimaryEmailVerifiedInside(accountId, extraEmail)
            true
        }
        sql {
            exec("CREATE FUNCTION reject_first_session() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''simulated session failure''; END;'")
            exec("CREATE TRIGGER reject_first_session BEFORE INSERT ON refresh_sessions FOR EACH ROW EXECUTE FUNCTION reject_first_session()")
        }
        assertFailsWith<ExposedSQLException> { register() }
        sql {
            assertEquals(0L, Users.selectAll().count())
            assertEquals(0L, AuthLoginEmails.selectAll().count())
            assertEquals(0L, RefreshSessions.selectAll().count())
            assertNull(AuthContactVerifications.selectAll().single()[AuthContactVerifications.appliedEntityId])
            exec("DROP TRIGGER reject_first_session ON refresh_sessions")
        }
        assertNotNull(register())
        sql {
            assertEquals(1L, Users.selectAll().count())
            assertEquals(1L, RefreshSessions.selectAll().count())
            assertEquals(accountId, AuthContactVerifications.selectAll().single()[AuthContactVerifications.appliedEntityId])
        }
        // Losing the HTTP response after commit must still leave a usable existing account.
        val login = assertNotNull(service.passwordLogin(AitaPasswordLoginRequestDataModel(extraEmail, password), device))
        assertEquals(AitaAuthNextStep.AUTHENTICATED, login.nextStep)
        assertNotNull(login.tokenPair)
        assertEquals(1L, sql { Users.selectAll().count() })
    }

    @Test fun legacyUnconfirmedAccountCanRecoverWithoutRegisteringOrLosingItsIdentity() = fixture {
        val accountId = user()
        assertNull(sql { AuthLoginEmails.selectAll().single()[AuthLoginEmails.verifiedAtMillis] })
        val flow = service.requestEmailCode(mainEmail, "PASSWORD_RECOVERY", "en", "192.0.2.91")
        val verified = assertNotNull(service.verifyEmailCode(AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId)),
            "PASSWORD_RECOVERY", device))
        val replacement = "Replacement-Password-021!"
        assertTrue(service.resetPassword(AitaPasswordRecoveryResetRequestDataModel(flow.flowId, verified.resetTicket, replacement)))
        val login = assertNotNull(service.passwordLogin(AitaPasswordLoginRequestDataModel(mainEmail, replacement), device))
        assertEquals(AitaAuthNextStep.AUTHENTICATED, login.nextStep)
        assertEquals(accountId, sql { Users.selectAll().single()[Users.id] })
    }

    @Test fun registrationQueuesAnAnonymousContactWithoutCreatingAnAccountOrLoginSession() = fixture {
        val target = registrationTarget()
        val flow = assertNotNull(service.requestContactCode(null, AitaContactCodeRequest(target, extraEmail), "192.0.2.40"))
        assertEquals(AitaAuthNextStep.EMAIL_CODE, flow.nextStep)
        assertNull(flow.tokenPair)
        assertEquals(extraEmail, email(flow.flowId)["to"]!!.jsonArray.single().jsonPrimitive.content)
        sql {
            assertEquals(0L, Users.selectAll().count())
            assertEquals(0L, RefreshSessions.selectAll().count())
            assertNull(AuthContactVerifications.selectAll().single()[AuthContactVerifications.actorUserId])
        }
    }

    @Test fun contactCodeVerificationRetriesRecoverTheSameReceiptAndDeadline() = fixture {
        val target = registrationTarget()
        val flow = assertNotNull(service.requestContactCode(null, AitaContactCodeRequest(target, extraEmail), "192.0.2.41"))
        val request = AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId))
        val first = assertNotNull(service.verifyContactCode(null, request, "192.0.2.41"))
        val second = assertNotNull(service.verifyContactCode(null, request, "192.0.2.41"))
        assertEquals(first.proof, second.proof)
        assertEquals(first.expiresAtMillis, second.expiresAtMillis)
        sql {
            val row = AuthContactVerifications.selectAll().single()
            assertNotEquals(first.proof.receipt, row[AuthContactVerifications.receiptHash])
            assertFalse(row[AuthContactVerifications.receiptCiphertext].orEmpty().contains(first.proof.receipt))
            assertEquals(listOf(UUID.fromString(flow.flowId)), service.requireContactProofsInside(null, target,
                listOf(extraEmail), emptyList(), listOf(first.proof)))
        }
    }

    @Test fun contactWrongAttemptsCommitAndEventuallyExhaustTheChallenge() = fixture {
        val flow = assertNotNull(service.requestContactCode(null, AitaContactCodeRequest(registrationTarget(), extraEmail), "192.0.2.42"))
        val correct = code(flow.flowId)
        val wrong = if (correct == "000000") "111111" else "000000"
        repeat(config.maxAttempts) { attempt ->
            assertNull(service.verifyContactCode(null, AitaEmailCodeVerifyRequestDataModel(flow.flowId, wrong), "192.0.2.42"))
            assertEquals(attempt + 1, sql { AuthOneTimeChallenges.selectAll().single()[AuthOneTimeChallenges.attempts] })
        }
        assertNull(service.verifyContactCode(null, AitaEmailCodeVerifyRequestDataModel(flow.flowId, correct), "192.0.2.42"))
    }

    @Test fun contactResendKeepsOriginalExpiryAndClosesOriginalCode() = fixture {
        val target = registrationTarget()
        val flow = assertNotNull(service.requestContactCode(null, AitaContactCodeRequest(target, extraEmail), "192.0.2.43"))
        val correct = code(flow.flowId)
        // A lost initial response is safe to retry during the same cooldown.
        assertEquals(flow.flowId, service.requestContactCode(null, AitaContactCodeRequest(target, extraEmail), "192.0.2.43")?.flowId)
        sql { AuthOneTimeChallenges.update({ AuthOneTimeChallenges.publicId eq UUID.fromString(flow.flowId) }) { it[resendAfterMillis] = 1L } }
        val replacement = assertNotNull(service.resendContactCode(null, AitaEmailCodeResendRequestDataModel(flow.flowId), "192.0.2.43"))
        assertNotEquals(flow.flowId, replacement.flowId)
        assertEquals(flow.expiresAtMillis, replacement.expiresAtMillis)
        assertNull(service.verifyContactCode(null, AitaEmailCodeVerifyRequestDataModel(flow.flowId, correct), "192.0.2.43"))
        assertNotNull(service.verifyContactCode(null, AitaEmailCodeVerifyRequestDataModel(replacement.flowId, code(replacement.flowId)), "192.0.2.43"))
    }

    @Test fun lostContactResendResponseRecoversTheSameChallengeWithoutSendingAgain() = fixture {
        val flow = assertNotNull(service.requestContactCode(null, AitaContactCodeRequest(registrationTarget(), extraEmail), "192.0.2.80"))
        sql { AuthOneTimeChallenges.update({ AuthOneTimeChallenges.publicId eq UUID.fromString(flow.flowId) }) { it[resendAfterMillis] = 1L } }
        val resend = AitaEmailCodeResendRequestDataModel(flow.flowId)
        val replacement = assertNotNull(service.resendContactCode(null, resend, "192.0.2.80"))
        val recovered = assertNotNull(service.resendContactCode(null, resend, "192.0.2.80"))
        assertEquals(replacement.flowId, recovered.flowId)
        assertEquals(replacement.expiresAtMillis, recovered.expiresAtMillis)
        assertEquals(2L, sql { AuthEmailOutbox.selectAll().count() })
        val verified = service.verifyContactCode(null, AitaEmailCodeVerifyRequestDataModel(recovered.flowId, code(recovered.flowId)), "192.0.2.80")
        assertNotNull(verified)
        assertNull(service.resendContactCode(null, resend, "192.0.2.80"))
    }

    @Test fun lostLoginAndPasswordRecoveryResendResponsesRecoverWithoutExtendingTheirBudget() = fixture {
        user()
        service.settings(sql { Users.selectAll().single()[Users.id] })
        for (purpose in listOf("PASSWORDLESS_LOGIN", "PASSWORD_RECOVERY")) {
            val flow = service.requestEmailCode(mainEmail, purpose, "en", "192.0.2.81")
            sql { AuthOneTimeChallenges.update({ AuthOneTimeChallenges.publicId eq UUID.fromString(flow.flowId) }) { it[resendAfterMillis] = 1L } }
            val replacement = assertNotNull(service.resend(flow.flowId, "en", "192.0.2.81", purpose))
            val recovered = assertNotNull(service.resend(flow.flowId, "en", "192.0.2.81", purpose))
            assertEquals(replacement.flowId, recovered.flowId)
            assertEquals(replacement.expiresAtMillis, recovered.expiresAtMillis)
            assertNull(service.resend(flow.flowId, "en", "192.0.2.81", if (purpose == "PASSWORDLESS_LOGIN") "PASSWORD_RECOVERY" else "PASSWORDLESS_LOGIN"))
            assertNotNull(service.verifyEmailCode(AitaEmailCodeVerifyRequestDataModel(recovered.flowId, code(recovered.flowId)), purpose, device))
            assertNull(service.resend(flow.flowId, "en", "192.0.2.81", purpose))
        }
    }

    @Test fun contactProofCannotCrossPurposeDraftAddressOrActor() = fixture {
        val owner = user()
        val other = user(phone = "+77771234568", mail = "other@example.test")
        val target = aitaContactDraftTarget(AitaContactPurpose.SUPPLIER_CONTACT, "", UUID.randomUUID().toString())
        val flow = assertNotNull(service.requestContactCode(owner, AitaContactCodeRequest(target, extraEmail), "192.0.2.44"))
        val request = AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId))
        assertNull(service.verifyContactCode(other, request, "192.0.2.44"))
        assertNull(service.verifyContactCode(null, request, "192.0.2.44"))
        val receipt = assertNotNull(service.verifyContactCode(owner, request, "192.0.2.44")).proof
        for (changed in listOf(target.copy(purpose = AitaContactPurpose.STORE_CONTACT),
            target.copy(entityId = "new:${UUID.randomUUID()}"))) {
            assertFailsWith<AitaContactVerificationRequiredException> {
                sql { service.requireContactProofsInside(owner, changed, listOf(extraEmail), emptyList(), listOf(receipt)) }
            }
        }
        assertFailsWith<AitaContactVerificationRequiredException> {
            sql { service.requireContactProofsInside(other, target, listOf(extraEmail), emptyList(), listOf(receipt)) }
        }
        assertFailsWith<AitaContactVerificationRequiredException> {
            sql { service.requireContactProofsInside(owner, target, listOf("wrong@example.test"), emptyList(), listOf(receipt)) }
        }
    }

    @Test fun phoneChannelAndAuthenticatedRegistrationAreRejectedWithoutOutboxWrites() = fixture {
        val owner = user()
        assertNull(service.requestContactCode(null, AitaContactCodeRequest(registrationTarget(), extraEmail, AitaContactChannel.PHONE), "192.0.2.45"))
        assertNull(service.requestContactCode(owner, AitaContactCodeRequest(registrationTarget(), extraEmail), "192.0.2.45"))
        assertEquals(0L, sql { AuthEmailOutbox.selectAll().count() })
    }

    @Test fun anotherAccountCannotRequestProofForYourAccountTarget() = fixture {
        val owner = user()
        val other = user(phone = "+77771234568", mail = "other@example.test")
        assertNull(service.requestContactCode(other, AitaContactCodeRequest(AitaContactTarget(AitaContactPurpose.ACCOUNT_CONTACT,
            owner.toString()), extraEmail), "192.0.2.46"))
    }

    @Test fun contactPasswordRevisionChangeClosesConfirmedReceipts() = fixture {
        val owner = user()
        val target = AitaContactTarget(AitaContactPurpose.ACCOUNT_CONTACT, owner.toString())
        val flow = assertNotNull(service.requestContactCode(owner, AitaContactCodeRequest(target, extraEmail), "192.0.2.47"))
        val receipt = assertNotNull(service.verifyContactCode(owner,
            AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId)), "192.0.2.47")).proof
        sql { Users.update({ Users.id eq owner }) { it[passwordHash] = Pw.hash("A-new-strong-password-42!".toCharArray()) } }
        assertFailsWith<AitaContactVerificationRequiredException> {
            sql { service.requireContactProofsInside(owner, target, listOf(extraEmail), listOf(mainEmail), listOf(receipt)) }
        }
    }

    @Test fun contactReceiptExpiryIsCheckedAtSaveEvenAfterCodeWasAccepted() = fixture {
        val flow = assertNotNull(service.requestContactCode(null, AitaContactCodeRequest(registrationTarget(), extraEmail), "192.0.2.48"))
        val result = assertNotNull(service.verifyContactCode(null,
            AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId)), "192.0.2.48"))
        sql { AuthContactVerifications.update({ AuthContactVerifications.challengePublicId eq UUID.fromString(flow.flowId) }) { it[receiptExpiresAtMillis] = 1L } }
        assertFailsWith<AitaContactVerificationRequiredException> {
            sql { service.requireContactProofsInside(null, result.target, listOf(extraEmail), emptyList(), listOf(result.proof)) }
        }
    }

    @Test fun contactConsumptionAndActualMutationRollBackTogetherAndCannotReplayAfterCommit() = fixture {
        val owner = user()
        val target = AitaContactTarget(AitaContactPurpose.ACCOUNT_CONTACT, owner.toString())
        val flow = assertNotNull(service.requestContactCode(owner, AitaContactCodeRequest(target, extraEmail), "192.0.2.49"))
        val receipt = assertNotNull(service.verifyContactCode(owner,
            AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId)), "192.0.2.49")).proof
        fun applyThen(fail: Boolean) = sql {
            service.lockContactActorInside(owner)
            val ids = service.requireContactProofsInside(owner, target, listOf(extraEmail), listOf(mainEmail), listOf(receipt))
            Users.update({ Users.id eq owner }) { it[firstName] = "Saved" }
            service.consumeContactProofsInside(ids, owner)
            if (fail) error("deliberate transaction rollback")
        }
        assertFailsWith<IllegalStateException> { applyThen(true) }
        sql {
            assertEquals("Test", Users.selectAll().single()[Users.firstName])
            assertNull(AuthOneTimeChallenges.selectAll().single()[AuthOneTimeChallenges.consumedAtMillis])
        }
        applyThen(false)
        assertFailsWith<AitaContactVerificationRequiredException> { applyThen(false) }
        sql {
            assertEquals(owner, AuthContactVerifications.selectAll().single()[AuthContactVerifications.appliedEntityId])
            assertEquals("", AuthContactVerifications.selectAll().single()[AuthContactVerifications.receiptCiphertext])
        }
    }

    @Test fun concurrentContactConsumersHaveExactlyOneWinner() = fixture {
        val owner = user()
        val target = AitaContactTarget(AitaContactPurpose.ACCOUNT_CONTACT, owner.toString())
        val flow = assertNotNull(service.requestContactCode(owner, AitaContactCodeRequest(target, extraEmail), "192.0.2.50"))
        val receipt = assertNotNull(service.verifyContactCode(owner,
            AitaEmailCodeVerifyRequestDataModel(flow.flowId, code(flow.flowId)), "192.0.2.50")).proof
        val start = CompletableDeferred<Unit>()
        val winners = coroutineScope {
            val jobs = List(2) { async(Dispatchers.IO) {
                start.await()
                try {
                    sql {
                        service.lockContactActorInside(owner)
                        val ids = service.requireContactProofsInside(owner, target, listOf(extraEmail), listOf(mainEmail), listOf(receipt))
                        service.consumeContactProofsInside(ids, owner)
                    }
                    true
                } catch (_: AitaContactVerificationRequiredException) { false }
            } }
            start.complete(Unit)
            withTimeout(15_000L) { jobs.awaitAll() }
        }
        assertEquals(1, winners.count { it })
    }

    @Test fun destinationQuotaCannotBeBypassedByCreatingMoreDrafts() = fixture {
        repeat(5) { n -> assertNotNull(service.requestContactCode(null,
            AitaContactCodeRequest(registrationTarget(), extraEmail), "192.0.2.${60 + n}")) }
        assertFailsWith<AitaAuthRateLimitedException> {
            service.requestContactCode(null, AitaContactCodeRequest(registrationTarget(), extraEmail), "192.0.2.70")
        }
    }

    @Test fun unchangedBusinessContactsAreNotRetrospectivelyMarkedVerified() = fixture {
        val owner = user()
        val target = aitaContactDraftTarget(AitaContactPurpose.SUPPLIER_CONTACT, "", UUID.randomUUID().toString())
        sql {
            assertTrue(service.requireContactProofsInside(owner, target, listOf(mainEmail), listOf(mainEmail), emptyList()).isEmpty())
            assertEquals(0L, AuthContactVerifications.selectAll().count())
        }
    }

    @Test fun oldEmailNotificationRetainsOldRecipientAndHasNoCodeAfterThePrimaryEmailChanges() = fixture {
        val owner = user()
        sql {
            Users.update({ Users.id eq owner }) { it[email] = extraEmail }
            service.markPrimaryEmailVerifiedInside(owner, extraEmail)
            service.enqueueOldEmailChangeNoticeInside(owner, mainEmail, "en")
        }
        val flowId = sql { AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.purpose eq AUTH_PURPOSE_CONTACT_NOTICE }
            .single()[AuthOneTimeChallenges.publicId].toString() }
        val copy = email(flowId)
        assertEquals(mainEmail, copy["to"]!!.jsonArray.single().jsonPrimitive.content)
        assertFalse(Regex("(?<![0-9])[0-9]{6}(?![0-9])").containsMatchIn(copy["text"]!!.jsonPrimitive.content))
        sql {
            assertNotNull(AuthSecurityProfiles.selectAll().single()[AuthSecurityProfiles.emailVerifiedAtMillis])
            assertNotNull(AuthLoginEmails.selectAll().where { AuthLoginEmails.emailNormalized eq extraEmail }.single()[AuthLoginEmails.verifiedAtMillis])
        }
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
