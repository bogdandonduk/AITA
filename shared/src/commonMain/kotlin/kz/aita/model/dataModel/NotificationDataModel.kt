package kz.aita.model.dataModel

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDataModel(
  val message: String,
  val type: NotificationType
)

enum class NotificationType {
  Positive, Negative, Neutral
}