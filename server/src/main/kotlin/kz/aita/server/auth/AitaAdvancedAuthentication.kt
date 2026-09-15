package kz.aita.server.auth

import kz.aita.eventMessage
import kz.aita.normalizeAuthEmailLocale
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

private const val AUTH_PURPOSE_EMAIL_FACTOR = "LOGIN_EMAIL_FACTOR"
private const val AUTH_PURPOSE_SECURITY_EMAIL = "SECURITY_EMAIL_PROOF"
private const val AUTH_PURPOSE_LOGIN = "PASSWORDLESS_LOGIN"
private const val AUTH_PURPOSE_RECOVERY = "PASSWORD_RECOVERY"
private const val AUTH_PURPOSE_PHONE = "PHONE_ALIAS"
private const val AUTH_PURPOSE_EMAIL_ALIAS = "EMAIL_ALIAS"
private const val AUTH_PURPOSE_TOTP_RECOVERY = "TOTP_RECOVERY"
private const val AUTH_PURPOSE_TOTP_NOTICE = "TOTP_RESET_NOTICE"
private const val AUTH_OUTBOX_PENDING = "PENDING"
private const val AUTH_OUTBOX_PROCESSING = "PROCESSING"
private const val AUTH_OUTBOX_RETRY = "RETRY_WAIT"
private const val AUTH_OUTBOX_SENT = "SENT"
private const val AUTH_OUTBOX_FAILED = "FAILED"
private const val AUTH_OUTBOX_CANCELLED = "CANCELLED"
private const val AUTH_LOGIN_CHALLENGE_PASSWORD = "PASSWORD"
private const val AUTH_LOGIN_CHALLENGE_EMAIL = "EMAIL_CODE"
private const val AUTH_LOGIN_CHALLENGE_AUTHENTICATOR = "AUTHENTICATOR"

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
    val totpRequiredForLogin = bool("totp_required_for_login").default(true)
    val emailRequiredForLogin = bool("email_required_for_login").default(false)
    val totpLastUsedStep = long("totp_last_used_step").nullable()
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
    val authorizationHash = char("authorization_hash", 64).nullable()
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
    val authorizationHash = char("authorization_hash", 64).nullable()
    val id = uuid("id")
    val challengePublicId = uuid("challenge_public_id").uniqueIndex()
    val userId = uuid("user_id").index()
    val action = varchar("action", 32)
    val requestedPhoneAlias = varchar("requested_phone_alias", 32).nullable()
    val consumedAtMillis = long("consumed_at_millis").nullable()
    val createdAtMillis = long("created_at_millis")
    override val primaryKey = PrimaryKey(id)
}

object AuthLoginEmails : Table("auth_login_emails") {
    val emailNormalized = varchar("email_normalized", 254)
    val userId = uuid("user_id")
    val isPrimary = bool("is_primary")
    val verifiedAtMillis = long("verified_at_millis").nullable()
    val createdAtMillis = long("created_at_millis")
    override val primaryKey = PrimaryKey(emailNormalized)
}

object AuthEmailAliasChallenges : Table("auth_email_alias_challenges") {
    val challengePublicId = uuid("challenge_public_id")
    val userId = uuid("user_id")
    val requestedEmail = varchar("requested_email", 254)
    val authorizationHash = char("authorization_hash", 64)
    val consumedAtMillis = long("consumed_at_millis").nullable()
    val createdAtMillis = long("created_at_millis")
    override val primaryKey = PrimaryKey(challengePublicId)
}

