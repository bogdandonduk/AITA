package kz.aita

import kotlinx.coroutines.flow.MutableStateFlow

/** A one-use, in-memory handoff to the normal authentication flow. Never carries a password or proof. */
internal data class RegistrationLoginEntry(
    val identifier: String,
    val recoverPassword: Boolean,
    val generation: Long = currentAuthenticatedSessionGeneration()
)
internal val registrationLoginEntry = MutableStateFlow<RegistrationLoginEntry?>(null)

internal fun registrationRecoveryIdentifier(pending: UserAuthSignUpDataModel, message: List<LocalizedStringDataModel>?): String =
    if (message.orEmpty().any { it.messageTemplate?.key == "message.user_with_this_phone_number_is_already_registered" })
        kz.aita.auth.normalizeAitaPhoneAlias(pending.phoneNumber).orEmpty()
    else pending.email.trim()
