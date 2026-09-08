package kz.aita

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kz.aita.auth.*

private enum class AitaLoginMode { PASSWORD, EMAIL_CODE, RECOVERY }
private enum class AitaLoginStep { PRIMARY, EMAIL_CODE, TOTP, NEW_PASSWORD, COMPLETE }
private enum class AitaLoginIdentifierType { PHONE, EMAIL }

internal enum class AitaAuthFeatureAvailability { CHECKING, AVAILABLE, UNAVAILABLE, UNKNOWN }

internal data class AitaAuthUiAvailability(
    val emailCodeLogin: AitaAuthFeatureAvailability,
    val passwordRecovery: AitaAuthFeatureAvailability,
    val authenticator: AitaAuthFeatureAvailability,
    val phoneLoginAlias: AitaAuthFeatureAvailability,
)

internal fun resolveAitaAuthUiAvailability(
    capabilities: AitaAuthCapabilitiesDataModel?,
    loading: Boolean,
): AitaAuthUiAvailability {
    fun resolve(predicate: AitaAuthCapabilitiesDataModel.() -> Boolean): AitaAuthFeatureAvailability = when {
        capabilities == null && loading -> AitaAuthFeatureAvailability.CHECKING
        capabilities == null -> AitaAuthFeatureAvailability.UNKNOWN
        capabilities?.let { predicate(it) } == true -> AitaAuthFeatureAvailability.AVAILABLE
        else -> AitaAuthFeatureAvailability.UNAVAILABLE
    }

    return AitaAuthUiAvailability(
        emailCodeLogin = resolve { emailCodeLoginEnabled },
        passwordRecovery = resolve { passwordRecoveryEnabled },
        authenticator = resolve { authenticatorTwoFactorEnabled },
        phoneLoginAlias = resolve { phoneLoginAliasEnabled },
    )
}

internal fun AppConfiguration.authUiText(en: String, ru: String, kk: String): String = when {
    stateValues.appLanguage.lowercase().startsWith("ru") -> ru
    (stateValues.appLanguage.lowercase().startsWith("kk") || stateValues.appLanguage.lowercase().startsWith("kz")) -> kk
    else -> en
}

internal fun AppConfiguration.authResponseText(response: ResponseDataModel<*>): String =
    response.message.orEmpty().visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { authUiText("Something went wrong. Try again.", "Что-то пошло не так. Попробуйте ещё раз.", "Бірдеңе дұрыс болмады. Қайталап көріңіз.") }

