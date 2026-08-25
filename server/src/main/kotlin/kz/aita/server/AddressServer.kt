package kz.aita.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kz.aita.AITA_ADDRESS_PROVIDER_YANDEX
import kz.aita.AddressMapPreviewDataModel
import kz.aita.AddressSuggestionDataModel
import kz.aita.LocalizedStringDataModel
import kz.aita.LocationDataModel
import kz.aita.hasValidCoordinates
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal class AddressProviderException(
  message: String,
  cause: Throwable? = null,
  val retryable: Boolean = true,
  val clientFault: Boolean = false
) : IllegalStateException(message, cause)

internal data class SignedAddressMapRequest(
  val latitude: Double,
  val longitude: Double,
  val expiresAtMillis: Long,
  val width: Int,
  val height: Int,
  val zoom: Int,
  val language: String,
  val darkTheme: Boolean,
  val signature: String
)

internal object YandexAddressService {
  private const val GEOSUGGEST_URL = "https://suggest-maps.yandex.ru/v1/suggest"
  private const val GEOCODER_URL = "https://geocode-maps.yandex.ru/v1/"
  private const val STATIC_MAP_URL = "https://static-maps.yandex.ru/v1"
  private const val MAP_URL_TTL_MILLIS = 15L * 60L * 1000L
  private const val MAX_RESPONSE_BYTES = 2_000_000

  private val httpClient: HttpClient by lazy {
    HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build()
  }

  private fun env(name: String): String? =
    System.getenv(name)?.trim()?.takeIf(String::isNotEmpty)
      ?: System.getProperty(name)?.trim()?.takeIf(String::isNotEmpty)

  private fun usableCredential(value: String?, minimumLength: Int = 8): String? {
    val clean = value?.trim()?.takeIf { it.length >= minimumLength } ?: return null
    val lower = clean.lowercase(Locale.ROOT)
    if (listOf("change_me", "changeme", "replace_with", "placeholder", "your_key", "your_secret")
        .any(lower::contains)) return null
    return clean
  }

  private val sharedMapsKey: String?
    get() = usableCredential(env("AITA_YANDEX_MAPS_API_KEY"))

  private val geosuggestKey: String?
    get() = usableCredential(env("AITA_YANDEX_GEOSUGGEST_API_KEY")) ?: sharedMapsKey

  private val geocoderKey: String?
    get() = usableCredential(env("AITA_YANDEX_GEOCODER_API_KEY")) ?: sharedMapsKey

  private val staticMapsKey: String?
    get() = usableCredential(env("AITA_YANDEX_STATIC_MAPS_API_KEY")) ?: sharedMapsKey

  private val mapSigningSecret: String?
    get() = usableCredential(env("AITA_ADDRESS_MAP_SIGNING_SECRET"), minimumLength = 32)

  val suggestionsConfigured: Boolean
    get() = !geosuggestKey.isNullOrBlank()

  val geocoderConfigured: Boolean
    get() = !geocoderKey.isNullOrBlank()

  val staticMapsConfigured: Boolean
    get() = !staticMapsKey.isNullOrBlank() && !mapSigningSecret.isNullOrBlank()

  fun configurationSummary(): String =
    "suggestions=$suggestionsConfigured geocoder=$geocoderConfigured staticMaps=$staticMapsConfigured"

  private fun normalizedLanguage(language: String?): String = when (language?.trim()?.lowercase(Locale.ROOT)) {
    "en" -> "en"
    "kk", "kz" -> "kk"
    else -> "ru"
  }

  private fun suggestLanguage(language: String): String = normalizedLanguage(language)

  private fun geocoderLanguage(language: String): String = when (normalizedLanguage(language)) {
    "en" -> "en_RU"
    "kk" -> "ru_RU"
    else -> "ru_RU"
  }

  private fun staticMapLanguage(language: String): String = when (normalizedLanguage(language)) {
    "en" -> "en_US"
    "kk" -> "ru_RU"
    else -> "ru_RU"
  }

  private fun encode(value: Any?): String = URLEncoder.encode(
    value?.toString().orEmpty(),
    StandardCharsets.UTF_8
  ).replace("+", "%20")

  private fun url(base: String, params: List<Pair<String, Any?>>): String =
    base + "?" + params
      .filter { (_, value) -> value != null && value.toString().isNotBlank() }
      .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

  private suspend fun getBytes(url: String, accept: String): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
    val request = HttpRequest.newBuilder(URI.create(url))
      .timeout(Duration.ofSeconds(18))
      .header("Accept", accept)
      .header("User-Agent", "AITA-Server/1.0")
      .GET()
      .build()

