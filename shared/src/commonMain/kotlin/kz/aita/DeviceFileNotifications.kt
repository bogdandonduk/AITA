package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Local capability. Neither this object nor its path/action is a wire or persisted model. */
data class SavedPdfFile(
    val fileName: String,
    val folder: String,
    val open: suspend (isCurrent: () -> Boolean) -> ReceiptPlatformActionResult
)
class ReceiptActionOwner internal constructor(internal val generation: Long, private val userId: String?) {
    // Exporting cached business data is a local operation, not a cloud-token validation.
    // Session generations still invalidate late work on logout/login (including A -> B -> A).
    fun isCurrent(): Boolean = !userId.isNullOrBlank() && currentAuthenticatedSessionGeneration() == generation && userAccountState.payloadValue?.id == userId
}
fun captureReceiptActionOwner(): ReceiptActionOwner = ReceiptActionOwner(currentAuthenticatedSessionGeneration(), userAccountState.payloadValue?.id)

/** The action registry is never synchronized. A server notification cannot supply an executable action. */
data class DeviceFileNotification internal constructor(
    val notification: NotificationDataModel,
    val owner: ReceiptActionOwner,
    internal val savedFile: SavedPdfFile?
)
data class DeviceFileNotificationState internal constructor(val generation: Long, val entries: List<DeviceFileNotification>)
private val _deviceFileNotifications = MutableStateFlow(DeviceFileNotificationState(0L, emptyList()))
val deviceFileNotifications = _deviceFileNotifications.asStateFlow()
internal const val DEVICE_FILE_NOTIFICATION_CATEGORY = "device_file"

internal fun resetDeviceFileNotifications(generation: Long) {
    _deviceFileNotifications.value = DeviceFileNotificationState(generation, emptyList())
}
fun deviceFileNotification(id: String): DeviceFileNotification? =
    _deviceFileNotifications.value.entries.firstOrNull { it.notification.id == id && it.owner.isCurrent() }

internal fun rememberDeviceFileNotification(notification: NotificationDataModel, savedFile: SavedPdfFile?, owner: ReceiptActionOwner) {
    _deviceFileNotifications.update { state ->
        if (!owner.isCurrent() || state.generation != owner.generation) state
        else state.copy(entries = (listOf(DeviceFileNotification(notification, owner, savedFile)) + state.entries.filter { it.owner.isCurrent() })
            .distinctBy { it.notification.id }.take(24))
    }
}
fun markDeviceFileNotificationsRead(id: String? = null) {
    val now = getCurrentTimeMillis()
    _deviceFileNotifications.update { state -> state.copy(entries = state.entries.map { entry ->
        if (entry.owner.isCurrent() && (id == null || entry.notification.id == id)) entry.copy(notification = entry.notification.copy(readAtMillis = now)) else entry
    }) }
}
suspend fun openDeviceNotificationFile(id: String): ReceiptPlatformActionResult {
    val entry = deviceFileNotification(id) ?: return ReceiptPlatformActionResult(false, "This file action belongs to an earlier sign-in")
    val file = entry.savedFile ?: return ReceiptPlatformActionResult(false, "No file is attached to this notification")
    return try {
        file.open { entry.owner.isCurrent() && deviceFileNotification(id)?.savedFile === file }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { ReceiptPlatformActionResult(false, "Could not open the saved PDF. It may have moved or a PDF viewer may be unavailable.") }
}
fun hasDeviceNotificationFile(id: String): Boolean = deviceFileNotification(id)?.savedFile != null
fun isDeviceFileNotification(notification: NotificationDataModel): Boolean = notification.category == DEVICE_FILE_NOTIFICATION_CATEGORY

/** File result toasts are informational; their longer Open window must not freeze checkout.
 * Keep an already-visible business warning blocking even when a file toast arrives over it. */
fun notificationForBusinessActionGuard(
    latest: NotificationDataModel?,
    active: List<NotificationDataModel>
): NotificationDataModel? = if (latest != null && isDeviceFileNotification(latest)) {
    active.firstOrNull { !isDeviceFileNotification(it) }
} else latest

fun safeReceiptPdfFileName(fileName: String): String {
    val clean = fileName.trim().replace(Regex("[\\x00-\\x1f\\x7f/\\\\:*?\"<>|]"), "_")
        .let { if (it.endsWith(".pdf", ignoreCase = true)) it.dropLast(4) else it }.trim(' ', '.').take(96)
    return (clean.ifBlank { "receipt" }) + ".pdf"
}
