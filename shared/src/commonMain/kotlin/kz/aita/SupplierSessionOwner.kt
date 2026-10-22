package kz.aita

/** Captured before queueing work: account identity alone cannot distinguish logout/relogin. */
internal class SupplierSessionOwner(
    val accountId: String,
    val sessionGeneration: Long,
    val userScope: String,
    private val currentUserScope: () -> String,
    private val currentGeneration: () -> Long
) {
    fun isCurrent(): Boolean = accountId.isNotBlank() && userScope.isNotBlank() &&
        currentUserScope() == userScope && currentGeneration() == sessionGeneration

    /** Check both sides of suspension, and pass the captured generation to the HTTP token pin. */
    suspend fun <T : Any> request(send: suspend (Long) -> T): T? {
        if (!isCurrent()) return null
        val response = send(sessionGeneration)
        return response.takeIf { isCurrent() }
    }
}
