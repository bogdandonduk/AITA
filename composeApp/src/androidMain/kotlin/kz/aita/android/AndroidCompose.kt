// THIS IS AndroidCompose.kt - in androidMain compose module of kmp compose app
package kz.aita.android

import aita.composeapp.generated.resources.Res
import aita.composeapp.generated.resources._0_0
import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.UiModeManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.print.*
import android.provider.MediaStore
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kz.aita.*
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.*
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject


private fun installAndroidSoftKeyboardHider(context: Context) {
    forceHidePlatformSoftKeyboard = {
        val activity = MainActivity.getOrNull()
        val targetContext = activity ?: context
        val inputMethodManager = targetContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val decorView = activity?.window?.decorView
        val hideAction = {
            val token = activity?.currentFocus?.windowToken ?: decorView?.windowToken
            if (token != null) {
                inputMethodManager?.hideSoftInputFromWindow(token, 0)
            }
        }

        if (decorView != null) {
            decorView.post { hideAction() }
        } else {
            hideAction()
        }
    }
}

private var activeAndroidSpeechRecognizer: SpeechRecognizer? = null

private const val ANDROID_SPEECH_EXTRA_ENABLE_LANGUAGE_DETECTION = "android.speech.extra.ENABLE_LANGUAGE_DETECTION"
private const val ANDROID_SPEECH_EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES = "android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES"
private const val ANDROID_SPEECH_EXTRA_ENABLE_LANGUAGE_SWITCH = "android.speech.extra.ENABLE_LANGUAGE_SWITCH"
private const val ANDROID_SPEECH_EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES = "android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES"
private const val ANDROID_SPEECH_EXTRA_LANGUAGE_SWITCH_INITIAL_ACTIVE_DURATION_TIME_MILLIS = "android.speech.extra.LANGUAGE_SWITCH_INITIAL_ACTIVE_DURATION_TIME_MILLIS"
private const val ANDROID_SPEECH_EXTRA_LANGUAGE_SWITCH_MAX_SWITCHES = "android.speech.extra.LANGUAGE_SWITCH_MAX_SWITCHES"
private const val ANDROID_SPEECH_LANGUAGE_SWITCH_BALANCED = "balanced"
private const val ANDROID_SPEECH_EXTRA_DETECTED_LANGUAGE = "android.speech.extra.DETECTED_LANGUAGE"
private const val ANDROID_SPEECH_EXTRA_LANGUAGE = "android.speech.extra.LANGUAGE"
private const val ANDROID_SPEECH_EXTRA_LANGUAGE_TAG = "android.speech.extra.LANGUAGE_TAG"

private fun androidSpeechLocaleTag(languageOrTag: String): String {
    val clean = languageOrTag.trim().replace('_', '-').takeIf { it.isNotBlank() } ?: return Locale.getDefault().toLanguageTag()
    val lower = clean.lowercase(Locale.ROOT)
    return when (lower) {
        "ru", "ru-ru" -> "ru-RU"
        "kk", "kk-kz", "kz", "kz-kz" -> "kk-KZ"
        "en", "en-us" -> "en-US"
        "en-gb" -> "en-GB"
        "main", "system" -> Locale.getDefault().toLanguageTag()
        else -> clean
    }
}

private fun androidSpeechLocaleTags(texts: VoiceInputPermissionRequestText): List<String> {
    val candidates = (listOf(texts.primaryLanguageTag) + texts.languageTags + appLanguageState.value + Locale.getDefault().toLanguageTag() + listOf("ru-RU", "kk-KZ", "en-US"))
        .map(::androidSpeechLocaleTag)
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
    return candidates.ifEmpty { listOf(Locale.getDefault().toLanguageTag()) }
}

private fun Bundle?.detectedAndroidSpeechLanguageTag(): String = this?.let { bundle ->
    listOf(
        bundle.getString(ANDROID_SPEECH_EXTRA_DETECTED_LANGUAGE),
        bundle.getString(ANDROID_SPEECH_EXTRA_LANGUAGE_TAG),
        bundle.getString(ANDROID_SPEECH_EXTRA_LANGUAGE)
    ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
}.orEmpty()

private fun androidSpeechShouldTryNextLanguage(error: Int): Boolean = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    12,
    13,
    14,
    15 -> true
    else -> false
}

private fun androidSpeechErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
    SpeechRecognizer.ERROR_CLIENT -> "Voice input client error"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing"
    SpeechRecognizer.ERROR_NETWORK -> "Network error during voice input"
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice input network timeout"
    SpeechRecognizer.ERROR_NO_MATCH -> "Nothing was recognized"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy"
    SpeechRecognizer.ERROR_SERVER -> "Voice recognizer server error"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
    10 -> "Too many voice input requests. Wait a moment and try again"
    11 -> "Voice recognizer disconnected. Try again"
    12 -> "This speech language is not supported on this device"
    13 -> "This speech language is not downloaded or available on this device"
    14 -> "Could not check speech recognition support"
    15 -> "Voice recognizer is temporarily unavailable"
    else -> "Voice input error ($error)"
}

private fun androidPermissionKind(permission: String): PlatformPermissionKind = when (permission) {
    Manifest.permission.CAMERA -> PlatformPermissionKind.Camera
    Manifest.permission.RECORD_AUDIO -> PlatformPermissionKind.Microphone
    else -> PlatformPermissionKind.AppSettings
}

