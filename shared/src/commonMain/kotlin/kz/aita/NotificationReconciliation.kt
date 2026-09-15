package kz.aita

import kotlinx.coroutines.sync.Mutex

internal val notificationHistoryMutex = Mutex()

/** Merge against the live list, not the list captured before the HTTP request. */
internal fun mergeNotificationSnapshot(
    before: List<NotificationDataModel>,
    current: List<NotificationDataModel>,
    incoming: List<NotificationDataModel>
): List<NotificationDataModel> {
    val beforeById = before.associateBy { it.id }
    val currentById = current.associateBy { it.id }
    val serverIds = incoming.map { it.id }.toSet()
    val server = incoming.map { remote ->
        val local = currentById[remote.id]
        if (local == null || remote.createdAtMillis > local.createdAtMillis) remote
        else if (remote.createdAtMillis < local.createdAtMillis) local
        else remote.copy(readAtMillis = local.readAtMillis ?: remote.readAtMillis,
            shownAtMillis = maxOf(remote.shownAtMillis, local.shownAtMillis))
    }
    val retained = current.filter { local ->
        local.id !in serverIds && (!local.isSavedOnServer || beforeById[local.id] != local)
    }
    return (server + retained).distinctBy { it.id }
        .sortedWith(compareByDescending<NotificationDataModel> { it.createdAtMillis }.thenBy { it.id })
}

/** A read ACK is not a new history snapshot. It must never erase concurrent arrivals. */
internal fun applyNotificationReadAcknowledgement(
    current: List<NotificationDataModel>,
    ids: Set<String>,
    acknowledged: List<NotificationDataModel>
): List<NotificationDataModel> {
    val byId = acknowledged.filter { it.id in ids }.associateBy { it.id }
    return current.map { local ->
        val remote = byId[local.id]
        if (remote == null || remote.createdAtMillis != local.createdAtMillis) local
        else local.copy(readAtMillis = local.readAtMillis ?: remote.readAtMillis)
    }
}
