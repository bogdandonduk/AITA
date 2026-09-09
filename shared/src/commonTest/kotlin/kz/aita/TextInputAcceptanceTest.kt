package kz.aita

import kotlin.test.*

class TextInputAcceptanceTest {
    @Test fun rejectedEditCannotChangeSubmittedTextBehindVisibleText() {
        var displayed = "7771234567"
        var submitted = displayed
        var callbacks = 0
        val accepted = dispatchAcceptedTextInput("letters", { it.all(Char::isDigit) }, { text, apply ->
            callbacks++; submitted = text; apply()
        }) { displayed = "letters" }
        assertFalse(accepted)
        assertEquals(0, callbacks)
        assertEquals("7771234567", displayed)
        assertEquals(displayed, submitted)
    }

    @Test fun acceptedEditNotifiesOwnerAndAppliesExactlyOnce() {
        var submitted = ""
        var applications = 0
        assertTrue(dispatchAcceptedTextInput("123", { it.length <= 10 }, { text, apply ->
            submitted = text; apply()
        }) { applications++ })
        assertEquals("123", submitted)
        assertEquals(1, applications)
    }

    @Test fun ownerMayDeclineAnOtherwiseValidEditWhileBusy() {
        var applications = 0
        assertTrue(dispatchAcceptedTextInput("123", null, { _, _ -> }) { applications++ })
        assertEquals(0, applications)
    }

    @Test fun clearingAndFieldsWithoutCallbackStillWork() {
        var text = "password"
        assertTrue(dispatchAcceptedTextInput("", { it.all(Char::isDigit) }, null) { text = "" })
        assertEquals("", text)
    }

    @Test fun overlongPasteDoesNotDisableAnOtherwiseValidForm() {
        var visible = "7771234567"
        var form = visible
        assertFalse(dispatchAcceptedTextInput("777123456789012345", { it.length <= 10 }, { next, apply ->
            form = next; apply()
        }) { visible = "777123456789012345" })
        assertEquals(form, visible)
        assertEquals(10, form.length)
    }
}