private fun Context.openAndroidApplicationSettingsResult(): ReceiptPlatformActionResult {
    return runCatching {
        val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(detailsIntent)
        ReceiptPlatformActionResult(true, "App settings opened")
    }.getOrElse { firstError ->
        runCatching {
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(fallbackIntent)
            ReceiptPlatformActionResult(true, "Android settings opened")
        }.getOrElse {
            ReceiptPlatformActionResult(false, firstError.message ?: it.message ?: "Could not open app settings")
        }
    }
}

private fun installAndroidVoiceInput(context: Context) {
    isPlatformVoiceInputAvailable = {
        MainActivity.getOrNull()?.let { SpeechRecognizer.isRecognitionAvailable(it) } == true
    }

    getVoiceInputPermissionState = {
        val activity = MainActivity.getOrNull()
        when {
            activity == null -> PlatformPermissionState.Unavailable
            !SpeechRecognizer.isRecognitionAvailable(activity) -> PlatformPermissionState.Unavailable
            else -> activity.permissionState(Manifest.permission.RECORD_AUDIO)
        }
    }

    stopPlatformVoiceInput = {
        runCatching { activeAndroidSpeechRecognizer?.stopListening() }
        runCatching { activeAndroidSpeechRecognizer?.cancel() }
        runCatching { activeAndroidSpeechRecognizer?.destroy() }
        activeAndroidSpeechRecognizer = null
    }

    startPlatformVoiceInput = start@{ texts, callbacks ->
        val activity = MainActivity.getOrNull()
        if (activity == null) {
            callbacks.onError(texts.deniedSubtitle)
            callbacks.onFinished()
            return@start
        }

        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            callbacks.onError("Voice recognition is not available on this Android device")
            callbacks.onFinished()
            return@start
        }

        val languageTags = androidSpeechLocaleTags(texts)
        val languageAttempts: List<String?> = (languageTags + listOf<String?>(null))
            .distinctBy { it?.lowercase(Locale.ROOT) ?: "auto" }

        fun beginListening(attemptIndex: Int = 0) {
            activity.runOnUiThread {
                runCatching { activeAndroidSpeechRecognizer?.destroy() }
                val recognizer = SpeechRecognizer.createSpeechRecognizer(activity)
                activeAndroidSpeechRecognizer = recognizer
                val currentLanguageTag = languageAttempts.getOrNull(attemptIndex)

                fun finishAndDestroy() {
                    runCatching { recognizer.destroy() }
                    if (activeAndroidSpeechRecognizer === recognizer) activeAndroidSpeechRecognizer = null
                }

                fun tryNextLanguageFor(error: Int): Boolean {
                    val nextIndex = attemptIndex + 1
                    if (androidSpeechShouldTryNextLanguage(error) && nextIndex < languageAttempts.size) {
                        finishAndDestroy()
                        beginListening(nextIndex)
                        return true
                    }
                    return false
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        callbacks.onAmplitude(0.20f)
                        val detectedLanguage = params.detectedAndroidSpeechLanguageTag()
                        when {
                            detectedLanguage.isNotBlank() -> callbacks.onDetectedLanguage(detectedLanguage)
                            !currentLanguageTag.isNullOrBlank() -> callbacks.onDetectedLanguage(currentLanguageTag)
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        callbacks.onAmplitude(0.40f)
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        callbacks.onAmplitude(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() {
                        callbacks.onAmplitude(0.12f)
                    }

                    override fun onError(error: Int) {
                        if (tryNextLanguageFor(error)) return
                        callbacks.onError(androidSpeechErrorMessage(error))
                        callbacks.onFinished()
                        finishAndDestroy()
                    }

                    override fun onResults(results: Bundle?) {
                        results.detectedAndroidSpeechLanguageTag().takeIf { it.isNotBlank() }?.let(callbacks.onDetectedLanguage)
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (text.isNotBlank()) callbacks.onFinalText(text)
                        callbacks.onFinished()
                        finishAndDestroy()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        partialResults.detectedAndroidSpeechLanguageTag().takeIf { it.isNotBlank() }?.let(callbacks.onDetectedLanguage)
                        val text = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (text.isNotBlank()) callbacks.onPartialText(text)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    currentLanguageTag?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, texts.listeningTitle)
                    if (Build.VERSION.SDK_INT >= 34 && languageTags.size > 1) {
                        putExtra(ANDROID_SPEECH_EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                        putStringArrayListExtra(ANDROID_SPEECH_EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, ArrayList(languageTags))
                        putExtra(ANDROID_SPEECH_EXTRA_ENABLE_LANGUAGE_SWITCH, ANDROID_SPEECH_LANGUAGE_SWITCH_BALANCED)
                        putStringArrayListExtra(ANDROID_SPEECH_EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, ArrayList(languageTags))
                        putExtra(ANDROID_SPEECH_EXTRA_LANGUAGE_SWITCH_INITIAL_ACTIVE_DURATION_TIME_MILLIS, 3500)
                        putExtra(ANDROID_SPEECH_EXTRA_LANGUAGE_SWITCH_MAX_SWITCHES, languageTags.size.coerceAtLeast(1))
                    }
                }

                runCatching { recognizer.startListening(intent) }
                    .onFailure { throwable ->
                        if (!tryNextLanguageFor(12)) {
                            callbacks.onError(throwable.message ?: "Could not start voice input")
                            callbacks.onFinished()
                            finishAndDestroy()
                        }
                    }
            }
        }

        val alreadyGranted = ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            beginListening()
            return@start
        }

        activity.requestPermissions(
            permissions = arrayOf(Manifest.permission.RECORD_AUDIO),
            deniedRationaleTitle = texts.deniedTitle,
            deniedRationaleSubtitle = texts.deniedSubtitle,
            dontAskAgainDeniedRationaleTitle = texts.settingsTitle,
            dontAskAgainDeniedRationaleSubtitle = texts.settingsSubtitle,
            force = false,
            permissionGrantedResultAction = { beginListening() },
            permissionDeniedResultAction = {
                callbacks.onDenied()
                callbacks.onFinished()
            }
        )
    }
}

@OptIn(ExperimentalGetImage::class)
private fun installAndroidCameraBarcodeScanner() {
    getCameraScannerPermissionState = {
        val activity = MainActivity.getOrNull()
        if (activity == null) PlatformPermissionState.Unavailable else activity.permissionState(Manifest.permission.CAMERA)
    }

    requestCameraScannerPermission = requestCameraScannerPermission@{ texts, onGranted, onDenied ->
        val activity = MainActivity.getOrNull()
        if (activity == null) {
            onDenied()
            return@requestCameraScannerPermission
        }

        val alreadyGranted = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            onGranted()
            return@requestCameraScannerPermission
        }

        activity.requestPermissions(
            permissions = arrayOf(Manifest.permission.CAMERA),
            deniedRationaleTitle = texts.deniedTitle,
            deniedRationaleSubtitle = texts.deniedSubtitle,
            dontAskAgainDeniedRationaleTitle = texts.settingsTitle,
            dontAskAgainDeniedRationaleSubtitle = texts.settingsSubtitle,
            force = false,
            permissionGrantedResultAction = { onGranted() },
            permissionDeniedResultAction = { onDenied() }
        )
    }

    barcodeCameraScannerContent = { modifier, onBarcodeDetected, onClose ->
        AndroidBarcodeCameraScannerPane(
            modifier = modifier,
            onBarcodeDetected = onBarcodeDetected,
            onClose = onClose
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@OptIn(ExperimentalGetImage::class)
@Composable
private fun AppConfiguration.AndroidBarcodeCameraScannerPane(
    modifier: Modifier,
    onBarcodeDetected: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var statusText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            runCatching { analysisExecutor.shutdown() }
        }
    }

    LaunchedEffect(camera, torchOn) {
        runCatching { camera?.cameraControl?.enableTorch(torchOn) }
    }

    DisposableEffect(lifecycleOwner, lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            runCatching {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                    if (!raw.isNullOrBlank()) {
                                        mainExecutor.execute {
                                            statusText = raw
                                            onBarcodeDetected(raw)
                                        }
                                    }
                                }
                                .addOnFailureListener { throwable ->
                                    mainExecutor.execute {
                                        statusText = throwable.message ?: "Camera scanner error"
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
                runCatching { camera?.cameraControl?.enableTorch(torchOn) }
            }.onFailure { throwable ->
                statusText = throwable.message ?: "Could not start camera"
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
            camera = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CameraScannerOverlayIconButton(
                contentDescription = localizedStringResource(987, "Switch camera"),
                iconPath = stateValues.drawablePathIconSwitch,
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }
            )

            CameraScannerOverlayIconButton(
                contentDescription = localizedStringResource(988, "Torch"),
                enabled = camera?.cameraInfo?.hasFlashUnit() != false,
                onClick = { torchOn = !torchOn }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (torchOn) "⚡" else "🔦",
                        color = Color.White.copy(alpha = if (camera?.cameraInfo?.hasFlashUnit() != false) 0.96f else 0.38f),
                        fontSize = 21.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            CameraScannerOverlayIconButton(
                contentDescription = localizedStringResource(989, "Close scanner"),
                iconPath = stateValues.drawablePathIconCancel,
                onClick = onClose
            )
        }
    }
}


@Composable
private fun AppConfiguration.CameraScannerOverlayIconButton(
    contentDescription: String,
    iconPath: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(stateValues.cornerRadius)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.20f))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.78f else 0.28f), shape)
            .aitaClickable(enabled = enabled, onClick = onClick)
            .padding(9.dp),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) {
            content()
        } else if (iconPath != null) {
            CpImage(
                modifier = Modifier.fillMaxSize(),
                url = iconPath,
                fallbackRes = Res.drawable._0_0,
                contentDescription = contentDescription,
                tintColor = Color.White.copy(alpha = if (enabled) 0.94f else 0.34f)
            )
        }
    }
}

