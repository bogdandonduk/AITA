package kz.aita

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

@Stable internal class SupplierPickingSession(
    val owner: SupplierPickingScope, private val scope: CoroutineScope
) {
    var entries by mutableStateOf<Map<String, SupplierPickingEntry>>(emptyMap()); private set
    var loaded by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var savedRevision by mutableStateOf(0); private set
    suspend fun load() {
        if (busy) return
        busy = true
        try { val result = loadSupplierPicking(owner); if (owner.isCurrent()) { entries = result; loaded = true; failed = false } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (owner.isCurrent()) failed = true }
        finally { busy = false }
    }
    fun save(lines: List<SupplierPickingLine>, changes: Map<String, Double>, after: () -> Unit = {}) {
        if (!loaded || busy || !owner.isCurrent()) return
        busy = true
        scope.launch {
            try {
                val result = saveSupplierPicking(owner, lines, changes)
                if (owner.isCurrent()) { entries = result; failed = false; savedRevision++; after() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owner.isCurrent()) failed = true }
            finally { busy = false }
        }
    }
}

@Composable internal fun AppConfiguration.rememberSupplierPickingSession(run: SupplierDispatchWorkspaceRunUiModel): SupplierPickingSession {
    val account = stateValues.userAccount?.id.orEmpty()
    val generation = currentAuthenticatedSessionGeneration()
    val scope = rememberCoroutineScope()
    val session = remember(account, generation, run.supplierId, run.key) {
        SupplierPickingSession(SupplierPickingScope(account, run.supplierId) {
            authenticatedSessionGenerationIsCurrent(generation) && userAccountState.payloadValue?.id == account &&
                activeSupplierProfileIdState.value?.takeIf { it.isNotBlank() }?.let { it == run.supplierId } != false
        }, scope)
    }
    val host = NavigationScreenModel.Supplier.Dispatch.Main
    val legacyKey = "supplier_picking:$account:${run.supplierId}:${run.key}"
    LaunchedEffect(session) { session.load() }
    LaunchedEffect(session.loaded, run.bundles, run.contractSafetyReady) {
        if (!session.loaded || session.busy || !session.owner.isCurrent() || !run.contractSafetyReady || run.bundles.isEmpty()) return@LaunchedEffect
        val old = decodePickingMarks(host.state.value[legacyKey])
        if (old.isNotEmpty()) {
            val lines = supplierPickingRows(run.bundles).map { supplierPickingLine(it.line) }
            val migration = lines.filter { old[it.key] == it.signature && it.key !in session.entries && it.quantity != null }
                .associate { it.key to it.quantity!!.total }
            // Only consume the line identities actually inspected. A partial run read must not erase
            // older checks for an order whose lines have not arrived on this device yet.
            val inspectedKeys = lines.map { it.key }.toSet()
            val remainingMarks = old.filterKeys { it !in inspectedKeys }
            suspend fun finishMigration() {
                if (!session.owner.isCurrent()) return
                if (remainingMarks.isEmpty()) host.removeState(legacyKey)
                else host.setState(legacyKey to jsonBase.encodeToString(remainingMarks))
            }
            if (migration.isNotEmpty()) session.save(lines, migration) { scope.launch { finishMigration() } }
            else if (remainingMarks.size != old.size) finishMigration()
        }
    }
    return session
}
