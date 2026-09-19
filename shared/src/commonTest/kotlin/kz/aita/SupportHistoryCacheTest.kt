package kz.aita

import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class SupportHistoryCacheTest {
    private fun ticket(id: String = "ticket", owner: String = "customer", revision: Long = 1) =
        SupportTicketDataModel(id,id,owner,subject="Help",revision=revision)
    private fun message(n: Int, id: String = "ticket", owner: String = "customer", body: String = "message $n") =
        SupportMessageDataModel("m$n",id,owner,"agent","agent",body=body,sequence=n.toLong())
    @Test fun cachedHistorySurvivesSerializationAndContainsOnlyThisCustomersMessages() {
        val page=SupportMessagePage(ticket(),listOf(message(1),message(2,owner="other"),message(3,id="other")))
        val saved=SupportHistorySnapshot("customer").withConversation(page)
        val loaded=jsonBase.decodeFromString<SupportHistorySnapshot>(jsonBase.encodeToString(saved))
        assertEquals(listOf(message(1)),loaded.conversations.single().messages)
        assertEquals("customer",loaded.tickets.single().userId)
        assertEquals(saved,saved.withConversation(SupportMessagePage(ticket(owner="other"),listOf(message(4,owner="other")))))
    }
    @Test fun cacheKeepsRecentMessagesAndAnOlderPageCursorWithinAStorageBound() {
        val page=SupportMessagePage(ticket(),(1..300).map { message(it,body="x".repeat(4000)) })
        val saved=SupportHistorySnapshot("customer").withConversation(page).conversations.single()
        assertEquals(300,saved.messages.last().sequence.toInt())
        assertTrue(saved.messages.sumOf { it.body.length }<=180_000)
        assertEquals(saved.messages.first().sequence,saved.nextBeforeSequence)
    }
    @Test fun aLateOldTicketCannotReopenAResolvedConversationInCache() {
        val closed=SupportHistorySnapshot("customer").withConversation(SupportMessagePage(ticket(revision=3).copy(status="closed"),listOf(message(1))))
        assertEquals(closed,closed.withConversation(SupportMessagePage(ticket(revision=2),listOf(message(2)))))
    }
    @Test fun oldHistoryDoesNotReplaceAnotherAccountsRecords() {
        val mixed=SupportHistorySnapshot("customer",listOf(ticket(),ticket("other","other")),listOf(
            SupportMessagePage(ticket(),listOf(message(1))),SupportMessagePage(ticket("other","other"),listOf(message(2,"other","other")))))
        assertEquals(1,mixed.bounded().tickets.size)
        assertEquals(1,mixed.bounded().conversations.size)
    }
}
