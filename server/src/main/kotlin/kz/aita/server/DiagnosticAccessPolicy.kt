package kz.aita.server

import io.ktor.server.config.ApplicationConfig
import kz.aita.DiagnosticBatch
import kz.aita.validDiagnosticId
import java.security.MessageDigest
import java.util.UUID

internal data class DiagnosticServerSettings(val enabled: Boolean = true, val administrators: Set<UUID> = emptySet(), val gatewayKey: String? = null) {
    fun canReview(user: UUID): Boolean = user in administrators
    companion object {
        fun read(config: ApplicationConfig): DiagnosticServerSettings {
            val enabled = config.propertyOrNull("diagnostics.enabled")?.getString()?.let { it == "true" } ?: true
            val raw = config.propertyOrNull("diagnostics.adminUserIds")?.getString().orEmpty()
            val parts = raw.split(',').map(String::trim).filter(String::isNotEmpty)
            val admins = if (parts.size <= 128 && parts.all(::validDiagnosticId)) parts.map(UUID::fromString).toSet() else emptySet()
            val key = config.propertyOrNull("diagnostics.gatewayKey")?.getString()?.takeIf { it.length in 32..512 }
            return DiagnosticServerSettings(enabled, admins, key)
        }
    }
}
internal fun diagnosticOwnerMatches(owner: UUID?, batch: DiagnosticBatch): Boolean =
    batch.events.isNotEmpty() && batch.events.all { it.context.accountId == owner?.toString() && (owner != null || it.context.storeId == null) }

internal fun trustedDiagnosticLocation(configuredKey: String?, suppliedKey: String?, country: String?, region: String?): TrustedDiagnosticLocation? {
    if (configuredKey == null || configuredKey.length !in 32..512 || suppliedKey == null || suppliedKey.length !in 32..512) return null
    if (!MessageDigest.isEqual(configuredKey.toByteArray(Charsets.UTF_8), suppliedKey.toByteArray(Charsets.UTF_8))) return null
    if (country == null || !Regex("[A-Z]{2}").matches(country) || country in setOf("XX", "T1")) return null
    val safeRegion = region?.takeIf { Regex("[A-Za-z0-9 -]{1,32}").matches(it) }
    return TrustedDiagnosticLocation(country, safeRegion)
}
