package kz.aita.server.payments

import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class PaymentRetryDecision(
    val retry: Boolean,
    val delay: Duration = Duration.ZERO,
    val terminalCode: String? = null,
)

class PaymentRetryPolicy(
    private val maximumAttempts: Int = 8,
    private val baseDelay: Duration = 1.seconds,
    private val maximumDelay: Duration = 5.seconds * 60,
    private val random: Random = Random.Default,
) {
    init {
        require(maximumAttempts >= 1)
        require(baseDelay > Duration.ZERO)
        require(maximumDelay >= baseDelay)
    }

    fun decide(attempt: Int, httpStatus: Int?, transportFailure: Boolean): PaymentRetryDecision {
        if (attempt >= maximumAttempts) return PaymentRetryDecision(false, terminalCode = "attempts_exhausted")
        val retryable = transportFailure || httpStatus == null || httpStatus in setOf(408, 409, 425, 429) || httpStatus >= 500
        if (!retryable) return PaymentRetryDecision(false, terminalCode = "provider_request_rejected")
        val exponent = (attempt - 1).coerceAtLeast(0).coerceAtMost(20)
        val rawMs = baseDelay.inWholeMilliseconds * (1L shl exponent)
        val cappedMs = min(rawMs, maximumDelay.inWholeMilliseconds)
        val jitter = if (cappedMs <= 1L) 0L else random.nextLong(0L, (cappedMs / 4L).coerceAtLeast(1L))
        return PaymentRetryDecision(true, (cappedMs + jitter).milliseconds)
    }
}
