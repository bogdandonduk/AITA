package kz.aita

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest

class ChunkedTextCacheTest {
    private class Memory {
        val rows = mutableMapOf<String, String>()
        var sequence = 1
        var writes = 0
        var failWrite = Int.MAX_VALUE
        val cache = ChunkedTextCache(
            read = { key, max -> rows[key]?.takeIf { it.length <= max } },
            write = { key, value -> if (++writes == failWrite) error("disk full"); rows[key] = value },
            remove = { rows.remove(it); Unit },
            removePrefixExcept = { prefix, keep -> rows.keys.removeAll { it.startsWith(prefix) && it != prefix + "manifest" && (keep.isEmpty() || !it.startsWith(keep)) }; Unit },
            newGeneration = { (sequence++).toString(16) }, chunkSize = 8, maxLength = 1024
        )
    }
    @Test fun multiChunkRoundTrip() = runTest {
        val m = Memory(); val text = "abcdefg".repeat(40); m.cache.put("stock", text)
        assertEquals(text, m.cache.get("stock")); assertFalse(m.rows.containsKey("stock"))
        assertTrue(m.rows.filterKeys { !it.endsWith("manifest") }.values.all { it.length <= 8 })
    }
    @Test fun emptyAuthoritativeSnapshotIsValid() = runTest {
        val m = Memory();m.cache.put("stock", "");assertEquals("",m.cache.get("stock"))
    }
    @Test fun legacySnapshotIsReadBeforeUpgrade() = runTest {
        val m=Memory();m.rows["stock"]="legacy";assertEquals("legacy",m.cache.get("stock"))
    }
    @Test fun successfulUpgradeRemovesLegacyAndPriorChunks() = runTest {
        val m=Memory();m.rows["stock"]="legacy";m.cache.put("stock","first long catalogue");val first=m.rows.keys.filterNot { it.endsWith("manifest") }.toSet()
        m.cache.put("stock","new");assertEquals("new",m.cache.get("stock"));assertTrue(first.none { it in m.rows })
    }
    @Test fun interruptedChunkWriteKeepsLastCommittedSnapshot() = runTest {
        val m=Memory();m.cache.put("stock","old catalogue");m.failWrite=m.writes+2
        assertFailsWith<IllegalStateException> { m.cache.put("stock","new catalogue".repeat(10)) }
        assertEquals("old catalogue",m.cache.get("stock"))
    }
    @Test fun interruptedManifestWriteKeepsLastCommittedSnapshot() = runTest {
        val m=Memory();m.cache.put("stock","old");m.failWrite=m.writes+2
        assertFailsWith<IllegalStateException> { m.cache.put("stock","new") };assertEquals("old",m.cache.get("stock"))
    }
    @Test fun corruptedChunkDoesNotBecomeAPartialCatalogue() = runTest {
        val m=Memory();m.cache.put("stock","first catalogue");val key=m.rows.keys.first { it.endsWith(":0") };m.rows[key]="corrupt!";assertNull(m.cache.get("stock"))
    }
    @Test fun missingChunkRejectsWholeSnapshot() = runTest {
        val m=Memory();m.cache.put("stock","first catalogue");m.rows.remove(m.rows.keys.first { it.endsWith(":0") });assertNull(m.cache.get("stock"))
    }
    @Test fun malformedManifestCannotRequestUnboundedReads() = runTest {
        val m=Memory();m.cache.put("stock","ok");m.rows[m.rows.keys.first { it.endsWith("manifest") }]="1|1|1024|999999|x";assertNull(m.cache.get("stock"))
    }
    @Test fun supplementaryCharactersNeverSplitAcrossSqlRows() = runTest {
        val m=Memory();val text="abcdefg\uD83D\uDE00".repeat(15);m.cache.put("stock",text)
        assertEquals(text,m.cache.get("stock"));assertTrue(m.rows.filterKeys { !it.endsWith("manifest") }.values.all { it.isEmpty() || !it.last().isHighSurrogate() })
    }
    @Test fun storeAndAccountNamespacesDoNotCollide() = runTest {
        val m=Memory();m.cache.put("a:b","one");m.cache.put("a:b:c","two");m.cache.delete("a:b")
        assertNull(m.cache.get("a:b"));assertEquals("two",m.cache.get("a:b:c"))
    }
    @Test fun deleteRemovesCommittedChunksAndLegacy() = runTest {
        val m=Memory();m.cache.put("stock","a catalogue");m.rows["stock"]="legacy";m.cache.delete("stock")
        assertNull(m.cache.get("stock"));assertTrue(m.rows.isEmpty())
    }
    @Test fun oversizedWriteDoesNotDestroySavedCopy() = runTest {
        val m=Memory();m.cache.put("stock","old");assertFailsWith<IllegalArgumentException> { m.cache.put("stock","x".repeat(1025)) };assertEquals("old",m.cache.get("stock"))
    }
    @Test fun anotherCacheKeyDoesNotWaitForAnUnrelatedWrite() = runTest {
        val rows=mutableMapOf<String,String>();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var sequence=0
        val cache=ChunkedTextCache(read={ k,_ -> rows[k] },write={ k,v -> if(k.contains(":slow:") && k.endsWith(":0")){ entered.complete(Unit);release.await() };rows[k]=v },
            remove={ rows.remove(it);Unit },removePrefixExcept={ _,_-> },newGeneration={ (++sequence).toString(16) },chunkSize=8,maxLength=1024)
        val slow=launch { cache.put("slow","slow") };entered.await()
        try { withTimeout(1000) { cache.put("fast","fast");assertEquals("fast",cache.get("fast")) } }
        finally { release.complete(Unit);slow.join() }
    }
}
