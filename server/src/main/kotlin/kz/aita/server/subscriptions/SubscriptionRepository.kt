package kz.aita.server.subscriptions

import kz.aita.*
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

internal data class SubscriptionLocation(val storeId: UUID, val ownerId: UUID, val region: String)
private data class Promo(
    val id: UUID, val kind: String, val duration: Long?, val basisPoints: Int?, val fixedMinor: Long?
)

/** Uses the caller's transaction/connection. Never commits, opens a second connection, or calls providers.
 * Lock order: sorted physical/root store rows -> subscription -> promo -> billing user -> wallet.
 * All money, grants, redemption counts and command outcomes commit or roll back together.
 */
internal class SubscriptionRepository(private val db: Connection) {
    init { check(!db.autoCommit) { "Subscription operations require a database transaction" } }

    fun lockLocation(storeId: UUID): SubscriptionLocation? {
        val parent = query("SELECT parent_store_id FROM stores WHERE id = ?", storeId) { it.getString(1) }.singleOrNull()
        val ids = (listOf(storeId) + listOfNotNull(parent?.let(UUID::fromString))).distinct().sortedBy(UUID::toString)
        query("SELECT id FROM stores WHERE id IN (${ids.joinToString(",") { "?" }}) ORDER BY id FOR UPDATE", *ids.toTypedArray()) { it.getString(1) }
        return query("""
            SELECT s.parent_store_id,
                   CASE WHEN s.parent_store_id IS NULL THEN s.owner_user_ids->>0 ELSE p.owner_user_ids->>0 END AS owner_id,
                   COALESCE(NULLIF(s.country_locales->>0, ''), p.country_locales->>0, '') AS region
            FROM stores s LEFT JOIN stores p ON p.id = s.parent_store_id
            WHERE s.id = ? AND s.is_active AND s.parent_store_id IS NOT NULL AND p.is_active
        """.trimIndent(), storeId) { row ->
            if (row.getString("parent_store_id") != parent) subscriptionFailure("subscription.changed")
            val owner = row.getString("owner_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@query null
            SubscriptionLocation(storeId, owner, row.getString("region").trim().uppercase())
        }.singleOrNull()
    }

    fun plans(region: String): List<StoreSubscriptionPlanDataModel> = query("""
        SELECT * FROM store_subscription_plan_prices WHERE region_code = ? AND is_active ORDER BY plan_id FOR SHARE
    """.trimIndent(), region) { row ->
        basicStoreSubscriptionPlan(row.getLong("price_minor"), row.getString("currency_code"),
            row.getString("region_code"), row.getLong("price_version")).copy(
            periodUnit = row.getString("period_unit"), periodCount = row.getInt("period_count"))
    }

    fun commandRecorded(storeId: UUID, actor: UUID, commandId: UUID): Boolean =
        query("SELECT command_id FROM subscription_commands WHERE store_id = ? AND actor_user_id = ? AND command_id = ?",
            storeId, actor, commandId) { it.getString(1) }.isNotEmpty()

    fun state(storeId: UUID): StoreSubscriptionStateDataModel? =
        query("SELECT * FROM store_subscription_states WHERE store_id = ?", storeId, map = ::stateRow).singleOrNull()

    fun hasAccess(storeId: UUID, now: Long): Boolean = state(storeId)?.grantsStoreAccess(storeId.toString(), now) == true

    private fun ensureState(location: SubscriptionLocation, now: Long): StoreSubscriptionStateDataModel {
        execute("""
            INSERT INTO store_subscription_states (id, store_id, owner_user_id, region_code, updated_at_millis)
            VALUES (?, ?, ?, ?, ?) ON CONFLICT (store_id) DO NOTHING
        """.trimIndent(), UUID.randomUUID(), location.storeId, location.ownerId, location.region, now)
        var state = query("SELECT * FROM store_subscription_states WHERE store_id = ? FOR UPDATE", location.storeId, map = ::stateRow).single()
        if (state.ownerUserId != location.ownerId.toString()) {
            // Paid access belongs to the location, but a new billing owner has not consented to renewal.
            execute("""
                UPDATE store_subscription_states SET owner_user_id = ?, auto_renew = FALSE,
                next_charge_at_millis = NULL, next_attempt_at_millis = NULL,
                revision = revision + 1, updated_at_millis = ?, updated_at = NOW() WHERE store_id = ?
            """.trimIndent(), location.ownerId, now, location.storeId)
            state = requireNotNull(state(location.storeId))
        }
        return state
    }

    fun dashboard(location: SubscriptionLocation, canManage: Boolean, now: Long): SubscriptionDashboardDataModel {
        val state = ensureState(location, now)
        val visiblePlans = plans(location.region).toMutableList()
        if (state.accessKind == SUBSCRIPTION_ACCESS_LIFETIME && state.grantsStoreAccess(state.storeId, now))
            visiblePlans += lifetimeStoreSubscriptionPlan(state.regionCode, state.currencyCode)
        val charges = if (!canManage) emptyList() else query("""
            SELECT * FROM store_subscription_charge_events WHERE store_id = ? ORDER BY created_at_millis DESC, id DESC LIMIT 100
        """.trimIndent(), location.storeId) { row ->
            StoreSubscriptionChargeDataModel(id = row.getString("id"), storeId = row.getString("store_id"),
                userId = row.getString("user_id"), planId = row.getString("plan_id"), amountMinor = row.getLong("amount_minor"),
                currencyCode = row.getString("currency_code"), periodStartMillis = row.getLong("period_start_millis"),
                periodEndMillis = row.getLong("period_end_millis"), status = row.getString("status"),
                walletLedgerEntryId = row.getString("wallet_ledger_entry_id"), createdAtMillis = row.getLong("created_at_millis"),
                note = row.getString("note"))
        }
        return SubscriptionDashboardDataModel(state, charges, visiblePlans, canManage,
            if (canManage) wallet(location.ownerId) else null, now)
    }

    fun quote(location: SubscriptionLocation, request: StoreSubscriptionQuoteRequestDataModel, now: Long): StoreSubscriptionQuoteDataModel {
        val state = ensureState(location, now)
        val plan = plans(location.region).singleOrNull { it.id == request.planId }
            ?: subscriptionFailure("subscription.price_unavailable", 400)
        val promo = promo(location, subscriptionCodeHash(request.promoCode), plan, now, lock = false)
        return buildQuote(location, state, plan, promo, now)
    }

    private fun buildQuote(location: SubscriptionLocation, state: StoreSubscriptionStateDataModel,
        plan: StoreSubscriptionPlanDataModel, promo: Promo?, now: Long): StoreSubscriptionQuoteDataModel {
        if (state.grantsStoreAccess(state.storeId, now) &&
            (promo?.kind != SUBSCRIPTION_ACCESS_LIFETIME || state.accessKind == SUBSCRIPTION_ACCESS_LIFETIME))
            subscriptionFailure("subscription.already_active")
        val kind = promo?.kind ?: SUBSCRIPTION_ACCESS_PAID
        val amount = when (kind) {
            SUBSCRIPTION_ACCESS_LIFETIME, SUBSCRIPTION_ACCESS_TIMED -> 0L
            SUBSCRIPTION_PROMO_DISCOUNT -> subscriptionDiscountedPrice(plan.priceMinor, promo?.basisPoints, promo?.fixedMinor)
            else -> plan.priceMinor
        }
        return StoreSubscriptionQuoteDataModel(location.storeId.toString(), plan.id, kind, amount, plan.priceMinor,
            plan.currencyCode, plan.priceVersion, promo?.duration, state.revision, Math.addExact(now, 120_000L),
            kind == SUBSCRIPTION_ACCESS_PAID || kind == SUBSCRIPTION_PROMO_DISCOUNT)
    }

    /** Successful replay returns the current dashboard, not an old entitlement snapshot. */
    fun update(location: SubscriptionLocation, actorId: UUID, request: StoreSubscriptionUpdateRequestDataModel, now: Long, sessionId: UUID? = null) {
        val command = subscriptionCommandUuid(request.commandId)
        val hash = subscriptionCommandHash(request)
        val current = ensureState(location, now)
        val existing = query("SELECT actor_user_id, request_hash FROM subscription_commands WHERE store_id = ? AND command_id = ?",
            location.storeId, command) { it.getString(1) to it.getString(2) }.singleOrNull()
        if (existing != null) {
            if (existing.first != actorId.toString() || existing.second != hash) subscriptionFailure("subscription.command_conflict")
            return
        }
        if (request.storeId != location.storeId.toString() || request.expectedRevision == null) subscriptionFailure("subscription.command_invalid", 400)
        if (request.expectedRevision != current.revision) subscriptionFailure("subscription.changed")
        if (!request.activateNow) {
            if (request.promoCode.isNotBlank() || request.planId != current.planId ||
                (request.autoRenew && (current.accessKind != SUBSCRIPTION_ACCESS_PAID || !current.grantsStoreAccess(current.storeId, now))))
                subscriptionFailure("subscription.command_invalid", 400)
            execute("""
                UPDATE store_subscription_states SET auto_renew = ?, next_charge_at_millis = ?, next_attempt_at_millis = NULL,
                revision = revision + 1, updated_at_millis = ?, updated_at = NOW() WHERE store_id = ?
            """.trimIndent(), request.autoRenew, current.currentPeriodEndMillis.takeIf { request.autoRenew }, now, location.storeId)
            recordCommand(location.storeId, command, actorId, hash, current.revision + 1, now)
            return
        }
        val plan = plans(location.region).singleOrNull { it.id == request.planId }
            ?: subscriptionFailure("subscription.price_unavailable", 400)
        val promo = promo(location, subscriptionCodeHash(request.promoCode), plan, now, lock = true)
        val quote = buildQuote(location, current, plan, promo, now)
        val expires = request.quoteValidUntilMillis ?: subscriptionFailure("subscription.command_invalid", 400)
        if (expires < now || expires > now + 300_000L || request.expectedChargeMinor != quote.chargeMinor ||
            request.expectedCurrencyCode != quote.currencyCode || request.expectedPriceVersion != quote.priceVersion ||
            request.expectedRegularPriceMinor != quote.regularPriceMinor || request.expectedAccessKind != quote.accessKind ||
            request.expectedDurationMillis != quote.durationMillis)
            subscriptionFailure("subscription.changed")
        val accessKind = if (quote.accessKind == SUBSCRIPTION_PROMO_DISCOUNT) SUBSCRIPTION_ACCESS_PAID else quote.accessKind
        val planId = if (accessKind == SUBSCRIPTION_ACCESS_LIFETIME) SUBSCRIPTION_LIFETIME_PLAN else plan.id
        val end: Long? = when (accessKind) {
            SUBSCRIPTION_ACCESS_LIFETIME -> null
            SUBSCRIPTION_ACCESS_TIMED -> Math.addExact(now, requireNotNull(quote.durationMillis))
            else -> subscriptionPeriodEnd(now, plan.periodUnit, plan.periodCount)
        }
        val renew = request.autoRenew && quote.canAutoRenew
        val ledgerId = if (quote.chargeMinor > 0) debit(location.ownerId, quote.chargeMinor, quote.currencyCode,
            "${current.id}:$command", now) ?: subscriptionFailure("subscription.balance", 400) else ""
        execute("""
            UPDATE store_subscription_states SET plan_id = ?, status = 'active', access_kind = ?, region_code = ?,
                renewal_price_minor = ?, currency_code = ?, renewal_period_unit = ?, renewal_period_count = ?, auto_renew = ?,
                started_at_millis = COALESCE(started_at_millis, ?), current_period_start_millis = ?, current_period_end_millis = ?,
                next_charge_at_millis = ?, next_attempt_at_millis = NULL, cancelled_at_millis = NULL, past_due_since_millis = NULL,
                revision = revision + 1, updated_at_millis = ?, updated_at = NOW() WHERE store_id = ?
        """.trimIndent(), planId, accessKind, location.region,
            if (accessKind == SUBSCRIPTION_ACCESS_LIFETIME) 0L else plan.priceMinor, plan.currencyCode, plan.periodUnit, plan.periodCount,
            renew, now, now, end, end.takeIf { renew }, now, location.storeId)
        recordCharge(location, planId, quote.chargeMinor, plan.currencyCode, now, end ?: 0L, command, ledgerId,
            if (accessKind == SUBSCRIPTION_ACCESS_PAID) "paid" else "promo_grant", now)
        if (promo != null) {
            execute("""
                INSERT INTO subscription_promo_redemptions
                    (promo_id, store_id, actor_user_id, command_id, kind, granted_until_millis, discount_applied_minor, redeemed_at_millis,
                     billing_owner_user_id, session_id, charge_minor, regular_price_minor, currency_code, granted_from_millis, request_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(), promo.id, location.storeId, actorId, command, promo.kind, end,
                if (promo.kind == SUBSCRIPTION_PROMO_DISCOUNT) plan.priceMinor - quote.chargeMinor else 0L, now,
                location.ownerId, sessionId, quote.chargeMinor, plan.priceMinor, plan.currencyCode, now, hash)
            // V102 atomically consumes and archives the code with this redemption. Never delete
            // its hash tombstone: re-issuing a used secret must fail even after a store is deleted.
        }
        recordCommand(location.storeId, command, actorId, hash, current.revision + 1, now)
    }

    fun dueStoreIds(now: Long): List<UUID> = query("""
        SELECT store_id FROM store_subscription_states
        WHERE auto_renew AND access_kind = 'paid' AND status IN ('active', 'past_due') AND next_charge_at_millis <= ?
          AND (next_attempt_at_millis IS NULL OR next_attempt_at_millis <= ?)
        ORDER BY next_charge_at_millis, store_id LIMIT 100
    """.trimIndent(), now, now) { UUID.fromString(it.getString(1)) }

    fun expiredStoreIds(now: Long): List<UUID> = query("""
        SELECT store_id FROM store_subscription_states
        WHERE NOT auto_renew AND status = 'active' AND access_kind IN ('paid', 'timed')
          AND current_period_end_millis <= ? ORDER BY current_period_end_millis, store_id LIMIT 100
    """.trimIndent(), now) { UUID.fromString(it.getString(1)) }

    /** Materialize expiry for realtime/history. Authorization already checks the period boundary
     * itself, so a delayed daemon can never extend access or charge a non-renewing grant.
     */
    fun expire(location: SubscriptionLocation, now: Long): Boolean {
        val current = ensureState(location, now)
        if (current.autoRenew || current.status != SUBSCRIPTION_STATUS_ACTIVE ||
            current.accessKind !in setOf(SUBSCRIPTION_ACCESS_PAID, SUBSCRIPTION_ACCESS_TIMED) ||
            (current.currentPeriodEndMillis ?: return false) > now) return false
        execute("""
            UPDATE store_subscription_states SET status = 'inactive', next_charge_at_millis = NULL,
                next_attempt_at_millis = NULL, revision = revision + 1, updated_at_millis = ?, updated_at = NOW()
            WHERE store_id = ?
        """.trimIndent(), now, location.storeId)
        return true
    }

    /** One location per transaction. A late renewal starts now; it never bills an unpaid outage. */
    fun renew(location: SubscriptionLocation, now: Long): Boolean {
        val current = ensureState(location, now)
        val timing = query("SELECT next_attempt_at_millis, renewal_period_unit, renewal_period_count FROM store_subscription_states WHERE store_id = ?",
            location.storeId) { Triple(it.longOrNull("next_attempt_at_millis"), it.getString("renewal_period_unit"), it.getInt("renewal_period_count")) }.single()
        val due = current.nextChargeAtMillis ?: return false
        if (!current.autoRenew || current.accessKind != SUBSCRIPTION_ACCESS_PAID || due > now ||
            current.status !in setOf(SUBSCRIPTION_STATUS_ACTIVE, SUBSCRIPTION_STATUS_PAST_DUE) || (timing.first ?: 0L) > now) return false
        if (current.planId != SUBSCRIPTION_BASIC_PLAN) {
            execute("UPDATE store_subscription_states SET auto_renew = FALSE, next_charge_at_millis = NULL, revision = revision + 1 WHERE store_id = ?", location.storeId)
            return true
        }
        val command = UUID.nameUUIDFromBytes("subscription-renew:${current.id}:$due".toByteArray(Charsets.UTF_8))
        val ledger = if (current.renewalPriceMinor == 0L) "" else debit(location.ownerId, current.renewalPriceMinor, current.currencyCode,
            "${current.id}:$command", now)
        if (ledger == null) {
            execute("""
                UPDATE store_subscription_states SET status = 'past_due', past_due_since_millis = COALESCE(past_due_since_millis, ?),
                next_attempt_at_millis = ?, updated_at_millis = ?, revision = revision + 1, updated_at = NOW() WHERE store_id = ?
            """.trimIndent(), now, Math.addExact(now, 3_600_000L), now, location.storeId)
            return true
        }
        val end = subscriptionPeriodEnd(now, timing.second, timing.third)
        execute("""
            UPDATE store_subscription_states SET status = 'active', current_period_start_millis = ?, current_period_end_millis = ?,
            next_charge_at_millis = ?, next_attempt_at_millis = NULL, past_due_since_millis = NULL,
            updated_at_millis = ?, revision = revision + 1, updated_at = NOW() WHERE store_id = ?
        """.trimIndent(), now, end, end, now, location.storeId)
        recordCharge(location, current.planId, current.renewalPriceMinor, current.currencyCode, now, end, command, ledger, "paid", now)
        return true
    }

    private fun promo(location: SubscriptionLocation, hash: String?, plan: StoreSubscriptionPlanDataModel, now: Long, lock: Boolean): Promo? {
        if (hash == null) return null
        val row = query("SELECT * FROM subscription_promocodes WHERE code_hash = ?" + if (lock) " FOR UPDATE" else "", hash) { row ->
            val store = row.getString("bound_store_id")
            val owner = row.getString("bound_owner_id")
            val currency = row.getString("currency_code")
            val region = row.getString("region_code")
            if (!row.getBoolean("is_active") || row.getString("plan_id") != plan.id ||
                row.getLong("valid_from_millis") > now || (row.longOrNull("valid_until_millis")?.let { it <= now } == true) ||
                row.getLong("redemption_count") != 0L ||
                (store != null && store != location.storeId.toString()) || (owner != null && owner != location.ownerId.toString()) ||
                (currency != null && currency != plan.currencyCode) || (region != null && region != location.region))
                subscriptionFailure("subscription.promo_invalid", 400)
            Promo(UUID.fromString(row.getString("id")), row.getString("kind"), row.longOrNull("duration_millis"),
                row.longOrNull("discount_basis_points")?.toInt(), row.longOrNull("discount_minor"))
        }.singleOrNull() ?: subscriptionFailure("subscription.promo_invalid", 400)
        if (query("SELECT promo_id FROM used_subscription_promocodes WHERE promo_id = ?", row.id) { it.getString(1) }.isNotEmpty())
            subscriptionFailure("subscription.promo_invalid", 400)
        return row
    }

    private fun wallet(owner: UUID): UserWalletDataModel? = query("SELECT * FROM user_wallets WHERE user_id = ?", owner) { row ->
        UserWalletDataModel(id = row.getString("id"), userId = owner.toString(), currencyCode = row.getString("currency_code"),
            aitaCurrencyCode = row.getString("currency_code").aitaCurrencyCode(), balanceMinor = row.getLong("balance_minor"),
            reservedMinor = row.getLong("reserved_minor"), updatedAtMillis = row.getLong("updated_at_millis"))
    }.singleOrNull()

    private fun debit(owner: UUID, amount: Long, currency: String, reference: String, now: Long): String? {
        require(amount > 0)
        // This user-row lock is also taken by the legacy wallet helper (creation + all balance writers).
        if (query("SELECT id FROM users WHERE id = ? AND is_active FOR UPDATE", owner) { it.getString(1) }.isEmpty()) return null
        val balance = query("SELECT * FROM user_wallets WHERE user_id = ? FOR UPDATE", owner) {
            Triple(UUID.fromString(it.getString("id")), it.getString("currency_code"), it.getLong("balance_minor") to it.getLong("reserved_minor"))
        }.singleOrNull() ?: return null
        if (balance.second != currency || balance.third.first < amount || balance.third.second > balance.third.first - amount) return null
        val after = Math.subtractExact(balance.third.first, amount)
        val id = UUID.randomUUID()
        execute("UPDATE user_wallets SET balance_minor = ?, updated_at_millis = ?, updated_at = NOW() WHERE id = ?", after, now, balance.first)
        execute("""
            INSERT INTO user_wallet_ledger_entries (id, user_id, wallet_id, type, amount_minor, balance_before_minor,
                balance_after_minor, currency_code, reference_type, reference_id, note, created_at_millis)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'store_subscription', ?, '', ?)
        """.trimIndent(), id, owner, balance.first, WALLET_LEDGER_SUBSCRIPTION_CHARGE, -amount, balance.third.first, after, currency, reference, now)
        return id.toString()
    }

    private fun recordCommand(store: UUID, command: UUID, actor: UUID, hash: String, revision: Long, now: Long) {
        execute("INSERT INTO subscription_commands (store_id, command_id, actor_user_id, request_hash, result_revision, created_at_millis) VALUES (?, ?, ?, ?, ?, ?)",
            store, command, actor, hash, revision, now)
    }

    private fun recordCharge(location: SubscriptionLocation, planId: String, amount: Long, currency: String,
        start: Long, end: Long, command: UUID, ledgerId: String, status: String, now: Long) {
        execute("""
            INSERT INTO store_subscription_charge_events (id, store_id, user_id, plan_id, amount_minor, currency_code,
                period_start_millis, period_end_millis, status, wallet_ledger_entry_id, created_at_millis, note, command_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '', ?)
        """.trimIndent(), UUID.randomUUID(), location.storeId, location.ownerId, planId, amount, currency,
            start, end, status, ledgerId, now, command)
    }

    private fun stateRow(row: ResultSet): StoreSubscriptionStateDataModel = StoreSubscriptionStateDataModel(
        id = row.getString("id"), storeId = row.getString("store_id"), ownerUserId = row.getString("owner_user_id"),
        planId = row.getString("plan_id"), status = row.getString("status"), autoRenew = row.getBoolean("auto_renew"),
        startedAtMillis = row.longOrNull("started_at_millis"), currentPeriodStartMillis = row.longOrNull("current_period_start_millis"),
        currentPeriodEndMillis = row.longOrNull("current_period_end_millis"), nextChargeAtMillis = row.longOrNull("next_charge_at_millis"),
        cancelledAtMillis = row.longOrNull("cancelled_at_millis"), pastDueSinceMillis = row.longOrNull("past_due_since_millis"),
        updatedAtMillis = row.getLong("updated_at_millis"), accessKind = row.getString("access_kind"), regionCode = row.getString("region_code"),
        renewalPriceMinor = row.getLong("renewal_price_minor"), currencyCode = row.getString("currency_code"), revision = row.getLong("revision")
    )

    private fun ResultSet.longOrNull(column: String): Long? = getLong(column).let { if (wasNull()) null else it }
    private fun PreparedStatement.bind(args: Array<out Any?>) { args.forEachIndexed { i, value -> setObject(i + 1, value) } }
    private fun execute(sql: String, vararg args: Any?): Int = db.prepareStatement(sql).use { it.bind(args); it.executeUpdate() }
    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> = db.prepareStatement(sql).use {
        it.bind(args)
        it.executeQuery().use { result -> buildList { while (result.next()) add(map(result)) } }
    }
}
