package kz.aita

import kotlinx.coroutines.launch

internal enum class NotificationDestination { GUEST_DOWNLOADS, UPDATE, HISTORY, DISMISS }

internal fun notificationDestination(notification: NotificationDataModel, signedIn: Boolean): NotificationDestination {
    val update = (notification.messageTemplate ?: notification.messageTranslations.eventMessageReferenceOrNull())?.key == "updates.notification"
    return when {
        update && !signedIn -> NotificationDestination.GUEST_DOWNLOADS
        update -> NotificationDestination.UPDATE
        signedIn -> NotificationDestination.HISTORY
        else -> NotificationDestination.DISMISS
    }
}

internal fun AppConfiguration.openNotification(notification: NotificationDataModel) {
    coroutineScope.launch {
        val account = userAccountState.payloadValue?.id
        when (notificationDestination(notification, !account.isNullOrBlank())) {
            NotificationDestination.GUEST_DOWNLOADS -> {
                dismissInAppNotification(notification.id, markAsRead = false)
                Navigation.goMain(NavigationScreenModel.UserAuth.Main)
                if (stateValues.isNarrowScreen) Navigation.UserAuth.goLeft(NavigationScreenModel.UserAuth.Downloads)
                else Navigation.UserAuth.goRight(NavigationScreenModel.UserAuth.Downloads)
            }
            NotificationDestination.DISMISS -> dismissInAppNotification(notification.id, markAsRead = false)
            else -> {
                val destination = notificationDestination(notification, true)
                if (notification.category == MISSED_NOTIFICATION_CATEGORY) {
                    requestUnreadNotifications()
                    dismissInAppNotification(notification.id, markAsRead = false)
                } else markNotificationRead(notification.id)
                if (account != userAccountState.payloadValue?.id) return@launch
                Navigation.goMain(NavigationScreenModel.Menu.Main)
                if (account != userAccountState.payloadValue?.id) return@launch
                Navigation.Menu.go(if (destination == NotificationDestination.UPDATE) NavigationScreenModel.Menu.ClientUpdate
                    else NavigationScreenModel.Menu.Notifications, stateValues.isNarrowScreen)
            }
        }
    }
}
