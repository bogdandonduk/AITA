package kz.aita

import kotlin.test.*

class MissedNotificationsTest {
    private val event = NotificationDataModel("Remote event", NotificationType.Neutral, id = "remote", userId = "owner", source = "server", isSavedOnServer = true)
    @Test fun localAcknowledgementsOtherAccountsAndReadEventsDoNotIncreaseTheBadge() {
        assertEquals(listOf(event), remoteUnreadNotifications(listOf(event, event,
            event.copy(id = "local", source = "app"), event.copy(id = "other", userId = "another"),
            event.copy(id = "read", readAtMillis = 10), event.copy(id = "unsaved", isSavedOnServer = false),
            event.copy(id = "connection", category = "connection")), "owner", "device-a"))
        assertTrue(remoteUnreadNotifications(listOf(event), null, "device-a").isEmpty())
    }
    @Test fun signInIsVisibleOnlyOnTheOtherDevice() {
        val signIn = event.copy(category = "security", metadata = mapOf("originInstallationId" to "device-b", "actorUserId" to "owner"))
        assertFalse(signIn.isRemoteUnreadNotification("owner", "device-b"))
        assertTrue(signIn.isRemoteUnreadNotification("owner", "device-a"))
        assertTrue(signIn.isRemoteUnreadNotification("owner", ""))
    }
    @Test fun allNewMessagesHaveSixTranslations() {
        for (key in listOf("notifications.missed", "security.signin.title", "security.signin.body", "security.signin.email", "return.quantity_limit"))
            for (language in listOf("en", "ru", "kk", "ky", "tg", "uz"))
                assertFalse(EventMessages.render(EventMessageReference(key, when (key) {
                    "notifications.missed" -> mapOf("count" to "23")
                    "security.signin.body" -> mapOf("device" to "Test")
                    else -> emptyMap()
                }), language).isNullOrBlank(), "$key/$language")
    }
}
