package kz.aita
import kotlin.test.*

class SupportConversationJournalTest {
    private fun command()=PendingSupportMessage("account","message-123456","ticket",false,"Hello",draftRevision="edit-1")
    private fun record()=SupportConversationJournal().withSupportDraft("Hello","edit-1").prepareSupportMessage(command())
    @Test fun preparationKeepsDraftAndImmutableCommandTogether() { val r=record();assertEquals("Hello",r.draft);assertEquals(command(),r.pending) }
    @Test fun retryingTheSameCommandIsANoOp() { assertEquals(record(),record().prepareSupportMessage(command())) }
    @Test fun changingBodyForAnUnresolvedCommandIsRejected() { assertFailsWith<IllegalArgumentException> { record().prepareSupportMessage(command().copy(body="changed")) } }
    @Test fun replacingTheIdOfAnUnresolvedCommandIsRejected() { assertFailsWith<IllegalArgumentException> { record().prepareSupportMessage(command().copy(commandId="message-new")) } }
    @Test fun newDraftDoesNotChangePendingBody() { val r=record().withSupportDraft("Next question","edit-2");assertEquals("Hello",r.pending?.body);assertEquals("Next question",r.draft) }
    @Test fun acknowledgementConsumesOnlyTheOriginalDraftRevision() { val r=record().acknowledgeSupportMessage(command());assertNull(r.pending);assertEquals("",r.draft) }
    @Test fun acknowledgementRetainsANewerDifferentDraft() { val r=record().withSupportDraft("Next","edit-2").acknowledgeSupportMessage(command());assertNull(r.pending);assertEquals("Next",r.draft) }
    @Test fun acknowledgementRetainsANewerIdenticalDraft() { val r=record().withSupportDraft("Hello","edit-2").acknowledgeSupportMessage(command());assertEquals("Hello",r.draft);assertEquals("edit-2",r.draftRevision) }
    @Test fun aDelayedOldEditorWriteCannotResurrectASentDraft() { val r=record().acknowledgeSupportMessage(command()).withSupportDraft("Hello","edit-1");assertEquals("",r.draft) }
    @Test fun oldAcknowledgementCannotRetireAnotherPendingMessage() { val r=record();assertEquals(r,r.acknowledgeSupportMessage(command().copy(commandId="another-command"))) }
    @Test fun sameIdDifferentBodyCannotRetirePendingMessage() { val r=record();assertEquals(r,r.acknowledgeSupportMessage(command().copy(body="changed"))) }
    @Test fun repeatedAcknowledgementIsHarmless() { val r=record().acknowledgeSupportMessage(command());assertEquals(r,r.acknowledgeSupportMessage(command())) }
    @Test fun aNewMessageCanFollowAnAcknowledgedMessage() { val c=command().copy(body="Next",commandId="next-command",draftRevision="edit-2");assertEquals(c,record().acknowledgeSupportMessage(command()).withSupportDraft("Next","edit-2").prepareSupportMessage(c).pending) }
    @Test fun invalidMessageBodiesNeverEnterTheJournal() { for(body in listOf(""," ","x\u0000y","x".repeat(4001))) assertFailsWith<IllegalArgumentException> { SupportConversationJournal().prepareSupportMessage(command().copy(body=body)) } }
    @Test fun emptyAccountOrUnsafeIdentifierIsRejected() { for(c in listOf(command().copy(accountId=""),command().copy(commandId="bad key!"),command().copy(commandId="short"))) assertFailsWith<IllegalArgumentException> { SupportConversationJournal().prepareSupportMessage(c) } }
    @Test fun agentCannotCreateACustomerTicketThroughAgentSend() { assertFailsWith<IllegalArgumentException> { SupportConversationJournal().prepareSupportMessage(command().copy(agent=true,ticketId=null)) } }
    @Test fun customerCanPrepareANewConversation() { assertNotNull(SupportConversationJournal().prepareSupportMessage(command().copy(ticketId=null)).pending) }
}
