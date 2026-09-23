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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kz.aita.auth.*

private enum class AitaLoginMode { PASSWORD, EMAIL_CODE, RECOVERY }
private enum class AitaLoginStep { PRIMARY, EMAIL_CODE, EMAIL_DESTINATION, EMAIL_SECOND_FACTOR, TOTP, PASSWORD_CONFIRMATION, NEW_PASSWORD, FINISHING, COMPLETE, AUTHENTICATOR_RECOVERY }
private enum class AitaCodeLoginMethod { EMAIL, AUTHENTICATOR }
private enum class AitaLoginIdentifierType { PHONE, EMAIL, SAVED }

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
        predicate(capabilities) -> AitaAuthFeatureAvailability.AVAILABLE
        else -> AitaAuthFeatureAvailability.UNAVAILABLE
    }

    return AitaAuthUiAvailability(
        emailCodeLogin = resolve { emailCodeLoginEnabled },
        passwordRecovery = resolve { passwordRecoveryEnabled },
        authenticator = resolve { authenticatorTwoFactorEnabled },
        phoneLoginAlias = resolve { phoneLoginAliasEnabled },
    )
}

internal fun AppConfiguration.authUiText(en: String, ru: String, kk: String, ky: String): String =
    when (effectiveAppLanguage(stateValues.appLanguage)) {
        "ru" -> ru
        "kk" -> kk
        "ky" -> ky
        else -> bundledInlineTranslation(en, ru, kk, stateValues.appLanguage) ?: en
    }

internal fun AppConfiguration.authResponseText(response: ResponseDataModel<*>): String =
    response.message.orEmpty().visibleLocalizedString(stateValues.appLanguage, "")
        .ifBlank { authUiText("Something went wrong. Try again.", "Что-то пошло не так. Попробуйте ещё раз.", "Бірдеңе дұрыс болмады. Қайталап көріңіз.", "Ката кетти. Кайра аракет кылыңыз.") }

