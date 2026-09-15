#!/usr/bin/env python3
"""Source/resource contracts and executable SQLite cache queries; not rendered UI/HTTP tests."""
from pathlib import Path
import json
import re
import sqlite3
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
SHARED = ROOT / 'shared/src/commonMain/kotlin/kz/aita'
UI = ROOT / 'composeApp/src/commonMain/kotlin/kz/aita'
SERVER = ROOT / 'server/src/main/kotlin/kz/aita/server/Server.kt'
SQL = (ROOT / 'shared/src/commonMain/sqldelight/kz/aita/app_database.sq').read_text()


def query(name):
    return re.search(r'\b' + name + r':\s*(.*?;)', SQL, re.S).group(1)


class InventoryContinuityContracts(unittest.TestCase):
    def test_one_reorder_intent_not_two_business_writes(self):
        text = (UI / 'CommonMainComposeCartStockA.kt').read_text()
        body = text.split('internal fun AppConfiguration.reorderShelfBatches(', 1)[1].split('\ninternal fun ', 1)[0]
        self.assertEqual(body.count('saveShelfOrder('), 1)
        self.assertNotIn('updateGoodsBatches(', body)
        self.assertNotIn('setActiveShelfBatch(', body)
        self.assertIn('expectedBatchIds', body)

    def test_shelf_server_uses_scope_permission_workshift_and_inventory_lock(self):
        route = SERVER.read_text().split('post("/reorderShelf")', 1)[1].split('post("/setActiveShelfBatch")', 1)[0]
        for term in ('matchesInventoryContextStoreIdInsideTransaction', 'STORE_PERMISSION_STOCK_BATCH_SET_ACTIVE_SHELF',
                     'requireWorkshift = true', 'lockStockInventoryInsideTransaction', 'newSuspendedTransaction',
                     'ShelfOrderDecision.Unchanged', 'HttpStatusCode.Conflict'):
            self.assertIn(term, route)
        self.assertEqual(route.count('publishStockRealtimeBundle('), 1)
        self.assertNotRegex(route, r'it\[StockBatchesV2\.(quantity|supplyPrice|salePriceOverride)\]\s*=')

    def test_client_checks_ack_owner_and_preserves_more_recent_rows(self):
        text = (SHARED / 'ShelfOrderClient.kt').read_text()
        for term in ('shelfOrderAcknowledgementMatches', 'inventoryOwnerIsCurrent(owner)', 'expectedSessionGeneration',
                     'updatedAtMillis > remote.updatedAtMillis', 'if (result.changed) postInAppNotification'):
            self.assertIn(term, text)

    def test_current_item_no_longer_hides_batches_during_projection_work(self):
        text = (UI / 'CommonMainComposeCartStockA.kt').read_text()
        self.assertIn('showBatches = showBatches && belongsToVisibleInventory', text)
        self.assertNotIn('showBatches = showBatches && projectionCurrent', text)
        self.assertIn('page = page.coerceIn', text)
        self.assertIn('shelfActionsEnabled = canOperateThisStoreInventory', text)
        self.assertIn('enabled = shelfActionsEnabled && !selectionMode', (UI / 'CommonMainComposeWidgetsConfig.kt').read_text())

    def test_retained_indexes_are_started_at_application_root(self):
        text = (UI / 'LiveCollectionWorkspace.kt').read_text()
        for term in ('SupervisorJob() + Dispatchers.Default', 'collectLatest', 'inventoryViewScopeKey()',
                     'notificationSelection', 'storesState.payload', 'ensureActive()', 'warehouseResult.value'):
            self.assertIn(term, text)
        self.assertIn('LiveCollectionWorkspace.start()', (UI / 'CommonMainComposeWidgetsConfig.kt').read_text())

    def test_notification_screen_keeps_list_state_and_does_not_auto_clear_unread_tab(self):
        text = (UI / 'CommonMainComposeMenuB.kt').read_text().split('fun AppConfiguration.NotificationsScreen(', 1)[1].split('internal fun AppConfiguration.notificationTypeLabel', 1)[0]
        for term in ('rememberPersistentLazyListState', 'selectedCategory != "unread"', 'visibleItemsInfo', 'items(filtered, key = { it.id })'):
            self.assertIn(term, text)
        self.assertNotIn('markAllNotificationsRead()', text)

    def test_notification_read_ack_cannot_replace_whole_list(self):
        text = (SHARED / 'CommonMain.kt').read_text()
        get = text.split('fun getNotifications()', 1)[1].split('fun saveNotificationToServer(', 1)[0]
        read = text.split('fun markNotificationsRead(', 1)[1].split('private fun List<SupportTicketDataModel>', 1)[0]
        self.assertIn('mergeNotificationSnapshot(before, currentNotifications, serverNotifications)', get)
        self.assertIn('applyNotificationReadAcknowledgement(', read)
        self.assertNotIn('DataState.Empty', get)
        self.assertNotIn('DataState.Success(response.payload', read)

    def test_cache_removals_remove_manifests_not_only_legacy_values(self):
        text = (SHARED / 'CommonMain.kt').read_text()
        for key in ('CACHE_PENDING_SESSION_CLEANUPS', 'CACHE_PENDING_WORKSHIFT_ENDS'):
            self.assertIn('deleteJsonCache(' + key + ')', text)
            self.assertNotIn('putLocalKv(CACHE_PREFIX + ' + key + ', null)', text)
        self.assertIn('deleteJsonCacheText(CACHE_PREFIX + key)', text)

    def test_cache_hydration_keeps_account_and_store_fences(self):
        text = (SHARED / 'InventoryLoadState.kt').read_text()
        self.assertIn('inventory-v2:$name:${owner.accountId}:${owner.storeId}', text)
        self.assertIn('httpStatus == 403 && !transportFailure', text)
        self.assertIn('ownerIsCurrent && !hasPayload', text)
        self.assertIn('inventoryStateMutex.withLock', text)

    def test_new_arrow_and_promo_assets_match_and_have_native_android_paths(self):
        android = '{http://schemas.android.com/apk/res/android}'
        for family in (146, 147):
            for theme in (0, 1):
                name = f'{family}_{theme}'
                server = ROOT / f'server/assets/drawable/svg/{name}.svg'
                compose = ROOT / f'composeApp/src/commonMain/composeResources/drawable/{name}.svg'
                self.assertEqual(server.read_bytes(), compose.read_bytes())
                svg = ET.parse(server).getroot()
                vector = ET.parse(ROOT / f'composeApp/src/androidMain/res/drawable/ic_aita_{name}.xml').getroot()
                self.assertEqual([p.attrib['d'] for p in svg], [p.attrib[android + 'pathData'] for p in vector])
                self.assertIn(f'Res.drawable._{name}', (UI / 'CommonMainComposeWidgetsConfig.kt').read_text())
                self.assertIn(f'@drawable/ic_aita_{name}', (ROOT / 'composeApp/src/androidMain/res/raw/aita_stock_navigation_keep.xml').read_text())

    def test_catalogues_register_both_new_families_without_duplicates(self):
        server = json.loads((ROOT / 'server/assets/drawable/drawables.json').read_text())
        compose = json.loads((ROOT / 'composeApp/src/commonMain/composeResources/files/assets/drawable/drawables.json').read_text())
        self.assertEqual(server, compose)
        rows = next(v for v in server.values() if isinstance(v, list))
        ids = [row['id'] for row in rows]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertTrue({146, 147}.issubset(ids))

    def test_quick_add_promo_is_separate_from_conditions(self):
        text = (UI / 'CommonMainComposeCartStockA.kt').read_text()
        self.assertIn('id = "promos"', text)
        self.assertIn('"promos" ->', text)
        self.assertIn('StockPromotionListEditor(', text)
        self.assertIn('"conditions" ->', text)
        self.assertIn('Res.drawable._146_0', (UI / 'CommonMainComposeCore.kt').read_text())
        self.assertIn('appearanceResources.catalog.drawable(147L, appThemeId)', (UI / 'CommonMainComposeWidgetsConfig.kt').read_text())

    def test_speech_uses_real_detection_not_the_requested_ui_language(self):
        text = (ROOT / 'composeApp/src/androidMain/kotlin/kz/aita/android/AndroidCompose.kt').read_text()
        for term in ('override fun onLanguageDetection', 'SpeechRecognizer.DETECTED_LANGUAGE',
                     'installedOnDeviceLanguages', 'LANGUAGE_SWITCH_BALANCED', '!texts.automaticLanguageDetection'):
            self.assertIn(term, text)
        self.assertNotIn('EXTRA_LANGUAGE_SWITCH_INITIAL_ACTIVE_DURATION_TIME_MILLIS', text)
        self.assertNotIn('callbacks.onDetectedLanguage(texts.primaryLanguageTag)', text)
        self.assertIn('finishActiveSession = null', text)

    def test_no_cache_screen_has_explicit_status_and_parent_sized_actions(self):
        text = (UI / 'InventoryLoadingUi.kt').read_text()
        for term in ('inventory.no_offline_copy', 'InventoryLoadSource.None', 'Modifier.widthIn(max = 720.dp)',
                     'diagnoseAitaConnection()', 'Modifier.fillMaxWidth()', 'InventoryFeedbackKind.SavedOffline'):
            self.assertIn(term, text)

    def test_diagnostics_are_read_only_and_do_not_bypass_certificates(self):
        text = (SHARED / 'ConnectionDiagnostics.kt').read_text()
        for term in ('cloudHealthHttpClient.get', 'withTimeoutOrNull', 'AITA_SERVER_HEADER', 'diagnosticRunning.compareAndSet'):
            self.assertIn(term, text)
        for term in ('getStoredUserAuthTokens', 'Bearer', 'deleteJsonCache', 'trustAll', 'HttpMethod.Post'):
            self.assertNotIn(term, text)
        server = SERVER.read_text()
        self.assertIn('allowHeader(HttpHeaders.CacheControl)', server)
        self.assertIn('allowHeader(HttpHeaders.Pragma)', server)

    def test_new_messages_have_all_six_languages_and_are_registered(self):
        text = (SHARED / 'messages/InventoryContinuityMessages.kt').read_text()
        rows = [line for line in text.splitlines() if 'EventMessageTemplate(' in line]
        self.assertGreater(len(rows), 20)
        for row in rows:
            for term in ('ky = ', 'tg = ', 'uz = '): self.assertIn(term, row)
        self.assertIn('inventoryContinuityMessageTemplates()', (SHARED / 'messages/EventMessageReference.kt').read_text())


