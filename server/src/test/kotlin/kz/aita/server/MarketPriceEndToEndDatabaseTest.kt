package kz.aita.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.sql.DriverManager
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*
import kz.aita.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue

/** Actual migrations → authenticated HTTP → stock pricing → saved watch → notification.
 * The only stub is the JWT issuer; no report, permission, persistence or notification callback is replaced.
 */
class MarketPriceEndToEndDatabaseTest {
    private inline fun <reified T> payload(body: String): T = jsonBase.decodeFromString(
        Json.parseToJsonElement(body).jsonObject.getValue("payload").jsonPrimitive.content)

    @Test fun realHttpReportsAndDailyNotificationsShareTheSamePublishedPricesAndPermissions() {
        val base = System.getenv("AITA_AUTH_TEST_DB_URL").orEmpty()
        assumeTrue("Requires a disposable local PostgreSQL cluster", base.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_test_[A-Za-z0-9_]+").matches(base))
        val username = System.getProperty("user.name")
        val databaseName = "aita_test_prices_" + UUID.randomUUID().toString().replace("-", "")
        val url = base.substringBeforeLast('/') + "/" + databaseName
        DriverManager.getConnection(base, username, "").use { it.createStatement().use { it.execute("CREATE DATABASE $databaseName") } }
        val old = TransactionManager.defaultDatabase
        var db: Database? = null
        try {
            Flyway.configure().dataSource(url, username, "").locations("classpath:db/migration").target("85").load().migrate()
            DriverManager.getConnection(url, username, "").use { c -> c.createStatement().use { statement ->
                // Historical operator seed is already applied in real databases. Do not create a customer's store in a test.
                statement.execute("CREATE TABLE IF NOT EXISTS aita_launch_stock_seed_state(seed_key TEXT PRIMARY KEY,store_id UUID NOT NULL,seeded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)")
                statement.execute("INSERT INTO aita_launch_stock_seed_state(seed_key,store_id) VALUES ('parent_store_toys_stock_2026_06_eddec888','00000000-0000-4000-8000-000000000000')")
            } }
            Flyway.configure().dataSource(url, username, "").locations("classpath:db/migration").load().migrate()
            val database = Database.connect(url, driver = "org.postgresql.Driver", user = username, password = "")
            db = database; TransactionManager.defaultDatabase = database
            val user = UUID.randomUUID(); val outsider = UUID.randomUUID(); val store = UUID.randomUUID(); val item = UUID.randomUUID()
            val peers = List(3) { Triple(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }
            var now = 1_800_000_000_000L
            transaction(database) {
                listOf(user, outsider).forEachIndexed { i, uid -> Users.insert {
                    it[id] = uid; it[publicId] = "PRICE-FLOW-$i"; it[phoneNumber] = "flow-$i"; it[email] = "flow-$i@example.invalid"
                    it[firstName] = "Flow"; it[lastName] = "Test"; it[countryLocale] = "kz"; it[passwordHash] = "not-a-login-credential"
                } }
                fun addStore(sid: UUID, parent: UUID? = null) = Stores.insert {
                    it[id] = sid; it[publicId] = sid.toString().replace("-", "").take(16); it[parentStoreId] = parent
                    it[branchType] = if (parent == null) null else StoreBranchType.INTERNET.name
                    it[ownerUserIds] = if (parent == null) listOf(user.toString()) else emptyList()
                    it[storeTypeIds] = emptyList(); it[name] = listOf(LocalizedStringDataModel("en", "Price flow"))
                    it[alias] = emptyList(); it[description] = emptyList(); it[companyForms] = emptyList()
                    it[location] = LocationDataModel(); it[phoneNumbers] = emptyList(); it[emails] = emptyList(); it[countryLocales] = listOf("kz")
                }
                fun addItem(id: UUID, sid: UUID, price: String) = StockItems.insert {
                    it[StockItems.id] = id; it[storeId] = sid; it[userId] = user
                    it[barcodes] = listOf("4006381333931"); it[name] = listOf(LocalizedStringDataModel("en", "Actual tea"))
                    it[description] = emptyList(); it[measurementUnitId] = "0"; it[categoryIds] = emptyList()
                    it[salePrices] = listOf(PriceDataModel(price, "KZT", "")); it[returnPrices] = emptyList(); it[supplyPrices] = emptyList()
                    it[isQuickItem] = false; it[imagePaths] = emptyList(); it[createdAtMillis] = 1; it[updatedAtMillis] = 1; it[isActive] = true
                }
                addStore(store); addItem(item, store, "40.00")
                peers.forEachIndexed { index, (root, branch, goods) ->
                    addStore(root); addStore(branch, root); addItem(goods, branch, "${(index + 1) * 10}.00")
                    exec("""INSERT INTO store_subscription_states(store_id,owner_user_id,plan_id,status,access_kind,current_period_start_millis,auto_renew,renewal_price_minor)
                        VALUES ('$branch','$user','internal_lifetime','active','lifetime',1,FALSE,0)""")
                    StockBatchesV2.insert {
                        it[id] = UUID.randomUUID(); it[goodsItemId] = goods; it[storeId] = branch; it[userId] = user
                        it[quantity] = QuantityDataModel("0", emptyList(), 100.0, 1.0, true)
                        it[supplyPrice] = PriceDataModel("1", "KZT", ""); it[discounts] = emptyList(); it[shelfPriority] = 0
                        it[status] = "OnShelf"; it[createdAtMillis] = 1; it[updatedAtMillis] = 1; it[isActive] = true
                    }
                    exec("""INSERT INTO marketplace_storefronts(store_id,branch_store_id,display_name,city,public_address,is_published,updated_by,updated_at_millis)
                        VALUES ('$branch','$branch','Public shop $index','Astana','Public door',TRUE,'$user',1);
                        INSERT INTO marketplace_listings(id,store_id,goods_item_id,title,gtin,is_published,created_at_millis,updated_at_millis,updated_by)
                        VALUES ('${UUID.randomUUID()}','$branch','$goods','Actual tea','04006381333931',TRUE,1,1,'$user')""")
                }
            }
            val algorithm = Algorithm.HMAC256("isolated-price-flow-token-key")
            fun token(uid: UUID) = JWT.create().withSubject(uid.toString()).sign(algorithm)
            testApplication {
                application {
                    install(Authentication) { jwt("fixture") { verifier(JWT.require(algorithm).build()); validate { JWTPrincipal(it.payload) } } }
                    routing { authenticate("fixture") { route("/analytics") {
                        marketPriceInsightsRoute(principal = { principal<JWTPrincipal>()?.subject?.let(UUID::fromString) }, clock = { now })
                        marketPriceWatchRoutes(principal = { principal<JWTPrincipal>()?.subject?.let(UUID::fromString) }, clock = { now })
                    } } }
                }
                val report = client.get("/analytics/market-prices?country=kz&city=Astana") { bearerAuth(token(user)); header("store_id", store) }
                assertEquals(HttpStatusCode.OK, report.status, report.bodyAsText())
                val page = payload<MarketPriceInsightsPage>(report.bodyAsText())
                assertTrue(page.isValidMarketPricePage(user.toString(), store.toString(), MarketPriceRequest(city = "Astana")))
                assertEquals(2000L, page.rows.single().statistics?.medianMinor); assertEquals(4000L, page.rows.single().ownPriceMinor)
                val command = MarketPriceWatchUpdate(UUID.randomUUID().toString(), 0, true,
                    MarketPriceWatchSettings(city = "Astana", items = listOf(MarketPriceWatchItem(item.toString(), "04006381333931"))))
                suspend fun save() = client.put("/analytics/market-prices/watch") {
                    bearerAuth(token(user)); header("store_id", store); contentType(ContentType.Application.Json); setBody(jsonBase.encodeToString(command))
                }
                val saved = save(); assertEquals(HttpStatusCode.OK, saved.status, saved.bodyAsText())
                val state = payload<MarketPriceWatchState>(saved.bodyAsText()); assertTrue(state.isValidFor(user.toString(), store.toString()))
                assertEquals(2000L, state.observations.single().baselineMinor); assertEquals(state, payload<MarketPriceWatchState>(save().bodyAsText()))
                transaction(database) { assertEquals(0L, Notifications.selectAll().count()) }
                transaction(database) {
                    peers.forEachIndexed { index, (_, _, goods) -> StockItems.update({ StockItems.id eq goods }) {
                        it[salePrices] = listOf(PriceDataModel("${(index + 1) * 20}.00", "KZT", ""))
                    } }
                }
                now += MARKET_PRICE_WATCH_INTERVAL
                transaction(database) { assertTrue(deliverPriceWatchInsideTransaction(user, store, now)); assertFalse(deliverPriceWatchInsideTransaction(user, store, now)) }
                val latest = client.get("/analytics/market-prices/watch") { bearerAuth(token(user)); header("store_id", store) }
                val observed = payload<MarketPriceWatchState>(latest.bodyAsText())
                assertEquals(4000L, observed.observations.single().baselineMinor)
                transaction(database) {
                    assertEquals(1L, Notifications.selectAll().count())
                    val notice = Notifications.selectAll().single().toNotificationDataModel()
                    assertEquals("market_prices", notice.category)
                    val details = assertNotNull(notice.marketPriceAlertDetails())
                    assertEquals(2000L, details.changes.single().previousMinor)
                    assertEquals(4000L, details.changes.single().currentMinor)
                    assertEquals("Astana", details.city)
                }
                for (endpoint in listOf("/analytics/market-prices", "/analytics/market-prices/watch")) {
                    assertEquals(HttpStatusCode.Forbidden, client.get(endpoint) { bearerAuth(token(outsider)); header("store_id", store) }.status)
                    assertEquals(HttpStatusCode.Unauthorized, client.get(endpoint) { header("store_id", store) }.status)
                }
                transaction(database) {
                    Stores.update({ Stores.id eq store }) { it[isActive] = false }
                    assertFalse(deliverPriceWatchInsideTransaction(user, store, now + MARKET_PRICE_WATCH_INTERVAL))
                    assertFalse(MarketPriceWatches.selectAll().single()[MarketPriceWatches.enabled])
                    assertEquals(1L, Notifications.selectAll().count())
                }
            }
        } finally {
            db?.let { TransactionManager.closeAndUnregister(it) }; TransactionManager.defaultDatabase = old
            DriverManager.getConnection(base, username, "").use { it.createStatement().use { it.execute("DROP DATABASE $databaseName WITH (FORCE)") } }
        }
    }
}
