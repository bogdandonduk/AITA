package kz.aita

import aita.composeapp.generated.resources.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kz.aita.auth.AitaAuthFlowDataModel
import kz.aita.auth.AitaAuthNextStep
import kz.aita.auth.AitaContactChannel
import kz.aita.auth.AitaContactCodeRequest
import kz.aita.auth.AitaContactPurpose
import kz.aita.auth.AitaContactTarget
import kz.aita.auth.AitaContactVerificationClient
import kz.aita.auth.AitaContactVerificationResult
import kz.aita.auth.AitaEmailCodeResendRequestDataModel
import kz.aita.auth.AitaEmailCodeVerifyRequestDataModel
import kz.aita.auth.AitaVerifiedContactProof
import kz.aita.auth.aitaContactDraftTarget
import kz.aita.auth.aitaContactEmailsRequiringProof
import kz.aita.auth.canonicalAitaContactTarget
import kz.aita.auth.AitaAuthenticationSettingsDataModel
import kz.aita.auth.aitaAuthCodeDigits
import kz.aita.auth.aitaReusableAccountContact
import kz.aita.auth.verifiedAccountContacts
import kz.aita.auth.AitaAdvancedAuthenticationClient
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** No code, receipt, or pending registration password is saveable or written to a form draft. */
internal class ContactEmailConfirmationState(
    val target: AitaContactTarget,
    val draftId: String,
    requiredAddresses: List<String>?,
    val owner: String?,
    val generation: Long
) {
    var requiredAddresses by mutableStateOf(requiredAddresses)
    val created = TimeSource.Monotonic.markNow()
    private data class Accepted(val result: AitaContactVerificationResult, val received: TimeMark)
    private var accepted by mutableStateOf<List<Accepted>>(emptyList())
    var clockTick by mutableLongStateOf(0L)
    var flow by mutableStateOf<AitaAuthFlowDataModel?>(null)
    var flowAddress by mutableStateOf("")
    var code by mutableStateOf("")
    var busy by mutableStateOf(false)
    var error by mutableStateOf("")
    var disposed = false
    val registration: Boolean get() = target.purpose == AitaContactPurpose.REGISTRATION
    fun ownsCurrentSession(): Boolean = !disposed && generation == currentAuthenticatedSessionGeneration() &&
        userAccountState.payloadValue?.id == owner &&
        if (registration) owner == null && getStoredUserAuthTokens?.invoke() == null
        else owner != null && authenticatedSessionGenerationIsCurrent(generation)
    private fun fresh(): List<Accepted> {
        // Observe the ticker for rendering; re-read the monotonic clock at the actual Save click too.
        @Suppress("UNUSED_VARIABLE") val tick = clockTick
        return accepted.filter { it.received.elapsedNow().inWholeMilliseconds <
            it.result.expiresAtMillis - it.result.serverTimeMillis }
    }
    val proofs: List<AitaVerifiedContactProof> get() = fresh().filter { it.result.address in requiredAddresses.orEmpty() }.map { it.result.proof }
    val pendingAddress: String? get() = requiredAddresses?.firstOrNull { address -> fresh().none { it.result.address == address } }
    val hasExpiredProof: Boolean get() = accepted.size > fresh().size
    val ready: Boolean get() = ownsCurrentSession() && canonicalAitaContactTarget(target) != null && requiredAddresses != null && !busy && pendingAddress == null
    fun accept(result: AitaContactVerificationResult, requestedFlow: AitaAuthFlowDataModel): Boolean {
        if (!ownsCurrentSession() || result.channel != AitaContactChannel.EMAIL ||
            canonicalAitaContactTarget(result.target) != canonicalAitaContactTarget(target) ||
            canonicalAitaContactTarget(result.target) == null || result.address != flowAddress ||
            result.address !in requiredAddresses.orEmpty() || result.proof.flowId != requestedFlow.flowId ||
            result.proof.receipt.length !in 40..128 || result.serverTimeMillis <= 0L ||
            result.expiresAtMillis <= result.serverTimeMillis) return false
        accepted = accepted.filterNot { it.result.address == result.address } + Accepted(result, TimeSource.Monotonic.markNow())
        flow = null; flowAddress = ""; code = ""; error = ""
        return true
    }
    fun clear() { accepted = emptyList(); flow = null; flowAddress = ""; code = ""; error = "" }
}

