#!/usr/bin/env python3
"""Executable SQLite regressions and source/resource contracts, not a Compose/PostgreSQL build."""
from pathlib import Path
import json
import re
import sqlite3
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[3]
SHARED = ROOT / 'shared/src/commonMain/kotlin/kz/aita'
UI = ROOT / 'composeApp/src/commonMain/kotlin/kz/aita'
SERVER = ROOT / 'server/src/main/kotlin/kz/aita/server'
SQL = (ROOT / 'shared/src/commonMain/sqldelight/kz/aita/app_database.sq').read_text()
def query(name): return re.search(r'\b' + name + r':\s*(.*?;)', SQL, re.S).group(1)
def read(root, name): return (root / name).read_text()

class CacheBindings(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.execute('CREATE TABLE key_value(key TEXT PRIMARY KEY,value TEXT)')
    def tearDown(self): self.db.close()
    def test_explicit_named_numeric_casts_and_string_key(self):
        self.db.execute('INSERT INTO key_value VALUES (?,?)', ('key', 'abcdef'))
        self.assertEqual(self.db.execute(query('selectKvSlice'), {'cacheKey':'key','sliceOffset':2,'sliceLength':3}).fetchone(), ('key','bcd'))
        call=read(SHARED,'JsonCacheStorage.kt')
        for named in ('sliceOffset = offset','sliceLength = 32_768L','cacheKey = key','manifestKey = prefix + "manifest"'): self.assertIn(named,call)
    def test_unicode_slice_boundaries(self):
        original='😀А🌿B'*40_000
        self.db.execute('INSERT INTO key_value VALUES (?,?)', ('unicode',original))
        parts=[self.db.execute(query('selectKvSlice'),{'cacheKey':'unicode','sliceOffset':i,'sliceLength':32_768}).fetchone()[1] for i in range(1,len(original)+1,32_768)]
        self.assertEqual(original,''.join(parts))
    def test_literal_prefix_cleanup_preserves_manifest_and_other_owners(self):
        keys=('x%_:manifest','x%_:new:1','x%_:old:1','xAAz:private')
        self.db.executemany('INSERT INTO key_value VALUES (?,?)',[(x,'data') for x in keys])
        self.db.execute(query('deleteKvPrefixExcept'),{'prefix':'x%_:','manifestKey':'x%_:manifest','keepPrefix':'x%_:new:'})
        self.assertEqual({'x%_:manifest','x%_:new:1','xAAz:private'}, {r[0] for r in self.db.execute('SELECT key FROM key_value')})
    def test_actual_generated_query_test_is_included(self):
        test=read(ROOT,'shared/src/jvmTest/kotlin/kz/aita/JsonCacheQueryBindingTest.kt')
        self.assertIn('AppDatabase.Schema.create(driver).await()',test)
        self.assertIn('database.app_databaseQueries',test)
        self.assertNotIn('class App_databaseQueries',test)

class ProductContracts(unittest.TestCase):
    def test_models_are_separate_from_private_stock_and_default_unpublished(self):
        self.assertIn('val marketplaceProfile: StockMarketplaceProfile? = null',read(SHARED,'CommonMain.kt'))
        models=read(SHARED,'MarketplaceModels.kt')
        self.assertIn('val published: Boolean = false',models)
        self.assertIn('val shareBranchAvailability: Boolean = false',models)
        self.assertIn('val replaceProduct: Boolean = false',models)
    def test_stock_create_update_clone_and_row_mapping_keep_profile(self):
        server=read(SERVER,'Server.kt')
        self.assertIn('val marketplaceProfile = jsonb("marketplace_profile"',server)
        self.assertEqual(2,server.count('it[StockItems.marketplaceProfile] = requestedMarketProfile'))
        self.assertIn('it[StockItems.marketplaceProfile] = sourceItemRow[StockItems.marketplaceProfile]',server)
        self.assertIn('marketplaceProfile = this[StockItems.marketplaceProfile].fromCurrentStock',server)
    def test_parent_guard_and_read_visibility_are_both_server_side(self):
        repository=read(SERVER,'marketplace/MarketplaceRepository.kt')
        self.assertIn('SELECT parent_store_id FROM stores WHERE id=?',repository)
        self.assertIn('marketFail("market.profile_parent_only", 403)',repository)
        self.assertIn('s.parent_store_id IS NULL',read(SERVER,'marketplace/MarketplacePublicVisibility.kt'))
        self.assertIn('subscriptions.lockLocation(store)',repository)
    def test_db_backstop_serializes_with_reparenting_and_withdraws_not_deletes(self):
        sql=read(ROOT,'server/src/main/resources/db/migration/V112__marketplace_product_profiles_parent_storefronts.sql')
        for text in ('FOR SHARE','IF NOT FOUND','AFTER UPDATE OF parent_store_id','BEFORE INSERT OR UPDATE ON marketplace_listings','BEFORE INSERT OR UPDATE ON marketplace_storefronts'):
            self.assertIn(text,sql)
        self.assertNotRegex(sql,r'(?i)DELETE\s+FROM')
    def test_listing_product_is_reviewed_snapshot_not_live_private_profile(self):
        repo=read(SERVER,'marketplace/MarketplaceRepository.kt')
        self.assertIn('!request.replaceProduct && previous != null',repo)
        self.assertIn('previous.product else raw.product',repo)
        self.assertRegex(repo, r'product\s*=\s*listing\.product')
        self.assertIn('MarketListingUpdate(value, replaceProduct=true)',read(SHARED,'MarketplaceClient.kt'))
    def test_branch_availability_is_opt_in_bounded_and_unit_gtin_matched(self):
        repo=read(SERVER,'marketplace/MarketplaceRepository.kt')
        block=repo.split('private fun branchAvailability(',1)[1].split('private fun items(',1)[0]
        for text in ('!offer.storefront.shareBranchAvailability','MARKET_BRANCH_MAX_LOCATIONS + 1','LIMIT 5001','marketSameBranchProduct','candidate.storeId==batch.storeId','batch.quantity.id==item.measurementUnitId','s.parent_store_id=?','e.store_id=s.id'):
            self.assertIn(text,block)
        self.assertNotIn('ILIKE',block);self.assertNotIn('name.lowercase()',block)
    def test_branch_stock_changes_invalidate_parent_public_catalogue(self):
        route=read(SERVER,'marketplace/MarketplaceRoutes.kt').split('internal suspend fun publishMarketplaceStockChange',1)[1]
        self.assertIn('f.share_branch_availability',route)
        self.assertIn('changed.parent_store_id=f.store_id',route)
        self.assertIn('entity="market/catalog"',route)
    def test_branch_envelope_has_no_quantity_or_price_fields(self):
        source=read(SHARED,'MarketProductProfile.kt').split('data class MarketBranchAvailability(',1)[1].split('\n)',1)[0]
        for private in ('quantity','price','supplier','owner','token','goodsItemId'): self.assertNotIn(private,source)
    def test_status_reader_is_permission_scoped_rate_bounded_and_read_only(self):
        route=read(SERVER,'marketplace/MarketplaceRoutes.kt').split('get("/seller/stock-status")',1)[1].split('get("/seller")',1)[0]
        for text in ('checkPrincipal','stockPublicationReadGate.acquire','readOnly = true','statement_timeout','finally','stockPublicationReadGate.release'):
            self.assertIn(text,route)
        self.assertIn('if (!readsStock(user, store))',read(SERVER,'marketplace/MarketplaceRepository.kt'))
    def test_status_workspace_is_once_per_app_not_per_item(self):
        workspace=read(SHARED,'MarketStockPublicationState.kt')
        for term in ('compareAndSet(false,true)','Dispatchers.Default','collectLatest','expectedSessionGeneration=owner.generation','owner.isCurrent()','stale=true'):
            self.assertIn(term,workspace)
        self.assertIn('MarketStockPublicationWorkspace.start()',read(UI,'CommonMainComposeWidgetsConfig.kt'))
        self.assertNotIn('networkRequest',read(UI,'MarketProductProfileUi.kt'))
    def test_private_draft_supports_both_full_and_quick_stock_editors(self):
        self.assertIn('StockMarketplaceEditor(',read(UI,'CommonMainComposeCartStockA.kt'))
        self.assertIn('StockMarketplaceEditor(',read(UI,'CommonMainComposeNavigation.kt'))
        self.assertIn('StockMarketplaceBadge(goodsItem)',read(UI,'CommonMainComposeWidgetsConfig.kt'))
    def test_restoration_keeps_internal_barcode_type(self):
        source=read(UI,'CommonMainComposeCartStockA.kt')
        self.assertIn('barcodeTypes = decodedBarcodeTypes',source)
        self.assertIn('marketplaceProfile = values.getOrNull(18)',source)
    def test_product_images_bypass_numbered_svg_lookup(self):
        source=read(UI,'MarketProductProfileUi.kt')
        self.assertIn('asyncPainterResource(data = Url(destination))',source)
        self.assertIn('LocalKamelConfig provides kamelConfig',source)
        self.assertIn('ContentScale.Fit',source)
        self.assertIn('onFailure =',source)
    def test_shimmer_requires_specific_layout_instead_of_generic_default(self):
        source=read(UI,'AitaLoadingMotion.kt')
        self.assertIn('layout: LoadingLayout,',source)
        self.assertNotRegex(source,r'layout:\s*LoadingLayout\s*=')
        for kind in ('StockCard','MarketplaceCard','OfferDetail','Notification','Subscription','Message','PaymentIntegration','SupplierSummary'):
            self.assertIn('LoadingLayout.'+kind+' ->',source)
    def test_shimmer_respects_reduced_motion_and_draws_without_recomposing_bars(self):
        source=read(UI,'AitaLoadingMotion.kt')
        for text in ('MotionDurationScale','scale <= 0f','onDrawBehind','phase.value'): self.assertIn(text,source)
    def test_refresh_retains_existing_offer_and_conversation_content(self):
        self.assertIn('current == null && loading',read(UI,'MarketOfferDetailDialog.kt'))
        source=read(UI,'SupportMessengerScreen.kt')
        self.assertIn('messages.isEmpty()',source);self.assertIn('tickets.isEmpty()',source)
    def test_new_messages_have_all_six_languages_and_registration(self):
        lines=[s for s in read(SHARED,'messages/MarketplaceProductMessages.kt').splitlines() if 'EventMessageTemplate(' in s]
        self.assertGreater(len(lines),30)
        for line in lines:
            for lang in ('ky =','tg =','uz ='): self.assertIn(lang,line)
        self.assertIn('marketplaceProductMessageTemplates()',read(SHARED,'messages/EventMessageReference.kt'))
    def test_svg_and_android_vectors_are_identical_geometry(self):
        android='{http://schemas.android.com/apk/res/android}'
        for variant in (0,1):
            filename=f'148_{variant}.svg'
            server=ROOT/'server/assets/drawable/svg'/filename
            compose=ROOT/'composeApp/src/commonMain/composeResources/drawable'/filename
            self.assertEqual(server.read_bytes(),compose.read_bytes())
            svg=ET.fromstring(server.read_bytes());vector=ET.parse(ROOT/f'composeApp/src/androidMain/res/drawable/ic_aita_148_{variant}.xml').getroot()
            self.assertEqual([p.attrib['d'] for p in svg.findall('{http://www.w3.org/2000/svg}path')],
                             [p.attrib[android+'pathData'] for p in vector.findall('path')])
    def test_svg_catalogues_and_android_shrinker_include_new_family(self):
        for path in ('server/assets/drawable/drawables.json','composeApp/src/commonMain/composeResources/files/assets/drawable/drawables.json'):
            data=json.loads(read(ROOT,path));self.assertEqual(1,len(re.findall(r'"id":\s*148\b',json.dumps(data))))
        self.assertIn('ic_aita_148_0',read(ROOT,'composeApp/src/androidMain/res/raw/aita_market_product_keep.xml'))
        self.assertIn('148 ->',read(UI,'MarketplaceIcons.kt'))
    def test_auth_types_exist_in_the_shared_source_set_and_are_explicitly_imported(self):
        definitions=read(SHARED,'auth/AdvancedAuthenticationModels.kt')
        for name in ('AitaAuthFlowDataModel','AitaAuthenticationSettingsDataModel'):
            self.assertIn('data class '+name,definitions)
            self.assertIn('import kz.aita.auth.'+name,read(UI,'AuthenticationEmailUi.kt'))
        self.assertIn('import kz.aita.auth.AitaAuthFlowDataModel',read(UI,'ContactEmailConfirmation.kt'))

if __name__=='__main__': unittest.main()
