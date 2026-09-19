package kz.aita.server.architecture

import kz.aita.server.subscriptions.SubscriptionRepository
import org.junit.Assume.assumeTrue
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import java.util.UUID
import kotlin.test.*

/** Real PostgreSQL migrations/constraints, isolated in a transaction and random schema.
 * Only an explicitly supplied aita_test_* database is accepted; production settings are never read.
 */
class StoreArchitectureMigrationDatabaseTest {
    private class Fixture(val c: Connection) {
        val owner = UUID.randomUUID(); val worker = UUID.randomUUID(); val branchWorker = UUID.randomUUID()
        val original = UUID.randomUUID(); val branch = UUID.randomUUID(); val internet = UUID.randomUUID()
        val item = UUID.randomUUID(); val branchItem = UUID.randomUUID(); val internetItem = UUID.randomUUID()
        val batch = UUID.randomUUID(); val branchBatch = UUID.randomUUID(); val sharedBranchBatch = UUID.randomUUID()
        val member = UUID.randomUUID(); val branchMember = UUID.randomUUID(); val request = UUID.randomUUID()
        val role = UUID.randomUUID(); val shift = UUID.randomUUID(); val receipt = UUID.randomUUID()
        val movement = UUID.randomUUID(); val operation = UUID.randomUUID()
        fun parent(id: UUID = original): String = scalar(c,
            "SELECT management_store_id FROM store_management_parent_migrations WHERE original_store_id='$id'")!!
    }

    private fun resource(name: String): String = requireNotNull(javaClass.getResourceAsStream("/db/migration/$name"))
        .bufferedReader().use { it.readText() }
    private fun migration(c: Connection, name: String) = exec(c, resource(name))
    private fun fixture(block: (Fixture) -> Unit) {
        val ownUrl = System.getenv("AITA_STORE_ARCH_TEST_DB_URL").orEmpty()
        val env = if (ownUrl.isNotBlank()) "AITA_STORE_ARCH_TEST_DB" else "AITA_MARKET_TEST_DB"
        val url = System.getenv("${env}_URL").orEmpty()
        assumeTrue("Set AITA_STORE_ARCH_TEST_DB_URL (or AITA_MARKET_TEST_DB_URL) to an isolated aita_test_* PostgreSQL database", url.isNotBlank())
        require(url.startsWith("jdbc:postgresql:"))
        val props = Properties().apply {
            System.getenv("${env}_USER")?.let { setProperty("user", it) }
            System.getenv("${env}_PASSWORD")?.let { setProperty("password", it) }
            setProperty("connectTimeout", "5"); setProperty("socketTimeout", "30")
            setProperty("ApplicationName", "aita-store-architecture-migration-test")
        }
        DriverManager.getConnection(url, props).use { c ->
            require(scalar(c, "SELECT current_database()")!!.startsWith("aita_test_")) { "Refusing non-test database" }
            c.autoCommit = false
            try {
                val schema = "store_arch_" + UUID.randomUUID().toString().replace("-", "")
                exec(c, "CREATE SCHEMA $schema; SET LOCAL search_path TO $schema; SET LOCAL statement_timeout='20s'; SET LOCAL lock_timeout='3s'")
                // Extract only these original CREATE TABLE statements: never execute V36's DROP section.
                val base = resource("V36__align_schema_for_launch.sql")
                listOf("users", "stores", "store_users", "store_subscriptions", "stock_items", "stock_batches", "transactions").forEach { table ->
                    exec(c, requireNotNull(Regex("CREATE TABLE $table \\([\\s\\S]*?\\n\\);").find(base)).value)
                }
                listOf("V42__cash_registers_and_store_workers.sql", "V43__public_ids_worker_invites_and_stock_sort.sql",
                    "V44__store_addresses_legal_ids_and_branches.sql", "V46__paging_user_finances_and_store_subscriptions.sql",
                    "V48__workshifts_and_worker_job_passwords.sql", "V49__operation_logs_and_privilege_enforcement.sql",
                    "V58__stock_barcode_integrity_and_cleanup.sql", "V72__create_stock_batch_movements.sql",
                    "V75__store_worker_role_templates_and_granular_permissions.sql", "V83__stores_active_schema_alignment_62518.sql",
                    "V101__per_location_subscriptions_and_promocodes.sql", "V104__opt_in_buyer_shop_windows.sql",
                    "V112__marketplace_product_profiles_parent_storefronts.sql", "V121__buyer_saved_shops.sql").forEach { migration(c, it) }
                val f = Fixture(c)
                seed(f)
                block(f)
            } finally { c.rollback() }
        }
    }

