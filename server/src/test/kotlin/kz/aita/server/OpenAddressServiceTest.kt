package kz.aita.server

import kotlinx.coroutines.runBlocking
import kz.aita.*
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.*

class OpenAddressServiceTest {
    @Test fun localSearchIsCountryScopedAndResolutionUsesCatalogueCoordinates(): Unit = runBlocking {
        val path=Files.createTempFile("aita-address-test-",".sqlite")
        val previous=System.getProperty("AITA_ADDRESSES_DB")
        try {
            DriverManager.getConnection("jdbc:sqlite:$path").use { db -> db.createStatement().use { s ->
                s.execute("CREATE TABLE addresses(id TEXT PRIMARY KEY,country TEXT,title TEXT,subtitle TEXT,lat REAL,lon REAL,kind TEXT,postal TEXT,names TEXT,search TEXT,house TEXT,approximate_locality TEXT)")
                s.execute("INSERT INTO addresses VALUES ('KZ:n1','KZ','Абая 47','Алматы, KZ',43.2,76.9,'house','','{}','абая 47 алматы kz','47',''),('RU:n2','RU','Абая 47','Москва, RU',55.7,37.6,'house','','{}','абая 47 москва ru','47','')")
                s.execute("CREATE VIRTUAL TABLE address_search USING fts5(search,content=addresses,content_rowid=rowid)")
                s.execute("INSERT INTO address_search(address_search) VALUES ('rebuild')")
            } }
            System.setProperty("AITA_ADDRESSES_DB",path.toString())
            val rows=OpenAddressService.suggest("Абая 47","ru",listOf("KZ"),null,null,10)
            assertEquals(listOf("KZ:n1"),rows.map { it.providerObjectId })
            assertTrue(OpenAddressService.suggest("Абая","ru",listOf("US"),null,null,10).isEmpty())
            assertTrue(OpenAddressService.suggest("Ал","ru",emptyList(),null,null,10).isEmpty())
            assertTrue(OpenAddressService.suggest("\" OR * --","ru",emptyList(),null,null,10).isEmpty())
            val address=OpenAddressService.resolve(rows.single().providerObjectId,"ru")
            assertEquals(43.2,address.latitude);assertEquals(76.9,address.longitude)
            assertTrue(address.isResolvedAddress())
            assertFailsWith<AddressProviderException> { OpenAddressService.resolve("KZ:n1' OR 1=1","ru") }
            assertFailsWith<AddressProviderException> { OpenAddressService.resolve("KZ:n999","ru") }
        } finally {
            if(previous==null) System.clearProperty("AITA_ADDRESSES_DB") else System.setProperty("AITA_ADDRESSES_DB",previous)
            Files.deleteIfExists(path)
        }
    }
}
