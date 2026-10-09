package kz.aita

import kotlin.test.*
class CameraBarcodeConsensusTest {
    @Test fun isolatedMisreadCannotAddAnItemAndStableFramesKeepEveryDigit() {
        val scanner=CameraBarcodeConsensus()
        assertNull(scanner.accept("4870123456789",1000))
        assertNull(scanner.accept("487012345678",1050))
        assertNull(scanner.accept("4870123456789",1100))
        assertNull(scanner.accept("4870123456789",1160))
        assertEquals("4870123456789",scanner.accept("4870123456789",1220))
        assertNull(scanner.accept("4870123456789",1280))
    }
    @Test fun differentProductsAndStaleFramesDoNotCombine() {
        val scanner=CameraBarcodeConsensus()
        assertNull(scanner.accept("AITA-R-123",100))
        assertNull(scanner.accept("AITA-R-123",100))
        assertNull(scanner.accept("AITA-R-123",1100))
        assertNull(scanner.accept("AITA-R-123",1160))
        assertEquals("AITA-R-123",scanner.accept("AITA-R-123",1220))
        assertNull(scanner.accept("123\n456",1300))
    }
}
