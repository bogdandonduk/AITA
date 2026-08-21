package kz.aita.server.payments

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PaymentRetryPolicyTest {
    private val policy = PaymentRetryPolicy(
        maximumAttempts = 3,
        baseDelay = 100.milliseconds,
        maximumDelay = 1.seconds,
        random = Random(1),
    )

    @Test
    fun retriesTransportRateLimitAndServerFailures() {
        assertTrue(policy.decide(1, null, transportFailure = true).retry)
        assertTrue(policy.decide(1, 429, transportFailure = false).retry)
        assertTrue(policy.decide(1, 503, transportFailure = false).retry)
    }

    @Test
    fun doesNotRetryOrdinaryValidationOrAfterAttemptLimit() {
        assertFalse(policy.decide(1, 400, transportFailure = false).retry)
        assertFalse(policy.decide(3, 503, transportFailure = false).retry)
    }
}
