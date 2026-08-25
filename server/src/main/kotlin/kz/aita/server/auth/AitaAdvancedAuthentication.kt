package kz.aita.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.origin
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kz.aita.ClientDeviceInfoDataModel
import kz.aita.LocalizedStringDataModel
import kz.aita.TokenPair
import kz.aita.auth.*
import kz.aita.checkAsPassword
import kz.aita.jsonBase
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
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
import kotlin.math.pow

private const val AUTH_PURPOSE_LOGIN = "PASSWORDLESS_LOGIN"
private const val AUTH_PURPOSE_RECOVERY = "PASSWORD_RECOVERY"
private const val AUTH_PURPOSE_PHONE = "PHONE_ALIAS"
private const val AUTH_OUTBOX_PENDING = "PENDING"
private const val AUTH_OUTBOX_PROCESSING = "PROCESSING"
private const val AUTH_OUTBOX_RETRY = "RETRY_WAIT"
private const val AUTH_OUTBOX_SENT = "SENT"
private const val AUTH_OUTBOX_FAILED = "FAILED"
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

private fun authEnv(name: String): String? = System.getenv(name)?.trim()?.takeIf(String::isNotEmpty)
    ?: System.getProperty(name)?.trim()?.takeIf(String::isNotEmpty)

private fun authBoolean(name: String, default: Boolean): Boolean = when (authEnv(name)?.lowercase(Locale.ROOT)) {
    "1", "true", "yes", "on" -> true
    "0", "false", "no", "off" -> false
    else -> default
}

private fun authLong(name: String, default: Long): Long = authEnv(name)?.toLongOrNull() ?: default

