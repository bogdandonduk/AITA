package kz.aita

internal fun AppConfiguration.printerConnectionText(key: String): String =
    eventMessage("printer.connection.$key").extractLocalizedString(stateValues.appLanguage).orEmpty()
