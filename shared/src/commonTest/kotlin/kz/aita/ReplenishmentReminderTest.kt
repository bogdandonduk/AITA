package kz.aita

import kotlin.test.*
import kotlin.time.Instant

class ReplenishmentReminderTest {
    private val now = Instant.parse("2026-11-06T05:00:00Z").toEpochMilliseconds()

    @Test
    fun usesLocalMorningAndDeliveryLeadTime() {
        val settings =
            ReplenishmentSettings(event = "new_year", arrivalDate = "2027-01-01", leadDays = 7)
        assertEquals(
            Instant.parse("2026-12-22T04:00:00Z").toEpochMilliseconds(),
            replenishmentReminderDue(settings, "Asia/Almaty", 3, now),
        )
    }

    @Test
    fun alreadyDueReminderRunsSoonWithoutBackdatingIt() {
        val settings =
            ReplenishmentSettings(event = "custom", arrivalDate = "2026-11-07", leadDays = 7)
        assertEquals(now, replenishmentReminderDue(settings, "Asia/Almaty", 3, now))
    }

    @Test
    fun localMorningRespectsDaylightSavingRatherThanFixedOffsets() {
        val before = Instant.parse("2026-03-27T12:00:00Z").toEpochMilliseconds()
        val settings =
            ReplenishmentSettings(event = "custom", arrivalDate = "2026-03-29", leadDays = 0)
        assertEquals(
            Instant.parse("2026-03-29T07:00:00Z").toEpochMilliseconds(),
            replenishmentReminderDue(settings, "Europe/Berlin", 0, before),
        )
    }

    @Test
    fun crossDeviceStatusShowsTheSavedZoneInsteadOfTheViewersClock() {
        val reminder =
            ReplenishmentReminder(
                storeId = "test",
                timeZoneId = "Asia/Almaty",
                dueAtMillis = Instant.parse("2026-12-22T04:00:00Z").toEpochMilliseconds(),
            )
        assertEquals("2026-12-22 09:00", reminder.localDueTime())
        assertEquals(
            "2026-12-21 23:00",
            reminder.copy(timeZoneId = "America/New_York").localDueTime(),
        )
        assertNull(reminder.copy(dueAtMillis = null).localDueTime())
        assertNull(reminder.copy(timeZoneId = "Mars/Unknown").localDueTime())
    }

    @Test
    fun invalidUndatedPastAndExcessiveSchedulesAreRejected() {
        val valid = ReplenishmentSettings(event = "custom", arrivalDate = "2027-01-01")
        for (settings in
            listOf(
                ReplenishmentSettings(),
                valid.copy(arrivalDate = ""),
                valid.copy(arrivalDate = "2026-11-05"),
                valid.copy(arrivalDate = "2030-01-01"),
            )) assertFailsWith<IllegalArgumentException> {
            replenishmentReminderDue(settings, "Asia/Almaty", 3, now)
        }
        for (days in listOf(-1, 31)) assertFailsWith<IllegalArgumentException> {
            replenishmentReminderDue(valid, "Asia/Almaty", days, now)
        }
        assertFailsWith<IllegalArgumentException> {
            replenishmentReminderDue(valid, "Mars/Unknown", 3, now)
        }
    }

    @Test
    fun reminderEventsAndInterfaceHaveAllAppLanguages() {
        for (language in listOf("en", "ru", "kk", "ky", "tg", "uz")) {
            val message =
                EventMessages.render(
                    EventMessageReference(
                        "purchasing.reminder.body",
                        arguments = mapOf("store" to "Test", "date" to "2027-01-01"),
                    ),
                    language,
                )
            assertNotNull(message)
            assertContains(message, "Test")
            assertContains(message, "2027-01-01")
            assertFalse(message.contains('{'))
            for (key in
                listOf(
                    "reminder_title",
                    "reminder_help",
                    "reminder_invalid",
                    "reminder_conflict",
                    "reminder_limit",
                    "reminder_pending",
                )) assertNotEquals(key, replenishmentText(key, language))
        }
    }
}
