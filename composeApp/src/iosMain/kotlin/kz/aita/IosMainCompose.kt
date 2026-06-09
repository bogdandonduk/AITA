// THIS IS IosMainCompose.kt - place in composeApp/src/iosMain/kotlin/kz/aita/IosMainCompose.kt
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package kz.aita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVAudioEngine
import platform.AVFoundation.AVAudioSession
import platform.AVFoundation.AVAudioSessionCategoryRecord
import platform.AVFoundation.AVAudioSessionModeMeasurement
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.AVMetadataObjectTypeUPCECode
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.create
import platform.QuartzCore.CALayer
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatusAuthorized
import platform.Speech.SFSpeechRecognizerAuthorizationStatusNotDetermined
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDevice
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIMarkupTextPrintFormatter
import platform.UIKit.UIPasteboard
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT

private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = this@toNSData.refTo(0), length = this@toNSData.size.convert())
}

private fun documentsPath(fileName: String): String {
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String
        ?: platform.Foundation.NSTemporaryDirectory()
    return documents.trimEnd('/') + "/" + fileName.ifBlank { "receipt.pdf" }
}

private class IosBarcodeDelegate(
    private val onBarcodeDetected: (String) -> Unit
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        val code = didOutputMetadataObjects
            .asSequence()
            .mapNotNull { it as? AVMetadataMachineReadableCodeObject }
            .mapNotNull { it.stringValue }
            .firstOrNull { it.isNotBlank() }
            ?: return

        onBarcodeDetected(code)
    }
}

private class IosBarcodeScannerController(
    private val onBarcodeDetected: (String) -> Unit
) {
    private val session = AVCaptureSession()
    private val output = AVCaptureMetadataOutput()
    private val delegate = IosBarcodeDelegate(onBarcodeDetected)
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var currentDevice: AVCaptureDevice? = null
    private var currentPosition = AVCaptureDevicePositionBack

    fun attach(view: UIView) {
        session.sessionPreset = AVCaptureSessionPresetHigh
        if (previewLayer == null) {
            previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
                videoGravity = AVLayerVideoGravityResizeAspectFill
            }
        }
        previewLayer?.removeFromSuperlayer()
        previewLayer?.frame = view.bounds
        previewLayer?.let { view.layer.addSublayer(it) }
        configure(currentPosition)
        start()
    }

    fun layout(view: UIView) {
        previewLayer?.frame = view.bounds
    }

    fun configure(position: Long) {
        currentPosition = position
        memScoped {
            val device = AVCaptureDevice.defaultDeviceWithDeviceType(
                deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
                mediaType = AVMediaTypeVideo,
                position = position
            ) ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return@memScoped

            val error = alloc<ObjCObjectVar<NSError?>>()
            val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error.ptr) ?: return@memScoped

            session.beginConfiguration()
            session.inputs.forEach { session.removeInput(it as AVCaptureInput) }
            session.outputs.forEach { session.removeOutput(it as AVCaptureOutput) }

            if (session.canAddInput(input)) session.addInput(input)
            if (session.canAddOutput(output)) session.addOutput(output)
            output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
            output.metadataObjectTypes = listOf(
                AVMetadataObjectTypeEAN13Code,
                AVMetadataObjectTypeEAN8Code,
                AVMetadataObjectTypeUPCECode,
                AVMetadataObjectTypeCode128Code,
                AVMetadataObjectTypeQRCode
            )
            session.commitConfiguration()
            currentDevice = device
        }
    }

    fun start() {
        if (!session.running) {
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.convert(), 0u)) {
                session.startRunning()
            }
        }
    }

    fun stop() {
        if (session.running) session.stopRunning()
    }

    fun switchCamera(front: Boolean) {
        configure(if (front) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack)
    }

    fun setTorch(enabled: Boolean) {
        val device = currentDevice ?: return
        if (!device.hasTorch) return
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (device.lockForConfiguration(error.ptr)) {
                device.torchMode = if (enabled) AVCaptureTorchModeOn else AVCaptureTorchModeOff
                device.unlockForConfiguration()
            }
        }
    }
}

