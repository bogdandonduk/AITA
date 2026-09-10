package kz.aita

/** One versioned intent. IO completions acknowledge a choice; they are not new UI commands. */
internal data class AppPreferenceIntent(
    val value: UserPreferencesDataModel = UserPreferencesDataModel(),
    val revision: Long = 0L,
    val isLocalSelection: Boolean = false
)

internal data class AppPreferenceDecision(val value: UserPreferencesDataModel, val shouldSync: Boolean)

private fun UserPreferencesDataModel.normalized() = UserPreferencesDataModel(
    normalizeAppLanguagePreference(appLanguage), normalizeAppThemePreference(appThemeId),
    normalizeAppSizeModePreference(appSizeModeId)
)

/** Apply account defaults once; pending/local choices outrank a late account response. */
internal fun resolveAppPreferenceChoice(
    current: AppPreferenceIntent,
    account: UserPreferencesDataModel,
    requestRevision: Long,
    pending: UserPreferencesDataModel?,
    override: AuthScreenPreferenceOverrideDataModel
): AppPreferenceDecision {
    val server = account.normalized()
    val changedDuringRequest = current.revision != requestRevision && current.isLocalSelection
    val base = if (changedDuringRequest) current.value.normalized() else pending?.normalized() ?: server
    val chosen = if (changedDuringRequest) base else base.copy(
        appLanguage = if (override.languageTouched) normalizeAppLanguagePreference(override.appLanguage) else base.appLanguage,
        appThemeId = if (override.themeTouched) normalizeAppThemePreference(override.appThemeId) else base.appThemeId,
        appSizeModeId = if (override.sizeModeTouched) normalizeAppSizeModePreference(override.appSizeModeId) else base.appSizeModeId
    )
    return AppPreferenceDecision(chosen, pending != null || override.touched || base != server)
}
