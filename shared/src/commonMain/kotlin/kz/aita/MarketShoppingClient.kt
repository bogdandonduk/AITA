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
    val journalRevision: Long = -1L,
    val acknowledgedCommandId: String? = null,
    val notice: List<LocalizedStringDataModel>? = null,
    val cancellingCommandId: String? = null
)

/** Injectable journal/transport boundary: the production instance below owns the one writer.
 * Tests can exercise crashes and lost replies without pointing anything at a customer's database.
 */
class MarketShoppingDeliveryStore(
    private val readValue: suspend (String) -> String?,
    private val writeValue: suspend (String, String?) -> Unit,
    private val loadRemote: suspend (MarketAccountScope) -> ResponseDataModel<MarketShoppingSnapshot>,
    private val sendRemote: suspend (MarketAccountScope, MarketShoppingCommand) -> ResponseDataModel<MarketShoppingOutcome>,
    private val onChanged: () -> Unit = {},
    private val lookupRemote: suspend (MarketAccountScope, MarketShoppingCommand) -> ResponseDataModel<MarketShoppingCommandLookup> = { _, _ ->
        ResponseDataModel(eventMessage("market.shopping_result_upgrade"), null, true, 404)
    },
    private val cancelRemote: suspend (MarketAccountScope, MarketShoppingCommand) -> ResponseDataModel<MarketShoppingOutcome> = { _, _ ->
        ResponseDataModel(eventMessage("market.shopping_cancel_upgrade"), null, true, 404)
    }
) {
    private val sender = Mutex()
    private val writer = Mutex()
    private fun key(account: String) = "buyer-shopping-journal-v1:$account"

    private suspend fun read(account: String): MarketShoppingJournal {
        val journal = readValue(key(account))?.let { jsonBase.decodeFromString<MarketShoppingJournal>(it) }
            ?: MarketShoppingJournal(account)
        check(journal.accountId == account && journal.snapshot?.userId?.let { it == account } != false &&
            journal.pending?.accountId?.let { it == account } != false)
        check(journal.localRevision >= 0L && journal.hasValidCancellationIntent())
        journal.snapshot?.let { check(it.isValidMarketShoppingSnapshot(account)) }
        journal.pending?.command?.let { check(it.isValidMarketShoppingCommand()) }
        return journal
    }
    private suspend fun write(journal: MarketShoppingJournal) {
        val encoded = jsonBase.encodeToString(journal)
        writeValue(key(journal.accountId), encoded)
        check(readValue(key(journal.accountId)) == encoded) { "Shopping journal was not persisted" }
    }
    private fun result(journal: MarketShoppingJournal, error: String? = null) =
        MarketShoppingClientResult(journal.snapshot, journal.pending, error?.let(::eventMessage),
            journalRevision = journal.localRevision, cancellingCommandId = journal.cancellingCommandId)

    suspend fun cached(scope: MarketAccountScope): MarketShoppingClientResult = writer.withLock {
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_denied"))
        try {
            val journal = read(scope.accountId)
            if (scope.isCurrent()) result(journal) else MarketShoppingClientResult()
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (scope.isCurrent()) MarketShoppingClientResult(error = eventMessage("market.shopping_storage")) else MarketShoppingClientResult() }
    }

    suspend fun refresh(scope: MarketAccountScope): MarketShoppingClientResult {
        if (!scope.isCurrent()) return MarketShoppingClientResult(error = eventMessage("market.shopping_denied"))
        val response = loadRemote(scope)
        if (!scope.isCurrent()) return MarketShoppingClientResult()
        return writer.withLock {
            val journal = try { read(scope.accountId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_storage")) }
            if (!scope.isCurrent()) return@withLock MarketShoppingClientResult()
            val data = response.payload
            if (response.negative || data?.isValidMarketShoppingSnapshot(scope.accountId) != true) return@withLock result(journal).copy(
                error = response.message ?: eventMessage("market.shopping_refresh"))
            val next = journal.withSnapshot(data)
            try { if (scope.isCurrent()) write(next) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withLock if (scope.isCurrent())
                result(next, "market.shopping_storage").copy(fresh = true, journalRevision = journal.localRevision) else MarketShoppingClientResult() }
            if (scope.isCurrent()) result(next).copy(fresh = true) else MarketShoppingClientResult()
        }
    }

    /** Prepare durably before any network write. All screens share these two writers. */
    suspend fun change(scope: MarketAccountScope, command: MarketShoppingCommand): MarketShoppingClientResult = sender.withLock {
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_denied"))
        if (!command.isValidMarketShoppingCommand()) return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_invalid"))
        val pending = PendingMarketShoppingCommand(scope.accountId, command)
        val prepared = writer.withLock prepare@{
            val current = try { read(scope.accountId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@prepare null }
            // A storage read or another writer may have suspended while the account/session
            // changed. Never prepare new work for an owner that is no longer current.
            if (!scope.isCurrent() || (current.pending != null && current.pending != pending)) return@prepare current
            if (current.cancellingCommandId != null) return@prepare current
            // Another view/queued writer can advance the durable list while this caller waits.
            // Refuse NEW stale intent before preparing or sending it. An identical pending
            // command is different: it may already have committed and must remain replayable.
            if (current.pending != pending && current.snapshot?.revision?.let { it != command.expectedRevision } == true)
                return@prepare current
            if (command.basketChange != null && current.pending != pending &&
                current.snapshot?.let { command.basketChange.basketIntentError(it, command.expectedRevision) } != null)
                return@prepare current
            if (command.checklistChange != null && current.pending != pending &&
                current.snapshot?.let { command.checklistChange.checklistIntentError(it, command.expectedRevision) } != null)
                return@prepare current
            try { current.prepare(pending).also { write(it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } ?: return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_storage"))
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult()
        if (prepared.cancellingCommandId != null) return@withLock result(prepared, "market.shopping_cancel_pending")
        if (prepared.pending != pending) return@withLock result(prepared,
            if (prepared.pending == null) "market.shopping_changed" else "market.shopping_pending")
        submit(scope, pending, prepared.localRevision)
    }

    /** Remember cancellation BEFORE sending it. A lost cancellation reply or restart must not
     * turn Retry into another application of the very change the buyer chose to stop.
     */
    suspend fun cancelPending(scope: MarketAccountScope, expected: MarketShoppingCommand): MarketShoppingClientResult = sender.withLock {
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_denied"))
        val journal = writer.withLock prepareCancellation@{
            val current = try { read(scope.accountId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@prepareCancellation null }
            if (!scope.isCurrent() || current.pending?.command != expected) return@prepareCancellation current
            val next = current.requestCancellation(PendingMarketShoppingCommand(scope.accountId, expected))
            try { write(next); next }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } ?: return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_storage"))
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult()
        if (journal.pending?.command != expected) return@withLock result(journal, "market.shopping_recovery_changed")
        sendCancellation(scope, journal)
    }

    private suspend fun sendCancellation(scope: MarketAccountScope, journal: MarketShoppingJournal): MarketShoppingClientResult {
        val pending = journal.pending ?: return result(journal)
        check(journal.cancellingCommandId == pending.command.commandId)
        if (!scope.isCurrent()) return MarketShoppingClientResult()
        val response = try { cancelRemote(scope, pending.command) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return if (scope.isCurrent()) result(journal, "market.shopping_cancel_failed") else MarketShoppingClientResult() }
        if (!scope.isCurrent()) return MarketShoppingClientResult()
        val outcome = response.payload
        if (response.negative || outcome == null || !pending.command.isValidShoppingOutcome(outcome, scope.accountId))
            return result(journal).copy(error = if (response.httpStatusCode == 404) eventMessage("market.shopping_cancel_upgrade")
                else response.message ?: eventMessage("market.shopping_cancel_failed"))
        return acknowledgeRecorded(scope, pending, outcome, journal.localRevision)
    }

    /** Explicit recovery only. A retry never creates a new command or repeats an increment. */
    suspend fun retry(scope: MarketAccountScope): MarketShoppingClientResult = sender.withLock {
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_denied"))
        val journal = try { writer.withLock { read(scope.accountId) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_storage")) }
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult()
        val pending = journal.pending ?: return@withLock result(journal)
        if (journal.cancellingCommandId != null) sendCancellation(scope, journal)
        else submit(scope, pending, journal.localRevision)
    }

    /** Does not retry the write. A missing record leaves the original pending identity intact,
     * even if the review is now expired: an earlier in-flight transaction can still commit.
     */
    suspend fun checkResult(scope: MarketAccountScope): MarketShoppingClientResult = sender.withLock {
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_denied"))
        val journal = try { writer.withLock { read(scope.accountId) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock MarketShoppingClientResult(error = eventMessage("market.shopping_storage")) }
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult()
        val pending = journal.pending ?: return@withLock result(journal)
        val response = try { lookupRemote(scope, pending.command) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock if (scope.isCurrent()) result(journal, "market.shopping_result_failed") else MarketShoppingClientResult() }
        if (!scope.isCurrent()) return@withLock MarketShoppingClientResult()
        val lookup = response.payload
        if (response.negative || lookup?.isValidShoppingLookup(pending.command, scope.accountId) != true)
            return@withLock result(journal).copy(error = if (response.httpStatusCode == 404) eventMessage("market.shopping_result_upgrade")
                else response.message ?: eventMessage("market.shopping_result_failed"))
        val outcome = lookup.outcome
        if (outcome == null) return@withLock writer.withLock latestRead@{
            val latest = try { read(scope.accountId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@latestRead result(journal, "market.shopping_storage") }
            if (!scope.isCurrent()) return@latestRead MarketShoppingClientResult()
            result(latest).copy(notice = eventMessage("market.shopping_not_recorded"))
        }
        acknowledgeRecorded(scope, pending, outcome, journal.localRevision)
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
        val valid = !response.negative && outcome != null && pending.command.isValidShoppingOutcome(outcome, scope.accountId)
        if (!valid || outcome == null) {
            // A later 4xx cannot disprove a previous response-lost commit. Only a recorded outcome
            // retires the local identity. Unknown/malformed responses keep recovery visible.
            return unresolved(when {
                pending.command.checklistChange != null && response.httpStatusCode == 404 -> eventMessage("market.checklist_upgrade")
                pending.command.basketChange != null && response.httpStatusCode == 404 -> eventMessage("market.basket_apply_upgrade")
                else -> response.message ?: eventMessage("market.shopping_pending")
            })
        }
        return acknowledgeRecorded(scope, pending, outcome, preparedRevision)
    }

    private suspend fun acknowledgeRecorded(scope: MarketAccountScope, pending: PendingMarketShoppingCommand,
        outcome: MarketShoppingOutcome, preparedRevision: Long): MarketShoppingClientResult {
        fun unresolved(error: List<LocalizedStringDataModel>?) = MarketShoppingClientResult(
            pending = pending, error = error, journalRevision = preparedRevision)
        return writer.withLock {
            val journal = try { read(scope.accountId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withLock unresolved(eventMessage("market.shopping_storage")) }
            if (!scope.isCurrent()) return@withLock unresolved(null)
            val next = journal.acknowledge(pending, outcome)
            try { write(next) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                return@withLock if (scope.isCurrent()) MarketShoppingClientResult(outcome.snapshot, pending,
                    eventMessage("market.shopping_storage"), fresh = true, journalRevision = journal.localRevision,
                    cancellingCommandId = journal.cancellingCommandId) else MarketShoppingClientResult()
            }
            // Persist an already-validated outcome for its original account, but never publish
            // the old account's result/signal after a suspended write crosses an owner change.
            if (!scope.isCurrent()) return@withLock MarketShoppingClientResult()
            onChanged()
            val notice = when {
                outcome.errorKey == "market.shopping_cancelled" -> eventMessage("market.shopping_cancelled")
                journal.cancellingCommandId != null && outcome.accepted -> eventMessage("market.shopping_cancel_already_applied")
                else -> null
            }
            result(next, outcome.errorKey.takeUnless { notice != null }).copy(acknowledged = true,
                accepted = outcome.accepted, fresh = true, acknowledgedCommandId = pending.command.commandId, notice = notice)
        }
    }
}

object MarketShoppingDelivery {
    private val store = MarketShoppingDeliveryStore(::getLocalKv, ::putLocalKv,
        loadRemote = { scope -> networkRequest<MarketShoppingSnapshot, Unit>(HttpMethod.Get,
            endpointUrl = "market/shopping-list", expectedSessionGeneration = scope.generation) },
        sendRemote = { scope, command -> networkRequest<MarketShoppingOutcome, MarketShoppingCommand>(HttpMethod.Put,
            endpointUrl = command.shoppingMutationEndpoint(),
            body = command, expectedSessionGeneration = scope.generation) },
        onChanged = MarketplaceSignals::changed,
        lookupRemote = { scope, command -> networkRequest<MarketShoppingCommandLookup, MarketShoppingCommand>(HttpMethod.Post,
            endpointUrl = "market/shopping-list/result", body = command, expectedSessionGeneration = scope.generation) },
        cancelRemote = { scope, command -> networkRequest<MarketShoppingOutcome, MarketShoppingCommand>(HttpMethod.Post,
            endpointUrl = "market/shopping-list/cancel", body = command, expectedSessionGeneration = scope.generation) })
    suspend fun checkResult(scope: MarketAccountScope) = withContext(Dispatchers.ourIo) { store.checkResult(scope) }
    suspend fun cancelPending(scope: MarketAccountScope, expected: MarketShoppingCommand) =
        withContext(Dispatchers.ourIo) { store.cancelPending(scope, expected) }
    suspend fun cached(scope: MarketAccountScope) = withContext(Dispatchers.ourIo) { store.cached(scope) }
    suspend fun refresh(scope: MarketAccountScope) = withContext(Dispatchers.ourIo) { store.refresh(scope) }
    suspend fun change(scope: MarketAccountScope, command: MarketShoppingCommand) = withContext(Dispatchers.ourIo) { store.change(scope, command) }
    suspend fun retry(scope: MarketAccountScope) = withContext(Dispatchers.ourIo) { store.retry(scope) }
}
