package kz.aita

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AitaLatestUiRequestOwnerTest {
    @Test
    fun onlyNewestTicketMayPublish() {
        val owner = AitaLatestUiRequestOwner()
        val old = owner.begin()
        val current = owner.begin()

        assertFalse(owner.owns(old))
        assertTrue(owner.owns(current))
    }

    @Test
    fun invalidationRejectsTheCurrentTicket() {
        val owner = AitaLatestUiRequestOwner()
        val ticket = owner.begin()
        owner.invalidate()
        assertFalse(owner.owns(ticket))
    }
}
