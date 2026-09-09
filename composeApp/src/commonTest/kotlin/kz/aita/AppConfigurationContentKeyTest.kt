package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AppConfigurationContentKeyTest {
    @Test
    fun noExtraKeysStillProduceOneUsableStableValue() {
        val first = appConfigurationContentKey(emptyArray())
        val second = appConfigurationContentKey(emptyArray())
        assertEquals(emptyList(), first)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun freshlyAllocatedEqualInputsDoNotChangeIdentity() {
        data class Owner(val id: String)
        val first = appConfigurationContentKey(arrayOf(Owner("account"), 42L))
        val second = appConfigurationContentKey(arrayOf(Owner("account"), 42L))
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun changingAnyExplicitInputChangesIdentity() {
        val initial = appConfigurationContentKey(arrayOf("account-a", "store-a", 1L))
        assertNotEquals(initial, appConfigurationContentKey(arrayOf("account-b", "store-a", 1L)))
        assertNotEquals(initial, appConfigurationContentKey(arrayOf("account-a", "store-b", 1L)))
        assertNotEquals(initial, appConfigurationContentKey(arrayOf("account-a", "store-a", 2L)))
    }

    @Test
    fun orderingIsPartOfTheKey() {
        assertNotEquals(
            appConfigurationContentKey(arrayOf("account", "store")),
            appConfigurationContentKey(arrayOf("store", "account"))
        )
    }

    @Test
    fun addingAndRemovingKeysChangesIdentity() {
        val none = appConfigurationContentKey(emptyArray())
        val one = appConfigurationContentKey(arrayOf("account"))
        val two = appConfigurationContentKey(arrayOf("account", "store"))
        assertNotEquals(none, one)
        assertNotEquals(one, two)
        assertNotEquals(none, two)
    }

    @Test
    fun hashCollisionsDoNotMakeDifferentInputsEqual() {
        // These strings intentionally collide; a contentHashCode-only fix would be wrong.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val first = appConfigurationContentKey(arrayOf("Aa"))
        val second = appConfigurationContentKey(arrayOf("BB"))
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, second)
    }

    @Test
    fun changingTheSourceArrayDoesNotMutateThePreviousKey() {
        val inputs = arrayOf<Any>("account-a", "store")
        val before = appConfigurationContentKey(inputs)
        inputs[0] = "account-b"
        assertEquals(listOf("account-a", "store"), before)
        assertNotEquals(before, appConfigurationContentKey(inputs))
    }

    @Test
    fun singletonInputAlsoHasSnapshotSemantics() {
        val inputs = arrayOf<Any>("account-a")
        val before = appConfigurationContentKey(inputs)
        inputs[0] = "account-b"
        assertEquals(listOf("account-a"), before)
        assertNotEquals(before, appConfigurationContentKey(inputs))
    }

    @Test
    fun arrayValuedInputKeepsItsExistingEqualitySemantics() {
        val input = arrayOf("nested")
        val first = appConfigurationContentKey(arrayOf(input))
        assertEquals(first, appConfigurationContentKey(arrayOf(input)))
        assertNotEquals(first, appConfigurationContentKey(arrayOf(arrayOf("nested"))))
    }
}
