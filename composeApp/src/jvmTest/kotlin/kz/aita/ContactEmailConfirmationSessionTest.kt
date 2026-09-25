package kz.aita

import kz.aita.auth.*
import kotlin.test.*
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.seconds

class ContactEmailConfirmationSessionTest {
    @Test fun returningToCodeEntryDoesNotRestartItsExpiryOrResendCountdown() {
        val time = TestTimeSource()
        val clocks = AuthFlowClockMemory(time)
        val flow = AitaAuthFlowDataModel(flowId = "countdown", serverTimeMillis = 1_000L, resendAfterMillis = 61_000L, expiresAtMillis = 601_000L)
        val first = clocks.anchor(flow, null, 1, 9_000L)
        time += 90.seconds
        val returned = clocks.anchor(flow.copy(), null, 1, 99_000L)
        assertSame(first, returned)
        assertEquals(90_000L, returned.received.elapsedNow().inWholeMilliseconds)
        assertEquals(510L, aitaAuthCountdownSeconds(aitaAuthExpiryDelayMillis(flow.expiresAtMillis, flow.serverTimeMillis, returned.localTimeMillis) - returned.received.elapsedNow().inWholeMilliseconds))
        assertNotSame(first, clocks.anchor(flow.copy(resendAfterMillis = 121_000L), null, 1, 99_000L))
        assertNotSame(first, clocks.anchor(flow, null, 2, 99_000L))
    }

    @Test fun aNewGuestCanRequestRegistrationConfirmationWithoutAnExistingLogin() {
        val state = ContactEmailConfirmationState(
            aitaContactDraftTarget(AitaContactPurpose.REGISTRATION, "", "02000000-0000-4000-8000-000000000001"),
            "02000000-0000-4000-8000-000000000001", listOf("guest@example.test"), null, currentAuthenticatedSessionGeneration())
        assertTrue(state.ownsCurrentSession())
        assertFalse(state.ready)
        state.disposed = true
        assertFalse(state.ownsCurrentSession())
    }
    @Test fun aGuestCannotUseAccountContactConfirmationOrAnOldSessionDraft() {
        val generation = currentAuthenticatedSessionGeneration()
        val target = aitaContactDraftTarget(AitaContactPurpose.ACCOUNT_CONTACT, "", "02000000-0000-4000-8000-000000000001")
        assertFalse(ContactEmailConfirmationState(target, "draft", listOf("guest@example.test"), null, generation).ownsCurrentSession())
        assertFalse(ContactEmailConfirmationState(target.copy(purpose = AitaContactPurpose.REGISTRATION), "draft",
            listOf("guest@example.test"), null, generation - 1).ownsCurrentSession())
    }
    @Test fun returningToTheSameFormRetainsItsChallengeButOtherAccountsAndContactsDoNot() {
        val generation = currentAuthenticatedSessionGeneration()
        val first = ContactConfirmationMemory.obtain(null, generation, AitaContactPurpose.REGISTRATION, "", "", listOf("guest@example.test"))
        first.flowAddress = "guest@example.test"
        first.flow = AitaAuthFlowDataModel(flowId = "test-challenge", nextStep = AitaAuthNextStep.EMAIL_CODE)
        val returned = ContactConfirmationMemory.obtain(null, generation, AitaContactPurpose.REGISTRATION, "", "", listOf("guest@example.test"))
        assertSame(first, returned)
        assertEquals("test-challenge", returned.flow?.flowId)
        assertNotSame(first, ContactConfirmationMemory.obtain(null, generation, AitaContactPurpose.REGISTRATION, "", "", listOf("different@example.test")))
        val nextSession = ContactConfirmationMemory.obtain(null, generation + 1, AitaContactPurpose.REGISTRATION, "", "", listOf("guest@example.test"))
        assertNotSame(first, nextSession)
        assertTrue(first.disposed)
        assertNull(first.flow)
        ContactConfirmationMemory.forget(nextSession)
        assertTrue(nextSession.disposed)
    }
}
