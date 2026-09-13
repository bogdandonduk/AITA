package kz.aita.server

import kotlinx.serialization.json.*
import kotlin.test.*

class ServerLocalizationTest {
    private fun parsed(text: String) = Json.parseToJsonElement(text)
    private fun merged(primary: String, bundled: String) = mergeConfigurationLanguageValues(parsed(primary), parsed(bundled))

    @Test fun missingLanguageLabelsAreAddedWithoutReplacingOldValues() {
        val result = merged("""[{"language":"en","value":"Light"},{"language":"ru","value":"Светлая"}]""",
            """[{"language":"en","value":"Light"},{"language":"ru","value":"Светлая"},{"language":"tg","value":"Равшан"},{"language":"ky","value":"Жарык"},{"language":"uz","value":"Yorugʻ"}]""").jsonArray
        assertEquals(5, result.size)
        assertEquals("Light", result[0].jsonObject.getValue("value").jsonPrimitive.content)
        assertEquals("Yorugʻ", result.last().jsonObject.getValue("value").jsonPrimitive.content)
    }

    @Test fun customLocalizedLabelsDoNotAcquireTheTranslationOfAnotherMeaning() {
        val primary = """[{"language":"en","value":"My label"},{"language":"ru","value":"Своё"}]"""
        assertEquals(parsed(primary), merged(primary,
            """[{"language":"en","value":"Light"},{"language":"ru","value":"Светлая"},{"language":"uz","value":"Yorugʻ"}]"""))
    }

    @Test fun explicitRegionalLocaleIsPreservedAndBlankLocaleCanBeFilled() {
        val result = merged("""[{"language":"en","value":"Light"},{"language":"UZ_latn_UZ","value":"Custom"},{"language":"tg","value":""}]""",
            """[{"language":"en","value":"Light"},{"language":"uz","value":"Yorugʻ"},{"language":"tg","value":"Равшан"}]""").jsonArray
        assertEquals(3, result.size)
        assertTrue(result.any { it.jsonObject["value"]?.jsonPrimitive?.content == "Custom" })
        assertTrue(result.any { it.jsonObject["value"]?.jsonPrimitive?.content == "Равшан" })
    }

    @Test fun countryIdentityIsLocaleAndCountryListsAreNeverExpanded() {
        val result = merged("""{"countries":[{"locale":"tj","language":"tj","name":[{"language":"en","value":"Tajikistan"}]}]}""",
            """{"countries":[{"locale":"tj","language":"tg","name":[{"language":"en","value":"Tajikistan"},{"language":"tg","value":"Тоҷикистон"}]},{"locale":"uz","language":"uz"}]}""").jsonObject.getValue("countries").jsonArray
        assertEquals(1, result.size)
        assertEquals("tj", result.single().jsonObject.getValue("language").jsonPrimitive.content)
        assertEquals(2, result.single().jsonObject.getValue("name").jsonArray.size)
    }

    @Test fun urlsPricesProviderFlagsAndUnknownKeysRemainOperatorOwned() {
        val primary = """{"serverUrl":{"first":"https://operator.invalid","second":"1"},"unknown":"kept","providers":[{"id":"one","price":123,"enabled":false,"name":[{"language":"en","value":"Plan"}]}]}"""
        val result = merged(primary,
            """{"serverUrl":{"first":"https://bundled.invalid","second":"999"},"providers":[{"id":"one","price":999,"enabled":true,"name":[{"language":"en","value":"Plan"},{"language":"uz","value":"Tarif"}]}]}""").jsonObject
        val original = parsed(primary).jsonObject
        assertEquals(original["serverUrl"], result["serverUrl"])
        assertEquals(original["unknown"], result["unknown"])
        val provider = result.getValue("providers").jsonArray.single().jsonObject
        assertEquals(JsonPrimitive(123), provider["price"])
        assertEquals(JsonPrimitive(false), provider["enabled"])
        assertEquals(2, provider.getValue("name").jsonArray.size)
    }

    @Test fun repeatedEnrichmentIsIdempotent() {
        val bundled = parsed("""[{"language":"en","value":"Light"},{"language":"uz","value":"Yorugʻ"}]""")
        val once = mergeConfigurationLanguageValues(parsed("""[{"language":"en","value":"Light"}]"""), bundled)
        assertEquals(once, mergeConfigurationLanguageValues(once, bundled))
    }

    @Test fun configurationWithNoLanguagesStillExposesAllSixShippedChoices() {
        val result = parsed(enrichGlobalConfigurationLanguages("""{"payload":{"languages":[],"unknown":"kept"}}""", null)).jsonObject
        val payload = result.getValue("payload").jsonObject
        assertEquals("kept", payload.getValue("unknown").jsonPrimitive.content)
        assertEquals(setOf("en","ru","kk","tg","ky","uz"), payload.getValue("languages").jsonArray.map {
            it.jsonObject.getValue("language").jsonPrimitive.content }.toSet())
    }

    @Test fun actualPackagedResponsesCompleteOldCataloguesWithoutOverwritingCustomText() {
        val root = javaClass.getResourceAsStream("/config/app/responses.json")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val rows = kz.aita.jsonBase.decodeFromString(kotlinx.serialization.builtins.ListSerializer(kz.aita.RemoteResponseDataModel.serializer()), root)
        for (row in rows) {
            val old = row.copy(message = row.message.filterNot { it.language in setOf("tg", "ky", "uz") })
            val complete = completeResponseLanguages(old)
            assertTrue(complete.message.map { it.language }.containsAll(listOf("tg","ky","uz")))
            for (value in old.message) assertTrue(value in complete.message)
        }
        val custom = rows.first().copy(message = listOf(kz.aita.LocalizedStringDataModel("en", "Operator-defined text")))
        assertEquals(custom, completeResponseLanguages(custom))
    }
}
