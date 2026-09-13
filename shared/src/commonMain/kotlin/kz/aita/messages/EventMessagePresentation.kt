package kz.aita

private fun localizedEventText(
    reference: EventMessageReference?, language: String,
    translations: List<LocalizedStringDataModel>, original: String,
    resources: (Long) -> List<LocalizedStringDataModel>?
): String = EventMessages.renderExact(reference, language, resources)
    ?: translations.exactLocalizedValue(language)
    ?: EventMessages.render(reference, language, resources)
    ?: translations.rawEventLocalizedText(language)?.takeIf { it.isNotBlank() }
    ?: original

fun NotificationDataModel.localizedEventMessage(
    language: String,
    resources: (Long) -> List<LocalizedStringDataModel>? = { null }
): String = localizedEventText(messageTemplate ?: messageTranslations.eventMessageReferenceOrNull()
    ?: legacyEventMessageReference(message), language, messageTranslations, message, resources)

fun NotificationDataModel.localizedEventTitle(
    language: String,
    resources: (Long) -> List<LocalizedStringDataModel>? = { null }
): String = localizedEventText(titleTemplate ?: titleTranslations.eventMessageReferenceOrNull()
    ?: legacyEventMessageReference(title), language, titleTranslations, title, resources)

fun OperationLogDataModel.localizedEventTitle(
    language: String,
    resources: (Long) -> List<LocalizedStringDataModel>? = { null }
): String = localizedEventText(resolvedTitleMessageReference(), language, title, "", resources)

fun OperationLogDataModel.localizedEventDetails(
    language: String,
    resources: (Long) -> List<LocalizedStringDataModel>? = { null }
): String = localizedEventText(resolvedDetailsMessageReference(), language, details, "", resources)

/** Same identity rule on device and server: unknown future types also include their fallback. */
fun NotificationDataModel.eventMessageIdentity(resources: EventResourceCatalogue): String =
    eventTextIdentity(eventTextForStorage(message, messageTranslations, messageTemplate, resources))

fun NotificationDataModel.eventTitleIdentity(resources: EventResourceCatalogue): String =
    eventTextIdentity(eventTextForStorage(title, titleTranslations, titleTemplate, resources))
