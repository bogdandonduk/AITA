package kz.aita

import kotlin.test.*

class BluetoothPrintTransferTest {
    @Test fun slowBoundedReceiverGetsEveryByteIncludingTheFooterBeforeDisconnect() {
        val bytes = ByteArray(70_123) { (it % 251).toByte() }
        val queued = ArrayList<Byte>()
        val received = ArrayList<Byte>()
        var elapsed = 0L
        var flushes = 0
        writePacedBluetoothPrint(bytes, { data, offset, count ->
            assertTrue(queued.size + count <= 1024, "printer receive buffer must not overflow")
            queued.addAll(data.slice(offset until offset + count))
        }, { flushes++ }, { milliseconds ->
            elapsed += milliseconds
            // A 115200-baud adapter takes time to forward accepted Bluetooth bytes.
            repeat(minOf(queued.size, (milliseconds * 11).toInt())) { received += queued.removeAt(0) }
        })
        assertEquals(bytes.toList(), received)
        assertTrue(queued.isEmpty())
        assertEquals((bytes.size + 511) / 512, flushes)
        assertTrue(elapsed < bluetoothPrinterWriteTimeoutMillis(bytes.size))
    }
    @Test fun partialFailureIsNeverReplayed() {
        var writes = 0
        assertFailsWith<IllegalStateException> {
            writePacedBluetoothPrint(ByteArray(4096), { _, _, _ ->
                writes++; if (writes == 2) error("disconnected")
            }, {}, {})
        }
        assertEquals(2, writes)
    }
}
