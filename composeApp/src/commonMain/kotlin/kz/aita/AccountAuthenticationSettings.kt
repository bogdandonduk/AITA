package kz.aita

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kz.aita.auth.*

private enum class AccountAuthEditor { NONE, TOTP_ENABLE, TOTP_SETUP, TOTP_DISABLE, TOTP_POLICY, RECOVERY_CODES, PHONE, EMAIL, TOTP_RECOVERY }

@Composable
internal fun AppConfiguration.AccountAuthenticationSettingsCard(
    initiallyExpanded: Boolean = false,
    collapsible: Boolean = true,
) {
    key(stateValues.userAccount?.id, currentAuthenticatedSessionGeneration()) {
        AccountAuthenticationSettingsContent(initiallyExpanded, collapsible)
    }
}

@Composable
private fun AppConfiguration.AccountAuthenticationSettingsContent(initiallyExpanded: Boolean, collapsible: Boolean) {
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    var loading by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf<AitaAuthenticationSettingsDataModel?>(null) }
    var capabilities by remember { mutableStateOf<AitaAuthCapabilitiesDataModel?>(null) }
    var capabilitiesError by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf(AccountAuthEditor.NONE) }
    var setup by remember { mutableStateOf<AitaTotpSetupDataModel?>(null) }
    var setupCode by remember { mutableStateOf("") }
    var setupRequireForLogin by remember { mutableStateOf(true) }
    var requestedLoginRequirement by remember { mutableStateOf(true) }
    var currentPassword by remember { mutableStateOf("") }
    var secondFactor by remember { mutableStateOf("") }
    var phoneAlias by remember { mutableStateOf("") }
    var phoneAction by remember { mutableStateOf(AitaPhoneAliasAction.ADD_OR_REPLACE) }
    var phoneFlowId by remember { mutableStateOf("") }
    var phoneFlow by remember { mutableStateOf<AitaAuthFlowDataModel?>(null) }
    var phoneCode by remember { mutableStateOf("") }
    var recoveryCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val screenScope = rememberCoroutineScope()
    val owner = captureAitaAuthenticationUiOwner()

    @Composable
    fun QuietAction(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
        TextButton(onClick = onClick, enabled = enabled && !loading,
            colors = ButtonDefaults.textButtonColors(contentColor = stateValues.AccentColor,
                disabledContentColor = stateValues.PlaceholderTextColor), content = content)
    }

    fun clearSensitive() {
        setup = null
        setupCode = ""
        currentPassword = ""
        secondFactor = ""
        phoneCode = ""
        phoneFlowId = ""
        phoneFlow = null
        recoveryCodes = emptyList()
        error = ""
        info = ""
    }

    fun beginAction() {
        loading = true
        error = ""
        info = ""
    }

    fun launchSecurityAction(action: suspend () -> Unit) {
        if (loading || !owner.isCurrent()) return
        beginAction()
        screenScope.launch {
            try {
                if (!owner.isCurrent()) return@launch
                if (withTimeoutOrNull(45_000L) { action(); true } != true) {
                    error = authUiText("Request timed out. Refresh settings before retrying.",
                        "Время ожидания истекло. Обновите настройки перед повтором.",
                        "Күту уақыты аяқталды. Қайталаудан бұрын баптауларды жаңартыңыз.", "Суроо-талаптын күтүү убактысы бүттү. Кайра аракет кылуудан мурун жөндөөлөрдү жаңыртыңыз.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = authUiText(
                    "Request failed. Try again.",
                    "Запрос не выполнен. Повторите.",
                    "Сұрау орындалмады. Қайталаңыз.", "Суроо-талап аткарылган жок. Кайра аракет кылыңыз."
                )
            } finally {
                loading = false
            }
        }
    }

    fun load() {
        if (loading || !owner.isCurrent()) return
        loading = true
        error = ""
        capabilitiesError = ""
        screenScope.launch {
            try {
                if (!owner.isCurrent()) return@launch
                val capabilitiesResponse = AitaAdvancedAuthenticationClient.capabilities()
                capabilitiesResponse.payload?.let {
                    capabilities = it
                } ?: run {
                    if (capabilities == null) capabilitiesError = authResponseText(capabilitiesResponse)
                }
                if (!owner.isCurrent()) return@launch
                val settingsResponse = AitaAdvancedAuthenticationClient.settings()
                if (!owner.isCurrent()) return@launch
                settingsResponse.payload?.let {
                    settings = it
                    phoneAlias = it.phoneLoginAlias.orEmpty()
                } ?: run { error = authResponseText(settingsResponse) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = authUiText(
                    "Could not load settings. Retry.",
                    "Не удалось загрузить настройки. Повторите.",
                    "Баптаулар жүктелмеді. Қайталаңыз.", "Жөндөөлөрдү жүктөө мүмкүн болгон жок. Кайра аракет кылыңыз."
                )
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(expanded) { if (expanded && settings == null) load() }

    val authAvailability = resolveAitaAuthUiAvailability(capabilities, loading && capabilities == null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                stateValues.PlaceholderTextColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CpImage(
                modifier = Modifier.size(stateValues.iconSize),
                url = stateValues.drawablePathIconSecurity,
                fallbackRes = stateValues.drawableResIconSecurity.value,
                contentDescription = authUiText("Sign-in and security", "Вход и безопасность", "Кіру және қауіпсіздік", "Кирүү жана коопсуздук"),
                tintColor = stateValues.AccentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = authUiText("Sign-in & security", "Вход и безопасность", "Кіру және қауіпсіздік", "Кирүү жана коопсуздук"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        settings?.loginSecondFactor != null && settings?.loginSecondFactor != AitaLoginSecondFactor.NONE -> authUiText("Two-factor sign-in is on", "Двухфакторный вход включён", "Екі факторлы кіру қосулы", "Эки факторлуу кирүү күйүк")
                        settings?.authenticatorEnabled == true -> authUiText("Authenticator connected · login 2FA off", "Аутентификатор подключён · 2FA при входе выключена", "Аутентификатор қосылған · кіру 2FA өшірулі", "Аутентификатор туташкан · кирүүдөгү 2FA өчүк")
                        settings != null -> authUiText("Password, email code and recovery settings", "Пароль, код из письма и восстановление", "Құпия сөз, email коды және қалпына келтіру", "Сырсөз, электрондук почта коду жана калыбына келтирүү жөндөөлөрү")
                        else -> authUiText("Manage secure ways to sign in", "Управление безопасными способами входа", "Қауіпсіз кіру тәсілдерін басқару", "Кирүүнүн коопсуз ыкмаларын башкаруу")
                    },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
            if (expanded) AuthSecurityIconAction(authUiText("Refresh", "Обновить", "Жаңарту", "Жаңыртуу"),
                stateValues.drawablePathIconRefresh, stateValues.drawableResIconRefresh.value,
                !loading && editor == AccountAuthEditor.NONE, ::load)
            if (collapsible) {
                QuietAction(onClick = { expanded = !expanded; if (!expanded) { editor = AccountAuthEditor.NONE; clearSensitive() } }) {
                    Text(if (expanded) authUiText("Close", "Закрыть", "Жабу", "Жабуу") else authUiText("Manage", "Управлять", "Басқару", "Башкаруу"))
                }
            }
        }

        AnimatedVisibility(expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (loading && settings == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = stateValues.AccentColor, trackColor = stateValues.PlaceholderTextColor.copy(alpha = 0.12f))
                }

                settings?.let { current ->
                    AuthSettingsInfoRow(authUiText("Main phone number", "Основной номер телефона", "Негізгі телефон нөмірі", "Негизги телефон номери"),
                        current.mainPhoneNumber.ifBlank { stateValues.userAccount?.phoneNumber.orEmpty() }
                            .ifBlank { authUiText("Not configured", "Не настроен", "Бапталмаған", "Жөндөлгөн эмес") })
                    AuthSettingsInfoRow(authUiText("Main email", "Основной email", "Негізгі email", "Негизги электрондук почта"), current.email)
                    AuthSettingsInfoRow(
                        authUiText("Extra phone number", "Дополнительный номер телефона", "Қосымша телефон нөмірі", "Кошумча телефон номери"),
                        current.phoneLoginAlias ?: authUiText("Not configured", "Не настроен", "Бапталмаған", "Жөндөлгөн эмес")
                    )
                    AuthSettingsInfoRow(authUiText("Extra email", "Дополнительный email", "Қосымша email", "Кошумча электрондук почта"),
                        current.additionalLoginEmails.joinToString("\n").ifBlank { authUiText("Not configured", "Не настроен", "Бапталмаған", "Жөндөлгөн эмес") })
                    AuthSettingsInfoRow(
                        authUiText("Authenticator", "Аутентификатор", "Аутентификатор", "Аутентификатор"),
                        if (current.authenticatorEnabled) authUiText("Connected", "Подключён", "Қосылған", "Туташкан") else authUiText("Off", "Выключен", "Өшірулі", "Өчүк"),
                        accent = current.authenticatorEnabled
                    )
                    if (current.authenticatorEnabled) {
                        AuthSettingsInfoRow(
                            authUiText("Recovery codes remaining", "Осталось резервных кодов", "Қалған қалпына келтіру кодтары", "Калган калыбына келтирүү коддору"),
                            current.recoveryCodesRemaining.toString()
                        )
                    }
                    if (editor == AccountAuthEditor.NONE && (capabilities?.emailSecondFactorEnabled == true || capabilities?.authenticatorLoginPolicyEnabled == true)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AuthenticatorLoginRequirementToggle(current.loginSecondFactor != AitaLoginSecondFactor.NONE, !loading) { required ->
                                clearSensitive(); requestedLoginRequirement = required; editor = AccountAuthEditor.TOTP_POLICY
                            }
                            if (current.loginSecondFactor != AitaLoginSecondFactor.NONE) AuthQuietAction(
                                if (current.loginSecondFactor == AitaLoginSecondFactor.EMAIL) authUiText("Email code", "Код из письма", "Email коды", "Электрондук почтадагы код")
                                else authUiText("Authenticator", "Аутентификатор", "Аутентификатор", "Аутентификатор"), !loading) {
                                clearSensitive(); requestedLoginRequirement = true; editor = AccountAuthEditor.TOTP_POLICY
                            }
                        }
                    }
                }

                if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                if (info.isNotBlank()) Text(info, color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)

                when (editor) {
                    AccountAuthEditor.NONE -> {
                        actionButton(
                            autoLoading = false,
                            modifier = Modifier.fillMaxWidth(),
                            text = if (settings?.authenticatorEnabled == true) {
                                authUiText("Disable authenticator", "Отключить аутентификатор", "Аутентификаторды өшіру", "Аутентификаторду өчүрүү")
                            } else {
                                authUiText("Enable authenticator", "Включить аутентификатор", "Аутентификаторды қосу", "Аутентификаторду күйгүзүү")
                            },
                            iconPath = stateValues.drawablePathIconSecurity,
                            enabled = !loading &&
                                settings != null &&
                                authAvailability.authenticator == AitaAuthFeatureAvailability.AVAILABLE,
                            onDisabledClick = {
                                if (authAvailability.authenticator != AitaAuthFeatureAvailability.AVAILABLE) {
                                    info = authUiText(
                                        "Authenticator protection is not enabled on this server yet.",
                                        "Защита аутентификатором пока не включена на этом сервере.",
                                        "Аутентификатор қорғанысы бұл серверде әзірге қосылмаған.", "Бул серверде аутентификатор менен коргоо азырынча иштетиле элек."
                                    )
                                }
                            }
                        ) {
                            clearSensitive()
                            if (settings?.authenticatorEnabled == true) {
                                editor = AccountAuthEditor.TOTP_DISABLE
                            } else {
                                editor = AccountAuthEditor.TOTP_ENABLE
                            }
                        }
                        if (settings?.authenticatorEnabled == true) {
                            actionButton(
                                autoLoading = false,
                                modifier = Modifier.fillMaxWidth(),
                                text = authUiText("Generate new recovery codes", "Создать новые резервные коды", "Жаңа қалпына келтіру кодтарын жасау", "Жаңы калыбына келтирүү коддорун түзүү"),
                                iconPath = stateValues.drawablePathIconPassword,
                                enabled = !loading &&
                                    authAvailability.authenticator == AitaAuthFeatureAvailability.AVAILABLE,
                                onDisabledClick = {
                                    info = authUiText(
                                        "Recovery codes are managed together with authenticator protection.",
                                        "Резервные коды управляются вместе с защитой аутентификатором.",
                                        "Қалпына келтіру кодтары аутентификатор қорғанысымен бірге басқарылады.", "Калыбына келтирүү коддору аутентификатор коргоосу менен бирге башкарылат."
                                    )
                                }
                            ) { clearSensitive(); editor = AccountAuthEditor.RECOVERY_CODES }
                        }
                        actionButton(
                            autoLoading = false,
                            modifier = Modifier.fillMaxWidth(),
                            text = authUiText("Extra phone number", "Дополнительный номер телефона", "Қосымша телефон нөмірі", "Кошумча телефон номери"),
                            iconPath = stateValues.drawablePathIconPhone,
                            enabled = !loading &&
                                settings != null &&
                                authAvailability.phoneLoginAlias == AitaAuthFeatureAvailability.AVAILABLE,
                            onDisabledClick = {
                                if (authAvailability.phoneLoginAlias != AitaAuthFeatureAvailability.AVAILABLE) {
                                    info = authUiText(
                                        "An extra phone number becomes available when secure email confirmation is enabled on the server.",
                                        "Дополнительный номер телефона станет доступен после включения защищённого подтверждения по email на сервере.",
                                        "Қосымша телефон нөмірі серверде қауіпсіз email растауы қосылғанда қолжетімді болады.", "Серверде электрондук почта аркылуу коопсуз ырастоо күйгүзүлгөндө кошумча телефон номерин колдонууга болот."
                                    )
                                }
                            }
                        ) { clearSensitive(); phoneAlias = settings?.phoneLoginAlias.orEmpty(); editor = AccountAuthEditor.PHONE }
                        actionButton(autoLoading = false, modifier = Modifier.fillMaxWidth(),
                            text = authUiText("Extra email", "Дополнительный email", "Қосымша email", "Кошумча электрондук почта"),
                            iconPath = stateValues.drawablePathIconEmail,
                            enabled = !loading && settings != null && capabilities?.additionalEmailLoginEnabled == true,
                            onDisabledClick = { info = authUiText("Update the server and enable email confirmation", "Обновите сервер и включите подтверждение email", "Серверді жаңартып, email растауын қосыңыз", "Серверди жаңыртып, электрондук почта аркылуу ырастоону күйгүзүңүз") }
                        ) { clearSensitive(); editor = AccountAuthEditor.EMAIL }
                        if (settings?.authenticatorEnabled == true) {
                            AuthQuietAction(authUiText("Lost authenticator?", "Нет доступа к аутентификатору?", "Аутентификаторға қолжетімділік жоқ па?", "Аутентификаторду жоготтуңузбу?"), !loading) {
                                clearSensitive(); editor = AccountAuthEditor.TOTP_RECOVERY
                            }
                        }
                    }

                    AccountAuthEditor.EMAIL -> settings?.let { current ->
                        AdditionalLoginEmailsEditor(current,
                            onUpdated = { settings = it },
                            onClose = { editor = AccountAuthEditor.NONE; clearSensitive() })
                    }

                    AccountAuthEditor.TOTP_RECOVERY -> AuthenticatorRecoveryEditor(
                        initialIdentifier = settings?.email.orEmpty(), editableIdentifier = false,
                        onRecovered = { recoveredUserId ->
                            if (stateValues.userAccount?.id == recoveredUserId) settings = settings?.copy(authenticatorEnabled = false, authenticatorRequiredForLogin = false,
                                recoveryCodesRemaining = 0, securityRevision = (settings?.securityRevision ?: 0L) + 1L)
                            clearSensitive()
                        },
                        onClose = { clearSensitive(); editor = AccountAuthEditor.NONE }
                    )

                    AccountAuthEditor.TOTP_ENABLE -> {
                        Text(
                            text = authUiText(
                                "Confirm your password.",
                                "Подтвердите пароль.",
                                "Құпия сөзіңізді растаңыз.", "Сырсөзүңүздү ырастатыңыз."
                            ),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                        SensitiveAuthConfirmationFields(
                            enabled = !loading,
                            currentPassword = currentPassword,
                            secondFactor = secondFactor,
                            secondFactorRequired = settings?.authenticatorEnabled == true,
                            onPasswordChange = { currentPassword = it },
                            onSecondFactorChange = { secondFactor = it }
                        )
                        val setupEmailProof = if (settings?.emailRequiredForLogin == true && settings?.authenticatorEnabled != true)
                            SecurityEmailProofInput(AitaSecurityEmailRequest(AitaSecurityEmailAction.TOTP_SETUP, "", currentPassword,
                                settings?.securityRevision ?: 0L, stateValues.appLanguage), !loading) else null
                        actionButton(
                            autoLoading = false,
                            modifier = Modifier.fillMaxWidth(),
                            text = authUiText(
                                "Continue",
                                "Продолжить",
                                "Жалғастыру", "Улантуу"
                            ),
                            enabled = !loading && currentPassword.isNotBlank() &&
                                (settings?.authenticatorEnabled != true || secondFactor.isNotBlank()) &&
                                (settings?.emailRequiredForLogin != true || settings?.authenticatorEnabled == true || setupEmailProof != null),
                            loading = loading
                        ) {
                            launchSecurityAction {
                                val response = AitaAdvancedAuthenticationClient.startTotpSetup(
                                    AitaSensitiveSecurityActionRequestDataModel(currentPassword, secondFactor, setupEmailProof)
                                )
                                response.payload?.let {
                                    setup = it
                                    currentPassword = ""
                                    secondFactor = ""
                                    editor = AccountAuthEditor.TOTP_SETUP
                                } ?: run { error = authResponseText(response) }
                            }
                        }
                        QuietAction(onClick = { editor = AccountAuthEditor.NONE; clearSensitive() }) {
                            Text(stateValues.stringCancel)
                        }
                    }

                    AccountAuthEditor.TOTP_SETUP -> {
                        val data = setup
                        val setupCountdown = rememberAuthFlowCountdown(data?.let {
                            AitaAuthFlowDataModel(flowId = it.setupId, expiresAtMillis = it.expiresAtMillis, serverTimeMillis = it.serverTimeMillis)
                        })
                        LaunchedEffect(data?.setupId, setupCountdown.expired, loading) {
                            if (data != null && setupCountdown.expired && !loading) {
                                setup = null; setupCode = ""; editor = AccountAuthEditor.TOTP_ENABLE
                                error = authUiText("Setup expired. Start again.", "Срок настройки истёк. Начните заново.", "Баптау мерзімі аяқталды. Қайта бастаңыз.", "Жөндөө мөөнөтү бүттү. Кайра баштаңыз.")
                            }
                        }
                        data?.let { AuthenticatorSetupQr(it) }
                        Text(
                            text = authUiText(
                                "Scan the QR code, then enter the authenticator code.",
                                "Отсканируйте QR-код и введите код аутентификатора.",
                                "QR кодты сканерлеп, аутентификатор кодын енгізіңіз.", "QR кодду сканерлеп, андан кийин аутентификатордун кодун киргизиңиз."
                            ),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                        data?.let {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                                    .background(stateValues.AccentColor.copy(alpha = 0.05f))
                                    .border(
                                        stateValues.unfocusedBorderWidth,
                                        stateValues.PlaceholderTextColor.copy(alpha = 0.55f),
                                        RoundedCornerShape(stateValues.cornerRadius)
                                    )
                                    .padding(stateValues.marginTextFieldGroup),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SelectionContainer(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = it.secret,
                                            color = stateValues.TextColor,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    ClipboardCopyButton(
                                        textToCopy = it.secret,
                                        contentDescription = authUiText(
                                            "Copy manual setup key",
                                            "Скопировать ключ ручной настройки",
                                            "Қолмен баптау кілтін көшіру", "Кол менен жөндөө ачкычын көчүрүү"
                                        )
                                    )
                                }
                                Text(
                                    text = authUiText(
                                        "Manual key · keep private",
                                        "Ручной ключ · не сообщайте никому",
                                        "Қолмен енгізу кілті · құпия сақтаңыз", "Кол менен киргизүү ачкычы · купуя сактаңыз"
                                    ),
                                    color = stateValues.PlaceholderTextColor,
                                    fontSize = stateValues.smallTextSize,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (it.otpauthUri.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = authUiText(
                                                "Authenticator setup link",
                                                "Ссылка настройки аутентификатора",
                                                "Аутентификаторды баптау сілтемесі", "Аутентификаторду жөндөө шилтемеси"
                                            ),
                                            color = stateValues.PlaceholderTextColor,
                                            fontSize = stateValues.smallTextSize,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ClipboardCopyButton(
                                            textToCopy = it.otpauthUri,
                                            contentDescription = authUiText(
                                                "Copy authenticator setup link",
                                                "Скопировать ссылку настройки аутентификатора",
                                                "Аутентификаторды баптау сілтемесін көшіру", "Аутентификаторду жөндөө шилтемесин көчүрүү"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        if (capabilities?.authenticatorLoginPolicyEnabled == true) {
                            AuthenticatorLoginRequirementToggle(setupRequireForLogin, !loading) { setupRequireForLogin = it }
                        }
                        aitaFormTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = setupCode,
                            onValueChange = { setupCode = it },
                            titleText = authUiText("Authenticator code", "Код аутентификатора", "Аутентификатор коды", "Аутентификатор коду"),
                            placeholderText = "000000",
                            identityKey = "account-authenticator-setup-code",
                            enabled = !loading,
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                            leadingIconPath = stateValues.drawablePathIconSecurity,
                            sensitive = true,
                            onTransformValue = { it.filter(Char::isDigit).take(6) }
                        )
                        actionButton(
                            autoLoading = false,
                            modifier = Modifier.fillMaxWidth(),
                            text = authUiText("Confirm and enable", "Подтвердить и включить", "Растау және қосу", "Ырастоо жана күйгүзүү"),
                            enabled = !loading && data != null && !setupCountdown.expired && setupCode.length == 6,
                            loading = loading
                        ) {
                            launchSecurityAction {
                                val response = AitaAdvancedAuthenticationClient.confirmTotpSetup(AitaTotpSetupConfirmRequestDataModel(data!!.setupId, setupCode, setupRequireForLogin))
                                response.payload?.let {
                                    recoveryCodes = it.recoveryCodes
                                    settings = settings?.copy(
                                        authenticatorEnabled = true,
                                        authenticatorRequiredForLogin = setupRequireForLogin,
                                        emailRequiredForLogin = if (setupRequireForLogin) false else settings?.emailRequiredForLogin == true,
                                        securityRevision = (settings?.securityRevision ?: 0L) + 1L,
                                        recoveryCodesRemaining = it.recoveryCodes.size
                                    )
                                    info = authUiText("Authenticator enabled. Save the recovery codes now.", "Аутентификатор включён. Сохраните резервные коды сейчас.", "Аутентификатор қосылды. Қалпына келтіру кодтарын қазір сақтаңыз.", "Аутентификатор күйгүзүлдү. Калыбына келтирүү коддорун азыр сактап алыңыз.")
                                    editor = AccountAuthEditor.NONE
                                    setup = null
                                    setupCode = ""
                                } ?: run { error = authResponseText(response) }
                            }
                        }
                        QuietAction(onClick = { editor = AccountAuthEditor.NONE; clearSensitive() }) { Text(stateValues.stringCancel) }
                    }

                    AccountAuthEditor.TOTP_POLICY -> settings?.let { current ->
                        LoginPolicyEditor(current, requestedLoginRequirement, emailAvailable = capabilities?.emailSecondFactorEnabled == true,
                            onUpdated = { settings = it; clearSensitive(); editor = AccountAuthEditor.NONE },
                            onClose = { clearSensitive(); editor = AccountAuthEditor.NONE })
                    }

                    AccountAuthEditor.TOTP_DISABLE, AccountAuthEditor.RECOVERY_CODES -> {
                        SensitiveAuthConfirmationFields(
                            enabled = !loading,
                            currentPassword = currentPassword,
                            secondFactor = secondFactor,
                            onPasswordChange = { currentPassword = it },
                            onSecondFactorChange = { secondFactor = it }
                        )
                        actionButton(
                            autoLoading = false,
                            modifier = Modifier.fillMaxWidth(),
                            text = if (editor == AccountAuthEditor.TOTP_DISABLE) {
                                authUiText("Disable authenticator", "Отключить аутентификатор", "Аутентификаторды өшіру", "Аутентификаторду өчүрүү")
                            } else {
                                authUiText("Generate new codes", "Создать новые коды", "Жаңа кодтар жасау", "Жаңы коддорду түзүү")
                            },
                            enabled = !loading && currentPassword.isNotBlank() && secondFactor.isNotBlank(),
                            loading = loading,
                            enabledColor = if (editor == AccountAuthEditor.TOTP_DISABLE) stateValues.ErrorColor else stateValues.AccentColor
                        ) {
                            launchSecurityAction {
                                val request = AitaSensitiveSecurityActionRequestDataModel(currentPassword, secondFactor)
                                if (editor == AccountAuthEditor.TOTP_DISABLE) {
                                    val response = AitaAdvancedAuthenticationClient.disableTotp(request)
                                    response.payload?.let {
                                        clearSensitive()
                                        settings = it
                                        info = authUiText("Authenticator disabled", "Аутентификатор отключён", "Аутентификатор өшірілді", "Аутентификатор өчүрүлдү")
                                        editor = AccountAuthEditor.NONE
                                    } ?: run { error = authResponseText(response) }
                                } else {
                                    val response = AitaAdvancedAuthenticationClient.regenerateRecoveryCodes(request)
                                    response.payload?.let {
                                        recoveryCodes = it.recoveryCodes
                                        settings = settings?.copy(recoveryCodesRemaining = it.recoveryCodes.size)
                                        info = authUiText("New recovery codes generated. The previous codes no longer work.", "Новые резервные коды созданы. Старые коды больше не работают.", "Жаңа қалпына келтіру кодтары жасалды. Ескі кодтар енді жұмыс істемейді.", "Жаңы калыбына келтирүү коддору түзүлдү. Мурунку коддор эми иштебейт.")
                                        editor = AccountAuthEditor.NONE
                                        currentPassword = ""
                                        secondFactor = ""
                                    } ?: run { error = authResponseText(response) }
                                }
                            }
                        }
                        AuthQuietAction(authUiText("Lost authenticator?", "Нет доступа к аутентификатору?", "Аутентификаторға қолжетімділік жоқ па?", "Аутентификаторду жоготтуңузбу?"), !loading) {
                            clearSensitive(); editor = AccountAuthEditor.TOTP_RECOVERY
                        }
                        QuietAction(onClick = { editor = AccountAuthEditor.NONE; clearSensitive() }) { Text(stateValues.stringCancel) }
                    }

                    AccountAuthEditor.PHONE -> {
                        Text(
                            text = authUiText(
                                "An extra number for sign-in; your main number stays unchanged.",
                                "Дополнительный номер для входа; основной номер не изменится.",
                                "Кіруге арналған қосымша нөмір; негізгі нөмір өзгермейді.", "Кирүү үчүн кошумча номер; негизги номериңиз өзгөрбөйт."
                            ),
                            color = stateValues.PlaceholderTextColor,
                            fontSize = stateValues.smallTextSize
                        )
                        if (phoneFlowId.isBlank()) {
                            tabRowWidget(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading, persistSelection = false,
                                selectedIndexInitial = if (phoneAction == AitaPhoneAliasAction.REMOVE) "remove" else "set",
                                tabs = listOf(
                                    TabContent("set", authUiText("Set or replace", "Установить или заменить", "Орнату немесе ауыстыру", "Коюу же алмаштыруу")) { phoneAction = AitaPhoneAliasAction.ADD_OR_REPLACE },
                                    TabContent("remove", authUiText("Remove", "Удалить", "Жою", "Алып салуу")) { phoneAction = AitaPhoneAliasAction.REMOVE }
                                )
                            )
                            val phoneForm = remember { object : StateHost() {} }
                            val phoneField = if (phoneAction == AitaPhoneAliasAction.ADD_OR_REPLACE) {
                                countrySelectionPhoneNumberTextField(
                                    modifier = Modifier.fillMaxWidth(), valueInitial = phoneAlias,
                                    titleText = authUiText("Extra phone number", "Дополнительный номер телефона", "Қосымша телефон нөмірі", "Кошумча телефон номери"),
                                    placeholderText = authUiText("Enter an extra phone number", "Введите дополнительный номер", "Қосымша телефон нөмірін енгізіңіз", "Кошумча телефон номерин киргизиңиз"),
                                    stateHost = phoneForm, stateKey = "account_phone_alias", identityKey = "account_phone_alias",
                                    enabled = !loading, autoFocus = false, imeWithAction = ImeWithAction(ImeAction.Next),
                                    retainTextAcrossRecreation = false, persistTextDraft = false,
                                    retainSelectionAcrossRecreation = false, persistSelectionDraft = false
                                )
                            } else null
                            SensitiveAuthConfirmationFields(
                                enabled = !loading,
                                currentPassword = currentPassword,
                                secondFactor = secondFactor,
                                secondFactorRequired = settings?.authenticatorEnabled == true,
                                onPasswordChange = { currentPassword = it },
                                onSecondFactorChange = { secondFactor = it }
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            actionButton(
                                autoLoading = false,
                                modifier = Modifier.weight(1f),
                                text = authUiText("Get code", "Получить код", "Код алу", "Код алуу"),
                                enabled = !loading && currentPassword.isNotBlank() &&
                                    (settings?.authenticatorEnabled != true || secondFactor.isNotBlank()) &&
                                    (phoneAction == AitaPhoneAliasAction.REMOVE || phoneField?.value?.text?.isNotBlank() == true),
                                loading = loading
                            ) {
                                phoneField?.checkContentValidity()
                                if (phoneField != null && !phoneField.isContentValid) return@actionButton
                                val requestedPhone = phoneField?.let { it.selectedSecondaryId.orEmpty() + it.value.text }.orEmpty()
                                val request = AitaPhoneAliasRequestDataModel(phoneAction, requestedPhone, currentPassword, secondFactor, stateValues.appLanguage)
                                launchSecurityAction {
                                    val response = AitaAdvancedAuthenticationClient.requestPhoneAlias(request)
                                    response.payload?.takeIf { !response.negative && it.nextStep == AitaAuthNextStep.EMAIL_CODE && it.flowId.isNotBlank() }?.let {
                                        phoneFlow = it; phoneFlowId = it.flowId; phoneCode = ""
                                    }
                                        ?: run { error = authResponseText(response) }
                                    currentPassword = ""
                                    secondFactor = ""
                                }
                            }
                                AuthSecurityIconAction(authUiText("Done", "Готово", "Дайын", "Даяр"), stateValues.drawablePathIconCheck,
                                    stateValues.drawableResIconCheck.value, !loading) { clearSensitive(); editor = AccountAuthEditor.NONE }
                            }
                        } else {
                            AuthEmailCodeEntry(
                                value = phoneCode, flow = phoneFlow, busy = loading,
                                onValueChange = { phoneCode = it; error = "" },
                                confirmText = authUiText("Confirm change", "Подтвердить изменение", "Өзгерісті растау", "Өзгөртүүнү ырастоо"),
                                identity = "account-phone-email-confirmation-code",
                                trailingAction = { AuthSecurityIconAction(authUiText("Done", "Готово", "Дайын", "Даяр"), stateValues.drawablePathIconCheck,
                                    stateValues.drawableResIconCheck.value, !loading) { clearSensitive(); editor = AccountAuthEditor.NONE } },
                                onSubmit = {
                                    val request = AitaPhoneAliasConfirmRequestDataModel(phoneFlowId, phoneCode)
                                    launchSecurityAction {
                                        val response = AitaAdvancedAuthenticationClient.confirmPhoneAlias(request)
                                        response.payload?.takeIf { !response.negative }?.let {
                                            clearSensitive(); settings = it; phoneAlias = it.phoneLoginAlias.orEmpty()
                                            info = authUiText("Extra phone number updated", "Дополнительный номер обновлён", "Қосымша телефон нөмірі жаңартылды", "Кошумча телефон номери жаңыртылды")
                                            editor = AccountAuthEditor.NONE
                                        } ?: run { error = authResponseText(response) }
                                    }
                                },
                                onResend = {
                                    val request = AitaEmailCodeResendRequestDataModel(phoneFlowId, stateValues.appLanguage)
                                    launchSecurityAction {
                                        val response = AitaAdvancedAuthenticationClient.resendPhoneAlias(request)
                                        response.payload?.takeIf { !response.negative && it.nextStep == AitaAuthNextStep.EMAIL_CODE && it.flowId.isNotBlank() }?.let {
                                            phoneFlow = it; phoneFlowId = it.flowId; phoneCode = ""
                                        } ?: run { error = authResponseText(response) }
                                    }
                                }
                            )
                        }
                    }
                }

                if (recoveryCodes.isNotEmpty()) {
                    RecoveryCodesPanel(recoveryCodes)
                }
            }
        }
    }
}

@Composable
private fun AppConfiguration.AuthSettingsInfoRow(label: String, value: String, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize, modifier = Modifier.weight(1f))
        Text(value, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End,
            color = if (accent) stateValues.AccentColor else stateValues.TextColor, fontSize = stateValues.smallTextSize, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun AppConfiguration.SensitiveAuthConfirmationFields(
    enabled: Boolean = true,
    currentPassword: String,
    secondFactor: String,
    secondFactorRequired: Boolean = true,
    onPasswordChange: (String) -> Unit,
    onSecondFactorChange: (String) -> Unit
) {
    aitaFormTextField(
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        value = currentPassword,
        onValueChange = onPasswordChange,
        titleText = authUiText("Current password", "Текущий пароль", "Ағымдағы құпия сөз", "Учурдагы сырсөз"),
        placeholderText = stateValues.stringEnterPassword,
        identityKey = "account-security-current-password",
        keyboardType = KeyboardType.Password,
        imeAction = if (secondFactorRequired) ImeAction.Next else ImeAction.Done,
        leadingIconPath = stateValues.drawablePathIconPassword,
        password = true,
        sensitive = true
    )
    if (secondFactorRequired) {
        aitaFormTextField(
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            value = secondFactor,
            onValueChange = onSecondFactorChange,
            titleText = authUiText("Authenticator or recovery code", "Код аутентификатора или резервный код", "Аутентификатор немесе қалпына келтіру коды", "Аутентификатор же калыбына келтирүү коду"),
            placeholderText = "000000", placeholderContent = { AuthenticatorCodePlaceholder(enabled) },
            identityKey = "account-security-second-factor",
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            leadingIconPath = stateValues.drawablePathIconSecurity,
            sensitive = true,
            onTransformValue = { it.take(64) }
        )
    }
}

@Composable
private fun AppConfiguration.RecoveryCodesPanel(codes: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .foregroundTactileShadow(stateValues.cornerRadius, elevated = false)
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(
                stateValues.unfocusedBorderWidth,
                stateValues.AccentColor,
                RoundedCornerShape(stateValues.cornerRadius)
            )
            .padding(stateValues.marginTextFieldGroup),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    authUiText("Save these recovery codes now", "Сохраните резервные коды сейчас", "Қалпына келтіру кодтарын қазір сақтаңыз", "Бул калыбына келтирүү коддорун азыр сактап алыңыз"),
                    color = stateValues.TextColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    authUiText("Each code works once. They will not be shown again after you close this card.", "Каждый код действует один раз. После закрытия карточки они больше не будут показаны.", "Әр код бір рет жұмыс істейді. Карточканы жапқаннан кейін олар қайта көрсетілмейді.", "Ар бир код бир гана жолу иштейт. Бул карточканы жапкандан кийин алар кайра көрсөтүлбөйт."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
            ClipboardCopyButton(
                textToCopy = codes.joinToString("\n"),
                contentDescription = authUiText(
                    "Copy all recovery codes",
                    "Скопировать все резервные коды",
                    "Барлық қалпына келтіру кодтарын көшіру", "Бардык калыбына келтирүү коддорун көчүрүү"
                )
            )
        }
        SelectionContainer {
            Text(codes.joinToString("\n"), color = stateValues.TextColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AppConfiguration.AuthenticatorLoginRequirementToggle(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(authUiText("Require 2FA at sign-in", "Требовать 2FA при входе", "Кіру кезінде 2FA талап ету", "Кирүүдө 2FA талап кылуу"),
                color = stateValues.TextColor, fontSize = stateValues.textSize)

        }
        val label = authUiText("Require 2FA at sign-in", "Требовать 2FA при входе", "Кіру кезінде 2FA талап ету", "Кирүүдө 2FA талап кылуу")
        Switch(modifier = Modifier.semantics { contentDescription = label },
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = stateValues.AccentColor,
                checkedThumbColor = stateValues.BackgroundColor,
                uncheckedThumbColor = stateValues.PlaceholderTextColor,
                uncheckedTrackColor = stateValues.PlaceholderTextColor.copy(alpha = 0.15f)))
    }
}
