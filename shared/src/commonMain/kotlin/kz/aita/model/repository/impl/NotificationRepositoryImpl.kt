package kz.aita.model.repository.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kz.aita.core.configurationRepository
import kz.aita.core.extractLocalizedString
import kz.aita.core.io
import kz.aita.model.dataModel.LocalizedStringDataModel
import kz.aita.model.dataModel.NotificationDataModel
import kz.aita.model.dataModel.NotificationType
import kz.aita.model.repository.NotificationRepository
import kz.aita.model.repository.Repository

class NotificationRepositoryImpl: Repository(), NotificationRepository {

  private val _latestNotificationState = MutableStateFlow<NotificationDataModel?>(null)
  override val latestNotificationState = _latestNotificationState.asStateFlow()

  override fun post(message: List<LocalizedStringDataModel>?, type: NotificationType, transient: Boolean) {
    message?.extractLocalizedString(configurationRepository.appLanguageState.value)?.run {
      launch(Dispatchers.io) {
        _latestNotificationState.emit(
          NotificationDataModel(
            this@run,
            type
          )
        )

        if (transient) {
          delay(3000)
          _latestNotificationState.emit(null)
        }
      }
    }
  }

  override fun post(message: String, type: NotificationType, transient: Boolean) {
    launch(Dispatchers.io) {
      _latestNotificationState.emit(
        NotificationDataModel(
          message,
          type
        )
      )

      if (transient) {
        delay(3000)
        _latestNotificationState.emit(null)
      }
    }
  }

  override fun clear() {
    launch(Dispatchers.io) {
      _latestNotificationState.emit(null)
    }
  }
}
