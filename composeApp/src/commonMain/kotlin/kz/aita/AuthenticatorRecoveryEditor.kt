package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kz.aita.auth.*

/** Shared by sign-in (including a lost-factor challenge) and signed-in security settings.
 * No saved-state/draft storage for passwords, email codes, or recovery flow identifiers.
 */
@Composable
internal fun AppConfiguration.AuthenticatorRecoveryEditor(
    initialIdentifier: String,
    editableIdentifier: Boolean = true,
    onRecovered: (String) -> Unit = {},
    onClose: () -> Unit,
) {
    val owner = remember { captureAitaAuthenticationUiOwner() }
    val scope = rememberCoroutineScope()
    var identifier by remember { mutableStateOf(initialIdentifier) }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf<AitaAuthFlowDataModel?>(null) }
    var complete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var action by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableLongStateOf(0L) }
    val recovered by rememberUpdatedState(onRecovered)
    val close by rememberUpdatedState(onClose)

    fun launchAction(block: suspend () -> Unit) {
        if (busy || !owner.isCurrent()) return
        busy = true; error = ""
        val attempt = ++generation
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (withTimeoutOrNull(45_000L) { block(); true } != true && owner.isCurrent() && attempt == generation) {
                    error = authUiText("Request timed out. Check the result before retrying.", "Время ожидания истекло. Проверьте результат перед повтором.", "Күту уақыты аяқталды. Қайталаудан бұрын нәтижені тексеріңіз.", "Суроо-талаптын күтүү убактысы бүттү. Кайра аракет кылуудан мурун натыйжаны текшериңиз.")
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (owner.isCurrent() && attempt == generation) error = authUiText("Request failed. Try again.", "Запрос не выполнен. Повторите.", "Сұрау орындалмады. Қайталаңыз.", "Суроо-талап аткарылган жок. Кайра аракет кылыңыз.")
            } finally {
                if (attempt == generation) { busy = false; action = null }
            }
        }
        action = next; next.start()
    }
    suspend fun acceptFlow(response: ResponseDataModel<AitaAuthFlowDataModel>) {
        currentCoroutineContext().ensureActive()
        if (!owner.isCurrent()) return
        val result = response.payload
        if (!response.negative && result?.nextStep == AitaAuthNextStep.EMAIL_CODE && result.flowId.isNotBlank()) {
            flow = result; password = ""; code = ""
        } else error = authResponseText(response)
    }
    fun requestCode() {
        val who = normalizeAitaLoginIdentifier(identifier)?.value
        if (who == null || password.isBlank()) {
            error = authUiText("Enter your login and current password", "Введите логин и текущий пароль", "Логин мен қазіргі құпия сөзді енгізіңіз", "Логиниңизди жана учурдагы сырсөзүңүздү киргизиңиз")
            return
        }
        val request = AitaAuthenticatorRecoveryRequestDataModel(who, password, stateValues.appLanguage)
        launchAction { acceptFlow(AitaAdvancedAuthenticationClient.requestAuthenticatorRecovery(request)) }
    }
    fun cancel() {
        generation++; action?.cancel(); action = null
        password = ""; code = ""; flow = null
        close()
    }
    DisposableEffect(Unit) { onDispose { action?.cancel() } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(authUiText("Recover authenticator access", "Восстановить доступ к аутентификатору", "Аутентификаторға қолжетімділікті қалпына келтіру", "Аутентификаторго кирүүнү калыбына келтирүү"),
            color = stateValues.TextColor, fontSize = stateValues.accentTextSize, fontWeight = FontWeight.Bold)
        if (complete) {
            Text(authUiText("Authenticator removed. All sessions and old recovery codes were revoked. Sign in again, then connect a new authenticator.",
                "Аутентификатор удалён. Все сеансы входа и старые резервные коды отозваны. Войдите заново и подключите новый аутентификатор.",
                "Аутентификатор жойылды. Барлық кіру сеанстары мен ескі резервтік кодтар қайтарып алынды. Қайта кіріп, жаңа аутентификаторды қосыңыз.", "Аутентификатор алынып салынды. Бардык сессиялар жана эски калыбына келтирүү коддору жокко чыгарылды. Кайра кирип, жаңы аутентификатор туташтырыңыз."),
                color = stateValues.AccentColor, fontSize = stateValues.textSize)
            AuthQuietAction(authUiText("Done", "Готово", "Дайын", "Даяр"), onClick = ::cancel)
        } else {
            Text(authUiText("Confirm your current password and a code sent to your main email. This removes the lost authenticator and ends all sign-in sessions.",
                "Подтвердите текущий пароль и код из письма на основной email. Это удалит потерянный аутентификатор и завершит все сеансы входа.",
                "Қазіргі құпия сөзді және негізгі email мекенжайына жіберілген кодты растаңыз. Бұл жоғалған аутентификаторды жойып, барлық кіру сеанстарын аяқтайды.", "Учурдагы сырсөзүңүздү жана негизги электрондук почтаңызга жөнөтүлгөн кодду ырастатыңыз. Бул жоголгон аутентификаторду алып салып, бардык кирүү сессияларын аяктатат."),
                color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            val current = flow
            if (current == null) {
                aitaFormTextField(modifier = Modifier.fillMaxWidth(), value = identifier, onValueChange = { identifier = it; error = "" },
                    titleText = authUiText("Email or full phone number", "Email или полный номер телефона", "Email немесе толық телефон нөмірі", "Электрондук почта же толук телефон номери"),
                    placeholderText = authUiText("Your account login", "Логин аккаунта", "Аккаунт логині", "Аккаунтуңуздун логини"),
                    identityKey = "totp-recovery-identifier", sensitive = true, enabled = !busy && editableIdentifier,
                    keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, leadingIconPath = stateValues.drawablePathIconEmail)
                aitaFormTextField(modifier = Modifier.fillMaxWidth(), value = password, onValueChange = { password = it; error = "" },
                    titleText = authUiText("Current password", "Текущий пароль", "Қазіргі құпия сөз", "Учурдагы сырсөз"), placeholderText = stateValues.stringEnterPassword,
                    identityKey = "totp-recovery-password", enabled = !busy, sensitive = true, password = true,
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, onImeAction = ::requestCode,
                    leadingIconPath = stateValues.drawablePathIconPassword)
                actionButton(modifier = Modifier.fillMaxWidth(), text = authUiText("Send recovery code", "Получить код восстановления", "Қалпына келтіру кодын алу", "Калыбына келтирүү кодун жөнөтүү"),
                    enabled = !busy, loading = busy, autoLoading = false, onClick = ::requestCode)
            } else {
                AuthEmailCodeEntry(code, current, busy, { code = it; error = "" }, onSubmit = {
                    val request = AitaEmailCodeVerifyRequestDataModel(current.flowId, code)
                    launchAction {
                        val response = AitaAdvancedAuthenticationClient.confirmAuthenticatorRecovery(request)
                        currentCoroutineContext().ensureActive()
                        if (owner.isCurrent()) {
                            val result = response.payload
                            if (!response.negative && result?.nextStep == AitaAuthNextStep.COMPLETE && result.recoveredUserId.isNotBlank()) {
                                complete = true; code = ""; flow = null; password = ""
                                acknowledgeAitaAuthenticatorRecovery(owner, result.recoveredUserId)
                                recovered(result.recoveredUserId)
                            } else error = authResponseText(response)
                        }
                    }
                }, onResend = {
                    val request = AitaEmailCodeResendRequestDataModel(current.flowId, stateValues.appLanguage)
                    launchAction { acceptFlow(AitaAdvancedAuthenticationClient.resendAuthenticatorRecovery(request)) }
                }, confirmText = authUiText("Reset authenticator", "Сбросить аутентификатор", "Аутентификаторды қалпына келтіру", "Аутентификаторду баштапкы абалга келтирүү"), identity = "totp-recovery-email-code")
            }
            if (error.isNotBlank()) Text(error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            AuthQuietAction(stateValues.stringCancel, !busy, ::cancel)
        }
    }
}
