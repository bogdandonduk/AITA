package kz.aita

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kz.aita.auth.*

private enum class AccountAuthEditor { NONE, TOTP_ENABLE, TOTP_SETUP, TOTP_DISABLE, RECOVERY_CODES, PHONE }

@Composable
internal fun AppConfiguration.AccountAuthenticationSettingsCard(
    initiallyExpanded: Boolean = false,
    collapsible: Boolean = true,
) {
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    var loading by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf<AitaAuthenticationSettingsDataModel?>(null) }
    var capabilities by remember { mutableStateOf<AitaAuthCapabilitiesDataModel?>(null) }
    var capabilitiesError by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf(AccountAuthEditor.NONE) }
    var setup by remember { mutableStateOf<AitaTotpSetupDataModel?>(null) }
    var setupCode by remember { mutableStateOf("") }
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
        if (loading) return
        beginAction()
        screenScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = authUiText(
                    "Request failed. Try again.",
                    "Запрос не выполнен. Повторите.",
                    "Сұрау орындалмады. Қайталаңыз."
                )
            } finally {
                loading = false
            }
        }
    }

    fun load() {
        if (loading) return
        loading = true
        error = ""
        capabilitiesError = ""
        screenScope.launch {
            try {
                val capabilitiesResponse = AitaAdvancedAuthenticationClient.capabilities()
                capabilitiesResponse.payload?.let {
                    capabilities = it
                } ?: run {
                    if (capabilities == null) capabilitiesError = authResponseText(capabilitiesResponse)
                }
                val settingsResponse = AitaAdvancedAuthenticationClient.settings()
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
                    "Баптаулар жүктелмеді. Қайталаңыз."
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
                contentDescription = authUiText("Sign-in and security", "Вход и безопасность", "Кіру және қауіпсіздік"),
                tintColor = stateValues.AccentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = authUiText("Sign-in & security", "Вход и безопасность", "Кіру және қауіпсіздік"),
                    color = stateValues.TextColor,
                    fontSize = stateValues.accentTextSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        settings?.authenticatorEnabled == true -> authUiText("Authenticator protection is on", "Защита аутентификатором включена", "Аутентификатор қорғанысы қосулы")
                        settings != null -> authUiText("Password, email code and recovery settings", "Пароль, код из письма и восстановление", "Құпия сөз, email коды және қалпына келтіру")
                        else -> authUiText("Manage secure ways to sign in", "Управление безопасными способами входа", "Қауіпсіз кіру тәсілдерін басқару")
                    },
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
            if (collapsible) {
                QuietAction(onClick = { expanded = !expanded; if (!expanded) { editor = AccountAuthEditor.NONE; clearSensitive() } }) {
                    Text(if (expanded) authUiText("Close", "Закрыть", "Жабу") else authUiText("Manage", "Управлять", "Басқару"))
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
                    AuthSettingsInfoRow(stateValues.stringEmail, current.email)
                    AuthSettingsInfoRow(
                        authUiText("Phone login alias", "Номер для входа", "Кіру телефон нөмірі"),
                        current.phoneLoginAlias ?: authUiText("Not configured", "Не настроен", "Бапталмаған")
                    )
                    AuthSettingsInfoRow(
                        authUiText("Authenticator", "Аутентификатор", "Аутентификатор"),
                        if (current.authenticatorEnabled) authUiText("Enabled", "Включён", "Қосулы") else authUiText("Off", "Выключен", "Өшірулі"),
                        accent = current.authenticatorEnabled
                    )
                    if (current.authenticatorEnabled) {
                        AuthSettingsInfoRow(
                            authUiText("Recovery codes remaining", "Осталось резервных кодов", "Қалған қалпына келтіру кодтары"),
                            current.recoveryCodesRemaining.toString()
                        )
                    }
                }

                AuthMethodAvailabilityPanel(
                    availability = authAvailability,
                    lookupError = capabilitiesError
                )

                if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                if (info.isNotBlank()) Text(info, color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)

                when (editor) {
                    AccountAuthEditor.NONE -> {
                        actionButton(
                            autoLoading = false,
                            modifier = Modifier.fillMaxWidth(),
                            text = if (settings?.authenticatorEnabled == true) {
                                authUiText("Disable authenticator", "Отключить аутентификатор", "Аутентификаторды өшіру")
                            } else {
                                authUiText("Enable authenticator", "Включить аутентификатор", "Аутентификаторды қосу")
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
                                        "Аутентификатор қорғанысы бұл серверде әзірге қосылмаған."
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
                                text = authUiText("Generate new recovery codes", "Создать новые резервные коды", "Жаңа қалпына келтіру кодтарын жасау"),
                                iconPath = stateValues.drawablePathIconPassword,
                                enabled = !loading &&
                                    authAvailability.authenticator == AitaAuthFeatureAvailability.AVAILABLE,
                                onDisabledClick = {
                                    info = authUiText(
                                        "Recovery codes are managed together with authenticator protection.",
                                        "Резервные коды управляются вместе с защитой аутентификатором.",
                                        "Қалпына келтіру кодтары аутентификатор қорғанысымен бірге басқарылады."
                                    )
                                }
                            ) { clearSensitive(); editor = AccountAuthEditor.RECOVERY_CODES }
                        }
                        actionButton(
                            autoLoading = false,
                            modifier = Modifier.fillMaxWidth(),
                            text = authUiText("Manage phone login alias", "Управлять номером для входа", "Кіру телефон нөмірін басқару"),
                            iconPath = stateValues.drawablePathIconPhone,
                            enabled = !loading &&
                                settings != null &&
                                authAvailability.phoneLoginAlias == AitaAuthFeatureAvailability.AVAILABLE,
                            onDisabledClick = {
                                if (authAvailability.phoneLoginAlias != AitaAuthFeatureAvailability.AVAILABLE) {
                                    info = authUiText(
                                        "Phone login aliases become available when secure email confirmation is enabled on the server.",
                                        "Номер для входа станет доступен после включения защищённого подтверждения по email на сервере.",
                                        "Кіру телефон нөмірі серверде қауіпсіз email растауы қосылғанда қолжетімді болады."
                                    )
                                }
                            }
                        ) { clearSensitive(); phoneAlias = settings?.phoneLoginAlias.orEmpty(); editor = AccountAuthEditor.PHONE }
                        QuietAction(onClick = ::load, enabled = !loading) { Text(authUiText("Refresh", "Обновить", "Жаңарту")) }
                    }

                    AccountAuthEditor.TOTP_ENABLE -> {
                        Text(
                            text = authUiText(
                                "Confirm your password.",
                                "Подтвердите пароль.",
                                "Құпия сөзіңізді растаңыз."
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
                        actionButton(
                            autoLoading = false,
                            modifier = Modifier.fillMaxWidth(),
                            text = authUiText(
                                "Continue",
                                "Продолжить",
                                "Жалғастыру"
                            ),
                            enabled = !loading && currentPassword.isNotBlank() &&
                                (settings?.authenticatorEnabled != true || secondFactor.isNotBlank()),
                            loading = loading
                        ) {
                            launchSecurityAction {
                                val response = AitaAdvancedAuthenticationClient.startTotpSetup(
                                    AitaSensitiveSecurityActionRequestDataModel(currentPassword, secondFactor)
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
                        Text(
                            text = authUiText(
                                "Add this account in your authenticator app, then enter its current six-digit code.",
                                "Добавьте аккаунт в приложение-аутентификатор, затем введите текущий шестизначный код.",
                                "Бұл аккаунтты аутентификатор қолданбасына қосып, ағымдағы алты таңбалы кодты енгізіңіз."
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
                                            "Қолмен баптау кілтін көшіру"
                                        )
                                    )
                                }
                                Text(
                                    text = authUiText(
                                        "Manual setup key. Keep it private.",
                                        "Ключ ручной настройки. Не сообщайте его никому.",
                                        "Қолмен баптау кілті. Оны ешкімге бермеңіз."
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
                                                "Аутентификаторды баптау сілтемесі"
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
                                                "Аутентификаторды баптау сілтемесін көшіру"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        aitaFormTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = setupCode,
                            onValueChange = { setupCode = it },
                            titleText = authUiText("Authenticator code", "Код аутентификатора", "Аутентификатор коды"),
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
                            text = authUiText("Confirm and enable", "Подтвердить и включить", "Растау және қосу"),
                            enabled = !loading && data != null && setupCode.length == 6,
                            loading = loading
                        ) {
                            launchSecurityAction {
                                val response = AitaAdvancedAuthenticationClient.confirmTotpSetup(AitaTotpSetupConfirmRequestDataModel(data!!.setupId, setupCode))
                                response.payload?.let {
                                    recoveryCodes = it.recoveryCodes
                                    settings = settings?.copy(
                                        authenticatorEnabled = true,
                                        recoveryCodesRemaining = it.recoveryCodes.size
                                    )
                                    info = authUiText("Authenticator enabled. Save the recovery codes now.", "Аутентификатор включён. Сохраните резервные коды сейчас.", "Аутентификатор қосылды. Қалпына келтіру кодтарын қазір сақтаңыз.")
                                    editor = AccountAuthEditor.NONE
                                    setup = null
                                    setupCode = ""
                                } ?: run { error = authResponseText(response) }
                            }
                        }
                        QuietAction(onClick = { editor = AccountAuthEditor.NONE; clearSensitive() }) { Text(stateValues.stringCancel) }
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
                                authUiText("Disable authenticator", "Отключить аутентификатор", "Аутентификаторды өшіру")
                            } else {
                                authUiText("Generate new codes", "Создать новые коды", "Жаңа кодтар жасау")
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
                                        info = authUiText("Authenticator disabled", "Аутентификатор отключён", "Аутентификатор өшірілді")
                                        editor = AccountAuthEditor.NONE
                                    } ?: run { error = authResponseText(response) }
                                } else {
                                    val response = AitaAdvancedAuthenticationClient.regenerateRecoveryCodes(request)
                                    response.payload?.let {
                                        recoveryCodes = it.recoveryCodes
                                        settings = settings?.copy(recoveryCodesRemaining = it.recoveryCodes.size)
                                        info = authUiText("New recovery codes generated. The previous codes no longer work.", "Новые резервные коды созданы. Старые коды больше не работают.", "Жаңа қалпына келтіру кодтары жасалды. Ескі кодтар енді жұмыс істемейді.")
                                        editor = AccountAuthEditor.NONE
                                        currentPassword = ""
                                        secondFactor = ""
                                    } ?: run { error = authResponseText(response) }
                                }
                            }
                        }
                        QuietAction(onClick = { editor = AccountAuthEditor.NONE; clearSensitive() }) { Text(stateValues.stringCancel) }
                    }

                    AccountAuthEditor.PHONE -> {
                        Text(
                            text = authUiText(
                                "Confirmation by email, not SMS.",
                                "Подтверждение по email, не в SMS.",
                                "Растау SMS емес, email арқылы."
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
                                    TabContent("set", authUiText("Set or replace", "Установить или заменить", "Орнату немесе ауыстыру")) { phoneAction = AitaPhoneAliasAction.ADD_OR_REPLACE },
                                    TabContent("remove", authUiText("Remove", "Удалить", "Жою")) { phoneAction = AitaPhoneAliasAction.REMOVE }
                                )
                            )
                            if (phoneAction == AitaPhoneAliasAction.ADD_OR_REPLACE) {
                                aitaFormTextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = phoneAlias,
                                    onValueChange = { phoneAlias = it },
                                    titleText = authUiText("Phone login alias", "Номер для входа", "Кіру телефон нөмірі"),
                                    placeholderText = authUiText("Enter a phone number", "Введите номер телефона", "Телефон нөмірін енгізіңіз"),
                                    identityKey = "account-phone-login-alias",
                                    enabled = !loading,
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next,
                                    leadingIconPath = stateValues.drawablePathIconPhone
                                )
                            }
                            SensitiveAuthConfirmationFields(
                                enabled = !loading,
                                currentPassword = currentPassword,
                                secondFactor = secondFactor,
                                secondFactorRequired = settings?.authenticatorEnabled == true,
                                onPasswordChange = { currentPassword = it },
                                onSecondFactorChange = { secondFactor = it }
                            )
                            actionButton(
                                autoLoading = false,
                                modifier = Modifier.fillMaxWidth(),
                                text = authUiText("Get code", "Получить код", "Код алу"),
                                enabled = !loading && currentPassword.isNotBlank() &&
                                    (settings?.authenticatorEnabled != true || secondFactor.isNotBlank()) &&
                                    (phoneAction == AitaPhoneAliasAction.REMOVE || normalizeAitaPhoneAlias(phoneAlias) != null),
                                loading = loading
                            ) {
                                val request = AitaPhoneAliasRequestDataModel(phoneAction, phoneAlias, currentPassword, secondFactor, stateValues.appLanguage)
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
                        } else {
                            AuthEmailCodeEntry(
                                value = phoneCode, flow = phoneFlow, busy = loading,
                                onValueChange = { phoneCode = it; error = "" },
                                confirmText = authUiText("Confirm change", "Подтвердить изменение", "Өзгерісті растау"),
                                identity = "account-phone-email-confirmation-code",
                                onSubmit = {
                                    val request = AitaPhoneAliasConfirmRequestDataModel(phoneFlowId, phoneCode)
                                    launchSecurityAction {
                                        val response = AitaAdvancedAuthenticationClient.confirmPhoneAlias(request)
                                        response.payload?.takeIf { !response.negative }?.let {
                                            clearSensitive(); settings = it; phoneAlias = it.phoneLoginAlias.orEmpty()
                                            info = authUiText("Phone updated", "Номер обновлён", "Нөмір жаңартылды")
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
                        QuietAction(onClick = { editor = AccountAuthEditor.NONE; clearSensitive() }) { Text(stateValues.stringCancel) }
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
private fun AppConfiguration.AuthMethodAvailabilityPanel(
    availability: AitaAuthUiAvailability,
    lookupError: String,
) {
    fun label(state: AitaAuthFeatureAvailability): String = when (state) {
        AitaAuthFeatureAvailability.AVAILABLE -> authUiText("Available", "Доступно", "Қолжетімді")
        AitaAuthFeatureAvailability.UNAVAILABLE -> authUiText("Unavailable", "Недоступно", "Қолжетімсіз")
        AitaAuthFeatureAvailability.CHECKING -> authUiText("Checking…", "Проверяем…", "Тексерілуде…")
        AitaAuthFeatureAvailability.UNKNOWN -> authUiText("Not checked", "Не проверено", "Тексерілмеген")
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AuthSettingsInfoRow(authUiText("Email code", "Код из письма", "Email коды"), label(availability.emailCodeLogin), availability.emailCodeLogin == AitaAuthFeatureAvailability.AVAILABLE)
        AuthSettingsInfoRow(authUiText("Password recovery", "Восстановление пароля", "Құпия сөзді қалпына келтіру"), label(availability.passwordRecovery), availability.passwordRecovery == AitaAuthFeatureAvailability.AVAILABLE)
        if (lookupError.isNotBlank()) Text(lookupError, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
    }
}

@Composable
private fun AppConfiguration.AuthSettingsInfoRow(label: String, value: String, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize, modifier = Modifier.weight(1f))
        Text(value, color = if (accent) stateValues.AccentColor else stateValues.TextColor, fontSize = stateValues.smallTextSize, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppConfiguration.SensitiveAuthConfirmationFields(
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
        titleText = authUiText("Current password", "Текущий пароль", "Ағымдағы құпия сөз"),
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
            modifier = Modifier.fillMaxWidth(),
            value = secondFactor,
            onValueChange = onSecondFactorChange,
            titleText = authUiText("Authenticator or recovery code", "Код аутентификатора или резервный код", "Аутентификатор немесе қалпына келтіру коды"),
            placeholderText = authUiText("Enter a current code", "Введите действующий код", "Ағымдағы кодты енгізіңіз"),
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
                    authUiText("Save these recovery codes now", "Сохраните резервные коды сейчас", "Қалпына келтіру кодтарын қазір сақтаңыз"),
                    color = stateValues.TextColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    authUiText("Each code works once. They will not be shown again after you close this card.", "Каждый код действует один раз. После закрытия карточки они больше не будут показаны.", "Әр код бір рет жұмыс істейді. Карточканы жапқаннан кейін олар қайта көрсетілмейді."),
                    color = stateValues.PlaceholderTextColor,
                    fontSize = stateValues.smallTextSize
                )
            }
            ClipboardCopyButton(
                textToCopy = codes.joinToString("\n"),
                contentDescription = authUiText(
                    "Copy all recovery codes",
                    "Скопировать все резервные коды",
                    "Барлық қалпына келтіру кодтарын көшіру"
                )
            )
        }
        SelectionContainer {
            Text(codes.joinToString("\n"), color = stateValues.TextColor, fontWeight = FontWeight.SemiBold)
        }
    }
}
