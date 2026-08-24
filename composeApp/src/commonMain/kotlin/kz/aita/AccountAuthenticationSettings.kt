package kz.aita

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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

private enum class AccountAuthEditor { NONE, TOTP_ENABLE, TOTP_SETUP, TOTP_DISABLE, RECOVERY_CODES, PHONE }

@Composable
internal fun AppConfiguration.AccountAuthenticationSettingsCard() {
    var expanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf<AitaAuthenticationSettingsDataModel?>(null) }
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

    fun clearSensitive() {
        setupCode = ""
        currentPassword = ""
        secondFactor = ""
        phoneCode = ""
        phoneFlowId = ""
        recoveryCodes = emptyList()
        error = ""
        info = ""
    }

    fun load() {
        if (loading) return
        coroutineScope.launch {
            loading = true
            val response = AitaAdvancedAuthenticationClient.settings()
            response.payload?.let {
                settings = it
                phoneAlias = it.phoneLoginAlias.orEmpty()
            } ?: run { error = authResponseText(response) }
            loading = false
        }
    }

    LaunchedEffect(expanded) { if (expanded && settings == null) load() }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(stateValues.marginTextFieldGroup),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                TextButton(onClick = { expanded = !expanded; if (!expanded) { editor = AccountAuthEditor.NONE; clearSensitive() } }) {
                    Text(if (expanded) authUiText("Close", "Закрыть", "Жабу") else authUiText("Manage", "Управлять", "Басқару"))
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
                                enabled = !loading && settings != null
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
                                    enabled = !loading
                                ) { clearSensitive(); editor = AccountAuthEditor.RECOVERY_CODES }
                            }
                            actionButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = authUiText("Manage phone login alias", "Управлять номером для входа", "Кіру телефон нөмірін басқару"),
                                enabled = !loading
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
                                loading = true
                                coroutineScope.launch {
                                    val response = AitaAdvancedAuthenticationClient.startTotpSetup(
                                        AitaSensitiveSecurityActionRequestDataModel(currentPassword, secondFactor)
                                    )
                                    response.payload?.let {
                                        setup = it
                                        currentPassword = ""
                                        secondFactor = ""
                                        editor = AccountAuthEditor.TOTP_SETUP
                                    } ?: run { error = authResponseText(response) }
                                    loading = false
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
                                SelectionContainer {
                                    Text(
                                        text = it.secret,
                                        color = stateValues.TextColor,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Text(
                                    text = authUiText("Manual setup key. Keep it private.", "Ключ ручной настройки. Не сообщайте его никому.", "Қолмен баптау кілті. Оны ешкімге бермеңіз."),
                                    color = stateValues.PlaceholderTextColor,
                                    fontSize = stateValues.smallTextSize,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
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
                                loading = true
                                coroutineScope.launch {
                                    val response = AitaAdvancedAuthenticationClient.confirmTotpSetup(AitaTotpSetupConfirmRequestDataModel(data!!.setupId, setupCode))
                                    response.payload?.let {
                                        recoveryCodes = it.recoveryCodes
                                        info = authUiText("Authenticator enabled. Save the recovery codes now.", "Аутентификатор включён. Сохраните резервные коды сейчас.", "Аутентификатор қосылды. Қалпына келтіру кодтарын қазір сақтаңыз.")
                                        editor = AccountAuthEditor.NONE
                                        load()
                                    } ?: run { error = authResponseText(response) }
                                    loading = false
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
                                loading = true
                                coroutineScope.launch {
                                    val request = AitaSensitiveSecurityActionRequestDataModel(currentPassword, secondFactor)
                                    if (editor == AccountAuthEditor.TOTP_DISABLE) {
                                        val response = AitaAdvancedAuthenticationClient.disableTotp(request)
                                        response.payload?.let {
                                            settings = it
                                            info = authUiText("Authenticator disabled", "Аутентификатор отключён", "Аутентификатор өшірілді")
                                            editor = AccountAuthEditor.NONE
                                            clearSensitive()
                                        } ?: run { error = authResponseText(response) }
                                    } else {
                                        val response = AitaAdvancedAuthenticationClient.regenerateRecoveryCodes(request)
                                        response.payload?.let {
                                            recoveryCodes = it.recoveryCodes
                                            info = authUiText("New recovery codes generated. The previous codes no longer work.", "Новые резервные коды созданы. Старые коды больше не работают.", "Жаңа қалпына келтіру кодтары жасалды. Ескі кодтар енді жұмыс істемейді.")
                                            editor = AccountAuthEditor.NONE
                                            load()
                                        } ?: run { error = authResponseText(response) }
                                    }
                                    loading = false
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
                                    loading = true
                                    coroutineScope.launch {
                                        val response = AitaAdvancedAuthenticationClient.requestPhoneAlias(
                                            AitaPhoneAliasRequestDataModel(phoneAction, phoneAlias, currentPassword, secondFactor, stateValues.appLanguage)
                                        )
                                        response.payload?.let { phoneFlowId = it.flowId; info = authUiText("Confirmation code sent to your email.", "Код подтверждения отправлен на email.", "Растау коды email-ға жіберілді.") }
                                            ?: run { error = authResponseText(response) }
                                        currentPassword = ""
                                        secondFactor = ""
                                        loading = false
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
                                    loading = true
                                    coroutineScope.launch {
                                        val response = AitaAdvancedAuthenticationClient.confirmPhoneAlias(AitaPhoneAliasConfirmRequestDataModel(phoneFlowId, phoneCode))
                                        response.payload?.let {
                                            settings = it
                                            phoneAlias = it.phoneLoginAlias.orEmpty()
                                            info = authUiText("Phone login alias updated", "Номер для входа обновлён", "Кіру телефон нөмірі жаңартылды")
                                            editor = AccountAuthEditor.NONE
                                            clearSensitive()
                                        } ?: run { error = authResponseText(response) }
                                        loading = false
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
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            SelectionContainer {
                Text(codes.joinToString("\n"), color = stateValues.TextColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
