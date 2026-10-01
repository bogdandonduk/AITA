package kz.aita

import kotlin.test.*

class OperationAmountsTest {
    @Test fun mixedQuickFillsCanReallocateAutomaticallyFilledAmounts() {
        assertEquals(250.0, mixedPaymentQuickFillTarget(250.0, 75.0, "cash"))
        assertEquals(175.0, mixedPaymentQuickFillTarget(250.0, 75.0, "card"))
        assertEquals(175.0, mixedPaymentQuickFillTarget(250.0, 75.0, "debt"))
        assertEquals(0.0, mixedPaymentQuickFillTarget(250.0, 300.0, "debt"))
    }
    @Test fun saleMarkupCanExceedCostButDebtCannot() {
        assertEquals(1500.0, percentQuickFillAmount(1000.0, 150.0, false))
        assertEquals(2000.0, percentQuickFillAmount(1000.0, 200.0, false))
        assertEquals(1000.0, percentQuickFillAmount(1000.0, 150.0, true))
        assertEquals(600.0, percentQuickFillAmount(1000.0, 60.0, true))
    }
    @Test fun percentRoundTripDoesNotLoseACent() {
        assertEquals("148.14", moneyInputFromDouble(percentQuickFillAmount(123.45, 120.0, false)))
        assertEquals("1.15", moneyInputFromDouble(1.15))
        assertEquals("0.29", moneyInputFromDouble(0.29))
        assertEquals("100.00", moneyInputFromDouble(99.999))
    }
    @Test fun nonfiniteAndNegativeInputsNeverProducePrices() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) assertEquals(0L, moneyMinorUnits(value))
    }
    @Test fun mixedAmountsFillNextFieldAndAllowReeditingEarlierOnes() {
        val first = fillMixedPaymentRemainder(1000.0,"","","","cash","300")
        assertEquals(MixedPaymentInputs("300", "700.00", ""), first)
        val second = fillMixedPaymentRemainder(1000.0,first.cash,first.card,first.debt,"card","500")
        assertEquals(MixedPaymentInputs("300","500","200.00"),second)
        assertEquals(MixedPaymentInputs("400","600.00",""),fillMixedPaymentRemainder(1000.0,second.cash,second.card,second.debt,"cash","400"))
        assertEquals(MixedPaymentInputs("300","600.00","100"),fillMixedPaymentRemainder(1000.0,second.cash,second.card,second.debt,"debt","100"))
    }
    @Test fun mixedAmountsAreBoundedAndKeepDecimalEditingText() {
        assertEquals(MixedPaymentInputs("100.00","0.00",""), fillMixedPaymentRemainder(100.0,"","","","cash","999"))
        assertEquals(MixedPaymentInputs("1.","0.15",""), fillMixedPaymentRemainder(1.15,"","","","cash","1."))
        assertEquals(MixedPaymentInputs("1","0.15","0.00"), fillMixedPaymentRemainder(1.15,"1","","","card","1"))
        assertEquals(MixedPaymentInputs("","1.15",""),fillMixedPaymentRemainder(1.15,"1","0.15","","cash",""))
    }
}
