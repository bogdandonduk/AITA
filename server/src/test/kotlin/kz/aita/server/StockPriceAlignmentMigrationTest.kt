package kz.aita.server

import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.*

class StockPriceAlignmentMigrationTest {
    @Test fun alignmentBacksUpEachOldValueAndNeverTouchesReceiptsOrMissingPrices() {
        val url=System.getenv("AITA_AUTH_TEST_DB_URL").orEmpty()
        assumeTrue("Requires isolated aita_test_* database",url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_test_[a-zA-Z0-9_]+").matches(url))
        DriverManager.getConnection(url,System.getProperty("user.name"),"").use { c ->
            val schema="price_alignment_"+UUID.randomUUID().toString().replace("-","")
            c.createStatement().use { sql ->
                sql.execute("CREATE SCHEMA $schema")
                try {
                    sql.execute("SET search_path TO $schema")
                    sql.execute("CREATE TABLE stock_items(id UUID PRIMARY KEY,supply_prices JSONB NOT NULL,sale_prices JSONB NOT NULL,updated_at_millis BIGINT NOT NULL)")
                    sql.execute("CREATE TABLE transactions(id INT PRIMARY KEY,amount NUMERIC NOT NULL)")
                    sql.execute("INSERT INTO transactions VALUES (1,42.50)")
                    for (n in 1..4) {
                        val sale=when(n){2->"[]";3->"[{\"price\":\"invalid\",\"currency\":\"KZT\"}]";else->"[{\"price\":\"25.50\",\"currency\":\"KZT\"}]"}
                        val supply=if(n==4)sale else "[{\"price\":\"7\",\"currency\":\"KZT\"}]"
                        c.prepareStatement("INSERT INTO stock_items VALUES(?,?::jsonb,?::jsonb,10)").use {
                            it.setObject(1,UUID(0,n.toLong()));it.setString(2,supply);it.setString(3,sale);it.executeUpdate()
                        }
                    }
                    sql.execute(javaClass.getResource("/db/migration/V135__align_existing_catalogue_supply_prices.sql")!!.readText())
                    sql.executeQuery("SELECT count(*) FROM stock_supply_price_alignment_backup").use {it.next();assertEquals(1,it.getInt(1))}
                    sql.executeQuery("SELECT old_supply_prices->0->>'price',old_updated_at_millis FROM stock_supply_price_alignment_backup").use {it.next();assertEquals("7",it.getString(1));assertEquals(10L,it.getLong(2))}
                    sql.executeQuery("SELECT supply_prices->0->>'price',updated_at_millis FROM stock_items ORDER BY id").use {
                        it.next();assertEquals("25.50",it.getString(1));assertTrue(it.getLong(2)>10)
                        it.next();assertEquals("7",it.getString(1));assertEquals(10L,it.getLong(2))
                        it.next();assertEquals("7",it.getString(1))
                    }
                    sql.executeQuery("SELECT amount::text FROM transactions").use {it.next();assertEquals("42.50",it.getString(1))}
                } finally {sql.execute("SET search_path TO public");sql.execute("DROP SCHEMA $schema CASCADE")}
            }
        }
    }
}
