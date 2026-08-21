package kz.aita.server.payments

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PaymentOperationWorker(
    private val scope: CoroutineScope,
    private val repository: PaymentOperationOutboxRepository,
    private val handler: DurablePaymentOperationHandler,
    private val retryPolicy: PaymentRetryPolicy = PaymentRetryPolicy(),
    private val idleDelay: Duration = 2.seconds,
    private val batchSize: Int = 20,
    workerId: String? = null,
) {
    private val workerId = workerId ?: buildString {
        append(runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("aita"))
        append('-')
        append(UUID.randomUUID())
    }.take(160)

    init {
        require(batchSize in 1..100)
        require(idleDelay > Duration.ZERO)
    }

    fun start(): Job = scope.launch {
        while (isActive) {
            val claim = runCatching { repository.claimBatch(workerId, batchSize) }
            if (claim.isFailure) {
                delay(idleDelay)
                continue
            }
            val batch = claim.getOrThrow()
            if (batch.isEmpty()) {
                delay(idleDelay)
                continue
            }
            for (operation in batch) {
                if (!isActive) break
                val result = try {
                    handler.execute(operation)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    ProviderOperationExecutionResult(
                        successful = false,
                        retryable = true,
                        safeErrorCode = "provider_transport_failure",
                        safeErrorMessage = PaymentSecretRedactor.redactText(
                            throwable.message ?: throwable::class.simpleName.orEmpty(),
                        ),
                    )
                }
                if (result.successful) {
                    repository.complete(operation.id, result)
                } else {
                    val decision = retryPolicy.decide(
                        attempt = operation.attemptCount,
                        httpStatus = result.httpStatus,
                        transportFailure = result.httpStatus == null,
                    )
                    val shouldRetry = result.retryable && decision.retry
                    repository.failOrRetry(
                        operationId = operation.id,
                        result = result.copy(retryable = shouldRetry),
                        nextAttemptAt = if (shouldRetry) Instant.now().plusMillis(decision.delay.inWholeMilliseconds) else null,
                    )
                }
            }
        }
    }
}
