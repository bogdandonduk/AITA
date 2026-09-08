package kz.aita.server.auth

import java.net.InetAddress
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal class AitaAuthRateLimitedException(val retryAfterSeconds: Long) :
    IllegalStateException("AUTH_CODE_RATE_LIMITED")

/** Trust the Worker-over-loopback path only, never forwarding headers from a direct remote client. */
internal fun aitaAuthClientIp(peer: String, forwardedFor: String?, edge: String?): String {
    val cleanPeer = peer.removePrefix("/").substringBefore('%').lowercase()
    val loopback = cleanPeer == "::1" || cleanPeer == "0:0:0:0:0:0:0:1" ||
        cleanPeer == "127.0.0.1" || cleanPeer == "::ffff:127.0.0.1"
    if (!loopback || edge != "cloudflare-workers-vpc") return peer
    // The AITA Worker replaces X-Forwarded-For with exactly one Cloudflare client IP.
    val candidate = forwardedFor?.trim()?.takeIf { it.length in 2..64 && ',' !in it } ?: return peer
    val ipv4 = candidate.split('.').let { parts ->
        parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 }
    }
    val ipv6 = ':' in candidate && candidate.all { it in "0123456789abcdefABCDEF:." }
    if (!ipv4 && !ipv6) return peer
    return runCatching { InetAddress.getByName(candidate).hostAddress }.getOrNull() ?: peer
}

internal fun authCodeCanBeVerified(
    now: Long, expiresAt: Long, consumedAt: Long?, verifiedAt: Long?, attempts: Int, maxAttempts: Int
): Boolean = consumedAt == null && verifiedAt == null && expiresAt > now && attempts < maxAttempts

internal fun authEmailCanBeDelivered(
    now: Long, expiresAt: Long, consumedAt: Long?, verifiedAt: Long?, userActive: Boolean
): Boolean = userActive && consumedAt == null && verifiedAt == null && expiresAt > now

internal fun resendRetryAfterMillis(value: String?, now: Long): Long? {
    val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    raw.toLongOrNull()?.let { return it.coerceIn(0L, 86_400L) * 1_000L }
    return runCatching {
        (ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now)
            .coerceIn(0L, 86_400_000L)
    }.getOrNull()
}

internal data class AuthEmailDeliveryResult(
    val success: Boolean,
    val messageId: String? = null,
    val errorCode: String? = null,
    val retry: Boolean = false,
    val retryAfterMillis: Long? = null,
    val serviceWideFailure: Boolean = false
)

/** The provider's raw message may contain addresses; use only allowlisted diagnostic codes. */
internal fun classifyResendDelivery(
    status: Int,
    messageId: String?,
    errorName: String?,
    retryAfter: String? = null,
    now: Long = System.currentTimeMillis()
): AuthEmailDeliveryResult {
    if (status in 200..299) {
        val id = messageId?.takeIf { it.isNotBlank() && it.length <= 200 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
        return if (id != null) AuthEmailDeliveryResult(true, messageId = id)
        else AuthEmailDeliveryResult(false, errorCode = "RESEND_INVALID_RESPONSE", retry = true, serviceWideFailure = true)
    }
    val known = errorName?.takeIf { it in setOf(
        "invalid_api_key", "missing_api_key", "invalid_from_address", "validation_error",
        "daily_quota_exceeded", "monthly_quota_exceeded", "rate_limit_exceeded",
        "invalid_idempotent_request", "concurrent_idempotent_requests", "security_error"
    ) }
    val retry = when {
        status == 409 -> known == "concurrent_idempotent_requests"
        known == "daily_quota_exceeded" || known == "monthly_quota_exceeded" -> false // code will expire first
        else -> status in setOf(408, 425, 429) || status >= 500
    }
    return AuthEmailDeliveryResult(
        success = false,
        errorCode = ("RESEND_HTTP_$status" + (known?.let { ":$it" } ?: "")).take(80),
        retry = retry,
        retryAfterMillis = resendRetryAfterMillis(retryAfter, now),
        serviceWideFailure = status in setOf(401, 403, 408, 425, 429, 451) || status >= 500 || known == "invalid_from_address"
    )
}
