#!/usr/bin/env python3
"""Wiring/translation checks only; not substitutes for Compose, Gradle or PostgreSQL tests."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
SHARED = ROOT / "shared/src/commonMain/kotlin/kz/aita"
SERVER = ROOT / "server/src/main/kotlin/kz/aita/server/marketplace"
UI = ROOT / "composeApp/src/commonMain/kotlin/kz/aita/MarketOfferDetailDialog.kt"


def between(source, start, end):
    first = source.index(start)
    return source[first:source.index(end, first)]


class OfferDetailWiring(unittest.TestCase):
    def test_new_route_is_authenticated_owned_private_and_read_only(self):
        source = (SERVER / "MarketplaceRoutes.kt").read_text()
        route = between(source, 'get("/offers/{offerId}/detail")', 'get("/offers/{offerId}")')
        self.assertLess(source.index('authenticate("auth-jwt")'), source.index('get("/offers/{offerId}/detail")'))
        for token in ('call.checkPrincipal()', 'private, no-store, max-age=0',
                      'readOnly = true, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ',
                      'offerDetail(user, call.parameters["offerId"].orEmpty())'):
            self.assertIn(token, route)
        self.assertEqual(1, route.count('call.marketResult('))
        self.assertNotIn('RealtimeServerBus', route)
        self.assertNotIn('after =', route)

    def test_budget_and_admission_release_on_all_exit_paths(self):
        source = (SERVER / "MarketplaceRoutes.kt").read_text()
        route = between(source, 'get("/offers/{offerId}/detail")', 'get("/offers/{offerId}")')
        self.assertIn('offerDetailReadGate = MarketBasketReadGate(requestsPerMinute = 60)', source)
        for token in ('finally { offerDetailReadGate.release(user) }', 'SET LOCAL statement_timeout',
                      'withOfferDetailReadBudget', 'HttpStatusCode.TooManyRequests', '"Retry-After"'):
            self.assertIn(token, route)
        budget = between((SERVER / 'MarketplaceRepository.kt').read_text(),
                         'fun <T> withOfferDetailReadBudget', 'private fun basketQueryTimeoutSeconds')
        self.assertIn('withBasketReadBudget(work)', budget)
        self.assertIn('marketFail("market.detail_busy", 503)', budget)
        self.assertIn('throw failure', budget)

    def test_projection_uses_the_existing_public_offer_not_private_seller_data(self):
        source = (SERVER / 'MarketplaceRepository.kt').read_text()
        method = between(source, 'fun offerDetail(', '/** A public shop page')
        self.assertIn('val current = offer(user, id)', method)
        self.assertIn('branchAvailability(current)', method)
        self.assertIn('MarketOfferDetailResult(user.toString(), current, availability.first, availability.second)', method)
        self.assertNotRegex(method, r'\b(INSERT INTO|DELETE FROM|UPDATE\s+\w+)')
        self.assertNotIn('dashboard', method)
        self.assertNotIn('ownsStore', method)
        legacy = between(source, 'fun offer(user:', 'fun offerDetail(')
        self.assertIn('offersByIds(user, listOf(marketUuid(id)))', legacy)

    def test_reader_is_cancellation_aware_validated_and_cannot_fall_back_or_write(self):
        source = (SHARED / 'MarketOfferDetail.kt').read_text()
        reader = between(source, 'suspend fun readOwnedMarketOfferDetail(', 'const val MARKET_OFFER_DETAIL_FRESH_MILLIS')
        self.assertEqual(1, reader.count('val response = read(id)'))
        self.assertGreaterEqual(reader.count('owner.isCurrent()'), 3)
        self.assertGreaterEqual(reader.count('currentCoroutineContext().ensureActive()'), 3)
        for token in ('response.transportFailure', 'response.httpStatusCode != 200', 'isValidOfferDetailResult',
                      'market.detail_upgrade', 'market.unavailable'):
            self.assertIn(token, reader)
        client = between((SHARED / 'MarketplaceClient.kt').read_text(), 'suspend fun loadMarketOfferDetail(', 'suspend fun loadMarketShop(')
        self.assertIn('readOwnedMarketOfferDetail(scope, id)', client)
        self.assertIn('expectedSessionGeneration = scope.generation', client)
        self.assertEqual(1, client.count('networkRequest<'))
        self.assertIn('market/offers/$normalizedId/detail', client)
        self.assertNotRegex(client, r'HttpMethod\.(Put|Post|Delete)')

    def test_ui_retires_pending_reads_and_rechecks_actions_at_click_time(self):
        source = UI.read_text()
        for token in ('MarketOfferDetailReadFence()', 'fence.invalidate(); fresh = false; requests.trySend(Unit)',
                      'fence.capture(offerId, MarketplaceSignals.revision.value)', 'loadMarketOfferDetail(owned, offerId)',
                      'displayed == offer && detailIsCurrent(MarketplaceSignals.revision.value)',
                      'receivedAt = TimeSource.Monotonic.markNow()', 'receivedAt?.elapsedNow()?.inWholeMilliseconds',
                      'val detailReady = detailIsCurrent(remote)', 'val latestShopping by rememberUpdatedState(shopping)',
                      'latestShopping.contains(offerId)', 'latestShopping.canChange', 'enabled = inList || (detailReady'):
            self.assertIn(token, source)
        self.assertEqual(3, source.count('displayedOfferIsCurrent(current)'))
        self.assertGreaterEqual(source.count('!fence.isCurrent(stamp, offerId, MarketplaceSignals.revision.value)'), 2)
        self.assertNotIn('loadMarketOffer(', source)
        self.assertNotIn('enabled = fresh', source)
        self.assertIn('catch (cancelled: CancellationException) { throw cancelled }', source)
        self.assertIn('DisposableEffect(requests) { onDispose { requests.close() } }', source)
        self.assertIn('delay(MARKET_OFFER_DETAIL_FRESH_MILLIS); requestRefresh()', source)

    def test_new_feedback_has_six_translations_and_no_unmatched_placeholders(self):
        source = (SHARED / 'messages/MarketplaceEventMessages.kt').read_text()
        quoted = r'"(?:[^"\\]|\\.)*"'
        for key in ('market.detail_failed', 'market.detail_upgrade', 'market.detail_busy', 'market.detail_stale'):
            self.assertEqual(1, source.count(f'EventMessageTemplate("{key}"'))
            entry = source.split(f'EventMessageTemplate("{key}"', 1)[1].split('EventMessageTemplate(', 1)[0]
            for lang in ('ky', 'tg', 'uz'):
                self.assertRegex(entry, rf'{lang}\s*=\s*"[^"\n]+"')
            values = re.findall(quoted, entry)
            self.assertEqual(6, len(values))
            self.assertTrue(all(not re.findall(r'\{[a-zA-Z_]+\}', value) for value in values))

    def test_db_regressions_cover_saved_isolation_withdrawal_and_snapshot(self):
        source = (ROOT / 'server/src/test/kotlin/kz/aita/server/marketplace/MarketplaceRepositoryDatabaseTest.kt').read_text()
        for method in ('detailEnvelopeBindsOnlyTheRequestingBuyersSavedMarker',
                       'withdrawnListingAndPrivateShopCannotBeReadThroughDetailEnvelope',
                       'detailWithNoSellableStockIsVisibleWithoutAnInventedPrice',
                       'detailRetainsOnePublicSnapshotAcrossConcurrentWithdrawal'):
            self.assertIn('@Test fun ' + method, source)
        self.assertIn('"AITA_MARKET_TEST_DB_URL"', source)
        self.assertIn('startsWith("aita_test_")', source)


if __name__ == '__main__':
    unittest.main()
