package kz.aita.server.auth

import kz.aita.auth.aitaAuthCodeDigits
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 6238 SHA-1 / six digits, with one adjacent step of clock tolerance.
 * Callers serialize the security profile and persist the accepted step with the operation.
 */
internal fun aitaTotpMatchingStep(secret: String, rawCode: String, nowMillis: Long, lastUsedStep: Long?): Long? {
    val raw = rawCode.trim()
    if (nowMillis < 0 || raw.any { it.digitToIntOrNull() == null && !it.isWhitespace() }) return null
    val code = aitaAuthCodeDigits(raw).takeIf { it.length == 6 } ?: return null
    val current = nowMillis / 30_000L
    return listOf(current, current - 1, current + 1).firstOrNull { step ->
        step >= 0 && (lastUsedStep == null || step > lastUsedStep) && aitaTotpCode(secret, step) == code
    }
}

internal fun aitaTotpCode(secret: String, step: Long): String {
    require(step >= 0)
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    var buffer = 0
    var bits = 0
    val bytes = ArrayList<Byte>()
    secret.uppercase(java.util.Locale.ROOT).filterNot { it == '=' || it.isWhitespace() }.forEach { char ->
        val value = alphabet.indexOf(char)
        require(value >= 0) { "Invalid authenticator key" }
        buffer = (buffer shl 5) or value
        bits += 5
        if (bits >= 8) { bytes.add((buffer shr (bits - 8)).toByte()); bits -= 8 }
    }
    require(bytes.isNotEmpty())
    val counter = ByteArray(8) { index -> ((step ushr (56 - index * 8)) and 255L).toByte() }
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(bytes.toByteArray(), "HmacSHA1"))
    val hash = mac.doFinal(counter)
    val offset = hash.last().toInt() and 15
    val binary = ((hash[offset].toInt() and 127) shl 24) or
        ((hash[offset + 1].toInt() and 255) shl 16) or
        ((hash[offset + 2].toInt() and 255) shl 8) or (hash[offset + 3].toInt() and 255)
    return (binary % 1_000_000).toString().padStart(6, '0')
}
