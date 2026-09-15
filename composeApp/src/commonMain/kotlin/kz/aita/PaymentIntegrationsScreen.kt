package kz.aita

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kz.aita.payments.management.*

internal data class PaymentIntegrationsStrings(
    val title: String = "Payments and fiscalization",
    val subtitle: String = "Connect Store-owned provider accounts. Secret values are write-only.",
    val back: String = "Back",
    val refresh: String = "Refresh",
    val balance: String = "AITA balance",
    val topUps: String = "Balance top-ups",
    val noTopUps: String = "No top-up invoices yet",
    val production: String = "Production",
    val test: String = "Test",
    val connected: String = "Configured",
    val notConnected: String = "Not configured",
    val unavailable: String = "Unavailable",
    val manage: String = "Manage",
    val verify: String = "Check setup",
    val remove: String = "Remove",
    val save: String = "Save",
    val cancel: String = "Cancel",
    val enabled: String = "Enabled",
    val displayName: String = "Connection name",
    val cashboxNumber: String = "Cashbox number",
    val apiToken: String = "API token",
    val tokenAlreadySaved: String = "A token is already saved. Leave this field blank to keep it.",
    val clearToken: String = "Remove the saved token",
    val webkassaDescription: String = "Fiscal receipts for Store sales and returns.",
    val kaspiDescription: String = "Kaspi Pay invoices for AITA balance top-ups.",
    val kaspiContractRequired: String = "Invoice creation stays disabled until the official Kaspi merchant API contract is configured.",
    val remoteCheckDisabled: String = "AITA can store credentials securely, but remote verification is not enabled yet.",
    val revisionConflictHint: String = "Settings changed on another device. Refresh before saving again.",
    val savedFields: String = "Saved fields",
    val setupNeedsAttention: String = "Setup needs attention",
    val disabled: String = "Disabled",
)