private class IosSpeechInputController {
    private var audioEngine: AVAudioEngine? = null
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null

    fun stop() {
        runCatching { audioEngine?.stop() }
        runCatching { audioEngine?.inputNode?.removeTapOnBus(0u) }
        runCatching { recognitionRequest?.endAudio() }
        runCatching { recognitionTask?.cancel() }
        audioEngine = null
        recognitionRequest = null
        recognitionTask = null
    }

    fun start(localeIdentifier: String, callbacks: VoiceInputCallbacks) {
        stop()

        val recognizer = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = localeIdentifier))
            ?: SFSpeechRecognizer()
        if (recognizer == null || recognizer.available != true) {
            callbacks.onError("Speech recognition is not available on this iOS device")
            callbacks.onFinished()
            return
        }

        val request = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
        }
        val engine = AVAudioEngine()
        val inputNode = engine.inputNode
        val format = inputNode.outputFormatForBus(0u)

        recognitionRequest = request
        audioEngine = engine

        recognitionTask = recognizer.recognitionTaskWithRequest(request) { result, error ->
            val text = result?.bestTranscription?.formattedString.orEmpty()
            val isFinalResult = (result?.valueForKey("final") as? Boolean) == true
            if (text.isNotBlank()) {
                if (isFinalResult) callbacks.onFinalText(text) else callbacks.onPartialText(text)
            }
            if (error != null || isFinalResult) {
                if (error != null && text.isBlank()) callbacks.onError(error.localizedDescription)
                callbacks.onFinished()
                stop()
            }
        }

        inputNode.installTapOnBus(0u, bufferSize = 1024u, format = format) { buffer, _ ->
            if (buffer != null) {
                request.appendAudioPCMBuffer(buffer)
                callbacks.onAmplitude(0.55f)
            }
        }

        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val session = AVAudioSession.sharedInstance()
            runCatching { session.setCategory(AVAudioSessionCategoryRecord, mode = AVAudioSessionModeMeasurement, options = 0u, error = error.ptr) }
            runCatching { session.setActive(true, error = error.ptr) }
            engine.prepare()
            if (!engine.startAndReturnError(error.ptr)) {
                callbacks.onError(error.value?.localizedDescription ?: "Could not start microphone")
                callbacks.onFinished()
                stop()
            } else {
                callbacks.onAmplitude(0.24f)
            }
        }
    }
}

private var iosSpeechInputController: IosSpeechInputController? = null

private fun iosSpeechLocaleIdentifier(): String {
    return when (appLanguageState.value.lowercase()) {
        "ru" -> "ru-RU"
        "kk" -> "kk-KZ"
        "en" -> "en-US"
        else -> getSystemLocaleLanguage().let { language ->
            when (language.lowercase()) {
                "ru" -> "ru-RU"
                "kk" -> "kk-KZ"
                else -> "en-US"
            }
        }
    }
}

private fun openIosApplicationSettingsNow(): ReceiptPlatformActionResult {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
    return if (url != null) {
        UIApplication.sharedApplication.openURL(url)
        ReceiptPlatformActionResult(true, "Settings opened")
    } else {
        ReceiptPlatformActionResult(false, "Could not open settings")
    }
}

private fun iosSpeechRecognitionPermissionState(): PlatformPermissionState = when (SFSpeechRecognizer.authorizationStatus()) {
    SFSpeechRecognizerAuthorizationStatusAuthorized -> PlatformPermissionState.Granted
    SFSpeechRecognizerAuthorizationStatusNotDetermined -> PlatformPermissionState.NotDetermined
    else -> PlatformPermissionState.PermanentlyDenied
}

private fun iosMicrophonePermissionState(): PlatformPermissionState = when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)) {
    AVAuthorizationStatusAuthorized -> PlatformPermissionState.Granted
    AVAuthorizationStatusNotDetermined -> PlatformPermissionState.NotDetermined
    else -> PlatformPermissionState.PermanentlyDenied
}