    private fun seed(f: Fixture) = with(f) {
        listOf(owner, worker, branchWorker).forEachIndexed { i, id -> exec(c, """INSERT INTO users
            (id,public_id,phone_number,email,first_name,last_name,country_locale,password_hash)
            VALUES ('$id','USER$i','test-phone-$i','user$i@example.invalid','Test','User','KZ','not-a-password')""") }
        exec(c, """INSERT INTO stores(id,public_id,owner_user_ids,country_locales,name,legal_id_type_id,legal_id,address) VALUES
            ('$original','ORIGINAL','["$owner"]','["KZ"]','[{"language":"en","text":"Original store"}]','kz_bin','123456789012','Original address'),
            ('$internet','INTERNET','["$owner"]','["KZ"]','[]','kz_bin','987654321098','Pickup address');
            INSERT INTO stores(id,public_id,parent_store_id,owner_user_ids,country_locales,address)
            VALUES ('$branch','BRANCH','$original','["$owner"]','["KZ"]','Branch address');
            UPDATE users SET active_store_id='$original' WHERE id='$owner';
            INSERT INTO store_users VALUES ('$original','$worker'),('$branch','$branchWorker');
            INSERT INTO store_worker_requests(id,store_id,requester_user_id,requested_at_millis,permissions,note)
            VALUES ('$request','$original','$worker',100,'["stock_read","sale_transaction"]','Root-wide request');
            INSERT INTO store_worker_memberships(id,store_id,user_id,accepted_at_millis,accepted_by_user_id,permissions,workshift_password_hash) VALUES
            ('$member','$original','$worker',100,'$owner','["stock_read","sale_transaction"]','retained-hash'),
            ('$branchMember','$branch','$branchWorker',100,'$owner','["stock_read"]','branch-hash');
            INSERT INTO store_worker_role_templates(id,store_id,name,permissions) VALUES
            ('$role','$original','[{"language":"en","text":"Root role"}]','["stock_read","sale_transaction"]');
            INSERT INTO workshifts(id,store_id,worker_membership_id,worker_user_id,started_at_millis,started_by_user_id)
            VALUES ('$shift','$original','$member','$worker',100,'$owner');
        """)
        listOf(Triple(item, original, "4006381333931"), Triple(branchItem, branch, "5901234123457"), Triple(internetItem, internet, "4601234567890"))
            .forEach { (id, store, barcode) -> exec(c, """INSERT INTO stock_items
                (id,user_id,store_id,barcodes,name,description,measurement_unit_id,sale_prices,note,created_at_millis,updated_at_millis)
                VALUES ('$id','$owner','$store','["$barcode"]','[{"language":"en","text":"Goods $barcode"}]',
                '[{"language":"en","text":"Preserved description"}]','piece','[{"price":"125","currency":"KZT"}]','Internal note',100,200)""") }
        exec(c, """INSERT INTO stock_batches(id,goods_item_id,user_id,store_id,quantity,supply_price,status,created_at_millis,updated_at_millis) VALUES
            ('$batch','$item','$owner','$original','{"value":7}','{"price":"85","currency":"KZT"}','Shelf',100,200),
            ('$branchBatch','$branchItem','$owner','$branch','{"value":5}','{"price":"85","currency":"KZT"}','Warehouse',100,200),
            ('$sharedBranchBatch','$item','$owner','$branch','{"value":3}','{"price":"85","currency":"KZT"}','Shelf',100,200);
            UPDATE stock_items SET active_shelf_batch_id='$batch' WHERE id='$item';
            INSERT INTO transactions(id,user_id,type,store_id,goods_in_transaction,paid_cash,time_millis)
            VALUES ('$receipt','$worker','purchase','$original','[{"goodsItemId":"$item","quantity":2,"price":125}]',250,250);
            INSERT INTO operation_logs(id,root_store_id,store_id,actor_user_id,workshift_id,action,entity_type,entity_id,created_at_millis)
            VALUES ('$operation','$original','$branch','$worker','$shift','transferred','stock_batch','$batch',300);
            INSERT INTO stock_batch_movements(id,root_store_id,source_store_id,destination_store_id,source_goods_item_id,destination_goods_item_id,
                source_batch_id,destination_batch_id,user_id,quantity,moved_at_millis)
            VALUES ('$movement','$original','$original','$branch','$item','$branchItem','$batch','$branchBatch','$worker','{"value":5}',300);
            INSERT INTO store_subscription_states(store_id,owner_user_id,plan_id,status,access_kind,current_period_start_millis,renewal_price_minor,revision) VALUES
            ('$original','$owner','internal_lifetime','active','lifetime',100,0,7);
            INSERT INTO store_subscription_states(store_id,owner_user_id,plan_id,status,access_kind,current_period_start_millis,current_period_end_millis,
                next_charge_at_millis,auto_renew,renewal_price_minor,revision) VALUES
            ('$branch','$owner','basic','active','paid',100,9999999999999,9999999999999,TRUE,799000,4),
            ('$internet','$owner','basic','active','paid',100,9999999999999,9999999999999,TRUE,799000,5);
            INSERT INTO store_subscriptions(store_id,history) VALUES ('$original','[{"kind":"lifetime","revision":7}]');
            INSERT INTO store_subscription_charge_events(store_id,user_id,plan_id,amount_minor,currency_code,period_start_millis,period_end_millis,status,created_at_millis)
            VALUES ('$branch','$owner','basic',799000,'KZT',100,9999999999999,'paid',100);
            INSERT INTO marketplace_storefronts(store_id,display_name,city,public_address,is_published,updated_by,updated_at_millis)
            VALUES ('$internet','Existing public shop','Astana','Pickup address',TRUE,'$owner',100);
            INSERT INTO marketplace_listings(id,store_id,goods_item_id,title,is_published,created_at_millis,updated_at_millis,updated_by)
            VALUES ('${UUID.randomUUID()}','$internet','$internetItem','Published goods',TRUE,100,100,'$owner');
        """)
    }

