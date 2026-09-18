package kz.aita

import kotlin.test.*

class VoiceFieldSessionTest {
    @Test fun anEarlierRecordingCannotFinishOrReplaceTheNextRecording() {
        val session = VoiceFieldSession { 1L to "store-a" }
        val first = session.begin()
        val second = session.begin()
        assertFalse(first())
        assertTrue(second())
        session.cancel()
        assertFalse(second())
    }

    @Test fun switchingStoreOrSigningInAgainRejectsDelayedSpeech() {
        var generation = 1L
        var store: String? = "store-a"
        val session = VoiceFieldSession { generation to store }
        val first = session.begin()
        store = "store-b"
        assertFalse(first())
        val second = session.begin()
        generation++
        assertFalse(second())
        store = null // Anonymous and account-only fields still support voice input.
        assertTrue(session.begin()())
    }
}
