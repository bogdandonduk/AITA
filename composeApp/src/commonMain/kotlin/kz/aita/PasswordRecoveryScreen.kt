package kz.aita

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/** Sensitive password values are intentionally owned by the caller with remember, never rememberSaveable. */
@Composable
internal fun AppConfiguration.PasswordRecoveryNewPasswordContent(
    newPassword: String,
    repeatedPassword: String,
    busy: Boolean,
    onNewPasswordChange: (String) -> Unit,
    onRepeatedPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(stateValues.marginTextField)) {
        Text(
            text = authUiText(
                "8+ characters, a digit and a symbol",
                "От 8 символов, цифра и спецсимвол",
                "8+ таңба, сан және арнайы таңба"
            ),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )
        aitaFormTextField(
            modifier = Modifier.fillMaxWidth(),
            value = newPassword,
            onValueChange = onNewPasswordChange,
            titleText = authUiText("New password", "Новый пароль", "Жаңа құпия сөз"),
            placeholderText = stateValues.stringEnterPassword,
            identityKey = "password-recovery-new-password",
            enabled = !busy,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next,
            leadingIconPath = stateValues.drawablePathIconPassword,
            password = true,
            sensitive = true
        )
        aitaFormTextField(
            modifier = Modifier.fillMaxWidth(),
            value = repeatedPassword,
            onValueChange = onRepeatedPasswordChange,
            titleText = authUiText("Repeat password", "Повторите пароль", "Құпия сөзді қайталаңыз"),
            placeholderText = stateValues.stringRepeatPassword,
            identityKey = "password-recovery-repeated-password",
            enabled = !busy,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Go,
            onImeAction = onSubmit,
            leadingIconPath = stateValues.drawablePathIconPassword,
            password = true,
            sensitive = true
        )
        actionButton(
            modifier = Modifier.fillMaxWidth(),
            text = authUiText("Restore password", "Восстановить пароль", "Құпия сөзді қалпына келтіру"),
            enabled = !busy && newPassword.isNotBlank() && repeatedPassword.isNotBlank(),
            loading = busy,
            autoLoading = false,
            onClick = onSubmit
        )
    }
}
