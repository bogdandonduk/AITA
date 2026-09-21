package kz.aita.server

import kotlinx.serialization.decodeFromString
import kz.aita.*
import kotlin.test.*

class GenericEnvelopeTest {
    @Test fun revocationReasonSurvivesTheActualServerEnvelope() {
        val encoded = aitaGenericEnvelopeText(eventMessage("auth.session_revoked"), null, true)
        val decoded = jsonBase.decodeFromString<GenericResponseDataModel>(encoded)
        assertTrue(decoded.negative)
        assertEquals("auth.session_revoked", decoded.getMessage()?.explicitEventMessageReference()?.key)
    }

    @Test fun nestedReferencesAndEscapedLiteralValuesRoundTrip() {
        val reference = EventMessageReference("event.join", children = mapOf("parts" to listOf(
            eventMessageReference("event.literal", "value" to "Quoted \"text\"\nC:\\path\t₸")
        )))
        val message = listOf(LocalizedStringDataModel("en", "Text\n\"quoted\"", reference))
        val decoded = jsonBase.decodeFromString<GenericResponseDataModel>(aitaGenericEnvelopeText(message, null, false))
        assertEquals(message, decoded.getMessage())
        assertEquals(reference, decoded.getMessage()?.explicitEventMessageReference())
    }
}
