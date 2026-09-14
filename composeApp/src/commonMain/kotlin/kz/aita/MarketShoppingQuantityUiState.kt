package kz.aita

import androidx.compose.runtime.*

/** One transient editor; dependencies are live reads, not values from a previous composition.
 * The normal shopping sender is still the sole owner of durable commands and recovery.
 */
@Stable
internal class MarketShoppingQuantityUiState(
    val review: MarketShoppingLineReview,
    private val currentSnapshot: () -> MarketShoppingSnapshot?,
    private val canChange: () -> Boolean,
    private val changeLine: (MarketShoppingLineReview, Int) -> Boolean,
    private val onDismiss: () -> Unit
) {
    var draft by mutableStateOf(review.line.units.toString())
        private set
    var active by mutableStateOf(true)
        private set
    val enteredUnits: Int? get() = marketShoppingQuantityFromDraft(draft)
    val matches: Boolean get() = active && review.matches(currentSnapshot())
    val editable: Boolean get() = matches && canChange()
    val canSubmit: Boolean get() = active && canChange() &&
        review.quantityEditUnits(currentSnapshot(), draft) != null

    fun edit(value: String) {
        // Retain invalid pastes verbatim. Ignoring them would leave a previous valid value
        // actionable, while filtering/truncating could send a quantity the buyer never entered.
        if (active) draft = value
    }

    fun submit(): Boolean {
        if (!active || !canChange()) return false
        val units = review.quantityEditUnits(currentSnapshot(), draft) ?: return false
        // changeLine rechecks the owner/intent and sets its busy flag synchronously. It then
        // prepares the original command durably before network I/O. False leaves this draft open.
        if (!changeLine(review, units)) return false
        dismiss()
        return true
    }

    fun dismiss() {
        if (!active) return
        active = false
        onDismiss()
    }

    fun dispose() { active = false }
}