private fun iosVoicePermissionState(): PlatformPermissionState {
    val speech = iosSpeechRecognitionPermissionState()
    val microphone = iosMicrophonePermissionState()
    return when {
        speech == PlatformPermissionState.PermanentlyDenied || microphone == PlatformPermissionState.PermanentlyDenied -> PlatformPermissionState.PermanentlyDenied
        speech == PlatformPermissionState.NotDetermined || microphone == PlatformPermissionState.NotDetermined -> PlatformPermissionState.NotDetermined
        speech == PlatformPermissionState.Granted && microphone == PlatformPermissionState.Granted -> PlatformPermissionState.Granted
        else -> PlatformPermissionState.Unavailable
    }
}

private fun installIosVoiceInput() {
    getVoiceInputPermissionState = { iosVoicePermissionState() }

    stopPlatformVoiceInput = {
        iosSpeechInputController?.stop()
        iosSpeechInputController = null
    }

    startPlatformVoiceInput = { texts, callbacks ->
        val startRecorder = {
            val controller = IosSpeechInputController()
            iosSpeechInputController = controller
            val requestedLocale = texts.primaryLanguageTag.takeIf { it.isNotBlank() } ?: texts.languageTags.firstOrNull().orEmpty().ifBlank { iosSpeechLocaleIdentifier() }
            callbacks.onDetectedLanguage(requestedLocale)
            controller.start(requestedLocale, callbacks)
        }

        val requestMicrophoneThenStart = {
            when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)) {
                AVAuthorizationStatusAuthorized -> startRecorder()
                AVAuthorizationStatusNotDetermined -> {
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { micGranted ->
                        dispatch_async(dispatch_get_main_queue()) {
                            if (micGranted) {
                                startRecorder()
                            } else {
                                callbacks.onDenied()
                                callbacks.onFinished()
                            }
                        }
                    }
                }
                else -> {
                    openIosApplicationSettingsNow()
                    callbacks.onDenied()
                    callbacks.onFinished()
                }
            }
        }

        when (SFSpeechRecognizer.authorizationStatus()) {
            SFSpeechRecognizerAuthorizationStatusAuthorized -> requestMicrophoneThenStart()
            SFSpeechRecognizerAuthorizationStatusNotDetermined -> {
                SFSpeechRecognizer.requestAuthorization { status ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (status == SFSpeechRecognizerAuthorizationStatusAuthorized) {
                            requestMicrophoneThenStart()
                        } else {
                            callbacks.onDenied()
                            callbacks.onFinished()
                        }
                    }
                }
            }
            else -> {
                openIosApplicationSettingsNow()
                callbacks.onDenied()
                callbacks.onFinished()
            }
        }
    }
}

private fun installIosComposePlatformBridges() {
    setClipboardText = { text -> UIPasteboard.generalPasteboard.string = text }

    openPlatformAppSettings = { _ ->
        openIosApplicationSettingsNow()
    }

    openSystemDevicesSettings = {
        openPlatformAppSettings?.invoke(PlatformPermissionKind.AppSettings)
            ?: ReceiptPlatformActionResult(false, "Could not open settings")
    }

    saveReceiptPdfFile = { fileName, pdfBytes ->
        withContext(Dispatchers.ourIo) {
            runCatching {
                val path = documentsPath(fileName)
                pdfBytes.toNSData().writeToFile(path, atomically = true)
                ReceiptPlatformActionResult(true, "Saved to $path")
            }.getOrElse { ReceiptPlatformActionResult(false, it.message ?: "Could not save PDF") }
        }
    }

    shareReceiptPdfFile = { fileName, pdfBytes, _ ->
        withContext(Dispatchers.ourIo) {
            runCatching {
                val path = documentsPath(fileName)
                pdfBytes.toNSData().writeToFile(path, atomically = true)
                ReceiptPlatformActionResult(true, "PDF saved to $path")
            }.getOrElse { ReceiptPlatformActionResult(false, it.message ?: "Could not share PDF") }
        }
    }

    printReceiptPlatformAction = { _, pdfBytes, _ ->
        val printController = UIPrintInteractionController.sharedPrintController()
        printController.printingItem = pdfBytes.toNSData()
        printController.presentAnimated(true, completionHandler = null)
        ReceiptPlatformActionResult(true, "Opening print dialog")
    }

    printPdfDocumentPlatformAction = { _, pdfBytes ->
        val printController = UIPrintInteractionController.sharedPrintController()
        printController.printingItem = pdfBytes.toNSData()
        printController.presentAnimated(true, completionHandler = null)
        ReceiptPlatformActionResult(true, "Opening print dialog")
    }

    printHtmlDocumentPlatformAction = { _, html ->
        val printController = UIPrintInteractionController.sharedPrintController()
        printController.printFormatter = UIMarkupTextPrintFormatter(markupText = html)
        printController.presentAnimated(true, completionHandler = null)
        ReceiptPlatformActionResult(true, "Opening print dialog")
    }

    getCameraScannerPermissionState = {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> PlatformPermissionState.Granted
            AVAuthorizationStatusNotDetermined -> PlatformPermissionState.NotDetermined
            else -> PlatformPermissionState.PermanentlyDenied
        }
    }

    requestCameraScannerPermission = { _, onGranted, onDenied ->
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> onGranted()
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (granted) onGranted() else onDenied()
                    }
                }
            }
            else -> {
                openIosApplicationSettingsNow()
                onDenied()
            }
        }
    }

    barcodeCameraScannerContent = { modifier, onBarcodeDetected, onClose ->
        IosBarcodeCameraScannerPane(
            modifier = modifier,
            onBarcodeDetected = onBarcodeDetected,
            onClose = onClose
        )
    }
}