internal fun AppConfiguration.contactText(key: String, arguments: Map<String, String> = emptyMap()): String =
    EventMessages.render(EventMessageReference("contact.$key", arguments), stateValues.appLanguage).orEmpty()

/** Ephemeral security material never enters synced form drafts or persistent device storage. */
internal object ContactConfirmationMemory {
    private val states = linkedMapOf<String, ContactEmailConfirmationState>()
    @OptIn(ExperimentalUuidApi::class)
    fun obtain(owner: String?, generation: Long, purpose: AitaContactPurpose, entityId: String,
        parentId: String, required: List<String>?): ContactEmailConfirmationState {
        states.entries.removeAll { (_, value) ->
            val stale = value.owner != owner || value.generation != generation || value.created.elapsedNow().inWholeMinutes >= 20
            if (stale) { value.disposed = true; value.clear() }
            stale
        }
        val key = "$purpose|$entityId|$parentId|${required?.joinToString("|")}"
        return states.getOrPut(key) {
            while (states.size >= 12) states.remove(states.keys.first())?.let { it.disposed = true; it.clear() }
            val draftId = Uuid.random().toString()
            ContactEmailConfirmationState(aitaContactDraftTarget(purpose, entityId, draftId, parentId), draftId, required, owner, generation)
        }
    }
    fun forget(state: ContactEmailConfirmationState) {
        states.entries.removeAll { it.value === state }
        state.disposed = true; state.clear()
    }
}

