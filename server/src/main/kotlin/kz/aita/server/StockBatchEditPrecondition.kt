package kz.aita.server

/** Checked while holding the same inventory lock as sales and stock transfers. */
internal fun stockBatchEditRevisionMatches(expected: Long?, current: Long): Boolean =
    expected == null || expected == current
