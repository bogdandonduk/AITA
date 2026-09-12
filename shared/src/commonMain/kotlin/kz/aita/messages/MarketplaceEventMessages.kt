package kz.aita

internal fun marketplaceEventMessageTemplates() = listOf(
    EventMessageTemplate("market.invalid", "Check the shop-window details. A name, city and public pickup address are required before publishing.", "Проверьте данные витрины. Для публикации нужны название, город и публичный адрес самовывоза.", "Витрина деректерін тексеріңіз. Жариялау үшін атау, қала және алып кету мекенжайы қажет."),
    EventMessageTemplate("market.owner", "Only a store owner can publish this location's shop window.", "Опубликовать витрину этой точки может только владелец магазина.", "Осы нүктенің витринасын тек дүкен иесі жариялай алады."),
    EventMessageTemplate("market.changed", "This publication changed on another device. Reload it before saving again.", "Публикация изменилась на другом устройстве. Обновите данные перед сохранением.", "Жарияланым басқа құрылғыда өзгерді. Сақтамас бұрын деректерді жаңартыңыз."),
    EventMessageTemplate("market.unavailable", "This offer is no longer published or available to view.", "Это предложение больше не опубликовано или недоступно для просмотра.", "Бұл ұсыныс енді жарияланбаған немесе көруге қолжетімсіз."),
    EventMessageTemplate("market.saved_limit", "Your saved list can contain up to 100 offers. Remove one before adding another.", "Можно сохранить до 100 предложений. Удалите одно, чтобы добавить новое.", "100 ұсынысқа дейін сақтауға болады. Жаңасын қосу үшін біреуін өшіріңіз."),
    EventMessageTemplate("market.listing_limit", "This location can publish up to 1,000 listings in the preview.", "В предварительной версии эта точка может опубликовать до 1 000 товаров.", "Алдын ала нұсқада осы нүкте 1 000 тауарға дейін жариялай алады."),
    EventMessageTemplate("market.saved", "Shop-window changes saved.", "Изменения витрины сохранены.", "Витрина өзгерістері сақталды.")
)
