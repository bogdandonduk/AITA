package kz.aita.server

import io.ktor.server.application.Application
import java.security.MessageDigest
import java.sql.Connection
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kz.aita.*
import kz.aita.server.marketplace.MarketplaceRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

internal object MarketPriceWatches : Table("market_price_watches") {
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
    val revision = long("revision")
    val enabled = bool("enabled").default(false)
    val settings = jsonb("settings", jsonBase, MarketPriceWatchSettings.serializer())
    val observations = jsonb("observations", jsonBase, ListSerializer(MarketPriceWatchObservation.serializer()))
    val checkedAtMillis = long("checked_at_millis").nullable()
    val nextCheckAtMillis = long("next_check_at_millis").nullable()
    val updatedAtMillis = long("updated_at_millis")
    val requestId = uuid("request_id")
    val requestFingerprint = text("request_fingerprint")
    override val primaryKey = PrimaryKey(userId, storeId)
}

internal class MarketPriceWatchFailure(val kind: String) : IllegalArgumentException(kind)
private fun watchRow(user: UUID, store: UUID, lock: Boolean = false) = MarketPriceWatches.selectAll().where {
    (MarketPriceWatches.userId eq user) and (MarketPriceWatches.storeId eq store)
}.apply { if (lock) forUpdate() }.singleOrNull()

private fun ResultRow.toPriceWatch() = MarketPriceWatchState(
    this[MarketPriceWatches.userId].toString(), this[MarketPriceWatches.storeId].toString(),
    this[MarketPriceWatches.revision], this[MarketPriceWatches.enabled], this[MarketPriceWatches.settings],
    this[MarketPriceWatches.observations], this[MarketPriceWatches.checkedAtMillis],
    this[MarketPriceWatches.nextCheckAtMillis], this[MarketPriceWatches.updatedAtMillis],
)

// Match account deletion's lock order. Once deletion commits, neither a save nor a scheduled
// check can recreate personal watch data or a notification for the deleted account.
private fun lockPriceWatchOwner(user: UUID) = Users.select(Users.isActive).where { Users.id eq user }
    .forUpdate().singleOrNull()?.get(Users.isActive) == true
private fun lockPriceWatch(user: UUID, store: UUID) {
    TransactionManager.current().exec("SELECT pg_advisory_xact_lock(hashtextextended('market-price-watch:$user:$store',0))")
}

internal fun readPriceWatchInsideTransaction(user: UUID, store: UUID): MarketPriceWatchState {
    if (!priceInsightsReaderInsideTransaction(user, store)) throw MarketPriceWatchFailure("access")
    return watchRow(user, store)?.toPriceWatch() ?: MarketPriceWatchState(user.toString(), store.toString())
}

private fun priceWatchReport(user: UUID, store: UUID, settings: MarketPriceWatchSettings, now: Long) =
    MarketplaceRepository(TransactionManager.current().connection.connection as Connection,
        ::marketplaceStoreOwnerInsideTransaction, ::priceInsightsReaderInsideTransaction)
        .priceInsights(user, store, settings.reportRequest(), now, settings.items.associate { it.itemId to it.gtin })

