#!/usr/bin/env python3
"""Catalogue integration wiring checks; these do not replace compilation or PostgreSQL tests."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
SHARED = ROOT / 'shared/src/commonMain/kotlin/kz/aita'
SERVER = ROOT / 'server/src/main/kotlin/kz/aita/server/marketplace'
UI = ROOT / 'composeApp/src/commonMain/kotlin/kz/aita/BuyerMarketplaceScreen.kt'


def between(source, start, end):
    first = source.index(start)
    return source[first:source.index(end, first)]


class DiscoveryReadContracts(unittest.TestCase):
    def test_route_is_authenticated_private_read_only_and_repeatable_read(self):
        source = (SERVER / 'MarketplaceRoutes.kt').read_text()
        route = between(source, 'post("/discovery")', 'post("/shops/search")')
        self.assertLess(source.index('authenticate("auth-jwt")'), source.index('post("/discovery")'))
        for value in ('call.checkPrincipal()', 'private, no-store, max-age=0',
                      'receiveAita<MarketDiscoveryRequest>()',
                      'readOnly = true, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ'):
            self.assertIn(value, route)
        self.assertEqual(1, route.count('call.marketResult('))
        self.assertNotIn('RealtimeServerBus.publish', route)
        self.assertNotIn('FOR UPDATE', route)

    def test_read_budget_and_admission_are_bounded_and_released(self):
        source = (SERVER / 'MarketplaceRoutes.kt').read_text()
        route = between(source, 'post("/discovery")', 'post("/shops/search")')
        self.assertIn('discoveryReadGate = MarketBasketReadGate(requestsPerMinute = 60)', source)
        for value in ('SET LOCAL statement_timeout', 'withDiscoveryReadBudget { discover(user, body) }',
                      'finally { discoveryReadGate.release(user) }', 'HttpStatusCode.TooManyRequests', '"Retry-After"'):
            self.assertIn(value, route)
        self.assertLess(route.index('try {'), route.index('receiveAita<'))
        repository = (SERVER / 'MarketplaceRepository.kt').read_text()
        budget = between(repository, 'fun <T> withDiscoveryReadBudget', 'private fun basketQueryTimeoutSeconds')
        self.assertIn('withBasketReadBudget(work)', budget)
        self.assertIn('marketFail("market.discovery_busy", 503)', budget)
        self.assertIn('throw failure', budget)

    def test_one_server_clock_owns_header_visibility_counts_and_projection(self):
        source = (SERVER / 'MarketplaceRepository.kt').read_text()
        read = between(source, 'fun discover(', 'private fun discoveryCategories')
        self.assertEqual(1, read.count('System.currentTimeMillis()'))
        self.assertEqual(1, read.count('publicShop(it, now)'))
        self.assertLess(read.index('publicShop(it, now)'), read.index('val catalogue'))
        self.assertIn('mutableListOf<Any?>(now, now)', read)
        self.assertIn('project(user, rows, now,', read)
        self.assertIn('accountId = user.toString(), storefront = shop', read)
        self.assertNotRegex(read, r'\b(INSERT INTO|DELETE FROM|UPDATE\s+\w+)')
        public = between(source, 'fun publicShop(', '/** Estimates use')
        self.assertIn('now: Long = System.currentTimeMillis()', public)
        self.assertIn('store, now, now, map = ::storefrontRow', public)
        self.assertIn('MarketplacePublicVisibility.shopPredicate', public)

    def test_client_returns_only_an_owned_validated_snapshot(self):
        source = (SHARED / 'MarketplaceClient.kt').read_text()
        client = between(source, 'suspend fun loadMarketDiscovery(', '/** Read-only endpoint')
        self.assertIn('ResponseDataModel<MarketDiscoverySnapshot>', client)
        self.assertIn('readOwnedMarketDiscovery(scope, request, cachedCatalogue)', client)
        self.assertEqual(1, client.count('networkRequest<'))
        self.assertIn('expectedSessionGeneration = scope.generation', client)
        read = (SHARED / 'MarketDiscoveryRead.kt').read_text()
        self.assertEqual(1, read.count('val response = read(normalized)'))
        self.assertGreaterEqual(read.count('owner.isCurrent()'), 3)
        self.assertGreaterEqual(read.count('currentCoroutineContext().ensureActive()'), 3)
        for value in ('response.transportFailure', 'response.httpStatusCode != 200',
                      'result.accountId == null', 'validatedDiscovery(normalized, cachedCatalogue, owner.accountId)',
                      'market.shop_unavailable', 'market.discovery_scope_upgrade'):
            self.assertIn(value, read)
        self.assertNotIn('HttpMethod.Put', read)
        self.assertNotIn('loadMarketShop', read)

    def test_header_cannot_be_a_separate_or_inconsistent_response(self):
        source = (SHARED / 'MarketDiscoveryModels.kt').read_text()
        for value in ('val accountId: String? = null', 'val storefront: MarketStorefront? = null',
                      'accountId != expectedAccountId', 'shop?.isValidPublicMarketShop()',
                      'page.offers.any { it.storefront != shop }', 'if (shop != null) return null',
                      'page.savedMutation != null', 'page.unavailableSavedCleared',
                      'page.offers.groupBy { it.storefront.storeId }'):
            self.assertIn(value, source)
        ui = UI.read_text()
        self.assertNotIn('loadMarketShop(', ui)
        self.assertEqual(1, ui.count('loadMarketDiscovery('))
        self.assertIn('data.shop = checked.result.storefront', ui)

    def test_stale_window_and_queued_refresh_do_not_enable_estimate_actions(self):
        source = UI.read_text()
        for value in ('val startedRevision = MarketplaceSignals.revision.value',
                      'val refreshQueued = requests.tryReceive().isSuccess', 'request.limit != latestLimit',
                      'startedRevision != MarketplaceSignals.revision.value', 'if (superseded)',
                      'data.readRevision = startedRevision', 'fun catalogueIsCurrent(',
                      'val catalogueReady = catalogueIsCurrent(revision)',
                      'canUseEstimate = catalogueReady', 'else if (estimateIsCurrent()) shopping.add',
                      'catalogueReady && data.countsFresh && counts != null'):
            self.assertIn(value, source)
        # Retained callbacks inspect live draft/query values, not an old composition's boolean.
        for value in ('!data.loading && browse.query == query',
                      'browse.search.value == browse.appliedSearch.value',
                      'browse.shopId.value != null || browse.city.value == browse.appliedCity.value',
                      '!latestDialogOwnsReads && owner?.isCurrent() == true'):
            self.assertIn(value, source)
        self.assertIn('data.fresh = false; data.countsFresh = false; requests.trySend(Unit)', source)
        self.assertIn('fence.reconcile(checked.result.page, readRevision, query.savedOnly)', source)

    def test_new_feedback_is_present_in_every_supported_language(self):
        source = (SHARED / 'messages/MarketplaceEventMessages.kt').read_text()
        for key in ('market.discovery_scope_upgrade', 'market.discovery_busy'):
            self.assertEqual(1, source.count(f'EventMessageTemplate("{key}"'))
            entry = source.split(f'EventMessageTemplate("{key}"', 1)[1].split('EventMessageTemplate(', 1)[0]
            for language in ('ky', 'tg', 'uz'):
                self.assertRegex(entry, rf'{language}\s*=\s*"[^"\n]+"')
            self.assertGreaterEqual(len(re.findall(r'"(?:[^"\\]|\\.)*"', entry)), 6)

    def test_database_regressions_include_empty_withdrawn_owner_and_concurrent_header(self):
        tests = (ROOT / 'server/src/test/kotlin/kz/aita/server/marketplace/MarketplaceRepositoryDatabaseTest.kt').read_text()
        for name in ('discoveryEmptyShopWindowKeepsThePublicHeaderAndAuthenticatedOwner',
                     'discoveryWithdrawnShopIsNotAnEmptySuccessfulWindow',
                     'discoveryExpiredShopCannotBorrowItsParentsPublishedHeader',
                     'discoverySavedMarkersAreBoundToTheSameAuthenticatedAccountAsTheHeader',
                     'discoveryHeaderAndOffersDoNotMixAConcurrentPublicationChange'):
            self.assertIn(f'@Test fun {name}', tests)
        self.assertIn('connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ', tests)
        self.assertIn('connection.isReadOnly = true', tests)


if __name__ == '__main__':
    unittest.main(verbosity=2)
