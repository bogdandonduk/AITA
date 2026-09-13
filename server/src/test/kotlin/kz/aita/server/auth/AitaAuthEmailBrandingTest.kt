package kz.aita.server.auth

import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.*

class AitaAuthEmailBrandingTest {
    @Test fun actualBundledLogoIsADecodableRasterImage() {
        val image = AitaAuthEmailBranding.images.single()
        assertEquals("aita-logo", image.contentId)
        assertEquals("aita-logo.png", image.filename)
        val png = Base64.getDecoder().decode(image.base64)
        assertContentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), png.take(8).toByteArray())
        val decoded = assertNotNull(ImageIO.read(ByteArrayInputStream(png)))
        assertEquals(480, decoded.width); assertEquals(240, decoded.height)
    }
    @Test fun allNewMailPurposesUseTheSameLocalBranding() {
        for (purpose in listOf("PASSWORDLESS_LOGIN", "PASSWORD_RECOVERY", "PHONE_ALIAS", "EMAIL_ALIAS",
            "TOTP_RECOVERY", "TOTP_RESET_NOTICE", "LOGIN_EMAIL_FACTOR", "SECURITY_EMAIL_PROOF")) {
            for (locale in listOf("en", "ru", "kk", "ky", "ky-KG")) {
                val copy = aitaAuthEmailCopy(purpose, locale, "001234", 10)
                assertTrue(copy.html.contains("src=\"cid:aita-logo\""))
                assertEquals(AitaAuthEmailBranding.images, copy.inlineImages)
                assertFalse(copy.html.contains("https://"))
                assertFalse(copy.html.contains("<script"))
                assertTrue(copy.html.contains("align=\"center\""))
            }
        }
    }
    @Test fun plainTextCodeIsNotDependentOnAnImage() {
        val copy = aitaAuthEmailCopy("LOGIN_EMAIL_FACTOR", "ru", "001234", 5)
        assertTrue(copy.text.contains("001234")); assertTrue(copy.html.contains(">001234<"))
        assertFalse(copy.subject.contains("001234"))
    }
    @Test fun layoutEscapesAllCallerText() {
        val html = aitaAuthEmailLayout("<b>bad</b>", "<script>bad</script>", "123456", "5&10", "\"quote\"", "en")
        assertTrue(html.contains("&lt;b&gt;")); assertFalse(html.contains("<script>"))
        assertTrue(html.contains("5&amp;10")); assertTrue(html.contains("&quot;quote&quot;"))
    }
    @Test fun noticesHaveNoCodeBox() {
        val copy = aitaAuthEmailCopy("TOTP_RESET_NOTICE", "en", "001234", 10)
        assertFalse(copy.html.contains("001234")); assertFalse(copy.html.contains("letter-spacing:7px"))
        assertTrue(copy.text.contains("revoked"))
    }
}