private data class AdvancedAuthConfig(
    val enabled: Boolean,
    val passwordRecoveryEnabled: Boolean,
    val emailProvider: String,
    val resendApiKey: String,
    val fromEmail: String,
    val replyTo: String,
    val codePepper: ByteArray,
    val encryptionKey: ByteArray,
    val codeTtlMillis: Long,
    val resetTtlMillis: Long,
    val resendCooldownMillis: Long,
    val maxAttempts: Int,
    val issuerName: String
) {
    val emailConfigured: Boolean
        get() = emailProvider.equals("resend", true) && resendApiKey.isNotBlank() && fromEmail.isNotBlank()

    companion object {
        fun load(): AdvancedAuthConfig {
            val production = authEnv("AITA_ENV")?.lowercase(Locale.ROOT) in setOf("production", "prod", "stage", "staging", "cloud")
            val enabled = authBoolean("AITA_ADVANCED_AUTH_ENABLED", default = !production)
            val pepperText = authEnv("AITA_AUTH_CODE_PEPPER")
                ?: if (production && enabled) error("AITA_AUTH_CODE_PEPPER must be configured")
                else "aita-local-development-auth-code-pepper-change-before-production-2026"
            if (production && enabled) require(pepperText.length >= 64) { "AITA_AUTH_CODE_PEPPER must be at least 64 characters" }

            val key = authEnv("AITA_ACCOUNT_SECURITY_MASTER_KEY_B64")?.let {
                runCatching { Base64.getDecoder().decode(it) }.getOrNull()
            } ?: if (production && enabled) {
                error("AITA_ACCOUNT_SECURITY_MASTER_KEY_B64 must be configured")
            } else {
                MessageDigest.getInstance("SHA-256").digest("aita-local-development-account-security-key".toByteArray())
            }
            require(key.size == 32) { "AITA_ACCOUNT_SECURITY_MASTER_KEY_B64 must decode to 32 bytes" }

            return AdvancedAuthConfig(
                enabled = enabled,
                passwordRecoveryEnabled = authBoolean("AITA_PASSWORD_RECOVERY_ENABLED", enabled),
                emailProvider = authEnv("AITA_AUTH_EMAIL_PROVIDER") ?: "resend",
                resendApiKey = authEnv("AITA_RESEND_API_KEY").orEmpty(),
                fromEmail = authEnv("AITA_AUTH_EMAIL_FROM").orEmpty(),
                replyTo = authEnv("AITA_AUTH_EMAIL_REPLY_TO").orEmpty(),
                codePepper = pepperText.toByteArray(StandardCharsets.UTF_8),
                encryptionKey = key,
                codeTtlMillis = authLong("AITA_AUTH_CODE_TTL_SECONDS", 600L).coerceIn(120L, 1800L) * 1000L,
                resetTtlMillis = authLong("AITA_AUTH_RESET_TTL_SECONDS", 600L).coerceIn(120L, 1800L) * 1000L,
                resendCooldownMillis = authLong("AITA_AUTH_RESEND_COOLDOWN_SECONDS", 60L).coerceIn(20L, 600L) * 1000L,
                maxAttempts = authLong("AITA_AUTH_MAX_CODE_ATTEMPTS", 5L).toInt().coerceIn(3, 10),
                issuerName = authEnv("AITA_AUTH_TOTP_ISSUER") ?: "AITA"
            )
        }
    }
}

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
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(config.encryptionKey, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(context.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return "v1.${Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)}.${Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)}"
    }

    fun decrypt(context: String, encoded: String): String {
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

    suspend fun resolveUser(identifier: String): AuthUser? {
        val normalized = normalizeAitaLoginIdentifier(identifier) ?: return null
        return newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val userId = when (normalized.kind) {
                AitaAuthIdentifierKind.EMAIL -> Users.selectAll()
                    .where { Users.email eq normalized.value }
                    .limit(1).singleOrNull()?.get(Users.id)
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

    fun capabilities(): AitaAuthCapabilitiesDataModel = AitaAuthCapabilitiesDataModel(
        enabled = config.enabled,
        passwordLoginEnabled = true,
        emailCodeLoginEnabled = config.enabled && config.emailConfigured,
        passwordRecoveryEnabled = config.enabled && config.passwordRecoveryEnabled && config.emailConfigured,
        authenticatorTwoFactorEnabled = config.enabled,
        phoneLoginAliasEnabled = config.enabled && config.emailConfigured,
        codeLength = 6,
        codeTtlSeconds = config.codeTtlMillis / 1000L,
        resendCooldownSeconds = config.resendCooldownMillis / 1000L
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

    suspend fun requestEmailCode(
        identifier: String,
        purpose: String,
        locale: String,
        ip: String
    ): AitaAuthFlowDataModel {
        val now = System.currentTimeMillis()
        val normalized = normalizeAitaLoginIdentifier(identifier)
        val identifierValue = normalized?.value.orEmpty()
        val identifierHash = crypto.hmac("identifier", identifierValue.ifBlank { "invalid" })
        val ipHash = crypto.hmac("ip", ip)
        val featureEnabled = config.enabled &&
            (purpose != AUTH_PURPOSE_RECOVERY || config.passwordRecoveryEnabled)
        val processRateAllowed = featureEnabled &&
            limiter.allow("id:$identifierHash", 5, now) &&
            limiter.allow("ip:$ipHash", 30, now)
        val persistentRateAllowed = if (processRateAllowed) {
            newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
                val since = now - 3_600_000L
                val identifierRequests = AuthOneTimeChallenges.selectAll().where {
                    (AuthOneTimeChallenges.identifierHash eq identifierHash) and
                        (AuthOneTimeChallenges.createdAtMillis greaterEq since)
                }.count()
                val ipRequests = AuthOneTimeChallenges.selectAll().where {
                    (AuthOneTimeChallenges.requestIpHash eq ipHash) and
                        (AuthOneTimeChallenges.createdAtMillis greaterEq since)
                }.count()
                identifierRequests < 5L && ipRequests < 30L
            }
        } else {
            false
        }
        val user = if (persistentRateAllowed) resolveUser(identifier) else null
        val fakePublicId = UUID.randomUUID().toString()
        val generic = AitaAuthFlowDataModel(
            flowId = fakePublicId,
            nextStep = AitaAuthNextStep.EMAIL_CODE,
            maskedDestination = normalized
                ?.takeIf { it.kind == AitaAuthIdentifierKind.EMAIL }
                ?.value
                ?.let(::maskEmail)
                .orEmpty(),
            expiresAtMillis = now + config.codeTtlMillis,
            resendAfterMillis = now + config.resendCooldownMillis
        )
        if (user == null || !user.active || user.email.isBlank() || !config.emailConfigured) {
            delay((80L..180L).random())
            return generic
        }

        val code = crypto.randomCode()
        val challengeId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            AuthOneTimeChallenges.update({
                (AuthOneTimeChallenges.userId eq user.id) and
                    (AuthOneTimeChallenges.purpose eq purpose) and
                    AuthOneTimeChallenges.consumedAtMillis.isNull()
            }) {
                it[AuthOneTimeChallenges.consumedAtMillis] = now
                it[AuthOneTimeChallenges.updatedAtMillis] = now
            }
            AuthOneTimeChallenges.insert {
                it[AuthOneTimeChallenges.id] = challengeId
                it[AuthOneTimeChallenges.publicId] = publicId
                it[AuthOneTimeChallenges.userId] = user.id
                it[AuthOneTimeChallenges.purpose] = purpose
                it[AuthOneTimeChallenges.identifierHash] = identifierHash
                it[AuthOneTimeChallenges.requestIpHash] = ipHash
                it[AuthOneTimeChallenges.locale] = locale.take(16).ifBlank { "en" }
                it[AuthOneTimeChallenges.codeHash] = crypto.hmac("code:$publicId", code)
                it[AuthOneTimeChallenges.codeCiphertext] = crypto.encrypt("auth-code:$challengeId", code)
                it[AuthOneTimeChallenges.attempts] = 0
                it[AuthOneTimeChallenges.maxAttempts] = config.maxAttempts
                it[AuthOneTimeChallenges.expiresAtMillis] = now + config.codeTtlMillis
                it[AuthOneTimeChallenges.resendAfterMillis] = now + config.resendCooldownMillis
                it[AuthOneTimeChallenges.createdAtMillis] = now
                it[AuthOneTimeChallenges.updatedAtMillis] = now
            }
            AuthEmailOutbox.insert {
                it[AuthEmailOutbox.id] = UUID.randomUUID()
                it[AuthEmailOutbox.challengeId] = challengeId
                it[AuthEmailOutbox.status] = AUTH_OUTBOX_PENDING
                it[AuthEmailOutbox.attempts] = 0
                it[AuthEmailOutbox.maxAttempts] = 8
                it[AuthEmailOutbox.nextAttemptAtMillis] = now
                it[AuthEmailOutbox.createdAtMillis] = now
                it[AuthEmailOutbox.updatedAtMillis] = now
            }
            auditInside(user.id, "EMAIL_CODE_REQUESTED_$purpose", identifierHash, ipHash, now)
        }
        return generic.copy(flowId = publicId.toString())
    }

    suspend fun resend(flowId: String, locale: String, ip: String): AitaAuthFlowDataModel? {
        val publicId = runCatching { UUID.fromString(flowId) }.getOrNull() ?: return null
        val now = System.currentTimeMillis()
        return newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val old = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction null
            val userId = old[AuthOneTimeChallenges.userId] ?: return@newSuspendedTransaction null
            if (old[AuthOneTimeChallenges.consumedAtMillis] != null || old[AuthOneTimeChallenges.expiresAtMillis] <= now) return@newSuspendedTransaction null
            if (old[AuthOneTimeChallenges.resendAfterMillis] > now) {
                return@newSuspendedTransaction AitaAuthFlowDataModel(
                    flowId = flowId,
                    nextStep = AitaAuthNextStep.EMAIL_CODE,
                    expiresAtMillis = old[AuthOneTimeChallenges.expiresAtMillis],
                    resendAfterMillis = old[AuthOneTimeChallenges.resendAfterMillis]
                )
            }
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@newSuspendedTransaction null
            val code = crypto.randomCode()
            val newChallengeId = UUID.randomUUID()
            val newPublicId = UUID.randomUUID()
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq old[AuthOneTimeChallenges.id] }) {
                it[AuthOneTimeChallenges.consumedAtMillis] = now
                it[AuthOneTimeChallenges.updatedAtMillis] = now
            }
            AuthOneTimeChallenges.insert {
                it[AuthOneTimeChallenges.id] = newChallengeId
                it[AuthOneTimeChallenges.publicId] = newPublicId
                it[AuthOneTimeChallenges.userId] = userId
                it[AuthOneTimeChallenges.purpose] = old[AuthOneTimeChallenges.purpose]
                it[AuthOneTimeChallenges.identifierHash] = old[AuthOneTimeChallenges.identifierHash]
                it[AuthOneTimeChallenges.requestIpHash] = crypto.hmac("ip", ip)
                it[AuthOneTimeChallenges.locale] = locale.take(16).ifBlank { old[AuthOneTimeChallenges.locale] }
                it[AuthOneTimeChallenges.codeHash] = crypto.hmac("code:$newPublicId", code)
                it[AuthOneTimeChallenges.codeCiphertext] = crypto.encrypt("auth-code:$newChallengeId", code)
                it[AuthOneTimeChallenges.attempts] = 0
                it[AuthOneTimeChallenges.maxAttempts] = old[AuthOneTimeChallenges.maxAttempts]
                it[AuthOneTimeChallenges.expiresAtMillis] = now + config.codeTtlMillis
                it[AuthOneTimeChallenges.resendAfterMillis] = now + config.resendCooldownMillis
                it[AuthOneTimeChallenges.createdAtMillis] = now
                it[AuthOneTimeChallenges.updatedAtMillis] = now
            }
            AuthEmailOutbox.insert {
                it[AuthEmailOutbox.id] = UUID.randomUUID()
                it[AuthEmailOutbox.challengeId] = newChallengeId
                it[AuthEmailOutbox.status] = AUTH_OUTBOX_PENDING
                it[AuthEmailOutbox.attempts] = 0
                it[AuthEmailOutbox.maxAttempts] = 8
                it[AuthEmailOutbox.nextAttemptAtMillis] = now
                it[AuthEmailOutbox.createdAtMillis] = now
                it[AuthEmailOutbox.updatedAtMillis] = now
            }
            AitaAuthFlowDataModel(
                flowId = newPublicId.toString(),
                nextStep = AitaAuthNextStep.EMAIL_CODE,
                maskedDestination = "",
                expiresAtMillis = now + config.codeTtlMillis,
                resendAfterMillis = now + config.resendCooldownMillis
            )
        }
    }

    suspend fun verifyEmailCode(
        request: AitaEmailCodeVerifyRequestDataModel,
        expectedPurpose: String,
        meta: Map<String, String>
    ): AitaAuthFlowDataModel? {
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return null
        val code = normalizeAitaOneTimeCode(request.code) ?: return null
        val now = System.currentTimeMillis()
        val verified = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val row = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction null
            val userId = row[AuthOneTimeChallenges.userId] ?: return@newSuspendedTransaction null
            val userActive = Users.selectAll().where { Users.id eq userId }
                .limit(1).singleOrNull()?.get(Users.isActive) == true
            if (!userActive) return@newSuspendedTransaction null
            if (row[AuthOneTimeChallenges.purpose] != expectedPurpose || row[AuthOneTimeChallenges.consumedAtMillis] != null ||
                row[AuthOneTimeChallenges.expiresAtMillis] <= now || row[AuthOneTimeChallenges.attempts] >= row[AuthOneTimeChallenges.maxAttempts]
            ) return@newSuspendedTransaction null
            val valid = crypto.constantTimeEquals(row[AuthOneTimeChallenges.codeHash], crypto.hmac("code:$publicId", code))
            AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq row[AuthOneTimeChallenges.id] }) {
                it[AuthOneTimeChallenges.attempts] = row[AuthOneTimeChallenges.attempts] + 1
                it[AuthOneTimeChallenges.updatedAtMillis] = now
                if (valid) it[AuthOneTimeChallenges.verifiedAtMillis] = now
            }
            if (!valid) return@newSuspendedTransaction null
            ensureProfileInside(userId, now)
            AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq userId }) {
                it[AuthSecurityProfiles.emailVerifiedAtMillis] = now
                it[AuthSecurityProfiles.updatedAtMillis] = now
            }
            Triple(row[AuthOneTimeChallenges.id], userId, row[AuthOneTimeChallenges.expiresAtMillis])
        } ?: return null

        val (challengeId, userId, expires) = verified
        return when (expectedPurpose) {
            AUTH_PURPOSE_LOGIN -> {
                newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
                    AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq challengeId }) {
                        it[AuthOneTimeChallenges.consumedAtMillis] = now
                        it[AuthOneTimeChallenges.updatedAtMillis] = now
                    }
                }
                if (totpEnabled(userId)) createLoginChallenge(userId, AUTH_LOGIN_CHALLENGE_EMAIL)
                else AitaAuthFlowDataModel(
                    nextStep = AitaAuthNextStep.AUTHENTICATED,
                    tokenPair = tokenService.newPair(userId, meta)
                )
            }
            AUTH_PURPOSE_RECOVERY -> {
                val ticket = crypto.randomToken(32)
                newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
                    AuthOneTimeChallenges.update({ AuthOneTimeChallenges.id eq challengeId }) {
                        it[AuthOneTimeChallenges.resetTicketHash] = crypto.hmac("reset:$publicId", ticket)
                        it[AuthOneTimeChallenges.resetTicketExpiresAtMillis] = now + config.resetTtlMillis
                        it[AuthOneTimeChallenges.updatedAtMillis] = now
                    }
                }
                AitaAuthFlowDataModel(
                    flowId = publicId.toString(),
                    nextStep = AitaAuthNextStep.PASSWORD_RESET,
                    expiresAtMillis = minOf(expires, now + config.resetTtlMillis),
                    resetTicket = ticket
                )
            }
            else -> null
        }
    }

    suspend fun resetPassword(request: AitaPasswordRecoveryResetRequestDataModel): Boolean {
        if (!request.newPassword.checkAsPassword()) return false
        val publicId = runCatching { UUID.fromString(request.flowId) }.getOrNull() ?: return false
        val now = System.currentTimeMillis()
        return newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val row = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq publicId }
                .forUpdate().singleOrNull() ?: return@newSuspendedTransaction false
            val userId = row[AuthOneTimeChallenges.userId] ?: return@newSuspendedTransaction false
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
            auditInside(userId, "PASSWORD_RESET_COMPLETED", row[AuthOneTimeChallenges.identifierHash], row[AuthOneTimeChallenges.requestIpHash], now)
            true
        }
    }

    private suspend fun createLoginChallenge(userId: UUID, method: String): AitaAuthFlowDataModel {
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
            expiresAtMillis = now + 5 * 60_000L
        )
    }

    suspend fun completeTotpLogin(request: AitaTotpLoginRequestDataModel, meta: Map<String, String>): AitaAuthFlowDataModel? {
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
        if (!config.enabled || !verifySensitiveAction(userId, request)) return null
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
        if (!config.enabled || !config.emailConfigured) return null
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
        val user = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            Users.selectAll().where { Users.id eq userId }.singleOrNull()
        } ?: return null
        val flow = requestEmailCode(user[Users.email], AUTH_PURPOSE_PHONE, request.locale, ip)
        val publicId = runCatching { UUID.fromString(flow.flowId) }.getOrNull() ?: return null
        val challengeOwnedByUser = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            AuthOneTimeChallenges.selectAll().where {
                (AuthOneTimeChallenges.publicId eq publicId) and
                    (AuthOneTimeChallenges.userId eq userId) and
                    (AuthOneTimeChallenges.purpose eq AUTH_PURPOSE_PHONE)
            }.limit(1).any()
        }
        if (!challengeOwnedByUser) return null
        newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            AuthPhoneAliasChallenges.insert {
                it[AuthPhoneAliasChallenges.id] = UUID.randomUUID()
                it[AuthPhoneAliasChallenges.challengePublicId] = publicId
                it[AuthPhoneAliasChallenges.userId] = userId
                it[AuthPhoneAliasChallenges.action] = request.action.name
                it[AuthPhoneAliasChallenges.requestedPhoneAlias] = phone
                it[AuthPhoneAliasChallenges.createdAtMillis] = System.currentTimeMillis()
            }
        }
        return flow
    }

    suspend fun confirmPhoneAlias(userId: UUID, request: AitaPhoneAliasConfirmRequestDataModel): AitaAuthenticationSettingsDataModel? {
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
            if (!Pw.verify(request.currentPassword.toCharArray(), user[Users.passwordHash])) return@newSuspendedTransaction false
            val profile = AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }.singleOrNull()
            if (profile?.get(AuthSecurityProfiles.totpEnabledAtMillis) != null) verifySecondFactorInside(userId, request.secondFactorCode, System.currentTimeMillis()) else true
        }

    private fun verifySecondFactorInside(userId: UUID, rawCode: String, now: Long): Boolean {
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
            AuthSecurityProfiles.insert {
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
        if (!workerStarted.compareAndSet(false, true)) return
        scope.launch {
            while (isActive) {
                try {
                    recoverStaleEmailLeases()
                    val workId = claimEmail()
                    if (workId == null) {
                        delay(2_500L)
                        continue
                    }
                    processEmail(workId)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (throwable: Throwable) {
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    application.environment.log.error("AITA authentication email worker failed", throwable)
                    delay(5_000L)
                }
            }
        }
    }

    private suspend fun claimEmail(): UUID? = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val row = AuthEmailOutbox.selectAll().where {
            (AuthEmailOutbox.status inList listOf(AUTH_OUTBOX_PENDING, AUTH_OUTBOX_RETRY)) and
                (AuthEmailOutbox.nextAttemptAtMillis lessEq now)
        }.orderBy(AuthEmailOutbox.createdAtMillis to SortOrder.ASC).forUpdate().limit(1).singleOrNull()
            ?: return@newSuspendedTransaction null
        AuthEmailOutbox.update({ AuthEmailOutbox.id eq row[AuthEmailOutbox.id] }) {
            it[AuthEmailOutbox.status] = AUTH_OUTBOX_PROCESSING
            it[AuthEmailOutbox.lockedAtMillis] = now
            it[AuthEmailOutbox.lockedBy] = workerId
            it[AuthEmailOutbox.updatedAtMillis] = now
        }
        row[AuthEmailOutbox.id]
    }

    private suspend fun recoverStaleEmailLeases() = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        val now = System.currentTimeMillis()
        AuthEmailOutbox.update({
            (AuthEmailOutbox.status eq AUTH_OUTBOX_PROCESSING) and
                (
                    AuthEmailOutbox.lockedAtMillis.isNull() or
                        (AuthEmailOutbox.lockedAtMillis lessEq now - 5 * 60_000L)
                )
        }) {
            it[AuthEmailOutbox.status] = AUTH_OUTBOX_RETRY
            it[AuthEmailOutbox.lockedAtMillis] = null
            it[AuthEmailOutbox.lockedBy] = null
            it[AuthEmailOutbox.nextAttemptAtMillis] = now
            it[AuthEmailOutbox.updatedAtMillis] = now
        }
    }

    private suspend fun processEmail(workId: UUID) {
        val payload = newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val work = AuthEmailOutbox.selectAll().where { AuthEmailOutbox.id eq workId }.singleOrNull()
                ?: return@newSuspendedTransaction null
            val challenge = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.id eq work[AuthEmailOutbox.challengeId] }.singleOrNull()
                ?: return@newSuspendedTransaction null
            val userId = challenge[AuthOneTimeChallenges.userId] ?: return@newSuspendedTransaction null
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@newSuspendedTransaction null
            val code = crypto.decrypt("auth-code:${challenge[AuthOneTimeChallenges.id]}", challenge[AuthOneTimeChallenges.codeCiphertext])
            EmailPayload(
                to = user[Users.email],
                purpose = challenge[AuthOneTimeChallenges.purpose],
                locale = challenge[AuthOneTimeChallenges.locale],
                code = code,
                attempts = work[AuthEmailOutbox.attempts],
                maxAttempts = work[AuthEmailOutbox.maxAttempts]
            )
        }
        if (payload == null || !config.emailConfigured) {
            finishEmail(workId, false, null, "EMAIL_PROVIDER_NOT_CONFIGURED", retry = false)
            return
        }
        val result = sendResendEmail(workId, payload)
        finishEmail(workId, result.success, result.messageId, result.errorCode, result.retry)
    }

    private suspend fun finishEmail(workId: UUID, success: Boolean, messageId: String?, errorCode: String?, retry: Boolean) =
        newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val row = AuthEmailOutbox.selectAll().where { AuthEmailOutbox.id eq workId }.forUpdate().singleOrNull()
                ?: return@newSuspendedTransaction
            val attempt = row[AuthEmailOutbox.attempts] + 1
            val canRetry = retry && attempt < row[AuthEmailOutbox.maxAttempts]
            AuthEmailOutbox.update({ AuthEmailOutbox.id eq workId }) {
                it[AuthEmailOutbox.attempts] = attempt
                it[AuthEmailOutbox.status] = when {
                    success -> AUTH_OUTBOX_SENT
                    canRetry -> AUTH_OUTBOX_RETRY
                    else -> AUTH_OUTBOX_FAILED
                }
                it[AuthEmailOutbox.providerMessageId] = messageId?.take(500)
                it[AuthEmailOutbox.lastErrorCode] = errorCode?.take(80)
                it[AuthEmailOutbox.lockedAtMillis] = null
                it[AuthEmailOutbox.lockedBy] = null
                it[AuthEmailOutbox.nextAttemptAtMillis] = if (canRetry) now + retryDelay(attempt) else now
                it[AuthEmailOutbox.updatedAtMillis] = now
                if (success) it[AuthEmailOutbox.sentAtMillis] = now
            }
            if (success) {
                AuthOneTimeChallenges.update({
                    AuthOneTimeChallenges.id eq row[AuthEmailOutbox.challengeId]
                }) {
                    // The HMAC remains sufficient for verification; discard the decryptable delivery copy.
                    it[AuthOneTimeChallenges.codeCiphertext] = ""
                    it[AuthOneTimeChallenges.updatedAtMillis] = now
                }
            }
        }

    private fun retryDelay(attempt: Int): Long = (2.0.pow(attempt.coerceIn(1, 8)) * 1_000L).toLong().coerceAtMost(15 * 60_000L) + random.nextLong(750L)

    @Serializable private data class ResendBody(val from: String, val to: List<String>, val subject: String, val html: String, val reply_to: String? = null)
    private data class EmailPayload(val to: String, val purpose: String, val locale: String, val code: String, val attempts: Int, val maxAttempts: Int)
    private data class EmailResult(val success: Boolean, val messageId: String? = null, val errorCode: String? = null, val retry: Boolean = false)

    private fun sendResendEmail(workId: UUID, payload: EmailPayload): EmailResult {
        val (subject, body) = emailCopy(payload.purpose, payload.locale, payload.code)
        val json = jsonBase.encodeToString(
            ResendBody.serializer(),
            ResendBody(config.fromEmail, listOf(payload.to), subject, body, config.replyTo.ifBlank { null })
        )
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
        val request = HttpRequest.newBuilder(URI("https://api.resend.com/emails"))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer ${config.resendApiKey}")
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", "aita-auth-$workId")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            val status = response.statusCode()
            when {
                status in 200..299 -> EmailResult(true, messageId = Regex("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(response.body().take(4096))?.groupValues?.getOrNull(1))
                status == 408 || status == 409 || status == 425 || status == 429 || status >= 500 -> EmailResult(false, errorCode = "RESEND_HTTP_$status", retry = true)
                else -> EmailResult(false, errorCode = "RESEND_HTTP_$status", retry = false)
            }
        } catch (throwable: Throwable) {
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            application.environment.log.warn("Authentication email delivery transport failure: ${throwable::class.simpleName}")
            EmailResult(false, errorCode = "RESEND_TRANSPORT", retry = true)
        }
    }

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

private object AdvancedAuthRuntime {
    @Volatile private var service: AitaAdvancedAuthService? = null
    fun get(tokenService: TokenService, application: Application): AitaAdvancedAuthService =
        service ?: synchronized(this) {
            service ?: AitaAdvancedAuthService(tokenService, AdvancedAuthConfig.load(), application).also { service = it }
        }
}

suspend fun advancedAuthSecondFactorEnabled(userId: UUID): Boolean =
    newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        AuthSecurityProfiles.selectAll().where { AuthSecurityProfiles.userId eq userId }
            .limit(1).singleOrNull()?.get(AuthSecurityProfiles.totpEnabledAtMillis) != null
    }