@Composable
internal fun AppConfiguration.AdvancedAuthenticationLoginScreen() {
    var capabilities by remember { mutableStateOf<AitaAuthCapabilitiesDataModel?>(null) }
    var capabilitiesLoading by remember { mutableStateOf(true) }
    var capabilitiesError by remember { mutableStateOf("") }
    var capabilitiesRefresh by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(AitaLoginMode.PASSWORD) }
    var codeMethod by remember { mutableStateOf(AitaCodeLoginMethod.EMAIL) }
    var step by remember { mutableStateOf(AitaLoginStep.PRIMARY) }
    var identifierType by remember { mutableStateOf(AitaLoginIdentifierType.PHONE) }
    var savedUsers by remember { mutableStateOf<List<SavedLoginUser>>(emptyList()) }
    // Intentionally not rememberSaveable: the user must choose, even with a single identity.
    var selectedSavedUserId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { savedUsers = SavedLoginUsers.list() }
    // The parent owns the submitted identifier. A second StateHost could restore visible text
    // after the parent had cleared it, causing valid-looking input to submit an empty login.
    var formGeneration by remember { mutableLongStateOf(0L) }
    val countries = remember(stateValues.globalAppConfiguration.countries) { stateValues.globalAppConfiguration.countries.withTajikistanFallback() }
    var phoneCountry by remember { mutableStateOf("+" + (countries.firstOrNull { it.locale.equals("kz", true) }?.phoneNumberCode ?: "7")) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var emailDestination by remember { mutableStateOf(AitaEmailDestination.MAIN) }
    var flow by remember { mutableStateOf<AitaAuthFlowDataModel?>(null) }
    var newPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var actionJob by remember { mutableStateOf<Job?>(null) }
    var actionGeneration by remember { mutableLongStateOf(0L) }
    var installedSessionGeneration by remember { mutableStateOf<Long?>(null) }
    var pendingTokens by remember { mutableStateOf<TokenPair?>(null) }
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
                    val response = withTimeoutOrNull(15_000L) { AitaAdvancedAuthenticationClient.capabilities() }
                        ?: throw IllegalStateException("Capabilities request timed out")
                    if (!response.negative && response.payload != null) {
                        capabilities = response.payload
                        capabilitiesError = ""
                    } else capabilitiesError = authResponseText(response)
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { capabilitiesError = authUiText("Connection unavailable", "Нет соединения", "Байланыс жоқ", "Байланыш жеткиликсиз") }
                finally { capabilitiesLoading = false }
            }
            kotlinx.coroutines.delay(10_000L)
        } while (step == AitaLoginStep.EMAIL_CODE || mode != AitaLoginMode.PASSWORD || capabilities == null || capabilities?.emailDeliveryUnavailable == true)
    }

    val availability = resolveAitaAuthUiAvailability(capabilities, capabilitiesLoading)
    val emailAvailability = if (mode == AitaLoginMode.RECOVERY) availability.passwordRecovery else availability.emailCodeLogin
    val emailReady = emailAvailability == AitaAuthFeatureAvailability.AVAILABLE && capabilities?.emailDeliveryUnavailable != true
    val countdown = rememberAuthFlowCountdown(flow)

    fun identifier(): String {
        if (identifierType == AitaLoginIdentifierType.SAVED) return savedUsers.firstOrNull { it.id == selectedSavedUserId }?.login.orEmpty()
        if (identifierType == AitaLoginIdentifierType.EMAIL) return normalizeAitaEmail(email).orEmpty()
        val country = countries.firstOrNull { "+${it.phoneNumberCode}" == phoneCountry } ?: return ""
        // Use the same normalizer as the phone editor, then require the whole national number.
        // Neither an empty field nor a country code / partial number may request an email code.
        val national = kz.aita.auth.normalizeAitaPhoneFieldInput(phone, phoneCountry, country.phoneNumberSize)
            ?: return ""
        if (national.length != country.phoneNumberSize) return ""
        return normalizeAitaPhoneAlias(phoneCountry + national).orEmpty()
    }

    fun reset(target: AitaLoginMode = mode) {
        actionGeneration++
        actionJob?.cancel(); actionJob = null; busy = false
        formGeneration++
        mode = target; step = AitaLoginStep.PRIMARY; flow = null
        password = ""; code = ""; newPassword = ""; repeatPassword = ""; error = ""
    }

    val registrationEntry by registrationLoginEntry.collectAsState()
    LaunchedEffect(registrationEntry) {
        val entry = registrationEntry ?: return@LaunchedEffect
        if (!registrationLoginEntry.compareAndSet(entry, null)) return@LaunchedEffect
        if (entry.generation != currentAuthenticatedSessionGeneration() || getStoredUserAuthTokens?.invoke() != null) return@LaunchedEffect
        reset(if (entry.recoverPassword) AitaLoginMode.RECOVERY else AitaLoginMode.PASSWORD)
        selectedSavedUserId = null
        if (normalizeAitaEmail(entry.identifier) != null) {
            identifierType = AitaLoginIdentifierType.EMAIL
            email = entry.identifier
        } else {
            identifierType = AitaLoginIdentifierType.PHONE
            val digits = entry.identifier.removePrefix("+")
            val country = countries.sortedByDescending { it.phoneNumberCode.length }
                .firstOrNull { digits.startsWith(it.phoneNumberCode) }
            if (country != null) {
                phoneCountry = "+" + country.phoneNumberCode
                phone = digits.removePrefix(country.phoneNumberCode)
            }
        }
    }

    suspend fun finishSignIn() {
        val generation = installedSessionGeneration ?: pendingTokens?.let { tokens ->
            adoptAdvancedAuthenticationTokens(tokens).also {
                installedSessionGeneration = it
                pendingTokens = null
            }
        } ?: return
        val response = finishAdvancedAuthenticationSignIn(generation)
        currentCoroutineContext().ensureActive()
        if (response.negative || response.payload == null) error = authResponseText(response)
    }

    suspend fun accept(response: ResponseDataModel<AitaAuthFlowDataModel>) {
        currentCoroutineContext().ensureActive()
        val result = response.payload
        if (response.negative || result == null) { error = authResponseText(response); return }
        if (result.nextStep == AitaAuthNextStep.AUTHENTICATED) {
            val tokens = result.tokenPair
            if (tokens == null || tokens.accessToken.isBlank() || tokens.refreshToken.isBlank()) {
                error = authUiText("Sign-in could not be completed", "Не удалось завершить вход", "Кіруді аяқтау мүмкін болмады", "Кирүүнү аяктоо мүмкүн болгон жок")
                return
            }
            // The one-time factor has already been consumed. A profile/load failure must retry
            // this stage, never submit the same TOTP/recovery challenge a second time.
            step = AitaLoginStep.FINISHING
            pendingTokens = tokens
            // Retain only the identifier in memory for Back. Secrets never survive a step reset.
            password = ""; code = ""; flow = null
            finishSignIn()
            return
        }
        val valid = when (result.nextStep) {
            AitaAuthNextStep.EMAIL_CODE -> result.flowId.isNotBlank() && (step == AitaLoginStep.PRIMARY || step == AitaLoginStep.EMAIL_CODE)
            AitaAuthNextStep.TOTP -> result.flowId.isNotBlank() && mode != AitaLoginMode.RECOVERY
            AitaAuthNextStep.PASSWORD_CONFIRMATION -> result.flowId.isNotBlank() && mode != AitaLoginMode.RECOVERY
            AitaAuthNextStep.EMAIL_DESTINATION -> result.flowId.isNotBlank() && result.emailDestinations.isNotEmpty() && mode != AitaLoginMode.RECOVERY
            AitaAuthNextStep.EMAIL_SECOND_FACTOR -> result.flowId.isNotBlank() && result.parentFlowId.isNotBlank() && mode != AitaLoginMode.RECOVERY
            AitaAuthNextStep.PASSWORD_RESET -> result.flowId.isNotBlank() && result.resetTicket.isNotBlank() && mode == AitaLoginMode.RECOVERY
            AitaAuthNextStep.COMPLETE -> mode == AitaLoginMode.RECOVERY && step == AitaLoginStep.NEW_PASSWORD
            else -> false
        }
        if (!valid) { error = authUiText("Unexpected server response", "Некорректный ответ сервера", "Сервер жауабы дұрыс емес", "Серверден күтүлбөгөн жооп келди"); return }
        flow = result.copy(maskedDestination = result.maskedDestination.ifBlank { flow?.maskedDestination.orEmpty() })
        code = ""; password = ""
        if (result.nextStep == AitaAuthNextStep.EMAIL_DESTINATION) emailDestination = result.emailDestinations.first().destination
        step = when (result.nextStep) {
            AitaAuthNextStep.EMAIL_CODE -> AitaLoginStep.EMAIL_CODE
            AitaAuthNextStep.TOTP -> AitaLoginStep.TOTP
            AitaAuthNextStep.EMAIL_DESTINATION -> AitaLoginStep.EMAIL_DESTINATION
            AitaAuthNextStep.EMAIL_SECOND_FACTOR -> AitaLoginStep.EMAIL_SECOND_FACTOR
            AitaAuthNextStep.PASSWORD_CONFIRMATION -> AitaLoginStep.PASSWORD_CONFIRMATION
            AitaAuthNextStep.PASSWORD_RESET -> AitaLoginStep.NEW_PASSWORD
            else -> AitaLoginStep.COMPLETE
        }
        if (step == AitaLoginStep.COMPLETE) { flow = null; newPassword = ""; repeatPassword = "" }
    }

    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        val generation = ++actionGeneration
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val completed = withTimeoutOrNull(60_000L) { action(); true } ?: false
                if (!completed && generation == actionGeneration) error = authUiText("Request timed out. Try again.", "Время ожидания истекло. Повторите.", "Күту уақыты аяқталды. Қайталаңыз.", "Суроо-талаптын күтүү убактысы бүттү. Кайра аракет кылыңыз.")
            }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (generation == actionGeneration) error = authUiText("Request failed. Try again.", "Запрос не выполнен. Повторите.", "Сұрау орындалмады. Қайталаңыз.", "Суроо-талап аткарылган жок. Кайра аракет кылыңыз.")
            }
            finally {
                if (generation == actionGeneration) { busy = false; actionJob = null }
            }
        }
        actionJob = job
        job.start()
    }

    fun submitPrimary() {
        val who = identifier()
        val pass = password
        val selectedMode = mode
        val selectedCodeMethod = codeMethod
        val factor = code
        if (busy) return
        val kind = if (who.contains("@")) AitaAuthIdentifierKind.EMAIL else AitaAuthIdentifierKind.PHONE
        if (!aitaPrimarySignInIsWellFormed(who, kind, pass, selectedMode == AitaLoginMode.PASSWORD)) {
            error = if (selectedMode == AitaLoginMode.PASSWORD) {
                authUiText("Invalid login or password", "Неверный логин или пароль", "Логин немесе құпиясөз қате", "Логин же сырсөз туура эмес")
            } else authUiText("Enter a valid email or phone number", "Введите корректный email или номер телефона", "Дұрыс email немесе телефон нөмірін енгізіңіз", "Жарактуу электрондук почта же телефон номерин киргизиңиз")
            return
        }
        if (selectedMode == AitaLoginMode.EMAIL_CODE && selectedCodeMethod == AitaCodeLoginMethod.AUTHENTICATOR && !aitaSecondFactorIsWellFormed(factor)) {
            error = authUiText("Enter a 6-digit authenticator code or a recovery code", "Введите 6 цифр аутентификатора или резервный код", "Аутентификатордың 6 санын немесе қалпына келтіру кодын енгізіңіз", "Аутентификатордун 6 орундуу кодун же калыбына келтирүү кодун киргизиңиз")
            return
        }
        // Capabilities are advisory, not a per-account authorization gate. A stale/failed
        // lookup must not disable a valid form; the actual endpoint enforces availability.
        runAction {
            accept(when (selectedMode) {
                AitaLoginMode.PASSWORD -> AitaAdvancedAuthenticationClient.passwordLogin(AitaPasswordLoginRequestDataModel(who, pass, buildCurrentClientDeviceInfo()))
                AitaLoginMode.RECOVERY -> AitaAdvancedAuthenticationClient.requestPasswordRecovery(AitaEmailCodeRequestDataModel(who, stateValues.appLanguage, buildCurrentClientDeviceInfo()))
                AitaLoginMode.EMAIL_CODE -> if (selectedCodeMethod == AitaCodeLoginMethod.AUTHENTICATOR) {
                    AitaAdvancedAuthenticationClient.authenticatorLogin(AitaAuthenticatorLoginRequestDataModel(who, factor, buildCurrentClientDeviceInfo()))
                } else AitaAdvancedAuthenticationClient.requestLoginCode(AitaEmailCodeRequestDataModel(who, stateValues.appLanguage, buildCurrentClientDeviceInfo(), emailDestination))
            })
        }
    }

    fun recoverAuthenticator() {
        reset(AitaLoginMode.EMAIL_CODE)
        step = AitaLoginStep.AUTHENTICATOR_RECOVERY
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
            val title = if (mode == AitaLoginMode.RECOVERY) authUiText("Restore password", "Восстановить пароль", "Құпия сөзді қалпына келтіру", "Сырсөздү калыбына келтирүү") else stateValues.stringLogIn
            if (stateValues.isNarrowScreen) {
                val logo by stateValues.drawableResAITALogo.collectAsState()
                LargeIconWithTitleWidget(imageUrl = stateValues.drawablePathAITALogo, imageRes = logo,
                    title = title, iconSize = stateValues.boundWidgetWidth * 0.34f)
                AuthPreferencesChooser()
            } else Text(title, color = stateValues.TextColor, fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)

            if (step == AitaLoginStep.PRIMARY) {
                tabRowWidget(modifier = Modifier.fillMaxWidth(), enabled = !busy, persistSelection = false,
                    selectedIndexInitial = identifierType.name.lowercase(),
                    tabs = listOf(
                        TabContent("phone", stateValues.stringPhoneNumber) { identifierType = AitaLoginIdentifierType.PHONE; error = "" },
                        TabContent("email", stateValues.stringEmail) { identifierType = AitaLoginIdentifierType.EMAIL; error = "" }
                    ) + if (savedUsers.isNotEmpty()) listOf(TabContent("saved", savedLoginText("tab")) {
                        identifierType = AitaLoginIdentifierType.SAVED; selectedSavedUserId = null; reset(mode)
                    }) else emptyList())
                if (mode != AitaLoginMode.RECOVERY) tabRowWidget(modifier = Modifier.fillMaxWidth(), enabled = !busy, persistSelection = false,
                    selectedIndexInitial = if (mode == AitaLoginMode.PASSWORD) "password" else "code",
                    tabs = listOf(
                        TabContent("password", authUiText("Password", "Пароль", "Құпия сөз", "Сырсөз")) { reset(AitaLoginMode.PASSWORD) },
                        TabContent("code", authUiText("Sign-in code", "Код для входа", "Кіру коды", "Кирүү коду")) { reset(AitaLoginMode.EMAIL_CODE) }
                    ))
            }

            // The parent owns vertical scrolling. Do not nest a scroll container in its LazyColumn item.
            key(formGeneration) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = aitaOrderedTransitionSpec<AitaLoginStep> { it.ordinal },
                    label = "aitaAuthStep"
                ) { currentStep ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        when (currentStep) {
                            AitaLoginStep.PRIMARY -> {
                                val ime = if (mode == AitaLoginMode.PASSWORD || (mode == AitaLoginMode.EMAIL_CODE && codeMethod == AitaCodeLoginMethod.AUTHENTICATOR)) ImeWithAction(ImeAction.Next) else ImeWithAction(ImeAction.Go, ::submitPrimary)
                                when (identifierType) {
                                    AitaLoginIdentifierType.SAVED -> Column(Modifier.fillMaxWidth()) {
                                        AitaDropdownField(Modifier.fillMaxWidth(), title = "", selectedId = selectedSavedUserId,
                                            options = savedUsers.map { DropdownOption(it.id, it.name, subtitle = it.login) },
                                            placeholder = savedLoginText("select"), enabled = !busy) {
                                            selectedSavedUserId = it; password = ""; code = ""; error = ""
                                        }
                                        if (selectedSavedUserId != null) AuthQuietAction(savedLoginText("forget"), !busy) {
                                            val forgotten = selectedSavedUserId
                                            runAction {
                                                forgotten?.let { SavedLoginUsers.forget(it) }
                                                savedUsers = SavedLoginUsers.list(); selectedSavedUserId = null
                                                password = ""; code = ""
                                                if (savedUsers.isEmpty()) identifierType = AitaLoginIdentifierType.PHONE
                                            }
                                        }
                                    }
                                    AitaLoginIdentifierType.PHONE -> countrySelectionPhoneNumberTextField(
                                        modifier = Modifier.fillMaxWidth(), countries = countries, valueInitial = phone,
                                        valueIsNationalNumber = true, selectedCountryCodeInitial = phoneCountry,
                                        identityKey = "auth_phone", enabled = !busy,
                                        retainTextAcrossRecreation = false, persistTextDraft = false,
                                        retainSelectionAcrossRecreation = false, persistSelectionDraft = false, imeWithAction = ime,
                                        onSelectedCountryCodeChange = { if (!busy && !it.isNullOrBlank()) phoneCountry = it },
                                        onValueChange = { value, _, selected, apply -> if (!busy) { phone = value; if (!selected.isNullOrBlank()) phoneCountry = selected; error = ""; apply() } }
                                    )
                                    AitaLoginIdentifierType.EMAIL -> emailTextField(
                                        modifier = Modifier.fillMaxWidth(), identityKey = "auth_email",
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
                                    if (mode == AitaLoginMode.EMAIL_CODE) tabRowWidget(
                                        modifier = Modifier.fillMaxWidth(), enabled = !busy, persistSelection = false,
                                        selectedIndexInitial = if (codeMethod == AitaCodeLoginMethod.EMAIL) "email-code" else "authenticator-code",
                                        tabs = listOf(
                                            TabContent("email-code", authUiText("Email code", "Email-код", "Email коды", "Электрондук почтадагы код")) {
                                                if (codeMethod != AitaCodeLoginMethod.EMAIL) { reset(AitaLoginMode.EMAIL_CODE); codeMethod = AitaCodeLoginMethod.EMAIL }
                                            },
                                            TabContent("authenticator-code", authUiText("Authenticator", "Аутентификатор", "Аутентификатор", "Аутентификатор")) {
                                                if (codeMethod != AitaCodeLoginMethod.AUTHENTICATOR) { reset(AitaLoginMode.EMAIL_CODE); codeMethod = AitaCodeLoginMethod.AUTHENTICATOR }
                                            }
                                        )
                                    )
                                    if (mode == AitaLoginMode.EMAIL_CODE && codeMethod == AitaCodeLoginMethod.AUTHENTICATOR) {
                                        AuthenticatorCodeEntryField(code, busy, { code = it; error = "" }, ::submitPrimary, "auth-direct-factor")
                                    } else if (!emailReady || capabilitiesError.isNotBlank()) {
                                        Text(when {
                                            capabilities?.emailDeliveryUnavailable == true -> authUiText("Email delivery delayed", "Доставка писем задерживается", "Хат жеткізу кешігуде", "Электрондук каттын жеткирилиши кечигүүдө")
                                            emailAvailability == AitaAuthFeatureAvailability.CHECKING -> authUiText("Checking…", "Проверяем…", "Тексерілуде…", "Текшерилүүдө…")
                                            capabilitiesError.isNotBlank() -> capabilitiesError
                                            else -> authUiText("Email codes unavailable", "Коды по email недоступны", "Email кодтары қолжетімсіз", "Электрондук почта коддору жеткиликсиз")
                                        }, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                        if (!capabilitiesLoading) AuthQuietAction(authUiText("Refresh", "Обновить", "Жаңарту", "Жаңыртуу"), !busy) { capabilitiesRefresh++ }
                                    }
                                }
                                if (mode == AitaLoginMode.EMAIL_CODE && codeMethod == AitaCodeLoginMethod.EMAIL &&
                                    !identifier().contains("@") && capabilities?.emailSecondFactorEnabled == true) {
                                    AuthEmailDestinationPicker(emailDestination, !busy, onSelected = { emailDestination = it })
                                }
                                actionButton(modifier = Modifier.fillMaxWidth(), text = if (mode == AitaLoginMode.PASSWORD || (mode == AitaLoginMode.EMAIL_CODE && codeMethod == AitaCodeLoginMethod.AUTHENTICATOR)) stateValues.stringLogIn else authUiText("Get code", "Получить код", "Код алу", "Код алуу"),
                                    enabled = !busy,
                                    loading = busy, autoLoading = false, onClick = ::submitPrimary)
                                if (mode == AitaLoginMode.EMAIL_CODE && codeMethod == AitaCodeLoginMethod.AUTHENTICATOR) {
                                    AuthQuietAction(authUiText("Lost authenticator?", "Нет доступа к аутентификатору?", "Аутентификаторға қолжетімділік жоқ па?", "Аутентификаторду жоготтуңузбу?"), !busy, ::recoverAuthenticator)
                                }
                            }
                            AitaLoginStep.EMAIL_CODE -> {
                                Text(flow?.maskedDestination?.takeIf { it.isNotBlank() } ?: authUiText("Check your account email", "Проверьте почту аккаунта", "Аккаунт поштаңызды тексеріңіз", "Аккаунтуңуздун электрондук почтасын текшериңиз"),
                                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                if (capabilities?.emailDeliveryUnavailable == true) Text(
                                    authUiText("Email delivery delayed. You can still use a received code.", "Доставка задерживается. Полученный код остаётся действительным.", "Хат кешігуде. Алынған кодты қолдануға болады.", "Электрондук каттын жеткирилиши кечигүүдө. Келген кодду дагы эле колдонсоңуз болот."),
                                    color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                                AuthEmailCodeEntry(code, flow, busy, { code = it; error = "" }, ::verifyCode,
                                    onResend = {
                                        val request = AitaEmailCodeResendRequestDataModel(flow?.flowId.orEmpty(), stateValues.appLanguage)
                                        runAction { accept(if (mode == AitaLoginMode.RECOVERY) AitaAdvancedAuthenticationClient.resendPasswordRecovery(request) else AitaAdvancedAuthenticationClient.resendLoginCode(request)) }
                                    }, resendEnabled = true)
                            }
                            AitaLoginStep.EMAIL_DESTINATION -> {
                                Text(authUiText("Send your code to", "Куда отправить код", "Кодты жіберу мекенжайы", "Кодду жөнөтүү дареги"),
                                    color = stateValues.TextColor, fontSize = stateValues.accentTextSize)
                                AuthEmailDestinationPicker(emailDestination, !busy, flow?.emailDestinations.orEmpty()) { emailDestination = it }
                                actionButton(modifier = Modifier.fillMaxWidth(), text = authUiText("Get code", "Получить код", "Код алу", "Код алуу"), enabled = !busy && !countdown.expired,
                                    loading = busy, autoLoading = false) {
                                    val request = AitaLoginEmailFactorRequest(flow?.flowId.orEmpty(), emailDestination, stateValues.appLanguage)
                                    runAction { accept(AitaAdvancedAuthenticationClient.requestLoginEmailFactor(request)) }
                                }
                            }
                            AitaLoginStep.EMAIL_SECOND_FACTOR -> {
                                Text(flow?.maskedDestination.orEmpty(), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                AuthEmailCodeEntry(code, flow, busy, { code = it; error = "" },
                                    onSubmit = {
                                        val request = AitaEmailCodeVerifyRequestDataModel(flow?.flowId.orEmpty(), code, buildCurrentClientDeviceInfo())
                                        runAction { accept(AitaAdvancedAuthenticationClient.verifyLoginEmailFactor(request)) }
                                    }, onResend = {
                                        val current = flow
                                        val request = AitaLoginEmailFactorRequest(current?.parentFlowId.orEmpty(), current?.selectedEmailDestination ?: emailDestination, stateValues.appLanguage)
                                        runAction { accept(AitaAdvancedAuthenticationClient.requestLoginEmailFactor(request)) }
                                    }, identity = "auth-email-second-factor")
                                AuthQuietAction(authUiText("Choose another email", "Выбрать другой email", "Басқа email таңдау", "Башка электрондук почтаны тандаңыз"), !busy) {
                                    flow = flow?.copy(flowId = flow?.parentFlowId.orEmpty(), parentFlowId = "", nextStep = AitaAuthNextStep.EMAIL_DESTINATION)
                                    code = ""; step = AitaLoginStep.EMAIL_DESTINATION
                                }
                            }
                            AitaLoginStep.TOTP -> {
                                fun submit() {
                                    if (busy) return
                                    if (countdown.expired) {
                                        error = authUiText("Sign in again", "Войдите заново", "Қайта кіріңіз", "Кайра кирүү")
                                        return
                                    }
                                    if (!aitaSecondFactorIsWellFormed(code)) {
                                        error = authUiText("Enter a 6-digit authenticator code or a recovery code", "Введите 6 цифр аутентификатора или резервный код", "Аутентификатордың 6 санын немесе қалпына келтіру кодын енгізіңіз", "Аутентификатордун 6 орундуу кодун же калыбына келтирүү кодун киргизиңиз")
                                        return
                                    }
                                    val request = AitaTotpLoginRequestDataModel(flow?.flowId.orEmpty(), code, buildCurrentClientDeviceInfo())
                                    runAction { accept(AitaAdvancedAuthenticationClient.completeTotpLogin(request)) }
                                }
                                AuthenticatorCodeEntryField(code, busy, { code = it; error = "" }, ::submit, "auth_totp")
                                if (countdown.expired) Text(authUiText("Sign in again", "Войдите заново", "Қайта кіріңіз", "Кайра кирүү"), color = stateValues.ErrorColor)
                                actionButton(modifier = Modifier.fillMaxWidth(), text = stateValues.stringLogIn, enabled = !busy, loading = busy, autoLoading = false, onClick = ::submit)
                            }
                            AitaLoginStep.PASSWORD_CONFIRMATION -> {
                                fun submit() {
                                    if (busy) return
                                    if (countdown.expired || password.isBlank()) {
                                        error = if (countdown.expired) authUiText("Sign in again", "Войдите заново", "Қайта кіріңіз", "Кайра кирүү")
                                            else authUiText("Enter your password", "Введите пароль", "Құпия сөзді енгізіңіз", "Сырсөзүңүздү киргизиңиз")
                                        return
                                    }
                                    val request = AitaAuthenticatorPasswordRequestDataModel(flow?.flowId.orEmpty(), password, buildCurrentClientDeviceInfo())
                                    runAction { accept(AitaAdvancedAuthenticationClient.completeAuthenticatorPassword(request)) }
                                }
                                Text(authUiText("Two-factor sign-in is on. Confirm your password.", "Двухфакторный вход включён. Подтвердите пароль.", "Екі факторлы кіру қосулы. Құпия сөзіңізді растаңыз.", "Эки факторлуу кирүү күйүк. Сырсөзүңүздү ырастатыңыз."),
                                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                aitaFormTextField(modifier = Modifier.fillMaxWidth(), value = password, onValueChange = { password = it; error = "" },
                                    titleText = stateValues.stringPassword, placeholderText = stateValues.stringEnterPassword,
                                    identityKey = "auth-factor-password", enabled = !busy, sensitive = true, password = true,
                                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, onImeAction = ::submit,
                                    leadingIconPath = stateValues.drawablePathIconPassword)
                                actionButton(modifier = Modifier.fillMaxWidth(), text = stateValues.stringLogIn, enabled = !busy,
                                    loading = busy, autoLoading = false, onClick = ::submit)
                            }
                            AitaLoginStep.AUTHENTICATOR_RECOVERY -> AuthenticatorRecoveryEditor(
                                initialIdentifier = identifier(), onClose = { reset(AitaLoginMode.PASSWORD) })
                            AitaLoginStep.NEW_PASSWORD -> PasswordRecoveryNewPasswordContent(newPassword, repeatPassword, busy,
                                { newPassword = it; error = "" }, { repeatPassword = it; error = "" }, onSubmit = {
                                    when {
                                        countdown.expired -> error = authUiText("Request a new recovery code", "Запросите новый код восстановления", "Жаңа қалпына келтіру кодын сұраңыз", "Жаңы калыбына келтирүү кодун суроо")
                                        newPassword != repeatPassword -> error = authUiText("Passwords do not match", "Пароли не совпадают", "Құпия сөздер сәйкес емес", "Сырсөздөр дал келбейт")
                                        !newPassword.checkAsPassword() -> error = authUiText("8+ characters, a digit and a symbol", "От 8 символов, цифра и спецсимвол", "8+ таңба, сан және арнайы таңба", "8+ белги, цифра жана атайын белги")
                                        else -> {
                                            val request = AitaPasswordRecoveryResetRequestDataModel(flow?.flowId.orEmpty(), flow?.resetTicket.orEmpty(), newPassword)
                                            runAction { accept(AitaAdvancedAuthenticationClient.resetPassword(request)) }
                                        }
                                    }
                                })
                            AitaLoginStep.FINISHING -> {
                                Text(authUiText("Finishing sign-in", "Завершаем вход", "Кіру аяқталуда", "Кирүү аякталууда"),
                                    color = stateValues.TextColor, fontSize = stateValues.accentTextSize)
                                Text(authUiText("Credentials accepted. Loading your account…", "Данные приняты. Загружаем аккаунт…", "Деректер қабылданды. Аккаунт жүктелуде…", "Кирүү маалыматы кабыл алынды. Аккаунтуңуз жүктөлүүдө…"),
                                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
                                actionButton(modifier = Modifier.fillMaxWidth(),
                                    text = authUiText("Continue", "Продолжить", "Жалғастыру", "Улантуу"),
                                    enabled = !busy, loading = busy, autoLoading = false) { runAction { finishSignIn() } }
                            }
                            AitaLoginStep.COMPLETE -> {
                                Text(authUiText("Password updated", "Пароль обновлён", "Құпия сөз жаңартылды", "Сырсөз жаңыртылды"), color = stateValues.AccentColor, fontWeight = FontWeight.Bold)
                                actionButton(modifier = Modifier.fillMaxWidth(), text = stateValues.stringLogIn, enabled = !busy, autoLoading = false) { reset(AitaLoginMode.PASSWORD) }
                            }
                        }
                    }
                }
            }
            if (step == AitaLoginStep.TOTP) {
                AuthQuietAction(authUiText("Lost authenticator?", "Нет доступа к аутентификатору?", "Аутентификаторға қолжетімділік жоқ па?", "Аутентификаторду жоготтуңузбу?"), !busy, ::recoverAuthenticator)
            }
            if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize, textAlign = TextAlign.Center)
            when {
                step == AitaLoginStep.COMPLETE || step == AitaLoginStep.AUTHENTICATOR_RECOVERY -> Unit
                step == AitaLoginStep.FINISHING -> AuthQuietAction(authUiText("Back to sign in", "Назад ко входу", "Кіруге қайту", "Кирүүгө кайтуу"), !busy) {
                    runAction {
                        installedSessionGeneration?.let { discardAdvancedAuthenticationSignIn(it) }
                        installedSessionGeneration = null; pendingTokens = null
                        reset(AitaLoginMode.PASSWORD)
                    }
                }
                step != AitaLoginStep.PRIMARY -> AuthQuietAction(authUiText("Back", "Назад", "Артқа", "Артка"), true) { reset(mode) }
                mode == AitaLoginMode.RECOVERY -> AuthQuietAction(authUiText("Back to sign in", "Назад ко входу", "Кіруге қайту", "Кирүүгө кайтуу"), !busy) { reset(AitaLoginMode.PASSWORD) }
                else -> Unit
            }
            if (stateValues.isNarrowScreen && step == AitaLoginStep.PRIMARY && mode != AitaLoginMode.RECOVERY) {
                actionButton(modifier = Modifier.fillMaxWidth(), text = stateValues.stringSignUp, enabled = !busy && !stateValues.signUpInProgress, autoLoading = false) {
                    scope.launch { Navigation.UserAuth.goLeft(NavigationScreenModel.UserAuth.SignUp) }
                }
            }
            if (step == AitaLoginStep.PRIMARY && mode == AitaLoginMode.PASSWORD) {
                AuthQuietAction(authUiText("Forgot password?", "Забыли пароль?", "Құпия сөзді ұмыттыңыз ба?", "Сырсөздү унуттуңузбу?"), !busy) {
                    reset(AitaLoginMode.RECOVERY)
                }
            }
        }
        Spacer(Modifier.height(stateValues.screenHeight / 18))
    }
}
