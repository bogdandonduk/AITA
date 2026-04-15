package kz.aita.android.app.system.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kz.aita.android.app.system.ui.viewModel.MainActivityViewModel
import kz.aita.compose.AppConfiguration
import kz.aita.compose.MainScreen

@AndroidEntryPoint
class MainActivity: ComponentActivity() {

  val viewModel: MainActivityViewModel by viewModels()

  private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
  var permissionGrantedResultAction: ((String) -> Unit)? = null
  var permissionDeniedResultAction: ((String) -> Unit)? = null

  private var currentSAFReceiptWriteLauncher: ActivityResultLauncher<Intent>? = null
  private var currentSAFReceiptWriteResultAction: ((Uri?) -> Unit)? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    instance = this

    permissionLauncher = registerForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions: Map<String, @JvmSuppressWildcards Boolean> ->
      permissions.forEach {
        if (!it.value) {
          permissionDeniedResultAction?.invoke(it.key)
          permissionDeniedResultAction = null
        } else {
          permissionGrantedResultAction?.invoke(it.key)
          permissionGrantedResultAction = null
        }
      }
    }

    currentSAFReceiptWriteLauncher =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        currentSAFReceiptWriteResultAction?.invoke(result.data?.data)
        currentSAFReceiptWriteResultAction = null
      }

//    enableFullscreen()

    setContent {
      AppConfiguration(
        {
          MainScreen()
        }
      )
    }
  }

  fun enableFullscreen() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)

    windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

    windowInsetsController.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  }

  fun disableFullScreen() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(
      window,
      window.decorView
    ).show(WindowInsetsCompat.Type.statusBars())
  }

  fun cancelPermissionRequestRationale() {
    viewModel.postCurrentRequestedPermission(
      "", "", null
    )
  }

  suspend fun requestPermissionDirectly(
    permissions: Array<String>,
    permissionGrantedResultAction: (() -> Unit)? = null,
    permissionDeniedResultAction: (() -> Unit)? = null
  ) {
    if (this.permissionGrantedResultAction != null) {
      val old = this.permissionGrantedResultAction

      this.permissionGrantedResultAction = {
        viewModel.removeRequestedPermission(it)
        viewModel.removeRequestedPermissionRepliedWithDontAskAgain(it)

        old?.invoke(it)
        permissionGrantedResultAction?.invoke()
      }
    } else {
      this.permissionGrantedResultAction = {
        viewModel.removeRequestedPermission(it)
        viewModel.removeRequestedPermissionRepliedWithDontAskAgain(it)


        permissionGrantedResultAction?.invoke()
      }
    }

    this.permissionDeniedResultAction = {
      lifecycleScope.launch {
        val deniedBeforeWithDontAskAgain2 = viewModel.getRequestedPermissionsRepliedWithDontAskAgain()
          .contains(it) && !ActivityCompat.shouldShowRequestPermissionRationale(
          this@MainActivity,
          it
        )

        viewModel.addRequestedPermission(it)

        if (deniedBeforeWithDontAskAgain2) {
          viewModel.addRequestedPermissionRepliedWithDontAskAgain(it)
        }

        permissionDeniedResultAction?.invoke()
      }
    }

    permissionLauncher.launch(permissions)
  }

  suspend fun requestPermissions(
    permissions: Array<String>,
    deniedRationaleTitle: String,
    deniedRationaleSubtitle: String,
    dontAskAgainDeniedRationaleTitle: String,
    dontAskAgainDeniedRationaleSubtitle: String,
    force: Boolean = false,
    permissionGrantedResultAction: ((String) -> Unit)? = null,
    permissionDeniedResultAction: ((String) -> Unit)? = null
  ) {
    viewModel.removeRequestedPermission(permissions.first())
    val deniedBefore = viewModel.getRequestedPermissions()
      .contains(permissions.first()) && ActivityCompat.shouldShowRequestPermissionRationale(
      this@MainActivity,
      permissions.first()
    )

    val deniedBeforeWithDontAskAgain = viewModel.getRequestedPermissionsRepliedWithDontAskAgain()
      .contains(permissions.first()) && !ActivityCompat.shouldShowRequestPermissionRationale(
      this@MainActivity,
      permissions.first()
    )

    if (force || !deniedBefore && !deniedBeforeWithDontAskAgain) {

      if (deniedBeforeWithDontAskAgain) {
        viewModel.postCurrentRequestedPermission(
          dontAskAgainDeniedRationaleTitle, dontAskAgainDeniedRationaleSubtitle, permissions
        )
      } else if (deniedBefore) {
        viewModel.postCurrentRequestedPermission(
          deniedRationaleTitle, deniedRationaleSubtitle, permissions
        )
      } else {
        this.permissionGrantedResultAction = {
          viewModel.removeRequestedPermission(it)
          viewModel.removeRequestedPermissionRepliedWithDontAskAgain(it)

          permissionGrantedResultAction?.invoke(it)
        }

        this.permissionDeniedResultAction = {
          lifecycleScope.launch {
            val deniedBeforeWithDontAskAgain2 = viewModel.getRequestedPermissionsRepliedWithDontAskAgain()
              .contains(it) && !ActivityCompat.shouldShowRequestPermissionRationale(
              this@MainActivity,
              it
            )

            viewModel.addRequestedPermission(it)

            if (deniedBeforeWithDontAskAgain2) {
              viewModel.addRequestedPermissionRepliedWithDontAskAgain(it)
            }

            permissionDeniedResultAction?.invoke(it)
          }
        }

        permissionLauncher.launch(permissions)
      }
    }
  }

  companion object {
    private lateinit var instance: MainActivity

    fun get() = instance

  }
}