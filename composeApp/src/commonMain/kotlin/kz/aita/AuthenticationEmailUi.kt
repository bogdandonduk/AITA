package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kz.aita.auth.AitaAdvancedAuthenticationClient
import kz.aita.auth.AitaAuthFlowDataModel
import kz.aita.auth.AitaAuthenticationSettingsDataModel
import kz.aita.auth.AitaEmailDestination
import kz.aita.auth.AitaEmailDestinationOption
import kz.aita.auth.AitaLoginPolicyRequest
import kz.aita.auth.AitaLoginSecondFactor
import kz.aita.auth.AitaSecurityEmailAction
import kz.aita.auth.AitaSecurityEmailProof
import kz.aita.auth.AitaSecurityEmailRequest
import kz.aita.auth.aitaAuthCodeDigits
import kz.aita.auth.aitaSecondFactorIsWellFormed
import kz.aita.auth.canonicalAitaSecurityTarget
import kz.aita.auth.loginSecondFactor
import org.jetbrains.compose.resources.DrawableResource

@Composable
internal fun AppConfiguration.AuthSecurityIconAction(label: String, path: String, res: DrawableResource,
    enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        CpImage(modifier = Modifier.size(24.dp), url = path, fallbackRes = res, contentDescription = label,
            tintColor = if (enabled) stateValues.AccentColor else stateValues.PlaceholderTextColor)
    }
}

@Composable
internal fun AppConfiguration.AuthEmailDestinationPicker(
    selected: AitaEmailDestination,
    enabled: Boolean,
    // Null means anonymous: never show account-specific existence or address information.
    options: List<AitaEmailDestinationOption>? = null,
    onSelected: (AitaEmailDestination) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val destinations = options?.map { it.destination } ?: AitaEmailDestination.entries
        destinations.forEach { destination ->
            val address = options?.firstOrNull { it.destination == destination }?.maskedAddress.orEmpty()
            Row(Modifier.fillMaxWidth().selectable(selected == destination, enabled = enabled,
                role = Role.RadioButton, onClick = { onSelected(destination) }).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == destination, onClick = null, enabled = enabled,
                    colors = RadioButtonDefaults.colors(selectedColor = stateValues.AccentColor,
                        unselectedColor = stateValues.PlaceholderTextColor))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (destination == AitaEmailDestination.MAIN) authUiText("Main email", "Основной email", "Негізгі email", "Негизги электрондук почта")
                        else authUiText("Extra email", "Дополнительный email", "Қосымша email", "Кошумча электрондук почта"),
                        color = stateValues.TextColor, fontSize = stateValues.textSize)
                    if (address.isNotBlank()) Text(address, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                }
            }
        }
    }
}

/** A transient, purpose-bound email proof. Returned code is validated/consumed only by the
 * actual sensitive operation, not locally and not by an unrelated login-code endpoint.
 */
