package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Employment is a short-lived UI projection, never an offline grant or a store-owner shortcut. */
object CompanyEmployment {
    private data class Lease(val access: CompanyAccessDataModel, val received: TimeMark, val generation: Long)
    private val leases = MutableStateFlow<Lease?>(null)
    val state = leases.map { it?.access }.distinctUntilChanged()
    private val mutex = Mutex()
    private val refreshScope=CoroutineScope(SupervisorJob()+Dispatchers.ourIo)
    fun refreshSoon() { refreshScope.launch { refresh() } }
    fun clear() { leases.value = null }
    fun expire() {
        val proof = leases.value ?: return
        if (proof.access.userId != userAccountState.payloadValue?.id ||
            !authenticatedSessionGenerationIsCurrent(proof.generation) ||
            proof.received.elapsedNow().inWholeMilliseconds >=
                (proof.access.validUntilMillis - proof.access.serverTimeMillis).coerceIn(0, 45_000)) {
            // An expiring old lease must not clear a concurrently installed fresh one.
            leases.compareAndSet(proof, null)
        }
    }
    fun has(capability: String): Boolean {
        expire()
        return capability in leases.value?.access?.capabilities.orEmpty()
    }
    suspend fun refresh() {
        val user = userAccountState.payloadValue?.id ?: run { clear(); return }
        val generation=currentAuthenticatedSessionGeneration()
        mutex.withLock {
            if(userAccountState.payloadValue?.id!=user || !authenticatedSessionGenerationIsCurrent(generation)) return@withLock
            val recent=leases.value
            if(recent!=null && recent.generation==generation && recent.access.userId==user &&
                recent.received.elapsedNow().inWholeMilliseconds<1_000) return@withLock
            val started = TimeSource.Monotonic.markNow()
            val response=networkRequest<CompanyAccessDataModel,Unit>(HttpMethod.Get,endpointUrl="company/me",expectedSessionGeneration=generation)
            if(userAccountState.payloadValue?.id!=user || !authenticatedSessionGenerationIsCurrent(generation)) return@withLock
            val data=response.payload
            if(response.negative || data?.userId!=user || data.serverTimeMillis<=0) { clear(); return@withLock }
            // Use the request start, not receipt time: a delayed network response cannot extend the lease.
            leases.value=Lease(data,started,generation); expire()
        }
    }
}

object SupportWorkspaceSignals {
    private val mutableRevision=MutableStateFlow(0L)
    val revision=mutableRevision.asStateFlow()
    fun changed() { mutableRevision.update { it+1L } }
}


data class SupportDeliveryResult(
    val ticketId: String?,
    val message: SupportMessageDataModel?,
    val error: List<LocalizedStringDataModel>? = null,
    val uncertain: Boolean = false
)

/** A saved immutable command is retryable after a lost reply/restart. No automatic offline sending.
 * One serialized KV record holds the current draft AND its pending command. Network I/O does not
 * hold the storage writer, so a newer draft can be saved while an earlier message is in flight.
 */
object SupportDelivery {
    private val sender = Mutex()
    private val writer = Mutex()
    private fun key(account: String, agent: Boolean, ticket: String?) =
        "support-conversation-v1:$account:$agent:${ticket ?: "new"}"

    suspend fun journal(account: String, agent: Boolean, ticket: String?): SupportConversationJournal {
        val value = getLocalKv(key(account, agent, ticket))
            ?.let { jsonBase.decodeFromString<SupportConversationJournal>(it) } ?: SupportConversationJournal()
        check(value.pending?.let { it.accountId == account && it.agent == agent && it.ticketId == ticket } != false)
        return value
    }
    suspend fun pending(account: String, agent: Boolean, ticket: String?): PendingSupportMessage? =
        journal(account, agent, ticket).pending

    suspend fun saveDraft(account: String, agent: Boolean, ticket: String?, text: String, revision: String) = writer.withLock {
        val record = journal(account, agent, ticket)
        // A canceled/debounced old editor write cannot resurrect the acknowledged draft.
        val next=record.withSupportDraft(text,revision)
        if(next!=record) putLocalKv(key(account, agent, ticket), jsonBase.encodeToString(next))
    }

    suspend fun send(command: PendingSupportMessage): SupportDeliveryResult = sender.withLock {
        val generation = currentAuthenticatedSessionGeneration()
        fun owned() = userAccountState.payloadValue?.id == command.accountId &&
            authenticatedSessionGenerationIsCurrent(generation)
        if (!owned()) return@withLock SupportDeliveryResult(null, null, eventMessage("support.denied"))
        if (command.agent && !CompanyEmployment.has(CompanyCapability.SUPPORT_REPLY))
            return@withLock SupportDeliveryResult(null, null, eventMessage("support.denied"))
        val storageKey = key(command.accountId, command.agent, command.ticketId)
        try {
            writer.withLock {
                val record = journal(command.accountId, command.agent, command.ticketId)
                putLocalKv(storageKey, jsonBase.encodeToString(record.prepareSupportMessage(command)))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock SupportDeliveryResult(null, null, eventMessage("support.save_failed")) }
        if (!owned()) return@withLock SupportDeliveryResult(null, null, eventMessage("support.send_unknown"), true)

        val meta = mapOf("clientLanguage" to command.language)
        val result = if (command.ticketId == null) {
            val response = networkRequest<SupportTicketDataModel, SupportTicketCreateRequestDataModel>(
                HttpMethod.Post, endpointUrl = "support/tickets/create",
                body = SupportTicketCreateRequestDataModel(command.body.take(80), command.body,
                    category = command.category, storeId = command.storeId, metadata = meta,
                    clientMessageId = command.commandId), expectedSessionGeneration = generation)
            val ticket = response.payload
            if (!response.negative && ticket?.userId == command.accountId && ticket.id.isNotBlank()) SupportDeliveryResult(ticket.id, null)
            else SupportDeliveryResult(null, null, response.message ?: eventMessage("support.send_unknown"), true)
        } else {
            val response = networkRequest<SupportMessageDataModel, SupportMessageSendRequestDataModel>(
                HttpMethod.Post, endpointUrl = if (command.agent) "support/agent/messages/send" else "support/messages/send",
                body = SupportMessageSendRequestDataModel(command.ticketId, command.body, metadata = meta,
                    clientMessageId = command.commandId), expectedSessionGeneration = generation)
            val message = response.payload
            if (!response.negative && message?.ticketId == command.ticketId && message.senderUserId == command.accountId) SupportDeliveryResult(message.ticketId, message)
            else SupportDeliveryResult(null, null, response.message ?: eventMessage("support.send_unknown"), true)
        }
        if (!owned()) return@withLock SupportDeliveryResult(null, null, eventMessage("support.send_unknown"), true)
        if (result.ticketId != null) {
            try {
                writer.withLock {
                    val record = journal(command.accountId, command.agent, command.ticketId)
                    val next=record.acknowledgeSupportMessage(command)
                    if(next!=record) putLocalKv(storageKey,jsonBase.encodeToString(next))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // The server acknowledged, but local retirement failed: keep retry identity visible.
                return@withLock result.copy(error = eventMessage("support.send_unknown"), uncertain = true)
            }
            SupportWorkspaceSignals.changed()
        }
        // Even a later 4xx cannot disprove an earlier timed-out commit. Only an acknowledged
        // replay retires the command; closing/reopening/reassignment never invents a new ID.
        result
    }
}
