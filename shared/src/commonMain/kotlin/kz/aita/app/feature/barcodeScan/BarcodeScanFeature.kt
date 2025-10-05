//package kz.aita.app.feature.barcodeScan
//
//
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.SupervisorJob
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.launch
//import kz.aita.core.io
//import kz.aita.model.service.GenericLocalService
//import kotlin.coroutines.CoroutineContext
//
//abstract class BarcodeScanFeature(
//  private val genericLocalService: GenericLocalService
//): CoroutineScope {
//
//  protected val _lastScannedBarcode = MutableStateFlow("")
//  val lastScannedBarcode = _lastScannedBarcode.asStateFlow()
//
//  protected var scannerInitJob: Job? = null
//  protected var barcodeScanningJob: Job? = null
//
//  companion object {
//    private val _scannerConnectedState = MutableStateFlow(false)
//    val scannerConnectedState = _scannerConnectedState.asStateFlow()
//
//    private val _bluetoothEnabled = MutableStateFlow(true)
//    val bluetoothEnabled = _bluetoothEnabled.asStateFlow()
//
//    var enableBluetoothOffered = false
//
//    val KEY_SELECTED_SCANNER_ADDRESS = "key_selectedScannerAddress"
//
//    private val _selectedScannerAddress =
//      MutableStateFlow<String?>(null)
//    val selectedScannerAddress = _selectedScannerAddress.asStateFlow()
//  }
//
//  protected suspend fun setScannerConnectedState(value: Boolean) {
//    _scannerConnectedState.emit(value)
//  }
//
//  protected suspend fun setBluetoothEnabled(value: Boolean) {
//    _bluetoothEnabled.emit(value)
//  }
//
//  fun setSelectedScannerAddress(address: String) {
//    launch(Dispatchers.io) {
//      genericLocalService.put(KEY_SELECTED_SCANNER_ADDRESS, address)
//
//      _selectedScannerAddress.emit(address)
//    }
//  }
//
//  suspend fun getSelectedScannerAddress(): String? {
//    return genericLocalService.get(KEY_SELECTED_SCANNER_ADDRESS)
//  }
//
//  open suspend fun initScanner(address: String) {
//    _scannerConnectedState.emit(false)
//  }
//
//  abstract suspend fun startBarcodeScanning()
//
//  override val coroutineContext: CoroutineContext = SupervisorJob()
//}
//
//expect val barcodeScanFeature: BarcodeScanFeature