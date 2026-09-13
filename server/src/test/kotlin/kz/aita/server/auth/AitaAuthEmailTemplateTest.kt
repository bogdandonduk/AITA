package kz.aita.server.auth
import kotlin.test.*
class AitaAuthEmailTemplateTest {
    @Test fun everyPurposeHasPlainAndHtmlCode() {
        for (locale in listOf("en","ru","kk","kz","ru-RU","ky","ky-KG","unknown"))
            for (purpose in listOf("PASSWORDLESS_LOGIN","PASSWORD_RECOVERY","PHONE_ALIAS","EMAIL_ALIAS","TOTP_RECOVERY")) {
                val copy=aitaAuthEmailCopy(purpose,locale,"001234",10)
                assertTrue(copy.subject.startsWith("AITA"))
                assertTrue(copy.text.contains("001234"));assertTrue(copy.html.contains("001234"))
                assertFalse(copy.html.contains("\\\"")); assertTrue(copy.html.contains("role=\"presentation\""))
                assertFalse(copy.html.contains("http://"));assertFalse(copy.html.contains("<script"))
            }
    }
    @Test fun kazakhAliasesAreIdentical() {
        assertEquals(aitaAuthEmailCopy("PHONE_ALIAS","kk","000000",10),aitaAuthEmailCopy("PHONE_ALIAS","kz","000000",10))
    }
    @Test fun invalidCodesCannotBecomeHtml() {
        for (v in listOf("<b>123", "123", "1234567", "abcdef")) {
            assertFailsWith<IllegalArgumentException> { aitaAuthEmailCopy("PASSWORDLESS_LOGIN","en",v,10) }
        }
    }

    @Test fun removalNoticeContainsNoReusableCodeOrResetLink() {
        for (locale in listOf("en", "ru", "kk", "kz", "ky", "ky-KG")) {
            val copy = aitaAuthEmailCopy("TOTP_RESET_NOTICE", locale, "001234", 10)
            assertFalse(copy.text.contains("001234")); assertFalse(copy.html.contains("001234"))
            assertFalse(copy.html.contains("href=")); assertTrue(copy.subject.startsWith("AITA"))
        }
    }
    @Test fun recoveryWarningNamesTheDestructiveSecurityAction() {
        val copy = aitaAuthEmailCopy("TOTP_RECOVERY", "en", "001234", 10)
        assertTrue(copy.text.contains("removes your authenticator"))
        assertTrue(copy.text.contains("ends all sign-in sessions"))
        assertTrue(copy.text.contains("only if you requested"))
    }
    @Test fun kyrgyzEmailKeepsTheCodeExpiryAndSecurityWarnings() {
        val copy = aitaAuthEmailCopy("TOTP_RECOVERY", "ky-KG", "001234", 10)
        assertEquals(copy, aitaAuthEmailCopy("TOTP_RECOVERY", "ky", "001234", 10))
        assertTrue(copy.html.contains("lang=\"ky\""))
        assertTrue(copy.text.contains("001234")); assertTrue(copy.text.contains("10 мүнөт"))
        assertTrue(copy.text.contains("бардык кирүү сеанстарын аяктатат"))
        assertTrue(copy.text.contains("өзүңүз сурансаңыз гана"))
        assertTrue(copy.text.contains("эч кимге бербеңиз"))
        assertTrue(copy.html.contains("Аккаунттун коопсуздугу"))
    }
}

class AitaTajikKyrgyzAuthEmailTemplateTest {
    private val purposes = listOf(
        "PASSWORDLESS_LOGIN", "LOGIN_EMAIL_FACTOR", "SECURITY_EMAIL_PROOF",
        "TOTP_RECOVERY", "PASSWORD_RECOVERY", "EMAIL_ALIAS", "PHONE_ALIAS"
    )

    @Test fun everyPurposeHasLocalizedPlainTextHtmlAndSecurityFooter() {
        for ((language, securityFooter) in listOf("tg" to "Амнияти ҳисоб", "ky" to "Аккаунттун коопсуздугу")) {
            for (purpose in purposes) {
                val copy = aitaAuthEmailCopy(purpose, language, "001234", 17)
                val english = aitaAuthEmailCopy(purpose, "en", "001234", 17)
                assertNotEquals(english.subject, copy.subject, "$purpose/$language")
                assertNotEquals(english.text, copy.text, "$purpose/$language")
                assertTrue(copy.subject.startsWith("AITA · "))
                assertTrue(copy.html.contains("<html lang=\"$language\">"))
                assertTrue(copy.html.contains(securityFooter))
                assertFalse(copy.html.contains("Account security"))
                assertTrue(copy.text.contains("001234"))
                assertTrue(copy.html.contains("001234"))
                assertTrue(copy.text.contains("17"))
                assertTrue(copy.html.contains("17"))
                assertFalse(copy.html.contains("<script"))
                assertFalse(copy.html.contains("https://"))
                assertEquals(english.inlineImages, copy.inlineImages)
            }
        }
    }

