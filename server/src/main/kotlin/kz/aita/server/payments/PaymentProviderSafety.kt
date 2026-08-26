package kz.aita.server.payments

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.util.*

internal data class ProviderEndpointValidation(
    val accepted: Boolean,
    val normalizedUrl: String? = null,
    val safeErrorCode: String? = null,
)

/** Protects provider credentials from SSRF and redirect-based credential forwarding. */
internal object PaymentProviderUrlPolicy {
    private val blockedHostSuffixes = listOf(".local", ".internal", ".localhost")

    fun validate(
        rawUrl: String,
        production: Boolean,
        allowedHosts: Set<String> = emptySet(),
        allowPrivateTestEndpoints: Boolean = false,
    ): ProviderEndpointValidation {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull()
            ?: return ProviderEndpointValidation(false, safeErrorCode = "invalid_provider_url")
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        if (host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) {
            return ProviderEndpointValidation(false, safeErrorCode = "invalid_provider_url")
        }
        if (production && scheme != "https") {
            return ProviderEndpointValidation(false, safeErrorCode = "provider_https_required")
        }
        if (!production && scheme !in setOf("http", "https")) {
            return ProviderEndpointValidation(false, safeErrorCode = "invalid_provider_scheme")
        }
        val normalizedAllowedHosts = allowedHosts.map { it.trim().lowercase(Locale.ROOT).removePrefix(".") }
            .filter { it.isNotBlank() }
            .toSet()
        if (production && normalizedAllowedHosts.isEmpty()) {
            return ProviderEndpointValidation(false, safeErrorCode = "provider_host_allowlist_required")
        }
        if (normalizedAllowedHosts.isNotEmpty() && normalizedAllowedHosts.none { allowed ->
                host == allowed || host.endsWith(".$allowed")
            }
        ) {
            return ProviderEndpointValidation(false, safeErrorCode = "provider_host_not_allowed")
        }
        val privateEndpoint = host == "localhost" || blockedHostSuffixes.any(host::endsWith) ||
            isLiteralPrivateAddress(host)
        if (privateEndpoint && (production || !allowPrivateTestEndpoints)) {
            return ProviderEndpointValidation(false, safeErrorCode = "provider_private_host_blocked")
        }
        val normalized = URI(scheme, null, host, uri.port, uri.path.ifBlank { "/" }, uri.query, null)
        return ProviderEndpointValidation(true, normalized.toASCIIString())
    }

    private fun isLiteralPrivateAddress(host: String): Boolean {
        val candidate = host.removePrefix("[").removeSuffix("]")
        val looksLikeIpv4 = candidate.count { it == '.' } == 3 && candidate.all { it.isDigit() || it == '.' }
        val looksLikeIpv6 = ':' in candidate
        if (!looksLikeIpv4 && !looksLikeIpv6) return false
        val address = runCatching { InetAddress.getByName(candidate) }.getOrNull() ?: return true
        return when (address) {
            is Inet4Address, is Inet6Address -> address.isAnyLocalAddress ||
                address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress ||
                address.isMulticastAddress
            else -> true
        }
    }
}

internal object PaymentSecretRedactor {
    private val sensitiveKey = Regex("(?i)(authorization|token|secret|password|api[-_]?key|signature)")
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+")
    private val jsonSecret = Regex(
        "(?i)(\\\"(?:token|secret|password|api[-_]?key|signature)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")",
    )

    fun redactHeaders(headers: Map<String, String>): Map<String, String> = headers.mapValues { (key, value) ->
        if (sensitiveKey.containsMatchIn(key)) "<redacted>" else redactText(value)
    }

    fun redactText(value: String): String = value
        .replace(bearer, "Bearer <redacted>")
        .replace(jsonSecret, "\$1<redacted>\$2")
        .take(4_096)
}

internal fun operationsConstantTimeEquals(expected: ByteArray, actual: ByteArray): Boolean =
    MessageDigest.isEqual(expected, actual)
