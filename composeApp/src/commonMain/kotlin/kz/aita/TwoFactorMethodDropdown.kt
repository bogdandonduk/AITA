package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
    var expanded by remember {mutableStateOf(false)}
    var width by remember {mutableIntStateOf(0)}
    val density=LocalDensity.current
    fun label(method:AitaLoginSecondFactor)=accountPresentationText(when(method){
        AitaLoginSecondFactor.NONE->"none";AitaLoginSecondFactor.EMAIL->"email";AitaLoginSecondFactor.AUTHENTICATOR->"authenticator"})
    Column(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(accountPresentationText("two_factor"),color=stateValues.TextColor,fontSize=stateValues.textSize)
        Box(Modifier.fillMaxWidth().onSizeChanged{width=it.width}) {
            OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                shape=RoundedCornerShape(stateValues.cornerRadius),border=BorderStroke(stateValues.unfocusedBorderWidth,stateValues.PlaceholderTextColor),
                colors=ButtonDefaults.outlinedButtonColors(contentColor=stateValues.TextColor)) {
                Text(label(selected),Modifier.weight(1f),fontSize=stateValues.textSize)
                CpImage(Modifier.size(20.dp),url=stateValues.drawablePathIconExpandMore,
                    fallbackRes=stateValues.drawableResIconExpandMore.value,contentDescription=null,tintColor=stateValues.TextColor)
            }
            DropdownMenu(expanded=expanded && enabled,onDismissRequest={expanded=false},
                modifier=Modifier.width(with(density){width.toDp()}),
                shape=RoundedCornerShape(stateValues.cornerRadius),
                containerColor=stateValues.BackgroundColor,
                tonalElevation=0.dp,
                border=BorderStroke(stateValues.unfocusedBorderWidth,stateValues.AccentColor)) {
                listOf(AitaLoginSecondFactor.NONE,AitaLoginSecondFactor.EMAIL,AitaLoginSecondFactor.AUTHENTICATOR).forEach {method->
                    val available=when(method){AitaLoginSecondFactor.NONE->true;AitaLoginSecondFactor.EMAIL->emailAvailable;else->authenticatorAvailable}
                    DropdownMenuItem(text={Text(label(method),color=if(!available)stateValues.PlaceholderTextColor else if(method==selected)stateValues.AccentColor else stateValues.TextColor,
                        fontSize=stateValues.textSize)},enabled=available,onClick={expanded=false;onSelected(method)})
                }
            }
        }
    }
}
