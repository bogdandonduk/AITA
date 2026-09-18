@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package kz.aita

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import kotlinx.coroutines.flow.collect

/**
 * The editor, not a recomposed TextFieldValue, owns the live cursor, IME composition and scroll.
 * AITA's existing draft/payment owners still own accepted text. Never save this second state:
 * the caller already decides which drafts may survive recreation (passwords must not).
 */
internal class AitaTextFieldBridge(initialValue: TextFieldValue) {
    val state = TextFieldState(initialValue.text, initialValue.selection)
    private var parentValue = initialValue.editorSnapshot()
    private var observedValue = parentValue
    private var awaitingParentValue: TextFieldValue? = null

    fun editorValue(): TextFieldValue = TextFieldValue(state.text.toString(), state.selection)

    fun resetFromParent(value: TextFieldValue) {
        parentValue = value.editorSnapshot()
        awaitingParentValue = null
        setEditorValue(parentValue)
    }

    fun updateFromParent(value: TextFieldValue) {
        val incoming = value.editorSnapshot()
        val awaiting = awaitingParentValue
        if (incoming == parentValue && awaiting == null) return
        // An acknowledgement of edit A must not overwrite edit B entered before recomposition.
        val staleAcknowledgement = awaiting != null && incoming == awaiting && editorValue() != awaiting
        parentValue = incoming
        awaitingParentValue = null
        if (!staleAcknowledgement) setEditorValue(incoming)
    }

    fun reportEditorChange(accept: (TextFieldValue) -> TextFieldValue): Boolean {
        val current = editorValue()
        if (current == observedValue) return false
        observedValue = current
        val accepted = accept(current).editorSnapshot()
        setEditorValue(accepted)
        // A parent may cap a payment to the SAME previously accepted amount. Reconcile after
        // its next composition even when that parent String has not changed.
        awaitingParentValue = accepted
        return true
    }

    private fun setEditorValue(value: TextFieldValue) {
        if (editorValue() != value) {
            state.edit {
                if (asCharSequence().toString() != value.text) replace(0, length, value.text)
                selection = value.selection
            }
        }
        observedValue = editorValue()
    }
}

private fun TextFieldValue.editorSnapshot() = TextFieldValue(text, selection)

/** Apply formatting only to the output buffer, never to stored text or password contents. */
internal fun TextFieldBuffer.applyAitaVisualTransformation(
    transformation: (TextFieldValue) -> TransformedText
) {
    val original = asCharSequence().toString()
    val transformed = transformation(TextFieldValue(original, selection))
    val output = transformed.text
    val offsets = (0..original.length).map {
        transformed.offsetMapping.originalToTransformed(it).coerceIn(0, output.length)
    }
    // All AITA transformations are monotonic, including the one-character password mask.
    // Small per-character changes retain native cursor stops; replacing the whole password
    // with one output edit would incorrectly make its entire length one cursor stop.
    if (offsets.zipWithNext().all { (a, b) -> a <= b }) {
        for (index in original.indices.reversed()) {
            val replacement = output.text.substring(offsets[index], offsets[index + 1])
            if (replacement != original[index].toString()) replace(index, index + 1, replacement)
        }
        if (offsets.first() > 0) replace(0, 0, output.text.substring(0, offsets.first()))
        if (offsets.last() < output.length) append(output.text.substring(offsets.last()))
    } else {
        // A malformed future mapping must never expose the underlying password. Prefer its
        // complete formatted output even though this fallback loses intermediate cursor stops.
        replace(0, length, output.text)
    }
    output.spanStyles.forEach { span ->
        if (span.start < span.end && span.end <= length) addStyle(span.item, span.start, span.end)
    }
}

@Composable
internal fun AitaEditableText(
    identityKey: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> TextFieldValue,
    modifier: Modifier,
    enabled: Boolean,
    readOnly: Boolean,
    keyboardOptions: KeyboardOptions,
    onKeyboardAction: KeyboardActionHandler?,
    textStyle: TextStyle,
    visualTransformation: (TextFieldValue) -> TransformedText,
    singleLine: Boolean,
    cursorBrush: Brush,
    inputFilter: ((String) -> Boolean)?,
    inputTransform: ((String) -> String)?,
    onSubmitText: ((String) -> Boolean)? = null,
    resetRevision: Int = 0,
    decorationBox: @Composable (@Composable () -> Unit) -> Unit
) {
    val bridge = remember(identityKey) { AitaTextFieldBridge(value) }
    val accept by rememberUpdatedState(onValueChange)
    val submit by rememberUpdatedState(onSubmitText)
    var acceptanceRevision by remember(bridge) { mutableIntStateOf(0) }
    var appliedResetRevision by remember(bridge) { mutableIntStateOf(resetRevision) }
    // Read the revision in composition: rejected/capped input must also get a reconciliation
    // pass when the parent value stays unchanged.
    @Suppress("UNUSED_VARIABLE") val revision = acceptanceRevision
    SideEffect {
        if (appliedResetRevision != resetRevision) {
            bridge.resetFromParent(value)
            appliedResetRevision = resetRevision
        } else bridge.updateFromParent(value)
    }
    LaunchedEffect(bridge) {
        snapshotFlow { bridge.editorValue() }.collect {
            if (bridge.reportEditorChange(accept)) acceptanceRevision++
        }
    }
    BasicTextField(
        state = bridge.state,
        modifier = if (onSubmitText == null) modifier else modifier.onPreviewKeyEvent { event ->
            if (enabled && !readOnly && event.type == KeyEventType.KeyDown &&
                (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Tab)) {
                // Flush the live editor before Enter, including scanners that deliver the
                // entire barcode and terminator before the parent has recomposed.
                if (bridge.reportEditorChange(accept)) acceptanceRevision++
                if (submit?.invoke(bridge.editorValue().text) == true) {
                    bridge.resetFromParent(TextFieldValue(""))
                    acceptanceRevision++
                    true
                } else false
            } else false
        },
        enabled = enabled,
        readOnly = readOnly,
        inputTransformation = InputTransformation {
            val original = asCharSequence().toString()
            // Handle-only motion is not a text edit, and must remain possible even in a field
            // whose old draft no longer passes today's validation rule.
            if (original != originalText.toString()) {
                val transformed = inputTransform?.invoke(original) ?: original
                if (inputFilter?.invoke(transformed) == false) {
                    revertAllChanges()
                } else if (transformed != original) {
                    val oldSelection = selection
                    replace(0, length, transformed)
                    selection = TextRange(
                        oldSelection.start.coerceIn(0, length),
                        oldSelection.end.coerceIn(0, length)
                    )
                }
            }
        },
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(),
        cursorBrush = cursorBrush,
        outputTransformation = OutputTransformation { applyAitaVisualTransformation(visualTransformation) },
        decorator = { innerTextField -> decorationBox(innerTextField) }
    )
}