@Composable
private fun AppConfiguration.IosBarcodeCameraScannerPane(
    modifier: Modifier,
    onBarcodeDetected: (String) -> Unit,
    onClose: () -> Unit
) {
    var frontCamera by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf(localizedStringResource(986, "Aim the camera at a barcode. The same barcode is ignored for 3 seconds to avoid accidental repeats.")) }
    val controller = remember {
        IosBarcodeScannerController { code ->
            statusText = code
            onBarcodeDetected(code)
        }
    }

    LaunchedEffect(frontCamera) { controller.switchCamera(frontCamera) }
    LaunchedEffect(torchOn) { controller.setTorch(torchOn) }
    DisposableEffect(Unit) { onDispose { controller.stop() } }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(stateValues.cornerRadius))
            .border(stateValues.unfocusedBorderWidth, stateValues.PlaceholderTextColor, RoundedCornerShape(stateValues.cornerRadius))
            .background(Color.Black)
    ) {
        UIKitView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                UIView().also { controller.attach(it) }
            },
            update = { view -> controller.layout(view) }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { frontCamera = !frontCamera },
                    colors = ButtonDefaults.buttonColors(containerColor = stateValues.BackgroundColor.copy(alpha = 0.86f))
                ) { Text(localizedStringResource(987, "Switch camera"), color = stateValues.TextColor, fontSize = stateValues.smallTextSize) }

                Spacer(modifier = Modifier.size(6.dp))

                Button(
                    onClick = { torchOn = !torchOn },
                    enabled = !frontCamera,
                    colors = ButtonDefaults.buttonColors(containerColor = stateValues.BackgroundColor.copy(alpha = 0.86f))
                ) { Text(localizedStringResource(988, "Torch") + if (torchOn) " ✓" else "", color = stateValues.TextColor, fontSize = stateValues.smallTextSize) }

                Spacer(modifier = Modifier.size(6.dp))

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = stateValues.BackgroundColor.copy(alpha = 0.86f))
                ) { Text(localizedStringResource(989, "Close scanner"), color = stateValues.TextColor, fontSize = stateValues.smallTextSize) }
            }

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stateValues.cornerRadius))
                    .background(stateValues.BackgroundColor.copy(alpha = 0.86f))
                    .padding(8.dp),
                text = statusText,
                color = stateValues.TextColor,
                fontSize = stateValues.smallTextSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun MainViewController(): UIViewController {
    installIosCommonPlatformBridges()
    installIosComposePlatformBridges()
    installIosVoiceInput()

    return ComposeUIViewController {
        AppConfiguration {
            MainScreen()
        }
    }
}
