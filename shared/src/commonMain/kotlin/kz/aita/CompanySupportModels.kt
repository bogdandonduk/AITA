package kz.aita

import kotlinx.serialization.Serializable

object CompanyCapability {
    const val SUPPORT_QUEUE = "support.queue.read"
    const val SUPPORT_CLAIM = "support.ticket.claim"
    const val SUPPORT_REPLY = "support.message.reply"
    const val SUPPORT_RESOLVE = "support.ticket.resolve"
    const val SUPPORT_MANAGE = "support.ticket.manage"
    const val SUPPORT_METRICS = "support.metrics.read"
}

@Serializable
data class CompanyJobDataModel(val id: String, val title: Map<String, String>)

/** Public UI projection only: no HR metadata, private employment history or tokens. */
@Serializable
data class CompanyAccessDataModel(
    val userId: String, val employmentId: String? = null,
    val jobs: List<CompanyJobDataModel> = emptyList(), val capabilities: Set<String> = emptySet(),
    val serverTimeMillis: Long, val validUntilMillis: Long
)

@Serializable
data class SupportTicketPage(val tickets: List<SupportTicketDataModel>, val nextBeforeId: String? = null,
    val nextBeforeMillis: Long? = null)

@Serializable
data class SupportMessagePage(val ticket: SupportTicketDataModel, val messages: List<SupportMessageDataModel>,
    val nextBeforeSequence: Long? = null)

@Serializable
data class SupportAgentActionRequest(val ticketId: String, val action: String, val expectedRevision: Long)

@Serializable
data class SupportReadCursorRequest(val ticketId: String, val throughSequence: Long)

@Serializable
data class SupportTeamMetrics(val open: Long, val unassigned: Long, val assignedToMe: Long,
    val repliesLast30Days: Long, val resolvedLast30Days: Long,
    val firstResponsesLast30Days: Long, val averageFirstResponseMillis: Long?)

/** Stable identifiers, rather than localized tab labels or guessed job titles, gate surfaces. */
fun companyCanOpenSupport(access: CompanyAccessDataModel?, accountId: String?): Boolean =
    accountId != null && access?.userId == accountId && access.employmentId != null &&
        CompanyCapability.SUPPORT_QUEUE in access.capabilities

fun supportCanReply(ticket: SupportTicketDataModel, userId: String, agent: Boolean, capabilities: Set<String>): Boolean =
    ticket.isActive && ticket.status != "closed" && if (agent)
        ticket.userId != userId && ticket.assignedAgentUserId == userId && CompanyCapability.SUPPORT_REPLY in capabilities
    else ticket.userId == userId
