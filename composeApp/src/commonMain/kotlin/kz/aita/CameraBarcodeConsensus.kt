package kz.aita

/** Never guess missing digits. Require the same complete decode across distinct camera frames. */
internal class CameraBarcodeConsensus {
    private var candidate = ""
    private var firstAt = 0L
    private var lastAt = 0L
    private var hits = 0
    private var emitted = ""
    private var emittedAt = 0L

    fun accept(raw: String, now: Long): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > 256 || value.any { it.isISOControl() }) return null
        if (value != candidate || now < lastAt || now - lastAt > 800) {
            candidate = value; firstAt = now; hits = 1
        } else if (now > lastAt) hits++
        lastAt = now
        if (hits < 3 || now - firstAt < 100 || (emitted == value && now - emittedAt < 3000)) return null
        emitted = value; emittedAt = now
        return value
    }
}
