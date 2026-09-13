package kz.aita.server.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AitaUzbekAuthEmailTemplateTest {
    private val purposes = listOf(
        "PASSWORDLESS_LOGIN", "LOGIN_EMAIL_FACTOR", "SECURITY_EMAIL_PROOF",
        "TOTP_RECOVERY", "PASSWORD_RECOVERY", "EMAIL_ALIAS", "PHONE_ALIAS"
    )

    @Test
    fun everyPurposeHasUzbekPlainTextHtmlAndSecurityFooter() {
        for (purpose in purposes) {
            val copy = aitaAuthEmailCopy(purpose, "uz", "001234", 17)
            val english = aitaAuthEmailCopy(purpose, "en", "001234", 17)
            assertNotEquals(english.subject, copy.subject, purpose)
            assertNotEquals(english.text, copy.text, purpose)
            assertTrue(copy.subject.startsWith("AITA · "))
            assertTrue(copy.html.contains("<html lang=\"uz\">"))
            assertTrue(copy.html.contains("Hisob xavfsizligi"))
            assertFalse(copy.html.contains("Account security"))
            assertTrue(copy.text.contains("001234"))
            assertTrue(copy.html.contains("001234"))
            assertTrue(copy.text.contains("17 daqiqa"))
            assertTrue(copy.html.contains("17 daqiqa"))
            assertFalse(copy.html.contains("<script"))
            assertFalse(copy.html.contains("https://"))
            assertEquals(english.inlineImages, copy.inlineImages)
        }
    }

    @Test
    fun regionalLatinScriptAndCaseAliasesUseTheSameCopy() {
        for (purpose in purposes + "TOTP_RESET_NOTICE") {
            val expected = aitaAuthEmailCopy(purpose, "uz", "001234", 10)
            for (alias in listOf("uz-UZ", "UZ", "UZ_uz", "uz-Latn-UZ", "uz_Latn_UZ")) {
                assertEquals(expected, aitaAuthEmailCopy(purpose, alias, "001234", 10), "$purpose/$alias")
            }
        }
    }

    @Test
    fun unknownLanguageNamesDoNotSelectUzbek() {
        for (locale in listOf("uzbek", "uzINVALID", "unknown", "")) {
            for (purpose in purposes + "TOTP_RESET_NOTICE") {
                assertEquals(aitaAuthEmailCopy(purpose, "en", "001234", 10),
                    aitaAuthEmailCopy(purpose, locale, "001234", 10), "$purpose/$locale")
            }
        }
    }

    @Test
    fun codesRetainLeadingZeroesAndExpiryIsInterpolated() {
        for (code in listOf("000000", "000001", "012345")) {
            for (minutes in listOf(1L, 7L, 23L)) {
                val copy = aitaAuthEmailCopy("PASSWORD_RECOVERY", "uz", code, minutes)
                assertEquals(1, Regex(Regex.escape(code)).findAll(copy.text).count())
                assertEquals(1, Regex(Regex.escape(code)).findAll(copy.html).count())
                assertTrue(copy.text.contains("$minutes daqiqa"))
                assertTrue(copy.html.contains("$minutes daqiqa"))
                assertTrue(copy.text.contains("Bu kodni hech kimga bermang."))
            }
        }
    }

    @Test
    fun recoveryWarningKeepsDestructiveConsequencesAndExplicitConsent() {
        val copy = aitaAuthEmailCopy("TOTP_RECOVERY", "uz", "001234", 10)
        for (phrase in listOf("autentifikatoringiz va tiklash kodlarini oʻchiradi",
            "barcha kirish seanslarini tugatadi", "faqat", "oʻzingiz soʻragan boʻlsangiz")) {
            assertTrue(copy.text.contains(phrase), phrase)
            assertTrue(copy.html.contains(phrase), "HTML: $phrase")
        }
    }

    @Test
    fun removalNoticeHasNoCodeOrLinkAndExplainsRevocation() {
        val copy = aitaAuthEmailCopy("TOTP_RESET_NOTICE", "uz", "001234", 10)
        assertTrue(copy.html.contains("<html lang=\"uz\">"))
        assertFalse(copy.text.contains("001234"))
        assertFalse(copy.html.contains("001234"))
        assertFalse(copy.html.contains("href="))
        for (phrase in listOf("parol va elektron pochta kodi", "Barcha kirish seanslari",
            "eski tiklash kodlari bekor qilindi", "darhol oʻzgartiring", "elektron pochta hisobingizni himoyalang")) {
            assertTrue(copy.text.contains(phrase), phrase)
            assertTrue(copy.html.contains(phrase), "HTML: $phrase")
        }
    }

    @Test
    fun securityChangeCopyRemainsDistinctFromSignIn() {
        val copy = aitaAuthEmailCopy("SECURITY_EMAIL_PROOF", "uz", "001234", 10)
        val instruction = "Oʻzingiz soʻragan oʻzgarishni tasdiqlash uchun"
        assertTrue(copy.text.contains(instruction))
        assertTrue(copy.html.contains(instruction))
        assertNotEquals(aitaAuthEmailCopy("PASSWORDLESS_LOGIN", "uz", "001234", 10).subject, copy.subject)
    }

    @Test
    fun codeValidationRejectsMarkupAndNonAsciiDigitsBeforeTranslation() {
        for (locale in listOf("uz", "uz-UZ", "uz-Latn-UZ")) {
            for (code in listOf("<b>123", "123", "1234567", "abcdef", "١٢٣٤٥٦", "１２３４５６")) {
                assertFailsWith<IllegalArgumentException> {
                    aitaAuthEmailCopy("PASSWORDLESS_LOGIN", locale, code, 10)
                }
            }
        }
    }
}
