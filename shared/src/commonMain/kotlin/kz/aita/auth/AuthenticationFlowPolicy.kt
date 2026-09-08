package kz.aita.auth

/** A password must never be replayed via a legacy path after an ambiguous or rejected attempt. */
internal fun canUseLegacyPasswordRoute(httpStatusCode: Int?, transportFailure: Boolean): Boolean =
    !transportFailure && (httpStatusCode == 404 || httpStatusCode == 405)

/** Use the server's duration, not the phone's clock; older servers fall back to local wall time. */
fun aitaAuthResendDelayMillis(resendAfterMillis: Long, serverTimeMillis: Long, localTimeMillis: Long): Long {
    val reference = serverTimeMillis.takeIf { it > 0L } ?: localTimeMillis
    if (resendAfterMillis <= reference || resendAfterMillis <= 0L) return 0L
    // The server's supported maximum is ten minutes. Saturate before subtraction to avoid overflow.
    return if (reference < resendAfterMillis - 600_000L) 600_000L else resendAfterMillis - reference
}

fun aitaAuthCountdownSeconds(remainingMillis: Long): Long =
    if (remainingMillis <= 0L) 0L else 1L + (remainingMillis - 1L) / 1000L

/** Absolute expiry is server-authoritative; this is only a bounded UI countdown. */
fun aitaAuthExpiryDelayMillis(expiresAtMillis: Long, serverTimeMillis: Long, localTimeMillis: Long): Long {
    val reference = serverTimeMillis.takeIf { it > 0L } ?: localTimeMillis
    if (expiresAtMillis <= reference || expiresAtMillis <= 0L) return 0L
    return if (reference < expiresAtMillis - 3_600_000L) 3_600_000L else expiresAtMillis - reference
}
