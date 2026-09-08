package kz.aita

import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlin.test.*

class ReceiptPrinterTransportTest {
    private fun isolated(block: suspend () -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("aita-printer-test").toFile()
        val oldDirectory = jvmPersistentDataDirPath
        val oldTarget = ReceiptPlatformJvmBridge.escPosDevicePath
        val oldWriter = ReceiptPlatformJvmBridge.writeEscPosBytes
        try {
            jvmPersistentDataDirPath = directory.absolutePath
            ReceiptPlatformJvmBridge.escPosDevicePath = null
            ReceiptPlatformJvmBridge.writeEscPosBytes = null
            withTimeout(10_000L) { block() }
        } finally {
            ReceiptPlatformJvmBridge.escPosDevicePath = oldTarget
            ReceiptPlatformJvmBridge.writeEscPosBytes = oldWriter
            jvmPersistentDataDirPath = oldDirectory
            directory.deleteRecursively()
        }
    }

    @Test fun tcpSendsExactlyOnceAndNoExtraBytes() = isolated {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        server.soTimeout = 5_000
        try {
            val received = CoroutineScope(currentCoroutineContext()).async(Dispatchers.IO) {
                server.accept().use { peer -> peer.soTimeout = 5_000; peer.getInputStream().readBytes() }
            }
            val bytes = byteArrayOf(27, 64, 72, 105, 10, 29, 86, 1)
            ReceiptPlatformJvmBridge.configureEscPosDevicePath("tcp://127.0.0.1:${server.localPort}")
            assertTrue(ReceiptPlatformJvmBridge.writeEscPosBytesToConfiguredPrinter(bytes))
            assertContentEquals(bytes, received.await())
        } finally { server.close() }
    }

    @Test fun deviceFileDoesNotRequireSerialDiscovery() = isolated {
        val target = Files.createTempFile("aita-printer-device", ".raw").toFile()
        try {
            val bytes = byteArrayOf(27,64,1,2,3)
            ReceiptPlatformJvmBridge.configureEscPosDevicePath(target.absolutePath)
            assertTrue(ReceiptPlatformJvmBridge.writeEscPosBytesToConfiguredPrinter(bytes))
            assertContentEquals(bytes, target.readBytes())
        } finally { target.delete() }
    }

    @Test fun invalidSelectionKeepsPreviousPreference() = isolated {
        ReceiptPlatformJvmBridge.configureEscPosDevicePath("print-service:XP-58 (copy 1)")
        assertFailsWith<IllegalArgumentException> { ReceiptPlatformJvmBridge.configureEscPosDevicePath("tcp://bad:wrong") }
        ReceiptPlatformJvmBridge.escPosDevicePath = null
        ReceiptPlatformJvmBridge.loadPersistedEscPosDevicePath()
        assertEquals("print-service:XP-58 (copy 1)", ReceiptPlatformJvmBridge.escPosDevicePath)
        ReceiptPlatformJvmBridge.configureEscPosDevicePath(null)
        ReceiptPlatformJvmBridge.loadPersistedEscPosDevicePath()
        assertNull(ReceiptPlatformJvmBridge.escPosDevicePath)
    }

    @Test fun overlappingWritesAreRejectedAndByteSnapshotIsStable() = isolated {
        val entered = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        var seen = byteArrayOf()
        ReceiptPlatformJvmBridge.writeEscPosBytes = { bytes ->
            entered.complete(Unit); proceed.await(); seen = bytes.copyOf(); true
        }
        val source = byteArrayOf(1,2,3)
        val first = CoroutineScope(currentCoroutineContext()).async { ReceiptPlatformJvmBridge.writeEscPosBytesToConfiguredPrinter(source) }
        entered.await(); source.fill(9)
        assertFailsWith<IllegalStateException> { ReceiptPlatformJvmBridge.writeEscPosBytesToConfiguredPrinter(byteArrayOf(4)) }
        proceed.complete(Unit); assertTrue(first.await()); assertContentEquals(byteArrayOf(1,2,3), seen)
    }

    @Test fun cancellationReleasesWriterOwnership() = isolated {
        val entered=CompletableDeferred<Unit>()
        ReceiptPlatformJvmBridge.writeEscPosBytes = { entered.complete(Unit); awaitCancellation() }
        val job=CoroutineScope(currentCoroutineContext()).launch { ReceiptPlatformJvmBridge.writeEscPosBytesToConfiguredPrinter(byteArrayOf(1)) }
        entered.await(); job.cancelAndJoin()
        ReceiptPlatformJvmBridge.writeEscPosBytes = { true }
        assertTrue(ReceiptPlatformJvmBridge.writeEscPosBytesToConfiguredPrinter(byteArrayOf(2)))
    }

    @Test fun emptyOrOversizedPayloadDoesNotReachWriter() = isolated {
        var calls=0
        ReceiptPlatformJvmBridge.writeEscPosBytes = { calls++;true }
        assertFailsWith<IllegalStateException> { ReceiptPlatformJvmBridge.writeEscPosBytesToConfiguredPrinter(byteArrayOf()) }
        assertFailsWith<IllegalStateException> { ReceiptPlatformJvmBridge.writeEscPosBytesToConfiguredPrinter(ByteArray(8*1024*1024+1)) }
        assertEquals(0,calls)
    }
}