    @Test fun unselectedAccountRepairOnlyChoosesAnUnambiguousOwnedActiveParent() = fixture { f -> with(f) {
        migration(c, "V123__management_parents_and_operating_branches.sql")
        // The owner has two families; the worker owns just one parent; the branch-only worker owns none.
        exec(c, "UPDATE stores SET owner_user_ids=owner_user_ids || jsonb_build_array('$worker'::text) WHERE id='${parent()}'")
        exec(c, "UPDATE users SET active_store_id=NULL")
        migration(c, "V125__recover_unselected_management_store.sql")
        assertNull(scalar(c, "SELECT active_store_id FROM users WHERE id='$owner'"))
        assertEquals(parent(), scalar(c, "SELECT active_store_id FROM users WHERE id='$worker'"))
        assertNull(scalar(c, "SELECT active_store_id FROM users WHERE id='$branchWorker'"))
        exec(c, "UPDATE users SET active_store_id='$branch' WHERE id='$worker'")
        migration(c, "V125__recover_unselected_management_store.sql")
        assertEquals(branch.toString(), scalar(c, "SELECT active_store_id FROM users WHERE id='$worker'"))
        exec(c, "UPDATE users SET active_store_id=NULL WHERE id='$worker'; UPDATE stores SET is_active=FALSE WHERE id='${parent()}'")
        migration(c, "V125__recover_unselected_management_store.sql")
        assertNull(scalar(c, "SELECT active_store_id FROM users WHERE id='$worker'"))
    } }

