package kz.aita

import kotlin.test.*

class StoreSubscriptionGateTest {
    private val proof=VerifiedStoreSubscription("account",StoreSubscriptionStateDataModel(storeId="branch",
        planId=SUBSCRIPTION_BASIC_PLAN,status=SUBSCRIPTION_STATUS_ACTIVE,currentPeriodStartMillis=100_000L,
        currentPeriodEndMillis=120_000L),50_000L,100_000L)
    private fun gate(p:VerifiedStoreSubscription?=proof,now:Long=50_000L,denied:Boolean=false,
        account:String?="account",store:String?="branch")=resolveStoreSubscriptionGate(account,store,p,denied,now)
    @Test fun cachedPaidProofClosesSynchronouslyAtExactExpiry() {
        assertEquals(StoreSubscriptionGate.Active,gate(now=69_999))
        assertEquals(StoreSubscriptionGate.Required,gate(now=70_000))
        // No refresh callback/network result is required to finish this transition.
        assertEquals(StoreSubscriptionGate.Required,gate(now=90_000))
    }
    @Test fun startupUnknownDoesNotPretendAnotherPaymentIsDue() { assertEquals(StoreSubscriptionGate.Checking,gate(null)) }
    @Test fun cachedInactiveProofOpensRecoveryEvenInRestoredSession() {
        assertEquals(StoreSubscriptionGate.Required,gate(proof.copy(subscription=proof.subscription.copy(status="inactive"))))
    }
    @Test fun authoritativeDenialWinsOverPositiveOfflineLifetimeProof() {
        assertEquals(StoreSubscriptionGate.Required,gate(life(),denied=true))
    }
    @Test fun differentAccountNeverInheritsCachedProof() { assertEquals(StoreSubscriptionGate.Checking,gate(account="other")) }
    @Test fun differentBranchNeverInheritsParentProof() { assertEquals(StoreSubscriptionGate.Checking,gate(store="parent")) }
    @Test fun logoutAndMissingLocationHaveDistinctStates() {
        assertEquals(StoreSubscriptionGate.Checking,gate(account=null));assertEquals(StoreSubscriptionGate.Required,gate(store=null))
    }
    @Test fun clockRollbackRequestsVerificationNotRenewal() { assertEquals(StoreSubscriptionGate.Checking,gate(now=44_999)) }
    @Test fun corruptClockRequiresVerification() { assertEquals(StoreSubscriptionGate.Checking,gate(proof.copy(verifiedAtServerMillis=0))) }
    private fun life()=proof.copy(subscription=proof.subscription.copy(accessKind=SUBSCRIPTION_ACCESS_LIFETIME,
        planId=SUBSCRIPTION_LIFETIME_PLAN,currentPeriodEndMillis=null,autoRenew=false))
    @Test fun lifetimeHasNoFarFutureExpiryOrClockLease() {
        listOf(0L,50_000L,Long.MAX_VALUE).forEach{assertEquals(StoreSubscriptionGate.Active,gate(life(),now=it))}
    }
    @Test fun malformedLifetimeShapeCannotGrant() {
        assertEquals(StoreSubscriptionGate.Required,gate(life().copy(subscription=life().subscription.copy(autoRenew=true))))
        assertEquals(StoreSubscriptionGate.Required,gate(life().copy(subscription=life().subscription.copy(planId="basic"))))
    }
    @Test fun lifetimeBeforeActualStartStillCannotGrant() {
        val future=life().copy(subscription=life().subscription.copy(currentPeriodStartMillis=100_001L))
        assertEquals(StoreSubscriptionGate.Required,gate(future))
    }
    @Test fun missingOrForeignProofCannotBecomeActiveByLongWait() {
        assertEquals(StoreSubscriptionGate.Checking,gate(null,now=Long.MAX_VALUE))
        assertEquals(StoreSubscriptionGate.Checking,gate(now=Long.MAX_VALUE,store="another"))
    }
}
