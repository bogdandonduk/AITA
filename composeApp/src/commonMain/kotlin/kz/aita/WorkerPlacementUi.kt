package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

@Composable
internal fun AppConfiguration.WorkerWorkplacePicker(worker: StoreWorkerDataModel) {
    val memberships by myWorkerMembershipsState.payload.collectAsState()
    val catalogue = stateValues.stores.orEmpty()
    val company = catalogue.findStoreOrBranch(worker.storeId)?.rootStoreId()
    val stores = (memberships.orEmpty() + worker).filter { it.isActive && it.userId == worker.userId &&
        catalogue.findStoreOrBranch(it.storeId)?.rootStoreId() == company }
        .flatMap { workerWorkplaces(it, catalogue) }.distinctBy { it.id }
    if (stores.isEmpty()) return
    val active = stateValues.activeStoreId
    val selectionScope by activeStoreSelectionScopeState.collectAsState()
    val shift by activeWorkshiftState.payload.collectAsState()
    var error by remember { mutableStateOf(false) }
    fun text(key: String) = workerPlacementMessage(key).extractLocalizedString(stateValues.appLanguage).orEmpty()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SimpleDropdownField(title = text("workplace"), selectedId = active?.takeIf { id -> stores.any { it.id == id } },
            options = stores.map { DropdownOption(it.id, it.name.extractLocalizedString(stateValues.appLanguage).orEmpty().ifBlank { it.publicId }) },
            placeholder = text("choose"), restoreLastChoice = false,
            onSelected = { id ->
                if (shift?.let { it.isActive && it.endedAtMillis == null && it.storeId != id } == true) error = true
                else { error = false; setActiveStoreId(id) }
            })
        Text(if (error) text("shift") else activeStoreScopeText(selectionScope), color = if (error) stateValues.ErrorColor else stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize)
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun AppConfiguration.WorkerPlacementEditor(worker: StoreWorkerDataModel) {
    val stores = stateValues.stores.orEmpty().flattenStoresWithBranches().distinctBy { it.id }
    val source = stores.firstOrNull { it.id == worker.storeId } ?: return
    val parent = source.parentStoreId ?: source.id
    val permissions = currentUserAssignableStorePermissions(source.id)
    if (!currentUserCanEditWorkerPermissions(source.id) || !permissions.containsAll(worker.permissions) ||
        (source.parentStoreId != null && !currentUserCanRemoveWorkers(source.id))) return
    val destinations = stores.filter { it.parentStoreId == parent && it.id != source.id &&
        currentUserCanInviteWorkers(it.id) && currentUserAssignableStorePermissions(it.id).containsAll(worker.permissions) }
    if (destinations.isEmpty()) return
    val scope = rememberCoroutineScope()
    val account = stateValues.userAccount?.id
    var expanded by rememberSaveable(account, worker.id) { mutableStateOf(false) }
    var destination by rememberSaveable(account, worker.id) { mutableStateOf<String?>(null) }
    var operation by rememberSaveable(account, worker.id, destination) { mutableStateOf(Uuid.random().toString()) }
    var busy by remember(account, worker.id) { mutableStateOf(false) }
    var message by remember(account, worker.id) { mutableStateOf<List<LocalizedStringDataModel>>(emptyList()) }
    fun text(key: String) = workerPlacementMessage(key).extractLocalizedString(stateValues.appLanguage).orEmpty()
    val assigning = source.parentStoreId == null
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actionButton(text = text(if (assigning) "assign" else "move"), iconPath = stateValues.drawablePathIconWorkers,
            autoLoading = false, confirmationRequired = false, onClick = { expanded = !expanded })
        if (expanded) {
            Text(text(if (assigning) "parent_help" else "move_help"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            SimpleDropdownField(title = text("workplace"), selectedId = destination,
                options = destinations.map { DropdownOption(it.id, it.name.extractLocalizedString(stateValues.appLanguage).orEmpty().ifBlank { it.publicId }) },
                placeholder = text("choose"), restoreLastChoice = false, onSelected = { if (!busy) { destination = it; message = emptyList() } })
            if (message.isNotEmpty()) Text(message.extractLocalizedString(stateValues.appLanguage).orEmpty(), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            actionButton(text = stateValues.stringConfirm, enabled = !busy, loading = busy, autoLoading = false, confirmationRequired = false,
                onClick = {
                    val target = destination
                    if (target == null || destinations.none { it.id == target }) message = workerPlacementMessage("invalid")
                    else if (!busy) {
                        busy = true; message = emptyList()
                        val generation = currentAuthenticatedSessionGeneration()
                        val command = WorkerPlacementCommand(operation, worker.id, worker.storeId, target)
                        scope.launch {
                            try {
                                val result = placeWorker(command, generation)
                                if (authenticatedSessionGenerationIsCurrent(generation)) {
                                    if (result is DataState.Success) { expanded = false; operation = Uuid.random().toString() }
                                    else message = result.message.orEmpty()
                                }
                            } finally { busy = false }
                        }
                    }
                })
        }
    }
}

internal fun filterWorkerDirectory(workers: List<StoreWorkerDataModel>, store: String, query: String): List<StoreWorkerDataModel> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    return workers.filter { it.isActive && (store == "all" || it.storeId == store) && words.all { word ->
        (listOf(it.displayName, it.userPublicId, it.phoneNumber, it.email, it.jobTitle, it.storePublicId) +
            it.storeName.map { name -> name.value }).any { value -> value.lowercase().contains(word) }
    } }.sortedWith(compareBy<StoreWorkerDataModel> { it.displayName.lowercase() }.thenBy { it.storeId }.thenBy { it.id })
}
