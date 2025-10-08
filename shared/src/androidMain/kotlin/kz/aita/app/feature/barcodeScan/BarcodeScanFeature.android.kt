//package kz.aita.app.feature.barcodeScan
//
//import android.Manifest
//import android.bluetooth.BluetoothAdapter
//import android.bluetooth.BluetoothDevice
//import android.bluetooth.BluetoothManager
//import android.bluetooth.BluetoothSocket
//import android.content.Context.BLUETOOTH_SERVICE
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.os.Build
//import android.provider.Settings
//import androidx.core.app.ActivityCompat
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.isActive
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import kz.aita.AppConfiguration
//import kz.aita.app.system.AITA
//import kz.aita.app.system.ui.activity.MainActivity
//import kz.aita.compose.navigation.NavigationScreenModel
//import kz.aita.core.genericLocalService
//import kz.aita.core.io
//import kz.aita.core.userRepository
//import kz.aita.model.wrapper.DataState
//
//actual val barcodeScanFeature: BarcodeScanFeature by lazy {
//  object: BarcodeScanFeature(genericLocalService) {
//    private val bluetoothManager = AITA.get().run {
//      getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
//    }
//    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
//
//    private var scanner: BluetoothDevice? = null
//    private var scannerSocket: BluetoothSocket? = null
//
//    override suspend fun initScanner(address: String) {
//      super.initScanner(address)
//
//      scanner = bluetoothAdapter.getRemoteDevice(address)
//
//      scanner?.uuids?.forEach {
//        try {
//          val scannerSocket = scanner?.createRfcommSocketToServiceRecord(it.uuid)
//          scannerSocket?.connect()
//
//          scannerSocket?.outputStream?.write("".toByteArray())
//          scannerSocket?.outputStream?.flush()
//
//          this.scannerSocket = scannerSocket
//          setScannerConnectedState(true)
//        } catch (exception: Exception) {
//          exception.printStackTrace()
//
//          setScannerConnectedState(false)
//        }
//      }
//    }
//
//    override suspend fun startBarcodeScanning() {
//      scannerInitJob = launch(Dispatchers.io) {
//        while (true) {
//          if (
//            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
//                ActivityCompat.checkSelfPermission(
//                  AITA.get(),
//                  Manifest.permission.BLUETOOTH_CONNECT
//                ) != PackageManager.PERMISSION_GRANTED)
//          ) {
//            withContext(Dispatchers.Main) {
//              while (
//                AppConfiguration.stateValues.navigationScreensMain.last() !is NavigationScreenModel.UserAuth &&
//                userRepository.userAccountState.value.value !is DataState.Success
//              )
//                delay(1000)
//
//              MainActivity
//                .get()
//                .requestPermissions(
//                  arrayOf(
//                    Manifest.permission.BLUETOOTH_CONNECT,
//                    Manifest.permission.BLUETOOTH_SCAN
//                  ),
//                  AppConfiguration.stateValues.stringBluetoothPermissionRequired,
//                  AppConfiguration.stateValues.stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters,
//                  AppConfiguration.stateValues.stringBluetoothPermissionRequired,
//                  AppConfiguration.stateValues.stringForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrintersYouCanGrantItInAppSettings
//                )
//            }
//          } else {
//            if (bluetoothAdapter.isEnabled) {
//              if (!bluetoothEnabled.value)
//                setBluetoothEnabled(true)
//
//              MainActivity
//                .get()
//                .run {
//                  if (viewModel.currentDialogWidget.value.first != "" || viewModel.currentDialogWidget.value.second != "") {
//                    viewModel.postCurrentDialogWidget("", "", { }) { }
//                  }
//                }
//
//              try {
//                if (scanner == null || scannerSocket == null || scannerSocket?.isConnected == false) {
//                  bluetoothAdapter
//                    .bondedDevices
//                    .filter {
//                      it.name.contains("scan", true)
//                    }[0]?.run {
//                    initScanner(address)
////                  initScanner("DC:0D:30:FA:4E:5C")
//                  } ?: throw IllegalStateException("No barcode scanner found")
//                }
//              } catch (exception: Exception) {
//                exception.printStackTrace()
//                scanner = null
//                scannerSocket = null
//                setScannerConnectedState(false)
//              }
//            } else {
//              if (bluetoothEnabled.value)
//                setBluetoothEnabled(false)
//
//              setScannerConnectedState(false)
//
//              if (!enableBluetoothOffered) {
//                MainActivity
//                  .get()
//                  .run {
//                    if (viewModel.currentDialogWidget.value.first == "" && viewModel.currentDialogWidget.value.second == "") {
//                      viewModel.postCurrentDialogWidget(
//                        AppConfiguration.stateValues.stringBluetoothDisabled,
//                        AppConfiguration.stateValues.stringEnableForSearchAndConnectionToBluetoothBarcodeScannersAndReceiptPrinters,
//                        {
//                          enableBluetoothOffered = true
//                        }
//                      ) {
//                        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
//                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                        startActivity(intent)
//                        enableBluetoothOffered = true
//                      }
//                    }
//                  }
//              }
//            }
//          }
//
//          delay(500)
//        }
//      }
//
//      barcodeScanningJob = launch(Dispatchers.IO) {
//        while (true) {
//          try {
//            if (
//              (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
//                  ActivityCompat.checkSelfPermission(
//                    AITA.get(),
//                    Manifest.permission.BLUETOOTH_CONNECT
//                  ) == PackageManager.PERMISSION_GRANTED) && bluetoothAdapter.isEnabled
//            ) {
//              scanner?.let { _ ->
//                scannerSocket?.let { scannerSocket ->
//                  val scannerSppBluetoothInputStream = scannerSocket.inputStream
//
//                  val buffer = ByteArray(1024)
//
//                  if (isActive && scannerSocket.isConnected) {
//                    scannerSocket.run {
//
//                      val bytes = scannerSppBluetoothInputStream?.read(buffer)
//
//                      if (bytes != null) {
//                        if (bytes > 0) {
//                          _lastScannedBarcode.emit(String(buffer, 0, bytes).trim())
//                          delay(100)
//                          _lastScannedBarcode.emit("")
//                        } else {
//                          delay(300)
//                        }
//                      }
//                    }
//                  }
//                }
//              }
//            }
//          } catch (exception: Exception) {
//            scanner = null
//            scannerSocket = null
//            exception.printStackTrace()
//          }
//
//          delay(100)
//        }
//      }
//    }
//  }
//}