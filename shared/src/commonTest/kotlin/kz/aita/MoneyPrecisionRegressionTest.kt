package kz.aita

import kotlin.test.*

class MoneyPrecisionRegressionTest {
    @Test fun exactCentsStayStableAcrossInputTotalsAndRepeatedRounding() {
        for (cents in 0..100_000) {
            val value = cents / 100.0
            assertEquals(value, value.roundMoney(), "cents=$cents")
            assertEquals(value, value.toString().toMoneyDouble(), "input cents=$cents")
        }
    }
    @Test fun genuineFractionalCentsKeepExistingTruncationPolicy() {
        assertEquals(1.23, 1.239.roundMoney())
        assertEquals(-1.24, (-1.231).roundMoney())
        assertEquals(0.0, 0.009.roundMoney())
        assertEquals(148.14, (123.45 * 1.2).roundMoney())
    }
}
