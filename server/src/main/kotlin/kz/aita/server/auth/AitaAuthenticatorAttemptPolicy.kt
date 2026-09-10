package kz.aita.server.auth

/** Admission uses persisted audit timestamps. All aliases of one known account share a bucket.
 * Returns a conservative delay without learning/returning any account or code information.
 */
internal fun aitaAuthenticatorRetryAfterSeconds(accountTimes: List<Long>, ipTimes: List<Long>, now: Long): Long {
    fun waitFor(times: List<Long>, window: Long, maximum: Int): Long {
        val active = times.filter { it > now - window }.sortedDescending()
        if (active.size < maximum) return 0L
        val remaining = (active[maximum - 1] + window - now).coerceAtLeast(1L)
        return 1L + (remaining - 1L) / 1_000L
    }
    return maxOf(waitFor(accountTimes, 60_000L, 8), waitFor(accountTimes, 3_600_000L, 40),
        waitFor(ipTimes, 60_000L, 60))
}