private fun installAndroidVectorDrawableRenderer() {
    renderAndroidVectorDrawable = renderer@{ modifier, resourceName, contentDescription, contentScale, colorFilter ->
        val context = LocalContext.current
        val androidResourceName = remember(resourceName) { "ic_aita_$resourceName" }
        val resourceId = remember(context.packageName, androidResourceName) {
            context.resources.getIdentifier(androidResourceName, "drawable", context.packageName)
        }

        if (resourceId == 0) {
            false
        } else {
            Image(
                modifier = modifier,
                painter = painterResource(resourceId),
                contentDescription = contentDescription,
                contentScale = contentScale,
                colorFilter = colorFilter
            )
            true
        }
    }
}

private class ReceiptPdfPrintDocumentAdapter(
    private val fileName: String,
    private val pdfBytes: ByteArray
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(fileName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()

        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }

        runCatching {
            val descriptor = destination ?: error("Print destination is not available")
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                output.write(pdfBytes)
                output.flush()
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        }.getOrElse { throwable ->
            callback?.onWriteFailed(throwable.message ?: "Could not write receipt PDF")
        }
    }
}

object ReceiptPlatformAndroidBridge {
    var writeEscPosBytes: (suspend (ByteArray) -> Boolean)? = null
    @Volatile var bluetoothPrinterMacAddress: String? = null

