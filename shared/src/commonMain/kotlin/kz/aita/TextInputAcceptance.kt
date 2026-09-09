package kz.aita

/** Reject an edit before notifying a form owner: visible text and submitted text must agree. */
fun dispatchAcceptedTextInput(
    text: String,
    accepts: ((String) -> Boolean)?,
    onValueChange: ((String, () -> Unit) -> Unit)?,
    applyChange: () -> Unit
): Boolean {
    if (accepts != null && !accepts(text)) return false
    if (onValueChange != null) onValueChange(text, applyChange) else applyChange()
    return true
}
