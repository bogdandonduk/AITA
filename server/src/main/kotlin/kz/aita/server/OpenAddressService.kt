package kz.aita.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import kz.aita.*
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.ResultSet
import java.text.Normalizer
import java.util.Locale

/** Read-only, independently compiled ODbL catalogue; no paid-provider data is mixed into it. */
internal object OpenAddressService {
    private val slots = Semaphore(4)
    fun databasePath(): Path = Path.of(System.getProperty("AITA_ADDRESSES_DB") ?: System.getenv("AITA_ADDRESSES_DB")
        ?: ((System.getenv("AITA_CLIENT_RELEASES_DIR") ?: "/var/lib/aita-client-releases") + "/addresses/addresses.sqlite"))
    val configured: Boolean get() = Files.isRegularFile(databasePath())
    private suspend fun <T> read(block: (java.sql.Connection) -> T): T = slots.withPermit { withContext(Dispatchers.IO) {
        if (!configured) throw AddressProviderException("Independent address catalogue is not installed", retryable = false)
        DriverManager.getConnection("jdbc:sqlite:file:" + databasePath().toAbsolutePath() + "?mode=ro").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA query_only=ON"); it.execute("PRAGMA busy_timeout=2000") }
            block(connection)
        }
    } }
    internal fun searchTerms(query: String): String = Regex("[\\p{L}\\p{N}]+")
        .findAll(Normalizer.normalize(query.take(240), Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace('ё', 'е'))
        .take(10).map { "\"${it.value}\"*" }.joinToString(" AND ")
    private fun ResultSet.suggestion(language: String): AddressSuggestionDataModel {
        val names = runCatching { jsonBase.parseToJsonElement(getString("names")).jsonObject }.getOrNull()
        val title = names?.get(language)?.jsonPrimitive?.contentOrNull ?: getString("title")
        val subtitle = (if (getString("approximate_locality").isNotBlank()) "≈ " else "") + getString("subtitle")
        return AddressSuggestionDataModel(provider = AITA_ADDRESS_PROVIDER_OPEN, providerObjectId = getString("id"),
            title = title, subtitle = subtitle, formattedAddress = listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(", "),
            kind = getString("kind"), countryCode = getString("country"))
    }
    suspend fun suggest(query: String, language: String, countryCodes: List<String>, userLatitude: Double?, userLongitude: Double?, limit: Int): List<AddressSuggestionDataModel> {
        val terms = searchTerms(query)
        if (query.trim().length < 3 || terms.isBlank()) return emptyList()
        val countries = countryCodes.map { it.uppercase(Locale.ROOT) }.distinct().filter { it in setOf("KZ", "KG", "TJ", "UZ", "RU") }.take(5)
        if (countryCodes.isNotEmpty() && countries.isEmpty()) return emptyList()
        return read { connection ->
            // Bound candidate ranking. Sorting millions of generic street prefixes delays typing.
            val scopedTerms = terms + if (countries.isEmpty()) "" else countries.joinToString(" OR ", " AND (", ")") { "\"${it.lowercase(Locale.ROOT)}\"" }
            val sql = "WITH matched AS MATERIALIZED (SELECT rowid, rank FROM address_search WHERE address_search MATCH ? LIMIT 512) " +
                "SELECT a.* FROM matched JOIN addresses a ON a.rowid=matched.rowid" +
                (if (countries.isEmpty()) "" else " WHERE country IN (${countries.joinToString(",") { "?" }})") +
                " ORDER BY matched.rank LIMIT 80"
            connection.prepareStatement(sql).use { statement ->
                statement.queryTimeout = 3
                statement.setString(1, scopedTerms); countries.forEachIndexed { i, country -> statement.setString(i+2, country) }
                statement.executeQuery().use { rows -> buildList {
                    while (rows.next()) {
                        val value = rows.suggestion(language)
                        val distance = if (userLatitude != null && userLongitude != null && userLatitude.isFinite() && userLongitude.isFinite() && userLatitude in -90.0..90.0 && userLongitude in -180.0..180.0) {
                            val dx=(rows.getDouble("lon")-userLongitude)*kotlin.math.cos(Math.toRadians(userLatitude))
                            val dy=rows.getDouble("lat")-userLatitude
                            kotlin.math.sqrt(dx*dx+dy*dy)*111_200
                        } else null
                        add(value.copy(distanceMeters=distance))
                    }
                } }.distinctBy { it.countryCode + ":" + it.formattedAddress }.let { values ->
                    if (userLatitude != null && userLongitude != null) values.sortedBy { it.distanceMeters ?: Double.MAX_VALUE } else values
                }.take(limit.coerceIn(1,10))
            }
        }
    }
    suspend fun resolve(providerObjectId: String, language: String, existing: LocationDataModel? = null): LocationDataModel = read { connection ->
        if (!Regex("(?:KZ|KG|TJ|UZ|RU):[nw][0-9]{1,20}").matches(providerObjectId))
            throw AddressProviderException("Invalid open address ID", retryable=false, clientFault=true)
        connection.prepareStatement("SELECT * FROM addresses WHERE id=?").use { statement ->
            statement.setString(1,providerObjectId)
            statement.executeQuery().use { row ->
                if (!row.next()) throw AddressProviderException("Address is no longer in the catalogue; select again or enter it manually",retryable=false,clientFault=true)
                val suggestion=row.suggestion(language);val now=System.currentTimeMillis()
                LocationDataModel(name=suggestion.title, postalIndex=row.getString("postal"), latitude=row.getDouble("lat"), longitude=row.getDouble("lon"),
                    provider=AITA_ADDRESS_PROVIDER_OPEN, providerObjectId=providerObjectId, kind=suggestion.kind, countryCode=suggestion.countryCode,
                    primaryLanguage=language, localizedNames=listOf(LocalizedStringDataModel("main",suggestion.title)),
                    localizedAddresses=listOf(LocalizedStringDataModel("main",suggestion.formattedAddress)),fallbackAddress=suggestion.formattedAddress,
                    resolvedAtMillis=existing?.resolvedAtMillis?.takeIf { it>0 } ?: now,lastCheckedAtMillis=now,
                    providerRevision=Files.getLastModifiedTime(databasePath()).toMillis().toString())
            }
        }
    }
}

