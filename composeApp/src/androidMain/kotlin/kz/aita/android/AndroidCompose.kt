// THIS IS AndroidCompose.kt - in androidMain compose module of kmp compose app
package kz.aita.android

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kz.aita.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

val Context.tokensDataStore by preferencesDataStore(name = "store_tokens")

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
    return getLocalKv(KEY_REQUESTED_PERMISSIONS)?.run { split("|") } ?: emptyList()
  }

  suspend fun getRequestedPermissionsRepliedWithDontAskAgain(): List<String> {
    return getLocalKv(KEY_REQUESTED_PERMISSIONS_REPLIED_WITH_DONT_ASK_AGAIN)?.run { split("|") } ?: emptyList()
  }

  fun addRequestedPermission(permission: String) {
    viewModelScope.launch {
      val permissions = getRequestedPermissions()

      if (!permissions.contains(permission))
        putLocalKv(KEY_REQUESTED_PERMISSIONS, permission)
    }
  }

  fun addRequestedPermissionRepliedWithDontAskAgain(permission: String) {
    viewModelScope.launch {
      val permissions = getRequestedPermissionsRepliedWithDontAskAgain()

      if (!permissions.contains(permission))
        putLocalKv(KEY_REQUESTED_PERMISSIONS_REPLIED_WITH_DONT_ASK_AGAIN, permission)
    }
  }

  fun removeRequestedPermission(permission: String) {
    viewModelScope.launch {
      val permissions = getRequestedPermissions()

      if (permissions.contains(permission))
        deleteLocalKv(KEY_REQUESTED_PERMISSIONS)
    }
  }

  fun removeRequestedPermissionRepliedWithDontAskAgain(permission: String) {
    viewModelScope.launch {
      val permissions = getRequestedPermissionsRepliedWithDontAskAgain()

      if (permissions.contains(permission))
        deleteLocalKv(KEY_REQUESTED_PERMISSIONS_REPLIED_WITH_DONT_ASK_AGAIN)
    }
  }
}

private const val ALIAS_KEYSTORE = "aita_keystore"

suspend fun getEncryptedValue(key: String): String? {
  val preferencesKey = stringPreferencesKey(key)

  val base64: String = AITA.get()
    .tokensDataStore
    .data
    .map { it[preferencesKey] }
    .first() ?: return null

  val blob = Base64.decode(base64, Base64.DEFAULT)
  val key: SecretKey = getOrCreateKey()

  return try {
    val iv = blob.copyOfRange(0, 12)
    val ct = blob.copyOfRange(12, blob.size)

    val cipher = Cipher
      .getInstance("AES/GCM/NoPadding")
      .apply {
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
      }

    val plain = cipher.doFinal(ct)

    plain.decodeToString()
  } catch (throwable: Throwable) {
    AITA.get().tokensDataStore.edit { it.remove(preferencesKey) }
    throwable.printStackTrace()

    null
  }
}

suspend fun setEncryptedValue(key: String, value: String?) {

  val preferencesKey = stringPreferencesKey(key)

  if (value == null) {
    AITA.get().tokensDataStore.edit { it.remove(preferencesKey) }
    return
  }

  val key: SecretKey = getOrCreateKey()

  val plain = value.encodeToByteArray()

  val cipher = Cipher
    .getInstance("AES/GCM/NoPadding")
    .apply {
      init(Cipher.ENCRYPT_MODE, key)
    }

  val iv = cipher.iv                                 // 12-byte nonce
  val ct = cipher.doFinal(plain)                     // ciphertext + 16-byte tag

  val blob = iv + ct
  val base64 = Base64.encodeToString(blob, Base64.NO_WRAP)

  AITA.get().tokensDataStore.edit { it[preferencesKey] = base64 }
}

private fun getOrCreateKey(): SecretKey {
  val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

  (ks.getKey(ALIAS_KEYSTORE, null) as? SecretKey)?.let { return it }

  val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
  val spec = KeyGenParameterSpec.Builder(
    ALIAS_KEYSTORE,
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
  )
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setRandomizedEncryptionRequired(true)
    .build()

  generator.init(spec)

  return generator.generateKey()
}

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

@HiltAndroidApp
class AITA : Application() {

  override fun onCreate() {
    super.onCreate()

    instance = this
    cacheDirPath = cacheDir.absolutePath
    getSqlDelightDriver =
      {
        AndroidSqliteDriver(
          schema = AppDatabase.Schema.synchronous(),
          context = this,
          name = "app_database.db"
        )
      }

    getStoredUserAuthTokens = {
      runBlocking(Dispatchers.IO) {
        getEncryptedValue("key_auth_tokens")?.run { jsonBase.decodeFromString<TokenPair>(this) }
      }
    }
    setStoredUserAuthTokens = {
      runBlocking(Dispatchers.IO) {
        setEncryptedValue("key_auth_tokens", it?.run { jsonBase.encodeToString(this) })
      }
    }
    getStoredUserAccountDataModel = {
      runBlocking {
        getEncryptedValue("key_user_account")?.run { jsonBase.decodeFromString<UserAccountDataModel>(this) }
      }
    }
    setStoredUserAccountDataModel = { value ->
      runBlocking {
        setEncryptedValue("key_user_account", value?.run { jsonBase.encodeToString(this) })
      }
    }

    init()
  }

  companion object {
    private lateinit var instance: AITA

    fun get() = instance
  }
}