class CacheSqlQueries(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.execute(re.search(r'CREATE TABLE key_value\s*\(.*?\);', SQL, re.S).group())

    def tearDown(self):
        self.db.close()

    def test_legacy_large_unicode_value_can_be_read_in_bounded_slices(self):
        value = 'item\U0001f600ӱ' * 100_000
        self.db.execute(query('insertKv'), ('stock', value))
        key, size = self.db.execute(query('selectKvLength'), ('stock',)).fetchone()
        self.assertEqual(size, len(value))
        parts = [self.db.execute(query('selectKvSlice'), (start, 32_768, 'stock')).fetchone()[1] for start in range(1, size + 1, 32_768)]
        self.assertEqual(''.join(parts), value)
        self.assertTrue(all(len(part.encode('utf-8')) <= 4 * 32_768 for part in parts))

    def test_cleanup_preserves_only_committed_generation_and_manifest(self):
        prefix = 'aita-cache-chunks-v1:5:stock:'
        keys = [prefix + 'manifest', prefix + '1:0', prefix + '2:0', prefix + '2:1', prefix + '3:0', 'other']
        self.db.executemany(query('insertKv'), [(key, 'v') for key in keys])
        keep = prefix + '2:'
        self.db.execute(query('deleteKvPrefixExcept'), {'prefix':prefix,'manifestKey':prefix+'manifest','keepPrefix':keep})
        self.assertEqual({row[0] for row in self.db.execute('SELECT key FROM key_value')}, {prefix + 'manifest', prefix + '2:0', prefix + '2:1', 'other'})

    def test_prefix_cleanup_treats_percent_underscore_as_literal(self):
        prefix = 'scope_1%:'
        self.db.executemany(query('insertKv'), [(prefix+'1:0','v'), ('scopeZ12:1:0','safe')])
        self.db.execute(query('deleteKvPrefixExcept'), {'prefix':prefix,'manifestKey':prefix+'manifest','keepPrefix':''})
        self.assertEqual(self.db.execute('SELECT key FROM key_value').fetchall(), [('scopeZ12:1:0',)])

    def test_nullable_legacy_value_is_not_an_authoritative_empty_json(self):
        self.db.execute(query('insertKv'), ('empty',None))
        self.assertIsNone(self.db.execute(query('selectKvLength'), ('empty',)).fetchone()[1])
        self.db.execute(query('insertKv'), ('empty',''))
        self.assertEqual(self.db.execute(query('selectKvLength'), ('empty',)).fetchone()[1], 0)


if __name__ == '__main__':
    unittest.main()
