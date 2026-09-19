package kz.aita

import kotlin.test.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class StoreArchitectureTest {
    private fun store(id: String = "existing", parent: String? = null, version: Int = 1) = StoreDataModel(
        id = id, parentStoreId = parent, userIds = listOf("owner"), storeTypeIds = emptyList(),
        name = listOf(LocalizedStringDataModel("en", "Store")), alias = emptyList(), description = emptyList(),
        companyForms = emptyList(), location = LocationDataModel(), phoneNumbers = emptyList(),
        emails = emptyList(), countryLocales = listOf("KZ"), createdAt = 1L, architectureVersion = version)

    @Test fun oldOfflineStoreDoesNotBecomeFreeManagementByDefault() {
        val legacyJson = JsonObject(jsonBase.parseToJsonElement(jsonBase.encodeToString(store())).jsonObject -
            setOf("architectureVersion", "branchType"))
        val old = jsonBase.decodeFromString<StoreDataModel>(legacyJson.toString())
        assertFalse(old.isManagementStore())
        assertTrue(old.supportsTransactions())
    }
    @Test fun freshManagementHasNoBranchTypeOrCheckout() {
        val parent = store("parent", version = 2)
        assertTrue(parent.isManagementStore())
        assertNull(parent.effectiveBranchType())
        assertFalse(parent.supportsTransactions())
        assertFalse(parent.isInternetBranch())
    }
    @Test fun bothBranchKindsOperateButOnlyInternetPublishes() {
        val physical = store("branch", "parent", 2)
        assertEquals(StoreBranchType.PHYSICAL, physical.effectiveBranchType())
        assertTrue(physical.supportsTransactions())
        assertFalse(physical.isInternetBranch())
        val internet = physical.copy(branchType = StoreBranchType.INTERNET)
        assertTrue(internet.supportsTransactions())
        assertTrue(internet.isInternetBranch())
        assertFalse(internet.isManagementStore())
    }
    @Test fun operationsAndCashAreBranchOnlyEvenWithQueryAndCase() {
        listOf("/Transactions/complete?retry=1", "/cashRegister/extract", "debtors/pay", "workshifts/start",
            "payments/integrations/provider").forEach { assertTrue(storeEndpointRequiresOperatingBranch(it), it) }
    }
    @Test fun warehouseAndHistoryRemainAccessibleAndAccountPaymentsStaySeparate() {
        listOf("stock/get", "stock/add", "stockBatches/move", "transactions/get", "transactions/search", "debtors/get",
            "workshifts/end", "payments/balance", "payments/topups", "workers/my/get").forEach {
            assertFalse(storeEndpointRequiresOperatingBranch(it), it)
        }
    }
    @Test fun architectureTermsHaveExactTranslationsInEveryLanguage() {
        listOf("store.management", "store.management_help", "store.branch_type", "store.branch.physical",
            "store.branch.internet", "store.branch_help", "store.operating_branch_required").forEach { key ->
            listOf("en", "ru", "kk", "ky", "tg", "uz").forEach { language ->
                assertFalse(EventMessages.renderExact(EventMessageReference(key), language).isNullOrBlank(), "$key/$language")
            }
        }
    }
}
