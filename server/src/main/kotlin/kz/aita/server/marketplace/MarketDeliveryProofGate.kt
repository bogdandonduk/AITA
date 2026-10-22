package kz.aita.server.marketplace

import kz.aita.MarketDeliveryAction
import kz.aita.MarketDeliveryCommand
import kz.aita.server.auth.AuthRateLimitDecision
import kz.aita.server.auth.AuthSlidingWindowLimiter
import java.util.UUID

/** Process-local protection against guessing a buyer's handover proof. Admission is outside the
 * SQL transaction, so rejected proofs and transaction rollbacks still consume an attempt.
 * Bounded memory, atomic admission, and no codes or hashes retained or logged. */
internal class MarketDeliveryProofGate(maximumTrackedKeys: Int = 8192) {
    private val limiter = AuthSlidingWindowLimiter(maximumTrackedKeys)

    fun check(actor: UUID, command: MarketDeliveryCommand, monotonicMillis: Long): AuthRateLimitDecision {
        // Buyers confirm their own receipt without a proof. Malformed/empty proofs cannot guess
        // the six-digit secret and remain subject to the ordinary write limit and server checks.
        if (command.action != MarketDeliveryAction.DELIVER || command.handoverCode.isNullOrBlank())
            return AuthRateLimitDecision(true)
        val order = marketUuid(command.orderId ?: marketFail("delivery.invalid", 400))
        marketUuid(command.commandId)
        // Retries count too: a failed command has no committed idempotency record, and reusing its
        // ID with a different code must not provide unlimited guesses.
        return limiter.check("$actor:$order", max = 6, now = monotonicMillis, windowMillis = 300_000L)
    }
}