    try {
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
      val body = response.body()
      if (body.size > MAX_RESPONSE_BYTES) {
        throw AddressProviderException("Yandex Maps response was unexpectedly large")
      }
      response.statusCode() to body
    } catch (throwable: AddressProviderException) {
      throw throwable
    } catch (throwable: Throwable) {
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
      throw AddressProviderException(
        message = "Could not reach Yandex Maps: ${throwable.message.orEmpty()}",
        cause = throwable
      )
    }
  }

  private suspend fun getJson(url: String): JsonObject {
    val (status, bytes) = getBytes(url, "application/json")
    val text = bytes.toString(StandardCharsets.UTF_8)
    if (status !in 200..299) {
      throw AddressProviderException(
        "Yandex Maps returned HTTP $status${text.take(240).trim().takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}",
        retryable = status == 429 || status >= 500
      )
    }
    return runCatching { kz.aita.jsonBase.parseToJsonElement(text).jsonObject }
      .getOrElse { throwable ->
        throw AddressProviderException("Yandex Maps returned an unreadable JSON response", throwable)
      }
  }

  private fun JsonObject.objectOrNull(key: String): JsonObject? =
    get(key)?.let { runCatching { it.jsonObject }.getOrNull() }

  private fun JsonObject.arrayOrEmpty(key: String): JsonArray =
    get(key)?.let { runCatching { it.jsonArray }.getOrNull() } ?: JsonArray(emptyList())

  private fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
      ?.trim()
      ?.takeIf(String::isNotBlank)

  private fun JsonElement?.objectOrNull(): JsonObject? =
    this?.let { runCatching { it.jsonObject }.getOrNull() }

  private fun JsonElement?.stringOrNull(): String? =
    this?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
      ?.trim()
      ?.takeIf(String::isNotBlank)

  private fun normalizedCountryCode(raw: String?): String =
    raw?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 }.orEmpty()

  suspend fun suggest(
    query: String,
    language: String,
    countryCodes: List<String>,
    userLatitude: Double?,
    userLongitude: Double?,
    limit: Int
  ): List<AddressSuggestionDataModel> {
    val key = geosuggestKey ?: throw AddressProviderException(
      "Yandex Geosuggest is not configured on the server",
      retryable = false
    )
    val cleanQuery = query.trim().replace(Regex("\\s+"), " ").take(240)
    if (cleanQuery.length < 3) return emptyList()

    val cleanCountries = countryCodes
      .take(16)
      .map(::normalizedCountryCode)
      .filter(String::isNotBlank)
      .distinct()

    val hasUserPosition = userLatitude != null && userLongitude != null &&
      userLatitude.isFinite() && userLongitude.isFinite() &&
      userLatitude in -90.0..90.0 && userLongitude in -180.0..180.0

    val requestUrl = url(
      GEOSUGGEST_URL,
      listOf(
        "apikey" to key,
        "text" to cleanQuery,
        "lang" to suggestLanguage(language),
        "results" to limit.coerceIn(1, 10),
        "countries" to cleanCountries.takeIf { it.isNotEmpty() }?.joinToString(","),
        "types" to "geo",
        "print_address" to 1,
        "attrs" to "uri",
        "highlight" to 0,
        "ull" to if (hasUserPosition) "$userLongitude,$userLatitude" else null
      )
    )

    val root = getJson(requestUrl)
    return root.arrayOrEmpty("results")
      .mapNotNull { element ->
        val item = element.objectOrNull() ?: return@mapNotNull null
        val providerObjectId = item.stringOrNull("uri")
          ?: item.objectOrNull("attrs")?.stringOrNull("uri")
          ?: return@mapNotNull null
        val title = item.objectOrNull("title")?.stringOrNull("text")
          ?: item.stringOrNull("title")
          ?: return@mapNotNull null
        val subtitle = item.objectOrNull("subtitle")?.stringOrNull("text")
          ?: item.stringOrNull("subtitle")
          ?: ""
        val address = item.objectOrNull("address")
        val formattedAddress = address?.stringOrNull("formatted_address")
          ?: address?.stringOrNull("formatted")
          ?: subtitle
        val countryCode = normalizedCountryCode(address?.stringOrNull("country_code"))
          .ifBlank { cleanCountries.singleOrNull().orEmpty() }
        val tags = item.arrayOrEmpty("tags").mapNotNull { it.stringOrNull() }
        val kind = tags.firstOrNull().orEmpty()
        val distanceMeters = item.objectOrNull("distance")
          ?.get("value")
          ?.let { runCatching { it.jsonPrimitive.doubleOrNull }.getOrNull() }

        AddressSuggestionDataModel(
          providerObjectId = providerObjectId,
          title = title,
          subtitle = subtitle,
          formattedAddress = formattedAddress,
          kind = kind,
          countryCode = countryCode,
          distanceMeters = distanceMeters
        )
      }
      .distinctBy { it.providerObjectId }
      .take(limit.coerceIn(1, 10))
  }

  private suspend fun localizedSuggestionForObject(
    providerObjectId: String,
    query: String,
    language: String,
    countryCodeHint: String
  ): Pair<String, String>? {
    val key = geosuggestKey ?: return null
    val cleanQuery = query.trim().replace(Regex("\\s+"), " ").take(240)
    if (cleanQuery.length < 3) return null
    val countryCode = normalizedCountryCode(countryCodeHint)
    val requestUrl = url(
      GEOSUGGEST_URL,
      listOf(
        "apikey" to key,
        "text" to cleanQuery,
        "lang" to suggestLanguage(language),
        "results" to 10,
        "countries" to countryCode.takeIf(String::isNotBlank),
        "types" to "geo",
        "print_address" to 1,
        "attrs" to "uri",
        "highlight" to 0
      )
    )
    val exact = getJson(requestUrl).arrayOrEmpty("results")
      .mapNotNull { it.objectOrNull() }
      .firstOrNull { item ->
        val uri = item.stringOrNull("uri") ?: item.objectOrNull("attrs")?.stringOrNull("uri")
        uri == providerObjectId
      } ?: return null
    val title = exact.objectOrNull("title")?.stringOrNull("text")
      ?: exact.stringOrNull("title")
      ?: return null
    val address = exact.objectOrNull("address")
      ?.stringOrNull("formatted_address")
      ?: exact.objectOrNull("address")?.stringOrNull("formatted")
      ?: exact.objectOrNull("subtitle")?.stringOrNull("text")
      ?: title
    return title to address
  }

  private data class LocalizedResolvedAddress(
    val language: String,
    val name: String,
    val formattedAddress: String,
    val postalCode: String,
    val countryCode: String,
    val kind: String,
    val latitude: Double,
    val longitude: Double
  )

  private fun parseGeocoderResult(root: JsonObject, language: String): LocalizedResolvedAddress? {
    val response = root.objectOrNull("response") ?: return null
    val collection = response.objectOrNull("GeoObjectCollection") ?: return null
    val member = collection.arrayOrEmpty("featureMember").firstOrNull()?.objectOrNull() ?: return null
    val geoObject = member.objectOrNull("GeoObject") ?: return null
    val metadata = geoObject.objectOrNull("metaDataProperty")
      ?.objectOrNull("GeocoderMetaData")
    val address = metadata?.objectOrNull("Address")
    val pointRaw = geoObject.objectOrNull("Point")?.stringOrNull("pos") ?: return null
    val point = pointRaw.split(Regex("\\s+")).mapNotNull(String::toDoubleOrNull)
    if (point.size < 2) return null
    val longitude = point[0]
    val latitude = point[1]
    if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

    val formatted = address?.stringOrNull("formatted")
      ?: metadata?.stringOrNull("text")
      ?: geoObject.stringOrNull("description")
      ?: geoObject.stringOrNull("name")
      ?: return null
    val name = geoObject.stringOrNull("name")
      ?: formatted.substringAfterLast(',').trim().ifBlank { formatted }

    return LocalizedResolvedAddress(
      language = normalizedLanguage(language),
      name = name,
      formattedAddress = formatted,
      postalCode = address?.stringOrNull("postal_code").orEmpty(),
      countryCode = normalizedCountryCode(address?.stringOrNull("country_code")),
      kind = metadata?.stringOrNull("kind").orEmpty(),
      latitude = latitude,
      longitude = longitude
    )
  }

  private suspend fun geocodeOneLanguage(
    providerObjectId: String,
    query: String,
    language: String
  ): LocalizedResolvedAddress {
    val key = geocoderKey ?: throw AddressProviderException(
      "Yandex Geocoder is not configured on the server",
      retryable = false
    )
    val baseParams = mutableListOf<Pair<String, Any?>>(
      "apikey" to key,
      "format" to "json",
      "results" to 1,
      "lang" to geocoderLanguage(language)
    )
    if (providerObjectId.isNotBlank()) {
      baseParams += "uri" to providerObjectId
    } else {
      baseParams += "geocode" to query
    }

    val first = parseGeocoderResult(getJson(url(GEOCODER_URL, baseParams)), language)
    if (first != null) return first

    if (providerObjectId.isNotBlank() && query.isNotBlank()) {
      val fallbackParams = baseParams.filterNot { it.first == "uri" }.toMutableList()
      fallbackParams += "geocode" to query
      parseGeocoderResult(getJson(url(GEOCODER_URL, fallbackParams)), language)?.let { return it }
    }

    throw AddressProviderException(
      "Yandex could not resolve the selected address",
      retryable = false,
      clientFault = true
    )
  }

  private fun revisionFor(
    providerObjectId: String,
    values: List<LocalizedResolvedAddress>
  ): String {
    val canonical = buildString {
      append(providerObjectId.trim())
      values.sortedBy { it.language }.forEach { value ->
        append('|').append(value.language)
        append('|').append("%.7f".format(Locale.ROOT, value.latitude))
        append('|').append("%.7f".format(Locale.ROOT, value.longitude))
        append('|').append(value.name.trim())
        append('|').append(value.formattedAddress.trim())
        append('|').append(value.postalCode.trim())
        append('|').append(value.countryCode.trim())
        append('|').append(value.kind.trim())
      }
    }
    return MessageDigest.getInstance("SHA-256")
      .digest(canonical.toByteArray(StandardCharsets.UTF_8))
      .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
  }

  suspend fun resolve(
    provider: String,
    providerObjectId: String,
    query: String,
    language: String,
    existing: LocationDataModel? = null,
    countryCodeHint: String = ""
  ): LocationDataModel {
    if (!provider.equals(AITA_ADDRESS_PROVIDER_YANDEX, ignoreCase = true)) {
      throw AddressProviderException(
        "Unsupported address provider",
        retryable = false,
        clientFault = true
      )
    }
    val cleanObjectId = providerObjectId.trim()
    if (cleanObjectId.isBlank() || cleanObjectId.length > 2_048) {
      throw AddressProviderException(
        "Select a valid address suggestion before saving",
        retryable = false,
        clientFault = true
      )
    }
    val cleanQuery = query.trim().replace(Regex("\\s+"), " ").take(500)
    val primaryLanguage = normalizedLanguage(language)
    val languages = listOf(primaryLanguage, "en", "ru", "kk").distinct()
    val geocodedValues = coroutineScope {
      languages.map { locale ->
        async { geocodeOneLanguage(cleanObjectId, cleanQuery, locale) }
      }.awaitAll()
    }
    val values = geocodedValues.map { value ->
      if (value.language != "kk") return@map value
      val localized = runCatching {
        localizedSuggestionForObject(
          providerObjectId = cleanObjectId,
          query = cleanQuery.ifBlank { value.formattedAddress },
          language = "kk",
          countryCodeHint = countryCodeHint.ifBlank { value.countryCode }
        )
      }.getOrNull() ?: return@map value
      value.copy(name = localized.first, formattedAddress = localized.second)
    }
    val primary = values.firstOrNull { it.language == primaryLanguage } ?: values.first()
    val now = System.currentTimeMillis()
    val localizedNames = buildList {
      add(LocalizedStringDataModel("main", primary.name))
      values.forEach { add(LocalizedStringDataModel(it.language, it.name)) }
    }.distinctBy { it.language.lowercase(Locale.ROOT) }
    val localizedAddresses = buildList {
      add(LocalizedStringDataModel("main", primary.formattedAddress))
      values.forEach { add(LocalizedStringDataModel(it.language, it.formattedAddress)) }
    }.distinctBy { it.language.lowercase(Locale.ROOT) }

    return LocationDataModel(
      name = primary.name,
      postalIndex = primary.postalCode,
      latitude = primary.latitude,
      longitude = primary.longitude,
      provider = AITA_ADDRESS_PROVIDER_YANDEX,
      providerObjectId = cleanObjectId,
      kind = primary.kind,
      countryCode = primary.countryCode.ifBlank { normalizedCountryCode(countryCodeHint) },
      primaryLanguage = primaryLanguage,
      localizedNames = localizedNames,
      localizedAddresses = localizedAddresses,
      fallbackAddress = primary.formattedAddress,
      resolvedAtMillis = existing?.resolvedAtMillis?.takeIf { it > 0L } ?: now,
      lastCheckedAtMillis = now,
      providerRevision = revisionFor(cleanObjectId, values)
    )
  }

  private fun normalizedMapRequest(
    location: LocationDataModel,
    language: String,
    darkTheme: Boolean,
    width: Int,
    height: Int,
    zoom: Int,
    expiresAtMillis: Long = System.currentTimeMillis() + MAP_URL_TTL_MILLIS
  ): SignedAddressMapRequest {
    require(location.hasValidCoordinates()) { "Resolved coordinates are required for a map preview" }
    val requestWithoutSignature = SignedAddressMapRequest(
      latitude = location.latitude,
      longitude = location.longitude,
      expiresAtMillis = expiresAtMillis,
      width = width.coerceIn(320, 650),
      height = height.coerceIn(180, 450),
      zoom = zoom.coerceIn(10, 18),
      language = normalizedLanguage(language),
      darkTheme = darkTheme,
      signature = ""
    )
    return requestWithoutSignature.copy(signature = signMapRequest(requestWithoutSignature))
  }

  private fun mapCanonical(request: SignedAddressMapRequest): String = listOf(
    "%.7f".format(Locale.ROOT, request.latitude),
    "%.7f".format(Locale.ROOT, request.longitude),
    request.expiresAtMillis.toString(),
    request.width.toString(),
    request.height.toString(),
    request.zoom.toString(),
    normalizedLanguage(request.language),
    if (request.darkTheme) "1" else "0"
  ).joinToString("|")

  private fun signMapRequest(request: SignedAddressMapRequest): String {
    val secret = mapSigningSecret ?: throw AddressProviderException(
      "Address map signing is not configured on the server",
      retryable = false
    )
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(
      mac.doFinal(mapCanonical(request).toByteArray(StandardCharsets.UTF_8))
    )
  }

  fun verifySignedMapRequest(request: SignedAddressMapRequest): Boolean {
    if (request.expiresAtMillis < System.currentTimeMillis() - 10_000L) return false
    if (request.expiresAtMillis > System.currentTimeMillis() + MAP_URL_TTL_MILLIS + 60_000L) return false
    if (request.latitude !in -90.0..90.0 || request.longitude !in -180.0..180.0) return false
    val expected = runCatching { signMapRequest(request.copy(signature = "")) }.getOrNull() ?: return false
    return MessageDigest.isEqual(
      expected.toByteArray(StandardCharsets.UTF_8),
      request.signature.toByteArray(StandardCharsets.UTF_8)
    )
  }

  fun createMapPreview(
    location: LocationDataModel,
    language: String,
    darkTheme: Boolean,
    width: Int,
    height: Int,
    zoom: Int,
    publicServerUrl: String
  ): AddressMapPreviewDataModel {
    if (!staticMapsConfigured) {
      throw AddressProviderException("Yandex Static Maps is not configured on the server", retryable = false)
    }
    val request = normalizedMapRequest(location, language, darkTheme, width, height, zoom)
    val mapUrl = url(
      publicServerUrl.trimEnd('/') + "/geo/address/map",
      listOf(
        "lat" to "%.7f".format(Locale.ROOT, request.latitude),
        "lon" to "%.7f".format(Locale.ROOT, request.longitude),
        "expires" to request.expiresAtMillis,
        "w" to request.width,
        "h" to request.height,
        "z" to request.zoom,
        "lang" to request.language,
        "dark" to if (request.darkTheme) 1 else 0,
        "sig" to request.signature
      )
    )
    val coordinates = "%.7f,%.7f".format(Locale.ROOT, request.longitude, request.latitude)
    val openMapUrl = url(
      "https://yandex.com/maps/",
      listOf(
        "ll" to coordinates,
        "z" to request.zoom,
        "pt" to coordinates,
        "l" to "map"
      )
    )
    return AddressMapPreviewDataModel(
      url = mapUrl,
      openMapUrl = openMapUrl,
      expiresAtMillis = request.expiresAtMillis
    )
  }

  suspend fun fetchStaticMap(request: SignedAddressMapRequest): Pair<String, ByteArray> {
    if (!verifySignedMapRequest(request)) {
      throw AddressProviderException(
        "Address map link is invalid or expired",
        retryable = false,
        clientFault = true
      )
    }
    val key = staticMapsKey ?: throw AddressProviderException(
      "Yandex Static Maps is not configured on the server",
      retryable = false
    )
    val coordinates = "%.7f,%.7f".format(Locale.ROOT, request.longitude, request.latitude)
    val requestUrl = url(
      STATIC_MAP_URL,
      listOf(
        "apikey" to key,
        "ll" to coordinates,
        "z" to request.zoom,
        "size" to "${request.width},${request.height}",
        "scale" to 1,
        "pt" to "$coordinates,pm2rdm",
        "lang" to staticMapLanguage(request.language),
        "theme" to if (request.darkTheme) "dark" else "light",
        "maptype" to "map"
      )
    )
    val (status, bytes) = getBytes(requestUrl, "image/png,image/jpeg,image/*")
    if (status !in 200..299) {
      throw AddressProviderException("Yandex Static Maps returned HTTP $status", retryable = status == 429 || status >= 500)
    }
    return "image/png" to bytes
  }
}
