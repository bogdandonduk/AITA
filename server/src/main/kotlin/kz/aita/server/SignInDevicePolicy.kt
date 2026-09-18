package kz.aita.server

/** Installation identity wins over a reused visible device name. Missing IDs use legacy metadata. */
internal fun isDifferentSignInDevice(previous: Map<String, String>?, current: Map<String, String>?): Boolean {
    if (previous.isNullOrEmpty() || current.isNullOrEmpty()) return false
    val old = previous["installationId"]?.takeIf { it.isNotBlank() }
    val now = current["installationId"]?.takeIf { it.isNotBlank() }
    if (old != null && now != null) return old != now
    val keys = listOf("deviceName", "platformName", "osName")
    return keys.any { !previous[it].isNullOrBlank() && !current[it].isNullOrBlank() && previous[it] != current[it] }
}
