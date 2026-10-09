package kz.aita

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private var activeToolbarCamera by mutableStateOf<Any?>(null)
internal class TransactionToolbarScannerState(val owner: Any = Any())

@Composable
internal fun AppConfiguration.rememberTransactionToolbarScanner(): TransactionToolbarScannerState? {
    val screen = stateValues.navigationScreensMain.lastOrNull()
    val active = screen is NavigationScreenModel.Transaction.MainSale || screen is NavigationScreenModel.Transaction.MainReturn || screen is NavigationScreenModel.Transaction.MainSupply
    if (!active || !platformSupportsCameraBarcodeScanner() || barcodeCameraScannerContent == null) return null
    val scanner = remember(stateValues.activeStoreId, screen) { TransactionToolbarScannerState() }
    DisposableEffect(scanner) { onDispose { if (activeToolbarCamera === scanner.owner) activeToolbarCamera = null } }
    return scanner
}

@Composable
internal fun AppConfiguration.TransactionToolbarScannerButton(scanner: TransactionToolbarScannerState) {
    val scope = rememberCoroutineScope()
    val text = cameraPermissionTexts(this)
    var permissionDialog by remember { mutableStateOf<PlatformPermissionState?>(null) }
    fun open() { activeToolbarCamera = scanner.owner }
    fun denied() { postInAppNotification(inventoryExperienceMessage("camera_failed"), NotificationType.Negative, transient = true) }
    fun request() { scope.launch { requestCameraScannerPermission?.invoke(text, ::open, ::denied) ?: open() } }
    permissionDialog?.let { state ->
        CameraScannerPermissionDialog(texts=text, permissionState=state,
            onDismiss={permissionDialog=null}, onConfirm={permissionDialog=null;
                if(state.needsSettingsText()) scope.launch {openPlatformAppSettings?.invoke(PlatformPermissionKind.Camera)} else request()})
    }
    actionButton(modifier=Modifier.size(40.dp),text="",iconPath=stateValues.drawablePathIconBarcodeCamScanner,
        iconRes=stateValues.drawableResIconBarcodeCamScanner.value,iconContentDescription=inventoryExperienceText("barcode_scan"),
        autoLoading=false,confirmationRequired=false,onClick={
            if(activeToolbarCamera===scanner.owner) activeToolbarCamera=null
            else scope.launch {
                when(val permission=getCameraScannerPermissionState?.invoke()) {
                    PlatformPermissionState.Granted -> open()
                    else -> permissionDialog=permission ?: PlatformPermissionState.Denied
                }
            }
        })
}

@Composable
internal fun AppConfiguration.TransactionToolbarScannerPreview(scanner: TransactionToolbarScannerState) {
    val context=rememberTransactionContext()
    val cart by getCartState(context.transactionTypeIndex,context.clientId).collectAsState()
    InlineBarcodeCameraScanner(visible=activeToolbarCamera===scanner.owner,onClose={activeToolbarCamera=null},onBarcodeDetected={ raw ->
        if("${context.transactionTypeIndex}:${context.clientId}" !in cartCheckoutsState.value && !transactionBarcodeModalOpen())
            tryHandleTransactionBarcodeInput(raw+"\n",context.transactionTypeIndex,context.clientId,cart)
    })
}