    @Test fun regionalScriptAndCaseVariantsSelectTheSameFixedCopy() {
        for ((language, aliases) in listOf(
            "tg" to listOf("tg-TJ", "TG", "TG_tj", "tg-Cyrl-TJ"),
            "ky" to listOf("ky-KG", "KY", "KY_kg", "ky-Cyrl-KG")
        )) {
            for (purpose in purposes + "TOTP_RESET_NOTICE") {
                val expected = aitaAuthEmailCopy(purpose, language, "001234", 10)
                for (alias in aliases) {
                    assertEquals(expected, aitaAuthEmailCopy(purpose, alias, "001234", 10), "$purpose/$alias")
                }
            }
        }
    }

    @Test fun unknownLanguageNamesDoNotAccidentallySelectATranslation() {
        for (locale in listOf("tgif", "kyrgyz", "tgINVALID", "kyINVALID", "unknown", "")) {
            for (purpose in purposes + "TOTP_RESET_NOTICE") {
                assertEquals(aitaAuthEmailCopy(purpose, "en", "001234", 10),
                    aitaAuthEmailCopy(purpose, locale, "001234", 10), "$purpose/$locale")
            }
        }
    }

    @Test fun codesKeepLeadingZeroesAndExpiryIsNotHardCoded() {
        for (language in listOf("tg", "ky")) {
            for (code in listOf("000000", "000001", "012345")) {
                for (minutes in listOf(1L, 7L, 23L)) {
                    val copy = aitaAuthEmailCopy("PASSWORD_RECOVERY", language, code, minutes)
                    assertEquals(1, Regex(Regex.escape(code)).findAll(copy.text).count())
                    assertEquals(1, Regex(Regex.escape(code)).findAll(copy.html).count())
                    val expiry = if (language == "tg") "$minutes дақиқа" else "$minutes мүнөт"
                    assertTrue(copy.text.contains(expiry))
                    assertTrue(copy.html.contains(expiry))
                }
            }
        }
    }

    @Test fun translatedRecoveryWarningRetainsAllDestructiveConsequencesAndConsent() {
        val expected = mapOf(
            "tg" to listOf("аутентификатор", "кодҳои барқарорсозиро нест", "ҳамаи сеансҳои воридшавиро анҷом",
                "танҳо", "худатон дархост"),
            "ky" to listOf("аутентификаторуңузду", "калыбына келтирүү коддорун өчүрүп", "бардык кирүү сессияларын аяктатат",
                "өзүңүз суранган", "гана киргизиңиз")
        )
        for ((language, phrases) in expected) {
            val copy = aitaAuthEmailCopy("TOTP_RECOVERY", language, "001234", 10)
            for (phrase in phrases) {
                assertTrue(copy.text.contains(phrase), "$language: $phrase")
                assertTrue(copy.html.contains(phrase), "$language HTML: $phrase")
            }
        }
    }

    @Test fun translatedRemovalNoticeRevokesOldProofAndDoesNotProvideAnotherCode() {
        val expected = mapOf(
            "tg" to listOf("гузарвожа", "почтаи электронӣ", "Ҳамаи сеансҳои", "кодҳои кӯҳнаи барқарорсозӣ",
                "бекор карда шуданд", "фавран", "ҳисоби почтаи электронии худро ҳифз"),
            "ky" to listOf("сырсөз", "электрондук почта коду", "Бардык кирүү сессиялары", "эски калыбына келтирүү коддору",
                "жокко чыгарылды", "дароо", "электрондук почта аккаунтуңузду коргоңуз")
        )
        for ((language, phrases) in expected) {
            val copy = aitaAuthEmailCopy("TOTP_RESET_NOTICE", language, "001234", 10)
            assertTrue(copy.html.contains("<html lang=\"$language\">"))
            assertFalse(copy.text.contains("001234"))
            assertFalse(copy.html.contains("001234"))
            assertFalse(copy.html.contains("href="))
            for (phrase in phrases) assertTrue(copy.text.contains(phrase), "$language: $phrase")
        }
    }

    @Test fun securityChangeCopyDoesNotBecomeAGenericSignInPrompt() {
        for ((language, instruction) in listOf(
            "tg" to "Барои тасдиқи тағйире, ки худатон дархост кардед",
            "ky" to "Өзүңүз суранган өзгөртүүнү ырастоо үчүн"
        )) {
            val copy = aitaAuthEmailCopy("SECURITY_EMAIL_PROOF", language, "001234", 10)
            assertTrue(copy.text.contains(instruction))
            assertTrue(copy.html.contains(instruction))
            assertNotEquals(aitaAuthEmailCopy("PASSWORDLESS_LOGIN", language, "001234", 10).subject, copy.subject)
        }
    }

    @Test fun invalidCodeValidationStillAppliesBeforeSelectingNewLanguageCopy() {
        for (locale in listOf("tg", "ky", "tg-TJ", "ky-KG")) {
            for (code in listOf("<b>123", "123", "1234567", "abcdef", "١٢٣٤٥٦", "１２３４５６")) {
                assertFailsWith<IllegalArgumentException> {
                    aitaAuthEmailCopy("PASSWORDLESS_LOGIN", locale, code, 10)
                }
            }
        }
    }
}
