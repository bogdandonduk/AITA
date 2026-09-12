package kz.aita

import kotlinx.serialization.Serializable

@Serializable
data class PendingSupportMessage(
    val accountId: String,
    val commandId: String,
    val ticketId: String?,
    val agent: Boolean,
    val body: String,
    val category: String = "general",
    val storeId: String? = null,
    val language: String = "en",
    val draftRevision: String = ""
)

@Serializable
data class SupportConversationJournal(
    val draft: String = "",
    val draftRevision: String = "",
    val pending: PendingSupportMessage? = null,
    val acknowledgedDraftRevision: String? = null
)


/** These pure transitions are also used by the single-writer persistence path. */
fun SupportConversationJournal.withSupportDraft(text: String, revision: String): SupportConversationJournal {
    require(revision.isNotBlank())
    return if(acknowledgedDraftRevision==revision) this
    else copy(draft=text.take(4000),draftRevision=revision)
}

fun SupportConversationJournal.prepareSupportMessage(command: PendingSupportMessage): SupportConversationJournal {
    require(command.accountId.isNotBlank())
    require(command.commandId.matches(Regex("[A-Za-z0-9_:-]{8,128}")))
    require(!command.agent || !command.ticketId.isNullOrBlank())
    require(command.body.isNotBlank() && command.body.length<=4000 && '\u0000' !in command.body)
    require(pending==null || pending==command) { "An unresolved command must not be replaced" }
    return copy(pending=command)
}

fun SupportConversationJournal.acknowledgeSupportMessage(command: PendingSupportMessage): SupportConversationJournal {
    if(pending!=command) return this
    return copy(pending=null,draft=if(draftRevision==command.draftRevision) "" else draft,
        acknowledgedDraftRevision=command.draftRevision)
}
