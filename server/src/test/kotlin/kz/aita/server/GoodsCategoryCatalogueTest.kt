package kz.aita.server

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GoodsCategoryCatalogueTest {
    private val header = "slug\tparent_slug\ten\tru\tkk\tquantity_unit_id\tky\n"
    @Test fun packagedCatalogueHasAllTranslationsAndUniqueSlugs() {
        val definitions = GoodsCategoryCatalogue.load()
        assertEquals(644, definitions.size)
        assertEquals(definitions.size, definitions.map { it.slug }.toSet().size)
        assertTrue(definitions.all { it.en.isNotBlank() && it.ru.isNotBlank() && it.kk.isNotBlank() && it.ky.isNotBlank() })
        assertTrue(definitions.any { it.slug == "electronics" || it.parentSlug == "electronics" })
        assertTrue(definitions.any { it.parentSlug == "sports" })
    }
    @Test fun completeCatalogueRetainsOriginalSeedsAndBuildsReachableDescendants() {
        val original = originalGenericGoodsCategorySeeds()
        val complete = defaultGenericGoodsCategorySeeds()
        assertEquals(120, original.size)
        assertEquals(764, complete.size)
        val bySlug = complete.associateBy { it.slug }
        original.forEach { assertEquals(it, bySlug.getValue(it.slug)) }
        val parents = categorySeedParents(complete)
        assertEquals(44, parents.count { it.value == null })
        for (language in listOf("en", "ru", "kk", "ky")) {
            val names = complete.map { seed -> seed.name.first { it.language == language }.value }
            assertEquals(names.size, names.toSet().size, "Duplicate full category path: $language")
        }
        complete.forEach { seed ->
            val ancestors = goodsCategoryAncestors(seed.slug, parents)
            assertTrue(seed.slug !in ancestors)
            ancestors.firstOrNull()?.let { assertEquals(null, parents.getValue(it)) }
        }
    }
    @Test fun emptyCatalogueIsValidButMissingHeaderIsNot() {
        assertTrue(GoodsCategoryCatalogue.parse(header).isEmpty())
        assertFailsWith<IllegalArgumentException> { GoodsCategoryCatalogue.parse("") }
    }
    @Test fun duplicateSlugIsRejected() {
        val row = "food_new\tfood\tNew\tНовое\tЖаңа\t0\tЖаңы\n"
        assertFailsWith<IllegalArgumentException> { GoodsCategoryCatalogue.parse(header + row + row) }
    }
    @Test fun missingTranslationIsRejected() {
        assertFailsWith<IllegalArgumentException> { GoodsCategoryCatalogue.parse(header + "food_new\tfood\tNew\t\tЖаңа\t0\tЖаңы") }
    }
    @Test fun missingKyrgyzTranslationIsRejected() {
        assertFailsWith<IllegalArgumentException> { GoodsCategoryCatalogue.parse(header + "food_new\tfood\tNew\tНовое\tЖаңа\t0\t") }
    }
    @Test fun unsupportedUnitIsRejected() {
        assertFailsWith<IllegalArgumentException> { GoodsCategoryCatalogue.parse(header + "food_new\tfood\tNew\tНовое\tЖаңа\t404\tЖаңы") }
    }
    @Test fun ancestorsAreRootFirstAndRootHasNone() {
        val parents = mapOf("root" to null, "parent" to "root", "leaf" to "parent")
        assertEquals(emptyList(), goodsCategoryAncestors("root", parents))
        assertEquals(listOf("root", "parent"), goodsCategoryAncestors("leaf", parents))
    }
    @Test fun cyclesAndMissingParentsAreRejected() {
        assertFailsWith<IllegalArgumentException> { goodsCategoryAncestors("a", mapOf("a" to "b", "b" to "a")) }
        assertFailsWith<IllegalArgumentException> { goodsCategoryAncestors("a", mapOf("a" to "missing")) }
    }
    @Test fun legacyRootUuidIsPreservedForDescendantLinks() {
        val legacyId = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val ids = resolveGoodsCategoryIds(mapOf("root" to "root", "leaf" to "root / leaf"), mapOf(legacyId to "root"))
        assertEquals(legacyId, ids.getValue("root"))
        val parents = mapOf("root" to null, "leaf" to "root")
        assertEquals(listOf(legacyId), goodsCategoryAncestors("leaf", parents).map(ids::getValue))
    }
    @Test fun stableExistingUuidWinsAfterNameWasChanged() {
        val stable = stableGoodsCategoryId("food")
        assertEquals(stable, resolveGoodsCategoryIds(mapOf("food" to "food"), mapOf(stable to "customized"))["food"])
    }
    @Test fun ambiguousLegacyNameDoesNotChooseArbitraryRow() {
        val a = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val b = UUID.fromString("10000000-0000-0000-0000-000000000002")
        val id = resolveGoodsCategoryIds(mapOf("food" to "food"), mapOf(a to "food", b to "food")).getValue("food")
        assertEquals(stableGoodsCategoryId("food"), id); assertNotEquals(a, id); assertNotEquals(b, id)
    }
    @Test fun repeatedResolutionKeepsAllIdsStable() {
        val names = mapOf("root" to "root", "leaf" to "root / leaf")
        val first = resolveGoodsCategoryIds(names, emptyMap())
        val existing = first.entries.associate { (slug, id) -> id to names.getValue(slug) }
        assertEquals(first, resolveGoodsCategoryIds(names, existing))
    }
}
