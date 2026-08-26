package kz.aita.server.payments

import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedIntegrationSecret(
    val keyVersion: Int,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

class IntegrationSecretCipher private constructor(
    private val activeKeyVersion: Int,
    private val keys: Map<Int, SecretKeySpec>,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): EncryptedIntegrationSecret {
        require(plaintext.isNotEmpty()) { "Integration secret cannot be empty." }
        val key = keys[activeKeyVersion] ?: error("Active integration key is unavailable.")
        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        return EncryptedIntegrationSecret(
            keyVersion = activeKeyVersion,
            nonce = nonce,
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    fun decrypt(
        envelope: EncryptedIntegrationSecret,
        associatedData: ByteArray,
    ): ByteArray {
        val key = keys[envelope.keyVersion]
            ?: error("Integration key version ${envelope.keyVersion} is unavailable.")
        require(envelope.nonce.size == NONCE_BYTES) { "Invalid integration-secret nonce." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, envelope.nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(envelope.ciphertext)
    }

    companion object {
        private const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        fun fromEnvironment(
            environment: Map<String, String> = System.getenv(),
        ): IntegrationSecretCipher {
            val activeVersion = environment["AITA_INTEGRATION_MASTER_KEY_VERSION"]
                ?.trim()
                ?.toIntOrNull()
                ?: 1
            val activeRaw = environment["AITA_INTEGRATION_MASTER_KEY_B64"]
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: error(
                    "AITA_INTEGRATION_MASTER_KEY_B64 is required. " +
                        "Generate a random 32-byte key and keep it only in the server environment.",
                )
            val keys = mutableMapOf(activeVersion to decodeKey(activeRaw))
            environment["AITA_INTEGRATION_PREVIOUS_MASTER_KEYS"]
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                ?.forEach { encodedEntry ->
                    val separator = encodedEntry.indexOf(':')
                    require(separator > 0) {
                        "Previous integration keys must use version:base64 format."
                    }
                    val version = encodedEntry.substring(0, separator).toInt()
                    val encoded = encodedEntry.substring(separator + 1)
                    keys[version] = decodeKey(encoded)
                }
            return IntegrationSecretCipher(
                activeKeyVersion = activeVersion,
                keys = keys,
            )
        }

        fun associatedData(
            storeId: String,
            provider: String,
            environment: String,
            credentialId: String,
        ): ByteArray = listOf(
            storeId.trim(),
            provider.trim().uppercase(),
            environment.trim().uppercase(),
            credentialId.trim(),
        ).joinToString("|").encodeToByteArray()

        private fun decodeKey(encoded: String): SecretKeySpec {
            val decoded = try {
                Base64.getDecoder().decode(encoded)
            } catch (throwable: IllegalArgumentException) {
                throw IllegalArgumentException("Integration master key is not valid Base64.", throwable)
            }
            require(decoded.size == KEY_BYTES) {
                "Integration master key must decode to exactly $KEY_BYTES bytes."
            }
            return SecretKeySpec(decoded, "AES")
        }
    }
}