internal fun savePriceWatchInsideTransaction(user: UUID, store: UUID, body: MarketPriceWatchUpdate, now: Long,
    report: (UUID, UUID, MarketPriceWatchSettings, Long) -> MarketPriceInsightsPage = ::priceWatchReport,
): MarketPriceWatchState {
    val request = runCatching { UUID.fromString(body.requestId) }.getOrNull()
        ?.takeIf { it.toString() == body.requestId } ?: throw MarketPriceWatchFailure("watch_invalid")
    if (body.expectedRevision < 0) throw MarketPriceWatchFailure("watch_invalid")
    if (!lockPriceWatchOwner(user)) throw MarketPriceWatchFailure("access")
    lockPriceWatch(user, store)
    if (!priceInsightsReaderInsideTransaction(user, store)) throw MarketPriceWatchFailure("access")
    val previous = watchRow(user, store, lock = true)
    val digest = MessageDigest.getInstance("SHA-256").digest(jsonBase.encodeToString(body).toByteArray())
        .joinToString("") { "%02x".format(it) }
    if (previous?.get(MarketPriceWatches.requestId) == request) {
        if (previous[MarketPriceWatches.requestFingerprint] != digest) throw MarketPriceWatchFailure("watch_conflict")
        return previous.toPriceWatch()
    }
    val oldRevision = previous?.get(MarketPriceWatches.revision) ?: 0
    if (oldRevision != body.expectedRevision || oldRevision == Long.MAX_VALUE) throw MarketPriceWatchFailure("watch_conflict")
    if (previous == null && MarketPriceWatches.selectAll().where { MarketPriceWatches.userId eq user }.count() >= 100)
        throw MarketPriceWatchFailure("watch_store_limit")
    val settings = if (body.enabled) body.settings.normalized()?.takeIf { it.items.isNotEmpty() }
        ?: throw MarketPriceWatchFailure("watch_invalid")
        else previous?.get(MarketPriceWatches.settings) ?: MarketPriceWatchSettings()
    val checked = if (body.enabled) report(user, store, settings, now) else null
    if (checked != null && (!checked.isValidMarketPricePage(user.toString(), store.toString(), settings.reportRequest()) ||
        checked.rows.size != settings.items.size || settings.items.any { item ->
            checked.rows.none { it.goodsItemId == item.itemId && it.basis?.gtin == item.gtin }
        })) throw MarketPriceWatchFailure("watch_invalid")
    val observations = checked?.let { evaluateMarketPriceWatch(settings, emptyList(), it).observations }
        ?: previous?.get(MarketPriceWatches.observations).orEmpty()
    fun write(row: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>) {
        row[MarketPriceWatches.revision] = oldRevision + 1
        row[MarketPriceWatches.enabled] = body.enabled
        row[MarketPriceWatches.settings] = settings
        row[MarketPriceWatches.observations] = observations
        row[MarketPriceWatches.checkedAtMillis] = checked?.checkedAtMillis ?: previous?.get(MarketPriceWatches.checkedAtMillis)
        row[MarketPriceWatches.nextCheckAtMillis] = if (body.enabled) now + MARKET_PRICE_WATCH_INTERVAL else null
        row[MarketPriceWatches.updatedAtMillis] = now
        row[MarketPriceWatches.requestId] = request
        row[MarketPriceWatches.requestFingerprint] = digest
    }
    if (previous == null) MarketPriceWatches.insert { it[userId] = user; it[storeId] = store; write(it) }
    else MarketPriceWatches.update({ (MarketPriceWatches.userId eq user) and (MarketPriceWatches.storeId eq store) }) { write(it) }
    return requireNotNull(watchRow(user, store)).toPriceWatch()
}

/** One atomic check: a notification and its advanced baseline commit together. Read failures
 * roll back both, and competing scheduler instances recheck the due time after taking the lock.
 */
