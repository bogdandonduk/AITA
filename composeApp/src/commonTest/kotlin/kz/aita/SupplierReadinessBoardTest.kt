package kz.aita

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplierReadinessBoardTest {
    @Test
    fun emptyReadinessDoesNotCreateAnEmptyWorkspaceCard() {
        assertFalse(SupplierDashboardReadinessDataModel().hasSupplierReadinessSignal())
    }

    @Test
    fun operationalOrCommercialProgressMakesReadinessVisible() {
        assertTrue(
            SupplierDashboardReadinessDataModel(
                answerNeededOrderCount = 1
            ).hasSupplierReadinessSignal()
        )
        assertTrue(
            SupplierDashboardReadinessDataModel(
                priceBookCoveredLineCount = 1
            ).hasSupplierReadinessSignal()
        )
        assertTrue(
            SupplierDashboardReadinessDataModel(
                acceptedQuantityTotal = 0.5
            ).hasSupplierReadinessSignal()
        )
    }
}
