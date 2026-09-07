package kz.aita.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Resend's optional reply_to accepts an address (or an array), not JSON null.
 * Build the provider body separately from AITA's encodeDefaults=true envelopes
 * so an unset reply address is omitted without changing any shared JSON settings.
 */
internal fun aitaResendEmailRequestJson(
    from: String,
    to: String,
    subject: String,
    html: String,
    replyTo: String? = null
): String = buildJsonObject {
    put("from", from)
    put("to", JsonArray(listOf(JsonPrimitive(to))))
    put("subject", subject)
    put("html", html)
    replyTo?.trim()?.takeIf { it.isNotEmpty() }?.let { put("reply_to", it) }
}.toString()