object AuthTotpRecoveryChallenges : Table("auth_totp_recovery_challenges") {
    val challengePublicId = uuid("challenge_public_id")
    val userId = uuid("user_id")
    val authorizationHash = char("authorization_hash", 64)
    val createdAtMillis = long("created_at_millis")
    override val primaryKey = PrimaryKey(challengePublicId)
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

object AuthLoginEmailChallenges : Table("auth_login_email_challenges") {
    val challengePublicId = uuid("challenge_public_id")
    val loginPublicId = uuid("login_public_id")
    override val primaryKey = PrimaryKey(challengePublicId)
}
object AuthSecurityEmailChallenges : Table("auth_security_email_challenges") {
    val challengePublicId = uuid("challenge_public_id")
    val userId = uuid("user_id")
    val action = varchar("action", 40)
    val targetHash = char("target_hash", 64)
    val authorizationHash = char("authorization_hash", 64)
    override val primaryKey = PrimaryKey(challengePublicId)
}

internal enum class AuthContactConflict { PHONE, EMAIL, EXTRA_LIMIT, MAIN_EMAIL_VERIFICATION }
internal class AitaAuthContactConflictException(val conflict: AuthContactConflict) : IllegalStateException(conflict.name)

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

internal data class AuthUser(val id: UUID, val email: String, val passwordHash: String, val active: Boolean)

internal class AitaAdvancedAuthService(
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
    private val dummyPasswordHash by lazy { Pw.hash(crypto.randomToken().toCharArray()) }

    private fun requireActionBudget(key: String, maximum: Int, now: Long) {
        val decision = limiter.check(key, maximum, now)
        if (!decision.allowed) throw AitaAuthRateLimitedException(decision.retryAfterSeconds)
    }

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
                AitaAuthIdentifierKind.EMAIL -> AuthLoginEmails.selectAll()
                    .where { (AuthLoginEmails.emailNormalized eq normalized.value) and
                        ((AuthLoginEmails.isPrimary eq true) or AuthLoginEmails.verifiedAtMillis.isNotNull()) }
                    .singleOrNull()?.get(AuthLoginEmails.userId)
                AitaAuthIdentifierKind.PHONE -> resolvePhoneLoginUserInside(normalized.value)
            } ?: return@newSuspendedTransaction null
            Users.selectAll().where { Users.id eq userId }.limit(1).singleOrNull()?.let {
                AuthUser(it[Users.id], it[Users.email], it[Users.passwordHash], it[Users.isActive])
            }
        }
    }

    suspend fun totpRequiredForLogin(userId: UUID): Boolean = advancedAuthSecondFactorEnabled(userId)

    fun capabilities(): AitaAuthCapabilitiesDataModel = config.capabilities().copy(
        emailDeliveryUnavailable = emailUnavailableUntil.get() > System.currentTimeMillis(),
        authenticatorLoginPolicyEnabled = config.securityConfigured,
        authenticatorCodeLoginEnabled = config.advancedReady,
        authenticatorEmailRecoveryEnabled = config.emailReady,
        emailSecondFactorEnabled = config.emailReady,
        contactEmailVerificationEnabled = config.emailReady,
        registrationEmailVerificationRequired = true
    )

    suspend fun passwordLogin(request: AitaPasswordLoginRequestDataModel, meta: Map<String, String>): AitaAuthFlowDataModel? {
        if (request.password.length !in 1..1024) return null
        val now = System.currentTimeMillis()
        val normalized = normalizeAitaLoginIdentifier(request.identifier) ?: return null
        val identifierHash = crypto.hmac("password-login", normalized.value)
        val ipHash = crypto.hmac("ip", meta["ip"].orEmpty())
        val identifierLimit = limiter.check("password:id:$identifierHash", 12, now)
        val limit = if (!identifierLimit.allowed) identifierLimit else limiter.check("password:ip:$ipHash", 60, now)
        if (!limit.allowed) {
            delay((120L..260L).random())
            audit(null, "PASSWORD_LOGIN_RATE_LIMITED", identifierHash, meta["ip"])
            // The password was not checked. Do not misreport a quota as invalid credentials.
            throw AitaAuthRateLimitedException(limit.retryAfterSeconds)
        }
        val resolved = resolveUser(normalized.value) ?: return null
        val checked = newSuspendedTransaction(Dispatchers.IO) {
            val user = lockSecurityUserInside(resolved.id) ?: return@newSuspendedTransaction null
            if (!user[Users.isActive] || !Pw.verify(request.password.toCharArray(), user[Users.passwordHash])) return@newSuspendedTransaction null
            val authorization = loginAuthorizationInside(user)
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq resolved.id }.singleOrNull()
            auditInside(resolved.id, "PASSWORD_PRIMARY_VERIFIED", null, ipHash, now)
            if (loginFactorInside(profile) != AitaLoginSecondFactor.NONE) {
                createLoginChallengeInside(resolved.id, AUTH_LOGIN_CHALLENGE_PASSWORD, authorization, now) to authorization
            } else null to authorization
        } ?: return null
        checked.first?.let { return it }
        val tokens = tokenService.newPairAfterVerification(resolved.id, meta) {
            loginAuthorizationStillValidInside(resolved.id, checked.second, requireOptionalTotp = true)
        } ?: return null
        return AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.AUTHENTICATED, tokenPair = tokens)
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
        identifierHash: String, ipHash: String, now: Long,
        deadlineMillis: Long = now + config.codeTtlMillis
    ): AitaAuthFlowDataModel {
        require(deadlineMillis > now)
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
            it[AuthOneTimeChallenges.locale] = normalizeAuthEmailLocale(locale)
            it[AuthOneTimeChallenges.codeHash] = crypto.hmac("code:$publicId", code)
            // V95 stores one immutable encrypted provider request, not a second plaintext/delivery code.
            it[AuthOneTimeChallenges.codeCiphertext] = ""
            it[AuthOneTimeChallenges.deliveryEmailHash] = destination?.let { crypto.hmac("delivery-email", it) }
            it[AuthOneTimeChallenges.attempts] = 0
            it[AuthOneTimeChallenges.maxAttempts] = config.maxAttempts
            it[AuthOneTimeChallenges.expiresAtMillis] = deadlineMillis
            it[AuthOneTimeChallenges.resendAfterMillis] = now + config.resendCooldownMillis
            it[AuthOneTimeChallenges.createdAtMillis] = now
            it[AuthOneTimeChallenges.updatedAtMillis] = now
        }
        if (destination != null && (userId != null || purpose == AUTH_PURPOSE_CONTACT)) {
            val workId = UUID.randomUUID()
            val copy = aitaAuthEmailCopy(purpose, locale, code, ((deadlineMillis - now + 59_999L) / 60_000L).coerceAtLeast(1L))
            val json = aitaResendEmailRequestJson(config.fromEmail, destination, copy.subject, copy.html, config.replyTo, copy.text, copy.inlineImages)
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
            expiresAtMillis = deadlineMillis,
            resendAfterMillis = now + config.resendCooldownMillis, serverTimeMillis = now
        )
    }

    suspend fun requestEmailCode(identifier: String, purpose: String, locale: String, ip: String, destinationChoice: AitaEmailDestination = AitaEmailDestination.MAIN): AitaAuthFlowDataModel {
        require(purpose == AUTH_PURPOSE_LOGIN || purpose == AUTH_PURPOSE_RECOVERY)
        requireEmailDelivery(recovery = purpose == AUTH_PURPOSE_RECOVERY)
        val now = System.currentTimeMillis()
        val normalized = normalizeAitaLoginIdentifier(identifier) ?: throw BadRequestException("Invalid sign-in identifier")
        val identifierHash = crypto.hmac("identifier", normalized.value)
        val ipHash = crypto.hmac("ip", ip)
        if (!limiter.allow("code:ip:$ipHash", 120, now)) throw AitaAuthRateLimitedException(3600L)
        val resolvedUser = resolveUser(normalized.value)
        var requestDisposition = "NO_UNIQUE_ACCOUNT"
        val flow = newSuspendedTransaction(Dispatchers.IO) {
            // Every email producer/resender locks its owner before request buckets and challenges.
            // Re-read the primary identity and destination after the lock, not a stale profile snapshot.
            val currentUser = resolvedUser?.id?.let(::lockSecurityUserInside)
            val stillOwned = currentUser != null && when (normalized.kind) {
                AitaAuthIdentifierKind.PHONE -> resolvePhoneLoginUserInside(normalized.value) == currentUser[Users.id]
                AitaAuthIdentifierKind.EMAIL -> AuthLoginEmails.selectAll().where {
                    (AuthLoginEmails.emailNormalized eq normalized.value) and
                        ((AuthLoginEmails.isPrimary eq true) or AuthLoginEmails.verifiedAtMillis.isNotNull())
                }.singleOrNull()?.get(AuthLoginEmails.userId) == currentUser[Users.id]
            }
            val destination = currentUser?.takeIf { stillOwned && it[Users.isActive] }
                ?.let {
                    if (purpose == AUTH_PURPOSE_LOGIN && normalized.kind == AitaAuthIdentifierKind.PHONE)
                        emailDestinationInside(it, destinationChoice)
                    else aitaEmailCodeDestination(normalized, it[Users.email])
                }
            val owner = currentUser?.get(Users.id)?.takeIf { destination != null }
            requestDisposition = when {
                currentUser == null || !stillOwned -> "NO_UNIQUE_ACCOUNT"
                !currentUser[Users.isActive] -> "INACTIVE_ACCOUNT"
                destination == null -> "NO_MAIN_EMAIL"
                else -> "QUEUED"
            }
            lockEmailBucketsInside(identifierHash, ipHash, owner)
            val latest = AuthOneTimeChallenges.selectAll().where {
                (AuthOneTimeChallenges.identifierHash eq identifierHash) and (AuthOneTimeChallenges.purpose eq purpose) and
                    AuthOneTimeChallenges.consumedAtMillis.isNull() and AuthOneTimeChallenges.verifiedAtMillis.isNull() and
                    (AuthOneTimeChallenges.expiresAtMillis greater now)
            }.orderBy(AuthOneTimeChallenges.createdAtMillis to SortOrder.DESC).limit(1).forUpdate().singleOrNull()
            // A repeated tap/request during cooldown reuses the flow; it must not invalidate the code in transit.
            val sameRecipient = latest?.get(AuthOneTimeChallenges.deliveryEmailHash) ==
                destination?.let { crypto.hmac("delivery-email", it) }
            val sameOwner = latest?.get(AuthOneTimeChallenges.userId) == owner
            if (latest != null && sameOwner && sameRecipient &&
                latest[AuthOneTimeChallenges.attempts] < latest[AuthOneTimeChallenges.maxAttempts] &&
                latest[AuthOneTimeChallenges.resendAfterMillis] > now) {
                requestDisposition = if (latest[AuthOneTimeChallenges.userId] == null) "REUSED_NEUTRAL_FLOW" else "REUSED_FLOW"
                return@newSuspendedTransaction emailFlow(latest, now)
            }
            checkEmailQuotaInside(identifierHash, ipHash, now)
            AuthOneTimeChallenges.update({
                (AuthOneTimeChallenges.identifierHash eq identifierHash) and
                    (AuthOneTimeChallenges.purpose eq purpose) and AuthOneTimeChallenges.consumedAtMillis.isNull()
            }) { it[AuthOneTimeChallenges.consumedAtMillis] = now; it[AuthOneTimeChallenges.updatedAtMillis] = now }
            createEmailChallengeInside(owner, destination, purpose, locale, identifierHash, ipHash, now)
        }
        // Log after commit, with neither the supplied phone nor the destination address/code.
        application.log.info("AITA authentication email request flow={} purpose={} identifierKind={} outcome={}",
            flow.flowId, purpose, normalized.kind, requestDisposition)
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
            if (expectedPurpose in setOf(AUTH_PURPOSE_PHONE, AUTH_PURPOSE_EMAIL_ALIAS) && (ownerUserId == null || peek[AuthOneTimeChallenges.userId] != ownerUserId)) return@newSuspendedTransaction null
            val lockedUser = peek[AuthOneTimeChallenges.userId]?.let(::lockSecurityUserInside)
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
            val user = lockedUser?.takeIf { it[Users.isActive] }
            val destination = user?.let { challengeDestinationInside(old, it) }
            if (user != null && destination == null) return@newSuspendedTransaction null
            val emailChange = if (expectedPurpose == AUTH_PURPOSE_EMAIL_ALIAS)
                AuthEmailAliasChallenges.selectAll().where { AuthEmailAliasChallenges.challengePublicId eq publicId }.singleOrNull()
                    ?: return@newSuspendedTransaction null
                else null
            val authenticatorRecovery = if (expectedPurpose == AUTH_PURPOSE_TOTP_RECOVERY && user != null)
                AuthTotpRecoveryChallenges.selectAll().where { AuthTotpRecoveryChallenges.challengePublicId eq publicId }.singleOrNull()
                    ?: return@newSuspendedTransaction null
                else null
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq old[AuthOneTimeChallenges.id] }) {
                it[AuthOneTimeChallenges.consumedAtMillis] = now; it[AuthOneTimeChallenges.updatedAtMillis] = now
            }
            val flow = createEmailChallengeInside(
                user?.get(Users.id), destination, expectedPurpose, locale,
                old[AuthOneTimeChallenges.identifierHash], ipHash, now,
                deadlineMillis = authenticatorRecovery?.get(AuthTotpRecoveryChallenges.createdAtMillis)?.plus(config.codeTtlMillis)
                    ?: phoneChange?.get(AuthPhoneAliasChallenges.createdAtMillis)?.plus(config.codeTtlMillis)
                    ?: emailChange?.get(AuthEmailAliasChallenges.createdAtMillis)?.plus(config.codeTtlMillis)
                    ?: (now + config.codeTtlMillis)
            )
            if (authenticatorRecovery != null) AuthTotpRecoveryChallenges.insert {
                it[challengePublicId] = UUID.fromString(flow.flowId)
                it[userId] = authenticatorRecovery[AuthTotpRecoveryChallenges.userId]
                it[authorizationHash] = authenticatorRecovery[AuthTotpRecoveryChallenges.authorizationHash]
                // Resending never renews the original password proof.
                it[createdAtMillis] = authenticatorRecovery[AuthTotpRecoveryChallenges.createdAtMillis]
            }
            if (emailChange != null) {
                AuthEmailAliasChallenges.update({ AuthEmailAliasChallenges.challengePublicId eq publicId }) { it[AuthEmailAliasChallenges.consumedAtMillis] = now }
                AuthEmailAliasChallenges.insert {
                    it[AuthEmailAliasChallenges.challengePublicId] = UUID.fromString(flow.flowId); it[AuthEmailAliasChallenges.userId] = requireNotNull(ownerUserId)
                    it[AuthEmailAliasChallenges.requestedEmail] = emailChange[AuthEmailAliasChallenges.requestedEmail]
                    it[AuthEmailAliasChallenges.authorizationHash] = emailChange[AuthEmailAliasChallenges.authorizationHash]; it[AuthEmailAliasChallenges.createdAtMillis] = emailChange[AuthEmailAliasChallenges.createdAtMillis]
                }
            }
            if (phoneChange != null) {
                AuthPhoneAliasChallenges.update({ AuthPhoneAliasChallenges.id eq phoneChange[AuthPhoneAliasChallenges.id] }) { it[AuthPhoneAliasChallenges.consumedAtMillis] = now }
                AuthPhoneAliasChallenges.insert {
                    it[AuthPhoneAliasChallenges.id] = UUID.randomUUID(); it[AuthPhoneAliasChallenges.challengePublicId] = UUID.fromString(flow.flowId)
                    it[AuthPhoneAliasChallenges.userId] = requireNotNull(ownerUserId); it[AuthPhoneAliasChallenges.action] = phoneChange[AuthPhoneAliasChallenges.action]
                    it[AuthPhoneAliasChallenges.requestedPhoneAlias] = phoneChange[AuthPhoneAliasChallenges.requestedPhoneAlias]
                    it[AuthPhoneAliasChallenges.authorizationHash] = phoneChange[AuthPhoneAliasChallenges.authorizationHash]
                    it[AuthPhoneAliasChallenges.createdAtMillis] = phoneChange[AuthPhoneAliasChallenges.createdAtMillis]
                }
            }
            flow
        }
    }

    suspend fun verifyEmailCode(
        request: AitaEmailCodeVerifyRequestDataModel, expectedPurpose: String, meta: Map<String, String>
    ): AitaAuthFlowDataModel? {
        require(expectedPurpose == AUTH_PURPOSE_LOGIN || expectedPurpose == AUTH_PURPOSE_RECOVERY)
        config.requireAdvancedAuthentication(recovery = expectedPurpose == AUTH_PURPOSE_RECOVERY)
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val code = normalizeAitaOneTimeCode(request.code) ?: return null
        val now = System.currentTimeMillis()
        val verified = newSuspendedTransaction(Dispatchers.IO) {
            val peek = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }.singleOrNull()
                ?: return@newSuspendedTransaction null
            val lockedUser = peek[AuthOneTimeChallenges.userId]?.let(::lockSecurityUserInside)
            val row = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction null
            if (row[AuthOneTimeChallenges.purpose] != expectedPurpose || !authCodeCanBeVerified(
                now, row[AuthOneTimeChallenges.expiresAtMillis], row[AuthOneTimeChallenges.consumedAtMillis],
                row[AuthOneTimeChallenges.verifiedAtMillis], row[AuthOneTimeChallenges.attempts], row[AuthOneTimeChallenges.maxAttempts]
            )) return@newSuspendedTransaction null
            val userId = row[AuthOneTimeChallenges.userId]
            val user = lockedUser
            val destination = user?.let { challengeDestinationInside(row, it) }
            val bindingMatches = destination != null
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
            if (destination == user?.get(Users.email)?.let(::normalizeAitaEmail)) {
                AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                    it[AuthSecurityProfiles.emailVerifiedAtMillis] = now; it[AuthSecurityProfiles.updatedAtMillis] = now
                }
            }
            // Verification and single-use consumption/ticket issuance commit under the SAME row lock.
            Triple(userId, ticket, loginAuthorizationInside(requireNotNull(user)))
        } ?: return null
        return when (expectedPurpose) {
            AUTH_PURPOSE_LOGIN -> if (totpRequiredForLogin(verified.first)) createLoginChallenge(verified.first, AUTH_LOGIN_CHALLENGE_EMAIL, verified.third)
                else tokenService.newPairAfterVerification(verified.first, meta) {
                    loginAuthorizationStillValidInside(verified.first, verified.third, requireOptionalTotp = true)
                }?.let { AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.AUTHENTICATED, tokenPair = it, serverTimeMillis = now) }
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
            val peek = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }.singleOrNull() ?: return@newSuspendedTransaction false
            val userId = peek[AuthOneTimeChallenges.userId] ?: return@newSuspendedTransaction false
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction false
            val row = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction false
            if (!user[Users.isActive]) return@newSuspendedTransaction false
            if (challengeDestinationInside(row, user) == null) return@newSuspendedTransaction false
            val ticketHash = row[AuthOneTimeChallenges.resetTicketHash] ?: return@newSuspendedTransaction false
            val ticketExpires = row[AuthOneTimeChallenges.resetTicketExpiresAtMillis] ?: return@newSuspendedTransaction false
            if (row[AuthOneTimeChallenges.purpose] != AUTH_PURPOSE_RECOVERY || row[AuthOneTimeChallenges.consumedAtMillis] != null || ticketExpires <= now) {
                return@newSuspendedTransaction false
            }
            if (!crypto.constantTimeEquals(ticketHash, crypto.hmac("reset:$publicId", request.resetTicket))) {
                return@newSuspendedTransaction false
            }
            Users.update({ Users.id eq userId }) { it[Users.passwordHash] = Pw.hash(request.newPassword.toCharArray()) }
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[totpPendingSecretCiphertext] = null; it[totpPendingSetupId] = null; it[totpPendingExpiresAtMillis] = null
            }
            RefreshSessions.update({ RefreshSessions.userId eq userId }) {
                it[RefreshSessions.revokedAt] = java.time.Instant.now()
                it[RefreshSessions.securityInvalidated] = true
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

    private fun loginAuthorizationInside(user: ResultRow): String {
        val id = user[Users.id]
        val revision = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq id }
            .singleOrNull()?.get(AuthSecurityProfiles.securityRevision) ?: 1L
        return crypto.hmac("login-authorization:$id", "${user[Users.passwordHash]}:$revision:${normalizeAitaEmail(user[Users.email])}")
    }

    private fun loginAuthorizationStillValidInside(userId: UUID, expected: String, requireOptionalTotp: Boolean = false): Boolean {
        val user = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return false
        if (!user[Users.isActive] || !crypto.constantTimeEquals(expected, loginAuthorizationInside(user))) return false
        if (requireOptionalTotp) {
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.singleOrNull()
            if (loginFactorInside(profile) != AitaLoginSecondFactor.NONE) return false
        }
        return true
    }

    private fun createLoginChallengeInside(userId: UUID, method: String, authorization: String, now: Long): AitaAuthFlowDataModel {
        val publicId = UUID.randomUUID()
        AuthLoginChallenges.insert {
            it[id] = UUID.randomUUID(); it[AuthLoginChallenges.publicId] = publicId
            it[AuthLoginChallenges.userId] = userId; it[primaryMethod] = method
            it[authorizationHash] = authorization; it[attempts] = 0; it[maxAttempts] = 8
            it[expiresAtMillis] = now + 5 * 60_000L; it[createdAtMillis] = now
        }
        val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.singleOrNull()
        val factor = loginFactorInside(profile)
        val next = when {
            method == AUTH_LOGIN_CHALLENGE_AUTHENTICATOR -> AitaAuthNextStep.PASSWORD_CONFIRMATION
            method == AUTH_LOGIN_CHALLENGE_EMAIL && factor == AitaLoginSecondFactor.EMAIL -> AitaAuthNextStep.PASSWORD_CONFIRMATION
            method == AUTH_LOGIN_CHALLENGE_PASSWORD && factor == AitaLoginSecondFactor.EMAIL -> AitaAuthNextStep.EMAIL_DESTINATION
            else -> AitaAuthNextStep.TOTP
        }
        val user = Users.selectAll().where { Users.id eq userId }.single()
        return AitaAuthFlowDataModel(flowId = publicId.toString(), nextStep = next,
            emailDestinations = if (next == AitaAuthNextStep.EMAIL_DESTINATION) emailOptionsInside(user) else emptyList(),
            expiresAtMillis = now + 5 * 60_000L, serverTimeMillis = now)
    }

    private suspend fun createLoginChallenge(userId: UUID, method: String, authorization: String): AitaAuthFlowDataModel? {
        config.requireSecurityConfigured()
        return newSuspendedTransaction(Dispatchers.IO) {
            lockSecurityUserInside(userId) ?: return@newSuspendedTransaction null
            if (!loginAuthorizationStillValidInside(userId, authorization)) return@newSuspendedTransaction null
            createLoginChallengeInside(userId, method, authorization, System.currentTimeMillis())
        }
    }

    private fun usableLoginChallengeInside(publicId: UUID, userId: UUID, methods: Set<String>, now: Long): ResultRow? {
        // User is locked by the caller before profile/challenge locks; recovery uses the same order.
        AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().singleOrNull()
        val row = AuthLoginChallenges.selectAll().where { AuthLoginChallenges.publicId eq publicId }.forUpdate().singleOrNull() ?: return null
        if (row[AuthLoginChallenges.userId] != userId || row[AuthLoginChallenges.primaryMethod] !in methods ||
            row[AuthLoginChallenges.consumedAtMillis] != null || row[AuthLoginChallenges.expiresAtMillis] <= now ||
            row[AuthLoginChallenges.attempts] >= row[AuthLoginChallenges.maxAttempts]) return null
        val binding = row[AuthLoginChallenges.authorizationHash] ?: return null
        return row.takeIf { loginAuthorizationStillValidInside(userId, binding) }
    }

    suspend fun completeTotpLogin(request: AitaTotpLoginRequestDataModel, meta: Map<String, String>): AitaAuthFlowDataModel? {
        config.requireSecurityConfigured()
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val userId = newSuspendedTransaction(Dispatchers.IO) {
            AuthLoginChallenges.selectAll().where { AuthLoginChallenges.publicId eq publicId }.singleOrNull()?.get(AuthLoginChallenges.userId)
        } ?: return null
        val tokens = tokenService.newPairAfterVerification(userId, meta) {
            val now = System.currentTimeMillis()
            val row = usableLoginChallengeInside(publicId, userId, setOf(AUTH_LOGIN_CHALLENGE_PASSWORD, AUTH_LOGIN_CHALLENGE_EMAIL), now)
                ?: return@newPairAfterVerification false
            if (loginFactorInside(AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.singleOrNull()) != AitaLoginSecondFactor.AUTHENTICATOR)
                return@newPairAfterVerification false
            val valid = verifySecondFactorInside(userId, request.code, now)
            AuthLoginChallenges.update({ AuthLoginChallenges.id eq row[AuthLoginChallenges.id] }) {
                it[attempts] = row[AuthLoginChallenges.attempts] + 1
                if (valid) it[consumedAtMillis] = now
            }
            if (valid) auditInside(userId, "TWO_FACTOR_LOGIN_COMPLETED", null, meta["ip"]?.let { crypto.hmac("ip", it) }, now)
            valid
        } ?: return null
        return AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.AUTHENTICATED, tokenPair = tokens)
    }

    /** Bound account/IP guesses in the database as well as this process; restarts do not reset the OTP budget. */
    private fun recordAuthenticatorAttemptInside(identifier: String, ip: String, userId: UUID?, now: Long) {
        val idHash = crypto.hmac("authenticator-login", userId?.toString() ?: identifier)
        val ipHash = crypto.hmac("ip", ip)
        listOf(crypto.hmac("factor-rate-id", idHash), crypto.hmac("factor-rate-ip", ipHash))
            .map { java.lang.Long.parseUnsignedLong(it.take(16), 16) }.distinct().sorted().forEach {
                org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec("SELECT pg_advisory_xact_lock($it)")
            }
        val kind = AuthSecurityAuditEvents.eventType eq "AUTHENTICATOR_LOGIN_ATTEMPT"
        val byId = AuthSecurityAuditEvents.select(AuthSecurityAuditEvents.createdAtMillis).where {
            kind and (AuthSecurityAuditEvents.identifierHash eq idHash) and
                (AuthSecurityAuditEvents.createdAtMillis greater now - 3_600_000L)
        }.orderBy(AuthSecurityAuditEvents.createdAtMillis to SortOrder.DESC).limit(40).map { it[AuthSecurityAuditEvents.createdAtMillis] }
        val byIp = AuthSecurityAuditEvents.select(AuthSecurityAuditEvents.createdAtMillis).where {
            kind and (AuthSecurityAuditEvents.ipHash eq ipHash) and
                (AuthSecurityAuditEvents.createdAtMillis greater now - 60_000L)
        }.orderBy(AuthSecurityAuditEvents.createdAtMillis to SortOrder.DESC).limit(60).map { it[AuthSecurityAuditEvents.createdAtMillis] }
        val wait = aitaAuthenticatorRetryAfterSeconds(byId, byIp, now)
        if (wait > 0L) throw AitaAuthRateLimitedException(wait)
        auditInside(userId, "AUTHENTICATOR_LOGIN_ATTEMPT", idHash, ipHash, now)
    }

    suspend fun authenticatorLogin(request: AitaAuthenticatorLoginRequestDataModel, meta: Map<String, String>): AitaAuthFlowDataModel? {
        config.requireAdvancedAuthentication()
        val normalized = normalizeAitaLoginIdentifier(request.identifier) ?: return null
        if (!aitaSecondFactorIsWellFormed(request.code)) return null
        val userId = resolveUser(normalized.value)?.id
        val checked = newSuspendedTransaction(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            // Only this path acquires factor-rate locks, always before the user lock.
            recordAuthenticatorAttemptInside(normalized.value, meta["ip"].orEmpty(), userId, now)
            if (userId == null) return@newSuspendedTransaction null
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction null
            if (!user[Users.isActive]) return@newSuspendedTransaction null
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().singleOrNull()
                ?: return@newSuspendedTransaction null
            if (profile[AuthSecurityProfiles.totpEnabledAtMillis] == null || !verifySecondFactorInside(userId, request.code, System.currentTimeMillis())) return@newSuspendedTransaction null
            val authorization = loginAuthorizationInside(user)
            if (loginFactorInside(profile) != AitaLoginSecondFactor.NONE) {
                createLoginChallengeInside(userId, AUTH_LOGIN_CHALLENGE_AUTHENTICATOR, authorization, now) to authorization
            } else null to authorization
        } ?: return null
        checked.first?.let { return it }
        val verifiedUserId = requireNotNull(userId)
        val tokens = tokenService.newPairAfterVerification(verifiedUserId, meta) {
            loginAuthorizationStillValidInside(verifiedUserId, checked.second, requireOptionalTotp = true)
        } ?: return null
        return AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.AUTHENTICATED, tokenPair = tokens)
    }

    suspend fun completeAuthenticatorPassword(request: AitaAuthenticatorPasswordRequestDataModel, meta: Map<String, String>): AitaAuthFlowDataModel? {
        config.requireAdvancedAuthentication()
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        if (request.password.length !in 1..1024) return null
        val userId = newSuspendedTransaction(Dispatchers.IO) {
            AuthLoginChallenges.selectAll().where { AuthLoginChallenges.publicId eq publicId }.singleOrNull()?.get(AuthLoginChallenges.userId)
        } ?: return null
        var nextEmailFlow: AitaAuthFlowDataModel? = null
        val tokens = tokenService.newPairAfterVerification(userId, meta) {
            val now = System.currentTimeMillis()
            val row = usableLoginChallengeInside(publicId, userId, setOf(AUTH_LOGIN_CHALLENGE_AUTHENTICATOR, AUTH_LOGIN_CHALLENGE_EMAIL), now)
                ?: return@newPairAfterVerification false
            val user = Users.selectAll().where { Users.id eq userId }.single()
            val policy = loginFactorInside(AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.singleOrNull())
            // A wrong endpoint must not consume a valid challenge for a different second factor.
            if (row[AuthLoginChallenges.primaryMethod] == AUTH_LOGIN_CHALLENGE_EMAIL && policy != AitaLoginSecondFactor.EMAIL)
                return@newPairAfterVerification false
            val valid = Pw.verify(request.password.toCharArray(), user[Users.passwordHash])
            AuthLoginChallenges.update({ AuthLoginChallenges.id eq row[AuthLoginChallenges.id] }) {
                it[attempts] = row[AuthLoginChallenges.attempts] + 1
                if (valid) it[consumedAtMillis] = now
            }
            if (!valid) return@newPairAfterVerification false
            if (row[AuthLoginChallenges.primaryMethod] == AUTH_LOGIN_CHALLENGE_AUTHENTICATOR &&
                loginFactorInside(AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.singleOrNull()) == AitaLoginSecondFactor.EMAIL) {
                nextEmailFlow = createLoginChallengeInside(userId, AUTH_LOGIN_CHALLENGE_PASSWORD, loginAuthorizationInside(user), now)
                return@newPairAfterVerification false
            }
            auditInside(userId, "FACTOR_PASSWORD_COMPLETED", null, meta["ip"]?.let { crypto.hmac("ip", it) }, now)
            true
        }
        nextEmailFlow?.let { return it }
        return tokens?.let { AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.AUTHENTICATED, tokenPair = it) }
    }

    /** Password proof is bound to this account's current credentials/revision/main email.
     * Neither possession of an existing bearer token nor an extra email alone can remove 2FA.
     */
    suspend fun requestAuthenticatorRecovery(request: AitaAuthenticatorRecoveryRequestDataModel, ip: String): AitaAuthFlowDataModel {
        requireEmailDelivery()
        val normalized = normalizeAitaLoginIdentifier(request.identifier) ?: throw BadRequestException("Invalid sign-in identifier")
        if (request.currentPassword.length !in 1..1024) throw BadRequestException("Password required")
        val now = System.currentTimeMillis()
        val idHash = crypto.hmac("identifier", normalized.value)
        val ipHash = crypto.hmac("ip", ip)
        requireActionBudget("totp-recovery-id:$idHash", 8, now)
        requireActionBudget("totp-recovery-ip:$ipHash", 40, now)
        val resolved = resolveUser(normalized.value)
        return newSuspendedTransaction(Dispatchers.IO) {
            val candidate = resolved?.id?.let(::lockSecurityUserInside)
            val profile = resolved?.id?.let { id ->
                AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq id }.forUpdate().singleOrNull()
            }
            // Verify a dummy hash for an unknown account, keeping the anonymous flow neutral.
            val passwordMatches = Pw.verify(request.currentPassword.toCharArray(), candidate?.get(Users.passwordHash) ?: dummyPasswordHash)
            val mainEmail = candidate?.get(Users.email)?.let(::normalizeAitaEmail)
            val eligible = candidate != null && candidate[Users.isActive] && passwordMatches && mainEmail != null &&
                profile?.get(AuthSecurityProfiles.totpEnabledAtMillis) != null
            val owner = if (eligible) candidate?.get(Users.id) else null
            val destination = mainEmail.takeIf { eligible }
            lockEmailBucketsInside(idHash, ipHash, owner)
            val binding = candidate?.takeIf { eligible }?.let(::loginAuthorizationInside)
            val latest = AuthOneTimeChallenges.selectAll().where {
                (AuthOneTimeChallenges.identifierHash eq idHash) and (AuthOneTimeChallenges.purpose eq AUTH_PURPOSE_TOTP_RECOVERY) and
                    AuthOneTimeChallenges.consumedAtMillis.isNull() and AuthOneTimeChallenges.verifiedAtMillis.isNull() and
                    (AuthOneTimeChallenges.expiresAtMillis greater now)
            }.orderBy(AuthOneTimeChallenges.createdAtMillis to SortOrder.DESC).limit(1).forUpdate().singleOrNull()
            val previousBinding = latest?.let { row -> AuthTotpRecoveryChallenges.selectAll().where {
                AuthTotpRecoveryChallenges.challengePublicId eq row[AuthOneTimeChallenges.publicId]
            }.singleOrNull()?.get(AuthTotpRecoveryChallenges.authorizationHash) }
            if (latest != null && latest[AuthOneTimeChallenges.userId] == owner && previousBinding == binding &&
                latest[AuthOneTimeChallenges.attempts] < latest[AuthOneTimeChallenges.maxAttempts] && latest[AuthOneTimeChallenges.resendAfterMillis] > now) {
                return@newSuspendedTransaction emailFlow(latest, now)
            }
            checkEmailQuotaInside(idHash, ipHash, now)
            AuthOneTimeChallenges.update({
                (AuthOneTimeChallenges.identifierHash eq idHash) and (AuthOneTimeChallenges.purpose eq AUTH_PURPOSE_TOTP_RECOVERY) and
                    AuthOneTimeChallenges.consumedAtMillis.isNull()
            }) { it[consumedAtMillis] = now; it[updatedAtMillis] = now }
            val flow = createEmailChallengeInside(owner, destination, AUTH_PURPOSE_TOTP_RECOVERY, request.locale, idHash, ipHash, now)
            if (owner != null && binding != null) AuthTotpRecoveryChallenges.insert {
                it[challengePublicId] = UUID.fromString(flow.flowId); it[userId] = owner
                it[authorizationHash] = binding; it[createdAtMillis] = now
            }
            // Do not expose the address resolved from a phone/extra email before verification.
            flow
        }
    }

    suspend fun confirmAuthenticatorRecovery(request: AitaEmailCodeVerifyRequestDataModel): UUID? {
        config.requireAdvancedAuthentication()
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val code = normalizeAitaOneTimeCode(request.code) ?: return null
        val recovered = newSuspendedTransaction(Dispatchers.IO) {
            val peek = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }.singleOrNull()
                ?: return@newSuspendedTransaction null
            if (peek[AuthOneTimeChallenges.purpose] != AUTH_PURPOSE_TOTP_RECOVERY) return@newSuspendedTransaction null
            val owner = peek[AuthOneTimeChallenges.userId]
            val user = owner?.let(::lockSecurityUserInside)
            val profile = owner?.let { id -> AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq id }.forUpdate().singleOrNull() }
            val challenge = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }.forUpdate().singleOrNull()
                ?: return@newSuspendedTransaction null
            val now = System.currentTimeMillis()
            if (!authCodeCanBeVerified(now, challenge[AuthOneTimeChallenges.expiresAtMillis], challenge[AuthOneTimeChallenges.consumedAtMillis],
                    challenge[AuthOneTimeChallenges.verifiedAtMillis], challenge[AuthOneTimeChallenges.attempts], challenge[AuthOneTimeChallenges.maxAttempts])) return@newSuspendedTransaction null
            val destination = user?.let { challengeDestinationInside(challenge, it) }
            val valid = owner != null && user != null && user[Users.isActive] && profile?.get(AuthSecurityProfiles.totpEnabledAtMillis) != null &&
                destination != null && crypto.constantTimeEquals(challenge[AuthOneTimeChallenges.codeHash], crypto.hmac("code:$publicId", code))
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq challenge[AuthOneTimeChallenges.id] }) {
                it[attempts] = challenge[AuthOneTimeChallenges.attempts] + 1; it[updatedAtMillis] = now
            }
            if (!valid) return@newSuspendedTransaction null
            val userId = requireNotNull(owner)
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[totpSecretCiphertext] = null; it[totpPendingSecretCiphertext] = null
                it[totpPendingSetupId] = null; it[totpPendingExpiresAtMillis] = null
                it[totpEnabledAtMillis] = null; it[totpLastUsedStep] = null; it[totpRequiredForLogin] = true
                it[emailVerifiedAtMillis] = now
                it[securityRevision] = requireNotNull(profile)[AuthSecurityProfiles.securityRevision] + 1L
                it[updatedAtMillis] = now
            }
            AuthRecoveryCodes.deleteWhere { AuthRecoveryCodes.userId eq userId }
            // Revoke bearer sessions, half-finished logins, reset tickets and pending email changes.
            RefreshSessions.update({ RefreshSessions.userId eq userId }) {
                it[revokedAt] = java.time.Instant.ofEpochMilli(now)
                it[securityInvalidated] = true
            }
            AuthLoginChallenges.update({ (AuthLoginChallenges.userId eq userId) and AuthLoginChallenges.consumedAtMillis.isNull() }) {
                it[consumedAtMillis] = now
            }
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.userId eq userId }) {
                it[consumedAtMillis] = now; it[updatedAtMillis] = now
            }
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.publicId eq publicId }) { it[verifiedAtMillis] = now }
            auditInside(userId, "TOTP_RESET_BY_MAIN_EMAIL", challenge[AuthOneTimeChallenges.identifierHash], challenge[AuthOneTimeChallenges.requestIpHash], now)
            // A separate, non-verifiable notice remains deliverable after the reset challenge is consumed.
            createEmailChallengeInside(userId, destination, AUTH_PURPOSE_TOTP_NOTICE, challenge[AuthOneTimeChallenges.locale],
                crypto.hmac("totp-reset-notice", publicId.toString()), challenge[AuthOneTimeChallenges.requestIpHash], now)
            userId
        }
        if (recovered != null) withContext(NonCancellable) {
            withTimeoutOrNull(1_000L) { publishAuthenticatorReset(recovered) }
        }
        return recovered
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
            mainPhoneNumber = normalizeAitaStoredMainPhone(user[Users.phoneNumber], user[Users.countryLocale]) ?: user[Users.phoneNumber],
            emailVerified = profile[AuthSecurityProfiles.emailVerifiedAtMillis] != null,
            emailRequiredForLogin = profile[AuthSecurityProfiles.emailRequiredForLogin],
            phoneLoginAlias = profile[AuthSecurityProfiles.phoneLoginAlias],
            phoneLoginAliasVerified = profile[AuthSecurityProfiles.phoneAliasVerifiedAtMillis] != null,
            authenticatorEnabled = profile[AuthSecurityProfiles.totpEnabledAtMillis] != null,
            authenticatorRequiredForLogin = aitaRequiresLoginSecondFactor(
                profile[AuthSecurityProfiles.totpEnabledAtMillis] != null,
                profile[AuthSecurityProfiles.totpRequiredForLogin]
            ),
            recoveryCodesRemaining = remaining,
            securityRevision = profile[AuthSecurityProfiles.securityRevision],
            additionalLoginEmails = AuthLoginEmails.selectAll().where {
                (AuthLoginEmails.userId eq userId) and (AuthLoginEmails.isPrimary eq false) and AuthLoginEmails.verifiedAtMillis.isNotNull()
            }.orderBy(AuthLoginEmails.createdAtMillis to SortOrder.ASC).map { it[AuthLoginEmails.emailNormalized] }
        )
    }

    suspend fun startTotpSetup(
        userId: UUID,
        request: AitaSensitiveSecurityActionRequestDataModel
    ): AitaTotpSetupDataModel? {
        config.requireAdvancedAuthentication()
        requireActionBudget("totp-setup:$userId", 12, System.currentTimeMillis())
        val now = System.currentTimeMillis()
        val setupId = UUID.randomUUID()
        val secretBytes = ByteArray(20).also(random::nextBytes)
        val secret = base32Encode(secretBytes)
        val expires = now + 10 * 60_000L
        val email = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction null
            ensureProfileInside(userId, now)
            AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().single()
            if (!verifyEmailAliasCredentialsInside(user, request.currentPassword, request.secondFactorCode, System.currentTimeMillis(), request.emailProof, AitaSecurityEmailAction.TOTP_SETUP)) return@newSuspendedTransaction null
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[AuthSecurityProfiles.totpPendingSecretCiphertext] = crypto.encrypt("totp-pending:$userId:$setupId", secret)
                it[AuthSecurityProfiles.totpPendingSetupId] = setupId
                it[AuthSecurityProfiles.totpPendingExpiresAtMillis] = expires
                it[AuthSecurityProfiles.updatedAtMillis] = now
            }
            user[Users.email]
        } ?: return null
        val label = "${config.issuerName}:${email.trim().lowercase()}"
        val uri = "otpauth://totp/${urlEncode(label)}?secret=$secret&issuer=${urlEncode(config.issuerName)}&algorithm=SHA1&digits=6&period=30"
        return AitaTotpSetupDataModel(setupId.toString(), secret, uri, expires, now)
    }

    suspend fun confirmTotpSetup(userId: UUID, request: AitaTotpSetupConfirmRequestDataModel): AitaAuthFlowDataModel? {
        config.requireAdvancedAuthentication()
        val setupId = runCatching { UUID.fromString(request.setupId) }.getOrNull() ?: return null
        requireActionBudget("totp-setup-confirm:$userId", 12, System.currentTimeMillis())
        val codes = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction null
            if (!user[Users.isActive]) return@newSuspendedTransaction null
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction null
            val now = System.currentTimeMillis()
            if (profile[AuthSecurityProfiles.totpPendingSetupId] != setupId ||
                (profile[AuthSecurityProfiles.totpPendingExpiresAtMillis] ?: 0L) <= now
            ) return@newSuspendedTransaction null
            val encoded = profile[AuthSecurityProfiles.totpPendingSecretCiphertext] ?: return@newSuspendedTransaction null
            val secret = crypto.decrypt("totp-pending:$userId:$setupId", encoded)
            val acceptedStep = runCatching { aitaTotpMatchingStep(secret, request.code, now, null) }.getOrNull()
                ?: return@newSuspendedTransaction null
            val recovery = generateRecoveryCodesInside(userId, now)
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[AuthSecurityProfiles.totpSecretCiphertext] = crypto.encrypt("totp-active:$userId", secret)
                it[AuthSecurityProfiles.totpPendingSecretCiphertext] = null
                it[AuthSecurityProfiles.totpPendingSetupId] = null
                it[AuthSecurityProfiles.totpPendingExpiresAtMillis] = null
                it[AuthSecurityProfiles.totpEnabledAtMillis] = now
                it[AuthSecurityProfiles.totpRequiredForLogin] = request.requireForLogin
                if (request.requireForLogin) it[AuthSecurityProfiles.emailRequiredForLogin] = false
                it[AuthSecurityProfiles.totpLastUsedStep] = acceptedStep
                it[AuthSecurityProfiles.securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1L
                it[AuthSecurityProfiles.updatedAtMillis] = now
            }
            auditInside(userId, "TOTP_ENABLED", null, null, now)
            recovery
        } ?: return null
        return AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.COMPLETE, recoveryCodes = codes)
    }

    suspend fun updateTotpLoginPolicy(userId: UUID, request: AitaTotpLoginPolicyRequestDataModel): AitaAuthenticationSettingsDataModel? {
        config.requireSecurityConfigured()
        val now = System.currentTimeMillis()
        if (!limiter.allow("totp-policy:$userId", 12, now)) return null
        val updated = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            // Match identity-management/password-reset lock order: user, profile, challenges.
            // Verification and a recovery-code consumption commit with the policy change.
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction false
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction false
            if (profile[AuthSecurityProfiles.totpEnabledAtMillis] == null ||
                profile[AuthSecurityProfiles.securityRevision] != request.expectedSecurityRevision) return@newSuspendedTransaction false
            if (!verifyEmailAliasCredentialsInside(user, request.currentPassword, request.secondFactorCode, now)) return@newSuspendedTransaction false
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[totpRequiredForLogin] = request.requiredForLogin
                if (request.requiredForLogin) it[emailRequiredForLogin] = false
                it[securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1L
                it[updatedAtMillis] = now
            }
            AuthLoginChallenges.update({ (AuthLoginChallenges.userId eq userId) and AuthLoginChallenges.consumedAtMillis.isNull() }) {
                it[consumedAtMillis] = now
            }
            auditInside(userId, if (request.requiredForLogin) "TOTP_LOGIN_REQUIRED" else "TOTP_LOGIN_OPTIONAL", null, null, now)
            true
        }
        return if (updated) settings(userId) else null
    }

    suspend fun disableTotp(userId: UUID, request: AitaSensitiveSecurityActionRequestDataModel): AitaAuthenticationSettingsDataModel? {
        config.requireSecurityConfigured()
        requireActionBudget("totp-disable:$userId", 12, System.currentTimeMillis())
        val updated = newSuspendedTransaction(Dispatchers.IO) {
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction false
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().singleOrNull()
                ?: return@newSuspendedTransaction false
            val now = System.currentTimeMillis()
            if (profile[AuthSecurityProfiles.totpEnabledAtMillis] == null ||
                !verifyEmailAliasCredentialsInside(user, request.currentPassword, request.secondFactorCode, now)) return@newSuspendedTransaction false
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[totpSecretCiphertext] = null; it[totpPendingSecretCiphertext] = null
                it[totpPendingSetupId] = null; it[totpPendingExpiresAtMillis] = null
                it[totpLastUsedStep] = null; it[totpEnabledAtMillis] = null; it[totpRequiredForLogin] = true
                it[securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1L
                it[updatedAtMillis] = now
            }
            AuthRecoveryCodes.deleteWhere { AuthRecoveryCodes.userId eq userId }
            auditInside(userId, "TOTP_DISABLED", null, null, now)
            true
        }
        return if (updated) settings(userId) else null
    }

    suspend fun regenerateRecoveryCodes(userId: UUID, request: AitaSensitiveSecurityActionRequestDataModel): List<String>? {
        config.requireSecurityConfigured()
        requireActionBudget("totp-regenerate:$userId", 12, System.currentTimeMillis())
        return newSuspendedTransaction(Dispatchers.IO) {
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction null
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().singleOrNull()
                ?: return@newSuspendedTransaction null
            val now = System.currentTimeMillis()
            if (profile[AuthSecurityProfiles.totpEnabledAtMillis] == null ||
                !verifyEmailAliasCredentialsInside(user, request.currentPassword, request.secondFactorCode, now)) return@newSuspendedTransaction null
            val result = generateRecoveryCodesInside(userId, now)
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1L
                it[updatedAtMillis] = now
            }
            auditInside(userId, "RECOVERY_CODES_REGENERATED", null, null, now)
            result
        }
    }

    suspend fun requestPhoneAlias(userId: UUID, request: AitaPhoneAliasRequestDataModel, ip: String): AitaAuthFlowDataModel? {
        config.requireEmailAuthentication()
        requireActionBudget("phone-alias-auth:$userId", 12, System.currentTimeMillis())
        val phone = when (request.action) {
            AitaPhoneAliasAction.ADD_OR_REPLACE -> normalizeAitaPhoneAlias(request.phoneNumber) ?: return null
            AitaPhoneAliasAction.REMOVE -> null
        }
        val now = System.currentTimeMillis()
        val ipHash = crypto.hmac("ip", ip)
        requireEmailDelivery()
        return newSuspendedTransaction(Dispatchers.IO) {
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction null
            if (!verifyEmailAliasCredentialsInside(user, request.currentPassword, request.secondFactorCode, System.currentTimeMillis(), defersMainEmailProof = true)) return@newSuspendedTransaction null
            if (phone != null && (phoneLoginIdentityHasOtherOwnerInside(phone, userId) ||
                phone == normalizeAitaStoredMainPhone(user[Users.phoneNumber], user[Users.countryLocale])))
                throw AitaAuthContactConflictException(AuthContactConflict.PHONE)
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
                it[AuthPhoneAliasChallenges.requestedPhoneAlias] = phone
                it[AuthPhoneAliasChallenges.authorizationHash] = aliasAuthorizationInside(user)
                it[AuthPhoneAliasChallenges.createdAtMillis] = now
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
            val user = lockSecurityUserInside(userId)?.takeIf { it[Users.isActive] }
                ?: return@newSuspendedTransaction false
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
            val authorization = phone[AuthPhoneAliasChallenges.authorizationHash] ?: return@newSuspendedTransaction false
            if (!crypto.constantTimeEquals(authorization, aliasAuthorizationInside(user))) return@newSuspendedTransaction false
            val binding = challenge[AuthOneTimeChallenges.deliveryEmailHash]
            if (binding != null && !crypto.constantTimeEquals(binding, crypto.hmac("delivery-email", normalizeAitaEmail(user[Users.email]).orEmpty()))) return@newSuspendedTransaction false
            val valid = crypto.constantTimeEquals(challenge[AuthOneTimeChallenges.codeHash], crypto.hmac("code:$publicId", code))
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq challenge[AuthOneTimeChallenges.id] }) {
                it[AuthOneTimeChallenges.attempts] = challenge[AuthOneTimeChallenges.attempts] + 1
                it[AuthOneTimeChallenges.updatedAtMillis] = now
            }
            if (!valid) return@newSuspendedTransaction false
            val action = AitaPhoneAliasAction.valueOf(phone[AuthPhoneAliasChallenges.action])
            val alias = if (action == AitaPhoneAliasAction.REMOVE) null else phone[AuthPhoneAliasChallenges.requestedPhoneAlias]
            if (alias != null) {
                lockPhoneLoginIdentityInside(alias)
                if (phoneLoginIdentityHasOtherOwnerInside(alias, userId) ||
                    alias == normalizeAitaStoredMainPhone(user[Users.phoneNumber], user[Users.countryLocale]))
                    throw AitaAuthContactConflictException(AuthContactConflict.PHONE)
            }
            ensureProfileInside(userId, now)
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().single()
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                    it[AuthSecurityProfiles.phoneLoginAlias] = alias
                    it[AuthSecurityProfiles.phoneAliasVerifiedAtMillis] = if (alias == null) null else now
                    it[AuthSecurityProfiles.emailVerifiedAtMillis] = now
                    it[AuthSecurityProfiles.securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1L
                    it[AuthSecurityProfiles.updatedAtMillis] = now
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

    private fun lockSecurityUserInside(userId: UUID): ResultRow? {
        // All account security writers lock the user BEFORE bucket/profile/challenge locks.
        // NO KEY UPDATE also permits unrelated foreign-key KEY SHARE readers. UUID is typed.
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            "SELECT id FROM users WHERE id = '$userId' FOR NO KEY UPDATE"
        )
        return Users.selectAll().where { Users.id eq userId }.singleOrNull()
    }

    private fun aliasAuthorizationInside(user: ResultRow): String {
        val id = user[Users.id]
        val revision = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq id }
            .singleOrNull()?.get(AuthSecurityProfiles.securityRevision) ?: 1L
        return crypto.hmac("email-alias-authorization:$id", "${user[Users.passwordHash]}:$revision:${normalizeAitaEmail(user[Users.email])}")
    }

    /** Resolve the original delivery binding; never silently retarget a retry to a new address. */
    private fun challengeDestinationInside(challenge: ResultRow, user: ResultRow): String? {
        val id = user[Users.id]
        if (!user[Users.isActive] || challenge[AuthOneTimeChallenges.userId] != id) return null
        if (challenge[AuthOneTimeChallenges.purpose] == AUTH_PURPOSE_EMAIL_ALIAS) {
            val pending = AuthEmailAliasChallenges.selectAll().where {
                AuthEmailAliasChallenges.challengePublicId eq challenge[AuthOneTimeChallenges.publicId]
            }.singleOrNull() ?: return null
            if (pending[AuthEmailAliasChallenges.userId] != id || pending[AuthEmailAliasChallenges.consumedAtMillis] != null ||
                pending[AuthEmailAliasChallenges.createdAtMillis] + config.codeTtlMillis <= System.currentTimeMillis() ||
                !crypto.constantTimeEquals(pending[AuthEmailAliasChallenges.authorizationHash], aliasAuthorizationInside(user))) return null
            val email = pending[AuthEmailAliasChallenges.requestedEmail]
            val binding = challenge[AuthOneTimeChallenges.deliveryEmailHash] ?: return null
            if (!crypto.constantTimeEquals(binding, crypto.hmac("delivery-email", email))) return null
            return email // Claim conflicts are reported after the correct code is verified, not as a bad code.
        }
        if (challenge[AuthOneTimeChallenges.purpose] == AUTH_PURPOSE_PHONE) {
            val change = AuthPhoneAliasChallenges.selectAll().where { AuthPhoneAliasChallenges.challengePublicId eq challenge[AuthOneTimeChallenges.publicId] }.singleOrNull() ?: return null
            val hash = change[AuthPhoneAliasChallenges.authorizationHash] ?: return null
            if (change[AuthPhoneAliasChallenges.userId] != id || change[AuthPhoneAliasChallenges.consumedAtMillis] != null ||
                change[AuthPhoneAliasChallenges.createdAtMillis] + config.codeTtlMillis <= System.currentTimeMillis() ||
                !crypto.constantTimeEquals(hash, aliasAuthorizationInside(user))) return null
        }
        if (challenge[AuthOneTimeChallenges.purpose] == AUTH_PURPOSE_SECURITY_EMAIL) {
            val proof = AuthSecurityEmailChallenges.selectAll().where { AuthSecurityEmailChallenges.challengePublicId eq challenge[AuthOneTimeChallenges.publicId] }.singleOrNull() ?: return null
            if (proof[AuthSecurityEmailChallenges.userId] != id || !crypto.constantTimeEquals(proof[AuthSecurityEmailChallenges.authorizationHash], loginAuthorizationInside(user))) return null
        }
        if (challenge[AuthOneTimeChallenges.purpose] == AUTH_PURPOSE_EMAIL_FACTOR) {
            val link = AuthLoginEmailChallenges.selectAll().where { AuthLoginEmailChallenges.challengePublicId eq challenge[AuthOneTimeChallenges.publicId] }.singleOrNull() ?: return null
            val login = AuthLoginChallenges.selectAll().where { AuthLoginChallenges.publicId eq link[AuthLoginEmailChallenges.loginPublicId] }.singleOrNull() ?: return null
            if (login[AuthLoginChallenges.userId] != id || login[AuthLoginChallenges.primaryMethod] != AUTH_LOGIN_CHALLENGE_PASSWORD ||
                login[AuthLoginChallenges.consumedAtMillis] != null || login[AuthLoginChallenges.expiresAtMillis] <= System.currentTimeMillis() ||
                login[AuthLoginChallenges.attempts] >= login[AuthLoginChallenges.maxAttempts] ||
                !crypto.constantTimeEquals(login[AuthLoginChallenges.authorizationHash].orEmpty(), loginAuthorizationInside(user))) return null
        }
        if (challenge[AuthOneTimeChallenges.purpose] == AUTH_PURPOSE_TOTP_RECOVERY) {
            val recovery = AuthTotpRecoveryChallenges.selectAll().where {
                AuthTotpRecoveryChallenges.challengePublicId eq challenge[AuthOneTimeChallenges.publicId]
            }.singleOrNull() ?: return null
            if (recovery[AuthTotpRecoveryChallenges.userId] != id ||
                recovery[AuthTotpRecoveryChallenges.createdAtMillis] + config.codeTtlMillis <= System.currentTimeMillis() ||
                !crypto.constantTimeEquals(recovery[AuthTotpRecoveryChallenges.authorizationHash], loginAuthorizationInside(user))) return null
        }
        val primary = normalizeAitaEmail(user[Users.email])
        val binding = challenge[AuthOneTimeChallenges.deliveryEmailHash] ?: return primary
        val candidates = listOfNotNull(primary) + if (challenge[AuthOneTimeChallenges.purpose] in setOf(AUTH_PURPOSE_PHONE, AUTH_PURPOSE_TOTP_RECOVERY, AUTH_PURPOSE_TOTP_NOTICE, AUTH_PURPOSE_SECURITY_EMAIL)) emptyList() else
            AuthLoginEmails.selectAll().where {
                (AuthLoginEmails.userId eq id) and (AuthLoginEmails.isPrimary eq false) and AuthLoginEmails.verifiedAtMillis.isNotNull()
            }.map { it[AuthLoginEmails.emailNormalized] }
        return candidates.firstOrNull { crypto.constantTimeEquals(binding, crypto.hmac("delivery-email", it)) }
    }

    private fun verifyEmailAliasCredentialsInside(user: ResultRow, password: String, secondFactor: String, now: Long,
        proof: AitaSecurityEmailProof? = null, action: AitaSecurityEmailAction? = null, target: String = "",
        defersMainEmailProof: Boolean = false): Boolean {
        if (password.length !in 1..1024 || !user[Users.isActive] || !Pw.verify(password.toCharArray(), user[Users.passwordHash])) return false
        val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq user[Users.id] }.singleOrNull()
        if (profile?.get(AuthSecurityProfiles.totpEnabledAtMillis) != null) return verifySecondFactorInside(user[Users.id], secondFactor, now)
        if (profile?.get(AuthSecurityProfiles.emailRequiredForLogin) == true && !defersMainEmailProof)
            return action != null && verifySecurityEmailInside(user, proof, action, target, now)
        return true
    }

    suspend fun requestEmailAlias(userId: UUID, request: AitaEmailAliasRequestDataModel, ip: String): AitaAuthFlowDataModel? {
        requireEmailDelivery()
        val email = normalizeAitaEmail(request.email) ?: return null
        val now = System.currentTimeMillis()
        val idHash = crypto.hmac("identifier", email)
        val ipHash = crypto.hmac("ip", ip)
        if (!limiter.allow("email-alias-auth:$userId", 12, now) || !limiter.allow("email-alias-ip:$ipHash", 60, now))
            throw AitaAuthRateLimitedException(3600L)
        return newSuspendedTransaction(Dispatchers.IO) {
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction null
            lockEmailBucketsInside(idHash, ipHash, userId)
            ensureProfileInside(userId, now)
            AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().single()
            if (!verifyEmailAliasCredentialsInside(user, request.currentPassword, request.secondFactorCode, now, request.emailProof, AitaSecurityEmailAction.ADD_EMAIL, email)) return@newSuspendedTransaction null
            if (AuthLoginEmails.selectAll().where { AuthLoginEmails.emailNormalized eq email }.limit(1).singleOrNull() != null) throw AitaAuthContactConflictException(AuthContactConflict.EMAIL)
            if (AuthLoginEmails.selectAll().where { (AuthLoginEmails.userId eq userId) and (AuthLoginEmails.isPrimary eq false) }.count() >= AITA_MAX_ADDITIONAL_LOGIN_EMAILS) throw AitaAuthContactConflictException(AuthContactConflict.EXTRA_LIMIT)
            checkEmailQuotaInside(idHash, ipHash, now)
            val flow = createEmailChallengeInside(userId, email, AUTH_PURPOSE_EMAIL_ALIAS, request.locale, idHash, ipHash, now)
            AuthEmailAliasChallenges.insert {
                it[AuthEmailAliasChallenges.challengePublicId] = UUID.fromString(flow.flowId); it[AuthEmailAliasChallenges.userId] = userId
                it[AuthEmailAliasChallenges.requestedEmail] = email; it[AuthEmailAliasChallenges.authorizationHash] = aliasAuthorizationInside(user); it[AuthEmailAliasChallenges.createdAtMillis] = now
            }
            flow.copy(maskedDestination = maskEmail(email))
        }
    }

    suspend fun confirmEmailAlias(userId: UUID, request: AitaEmailAliasConfirmRequestDataModel): AitaAuthenticationSettingsDataModel? {
        config.requireAdvancedAuthentication()
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val code = normalizeAitaOneTimeCode(request.code) ?: return null
        val now = System.currentTimeMillis()
        val applied = try {
            newSuspendedTransaction(Dispatchers.IO) {
                val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction false
                val row = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }.forUpdate().singleOrNull() ?: return@newSuspendedTransaction false
                if (row[AuthOneTimeChallenges.userId] != userId || row[AuthOneTimeChallenges.purpose] != AUTH_PURPOSE_EMAIL_ALIAS ||
                    !authCodeCanBeVerified(now, row[AuthOneTimeChallenges.expiresAtMillis], row[AuthOneTimeChallenges.consumedAtMillis],
                        row[AuthOneTimeChallenges.verifiedAtMillis], row[AuthOneTimeChallenges.attempts], row[AuthOneTimeChallenges.maxAttempts])) return@newSuspendedTransaction false
                val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().singleOrNull()
                    ?: return@newSuspendedTransaction false
                val destination = challengeDestinationInside(row, user) ?: return@newSuspendedTransaction false
                val valid = crypto.constantTimeEquals(row[AuthOneTimeChallenges.codeHash], crypto.hmac("code:$publicId", code))
                AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq row[AuthOneTimeChallenges.id] }) {
                    it[attempts] = row[AuthOneTimeChallenges.attempts] + 1; it[updatedAtMillis] = now
                }
                if (!valid) return@newSuspendedTransaction false
                if (AuthLoginEmails.selectAll().where { AuthLoginEmails.emailNormalized eq destination }.singleOrNull() != null)
                    throw AitaAuthContactConflictException(AuthContactConflict.EMAIL)
                if (AuthLoginEmails.selectAll().where { (AuthLoginEmails.userId eq userId) and (AuthLoginEmails.isPrimary eq false) }.count() >= AITA_MAX_ADDITIONAL_LOGIN_EMAILS) throw AitaAuthContactConflictException(AuthContactConflict.EXTRA_LIMIT)
                AuthLoginEmails.insert {
                    it[AuthLoginEmails.emailNormalized] = destination; it[AuthLoginEmails.userId] = userId; it[AuthLoginEmails.isPrimary] = false
                    it[AuthLoginEmails.verifiedAtMillis] = now; it[AuthLoginEmails.createdAtMillis] = now
                }
                AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq row[AuthOneTimeChallenges.id] }) {
                    it[verifiedAtMillis] = now; it[consumedAtMillis] = now; it[updatedAtMillis] = now
                }
                AuthEmailAliasChallenges.update({ AuthEmailAliasChallenges.challengePublicId eq publicId }) { it[AuthEmailAliasChallenges.consumedAtMillis] = now }
                AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                    it[securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1; it[updatedAtMillis] = now
                }
                auditInside(userId, "EMAIL_ALIAS_CONFIRMED", crypto.hmac("identifier", destination), null, now)
                true
            }
        } catch (error: ExposedSQLException) {
            // A competing account registration/confirmation won the registry's unique key.
            // Catch OUTSIDE the transaction: do not commit an aborted SQL transaction.
            if (error.sqlState == "23505") throw AitaAuthContactConflictException(AuthContactConflict.EMAIL) else throw error
        }
        return if (applied) settings(userId) else null
    }

    suspend fun removeEmailAlias(userId: UUID, request: AitaEmailAliasRemoveRequestDataModel, ip: String): AitaAuthenticationSettingsDataModel? {
        config.requireAdvancedAuthentication()
        val email = normalizeAitaEmail(request.email) ?: return null
        val now = System.currentTimeMillis()
        if (!limiter.allow("email-alias-auth:$userId", 12, now)) throw AitaAuthRateLimitedException(3600L)
        val removed = newSuspendedTransaction(Dispatchers.IO) {
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction false
            // Lock challenge rows before the profile, like code verification. Do not
            // deadlock against a verifier which owns a challenge and needs this profile.
            AuthLoginChallenges.selectAll().where {
                (AuthLoginChallenges.userId eq userId) and AuthLoginChallenges.consumedAtMillis.isNull()
            }.orderBy(AuthLoginChallenges.id to SortOrder.ASC).forUpdate().toList()
            AuthOneTimeChallenges.selectAll().where {
                (AuthOneTimeChallenges.userId eq userId) and
                    (AuthOneTimeChallenges.deliveryEmailHash eq crypto.hmac("delivery-email", email)) and
                    AuthOneTimeChallenges.consumedAtMillis.isNull()
            }.orderBy(AuthOneTimeChallenges.id to SortOrder.ASC).forUpdate().toList()
            ensureProfileInside(userId, now)
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().single()
            if (!verifyEmailAliasCredentialsInside(user, request.currentPassword, request.secondFactorCode, now, request.emailProof, AitaSecurityEmailAction.REMOVE_EMAIL, email)) return@newSuspendedTransaction false
            val removedCount = AuthLoginEmails.deleteWhere {
                (AuthLoginEmails.userId eq userId) and (AuthLoginEmails.emailNormalized eq email) and (AuthLoginEmails.isPrimary eq false)
            }
            if (removedCount != 1) return@newSuspendedTransaction false
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1; it[updatedAtMillis] = now
            }
            AuthOneTimeChallenges.update({ (AuthOneTimeChallenges.userId eq userId) and
                (AuthOneTimeChallenges.deliveryEmailHash eq crypto.hmac("delivery-email", email)) and AuthOneTimeChallenges.consumedAtMillis.isNull() }) {
                it[consumedAtMillis] = now; it[updatedAtMillis] = now
            }
            AuthLoginChallenges.update({ (AuthLoginChallenges.userId eq userId) and AuthLoginChallenges.consumedAtMillis.isNull() }) { it[consumedAtMillis] = now }
            auditInside(userId, "EMAIL_ALIAS_REMOVED", crypto.hmac("identifier", email), crypto.hmac("ip", ip), now)
            true
        }
        return if (removed) settings(userId) else null
    }

    private fun loginFactorInside(profile: ResultRow?): AitaLoginSecondFactor = if (profile == null) AitaLoginSecondFactor.NONE else
        aitaLoginSecondFactor(profile[AuthSecurityProfiles.totpEnabledAtMillis] != null,
            profile[AuthSecurityProfiles.totpRequiredForLogin], profile[AuthSecurityProfiles.emailRequiredForLogin])

    private fun emailDestinationInside(user: ResultRow, choice: AitaEmailDestination): String? = when (choice) {
        AitaEmailDestination.MAIN -> normalizeAitaEmail(user[Users.email])
        AitaEmailDestination.EXTRA -> AuthLoginEmails.selectAll().where {
            (AuthLoginEmails.userId eq user[Users.id]) and (AuthLoginEmails.isPrimary eq false) and AuthLoginEmails.verifiedAtMillis.isNotNull()
        }.orderBy(AuthLoginEmails.createdAtMillis to SortOrder.ASC, AuthLoginEmails.emailNormalized to SortOrder.ASC)
            .firstOrNull()?.get(AuthLoginEmails.emailNormalized)?.let(::normalizeAitaEmail)
    }

    private fun emailOptionsInside(user: ResultRow): List<AitaEmailDestinationOption> = AitaEmailDestination.entries.mapNotNull { choice ->
        emailDestinationInside(user, choice)?.let { AitaEmailDestinationOption(choice, maskAitaEmailDestination(it)) }
    }

    /** Password-bound challenge only. Anonymous phone requests never get these masked addresses. */
    suspend fun requestLoginEmailFactor(request: AitaLoginEmailFactorRequest, ip: String): AitaAuthFlowDataModel? {
        requireEmailDelivery()
        val loginId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val now = System.currentTimeMillis()
        val ipHash = crypto.hmac("ip", ip)
        requireActionBudget("email-factor:$ipHash", 60, now)
        return newSuspendedTransaction(Dispatchers.IO) {
            val peek = AuthLoginChallenges.selectAll().where { AuthLoginChallenges.publicId eq loginId }.singleOrNull() ?: return@newSuspendedTransaction null
            val userId = peek[AuthLoginChallenges.userId]
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction null
            val login = usableLoginChallengeInside(loginId, userId, setOf(AUTH_LOGIN_CHALLENGE_PASSWORD), now) ?: return@newSuspendedTransaction null
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.singleOrNull()
            if (loginFactorInside(profile) != AitaLoginSecondFactor.EMAIL) return@newSuspendedTransaction null
            val destination = emailDestinationInside(user, request.destination) ?: return@newSuspendedTransaction null
            val idHash = crypto.hmac("login-email-factor", userId.toString())
            lockEmailBucketsInside(idHash, ipHash, userId)
            val previous = AuthLoginEmailChallenges.selectAll().where { AuthLoginEmailChallenges.loginPublicId eq loginId }
                .map { it[AuthLoginEmailChallenges.challengePublicId] }
            val latest = if (previous.isEmpty()) null else AuthOneTimeChallenges.selectAll().where {
                (AuthOneTimeChallenges.publicId inList previous) and AuthOneTimeChallenges.consumedAtMillis.isNull()
            }.orderBy(AuthOneTimeChallenges.createdAtMillis to SortOrder.DESC).limit(1).forUpdate().singleOrNull()
            fun flow(value: AitaAuthFlowDataModel) = value.copy(nextStep = AitaAuthNextStep.EMAIL_SECOND_FACTOR,
                parentFlowId = loginId.toString(), selectedEmailDestination = request.destination,
                maskedDestination = maskAitaEmailDestination(destination), emailDestinations = emailOptionsInside(user))
            if (latest != null && latest[AuthOneTimeChallenges.resendAfterMillis] > now) {
                if (latest[AuthOneTimeChallenges.deliveryEmailHash] != crypto.hmac("delivery-email", destination) ||
                    latest[AuthOneTimeChallenges.attempts] >= latest[AuthOneTimeChallenges.maxAttempts])
                    throw AitaAuthRateLimitedException((latest[AuthOneTimeChallenges.resendAfterMillis] - now) / 1000L + 1L)
                return@newSuspendedTransaction flow(emailFlow(latest, now))
            }
            checkEmailQuotaInside(idHash, ipHash, now)
            if (previous.isNotEmpty()) AuthOneTimeChallenges.update({ AuthOneTimeChallenges.publicId inList previous }) {
                it[consumedAtMillis] = now; it[updatedAtMillis] = now
            }
            val result = createEmailChallengeInside(userId, destination, AUTH_PURPOSE_EMAIL_FACTOR, request.locale, idHash, ipHash, now,
                deadlineMillis = login[AuthLoginChallenges.expiresAtMillis])
            AuthLoginEmailChallenges.insert {
                it[challengePublicId] = UUID.fromString(result.flowId); it[loginPublicId] = loginId
            }
            flow(result)
        }
    }

    suspend fun verifyLoginEmailFactor(request: AitaEmailCodeVerifyRequestDataModel, meta: Map<String, String>): AitaAuthFlowDataModel? {
        config.requireAdvancedAuthentication()
        val id = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val code = normalizeAitaOneTimeCode(request.code) ?: return null
        val userId = newSuspendedTransaction(Dispatchers.IO) {
            AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq id }.singleOrNull()?.get(AuthOneTimeChallenges.userId)
        } ?: return null
        val tokens = tokenService.newPairAfterVerification(userId, meta) {
            val now = System.currentTimeMillis()
            val link = AuthLoginEmailChallenges.selectAll().where { AuthLoginEmailChallenges.challengePublicId eq id }.singleOrNull()
                ?: return@newPairAfterVerification false
            val login = usableLoginChallengeInside(link[AuthLoginEmailChallenges.loginPublicId], userId, setOf(AUTH_LOGIN_CHALLENGE_PASSWORD), now)
                ?: return@newPairAfterVerification false
            val user = Users.selectAll().where { Users.id eq userId }.single()
            val row = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq id }.forUpdate().singleOrNull()
                ?: return@newPairAfterVerification false
            if (row[AuthOneTimeChallenges.userId] != userId || row[AuthOneTimeChallenges.purpose] != AUTH_PURPOSE_EMAIL_FACTOR ||
                !authCodeCanBeVerified(now, row[AuthOneTimeChallenges.expiresAtMillis], row[AuthOneTimeChallenges.consumedAtMillis],
                    row[AuthOneTimeChallenges.verifiedAtMillis], row[AuthOneTimeChallenges.attempts], row[AuthOneTimeChallenges.maxAttempts])) return@newPairAfterVerification false
            if (loginFactorInside(AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.singleOrNull()) != AitaLoginSecondFactor.EMAIL) return@newPairAfterVerification false
            val destination = challengeDestinationInside(row, user) ?: return@newPairAfterVerification false
            val valid = crypto.constantTimeEquals(row[AuthOneTimeChallenges.codeHash], crypto.hmac("code:$id", code))
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.publicId eq id }) {
                it[attempts] = row[AuthOneTimeChallenges.attempts] + 1; it[updatedAtMillis] = now
                if (valid) { it[verifiedAtMillis] = now; it[consumedAtMillis] = now }
            }
            AuthLoginChallenges.update({ AuthLoginChallenges.id eq login[AuthLoginChallenges.id] }) {
                it[attempts] = login[AuthLoginChallenges.attempts] + 1
                if (valid) it[consumedAtMillis] = now
            }
            if (valid && destination == normalizeAitaEmail(user[Users.email])) AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[emailVerifiedAtMillis] = now; it[updatedAtMillis] = now
            }
            if (valid) auditInside(userId, "EMAIL_SECOND_FACTOR_COMPLETED", null, meta["ip"]?.let { crypto.hmac("ip", it) }, now)
            valid
        } ?: return null
        return AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.AUTHENTICATED, tokenPair = tokens)
    }

    /** A signed-in bearer is NOT enough: check the password and bind the exact operation/revision.
     * Only the existing main email receives these proofs; client cannot supply another recipient.
     */
    suspend fun requestSecurityEmail(userId: UUID, request: AitaSecurityEmailRequest, ip: String): AitaAuthFlowDataModel? {
        requireEmailDelivery()
        val target = canonicalAitaSecurityTarget(request.action, request.target) ?: return null
        val now = System.currentTimeMillis()
        requireActionBudget("security-email:$userId", 12, now)
        val ipHash = crypto.hmac("ip", ip)
        return newSuspendedTransaction(Dispatchers.IO) {
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction null
            ensureProfileInside(userId, now)
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().single()
            if (!user[Users.isActive] || request.currentPassword.length !in 1..1024 ||
                profile[AuthSecurityProfiles.securityRevision] != request.expectedSecurityRevision ||
                !Pw.verify(request.currentPassword.toCharArray(), user[Users.passwordHash])) return@newSuspendedTransaction null
            val email = normalizeAitaEmail(user[Users.email]) ?: return@newSuspendedTransaction null
            val idHash = crypto.hmac("security-email-user", userId.toString())
            lockEmailBucketsInside(idHash, ipHash, userId)
            val targetHash = crypto.hmac("security-target:${request.action.name}", target)
            val authorization = loginAuthorizationInside(user)
            val latest = AuthOneTimeChallenges.selectAll().where {
                (AuthOneTimeChallenges.userId eq userId) and (AuthOneTimeChallenges.purpose eq AUTH_PURPOSE_SECURITY_EMAIL) and
                    AuthOneTimeChallenges.consumedAtMillis.isNull() and (AuthOneTimeChallenges.expiresAtMillis greater now)
            }.orderBy(AuthOneTimeChallenges.createdAtMillis to SortOrder.DESC).limit(1).forUpdate().singleOrNull()
            if (latest != null && latest[AuthOneTimeChallenges.resendAfterMillis] > now) {
                val old = AuthSecurityEmailChallenges.selectAll().where { AuthSecurityEmailChallenges.challengePublicId eq latest[AuthOneTimeChallenges.publicId] }.singleOrNull()
                if (old != null && latest[AuthOneTimeChallenges.attempts] < latest[AuthOneTimeChallenges.maxAttempts] &&
                    old[AuthSecurityEmailChallenges.action] == request.action.name &&
                    old[AuthSecurityEmailChallenges.targetHash] == targetHash && old[AuthSecurityEmailChallenges.authorizationHash] == authorization)
                    return@newSuspendedTransaction emailFlow(latest, now).copy(maskedDestination = maskAitaEmailDestination(email))
                throw AitaAuthRateLimitedException((latest[AuthOneTimeChallenges.resendAfterMillis] - now) / 1000L + 1L)
            }
            checkEmailQuotaInside(idHash, ipHash, now)
            AuthOneTimeChallenges.update({ (AuthOneTimeChallenges.userId eq userId) and (AuthOneTimeChallenges.purpose eq AUTH_PURPOSE_SECURITY_EMAIL) and AuthOneTimeChallenges.consumedAtMillis.isNull() }) {
                it[consumedAtMillis] = now; it[updatedAtMillis] = now
            }
            val flow = createEmailChallengeInside(userId, email, AUTH_PURPOSE_SECURITY_EMAIL, request.locale, idHash, ipHash, now)
            AuthSecurityEmailChallenges.insert {
                it[challengePublicId] = UUID.fromString(flow.flowId); it[AuthSecurityEmailChallenges.userId] = userId
                it[action] = request.action.name; it[AuthSecurityEmailChallenges.targetHash] = targetHash; it[authorizationHash] = authorization
            }
            flow.copy(maskedDestination = maskAitaEmailDestination(email))
        }
    }

    private fun verifySecurityEmailInside(user: ResultRow, proof: AitaSecurityEmailProof?, action: AitaSecurityEmailAction, target: String, now: Long): Boolean {
        val id = proof?.flowId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return false
        val code = normalizeAitaOneTimeCode(proof.code) ?: return false
        val canonical = canonicalAitaSecurityTarget(action, target) ?: return false
        val binding = AuthSecurityEmailChallenges.selectAll().where { AuthSecurityEmailChallenges.challengePublicId eq id }.singleOrNull() ?: return false
        if (binding[AuthSecurityEmailChallenges.userId] != user[Users.id] || binding[AuthSecurityEmailChallenges.action] != action.name ||
            !crypto.constantTimeEquals(binding[AuthSecurityEmailChallenges.targetHash], crypto.hmac("security-target:${action.name}", canonical)) ||
            !crypto.constantTimeEquals(binding[AuthSecurityEmailChallenges.authorizationHash], loginAuthorizationInside(user))) return false
        val row = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq id }.forUpdate().singleOrNull() ?: return false
        if (row[AuthOneTimeChallenges.userId] != user[Users.id] || row[AuthOneTimeChallenges.purpose] != AUTH_PURPOSE_SECURITY_EMAIL ||
            !authCodeCanBeVerified(now, row[AuthOneTimeChallenges.expiresAtMillis], row[AuthOneTimeChallenges.consumedAtMillis],
                row[AuthOneTimeChallenges.verifiedAtMillis], row[AuthOneTimeChallenges.attempts], row[AuthOneTimeChallenges.maxAttempts]) ||
            challengeDestinationInside(row, user) == null) return false
        val valid = crypto.constantTimeEquals(row[AuthOneTimeChallenges.codeHash], crypto.hmac("code:$id", code))
        AuthOneTimeChallenges.update({ AuthOneTimeChallenges.publicId eq id }) {
            it[attempts] = row[AuthOneTimeChallenges.attempts] + 1; it[updatedAtMillis] = now
            if (valid) { it[consumedAtMillis] = now; it[verifiedAtMillis] = now }
        }
        if (valid) AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq user[Users.id] }) { it[emailVerifiedAtMillis] = now }
        return valid
    }

    suspend fun updateLoginPolicy(userId: UUID, request: AitaLoginPolicyRequest): AitaAuthenticationSettingsDataModel? {
        config.requireAdvancedAuthentication()
        requireActionBudget("login-policy:$userId", 12, System.currentTimeMillis())
        val changed = newSuspendedTransaction(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val user = lockSecurityUserInside(userId) ?: return@newSuspendedTransaction false
            ensureProfileInside(userId, now)
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().single()
            if (profile[AuthSecurityProfiles.securityRevision] != request.expectedSecurityRevision || !user[Users.isActive] ||
                request.currentPassword.length !in 1..1024 || !Pw.verify(request.currentPassword.toCharArray(), user[Users.passwordHash])) return@newSuspendedTransaction false
            if (request.method == AitaLoginSecondFactor.AUTHENTICATOR && profile[AuthSecurityProfiles.totpEnabledAtMillis] == null) return@newSuspendedTransaction false
            if (request.method == AitaLoginSecondFactor.EMAIL && (!config.emailReady || normalizeAitaEmail(user[Users.email]) == null)) return@newSuspendedTransaction false
            // Never require the same TOTP twice. Current enrollment protects factor changes;
            // main-email proof additionally verifies the destination before enabling email 2FA.
            if (profile[AuthSecurityProfiles.totpEnabledAtMillis] != null && !verifySecondFactorInside(userId, request.secondFactorCode, now)) return@newSuspendedTransaction false
            val needsEmail = request.method == AitaLoginSecondFactor.EMAIL ||
                (profile[AuthSecurityProfiles.totpEnabledAtMillis] == null && profile[AuthSecurityProfiles.emailRequiredForLogin])
            if (needsEmail && !verifySecurityEmailInside(user, request.emailProof, AitaSecurityEmailAction.LOGIN_POLICY, request.method.name, now)) return@newSuspendedTransaction false
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[totpRequiredForLogin] = request.method == AitaLoginSecondFactor.AUTHENTICATOR
                it[emailRequiredForLogin] = request.method == AitaLoginSecondFactor.EMAIL
                it[securityRevision] = profile[AuthSecurityProfiles.securityRevision] + 1L; it[updatedAtMillis] = now
                // A pending setup made before this policy must not later undo it.
                it[totpPendingSecretCiphertext] = null; it[totpPendingSetupId] = null; it[totpPendingExpiresAtMillis] = null
            }
            AuthLoginChallenges.update({ (AuthLoginChallenges.userId eq userId) and AuthLoginChallenges.consumedAtMillis.isNull() }) { it[consumedAtMillis] = now }
            auditInside(userId, "LOGIN_POLICY_${request.method.name}", null, null, now)
            true
        }
        return if (changed) settings(userId) else null
    }

    /** Called under the account row lock by the existing profile route. */
    internal fun verifyProfileSecurityInside(user: ResultRow, request: kz.aita.UserAccountUpdateDataModel): Boolean {
        val account = request.account
        val changesProtectedValues = normalizeAitaEmail(account.email) != normalizeAitaEmail(user[Users.email]) ||
            normalizeAitaPhoneAlias(account.phoneNumber) != normalizeAitaStoredMainPhone(user[Users.phoneNumber], user[Users.countryLocale]) ||
            account.isActive != user[Users.isActive] || !request.newPassword.isNullOrBlank()
        if (!changesProtectedValues) return true
        // The profile route has already locked and validated an exact new-address contact receipt.
        // Existing password and enrolled-factor checks below still authorize the account change.
        return verifyEmailAliasCredentialsInside(user, request.password, request.secondFactorCode, System.currentTimeMillis(),
            request.emailProof, AitaSecurityEmailAction.PROFILE, aitaProfileSecurityTarget(account.phoneNumber, account.email, account.isActive))
    }

    private fun verifySecondFactorInside(userId: UUID, rawCode: String, now: Long): Boolean {
        config.requireSecurityConfigured()
        if (!aitaSecondFactorIsWellFormed(rawCode)) return false
        val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.forUpdate().singleOrNull() ?: return false
        if (profile[AuthSecurityProfiles.totpEnabledAtMillis] == null) return false
        val secret = profile[AuthSecurityProfiles.totpSecretCiphertext]?.let {
            runCatching { crypto.decrypt("totp-active:$userId", it) }.getOrNull()
        }
        val step = secret?.let { runCatching { aitaTotpMatchingStep(it, rawCode, now, profile[AuthSecurityProfiles.totpLastUsedStep]) }.getOrNull() }
        if (step != null) {
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) { it[totpLastUsedStep] = step }
            return true
        }
        val normalizedRecovery = normalizeAitaRecoveryCode(rawCode)
        if (normalizedRecovery.length != 12) return false
        val hash = crypto.hmac("recovery:$userId", normalizedRecovery)
        val row = AuthRecoveryCodes.selectAll().where {
            (AuthRecoveryCodes.userId eq userId) and (AuthRecoveryCodes.codeHash eq hash) and AuthRecoveryCodes.usedAtMillis.isNull()
        }.forUpdate().singleOrNull() ?: return false
        AuthRecoveryCodes.update({ AuthRecoveryCodes.id eq row[AuthRecoveryCodes.id] }) { it[usedAtMillis] = now }
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

    /** Separate enrollment delivery from anonymous sign-in lookup (which must stay neutral). */
    private fun requireContactDelivery() {
        try { requireEmailDelivery() }
        catch (_: AitaAuthUnavailableException) { throw AitaContactDeliveryUnavailableException() }
    }

    internal fun lockContactActorInside(actor: UUID) {
        val user = lockSecurityUserInside(actor)
        if (user == null || !user[Users.isActive]) throw AitaContactVerificationRequiredException()
    }

    private fun contactScope(actor: UUID?, target: AitaContactTarget, address: String): String =
        crypto.hmac("contact-scope", listOf(AitaContactChannel.EMAIL.name, actor?.toString().orEmpty(), target.purpose.name,
            target.entityId, target.parentId, address).joinToString("\n"))

    private fun contactAuthorizationValidInside(binding: ResultRow): Boolean {
        val actor = binding[AuthContactVerifications.actorUserId]
        if (!contactTargetIsAuthorizedInside(actor, binding.contactTarget())) return false
        if (actor == null) return binding[AuthContactVerifications.authorizationHash] == null
        val user = Users.selectAll().where { Users.id eq actor }.singleOrNull() ?: return false
        return crypto.constantTimeEquals(binding[AuthContactVerifications.authorizationHash].orEmpty(), loginAuthorizationInside(user))
    }

    suspend fun requestContactCode(actor: UUID?, request: AitaContactCodeRequest, ip: String): AitaAuthFlowDataModel? {
        requireContactDelivery()
        if (request.channel != AitaContactChannel.EMAIL) return null // SMS is deliberately not implemented.
        val target = canonicalAitaContactTarget(request.target) ?: return null
        val address = normalizeAitaEmail(request.address) ?: return null
        if ((target.purpose == AitaContactPurpose.REGISTRATION) != (actor == null)) return null
        return issueContactCode(actor, target, address, request.locale, ip, null)
    }

    private suspend fun issueContactCode(actor: UUID?, target: AitaContactTarget, address: String,
        locale: String, ip: String, previousFlowId: UUID?): AitaAuthFlowDataModel? {
        val requestedAt = System.currentTimeMillis()
        // Destination quota is shared across ALL contact purposes, actors and draft identifiers.
        val emailHash = crypto.hmac("contact-destination", address)
        val ipHash = crypto.hmac("ip", ip)
        requireActionBudget("contact:request:ip:$ipHash", 120, requestedAt)
        val scopeHash = contactScope(actor, target, address)
        return newSuspendedTransaction(Dispatchers.IO) {
            val user = actor?.let(::lockSecurityUserInside)
            if (actor != null && user == null) return@newSuspendedTransaction null
            if (!contactTargetIsAuthorizedInside(actor, target)) return@newSuspendedTransaction null
            lockEmailBucketsInside(emailHash, ipHash, actor)
            val now = System.currentTimeMillis()
            val binding = if (previousFlowId != null) AuthContactVerifications.selectAll()
                .where { AuthContactVerifications.challengePublicId eq previousFlowId }.singleOrNull()
            else AuthContactVerifications.selectAll().where { AuthContactVerifications.scopeHash eq scopeHash }
                .orderBy(AuthContactVerifications.createdAtMillis to SortOrder.DESC).limit(1).singleOrNull()
            if (previousFlowId != null && (binding == null || binding[AuthContactVerifications.scopeHash] != scopeHash ||
                !contactAuthorizationValidInside(binding))) return@newSuspendedTransaction null
            val old = binding?.let { b -> AuthOneTimeChallenges.selectAll()
                .where { AuthOneTimeChallenges.publicId eq b[AuthContactVerifications.challengePublicId] }.forUpdate().singleOrNull() }
            if (previousFlowId != null && (old == null || old[AuthOneTimeChallenges.purpose] != AUTH_PURPOSE_CONTACT ||
                old[AuthOneTimeChallenges.consumedAtMillis] != null || old[AuthOneTimeChallenges.verifiedAtMillis] != null ||
                old[AuthOneTimeChallenges.expiresAtMillis] <= now)) return@newSuspendedTransaction null
            if (old != null && old[AuthOneTimeChallenges.consumedAtMillis] == null &&
                old[AuthOneTimeChallenges.verifiedAtMillis] == null && old[AuthOneTimeChallenges.expiresAtMillis] > now &&
                old[AuthOneTimeChallenges.attempts] < old[AuthOneTimeChallenges.maxAttempts] &&
                old[AuthOneTimeChallenges.resendAfterMillis] > now && binding != null && contactAuthorizationValidInside(binding)) {
                return@newSuspendedTransaction emailFlow(old, now).copy(maskedDestination = maskEmail(address))
            }
            checkEmailQuotaInside(emailHash, ipHash, now)
            if (actor != null) {
                val hourly = AuthContactVerifications.select(AuthContactVerifications.createdAtMillis).where {
                    (AuthContactVerifications.actorUserId eq actor) and (AuthContactVerifications.createdAtMillis greaterEq now - 3_600_000L)
                }.orderBy(AuthContactVerifications.createdAtMillis to SortOrder.DESC).limit(30).toList()
                if (hourly.size >= 30) throw AitaAuthRateLimitedException(
                    ((hourly.last()[AuthContactVerifications.createdAtMillis] + 3_600_000L - now) / 1000L + 1L).coerceAtLeast(1L))
            }
            if (old != null && old[AuthOneTimeChallenges.consumedAtMillis] == null) {
                AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq old[AuthOneTimeChallenges.id] }) {
                    it[consumedAtMillis] = now; it[updatedAtMillis] = now
                }
            }
            val deadline = if (previousFlowId == null) now + config.codeTtlMillis else requireNotNull(old)[AuthOneTimeChallenges.expiresAtMillis]
            val flow = createEmailChallengeInside(actor, address, AUTH_PURPOSE_CONTACT, locale, emailHash, ipHash, now, deadline)
            AuthContactVerifications.insert {
                it[challengePublicId] = UUID.fromString(flow.flowId); it[actorUserId] = actor
                it[channel] = AitaContactChannel.EMAIL.name; it[contactPurpose] = target.purpose.name
                it[entityId] = target.entityId; it[parentId] = target.parentId; it[AuthContactVerifications.address] = address
                it[AuthContactVerifications.scopeHash] = scopeHash
                it[authorizationHash] = user?.let(::loginAuthorizationInside); it[createdAtMillis] = now
            }
            flow.copy(maskedDestination = maskEmail(address))
        }
    }

    suspend fun resendContactCode(actor: UUID?, request: AitaEmailCodeResendRequestDataModel, ip: String): AitaAuthFlowDataModel? {
        requireContactDelivery()
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val snapshot = newSuspendedTransaction(Dispatchers.IO) {
            val row = AuthContactVerifications.selectAll().where { AuthContactVerifications.challengePublicId eq publicId }.singleOrNull()
                ?: return@newSuspendedTransaction null
            if (row[AuthContactVerifications.actorUserId] != actor) return@newSuspendedTransaction null
            row.contactTarget() to row[AuthContactVerifications.address]
        } ?: return null
        return issueContactCode(actor, snapshot.first, snapshot.second, request.locale, ip, publicId)
    }

    suspend fun verifyContactCode(actor: UUID?, request: AitaEmailCodeVerifyRequestDataModel, ip: String): AitaContactVerificationResult? {
        config.requireSecurityConfigured()
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val code = normalizeAitaOneTimeCode(request.code) ?: return null
        val requestedAt = System.currentTimeMillis()
        requireActionBudget("contact:verify:ip:${crypto.hmac("ip", ip)}", 120, requestedAt)
        return newSuspendedTransaction(Dispatchers.IO) {
            actor?.let { if (lockSecurityUserInside(it) == null) return@newSuspendedTransaction null }
            val row = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }.forUpdate().singleOrNull()
                ?: return@newSuspendedTransaction null
            if (row[AuthOneTimeChallenges.purpose] != AUTH_PURPOSE_CONTACT || row[AuthOneTimeChallenges.userId] != actor) return@newSuspendedTransaction null
            val binding = AuthContactVerifications.selectAll().where { AuthContactVerifications.challengePublicId eq publicId }.forUpdate().singleOrNull()
                ?: return@newSuspendedTransaction null
            if (binding[AuthContactVerifications.actorUserId] != actor || !contactAuthorizationValidInside(binding)) return@newSuspendedTransaction null
            val now = System.currentTimeMillis() // Do not validate against a timestamp from before lock waits.
            val deadline = if (row[AuthOneTimeChallenges.verifiedAtMillis] != null) binding[AuthContactVerifications.receiptExpiresAtMillis] ?: 0L
                else row[AuthOneTimeChallenges.expiresAtMillis]
            if (row[AuthOneTimeChallenges.consumedAtMillis] != null || deadline <= now ||
                row[AuthOneTimeChallenges.attempts] >= row[AuthOneTimeChallenges.maxAttempts]) return@newSuspendedTransaction null
            if (!crypto.constantTimeEquals(row[AuthOneTimeChallenges.codeHash], crypto.hmac("code:$publicId", code))) {
                // Return normally so failed attempts COMMIT; throwing would roll the counter back.
                AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq row[AuthOneTimeChallenges.id] }) {
                    it[attempts] = row[AuthOneTimeChallenges.attempts] + 1; it[updatedAtMillis] = now
                }
                return@newSuspendedTransaction null
            }
            val oldReceipt = binding[AuthContactVerifications.receiptCiphertext]
            val receipt = oldReceipt?.let { crypto.decrypt("contact-receipt:$publicId", it) } ?: crypto.randomToken()
            val expires = binding[AuthContactVerifications.receiptExpiresAtMillis] ?: (now + config.resetTtlMillis)
            if (oldReceipt == null) {
                AuthContactVerifications.update({ AuthContactVerifications.challengePublicId eq publicId }) {
                    it[receiptHash] = crypto.hmac("contact-proof:$publicId", receipt)
                    it[receiptCiphertext] = crypto.encrypt("contact-receipt:$publicId", receipt)
                    it[receiptExpiresAtMillis] = expires
                }
                AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq row[AuthOneTimeChallenges.id] }) {
                    it[verifiedAtMillis] = now; it[updatedAtMillis] = now
                }
                auditInside(actor, "CONTACT_EMAIL_VERIFIED", row[AuthOneTimeChallenges.identifierHash], row[AuthOneTimeChallenges.requestIpHash], now)
            }
            // Retries with the same code recover the SAME proof without extending its lifetime.
            AitaContactVerificationResult(AitaVerifiedContactProof(publicId.toString(), receipt), binding.contactTarget(),
                binding[AuthContactVerifications.address], expiresAtMillis = expires, serverTimeMillis = now)
        }
    }

    /** The caller's mutation transaction owns these locks until save/rollback. No code is accepted here. */
    internal fun requireContactProofsInside(actor: UUID?, target: AitaContactTarget, proposed: List<String>, previous: List<String>,
        proofs: List<AitaVerifiedContactProof>): List<UUID> {
        val needed = aitaContactEmailsRequiringProof(proposed, previous) ?: throw BadRequestException("Invalid contact email")
        if (needed.isEmpty()) return emptyList()
        config.requireSecurityConfigured()
        val canonicalTarget = canonicalAitaContactTarget(target) ?: throw AitaContactVerificationRequiredException()
        if (!contactTargetIsAuthorizedInside(actor, canonicalTarget)) throw AitaContactVerificationRequiredException()
        if (proofs.size != needed.size || proofs.size > 10 || proofs.map { it.flowId }.distinct().size != proofs.size)
            throw AitaContactVerificationRequiredException()
        var earliestExpiry = Long.MAX_VALUE
        val matched = mutableSetOf<String>()
        val ids = mutableListOf<UUID>()
        for (proof in proofs.sortedBy { it.flowId }) {
            val publicId = runCatching { UUID.fromString(proof.flowId) }.getOrNull() ?: throw AitaContactVerificationRequiredException()
            if (proof.receipt.length !in 40..128) throw AitaContactVerificationRequiredException()
            val row = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }.forUpdate().singleOrNull()
                ?: throw AitaContactVerificationRequiredException()
            val binding = AuthContactVerifications.selectAll().where { AuthContactVerifications.challengePublicId eq publicId }.forUpdate().singleOrNull()
                ?: throw AitaContactVerificationRequiredException()
            val now = System.currentTimeMillis()
            earliestExpiry = minOf(earliestExpiry, binding[AuthContactVerifications.receiptExpiresAtMillis] ?: 0L)
            val address = binding[AuthContactVerifications.address]
            if (address !in needed || !matched.add(address) || row[AuthOneTimeChallenges.purpose] != AUTH_PURPOSE_CONTACT ||
                row[AuthOneTimeChallenges.userId] != actor || row[AuthOneTimeChallenges.verifiedAtMillis] == null ||
                !aitaContactProofMatches(canonicalTarget, binding.contactTarget(), actor?.toString(), binding[AuthContactVerifications.actorUserId]?.toString(),
                    address, address, AitaContactChannel.valueOf(binding[AuthContactVerifications.channel]), now,
                    binding[AuthContactVerifications.receiptExpiresAtMillis] ?: 0L, row[AuthOneTimeChallenges.consumedAtMillis]) ||
                !contactAuthorizationValidInside(binding) ||
                !crypto.constantTimeEquals(binding[AuthContactVerifications.receiptHash].orEmpty(), crypto.hmac("contact-proof:$publicId", proof.receipt)))
                throw AitaContactVerificationRequiredException()
            ids += publicId
        }
        if (matched.size != needed.size || System.currentTimeMillis() >= earliestExpiry) throw AitaContactVerificationRequiredException()
        return ids
    }

    /** Consume only AFTER every proof/permission passes, in the SAME transaction as the actual write. */
    internal fun consumeContactProofsInside(publicIds: List<UUID>, appliedEntityId: UUID) {
        val now = System.currentTimeMillis()
        for (publicId in publicIds) {
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.publicId eq publicId }) {
                it[consumedAtMillis] = now; it[updatedAtMillis] = now
            }
            // Erase the recoverable receipt on consumption; the HMAC remains only as audit material.
            AuthContactVerifications.update({ AuthContactVerifications.challengePublicId eq publicId }) {
                it[receiptCiphertext] = ""
                it[AuthContactVerifications.appliedEntityId] = appliedEntityId
                it[appliedAtMillis] = now
            }
        }
    }

    internal fun markPrimaryEmailVerifiedInside(userId: UUID, email: String) {
        val now = System.currentTimeMillis()
        ensureProfileInside(userId, now)
        AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
            it[emailVerifiedAtMillis] = now; it[updatedAtMillis] = now
        }
        AuthLoginEmails.update({ (AuthLoginEmails.userId eq userId) and (AuthLoginEmails.emailNormalized eq email) }) {
            it[verifiedAtMillis] = now
        }
    }

    internal fun enqueueOldEmailChangeNoticeInside(userId: UUID, oldEmail: String, locale: String) {
        val destination = normalizeAitaEmail(oldEmail) ?: return
        val now = System.currentTimeMillis()
        val flow = createEmailChallengeInside(userId, destination, AUTH_PURPOSE_CONTACT_NOTICE, locale,
            crypto.hmac("contact-change-notice", userId.toString()), crypto.hmac("ip", "internal-notice"), now, now + 86_400_000L)
        AuthContactChangeNotices.insert {
            it[challengePublicId] = UUID.fromString(flow.flowId); it[AuthContactChangeNotices.userId] = userId
            it[address] = destination; it[createdAtMillis] = now
        }
        auditInside(userId, "ACCOUNT_EMAIL_CHANGED_CONFIRMED", null, null, now)
    }

    private fun contactDeliveryDestinationInside(challenge: ResultRow): String? {
        val binding = AuthContactVerifications.selectAll().where {
            AuthContactVerifications.challengePublicId eq challenge[AuthOneTimeChallenges.publicId]
        }.singleOrNull() ?: return null
        if (binding[AuthContactVerifications.channel] != AitaContactChannel.EMAIL.name ||
            binding[AuthContactVerifications.actorUserId] != challenge[AuthOneTimeChallenges.userId] ||
            !contactAuthorizationValidInside(binding)) return null
        val address = binding[AuthContactVerifications.address]
        return address.takeIf { crypto.constantTimeEquals(challenge[AuthOneTimeChallenges.deliveryEmailHash].orEmpty(), crypto.hmac("delivery-email", it)) }
    }

    private fun contactNoticeDestinationInside(challenge: ResultRow): String? {
        val notice = AuthContactChangeNotices.selectAll().where {
            AuthContactChangeNotices.challengePublicId eq challenge[AuthOneTimeChallenges.publicId]
        }.singleOrNull() ?: return null
        if (notice[AuthContactChangeNotices.userId] != challenge[AuthOneTimeChallenges.userId]) return null
        val address = notice[AuthContactChangeNotices.address]
        return address.takeIf { crypto.constantTimeEquals(challenge[AuthOneTimeChallenges.deliveryEmailHash].orEmpty(), crypto.hmac("delivery-email", it)) }
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
                val user = userId?.let { id -> Users.selectAll().where { Users.id eq id }.singleOrNull() }
                val contactPurpose = challenge[AuthOneTimeChallenges.purpose] == AUTH_PURPOSE_CONTACT
                val contactNotice = challenge[AuthOneTimeChallenges.purpose] == AUTH_PURPOSE_CONTACT_NOTICE
                if (!contactPurpose && user == null)
                    return@newSuspendedTransaction DeliveryWork(cancellation = "ACCOUNT_UNAVAILABLE")
                val destination = when {
                    contactPurpose -> contactDeliveryDestinationInside(challenge)
                    contactNotice -> contactNoticeDestinationInside(challenge)
                    else -> challengeDestinationInside(challenge, requireNotNull(user))
                } ?: return@newSuspendedTransaction DeliveryWork(cancellation = "RECIPIENT_CHANGED_OR_UNVERIFIED")
                if (!authEmailCanBeDelivered(System.currentTimeMillis(), challenge[AuthOneTimeChallenges.expiresAtMillis],
                        challenge[AuthOneTimeChallenges.consumedAtMillis], challenge[AuthOneTimeChallenges.verifiedAtMillis],
                        contactNotice || (if (contactPurpose) true else user?.get(Users.isActive) == true)))
                    return@newSuspendedTransaction DeliveryWork(cancellation = "CODE_EXPIRED_OR_REPLACED")
                if (challenge[AuthOneTimeChallenges.purpose] == AUTH_PURPOSE_RECOVERY && !config.passwordRecoveryEnabled)
                    return@newSuspendedTransaction DeliveryWork(cancellation = "RECOVERY_DISABLED")

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

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}

