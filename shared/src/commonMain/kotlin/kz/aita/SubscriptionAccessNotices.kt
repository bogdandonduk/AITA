package kz.aita

/** Access is a persistent navigation state, not an event to save/blink on every automatic read. */
private val subscriptionAccessNoticeKeys = setOf("subscription.required", "subscription.verify")

fun isSubscriptionAccessNotice(message: List<LocalizedStringDataModel>?): Boolean =
    message?.explicitEventMessageReference()?.key in subscriptionAccessNoticeKeys ||
        message.orEmpty().any { isSubscriptionAccessNotice(it.value) }

fun isSubscriptionAccessNotice(text: String): Boolean = subscriptionAccessNoticeKeys.any { key ->
    eventMessage(key).any { it.value.isNotBlank() && it.value.trim() == text.trim() }
}

fun NotificationDataModel.isSubscriptionAccessNotice(): Boolean =
    messageTemplate?.key in subscriptionAccessNoticeKeys || isSubscriptionAccessNotice(message)