internal object AitaAddressService {
    suspend fun suggest(query: String, language: String, countryCodes: List<String>, userLatitude: Double?, userLongitude: Double?, limit: Int) =
        if (OpenAddressService.configured) OpenAddressService.suggest(query,language,countryCodes,userLatitude,userLongitude,limit)
        else YandexAddressService.suggest(query,language,countryCodes,userLatitude,userLongitude,limit)
    suspend fun resolve(provider: String, providerObjectId: String, query: String, language: String, existing: LocationDataModel? = null, countryCodeHint: String = "") =
        if (provider == AITA_ADDRESS_PROVIDER_OPEN) OpenAddressService.resolve(providerObjectId,language,existing)
        else YandexAddressService.resolve(provider,providerObjectId,query,language,existing,countryCodeHint)
    fun createMapPreview(location: LocationDataModel, language: String, darkTheme: Boolean, width: Int, height: Int, zoom: Int, publicServerUrl: String): AddressMapPreviewDataModel =
        if (location.provider == AITA_ADDRESS_PROVIDER_OPEN && location.hasValidCoordinates()) AddressMapPreviewDataModel(
            url="", openMapUrl="https://www.openstreetmap.org/?mlat=${location.latitude}&mlon=${location.longitude}#map=${zoom.coerceIn(10,18)}/${location.latitude}/${location.longitude}",
            expiresAtMillis=System.currentTimeMillis()+900_000, attribution="© OpenStreetMap contributors · ODbL 1.0")
        else YandexAddressService.createMapPreview(location,language,darkTheme,width,height,zoom,publicServerUrl)
}
