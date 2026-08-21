package kz.aita

import kz.aita.payments.management.PaymentBalanceDto
import kz.aita.payments.management.PaymentCapabilityDto
import kz.aita.payments.management.PaymentIntegrationSummaryDto
import kz.aita.payments.management.PaymentManagedEnvironment
import kz.aita.payments.management.PaymentManagedProvider
import kz.aita.payments.management.PaymentTopUpInvoiceDto

internal data class PaymentIntegrationsUiState(
    val loading: Boolean = false,
    val mutatingProvider: PaymentManagedProvider? = null,
    val integrations: List<PaymentIntegrationSummaryDto> = emptyList(),
    val capabilities: List<PaymentCapabilityDto> = emptyList(),
    val balance: PaymentBalanceDto? = null,
    val topUps: List<PaymentTopUpInvoiceDto> = emptyList(),
    val selectedEnvironment: PaymentManagedEnvironment = PaymentManagedEnvironment.PRODUCTION,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    fun integration(provider: PaymentManagedProvider): PaymentIntegrationSummaryDto? =
        integrations.firstOrNull { it.provider == provider && it.environment == selectedEnvironment }

    fun capability(provider: PaymentManagedProvider): PaymentCapabilityDto? =
        capabilities.firstOrNull { it.provider == provider && it.environment == selectedEnvironment }

    fun canEdit(provider: PaymentManagedProvider): Boolean =
        mutatingProvider == null && capability(provider)?.credentialManagementEnabled == true
}

internal data class PaymentIntegrationDraft(
    val provider: PaymentManagedProvider,
    val environment: PaymentManagedEnvironment,
    val expectedRevision: Long? = null,
    val displayName: String = "",
    val enabled: Boolean = true,
    val settings: Map<String, String> = emptyMap(),
    /** Ephemeral write-only values. They must not be persisted in navigation or draft storage. */
    val secretValues: Map<String, String> = emptyMap(),
    val clearSecretKeys: Set<String> = emptySet(),
)

internal fun PaymentIntegrationDraft.withSecretsClearedFromMemory(): PaymentIntegrationDraft =
    copy(secretValues = secretValues.mapValues { "" }, clearSecretKeys = emptySet())
