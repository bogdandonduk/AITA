package kz.aita.android.app.system.ui.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kz.aita.genericLocalService
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(): ViewModel() {

  private val _currentRequestedPermission = MutableStateFlow<Triple<String, String, Array<String>?>>(Triple("", "", null))
  val currentRequestedPermission = _currentRequestedPermission.asStateFlow()

  private val _currentDialogWidget = MutableStateFlow(Triple("", "", { } to { }))
  val currentDialogWidget = _currentDialogWidget.asStateFlow()

  private val _cameraTorchOn = MutableStateFlow(false)
  val cameraTorchOn = _cameraTorchOn.asStateFlow()

  private val KEY_REQUESTED_PERMISSIONS = "key_RequestedPermissions"
  private val KEY_REQUESTED_PERMISSIONS_REPLIED_WITH_DONT_ASK_AGAIN = "key_RequestedPermissionsRepliedWithDontAskAgain"

  fun postCurrentRequestedPermission(
    rationaleTitle: String,
    rationaleSubtitle: String,
    permission: Array<String>?
  ) {
    viewModelScope.launch {
      _currentRequestedPermission.emit(Triple(rationaleTitle, rationaleSubtitle, permission))
    }
  }

  fun setTorch(on: Boolean) {
    viewModelScope.launch {
      _cameraTorchOn.emit(on)
    }
  }

  fun postCurrentDialogWidget(
    title: String,
    subtitle: String,
    negativeAction: () -> Unit,
    positiveAction: () -> Unit
  ) {
    viewModelScope.launch {
      _currentDialogWidget.emit(Triple(title, subtitle, negativeAction to positiveAction))
    }
  }


  suspend fun getRequestedPermissions(): List<String> {
    return genericLocalService.getKv(KEY_REQUESTED_PERMISSIONS)?.run { split("|") } ?: emptyList()
  }

  suspend fun getRequestedPermissionsRepliedWithDontAskAgain(): List<String> {
    return genericLocalService.getKv(KEY_REQUESTED_PERMISSIONS_REPLIED_WITH_DONT_ASK_AGAIN)?.run { split("|") } ?: emptyList()
  }

  fun addRequestedPermission(permission: String) {
    viewModelScope.launch {
      val permissions = getRequestedPermissions()

      if (!permissions.contains(permission))
        genericLocalService.putKv(KEY_REQUESTED_PERMISSIONS, permission)
    }
  }

  fun addRequestedPermissionRepliedWithDontAskAgain(permission: String) {
    viewModelScope.launch {
      val permissions = getRequestedPermissionsRepliedWithDontAskAgain()

      if (!permissions.contains(permission))
        genericLocalService.putKv(KEY_REQUESTED_PERMISSIONS_REPLIED_WITH_DONT_ASK_AGAIN, permission)
    }
  }

  fun removeRequestedPermission(permission: String) {
    viewModelScope.launch {
      val permissions = getRequestedPermissions()

      if (permissions.contains(permission))
        genericLocalService.deleteKv(KEY_REQUESTED_PERMISSIONS)
    }
  }

  fun removeRequestedPermissionRepliedWithDontAskAgain(permission: String) {
    viewModelScope.launch {
      val permissions = getRequestedPermissionsRepliedWithDontAskAgain()

      if (permissions.contains(permission))
        genericLocalService.deleteKv (KEY_REQUESTED_PERMISSIONS_REPLIED_WITH_DONT_ASK_AGAIN)
    }
  }
}
