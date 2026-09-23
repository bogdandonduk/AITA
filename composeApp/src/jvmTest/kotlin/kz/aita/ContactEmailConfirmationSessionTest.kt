package kz.aita

import kz.aita.auth.*
import kotlin.test.*

class ContactEmailConfirmationSessionTest {
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
}
