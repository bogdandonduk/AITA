package kz.aita

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
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
import org.jetbrains.compose.resources.DrawableResource

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
    var phoneCode by remember { mutableStateOf("") }
    var recoveryCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val screenScope = rememberCoroutineScope()

    fun clearSensitive() {
        setup = null
        setupCode = ""
        currentPassword = ""
        secondFactor = ""
        phoneCode = ""
        phoneFlowId = ""
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
                    "AITA could not complete this security request. Check your connection and try again.",
                    "AITA не удалось выполнить запрос безопасности. Проверьте соединение и повторите попытку.",
                    "AITA қауіпсіздік сұрауын орындай алмады. Байланысты тексеріп, қайталап көріңіз."
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
                    "AITA could not load your security settings. Check your connection and try again.",
                    "AITA не удалось загрузить настройки безопасности. Проверьте соединение и повторите попытку.",
                    "AITA қауіпсіздік баптауларын жүктей алмады. Байланысты тексеріп, қайталап көріңіз."
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
                        text = authUiText("Sign-in and two-factor authentication", "Вход и двухфакторная аутентификация", "Кіру және екі факторлы аутентификация"),
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
                    TextButton(onClick = { expanded = !expanded; if (!expanded) { editor = AccountAuthEditor.NONE; clearSensitive() } }) {
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
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    settings?.let { current ->
                        AuthSettingsInfoRow(authUiText("Verified email", "Подтверждённый email", "Расталған email"), current.email)
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
                        settings = settings,
                        lookupError = capabilitiesError
                    )

                    if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
                    if (info.isNotBlank()) Text(info, color = stateValues.AccentColor, fontSize = stateValues.smallTextSize)

                    when (editor) {
                        AccountAuthEditor.NONE -> {
                            actionButton(
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
                            TextButton(onClick = ::load, enabled = !loading) { Text(authUiText("Refresh security settings", "Обновить настройки безопасности", "Қауіпсіздік баптауларын жаңарту")) }
                        }

                        AccountAuthEditor.TOTP_ENABLE -> {
                            Text(
                                text = authUiText(
                                    "Confirm your current password before adding an authenticator.",
                                    "Подтвердите текущий пароль перед подключением аутентификатора.",
                                    "Аутентификаторды қоспас бұрын ағымдағы құпия сөзді растаңыз."
                                ),
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize
                            )
                            SensitiveAuthConfirmationFields(
                                currentPassword = currentPassword,
                                secondFactor = secondFactor,
                                secondFactorRequired = settings?.authenticatorEnabled == true,
                                onPasswordChange = { currentPassword = it },
                                onSecondFactorChange = { secondFactor = it }
                            )
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = authUiText(
                                    "Continue to authenticator setup",
                                    "Перейти к настройке аутентификатора",
                                    "Аутентификаторды баптауға өту"
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
                            TextButton(onClick = { editor = AccountAuthEditor.NONE; clearSensitive() }) {
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
                            TextButton(onClick = { editor = AccountAuthEditor.NONE; clearSensitive() }) { Text(stateValues.stringCancel) }
                        }

                        AccountAuthEditor.TOTP_DISABLE, AccountAuthEditor.RECOVERY_CODES -> {
                            SensitiveAuthConfirmationFields(
                                currentPassword = currentPassword,
                                secondFactor = secondFactor,
                                onPasswordChange = { currentPassword = it },
                                onSecondFactorChange = { secondFactor = it }
                            )
                            actionButton(
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
                            TextButton(onClick = { editor = AccountAuthEditor.NONE; clearSensitive() }) { Text(stateValues.stringCancel) }
                        }

                        AccountAuthEditor.PHONE -> {
                            Text(
                                text = authUiText(
                                    "The phone number is an alternate login name. Confirmation goes to your verified email; AITA does not send an SMS.",
                                    "Номер — это дополнительный логин. Подтверждение придёт на email; AITA не отправляет SMS.",
                                    "Телефон нөмірі — қосымша логин. Растау email арқылы келеді; AITA SMS жібермейді."
                                ),
                                color = stateValues.PlaceholderTextColor,
                                fontSize = stateValues.smallTextSize
                            )
                            if (phoneFlowId.isBlank()) {
                                val tabs = tabRowWidget(
                                    modifier = Modifier.fillMaxWidth(),
                                    selectedIndexInitial = if (phoneAction == AitaPhoneAliasAction.REMOVE) "remove" else "set",
                                    tabs = listOf(
                                        TabContent("set", authUiText("Set or replace", "Установить или заменить", "Орнату немесе ауыстыру")),
                                        TabContent("remove", authUiText("Remove", "Удалить", "Жою"))
                                    )
                                )
                                LaunchedEffect(tabs.id) {
                                    phoneAction = if (tabs.id == "remove") AitaPhoneAliasAction.REMOVE else AitaPhoneAliasAction.ADD_OR_REPLACE
                                }
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
                                    currentPassword = currentPassword,
                                    secondFactor = secondFactor,
                                    secondFactorRequired = settings?.authenticatorEnabled == true,
                                    onPasswordChange = { currentPassword = it },
                                    onSecondFactorChange = { secondFactor = it }
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = authUiText("Send email confirmation", "Отправить подтверждение на email", "Email растауын жіберу"),
                                    enabled = !loading && currentPassword.isNotBlank() &&
                                        (settings?.authenticatorEnabled != true || secondFactor.isNotBlank()) &&
                                        (phoneAction == AitaPhoneAliasAction.REMOVE || normalizeAitaPhoneAlias(phoneAlias) != null),
                                    loading = loading
                                ) {
                                    launchSecurityAction {
                                        val response = AitaAdvancedAuthenticationClient.requestPhoneAlias(
                                            AitaPhoneAliasRequestDataModel(phoneAction, phoneAlias, currentPassword, secondFactor, stateValues.appLanguage)
                                        )
                                        response.payload?.let { phoneFlowId = it.flowId; info = authUiText("Confirmation code sent to your email.", "Код подтверждения отправлен на email.", "Растау коды email-ға жіберілді.") }
                                            ?: run { error = authResponseText(response) }
                                        currentPassword = ""
                                        secondFactor = ""
                                    }
                                }
                            } else {
                                aitaFormTextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = phoneCode,
                                    onValueChange = { phoneCode = it },
                                    titleText = authUiText("Email confirmation code", "Код подтверждения из письма", "Email растау коды"),
                                    placeholderText = "000000",
                                    identityKey = "account-phone-email-confirmation-code",
                                    enabled = !loading,
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done,
                                    leadingIconPath = stateValues.drawablePathIconEmail,
                                    sensitive = true,
                                    onTransformValue = { it.filter(Char::isDigit).take(6) }
                                )
                                actionButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = authUiText("Confirm change", "Подтвердить изменение", "Өзгерісті растау"),
                                    enabled = !loading && phoneCode.length == 6,
                                    loading = loading
                                ) {
                                    launchSecurityAction {
                                        val response = AitaAdvancedAuthenticationClient.confirmPhoneAlias(AitaPhoneAliasConfirmRequestDataModel(phoneFlowId, phoneCode))
                                        response.payload?.let {
                                            clearSensitive()
                                            settings = it
                                            phoneAlias = it.phoneLoginAlias.orEmpty()
                                            info = authUiText("Phone login alias updated", "Номер для входа обновлён", "Кіру телефон нөмірі жаңартылды")
                                            editor = AccountAuthEditor.NONE
                                        } ?: run { error = authResponseText(response) }
                                    }
                                }
                            }
                            TextButton(onClick = { editor = AccountAuthEditor.NONE; clearSensitive() }) { Text(stateValues.stringCancel) }
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
    settings: AitaAuthenticationSettingsDataModel?,
    lookupError: String,
) {
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = authUiText("Your ways to sign in", "Ваши способы входа", "Кіру тәсілдеріңіз"),
                color = stateValues.TextColor,
                fontSize = stateValues.accentTextSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = authUiText(
                    "Code sign-in and password recovery are available from the login screen. Manage account-only methods here.",
                    "Вход по коду и восстановление пароля доступны на экране входа. Здесь настраиваются способы, связанные с аккаунтом.",
                    "Кодпен кіру және құпия сөзді қалпына келтіру кіру экранында қолжетімді. Аккаунтқа қатысты тәсілдерді осы жерден басқарыңыз."
                ),
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }

        AuthMethodAvailabilityRow(
            title = authUiText("Password", "Пароль", "Құпия сөз"),
            detail = authUiText(
                "Use your email or phone login name with your password.",
                "Используйте email или номер для входа вместе с паролем.",
                "Email немесе кіру телефон нөмірін құпия сөзбен бірге пайдаланыңыз."
            ),
            availability = AitaAuthFeatureAvailability.AVAILABLE,
            active = true,
            activeLabel = authUiText("Ready", "Готов", "Дайын"),
            iconPath = stateValues.drawablePathIconPassword,
            iconRes = stateValues.drawableResIconPassword.value
        )

        AuthMethodAvailabilityRow(
            title = authUiText("Email sign-in code", "Код для входа из письма", "Email арқылы кіру коды"),
            detail = authUiText(
                "A one-time code is sent to the verified email on the account.",
                "Одноразовый код отправляется на подтверждённый email аккаунта.",
                "Бір реттік код аккаунттың расталған email мекенжайына жіберіледі."
            ),
            availability = availability.emailCodeLogin,
            iconPath = stateValues.drawablePathIconEmail,
            iconRes = stateValues.drawableResIconEmail.value
        )

        AuthMethodAvailabilityRow(
            title = authUiText("Password recovery", "Восстановление пароля", "Құпия сөзді қалпына келтіру"),
            detail = authUiText(
                "Restore access through a confirmation code sent to your verified email.",
                "Восстановите доступ с помощью кода, отправленного на подтверждённый email.",
                "Расталған email-ға жіберілген код арқылы қолжетімділікті қалпына келтіріңіз."
            ),
            availability = availability.passwordRecovery,
            iconPath = stateValues.drawablePathIconPassword,
            iconRes = stateValues.drawableResIconPassword.value
        )

        AuthMethodAvailabilityRow(
            title = authUiText("Authenticator protection", "Защита аутентификатором", "Аутентификатор қорғанысы"),
            detail = if (settings?.authenticatorEnabled == true) {
                authUiText(
                    "A current authenticator or recovery code is required after primary sign-in.",
                    "После основного входа требуется действующий код аутентификатора или резервный код.",
                    "Негізгі кіруден кейін ағымдағы аутентификатор немесе қалпына келтіру коды қажет."
                )
            } else {
                authUiText(
                    "Optional two-factor protection with any standard authenticator app.",
                    "Дополнительная двухфакторная защита через любое стандартное приложение-аутентификатор.",
                    "Кез келген стандартты аутентификатор қолданбасы арқылы қосымша екі факторлы қорғаныс."
                )
            },
            availability = availability.authenticator,
            active = settings?.authenticatorEnabled == true,
            activeLabel = authUiText("On", "Включён", "Қосулы"),
            iconPath = stateValues.drawablePathIconSecurity,
            iconRes = stateValues.drawableResIconSecurity.value
        )

        AuthMethodAvailabilityRow(
            title = authUiText("Phone login alias", "Номер для входа", "Кіру телефон нөмірі"),
            detail = settings?.phoneLoginAlias?.takeIf { it.isNotBlank() }?.let { alias ->
                authUiText(
                    "Use $alias as an alternate login name. Confirmation changes go to email.",
                    "Используйте $alias как дополнительный логин. Изменения подтверждаются по email.",
                    "$alias нөмірін қосымша логин ретінде пайдаланыңыз. Өзгерістер email арқылы расталады."
                )
            } ?: authUiText(
                "Add a phone number as an alternate login name; confirmation is sent by email, not SMS.",
                "Добавьте номер как дополнительный логин; подтверждение приходит по email, а не по SMS.",
                "Телефон нөмірін қосымша логин ретінде қосыңыз; растау SMS емес, email арқылы келеді."
            ),
            availability = availability.phoneLoginAlias,
            active = settings?.phoneLoginAlias?.isNotBlank() == true,
            activeLabel = authUiText("Set", "Настроен", "Бапталған"),
            iconPath = stateValues.drawablePathIconPhone,
            iconRes = stateValues.drawableResIconPhone.value
        )

        if (lookupError.isNotBlank()) {
            Text(
                text = lookupError,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
    }
}

@Composable
private fun AppConfiguration.AuthMethodAvailabilityRow(
    title: String,
    detail: String,
    availability: AitaAuthFeatureAvailability,
    iconPath: String,
    iconRes: DrawableResource,
    active: Boolean = false,
    activeLabel: String = authUiText("Enabled", "Включено", "Қосулы"),
) {
    val statusText = when {
        active && availability == AitaAuthFeatureAvailability.AVAILABLE -> activeLabel
        availability == AitaAuthFeatureAvailability.AVAILABLE -> authUiText("Available", "Доступно", "Қолжетімді")
        availability == AitaAuthFeatureAvailability.CHECKING -> authUiText("Checking", "Проверяем", "Тексерілуде")
        availability == AitaAuthFeatureAvailability.UNKNOWN -> authUiText("Check", "Проверить", "Тексеру")
        else -> authUiText("Off", "Недоступно", "Өшірулі")
    }
    val statusColor = when {
        availability == AitaAuthFeatureAvailability.AVAILABLE -> stateValues.AccentColor
        availability == AitaAuthFeatureAvailability.UNAVAILABLE -> stateValues.ErrorColor
        else -> stateValues.PlaceholderTextColor
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CpImage(
            modifier = Modifier.size(stateValues.iconSize),
            url = iconPath,
            fallbackRes = iconRes,
            contentDescription = title,
            tintColor = statusColor
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = stateValues.TextColor,
                fontSize = stateValues.textSize,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                color = stateValues.PlaceholderTextColor,
                fontSize = stateValues.smallTextSize
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(stateValues.cornerRadius))
                .background(statusColor.copy(alpha = 0.12f))
                .border(
                    stateValues.unfocusedBorderWidth,
                    statusColor.copy(alpha = 0.55f),
                    RoundedCornerShape(stateValues.cornerRadius)
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = statusText,
                color = statusColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
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
    currentPassword: String,
    secondFactor: String,
    secondFactorRequired: Boolean = true,
    onPasswordChange: (String) -> Unit,
    onSecondFactorChange: (String) -> Unit
) {
    aitaFormTextField(
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
