package kz.aita

import kz.aita.auth.AitaLoginSecondFactor
import kotlin.test.*

class TwoFactorMethodTest {
    @Test fun noneOnlyNeedsNoEditorWhenAlreadyNone() {
        for(current in AitaLoginSecondFactor.entries)for(enrolled in listOf(false,true))
            assertEquals(if(current==AitaLoginSecondFactor.NONE)FactorSelectionAction.NONE else FactorSelectionAction.CONFIRM_DISABLE,
                factorSelectionAction(AitaLoginSecondFactor.NONE,current,enrolled))
    }
    @Test fun authenticatorNeedsEnrollmentBeforeItCanBecomeRequired() {
        for(current in AitaLoginSecondFactor.entries) {
            assertEquals(FactorSelectionAction.ENROLL,factorSelectionAction(AitaLoginSecondFactor.AUTHENTICATOR,current,false))
            assertEquals(FactorSelectionAction.POLICY,factorSelectionAction(AitaLoginSecondFactor.AUTHENTICATOR,current,true))
        }
    }
    @Test fun emailAlwaysUsesTheProtectedPolicyEditor() {
        for(current in AitaLoginSecondFactor.entries)for(enrolled in listOf(false,true))
            assertEquals(FactorSelectionAction.POLICY,factorSelectionAction(AitaLoginSecondFactor.EMAIL,current,enrolled))
    }
}