@Composable
internal fun AppConfiguration.SecurityEmailProofInput(
    request: AitaSecurityEmailRequest, enabled: Boolean = true
): AitaSecurityEmailProof? {
    val owner = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    return key(owner, generation, request.action, request.target, request.currentPassword, request.expectedSecurityRevision) {
        val scope = rememberCoroutineScope()
        var flow by remember { mutableStateOf<AitaAuthFlowDataModel?>(null) }
        var code by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        val countdown = rememberAuthFlowCountdown(flow)
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (flow != null) {
                aitaFormTextField(modifier = Modifier.fillMaxWidth(), value = code,
                    onValueChange = { code = it; error = "" },
                    titleText = authUiText("Main email confirmation", "Подтверждение основного email", "Негізгі email растауы", "Негизги электрондук почта аркылуу ырастоо"),
                    placeholderText = "000000", identityKey = "security-email-${flow?.flowId}",
                    keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done,
                    leadingIconPath = stateValues.drawablePathIconEmail, sensitive = true, enabled = enabled && !busy,
                    onTransformValue = { aitaAuthCodeDigits(it).take(6) })
                Text(flow?.maskedDestination.orEmpty(), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
            actionButton(modifier = Modifier.fillMaxWidth(),
                text = (if (flow == null) authUiText("Get email confirmation", "Получить подтверждение по email", "Email растауын алу", "Электрондук почта аркылуу ырастоо алуу")
                    else authUiText("Resend", "Ещё код", "Қайта жіберу", "Кайра жөнөтүү")) +
                    if (countdown.resendSeconds > 0L) " · ${countdown.resendSeconds}" else "",
                enabled = enabled && !busy && request.currentPassword.isNotBlank() && canonicalAitaSecurityTarget(request.action, request.target) != null && countdown.resendSeconds == 0L,
                loading = busy, autoLoading = false) {
                if (!busy && owner != null && authenticatedSessionGenerationIsCurrent(generation) && userAccountState.payloadValue?.id == owner) {
                    busy = true; error = ""
                    scope.launch {
                        try {
                            if (!authenticatedSessionGenerationIsCurrent(generation) || userAccountState.payloadValue?.id != owner) return@launch
                            val result = withTimeoutOrNull(30_000L) { AitaAdvancedAuthenticationClient.requestSecurityEmail(request) }
                            if (authenticatedSessionGenerationIsCurrent(generation) && userAccountState.payloadValue?.id == owner) {
                                if (result != null && !result.negative && result.payload != null) { flow = result.payload; code = "" }
                                else error = result?.let { authResponseText(it) } ?: authUiText("Request timed out", "Время ожидания истекло", "Күту уақыты аяқталды", "Суроо-талаптын күтүү убактысы бүттү")
                            }
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { error = authUiText("Request failed. Try again.", "Запрос не выполнен. Повторите.", "Сұрау орындалмады. Қайталаңыз.", "Суроо-талап аткарылган жок. Кайра аракет кылыңыз.") }
                        finally { busy = false }
                    }
                }
            }
            if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            if (countdown.expired) Text(authUiText("Code expired", "Код истёк", "Код мерзімі аяқталды", "Коддун мөөнөтү бүттү"), color = stateValues.ErrorColor)
        }
        flow?.takeIf { !busy && !countdown.expired && code.length == 6 }?.let { AitaSecurityEmailProof(it.flowId, code) }
    }
}

@Composable
internal fun AppConfiguration.LoginPolicyEditor(settings: AitaAuthenticationSettingsDataModel,
    selectedMethod: AitaLoginSecondFactor, onUpdated: (AitaAuthenticationSettingsDataModel) -> Unit,
    onClose: () -> Unit, onBusyChanged: (Boolean) -> Unit = {}) {
    val owner = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    var password by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val chosen = selectedMethod
    LaunchedEffect(busy) { onBusyChanged(busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SensitiveAuthConfirmationFields(enabled = !busy, currentPassword = password, secondFactor = factor,
            secondFactorRequired = settings.authenticatorEnabled, onPasswordChange = { password = it }, onSecondFactorChange = { factor = it })
        val needsEmail = chosen == AitaLoginSecondFactor.EMAIL || (!settings.authenticatorEnabled && settings.emailRequiredForLogin)
        val proof = if (needsEmail) SecurityEmailProofInput(AitaSecurityEmailRequest(AitaSecurityEmailAction.LOGIN_POLICY,
            chosen.name, password, settings.securityRevision, stateValues.appLanguage), !busy) else null
        if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            actionButton(modifier = Modifier.fillMaxWidth(), text = authUiText("Confirm change", "Подтвердить изменение", "Өзгерісті растау", "Өзгөртүүнү ырастоо"),
                enabled = !busy && password.isNotBlank() && (!settings.authenticatorEnabled || aitaSecondFactorIsWellFormed(factor)) && (!needsEmail || proof != null),
                loading = busy, autoLoading = false) {
                val request = AitaLoginPolicyRequest(chosen, password, factor, settings.securityRevision, proof)
                if (busy || owner == null || !authenticatedSessionGenerationIsCurrent(generation) || userAccountState.payloadValue?.id != owner) return@actionButton
                busy = true; error = ""
                scope.launch {
                    try {
                        if (!authenticatedSessionGenerationIsCurrent(generation) || userAccountState.payloadValue?.id != owner) return@launch
                        val result = withTimeoutOrNull(30_000L) { AitaAdvancedAuthenticationClient.updateLoginPolicy(request) }
                        if (authenticatedSessionGenerationIsCurrent(generation) && userAccountState.payloadValue?.id == owner) {
                            val updatedSettings = result?.payload
                            if (result != null && !result.negative && updatedSettings != null) onUpdated(updatedSettings)
                            else error = result?.let { authResponseText(it) } ?: authUiText("Refresh settings before retrying", "Обновите настройки перед повтором", "Қайталаудан бұрын баптауларды жаңартыңыз", "Кайра аракет кылуудан мурун жөндөөлөрдү жаңыртыңыз")
                        }
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { error = authUiText("Refresh settings before retrying", "Обновите настройки перед повтором", "Қайталаудан бұрын баптауларды жаңартыңыз", "Кайра аракет кылуудан мурун жөндөөлөрдү жаңыртыңыз") }
                    finally { busy = false; factor = "" }
                }
            }
            AuthSecurityIconAction(authUiText("Done", "Готово", "Дайын", "Даяр"), stateValues.drawablePathIconCheck, stateValues.drawableResIconCheck.value, !busy, onClose)
        }
    }
}

internal data class ProfileSecurityConfirmation(val ready: Boolean, val factor: String = "", val emailProof: AitaSecurityEmailProof? = null)

@Composable
internal fun AppConfiguration.ProfileSecurityConfirmationInput(target: String, password: String): ProfileSecurityConfirmation {
    val owner = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    var settings by remember(owner, generation) { mutableStateOf<AitaAuthenticationSettingsDataModel?>(null) }
    var error by remember(owner, generation) { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var factor by remember(owner, generation, target) { mutableStateOf("") }
    LaunchedEffect(owner, generation, revision) {
        if (owner == null || !authenticatedSessionGenerationIsCurrent(generation) || userAccountState.payloadValue?.id != owner) return@LaunchedEffect
        try {
            val response = withTimeoutOrNull(20_000L) { AitaAdvancedAuthenticationClient.settings() }
            if (authenticatedSessionGenerationIsCurrent(generation) && userAccountState.payloadValue?.id == owner) {
                if (response != null && !response.negative && response.payload != null) { settings = response.payload; error = "" }
                else error = response?.let { authResponseText(it) } ?: authUiText("Could not load security settings", "Не удалось загрузить настройки безопасности", "Қауіпсіздік баптаулары жүктелмеді", "Коопсуздук жөндөөлөрүн жүктөө мүмкүн болгон жок")
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = authUiText("Could not load security settings", "Не удалось загрузить настройки безопасности", "Қауіпсіздік баптаулары жүктелмеді", "Коопсуздук жөндөөлөрүн жүктөө мүмкүн болгон жок") }
    }
    val current = settings
    if (error.isNotBlank()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(error, Modifier.weight(1f), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        AuthSecurityIconAction(authUiText("Refresh", "Обновить", "Жаңарту", "Жаңыртуу"), stateValues.drawablePathIconRefresh, stateValues.drawableResIconRefresh.value) { revision++ }
    }
    if (current == null) {
        if (error.isBlank()) Text(authUiText("Checking account security…", "Проверяем безопасность аккаунта…", "Аккаунт қауіпсіздігін тексеру…", "Аккаунттун коопсуздугу текшерилүүдө…"),
            color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        return ProfileSecurityConfirmation(false)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(authUiText("Security confirmation", "Подтверждение безопасности", "Қауіпсіздікті растау", "Коопсуздукту ырастоо"),
            Modifier.weight(1f), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
        AuthSecurityIconAction(authUiText("Refresh", "Обновить", "Жаңарту", "Жаңыртуу"), stateValues.drawablePathIconRefresh, stateValues.drawableResIconRefresh.value) {
            settings = null; error = ""; factor = ""; revision++
        }
    }
    // New-address ownership is confirmed in the profile form, separately from this current-account factor.
    if (current.authenticatorEnabled) AuthenticatorCodeEntryField(factor, false, { factor = it }, {}, "profile-security-factor")
    val needsEmail = current.emailRequiredForLogin && !current.authenticatorEnabled
    val proof = if (needsEmail) SecurityEmailProofInput(AitaSecurityEmailRequest(AitaSecurityEmailAction.PROFILE, target, password,
        current.securityRevision, stateValues.appLanguage)) else null
    return ProfileSecurityConfirmation((!current.authenticatorEnabled || aitaSecondFactorIsWellFormed(factor)) && (!needsEmail || proof != null), factor, proof)
}
