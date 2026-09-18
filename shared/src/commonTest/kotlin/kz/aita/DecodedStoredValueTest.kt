package kz.aita

import kotlin.test.*

class DecodedStoredValueTest {
    @Test fun repeatedOwnershipChecksDecodeOnceAndObserveExternalChanges() {
        var stored: String? = "session-one"
        var decodes = 0
        val cache = DecodedStoredValue({ stored }, { stored = it }, { decodes++; listOf(it) }, { it.single() })
        val first = cache.get()
        repeat(500) { assertSame(first, cache.get()) }
        assertEquals(1, decodes)
        stored = "session-two"
        assertEquals(listOf("session-two"), cache.get())
        assertEquals(2, decodes)
        stored = null
        assertNull(cache.get())
    }

    @Test fun failedStorageWriteRetainsTheLastDurableSessionAndDoesNotClaimLogout() {
        var stored: String? = "durable-session"
        var fail = true
        val cache = DecodedStoredValue({ stored }, { if (fail) error("Quota"); stored = it }, { it }, { it })
        assertEquals("durable-session", cache.get())
        assertFailsWith<IllegalStateException> { cache.set("undurable-session") }
        assertEquals("durable-session", cache.get())
        assertFailsWith<IllegalStateException> { cache.set(null) }
        assertEquals("durable-session", cache.get())
        fail = false
        cache.set(null)
        assertNull(cache.get())
        assertNull(stored)
    }

    @Test fun failedReadsOrDecodingNeverBecomeCachedMissingCredentials() {
        var failRead = true
        var failDecode = false
        var stored: String? = "retained-session"
        val cache = DecodedStoredValue({ if (failRead) error("Storage unavailable"); stored }, { stored = it },
            { if (failDecode) error("Could not decode"); it }, { it })
        assertFailsWith<IllegalStateException> { cache.get() }
        failRead = false
        assertEquals("retained-session", cache.get())
        stored = "new-session"; failDecode = true
        assertFailsWith<IllegalStateException> { cache.get() }
        assertEquals("new-session", stored)
        failDecode = false
        assertEquals("new-session", cache.get())
    }
}
