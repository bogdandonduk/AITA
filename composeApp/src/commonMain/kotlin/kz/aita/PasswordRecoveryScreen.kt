package kz.aita

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation

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
                "Choose a new password. It must contain at least 8 characters, a digit and a special character.",
                "Выберите новый пароль: не менее 8 символов, цифра и специальный символ.",
                "Жаңа құпия сөз таңдаңыз: кемінде 8 таңба, сан және арнайы таңба."
            ),
            color = stateValues.PlaceholderTextColor,
            fontSize = stateValues.smallTextSize
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = newPassword,
            onValueChange = onNewPasswordChange,
            singleLine = true,
            label = { Text(authUiText("New password", "Новый пароль", "Жаңа құпия сөз")) },
            visualTransformation = PasswordVisualTransformation()
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = repeatedPassword,
            onValueChange = onRepeatedPasswordChange,
            singleLine = true,
            label = { Text(authUiText("Repeat password", "Повторите пароль", "Құпия сөзді қайталаңыз")) },
            visualTransformation = PasswordVisualTransformation()
        )
        actionButton(
            modifier = Modifier.fillMaxWidth(),
            text = authUiText("Restore password", "Восстановить пароль", "Құпия сөзді қалпына келтіру"),
            enabled = !busy && newPassword.isNotBlank() && repeatedPassword.isNotBlank(),
            loading = busy,
            onClick = onSubmit
        )
    }
}
