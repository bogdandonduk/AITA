package kz.aita

import kotlin.test.*

class AppResourceCopyTest {
    @Test fun cachedServerCopyUsesCurrentWordingWithoutChangingStoredValues() {
        val old = listOf(LocalizedStringDataModel("en", "Server connected."))
        assertEquals("Server connected", resolveLocalizedResource(1138, "en", old, null))
        assertEquals("Server connected.", old.single().value)
        assertEquals("Can’t reach server. Check Wi-Fi.", resolveLocalizedResource(1140, "en",
            listOf(LocalizedStringDataModel("en", "Can’t reach AITA server. Check Wi-Fi.")), null))
    }
    @Test fun userAuthoredNamesAndNotesStayVerbatim() {
        val name = listOf(LocalizedStringDataModel("en", "AITA server."))
        assertEquals("AITA server.", name.exactLocalizedValue("en"))
    }
    @Test fun cachedEventResourcesAreNormalizedBeforeUserArgumentsAreInserted() {
        assertEquals("Server connected", EventMessages.render(EventMessageReference("resource.1138"), "en") {
            listOf(LocalizedStringDataModel("en", "Server connected."))
        })
        assertEquals("server: AITA server", EventMessages.render(
            EventMessageReference("resource.1140", mapOf("name" to "AITA server")), "en") {
            listOf(LocalizedStringDataModel("en", "AITA server: {name}"))
        })
    }
}
