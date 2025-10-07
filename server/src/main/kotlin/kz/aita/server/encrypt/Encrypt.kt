package kz.aita.server.encrypt

import at.favre.lib.crypto.bcrypt.BCrypt
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Pw {
  private val bcrypt = BCrypt.withDefaults()
  private val verifyer = BCrypt.verifyer()

  fun hash(password: CharArray): String =
    bcrypt.hashToString(14, password).also {
      Arrays.fill(password, '\u0000')
    }         // Cost 12; raise to 13–14 for extra security

  fun verify(password: CharArray, hash: String): Boolean =
    verifyer.verify(password, hash).verified.also {
      Arrays.fill(password, '\u0000')
    }
}

object Refresh {
  private val rng = SecureRandom()

  private val b64url = Base64.getUrlEncoder().withoutPadding()
  private val b64urlDec = Base64.getUrlDecoder()

  private val HEX = "0123456789abcdef".toCharArray()

  private val hmacKey: SecretKeySpec? = System.getenv("AITA_REFRESH_PEPPER")
    ?.takeIf { it.isNotBlank() }
    ?.let { SecretKeySpec(it.toByteArray(StandardCharsets.UTF_8), "HmacSHA256") }

  fun newPlainToken(): String {
    val buf = ByteArray(32)
    rng.nextBytes(buf)
    return b64url.encodeToString(buf)
  }

  fun hash(token: String): String {
    val key = requireNotNull(hmacKey) { "Set AITA_REFRESH_PEPPER to use HMAC hashing" }
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(key)
    val msg = try { b64urlDec.decode(token) } catch (_: Exception) {
      token.toByteArray(StandardCharsets.UTF_8)
    }
    return mac.doFinal(msg).toHexLower()
  }

  fun ByteArray.toHexLower(): String {
    val out = CharArray(size * 2)
    var i = 0
    for (b in this) {
      val v = b.toInt() and 0xFF
      out[i++] = HEX[v ushr 4]
      out[i++] = HEX[v and 0x0F]
    }
    return String(out)
  }
}
