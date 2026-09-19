package kz.aita.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kz.aita.RealtimeUpdateDataModel
import kz.aita.jsonBase
import java.util.UUID
import kotlin.test.*

class StoreDeletionRoutesTest {
    private val account = UUID.randomUUID()
    private val store = UUID.randomUUID()
    private val algorithm = Algorithm.HMAC256("isolated-store-deletion-route-test")
    private val token = JWT.create().withSubject(account.toString()).sign(algorithm)

    private fun scenario(result: Int = 0, block: suspend ApplicationTestBuilder.(MutableList<Pair<UUID, String>>) -> Unit) = testApplication {
        val deletions = mutableListOf<Pair<UUID, String>>()
        application {
            install(Authentication) { jwt("auth-jwt") { verifier(JWT.require(algorithm).build()); validate { JWTPrincipal(it.payload) } } }
            install(io.ktor.server.websocket.WebSockets)
            routing { authenticate("auth-jwt") {
                route("/stores") { storeDeletionActions { owner, id -> deletions += owner to id; result } }
                webSocket("/test-events") {
                    val after = RealtimeServerBus.sharedUpdates.replayCache.lastOrNull()?.sequence ?: 0
                    send(Frame.Text("ready"))
                    RealtimeServerBus.sharedUpdates.collect { event ->
                        if ((event.sequence ?: 0) > after) send(Frame.Text(jsonBase.encodeToString(RealtimeUpdateDataModel.serializer(), event)))
                    }
                }
            } }
        }
        block(deletions)
    }

    @Test fun committedDeletionReachesTwoConnectedClientsWithoutDeletedStoreAudience() = scenario { deletions ->
        val socketClient = createClient { install(io.ktor.client.plugins.websocket.WebSockets) }
        coroutineScope {
            val ready = List(2) { CompletableDeferred<Unit>() }
            val received = ready.map { signal -> async {
                var update: RealtimeUpdateDataModel? = null
                socketClient.webSocket("/test-events", request = { bearerAuth(token) }) {
                    assertEquals("ready", (incoming.receive() as Frame.Text).readText())
                    signal.complete(Unit)
                    update = jsonBase.decodeFromString<RealtimeUpdateDataModel>((withTimeout(5_000) { incoming.receive() } as Frame.Text).readText())
                }
                requireNotNull(update)
            } }
            ready.forEach { it.await() }
            val response = client.delete("/stores/delete") {
                bearerAuth(token); contentType(ContentType.Application.Json); setBody(store.toString())
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf(account to store.toString()), deletions)
            val events = received.awaitAll()
            assertEquals(events[0].id, events[1].id)
            events.forEach {
                assertEquals("stores", it.entity)
                assertNull(it.storeId); assertNull(it.userId)
                assertEquals("shared_state_changed", it.reason)
            }
        }
    }

    @Test fun rejectedDeletionDoesNotPublishAnInvalidation() = scenario(result = 3) {
        val before = RealtimeServerBus.sharedUpdates.replayCache.lastOrNull()?.sequence
        assertEquals(HttpStatusCode.Conflict, client.delete("/stores/delete") {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody(store.toString())
        }.status)
        assertEquals(before, RealtimeServerBus.sharedUpdates.replayCache.lastOrNull()?.sequence)
    }

    @Test fun anonymousCallerCannotReachDeletion() = scenario { deletions ->
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/stores/delete") {
            contentType(ContentType.Application.Json); setBody(store.toString())
        }.status)
        assertTrue(deletions.isEmpty())
    }
}
