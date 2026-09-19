package kz.aita

import kotlinx.coroutines.*
import kotlin.test.*

class ActiveStoreSelectionCoordinatorTest {
    private class Fixture(timeoutMillis: Long = 60_000L) {
        var owner = ActiveStoreOwner("account-a", 1L)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val disk = mutableMapOf<String, SavedActiveStoreChoice>()
        val shown = mutableListOf<String?>()
        val calls = mutableListOf<Pair<ActiveStoreOwner?, String?>>()
        val acknowledgements = mutableListOf<ActiveStoreSelectionSnapshot>()
        var loadAction: suspend (ActiveStoreOwner) -> SavedActiveStoreChoice? = { disk[it.accountId] }
        var persistAction: suspend (ActiveStoreSelectionSnapshot) -> Unit = {}
        var sendAction: suspend (ActiveStoreSelectionSnapshot) -> ActiveStoreSyncOutcome = { ActiveStoreSyncOutcome.SAVED }
        val controller: ActiveStoreSelectionCoordinator by lazy { ActiveStoreSelectionCoordinator(
            scope, { it == owner }, { loadAction(it) },
            { selected ->
                persistAction(selected)
                selected.owner?.let { disk[it.accountId] = SavedActiveStoreChoice(selected.choice, selected.pendingSync) }
            },
            { if (controller.isCurrent(it)) shown += it.choice.storeId },
            { calls += it.owner to it.choice.storeId; sendAction(it) },
            { acknowledgements += it }, timeoutMillis
        ) }
        suspend fun adopt(store: String? = "A") { controller.adopt(owner, store) }
        fun select(store: String?, sync: Boolean = true) = controller.select(owner, store, sync)
    }

    private fun fixture(timeoutMillis: Long = 60_000L, block: suspend Fixture.() -> Unit) = runBlocking {
        val f = Fixture(timeoutMillis)
        try { withTimeout(5_000L) { f.block() } } finally { f.scope.cancel() }
    }

    @Test fun storageFailureDoesNotBlockAccountSyncAndIsRetriedAfterAcknowledgement() = fixture {
        adopt("A")
        persistAction = { error("quota exceeded") }
        select("B").join()
        assertEquals(listOf("B"), calls.map { it.second })
        assertEquals("B", shown.last())
        assertFalse(controller.snapshot.pendingSync)
        assertEquals("B", acknowledgements.last().choice.storeId)
        persistAction = {}
        controller.retryPending().join()
        assertEquals("B", disk.getValue(owner.accountId).choice.storeId)
        assertFalse(disk.getValue(owner.accountId).pendingSync)
        assertEquals(1, calls.size, "retry local storage without echoing a saved selection")
    }

    @Test fun staleOfflineDeletedStoreCannotEraseParentSelectedOnAnotherDevice() = fixture {
        var serverStore: String? = "parent"
        disk[owner.accountId] = SavedActiveStoreChoice(ActiveStoreChoice("deleted", parentStoreId = "parent"), true)
        sendAction = { selected ->
            if (selected.choice.storeId == "deleted") ActiveStoreSyncOutcome.REJECTED
            else { serverStore = selected.choice.storeId; ActiveStoreSyncOutcome.SAVED }
        }
        adopt("parent")
        controller.retryPending().join()
        assertEquals("parent", serverStore)
        controller.acceptRemote(owner, serverStore, controller.snapshot.revision)
        assertEquals("parent", shown.last())
        assertEquals(listOf("deleted"), calls.map { it.second })
    }

    @Test fun anotherDevicesSelectionIsPublishedAndSavedWithoutEchoingItBack() = fixture {
        adopt("A")
        controller.acceptRemote(owner, "B", controller.snapshot.revision)
        assertEquals("B", shown.last())
        assertEquals("B", disk.getValue(owner.accountId).choice.storeId)
        assertTrue(calls.isEmpty())
    }

    @Test fun profileThatStartedBeforeLocalClickCannotUndoTheClickEvenAfterItsAcknowledgement() = fixture {
        adopt("A")
        val revision = controller.snapshot.revision
        select("B").join()
        controller.acceptRemote(owner, "A", revision)
        assertEquals("B", shown.last())
    }

    @Test fun remoteSnapshotCannotDiscardAnOfflineSelection() = fixture {
        adopt("A")
        sendAction = { ActiveStoreSyncOutcome.RETRY_LATER }
        select("B").join()
        controller.acceptRemote(owner, "C", controller.snapshot.revision)
        assertEquals("B", shown.last())
        assertTrue(controller.snapshot.pendingSync)
    }

