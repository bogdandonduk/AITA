package kz.aita.server.encrypt

import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.MessageDigest
import java.util.*

object Pw {
  private val bcrypt = BCrypt.withDefaults()
  private val verifyer = BCrypt.verifyer()

  fun hash(password: CharArray): String =
    bcrypt.hashToString(12, password)               // Cost 12; raise to 13–14 for extra security

  fun verify(password: CharArray, hash: String): Boolean =
    verifyer.verify(password, hash).verified
}

object Refresh {
  private val sha256 = MessageDigest.getInstance("SHA-256")

  fun newPlainToken(): String =
    UUID.randomUUID().toString() + "-" + UUID.randomUUID()   // Long random string

  fun hash(token: String): String =
    sha256.digest(token.toByteArray()).joinToString("") { "%02x".format(it) } // hex(sha256)
}