@Composable
internal fun AppConfiguration.AdvancedAuthenticationLoginScreen() {
    var capabilities by remember { mutableStateOf<AitaAuthCapabilitiesDataModel?>(null) }
    var capabilitiesLoading by remember { mutableStateOf(true) }
    var capabilitiesError by remember { mutableStateOf("") }
    var capabilitiesRefresh by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(AitaLoginMode.PASSWORD) }
    var step by remember { mutableStateOf(AitaLoginStep.PRIMARY) }
    var identifierType by remember { mutableStateOf(AitaLoginIdentifierType.PHONE) }
    val loginState = remember { object : StateHost() {} }
    val countries = remember(stateValues.globalAppConfiguration.countries) { stateValues.globalAppConfiguration.countries.withTajikistanFallback() }
    var phoneCountry by remember { mutableStateOf("+" + (countries.firstOrNull { it.locale.equals("kz", true) }?.phoneNumberCode ?: "7")) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf<AitaAuthFlowDataModel?>(null) }
    var newPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val currentBusy by rememberUpdatedState(busy)

    // Retry capability lookup after a transient outage and expose provider-wide failures while waiting.
    // Never query an anonymous per-account delivery status: that would disclose account existence.
    LaunchedEffect(capabilitiesRefresh, step, mode) {
        do {
            if (!currentBusy) {
                capabilitiesLoading = capabilities == null
                try {
                    val response = AitaAdvancedAuthenticationClient.capabilities()
                    if (!response.negative && response.payload != null) {
                        capabilities = response.payload
                        capabilitiesError = ""
                    } else capabilitiesError = authResponseText(response)
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { capabilitiesError = authUiText("Connection unavailable", "Нет соединения", "Байланыс жоқ") }
                finally { capabilitiesLoading = false }
            }
            kotlinx.coroutines.delay(10_000L)
        } while (step == AitaLoginStep.EMAIL_CODE || capabilities == null || capabilities?.emailDeliveryUnavailable == true)
    }

    val availability = resolveAitaAuthUiAvailability(capabilities, capabilitiesLoading)
    val emailAvailability = if (mode == AitaLoginMode.RECOVERY) availability.passwordRecovery else availability.emailCodeLogin
    val emailReady = emailAvailability == AitaAuthFeatureAvailability.AVAILABLE && capabilities?.emailDeliveryUnavailable != true
    val countdown = rememberAuthFlowCountdown(flow)

    fun identifier(): String = normalizeAitaLoginIdentifier(
        if (identifierType == AitaLoginIdentifierType.EMAIL) email else phoneCountry + phone
    )?.value.orEmpty()

    fun reset(target: AitaLoginMode = mode) {
        if (busy) return
        mode = target; step = AitaLoginStep.PRIMARY; flow = null
        password = ""; code = ""; newPassword = ""; repeatPassword = ""; error = ""
    }

    suspend fun accept(response: ResponseDataModel<AitaAuthFlowDataModel>) {
        val result = response.payload
        if (response.negative || result == null) { error = authResponseText(response); return }
        if (result.nextStep == AitaAuthNextStep.AUTHENTICATED) {
            val tokens = result.tokenPair
            if (tokens == null || tokens.accessToken.isBlank() || tokens.refreshToken.isBlank()) {
                error = authUiText("Sign-in could not be completed", "Не удалось завершить вход", "Кіруді аяқтау мүмкін болмады")
                return
            }
            adoptAdvancedAuthenticationTokens(tokens)
            phone = ""; email = ""; password = ""; code = ""; flow = null
            return
        }
        val valid = when (result.nextStep) {
            AitaAuthNextStep.EMAIL_CODE -> result.flowId.isNotBlank() && (step == AitaLoginStep.PRIMARY || step == AitaLoginStep.EMAIL_CODE)
            AitaAuthNextStep.TOTP -> result.flowId.isNotBlank() && mode != AitaLoginMode.RECOVERY
            AitaAuthNextStep.PASSWORD_RESET -> result.flowId.isNotBlank() && result.resetTicket.isNotBlank() && mode == AitaLoginMode.RECOVERY
            AitaAuthNextStep.COMPLETE -> mode == AitaLoginMode.RECOVERY && step == AitaLoginStep.NEW_PASSWORD
            else -> false
        }
        if (!valid) { error = authUiText("Unexpected server response", "Некорректный ответ сервера", "Сервер жауабы дұрыс емес"); return }
        flow = result.copy(maskedDestination = result.maskedDestination.ifBlank { flow?.maskedDestination.orEmpty() })
        code = ""; password = ""
        step = when (result.nextStep) {
            AitaAuthNextStep.EMAIL_CODE -> AitaLoginStep.EMAIL_CODE
            AitaAuthNextStep.TOTP -> AitaLoginStep.TOTP
            AitaAuthNextStep.PASSWORD_RESET -> AitaLoginStep.NEW_PASSWORD
            else -> AitaLoginStep.COMPLETE
        }
        if (step == AitaLoginStep.COMPLETE) { flow = null; newPassword = ""; repeatPassword = "" }
    }

    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { error = authUiText("Request failed. Try again.", "Запрос не выполнен. Повторите.", "Сұрау орындалмады. Қайталаңыз.") }
            finally { busy = false }
        }
    }

    fun submitPrimary() {
        val who = identifier()
        val pass = password
        val selectedMode = mode
        if (who.isBlank() || (selectedMode == AitaLoginMode.PASSWORD && pass.isBlank())) return
        if (selectedMode != AitaLoginMode.PASSWORD && !emailReady) return
        runAction {
            accept(when (selectedMode) {
                AitaLoginMode.PASSWORD -> AitaAdvancedAuthenticationClient.passwordLogin(AitaPasswordLoginRequestDataModel(who, pass, buildCurrentClientDeviceInfo()))
                AitaLoginMode.RECOVERY -> AitaAdvancedAuthenticationClient.requestPasswordRecovery(AitaEmailCodeRequestDataModel(who, stateValues.appLanguage, buildCurrentClientDeviceInfo()))
                AitaLoginMode.EMAIL_CODE -> AitaAdvancedAuthenticationClient.requestLoginCode(AitaEmailCodeRequestDataModel(who, stateValues.appLanguage, buildCurrentClientDeviceInfo()))
            })
        }
    }

    fun verifyCode() {
        val active = flow ?: return
        if (busy || countdown.expired || code.length != 6) return
        val request = AitaEmailCodeVerifyRequestDataModel(active.flowId, code, buildCurrentClientDeviceInfo())
        runAction { accept(if (mode == AitaLoginMode.RECOVERY) AitaAdvancedAuthenticationClient.verifyPasswordRecovery(request) else AitaAdvancedAuthenticationClient.verifyLoginCode(request)) }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (stateValues.isNarrowScreen) Spacer(Modifier.height(stateValues.screenHeight / 18))
        Column(Modifier.width(stateValues.boundWidgetWidth).padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val title = if (mode == AitaLoginMode.RECOVERY) authUiText("Restore password", "Восстановить пароль", "Құпия сөзді қалпына келтіру") else stateValues.stringLogIn
            if (stateValues.isNarrowScreen) {
                val logo by stateValues.drawableResAITALogo.collectAsState()
                LargeIconWithTitleWidget(imageUrl = stateValues.drawablePathAITALogo, imageRes = logo,
                    title = title, iconSize = stateValues.boundWidgetWidth * 0.34f)
                AuthPreferencesChooser()
            } else Text(title, color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)

            if (step == AitaLoginStep.PRIMARY) {
                tabRowWidget(modifier = Modifier.fillMaxWidth(), enabled = !busy, persistSelection = false,
                    selectedIndexInitial = if (identifierType == AitaLoginIdentifierType.PHONE) "phone" else "email",
                    tabs = listOf(
                        TabContent("phone", stateValues.stringPhoneNumber) { identifierType = AitaLoginIdentifierType.PHONE; error = "" },
                        TabContent("email", stateValues.stringEmail) { identifierType = AitaLoginIdentifierType.EMAIL; error = "" }
                    ))
                if (mode != AitaLoginMode.RECOVERY) tabRowWidget(modifier = Modifier.fillMaxWidth(), enabled = !busy, persistSelection = false,
                    selectedIndexInitial = if (mode == AitaLoginMode.PASSWORD) "password" else "code",
                    tabs = listOf(
                        TabContent("password", authUiText("Password", "Пароль", "Құпия сөз")) { reset(AitaLoginMode.PASSWORD) },
                        TabContent("code", authUiText("Sign-in code", "Код для входа", "Кіру коды")) { reset(AitaLoginMode.EMAIL_CODE) }
                    ))
            }

            // The parent owns vertical scrolling. Do not nest a scroll container in its LazyColumn item.
            AnimatedContent(targetState = step, label = "aitaAuthStep") { currentStep ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (currentStep) {
                        AitaLoginStep.PRIMARY -> {
                            val ime = if (mode == AitaLoginMode.PASSWORD) ImeWithAction(ImeAction.Next) else ImeWithAction(ImeAction.Go, ::submitPrimary)
                            when (identifierType) {
                                AitaLoginIdentifierType.PHONE -> countrySelectionPhoneNumberTextField(
                                    modifier = Modifier.fillMaxWidth(), countries = countries, valueInitial = phone,
                                    stateHost = loginState, stateKey = "auth_phone", identityKey = "auth_phone", enabled = !busy,
                                    retainTextAcrossRecreation = false, persistTextDraft = false,
                                    retainSelectionAcrossRecreation = false, persistSelectionDraft = false, imeWithAction = ime,
                                    onSelectedCountryCodeChange = { if (!busy && !it.isNullOrBlank()) phoneCountry = it },
                                    onValueChange = { value, _, selected, apply -> if (!busy) { phone = value; if (!selected.isNullOrBlank()) phoneCountry = selected; error = ""; apply() } }
                                )
                                AitaLoginIdentifierType.EMAIL -> emailTextField(
                                    modifier = Modifier.fillMaxWidth(), stateHost = loginState, stateKey = "auth_email", identityKey = "auth_email",
                                    valueInitial = email, enabled = !busy, retainTextAcrossRecreation = false, persistTextDraft = false,
                                    imeWithAction = ime, onValueChange = { value, apply -> if (!busy) { email = value; error = ""; apply() } }
                                )
                            }
                            if (mode == AitaLoginMode.PASSWORD) aitaFormTextField(
                                modifier = Modifier.fillMaxWidth(), value = password, onValueChange = { password = it; error = "" },
                                titleText = stateValues.stringPassword, placeholderText = stateValues.stringEnterPassword,
                                identityKey = "auth_password", enabled = !busy, keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Go, onImeAction = ::submitPrimary, leadingIconPath = stateValues.drawablePathIconPassword,
                                password = true, sensitive = true
                            ) else {
                                if (identifierType == AitaLoginIdentifierType.PHONE) Text(
                                    authUiText("Code by email, not SMS", "Код придёт на email, не в SMS", "Код SMS емес, email арқылы келеді"),
                                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                if (!emailReady || capabilitiesError.isNotBlank()) {
                                    Text(when {
                                        capabilities?.emailDeliveryUnavailable == true -> authUiText("Email delivery delayed", "Доставка писем задерживается", "Хат жеткізу кешігуде")
                                        emailAvailability == AitaAuthFeatureAvailability.CHECKING -> authUiText("Checking…", "Проверяем…", "Тексерілуде…")
                                        capabilitiesError.isNotBlank() -> capabilitiesError
                                        else -> authUiText("Email codes unavailable", "Коды по email недоступны", "Email кодтары қолжетімсіз")
                                    }, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                    if (!capabilitiesLoading) AuthQuietAction(authUiText("Refresh", "Обновить", "Жаңарту"), !busy) { capabilitiesRefresh++ }
                                }
                            }
                            actionButton(modifier = Modifier.fillMaxWidth(), text = if (mode == AitaLoginMode.PASSWORD) stateValues.stringLogIn else authUiText("Get code", "Получить код", "Код алу"),
                                enabled = !busy && identifier().isNotBlank() && (if (mode == AitaLoginMode.PASSWORD) password.isNotBlank() else emailReady),
                                loading = busy, autoLoading = false, onClick = ::submitPrimary)
                        }
                        AitaLoginStep.EMAIL_CODE -> {
                            Text(flow?.maskedDestination?.takeIf { it.isNotBlank() } ?: authUiText("Check your account email", "Проверьте почту аккаунта", "Аккаунт поштаңызды тексеріңіз"),
                                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                            if (capabilities?.emailDeliveryUnavailable == true) Text(
                                authUiText("Email delivery delayed. You can still use a received code.", "Доставка задерживается. Полученный код остаётся действительным.", "Хат кешігуде. Алынған кодты қолдануға болады."),
                                color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                            AuthEmailCodeEntry(code, flow, busy, { code = it; error = "" }, ::verifyCode,
                                onResend = {
                                    val request = AitaEmailCodeResendRequestDataModel(flow?.flowId.orEmpty(), stateValues.appLanguage)
                                    runAction { accept(if (mode == AitaLoginMode.RECOVERY) AitaAdvancedAuthenticationClient.resendPasswordRecovery(request) else AitaAdvancedAuthenticationClient.resendLoginCode(request)) }
                                }, resendEnabled = capabilities?.emailDeliveryUnavailable != true)
                        }
                        AitaLoginStep.TOTP -> {
                            fun submit() {
                                if (code.isBlank() || countdown.expired) return
                                val request = AitaTotpLoginRequestDataModel(flow?.flowId.orEmpty(), code, buildCurrentClientDeviceInfo())
                                runAction { accept(AitaAdvancedAuthenticationClient.completeTotpLogin(request)) }
                            }
                            aitaFormTextField(modifier = Modifier.fillMaxWidth(), value = code, onValueChange = { code = it; error = "" },
                                titleText = authUiText("Authenticator / recovery code", "Аутентификатор / резервный код", "Аутентификатор / резервтік код"),
                                placeholderText = "000000", identityKey = "auth_totp", enabled = !busy, sensitive = true,
                                keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go, onImeAction = ::submit,
                                leadingIconPath = stateValues.drawablePathIconSecurity, onTransformValue = { it.take(32) })
                            if (countdown.expired) Text(authUiText("Sign in again", "Войдите заново", "Қайта кіріңіз"), color = stateValues.ErrorColor)
                            actionButton(modifier = Modifier.fillMaxWidth(), text = stateValues.stringLogIn, enabled = !busy && !countdown.expired && code.isNotBlank(), loading = busy, autoLoading = false, onClick = ::submit)
                        }
                        AitaLoginStep.NEW_PASSWORD -> PasswordRecoveryNewPasswordContent(newPassword, repeatPassword, busy,
                            { newPassword = it; error = "" }, { repeatPassword = it; error = "" }, onSubmit = {
                                when {
                                    countdown.expired -> error = authUiText("Request a new recovery code", "Запросите новый код восстановления", "Жаңа қалпына келтіру кодын сұраңыз")
                                    newPassword != repeatPassword -> error = authUiText("Passwords do not match", "Пароли не совпадают", "Құпия сөздер сәйкес емес")
                                    !newPassword.checkAsPassword() -> error = authUiText("8+ characters, a digit and a symbol", "От 8 символов, цифра и спецсимвол", "8+ таңба, сан және арнайы таңба")
                                    else -> {
                                        val request = AitaPasswordRecoveryResetRequestDataModel(flow?.flowId.orEmpty(), flow?.resetTicket.orEmpty(), newPassword)
                                        runAction { accept(AitaAdvancedAuthenticationClient.resetPassword(request)) }
                                    }
                                }
                            })
                        AitaLoginStep.COMPLETE -> {
                            Text(authUiText("Password updated", "Пароль обновлён", "Құпия сөз жаңартылды"), color = stateValues.AccentColor, fontWeight = FontWeight.Bold)
                            actionButton(modifier = Modifier.fillMaxWidth(), text = stateValues.stringLogIn, enabled = !busy, autoLoading = false) { reset(AitaLoginMode.PASSWORD) }
                        }
                    }
                }
            }
            if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize, textAlign = TextAlign.Center)
            when {
                step == AitaLoginStep.COMPLETE -> Unit
                step != AitaLoginStep.PRIMARY -> AuthQuietAction(authUiText("Back", "Назад", "Артқа"), !busy) { reset(mode) }
                mode == AitaLoginMode.RECOVERY -> AuthQuietAction(authUiText("Back to sign in", "Назад ко входу", "Кіруге қайту"), !busy) { reset(AitaLoginMode.PASSWORD) }
                else -> AuthQuietAction(authUiText("Forgot password?", "Забыли пароль?", "Құпия сөзді ұмыттыңыз ба?"), !busy) { reset(AitaLoginMode.RECOVERY) }
            }
            if (stateValues.isNarrowScreen && step == AitaLoginStep.PRIMARY && mode != AitaLoginMode.RECOVERY) {
                actionButton(modifier = Modifier.fillMaxWidth(), text = stateValues.stringSignUp, enabled = !busy && !stateValues.signUpInProgress, autoLoading = false) {
                    scope.launch { Navigation.UserAuth.goLeft(NavigationScreenModel.UserAuth.SignUp) }
                }
            }
        }
        Spacer(Modifier.height(stateValues.screenHeight / 18))
    }
}
