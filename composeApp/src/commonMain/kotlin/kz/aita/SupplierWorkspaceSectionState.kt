package kz.aita

internal const val SUPPLIER_WORKSPACE_SECTION_STATE_KEY = "supplier_workspace_section_v1"

/** Explicit cross-workspace navigation reveals results; background filter persistence does not. */
internal fun supplierWorkspaceNavigationSeed(
    resultSectionId: String,
    revealResults: Boolean,
    vararg filters: Pair<String, String>
): List<Pair<String, String>> = buildList {
    addAll(filters)
    if (revealResults) add(SUPPLIER_WORKSPACE_SECTION_STATE_KEY to resultSectionId)
}
