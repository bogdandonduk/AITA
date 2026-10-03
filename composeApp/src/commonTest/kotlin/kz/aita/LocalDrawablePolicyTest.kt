package kz.aita

import aita.composeapp.generated.resources.*
import kotlin.test.*

class LocalDrawablePolicyTest {
    @Test fun bundledOnlyPlatformIconNeverRequestsAnEmptyRemotePath() {
        assertTrue(shouldPreferLocalDrawable("", Res.drawable.platform_linux))
        assertTrue(shouldPreferLocalDrawable("  ", Res.drawable.platform_linux))
        assertFalse(shouldPreferLocalDrawable("https://example.org/custom-photo.png", Res.drawable.platform_linux))
        assertFalse(shouldPreferLocalDrawable("", null))
    }
}
