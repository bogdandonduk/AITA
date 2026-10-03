package kz.aita

import kotlin.test.*

class NotificationDestinationTest {
    private val update = NotificationDataModel("", NotificationType.Positive,
        messageTemplate = EventMessageReference("updates.notification", mapOf("version" to "1.2.4")))
    @Test fun guestUpdateOpensPublicDownloadsAndNeverAccountMenu() {
        assertEquals(NotificationDestination.GUEST_DOWNLOADS, notificationDestination(update, false))
        assertEquals(NotificationDestination.UPDATE, notificationDestination(update, true))
    }
    @Test fun guestBusinessNotificationCannotEnterAccountHistory() {
        val normal = NotificationDataModel("anything", NotificationType.Neutral)
        assertEquals(NotificationDestination.DISMISS, notificationDestination(normal, false))
        assertEquals(NotificationDestination.HISTORY, notificationDestination(normal, true))
    }
    @Test fun storeLinksDoNotInventUnpublishedApplicationIds() {
        listOf("android", "windows", "linux", "macos", "ios").forEach {
            assertTrue(platformStoreLink(it)!!.url.startsWith("https://"))
        }
        assertNull(platformStoreLink("web"))
    }
}
