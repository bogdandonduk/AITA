package kz.aita.server

import kz.aita.*
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.exposed.sql.batchInsert
import java.net.URI
import java.time.Instant
import java.util.UUID

internal object PublicSupplierCatalogue {
    val entries: List<SupplierDataModel> by lazy {
        val text = checkNotNull(javaClass.getResourceAsStream("/catalogue/public-suppliers.json"))
            .bufferedReader().use { it.readText() }
        parse(text)
    }
    val byId: Map<String, SupplierDataModel> by lazy { entries.associateBy { it.id } }
    fun parse(text: String): List<SupplierDataModel> =
        jsonBase.decodeFromString(ListSerializer(SupplierDataModel.serializer()), text).also { rows ->
            require(rows.size <= 10_000 && rows.map { it.id }.distinct().size == rows.size)
            rows.forEach { row ->
                UUID.fromString(row.id)
                require(row.userIds.isEmpty() && row.phoneNumbers.isNullOrEmpty() && row.emails.isNullOrEmpty())
                require(row.name.isNotEmpty() && row.name.all { it.value.isNotBlank() && it.value.length <= 500 })
                val source = requireNotNull(row.catalogueSource)
                require(source.name == "Wikidata" && source.license == "CC0-1.0")
                require(source.url.matches(Regex("https://www\\.wikidata\\.org/wiki/Q[0-9]+")))
                require(source.countryCodes.isNotEmpty() && source.countryCodes.all { it in setOf("KZ", "TJ", "UZ", "KG", "RU") })
                require(source.websites.all { url -> URI(url).let { it.scheme in setOf("http", "https") && it.host != null && it.userInfo == null } })
            }
        }
}

/** Stable IDs and INSERT ON CONFLICT preserve existing records, ownership and edits. */
internal fun seedPublicSuppliersInsideTransaction() {
    Suppliers.batchInsert(PublicSupplierCatalogue.entries, ignore = true, shouldReturnGeneratedValues = false) { entry ->
        this[Suppliers.id] = UUID.fromString(entry.id)
        this[Suppliers.userIds] = "[]"
        this[Suppliers.name] = jsonBase.encodeToString(ListSerializer(LocalizedStringDataModel.serializer()), entry.name)
        this[Suppliers.addedAt] = Instant.parse("2026-09-20T00:00:00Z")
        this[Suppliers.isActive] = true
    }
}
