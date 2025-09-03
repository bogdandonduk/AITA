package kz.aita.core.configuration

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val _appLocale = MutableStateFlow("")
val appLocale = _appLocale.asStateFlow()

suspend fun setAppLocale(locale: String) {
  _appLocale.emit(locale)
}

@Composable
fun getStringResource(res: StringResource): String {
  return stringResource(res)
}
