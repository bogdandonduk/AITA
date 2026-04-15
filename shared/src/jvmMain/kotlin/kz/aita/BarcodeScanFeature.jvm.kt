//package kz.aita.app.feature.barcodeScan
//
//import com.fazecast.jSerialComm.SerialPort
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.cancelAndJoin
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import kz.aita.core.genericLocalService
//import kz.aita.io
//
//actual val barcodeScanFeature: BarcodeScanFeature by lazy {
//  object: BarcodeScanFeature(genericLocalService) {
//
//    private var port: SerialPort? = null
//
//    override suspend fun initScanner(address: String) {
//      super.initScanner(address)
//
//      println("before ports")
//
//      val ports = SerialPort.getCommPorts().toList()
//      val keyword = "COM"
//      ports.forEach {
//        check(it.openPort()) {
//          println("port failed to open ${it.descriptivePortName} ${it.portDescription}")
//        }
//
//        println("opened port ${it.descriptivePortName} ${it.portDescription}")
//      }
//
//
////      val chosenPort = ports.find { it.descriptivePortName.contains(keyword, true) || it.portDescription.contains(keyword, true) }!!
////
////      chosenPort.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
////      chosenPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 200, 0)
////      check(chosenPort.openPort()) {
////        println("port failed to open $chosenPort")
////      }
////      port = chosenPort
//
//    }
//
//    override suspend fun startBarcodeScanning() {
//      println("called even")
//      scannerInitJob = launch(Dispatchers.io) {
//        while (true) {
//          try {
//            initScanner("")
//          } catch (exception: Exception) {
//            exception.printStackTrace()
//            setScannerConnectedState(false)
//          }
//
//          delay(500)
//        }
//      }
//
//      barcodeScanningJob = launch {
//        while (true) {
//          val buffer = ByteArray(1024)
//          val builder = StringBuilder()
//
//          val bytes = port?.inputStream?.read(buffer)
//
//          if (bytes != null) {
//            if (bytes > 0) {
//              val value = String(buffer, 0, bytes).trim()
//              _lastScannedBarcode.emit(value)
//              println("scanner read $value")
//
////              delay(100)
////              _lastScannedBarcode.emit("")
//            } else {
//              delay(300)
//            }
//          }
//        }
//      }
//    }
//
//    private suspend fun stop() {
//      barcodeScanningJob?.cancelAndJoin()
//      barcodeScanningJob = null
//      port?.let {
//        if (it.isOpen) it.closePort()
//      }
//      port = null
//    }
//  }
//}