package kz.aita

internal fun AppConfiguration.activeStoreScopeText(scope: ActiveStoreSelectionScope, action: Boolean = false): String {
    val device = scope == ActiveStoreSelectionScope.DEVICE
    val words = when {
        device && action -> listOf("Set active on this device", "Выбрать на этом устройстве", "Осы құрылғыда таңдау", "Бул түзмөктө тандоо", "Дар ин дастгоҳ интихоб кардан", "Shu qurilmada tanlash")
        action -> listOf("Set active across account devices", "Выбрать на устройствах аккаунта", "Аккаунт құрылғыларында таңдау", "Аккаунттун түзмөктөрүндө тандоо", "Дар дастгоҳҳои ҳисоб интихоб кардан", "Hisob qurilmalarida tanlash")
        device -> listOf("This device", "Это устройство", "Осы құрылғы", "Бул түзмөк", "Ин дастгоҳ", "Shu qurilma")
        else -> listOf("Account devices", "Устройства аккаунта", "Аккаунт құрылғылары", "Аккаунттун түзмөктөрү", "Дастгоҳҳои ҳисоб", "Hisob qurilmalari")
    }
    return words[listOf("en", "ru", "kk", "ky", "tg", "uz").indexOf(effectiveAppLanguage(stateValues.appLanguage)).coerceAtLeast(0)]
}
