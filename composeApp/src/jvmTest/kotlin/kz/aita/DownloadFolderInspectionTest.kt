package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DownloadFolderInspectionTest {
    @Test fun revokedFolderPermissionDoesNotThrowOrSilentlySelectTheDefault() = runBlocking {
        val saved = "content://documents/tree/primary%3AStoreReleases"
        val result = inspectDownloadFolder(null, false, { saved }) { folder ->
            assertEquals(saved, folder)
            throw SecurityException("The folder permission was revoked")
        }
        assertEquals(saved, result.folder)
        assertTrue(result.loaded)
        assertEquals("storage", result.error)
        assertNull(result.label)
    }

    @Test fun preferenceReadFailureIsRecoverableAndIsNotCachedAsTheDefault() = runBlocking {
        var labelReads = 0
        val failed = inspectDownloadFolder(null, false, { throw IllegalStateException("Storage unavailable") }) {
            labelReads++
            "Default folder"
        }
        assertFalse(failed.loaded)
        assertEquals("storage", failed.error)
        assertEquals(0, labelReads)

        val recovered = inspectDownloadFolder(failed.folder, failed.loaded, { "/chosen/releases" }) { it }
        assertTrue(recovered.loaded)
        assertEquals("/chosen/releases", recovered.folder)
        assertEquals("/chosen/releases", recovered.label)
        assertNull(recovered.error)
    }

    @Test fun inaccessibleLoadedFolderStaysSelectedWithoutRereadingPreferences() = runBlocking {
        val result = inspectDownloadFolder("/unmounted/releases", true, { fail("Already loaded") }) {
            throw IllegalStateException("Mount unavailable")
        }
        assertEquals("/unmounted/releases", result.folder)
        assertTrue(result.loaded)
        assertEquals("storage", result.error)
    }

    @Test fun defaultFolderIsUsedOnlyWhenThePreferenceWasSuccessfullyReadAsEmpty() = runBlocking {
        val result = inspectDownloadFolder(null, false, { null }) {
            assertNull(it)
            "Download/AITA/Releases"
        }
        assertTrue(result.loaded)
        assertNull(result.folder)
        assertNull(result.error)
        assertEquals("Download/AITA/Releases", result.label)
    }

    @Test fun preferenceAndFolderLookupCancellationPropagate() = runBlocking {
        val cancelledRead = CancellationException("Read cancelled")
        assertSame(cancelledRead, assertFailsWith<CancellationException> {
            inspectDownloadFolder(null, false, { throw cancelledRead }) { fail("No lookup after cancellation") }
        })
        val cancelledLookup = CancellationException("Lookup cancelled")
        assertSame(cancelledLookup, assertFailsWith<CancellationException> {
            inspectDownloadFolder("/chosen", true, { fail("Already loaded") }) { throw cancelledLookup }
        })
        Unit
    }
}