private val AdvancedAuthServiceKey = AttributeKey<AitaAdvancedAuthService>("AITA.AdvancedAuthentication.Service")

internal fun advancedAuthService(tokenService: TokenService, application: Application): AitaAdvancedAuthService =
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

/** Also used by the legacy password route, so it cannot bypass an enabled login requirement. */
suspend fun advancedAuthSecondFactorEnabled(userId: UUID): Boolean =
    newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }
            .limit(1).singleOrNull() ?: return@newSuspendedTransaction false
        aitaLoginSecondFactor(profile[AuthSecurityProfiles.totpEnabledAtMillis] != null,
            profile[AuthSecurityProfiles.totpRequiredForLogin], profile[AuthSecurityProfiles.emailRequiredForLogin]) != AitaLoginSecondFactor.NONE
    }

suspend fun resolveAdvancedAuthUser(identifier: String): UUID? {
    val normalized = normalizeAitaLoginIdentifier(identifier) ?: return null
    return newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        when (normalized.kind) {
            AitaAuthIdentifierKind.EMAIL -> AuthLoginEmails.selectAll().where {
                (AuthLoginEmails.emailNormalized eq normalized.value) and
                    ((AuthLoginEmails.isPrimary eq true) or AuthLoginEmails.verifiedAtMillis.isNotNull())
            }.singleOrNull()?.get(AuthLoginEmails.userId)
            AitaAuthIdentifierKind.PHONE -> resolvePhoneLoginUserInside(normalized.value)
        }
    }
}

