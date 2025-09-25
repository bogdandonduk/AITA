package kz.aita.app.feature.barcodeScan

import kotlinx.coroutines.Job


abstract class BarcodeScanFeature {

  private var barcodeScanInitJob: Job? = null
  private var barcodeScanPrimaryJob: Job? = null
  private var barcodeScanSecondaryJob: Job? = null

  abstract suspend fun startBarcodeScanJobPrimary()
  abstract suspend fun stopBarcodeScanJobPrimary()

  abstract suspend fun startBarcodeScanJobSecondary()
  abstract suspend fun stopBarcodeScanJobSecondary()
}

expect val barcodeScanFeature: BarcodeScanFeature