@Composable
internal fun PaymentIntegrationsWorkspace(
    state: PaymentIntegrationsUiState,
    storeId: String,
    strings: PaymentIntegrationsStrings = PaymentIntegrationsStrings(),
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectEnvironment: (PaymentManagedEnvironment) -> Unit,
    onSave: (PaymentManagedProvider, PaymentIntegrationSecretPatchRequest) -> Unit,
    onDelete: (PaymentManagedProvider, PaymentIntegrationDeleteRequest) -> Unit,
    onTest: (PaymentManagedProvider, PaymentIntegrationTestRequest) -> Unit,
) {
    var editingProvider by remember { mutableStateOf<PaymentManagedProvider?>(null) }
    val busy = state.loading || state.mutatingProvider != null

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack, enabled = !busy) { Text(strings.back) }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = onRefresh, enabled = !busy) {
                            if (state.loading) { AitaBusyIndicator(Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                            Text(strings.refresh)
                        }
                    }
                    Text(strings.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(strings.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            state.errorMessage?.let { message ->
                item { MessageCard(message, isError = true) }
            }
            state.successMessage?.let { message ->
                item { MessageCard(message, isError = false) }
            }

            item {
                EnvironmentSelector(
                    selected = state.selectedEnvironment,
                    strings = strings,
                    enabled = !busy,
                    onSelect = onSelectEnvironment,
                )
            }

            if (state.loading && state.balance == null && state.integrations.isEmpty() && state.capabilities.isEmpty()) {
                item { AitaLoadingSkeleton(Modifier.fillMaxWidth().padding(16.dp), layout = LoadingLayout.PaymentIntegration, rows = 5) }
                return@LazyColumn
            }

            item {
                BalanceCard(state = state, strings = strings)
            }

            item {
                ProviderCard(
                    provider = PaymentManagedProvider.WEBKASSA,
                    summary = state.integration(PaymentManagedProvider.WEBKASSA),
                    capabilityEnabled = state.capability(PaymentManagedProvider.WEBKASSA)?.credentialManagementEnabled == true,
                    description = strings.webkassaDescription,
                    strings = strings,
                    busy = busy,
                    onManage = { editingProvider = PaymentManagedProvider.WEBKASSA },
                    onTest = {
                        onTest(
                            PaymentManagedProvider.WEBKASSA,
                            PaymentIntegrationTestRequest(storeId, state.selectedEnvironment),
                        )
                    },
                )
            }

            item {
                ProviderCard(
                    provider = PaymentManagedProvider.KASPI_PAY,
                    summary = state.integration(PaymentManagedProvider.KASPI_PAY),
                    capabilityEnabled = state.capability(PaymentManagedProvider.KASPI_PAY)?.credentialManagementEnabled == true,
                    description = strings.kaspiDescription,
                    strings = strings,
                    busy = busy,
                    onManage = { editingProvider = PaymentManagedProvider.KASPI_PAY },
                    onTest = {
                        onTest(
                            PaymentManagedProvider.KASPI_PAY,
                            PaymentIntegrationTestRequest(storeId, state.selectedEnvironment),
                        )
                    },
                    footer = strings.kaspiContractRequired,
                )
            }

            item {
                Text(
                    strings.topUps,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.topUps.isEmpty()) {
                item {
                    Text(
                        strings.noTopUps,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.topUps, key = { it.id }) { invoice ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(formatMinor(invoice.amountMinor, invoice.currency), style = MaterialTheme.typography.titleMedium)
                            Text(invoice.status, style = MaterialTheme.typography.bodyMedium)
                            Text(invoice.id.take(16), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }

    }

    editingProvider?.let { provider ->
        PaymentIntegrationEditorDialog(
            provider = provider,
            storeId = storeId,
            environment = state.selectedEnvironment,
            existing = state.integration(provider),
            capabilityEnabled = state.capability(provider)?.credentialManagementEnabled == true,
            strings = strings,
            busy = state.mutatingProvider == provider,
            onDismiss = { if (state.mutatingProvider == null) editingProvider = null },
            onSave = { request ->
                editingProvider = null
                onSave(provider, request)
            },
            onDelete = { request ->
                editingProvider = null
                onDelete(provider, request)
            },
        )
    }
}

@Composable
private fun EnvironmentSelector(
    selected: PaymentManagedEnvironment,
    strings: PaymentIntegrationsStrings,
    enabled: Boolean,
    onSelect: (PaymentManagedEnvironment) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { onSelect(PaymentManagedEnvironment.PRODUCTION) },
            enabled = enabled,
            label = { Text(strings.production + if (selected == PaymentManagedEnvironment.PRODUCTION) " •" else "") },
        )
        AssistChip(
            onClick = { onSelect(PaymentManagedEnvironment.TEST) },
            enabled = enabled,
            label = { Text(strings.test + if (selected == PaymentManagedEnvironment.TEST) " •" else "") },
        )
    }
}

@Composable
private fun BalanceCard(state: PaymentIntegrationsUiState, strings: PaymentIntegrationsStrings) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.balance, style = MaterialTheme.typography.labelLarge)
            Text(
                state.balance?.let { formatMinor(it.balanceMinor, it.currency) } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
            )
            if (state.capability(PaymentManagedProvider.KASPI_PAY)?.invoiceCreationEnabled != true) {
                Text(strings.kaspiContractRequired, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: PaymentManagedProvider,
    summary: PaymentIntegrationSummaryDto?,
    capabilityEnabled: Boolean,
    description: String,
    strings: PaymentIntegrationsStrings,
    busy: Boolean,
    onManage: () -> Unit,
    onTest: () -> Unit,
    footer: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (provider == PaymentManagedProvider.WEBKASSA) "Webkassa" else "Kaspi Pay", style = MaterialTheme.typography.titleLarge)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    when {
                        !capabilityEnabled -> strings.unavailable
                        summary == null -> strings.notConnected
                        else -> strings.connected
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            summary?.displayName?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            summary?.let {
                Text(verificationLabel(it.verificationState, strings), style = MaterialTheme.typography.bodySmall)
                if (it.configuredSecretKeys.isNotEmpty()) {
                    Text(
                        "${strings.savedFields}: ${it.configuredSecretKeys.sorted().joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            footer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Divider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onManage, enabled = capabilityEnabled && !busy) { Text(strings.manage) }
                OutlinedButton(onClick = onTest, enabled = summary != null && !busy) { Text(strings.verify) }
            }
        }
    }
}

@Composable
private fun PaymentIntegrationEditorDialog(
    provider: PaymentManagedProvider,
    storeId: String,
    environment: PaymentManagedEnvironment,
    existing: PaymentIntegrationSummaryDto?,
    capabilityEnabled: Boolean,
    strings: PaymentIntegrationsStrings,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (PaymentIntegrationSecretPatchRequest) -> Unit,
    onDelete: (PaymentIntegrationDeleteRequest) -> Unit,
) {
    var displayName by remember(provider, environment, existing?.revision) { mutableStateOf(existing?.displayName.orEmpty()) }
    var cashboxNumber by remember(provider, environment, existing?.revision) {
        mutableStateOf(existing?.settings?.get("cashboxNumber").orEmpty())
    }
    var token by remember(provider, environment, existing?.revision) { mutableStateOf("") }
    var clearToken by remember(provider, environment, existing?.revision) { mutableStateOf(false) }
    var enabled by remember(provider, environment, existing?.revision) { mutableStateOf(existing?.enabled ?: true) }

    fun clearEphemeralSecret() {
        token = "\u0000".repeat(token.length)
        token = ""
    }

    AlertDialog(
        onDismissRequest = {
            clearEphemeralSecret()
            onDismiss()
        },
        title = { Text(if (provider == PaymentManagedProvider.WEBKASSA) "Webkassa" else "Kaspi Pay") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!capabilityEnabled) {
                    Text(strings.kaspiContractRequired, color = MaterialTheme.colorScheme.error)
                }
                AppConfiguration.aitaFormTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = displayName,
                    onValueChange = { displayName = it },
                    titleText = strings.displayName,
                    placeholderText = strings.displayName,
                    identityKey = "payment-integration-${provider.name}-${environment.name}-display-name",
                    enabled = !busy && capabilityEnabled,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    leadingIconPath = AppConfiguration.stateValues.drawablePathIconEdit,
                    onTransformValue = { it.take(160) }
                )
                if (provider == PaymentManagedProvider.WEBKASSA) {
                    AppConfiguration.aitaFormTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = cashboxNumber,
                        onValueChange = { cashboxNumber = it },
                        titleText = strings.cashboxNumber,
                        placeholderText = strings.cashboxNumber,
                        identityKey = "payment-integration-${provider.name}-${environment.name}-cashbox-number",
                        enabled = !busy && capabilityEnabled,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                        leadingIconPath = AppConfiguration.stateValues.drawablePathIconReceipt,
                        onTransformValue = { it.take(128) }
                    )
                    AppConfiguration.aitaFormTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = token,
                        onValueChange = { token = it; clearToken = false },
                        titleText = strings.apiToken,
                        placeholderText = strings.apiToken,
                        identityKey = "payment-integration-${provider.name}-${environment.name}-api-token",
                        enabled = !busy && capabilityEnabled && !clearToken,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        leadingIconPath = AppConfiguration.stateValues.drawablePathIconSecurity,
                        password = true,
                        sensitive = true,
                        onTransformValue = { it.take(16_384) }
                    )
                    if (existing?.configuredSecretKeys?.contains("apiToken") == true) {
                        Text(
                            strings.tokenAlreadySaved,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (existing?.configuredSecretKeys?.contains("apiToken") == true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = clearToken,
                                onCheckedChange = { clearToken = it; if (it) clearEphemeralSecret() },
                                enabled = !busy && capabilityEnabled,
                            )
                            Text(strings.clearToken)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = !busy && capabilityEnabled)
                    Text(strings.enabled, modifier = Modifier.padding(start = 8.dp))
                }
                Text(strings.remoteCheckDisabled, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && capabilityEnabled,
                onClick = {
                    val secretCopy = token
                    clearEphemeralSecret()
                    onSave(
                        PaymentIntegrationSecretPatchRequest(
                            storeId = storeId,
                            environment = environment,
                            expectedRevision = existing?.revision,
                            displayName = displayName.trim().takeIf(String::isNotEmpty),
                            enabled = enabled,
                            settings = if (provider == PaymentManagedProvider.WEBKASSA)
                                mapOf("cashboxNumber" to cashboxNumber.trim()).filterValues(String::isNotEmpty)
                            else emptyMap(),
                            secrets = if (secretCopy.isNotBlank()) mapOf("apiToken" to secretCopy) else emptyMap(),
                            clearSecretKeys = if (clearToken) setOf("apiToken") else emptySet(),
                        )
                    )
                },
            ) { Text(strings.save) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (existing != null) {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            clearEphemeralSecret()
                            onDelete(PaymentIntegrationDeleteRequest(storeId, environment, existing.revision))
                        },
                    ) { Text(strings.remove) }
                }
                TextButton(
                    enabled = !busy,
                    onClick = {
                        clearEphemeralSecret()
                        onDismiss()
                    },
                ) { Text(strings.cancel) }
            }
        },
    )
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun verificationLabel(state: PaymentVerificationState, strings: PaymentIntegrationsStrings): String = when (state) {
    PaymentVerificationState.NOT_CONFIGURED -> strings.notConnected
    PaymentVerificationState.CONFIGURED_UNVERIFIED -> strings.remoteCheckDisabled
    PaymentVerificationState.VERIFIED -> strings.connected
    PaymentVerificationState.FAILED -> strings.setupNeedsAttention
    PaymentVerificationState.DISABLED -> strings.disabled
}

private fun formatMinor(amountMinor: Long, currency: String): String {
    val negative = amountMinor < 0
    val signedMajor = amountMinor / 100
    val major = if (negative) -signedMajor else signedMajor
    val minor = kotlin.math.abs(amountMinor % 100).toString().padStart(2, '0')
    val sign = if (negative) "−" else ""
    return "$sign$major.$minor ${currency.uppercase()}"
}
