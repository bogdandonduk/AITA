package kz.aita.android

/** Use the full display as well as the window configuration: a tablet's narrow split-screen
 * is not a phone. Foldables are unrestricted when opened to a tablet-sized display. */
internal fun shouldLockPhoneToPortrait(
    displaySmallestWidthDp: Int,
    configurationSmallestWidthDp: Int,
    normalUiMode: Boolean,
    multiWindow: Boolean,
    pictureInPicture: Boolean,
    largeScreenConfiguration: Boolean = false
): Boolean = normalUiMode && !multiWindow && !pictureInPicture && !largeScreenConfiguration &&
    displaySmallestWidthDp in 1 until 600 && configurationSmallestWidthDp < 600