    @Test fun migrationPreservesEveryOperatingIdentityAndAllPaidOrLifetimeData() = fixture { f -> with(f) {
        val tables = listOf("store_subscription_states", "store_subscriptions", "store_subscription_charge_events", "stock_batches", "transactions", "workshifts", "marketplace_storefronts", "marketplace_listings")
        val before = tables.associateWith { snapshot(c, it) }
        val originalItems = snapshot(c, "stock_items")
        migration(c, "V123__management_parents_and_operating_branches.sql")
        tables.forEach { assertEquals(before[it], snapshot(c, it), "$it must stay byte-for-byte equivalent as JSON") }
        assertEquals(originalItems, snapshot(c, "stock_items", "id IN ('$item','$branchItem','$internetItem')"))
        assertEquals(original.toString(), scalar(c, "SELECT store_id FROM stock_items WHERE id='$item'"))
        assertEquals(branch.toString(), scalar(c, "SELECT store_id FROM stock_items WHERE id='$branchItem'"))
        assertEquals("ORIGINAL", scalar(c, "SELECT public_id FROM stores WHERE id='$original'"))
        assertEquals(parent(), scalar(c, "SELECT parent_store_id FROM stores WHERE id='$original'"))
        assertEquals(parent(), scalar(c, "SELECT parent_store_id FROM stores WHERE id='$branch'"))
        assertEquals(parent(internet), scalar(c, "SELECT parent_store_id FROM stores WHERE id='$internet'"))
        assertEquals("PHYSICAL", scalar(c, "SELECT branch_type FROM stores WHERE id='$original'"))
        assertEquals("INTERNET", scalar(c, "SELECT branch_type FROM stores WHERE id='$internet'"))
        assertEquals(original.toString(), scalar(c, "SELECT active_store_id FROM users WHERE id='$owner'"))
        assertEquals("0", scalar(c, "SELECT count(*) FROM store_subscription_states ss JOIN stores s ON s.id=ss.store_id WHERE s.parent_store_id IS NULL"))
        val subscriptions = SubscriptionRepository(c)
        assertTrue(subscriptions.hasAccess(original, 5_000_000_000_000L), "Lifetime is still lifetime")
        assertNotNull(subscriptions.lockLocation(original))
        assertNull(subscriptions.lockLocation(UUID.fromString(parent())), "Management cannot enter billing")
        assertEquals("123456789012", scalar(c, "SELECT legal_id FROM stores WHERE id='${parent()}'"))
    } }

    @Test fun architectureAndMarketplaceMigrationsPreservePublishedShopAndSavedBookmark() = fixture { f -> with(f) {
        val pickup = UUID.randomUUID()
        exec(c, """INSERT INTO stores(id,public_id,parent_store_id,owner_user_ids,country_locales,address)
            VALUES ('$pickup','PICKUP','$internet','["$owner"]','["KZ"]','Physical pickup address');
            UPDATE marketplace_storefronts SET share_branch_availability=TRUE WHERE store_id='$internet';
            INSERT INTO buyer_saved_shop_states(user_id,revision) VALUES ('$worker',4);
            INSERT INTO buyer_saved_shops(user_id,store_id,created_at_millis) VALUES ('$worker','$internet',450);
        """)
        val storefront = scalar(c, "SELECT to_jsonb(f)::text FROM marketplace_storefronts f WHERE store_id='$internet'")
        val listings = snapshot(c, "marketplace_listings")
        val bookmarks = snapshot(c, "buyer_saved_shops")
        val bookmarkState = snapshot(c, "buyer_saved_shop_states")
        migration(c, "V123__management_parents_and_operating_branches.sql")
        migration(c, "V124__internet_branch_marketplace_locations.sql")
        assertEquals(storefront, scalar(c, "SELECT (to_jsonb(f)-'branch_store_id'-'location_store_ids')::text FROM marketplace_storefronts f WHERE store_id='$internet'"),
            "An already-published shop keeps its public identity, consent and revision through both migrations")
        assertEquals(listings, snapshot(c, "marketplace_listings"), "Published goods keep their original identity and publication state")
        assertEquals(bookmarks, snapshot(c, "buyer_saved_shops"))
        assertEquals(bookmarkState, snapshot(c, "buyer_saved_shop_states"))
        assertEquals(internet.toString(), scalar(c, "SELECT branch_store_id FROM marketplace_storefronts WHERE store_id='$internet'"))
        assertEquals("[\"$pickup\"]", scalar(c, "SELECT location_store_ids::text FROM marketplace_storefronts WHERE store_id='$internet'"))
        assertEquals("0", scalar(c, "SELECT count(*) FROM marketplace_storefronts f JOIN stores s ON s.id=f.branch_store_id WHERE s.parent_store_id IS NULL"))
        assertEquals("true", scalar(c, "SELECT convalidated::text FROM pg_constraint WHERE conrelid='buyer_saved_shops'::regclass AND confrelid='marketplace_storefronts'::regclass"))
        rejects(c, "23503") { exec(c, "INSERT INTO buyer_saved_shops(user_id,store_id,created_at_millis) VALUES ('$worker','$original',451)") }
    } }

