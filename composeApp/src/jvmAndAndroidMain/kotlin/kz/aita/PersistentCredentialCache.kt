package kz.aita

/** One decoded credential value per process. Publish writes only after durable storage succeeds.
 * A read failure is not an absent credential and must never be cached or delete its backing data.
 */
internal class PersistentCredentialCache<T>(
    private val read: () -> T?,
    private val write: (T?) -> Unit
) {
    private data class Snapshot<T>(val value: T?)
    @Volatile private var snapshot: Snapshot<T>? = null

    // A UI ownership check must not wait behind an encrypted disk write. Until that write
    // commits, the last durable credential is still the current credential.
    fun get(): T? {
        snapshot?.let { return it.value }
        return synchronized(this) {
            snapshot?.value ?: if (snapshot != null) null else read().also { snapshot = Snapshot(it) }
        }
    }

    @Synchronized fun set(next: T?) {
        write(next)
        snapshot = Snapshot(next)
    }
}