internal fun verifyAdvancedAuthProfileChangeInside(user: ResultRow, request: kz.aita.UserAccountUpdateDataModel,
    tokenService: TokenService, application: Application): Boolean =
    advancedAuthService(tokenService, application).verifyProfileSecurityInside(user, request)

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
        route("/registration/email") {
            installContactVerificationActions(service, registration = true)
        }
        get("/capabilities") {
            call.genericResponse(HttpStatusCode.OK, service.capabilities())
        }

        post("/login/email-factor/request") {
            val result = service.requestLoginEmailFactor(call.receiveAita<AitaLoginEmailFactorRequest>(), call.authClientIp())
            if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest,
                eventMessage("auth.message.sign_in_again_or_choose_an_available_email"))
            else call.genericResponse(HttpStatusCode.Accepted, result)
        }
        post("/login/email-factor/verify") {
            val request = call.receiveAita<AitaEmailCodeVerifyRequestDataModel>()
            val result = service.verifyLoginEmailFactor(request, authMeta(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest,
                eventMessage("auth.message.code_invalid_or_expired"))
            else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/login/password") {
            val request = call.receiveAita<AitaPasswordLoginRequestDataModel>()
            val result = service.passwordLogin(request.copy(deviceInfo = request.deviceInfo), authMeta(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                eventMessage("message.invalid_login_or_password")
            ) else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/login/code/request") {
            val request = call.receiveAita<AitaEmailCodeRequestDataModel>()
            val result = service.requestEmailCode(request.identifier, AUTH_PURPOSE_LOGIN, request.locale, call.authClientIp(), request.destination)
            call.genericResponse(HttpStatusCode.Accepted, result)
        }

        post("/login/code/resend") {
            val request = call.receiveAita<AitaEmailCodeResendRequestDataModel>()
            val result = service.resend(request.flowId, request.locale, call.authClientIp(), AUTH_PURPOSE_LOGIN)
            if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized,
                eventMessage("auth.message.request_a_new_code"))
            else call.genericResponse(HttpStatusCode.Accepted, result)
        }

        post("/login/code/verify") {
            val request = call.receiveAita<AitaEmailCodeVerifyRequestDataModel>()
            val result = service.verifyEmailCode(request, AUTH_PURPOSE_LOGIN, authMeta(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                eventMessage("auth.message.the_code_is_invalid_or_expired")
            ) else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/login/totp") {
            val request = call.receiveAita<AitaTotpLoginRequestDataModel>()
            val result = service.completeTotpLogin(request, authMeta(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                eventMessage("auth.message.the_authenticator_or_recovery_code_is_invalid")
            ) else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/login/authenticator") {
            val request = call.receiveAita<AitaAuthenticatorLoginRequestDataModel>()
            val result = service.authenticatorLogin(request, authMeta(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized,
                eventMessage("auth.message.check_the_login_and_use_a_fresh_authenticator_or_recovery_code"))
            else call.genericResponse(HttpStatusCode.OK, result)
        }
        post("/login/authenticator/password") {
            val request = call.receiveAita<AitaAuthenticatorPasswordRequestDataModel>()
            val result = service.completeAuthenticatorPassword(request, authMeta(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized,
                eventMessage("auth.message.password_is_incorrect_or_this_sign_in_has_expired"))
            else call.genericResponse(HttpStatusCode.OK, result)
        }
        post("/authenticator-recovery/request") {
            val request = call.receiveAita<AitaAuthenticatorRecoveryRequestDataModel>()
            call.genericResponse(HttpStatusCode.Accepted, service.requestAuthenticatorRecovery(request, call.authClientIp()))
        }
        post("/authenticator-recovery/resend") {
            val request = call.receiveAita<AitaEmailCodeResendRequestDataModel>()
            val result = service.resend(request.flowId, request.locale, call.authClientIp(), AUTH_PURPOSE_TOTP_RECOVERY)
            if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized,
                eventMessage("auth.message.start_authenticator_recovery_again"))
            else call.genericResponse(HttpStatusCode.Accepted, result)
        }
        post("/authenticator-recovery/confirm") {
            val request = call.receiveAita<AitaEmailCodeVerifyRequestDataModel>()
            val recoveredUserId = service.confirmAuthenticatorRecovery(request)
            if (recoveredUserId != null) call.genericResponse(HttpStatusCode.OK,
                AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.COMPLETE, recoveredUserId = recoveredUserId.toString()),
                eventMessage("auth.message.authenticator_removed_sign_in_again_and_connect_a_new_authenticator"))
            else call.genericResponseNoPayload(HttpStatusCode.Unauthorized,
                eventMessage("auth.message.the_code_is_invalid_expired_or_the_security_settings_changed"))
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
                eventMessage("auth.message.request_a_new_code"))
            else call.genericResponse(HttpStatusCode.Accepted, result)
        }

        post("/password-recovery/verify") {
            val request = call.receiveAita<AitaEmailCodeVerifyRequestDataModel>()
            val result = service.verifyEmailCode(request, AUTH_PURPOSE_RECOVERY, authMeta(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                eventMessage("auth.message.the_code_is_invalid_or_expired")
            ) else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/password-recovery/reset") {
            val request = call.receiveAita<AitaPasswordRecoveryResetRequestDataModel>()
            if (!request.newPassword.checkAsPassword()) {
                return@post call.genericResponseNoPayload(
                    HttpStatusCode.BadRequest,
                    eventMessage("auth.message.password_must_contain_at_least_8_characters_a_digit_and_a_special")
                )
            }
            if (service.resetPassword(request)) call.genericResponse(
                HttpStatusCode.OK,
                AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.COMPLETE),
                eventMessage("auth.message.password_restored_sign_in_with_the_new_password")
            ) else call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                eventMessage("auth.message.the_recovery_session_is_invalid_or_expired")
            )
        }

        authenticate("auth-jwt") {
            route("/security") {
                route("/contact-verification") {
                    installContactVerificationActions(service, registration = false)
                }
                post("/email-proof/request") {
                    val userId = call.checkPrincipal() ?: return@post
                    val result = service.requestSecurityEmail(userId, call.receiveAita<AitaSecurityEmailRequest>(), call.authClientIp())
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest,
                        eventMessage("auth.message.check_your_password_and_refresh_settings"))
                    else call.genericResponse(HttpStatusCode.Accepted, result)
                }
                post("/login-policy") {
                    val userId = call.checkPrincipal() ?: return@post
                    val result = service.updateLoginPolicy(userId, call.receiveAita<AitaLoginPolicyRequest>())
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest,
                        eventMessage("auth.message.security_confirmation_failed_or_settings_changed_refresh_and_try_again"))
                    else call.genericResponse(HttpStatusCode.OK, result)
                }
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
                            eventMessage("auth.message.security_confirmation_failed")
                        )
                    } else {
                        call.genericResponse(HttpStatusCode.OK, result)
                    }
                }

                post("/totp/setup/confirm") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaTotpSetupConfirmRequestDataModel>()
                    val result = service.confirmTotpSetup(userId, request)
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized, eventMessage("auth.message.authenticator_code_is_invalid"))
                    else call.genericResponse(HttpStatusCode.OK, result)
                }

                post("/totp/login-policy") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaTotpLoginPolicyRequestDataModel>()
                    val result = service.updateTotpLoginPolicy(userId, request)
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest,
                        eventMessage("auth.message.security_confirmation_failed_or_settings_changed_refresh_and_try_again"))
                    else call.genericResponse(HttpStatusCode.OK, result)
                }

                post("/totp/disable") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaSensitiveSecurityActionRequestDataModel>()
                    val result = service.disableTotp(userId, request)
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized, eventMessage("auth.message.security_confirmation_failed"))
                    else call.genericResponse(HttpStatusCode.OK, result)
                }

                post("/totp/recovery-codes/regenerate") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaSensitiveSecurityActionRequestDataModel>()
                    val codes = service.regenerateRecoveryCodes(userId, request)
                    if (codes == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized, eventMessage("auth.message.security_confirmation_failed"))
                    else call.genericResponse(HttpStatusCode.OK, AitaAuthFlowDataModel(nextStep = AitaAuthNextStep.COMPLETE, recoveryCodes = codes))
                }

                post("/email/request") {
                    val userId = call.checkPrincipal() ?: return@post
                    val result = service.requestEmailAlias(userId, call.receiveAita<AitaEmailAliasRequestDataModel>(), call.authClientIp())
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest, eventMessage("auth.message.could_not_add_this_extra_email_check_your_password_security_code_and"))
                    else call.genericResponse(HttpStatusCode.Accepted, result)
                }
                post("/email/resend") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaEmailCodeResendRequestDataModel>()
                    val result = service.resend(request.flowId, request.locale, call.authClientIp(), AUTH_PURPOSE_EMAIL_ALIAS, userId)
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest, eventMessage("auth.message.start_extra_email_setup_again"))
                    else call.genericResponse(HttpStatusCode.Accepted, result)
                }
                post("/email/confirm") {
                    val userId = call.checkPrincipal() ?: return@post
                    val result = service.confirmEmailAlias(userId, call.receiveAita<AitaEmailAliasConfirmRequestDataModel>())
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest, eventMessage("auth.message.code_invalid_expired_or_extra_email_unavailable"))
                    else call.genericResponse(HttpStatusCode.OK, result)
                }
                post("/email/remove") {
                    val userId = call.checkPrincipal() ?: return@post
                    val result = service.removeEmailAlias(userId, call.receiveAita<AitaEmailAliasRemoveRequestDataModel>(), call.authClientIp())
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest, eventMessage("auth.message.security_confirmation_failed"))
                    else call.genericResponse(HttpStatusCode.OK, result)
                }

                post("/phone/request") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaPhoneAliasRequestDataModel>()
                    val result = service.requestPhoneAlias(userId, request, call.authClientIp())
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest, eventMessage("auth.message.extra_phone_number_change_could_not_be_requested"))
                    else call.genericResponse(HttpStatusCode.Accepted, result)
                }

                post("/phone/resend") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaEmailCodeResendRequestDataModel>()
                    val result = service.resend(request.flowId, request.locale, call.authClientIp(), AUTH_PURPOSE_PHONE, userId)
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized,
                        eventMessage("auth.message.request_a_new_code"))
                    else call.genericResponse(HttpStatusCode.Accepted, result)
                }

                post("/phone/confirm") {
                    val userId = call.checkPrincipal() ?: return@post
                    val request = call.receiveAita<AitaPhoneAliasConfirmRequestDataModel>()
                    val result = service.confirmPhoneAlias(userId, request)
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.Unauthorized, eventMessage("auth.message.the_confirmation_code_is_invalid_or_expired"))
                    else call.genericResponse(HttpStatusCode.OK, result)
                }
            }
        }
    }
}

