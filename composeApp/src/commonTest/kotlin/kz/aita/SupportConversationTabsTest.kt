package kz.aita

import kotlin.test.*

class SupportConversationTabsTest {
    private fun ticket(id:String="t1",revision:Long=1)=SupportTicketDataModel(id,"public","customer",subject="Printer question",revision=revision)
    @Test fun fixedChatsTabAndTwoConversationRolesHaveDifferentIds() {
        assertNotEquals("chat",supportConversationTabId(false,"t1"))
        assertNotEquals(supportConversationTabId(false,"t1"),supportConversationTabId(true,"t1"))
        assertNotEquals(supportConversationTabId(false,null),supportConversationTabId(false,"t1"))
    }
    @Test fun customerCanResolveOnlyTheirActiveConversation() {
        assertTrue(supportTabCanResolve(ticket(),"customer",false,emptySet()))
        assertFalse(supportTabCanResolve(ticket(),"other",false,emptySet()))
        assertFalse(supportTabCanResolve(ticket().copy(isActive=false),"customer",false,emptySet()))
        assertFalse(supportTabCanResolve(ticket().copy(status="closed"),"customer",false,emptySet()))
    }
    @Test fun agentNeedsCurrentCapabilityAndAssignmentOrManager() {
        val assigned=ticket().copy(assignedAgentUserId="agent")
        assertTrue(supportTabCanResolve(assigned,"agent",true,setOf(CompanyCapability.SUPPORT_RESOLVE)))
        assertFalse(supportTabCanResolve(assigned,"agent",true,emptySet()))
        assertFalse(supportTabCanResolve(ticket(),"agent",true,setOf(CompanyCapability.SUPPORT_RESOLVE)))
        assertTrue(supportTabCanResolve(ticket(),"manager",true,setOf(CompanyCapability.SUPPORT_RESOLVE,CompanyCapability.SUPPORT_MANAGE)))
        assertFalse(supportTabCanResolve(ticket(),"customer",true,setOf(CompanyCapability.SUPPORT_RESOLVE,CompanyCapability.SUPPORT_MANAGE)))
    }
    @Test fun delayedReadCannotUndoConfirmedResolution() {
        val closed=ticket(revision=3).copy(status="closed")
        assertEquals(closed,newestSupportTabTicket(closed,ticket(revision=2),"t1"))
        assertEquals(closed,newestSupportTabTicket(closed,ticket("other",10),"t1"))
        assertEquals(ticket(revision=4),newestSupportTabTicket(closed,ticket(revision=4),"t1"))
    }
    @Test fun tabDraftsAndSelectionSurviveNavigationWithinOneSession() {
        val books=SupportConversationBooks();val book=books.forOwner("customer",1)
        val tab=SupportConversationTab(false,"t1",SupportConversationMemory(ticket()))
        tab.memory.draft="Unsent text";tab.memory.edited=true
        book.conversations.add(tab);book.selected=tab.key
        assertSame(book,books.forOwner("customer",1))
        assertEquals(tab.key,books.forOwner("customer",1).selected)
        assertEquals("Unsent text",books.forOwner("customer",1).conversations.single().memory.draft)
    }
    @Test fun accountChangeLogoutAndNewSessionClearTabs() {
        val books=SupportConversationBooks()
        books.forOwner("customer",1).conversations.add(SupportConversationTab(false,"t1",SupportConversationMemory(ticket())))
        assertTrue(books.forOwner("customer",2).conversations.isEmpty())
        assertEquals("chat",books.forOwner("other",2).selected)
        assertTrue(books.forOwner(null,3).conversations.isEmpty())
    }
    @Test fun closingBeforeInitialReadStillSavesNewlyTypedDraft() {
        assertFalse(supportDraftNeedsSaving(loaded=false,edited=false))
        assertTrue(supportDraftNeedsSaving(loaded=false,edited=true))
        assertTrue(supportDraftNeedsSaving(loaded=true,edited=false))
        assertTrue(supportDraftNeedsSaving(loaded=true,edited=true))
    }
}
