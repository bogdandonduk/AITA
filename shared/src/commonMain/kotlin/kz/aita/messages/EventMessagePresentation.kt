package kz.aita

fun NotificationDataModel.localizedEventMessage(
    language: String,
    resources: (Long) -> List<LocalizedStringDataModel>? = { null }
): String = EventMessages.render(messageTemplate ?: messageTranslations.eventMessageReferenceOrNull()
    ?: legacyEventMessageReference(message), language, resources)
    ?: messageTranslations.rawEventLocalizedText(language)?.takeIf { it.isNotBlank() }
    ?: message

fun NotificationDataModel.localizedEventTitle(
    language: String,
    resources: (Long) -> List<LocalizedStringDataModel>? = { null }
): String = EventMessages.render(titleTemplate ?: titleTranslations.eventMessageReferenceOrNull()
    ?: legacyEventMessageReference(title), language, resources)
    ?: titleTranslations.rawEventLocalizedText(language)?.takeIf { it.isNotBlank() }
    ?: title

fun OperationLogDataModel.localizedEventTitle(
    language: String,
    resources: (Long) -> List<LocalizedStringDataModel>? = { null }
): String = EventMessages.render(resolvedTitleMessageReference(), language, resources)
    ?: title.rawEventLocalizedText(language).orEmpty()

fun OperationLogDataModel.localizedEventDetails(
    language: String,
    resources: (Long) -> List<LocalizedStringDataModel>? = { null }
): String = EventMessages.render(resolvedDetailsMessageReference(), language, resources)
    ?: details.rawEventLocalizedText(language).orEmpty()

/** Same identity rule on device and server: unknown future types also include their fallback. */
fun NotificationDataModel.eventMessageIdentity(resources: EventResourceCatalogue): String =
    eventTextIdentity(eventTextForStorage(message, messageTranslations, messageTemplate, resources))

fun NotificationDataModel.eventTitleIdentity(resources: EventResourceCatalogue): String =
    eventTextIdentity(eventTextForStorage(title, titleTranslations, titleTemplate, resources))
