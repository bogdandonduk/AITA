package kz.aita

internal const val SUPPLIER_CUSTOMERS_FILTER_STATE_KEY = "supplier_customers_filter_v2"
internal const val SUPPLIER_CUSTOMERS_SORT_STATE_KEY = "supplier_customers_sort_v2"
internal const val SUPPLIER_CONTRACT_STATUS_FILTER_STATE_KEY = "supplier_contract_status_filter_v2"
internal const val SUPPLIER_CONTRACT_SORT_STATE_KEY = "supplier_contract_sort_v2"
internal const val SUPPLIER_DISPATCH_FILTER_STATE_KEY = "supplier_dispatch_filter_v2"
internal const val SUPPLIER_DISPATCH_SORT_STATE_KEY = "supplier_dispatch_sort_v2"

internal suspend fun seedSupplierCustomersNavigation(
    searchQuery: String = "",
    filterId: String = SUPPLIER_CUSTOMERS_FILTER_ALL,
    sortId: String = SUPPLIER_CUSTOMERS_SORT_ACTION,
    revealResults: Boolean = true
) {
    NavigationScreenModel.Supplier.Customers.Main.setStates(*supplierWorkspaceNavigationSeed(
        resultSectionId = "partners",
        revealResults = revealResults,
        NavigationScreenModel.KEY_STATE_SEARCH_QUERY to searchQuery.trim(),
        SUPPLIER_CUSTOMERS_FILTER_STATE_KEY to filterId.ifBlank { SUPPLIER_CUSTOMERS_FILTER_ALL },
        SUPPLIER_CUSTOMERS_SORT_STATE_KEY to sortId.ifBlank { SUPPLIER_CUSTOMERS_SORT_ACTION }
    ).toTypedArray())
}

internal suspend fun seedSupplierContractsNavigation(
    searchQuery: String = "",
    statusFilter: String = SUPPLIER_CONTRACT_FILTER_ALL,
    sortId: String = SUPPLIER_CONTRACT_SORT_ACTION
) {
    NavigationScreenModel.Supplier.Contracts.Main.setStates(
        NavigationScreenModel.KEY_STATE_SEARCH_QUERY to searchQuery.trim(),
        SUPPLIER_CONTRACT_STATUS_FILTER_STATE_KEY to statusFilter.ifBlank { SUPPLIER_CONTRACT_FILTER_ALL },
        SUPPLIER_CONTRACT_SORT_STATE_KEY to sortId.ifBlank { SUPPLIER_CONTRACT_SORT_ACTION }
    )
}

internal suspend fun seedSupplierDispatchNavigation(
    searchQuery: String = "",
    laneFilter: String = SUPPLIER_DISPATCH_FILTER_ALL,
    sortId: String = SUPPLIER_DISPATCH_SORT_ACTION
) {
    NavigationScreenModel.Supplier.Dispatch.Main.setStates(
        NavigationScreenModel.KEY_STATE_SEARCH_QUERY to searchQuery.trim(),
        SUPPLIER_DISPATCH_FILTER_STATE_KEY to normalizedSupplierDispatchFilter(laneFilter),
        SUPPLIER_DISPATCH_SORT_STATE_KEY to normalizedSupplierDispatchSort(sortId)
    )
}