suspend fun resolveAdvancedAuthUser(identifier: String): UUID? {
    val normalized = normalizeAitaLoginIdentifier(identifier) ?: return null
    return newSuspendedTransaction(kotlinx.coroutines.Dispatchers.IO) {
        when (normalized.kind) {
            AitaAuthIdentifierKind.EMAIL -> Users.selectAll().where { Users.email eq normalized.value }.limit(1).singleOrNull()?.get(Users.id)
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
    val service = AdvancedAuthRuntime.get(tokenService, application)
    service.startEmailWorker(backgroundScope)

    route("/auth") {
        get("/capabilities") {
            call.genericResponse(HttpStatusCode.OK, service.capabilities())
        }

        post("/login/password") {
            val request = call.receiveAita<AitaPasswordLoginRequestDataModel>()
            val result = service.passwordLogin(request.copy(deviceInfo = request.deviceInfo), metaFrom(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                authMessage("Invalid login or password", "Неверный логин или пароль", "Логин немесе құпиясөз қате")
            ) else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/login/code/request") {
            val request = call.receiveAita<AitaEmailCodeRequestDataModel>()
            val result = service.requestEmailCode(request.identifier, AUTH_PURPOSE_LOGIN, request.locale, call.request.origin.remoteHost)
            call.genericResponse(
                HttpStatusCode.Accepted,
                result,
                authMessage(
                    "If the account can receive email, a sign-in code has been sent.",
                    "Если аккаунт может получать письма, код входа отправлен.",
                    "Аккаунт хат қабылдай алса, кіру коды жіберілді."
                )
            )
        }

        post("/login/code/resend") {
            val request = call.receiveAita<AitaEmailCodeResendRequestDataModel>()
            val result = service.resend(request.flowId, request.locale, call.request.origin.remoteHost)
            call.genericResponse(HttpStatusCode.Accepted, result ?: AitaAuthFlowDataModel(flowId = request.flowId, nextStep = AitaAuthNextStep.EMAIL_CODE))
        }

        post("/login/code/verify") {
            val request = call.receiveAita<AitaEmailCodeVerifyRequestDataModel>()
            val result = service.verifyEmailCode(request, AUTH_PURPOSE_LOGIN, metaFrom(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                authMessage("The code is invalid or expired", "Код неверен или истёк", "Код қате немесе мерзімі аяқталған")
            ) else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/login/totp") {
            val request = call.receiveAita<AitaTotpLoginRequestDataModel>()
            val result = service.completeTotpLogin(request, metaFrom(call, request.deviceInfo))
            if (result == null) call.genericResponseNoPayload(
                HttpStatusCode.Unauthorized,
                authMessage("The authenticator or recovery code is invalid", "Код аутентификатора или резервный код неверен", "Аутентификатор немесе қалпына келтіру коды қате")
            ) else call.genericResponse(HttpStatusCode.OK, result)
        }

        post("/password-recovery/request") {
            val request = call.receiveAita<AitaEmailCodeRequestDataModel>()
            val result = service.requestEmailCode(request.identifier, AUTH_PURPOSE_RECOVERY, request.locale, call.request.origin.remoteHost)
            call.genericResponse(
                HttpStatusCode.Accepted,
                result,
                authMessage(
                    "If the account can receive recovery email, a code has been sent.",
                    "Если аккаунт может получить письмо для восстановления, код отправлен.",
                    "Аккаунт қалпына келтіру хатын қабылдай алса, код жіберілді."
                )
            )
        }

        post("/password-recovery/resend") {
            val request = call.receiveAita<AitaEmailCodeResendRequestDataModel>()
            val result = service.resend(request.flowId, request.locale, call.request.origin.remoteHost)
            call.genericResponse(HttpStatusCode.Accepted, result ?: AitaAuthFlowDataModel(flowId = request.flowId, nextStep = AitaAuthNextStep.EMAIL_CODE))
        }

        post("/password-recovery/verify") {
            val request = call.receiveAita<AitaEmailCodeVerifyRequestDataModel>()
            val result = service.verifyEmailCode(request, AUTH_PURPOSE_RECOVERY, metaFrom(call, request.deviceInfo))
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
                    val result = service.requestPhoneAlias(userId, request, call.request.origin.remoteHost)
                    if (result == null) call.genericResponseNoPayload(HttpStatusCode.BadRequest, authMessage("Phone change could not be requested", "Не удалось запросить изменение номера", "Телефон өзгерісін сұрау мүмкін болмады"))
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
