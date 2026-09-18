package kz.aita

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kz.aita.updates.ClientDownloadFile
import kotlin.test.*

class ClientDownloadTransferTest {
    private val bytes = "downloadable fixture".toByteArray()
    private val file = ClientDownloadFile("ANDROID", kind = "AAB", url = "https://updates.example.org/file.aab", bytes = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
    private fun connection(content: ByteArray) = object : HttpURLConnection(URL(file.url)) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getInputStream() = ByteArrayInputStream(content)
        override fun getHeaderField(name: String?) = null
    }
    @Test fun onlyCompleteHashVerifiedBytesAreReturnedForSaving() = runBlocking {
        val root = Files.createTempDirectory("aita-download-test-").toFile()
        try {
            val saved = fetchVerifiedClientDownload(root, file, { _, _ -> }) { connection(bytes) }
            assertContentEquals(bytes, saved.readBytes())
            assertEquals(listOf(saved.name), root.list()!!.toList())
        } finally { root.deleteRecursively() }
        Unit
    }
    @Test fun truncatedOversizedAndWrongHashDownloadsLeaveNoFile() = runBlocking {
        val root = Files.createTempDirectory("aita-download-test-").toFile()
        try {
            for (bad in listOf(bytes.dropLast(1).toByteArray(), bytes + 1.toByte(), ByteArray(bytes.size))) {
                assertFailsWith<ClientUpdateFailure> { fetchVerifiedClientDownload(root, file, { _, _ -> }) { connection(bad) } }
                assertTrue(root.list()!!.isEmpty())
            }
        } finally { root.deleteRecursively() }
        Unit
    }
}
