package kz.aita

import java.util.concurrent.Executors
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class PersistentCredentialCacheTest {
    @Test fun concurrentOwnershipChecksDecodeCredentialsOnlyOnce() {
        val reads = AtomicInteger()
        val payload = Any()
        val cache = PersistentCredentialCache({ reads.incrementAndGet(); Thread.sleep(10); payload }, {})
        val pool = Executors.newFixedThreadPool(24)
        try {
            pool.invokeAll(List(500) { Callable { cache.get() } }).forEach { assertSame(payload, it.get()) }
            assertEquals(1, reads.get())
        } finally { pool.shutdownNow() }
    }

    @Test fun failedReadDoesNotDeleteOrCacheMissingCredentials() {
        var fail = true
        var stored: String? = "existing-session"
        val cache = PersistentCredentialCache({ if (fail) error("Temporary keystore failure"); stored }, { stored = it })
        assertFailsWith<IllegalStateException> { cache.get() }
        assertEquals("existing-session", stored)
        fail = false
        assertEquals("existing-session", cache.get())
    }

    @Test fun refreshAndExplicitLogoutReplaceCachedValueAfterPersistence() {
        var stored: String? = "old"
        val cache = PersistentCredentialCache({ stored }, { stored = it })
        assertEquals("old", cache.get())
        cache.set("new")
        assertEquals("new", cache.get())
        cache.set(null)
        assertNull(cache.get()); assertNull(stored)
    }

    @Test fun failedRefreshWriteDoesNotInstallUndurableToken() {
        val cache = PersistentCredentialCache({ "old" }, { error("Disk full") })
        assertEquals("old", cache.get())
        assertFailsWith<IllegalStateException> { cache.set("new") }
        assertEquals("old", cache.get())
    }
}
