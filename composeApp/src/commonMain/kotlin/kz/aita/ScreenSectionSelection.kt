package kz.aita

/** IDs, not translated labels or list positions, identify a screen section. */
internal fun resolveScreenSectionId(
    selectedId: String?,
    availableIds: List<String>,
    defaultId: String = availableIds.firstOrNull().orEmpty()
): String = selectedId?.takeIf { it in availableIds }
    ?: defaultId.takeIf { it in availableIds }
    ?: availableIds.firstOrNull().orEmpty()

/** A deleted final row must not leave an otherwise populated history on an empty page. */
internal fun boundedSectionPage(page: Int, totalItems: Int, pageSize: Int): Int {
    val size = pageSize.coerceAtLeast(1)
    val lastPage = (totalItems.coerceAtLeast(1) - 1) / size
    return page.coerceIn(0, lastPage)
}
