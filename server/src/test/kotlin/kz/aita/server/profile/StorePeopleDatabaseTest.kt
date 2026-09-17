package kz.aita.server.profile

import kz.aita.*
import kz.aita.server.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import java.util.Base64
import kotlin.test.*

/** Disposable, opt-in PostgreSQL only. Actual tables, migration, repositories and permissions. */
class StorePeopleDatabaseTest {
    private class Fixture(val db:Database) {
        val owner=UUID.randomUUID();val employee=UUID.randomUUID();val colleague=UUID.randomUUID()
        val stranger=UUID.randomUUID();val store=UUID.randomUUID();val branch=UUID.randomUUID();val elsewhere=UUID.randomUUID()
        val now=System.currentTimeMillis()
    }
    private fun fixture(block:suspend (Fixture)->Unit)=runBlocking {
        val url=System.getenv("AITA_PEOPLE_TEST_JDBC").orEmpty()
        assumeTrue("Requires disposable local aita_people_test database",url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_people_test").matches(url))
        val user=System.getProperty("user.name");val schema="people_test_"+UUID.randomUUID().toString().replace("-","")
        DriverManager.getConnection(url,user,"").use {c->c.createStatement().use {it.execute("CREATE SCHEMA $schema")}}
        val db=Database.connect("$url?currentSchema=$schema",driver="org.postgresql.Driver",user=user,password="")
        val f=Fixture(db)
        try {
            transaction(db) {
                SchemaUtils.create(Users,Stores,StoreUsers,StoreWorkerMemberships,Transactions,OperationLogs)
                for((i,id) in listOf(f.owner,f.employee,f.colleague,f.stranger).withIndex())Users.insert {
                    it[Users.id]=id;it[publicId]="TEST$i";it[phoneNumber]="test-phone-$i";it[email]="test$i@example.invalid"
                    it[firstName]="Person$i";it[lastName]="Test";it[countryLocale]="KZ";it[passwordHash]="not-a-real-password-hash"
                }
                for((i,id) in listOf(f.store,f.branch,f.elsewhere).withIndex())Stores.insert {
                    it[Stores.id]=id;it[publicId]="STORE$i";it[parentStoreId]=if(id==f.branch)f.store else null
                    it[ownerUserIds]=listOf(if(id==f.elsewhere)f.stranger.toString() else f.owner.toString())
                    it[storeTypeIds]=emptyList();it[name]=listOf(LocalizedStringDataModel("en","Shop$i"));it[alias]=emptyList()
                    it[description]=emptyList();it[companyForms]=emptyList();it[location]=LocationDataModel()
                    it[phoneNumbers]=emptyList();it[emails]=emptyList();it[countryLocales]=emptyList()
                }
                for(id in listOf(f.employee,f.colleague))StoreWorkerMemberships.insert {
                    it[StoreWorkerMemberships.id]=UUID.randomUUID();it[storeId]=f.store;it[userId]=id
                    it[acceptedAtMillis]=f.now-100000;it[acceptedByUserId]=f.owner;it[permissions]=emptyList()
                    it[jobTitle]="Cashier";it[salary]="NEVER-PUBLIC-SALARY";it[workshiftPasswordHash]="NEVER-PUBLIC-SECRET"
                }
                fun migration(name:String)=javaClass.getResource("/db/migration/$name")!!.readText()
                exec(migration("V114__user_profile_photos.sql"))
                exec("INSERT INTO user_profile_photos(user_id,revision,jpeg,updated_at_millis) VALUES ('${f.employee}',3,decode('010203','hex'),1)")
                exec(migration("V115__mode_profile_photos_and_people_indexes.sql"))
            }
            block(f)
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url,user,"").use {c->c.createStatement().use {it.execute("DROP SCHEMA $schema CASCADE")}}
        }
    }
    @Test fun legacyPrivatePictureMovesOnlyToMarketplaceAndRemovalClearsEveryCopy()=fixture {f->
        val market=DatabaseModeProfilePhotos(ProfilePhotoMode.MARKETPLACE,f.db)
        val store=DatabaseModeProfilePhotos(ProfilePhotoMode.STORE,f.db)
        assertEquals(3,market.read(f.employee).revision);assertNotNull(market.read(f.employee).jpegBase64)
        assertNull(store.read(f.employee).jpegBase64);assertEquals(0,store.read(f.employee).revision)
        assertNull(DatabaseModeProfilePhotos(ProfilePhotoMode.SUPPLIER,f.db).read(f.employee).jpegBase64)
        assertFalse(market.change(f.employee,3,null).conflict)
        transaction(f.db) {exec("SELECT jpeg FROM user_profile_photos WHERE user_id='${f.employee}'") {rs->assertTrue(rs.next());assertNull(rs.getBytes(1))}}
        store.change(f.employee,0,byteArrayOf(4,5,6))
        assertNull(market.read(f.employee).jpegBase64);assertEquals(1,store.read(f.employee).revision)
    }
    @Test fun storeAndSupplierPicturesHaveIndependentRevisionFences()=fixture {f->
        val a=DatabaseModeProfilePhotos(ProfilePhotoMode.STORE,f.db);val b=DatabaseModeProfilePhotos(ProfilePhotoMode.SUPPLIER,f.db)
        a.change(f.employee,0,byteArrayOf(1));b.change(f.employee,0,byteArrayOf(2))
        assertEquals(ProfilePhotoMode.STORE,a.read(f.employee).mode);assertEquals(ProfilePhotoMode.SUPPLIER,b.read(f.employee).mode)
        assertNotEquals(a.read(f.employee).jpegBase64,b.read(f.employee).jpegBase64)
        assertTrue(a.change(f.employee,0,byteArrayOf(3)).conflict)
        assertFalse(a.change(f.employee,0,byteArrayOf(1)).conflict)
    }
    @Test fun colleagueGetsIdentityButOnlySelfAndManagerGetAnalytics()=fixture {f->
        val repo=DatabaseStorePeople(f.db)
        assertNull(repo.read(f.stranger,f.store,f.employee,30))
        assertNull(repo.read(f.employee,f.elsewhere,f.stranger,30))
        val colleague=assertNotNull(repo.read(f.colleague,f.store,f.employee,30));assertNull(colleague.statistics)
        assertNotNull(repo.read(f.employee,f.store,f.employee,30)?.statistics)
        assertNotNull(repo.read(f.owner,f.store,f.employee,30)?.statistics)
        val json=jsonBase.encodeToString(StorePersonProfile.serializer(),colleague)
        listOf("NEVER-PUBLIC","password","email","salary","phone").forEach {assertFalse(json.contains(it),it)}
    }
    private fun tx(f:Fixture,type:String,lines:List<GoodsItemInTransactionDataModel>,store:UUID=f.store,person:UUID=f.employee,time:Long=f.now-1000)=transaction(f.db) {
        Transactions.insert {
            it[id]=UUID.randomUUID();it[storeId]=store;it[userId]=person;it[workshiftId]=0
            it[Transactions.type]=type;it[goodsInTransaction]=lines;it[timeMillis]=time
            it[paidCash]=0.0;it[paidCard]=0.0;it[cardPaymentOptionId]=0
        }
    }
    private fun line(q:Double,p:Double,c:String?)=GoodsItemInTransactionDataModel("test",q,p,currencyCode=c)
    @Test fun realPurchaseTypeCurrenciesReturnsAndActorStorePeriodScopesAreCorrect()=fixture {f->
        tx(f,"purchase",listOf(line(2.0,100.0,"KZT"),line(1.0,3.0,"USD")))
        tx(f,"purchase",listOf(line(1.0,40.0,"KZT")))
        tx(f,"return",listOf(line(1.0,50.0,"KZT")))
        tx(f,"accept",listOf(line(3.0,10.0,"KZT")))
        tx(f,"purchase",listOf(line(1.0,999.0,"KZT")),store=f.branch)
        tx(f,"purchase",listOf(line(1.0,999.0,"KZT")),person=f.colleague)
        tx(f,"purchase",listOf(line(1.0,999.0,"KZT")),time=f.now-100L*86400000)
        val profile=assertNotNull(DatabaseStorePeople(f.db).read(f.owner,f.store,f.employee,30))
        val s=assertNotNull(profile.statistics);assertEquals(2,s.sales);assertEquals(1,s.returns);assertEquals(1,s.supplies)
        assertEquals(1,s.activeDays);assertEquals(4,s.days.sumOf {it.transactions})
        val kzt=s.currencies.single {it.currency=="KZT"};assertEquals("240.00",kzt.sales);assertEquals("190.00",kzt.netSales)
        assertEquals("120.00",kzt.averageSale);assertEquals("30.00",kzt.supplies)
        assertEquals("3.00",s.currencies.single {it.currency=="USD"}.sales)
        assertTrue(validStorePersonProfile(profile,f.owner.toString(),f.store.toString(),f.employee.toString()))
    }
    @Test fun revokedMembershipImmediatelyDeniesReadsAndHistoricalAccessNeedsManagement()=fixture {f->
        tx(f,"purchase",emptyList())
        transaction(f.db) {StoreWorkerMemberships.update({StoreWorkerMemberships.userId eq f.employee}){it[isActive]=false}}
        val repo=DatabaseStorePeople(f.db);assertNull(repo.read(f.employee,f.store,f.employee,30))
        assertNull(repo.read(f.colleague,f.store,f.employee,30));assertEquals("FORMER",repo.read(f.owner,f.store,f.employee,30)?.role)
    }
    @Test fun legacyIncompleteAmountsAreFlaggedWithoutInventingMoney()=fixture {f->
        tx(f,"purchase",listOf(line(1.0,10.0,null)))
        transaction(f.db) {exec("""UPDATE transactions SET goods_in_transaction='[{"quantity":2,"pricePerUnit":"missing"},{"quantity":1,"pricePerUnit":10}]'::jsonb WHERE user_id='${f.employee}'""")}
        val s=assertNotNull(DatabaseStorePeople(f.db).read(f.owner,f.store,f.employee,7)?.statistics)
        assertEquals(1,s.incompletePriceLines);assertEquals("?",s.currencies.single().currency)
        assertEquals("10.00",s.currencies.single().sales)
    }
    @Test fun ownerCanSeeBranchEmployeeButNotAnUnrelatedPersonsIdentity()=fixture {f->
        transaction(f.db) {StoreWorkerMemberships.update({StoreWorkerMemberships.userId eq f.employee}) {it[storeId]=f.branch}}
        val repo=DatabaseStorePeople(f.db)
        assertNotNull(repo.read(f.owner,f.branch,f.employee,7))
        assertNull(repo.read(f.owner,f.store,f.employee,7))
        assertNull(repo.read(f.owner,f.branch,f.stranger,7))
    }
    @Test fun modePhotoTableRejectsInvalidScopesAndOversizedBytes()=fixture {f->
        for((mode,size) in listOf("OTHER" to 1,"STORE" to 524289)) {
            assertFails {transaction(f.db) {exec("INSERT INTO user_mode_profile_photos(user_id,mode,revision,jpeg,updated_at_millis) VALUES ('${f.owner}','$mode',1,decode(repeat('aa',$size),'hex'),1)")}}
        }
    }
}