    @Test fun rootWorkersRequestsAndRolesRemainRootWideButBranchWorkersStayScoped() = fixture { f -> with(f) {
        val operationBefore = scalar(c, "SELECT (to_jsonb(t)-'root_store_id')::text FROM operation_logs t WHERE id='$operation'")
        val movementBefore = scalar(c, "SELECT (to_jsonb(t)-'root_store_id')::text FROM stock_batch_movements t WHERE id='$movement'")
        migration(c, "V123__management_parents_and_operating_branches.sql")
        assertEquals(parent(), scalar(c, "SELECT store_id FROM store_worker_memberships WHERE id='$member'"))
        assertEquals(branch.toString(), scalar(c, "SELECT store_id FROM store_worker_memberships WHERE id='$branchMember'"))
        assertEquals(parent(), scalar(c, "SELECT store_id FROM store_worker_requests WHERE id='$request'"))
        assertEquals(parent(), scalar(c, "SELECT store_id FROM store_worker_role_templates WHERE id='$role'"))
        assertEquals("[\"stock_read\", \"sale_transaction\"]", scalar(c, "SELECT permissions::text FROM store_worker_memberships WHERE id='$member'"))
        assertEquals("retained-hash", scalar(c, "SELECT workshift_password_hash FROM store_worker_memberships WHERE id='$member'"))
        assertEquals("1", scalar(c, "SELECT count(*) FROM store_users WHERE store_id='${parent()}' AND user_id='$worker'"))
        assertEquals("0", scalar(c, "SELECT count(*) FROM store_users WHERE store_id='${parent()}' AND user_id='$branchWorker'"))
        assertEquals(parent(), scalar(c, "SELECT root_store_id FROM operation_logs WHERE id='$operation'"))
        assertEquals(parent(), scalar(c, "SELECT root_store_id FROM stock_batch_movements WHERE id='$movement'"))
        assertEquals(operationBefore, scalar(c, "SELECT (to_jsonb(t)-'root_store_id')::text FROM operation_logs t WHERE id='$operation'"))
        assertEquals(movementBefore, scalar(c, "SELECT (to_jsonb(t)-'root_store_id')::text FROM stock_batch_movements t WHERE id='$movement'"))
    } }

    @Test fun catalogueCopiesPreserveOperatingIdsQuantitiesAndLegacyBranchShelfReferences() = fixture { f -> with(f) {
        val batches = snapshot(c, "stock_batches")
        migration(c, "V123__management_parents_and_operating_branches.sql")
        assertEquals("5", scalar(c, "SELECT count(*) FROM stock_items"))
        assertEquals("2", scalar(c, "SELECT count(*) FROM stock_items WHERE store_id IN (SELECT management_store_id FROM store_management_parent_migrations)"))
        assertEquals("0", scalar(c, "SELECT count(*) FROM stock_batches WHERE store_id IN (SELECT management_store_id FROM store_management_parent_migrations)"))
        assertEquals(batches, snapshot(c, "stock_batches"))
        assertEquals("15", scalar(c, "SELECT sum((quantity->>'value')::int) FROM stock_batches"))
        assertEquals(batch.toString(), scalar(c, "SELECT active_shelf_batch_id FROM stock_items WHERE id='$item'"))
        assertEquals(item.toString(), scalar(c, "SELECT goods_item_id FROM stock_batches WHERE id='$sharedBranchBatch' AND store_id='$branch'"))
        assertEquals("0", scalar(c, "SELECT count(*) FROM stock_items WHERE store_id='${parent()}' AND active_shelf_batch_id IS NOT NULL"))
        val fields = "-'id'-'store_id'-'active_shelf_batch_id'-'updated_at_millis'"
        assertEquals(scalar(c, "SELECT (to_jsonb(i)$fields)::text FROM stock_items i WHERE id='$item'"),
            scalar(c, "SELECT (to_jsonb(i)$fields)::text FROM stock_items i WHERE store_id='${parent()}'"))
        rejects(c, "23505") { exec(c, "INSERT INTO stock_items SELECT (jsonb_populate_record(NULL::stock_items,to_jsonb(i)||jsonb_build_object('id',gen_random_uuid()))).* FROM stock_items i WHERE id='$item'") }
        // A real branch may pull the catalogue title into its own namespace, once.
        exec(c, "INSERT INTO stock_items SELECT (jsonb_populate_record(NULL::stock_items,to_jsonb(i)||jsonb_build_object('id',gen_random_uuid(),'store_id','$branch','active_shelf_batch_id',NULL))).* FROM stock_items i WHERE id='$item'")
        rejects(c, "23505") { exec(c, "INSERT INTO stock_items SELECT (jsonb_populate_record(NULL::stock_items,to_jsonb(i)||jsonb_build_object('id',gen_random_uuid(),'store_id','$branch'))).* FROM stock_items i WHERE id='$item'") }
    } }

