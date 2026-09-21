package kz.aita.server

import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.*
import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlin.test.*

/** Actual JWT provider + real session rows; no mocked authentication acceptance. */
class RealtimeJwtDatabaseTest {
    @Test fun browserAndNativeCredentialsUseTheSameSessionRevocationPolicy() {
        val url = System.getenv("AITA_AUTH_TEST_DB_URL").orEmpty()
        assumeTrue("Requires isolated PostgreSQL", url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_test_[a-zA-Z0-9_]+").matches(url))
        val owner = System.getProperty("user.name")
        val schema = "realtime_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, owner, "").use { it.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        val db = Database.connect("$url?currentSchema=$schema", driver = "org.postgresql.Driver", user = owner, password = "")
        val account = UUID.randomUUID(); val session = UUID.randomUUID()
        val secret = "isolated-realtime-signing-secret-".repeat(3)
        try {
            transaction(db) {
                SchemaUtils.create(Users, RefreshSessions)
                Users.insert {
                    it[id] = account; it[publicId] = "REALTIME"; it[phoneNumber] = "77000000118"
                    it[email] = "realtime@example.invalid"; it[firstName] = "Realtime"; it[lastName] = "Test"
                    it[countryLocale] = "KZ"; it[passwordHash] = "not-used-by-this-test"
                }
                RefreshSessions.insert {
                    it[id] = session; it[userId] = account; it[tokenHash] = "0".repeat(64)
                    it[expiresAt] = Instant.now().plusSeconds(3600)
                }
            }
            testApplication {
                environment { config = MapApplicationConfig(
                    "ktor.security.jwt.issuer" to "isolated-realtime", "ktor.security.jwt.audience" to "isolated-realtime",
                    "ktor.security.jwt.secret" to secret, "app.environment" to "test") }
                application {
                    configureJwtAuth()
                    install(io.ktor.server.websocket.WebSockets)
                    routing { authenticate("auth-jwt") {
                        webSocket("/rt/updates", protocol = AITA_REALTIME_PROTOCOL) { send(Frame.Text("authenticated")) }
                        webSocket("/rt/updates") { send(Frame.Text("authenticated")) }
                        get("/protected") { call.respondText("authenticated") }
                    } }
                }
                val token = TokenService(JwtConfig("isolated-realtime", "isolated-realtime", "test", secret, 900000, 3600000))
                    .signAccess(account, session, Instant.now())
                val socketClient = createClient { install(WebSockets) }
                socketClient.webSocket("/rt/updates", request = {
                    header(HttpHeaders.SecWebSocketProtocol, AITA_REALTIME_PROTOCOL)
                    header(HttpHeaders.SecWebSocketProtocol, AITA_REALTIME_AUTH_PROTOCOL_PREFIX + token)
                }) { assertEquals("authenticated", (incoming.receive() as Frame.Text).readText()) }
                socketClient.webSocket("/rt/updates", request = { bearerAuth(token) }) {
                    assertEquals("authenticated", (incoming.receive() as Frame.Text).readText())
                }
                assertEquals(HttpStatusCode.Unauthorized, client.get("/protected") {
                    header(HttpHeaders.SecWebSocketProtocol, "$AITA_REALTIME_PROTOCOL, $AITA_REALTIME_AUTH_PROTOCOL_PREFIX$token")
                }.status)
                transaction(db) { RefreshSessions.update({ RefreshSessions.id eq session }) { it[securityInvalidated] = true } }
                assertEquals(HttpStatusCode.Unauthorized, client.get("/protected") { bearerAuth(token) }.status)
                assertFails { socketClient.webSocket("/rt/updates", request = {
                    header(HttpHeaders.SecWebSocketProtocol, AITA_REALTIME_PROTOCOL)
                    header(HttpHeaders.SecWebSocketProtocol, AITA_REALTIME_AUTH_PROTOCOL_PREFIX + token)
                }) { fail("A revoked browser session must not open") } }
                assertFails { socketClient.webSocket("/rt/updates", request = { bearerAuth(token) }) {
                    fail("A revoked native session must not open")
                } }
            }
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url, owner, "").use { it.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
}
