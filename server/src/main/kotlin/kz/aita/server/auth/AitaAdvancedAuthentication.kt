package kz.aita.server.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.*
import kz.aita.LocalizedStringDataModel
import kz.aita.auth.*
import kz.aita.checkAsPassword
import kz.aita.server.*
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.any
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.getOrNull
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.collections.last
import kotlin.collections.listOf
import kotlin.collections.plusAssign
import kotlin.collections.single
import kotlin.collections.singleOrNull
import kotlin.collections.toByteArray
import kotlin.math.pow

private const val AUTH_PURPOSE_LOGIN = "PASSWORDLESS_LOGIN"
private const val AUTH_PURPOSE_RECOVERY = "PASSWORD_RECOVERY"
private const val AUTH_PURPOSE_PHONE = "PHONE_ALIAS"
private const val AUTH_OUTBOX_PENDING = "PENDING"
private const val AUTH_OUTBOX_PROCESSING = "PROCESSING"
private const val AUTH_OUTBOX_RETRY = "RETRY_WAIT"
private const val AUTH_OUTBOX_SENT = "SENT"
private const val AUTH_OUTBOX_FAILED = "FAILED"
private const val AUTH_OUTBOX_CANCELLED = "CANCELLED"
private const val AUTH_LOGIN_CHALLENGE_PASSWORD = "PASSWORD"
private const val AUTH_LOGIN_CHALLENGE_EMAIL = "EMAIL_CODE"

object AuthSecurityProfiles : Table("auth_security_profiles") {
    val userId = uuid("user_id")
    val emailVerifiedAtMillis = long("email_verified_at_millis").nullable()
    val phoneLoginAlias = varchar("phone_login_alias", 32).nullable()
    val phoneAliasVerifiedAtMillis = long("phone_alias_verified_at_millis").nullable()
    val totpSecretCiphertext = text("totp_secret_ciphertext").nullable()
    val totpPendingSecretCiphertext = text("totp_pending_secret_ciphertext").nullable()
    val totpPendingSetupId = uuid("totp_pending_setup_id").nullable()
    val totpPendingExpiresAtMillis = long("totp_pending_expires_at_millis").nullable()
    val totpEnabledAtMillis = long("totp_enabled_at_millis").nullable()
    val securityRevision = long("security_revision").default(1L)
    val createdAtMillis = long("created_at_millis")
    val updatedAtMillis = long("updated_at_millis")
    override val primaryKey = PrimaryKey(userId)
}

object AuthOneTimeChallenges : Table("auth_one_time_challenges") {
    val id = uuid("id")
    val publicId = uuid("public_id").uniqueIndex()
    val userId = uuid("user_id").nullable()
    val purpose = varchar("purpose", 40)
    val identifierHash = char("identifier_hash", 64).index()
    val requestIpHash = char("request_ip_hash", 64).index()
    val locale = varchar("locale", 16).default("en")
    val codeHash = char("code_hash", 64)
    val codeCiphertext = text("code_ciphertext")
    val deliveryEmailHash = char("delivery_email_hash", 64).nullable()
    val attempts = integer("attempts").default(0)
    val maxAttempts = integer("max_attempts").default(5)
    val expiresAtMillis = long("expires_at_millis").index()
    val resendAfterMillis = long("resend_after_millis")
    val verifiedAtMillis = long("verified_at_millis").nullable()
    val resetTicketHash = char("reset_ticket_hash", 64).nullable()
    val resetTicketExpiresAtMillis = long("reset_ticket_expires_at_millis").nullable()
    val consumedAtMillis = long("consumed_at_millis").nullable()
    val createdAtMillis = long("created_at_millis").index()
    val updatedAtMillis = long("updated_at_millis")
    override val primaryKey = PrimaryKey(id)
}

object AuthEmailOutbox : Table("auth_email_outbox") {
    val id = uuid("id")
    val challengeId = uuid("challenge_id").uniqueIndex()
    val status = varchar("status", 24).index()
    val attempts = integer("attempts").default(0)
    val maxAttempts = integer("max_attempts").default(8)
    val nextAttemptAtMillis = long("next_attempt_at_millis").index()
    val lockedAtMillis = long("locked_at_millis").nullable()
    val lockedBy = varchar("locked_by", 120).nullable()
    val providerMessageId = text("provider_message_id").nullable()
    val payloadCiphertext = text("payload_ciphertext").nullable()
    val lastErrorCode = varchar("last_error_code", 80).nullable()
    val createdAtMillis = long("created_at_millis")
    val updatedAtMillis = long("updated_at_millis")
    val sentAtMillis = long("sent_at_millis").nullable()
    override val primaryKey = PrimaryKey(id)
}

object AuthLoginChallenges : Table("auth_login_challenges") {
    val id = uuid("id")
    val publicId = uuid("public_id").uniqueIndex()
    val userId = uuid("user_id")
    val primaryMethod = varchar("primary_method", 32)
    val attempts = integer("attempts").default(0)
    val maxAttempts = integer("max_attempts").default(8)
    val expiresAtMillis = long("expires_at_millis").index()
    val consumedAtMillis = long("consumed_at_millis").nullable()
    val createdAtMillis = long("created_at_millis")
    override val primaryKey = PrimaryKey(id)
}

object AuthRecoveryCodes : Table("auth_recovery_codes") {
    val id = uuid("id")
    val userId = uuid("user_id").index()
    val codeHash = char("code_hash", 64)
    val createdAtMillis = long("created_at_millis")
    val usedAtMillis = long("used_at_millis").nullable()
    override val primaryKey = PrimaryKey(id)
}

object AuthPhoneAliasChallenges : Table("auth_phone_alias_challenges") {
    val id = uuid("id")
    val challengePublicId = uuid("challenge_public_id").uniqueIndex()
    val userId = uuid("user_id").index()
    val action = varchar("action", 32)
    val requestedPhoneAlias = varchar("requested_phone_alias", 32).nullable()
    val consumedAtMillis = long("consumed_at_millis").nullable()
    val createdAtMillis = long("created_at_millis")
    override val primaryKey = PrimaryKey(id)
}

object AuthSecurityAuditEvents : Table("auth_security_audit_events") {
    val id = uuid("id")
    val userId = uuid("user_id").nullable().index()
    val eventType = varchar("event_type", 80).index()
    val identifierHash = char("identifier_hash", 64).nullable()
    val ipHash = char("ip_hash", 64).nullable()
    val metadata = text("metadata").default("{}")
    val createdAtMillis = long("created_at_millis").index()
    override val primaryKey = PrimaryKey(id)
}

private fun authMessage(main: String, ru: String, kk: String): List<LocalizedStringDataModel> = listOf(
    LocalizedStringDataModel("main", main),
    LocalizedStringDataModel("en", main),
    LocalizedStringDataModel("ru", ru),
    LocalizedStringDataModel("kk", kk)
)

// AdvancedAuthConfig is shared with the error handler and tests; its only declaration
// and environment parser live in AitaAuthConfiguration.kt in this package.
private class AuthCrypto(private val config: AdvancedAuthConfig) {
    private val random = SecureRandom()

    fun randomCode(): String = random.nextInt(1_000_000).toString().padStart(6, '0')
    fun randomToken(bytes: Int = 32): String = ByteArray(bytes).also(random::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    fun hmac(context: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(config.codePepper, "HmacSHA256"))
        return mac.doFinal("$context\u0000$value".toByteArray(StandardCharsets.UTF_8)).toHex()
    }

    fun constantTimeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(
        a.toByteArray(StandardCharsets.UTF_8), b.toByteArray(StandardCharsets.UTF_8)
    )

    fun encrypt(context: String, plaintext: String): String {
        config.requireSecurityConfigured()
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(config.encryptionKey, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(context.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return "v1.${Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)}.${Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)}"
    }

    fun decrypt(context: String, encoded: String): String {
        config.requireSecurityConfigured()
        val parts = encoded.split('.')
        require(parts.size == 3 && parts[0] == "v1") { "Unsupported encrypted value" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(config.encryptionKey, "AES"),
            GCMParameterSpec(128, Base64.getUrlDecoder().decode(parts[1]))
        )
        cipher.updateAAD(context.toByteArray(StandardCharsets.UTF_8))
        return String(cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])), StandardCharsets.UTF_8)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

private class AuthSlidingWindowLimiter(
    private val maximumTrackedKeys: Int = 20_000
) {
    private val values = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val calls = AtomicLong(0L)

    fun allow(key: String, max: Int, now: Long, windowMillis: Long = 3_600_000L): Boolean {
        if (calls.incrementAndGet() % 256L == 0L) cleanup(now, windowMillis)
        val existing = values[key]
        if (existing == null && values.size >= maximumTrackedKeys) {
            cleanup(now, windowMillis)
            if (values.size >= maximumTrackedKeys) return false
        }
        val queue = values.computeIfAbsent(key) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && queue.first() < now - windowMillis) queue.removeFirst()
            if (queue.size >= max) return false
            queue.addLast(now)
            return true
        }
    }

    private fun cleanup(now: Long, windowMillis: Long) {
        values.entries.removeIf { (_, queue) ->
            synchronized(queue) {
                while (queue.isNotEmpty() && queue.first() < now - windowMillis) queue.removeFirst()
                queue.isEmpty()
            }
        }
    }
}

private data class AuthUser(val id: UUID, val email: String, val passwordHash: String, val active: Boolean)

