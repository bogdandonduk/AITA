package kz.aita.server.marketplace

import kotlin.test.*

class MarketplaceMoneyTest {
    @Test fun decimalTextIsNotFlooredTwice() {
        assertEquals(19999L,marketPriceMinor("199.99")); assertEquals(799000L,marketPriceMinor("7990"))
        assertEquals(2999L,marketPriceMinor("29,99")); assertEquals(1L,marketPriceMinor("0.01"))
    }
    @Test fun fractionalCentRoundsExplicitly() { assertEquals(101L,marketPriceMinor("1.005")); assertEquals(100L,marketPriceMinor("1.004")) }
    @Test fun invalidNegativeNonFiniteAndExcessivePricesAreUnknown() {
        listOf("", "-1", "NaN", "Infinity", "10000000001", "lots", "1 000").forEach{assertNull(marketPriceMinor(it),it)}
    }
    @Test fun genuineZeroIsPreservedAndMaximumCannotOverflow() {
        assertEquals(0L,marketPriceMinor("0"));assertEquals(1000000000000L,marketPriceMinor("10000000000"))
    }
    @Test fun malformedUuidCannotReachSqlAsAnArbitraryIdentifier() {
        listOf("", "1-1-1-1-1", "not-uuid", "x' OR true--").forEach{assertFailsWith<MarketFailure>{marketUuid(it)}}
        assertEquals("c534bab2-3f6a-4c0d-ae55-40faa6f2790f",marketUuid("C534BAB2-3F6A-4C0D-AE55-40FAA6F2790F").toString())
    }
    @Test fun decimalSellingMultiplesDoNotInventExcessQuantity() {
        assertEquals(0.3, marketRequestedQuantity(0.1, 3))
        assertEquals(1.5, marketRequestedQuantity(0.5, 3))
        assertEquals(999_000_000.0, marketRequestedQuantity(1_000_000.0, 999))
        assertFailsWith<IllegalArgumentException> { marketRequestedQuantity(Double.NaN, 1) }
        assertFailsWith<IllegalArgumentException> { marketRequestedQuantity(1.0, 0) }
    }
}
