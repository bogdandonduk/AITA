package kz.aita.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdvancedAuthenticationModelsTest {
    @Test fun emailNormalization() {
        assertEquals("user@example.com", normalizeAitaEmail(" USER@Example.COM "))
        assertNull(normalizeAitaEmail("not-email"))
    }

    @Test fun phoneNormalization() {
        assertEquals("+77771234567", normalizeAitaPhoneAlias("+7 (777) 123-45-67"))
        assertEquals("+77771234567", normalizeAitaPhoneAlias("8 777 123 45 67"))
        assertNull(normalizeAitaPhoneAlias("123"))
    }

    @Test fun codeNormalization() {
        assertEquals("123456", normalizeAitaOneTimeCode("123 456"))
        assertNull(normalizeAitaOneTimeCode("12345"))
    }
}
