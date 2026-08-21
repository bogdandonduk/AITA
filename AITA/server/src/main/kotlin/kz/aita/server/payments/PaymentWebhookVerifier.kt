package kz.aita.server.payments

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Provider-neutral verifier. It is enabled only after the provider's documented signature profile is configured. */
data class PaymentWebhookSignatureProfile(
    val algorithm: String,
    val headerName: String,
    val prefix: String = "",
    val encoding: String = "hex",
    val maximumClockSkewSeconds: Long? = null,
)

class PaymentWebhookVerifier(
    private val profile: PaymentWebhookSignatureProfile,
    secret: ByteArray,
) {
    private val secret = secret.copyOf()

    init {
        require(this.secret.size >= 16) { "webhook_secret_too_short" }
        require(profile.algorithm in setOf("HmacSHA256", "HmacSHA512"))
        require(profile.headerName.matches(Regex("[A-Za-z0-9-]{1,80}")))
        require(profile.encoding in setOf("hex", "base64"))
    }

    fun verify(payload: ByteArray, suppliedHeader: String?): Boolean {
        if (suppliedHeader.isNullOrBlank()) return false
        val supplied = suppliedHeader.trim().removePrefix(profile.prefix).trim()
        val mac = Mac.getInstance(profile.algorithm)
        mac.init(SecretKeySpec(secret, profile.algorithm))
        val expectedBytes = mac.doFinal(payload)
        val suppliedBytes = when (profile.encoding) {
            "hex" -> supplied.hexToBytesOrNull()
            "base64" -> runCatching { java.util.Base64.getDecoder().decode(supplied) }.getOrNull()
            else -> null
        } ?: return false
        return MessageDigest.isEqual(expectedBytes, suppliedBytes)
    }

    fun close() {
        secret.fill(0)
    }
}

private fun String.hexToBytesOrNull(): ByteArray? {
    if (length % 2 != 0 || any { it.digitToIntOrNull(16) == null }) return null
    return ByteArray(length / 2) { index ->
        val high = this[index * 2].digitToInt(16)
        val low = this[index * 2 + 1].digitToInt(16)
        ((high shl 4) or low).toByte()
    }
}