    @Test fun migrationMappingsDoNotPreventDeletingStores() = fixture { f -> with(f) {
        migration(c, "V123__management_parents_and_operating_branches.sql")
        // Existing store deletion clears owned data first; the retained mapping must never block it.
        exec(c, "DELETE FROM marketplace_listings WHERE store_id='$internet'; DELETE FROM stock_items WHERE store_id='$internet'; DELETE FROM stores WHERE id='$internet'")
        assertEquals("0", scalar(c, "SELECT count(*) FROM store_management_parent_migrations WHERE original_store_id='$internet'"))
        assertNotNull(scalar(c, "SELECT id FROM stores WHERE id='${parent()}'"))
    } }

    @Test fun parentsCannotGainSubscriptionsAndBranchesRequireAValidType() = fixture { f -> with(f) {
        migration(c, "V123__management_parents_and_operating_branches.sql")
        rejects(c, "23514") { exec(c, "INSERT INTO store_subscription_states(store_id,owner_user_id) VALUES ('${parent()}','$owner')") }
        rejects(c, "23514") { exec(c, "UPDATE store_subscription_states SET store_id='${parent()}' WHERE store_id='$original'") }
        rejects(c, "23514") { exec(c, "INSERT INTO store_subscriptions(store_id) VALUES ('${parent()}')") }
        rejects(c, "23514") { exec(c, "UPDATE store_subscriptions SET store_id='${parent()}' WHERE store_id='$original'") }
        rejects(c, "23514") { exec(c, "UPDATE stores SET branch_type=NULL WHERE id='$branch'") }
        rejects(c, "23514") { exec(c, "UPDATE stores SET branch_type='OTHER' WHERE id='$branch'") }
        rejects(c, "23514") { exec(c, "UPDATE stores SET branch_type='PHYSICAL' WHERE id='${parent()}'") }
    } }

    @Test fun movingParentsAndPromotingSubscribedBranchesCannotBypassStructuralGuards() = fixture { f -> with(f) {
        migration(c, "V123__management_parents_and_operating_branches.sql")
        rejects(c, "23514") { exec(c, "INSERT INTO stores(id,public_id,parent_store_id,branch_type) VALUES (gen_random_uuid(),'NESTED','$branch','PHYSICAL')") }
        rejects(c, "23514") { exec(c, "UPDATE stores SET parent_store_id='${parent(internet)}',branch_type='PHYSICAL' WHERE id='${parent()}'") }
        rejects(c, "23514") { exec(c, "UPDATE stores SET parent_store_id=NULL,branch_type=NULL,legal_id='' WHERE id='$branch'") }
        rejects(c, "23514") { exec(c, "UPDATE stores SET parent_store_id=id WHERE id='$branch'") }
    } }

    companion object {
        private fun exec(c: Connection, sql: String) { c.createStatement().use { it.execute(sql) } }
        private fun scalar(c: Connection, sql: String): String? = c.createStatement().use { s ->
            s.executeQuery(sql).use { rows -> if (rows.next()) rows.getString(1) else null }
        }
        private fun snapshot(c: Connection, table: String, where: String = "TRUE"): String? = scalar(c,
            "SELECT jsonb_agg(value ORDER BY value::text)::text FROM (SELECT to_jsonb(t) value FROM $table t WHERE $where) rows")
        private fun rejects(c: Connection, state: String, block: () -> Unit) {
            val point = c.setSavepoint()
            try { assertEquals(state, assertFailsWith<SQLException>(block = block).sqlState) }
            finally { c.rollback(point) }
        }
    }
}
