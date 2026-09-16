package kz.aita

import androidx.compose.runtime.*

internal fun supportConversationTabId(agent:Boolean,ticketId:String?):String =
    "conversation:${if(agent) "agent" else "customer"}:${ticketId ?: "new"}"

internal fun supportTabCanResolve(ticket:SupportTicketDataModel?,account:String,agent:Boolean,capabilities:Set<String>):Boolean {
    if(ticket==null || !ticket.isActive || ticket.status=="closed")return false
    return if(!agent) ticket.userId==account else ticket.userId!=account &&
        CompanyCapability.SUPPORT_RESOLVE in capabilities &&
        (ticket.assignedAgentUserId==account || CompanyCapability.SUPPORT_MANAGE in capabilities)
}

internal data class SupportConversationTab(val agent:Boolean,val ticketId:String?,val memory:SupportConversationMemory) {
    val key:String get()=supportConversationTabId(agent,ticketId)
}

/** Retained per open tab and sign-in generation, never shared across accounts. */
internal class SupportConversationMemory(initialTicket:SupportTicketDataModel?=null) {
    var ticket by mutableStateOf<SupportTicketDataModel?>(initialTicket)
    var messages by mutableStateOf<List<SupportMessageDataModel>>(emptyList())
    var before by mutableStateOf<Long?>(null)
    var loading by mutableStateOf(initialTicket!=null)
    var loadingMore by mutableStateOf(false)
    var refresh by mutableStateOf(0)
    var feedback by mutableStateOf<List<LocalizedStringDataModel>?>(null)
    var draft by mutableStateOf("")
    var draftRevision by mutableStateOf(newClientSideUuidString())
    var draftLoaded by mutableStateOf(false)
    var edited by mutableStateOf(false)
    var pending by mutableStateOf<PendingSupportMessage?>(null)
    var sending by mutableStateOf(false)
    var acting by mutableStateOf(false)
    var category by mutableStateOf("general")
    var hasNew by mutableStateOf(false)
    val list=androidx.compose.foundation.lazy.LazyListState()
    var closing by mutableStateOf(false)
}

/** Stable across navigation and width changes, but only for one exact authenticated session. */
internal class SupportConversationBook {
    var selected by mutableStateOf("chat")
    val conversations=mutableStateListOf<SupportConversationTab>()
}
internal class SupportConversationBooks {
    private var owner:Pair<String?,Long>?=null
    private var book=SupportConversationBook()
    fun forOwner(account:String?,generation:Long):SupportConversationBook {
        val next=account to generation
        if(owner!=next) {book=SupportConversationBook();owner=next}
        return book
    }
}
internal val supportConversationBooks=SupportConversationBooks()

internal fun newestSupportTabTicket(current:SupportTicketDataModel?,incoming:SupportTicketDataModel,
    expectedId:String):SupportTicketDataModel? = when {
    incoming.id!=expectedId -> current
    current==null -> incoming
    current.id!=expectedId || incoming.revision<current.revision -> current
    else -> incoming
}

internal fun supportDraftNeedsSaving(loaded:Boolean,edited:Boolean):Boolean = loaded || edited