@Composable
internal fun AppConfiguration.rememberContactEmailConfirmation(
    purpose: AitaContactPurpose,
    entityId: String,
    emails: List<String>,
    previousEmails: List<String> = emptyList(),
    parentId: String = ""
): ContactEmailConfirmationState {
    val owner = stateValues.userAccount?.id
    val generation = currentAuthenticatedSessionGeneration()
    val changed = aitaContactEmailsRequiringProof(emails, previousEmails)
    val state = remember(owner, generation, purpose, entityId, parentId, changed) {
        ContactConfirmationMemory.obtain(owner, generation, purpose, entityId, parentId, changed)
    }
    // Do not clear a live challenge when Android recreates the activity or the user visits email.
    // Code text is unnecessary to retain; flow/proof lifetime remains enforced by the server.
    DisposableEffect(state) { onDispose { state.code = "" } }
    var verifiedOwnerEmails by remember(owner, generation) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(owner, generation, stateValues.userAccount?.email) {
        if (purpose != AitaContactPurpose.STORE_CONTACT || owner == null) return@LaunchedEffect
        try {
            val settings = withTimeoutOrNull(15_000L) { AitaAdvancedAuthenticationClient.settings() }
            val payload = settings?.payload
            if (currentAuthenticatedSessionGeneration() == generation && userAccountState.payloadValue?.id == owner &&
                settings != null && !settings.negative && payload != null) {
                verifiedOwnerEmails = payload.verifiedAccountContacts(AitaContactChannel.EMAIL)
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* A failed ownership check leaves ordinary confirmation available. */ }
    }
    LaunchedEffect(state, verifiedOwnerEmails) {
        state.requiredAddresses = changed?.filterNot {
            aitaReusableAccountContact(purpose, AitaContactChannel.EMAIL, it, verifiedOwnerEmails)
        }
    }
    LaunchedEffect(state) {
        while (true) { delay(500L); state.clockTick++ }
    }
    return state
}

@Composable
internal fun AppConfiguration.ContactEmailConfirmationContent(state: ContactEmailConfirmationState, enabled: Boolean = true) {
    if (state.requiredAddresses?.isEmpty() == true) return // Unchanged legacy addresses are NOT called verified.
    val scope = rememberCoroutineScope()
    val countdown = rememberAuthFlowCountdown(state.flow)
    val address = state.pendingAddress
    val allowed = enabled && !state.busy && state.ownsCurrentSession()
    val send: () -> Unit = send@{
        val destination = state.pendingAddress ?: return@send
        if (!enabled || state.busy || !state.ownsCurrentSession()) return@send
        val old = state.flow?.takeIf { state.flowAddress == destination && !countdown.expired }
        if (old != null && countdown.resendSeconds > 0L) {
            state.error = contactText("resend_wait", mapOf("seconds" to countdown.resendSeconds.toString())); return@send
        }
        state.busy = true; state.error = ""
        scope.launch {
            try {
                if (!state.ownsCurrentSession()) return@launch
                val response = withTimeoutOrNull(30_000L) {
                    if (old == null) AitaContactVerificationClient.request(
                        AitaContactCodeRequest(state.target, destination, locale = stateValues.appLanguage), state.generation)
                    else AitaContactVerificationClient.resend(state.registration,
                        AitaEmailCodeResendRequestDataModel(old.flowId, stateValues.appLanguage), state.generation)
                }
                if (!state.ownsCurrentSession()) return@launch
                val value = response?.payload
                if (response != null && !response.negative && value != null && value.flowId.isNotBlank() &&
                    value.nextStep == AitaAuthNextStep.EMAIL_CODE && value.serverTimeMillis > 0L &&
                    value.expiresAtMillis > value.serverTimeMillis && value.tokenPair == null && value.resetTicket.isEmpty()) {
                    state.flow = value; state.flowAddress = destination; state.code = ""
                } else state.error = response?.let { authResponseText(it) }?.takeIf { it.isNotBlank() } ?: contactText("request_failed")
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (state.ownsCurrentSession()) state.error = contactText("request_failed") }
            finally { state.busy = false }
        }
    }
    val verify: () -> Unit = verify@{
        val requestedFlow = state.flow ?: return@verify
        if (state.code.length != 6) { state.error = contactText("six_digits"); return@verify }
        if (!enabled || state.busy || !state.ownsCurrentSession() || countdown.expired ||
            state.code.length != 6 || state.flowAddress != state.pendingAddress) return@verify
        val request = AitaEmailCodeVerifyRequestDataModel(requestedFlow.flowId, state.code)
        state.busy = true; state.error = ""
        scope.launch {
            try {
                if (!state.ownsCurrentSession()) return@launch
                val response = withTimeoutOrNull(30_000L) {
                    AitaContactVerificationClient.verify(state.registration, request, state.generation)
                }
                if (!state.ownsCurrentSession()) return@launch
                val value = response?.payload
                if (response == null || response.negative || value == null || !state.accept(value, requestedFlow)) {
                    state.error = response?.let { authResponseText(it) }?.takeIf { it.isNotBlank() } ?: contactText("request_failed")
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (state.ownsCurrentSession()) state.error = contactText("request_failed") }
            finally { state.busy = false }
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)
        .background(stateValues.AccentColor.copy(alpha = 0.06f), shape)
        .border(BorderStroke(1.dp, stateValues.AccentColor.copy(alpha = 0.35f)), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val theme = appDrawableThemeId(stateValues.appThemeId)
            CpImage(Modifier.size(36.dp), url = stateValues.drawables.orEmpty().extractPath(145L, theme) ?: "svg/145_${theme}.svg",
                fallbackRes = if (theme == 1L) Res.drawable._145_1 else Res.drawable._145_0,
                contentDescription = contactText("title"), tintColor = null)
            Text(contactText(if (state.ready) "verified" else "title"), color = stateValues.TextColor,
                fontSize = stateValues.textSize, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        if (state.requiredAddresses == null) {
            Text(contactText("invalid_email"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
        } else if (state.ready) {
            Text(contactText("pending_save"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            actionButton(Modifier.fillMaxWidth(), text = contactText("again"), enabled = allowed,
                autoLoading = false, onClick = { if (allowed) state.clear() })
        } else {
            Text(contactText("detail"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            if (address != null) Text(address, color = stateValues.TextColor, fontSize = stateValues.textSize)
            if (state.flow != null && state.flowAddress == address && !countdown.expired) {
                aitaFormTextField(modifier = Modifier.fillMaxWidth(), value = state.code,
                    onValueChange = { state.code = it; state.error = "" }, titleText = contactText("code_label"),
                    placeholderText = "000000", identityKey = "contact-code-${state.flow?.flowId}",
                    enabled = allowed, keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go,
                    onImeAction = verify, leadingIconPath = stateValues.drawablePathIconEmail,
                    sensitive = true, parentOwnsValue = true, onTransformValue = { aitaAuthCodeDigits(it).take(6) })
                actionButton(Modifier.fillMaxWidth(), text = contactText("confirm"), enabled = allowed,
                    loading = state.busy, autoLoading = false, onClick = verify)
                Text(contactText("expires", mapOf("seconds" to countdown.expiresSeconds.toString())),
                    color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            }
            if (countdown.expired || state.hasExpiredProof) Text(contactText("expired"), color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
            val waiting = state.flowAddress == address && !countdown.expired && countdown.resendSeconds > 0L
            actionButton(Modifier.fillMaxWidth(), text = if (waiting) contactText("resend_wait", mapOf("seconds" to countdown.resendSeconds.toString()))
                else contactText(if (state.flow == null || countdown.expired || state.flowAddress != address) "send" else "resend"),
                enabled = allowed && address != null, loading = state.busy, autoLoading = false, onClick = send)
            // A stale authorization/consumed flow can be restarted without discarding the business form.
            if (state.error.isNotBlank() && state.flow != null) actionButton(Modifier.fillMaxWidth(),
                text = contactText("again"), enabled = allowed, autoLoading = false, onClick = { if (allowed) state.clear() })
        }
        if (state.error.isNotBlank()) Text(state.error, color = stateValues.ErrorColor, fontSize = stateValues.smallTextSize)
    }
}

@Composable
internal fun AppConfiguration.RegistrationEmailConfirmationScreen(pending: UserAuthSignUpDataModel, onBack: () -> Unit) {
    val confirmation = rememberContactEmailConfirmation(AitaContactPurpose.REGISTRATION, "", listOf(pending.email))
    val scope = rememberCoroutineScope()
    var existingAccount by remember { mutableStateOf<Boolean?>(null) }
    var recoveryIdentifier by remember { mutableStateOf(pending.email) }
    val busy = stateValues.signUpInProgress
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.width(stateValues.boundWidgetWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(contactText("registration_title"), color = stateValues.TextColor,
                fontSize = stateValues.titleTextSize, fontWeight = FontWeight.Bold)
            Text(contactText("registration_detail"), color = stateValues.PlaceholderTextColor, fontSize = stateValues.smallTextSize)
            ContactEmailConfirmationContent(confirmation, enabled = !busy)
            if (existingAccount != true) actionButton(Modifier.fillMaxWidth(), text = contactText("create_account"), enabled = confirmation.ready && !busy,
                loading = busy, autoLoading = false, unavailableText = contactText("required_before_save")) {
                if (confirmation.ready && !stateValues.signUpInProgress) signUpUser(pending.copy(
                    contactVerificationId = confirmation.draftId, contactEmailProofs = confirmation.proofs), onFailure = { response ->
                    scope.launch {
                        if (response.httpStatusCode == 409 || response.httpStatusCode == 403 || response.transportFailure ||
                            (response.httpStatusCode ?: 0) >= 500) {
                            existingAccount = response.httpStatusCode == 409
                            recoveryIdentifier = registrationRecoveryIdentifier(pending, response.message)
                        }
                    }
                })
            }
            if (existingAccount != null) {
                Text(contactText(if (existingAccount == true) "registration_exists" else "registration_uncertain"),
                    color = stateValues.TextColor, fontSize = stateValues.smallTextSize)
                fun openExisting(recover: Boolean) {
                    scope.launch {
                        registrationLoginEntry.value = RegistrationLoginEntry(recoveryIdentifier, recover)
                        Navigation.UserAuth.clearLeft()
                        onBack()
                    }
                }
                actionButton(Modifier.fillMaxWidth(), text = contactText("existing_login"), enabled = !busy,
                    autoLoading = false, onClick = { openExisting(false) })
                actionButton(Modifier.fillMaxWidth(), text = authUiText("Forgot password?", "Забыли пароль?", "Құпия сөзді ұмыттыңыз ба?", "Сырсөздү унуттуңузбу?"), enabled = !busy,
                    autoLoading = false, onClick = { openExisting(true) })
            }
            actionButton(Modifier.fillMaxWidth(), text = contactText("back"), enabled = !busy,
                autoLoading = false, onClick = { if (!busy) onBack() })
            Spacer(Modifier.height(stateValues.screenHeight / 10))
        }
    }
}
