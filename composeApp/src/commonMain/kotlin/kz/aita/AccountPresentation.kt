package kz.aita

internal fun AppConfiguration.accountPresentationText(key: String) =
    eventMessage("account.ui.$key").extractLocalizedString(stateValues.appLanguage).orEmpty()
