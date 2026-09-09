package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kz.aita.auth.*

@Composable
internal fun AppConfiguration.AdditionalLoginEmailsEditor(
    settings: AitaAuthenticationSettingsDataModel,
    onUpdated: (AitaAuthenticationSettingsDataModel) -> Unit,
    onClose: () -> Unit
) {
    val ownerId = stateValues.userAccount?.id
    val scope = rememberCoroutineScope()
    val form = remember(ownerId) { object : StateHost() {} }
    var email by remember(ownerId) { mutableStateOf("") }
    var removeEmail by remember(ownerId) { mutableStateOf<String?>(null) }
    var password by remember(ownerId) { mutableStateOf("") }
    var factor by remember(ownerId) { mutableStateOf("") }
    var flow by remember(ownerId) { mutableStateOf<AitaAuthFlowDataModel?>(null) }
    var code by remember(ownerId) { mutableStateOf("") }
    var busy by remember(ownerId) { mutableStateOf(false) }
    var error by remember(ownerId) { mutableStateOf("") }
    var fieldEpoch by remember(ownerId) { mutableIntStateOf(0) }
    val currentOnUpdated by rememberUpdatedState(onUpdated)

    fun clear() { email = ""; removeEmail = null; password = ""; factor = ""; flow = null; code = ""; fieldEpoch++ }
    fun launchAction(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = authUiText("Request failed. Try again.", "Запрос не выполнен. Повторите.", "Сұрау орындалмады. Қайталаңыз.") }
            finally { busy = false }
        }
    }
    fun accept(response: ResponseDataModel<AitaAuthenticationSettingsDataModel>) {
        if (userAccountState.payloadValue?.id != ownerId) return
        val updated = response.payload
        if (!response.negative && updated != null) { clear(); currentOnUpdated(updated) }
        else error = authResponseText(response)
    }
    fun acceptFlow(response: ResponseDataModel<AitaAuthFlowDataModel>) {
        if (userAccountState.payloadValue?.id != ownerId) return
        val updated = response.payload
        if (!response.negative && updated?.nextStep == AitaAuthNextStep.EMAIL_CODE && updated.flowId.isNotBlank()) {
            flow = updated; code = ""; password = ""; factor = ""
        } else error = authResponseText(response)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(authUiText("Extra emails", "Дополнительные email", "Қосымша email мекенжайлары"),
            color = stateValues.TextColor, fontSize = stateValues.accentTextSize)
        Text(authUiText("Main email", "Основной email", "Негізгі email") + ": " + settings.email,
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        if (flow == null && removeEmail == null) {
            settings.additionalLoginEmails.forEach { address ->
                key(address) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(address, Modifier.weight(1f), color = stateValues.TextColor, fontSize = stateValues.textSize)
                        AuthQuietAction(authUiText("Remove", "Удалить", "Жою"), !busy) {
                            clear(); removeEmail = address
                        }
                    }
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        val currentFlow = flow
        when {
            currentFlow != null -> {
                Text(authUiText("Code sent to", "Код отправлен на", "Код жіберілді") + " " + email,
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                AuthEmailCodeEntry(code, currentFlow, busy, { code = it; error = "" },
                    onSubmit = {
                        val request = AitaEmailAliasConfirmRequestDataModel(currentFlow.flowId, code)
                        launchAction { accept(AitaAdvancedAuthenticationClient.confirmEmailAlias(request)) }
                    }, onResend = {
                        val request = AitaEmailCodeResendRequestDataModel(currentFlow.flowId, stateValues.appLanguage)
                        launchAction { acceptFlow(AitaAdvancedAuthenticationClient.resendEmailAlias(request)) }
                    }, identity = "additional-email-code-${currentFlow.flowId}",
                    confirmText = authUiText("Confirm extra email", "Подтвердить дополнительный email", "Қосымша email растау"))
                AuthQuietAction(stateValues.stringCancel, !busy) { clear() }
            }
            removeEmail != null || settings.additionalLoginEmails.size < AITA_MAX_ADDITIONAL_LOGIN_EMAILS -> {
                if (removeEmail == null) {
                    key(fieldEpoch) {
                        emailTextField(modifier = Modifier.fillMaxWidth(), stateHost = form,
                            stateKey = "additional_login_email_$fieldEpoch", identityKey = "additional_login_email_$fieldEpoch",
                            valueInitial = email, enabled = !busy, autoFocus = false,
                            titleText = authUiText("Extra email", "Дополнительный email", "Қосымша email"),
                            placeholderText = authUiText("Enter an extra email", "Введите дополнительный email", "Қосымша email енгізіңіз"),
                            retainTextAcrossRecreation = false, persistTextDraft = false,
                            imeWithAction = ImeWithAction(ImeAction.Next),
                            onValueChange = { value, apply -> if (!busy) { email = value; error = ""; apply() } })
                    }
                } else Text(removeEmail.orEmpty(), color = stateValues.TextColor, fontSize = stateValues.textSize)
                SensitiveAuthConfirmationFields(enabled = !busy, currentPassword = password, secondFactor = factor,
                    secondFactorRequired = settings.authenticatorEnabled, onPasswordChange = { password = it }, onSecondFactorChange = { factor = it })
                actionButton(modifier = Modifier.fillMaxWidth(), autoLoading = false, loading = busy,
                    text = if (removeEmail == null) authUiText("Get code", "Получить код", "Код алу") else authUiText("Remove extra email", "Удалить дополнительный email", "Қосымша email жою"),
                    enabledColor = if (removeEmail == null) stateValues.AccentColor else stateValues.ErrorColor,
                    enabled = !busy && password.isNotBlank() && (!settings.authenticatorEnabled || factor.isNotBlank()) &&
                        (removeEmail != null || normalizeAitaEmail(email) != null)) {
                    val removal = removeEmail
                    if (removal != null) {
                        val request = AitaEmailAliasRemoveRequestDataModel(removal, password, factor)
                        launchAction { accept(AitaAdvancedAuthenticationClient.removeEmailAlias(request)); password = ""; factor = "" }
                    } else {
                        val request = AitaEmailAliasRequestDataModel(email.trim(), password, factor, stateValues.appLanguage)
                        launchAction { acceptFlow(AitaAdvancedAuthenticationClient.requestEmailAlias(request)); password = ""; factor = "" }
                    }
                }
                if (removeEmail != null) AuthQuietAction(stateValues.stringCancel, !busy) { clear() }
            }
            else -> Text(authUiText("Five extra emails maximum", "Не более пяти дополнительных email", "Ең көбі бес қосымша email"),
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        }
        AuthQuietAction(authUiText("Done", "Готово", "Дайын"), !busy) { clear(); onClose() }
    }
}
