package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kz.aita.auth.AitaLoginSecondFactor

internal enum class FactorSelectionAction { NONE, CONFIRM_DISABLE, POLICY, ENROLL }
internal fun factorSelectionAction(chosen:AitaLoginSecondFactor,current:AitaLoginSecondFactor,enrolled:Boolean)=when {
    chosen==AitaLoginSecondFactor.NONE && current==AitaLoginSecondFactor.NONE -> FactorSelectionAction.NONE
    chosen==AitaLoginSecondFactor.NONE -> FactorSelectionAction.CONFIRM_DISABLE
    chosen==AitaLoginSecondFactor.AUTHENTICATOR && !enrolled -> FactorSelectionAction.ENROLL
    else -> FactorSelectionAction.POLICY
}

@Composable internal fun AppConfiguration.TwoFactorMethodDropdown(selected:AitaLoginSecondFactor,enabled:Boolean,
    emailAvailable:Boolean,authenticatorAvailable:Boolean,onSelected:(AitaLoginSecondFactor)->Unit) {
    fun label(method:AitaLoginSecondFactor)=accountPresentationText(when(method){
        AitaLoginSecondFactor.NONE->"none";AitaLoginSecondFactor.EMAIL->"email";AitaLoginSecondFactor.AUTHENTICATOR->"authenticator"})
    AitaDropdownField(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        title = accountPresentationText("two_factor"), selectedId = selected.name,
        enabled = enabled, placeholder = label(selected),
        options = AitaLoginSecondFactor.entries.map { method ->
            DropdownOption(method.name, label(method), enabled = when (method) {
                AitaLoginSecondFactor.NONE -> true
                AitaLoginSecondFactor.EMAIL -> emailAvailable
                AitaLoginSecondFactor.AUTHENTICATOR -> authenticatorAvailable
            })
        }, onSelected = { onSelected(AitaLoginSecondFactor.valueOf(it)) })
}