    @Test fun authoritativeStartupUsesServerSelectionButKeepsOfflinePendingIntent() = fixture {
        disk[owner.accountId] = SavedActiveStoreChoice(ActiveStoreChoice("deleted"), false)
        controller.adopt(owner, "parent", preferServerSelection = true)
        assertEquals("parent", shown.last())
        owner = owner.copy(sessionGeneration = 2L)
        disk[owner.accountId] = SavedActiveStoreChoice(ActiveStoreChoice("offline-branch"), true)
        sendAction = { ActiveStoreSyncOutcome.RETRY_LATER }
        controller.adopt(owner, "parent", preferServerSelection = true)
        assertEquals("offline-branch", shown.last())
        assertTrue(controller.snapshot.pendingSync)
    }

    @Test fun remoteRepairReplacesAnAlreadyLoadedEmptySelectionWithoutRelogin() = fixture {
        adopt(null)
        controller.acceptRemote(owner, "parent", controller.snapshot.revision)
        assertEquals("parent", shown.last())
        val oldOwner = owner
        owner = ActiveStoreOwner("account-b", 2L)
        adopt("other")
        controller.acceptRemote(oldOwner, "private", controller.snapshot.revision)
        assertEquals("other", shown.last())
    }

    @Test fun delayedProfileRefreshCannotReapplyOldStore() = fixture {
        adopt("A")
        select("B").join()
        adopt("A")
        assertEquals("B", controller.snapshot.choice.storeId)
        assertEquals("B", shown.last())
        assertEquals("B", disk[owner.accountId]?.choice?.storeId)
    }

    @Test fun savedLocalSelectionWinsOnRestartEvenWhenServerProfileIsOld() = fixture {
        disk[owner.accountId] = SavedActiveStoreChoice(ActiveStoreChoice("B"), false)
        adopt("A")
        assertEquals("B", shown.last())
        assertTrue(calls.isEmpty())
    }

    @Test fun fastBThenAReturnIsNotLostBeforeBFinishes() = fixture {
        adopt("A")
        val gate = CompletableDeferred<Unit>()
        sendAction = { if (it.choice.storeId == "B") gate.await(); ActiveStoreSyncOutcome.SAVED }
        val b = select("B")
        val a = select("A")
        assertEquals("A", controller.snapshot.choice.storeId)
        gate.complete(Unit)
        joinAll(b, a)
        assertEquals(listOf("B", "A"), calls.map { it.second })
        assertEquals(listOf("A"), acknowledgements.map { it.choice.storeId })
        assertEquals("A", disk[owner.accountId]?.choice?.storeId)
    }

    @Test fun intermediateSelectionsAreCoalescedAndRequestsAreOrdered() = fixture {
        adopt("A")
        val gate = CompletableDeferred<Unit>()
        sendAction = { if (it.choice.storeId == "B") gate.await(); ActiveStoreSyncOutcome.SAVED }
        val jobs = listOf(select("B"), select("C"), select("D"))
        assertEquals(listOf("B"), calls.map { it.second })
        gate.complete(Unit)
        jobs.joinAll()
        assertEquals(listOf("B", "D"), calls.map { it.second })
        assertEquals("D", shown.last())
        assertFalse(controller.snapshot.pendingSync)
    }

    @Test fun transientFailureKeepsVisibleSelectionAndDurablePendingSync() = fixture {
        adopt("A")
        sendAction = { ActiveStoreSyncOutcome.RETRY_LATER }
        select("B").join()
        assertEquals("B", shown.last())
        assertTrue(controller.snapshot.pendingSync)
        assertTrue(disk.getValue(owner.accountId).pendingSync)
        sendAction = { ActiveStoreSyncOutcome.SAVED }
        controller.retryPending().join()
        assertEquals(listOf("B", "B"), calls.map { it.second })
        assertEquals("B", shown.last())
        assertFalse(disk.getValue(owner.accountId).pendingSync)
    }

    @Test fun pendingSelectionIsRetriedAfterProcessRestoration() = fixture {
        disk[owner.accountId] = SavedActiveStoreChoice(ActiveStoreChoice("B"), true)
        adopt("A")
        controller.retryPending().join()
        assertEquals("B", shown.last())
        assertEquals(listOf("B"), calls.map { it.second })
        assertFalse(controller.snapshot.pendingSync)
    }

    @Test fun lateHydrationDoesNotOverwriteAnAlreadyClaimedClick() = fixture {
        val gate = CompletableDeferred<Unit>()
        loadAction = { gate.await(); SavedActiveStoreChoice(ActiveStoreChoice("A"), false) }
        val restoration = scope.launch { adopt() }
        val selection = select("B")
        gate.complete(Unit)
        joinAll(restoration, selection)
        assertEquals("B", shown.last())
        assertEquals("B", disk.getValue(owner.accountId).choice.storeId)
    }

    @Test fun slowDiskWriteCannotEchoPreviousStoreOverNewChoice() = fixture {
        adopt("A")
        val gate = CompletableDeferred<Unit>()
        persistAction = { if (it.choice.storeId == "B") gate.await() }
        val b = select("B")
        val c = select("C")
        assertEquals("C", controller.snapshot.choice.storeId)
        gate.complete(Unit)
        joinAll(b, c)
        assertEquals("C", shown.last())
        assertEquals("C", disk.getValue(owner.accountId).choice.storeId)
        assertEquals(listOf("C"), calls.map { it.second })
    }

