package kz.aita.server.support

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.*
import kz.aita.*
import kz.aita.server.*
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private object SupportRateLimiter {
    private data class Window(val start: Long, val count: Int)
    private val windows = ConcurrentHashMap<String, Window>()
    fun check(user: UUID, create: Boolean) {
        val now = System.currentTimeMillis(); val span = if(create) 600_000L else 60_000L
        val window = windows.compute("$user:$create") { _, old ->
            if(old==null || now-old.start>=span) Window(now,1) else old.copy(count=(old.count+1).coerceAtMost(1000))
        } ?: return
        if(windows.size>10000) windows.entries.removeIf { now-it.value.start>600_000L }
        if(window.count > if(create) 10 else 60) supportFail("support.rate_limited",429)
    }
}

internal fun companySupportRepositoryInsideTransaction() = CompanySupportRepository(TransactionManager.current().connection.connection as Connection)

private suspend fun publishSupportChange(customer: String) {
    RealtimeServerBus.publish(entity="support/customer", userId=customer, reason="support_changed")
    // No ticket/customer identifiers in the shared invalidation; audience is authorized per delivery.
    RealtimeServerBus.publish(entity="support/agent", reason="support_queue_changed")
}

private suspend inline fun <reified T> RoutingCall.supportResult(
    status: HttpStatusCode = HttpStatusCode.OK,
    noinline after: suspend (T) -> Unit = {},
    crossinline work: CompanySupportRepository.() -> T
) {
    try {
        val result = newSuspendedTransaction(aitaServerIoContext) { companySupportRepositoryInsideTransaction().work() }
        after(result)
        genericResponse(status,result)
    } catch(cancelled: CancellationException) { throw cancelled }
    catch(failure: SupportFailure) { genericResponseNoPayload(HttpStatusCode.fromValue(failure.status),eventMessage(failure.key)) }
}
private fun RoutingCall.agent(): Boolean = request.queryParameters["agent"]=="true"
private fun RoutingCall.ticketId(): UUID = supportUuid(request.queryParameters["ticket_id"] ?: request.headers["ticket_id"] ?: "")
private fun RoutingCall.optionalLong(key: String): Long? = request.queryParameters[key]?.let {
    it.toLongOrNull()?.takeIf { value -> value >= 0 } ?: supportFail("support.invalid",400)
}

internal fun Route.companySupportRoutes() {
    authenticate("auth-jwt") {
        get("/company/me") {
            val user=call.checkPrincipal() ?: return@get
            call.supportResult { access(user) }
        }
        route("/support") {
            get("/workspace/tickets") {
                val user=call.checkPrincipal() ?: return@get
                call.supportResult { tickets(user,call.agent(),call.request.queryParameters["filter"] ?: "all",
                    call.optionalLong("before_millis"),call.request.queryParameters["before_id"]) }
            }
            get("/workspace/messages") {
                val user=call.checkPrincipal() ?: return@get
                call.supportResult { messages(user,call.ticketId(),call.agent(),call.optionalLong("before_sequence")) }
            }
            post("/workspace/read") {
                val user=call.checkPrincipal() ?: return@post
                val body=call.receiveAita<SupportReadCursorRequest>()
                call.supportResult(after={ if(it.second) publishSupportChange(it.first.userId) }) {
                    markRead(user,call.agent(),body)
                }
            }
            post("/workspace/action") {
                val user=call.checkPrincipal() ?: return@post
                val body=call.receiveAita<SupportAgentActionRequest>()
                call.supportResult(after={ publishSupportChange(it.userId) }) {
                    if(body.expectedRevision<0) supportFail("support.invalid",400)
                    action(user,call.agent(),body)
                }
            }
            post("/agent/messages/send") {
                val user=call.checkPrincipal() ?: return@post
                val body=call.receiveAita<SupportMessageSendRequestDataModel>()
                call.supportResult(HttpStatusCode.Created,after={ publishSupportChange(it.userId) }) {
                    SupportRateLimiter.check(user,false); send(user,true,body)
                }
            }
            get("/agent/metrics") {
                val user=call.checkPrincipal() ?: return@get
                call.supportResult { metrics(user) }
            }
            // Existing client contracts remain; the new workspace uses cursor-based pages above.
            get("/tickets/get") {
                val user=call.checkPrincipal() ?: return@get
                call.supportResult { customerTickets(user) }
            }
            post("/tickets/create") {
                val user=call.checkPrincipal() ?: return@post
                val body=call.receiveAita<SupportTicketCreateRequestDataModel>()
                call.supportResult(HttpStatusCode.Created,after={ publishSupportChange(it.userId) }) {
                    SupportRateLimiter.check(user,true)
                    create(user,body) { store -> userHasStoreAccessInsideTransaction(user,store) }
                }
            }
            for(ticketAction in listOf("close","reopen")) post("/tickets/$ticketAction") {
                val user=call.checkPrincipal() ?: return@post
                val body=call.receiveAita<SupportTicketActionRequestDataModel>()
                call.supportResult(after={ publishSupportChange(it.userId) }) {
                    action(user,false,SupportAgentActionRequest(body.ticketId,ticketAction,-1))
                }
            }
            get("/messages/get") {
                val user=call.checkPrincipal() ?: return@get
                // Reads do not mark a hidden/background conversation as seen.
                call.supportResult { messages(user,call.ticketId(),false).messages }
            }
            post("/messages/send") {
                val user=call.checkPrincipal() ?: return@post
                val body=call.receiveAita<SupportMessageSendRequestDataModel>()
                call.supportResult(HttpStatusCode.Created,after={ publishSupportChange(it.userId) }) {
                    SupportRateLimiter.check(user,false); send(user,false,body)
                }
            }
            post("/messages/read") {
                val user=call.checkPrincipal() ?: return@post
                val body=call.receiveAita<SupportMessagesReadRequestDataModel>()
                call.supportResult(after={ if(it.isNotEmpty()) publishSupportChange(it.first().userId) }) {
                    val page=messages(user,supportUuid(body.ticketId),false)
                    page.messages.lastOrNull()?.let { markRead(user,false,SupportReadCursorRequest(body.ticketId,it.sequence)) }
                    messages(user,supportUuid(body.ticketId),false).messages
                }
            }
        }
    }
}
