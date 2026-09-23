package kz.aita

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ClientDownloadRecoveryTest {
    private val bytes = ByteArray(900_001) { (it % 251).toByte() }
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun connection(data: ByteArray, range: String? = null) = object : HttpURLConnection(URL("https://updates.example.org/app.exe")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getInputStream(): InputStream = ByteArrayInputStream(data)
        override fun getHeaderField(name: String?) = if (name == "Content-Range") range else null
    }
    private fun scenario(body: suspend (java.io.File) -> Unit) = runBlocking {
        val file = Files.createTempFile("aita-resume-", ".part").toFile()
        try { body(file) } finally { file.delete() }
    }
    @Test fun recoversFromHalfDownloadUsingValidatedRangeWithoutLosingProgress() = scenario { file ->
        val offsets = ArrayList<Long>(); val progress = ArrayList<Long>()
        transferVerifiedClientFile(file, "https://updates.example.org/app.exe", bytes.size.toLong(), hash,
            { done, _ -> progress += done }, { _, offset ->
                offsets += offset
                if (offset == 0L) connection(bytes.copyOf(450_000))
                else connection(bytes.copyOfRange(offset.toInt(), bytes.size), "bytes $offset-${bytes.lastIndex}/${bytes.size}")
            }, {})
        assertEquals(listOf(0L, 450_000L), offsets)
        assertContentEquals(bytes, file.readBytes())
        assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
    }
    @Test fun serverIgnoringRangeRestartsInsteadOfAppendingDuplicateBytes() = scenario { file ->
        var calls = 0
        transferVerifiedClientFile(file, "https://updates.example.org/app.exe", bytes.size.toLong(), hash, { _, _ -> }, { _, _ ->
            connection(if (calls++ == 0) bytes.copyOf(450_000) else bytes)
        }, {})
        assertEquals(2, calls); assertContentEquals(bytes, file.readBytes())
    }
    @Test fun wrongResumeOffsetOrTotalFailsClosed() = scenario { file ->
        for (range in listOf("bytes 0-${bytes.lastIndex}/${bytes.size}", "bytes 450000-${bytes.lastIndex}/999999")) {
            var calls = 0
            val failure = assertFailsWith<ClientUpdateFailure> {
                transferVerifiedClientFile(file, "https://updates.example.org/app.exe", bytes.size.toLong(), hash, { _, _ -> }, { _, _ ->
                    connection(if (calls++ == 0) bytes.copyOf(450_000) else bytes.copyOfRange(450_000, bytes.size), if (calls == 1) null else range)
                }, {})
            }
            assertEquals("integrity", failure.reason); assertEquals(2, calls)
        }
    }
    @Test fun exhaustedNetworkRetriesAreNotReportedAsBadSignature() = scenario { file ->
        var calls = 0
        val failure = assertFailsWith<ClientUpdateFailure> {
            transferVerifiedClientFile(file, "https://updates.example.org/app.exe", bytes.size.toLong(), hash, { _, _ -> }, { _, _ ->
                calls++; throw IOException("connection reset")
            }, {})
        }
        assertEquals("network", failure.reason); assertEquals(4, calls)
    }
    @Test fun cancellationAndCorruptionNeverRetryOrPublishSuccess() = scenario { file ->
        var calls = 0
        assertFailsWith<CancellationException> {
            transferVerifiedClientFile(file, "https://updates.example.org/app.exe", bytes.size.toLong(), hash, { _, _ -> }, { _, _ ->
                calls++; throw CancellationException("cancelled")
            }, {})
        }
        assertEquals(1, calls); calls = 0
        assertEquals("integrity", assertFailsWith<ClientUpdateFailure> {
            transferVerifiedClientFile(file, "https://updates.example.org/app.exe", bytes.size.toLong(), hash, { _, _ -> }, { _, _ ->
                calls++; connection(ByteArray(bytes.size))
            }, {})
        }.reason)
        assertEquals(1, calls)
    }
}
