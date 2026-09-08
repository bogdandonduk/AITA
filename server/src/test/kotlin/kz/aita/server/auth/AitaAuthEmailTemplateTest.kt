package kz.aita.server.auth
import kotlin.test.*
class AitaAuthEmailTemplateTest {
    @Test fun everyPurposeHasPlainAndHtmlCode() {
        for (locale in listOf("en","ru","kk","kz","ru-RU","unknown"))
            for (purpose in listOf("PASSWORDLESS_LOGIN","PASSWORD_RECOVERY","PHONE_ALIAS")) {
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
}