internal fun deliverPriceWatchInsideTransaction(user: UUID, store: UUID, now: Long,
    report: (UUID, UUID, MarketPriceWatchSettings, Long) -> MarketPriceInsightsPage = ::priceWatchReport,
): Boolean {
    val active = lockPriceWatchOwner(user)
    lockPriceWatch(user, store)
    // A repeatable-read snapshot may have been opened before waiting for the owner lock.
    // Lock the watch row too: PostgreSQL then rejects a stale snapshot before it can
    // calculate a second report or attempt a duplicate notification insert.
    val state = watchRow(user, store, lock = true)?.toPriceWatch() ?: return false
    val dueAt = state.nextCheckAtMillis
    if (!state.enabled || dueAt == null || dueAt > now) return false
    if (!active || !priceInsightsReaderInsideTransaction(user, store)) {
        MarketPriceWatches.update({ (MarketPriceWatches.userId eq user) and (MarketPriceWatches.storeId eq store) }) {
            it[enabled] = false; it[nextCheckAtMillis] = null; it[observations] = emptyList(); it[updatedAtMillis] = now
        }
        return false
    }
    val checked = report(user, store, state.settings, now)
    require(checked.isValidMarketPricePage(user.toString(), store.toString(), state.settings.reportRequest()))
    val evaluation = evaluateMarketPriceWatch(state.settings, state.observations, checked)
    if (evaluation.changedItemIds.isNotEmpty()) {
        val language = Users.select(Users.appLanguage).where { Users.id eq user }.single()[Users.appLanguage]
        val storeName = Stores.select(Stores.name).where { Stores.id eq store }.single()[Stores.name]
            .extractLocalizedString(language).orEmpty()
        val names = evaluation.observations.filter { it.itemId in evaluation.changedItemIds }.take(3)
            .joinToString(", ") { it.name.extractLocalizedString(language).orEmpty().take(70) }
        insertServerNotificationInsideTransaction(user, store, EventMessageReference("market.prices.changed.title"),
            EventMessageReference("market.prices.changed.body", arguments = mapOf("store" to storeName,
                "count" to evaluation.changedItemIds.size.toString(), "items" to names,
                "threshold" to (state.settings.thresholdBasisPoints / 100).toString())), category = "market_prices",
            metadata = mapOf("notificationId" to "market_prices_${user}_${store}_${state.revision}_$now",
                "storeId" to store.toString(), "country" to state.settings.country, "city" to state.settings.city,
                "priceChanges" to jsonBase.encodeToString(MarketPriceAlertDetails(user.toString(), store.toString(),
                    state.settings.country, state.settings.city, now, evaluation.changes))), nowMillis = now)
    }
    MarketPriceWatches.update({ (MarketPriceWatches.userId eq user) and (MarketPriceWatches.storeId eq store) }) {
        it[observations] = evaluation.observations; it[checkedAtMillis] = now
        it[nextCheckAtMillis] = now + MARKET_PRICE_WATCH_INTERVAL; it[updatedAtMillis] = now
    }
    return evaluation.changedItemIds.isNotEmpty()
}

internal fun Application.startMarketPriceWatches(scope: CoroutineScope) {
    scope.launch {
        while (isActive) {
            try {
                val now = System.currentTimeMillis()
                val due = newSuspendedTransaction(aitaServerIoContext) {
                    maxAttempts = 1; exec("SET LOCAL statement_timeout = '5s'")
                    MarketPriceWatches.selectAll().where { (MarketPriceWatches.enabled eq true) and (MarketPriceWatches.nextCheckAtMillis lessEq now) }
                        .orderBy(MarketPriceWatches.nextCheckAtMillis).limit(5)
                        .map { Triple(it[MarketPriceWatches.userId], it[MarketPriceWatches.storeId], it[MarketPriceWatches.revision]) }
                }
                for ((user, store, revision) in due) {
                    try {
                        val sent = newSuspendedTransaction(aitaServerIoContext, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ) {
                            maxAttempts = 1; exec("SET LOCAL statement_timeout = '10s'"); exec("SET LOCAL lock_timeout = '2s'")
                            deliverPriceWatchInsideTransaction(user, store, now)
                        }
                        if (sent) RealtimeServerBus.publish(entity = "notifications", userId = user.toString(), reason = "market_price_change")
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (problem: Exception) {
                        environment.log.error("Market price watch check failed; will retry", problem)
                        // Do not spin on an unavailable database/market or postpone a newer user edit.
                        newSuspendedTransaction(aitaServerIoContext) {
                            maxAttempts = 1; exec("SET LOCAL statement_timeout = '5s'"); exec("SET LOCAL lock_timeout = '2s'")
                            MarketPriceWatches.update({ (MarketPriceWatches.userId eq user) and (MarketPriceWatches.storeId eq store) and
                                (MarketPriceWatches.revision eq revision) and (MarketPriceWatches.enabled eq true) and
                                (MarketPriceWatches.nextCheckAtMillis lessEq now) }) { it[nextCheckAtMillis] = now + 1_800_000 }
                        }
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { environment.log.error("Market price watch scheduler unavailable", problem) }
            delay(60_000)
        }
    }
}
