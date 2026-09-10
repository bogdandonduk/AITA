package kz.aita.server.auth
import kotlin.test.*

class AitaAuthenticatorAttemptPolicyTest {
    private val now = 10_000_000L
    @Test fun belowBothLimitsIsAdmitted() { assertEquals(0L, aitaAuthenticatorRetryAfterSeconds(List(7) { now }, List(59) { now }, now)) }
    @Test fun minuteAccountLimitHasExactDelay() { assertEquals(40L, aitaAuthenticatorRetryAfterSeconds(List(8) { now - 20_000 }, emptyList(), now)) }
    @Test fun hourlyLimitSurvivesManyMinuteWindows() { assertEquals(3480L, aitaAuthenticatorRetryAfterSeconds(List(40) { now - 120_000 }, emptyList(), now)) }
    @Test fun ipLimitDoesNotDependOnTheIdentifier() { assertEquals(60L, aitaAuthenticatorRetryAfterSeconds(emptyList(), List(60) { now }, now)) }
    @Test fun boundaryTimestampsExpireWithoutOneExtraMinute() { assertEquals(0L, aitaAuthenticatorRetryAfterSeconds(List(8) { now - 60_000 }, List(60) { now - 60_000 }, now)) }
    @Test fun roundsPartialSecondsUpAndIgnoresInputOrdering() { assertEquals(1L, aitaAuthenticatorRetryAfterSeconds(List(8) { now - 59_001 }.reversed(), emptyList(), now)) }
    @Test fun longestOutstandingBudgetWins() { assertEquals(3540L, aitaAuthenticatorRetryAfterSeconds(List(40) { now - 60_000 }, List(60) { now }, now)) }
    @Test fun backwardClockDoesNotReopenTheBudget() { assertTrue(aitaAuthenticatorRetryAfterSeconds(List(8) { now + 1_000 }, emptyList(), now) >= 60L) }
}
