package kz.aita.server.auth

import kotlin.test.*

class AitaAuthUnavailableMessageTest {
    @Test
    fun everyUnavailableReasonHasAllSupportedLanguages() {
        AitaAuthUnavailableReason.entries.forEach { reason ->
            val messages = authUnavailableMessage(reason)
            assertEquals(listOf("main", "en", "ru", "kk", "ky"), messages.map { it.language })
            assertTrue(messages.all { it.value.isNotBlank() })
        }
    }

    @Test
    fun defaultLanguageMatchesEnglishForEveryReason() {
        AitaAuthUnavailableReason.entries.forEach { reason ->
            val messages = authUnavailableMessage(reason).associate { it.language to it.value }
            assertEquals(messages["en"], messages["main"])
        }
    }

    @Test
    fun securityMisconfigurationIsNotPresentedAsAnInvalidPassword() {
        val messages = authUnavailableMessage(AitaAuthUnavailableReason.SECURITY_CONFIGURATION)
        assertEquals(
            "Account security needs server setup. Contact the administrator.",
            messages.single { it.language == "en" }.value
        )
        assertFalse(messages.any { it.value.contains("invalid password", ignoreCase = true) })
    }

    @Test
    fun exceptionRetainsReasonWithoutExposingConfigurationValues() {
        AitaAuthUnavailableReason.entries.forEach { reason ->
            val exception = AitaAuthUnavailableException(reason)
            assertEquals(reason, exception.reason)
            assertEquals(reason.name, exception.message)
            assertNull(exception.cause)
        }
    }
}
