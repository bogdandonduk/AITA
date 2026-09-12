package kz.aita.server

import java.nio.charset.StandardCharsets
import java.util.UUID

/** Stable, vendor-neutral classifications, not a product list or a legal permission catalogue. */
internal data class GoodsCategoryDefinition(
    val slug: String,
    val parentSlug: String?,
    val en: String,
    val ru: String,
    val kk: String,
    val quantityUnitId: String
)

internal object GoodsCategoryCatalogue {
    private const val RESOURCE = "/catalogue/goods-categories.tsv"

    fun load(): List<GoodsCategoryDefinition> =
        checkNotNull(javaClass.getResourceAsStream(RESOURCE)) { "Missing packaged goods category catalogue" }
            .bufferedReader(StandardCharsets.UTF_8).use { parse(it.readText()) }

    fun parse(text: String): List<GoodsCategoryDefinition> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == "slug\tparent_slug\ten\tru\tkk\tquantity_unit_id") {
            "Invalid goods category catalogue header"
        }
        val seen = hashSetOf<String>()
        return lines.drop(1).mapIndexed { index, line ->
            val columns = line.split('\t')
            require(columns.size == 6) { "Invalid category columns at line ${index + 2}" }
            val (slug, parent, en, ru, kk) = columns
            val unit = columns[5]
            require(slug.matches(Regex("[a-z][a-z0-9_]*")) && seen.add(slug)) {
                "Invalid or duplicate category slug at line ${index + 2}"
            }
            require(parent.isEmpty() || parent.matches(Regex("[a-z][a-z0-9_]*"))) { "Invalid category parent: $slug" }
            require(listOf(en, ru, kk).all { it.isNotBlank() && it == it.trim() }) { "Missing category translation: $slug" }
            require(unit == "0" || unit == "1") { "Unknown category quantity unit: $slug" }
            GoodsCategoryDefinition(slug, parent.takeIf(String::isNotEmpty), en, ru, kk, unit)
        }
    }
}

internal fun stableGoodsCategoryId(slug: String): UUID =
    UUID.nameUUIDFromBytes("aita:generic-goods-category:$slug".toByteArray(StandardCharsets.UTF_8))

/** Ancestors are ordered root first, retaining compatibility with AITA's root/subcategory pickers. */
internal fun goodsCategoryAncestors(slug: String, parents: Map<String, String?>): List<String> {
    require(slug in parents) { "Unknown category: $slug" }
    val visited = hashSetOf(slug)
    val ancestors = mutableListOf<String>()
    var parent = parents[slug]
    while (parent != null) {
        require(parent in parents) { "Missing parent $parent of $slug" }
        require(visited.add(parent)) { "Category cycle at $parent" }
        ancestors += parent
        parent = parents[parent]
    }
    return ancestors.asReversed()
}

internal fun resolveGoodsCategoryIds(
    seedNames: Map<String, String>,
    existingNames: Map<UUID, String>
): Map<String, UUID> {
    val existingByName = existingNames.entries.groupBy({ it.value }, { it.key })
    val resolved = seedNames.mapValues { (slug, name) ->
        val stableId = stableGoodsCategoryId(slug)
        when {
            stableId in existingNames -> stableId
            // A legacy row can own the original seed name. Do not replace its UUID or item links.
            existingByName[name]?.size == 1 -> existingByName.getValue(name).single()
            else -> stableId
        }
    }
    require(resolved.values.toSet().size == resolved.size) { "Two category seeds resolve to the same row" }
    return resolved
}
