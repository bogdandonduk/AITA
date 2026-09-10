package kz.aita.auth

import kotlin.test.*

class AuthenticationUiOwnershipTest {
    @Test fun sameAnonymousAttemptIsCurrent() = assertTrue(aitaAuthenticationUiOwnerIsCurrent(2, null, 2, null))
    @Test fun anonymousRecoveryCannotExpireAnotherAccount() = assertFalse(aitaRecoveryResultBelongsToOwner(2, null, 2, null, "A"))
    @Test fun matchingAccountCanExpireOnlyItsOwnSession() = assertTrue(aitaRecoveryResultBelongsToOwner(2, "A", 2, "A", "A"))
    @Test fun anotherRecoveredAccountDoesNotExpireOpenAccount() = assertFalse(aitaRecoveryResultBelongsToOwner(2, "A", 2, "A", "B"))
    @Test fun accountSwitchInvalidatesResponse() = assertFalse(aitaAuthenticationUiOwnerIsCurrent(2, "A", 3, "B"))
    @Test fun returnToSameAccountDoesNotReviveOldResponse() = assertFalse(aitaAuthenticationUiOwnerIsCurrent(2, "A", 4, "A"))
    @Test fun reloginOnSameAccountInvalidatesResponse() = assertFalse(aitaRecoveryResultBelongsToOwner(2, "A", 3, "A", "A"))
    @Test fun logoutInvalidatesResponse() = assertFalse(aitaAuthenticationUiOwnerIsCurrent(2, "A", 3, null))
}
