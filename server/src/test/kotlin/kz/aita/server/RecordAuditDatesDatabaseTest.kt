package kz.aita.server

import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class RecordAuditDatesDatabaseTest {
    @Test fun migrationPreservesUnknownHistoryAndTracksActualChangesWithoutRewritingCreation() {
        val url=System.getenv("AITA_STORE_ARCH_TEST_DB_URL").orEmpty()
        assumeTrue(url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_test_[a-z0-9]+").matches(url))
        val user=System.getProperty("user.name")
        val schema="dates_"+UUID.randomUUID().toString().replace("-","")
        DriverManager.getConnection(url,user,"").use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val db=Database.connect("$url?currentSchema=$schema",driver="org.postgresql.Driver",user=user,password="")
        try {
            transaction(db) {
                SchemaUtils.create(Users,Stores,Suppliers,StoreWorkerMemberships,StoreWorkerRequests,StoreBuyers,OperationLogs)
                // Reproduce the prior schema, including pre-existing records with no modification history.
                exec("ALTER TABLE users DROP COLUMN updated_at")
                exec("ALTER TABLE suppliers DROP COLUMN updated_at")
                val legacy=UUID.randomUUID()
                exec("INSERT INTO suppliers(id,name,added_at) VALUES ('$legacy','Legacy','2020-01-01T00:00:00Z')")
                exec(javaClass.getResource("/db/migration/V140__record_audit_dates.sql")!!.readText())
                val old=Suppliers.selectAll().where {Suppliers.id eq legacy}.single()
                assertNull(old[Suppliers.updatedAt])
                val created=old[Suppliers.addedAt]
                Suppliers.update({Suppliers.id eq legacy}) {it[name]="Edited";it[addedAt]=Instant.now()}
                val changed=Suppliers.selectAll().where {Suppliers.id eq legacy}.single()
                assertEquals(created,changed[Suppliers.addedAt])
                assertNotNull(changed[Suppliers.updatedAt])
                Suppliers.update({Suppliers.id eq legacy}) {it[name]="Edited";it[updatedAt]=Instant.now().plusSeconds(3600)}
                assertEquals(changed[Suppliers.updatedAt],Suppliers.selectAll().where {Suppliers.id eq legacy}.single()[Suppliers.updatedAt])
                val fresh=UUID.randomUUID()
                Suppliers.insert {it[id]=fresh;it[name]="New"}
                val inserted=Suppliers.selectAll().where {Suppliers.id eq fresh}.single()
                assertEquals(inserted[Suppliers.addedAt],inserted[Suppliers.updatedAt])
                Suppliers.update({Suppliers.id eq fresh}) {it[name]="Next"}
                assertTrue(Suppliers.selectAll().where {Suppliers.id eq fresh}.single()[Suppliers.updatedAt]!! > inserted[Suppliers.updatedAt]!!)
                val account=UUID.randomUUID()
                Users.insert {it[id]=account;it[publicId]="AUDIT";it[phoneNumber]="audit";it[email]="audit@example.invalid"
                    it[firstName]="Audit";it[lastName]="Test";it[countryLocale]="KZ";it[passwordHash]="test-only"}
                val registration=Users.selectAll().where {Users.id eq account}.single()
                assertEquals(registration[Users.createdAt],registration[Users.updatedAt])
                Users.update({Users.id eq account}) {it[firstName]="Changed"}
                val edited=Users.selectAll().where {Users.id eq account}.single()
                assertEquals(registration[Users.createdAt],edited[Users.createdAt])
                assertTrue(edited[Users.updatedAt]!! > registration[Users.updatedAt]!!)
            }
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url,user,"").use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
}
