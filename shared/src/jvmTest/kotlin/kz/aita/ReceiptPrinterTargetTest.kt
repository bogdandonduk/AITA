package kz.aita
import kotlin.test.*
class ReceiptPrinterTargetTest {
    @Test fun nativeQueuesDoNotNeedSerialPortDiscovery() {
        assertEquals(ReceiptPrinterTarget.SystemQueue("XP-58 (copy 1)"),parseReceiptPrinterTarget(" print-service:XP-58 (copy 1) "))
        assertEquals(ReceiptPrinterTarget.SystemQueue("Касса 1"),parseReceiptPrinterTarget("PRINT-SERVICE:Касса 1"))
        assertEquals(ReceiptPrinterTarget.LegacyName("XP-58 (copy 1)"),parseReceiptPrinterTarget("XP-58 (copy 1)"))
    }
    @Test fun serialNamesRetainExplicitTransport() {
        assertEquals(ReceiptPrinterTarget.Serial("COM3"),parseReceiptPrinterTarget("serial:COM3"))
        assertEquals(ReceiptPrinterTarget.Serial("COM4"),parseReceiptPrinterTarget("\\\\.\\COM4"))
        assertEquals(ReceiptPrinterTarget.Serial("/dev/ttyUSB0"),parseReceiptPrinterTarget("serial:/dev/ttyUSB0"))
    }
    @Test fun ipv4Ipv6AndDnsTargets() {
        assertEquals(ReceiptPrinterTarget.Tcp("192.168.1.50",9100),parseReceiptPrinterTarget("tcp://192.168.1.50:9100"))
        assertEquals(ReceiptPrinterTarget.Tcp("printer.local",9100),parseReceiptPrinterTarget("tcp:printer.local"))
        assertEquals(ReceiptPrinterTarget.Tcp("2001:db8::1",9101),parseReceiptPrinterTarget("tcp://[2001:db8::1]:9101"))
    }
    @Test fun invalidTcpIsNotSilentlyDefaulted() {
        for (target in listOf("tcp://host:","tcp://host:abc","tcp://host:0","tcp://host:65536","tcp://host:-1","tcp://user:pass@host","tcp://host/path","tcp://host?q=1","tcp://host#x","tcp://")) {
            assertFailsWith<IllegalArgumentException>(target) { parseReceiptPrinterTarget(target) }
        }
    }
    @Test fun rawDeviceFilesRemainSupported() {
        assertEquals(ReceiptPrinterTarget.DeviceFile("/dev/usb/lp0"),parseReceiptPrinterTarget("file:///dev/usb/lp0"))
        assertEquals(ReceiptPrinterTarget.DeviceFile("/dev/usb/lp0"),parseReceiptPrinterTarget("/dev/usb/lp0"))
    }
    @Test fun ambiguousWindowsJobsAreNeverReplayed() {
        assertTrue(windowsRawFallbackIsSafe("AITA_PRINT_SAFE_FAILURE:",false))
        assertFalse(windowsRawFallbackIsSafe("AITA_PRINT_SAFE_FAILURE:",true))
        assertFalse(windowsRawFallbackIsSafe("unknown error",false))
        for (marker in listOf("AITA_PRINT_SUBMITTING:","AITA_PRINT_JOB_STARTED:","AITA_PRINT_OK:")) {
            assertFalse(windowsRawFallbackIsSafe("AITA_PRINT_SAFE_FAILURE:\n$marker",false))
        }
    }
    @Test fun invalidTargetsFailBeforeSavingOrWriting() {
        for (target in listOf("", "print-service:","serial:","bad\nqueue","bad\rqueue","bad\u0000queue")) {
            assertFailsWith<IllegalArgumentException> { parseReceiptPrinterTarget(target) }
        }
    }
}
