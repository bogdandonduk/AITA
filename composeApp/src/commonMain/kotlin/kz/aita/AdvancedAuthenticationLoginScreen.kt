package kz.aita

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.aita.auth.*

private enum class AitaLoginMode { PASSWORD, EMAIL_CODE, RECOVERY }
private enum class AitaLoginStep { PRIMARY, EMAIL_CODE, TOTP, NEW_PASSWORD, COMPLETE }
private enum class AitaLoginIdentifierType { PHONE, EMAIL }

internal fun AppConfiguration.authUiText(en: String, ru: String, kk: String): String = when {
    stateValues.appLanguage.lowercase().startsWith("ru") -> ru
    stateValues.appLanguage.lowercase().startsWith("kk") -> kk
    else -> en
}

internal fun AppConfiguration.authResponseText(response: ResponseDataModel<*>): String =
    response.message.orEmpty().visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { authUiText("Something went wrong. Try again.", "Что-то пошло не так. Попробуйте ещё раз.", "Бірдеңе дұрыс болмады. Қайталап көріңіз.") }

@Composable
internal fun AppConfiguration.AdvancedAuthenticationLoginScreen() {
    var capabilities by remember { mutableStateOf(AitaAuthCapabilitiesDataModel()) }
    var mode by remember { mutableStateOf(AitaLoginMode.PASSWORD) }
    var step by remember { mutableStateOf(AitaLoginStep.PRIMARY) }
    var identifierType by remember { mutableStateOf(AitaLoginIdentifierType.PHONE) }
    val transientLoginState = remember { object : StateHost() {} }
    val loginCountries = remember(stateValues.globalAppConfiguration.countries) {
        stateValues.globalAppConfiguration.countries.withTajikistanFallback()
    }
    val defaultPhoneCountryCode = remember(loginCountries) {
        loginCountries.firstOrNull { it.locale.equals("kz", ignoreCase = true) }
            ?.phoneNumberCode
            ?: loginCountries.firstOrNull { it.phoneNumberCode == "7" }?.phoneNumberCode
            ?: loginCountries.firstOrNull()?.phoneNumberCode
            ?: "7"
    }.let { "+$it" }
    var phoneCountryCode by remember(defaultPhoneCountryCode) { mutableStateOf(defaultPhoneCountryCode) }
    var phoneLocalNumber by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var flowId by remember { mutableStateOf("") }
    var resetTicket by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeatedPassword by remember { mutableStateOf("") }
    var maskedDestination by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var infoText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val response = AitaAdvancedAuthenticationClient.capabilities()
        response.payload?.let { capabilities = it }
    }

    fun resolvedIdentifier(): String {
        val raw = when (identifierType) {
            AitaLoginIdentifierType.PHONE -> phoneCountryCode + phoneLocalNumber
            AitaLoginIdentifierType.EMAIL -> emailAddress
        }
        return normalizeAitaLoginIdentifier(raw)?.value.orEmpty()
    }

    fun resetSensitiveState(targetMode: AitaLoginMode = mode) {
        mode = targetMode
        step = AitaLoginStep.PRIMARY
        password = ""
        code = ""
        flowId = ""
        resetTicket = ""
        newPassword = ""
        repeatedPassword = ""
        maskedDestination = ""
        errorText = ""
        infoText = ""
    }

    suspend fun consumeFlow(flow: AitaAuthFlowDataModel) {
        flow.tokenPair?.takeIf { it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() }?.let {
            adoptAdvancedAuthenticationTokens(it)
            phoneLocalNumber = ""
            emailAddress = ""
            password = ""
            code = ""
            resetTicket = ""
            return
        }
        flowId = flow.flowId.ifBlank { flowId }
        maskedDestination = flow.maskedDestination.ifBlank { maskedDestination }
        when (flow.nextStep) {
            AitaAuthNextStep.EMAIL_CODE -> step = AitaLoginStep.EMAIL_CODE
            AitaAuthNextStep.TOTP -> step = AitaLoginStep.TOTP
            AitaAuthNextStep.PASSWORD_RESET -> {
                resetTicket = flow.resetTicket
                step = AitaLoginStep.NEW_PASSWORD
                code = ""
            }
            AitaAuthNextStep.COMPLETE -> step = AitaLoginStep.COMPLETE
            AitaAuthNextStep.AUTHENTICATED -> Unit
        }
    }

    fun runAction(block: suspend () -> Unit) {
        if (busy) return
        coroutineScope.launch {
            busy = true
            errorText = ""
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    fun submitPasswordLogin() {
        val identifier = resolvedIdentifier()
        if (identifier.isBlank() || password.isBlank()) return
        runAction {
            val response = AitaAdvancedAuthenticationClient.passwordLogin(
                AitaPasswordLoginRequestDataModel(identifier, password, buildCurrentClientDeviceInfo())
            )
            response.payload?.let { consumeFlow(it) } ?: run { errorText = authResponseText(response) }
        }
    }

    fun requestSignInCode() {
        val identifier = resolvedIdentifier()
        if (identifier.isBlank()) return
        runAction {
            val response = AitaAdvancedAuthenticationClient.requestLoginCode(
                AitaEmailCodeRequestDataModel(identifier, stateValues.appLanguage, buildCurrentClientDeviceInfo())
            )
            response.payload?.let { consumeFlow(it) } ?: run { errorText = authResponseText(response) }
        }
    }

    fun requestRecoveryCode() {
        val identifier = resolvedIdentifier()
        if (identifier.isBlank()) return
        runAction {
            val response = AitaAdvancedAuthenticationClient.requestPasswordRecovery(
                AitaEmailCodeRequestDataModel(identifier, stateValues.appLanguage, buildCurrentClientDeviceInfo())
            )
            response.payload?.let { consumeFlow(it) } ?: run { errorText = authResponseText(response) }
        }
    }

    fun submitEmailCode() {
        if (code.length != 6) return
        runAction {
            val request = AitaEmailCodeVerifyRequestDataModel(flowId, code, buildCurrentClientDeviceInfo())
            val response = if (mode == AitaLoginMode.RECOVERY) {
                AitaAdvancedAuthenticationClient.verifyPasswordRecovery(request)
            } else {
                AitaAdvancedAuthenticationClient.verifyLoginCode(request)
            }
            response.payload?.let { consumeFlow(it) } ?: run { errorText = authResponseText(response) }
        }
    }

    fun submitTotpCode() {
        if (code.isBlank()) return
        runAction {
            val response = AitaAdvancedAuthenticationClient.completeTotpLogin(
                AitaTotpLoginRequestDataModel(flowId, code, buildCurrentClientDeviceInfo())
            )
            response.payload?.let { consumeFlow(it) } ?: run { errorText = authResponseText(response) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (stateValues.isNarrowScreen) Spacer(Modifier.height(stateValues.screenHeight / 11))

        Column(
            modifier = Modifier
                .width(stateValues.boundWidgetWidth)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (stateValues.isNarrowScreen) {
                val drawableResAITALogo by stateValues.drawableResAITALogo.collectAsState()
                LargeIconWithTitleWidget(
                    imageUrl = stateValues.drawablePathAITALogo,
                    imageRes = drawableResAITALogo,
                    title = when (mode) {
                        AitaLoginMode.RECOVERY -> authUiText("Restore password", "Восстановить пароль", "Құпия сөзді қалпына келтіру")
                        else -> stateValues.stringLogIn
                    },
                    iconSize = stateValues.boundWidgetWidth * 0.43f
                )
            } else {
                Text(
                    text = when (mode) {
                        AitaLoginMode.RECOVERY -> authUiText("Restore password", "Восстановить пароль", "Құпия сөзді қалпына келтіру")
                        else -> stateValues.stringLogIn
                    },
                    color = stateValues.TextColor,
                    fontSize = stateValues.titleTextSize,
                    fontWeight = FontWeight.Bold
                )
            }

            if (stateValues.isNarrowScreen) AuthPreferencesChooser()

            if (mode != AitaLoginMode.RECOVERY && step == AitaLoginStep.PRIMARY && capabilities.emailCodeLoginEnabled) {
                val selected = tabRowWidget(
                    modifier = Modifier.fillMaxWidth(),
                    selectedIndexInitial = if (mode == AitaLoginMode.PASSWORD) "password" else "code",
                    persistSelection = false,
                    tabs = listOf(
                        TabContent("password", authUiText("Password", "Пароль", "Құпия сөз")),
                        TabContent("code", authUiText("Email code", "Код из письма", "Email коды"))
                    )
                )
                LaunchedEffect(selected.id) {
                    val newMode = if (selected.id == "code") AitaLoginMode.EMAIL_CODE else AitaLoginMode.PASSWORD
                    if (newMode != mode) resetSensitiveState(newMode)
                }
            }

            if (step == AitaLoginStep.PRIMARY) {
                val selectedIdentifierType = tabRowWidget(
                    modifier = Modifier.fillMaxWidth(),
                    selectedIndexInitial = when (identifierType) {
                        AitaLoginIdentifierType.PHONE -> "phone"
                        AitaLoginIdentifierType.EMAIL -> "email"
                    },
                    persistSelection = false,
                    tabs = listOf(
                        TabContent("phone", stateValues.stringPhoneNumber),
                        TabContent("email", stateValues.stringEmail)
                    )
                )
                LaunchedEffect(selectedIdentifierType.id) {
                    val newType = if (selectedIdentifierType.id == "email") {
                        AitaLoginIdentifierType.EMAIL
                    } else {
                        AitaLoginIdentifierType.PHONE
                    }
                    if (newType != identifierType) {
                        identifierType = newType
                        errorText = ""
                        infoText = ""
                    }
                }
            }

            AnimatedContent(targetState = step, label = "advancedAuthStep") { currentStep ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (currentStep) {
                        AitaLoginStep.PRIMARY -> {
                            val identifierImeAction = when (mode) {
                                AitaLoginMode.PASSWORD -> ImeWithAction(ImeAction.Next)
                                AitaLoginMode.EMAIL_CODE -> ImeWithAction(ImeAction.Go, ::requestSignInCode)
                                AitaLoginMode.RECOVERY -> ImeWithAction(ImeAction.Go, ::requestRecoveryCode)
                            }

                            when (identifierType) {
                                AitaLoginIdentifierType.PHONE -> {
                                    countrySelectionPhoneNumberTextField(
                                        modifier = Modifier.fillMaxWidth(),
                                        countries = loginCountries,
                                        valueInitial = phoneLocalNumber,
                                        stateHost = transientLoginState,
                                        stateKey = "advanced_auth_login_phone",
                                        identityKey = "advanced-auth-login-phone",
                                        imeWithAction = identifierImeAction,
                                        retainTextAcrossRecreation = false,
                                        persistTextDraft = false,
                                        retainSelectionAcrossRecreation = false,
                                        persistSelectionDraft = false,
                                        autoFocus = true,
                                        onSelectedCountryCodeChange = { selectedCode ->
                                            selectedCode?.takeIf { it.isNotBlank() }?.let { phoneCountryCode = it }
                                        },
                                        onValueChange = { value, _, selectedCode, applyChange ->
                                            phoneLocalNumber = value
                                            selectedCode?.takeIf { it.isNotBlank() }?.let { phoneCountryCode = it }
                                            errorText = ""
                                            applyChange()
                                        }
                                    )
                                }

                                AitaLoginIdentifierType.EMAIL -> {
                                    emailTextField(
                                        modifier = Modifier.fillMaxWidth(),
                                        stateHost = transientLoginState,
                                        stateKey = "advanced_auth_login_email",
                                        identityKey = "advanced-auth-login-email",
                                        valueInitial = emailAddress,
                                        retainTextAcrossRecreation = false,
                                        persistTextDraft = false,
                                        autoFocus = true,
                                        imeWithAction = identifierImeAction,
                                        onValueChange = { value, applyChange ->
                                            emailAddress = value
                                            errorText = ""
                                            applyChange()
                                        }
                                    )
                                }
                            }

                            if (mode == AitaLoginMode.PASSWORD) {
                                aitaFormTextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = password,
                                    onValueChange = { password = it; errorText = "" },
                                    titleText = stateValues.stringPassword,
                                    placeholderText = stateValues.stringEnterPassword,
                                    identityKey = "advanced-auth-login-password",
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Go,
                                    onImeAction = ::submitPasswordLogin,
                                    leadingIconPath = stateValues.drawablePathIconPassword,
                                    password = true,
                                    sensitive = true
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = stateValues.stringLogIn,
                                    enabled = !busy && resolvedIdentifier().isNotBlank() && password.isNotBlank(),
                                    loading = busy,
                                    loadingText = stateValues.stringLoggingIn
                                ) { submitPasswordLogin() }
                            } else {
                                Text(
                                    text = authUiText(
                                        "We’ll send a one-time code to the verified email on this account.",
                                        "Мы отправим одноразовый код на подтверждённый email аккаунта.",
                                        "Бір реттік код аккаунттың расталған email мекенжайына жіберіледі."
                                    ),
                                    color = stateValues.PlaceholderTextColor,
                                    fontSize = stateValues.smallTextSize,
                                    textAlign = TextAlign.Center
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = authUiText("Send sign-in code", "Отправить код входа", "Кіру кодын жіберу"),
                                    enabled = !busy && resolvedIdentifier().isNotBlank(),
                                    loading = busy
                                ) { requestSignInCode() }
                            }
                        }

                        AitaLoginStep.EMAIL_CODE -> {
                            Text(
                                text = if (maskedDestination.isBlank()) {
                                    authUiText("Enter the six-digit code from your email.", "Введите шестизначный код из письма.", "Email-дағы алты таңбалы кодты енгізіңіз.")
                                } else {
                                    authUiText("Code sent to $maskedDestination", "Код отправлен на $maskedDestination", "Код $maskedDestination мекенжайына жіберілді")
                                },
                                color = stateValues.PlaceholderTextColor,
                                textAlign = TextAlign.Center
                            )
                            aitaFormTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = code,
                                onValueChange = { code = it; errorText = "" },
                                titleText = authUiText("Six-digit code", "Шестизначный код", "Алты таңбалы код"),
                                placeholderText = "000000",
                                identityKey = "advanced-auth-email-code",
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Go,
                                onImeAction = ::submitEmailCode,
                                leadingIconPath = stateValues.drawablePathIconSecurity,
                                sensitive = true,
                                onTransformValue = { it.filter(Char::isDigit).take(6) }
                            )
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = authUiText("Confirm code", "Подтвердить код", "Кодты растау"),
                                enabled = !busy && code.length == 6,
                                loading = busy
                            ) { submitEmailCode() }
                            TextButton(enabled = !busy, onClick = {
                                runAction {
                                    val request = AitaEmailCodeResendRequestDataModel(flowId, stateValues.appLanguage)
                                    val response = if (mode == AitaLoginMode.RECOVERY) {
                                        AitaAdvancedAuthenticationClient.resendPasswordRecovery(request)
                                    } else {
                                        AitaAdvancedAuthenticationClient.resendLoginCode(request)
                                    }
                                    response.payload?.let {
                                        flowId = it.flowId.ifBlank { flowId }
                                        maskedDestination = it.maskedDestination.ifBlank { maskedDestination }
                                        infoText = authUiText("A new code was requested.", "Запрошен новый код.", "Жаңа код сұралды.")
                                    } ?: run { errorText = authResponseText(response) }
                                }
                            }) { Text(authUiText("Send a new code", "Отправить новый код", "Жаңа код жіберу")) }
                        }

                        AitaLoginStep.TOTP -> {
                            Text(
                                text = authUiText(
                                    "Enter the code from your authenticator app, or one unused recovery code.",
                                    "Введите код из приложения-аутентификатора или неиспользованный резервный код.",
                                    "Аутентификатор қолданбасындағы кодты немесе пайдаланылмаған қалпына келтіру кодын енгізіңіз."
                                ),
                                color = stateValues.PlaceholderTextColor,
                                textAlign = TextAlign.Center
                            )
                            aitaFormTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = code,
                                onValueChange = { code = it; errorText = "" },
                                titleText = authUiText("Authenticator or recovery code", "Код аутентификатора или резервный код", "Аутентификатор немесе қалпына келтіру коды"),
                                placeholderText = authUiText("Enter a current code", "Введите действующий код", "Ағымдағы кодты енгізіңіз"),
                                identityKey = "advanced-auth-totp-or-recovery-code",
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Go,
                                onImeAction = ::submitTotpCode,
                                leadingIconPath = stateValues.drawablePathIconSecurity,
                                sensitive = true,
                                onTransformValue = { it.take(32) }
                            )
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = authUiText("Complete sign-in", "Завершить вход", "Кіруді аяқтау"),
                                enabled = !busy && code.isNotBlank(),
                                loading = busy
                            ) { submitTotpCode() }
                        }

                        AitaLoginStep.NEW_PASSWORD -> PasswordRecoveryNewPasswordContent(
                            newPassword = newPassword,
                            repeatedPassword = repeatedPassword,
                            busy = busy,
                            onNewPasswordChange = { newPassword = it; errorText = "" },
                            onRepeatedPasswordChange = { repeatedPassword = it; errorText = "" },
                            onSubmit = {
                                if (newPassword != repeatedPassword) {
                                    errorText = authUiText("Passwords do not match", "Пароли не совпадают", "Құпия сөздер сәйкес емес")
                                } else if (!newPassword.checkAsPassword()) {
                                    errorText = authUiText(
                                        "Use at least 8 characters, one digit and one special character.",
                                        "Используйте не менее 8 символов, одну цифру и один специальный символ.",
                                        "Кемінде 8 таңба, бір сан және бір арнайы таңба пайдаланыңыз."
                                    )
                                } else runAction {
                                    val response = AitaAdvancedAuthenticationClient.resetPassword(
                                        AitaPasswordRecoveryResetRequestDataModel(flowId, resetTicket, newPassword)
                                    )
                                    response.payload?.let { consumeFlow(it) } ?: run { errorText = authResponseText(response) }
                                }
                            }
                        )

                        AitaLoginStep.COMPLETE -> {
                            Text(
                                text = authUiText(
                                    "Password restored. You can sign in with your new password.",
                                    "Пароль восстановлен. Теперь можно войти с новым паролем.",
                                    "Құпия сөз қалпына келтірілді. Енді жаңа құпия сөзбен кіре аласыз."
                                ),
                                color = stateValues.AccentColor,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = authUiText("Return to sign in", "Вернуться ко входу", "Кіруге оралу")
                            ) { resetSensitiveState(AitaLoginMode.PASSWORD) }
                        }
                    }
                }
            }

            if (errorText.isNotBlank()) {
                Text(errorText, color = stateValues.ErrorColor, textAlign = TextAlign.Center, fontSize = stateValues.smallTextSize)
            } else if (infoText.isNotBlank()) {
                Text(infoText, color = stateValues.AccentColor, textAlign = TextAlign.Center, fontSize = stateValues.smallTextSize)
            }

            if (step != AitaLoginStep.PRIMARY && step != AitaLoginStep.COMPLETE) {
                TextButton(enabled = !busy, onClick = { resetSensitiveState(mode) }) {
                    Text(authUiText("Start again", "Начать заново", "Қайта бастау"))
                }
            }

            if (step == AitaLoginStep.PRIMARY && mode != AitaLoginMode.RECOVERY && capabilities.passwordRecoveryEnabled) {
                TextButton(enabled = !busy, onClick = { resetSensitiveState(AitaLoginMode.RECOVERY) }) {
                    Text(authUiText("Forgot password?", "Забыли пароль?", "Құпия сөзді ұмыттыңыз ба?"))
                }
            }

            if (step == AitaLoginStep.PRIMARY && mode == AitaLoginMode.RECOVERY) {
                Text(
                    text = authUiText(
                        "Enter your email or phone login alias. The recovery code will go to the verified email on the account.",
                        "Введите email или номер для входа. Код восстановления придёт на подтверждённый email аккаунта.",
                        "Email немесе кіру телефон нөмірін енгізіңіз. Қалпына келтіру коды аккаунттың расталған email мекенжайына келеді."
                    ),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize,
                    textAlign = TextAlign.Center
                )
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = authUiText("Send recovery code", "Отправить код восстановления", "Қалпына келтіру кодын жіберу"),
                    enabled = !busy && resolvedIdentifier().isNotBlank(),
                    loading = busy
                ) { requestRecoveryCode() }
                TextButton(enabled = !busy, onClick = { resetSensitiveState(AitaLoginMode.PASSWORD) }) {
                    Text(authUiText("Back to sign in", "Назад ко входу", "Кіруге қайту"))
                }
            }

            if (stateValues.isNarrowScreen && step == AitaLoginStep.PRIMARY && mode != AitaLoginMode.RECOVERY) {
                actionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stateValues.stringSignUp,
                    enabled = !busy && !stateValues.signUpInProgress
                ) {
                    coroutineScope.launch { Navigation.UserAuth.goLeft(NavigationScreenModel.UserAuth.SignUp) }
                }
            }
        }
        Spacer(Modifier.height(stateValues.screenHeight / 10))
    }
}
