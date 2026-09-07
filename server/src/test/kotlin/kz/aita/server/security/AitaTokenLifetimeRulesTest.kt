package kz.aita.server.security

import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AitaTokenLifetimeRulesTest {
    @Test
    fun finiteAccessTokenExpiresAfterTenMinutes() {
        val issued = Instant.ofEpochMilli(1_000_000L)
        val expires = AitaTokenLifetimeRules.accessExpiresAt(issued)
        assertTrue(expires.toEpochMilli() - issued.toEpochMilli() == 600_000L)
    }

    @Test
    fun legacyTokenRequiresRecentIssuedAt() {
        val now = 2_000_000L
        assertTrue(
            AitaTokenLifetimeRules.payloadIsAcceptable(
                expiresAt = null,
                issuedAt = Date(now - 5 * 60_000L),
                nowMillis = now
            )
        )
        assertFalse(
            AitaTokenLifetimeRules.payloadIsAcceptable(
                expiresAt = null,
                issuedAt = Date(now - 30 * 60_000L),
                nowMillis = now
            )
        )
        assertFalse(AitaTokenLifetimeRules.payloadIsAcceptable(null, null, now))
    }
}