/** Anonymous enrollment endpoints can NEVER request or verify an authenticated contact purpose. */
private fun Route.installContactVerificationActions(service: AitaAdvancedAuthService, registration: Boolean) {
    post("/request") {
        val actor: UUID? = if (registration) null else (call.checkPrincipal() ?: return@post)
        val request = call.receiveAita<AitaContactCodeRequest>()
        if ((request.target.purpose == AitaContactPurpose.REGISTRATION) != registration || request.channel != AitaContactChannel.EMAIL) {
            call.genericResponseNoPayload(HttpStatusCode.BadRequest, eventMessage("contact.invalid_request"))
            return@post
        }
        val result = service.requestContactCode(actor, request, call.authClientIp())
        if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest, eventMessage("contact.invalid_request"))
        else call.genericResponse(HttpStatusCode.Accepted, result)
    }
    post("/resend") {
        val actor: UUID? = if (registration) null else (call.checkPrincipal() ?: return@post)
        val result = service.resendContactCode(actor, call.receiveAita<AitaEmailCodeResendRequestDataModel>(), call.authClientIp())
        if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest, eventMessage("contact.restart"))
        else call.genericResponse(HttpStatusCode.Accepted, result)
    }
    post("/verify") {
        val actor: UUID? = if (registration) null else (call.checkPrincipal() ?: return@post)
        val result = service.verifyContactCode(actor, call.receiveAita<AitaEmailCodeVerifyRequestDataModel>(), call.authClientIp())
        if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest, eventMessage("contact.code_invalid"))
        else call.genericResponse(HttpStatusCode.OK, result)
    }
}
