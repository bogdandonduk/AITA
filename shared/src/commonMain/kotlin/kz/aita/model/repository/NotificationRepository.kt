package kz.aita.model.repository

import kotlinx.coroutines.flow.StateFlow
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.NotificationDataModel
import kz.aita.model.dataModel.NotificationType

interface NotificationRepository {

  val latestNotificationState: StateFlow<NotificationDataModel?>

  fun postNotification(message: List<LocalizedStringDataModel>?, type: NotificationType)
}
