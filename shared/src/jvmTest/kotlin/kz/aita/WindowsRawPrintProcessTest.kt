package kz.aita

import kotlin.test.*

class WindowsRawPrintProcessTest {
    @Test fun launcherTransportPreservesUnicodeQuotesAndSpacesWithoutDependingOnWindowsCodePage() {
        val name = "Чек ӘҚ & ' receipt printer"
        val path = "C:\\Users\\Әлия\\AITA ' data\\receipt.bin"
        val encoded = encodeWindowsRawPrintArguments(name, path)
        assertTrue(encoded.all { arg -> arg.all { it.code < 128 && !it.isWhitespace() } })
        assertEquals(name to path, decodeWindowsRawPrintArguments(encoded.toTypedArray()))
        assertFailsWith<IllegalArgumentException> { decodeWindowsRawPrintArguments(arrayOf("bad")) }
        assertFailsWith<IllegalArgumentException> { decodeWindowsRawPrintArguments(arrayOf("%", "%")) }
    }
    @Test fun actualWindowsApiRejectsAnUnknownQueueBeforeAnyPageIsSubmitted() {
        org.junit.Assume.assumeTrue("Native spooler is exercised on Windows", System.getProperty("os.name").contains("Windows", true))
        val folder = java.nio.file.Files.createTempDirectory("AITA printer Кириллица '").toFile()
        try {
            val file = java.io.File(folder, "receipt.raw").apply { writeBytes(byteArrayOf(27, 64)) }
            val events = mutableListOf<String>()
            val failure = assertFailsWith<WindowsSpoolFailure> {
                executeWindowsRawPrintRequest(encodeWindowsRawPrintArguments("AITA-missing-ӘҚ-" + java.util.UUID.randomUUID(), file.absolutePath).toTypedArray(), events::add)
            }
            assertEquals("OpenPrinter", failure.operation); assertEquals(1801, failure.code)
            assertTrue(events.isEmpty())
        } finally { folder.deleteRecursively() }
    }
    private class Spool : WindowsRawSpoolApi {
        val calls = mutableListOf<String>()
        val data = mutableListOf<Byte>()
        var partial = 3
        var fail: String? = null
        private fun call(name: String) { calls += name; if (fail == name) throw WindowsSpoolFailure(name, 5) }
        override fun open(name: String) = call("open")
        override fun startDocument(): Int { call("start"); return 238 }
        override fun startPage() = call("page")
        override fun write(bytes: ByteArray, offset: Int, count: Int): Int {
            call("write")
            val n = minOf(count, partial)
            if (n > 0) data += bytes.slice(offset until offset + n)
            return n
        }
        override fun endPage() = call("endPage")
        override fun endDocument() = call("end")
        override fun abort() = call("abort")
        override fun close() = call("close")
    }
    @Test fun positivePartialWritesDeliverExactlyOneCompleteJob() {
        val api = Spool(); val events = mutableListOf<String>(); val bytes = ByteArray(30) { it.toByte() }
        submitWindowsRawReceipt(api, "A queue with Кириллица & quotes", bytes, events::add)
        assertContentEquals(bytes, api.data.toByteArray())
        assertEquals(1, api.calls.count { it == "start" }); assertFalse("abort" in api.calls)
        assertEquals("close", api.calls.last()); assertEquals("AITA_PRINT_OK:job=238;bytes=30", events.last())
    }
    @Test fun partialFailureIsAbortedWithoutCompletingOrReplayingJob() {
        for (failure in listOf("page", "write", "endPage", "end")) {
            val api = Spool().apply { fail = failure }; val events = mutableListOf<String>()
            val error = assertFailsWith<WindowsSpoolFailure> { submitWindowsRawReceipt(api, "Queue", byteArrayOf(1,2,3), events::add) }
            assertEquals(failure, error.operation); assertEquals(5, error.code)
            assertTrue("abort" in api.calls); assertEquals("close", api.calls.last())
            assertEquals(1, api.calls.count { it == "start" }); assertFalse(events.any { it.startsWith("AITA_PRINT_OK:") })
        }
    }
    @Test fun zeroProgressDoesNotLoopOrSendAnotherJob() {
        val api = Spool().apply { partial = 0 }
        assertFailsWith<IllegalStateException> { submitWindowsRawReceipt(api, "Queue", byteArrayOf(1), {}) }
        assertEquals(1, api.calls.count { it == "write" }); assertTrue("abort" in api.calls)
    }
    @Test fun openFailureIsNeverReportedAsAStartedJob() {
        val api = Spool().apply { fail = "open" }; val events = mutableListOf<String>()
        assertFailsWith<WindowsSpoolFailure> { submitWindowsRawReceipt(api, "Queue", byteArrayOf(1), events::add) }
        assertTrue(events.isEmpty()); assertEquals(listOf("open"), api.calls)
    }
    @Test fun invalidInputCannotReachNativeApi() {
        val api = Spool()
        assertFailsWith<IllegalArgumentException> { submitWindowsRawReceipt(api, "Queue", byteArrayOf(), {}) }
        assertFailsWith<IllegalArgumentException> { submitWindowsRawReceipt(api, "Queue\nOther", byteArrayOf(1), {}) }
        assertTrue(api.calls.isEmpty())
    }
}
