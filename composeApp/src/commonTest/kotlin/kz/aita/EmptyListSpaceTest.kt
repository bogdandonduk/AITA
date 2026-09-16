package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals

class EmptyListSpaceTest {
    @Test fun noHeadersUsesBlankViewport() = assertEquals(576, remainingListSpacePixels(-12, 588, 12, 0, 160))
    @Test fun subtractsPrecedingHeaderAndPadding() = assertEquals(368, remainingListSpacePixels(-16, 584, 16, 200, 160))
    @Test fun aTallHeaderKeepsReadableScrollablePanel() = assertEquals(160, remainingListSpacePixels(0, 400, 12, 350, 160))
    @Test fun scrolledPastHeaderNeverAddsInvisibleSpace() = assertEquals(588, remainingListSpacePixels(0, 600, 12, -200, 160))
    @Test fun notMeasuredYetHasBoundedFallback() = assertEquals(160, remainingListSpacePixels(0, 0, 12, null, 160))
    @Test fun offscreenItemDoesNotPretendHeaderHeightIsZero() = assertEquals(160, remainingListSpacePixels(0, 600, 12, null, 160))
    @Test fun sizeChangesRecomputeRemainingArea() {
        assertEquals(368, remainingListSpacePixels(-16, 584, 16, 200, 160))
        assertEquals(168, remainingListSpacePixels(-16, 384, 16, 200, 160))
    }
    @Test fun largeOffsetsDoNotOverflow() = assertEquals(Int.MAX_VALUE, remainingListSpacePixels(Int.MIN_VALUE, Int.MAX_VALUE, 0, Int.MIN_VALUE, 160))
    @Test fun malformedPaddingCannotEnlargePanel() = assertEquals(400, remainingListSpacePixels(0, 600, -1, 200, 160))
}
