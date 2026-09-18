package kz.aita

/** Reject callbacks from an earlier recording or a different account/store session. */
internal class VoiceFieldSession(private val currentOwner: () -> Pair<Long, String?>) {
    private var revision = 0L
    fun begin(): () -> Boolean {
        val ticket = ++revision
        val owner = currentOwner()
        return { ticket == revision && owner == currentOwner() }
    }
    fun cancel() { revision++ }
}
