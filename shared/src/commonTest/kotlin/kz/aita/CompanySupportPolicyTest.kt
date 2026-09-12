package kz.aita

import kotlin.test.*

class CompanySupportPolicyTest {
    private fun access()=CompanyAccessDataModel("agent","employment",capabilities=setOf(CompanyCapability.SUPPORT_QUEUE),serverTimeMillis=100,validUntilMillis=200)
    private fun ticket()=SupportTicketDataModel("ticket","S-1","customer",subject="Question",assignedAgentUserId="agent")
    @Test fun anAnonymousUserNeverSeesAgentSurface() { assertFalse(companyCanOpenSupport(access(),null)) }
    @Test fun unloadedEmploymentNeverShowsAgentSurface() { assertFalse(companyCanOpenSupport(null,"agent")) }
    @Test fun anotherAccountsCapabilityCannotRevealASurface() { assertFalse(companyCanOpenSupport(access(),"another")) }
    @Test fun capabilityWithoutEmploymentDoesNotGrant() { assertFalse(companyCanOpenSupport(access().copy(employmentId=null),"agent")) }
    @Test fun jobTitleIsNotAnAuthority() { assertFalse(companyCanOpenSupport(access().copy(capabilities=emptySet(),jobs=listOf(CompanyJobDataModel("support_agent",mapOf("en" to "Support agent")))),"agent")) }
    @Test fun matchingEmploymentAndCapabilityRevealTheSurface() { assertTrue(companyCanOpenSupport(access(),"agent")) }
    @Test fun queueReadAloneDoesNotAllowReplies() { assertFalse(supportCanReply(ticket(),"agent",true,setOf(CompanyCapability.SUPPORT_QUEUE))) }
    @Test fun assignedReplyCapabilityCanReply() { assertTrue(supportCanReply(ticket(),"agent",true,setOf(CompanyCapability.SUPPORT_REPLY))) }
    @Test fun anotherAgentCannotReplyToAssignedWork() { assertFalse(supportCanReply(ticket(),"another",true,setOf(CompanyCapability.SUPPORT_REPLY))) }
    @Test fun managerStillClaimsBeforeSending() { assertFalse(supportCanReply(ticket(),"boss",true,setOf(CompanyCapability.SUPPORT_REPLY,CompanyCapability.SUPPORT_MANAGE))) }
    @Test fun userCannotReplyToSomeoneElsesCustomerChat() { assertFalse(supportCanReply(ticket(),"outsider",false,emptySet())) }
    @Test fun customerNeedsNoSubscriptionOrEmployment() { assertTrue(supportCanReply(ticket(),"customer",false,emptySet())) }
    @Test fun customerCannotHandleTheirOwnTicketAsAgent() { assertFalse(supportCanReply(ticket().copy(assignedAgentUserId="customer"),"customer",true,setOf(CompanyCapability.SUPPORT_REPLY))) }
    @Test fun closedOrInactiveConversationCannotReceiveReplies() {
        for(t in listOf(ticket().copy(status="closed"),ticket().copy(isActive=false))) {
            assertFalse(supportCanReply(t,"agent",true,setOf(CompanyCapability.SUPPORT_REPLY)))
            assertFalse(supportCanReply(t,"customer",false,emptySet()))
        }
    }
}
