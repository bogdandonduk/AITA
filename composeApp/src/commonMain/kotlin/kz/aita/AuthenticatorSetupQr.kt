package kz.aita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.aita.auth.AitaTotpSetupDataModel
import kotlin.math.floor

/** QR rendering stays local: no external QR service receives the server-provided setup URI. */
@Composable
internal fun AppConfiguration.AuthenticatorSetupQr(setup: AitaTotpSetupDataModel) {
    val matrix by key(setup.setupId, setup.otpauthUri) {
        produceState<AitaQrMatrix?>(null) {
            value = withContext(Dispatchers.Default) {
                if (!setup.otpauthUri.startsWith("otpauth://totp/")) null else
                    try { AitaQrCode.encode(setup.otpauthUri) } catch (_: IllegalArgumentException) { null }
            }
        }
    }
    val description = authUiText("Authenticator setup QR code", "QR-код настройки аутентификатора", "Аутентификатор баптау QR коды", "Аутентификаторду жөндөөчү QR код")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        matrix?.let { qr ->
            Canvas(Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f)
                .semantics { contentDescription = description }) {
                // White paper in both themes, square black modules, no rounding/logo over the data.
                drawRect(Color.White)
                val modules = qr.size + 8
                val step = floor(minOf(size.width, size.height) / modules).coerceAtLeast(1f)
                val left = floor((size.width - modules * step) / 2) + 4 * step
                val top = floor((size.height - modules * step) / 2) + 4 * step
                for (y in 0 until qr.size) for (x in 0 until qr.size) if (qr[x, y]) {
                    drawRect(Color.Black, Offset(left + x * step, top + y * step), Size(step, step))
                }
            }
        } ?: Text(authUiText("Use the setup key below", "Используйте ключ ниже", "Төмендегі кілтті пайдаланыңыз", "Төмөнкү жөндөө ачкычын колдонуңуз"),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
    }
}
