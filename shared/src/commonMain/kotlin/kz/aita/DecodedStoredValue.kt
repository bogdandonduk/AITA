package kz.aita

/** Cache decoding, while still observing external storage changes on every read.
 * Used on the browser's single application thread. A failed read/decode/write is never
 * published as missing credentials or a successfully saved value.
 */
internal class DecodedStoredValue<T>(
    private val read: () -> String?,
    private val write: (String?) -> Unit,
    private val decode: (String) -> T,
    private val encode: (T) -> String
) {
    private var loaded = false
    private var encoded: String? = null
    private var value: T? = null

    fun get(): T? {
        val next = read()
        if (!loaded || next != encoded) {
            val decoded = next?.let(decode)
            value = decoded
            encoded = next
            loaded = true
        }
        return value
    }

    fun set(next: T?) {
        val raw = next?.let(encode)
        write(raw)
        value = next
        encoded = raw
        loaded = true
    }
}
