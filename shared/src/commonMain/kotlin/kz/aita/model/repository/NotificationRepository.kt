package kz.aita.model.repository

import kotlinx.coroutines.flow.StateFlow
import kz.aita.model.dataModel.NotificationDataModel
import kz.aita.model.dataModel.NotificationType

interface NotificationRepository {

  val latestNotificationState: StateFlow<NotificationDataModel?>

  fun postNotification(message: String, type: NotificationType)
}
