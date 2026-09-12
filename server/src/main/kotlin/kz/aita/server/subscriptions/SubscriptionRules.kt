package kz.aita.server.subscriptions

import kz.aita.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal class SubscriptionFailure(val key: String, val httpStatus: Int = 409) : RuntimeException(key)
internal fun subscriptionFailure(key: String, status: Int = 409): Nothing = throw SubscriptionFailure(key, status)

internal fun subscriptionSha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal fun subscriptionCodeHash(raw: String): String? {
    if (raw.isBlank()) return null
    val normalized = normalizeSubscriptionPromoCode(raw) ?: subscriptionFailure("subscription.promo_invalid", 400)
    return subscriptionSha256(normalized)
}

internal fun subscriptionCommandHash(request: StoreSubscriptionUpdateRequestDataModel): String = subscriptionSha256(
    listOf(request.storeId, request.planId, request.autoRenew, request.activateNow,
        subscriptionCodeHash(request.promoCode).orEmpty(), request.expectedRevision, request.expectedChargeMinor,
        request.expectedCurrencyCode, request.expectedPriceVersion, request.quoteValidUntilMillis,
        request.expectedRegularPriceMinor, request.expectedAccessKind, request.expectedDurationMillis)
        // Length-prefix each field and distinguish null from the literal string "null".
        // Untrusted strings cannot shift field boundaries in an idempotency fingerprint.
        .joinToString("") { value -> value?.toString()?.let { "${it.length}:$it" } ?: "-1:" }
)

internal fun subscriptionCommandUuid(raw: String): UUID = runCatching { UUID.fromString(raw) }
    .getOrNull()?.takeIf { it.toString().equals(raw, ignoreCase = true) }
    ?: subscriptionFailure("subscription.command_invalid", 400)

internal fun subscriptionPeriodEnd(start: Long, unit: String, count: Int): Long {
    require(count in 1..120 && unit in setOf(SUBSCRIPTION_PERIOD_MONTH, SUBSCRIPTION_PERIOD_YEAR))
    val instant = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC)
    return (if (unit == SUBSCRIPTION_PERIOD_YEAR) instant.plusYears(count.toLong()) else instant.plusMonths(count.toLong()))
        .toInstant().toEpochMilli()
}
