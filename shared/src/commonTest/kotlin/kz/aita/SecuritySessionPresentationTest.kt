package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecuritySessionPresentationTest {
    @Test fun rawSecretsAndInternalIdsNeverReachPresentation() {
        val source = mapOf("sessionId" to "session-secret", "refreshToken" to "token-secret",
            "rotationReason" to "diagnostic", "deviceInstallationId" to "installation-secret",
            "accessToken" to "jwt-secret", "userId" to "user-secret", "localeLanguage" to "ru")
        assertEquals(mapOf("localeLanguage" to "ru"), securitySessionDisplayMetadata(source))
        assertEquals("token-secret", source["refreshToken"]) // Stored audit source is never mutated.
    }
    @Test fun unknownFutureMetadataKeysAreDeniedByDefault() =
        assertTrue(securitySessionDisplayMetadata(mapOf("newSecret" to "value")).isEmpty())
    @Test fun ordinaryLocaleTagsRemainReadable() {
        for (locale in listOf("en", "ru", "kk", "ky", "tg", "uz", "en-US", "pt_BR", "zh-Hant-TW"))
            assertEquals(mapOf("localeLanguage" to locale), securitySessionDisplayMetadata(mapOf("localeLanguage" to " $locale ")))
    }
    @Test fun malformedAndInjectedLocalesAreRemoved() {
        for (locale in listOf("", "  ", "token-secret-value-here", "en\nID:123", "<script>", "a".repeat(80)))
            assertTrue(securitySessionDisplayMetadata(mapOf("localeLanguage" to locale)).isEmpty(), locale)
    }
    @Test fun emptyLegacyHistoryRemainsEmpty() = assertTrue(securitySessionDisplayMetadata(emptyMap()).isEmpty())
}