    @Test fun previousAccountResponseCannotAcknowledgeOrChangeNewAccount() = fixture {
        adopt("A")
        val oldOwner = owner
        val gate = CompletableDeferred<Unit>()
        sendAction = { if (it.owner == oldOwner) gate.await(); ActiveStoreSyncOutcome.SAVED }
        val oldRequest = select("B")
        owner = ActiveStoreOwner("account-b", 2L)
        adopt("X")
        gate.complete(Unit)
        oldRequest.join()
        assertEquals("X", shown.last())
        assertEquals(owner, controller.snapshot.owner)
        assertTrue(acknowledgements.isEmpty())
        assertTrue(disk.getValue(oldOwner.accountId).pendingSync)
    }

    @Test fun sameAccountNewSessionRejectsOldResponse() = fixture {
        adopt("A")
        val oldOwner = owner
        val gate = CompletableDeferred<Unit>()
        sendAction = { if (it.owner == oldOwner) gate.await(); ActiveStoreSyncOutcome.SAVED }
        val oldRequest = select("B")
        owner = owner.copy(sessionGeneration = 2L)
        adopt("A")
        gate.complete(Unit)
        oldRequest.join()
        controller.retryPending().join()
        assertEquals("B", shown.last())
        assertTrue(acknowledgements.all { it.owner == owner })
        assertEquals(listOf(1L, 2L), calls.map { it.first?.sessionGeneration })
    }

    @Test fun explicitNoStoreSurvivesProfileRefreshAndRestoration() = fixture {
        adopt("A")
        select(null).join()
        adopt("A")
        assertNull(shown.last())
        assertTrue(controller.snapshot.choice.explicitNone)
        assertTrue(disk.getValue(owner.accountId).choice.explicitNone)
    }

    @Test fun permissionDenialNeverClearsTheOtherDevicesServerChoice() = fixture {
        adopt("A")
        sendAction = { if (it.choice.storeId == "B") ActiveStoreSyncOutcome.REJECTED else ActiveStoreSyncOutcome.SAVED }
        select("B").join()
        assertNull(shown.last())
        assertFalse(controller.snapshot.choice.explicitNone)
        assertEquals(listOf("B"), calls.map { it.second })
        assertFalse(controller.snapshot.pendingSync)
    }

    @Test fun stalePermissionDenialDoesNotClearANewerPermittedStore() = fixture {
        adopt("A")
        val gate = CompletableDeferred<Unit>()
        sendAction = {
            if (it.choice.storeId == "B") { gate.await(); ActiveStoreSyncOutcome.REJECTED }
            else ActiveStoreSyncOutcome.SAVED
        }
        val b = select("B"); val c = select("C")
        gate.complete(Unit); joinAll(b, c)
        assertEquals("C", shown.last())
        assertEquals(listOf("B", "C"), calls.map { it.second })
    }

    @Test fun logoutClearsLocallyWithoutSendingAnOldAccountsPreference() = fixture {
        adopt("A")
        controller.select(null, null, syncServer = false).join()
        assertNull(shown.last())
        assertNull(controller.snapshot.owner)
        assertTrue(calls.isEmpty())
    }

    @Test fun boundedStallReleasesSyncForTheNextAttempt() = fixture(timeoutMillis = 15L) {
        adopt("A")
        sendAction = { awaitCancellation() }
        select("B").join()
        assertTrue(controller.snapshot.pendingSync)
        assertEquals("B", shown.last())
        sendAction = { ActiveStoreSyncOutcome.SAVED }
        controller.retryPending().join()
        assertFalse(controller.snapshot.pendingSync)
    }

    @Test fun healthyIdleRetriesDoNotContinuouslyRewritePreferences() = fixture {
        adopt("A")
        var writes = 0
        persistAction = { writes++ }
        repeat(10) { controller.retryPending().join() }
        assertEquals(0, writes)
        assertTrue(calls.isEmpty())
    }
    @Test fun onlyDefinitiveRejectionClearsSelection() {
        assertEquals(ActiveStoreSyncOutcome.SAVED, activeStoreSyncOutcome(false, false, 200))
        listOf(400, 403).forEach { assertEquals(ActiveStoreSyncOutcome.REJECTED, activeStoreSyncOutcome(true, false, it)) }
        listOf(null, 401, 404, 408, 409, 429, 500, 502, 503, 504).forEach {
            assertEquals(ActiveStoreSyncOutcome.RETRY_LATER, activeStoreSyncOutcome(true, false, it))
        }
        listOf(400, 403, 404, 503).forEach {
            assertEquals(ActiveStoreSyncOutcome.RETRY_LATER, activeStoreSyncOutcome(true, true, it))
        }
    }

}
