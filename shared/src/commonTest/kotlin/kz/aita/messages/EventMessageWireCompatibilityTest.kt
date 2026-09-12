package kz.aita

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs against the real serialization plugin/runtime in the project's common test graph. */
class EventMessageWireCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class OldNotification(val message: String, val type: NotificationType, val title: String = "")

    @Serializable
    private data class OldLocalizedString(val language: String, val value: String)

    @Test fun textOnlyNotificationStillDecodesWithNoTemplate() {
        val old = """{"id":"old-1","message":"Original message","type":"Positive","createdAtMillis":123}"""
        val decoded = json.decodeFromString<NotificationDataModel>(old)
        assertNull(decoded.messageTemplate)
        assertEquals("Original message", decoded.localizedEventMessage("ru"))
        assertEquals(123L, decoded.createdAtMillis)
    }
    @Test fun referenceAndOriginalFactsSurviveCacheRoundTrip() {
        val reference = EventMessageReference("worker.request.body", children = mapOf(
            "person" to listOf(eventFact("Богдан {person}")), "store" to listOf(eventFact("AITA"))))
        val original = NotificationDataModel("Compatibility text", NotificationType.Neutral,
            id = "saved-2", messageTemplate = reference, createdAtMillis = 456)
        val restored = json.decodeFromString<NotificationDataModel>(json.encodeToString(original))
        assertEquals(original, restored)
        assertTrue(restored.localizedEventMessage("kk").contains("Богдан {person}"))
    }
    @Test fun olderClientCanReadNewNotificationCompatibilityText() {
        val original = NotificationDataModel("Stock item added", NotificationType.Positive,
            messageTemplate = EventMessageReference("message.stock_item_added"))
        val old = json.decodeFromString<OldNotification>(json.encodeToString(original))
        assertEquals("Stock item added", old.message)
        assertEquals(NotificationType.Positive, old.type)
    }
    @Test fun olderLocalizedStringClientIgnoresTheNewDescriptor() {
        val original = EventMessages.localized(EventMessageReference("message.stock_item_added")).first()
        val old = json.decodeFromString<OldLocalizedString>(json.encodeToString(original))
        assertEquals("main", old.language)
        assertEquals("Stock item added", old.value)
    }
    @Test fun oldOperationLogWithoutTemplatesStillDecodes() {
        val old = """{"id":"old-log","action":"updated","entityType":"store","title":[{"language":"en","value":"Store updated"}],"createdAtMillis":789}"""
        val restored = json.decodeFromString<OperationLogDataModel>(old)
        assertNull(restored.titleTemplate)
        assertEquals("Магазин: обновлено", restored.localizedEventTitle("ru"))
        assertEquals(789L, restored.createdAtMillis)
    }
}