    fun configureBluetoothPrinter(macAddress: String?) {
        bluetoothPrinterMacAddress = macAddress?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
    }

    suspend fun writeEscPosBytesToConfiguredPrinter(printerBytes: ByteArray): Boolean =
        writeEscPosBytes?.let { it(printerBytes.copyOf()) }
            ?: BluetoothPrinterTransport.write(bluetoothPrinterMacAddress, printerBytes)
}


object LabelPrinterAndroidBridge {
    /** Optional direct TSPL/ZPL/CPCL label writer. */
    var writeLabelBytes: (suspend (ByteArray) -> Boolean)? = null
    var bluetoothLabelPrinterMacAddress: String? = null
    var labelPrinterProtocol: String = LABEL_PRINTER_PROTOCOL_AUTO

    private val bluetoothSerialPortProfileUuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun configureBluetoothLabelPrinter(macAddress: String?) {
        bluetoothLabelPrinterMacAddress = macAddress
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun configureProtocol(protocol: String?) {
        labelPrinterProtocol = normalizeLabelPrinterProtocol(protocol)
    }

    private suspend fun writeLabelBytesToConfiguredBluetoothPrinter(labelBytes: ByteArray): Boolean =
        BluetoothPrinterTransport.write(bluetoothLabelPrinterMacAddress, labelBytes)

    suspend fun writeLabelBytesToConfiguredPrinter(labelBytes: ByteArray): Boolean {
        writeLabelBytes?.let { customWriter -> return customWriter(labelBytes) }
        return writeLabelBytesToConfiguredBluetoothPrinter(labelBytes)
    }
}

fun installReceiptPlatformAndroid(context: Context) {
    val appContext = context.applicationContext
    openExternalUrlPlatformAction = { rawUrl ->
        withContext(Dispatchers.Main) {
            runCatching {
                val uri = Uri.parse(rawUrl.trim())
                require(uri.scheme?.lowercase() in setOf("http", "https", "geo")) { "Unsupported link" }
                appContext.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                ReceiptPlatformActionResult(true, "Opened map")
            }.getOrElse { throwable ->
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not open the link")
            }
        }
    }
    val receiptPrinterPreferences = appContext.getSharedPreferences("aita_receipt_printer", Context.MODE_PRIVATE)
    val labelPrinterPreferences = appContext.getSharedPreferences("aita_label_printer", Context.MODE_PRIVATE)
    ReceiptPlatformAndroidBridge.configureBluetoothPrinter(receiptPrinterPreferences.getString("bluetooth_printer_mac_address", null))
    LabelPrinterAndroidBridge.configureBluetoothLabelPrinter(labelPrinterPreferences.getString("bluetooth_label_printer_mac_address", null))
    LabelPrinterAndroidBridge.configureProtocol(labelPrinterPreferences.getString("label_printer_protocol", LABEL_PRINTER_PROTOCOL_AUTO))
    configuredReceiptPrinterDeviceIdState.value = ReceiptPlatformAndroidBridge.bluetoothPrinterMacAddress
    configuredLabelPrinterProtocolState.value = LabelPrinterAndroidBridge.labelPrinterProtocol
    val activePrintWebViews = mutableListOf<WebView>()


    fun createCachedPdfUri(fileName: String, pdfBytes: ByteArray): Uri {
        val safeFileName = fileName.ifBlank { "receipt.pdf" }
        val dir = File(appContext.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, safeFileName).apply { writeBytes(pdfBytes) }

        return runCatching {
            FileProvider.getUriForFile(appContext, appContext.packageName + ".fileprovider", file)
        }.getOrElse {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw it

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeFileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw it
            appContext.contentResolver.openOutputStream(uri)?.use { output -> output.write(pdfBytes) }
                ?: throw it
            val doneValues = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            appContext.contentResolver.update(uri, doneValues, null, null)
            uri
        }
    }

    fun savePdfToDownloadsOrPrivateDocuments(fileName: String, pdfBytes: ByteArray): ReceiptPlatformActionResult {
        return runCatching {
            val safeFileName = fileName.ifBlank { "receipt.pdf" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeFileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create PDF file")

                appContext.contentResolver.openOutputStream(uri)?.use { it.write(pdfBytes) }
                    ?: error("Could not open PDF output stream")

                val doneValues = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                appContext.contentResolver.update(uri, doneValues, null, null)

                ReceiptPlatformActionResult(true, "Saved to Downloads")
            } else {
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = if (publicDir.exists() || publicDir.mkdirs()) {
                    publicDir
                } else {
                    appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: appContext.filesDir
                }
                val file = File(targetDir, safeFileName)
                file.writeBytes(pdfBytes)
                ReceiptPlatformActionResult(true, "Saved to ${file.absolutePath}")
            }
        }.getOrElse { throwable ->
            val fallbackDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: appContext.filesDir
            runCatching {
                val file = File(fallbackDir, fileName.ifBlank { "receipt.pdf" })
                file.writeBytes(pdfBytes)
                ReceiptPlatformActionResult(true, "Saved to ${file.absolutePath}")
            }.getOrElse {
                ReceiptPlatformActionResult(false, throwable.message ?: it.message ?: "Could not save PDF")
            }
        }
    }



    fun printAttributesForDocument(fileName: String): PrintAttributes {
        val safeName = fileName.lowercase(Locale.ROOT)
        val mediaSize = when {
            safeName.contains("sheet") || safeName.contains("a4") -> PrintAttributes.MediaSize.ISO_A4
            safeName.contains("label") || safeName.contains("tag") -> PrintAttributes.MediaSize("AITA_LABEL_58_40", "AITA label 58 x 40 mm", 2283, 1575)
            else -> PrintAttributes.MediaSize.ISO_A4
        }
        return PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
    }

    fun printPdfWithSystemPaperPrinter(fileName: String, pdfBytes: ByteArray): ReceiptPlatformActionResult {
        return runCatching {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: error("Android print service is not available")
            val safeFileName = fileName.ifBlank { "aita-document.pdf" }
            printManager.print(
                "AITA ${safeFileName.removeSuffix(".pdf")}",
                ReceiptPdfPrintDocumentAdapter(safeFileName, pdfBytes),
                printAttributesForDocument(safeFileName)
            )
            ReceiptPlatformActionResult(true, "Opening system print dialog")
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not open system print dialog")
        }
    }

    fun printHtmlWithSystemPrinter(fileName: String, html: String): ReceiptPlatformActionResult {
        return runCatching {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: error("Android print service is not available")
            val safeFileName = fileName.ifBlank { "aita-document.html" }
            val printTitle = "AITA ${safeFileName.removeSuffix(".html")}"
            val webView = WebView(context)
            activePrintWebViews += webView
            while (activePrintWebViews.size > 6) {
                val oldView = activePrintWebViews.removeAt(0)
                runCatching { oldView.destroy() }
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val adapter = webView.createPrintDocumentAdapter(printTitle)
                    printManager.print(printTitle, adapter, printAttributesForDocument(safeFileName))
                    webView.postDelayed({
                        activePrintWebViews.remove(webView)
                        runCatching { webView.destroy() }
                    }, 30_000L)
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            ReceiptPlatformActionResult(true, "Opening system print dialog")
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not open system print dialog")
        }
    }

    fun likelyReceiptPrinterName(name: String): Boolean {
        val clean = name.lowercase()
        return listOf("aokia", "ak-3558", "ak3558", "xp-58", "xp58", "pos", "esc", "receipt", "printer", "thermal", "xprinter", "gprinter", "rongta", "sunmi", "mtp", "rp", "xp-")
            .any { clean.contains(it) }
    }

    @SuppressLint("MissingPermission")
    fun listBluetoothReceiptPrinterDevices(): List<PlatformReceiptPrinterDataModel> {
        val configuredAddress = ReceiptPlatformAndroidBridge.bluetoothPrinterMacAddress?.trim().orEmpty()
        val discovered = BluetoothPrinterTransport.pairedDevices()
                .mapNotNull { device ->
                    val address = runCatching { device.address }.getOrNull()?.trim().orEmpty()
                    val name = runCatching { device.name }.getOrNull()?.trim().orEmpty()
                    val id = address
                    if (!BluetoothAdapter.checkBluetoothAddress(id)) return@mapNotNull null
                    val probable = likelyReceiptPrinterName(name)
                    PlatformReceiptPrinterDataModel(
                        id = id,
                        name = name.ifBlank { address.ifBlank { "Bluetooth device" } },
                        subtitle = listOfNotNull(
                            address.takeIf { it.isNotBlank() },
                            if (probable) "Likely ESC/POS receipt printer" else "Paired Bluetooth device"
                        ).joinToString(" • "),
                        configured = address.equals(configuredAddress, ignoreCase = true) || id.equals(configuredAddress, ignoreCase = true),
                        available = true
                    )
                }


        val withSavedConfiguredPrinter = if (configuredAddress.isNotBlank() && discovered.none { it.configured || it.id.equals(configuredAddress, ignoreCase = true) }) {
            discovered + PlatformReceiptPrinterDataModel(
                id = configuredAddress,
                name = "Saved receipt printer",
                subtitle = "Saved Bluetooth printer; connect or pair it in system settings if unavailable",
                configured = true,
                available = false
            )
        } else {
            discovered
        }

        return withSavedConfiguredPrinter
            .distinctBy { it.id.lowercase() }
            .sortedWith(
                compareByDescending<PlatformReceiptPrinterDataModel> { it.configured }
                    .thenByDescending { likelyReceiptPrinterName(it.name) }
                    .thenBy { it.name.lowercase() }
            )
    }


    fun likelyLabelPrinterName(name: String): Boolean {
        val clean = name.lowercase()
        return listOf("label", "sticker", "tspl", "tsc", "zebra", "zpl", "cpcl", "godex", "gainscha", "xprinter", "xp-", "bixolon", "printer")
            .any { clean.contains(it) }
    }

    @SuppressLint("MissingPermission")
    fun listBluetoothLabelPrinterDevices(): List<PlatformLabelPrinterDataModel> {
        val configuredAddress = LabelPrinterAndroidBridge.bluetoothLabelPrinterMacAddress?.trim().orEmpty()
        val discovered = runCatching {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@runCatching emptyList()
            adapter.bondedDevices
                .orEmpty()
                .mapNotNull { device ->
                    val address = runCatching { device.address }.getOrNull()?.trim().orEmpty()
                    val name = runCatching { device.name }.getOrNull()?.trim().orEmpty()
                    val id = address.ifBlank { name }
                    if (id.isBlank()) return@mapNotNull null
                    val probable = likelyLabelPrinterName(name)
                    PlatformLabelPrinterDataModel(
                        id = id,
                        name = name.ifBlank { address.ifBlank { "Bluetooth label printer" } },
                        subtitle = listOfNotNull(
                            address.takeIf { it.isNotBlank() },
                            if (probable) "Likely TSPL/ZPL/CPCL sticky label printer" else "Paired Bluetooth device"
                        ).joinToString(" • "),
                        configured = address.equals(configuredAddress, ignoreCase = true) || id.equals(configuredAddress, ignoreCase = true),
                        available = true
                    )
                }
        }.getOrElse { emptyList() }

        val withSavedConfiguredPrinter = if (configuredAddress.isNotBlank() && discovered.none { it.configured || it.id.equals(configuredAddress, ignoreCase = true) }) {
            discovered + PlatformLabelPrinterDataModel(
                id = configuredAddress,
                name = "Saved label printer",
                subtitle = "Saved Bluetooth label printer; connect or pair it in system settings if unavailable",
                configured = true,
                available = false
            )
        } else {
            discovered
        }

        return withSavedConfiguredPrinter
            .distinctBy { it.id.lowercase() }
            .sortedWith(
                compareByDescending<PlatformLabelPrinterDataModel> { it.configured }
                    .thenByDescending { likelyLabelPrinterName(it.name) }
                    .thenBy { it.name.lowercase() }
            )
    }

    saveReceiptPdfFile = { fileName, pdfBytes ->
        withContext(Dispatchers.IO) {
            savePdfToDownloadsOrPrivateDocuments(fileName, pdfBytes)
        }
    }

    shareReceiptPdfFile = { fileName, pdfBytes, whatsappOnly ->
        withContext(Dispatchers.Main) {
            runCatching {
                val uri = withContext(Dispatchers.IO) { createCachedPdfUri(fileName, pdfBytes) }
                val pdfShareLabel = if (fileName.contains("report", true) || fileName.contains("analytics", true)) "AITA analytics report" else "AITA receipt"
                val pdfShareTitle = if (fileName.contains("report", true) || fileName.contains("analytics", true)) "Share report" else "Share receipt"
                val baseIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, pdfShareLabel)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (whatsappOnly) setPackage("com.whatsapp")
                }

                try {
                    if (whatsappOnly) {
                        context.startActivity(baseIntent)
                    } else {
                        context.startActivity(Intent.createChooser(baseIntent, pdfShareTitle).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                    ReceiptPlatformActionResult(true, if (whatsappOnly) "Opening WhatsApp" else "Opening share sheet")
                } catch (notFound: ActivityNotFoundException) {
                    if (!whatsappOnly) throw notFound
                    val chooserIntent = Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, pdfShareLabel)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        pdfShareTitle
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(chooserIntent)
                    ReceiptPlatformActionResult(true, "WhatsApp is not installed; opening share sheet")
                }
            }.getOrElse { throwable ->
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not share PDF")
            }
        }
    }

    printPdfDocumentPlatformAction = { fileName, pdfBytes ->
        withContext(Dispatchers.Main) {
            printPdfWithSystemPaperPrinter(fileName, pdfBytes)
        }
    }

    printHtmlDocumentPlatformAction = { fileName, html ->
        withContext(Dispatchers.Main) {
            printHtmlWithSystemPrinter(fileName, html)
        }
    }

    preparePlatformReceiptPrinterAction = {
        try {
            BluetoothPrinterTransport.requestConnectPermission()
            ReceiptPlatformActionResult(true)
        } catch (cancel: CancellationException) { throw cancel }
        catch (exception: Exception) { ReceiptPlatformActionResult(false, exception.message ?: "Bluetooth permission unavailable") }
    }

    listPlatformReceiptPrinterDevicesAction = {
        withContext(Dispatchers.IO) {
            listBluetoothReceiptPrinterDevices()
        }
    }

    configurePlatformReceiptPrinterDeviceAction = { deviceId ->
        val cleanAddress = deviceId?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        require(cleanAddress == null || BluetoothAdapter.checkBluetoothAddress(cleanAddress)) { "Select a paired Bluetooth printer" }
        if (cleanAddress != null) BluetoothPrinterTransport.requestConnectPermission()
        // Commit durable selection before publishing it to the process.
        val edit = receiptPrinterPreferences.edit()
        if (cleanAddress == null) edit.remove("bluetooth_printer_mac_address") else edit.putString("bluetooth_printer_mac_address", cleanAddress)
        check(edit.commit()) { "Could not save receipt printer selection" }
        ReceiptPlatformAndroidBridge.configureBluetoothPrinter(cleanAddress)
        ReceiptPlatformActionResult(
            true,
            if (deviceId.isNullOrBlank()) "Receipt printer cleared" else "Receipt printer selected"
        )
    }


    listPlatformLabelPrinterDevicesAction = {
        withContext(Dispatchers.IO) {
            listBluetoothLabelPrinterDevices()
        }
    }

    configurePlatformLabelPrinterDeviceAction = { deviceId ->
        LabelPrinterAndroidBridge.configureBluetoothLabelPrinter(deviceId)
        if (deviceId.isNullOrBlank()) {
            labelPrinterPreferences.edit().remove("bluetooth_label_printer_mac_address").apply()
        } else {
            labelPrinterPreferences.edit().putString("bluetooth_label_printer_mac_address", deviceId.trim()).apply()
        }
        ReceiptPlatformActionResult(
            true,
            if (deviceId.isNullOrBlank()) "Label printer cleared" else "Label printer selected"
        )
    }

    configurePlatformLabelPrinterProtocolAction = { protocol ->
        val normalized = normalizeLabelPrinterProtocol(protocol)
        LabelPrinterAndroidBridge.configureProtocol(normalized)
        labelPrinterPreferences.edit().putString("label_printer_protocol", normalized).apply()
        configuredLabelPrinterProtocolState.value = normalized
        ReceiptPlatformActionResult(true, "Label printer protocol selected")
    }

    printLabelPrinterBytes = { labelBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                if (LabelPrinterAndroidBridge.writeLabelBytesToConfiguredPrinter(labelBytes)) {
                    ReceiptPlatformActionResult(true, "Label sent to printer")
                } else {
                    ReceiptPlatformActionResult(false, "Android Bluetooth sticky label printer is not configured")
                }
            }.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not print sticky label")
            }
        }
    }

    printReceiptPlatformAction = { _, _, printerBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                if (ReceiptPlatformAndroidBridge.writeEscPosBytesToConfiguredPrinter(printerBytes)) {
                    ReceiptPlatformActionResult(true, "Receipt sent to printer")
                } else {
                    ReceiptPlatformActionResult(false, "Android Bluetooth ESC/POS receipt printer is not configured")
                }
            }.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                ReceiptPlatformActionResult(false, throwable.message ?: "Could not print receipt")
            }
        }
    }

    printReceiptEscPosBytes = { printerBytes ->
        withContext(Dispatchers.IO) {
            runCatching {
                if (ReceiptPlatformAndroidBridge.writeEscPosBytesToConfiguredPrinter(printerBytes)) {
                    ReceiptPlatformActionResult(true, "Receipt sent to printer")
                } else {
                    ReceiptPlatformActionResult(false, "Android Bluetooth ESC/POS receipt printer is not configured")
                }
            }.getOrElse {
                if (it is CancellationException) throw it
                ReceiptPlatformActionResult(false, it.message ?: "Could not print receipt")
            }
        }
    }
}

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


    private fun String?.permissionList(): List<String> =
        this
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    private suspend fun putPermissionList(key: String, permissions: List<String>) {
        val cleaned = permissions.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleaned.isEmpty()) deleteLocalKv(key) else putLocalKv(key, cleaned.joinToString("|"))
    }

    suspend fun getRequestedPermissions(): List<String> {
        return getLocalKv(KEY_REQUESTED_PERMISSIONS).permissionList()
    }

    suspend fun getRequestedPermissionsRepliedWithDontAskAgain(): List<String> {
        return getLocalKv(KEY_REQUESTED_PERMISSIONS_REPLIED_WITH_DONT_ASK_AGAIN).permissionList()
    }

    fun addRequestedPermission(permission: String) {
        viewModelScope.launch {
            putPermissionList(KEY_REQUESTED_PERMISSIONS, getRequestedPermissions() + permission)
        }
    }

    fun addRequestedPermissionRepliedWithDontAskAgain(permission: String) {
        viewModelScope.launch {
            putPermissionList(KEY_REQUESTED_PERMISSIONS_REPLIED_WITH_DONT_ASK_AGAIN, getRequestedPermissionsRepliedWithDontAskAgain() + permission)
        }
    }

    fun removeRequestedPermission(permission: String) {
        viewModelScope.launch {
            putPermissionList(KEY_REQUESTED_PERMISSIONS, getRequestedPermissions().filterNot { it == permission })
        }
    }

    fun removeRequestedPermissionRepliedWithDontAskAgain(permission: String) {
        viewModelScope.launch {
            putPermissionList(KEY_REQUESTED_PERMISSIONS_REPLIED_WITH_DONT_ASK_AGAIN, getRequestedPermissionsRepliedWithDontAskAgain().filterNot { it == permission })
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
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
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

private suspend fun getOrCreateAndroidInstallationId(): String {
    val existing = getEncryptedValue("key_installation_id")?.takeIf { it.isNotBlank() }
    if (existing != null) return existing

    val fresh = UUID.randomUUID().toString()
    setEncryptedValue("key_installation_id", fresh)
    return fresh
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

    private var pendingPrinterPermission: CompletableDeferred<Boolean>? = null
    private val printerPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPrinterPermission?.complete(granted)
        pendingPrinterPermission = null
    }

    suspend fun awaitPrinterBluetoothPermission(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (BluetoothPrinterTransport.hasConnectPermission()) return@withContext true
        if (isFinishing || isDestroyed) return@withContext false
        val pending = pendingPrinterPermission ?: CompletableDeferred<Boolean>().also {
            pendingPrinterPermission = it
            try { printerPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
            catch (exception: Exception) { pendingPrinterPermission = null; it.complete(false) }
        }
        pending.await()
    }

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    var permissionGrantedResultAction: ((String) -> Unit)? = null
    var permissionDeniedResultAction: ((String) -> Unit)? = null

    private var currentSAFReceiptWriteLauncher: ActivityResultLauncher<Intent>? = null
    private var currentSAFReceiptWriteResultAction: ((Uri?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        instance = this
        updatePhoneOrientationPolicy()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        installAndroidSoftKeyboardHider(this)
        installReceiptPlatformAndroid(this)
        installAndroidVectorDrawableRenderer()
        installAndroidCameraBarcodeScanner()
        installAndroidVoiceInput(this)

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

    suspend fun permissionState(permission: String): PlatformPermissionState {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.removeRequestedPermission(permission)
            viewModel.removeRequestedPermissionRepliedWithDontAskAgain(permission)
            return PlatformPermissionState.Granted
        }

        val requestedBefore = viewModel.getRequestedPermissions().contains(permission)
        val storedPermanentlyDenied = viewModel.getRequestedPermissionsRepliedWithDontAskAgain().contains(permission)
        val systemWantsRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission)

        return when {
            storedPermanentlyDenied && !systemWantsRationale -> PlatformPermissionState.PermanentlyDenied
            requestedBefore && !systemWantsRationale -> PlatformPermissionState.PermanentlyDenied
            systemWantsRationale -> PlatformPermissionState.Denied
            else -> PlatformPermissionState.NotDetermined
        }
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
        val permission = permissions.firstOrNull() ?: return

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            permissions.forEach { granted ->
                viewModel.removeRequestedPermission(granted)
                viewModel.removeRequestedPermissionRepliedWithDontAskAgain(granted)
                permissionGrantedResultAction?.invoke(granted)
            }
            return
        }

        if (permissionState(permission) == PlatformPermissionState.PermanentlyDenied) {
            openPlatformAppSettings?.invoke(androidPermissionKind(permission))
            permissionDeniedResultAction?.invoke(permission)
            return
        }

        this.permissionGrantedResultAction = { granted ->
            viewModel.removeRequestedPermission(granted)
            viewModel.removeRequestedPermissionRepliedWithDontAskAgain(granted)
            permissionGrantedResultAction?.invoke(granted)
        }

        this.permissionDeniedResultAction = { denied ->
            lifecycleScope.launch {
                val requestedBefore = viewModel.getRequestedPermissions().contains(denied)
                viewModel.addRequestedPermission(denied)
                if (requestedBefore && !ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, denied)) {
                    viewModel.addRequestedPermissionRepliedWithDontAskAgain(denied)
                }
                permissionDeniedResultAction?.invoke(denied)
            }
        }

        permissionLauncher.launch(permissions)
    }

    override fun onResume() {
        super.onResume()
        instance = this
        updatePhoneOrientationPolicy()
        installAndroidSoftKeyboardHider(this)
        installReceiptPlatformAndroid(this)
        installAndroidVectorDrawableRenderer()
        installAndroidCameraBarcodeScanner()
        installAndroidVoiceInput(this)
    }

    @Suppress("DEPRECATION")
    private fun updatePhoneOrientationPolicy(configuration: Configuration = resources.configuration) {
        val smallestDisplayWidthDp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            (minOf(bounds.width(), bounds.height()) / resources.displayMetrics.density).toInt()
        } else {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            (minOf(metrics.widthPixels, metrics.heightPixels) / metrics.density).toInt()
        }
        val mode = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
            ?: (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK)
        val lockPortrait = shouldLockPhoneToPortrait(
            displaySmallestWidthDp = smallestDisplayWidthDp,
            configurationSmallestWidthDp = configuration.smallestScreenWidthDp,
            normalUiMode = mode == Configuration.UI_MODE_TYPE_NORMAL,
            multiWindow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode,
            pictureInPicture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode,
            largeScreenConfiguration = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        )
        val orientation = if (lockPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (requestedOrientation != orientation) requestedOrientation = orientation
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePhoneOrientationPolicy(newConfig)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        updatePhoneOrientationPolicy(newConfig)
    }

    override fun onStop() {
        runCatching { stopPlatformVoiceInput?.invoke() }
        super.onStop()
    }

    override fun onDestroy() {
        pendingPrinterPermission?.complete(false)
        pendingPrinterPermission = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private var instance: MainActivity? = null

        fun get(): MainActivity = instance ?: error("MainActivity is not available")

        fun getOrNull(): MainActivity? = instance

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

        getPersistentUiDraftValue = { key ->
            getEncryptedValue("key_ui_draft_" + key.hashCode().toString())
        }
        setPersistentUiDraftValue = { key, value ->
            setEncryptedValue("key_ui_draft_" + key.hashCode().toString(), value)
        }

        setClipboardText = { text ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AITA", text))
        }

        openPlatformAppSettings = { _ ->
            openAndroidApplicationSettingsResult()
        }

        openSystemDevicesSettings = {
            runCatching {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                ReceiptPlatformActionResult(true, "Device settings opened")
            }.getOrElse {
                ReceiptPlatformActionResult(false, it.message ?: "Could not open device settings")
            }
        }

        getClientDeviceInfo = {
            runBlocking(Dispatchers.IO) {
                val packageInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
                val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                val model = Build.MODEL.orEmpty()
                val deviceTitle = listOf(manufacturer, model)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "Android device" }

                ClientDeviceInfoDataModel(
                    installationId = getOrCreateAndroidInstallationId(),
                    deviceName = deviceTitle,
                    platformName = "Android",
                    osName = "Android ${Build.VERSION.RELEASE ?: ""} (SDK ${Build.VERSION.SDK_INT})",
                    appName = "AITA",
                    appVersion = packageInfo?.versionName.orEmpty(),
                    localeLanguage = getSystemLocaleLanguage()
                )
            }
        }

        init()
    }

    companion object {
        private lateinit var instance: AITA

        fun get() = instance
    }
}