private class AitaAdvancedAuthService(
    private val tokenService: TokenService,
    private val config: AdvancedAuthConfig,
    private val application: Application
) {
    private val crypto = AuthCrypto(config)
    private val limiter = AuthSlidingWindowLimiter()
    private val random = SecureRandom()
    private val workerStarted = AtomicBoolean(false)
    private val workerId = "aita-auth-${UUID.randomUUID()}"
    private val senderDelegate = lazy { AitaResendSender(config.resendApiKey) }
    private val sender by senderDelegate
    private val emailUnavailableUntil = AtomicLong(0L)

    private fun requireEmailDelivery(recovery: Boolean = false) {
        config.requireEmailAuthentication(recovery)
        if (emailUnavailableUntil.get() > System.currentTimeMillis()) {
            throw AitaAuthUnavailableException(AitaAuthUnavailableReason.EMAIL_DELIVERY)
        }
    }

    suspend fun resolveUser(identifier: String): AuthUser? {
        val normalized = normalizeAitaLoginIdentifier(identifier) ?: return null
        return newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val userId = when (normalized.kind) {
                AitaAuthIdentifierKind.EMAIL -> Users.selectAll()
                    .where { Users.email.lowerCase() eq normalized.value }
                    .limit(2).toList().singleOrNull()?.get(Users.id)
                AitaAuthIdentifierKind.PHONE -> {
                    AuthSecurityProfiles.selectAll()
                        .where { AuthSecurityProfiles.phoneLoginAlias eq normalized.value }
                        .limit(1).singleOrNull()?.get(AuthSecurityProfiles.userId)
                        ?: Users.selectAll().where { Users.phoneNumber eq normalized.value }
                            .limit(1).singleOrNull()?.get(Users.id)
                }
            } ?: return@newSuspendedTransaction null
            Users.selectAll().where { Users.id eq userId }.limit(1).singleOrNull()?.let {
                AuthUser(it[Users.id], it[Users.email], it[Users.passwordHash], it[Users.isActive])
            }
        }
    }

    suspend fun totpEnabled(userId: UUID): Boolean = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }
            .limit(1).singleOrNull()?.get(AuthSecurityProfiles.totpEnabledAtMillis) != null
    }

    fun capabilities(): AitaAuthCapabilitiesDataModel = config.capabilities().copy(
        emailDeliveryUnavailable = emailUnavailableUntil.get() > System.currentTimeMillis()
    )

    suspend fun passwordLogin(request: AitaPasswordLoginRequestDataModel, meta: Map<String, String>): AitaAuthFlowDataModel? {
        val now = System.currentTimeMillis()
        val normalized = normalizeAitaLoginIdentifier(request.identifier) ?: return null
        val identifierHash = crypto.hmac("password-login", normalized.value)
        val ipHash = crypto.hmac("ip", meta["ip"].orEmpty())
        val rateAllowed = limiter.allow("password:id:$identifierHash", 12, now) &&
            limiter.allow("password:ip:$ipHash", 60, now)
        if (!rateAllowed) {
            delay((120L..260L).random())
            audit(null, "PASSWORD_LOGIN_RATE_LIMITED", identifierHash, meta["ip"])
            return null
        }
        val user = resolveUser(normalized.value) ?: return null
        if (!user.active || !Pw.verify(request.password.toCharArray(), user.passwordHash)) return null
        audit(user.id, "PASSWORD_PRIMARY_VERIFIED", null, meta["ip"])
        return if (totpEnabled(user.id)) {
            createLoginChallenge(user.id, AUTH_LOGIN_CHALLENGE_PASSWORD)
        } else {
            AitaAuthFlowDataModel(
                nextStep = AitaAuthNextStep.AUTHENTICATED,
                tokenPair = tokenService.newPair(user.id, meta)
            )
        }
    }

    /** Serialize request quotas across processes, using non-reversible, parameter-free bucket keys. */
    private fun lockEmailBucketsInside(identifierHash: String, ipHash: String, userId: UUID?) {
        val keys = listOfNotNull(identifierHash, ipHash, userId?.let { crypto.hmac("email-user", it.toString()) })
            .map { java.lang.Long.parseUnsignedLong(it.take(16), 16) }.distinct().sorted()
        keys.forEach { key ->
            org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec("SELECT pg_advisory_xact_lock($key)")
        }
    }

    private fun checkEmailQuotaInside(identifierHash: String, ipHash: String, now: Long) {
        val since = now - 3_600_000L
        val identifierRequests = AuthOneTimeChallenges.select(AuthOneTimeChallenges.createdAtMillis).where {
            (AuthOneTimeChallenges.identifierHash eq identifierHash) and
                (AuthOneTimeChallenges.createdAtMillis greaterEq since)
        }.orderBy(AuthOneTimeChallenges.createdAtMillis to SortOrder.DESC).limit(5).toList()
        val ipRequests = AuthOneTimeChallenges.select(AuthOneTimeChallenges.createdAtMillis).where {
            (AuthOneTimeChallenges.requestIpHash eq ipHash) and
                (AuthOneTimeChallenges.createdAtMillis greaterEq since)
        }.orderBy(AuthOneTimeChallenges.createdAtMillis to SortOrder.DESC).limit(30).toList()
        val retryAt = listOfNotNull(
            identifierRequests.takeIf { it.size >= 5 }?.last()?.get(AuthOneTimeChallenges.createdAtMillis),
            ipRequests.takeIf { it.size >= 30 }?.last()?.get(AuthOneTimeChallenges.createdAtMillis)
        ).maxOrNull()
        if (retryAt != null) throw AitaAuthRateLimitedException(((retryAt + 3_600_000L - now) / 1000L + 1L).coerceAtLeast(1L))
    }

    private fun emailFlow(row: ResultRow, now: Long) = AitaAuthFlowDataModel(
        flowId = row[AuthOneTimeChallenges.publicId].toString(),
        nextStep = AitaAuthNextStep.EMAIL_CODE,
        expiresAtMillis = row[AuthOneTimeChallenges.expiresAtMillis],
        resendAfterMillis = row[AuthOneTimeChallenges.resendAfterMillis],
        serverTimeMillis = now
    )

    /** Caller owns a DB transaction and the request buckets. Unknown accounts get the same flow shape. */
    private fun createEmailChallengeInside(
        userId: UUID?, email: String?, purpose: String, locale: String,
        identifierHash: String, ipHash: String, now: Long
    ): AitaAuthFlowDataModel {
        val id = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        val code = crypto.randomCode()
        val destination = email?.let(::normalizeAitaEmail)
        AuthOneTimeChallenges.insert {
            it[AuthOneTimeChallenges.id] = id
            it[AuthOneTimeChallenges.publicId] = publicId
            it[AuthOneTimeChallenges.userId] = userId
            it[AuthOneTimeChallenges.purpose] = purpose
            it[AuthOneTimeChallenges.identifierHash] = identifierHash
            it[AuthOneTimeChallenges.requestIpHash] = ipHash
            it[AuthOneTimeChallenges.locale] = locale.take(16).ifBlank { "en" }
            it[AuthOneTimeChallenges.codeHash] = crypto.hmac("code:$publicId", code)
            // V95 stores one immutable encrypted provider request, not a second plaintext/delivery code.
            it[AuthOneTimeChallenges.codeCiphertext] = ""
            it[AuthOneTimeChallenges.deliveryEmailHash] = destination?.let { crypto.hmac("delivery-email", it) }
            it[AuthOneTimeChallenges.attempts] = 0
            it[AuthOneTimeChallenges.maxAttempts] = config.maxAttempts
            it[AuthOneTimeChallenges.expiresAtMillis] = now + config.codeTtlMillis
            it[AuthOneTimeChallenges.resendAfterMillis] = now + config.resendCooldownMillis
            it[AuthOneTimeChallenges.createdAtMillis] = now
            it[AuthOneTimeChallenges.updatedAtMillis] = now
        }
        if (userId != null && destination != null) {
            val workId = UUID.randomUUID()
            val copy = aitaAuthEmailCopy(purpose, locale, code, config.codeTtlMillis / 60_000L)
            val json = aitaResendEmailRequestJson(config.fromEmail, destination, copy.subject, copy.html, config.replyTo, copy.text)
            AuthEmailOutbox.insert {
                it[AuthEmailOutbox.id] = workId
                it[AuthEmailOutbox.challengeId] = id
                it[AuthEmailOutbox.payloadCiphertext] = crypto.encrypt("auth-email:$workId", json)
                it[AuthEmailOutbox.status] = AUTH_OUTBOX_PENDING
                it[AuthEmailOutbox.attempts] = 0
                it[AuthEmailOutbox.maxAttempts] = 8
                it[AuthEmailOutbox.nextAttemptAtMillis] = now
                it[AuthEmailOutbox.createdAtMillis] = now
                it[AuthEmailOutbox.updatedAtMillis] = now
            }
            application.log.info("AITA authentication email queued flow={} work={} purpose={}", publicId, workId, purpose)
        }
        auditInside(userId, "EMAIL_CODE_REQUESTED_$purpose", identifierHash, ipHash, now)
        return AitaAuthFlowDataModel(
            flowId = publicId.toString(), nextStep = AitaAuthNextStep.EMAIL_CODE,
            expiresAtMillis = now + config.codeTtlMillis,
            resendAfterMillis = now + config.resendCooldownMillis, serverTimeMillis = now
        )
    }

    suspend fun requestEmailCode(identifier: String, purpose: String, locale: String, ip: String): AitaAuthFlowDataModel {
        require(purpose == AUTH_PURPOSE_LOGIN || purpose == AUTH_PURPOSE_RECOVERY)
        requireEmailDelivery(recovery = purpose == AUTH_PURPOSE_RECOVERY)
        val now = System.currentTimeMillis()
        val normalized = normalizeAitaLoginIdentifier(identifier) ?: throw BadRequestException("Invalid sign-in identifier")
        val identifierHash = crypto.hmac("identifier", normalized.value)
        val ipHash = crypto.hmac("ip", ip)
        if (!limiter.allow("code:ip:$ipHash", 120, now)) throw AitaAuthRateLimitedException(3600L)
        val user = resolveUser(normalized.value)?.takeIf { it.active && normalizeAitaEmail(it.email) != null }
        val flow = newSuspendedTransaction(Dispatchers.IO) {
            lockEmailBucketsInside(identifierHash, ipHash, user?.id)
            val latest = AuthOneTimeChallenges.selectAll().where {
                (AuthOneTimeChallenges.identifierHash eq identifierHash) and (AuthOneTimeChallenges.purpose eq purpose) and
                    AuthOneTimeChallenges.consumedAtMillis.isNull() and AuthOneTimeChallenges.verifiedAtMillis.isNull() and
                    (AuthOneTimeChallenges.expiresAtMillis greater now)
            }.orderBy(AuthOneTimeChallenges.createdAtMillis to SortOrder.DESC).limit(1).forUpdate().singleOrNull()
            // A repeated tap/request during cooldown reuses the flow; it must not invalidate the code in transit.
            if (latest != null && latest[AuthOneTimeChallenges.resendAfterMillis] > now) return@newSuspendedTransaction emailFlow(latest, now)
            checkEmailQuotaInside(identifierHash, ipHash, now)
            AuthOneTimeChallenges.update({
                (AuthOneTimeChallenges.identifierHash eq identifierHash) and
                    (AuthOneTimeChallenges.purpose eq purpose) and AuthOneTimeChallenges.consumedAtMillis.isNull()
            }) { it[AuthOneTimeChallenges.consumedAtMillis] = now; it[AuthOneTimeChallenges.updatedAtMillis] = now }
            createEmailChallengeInside(user?.id, user?.email, purpose, locale, identifierHash, ipHash, now)
        }
        // Only reflect the identifier the caller supplied. A phone request must not disclose the account email.
        return flow.copy(maskedDestination = normalized.takeIf { it.kind == AitaAuthIdentifierKind.EMAIL }?.value?.let(::maskEmail).orEmpty())
    }

    suspend fun resend(
        flowId: String, locale: String, ip: String, expectedPurpose: String, ownerUserId: UUID? = null
    ): AitaAuthFlowDataModel? {
        requireEmailDelivery(recovery = expectedPurpose == AUTH_PURPOSE_RECOVERY)
        val publicId = runCatching { UUID.fromString(flowId) }.getOrNull() ?: return null
        val now = System.currentTimeMillis()
        val ipHash = crypto.hmac("ip", ip)
        if (!limiter.allow("code:ip:$ipHash", 120, now)) throw AitaAuthRateLimitedException(3600L)
        return newSuspendedTransaction(Dispatchers.IO) {
            val peek = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }.singleOrNull()
                ?: return@newSuspendedTransaction null
            if (peek[AuthOneTimeChallenges.purpose] != expectedPurpose) return@newSuspendedTransaction null
            if (expectedPurpose == AUTH_PURPOSE_PHONE && (ownerUserId == null || peek[AuthOneTimeChallenges.userId] != ownerUserId)) return@newSuspendedTransaction null
            lockEmailBucketsInside(peek[AuthOneTimeChallenges.identifierHash], ipHash, peek[AuthOneTimeChallenges.userId])
            val old = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }.forUpdate().singleOrNull()
                ?: return@newSuspendedTransaction null
            if (old[AuthOneTimeChallenges.consumedAtMillis] != null || old[AuthOneTimeChallenges.verifiedAtMillis] != null) return@newSuspendedTransaction null
            if (old[AuthOneTimeChallenges.resendAfterMillis] > now) return@newSuspendedTransaction emailFlow(old, now)
            checkEmailQuotaInside(old[AuthOneTimeChallenges.identifierHash], ipHash, now)
            val phoneChange = if (expectedPurpose == AUTH_PURPOSE_PHONE) {
                AuthPhoneAliasChallenges.selectAll().where { AuthPhoneAliasChallenges.challengePublicId eq publicId }
                    .forUpdate().singleOrNull()?.takeIf { it[AuthPhoneAliasChallenges.userId] == ownerUserId && it[AuthPhoneAliasChallenges.consumedAtMillis] == null }
                    ?: return@newSuspendedTransaction null
            } else null
            val user = old[AuthOneTimeChallenges.userId]?.let { id ->
                Users.selectAll().where { Users.id eq id }.singleOrNull()?.takeIf { it[Users.isActive] }
            }
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq old[AuthOneTimeChallenges.id] }) {
                it[AuthOneTimeChallenges.consumedAtMillis] = now; it[AuthOneTimeChallenges.updatedAtMillis] = now
            }
            val flow = createEmailChallengeInside(
                user?.get(Users.id), user?.get(Users.email), expectedPurpose, locale,
                old[AuthOneTimeChallenges.identifierHash], ipHash, now
            )
            if (phoneChange != null) {
                AuthPhoneAliasChallenges.update({ AuthPhoneAliasChallenges.id eq phoneChange[AuthPhoneAliasChallenges.id] }) { it[AuthPhoneAliasChallenges.consumedAtMillis] = now }
                AuthPhoneAliasChallenges.insert {
                    it[AuthPhoneAliasChallenges.id] = UUID.randomUUID(); it[AuthPhoneAliasChallenges.challengePublicId] = UUID.fromString(flow.flowId)
                    it[AuthPhoneAliasChallenges.userId] = requireNotNull(ownerUserId); it[AuthPhoneAliasChallenges.action] = phoneChange[AuthPhoneAliasChallenges.action]
                    it[AuthPhoneAliasChallenges.requestedPhoneAlias] = phoneChange[AuthPhoneAliasChallenges.requestedPhoneAlias]; it[AuthPhoneAliasChallenges.createdAtMillis] = now
                }
            }
            flow
        }
    }

    suspend fun verifyEmailCode(
        request: AitaEmailCodeVerifyRequestDataModel, expectedPurpose: String, meta: Map<String, String>
    ): AitaAuthFlowDataModel? {
        config.requireAdvancedAuthentication(recovery = expectedPurpose == AUTH_PURPOSE_RECOVERY)
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val code = normalizeAitaOneTimeCode(request.code) ?: return null
        val now = System.currentTimeMillis()
        val verified = newSuspendedTransaction(Dispatchers.IO) {
            val row = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction null
            if (row[AuthOneTimeChallenges.purpose] != expectedPurpose || !authCodeCanBeVerified(
                now, row[AuthOneTimeChallenges.expiresAtMillis], row[AuthOneTimeChallenges.consumedAtMillis],
                row[AuthOneTimeChallenges.verifiedAtMillis], row[AuthOneTimeChallenges.attempts], row[AuthOneTimeChallenges.maxAttempts]
            )) return@newSuspendedTransaction null
            val userId = row[AuthOneTimeChallenges.userId]
            val user = userId?.let { Users.selectAll().where { Users.id eq it }.singleOrNull() }
            val bindingMatches = row[AuthOneTimeChallenges.deliveryEmailHash]?.let { binding ->
                user?.get(Users.email)?.let(::normalizeAitaEmail)?.let { crypto.constantTimeEquals(binding, crypto.hmac("delivery-email", it)) } == true
            } ?: true // pre-V95 codes remain valid until their existing expiry
            val valid = userId != null && user?.get(Users.isActive) == true && bindingMatches &&
                crypto.constantTimeEquals(row[AuthOneTimeChallenges.codeHash], crypto.hmac("code:$publicId", code))
            val ticket = if (valid && expectedPurpose == AUTH_PURPOSE_RECOVERY) crypto.randomToken(32) else ""
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq row[AuthOneTimeChallenges.id] }) {
                it[AuthOneTimeChallenges.attempts] = row[AuthOneTimeChallenges.attempts] + 1; it[AuthOneTimeChallenges.updatedAtMillis] = now
                if (valid) {
                    it[AuthOneTimeChallenges.verifiedAtMillis] = now
                    if (expectedPurpose == AUTH_PURPOSE_LOGIN) it[AuthOneTimeChallenges.consumedAtMillis] = now
                    if (expectedPurpose == AUTH_PURPOSE_RECOVERY) {
                        it[AuthOneTimeChallenges.resetTicketHash] = crypto.hmac("reset:$publicId", ticket)
                        it[AuthOneTimeChallenges.resetTicketExpiresAtMillis] = now + config.resetTtlMillis
                    }
                }
            }
            if (!valid || userId == null) return@newSuspendedTransaction null
            ensureProfileInside(userId, now)
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[AuthSecurityProfiles.emailVerifiedAtMillis] = now; it[AuthSecurityProfiles.updatedAtMillis] = now
            }
            // Verification and single-use consumption/ticket issuance commit under the SAME row lock.
            userId to ticket
        } ?: return null
        return when (expectedPurpose) {
            AUTH_PURPOSE_LOGIN -> if (totpEnabled(verified.first)) createLoginChallenge(verified.first, AUTH_LOGIN_CHALLENGE_EMAIL)
                else AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.AUTHENTICATED, tokenPair = tokenService.newPair(verified.first, meta), serverTimeMillis = now)
            AUTH_PURPOSE_RECOVERY -> AitaAuthFlowDataModel(
                flowId = publicId.toString(), nextStep = AitaAuthNextStep.PASSWORD_RESET,
                expiresAtMillis = now + config.resetTtlMillis, resetTicket = verified.second, serverTimeMillis = now
            )
            else -> null
        }
    }

    suspend fun resetPassword(request: AitaPasswordRecoveryResetRequestDataModel): Boolean {
        config.requireAdvancedAuthentication(recovery = true)
        if (!request.newPassword.checkAsPassword()) return false
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return false
        val now = System.currentTimeMillis()
        return newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val row = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction false
            val userId = row[AuthOneTimeChallenges.userId] ?: return@newSuspendedTransaction false
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@newSuspendedTransaction false
            if (!user[Users.isActive]) return@newSuspendedTransaction false
            val binding = row[AuthOneTimeChallenges.deliveryEmailHash]
            if (binding != null && !crypto.constantTimeEquals(binding, crypto.hmac("delivery-email", normalizeAitaEmail(user[Users.email]).orEmpty()))) return@newSuspendedTransaction false
            val ticketHash = row[AuthOneTimeChallenges.resetTicketHash] ?: return@newSuspendedTransaction false
            val ticketExpires = row[AuthOneTimeChallenges.resetTicketExpiresAtMillis] ?: return@newSuspendedTransaction false
            if (row[AuthOneTimeChallenges.purpose] != AUTH_PURPOSE_RECOVERY || row[AuthOneTimeChallenges.consumedAtMillis] != null || ticketExpires <= now) {
                return@newSuspendedTransaction false
            }
            if (!crypto.constantTimeEquals(ticketHash, crypto.hmac("reset:$publicId", request.resetTicket))) {
                return@newSuspendedTransaction false
            }
            Users.update({ Users.id eq userId }) { it[Users.passwordHash] = Pw.hash(request.newPassword.toCharArray()) }
            RefreshSessions.update({ (RefreshSessions.userId eq userId) and RefreshSessions.revokedAt.isNull() }) {
                it[RefreshSessions.revokedAt] = java.time.Instant.now()
            }
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.userId eq userId }) {
                it[AuthOneTimeChallenges.consumedAtMillis] = now
                it[AuthOneTimeChallenges.updatedAtMillis] = now
            }
            AuthLoginChallenges.update({ (AuthLoginChallenges.userId eq userId) and AuthLoginChallenges.consumedAtMillis.isNull() }) {
                it[AuthLoginChallenges.consumedAtMillis] = now
            }
            auditInside(userId, "PASSWORD_RESET_COMPLETED", row[AuthOneTimeChallenges.identifierHash], row[AuthOneTimeChallenges.requestIpHash], now)
            true
        }
    }

    private suspend fun createLoginChallenge(userId: UUID, method: String): AitaAuthFlowDataModel {
        config.requireSecurityConfigured()
        val now = System.currentTimeMillis()
        val publicId = UUID.randomUUID()
        newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            AuthLoginChallenges.insert {
                it[AuthLoginChallenges.id] = UUID.randomUUID()
                it[AuthLoginChallenges.publicId] = publicId
                it[AuthLoginChallenges.userId] = userId
                it[AuthLoginChallenges.primaryMethod] = method
                it[AuthLoginChallenges.attempts] = 0
                it[AuthLoginChallenges.maxAttempts] = 8
                it[AuthLoginChallenges.expiresAtMillis] = now + 5 * 60_000L
                it[AuthLoginChallenges.createdAtMillis] = now
            }
        }
        return AitaAuthFlowDataModel(
            flowId = publicId.toString(),
            nextStep = AitaAuthNextStep.TOTP,
            expiresAtMillis = now + 5 * 60_000L,
            serverTimeMillis = now
        )
    }

    suspend fun completeTotpLogin(request: AitaTotpLoginRequestDataModel, meta: Map<String, String>): AitaAuthFlowDataModel? {
        config.requireSecurityConfigured()
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val now = System.currentTimeMillis()
        val userId = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val row = AuthLoginChallenges.selectAll().where { AuthLoginChallenges.publicId eq publicId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction null
            if (row[AuthLoginChallenges.consumedAtMillis] != null || row[AuthLoginChallenges.expiresAtMillis] <= now ||
                row[AuthLoginChallenges.attempts] >= row[AuthLoginChallenges.maxAttempts]
            ) return@newSuspendedTransaction null
            val id = row[AuthLoginChallenges.userId]
            val userActive = Users.selectAll().where { Users.id eq id }
                .limit(1).singleOrNull()?.get(Users.isActive) == true
            if (!userActive) return@newSuspendedTransaction null
            val valid = verifySecondFactorInside(id, request.code, now)
            AuthLoginChallenges.update({ AuthLoginChallenges.id eq row[AuthLoginChallenges.id] }) {
                it[AuthLoginChallenges.attempts] = row[AuthLoginChallenges.attempts] + 1
                if (valid) it[AuthLoginChallenges.consumedAtMillis] = now
            }
            if (valid) id else null
        } ?: return null
        audit(userId, "TWO_FACTOR_LOGIN_COMPLETED", null, meta["ip"])
        return AitaAuthFlowDataModel(
            nextStep = AitaAuthNextStep.AUTHENTICATED,
            tokenPair = tokenService.newPair(userId, meta)
        )
    }

    suspend fun settings(userId: UUID): AitaAuthenticationSettingsDataModel = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        val now = System.currentTimeMillis()
        ensureProfileInside(userId, now)
        val user = Users.selectAll().where { Users.id eq userId }.single()
        val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.single()
        val remaining = AuthRecoveryCodes.selectAll().where {
            (AuthRecoveryCodes.userId eq userId) and AuthRecoveryCodes.usedAtMillis.isNull()
        }.count().toInt()
        AitaAuthenticationSettingsDataModel(
            email = user[Users.email],
            emailVerified = profile[AuthSecurityProfiles.emailVerifiedAtMillis] != null,
            phoneLoginAlias = profile[AuthSecurityProfiles.phoneLoginAlias],
            phoneLoginAliasVerified = profile[AuthSecurityProfiles.phoneAliasVerifiedAtMillis] != null,
            authenticatorEnabled = profile[AuthSecurityProfiles.totpEnabledAtMillis] != null,
            recoveryCodesRemaining = remaining,
            securityRevision = profile[AuthSecurityProfiles.securityRevision]
        )
    }

    suspend fun startTotpSetup(
        userId: UUID,
        request: AitaSensitiveSecurityActionRequestDataModel
    ): AitaTotpSetupDataModel? {
        config.requireAdvancedAuthentication()
        if (!verifySensitiveAction(userId, request)) return null
        val now = System.currentTimeMillis()
        val setupId = UUID.randomUUID()
        val secretBytes = ByteArray(20).also(random::nextBytes)
        val secret = base32Encode(secretBytes)
        val expires = now + 10 * 60_000L
        val email = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            ensureProfileInside(userId, now)
            val user = Users.selectAll().where { Users.id eq userId }.single()
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[AuthSecurityProfiles.totpPendingSecretCiphertext] = crypto.encrypt("totp-pending:$userId:$setupId", secret)
                it[AuthSecurityProfiles.totpPendingSetupId] = setupId
                it[AuthSecurityProfiles.totpPendingExpiresAtMillis] = expires
                it[AuthSecurityProfiles.updatedAtMillis] = now
            }
            user[Users.email]
        }
        val label = "AITA:${email.trim().lowercase()}"
        val uri = "otpauth://totp/${urlEncode(label)}?secret=$secret&issuer=${urlEncode(config.issuerName)}&algorithm=SHA1&digits=6&period=30"
        return AitaTotpSetupDataModel(setupId.toString(), secret, uri, expires)
    }

    suspend fun confirmTotpSetup(userId: UUID, request: AitaTotpSetupConfirmRequestDataModel): AitaAuthFlowDataModel? {
        config.requireAdvancedAuthentication()
        val setupId = runCatching { UUID.fromString(request.setupId) }.getOrNull() ?: return null
        val now = System.currentTimeMillis()
        val codes = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction null
            if (profile[AuthSecurityProfiles.totpPendingSetupId] != setupId ||
                (profile[AuthSecurityProfiles.totpPendingExpiresAtMillis] ?: 0L) <= now
            ) return@newSuspendedTransaction null
            val encoded = profile[AuthSecurityProfiles.totpPendingSecretCiphertext] ?: return@newSuspendedTransaction null
            val secret = crypto.decrypt("totp-pending:$userId:$setupId", encoded)
            if (!verifyTotp(secret, request.code, now)) return@newSuspendedTransaction null
            val recovery = generateRecoveryCodesInside(userId, now)
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[AuthSecurityProfiles.totpSecretCiphertext] = crypto.encrypt("totp-active:$userId", secret)
                it[AuthSecurityProfiles.totpPendingSecretCiphertext] = null
                it[AuthSecurityProfiles.totpPendingSetupId] = null
                it[AuthSecurityProfiles.totpPendingExpiresAtMillis] = null
                it[AuthSecurityProfiles.totpEnabledAtMillis] = now
                it[AuthSecurityProfiles.securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1L
                it[AuthSecurityProfiles.updatedAtMillis] = now
            }
            auditInside(userId, "TOTP_ENABLED", null, null, now)
            recovery
        } ?: return null
        return AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.COMPLETE, recoveryCodes = codes)
    }

    suspend fun disableTotp(userId: UUID, request: AitaSensitiveSecurityActionRequestDataModel): AitaAuthenticationSettingsDataModel? {
        config.requireSecurityConfigured()
        if (!verifySensitiveAction(userId, request)) return null
        val now = System.currentTimeMillis()
        newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().single()
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[AuthSecurityProfiles.totpSecretCiphertext] = null
                it[AuthSecurityProfiles.totpEnabledAtMillis] = null
                it[AuthSecurityProfiles.securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1L
                it[AuthSecurityProfiles.updatedAtMillis] = now
            }
            AuthRecoveryCodes.deleteWhere { AuthRecoveryCodes.userId eq userId }
            auditInside(userId, "TOTP_DISABLED", null, null, now)
        }
        return settings(userId)
    }

    suspend fun regenerateRecoveryCodes(userId: UUID, request: AitaSensitiveSecurityActionRequestDataModel): List<String>? {
        config.requireSecurityConfigured()
        if (!verifySensitiveAction(userId, request)) return null
        val now = System.currentTimeMillis()
        return newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().singleOrNull()
                ?: return@newSuspendedTransaction null
            if (profile[AuthSecurityProfiles.totpEnabledAtMillis] == null) return@newSuspendedTransaction null
            val result = generateRecoveryCodesInside(userId, now)
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[AuthSecurityProfiles.securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1L
                it[AuthSecurityProfiles.updatedAtMillis] = now
            }
            auditInside(userId, "RECOVERY_CODES_REGENERATED", null, null, now)
            result
        }
    }

    suspend fun requestPhoneAlias(userId: UUID, request: AitaPhoneAliasRequestDataModel, ip: String): AitaAuthFlowDataModel? {
        config.requireEmailAuthentication()
        if (!verifySensitiveAction(userId, AitaSensitiveSecurityActionRequestDataModel(request.currentPassword, request.secondFactorCode))) return null
        val phone = when (request.action) {
            AitaPhoneAliasAction.ADD_OR_REPLACE -> normalizeAitaPhoneAlias(request.phoneNumber) ?: return null
            AitaPhoneAliasAction.REMOVE -> null
        }
        if (phone != null) {
            val conflict = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
                val profileConflict = AuthSecurityProfiles.selectAll().where {
                    (AuthSecurityProfiles.phoneLoginAlias eq phone) and (AuthSecurityProfiles.userId neq userId)
                }.limit(1).any()
                val legacyUserConflict = Users.selectAll().where {
                    (Users.phoneNumber eq phone) and (Users.id neq userId)
                }.limit(1).any()
                profileConflict || legacyUserConflict
            }
            if (conflict) return null
        }
        val now = System.currentTimeMillis()
        val ipHash = crypto.hmac("ip", ip)
        requireEmailDelivery()
        return newSuspendedTransaction(Dispatchers.IO) {
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull()?.takeIf { it[Users.isActive] }
                ?: return@newSuspendedTransaction null
            val email = normalizeAitaEmail(user[Users.email]) ?: return@newSuspendedTransaction null
            val identifierHash = crypto.hmac("identifier", email)
            lockEmailBucketsInside(identifierHash, ipHash, userId)
            val latest = AuthOneTimeChallenges.selectAll().where {
                (AuthOneTimeChallenges.userId eq userId) and (AuthOneTimeChallenges.purpose eq AUTH_PURPOSE_PHONE)
            }.orderBy(AuthOneTimeChallenges.createdAtMillis to SortOrder.DESC).limit(1).singleOrNull()
            val retryAt = latest?.get(AuthOneTimeChallenges.resendAfterMillis) ?: 0L
            if (retryAt > now) throw AitaAuthRateLimitedException((retryAt - now) / 1000L + 1L)
            checkEmailQuotaInside(identifierHash, ipHash, now)
            AuthOneTimeChallenges.update({
                (AuthOneTimeChallenges.userId eq userId) and (AuthOneTimeChallenges.purpose eq AUTH_PURPOSE_PHONE) and AuthOneTimeChallenges.consumedAtMillis.isNull()
            }) { it[AuthOneTimeChallenges.consumedAtMillis] = now; it[AuthOneTimeChallenges.updatedAtMillis] = now }
            val flow = createEmailChallengeInside(userId, email, AUTH_PURPOSE_PHONE, request.locale, identifierHash, ipHash, now)
            AuthPhoneAliasChallenges.insert {
                it[AuthPhoneAliasChallenges.id] = UUID.randomUUID(); it[AuthPhoneAliasChallenges.challengePublicId] = UUID.fromString(flow.flowId)
                it[AuthPhoneAliasChallenges.userId] = userId; it[AuthPhoneAliasChallenges.action] = request.action.name
                it[AuthPhoneAliasChallenges.requestedPhoneAlias] = phone; it[AuthPhoneAliasChallenges.createdAtMillis] = now
            }
            flow.copy(maskedDestination = maskEmail(email))
        }
    }

    suspend fun confirmPhoneAlias(userId: UUID, request: AitaPhoneAliasConfirmRequestDataModel): AitaAuthenticationSettingsDataModel? {
        config.requireAdvancedAuthentication()
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val code = normalizeAitaOneTimeCode(request.code) ?: return null
        val now = System.currentTimeMillis()
        val applied = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val challenge = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction false
            val phone = AuthPhoneAliasChallenges.selectAll().where { AuthPhoneAliasChallenges.challengePublicId eq publicId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction false
            if (phone[AuthPhoneAliasChallenges.userId] != userId || phone[AuthPhoneAliasChallenges.consumedAtMillis] != null ||
                challenge[AuthOneTimeChallenges.purpose] != AUTH_PURPOSE_PHONE || challenge[AuthOneTimeChallenges.expiresAtMillis] <= now ||
                challenge[AuthOneTimeChallenges.consumedAtMillis] != null ||
                challenge[AuthOneTimeChallenges.attempts] >= challenge[AuthOneTimeChallenges.maxAttempts]
            ) return@newSuspendedTransaction false
            if (challenge[AuthOneTimeChallenges.userId] != userId) return@newSuspendedTransaction false
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull()?.takeIf { it[Users.isActive] }
                ?: return@newSuspendedTransaction false
            val binding = challenge[AuthOneTimeChallenges.deliveryEmailHash]
            if (binding != null && !crypto.constantTimeEquals(binding, crypto.hmac("delivery-email", normalizeAitaEmail(user[Users.email]).orEmpty()))) return@newSuspendedTransaction false
            val valid = crypto.constantTimeEquals(challenge[AuthOneTimeChallenges.codeHash], crypto.hmac("code:$publicId", code))
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq challenge[AuthOneTimeChallenges.id] }) {
                it[AuthOneTimeChallenges.attempts] = challenge[AuthOneTimeChallenges.attempts] + 1
                it[AuthOneTimeChallenges.updatedAtMillis] = now
            }
            if (!valid) return@newSuspendedTransaction false
            ensureProfileInside(userId, now)
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().single()
            val action = AitaPhoneAliasAction.valueOf(phone[AuthPhoneAliasChallenges.action])
            val alias = if (action == AitaPhoneAliasAction.REMOVE) null else phone[AuthPhoneAliasChallenges.requestedPhoneAlias]
            try {
                AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                    it[AuthSecurityProfiles.phoneLoginAlias] = alias
                    it[AuthSecurityProfiles.phoneAliasVerifiedAtMillis] = if (alias == null) null else now
                    it[AuthSecurityProfiles.emailVerifiedAtMillis] = now
                    it[AuthSecurityProfiles.securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1L
                    it[AuthSecurityProfiles.updatedAtMillis] = now
                }
            } catch (_: ExposedSQLException) {
                return@newSuspendedTransaction false
            }
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq challenge[AuthOneTimeChallenges.id] }) {
                it[AuthOneTimeChallenges.consumedAtMillis] = now
                it[AuthOneTimeChallenges.verifiedAtMillis] = now
                it[AuthOneTimeChallenges.updatedAtMillis] = now
            }
            AuthPhoneAliasChallenges.update({ AuthPhoneAliasChallenges.id eq phone[AuthPhoneAliasChallenges.id] }) {
                it[AuthPhoneAliasChallenges.consumedAtMillis] = now
            }
            auditInside(userId, if (alias == null) "PHONE_ALIAS_REMOVED" else "PHONE_ALIAS_CONFIRMED", null, null, now)
            true
        }
        return if (applied) settings(userId) else null
    }

    private suspend fun verifySensitiveAction(userId: UUID, request: AitaSensitiveSecurityActionRequestDataModel): Boolean =
        newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@newSuspendedTransaction false
            if (!user[Users.isActive] || !Pw.verify(request.currentPassword.toCharArray(), user[Users.passwordHash])) return@newSuspendedTransaction false
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.singleOrNull()
            if (profile?.get(AuthSecurityProfiles.totpEnabledAtMillis) != null) verifySecondFactorInside(userId, request.secondFactorCode, System.currentTimeMillis()) else true
        }

    private fun verifySecondFactorInside(userId: UUID, rawCode: String, now: Long): Boolean {
        config.requireSecurityConfigured()
        val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.singleOrNull() ?: return false
        val encrypted = profile[AuthSecurityProfiles.totpSecretCiphertext] ?: return false
        val secret = runCatching { crypto.decrypt("totp-active:$userId", encrypted) }.getOrNull() ?: return false
        if (verifyTotp(secret, rawCode, now)) return true
        val normalizedRecovery = normalizeAitaRecoveryCode(rawCode)
        if (normalizedRecovery.length < 8) return false
        val hash = crypto.hmac("recovery:$userId", normalizedRecovery)
        val row = AuthRecoveryCodes.selectAll().where {
            (AuthRecoveryCodes.userId eq userId) and
                (AuthRecoveryCodes.codeHash eq hash) and
                AuthRecoveryCodes.usedAtMillis.isNull()
        }.forUpdate().singleOrNull() ?: return false
        AuthRecoveryCodes.update({ AuthRecoveryCodes.id eq row[AuthRecoveryCodes.id] }) { it[AuthRecoveryCodes.usedAtMillis] = now }
        return true
    }

    private fun generateRecoveryCodesInside(userId: UUID, now: Long): List<String> {
        config.requireSecurityConfigured()
        AuthRecoveryCodes.deleteWhere { AuthRecoveryCodes.userId eq userId }
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val codes = List(10) {
            val raw = buildString {
                repeat(12) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            raw.take(6) + "-" + raw.drop(6)
        }
        codes.forEach { code ->
            AuthRecoveryCodes.insert {
                it[AuthRecoveryCodes.id] = UUID.randomUUID()
                it[AuthRecoveryCodes.userId] = userId
                it[AuthRecoveryCodes.codeHash] = crypto.hmac("recovery:$userId", normalizeAitaRecoveryCode(code))
                it[AuthRecoveryCodes.createdAtMillis] = now
            }
        }
        return codes
    }

    private fun ensureProfileInside(userId: UUID, now: Long) {
        if (AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.empty()) {
            AuthSecurityProfiles.insertIgnore {
                it[AuthSecurityProfiles.userId] = userId
                it[AuthSecurityProfiles.securityRevision] = 1L
                it[AuthSecurityProfiles.createdAtMillis] = now
                it[AuthSecurityProfiles.updatedAtMillis] = now
            }
        }
    }

    private suspend fun audit(userId: UUID?, event: String, identifierHash: String?, ip: String?) {
        val now = System.currentTimeMillis()
        newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            auditInside(userId, event, identifierHash, ip?.let { crypto.hmac("ip", it) }, now)
        }
    }

    private fun auditInside(userId: UUID?, event: String, identifierHash: String?, ipHash: String?, now: Long) {
        AuthSecurityAuditEvents.insert {
            it[AuthSecurityAuditEvents.id] = UUID.randomUUID()
            it[AuthSecurityAuditEvents.userId] = userId
            it[AuthSecurityAuditEvents.eventType] = event.take(80)
            it[AuthSecurityAuditEvents.identifierHash] = identifierHash
            it[AuthSecurityAuditEvents.ipHash] = ipHash
            it[AuthSecurityAuditEvents.metadata] = "{}"
            it[AuthSecurityAuditEvents.createdAtMillis] = now
        }
    }

    fun startEmailWorker(scope: CoroutineScope) {
        if (!config.emailReady) {
            application.log.warn("AITA authentication email worker disabled: configuration incomplete; password login remains available")
            return
        }
        if (!workerStarted.compareAndSet(false, true)) return
        application.log.info("AITA authentication email worker started provider=resend")
        scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    try {
                        val providerPause = emailUnavailableUntil.get() - System.currentTimeMillis()
                        if (providerPause > 0L) {
                            delay(providerPause.coerceAtMost(60_000L))
                            continue
                        }
                        recoverStaleEmailLeases()
                        val workId = claimEmail()
                        if (workId == null) delay(2_500L) else {
                            processEmail(workId)
                            // A single account can enqueue a burst. Keep this worker below four sends/s;
                            // a provider Retry-After also pauses the entire queue, not only one record.
                            delay(250L)
                        }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (exception: Exception) {
                        // SQL/provider exception messages can contain recipient or encrypted data.
                        application.log.error("AITA authentication email worker error type={}", exception.javaClass.simpleName)
                        delay(5_000L)
                    }
                }
            } finally {
                workerStarted.set(false)
            }
        }
    }

    fun close() {
        if (senderDelegate.isInitialized()) sender.close()
    }

    private suspend fun claimEmail(): UUID? = newSuspendedTransaction(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val row = AuthEmailOutbox.selectAll().where {
            (AuthEmailOutbox.status inList listOf(AUTH_OUTBOX_PENDING, AUTH_OUTBOX_RETRY)) and
                (AuthEmailOutbox.nextAttemptAtMillis lessEq now)
        }.orderBy(AuthEmailOutbox.createdAtMillis to SortOrder.ASC).limit(1).forUpdate().singleOrNull()
            ?: return@newSuspendedTransaction null
        AuthEmailOutbox.update({ AuthEmailOutbox.id eq row[AuthEmailOutbox.id] }) {
            it[AuthEmailOutbox.status] = AUTH_OUTBOX_PROCESSING
            // Count a claim, not only a completed HTTP call: a crashing/decryption-failing job is bounded too.
            it[AuthEmailOutbox.attempts] = row[AuthEmailOutbox.attempts] + 1
            it[AuthEmailOutbox.lockedAtMillis] = now
            it[AuthEmailOutbox.lockedBy] = workerId
            it[AuthEmailOutbox.updatedAtMillis] = now
        }
        row[AuthEmailOutbox.id]
    }

    private suspend fun recoverStaleEmailLeases() = newSuspendedTransaction(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        AuthEmailOutbox.update({
            (AuthEmailOutbox.status eq AUTH_OUTBOX_PROCESSING) and
                (AuthEmailOutbox.lockedAtMillis.isNull() or (AuthEmailOutbox.lockedAtMillis lessEq now - 60_000L))
        }) {
            it[AuthEmailOutbox.status] = AUTH_OUTBOX_RETRY
            it[AuthEmailOutbox.lockedAtMillis] = null
            it[AuthEmailOutbox.lockedBy] = null
            it[AuthEmailOutbox.nextAttemptAtMillis] = now
            it[AuthEmailOutbox.updatedAtMillis] = now
        }
    }

    private data class DeliveryWork(val json: String? = null, val cancellation: String? = null)

    private suspend fun processEmail(workId: UUID) {
        val payload = try {
            newSuspendedTransaction(Dispatchers.IO) {
                val work = AuthEmailOutbox.selectAll().where { AuthEmailOutbox.id eq workId }.singleOrNull()
                    ?: return@newSuspendedTransaction DeliveryWork(cancellation = "WORK_REMOVED")
                if (work[AuthEmailOutbox.status] != AUTH_OUTBOX_PROCESSING || work[AuthEmailOutbox.lockedBy] != workerId)
                    return@newSuspendedTransaction DeliveryWork(cancellation = "LEASE_LOST")
                if (work[AuthEmailOutbox.attempts] > work[AuthEmailOutbox.maxAttempts])
                    return@newSuspendedTransaction DeliveryWork(cancellation = "ATTEMPTS_EXHAUSTED")
                val challenge = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.id eq work[AuthEmailOutbox.challengeId] }.singleOrNull()
                    ?: return@newSuspendedTransaction DeliveryWork(cancellation = "CHALLENGE_REMOVED")
                val userId = challenge[AuthOneTimeChallenges.userId]
                    ?: return@newSuspendedTransaction DeliveryWork(cancellation = "CHALLENGE_UNAVAILABLE")
                val user = Users.selectAll().where { Users.id eq userId }.singleOrNull()
                    ?: return@newSuspendedTransaction DeliveryWork(cancellation = "ACCOUNT_UNAVAILABLE")
                if (!authEmailCanBeDelivered(System.currentTimeMillis(), challenge[AuthOneTimeChallenges.expiresAtMillis],
                        challenge[AuthOneTimeChallenges.consumedAtMillis], challenge[AuthOneTimeChallenges.verifiedAtMillis], user[Users.isActive]))
                    return@newSuspendedTransaction DeliveryWork(cancellation = "CODE_EXPIRED_OR_REPLACED")
                if (challenge[AuthOneTimeChallenges.purpose] == AUTH_PURPOSE_RECOVERY && !config.passwordRecoveryEnabled)
                    return@newSuspendedTransaction DeliveryWork(cancellation = "RECOVERY_DISABLED")
                val destination = normalizeAitaEmail(user[Users.email])
                    ?: return@newSuspendedTransaction DeliveryWork(cancellation = "ACCOUNT_EMAIL_UNAVAILABLE")
                val binding = challenge[AuthOneTimeChallenges.deliveryEmailHash]
                if (binding != null && !crypto.constantTimeEquals(binding, crypto.hmac("delivery-email", destination)))
                    return@newSuspendedTransaction DeliveryWork(cancellation = "ACCOUNT_EMAIL_CHANGED")

                val encrypted = work[AuthEmailOutbox.payloadCiphertext]
                val json = if (!encrypted.isNullOrBlank()) {
                    crypto.decrypt("auth-email:$workId", encrypted)
                } else {
                    // Upgrade a never-submitted pre-V95 job. A previously attempted legacy job has no
                    // immutable request snapshot; do not guess/replay it under an old idempotency key.
                    if (work[AuthEmailOutbox.attempts] > 1)
                        return@newSuspendedTransaction DeliveryWork(cancellation = "LEGACY_RETRY_NEEDS_FRESH_CODE")
                    val code = crypto.decrypt("auth-code:${challenge[AuthOneTimeChallenges.id]}", challenge[AuthOneTimeChallenges.codeCiphertext])
                    val (title, body) = emailCopy(challenge[AuthOneTimeChallenges.purpose], challenge[AuthOneTimeChallenges.locale], code)
                    aitaResendEmailRequestJson(config.fromEmail, destination, title, body, config.replyTo).also { requestJson ->
                        AuthEmailOutbox.update({ AuthEmailOutbox.id eq workId }) {
                            it[AuthEmailOutbox.payloadCiphertext] = crypto.encrypt("auth-email:$workId", requestJson)
                        }
                        AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq challenge[AuthOneTimeChallenges.id] }) {
                            it[AuthOneTimeChallenges.deliveryEmailHash] = crypto.hmac("delivery-email", destination)
                        }
                    }
                }
                // A snapshot is immutable, but its recipient must still belong to this account.
                val recipients = Json.parseToJsonElement(json).jsonObject["to"]?.jsonArray
                if (recipients?.size != 1 || normalizeAitaEmail(recipients.single().jsonPrimitive.contentOrNull.orEmpty()) != destination)
                    return@newSuspendedTransaction DeliveryWork(cancellation = "RECIPIENT_CHANGED")
                DeliveryWork(json = json)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: java.security.GeneralSecurityException) {
            finishEmail(workId, AuthEmailDeliveryResult(false, errorCode = "EMAIL_PAYLOAD_DECRYPTION_FAILED"))
            return
        } catch (error: IllegalArgumentException) {
            finishEmail(workId, AuthEmailDeliveryResult(false, errorCode = "EMAIL_PAYLOAD_INVALID"))
            return
        }
        if (payload.cancellation != null) {
            finishEmail(workId, AuthEmailDeliveryResult(false, errorCode = payload.cancellation), cancelled = true)
            return
        }
        val result = sender.send(workId, requireNotNull(payload.json))
        if (result.success) emailUnavailableUntil.set(0L)
        else if (result.serviceWideFailure) emailUnavailableUntil.set(
            System.currentTimeMillis() + (result.retryAfterMillis ?: 60_000L).coerceIn(15_000L, 60_000L)
        )
        finishEmail(workId, result)
    }

    private suspend fun finishEmail(workId: UUID, result: AuthEmailDeliveryResult, cancelled: Boolean = false) {
        val status = newSuspendedTransaction(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val row = AuthEmailOutbox.selectAll().where { AuthEmailOutbox.id eq workId }.forUpdate().singleOrNull()
                ?: return@newSuspendedTransaction null
            if (row[AuthEmailOutbox.status] != AUTH_OUTBOX_PROCESSING || row[AuthEmailOutbox.lockedBy] != workerId)
                return@newSuspendedTransaction null
            val challenge = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.id eq row[AuthEmailOutbox.challengeId] }.singleOrNull()
            val attempt = row[AuthEmailOutbox.attempts]
            val nextAttempt = now + maxOf(retryDelay(attempt), result.retryAfterMillis ?: 0L)
            val canRetry = result.retry && !cancelled && attempt < row[AuthEmailOutbox.maxAttempts] && challenge != null &&
                authEmailCanBeDelivered(nextAttempt, challenge[AuthOneTimeChallenges.expiresAtMillis],
                    challenge[AuthOneTimeChallenges.consumedAtMillis], challenge[AuthOneTimeChallenges.verifiedAtMillis], true)
            val nextStatus = when {
                result.success -> AUTH_OUTBOX_SENT
                cancelled -> AUTH_OUTBOX_CANCELLED
                canRetry -> AUTH_OUTBOX_RETRY
                else -> AUTH_OUTBOX_FAILED
            }
            AuthEmailOutbox.update({ AuthEmailOutbox.id eq workId }) {
                it[AuthEmailOutbox.status] = nextStatus
                it[AuthEmailOutbox.providerMessageId] = result.messageId?.take(200)
                it[AuthEmailOutbox.lastErrorCode] = result.errorCode?.take(80)
                it[AuthEmailOutbox.lockedAtMillis] = null
                it[AuthEmailOutbox.lockedBy] = null
                it[AuthEmailOutbox.nextAttemptAtMillis] = if (canRetry) nextAttempt else now
                it[AuthEmailOutbox.updatedAtMillis] = now
                if (result.success) it[AuthEmailOutbox.sentAtMillis] = now
                if (!canRetry) it[AuthEmailOutbox.payloadCiphertext] = null
            }
            if (!canRetry) AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq row[AuthEmailOutbox.challengeId] }) {
                it[AuthOneTimeChallenges.codeCiphertext] = ""
                it[AuthOneTimeChallenges.updatedAtMillis] = now
            }
            nextStatus
        } ?: return
        application.log.info("AITA authentication email work={} status={} code={}", workId, status, result.errorCode ?: "OK")
    }

    private fun retryDelay(attempt: Int): Long =
        (2.0.pow(attempt.coerceIn(1, 8)) * 1_000L).toLong() + random.nextLong(750L)

    // Kept byte-for-byte for a never-submitted pre-V95 row; new messages use aitaAuthEmailCopy.
    private fun emailCopy(purpose: String, locale: String, code: String): Pair<String, String> {
        val lang = locale.lowercase(Locale.ROOT)
        val title = when {
            lang.startsWith("ru") -> when (purpose) {
                AUTH_PURPOSE_RECOVERY -> "Восстановление пароля AITA"
                AUTH_PURPOSE_PHONE -> "Подтверждение номера для входа в AITA"
                else -> "Код входа в AITA"
            }
            lang.startsWith("kk") -> when (purpose) {
                AUTH_PURPOSE_RECOVERY -> "AITA құпия сөзін қалпына келтіру"
                AUTH_PURPOSE_PHONE -> "AITA кіру нөмірін растау"
                else -> "AITA кіру коды"
            }
            else -> when (purpose) {
                AUTH_PURPOSE_RECOVERY -> "Restore your AITA password"
                AUTH_PURPOSE_PHONE -> "Confirm your AITA login phone"
                else -> "Your AITA sign-in code"
            }
        }
        val instruction = when {
            lang.startsWith("ru") -> "Введите этот одноразовый код в AITA. Никому его не сообщайте."
            lang.startsWith("kk") -> "Бұл бір реттік кодты AITA қолданбасына енгізіңіз. Оны ешкімге бермеңіз."
            else -> "Enter this one-time code in AITA. Never share it with anyone."
        }
        val expiryNotice = when {
            lang.startsWith("ru") -> "Код скоро истечёт. Служба поддержки AITA никогда не попросит сообщить его."
            lang.startsWith("kk") -> "Кодтың мерзімі жақында аяқталады. AITA қолдау қызметі оны ешқашан сұрамайды."
            else -> "This code expires soon. AITA support will never ask you for it."
        }
        return title to """<div style=\"font-family:system-ui,sans-serif;max-width:520px;margin:auto;padding:24px\"><h2>AITA</h2><p>$instruction</p><div style=\"font-size:34px;font-weight:700;letter-spacing:8px;padding:18px 0\">$code</div><p style=\"color:#666\">$expiryNotice</p></div>"""
    }

    private fun maskEmail(email: String): String {
        val clean = email.trim()
        if ('@' !in clean) return ""
        val local = clean.substringBefore('@')
        val domain = clean.substringAfter('@')
        val shown = local.take(2)
        return shown + "•".repeat((local.length - shown.length).coerceIn(2, 8)) + "@$domain"
    }

    private fun verifyTotp(secret: String, rawCode: String, nowMillis: Long): Boolean {
        val code = normalizeAitaOneTimeCode(rawCode) ?: return false
        val step = nowMillis / 30_000L
        return (-1L..1L).any { offset -> totp(secret, step + offset) == code }
    }

    private fun totp(secret: String, counter: Long): String {
        val key = base32Decode(secret)
        val data = ByteArray(8)
        for (i in 7 downTo 0) data[7 - i] = ((counter ushr (i * 8)) and 0xff).toByte()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    private fun base32Encode(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bitsLeft = 0
        val out = StringBuilder()
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                out.append(alphabet[(buffer shr (bitsLeft - 5)) and 31])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) out.append(alphabet[(buffer shl (5 - bitsLeft)) and 31])
        return out.toString()
    }

    private fun base32Decode(value: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bitsLeft = 0
        val out = ArrayList<Byte>()
        value.uppercase(Locale.ROOT).filter { it != '=' && !it.isWhitespace() }.forEach { char ->
            val index = alphabet.indexOf(char)
            require(index >= 0) { "Invalid Base32" }
            buffer = (buffer shl 5) or index
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out += ((buffer shr (bitsLeft - 8)) and 0xff).toByte()
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}

private val AdvancedAuthServiceKey = AttributeKey<AitaAdvancedAuthService>("AITA.AdvancedAuthentication.Service")

private fun advancedAuthService(tokenService: TokenService, application: Application): AitaAdvancedAuthService =
    synchronized(application) {
        application.attributes.getOrNull(AdvancedAuthServiceKey) ?: run {
            val config = AdvancedAuthConfig.load(
                environmentName = application.environment.config.propertyOrNull("app.environment")?.getString()
            )
            if (config.enabled && !config.securityConfigured) application.log.warn(
                "AITA optional account security unavailable: {}. Password login remains available; existing second factors are not bypassed.",
                config.configurationIssue
            )
            AitaAdvancedAuthService(tokenService, config, application).also { service ->
                application.attributes.put(AdvancedAuthServiceKey, service)
                application.monitor.subscribe(ApplicationStopped) { service.close() }
            }
        }
    }

private fun ApplicationCall.authClientIp(): String = aitaAuthClientIp(
    request.origin.remoteHost, request.headers["X-Forwarded-For"], request.headers["X-AITA-Edge"]
)

private fun authMeta(call: ApplicationCall, deviceInfo: kz.aita.ClientDeviceInfoDataModel?): Map<String, String> =
    metaFrom(call, deviceInfo) + ("ip" to call.authClientIp())

suspend fun advancedAuthSecondFactorEnabled(userId: UUID): Boolean =
    newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }
            .limit(1).singleOrNull()?.get(AuthSecurityProfiles.totpEnabledAtMillis) != null
    }

