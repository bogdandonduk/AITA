package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Customer history only. Agent permissions and other customers' conversations are never cached. */
@Serializable
data class SupportHistorySnapshot(
    val accountId: String,
    val tickets: List<SupportTicketDataModel> = emptyList(),
    val conversations: List<SupportMessagePage> = emptyList()
) {
    fun bounded(): SupportHistorySnapshot {
        val ownedTickets = tickets.filter { it.userId == accountId && it.isActive }.distinctBy { it.id }
            .sortedByDescending { it.updatedAtMillis }.take(80)
        var remainingCharacters = 180_000
        val pages = conversations.filter { it.ticket.userId == accountId && it.ticket.isActive }
            .distinctBy { it.ticket.id }.take(12).map { page ->
                val messages = page.messages.filter { it.userId == accountId && it.ticketId == page.ticket.id && it.isActive }
                    .distinctBy { it.id }.sortedByDescending { it.sequence }.take(100).takeWhile {
                        remainingCharacters -= it.body.length + it.attachments.sumOf(String::length)
                        remainingCharacters >= 0
                    }.reversed()
                page.copy(messages = messages, nextBeforeSequence = if (messages.size < page.messages.size)
                    messages.firstOrNull()?.sequence ?: page.nextBeforeSequence else page.nextBeforeSequence)
            }.filter { it.messages.isNotEmpty() }
        return copy(tickets = ownedTickets, conversations = pages)
    }
    fun withConversation(page: SupportMessagePage): SupportHistorySnapshot {
        if (page.ticket.userId != accountId) return this
        val old = conversations.firstOrNull { it.ticket.id == page.ticket.id }
        if (old != null && old.ticket.revision > page.ticket.revision) return this
        return copy(tickets = listOf(page.ticket) + tickets.filterNot { it.id == page.ticket.id },
            conversations = listOf(page) + conversations.filterNot { it.ticket.id == page.ticket.id }).bounded()
    }
}

object SupportHistoryCache {
    private val writer = Mutex()
    private fun key(account: String) = "support-history-v1:$account"
    suspend fun read(account: String): SupportHistorySnapshot {
        val value = try { getLocalKv(key(account))?.let { jsonBase.decodeFromString<SupportHistorySnapshot>(it) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        return value?.takeIf { it.accountId == account }?.bounded() ?: SupportHistorySnapshot(account)
    }
    suspend fun tickets(account: String, tickets: List<SupportTicketDataModel>) = update(account) {
        it.copy(tickets = tickets + it.tickets.filterNot { old -> tickets.any { incoming -> incoming.id == old.id } }).bounded()
    }
    suspend fun conversation(account: String, page: SupportMessagePage) = update(account) { it.withConversation(page) }
    private suspend fun update(account: String, transform: (SupportHistorySnapshot) -> SupportHistorySnapshot): Boolean = writer.withLock {
        try {
            putLocalKv(key(account), jsonBase.encodeToString(transform(read(account))))
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
}
