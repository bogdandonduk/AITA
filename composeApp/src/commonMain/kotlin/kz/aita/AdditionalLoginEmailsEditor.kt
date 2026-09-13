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
import kotlinx.coroutines.withTimeoutOrNull
import kz.aita.auth.*

@Composable
internal fun AppConfiguration.AdditionalLoginEmailsEditor(
    settings: AitaAuthenticationSettingsDataModel,
    onUpdated: (AitaAuthenticationSettingsDataModel) -> Unit,
    onClose: () -> Unit
) {
    val ownerId = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val scope = rememberCoroutineScope()
    var email by remember(ownerId, generation) { mutableStateOf("") }
    var removeEmail by remember(ownerId, generation) { mutableStateOf<String?>(null) }
    var password by remember(ownerId, generation) { mutableStateOf("") }
    var factor by remember(ownerId, generation) { mutableStateOf("") }
    var flow by remember(ownerId, generation) { mutableStateOf<AitaAuthFlowDataModel?>(null) }
    var code by remember(ownerId, generation) { mutableStateOf("") }
    var busy by remember(ownerId, generation) { mutableStateOf(false) }
    var error by remember(ownerId, generation) { mutableStateOf("") }
    var fieldEpoch by remember(ownerId, generation) { mutableIntStateOf(0) }
    val currentOnUpdated by rememberUpdatedState(onUpdated)
    fun current() = authenticatedSessionGenerationIsCurrent(generation) && userAccountState.payloadValue?.id == ownerId
    fun clear() { email = ""; removeEmail = null; password = ""; factor = ""; flow = null; code = ""; fieldEpoch++ }
    fun close() { clear(); onClose() }
    fun launchAction(block: suspend () -> Unit) {
        if (busy || !current() || ownerId == null) return
        busy = true; error = ""
        scope.launch {
            try {
                if (!current()) return@launch
                if (withTimeoutOrNull(40_000L) { block(); true } != true && current())
                    error = authUiText("Refresh settings before retrying", "Обновите настройки перед повтором", "Қайталаудан бұрын баптауларды жаңартыңыз", "Кайра аракет кылуудан мурун жөндөөлөрдү жаңыртыңыз")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current()) error = authUiText("Request failed. Try again.", "Запрос не выполнен. Повторите.", "Сұрау орындалмады. Қайталаңыз.", "Суроо-талап аткарылган жок. Кайра аракет кылыңыз.") }
            finally { if (current()) busy = false }
        }
    }
    fun accept(response: ResponseDataModel<AitaAuthenticationSettingsDataModel>) {
        if (!current()) return
        val updated = response.payload
        if (!response.negative && updated != null) { clear(); currentOnUpdated(updated) }
        else error = authResponseText(response)
    }
    fun acceptFlow(response: ResponseDataModel<AitaAuthFlowDataModel>) {
        if (!current()) return
        val updated = response.payload
        if (!response.negative && updated?.nextStep == AitaAuthNextStep.EMAIL_CODE && updated.flowId.isNotBlank()) {
            flow = updated; code = ""; password = ""; factor = ""
        } else error = authResponseText(response)
    }
    @Composable fun Done() = AuthSecurityIconAction(authUiText("Done", "Готово", "Дайын", "Даяр"),
        stateValues.drawablePathIconCheck, stateValues.drawableResIconCheck.value, !busy, ::close)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(authUiText("Extra email", "Дополнительный email", "Қосымша email", "Кошумча электрондук почта"), color = stateValues.TextColor, fontSize = stateValues.accentTextSize)
        if (flow == null && removeEmail == null) {
            settings.additionalLoginEmails.forEach { address ->
                key(address) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(address, Modifier.weight(1f), color = stateValues.TextColor, fontSize = stateValues.textSize)
                        AuthSecurityIconAction(authUiText("Remove", "Удалить", "Жою", "Алып салуу"), stateValues.drawablePathIconCancel,
                            stateValues.drawableResIconCancel.value, !busy) { clear(); removeEmail = address }
                    }
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        val currentFlow = flow
        when {
            currentFlow != null -> {
                Text(currentFlow.maskedDestination, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                AuthEmailCodeEntry(code, currentFlow, busy, { code = it; error = "" },
                    onSubmit = {
                        val request = AitaEmailAliasConfirmRequestDataModel(currentFlow.flowId, code)
                        launchAction { accept(AitaAdvancedAuthenticationClient.confirmEmailAlias(request)) }
                    }, onResend = {
                        val request = AitaEmailCodeResendRequestDataModel(currentFlow.flowId, stateValues.appLanguage)
                        launchAction { acceptFlow(AitaAdvancedAuthenticationClient.resendEmailAlias(request)) }
                    }, identity = "additional-email-code-${currentFlow.flowId}",
                    confirmText = authUiText("Confirm extra email", "Подтвердить дополнительный email", "Қосымша email растау", "Кошумча электрондук почтаны ырастоо"),
                    trailingAction = { Done() })
            }
            removeEmail != null || settings.additionalLoginEmails.size < AITA_MAX_ADDITIONAL_LOGIN_EMAILS -> {
                if (removeEmail == null) key(fieldEpoch) {
                    emailTextField(modifier = Modifier.fillMaxWidth(), identityKey = "additional_login_email_$fieldEpoch",
                        valueInitial = email, enabled = !busy, autoFocus = false,
                        titleText = authUiText("Extra email", "Дополнительный email", "Қосымша email", "Кошумча электрондук почта"),
                        placeholderText = authUiText("Enter an extra email", "Введите дополнительный email", "Қосымша email енгізіңіз", "Кошумча электрондук почтаны киргизиңиз"),
                        retainTextAcrossRecreation = false, persistTextDraft = false,
                        imeWithAction = ImeWithAction(ImeAction.Next),
                        onValueChange = { value, apply -> if (!busy) { email = value; error = ""; apply() } })
                } else Text(removeEmail.orEmpty(), color = stateValues.TextColor, fontSize = stateValues.textSize)
                SensitiveAuthConfirmationFields(enabled = !busy, currentPassword = password, secondFactor = factor,
                    secondFactorRequired = settings.authenticatorEnabled, onPasswordChange = { password = it }, onSecondFactorChange = { factor = it })
                val needsEmail = settings.emailRequiredForLogin && !settings.authenticatorEnabled
                val proof = if (needsEmail) SecurityEmailProofInput(AitaSecurityEmailRequest(
                    if (removeEmail == null) AitaSecurityEmailAction.ADD_EMAIL else AitaSecurityEmailAction.REMOVE_EMAIL,
                    normalizeAitaEmail(removeEmail ?: email).orEmpty(), password, settings.securityRevision, stateValues.appLanguage), !busy) else null
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    actionButton(modifier = Modifier.weight(1f), autoLoading = false, loading = busy,
                        text = if (removeEmail == null) authUiText("Get code", "Получить код", "Код алу", "Код алуу") else authUiText("Remove extra email", "Удалить дополнительный email", "Қосымша email жою", "Кошумча электрондук почтаны алып салуу"),
                        enabledColor = if (removeEmail == null) stateValues.AccentColor else stateValues.ErrorColor,
                        enabled = !busy && password.isNotBlank() && (!settings.authenticatorEnabled || aitaSecondFactorIsWellFormed(factor)) &&
                            (!needsEmail || proof != null) && (removeEmail != null || normalizeAitaEmail(email) != null)) {
                        val removal = removeEmail
                        if (removal != null) {
                            val request = AitaEmailAliasRemoveRequestDataModel(removal, password, factor, proof)
                            launchAction { accept(AitaAdvancedAuthenticationClient.removeEmailAlias(request)); factor = "" }
                        } else {
                            val request = AitaEmailAliasRequestDataModel(email.trim(), password, factor, stateValues.appLanguage, proof)
                            launchAction { acceptFlow(AitaAdvancedAuthenticationClient.requestEmailAlias(request)); factor = "" }
                        }
                    }
                    Done()
                }
            }
            else -> {
                if (settings.additionalLoginEmails.size > 1) Text(authUiText("Keep one extra email; remove the others.",
                    "Оставьте один дополнительный email, удалив остальные.", "Бір қосымша email қалдырып, қалғандарын жойыңыз.", "Бир кошумча электрондук почтаны калтырып, калгандарын алып салыңыз."),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Done() }
            }
        }
    }
}
