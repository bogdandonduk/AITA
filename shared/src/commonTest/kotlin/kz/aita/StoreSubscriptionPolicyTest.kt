package kz.aita

import kotlin.test.*

class StoreSubscriptionPolicyTest {
    private fun paid() = StoreSubscriptionStateDataModel(storeId = "branch-a", status = SUBSCRIPTION_STATUS_ACTIVE,
        planId = SUBSCRIPTION_BASIC_PLAN, currentPeriodStartMillis = 100L, currentPeriodEndMillis = 1_000L)
    private fun life() = paid().copy(planId = SUBSCRIPTION_LIFETIME_PLAN, accessKind = SUBSCRIPTION_ACCESS_LIFETIME,
        currentPeriodEndMillis = null, autoRenew = false)
    @Test fun basicUsesIntegerTiynAndExactRegionalIdentity() {
        val plan = basicStoreSubscriptionPlan()
        assertEquals(799_000L, plan.priceMinor); assertEquals("KZT", plan.currencyCode)
        assertEquals("KZ", plan.regionCode); assertEquals("basic", plan.id)
        assertFalse(plan.hidden); assertEquals(setOf("en", "ru", "kk", "ky"), plan.name.map { it.language }.toSet())
    }
    @Test fun regionOverridesAreExplicitNotCurrencyConversion() {
        val plan = basicStoreSubscriptionPlan(12_345L, "TJS", "TJ", 8L)
        assertEquals(12_345L, plan.priceMinor); assertEquals("TJ", plan.regionCode)
        assertEquals("TJS", plan.currencyCode); assertEquals(8L, plan.priceVersion)
    }
    @Test fun lifetimeCatalogueEntryIsHiddenAndFree() {
        val plan = lifetimeStoreSubscriptionPlan("KZ", "KZT")
        assertTrue(plan.hidden); assertEquals(0L, plan.priceMinor); assertEquals(SUBSCRIPTION_LIFETIME_PLAN, plan.id)
    }
    @Test fun paidAccessBelongsOnlyToTheExactPhysicalStore() {
        assertTrue(paid().grantsStoreAccess("branch-a", 999))
        assertFalse(paid().grantsStoreAccess("parent", 999)); assertFalse(paid().grantsStoreAccess("branch-b", 999))
    }
    @Test fun expiryIsExclusiveAndStartIsInclusive() {
        assertFalse(paid().grantsStoreAccess("branch-a", 99)); assertTrue(paid().grantsStoreAccess("branch-a", 100))
        assertFalse(paid().grantsStoreAccess("branch-a", 1_000))
    }
    @Test fun missingStoreOrPeriodNeverGrants() {
        assertFalse(paid().grantsStoreAccess(null, 500)); assertFalse(paid().grantsStoreAccess("", 500))
        assertFalse(paid().copy(currentPeriodEndMillis = null).grantsStoreAccess("branch-a", 500))
        assertFalse(paid().copy(currentPeriodStartMillis = null).grantsStoreAccess("branch-a", 500))
    }
    @Test fun inactiveCancelledAndPastDueNeverGrant() {
        listOf("inactive", "cancelled", "past_due", "future_unknown").forEach {
            assertFalse(paid().copy(status = it).grantsStoreAccess("branch-a", 500))
        }
    }
    @Test fun unknownAccessKindsFailClosed() {
        assertFalse(paid().copy(accessKind = "trial_untrusted").grantsStoreAccess("branch-a", 500))
    }
    @Test fun timedGrantExpiresLikePaidButDoesNotBecomeLifetime() {
        val timed = paid().copy(accessKind = SUBSCRIPTION_ACCESS_TIMED, autoRenew = false)
        assertTrue(timed.grantsStoreAccess("branch-a", 999)); assertFalse(timed.grantsStoreAccess("branch-a", 1_000))
    }
    @Test fun lifetimeRequiresItsSpecialPlanNoExpiryAndNoRenewal() {
        assertTrue(life().grantsStoreAccess("branch-a", Long.MAX_VALUE))
        assertFalse(life().copy(autoRenew = true).grantsStoreAccess("branch-a", 500))
        assertFalse(life().copy(planId = "basic").grantsStoreAccess("branch-a", 500))
        assertFalse(life().copy(currentPeriodEndMillis = 1_000).grantsStoreAccess("branch-a", 500))
    }
    @Test fun codeNormalizationAcceptsAsciiCaseOnly() {
        assertEquals("AITA-FREE_7", normalizeSubscriptionPromoCode("  aita-free_7  "))
        assertNull(normalizeSubscriptionPromoCode("ABC12")); assertNull(normalizeSubscriptionPromoCode("A".repeat(97)))
    }
    @Test fun codeNormalizationRejectsLookalikesExpansionAndEmbeddedWhitespace() {
        listOf("АITA-1234", "STRAßE1", "AITA 123", "AITA\n123", "AITA-😀").forEach { assertNull(normalizeSubscriptionPromoCode(it)) }
    }
    @Test fun percentageDiscountsUseIntegerMinorUnits() {
        assertEquals(599_250L, subscriptionDiscountedPrice(799_000, 2500, null))
        assertEquals(76L, subscriptionDiscountedPrice(101, 2500, null))
    }
    @Test fun hundredPercentAndFixedDiscountsCannotCreateNegativeCharges() {
        assertEquals(0L, subscriptionDiscountedPrice(799_000, 10000, null))
        assertEquals(0L, subscriptionDiscountedPrice(100, null, 101))
        assertEquals(75L, subscriptionDiscountedPrice(100, null, 25))
    }
    @Test fun discountBoundsAvoidOverflow() {
        assertEquals(0L, subscriptionDiscountedPrice(1_000_000_000_000, 10000, null))
        assertFailsWith<IllegalArgumentException> { subscriptionDiscountedPrice(Long.MAX_VALUE, 1, null) }
        assertFailsWith<IllegalArgumentException> { subscriptionDiscountedPrice(-1, 1, null) }
    }
    @Test fun mutuallyExclusiveDiscountTypesAreRequired() {
        assertFailsWith<IllegalArgumentException> { subscriptionDiscountedPrice(100, null, null) }
        assertFailsWith<IllegalArgumentException> { subscriptionDiscountedPrice(100, 1, 1) }
        assertFailsWith<IllegalArgumentException> { subscriptionDiscountedPrice(100, 0, null) }
        assertFailsWith<IllegalArgumentException> { subscriptionDiscountedPrice(100, null, 0) }
    }
    @Test fun businessEndpointsRequireEntitlementIndependentOfOwners() {
        listOf("stock/get", "/stockBatches/move", "transactions/complete", "cashRegister/extract", "debtors/get",
            "analytics/get", "logs/get", "operationLogs/get", "workers/start", "workshifts/start", "stores/update", "stores/delete",
            "payments/integrations/provider").forEach { assertTrue(storeSubscriptionRequiredForEndpoint(it), it) }
    }
    @Test fun recoveryAndBillingRemainAvailableWithoutPaidAccess() {
        listOf("auth/logIn", "subscriptions/store/update", "subscriptions/store/quote", "subscriptions/store/command",
            "stores/get", "stores/active", "stores/add", "user/get", "finance/topup/create", "workshifts/end", "workers/my/get",
            "workers/removal/confirm", "workers/invitations/decline", "payments/balance", "payments/topups").forEach {
            assertFalse(storeSubscriptionRequiredForEndpoint(it), it)
        }
    }
    @Test fun supplierEndpointsUseAuthoritativeDualPartyAuthorization() {
        listOf("supplierOrders/status", "supplierContracts/upsert", "supplierGoodsPrices/upsert", "suppliers/get").forEach {
            assertFalse(storeSubscriptionRequiredForEndpoint(it), it)
        }
    }
    @Test fun queryAndCaseDoNotBypassRouteClassification() {
        assertTrue(storeSubscriptionRequiredForEndpoint("/STOCK/get?storeId=other#fragment"))
        assertFalse(storeSubscriptionRequiredForEndpoint("/workshifts/end?recovery=1"))
    }
}
