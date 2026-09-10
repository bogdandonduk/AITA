package kz.aita

import kotlin.test.*

class DeviceNotificationPolicyTest {
    private val saved = NotificationDataModel("Saved PDF", NotificationType.Positive, id = "saved", category = "device_file")
    private val warning = NotificationDataModel("Check the transaction", NotificationType.Negative, id = "warning", category = "general")

    @Test fun fileToastDoesNotDisableTheNextSale() {
        assertNull(notificationForBusinessActionGuard(saved, listOf(saved)))
    }
    @Test fun businessWarningUnderTheFileToastRemainsBlocking() {
        assertSame(warning, notificationForBusinessActionGuard(saved, listOf(saved, warning)))
    }
    @Test fun ordinaryNotificationGuardsAreUnchanged() {
        assertSame(warning, notificationForBusinessActionGuard(warning, listOf(saved, warning)))
        assertNull(notificationForBusinessActionGuard(null, emptyList()))
    }
}
