package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Cache/retry journal is account-scoped and deliberately independent of the active merchant store. */
data class MarketShoppingClientResult(
    val snapshot: MarketShoppingSnapshot? = null,
    val pending: PendingMarketShoppingCommand? = null,
    val error: List<LocalizedStringDataModel>? = null,
    val acknowledged: Boolean = false,
    val accepted: Boolean = false,
    val fresh: Boolean = false,
    val journalRevision: Long = -1L
)

/** Injectable journal/transport boundary: the production instance below owns the one writer.
 * Tests can exercise crashes and lost replies without pointing anything at a customer's database.
 */
class MarketShoppingDeliveryStore(
    private val readValue: suspend (String) -> String?,
    private val writeValue: suspend (String, String?) -> Unit,
    private val loadRemote: suspend (MarketAccountScope) -> ResponseDataModel<MarketShoppingSnapshot>,
    private val sendRemote: suspend (MarketAccountScope, MarketShoppingCommand) -> ResponseDataModel<MarketShoppingOutcome>,
    private val onChanged: () -> Unit = {}
) {
    private val sender = Mutex()
    private val writer = Mutex()
    private fun key(account: String) = "buyer-shopping-journal-v1:$account"

    private suspend fun read(account: String): MarketShoppingJournal {
        val journal = readValue(key(account))?.let { jsonBase.decodeFromString<MarketShoppingJournal>(it) }
            ?: MarketShoppingJournal(account)
        check(journal.accountId == account && journal.snapshot?.userId?.let { it == account } != false &&
            journal.pending?.accountId?.let { it == account } != false)
        check(journal.localRevision >= 0L)
        journal.snapshot?.let { data ->
            check(data.revision >= 0L && data.lines.size <= MARKET_SHOPPING_MAX_LINES)
            check(data.lines.map { it.line.offerId }.distinct().size == data.lines.size)
            check(data.lines.all { it.line.units in 1..MARKET_SHOPPING_MAX_UNITS && it.line.basis.isValidMarketBasis() &&
                (it.subtotalMinor == null || it.subtotalMinor >= 0L) })
        }
        journal.pending?.command?.let { command ->
            check(command.expectedRevision >= 0L && command.units in 0..MARKET_SHOPPING_MAX_UNITS &&
                (if (command.units == 0) command.basis == null else command.basis?.isValidMarketBasis() == true))
        }
        return journal
    }
    private suspend fun write(journal: MarketShoppingJournal) {
        val encoded = jsonBase.encodeToString(journal)
        writeValue(key(journal.accountId), encoded)
        check(readValue(key(journal.accountId)) == encoded) { "Shopping journal was not persisted" }
    }
    private fun result(journal: MarketShoppingJournal, error: String? = null) =
        MarketShoppingClientResult(journal.snapshot, journal.pending, error?.let(::eventMessage), journalRevision = journal.localRevision)

    suspend fun cached(scope: MarketAccountScope): MarketShoppingClientResult = writer.withLock {
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_denied"))
        try { result(read(scope.accountId)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { MarketShoppingClientResult(error = eventMessage("market.shopping_storage")) }
    }

    suspend fun refresh(scope: MarketAccountScope): MarketShoppingClientResult {
        if (!scope.isCurrent()) return MarketShoppingClientResult(error = eventMessage("market.shopping_denied"))
        val response = loadRemote(scope)
        if (!scope.isCurrent()) return MarketShoppingClientResult()
        return writer.withLock {
            val journal = try { read(scope.accountId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_storage")) }
            val data = response.payload
            if (response.negative || data?.userId != scope.accountId) return@withLock result(journal).copy(
                error = response.message ?: eventMessage("market.shopping_refresh"))
            val next = journal.withSnapshot(data)
            try { if (scope.isCurrent()) write(next) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withLock result(next, "market.shopping_storage").copy(fresh = true, journalRevision = journal.localRevision) }
            result(next).copy(fresh = true)
        }
    }

    /** Prepare durably before any network write. All screens share these two writers. */
    suspend fun change(scope: MarketAccountScope, command: MarketShoppingCommand): MarketShoppingClientResult = sender.withLock {
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_denied"))
        val pending = PendingMarketShoppingCommand(scope.accountId, command)
        val prepared = writer.withLock prepare@{
            val current = try { read(scope.accountId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@prepare null }
            if (current.pending != null && current.pending != pending) return@prepare current
            try { current.prepare(pending).also { write(it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } ?: return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_storage"))
        if (prepared.pending != pending) return@withLock result(prepared, "market.shopping_pending")
        submit(scope, pending, prepared.localRevision)
    }

    /** Explicit recovery only. A retry never creates a new command or repeats an increment. */
    suspend fun retry(scope: MarketAccountScope): MarketShoppingClientResult = sender.withLock {
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_denied"))
        val journal = try { writer.withLock { read(scope.accountId) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_storage")) }
        val pending = journal.pending ?: return@withLock result(journal)
        submit(scope, pending, journal.localRevision)
    }

    private suspend fun submit(scope: MarketAccountScope, pending: PendingMarketShoppingCommand, preparedRevision: Long): MarketShoppingClientResult {
        // Even an unconfirmed result carries the durable prepare sequence. A GET which completed
        // before prepare must not clear its pending marker when callbacks arrive out of order.
        fun unresolved(error: List<LocalizedStringDataModel>? = eventMessage("market.shopping_pending")) =
            MarketShoppingClientResult(pending = pending, error = error, journalRevision = preparedRevision)
        if (!scope.isCurrent()) return unresolved()
        val response = try {
            sendRemote(scope, pending.command)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return unresolved() }
        if (!scope.isCurrent()) return unresolved(null)
        val outcome = response.payload
        val valid = !response.negative && outcome != null && outcome.commandId == pending.command.commandId &&
            outcome.snapshot.userId == scope.accountId && outcome.snapshot.revision >= 0 &&
            (if (outcome.accepted) outcome.appliedRevision?.let { it in 0..outcome.snapshot.revision } == true && outcome.errorKey == null
                else outcome.appliedRevision == null && !outcome.errorKey.isNullOrBlank())
        if (!valid || outcome == null) {
            // A later 4xx cannot disprove a previous response-lost commit. Only a recorded outcome
            // retires the local identity. Unknown/malformed responses keep recovery visible.
            return unresolved(response.message ?: eventMessage("market.shopping_pending"))
        }
        return writer.withLock {
            val journal = try { read(scope.accountId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withLock unresolved(eventMessage("market.shopping_storage")) }
            val next = journal.acknowledge(pending, outcome)
            try { write(next) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                return@withLock MarketShoppingClientResult(outcome.snapshot, pending, eventMessage("market.shopping_storage"), fresh = true, journalRevision = journal.localRevision)
            }
            onChanged()
            result(next, outcome.errorKey).copy(acknowledged = true, accepted = outcome.accepted, fresh = true)
        }
    }
}

object MarketShoppingDelivery {
    private val store = MarketShoppingDeliveryStore(::getLocalKv, ::putLocalKv,
        loadRemote = { scope -> networkRequest<MarketShoppingSnapshot, Unit>(HttpMethod.Get,
            endpointUrl = "market/shopping-list", expectedSessionGeneration = scope.generation) },
        sendRemote = { scope, command -> networkRequest<MarketShoppingOutcome, MarketShoppingCommand>(HttpMethod.Put,
            endpointUrl = "market/shopping-list", body = command, expectedSessionGeneration = scope.generation) },
        onChanged = MarketplaceSignals::changed)
    suspend fun cached(scope: MarketAccountScope) = withContext(Dispatchers.ourIo) { store.cached(scope) }
    suspend fun refresh(scope: MarketAccountScope) = withContext(Dispatchers.ourIo) { store.refresh(scope) }
    suspend fun change(scope: MarketAccountScope, command: MarketShoppingCommand) = withContext(Dispatchers.ourIo) { store.change(scope, command) }
    suspend fun retry(scope: MarketAccountScope) = withContext(Dispatchers.ourIo) { store.retry(scope) }
}
