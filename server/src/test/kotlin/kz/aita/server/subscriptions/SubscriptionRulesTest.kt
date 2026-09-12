package kz.aita.server.subscriptions

import kz.aita.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class SubscriptionRulesTest {
    private fun request() = StoreSubscriptionUpdateRequestDataModel("store", "basic", promoCode = "AITA-HELLO",
        commandId = UUID.randomUUID().toString(), expectedRevision = 4, expectedChargeMinor = 599250,
        expectedCurrencyCode = "KZT", expectedPriceVersion = 1, quoteValidUntilMillis = 1000,
        expectedRegularPriceMinor = 799000, expectedAccessKind = "discount")
    @Test fun codeHashIsStableNormalizedAndDoesNotContainTheSecret() {
        assertEquals(subscriptionCodeHash("aita-hello"), subscriptionCodeHash(" AITA-HELLO "))
        assertEquals(64, requireNotNull(subscriptionCodeHash("AITA-HELLO")).length)
        assertFalse(requireNotNull(subscriptionCodeHash("AITA-HELLO")).contains("HELLO"))
        assertNull(subscriptionCodeHash(" "))
    }
    @Test fun codeHashMatchesTheStandardSha256Vector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", subscriptionSha256("abc"))
    }
    @Test fun malformedCodeAndUuidAreDomainFailures() {
        assertEquals(400, assertFailsWith<SubscriptionFailure> { subscriptionCodeHash("wrong code") }.httpStatus)
        listOf("", "1-1-1-1-1", "not-a-uuid").forEach { raw -> assertFailsWith<SubscriptionFailure> { subscriptionCommandUuid(raw) } }
        val id = UUID.randomUUID(); assertEquals(id, subscriptionCommandUuid(id.toString().uppercase()))
    }
    @Test fun equivalentCodesHaveTheSameCommandFingerprint() {
        val first = request(); assertEquals(subscriptionCommandHash(first), subscriptionCommandHash(first.copy(promoCode = " aita-hello ")))
    }
    @Test fun commandIdentityIsSeparateFromImmutableCommandContent() {
        val first = request(); assertEquals(subscriptionCommandHash(first), subscriptionCommandHash(first.copy(commandId = UUID.randomUUID().toString())))
    }
    @Test fun eachCommercialConsentFieldParticipatesInReplayIdentity() {
        val first = request(); val hash = subscriptionCommandHash(first)
        listOf(first.copy(storeId = "other"), first.copy(planId = "internal_lifetime"), first.copy(autoRenew = !first.autoRenew),
            first.copy(activateNow = false), first.copy(expectedRevision = 5), first.copy(expectedChargeMinor = 0),
            first.copy(expectedCurrencyCode = "TJS"), first.copy(expectedPriceVersion = 2), first.copy(quoteValidUntilMillis = 2000),
            first.copy(expectedRegularPriceMinor = 0), first.copy(expectedAccessKind = "lifetime"), first.copy(expectedDurationMillis = 50),
            first.copy(promoCode = "AITA-OTHER")).forEach { assertNotEquals(hash, subscriptionCommandHash(it)) }
    }
    @Test fun nullAndLiteralNullCannotShareACommandFingerprint() {
        val base = StoreSubscriptionUpdateRequestDataModel("store", "basic", expectedCurrencyCode = null)
        assertNotEquals(subscriptionCommandHash(base), subscriptionCommandHash(base.copy(expectedCurrencyCode = "null")))
    }
    @Test fun embeddedNewlinesCannotShiftCommandFieldBoundaries() {
        val base = StoreSubscriptionUpdateRequestDataModel("store", "basic", expectedCurrencyCode = "KZT")
        assertNotEquals(subscriptionCommandHash(base), subscriptionCommandHash(base.copy(planId = "basic\nfalse")))
    }
    @Test fun calendarMonthRenewalHandlesMonthEndsAndLeapYears() {
        val january = Instant.parse("2024-01-31T10:15:30Z").toEpochMilli()
        assertEquals(Instant.parse("2024-02-29T10:15:30Z").toEpochMilli(), subscriptionPeriodEnd(january, "month", 1))
    }
    @Test fun yearlyRenewalDoesNotUseFixedThirtyDayArithmetic() {
        val start = Instant.parse("2024-02-29T10:15:30Z").toEpochMilli()
        assertEquals(Instant.parse("2025-02-28T10:15:30Z").toEpochMilli(), subscriptionPeriodEnd(start, "year", 1))
    }
    @Test fun invalidPeriodsFailBeforeAnyCharge() {
        assertFailsWith<IllegalArgumentException> { subscriptionPeriodEnd(1000, "month", 0) }
        assertFailsWith<IllegalArgumentException> { subscriptionPeriodEnd(1000, "minute", 1) }
    }
    @Test fun promoGuessingAndWritesHaveSeparatePerAccountBudgets() {
        val user = UUID.randomUUID()
        repeat(20) { assertTrue(SubscriptionAttemptLimiter.allow(user, false, 1000)) }
        assertFalse(SubscriptionAttemptLimiter.allow(user, false, 1000))
        repeat(10) { assertTrue(SubscriptionAttemptLimiter.allow(user, true, 1000)) }
        assertFalse(SubscriptionAttemptLimiter.allow(user, true, 1000))
        assertTrue(SubscriptionAttemptLimiter.allow(user, true, 61000))
        assertTrue(SubscriptionAttemptLimiter.allow(UUID.randomUUID(), false, 1000))
    }
}
