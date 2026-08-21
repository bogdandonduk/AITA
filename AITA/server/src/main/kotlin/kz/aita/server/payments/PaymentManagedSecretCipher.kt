package kz.aita.server.payments

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class ManagedSecretEnvelope(
    val formatVersion: Int = 1,
    val keyVersion: Int,
    val nonceB64: String,
    val cipherTextB64: String,
)

internal class PaymentManagedSecretCipher private constructor(
    private val currentVersion: Int,
    private val keys: Map<Int, ByteArray>,
) {
    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }

    fun encrypt(storeId: String, provider: String, environment: String, keyName: String, value: CharArray): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val plain = value.concatToString().encodeToByteArray()
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.getValue(currentVersion), "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(context(storeId, provider, environment, keyName))
            val encrypted = cipher.doFinal(plain)
            json.encodeToString(
                ManagedSecretEnvelope(
                    keyVersion = currentVersion,
                    nonceB64 = Base64.getEncoder().encodeToString(nonce),
                    cipherTextB64 = Base64.getEncoder().encodeToString(encrypted),
                )
            )
        } finally {
            plain.fill(0)
            nonce.fill(0)
            value.fill('\u0000')
        }
    }

    fun decrypt(storeId: String, provider: String, environment: String, keyName: String, envelopeText: String): CharArray {
        val envelope = json.decodeFromString<ManagedSecretEnvelope>(envelopeText)
        val key = keys[envelope.keyVersion] ?: error("Missing integration master key version ${envelope.keyVersion}")
        val nonce = Base64.getDecoder().decode(envelope.nonceB64)
        val encrypted = Base64.getDecoder().decode(envelope.cipherTextB64)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(context(storeId, provider, environment, keyName))
            cipher.doFinal(encrypted).decodeToString().toCharArray()
        } finally {
            nonce.fill(0)
            encrypted.fill(0)
        }
    }

    private fun context(storeId: String, provider: String, environment: String, keyName: String): ByteArray =
        "aita-payment-credential|$storeId|$provider|$environment|$keyName".encodeToByteArray()

    companion object {
        fun fromEnvironment(): PaymentManagedSecretCipher {
            val currentVersion = System.getenv("AITA_INTEGRATION_MASTER_KEY_VERSION")?.trim()?.toIntOrNull()
                ?: error("AITA_INTEGRATION_MASTER_KEY_VERSION is missing")
            val current = decodeKey(System.getenv("AITA_INTEGRATION_MASTER_KEY_B64"), "current")
            val keys = linkedMapOf(currentVersion to current)
            System.getenv("AITA_INTEGRATION_PREVIOUS_MASTER_KEYS").orEmpty()
                .split(',', ';')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach { entry ->
                    val separator = if ('=' in entry) '=' else ':'
                    val parts = entry.split(separator, limit = 2)
                    require(parts.size == 2) { "Invalid previous integration key entry" }
                    val version = parts[0].trim().toInt()
                    require(version !in keys) { "Duplicate integration key version $version" }
                    keys[version] = decodeKey(parts[1], "version $version")
                }
            return PaymentManagedSecretCipher(currentVersion, keys)
        }

        private fun decodeKey(raw: String?, label: String): ByteArray {
            val decoded = Base64.getDecoder().decode(raw?.trim()?.takeIf(String::isNotEmpty)
                ?: error("Missing $label integration master key"))
            require(decoded.size == 32) { "Integration master key $label must decode to exactly 32 bytes" }
            return decoded
        }
    }
}
