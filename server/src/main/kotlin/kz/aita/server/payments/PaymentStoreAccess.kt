package kz.aita.server.payments

import com.auth0.jwt.interfaces.Claim
import io.ktor.server.auth.jwt.*
import java.sql.Connection

internal object PaymentStoreAccess {
    private val relationshipQueries = listOf(
        "SELECT 1 FROM store_users WHERE CAST(store_id AS TEXT) = ? AND CAST(user_id AS TEXT) = ? LIMIT 1",
        "SELECT 1 FROM store_worker_requests WHERE CAST(store_id AS TEXT) = ? AND CAST(requester_user_id AS TEXT) = ? LIMIT 1",
        "SELECT 1 FROM store_worker_memberships WHERE CAST(store_id AS TEXT) = ? AND CAST(user_id AS TEXT) = ? AND COALESCE(is_active, TRUE) = TRUE LIMIT 1",
        "SELECT 1 FROM stores WHERE CAST(id AS TEXT) = ? AND CAST(user_ids AS TEXT) = ? LIMIT 1",
        "SELECT 1 FROM stores WHERE CAST(id AS TEXT) = ? AND CAST(owner_user_ids AS TEXT) = ? LIMIT 1",
        "SELECT 1 FROM store_subscription_states WHERE CAST(id AS TEXT) = ? AND CAST(owner_user_id AS TEXT) = ? LIMIT 1",
        "SELECT 1 FROM store_subscription_charge_events WHERE CAST(id AS TEXT) = ? AND CAST(user_id AS TEXT) = ? LIMIT 1",
        "SELECT 1 FROM stores WHERE CAST(id AS TEXT) = ? AND CAST(user_id AS TEXT) = ? LIMIT 1"
    )

    fun subject(principal: JWTPrincipal): String? = sequenceOf(
        principal.payload.subject,
        claimText(principal.payload.claims["userId"]),
        claimText(principal.payload.claims["user_id"]),
        claimText(principal.payload.claims["accountId"]),
        claimText(principal.payload.claims["account_id"]),
    ).firstOrNull { !it.isNullOrBlank() }

    fun canAccess(principal: JWTPrincipal, storeId: String, connection: Connection): Boolean {
        val normalizedStoreId = storeId.trim()
        if (normalizedStoreId.isEmpty()) return false
        if (claimedStoreIds(principal).any { it.equals(normalizedStoreId, ignoreCase = true) }) return true
        val subject = subject(principal) ?: return false
        return relationshipQueries.any { sql ->
            runCatching {
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, normalizedStoreId)
                    statement.setString(2, subject)
                    statement.executeQuery().use { it.next() }
                }
            }.getOrDefault(false)
        }
    }

    private fun claimedStoreIds(principal: JWTPrincipal): Set<String> {
        val names = listOf("storeId", "store_id", "storeIds", "store_ids", "stores", "allowedStores")
        return buildSet {
            names.forEach { name ->
                val claim = principal.payload.claims[name] ?: return@forEach
                claimText(claim)?.split(',', ';', ' ')?.map(String::trim)?.filter(String::isNotEmpty)?.let(::addAll)
                runCatching { claim.asList(String::class.java) }.getOrNull()?.filterNotNull()?.map(String::trim)
                    ?.filter(String::isNotEmpty)?.let(::addAll)
            }
        }
    }

    private fun claimText(claim: Claim?): String? = runCatching { claim?.asString() }.getOrNull()?.trim()?.takeIf(String::isNotEmpty)
}
