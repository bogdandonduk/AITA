package kz.aita

import kotlin.test.*

class UsbReceiptPrinterPolicyTest {
    private val serial = "a".repeat(64)
    private val attachment = "b".repeat(32)
    private fun intf(id: Int = 0, alternate: Int = 0, clazz: Int = 7, protocol: Int = 2,
        endpoints: List<UsbPrintEndpoint> = listOf(UsbPrintEndpoint(1, 0, 2))) =
        UsbPrintInterface(id, alternate, clazz, 1, protocol, endpoints)
    @Test fun serialAndAttachmentIdentitiesRoundTripWithoutExposingSerialText() {
        for (value in listOf(UsbPrinterIdentity(1046, 20497, 0, 0, true, serial), UsbPrinterIdentity(1046, 20497, 1, 2, false, attachment)))
            assertEquals(value, parseUsbPrinterIdentity(value.encode()))
    }
    @Test fun malformedTargetsAreNeverInterpretedAsBluetooth() {
        for (raw in listOf("", "usb:", "USB:v1:1:2:0:0:s:$serial", "usb:v1:-1:2:0:0:s:$serial",
            "usb:v1:65536:2:0:0:s:$serial", "usb:v1:1:2:256:0:s:$serial", "usb:v1:1:2:0:0:s:$attachment",
            "usb:v1:1:2:0:0:a:$serial", "usb:v1:1:2:0:0:x:$serial", "usb:v1:1:2:0:0:s:${"G".repeat(64)}")) {
            assertFailsWith<IllegalArgumentException>(raw) { parseUsbPrinterIdentity(raw) }
        }
        assertEquals("usb", receiptPrinterTransport("USB:invalid"))
        assertEquals("bluetooth", receiptPrinterTransport("aa:bb:cc:dd:ee:ff"))
    }
    @Test fun unrelatedUsbInterfacesAndUnsupportedProtocolsAreRejected() {
        for (clazz in listOf(0, 2, 3, 8, 10, 255)) assertFalse(supportedUsbPrintInterface(intf(clazz = clazz)))
        for (protocol in listOf(0, 3, 255)) assertFalse(supportedUsbPrintInterface(intf(protocol = protocol)))
        assertNull(chooseUsbPrintInterface(listOf(intf(clazz = 8), intf(clazz = 3))))
    }
    @Test fun outputMustBeBulkAndNotEndpointZeroOrInput() {
        for (endpoint in listOf(UsbPrintEndpoint(0, 0, 2), UsbPrintEndpoint(129, 128, 2), UsbPrintEndpoint(1, 0, 3)))
            assertFalse(supportedUsbPrintInterface(intf(endpoints = listOf(endpoint))))
        assertTrue(supportedUsbPrintInterface(intf(protocol = 1)))
        assertTrue(supportedUsbPrintInterface(intf(protocol = 2)))
    }
    @Test fun defaultAlternateIsPreferredWithoutAssumingInterfaceIndexEqualsId() {
        val chosen = chooseUsbPrintInterface(listOf(intf(id = 4, alternate = 1), intf(clazz = 8), intf(id = 7)))
        assertEquals(7, chosen?.id); assertEquals(0, chosen?.alternate)
    }
    @Test fun connectionLabelsRemainSpecificToTheConfiguredTransport() {
        assertEquals("system", receiptPrinterTransport("print-service:XP-58"))
        assertEquals("network", receiptPrinterTransport("tcp://192.168.1.20:9100"))
        assertEquals("serial", receiptPrinterTransport("serial:COM3"))
        assertEquals("wired", receiptPrinterTransport("/dev/usb/lp0"))
    }
    @Test fun newUiMessagesHaveExactTranslationsInEverySupportedLanguage() {
        val definitions = checkoutCartMessageTemplates() + printerConnectionMessageTemplates()
        for (definition in definitions) for (language in listOf("en", "ru", "kk", "ky", "tg", "uz")) {
            assertFalse(definition.exactText(language).isNullOrBlank(), "${definition.key}:$language")
            assertNotNull(EventMessages.renderExact(EventMessageReference(definition.key), language))
        }
    }
}
