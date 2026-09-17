package kz.aita

import kotlinx.coroutines.flow.*

/** Identical split-pane rules for every cart; higher slots never alias the original fifth. */
open class CartNavigationWorkspace(private val type: Int, private val integrated: Boolean = false) {
    private val selected = MutableStateFlow(0)
    val ClientId = selected.asStateFlow()
    private val left = MutableStateFlow<Map<Int, MutableStateFlow<List<NavigationScreenModel.Transaction>>>>(emptyMap())
    private val right = MutableStateFlow<Map<Int, MutableStateFlow<List<NavigationScreenModel.Transaction>>>>(emptyMap())
    private var narrow: Boolean? = null
    private var boundScope: CartScope? = null
    private fun state(id: Int, isLeft: Boolean): MutableStateFlow<List<NavigationScreenModel.Transaction>> {
        require(validCartSlot(type, id))
        val map = if (isLeft) left else right
        val root = if (isLeft) NavigationScreenModel.Transaction.Cart else NavigationScreenModel.Transaction.Selection
        return map.updateAndGet { old -> if (id in old) old else old + (id to MutableStateFlow(listOf(root))) }.getValue(id)
    }
    private fun usable() = !integrated || (boundScope != null && boundScope == DynamicCarts.captureScope())
    fun screens(id: Int, isNarrowScreen: Boolean): StateFlow<List<NavigationScreenModel.Transaction>> = state(id, isNarrowScreen)
    suspend fun setClientId(id: Int) {
        require(validCartSlot(type, id)); if (!usable()) return
        if (integrated && !DynamicCarts.reveal(type, id)) return
        selected.value = id
    }
    suspend fun go(model: NavigationScreenModel.Transaction, isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, remove: Boolean = false) {
        if (usable()) goTo(selected.value, isNarrowScreen, model, remove)
    }
    private fun goTo(id: Int, isLeft: Boolean, model: NavigationScreenModel.Transaction, remove: Boolean = false) {
        state(id, isLeft).update { old ->
            if (old.lastOrNull()?.route == model.route) old else {
                val trail = if (remove && old.size > 1) old.dropLast(1) else old
                listOf(trail.first()) + trail.drop(1).takeLast(30) + model
            }
        }
    }
    suspend fun pop(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen, navigateAfterwards: NavigationScreenModel.Transaction? = null) {
        if (!usable()) return
        val id = selected.value
        if (isNarrowScreen && state(id, false).value.lastOrNull()?.route == state(id, true).value.lastOrNull()?.route)
            state(id, false).update { if (it.size > 1) it.dropLast(1) else it }
        state(id, isNarrowScreen).update { if (it.size > 1) it.dropLast(1) else it }
        navigateAfterwards?.let { goTo(id, isNarrowScreen, it) }
    }
    suspend fun clear(isNarrowScreen: Boolean = AppConfiguration.stateValues.isNarrowScreen) {
        if (usable()) clearAt(selected.value, isNarrowScreen)
    }
    suspend fun clearSlot(id: Int, isNarrowScreen: Boolean) { if (usable()) clearAt(id, isNarrowScreen) }
    private fun clearAt(id: Int, isLeft: Boolean, model: NavigationScreenModel.Transaction = if (isLeft) NavigationScreenModel.Transaction.Cart else NavigationScreenModel.Transaction.Selection) {
        state(id, isLeft).value = listOf(model)
    }
    fun isVeryFirstScreen(isNarrowScreen: Boolean, clientId: Int) = state(clientId, isNarrowScreen).value.size <= 1
    suspend fun resetCart(id: Int) { clearAt(id, true); clearAt(id, false) }
    suspend fun resetAll() { selected.value = 0; (left.value.keys + right.value.keys).forEach { resetCart(it) }; left.value = emptyMap(); right.value = emptyMap(); narrow = null }
    internal suspend fun bind(owner: CartScope?) { resetAll(); boundScope = owner }
    internal fun persistentSnapshot(): PersistedTransactionNavigationSectionDataModel {
        val used = maxOf(selected.value, (left.value.keys + right.value.keys).maxOrNull() ?: 0) + 1
        val count = maxOf(INITIAL_CART_SLOTS, used, if (integrated) DynamicCarts.counts.value[type] else 2)
        require(count <= MAX_CART_SLOTS)
        return PersistedTransactionNavigationSectionDataModel(selected.value,
            (0 until count).map { state(it, true).value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Cart) },
            (0 until count).map { state(it, false).value.toCompactPersistentRoutes(NavigationScreenModel.Transaction.Selection) }, count)
    }
    internal suspend fun restorePersistentSnapshot(snapshot: PersistedTransactionNavigationSectionDataModel) {
        require(validCartSlot(type, snapshot.clientId))
        require(snapshot.left.size <= MAX_CART_SLOTS && snapshot.right.size <= MAX_CART_SLOTS)
        require(snapshot.slotCount == null || snapshot.slotCount in INITIAL_CART_SLOTS..MAX_CART_SLOTS)
        val meaningful = maxOf(snapshot.left.indexOfLast { it.size > 1 }, snapshot.right.indexOfLast { it.size > 1 }, snapshot.clientId)
        val count = maxOf(INITIAL_CART_SLOTS, snapshot.slotCount ?: 2, meaningful + 1,
            if (integrated) DynamicCarts.counts.value[type] else 2)
        if (integrated && !DynamicCarts.reveal(type, count - 1)) return
        for (id in 0 until count) {
            state(id, true).value = snapshot.left.getOrNull(id).toPersistentTransactionStack(NavigationScreenModel.Transaction.Cart)
            state(id, false).value = snapshot.right.getOrNull(id).toPersistentTransactionStack(NavigationScreenModel.Transaction.Selection)
        }
        selected.value = snapshot.clientId
        narrow = null
    }
    suspend fun init(isNarrowScreen: Boolean) {
        if (narrow == isNarrowScreen) return
        narrow = isNarrowScreen
        (left.value.keys + right.value.keys).forEach { id ->
            val source = state(id, !isNarrowScreen).value
            if (source.size > 1) {
                val root = if (isNarrowScreen) NavigationScreenModel.Transaction.Cart else NavigationScreenModel.Transaction.Selection
                state(id, isNarrowScreen).value = listOf(root) + source.drop(1).filter { it.route != root.route }
            }
            clearAt(id, !isNarrowScreen)
        }
    }
    val LeftClient1 get() = screens(0, true)
    val LeftClient2 get() = screens(1, true)
    val LeftClient3 get() = screens(2, true)
    val LeftClient4 get() = screens(3, true)
    val LeftClient5 get() = screens(4, true)
    val RightClient1 get() = screens(0, false)
    val RightClient2 get() = screens(1, false)
    val RightClient3 get() = screens(2, false)
    val RightClient4 get() = screens(3, false)
    val RightClient5 get() = screens(4, false)
    suspend fun clearLeftClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) = clearAt(0, true, model)
    suspend fun clearLeftClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) = clearAt(1, true, model)
    suspend fun clearLeftClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) = clearAt(2, true, model)
    suspend fun clearLeftClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) = clearAt(3, true, model)
    suspend fun clearLeftClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Cart) = clearAt(4, true, model)
    suspend fun clearRightClient1(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) = clearAt(0, false, model)
    suspend fun clearRightClient2(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) = clearAt(1, false, model)
    suspend fun clearRightClient3(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) = clearAt(2, false, model)
    suspend fun clearRightClient4(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) = clearAt(3, false, model)
    suspend fun clearRightClient5(model: NavigationScreenModel.Transaction = NavigationScreenModel.Transaction.Selection) = clearAt(4, false, model)
}
