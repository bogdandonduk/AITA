package kz.aita

import kotlinx.serialization.Serializable

/** Public catalogue provenance. Never a claim of supplier membership or verified availability. */
@Serializable
data class CatalogueSource(
    val name: String,
    val url: String,
    val license: String,
    val licenseUrl: String,
    val retrievedOn: String,
    val countryCodes: List<String> = emptyList(),
    val websites: List<String> = emptyList(),
    val sectors: List<LocalizedStringDataModel> = emptyList(),
)
