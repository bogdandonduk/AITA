package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

const val MISSED_NOTIFICATION_CATEGORY = "missed_summary"

/** App acknowledgements, local files and the device that initiated an event are not missed work. */
fun NotificationDataModel.isRemoteUnreadNotification(account: String?, installation: String?): Boolean =
    account != null && (userId == null || userId == account) && readAtMillis == null && isSavedOnServer &&
        source == "server" && category !in setOf("connection", "session", MISSED_NOTIFICATION_CATEGORY, "device_file") &&
        !(installation?.isNotBlank() == true && metadata["originInstallationId"] == installation)

fun remoteUnreadNotifications(items: List<NotificationDataModel>, account: String?, installation: String?): List<NotificationDataModel> =
    items.filter { it.isRemoteUnreadNotification(account, installation) }.distinctBy { it.id }

@Serializable internal data class NotificationAnnouncementJournal(val ids: Set<String> = emptySet())

/** Called under the notification-history mutex. The account/device journal also survives restarts. */
internal object MissedNotifications {
    private var owner: Pair<String, Long>? = null
    private var announced = emptySet<String>()
    private var lastRefresh = 0L
    suspend fun accept(items: List<NotificationDataModel>, generation: Long) {
        val account = userAccountState.payloadValue?.id ?: return
        val installation = getClientDeviceInfo?.invoke()?.installationId.orEmpty()
        val expected = account to generation
        fun current() = authenticatedSessionGenerationIsCurrent(generation) && userAccountState.payloadValue?.id == account
        val key = "notification-announcements.v1:$account:$installation"
        if (owner != expected) {
            val saved = try { getLocalKv(key)?.let { jsonBase.decodeFromString<NotificationAnnouncementJournal>(it).ids }.orEmpty() }
                catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { emptySet() }
            if (!current()) return
            owner = expected; announced = saved; lastRefresh = 0L
        }
        val unread = remoteUnreadNotifications(items, account, installation)
        val fresh = unread.filter { it.id !in announced }
        val now = getCurrentTimeMillis()
        val missed = lastRefresh == 0L || now - lastRefresh > 30_000L
        lastRefresh = now
        if (fresh.isEmpty()) return
        announced = (announced + fresh.map { it.id }).intersect(items.map { it.id }.toSet()).takeLastSet(1000)
        try { putLocalKv(key, jsonBase.encodeToString(NotificationAnnouncementJournal(announced))) }
        catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* Keep the in-session deduplication. */ }
        if (!current()) return
        if (missed || fresh.size > 1) {
            val reference = EventMessageReference("notifications.missed", mapOf("count" to fresh.size.toString()))
            pushInAppNotificationNow(NotificationDataModel(id = "missed_${generation}_$now", userId = account,
                message = EventMessages.render(reference, appLanguageState.value).orEmpty(), messageTemplate = reference,
                type = NotificationType.Neutral, category = MISSED_NOTIFICATION_CATEGORY, source = "device",
                createdAtMillis = now, shownAtMillis = now), transient = true)
        } else fresh.forEach { if (current()) pushInAppNotificationNow(it.copy(shownAtMillis = now), transient = true) }
    }
}
private fun Set<String>.takeLastSet(limit: Int): Set<String> = toList().takeLast(limit).toSet()
