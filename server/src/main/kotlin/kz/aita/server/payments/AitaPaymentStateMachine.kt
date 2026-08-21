package kz.aita.server.payments

import kz.aita.payments.AitaBalanceInvoiceStatus

data class ProviderInvoiceObservation(
    val status: AitaBalanceInvoiceStatus,
    val externalInvoiceId: String,
    val observedAtEpochMillis: Long,
    val providerEventId: String? = null,
)

data class InvoiceReconciliationDecision(
    val apply: Boolean,
    val targetStatus: AitaBalanceInvoiceStatus,
    val creditBalance: Boolean,
    val reason: String,
)

fun decideInvoiceReconciliation(
    current: AitaBalanceInvoiceStatus,
    observation: ProviderInvoiceObservation,
): InvoiceReconciliationDecision {
    if (current == observation.status) {
        return InvoiceReconciliationDecision(
            apply = false,
            targetStatus = current,
            creditBalance = false,
            reason = "already_applied",
        )
    }

    val allowed = kz.aita.payments.canTransitionAitaBalanceInvoice(current, observation.status)
    if (!allowed) {
        return InvoiceReconciliationDecision(
            apply = false,
            targetStatus = current,
            creditBalance = false,
            reason = "stale_or_invalid_transition",
        )
    }

    return InvoiceReconciliationDecision(
        apply = true,
        targetStatus = observation.status,
        creditBalance = observation.status == AitaBalanceInvoiceStatus.PAID,
        reason = "provider_state_accepted",
    )
}
