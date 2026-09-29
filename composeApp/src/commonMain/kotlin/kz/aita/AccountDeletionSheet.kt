package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kz.aita.auth.AitaAdvancedAuthenticationClient
import kz.aita.auth.AitaSecurityEmailAction

@Composable internal fun AppConfiguration.AccountDeletionSheet(onDismiss: () -> Unit) {
    val owner = stateValues.userAccount?.id ?: return
    val generation = currentAuthenticatedSessionGeneration()
    val scope = rememberCoroutineScope()
    var busy by remember(owner,generation) { mutableStateOf(false) }
    var error by remember(owner,generation) { mutableStateOf("") }
    var confirm by remember(owner,generation) { mutableStateOf(false) }
    fun text(key: String) = accountDeletionText(key,stateValues.appLanguage)
    AitaBottomSheet(title=text("title"),iconPath=stateValues.drawablePathIconDelete,
        iconRes=stateValues.drawableResIconDelete.value,onDismiss={ if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(text("explain"),color=stateValues.TextColor,fontSize=stateValues.textSize)
            val password = genericTextField(titleText=stateValues.stringPassword, placeholderText=stateValues.stringPassword,
                identityKey="account-delete-password:$owner", keyboardType=androidx.compose.ui.text.input.KeyboardType.Password, enabled=!busy,
                enableVoiceInput=false, visualTransformation={ androidx.compose.ui.text.input.PasswordVisualTransformation().filter(androidx.compose.ui.text.AnnotatedString(it.text)) },
                retainTextAcrossRecreation=false, persistTextDraft=false)
            val security=ProfileSecurityConfirmationInput("",password.value.text,AitaSecurityEmailAction.ACCOUNT_DELETE)
            if (error.isNotBlank()) Text(error,color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize)
            actionButton(text=text("title"),iconPath=stateValues.drawablePathIconDelete,autoLoading=false,loading=busy,
                enabled=!busy,confirmationRequired=false,onClick={
                    if (!busy) {
                        if (password.value.text.isBlank() || !security.ready) error=text("confirmation") else confirm=true
                    }
                })
            if (confirm) ModalDialogWidget(title=text("title"),subTitle=text("explain"),
                negativeButtonText=stateValues.stringCancel,positiveButtonText=text("title"),
                onDismiss={confirm=false},negativeAction={confirm=false},positiveAction={
                    confirm=false;busy=true;error=""
                    val request=AccountDeletionRequest(password.value.text,security.factor,security.emailProof,true)
                    scope.launch {
                        try {
                            val response=withTimeoutOrNull(45_000) { AitaAdvancedAuthenticationClient.deleteAccount(request) }
                            if (response?.negative==false && response.payload=="deleted") {
                                // Cleanup completes even when removing this UI cancels its composition scope.
                                withContext(NonCancellable) { finishDeletedAccountLocally(owner,generation) }
                                password.reset();onDismiss()
                            } else if (authenticatedSessionGenerationIsCurrent(generation)) {
                                error=response?.message?.extractLocalizedString(stateValues.appLanguage)?.takeIf { it.isNotBlank() } ?: text("network")
                            }
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { error=text("network") }
                        finally { busy=false }
                    }
                })
        }
    }
}
