package kz.aita

internal const val CLOUD_RECOVERY_CONFIRMATION_PROBE_INTERVAL_MILLIS = 4_000L
internal const val CLOUD_HEALTH_MAX_BUSY_DEFER_MILLIS = 60_000L

/** A positive probe during an outage starts confirmation, not another exponential backoff.
 * Three probes at 0/4/8 seconds satisfy the existing recovery quorum before its reset window.
 */
internal fun cloudRecoveryAwareProbeDelayMillis(
    serverAvailable: Boolean,
    awaitingRecoveryConfirmation: Boolean,
    ordinaryDelayMillis: Long
): Long = if (serverAvailable && awaitingRecoveryConfirmation) {
    CLOUD_RECOVERY_CONFIRMATION_PROBE_INTERVAL_MILLIS
} else ordinaryDelayMillis

internal fun shouldDeferCloudHealthProbe(
    activeNetworkOperations: Int,
    transportUnavailable: Boolean,
    resumedAfterPause: Boolean,
    previousProbeFailed: Boolean,
    nowMillis: Long,
    lastProbeAtMillis: Long
): Boolean = activeNetworkOperations > 0 && !transportUnavailable && !resumedAfterPause &&
    !previousProbeFailed && lastProbeAtMillis > 0L && nowMillis >= lastProbeAtMillis &&
    nowMillis - lastProbeAtMillis < CLOUD_HEALTH_MAX_BUSY_DEFER_MILLIS
