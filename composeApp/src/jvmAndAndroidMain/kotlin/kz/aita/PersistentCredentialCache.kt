package kz.aita

/** One decoded credential value per process. Publish writes only after durable storage succeeds.
 * A read failure is not an absent credential and must never be cached or delete its backing data.
 */
internal class PersistentCredentialCache<T>(
    private val read: () -> T?,
    private val write: (T?) -> Unit
) {
    private var loaded = false
    private var value: T? = null

    @Synchronized fun get(): T? {
        if (!loaded) {
            value = read()
            loaded = true
        }
        return value
    }

    @Synchronized fun set(next: T?) {
        write(next)
        value = next
        loaded = true
    }
}
