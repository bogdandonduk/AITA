package kz.aita.server.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AitaResendEmailPayloadTest {
    private fun body(replyTo: String? = null) = aitaResendEmailRequestJson(
        from = "AITA Security <security@auth.aita.kz>",
        to = "recipient@example.com",
        subject = "Your AITA sign-in code",
        html = "<p>123456</p>",
        replyTo = replyTo
    )

    @Test
    fun missingReplyToIsOmitted() {
        val request = Json.parseToJsonElement(body()).jsonObject
        assertFalse("reply_to" in request)
        assertEquals(setOf("from", "to", "subject", "html"), request.keys)
    }

    @Test
    fun emptyReplyToIsOmitted() {
        assertFalse("reply_to" in Json.parseToJsonElement(body("")).jsonObject)
    }

    @Test
    fun whitespaceReplyToIsOmitted() {
        assertFalse("reply_to" in Json.parseToJsonElement(body(" \t\n ")).jsonObject)
    }

    @Test
    fun configuredReplyToIsTrimmedAndIncludedAsAString() {
        val request = Json.parseToJsonElement(body(" support@example.com ")).jsonObject
        assertEquals("support@example.com", request.getValue("reply_to").jsonPrimitive.content)
    }

    @Test
    fun recipientRemainsASingleElementArray() {
        val request = Json.parseToJsonElement(body()).jsonObject
        assertEquals(1, request.getValue("to").jsonArray.size)
        assertEquals("recipient@example.com", request.getValue("to").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun messageContentSurvivesJsonEscaping() {
        val from = "AITA \"Security\" <security@auth.aita.kz>"
        val subject = "Код входа — AITA \"123456\""
        val html = "<p title=\"code\">123456</p>\n\tҚұпия код: \\"
        val request = Json.parseToJsonElement(
            aitaResendEmailRequestJson(from, "recipient@example.com", subject, html)
        ).jsonObject
        assertEquals(from, request.getValue("from").jsonPrimitive.content)
        assertEquals(subject, request.getValue("subject").jsonPrimitive.content)
        assertEquals(html, request.getValue("html").jsonPrimitive.content)
    }

    @Test
    fun repeatedSerializationIsStableForProviderRetries() {
        assertEquals(body(), body())
        assertEquals(body("support@example.com"), body("support@example.com"))
    }
    @Test
    fun plainTextAlternativeIsIncludedAndOptional() {
        val text = "AITA\nКод: 001234\nEnter it in the app."
        val request = Json.parseToJsonElement(
            aitaResendEmailRequestJson("AITA <a@example.com>", "b@example.com", "Code", "<p>001234</p>", text = text)
        ).jsonObject
        assertEquals(text, request.getValue("text").jsonPrimitive.content)
        assertFalse("text" in Json.parseToJsonElement(body()).jsonObject)
    }
    @Test
    fun inlineLogoHasMatchingCidAndImmutableContent() {
        val copy = aitaAuthEmailCopy("PASSWORDLESS_LOGIN", "ru", "001234", 10)
        val request = Json.parseToJsonElement(aitaResendEmailRequestJson(
            "AITA <security@example.com>", "recipient@example.com", copy.subject, copy.html,
            text = copy.text, inlineImages = copy.inlineImages)).jsonObject
        val logo = request.getValue("attachments").jsonArray.single().jsonObject
        assertEquals("aita-logo", logo.getValue("content_id").jsonPrimitive.content)
        assertEquals("aita-logo.png", logo.getValue("filename").jsonPrimitive.content)
        assertEquals("image/png", logo.getValue("content_type").jsonPrimitive.content)
        assertEquals(copy.inlineImages.single().base64, logo.getValue("content").jsonPrimitive.content)
        assertFalse("attachments" in Json.parseToJsonElement(body()).jsonObject)
    }

}
