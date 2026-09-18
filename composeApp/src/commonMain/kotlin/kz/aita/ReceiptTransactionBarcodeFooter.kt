package kz.aita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Kept inside the white receipt, after the final text, in every receipt preview. */
@Composable
internal fun AppConfiguration.ReceiptTransactionBarcodeFooter(snapshot: TransactionReceiptSnapshotDataModel) {
    val payload = remember(snapshot.transaction.id, snapshot.transaction.clientOperationId) { snapshot.transaction.receiptBarcodePayload() } ?: return
    Spacer(Modifier.height(12.dp))
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color.White)) {
        // Round each module to whole screen pixels, preserving crisp bars at fractional UI scales.
        // Quiet zones are encoded in the geometry; never crop or stretch individual bars.
        val density = LocalDensity.current.density
        val modulePixels = density.roundToInt().coerceAtLeast(1).toFloat()
        val moduleDp = modulePixels / density
        val vertical = maxWidth.value < 297f * moduleDp
        val geometry = remember(payload, vertical) { transactionReceiptBarcodeGeometry(payload, 1f, 56f, vertical) }
        val description = "${stateValues.stringBarcode}: ${snapshot.transaction.id}"
        Canvas(Modifier.fillMaxWidth().height((geometry.height * moduleDp).dp).semantics { contentDescription = description }) {
            val unit = modulePixels
            val left = ((size.width - geometry.width * unit) / 2f).roundToInt().toFloat()
            geometry.bars.forEach { bar ->
                drawRect(Color.Black, Offset(left + bar.x * unit, bar.y * unit), Size(bar.width * unit, bar.height * unit))
            }
        }
    }
}
