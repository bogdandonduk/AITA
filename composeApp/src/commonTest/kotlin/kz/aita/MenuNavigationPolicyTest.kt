package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MenuNavigationPolicyTest {
    private val list = NavigationScreenModel.Menu.List
    private val account = NavigationScreenModel.Menu.UserAccount
    private val subscription = NavigationScreenModel.Menu.StoreSubscriptionPlans
    private val workers = NavigationScreenModel.Menu.Workers

    @Test fun recoveryHasRealNarrowBase() {
        assertEquals(listOf(list, subscription), normalizeMenuStack(listOf(subscription), list))
    }
    @Test fun recoveryHasRealWideBase() {
        assertEquals(listOf(account, subscription), normalizeMenuStack(listOf(subscription), account))
    }
    @Test fun narrowToWidePreservesSubscriptionAndBackDestination() {
        val (left, right) = adaptMenuStacks(listOf(list, subscription), listOf(account), false)
        assertEquals(listOf(list), left)
        assertEquals(listOf(account, subscription), right)
    }
    @Test fun wideToNarrowPreservesSubscriptionAndBackDestination() {
        val (left, right) = adaptMenuStacks(listOf(list), listOf(account, subscription), true)
        assertEquals(listOf(list, subscription), left)
        assertEquals(listOf(account), right)
    }
    @Test fun repeatedResizesKeepTrailAndNeverDuplicateBase() {
        var stacks: Pair<List<NavigationScreenModel.Menu>, List<NavigationScreenModel.Menu>> =
            listOf(list, workers, subscription) to listOf(account)
        repeat(12) {
            stacks = adaptMenuStacks(stacks.first, stacks.second, false)
            assertEquals(listOf(account, workers, subscription), stacks.second)
            stacks = adaptMenuStacks(stacks.first, stacks.second, true)
            assertEquals(listOf(list, workers, subscription), stacks.first)
        }
    }
    @Test fun legacySubscriptionRootIsRepairedBeforeTransfer() {
        val stacks = adaptMenuStacks(listOf(list), listOf(subscription), true)
        assertEquals(listOf(list, subscription), stacks.first)
    }
    @Test fun emptyAndRootOnlyStacksStayRootOnly() {
        assertEquals(listOf(list) to listOf(account), adaptMenuStacks(emptyList(), emptyList(), false))
        assertEquals(listOf(list) to listOf(account), adaptMenuStacks(listOf(list), listOf(account), true))
    }
    @Test fun obsoleteWorkRouteOpensExistingWorkersPage() {
        assertEquals(workers, persistentAppRouteToScreen("MenuWorkNavigationScreenModelRoute"))
        assertFalse(menuDestinationRequiresStoreSubscription(workers))
    }
}
