package kz.aita

internal fun AppConfiguration.inventoryExperienceText(key: String): String =
    inventoryExperienceMessage(key).extractLocalizedString(stateValues.appLanguage).orEmpty()

internal fun AppConfiguration.stockBatchQuantityForUi(batch: GoodsBatchDataModel): String =
    if (batch.tracksQuantity) batch.quantity.quantityText(stateValues.appLanguage)
    else inventoryExperienceText("unlimited")
