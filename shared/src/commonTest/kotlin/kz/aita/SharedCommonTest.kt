package kz.aita

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun resolvedAddressUsesLocalizedSnapshotsAndCoordinatesAsIdentity() {
        val location = LocationDataModel(
            name = "Astana, Dostyk Street, 1",
            latitude = 51.1282,
            longitude = 71.4304,
            provider = AITA_ADDRESS_PROVIDER_YANDEX,
            providerObjectId = "ymapsbm1://geo?data=test",
            countryCode = "KZ",
            primaryLanguage = "ru",
            localizedNames = listOf(
                LocalizedStringDataModel("en", "Dostyk Street, 1"),
                LocalizedStringDataModel("ru", "улица Достык, 1"),
                LocalizedStringDataModel("kk", "Достық көшесі, 1")
            ),
            localizedAddresses = listOf(
                LocalizedStringDataModel("main", "Казахстан, Астана, улица Достык, 1"),
                LocalizedStringDataModel("en", "Kazakhstan, Astana, Dostyk Street, 1"),
                LocalizedStringDataModel("ru", "Казахстан, Астана, улица Достык, 1"),
                LocalizedStringDataModel("kk", "Қазақстан, Астана, Достық көшесі, 1")
            ),
            fallbackAddress = "Казахстан, Астана, улица Достык, 1",
            resolvedAtMillis = 100L,
            lastCheckedAtMillis = 200L,
            providerRevision = "revision"
        )

        assertTrue(location.isResolvedAddress())
        assertEquals("Kazakhstan, Astana, Dostyk Street, 1", location.displayAddress("en"))
        assertEquals("Қазақстан, Астана, Достық көшесі, 1", location.displayAddress("kk"))
        assertTrue(location.matchesDisplayedAddress("  Kazakhstan,   Astana, Dostyk Street, 1 ", "en"))
        assertFalse(location.matchesDisplayedAddress("Astana, somewhere else", "en"))
    }

    @Test
    fun legacyTextOnlyLocationRemainsDisplayableButCannotBeSavedAsVerified() {
        val legacy = LocationDataModel(
            name = "Legacy address text",
            postalIndex = "010000",
            latitude = 0.0,
            longitude = 0.0
        )

        assertEquals("Legacy address text", legacy.displayAddress("en"))
        assertFalse(legacy.isResolvedAddress())
        assertFalse(legacy.hasValidCoordinates())
    }

    @Test
    fun bootstrapDecoderAcceptsPrimaryPairAndMixedCandidateShapes() {
        val decoded = decodeBootstrapServerUrlCandidates(
            """
            {
              "serverUrl": {
                "first": "https://aita-api.bogdan-dond.uk.workers.dev/",
                "second": "1"
              },
              "serverCandidates": [
                {
                  "url": "https://aita-api.bogdan-dond.uk.workers.dev",
                  "priority": 100,
                  "supportsRealtime": true,
                  "role": "primary-domainless"
                },
                "https://api.aita.kz/",
                {
                  "serverUrl": "https://secondary.example.com/healthz"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "https://aita-api.bogdan-dond.uk.workers.dev",
                "https://api.aita.kz",
                "https://secondary.example.com"
            ),
            decoded
        )
    }

    @Test
    fun bootstrapDecoderAcceptsJsonEncodedPayloadCandidates() {
        val decoded = decodeBootstrapServerUrlCandidates(
            """
            {
              "message": null,
              "payload": "{\"serverCandidates\":[\"https://one.example\",{\"url\":\"https://two.example/config/global\"}]}"
            }
            """.trimIndent()
        )

        assertEquals(
            listOf("https://one.example", "https://two.example"),
            decoded
        )
    }

    @Test
    fun serverPublishedLegacyAliasCannotDisplaceAnchoredPrimary() {
        val primary = Pair("https://aita-api.bogdan-dond.uk.workers.dev", "1")
        val legacy = Pair("https://api.aita.kz", "2")

        assertEquals(primary, chooseClientServerUrlPair(primary, legacy))
        assertEquals(legacy, chooseClientServerUrlPair(Pair("", "1"), legacy))
    }

    @Test
    fun onlyCloudDataEndpointsRequireAuthenticatedSessionGate() {
        assertTrue(cloudEndpointRequiresAuthentication("auth/session"))
        assertTrue(cloudEndpointRequiresAuthentication("stock/add"))
        assertTrue(cloudEndpointRequiresAuthentication("notifications/add"))
        assertFalse(cloudEndpointRequiresAuthentication("auth/ping"))
        assertFalse(cloudEndpointRequiresAuthentication("auth/refresh"))
        assertFalse(cloudEndpointRequiresAuthentication("config/global"))
        assertFalse(cloudEndpointRequiresAuthentication("res/string"))
        assertFalse(cloudEndpointRequiresAuthentication("readyz"))
    }

    @Test
    fun expiredCloudSessionMessageIsActionableAndPreservesLocalDataMeaning() {
        val message = cloudSessionExpiredMessage()
        assertTrue(message.any { it.language == "en" && "Sign in again" in it.value && "local data" in it.value })
        assertTrue(message.any { it.language == "ru" && "Войдите снова" in it.value && "Локальные данные" in it.value })
        assertTrue(message.any { it.language == "kk" && "қайта кіріңіз" in it.value && "Жергілікті деректер" in it.value })
    }

    @Test
    fun onlyReadOnlyHttpMethodsMayFailOverAcrossAliases() {
        assertTrue(HttpMethod.Get.canRetryAcrossAitaServerAliases())
        assertTrue(HttpMethod.Head.canRetryAcrossAitaServerAliases())
        assertTrue(HttpMethod.Options.canRetryAcrossAitaServerAliases())
        assertFalse(HttpMethod.Post.canRetryAcrossAitaServerAliases())
        assertFalse(HttpMethod.Put.canRetryAcrossAitaServerAliases())
        assertFalse(HttpMethod.Patch.canRetryAcrossAitaServerAliases())
        assertFalse(HttpMethod.Delete.canRetryAcrossAitaServerAliases())
    }

    @Test
    fun aliasRetryRequiresReadOnlyMethodAndTransportLikeFailure() {
        assertTrue(
            shouldRetryNetworkRequestOnNextServerUrl(
                method = HttpMethod.Get,
                endpointUrl = "healthz",
                status = HttpStatusCode.ServiceUnavailable,
                rawBody = "{\"transportFailure\":true}",
                aitaServerResponse = true
            )
        )
        assertTrue(
            shouldRetryNetworkRequestOnNextServerUrl(
                method = HttpMethod.Get,
                endpointUrl = "config/global",
                status = HttpStatusCode.OK,
                rawBody = "<html>not AITA</html>",
                aitaServerResponse = false
            )
        )
        assertFalse(
            shouldRetryNetworkRequestOnNextServerUrl(
                method = HttpMethod.Post,
                endpointUrl = "transactions/add",
                status = HttpStatusCode.ServiceUnavailable,
                rawBody = "{\"transportFailure\":true}",
                aitaServerResponse = true
            )
        )
        assertFalse(
            shouldRetryNetworkRequestOnNextServerUrl(
                method = HttpMethod.Get,
                endpointUrl = "user/get",
                status = HttpStatusCode.Unauthorized,
                rawBody = "{\"negative\":true}",
                aitaServerResponse = true
            )
        )
    }
}
