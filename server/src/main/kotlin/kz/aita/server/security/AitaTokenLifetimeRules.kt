package kz.aita.server.security

import java.time.Instant
import java.util.*

internal object AitaTokenLifetimeRules {
    const val ACCESS_TOKEN_TTL_SECONDS: Long = 10L * 60L
    const val CLOCK_SKEW_SECONDS: Long = 60L
    const val LEGACY_TOKEN_MIGRATION_SECONDS: Long = 15L * 60L

    fun accessExpiresAt(issuedAt: Instant): Instant =
        issuedAt.plusSeconds(ACCESS_TOKEN_TTL_SECONDS)

    fun payloadIsAcceptable(
        expiresAt: Date?,
        issuedAt: Date?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val skewMillis = CLOCK_SKEW_SECONDS * 1_000L
        if (expiresAt != null) {
            return expiresAt.time >= nowMillis - skewMillis
        }

        val issuedMillis = issuedAt?.time ?: return false
        if (issuedMillis > nowMillis + skewMillis) return false
        val legacyDeadline = safeAdd(issuedMillis, LEGACY_TOKEN_MIGRATION_SECONDS * 1_000L)
        return nowMillis <= safeAdd(legacyDeadline, skewMillis)
    }

    private fun safeAdd(base: Long, delta: Long): Long =
        if (delta > 0L && base > Long.MAX_VALUE - delta) Long.MAX_VALUE else base + delta
}
