#!/usr/bin/env python3
"""Source/resource contracts only. These do not replace Compose, Gradle or real SQL tests."""
from pathlib import Path
import importlib.util
import json
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
SHARED = ROOT / "shared/src/commonMain/kotlin/kz/aita"
UI = ROOT / "composeApp/src/commonMain/kotlin/kz/aita/MarketShopDirectoryPanel.kt"
ROUTES = ROOT / "server/src/main/kotlin/kz/aita/server/marketplace/MarketplaceRoutes.kt"


def between(text, first, following):
    start = text.index(first)
    return text[start:text.index(following, start)]


class ShopDirectorySafetyWiring(unittest.TestCase):
    def test_rows_must_match_both_literal_name_address_terms_and_city(self):
        source = (SHARED / "MarketShopDirectoryModels.kt").read_text()
        validator = between(source, "fun MarketShopDirectoryResult.isValidShopDirectoryResult", "/** No account")
        for required in ('account.isNotBlank()', 'shop.city.contains(normalized.city, ignoreCase = true)',
                         '"${shop.displayName} ${shop.publicAddress}"', "normalized.text.split(' ')",
                         'searchable.contains(it, ignoreCase = true)', 'shop.isValidPublicMarketShop()'):
            self.assertIn(required, validator)
        self.assertNotIn('shop.pickupNote}', validator)
        self.assertIn('const val MARKET_SHOPS_PROTOCOL = 1', source)

    def test_one_owned_cancellable_read_does_not_accept_failed_http_or_transport(self):
        source = (SHARED / "MarketShopDirectoryModels.kt").read_text().split('suspend fun readOwnedMarketShopDirectory', 1)[1]
        self.assertEqual(1, source.count('val response = read(normalized)'))
        self.assertEqual(3, source.count('currentCoroutineContext().ensureActive()'))
        self.assertEqual(3, source.count('owner.isCurrent()'))
        self.assertLess(source.index('if (response.transportFailure)'), source.index('if (response.httpStatusCode == 404)'))
        self.assertIn('response.httpStatusCode != 200', source)
        self.assertNotIn('catch', source)
        self.assertNotIn('saved', source)

    def test_inputs_invalidate_before_debounce_and_aba_edits_requeue(self):
        source = UI.read_text()
        update = between(source, 'fun setSearch(', 'fun viewBlocked(')
        self.assertLess(update.index('data.invalidate()'), update.index('navigation.text = text'))
        self.assertEqual(2, source.count('setSearch(text = it.take(120))'))
        self.assertEqual(2, source.count('setSearch(city = it.take(100))'))
        self.assertIn('setSearch(text = "", city = "")', source)
        self.assertIn('else if (data.loadedStamp == null)', source)
        self.assertIn('navigation.scroll.scrollToItem(0)', source)

    def test_refresh_immediately_retires_current_rows_and_discards_old_errors_too(self):
        source = UI.read_text()
        queue = between(source, 'fun queueRefresh()', 'fun setSearch(')
        self.assertLess(queue.index('data.invalidate()'), queue.index('requests.trySend(Unit)'))
        read = between(source, 'val response = loadMarketShopDirectory', 'catch (cancelled: CancellationException)')
        self.assertLess(read.index('!data.fence.isCurrent(stamp, navigation.request'), read.index('navigation.result = result'))
        self.assertLess(read.index('!data.fence.isCurrent(stamp, navigation.request'), read.index('data.error = response.message'))
        self.assertIn('requests.tryReceive().isSuccess', source)
        self.assertIn('Channel<Unit>(Channel.CONFLATED)', source)
        self.assertIn('data.active = false; data.invalidate(); requests.close()', source)

    def test_retained_navigation_callbacks_recheck_live_state_and_exact_card(self):
        source = UI.read_text()
        visit = between(source, 'fun visitDisplayed(', 'fun expandWindow(')
        for required in ('data.fence.canVisit', 'navigation.result, entry, navigation.request',
                         'MarketplaceSignals.revision.value', 'viewBlocked()', 'latestVisit(entry.storefront)'):
            self.assertIn(required, visit)
        expand = between(source, 'fun expandWindow()', 'DisposableEffect(data)')
        self.assertIn('!resultIsCurrent()', expand)
        self.assertIn('navigation.request.limit >= MARKET_SHOPS_MAX_WINDOW', expand)
        self.assertLess(expand.index('data.invalidate()'), expand.index('navigation.request ='))
        self.assertIn('onClick = { visitDisplayed(entry) }', source)
        self.assertIn('onClick = { expandWindow() }', source)
        self.assertNotIn('onVisit(shop)', source)

    def test_age_is_monotonic_and_previous_counts_are_labelled(self):
        source = UI.read_text()
        self.assertIn('data.receivedAt = TimeSource.Monotonic.markNow()', source)
        self.assertIn('data.receivedAt?.elapsedNow()?.inWholeMilliseconds', source)
        self.assertIn('eventMessage("market.shops_stale")', source)
        self.assertIn('eventMessage("market.shops_previous_count", "count" to entry.publishedOffers.toString())', source)
        fence = (SHARED / 'MarketShopDirectoryReadFence.kt').read_text()
        self.assertIn('ageMillis in 0 until MARKET_SHOP_DIRECTORY_FRESH_MILLIS', fence)
        self.assertIn('result?.shops?.contains(displayed) == true', fence)
        self.assertNotIn('System.currentTimeMillis', source + fence)

    def test_existing_endpoint_is_authenticated_bounded_read_only_and_releases_gate(self):
        source = ROUTES.read_text()
        route = between(source, 'post("/shops/search")', 'get("/offers")')
        self.assertLess(source.index('authenticate("auth-jwt")'), source.index('post("/shops/search")'))
        for required in ('call.checkPrincipal()', 'private, no-store, max-age=0',
                         'shopDirectoryReadGate.acquire', 'finally { shopDirectoryReadGate.release(user) }',
                         'readOnly = true, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ',
                         'MarketShopDirectoryRepository(db).search(user, body)', 'SET LOCAL statement_timeout',
                         '"Retry-After"', 'eventMessage("market.shops_busy")'):
            self.assertIn(required, route)
        self.assertIn('shopDirectoryReadGate = MarketBasketReadGate(requestsPerMinute = 60)', source)
        self.assertEqual(1, route.count('call.marketResult('))
        self.assertNotIn('RealtimeServerBus', route)
        self.assertNotIn('after =', route)

    def test_new_messages_have_six_languages_and_matching_placeholders(self):
        # Reuse the repository's Kotlin-string scanner, including named optional languages.
        path = ROOT / 'scripts/repository/tests/test-kyrgyz-localization.py'
        spec = importlib.util.spec_from_file_location('ky_contracts', path)
        mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)
        source = (SHARED / 'messages/MarketplaceEventMessages.kt').read_text()
        for key in ('market.shops_busy', 'market.shops_stale', 'market.shops_previous_count',
                    'market.shops_invalid', 'market.shops_failed', 'market.shops_upgrade'):
            block = re.search(r'EventMessageTemplate\("' + re.escape(key) + r'".*?\),', source, re.S)
            self.assertIsNotNone(block, key)
            parsed = mod.EVENT.fullmatch(block[0][:-1])
            self.assertIsNotNone(parsed, key)
            raw_values = re.findall(mod.Q, block[0])
            self.assertEqual(7, len(raw_values), key) # Key and six translations; no children in these messages.
            values = [json.loads(v) for v in raw_values[1:]]
            placeholders = [set(re.findall(r'\{[A-Za-z][A-Za-z0-9_]*\}', v)) for v in values]
            self.assertTrue(all(v.strip() for v in values), key)
            self.assertTrue(all(p == placeholders[0] for p in placeholders), key)
            for lang in ('ky', 'tg', 'uz'):
                self.assertIn(lang + ' = ', block[0])


if __name__ == '__main__':
    unittest.main(verbosity=2)