suspend fun resolveAdvancedAuthUser(identifier: String): UUID? {
    val normalized = normalizeAitaLoginIdentifier(identifier) ?: return null
    return newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        when (normalized.kind) {
            AitaAuthIdentifierKind.EMAIL -> Users.selectAll().where { Users.email.lowerCase() eq normalized.value }.limit(2).toList().singleOrNull()?.get(Users.id)
            AitaAuthIdentifierKind.PHONE -> AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.phoneLoginAlias eq normalized.value }
                .limit(1).singleOrNull()?.get(AuthSecurityProfiles.userId)
                ?: Users.selectAll().where { Users.phoneNumber eq normalized.value }.limit(1).singleOrNull()?.get(Users.id)
        }
    }
}

fun Route.installAitaAdvancedAuthenticationRoutes(
    tokenService: TokenService,
    backgroundScope: CoroutineScope,
    application: Application
) {
    val service = advancedAuthService(tokenService, application)
    service.startEmailWorker(backgroundScope)

    route("/auth") {
        intercept(ApplicationCallPipeline.Plugins) {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        }
        get("/capabilities") {
            call.genericResponse(HttpStatusCode.OK, service.capabilities())
        }

        post("/login/password") {
            val request = call.receiveAita<AitaPasswordLoginRequestDataModel>()
            val result = service.passwordLogin(request.copy(deviceInfo = request.deviceInfo), authMeta(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                authMessage("Invalid login or password", "Неверный логин или пароль", "Логин немесе құпиясөз қате")
            ) else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/login/code/request") {
            val request = call.receiveAita<AitaEmailCodeRequestDataModel>()
            val result = service.requestEmailCode(request.identifier, AUTH_PURPOSE_LOGIN, request.locale, call.authClientIp())
            call.genericResponse(HttpStatusCode.Accepted, result)
        }

        post("/login/code/resend") {
            val request = call.receiveAita<AitaEmailCodeResendRequestDataModel>()
            val result = service.resend(request.flowId, request.locale, call.authClientIp(), AUTH_PURPOSE_LOGIN)
            if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized,
                authMessage("Request a new code", "Запросите новый код", "Жаңа код сұраңыз"))
            else call.genericResponse(HttpStatusCode.Accepted, result)
        }

        post("/login/code/verify") {
            val request = call.receiveAita<AitaEmailCodeVerifyRequestDataModel>()
            val result = service.verifyEmailCode(request, AUTH_PURPOSE_LOGIN, authMeta(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                authMessage("The code is invalid or expired", "Код неверен или истёк", "Код қате немесе мерзімі аяқталған")
            ) else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/login/totp") {
            val request = call.receiveAita<AitaTotpLoginRequestDataModel>()
            val result = service.completeTotpLogin(request, authMeta(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                authMessage("The authenticator or recovery code is invalid", "Код аутентификатора или резервный код неверен", "Аутентификатор немесе қалпына келтіру коды қате")
            ) else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/password-recovery/request") {
            val request = call.receiveAita<AitaEmailCodeRequestDataModel>()
            val result = service.requestEmailCode(request.identifier, AUTH_PURPOSE_RECOVERY, request.locale, call.authClientIp())
            call.genericResponse(HttpStatusCode.Accepted, result)
        }

        post("/password-recovery/resend") {
            val request = call.receiveAita<AitaEmailCodeResendRequestDataModel>()
            val result = service.resend(request.flowId, request.locale, call.authClientIp(), AUTH_PURPOSE_RECOVERY)
            if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized,
                authMessage("Request a new code", "Запросите новый код", "Жаңа код сұраңыз"))
            else call.genericResponse(HttpStatusCode.Accepted, result)
        }

        post("/password-recovery/verify") {
            val request = call.receiveAita<AitaEmailCodeVerifyRequestDataModel>()
            val result = service.verifyEmailCode(request, AUTH_PURPOSE_RECOVERY, authMeta(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                authMessage("The code is invalid or expired", "Код неверен или истёк", "Код қате немесе мерзімі аяқталған")
            ) else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/password-recovery/reset") {
            val request = call.receiveAita<AitaPasswordRecoveryResetRequestDataModel>()
            if (!request.newPassword.checkAsPassword()) {
                return@post call.genericResponseNoPayload(
                    HttpStatusCode.BadRequest,
                    authMessage(
                        "Password must contain at least 8 characters, a digit and a special character",
                        "Пароль должен содержать не менее 8 символов, цифру и специальный символ",
                        "Құпия сөз кемінде 8 таңба, сан және арнайы таңба қамтуы керек"
                    )
                )
            }
            if (service.resetPassword(request)) call.genericResponse(
                HttpStatusCode.OK,
                AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.COMPLETE),
                authMessage("Password restored. Sign in with the new password.", "Пароль восстановлен. Войдите с новым паролем.", "Құпия сөз қалпына келтірілді. Жаңа құпия сөзбен кіріңіз.")
            ) else call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                authMessage("The recovery session is invalid or expired", "Сеанс восстановления недействителен или истёк", "Қалпына келтіру сеансы жарамсыз немесе мерзімі аяқталған")
            )
        }

        authenticate("auth-jwt") {
            route("/security") {
                get("/settings") {
                    val userId = call.checkPrincipal() ?: return@get
                    call.genericResponse(HttpStatusCode.OK, service.settings(userId))
                }

                post("/totp/setup/start") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaSensitiveSecurityActionRequestDataModel>()
                    val result = service.startTotpSetup(userId, request)
                    if (result == null) {
                        call.genericResponseNoPayload(
                            HttpStatusCode.Unauthorized,
                            authMessage(
                                "Security confirmation failed",
                                "Не удалось подтвердить действие",
                                "Қауіпсіздік растауы сәтсіз"
                            )
                        )
                    } else {
                        call.genericResponse(HttpStatusCode.OK, result)
                    }
                }

                post("/totp/setup/confirm") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaTotpSetupConfirmRequestDataModel>()
                    val result = service.confirmTotpSetup(userId, request)
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized, authMessage("Authenticator code is invalid", "Код аутентификатора неверен", "Аутентификатор коды қате"))
                    else call.genericResponse(HttpStatusCode.OK, result)
                }

                post("/totp/disable") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaSensitiveSecurityActionRequestDataModel>()
                    val result = service.disableTotp(userId, request)
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized, authMessage("Security confirmation failed", "Не удалось подтвердить действие", "Қауіпсіздік растауы сәтсіз"))
                    else call.genericResponse(HttpStatusCode.OK, result)
                }

                post("/totp/recovery-codes/regenerate") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaSensitiveSecurityActionRequestDataModel>()
                    val codes = service.regenerateRecoveryCodes(userId, request)
                    if (codes == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized, authMessage("Security confirmation failed", "Не удалось подтвердить действие", "Қауіпсіздік растауы сәтсіз"))
                    else call.genericResponse(HttpStatusCode.OK, AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.COMPLETE, recoveryCodes = codes))
                }

                post("/phone/request") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaPhoneAliasRequestDataModel>()
                    val result = service.requestPhoneAlias(userId, request, call.authClientIp())
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest, authMessage("Phone change could not be requested", "Не удалось запросить изменение номера", "Телефон өзгерісін сұрау мүмкін болмады"))
                    else call.genericResponse(HttpStatusCode.Accepted, result)
                }

                post("/phone/resend") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaEmailCodeResendRequestDataModel>()
                    val result = service.resend(request.flowId, request.locale, call.authClientIp(), AUTH_PURPOSE_PHONE, userId)
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized,
                        authMessage("Request a new code", "Запросите новый код", "Жаңа код сұраңыз"))
                    else call.genericResponse(HttpStatusCode.Accepted, result)
                }

                post("/phone/confirm") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaPhoneAliasConfirmRequestDataModel>()
                    val result = service.confirmPhoneAlias(userId, request)
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized, authMessage("The confirmation code is invalid or expired", "Код подтверждения неверен или истёк", "Растау коды қате немесе мерзімі аяқталған"))
                    else call.genericResponse(HttpStatusCode.OK, result)
                }
            }
        }
    }
}
