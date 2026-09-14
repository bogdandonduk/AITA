#!/usr/bin/env python3
"""Offline comparison route/UI wiring checks, not compilation or database execution."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
SHARED = ROOT / 'shared/src/commonMain/kotlin/kz/aita'
SERVER = ROOT / 'server/src/main/kotlin/kz/aita/server/marketplace'
UI = ROOT / 'composeApp/src/commonMain/kotlin/kz/aita/MarketComparisonDialog.kt'


def between(source, start, end):
    return source[source.index(start):source.index(end, source.index(start))]


class ComparisonWindowContracts(unittest.TestCase):
    def test_authenticated_window_is_one_read_only_repeatable_read_transaction(self):
        routes = (SERVER / 'MarketplaceRoutes.kt').read_text()
        route = between(routes, 'post("/compare/window")', 'post("/shopping-list/result")')
        self.assertIn('call.checkPrincipal()', route)
        self.assertIn('receiveAita<MarketComparisonWindowRequest>()', route)
        self.assertEqual(1, route.count('call.marketResult('))
        self.assertIn('readOnly = true, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ', route)
        self.assertIn('MarketShoppingRepository(db, this).comparisonWindow(user, body)', route)
        self.assertIn('private, no-store, max-age=0', route)
        self.assertNotIn('RealtimeServerBus.publish', route)
        self.assertNotIn('FOR UPDATE', route)

    def test_admission_and_statement_timeouts_are_released_on_every_exit(self):
        routes = (SERVER / 'MarketplaceRoutes.kt').read_text()
        route = between(routes, 'post("/compare/window")', 'post("/shopping-list/result")')
        self.assertIn('private val comparisonReadGate = MarketBasketReadGate()', routes)
        self.assertIn('comparisonReadGate.acquire(user, System.nanoTime() / 1_000_000L)', route)
        self.assertIn('HttpStatusCode.TooManyRequests', route)
        self.assertIn('"Retry-After"', route)
        self.assertIn('SET LOCAL statement_timeout', route)
        self.assertIn('finally { comparisonReadGate.release(user) }', route)
        self.assertLess(route.index('try {'), route.index('receiveAita<'))
        market = (SERVER / 'MarketplaceRepository.kt').read_text()
        budget = between(market, 'fun <T> withComparisonReadBudget', 'private fun basketQueryTimeoutSeconds')
        self.assertIn('withBasketReadBudget(work)', budget)
        self.assertIn('marketFail("market.comparison_window_busy", 503)', budget)
        self.assertIn('throw failure', budget)

    def test_the_server_scans_once_and_quotes_bounded_batches_at_one_instant(self):
        market = (SERVER / 'MarketplaceRepository.kt').read_text()
        read = between(market, 'fun compareWindow(', 'internal data class BasketCandidates')
        self.assertEqual(1, read.count('candidates("SELECT'))
        for part in ('publicPredicate', '"l.gtin=?"', '"l.id<>?"', '"l.store_id<>?"',
                     '"lower(f.city)=lower(?)"', 'args.add(input.city)', 'input.candidateLimit + 1',
                     'ORDER BY l.id LIMIT ?', 'candidates.take(input.candidateLimit)',
                     '.chunked(MARKET_SHOPPING_MAX_LINES)', 'quoteShopping(user, it, now)',
                     'quoteShopping(user, listOf(reference), now)', '.rankedComparison()',
                     'scanned.size', 'candidates.size > input.candidateLimit'):
            self.assertIn(part, read)
        self.assertNotIn('OFFSET', read)
        self.assertNotIn('System.currentTimeMillis()', read)
        self.assertNotRegex(read, r'\b(INSERT INTO|DELETE FROM|UPDATE\s+\w+)')

    def test_reference_resolution_keeps_account_revision_and_quantity_checks(self):
        shopping = (SERVER / 'MarketShoppingRepository.kt').read_text()
        read = between(shopping, 'fun comparisonWindow(', '/** A separate endpoint')
        self.assertIn('market.withComparisonReadBudget', read)
        self.assertEqual(1, read.count('System.currentTimeMillis()'))
        self.assertIn('comparisonReference(user, input.selection, now)', read)
        self.assertIn('revision(user) != selection.shoppingRevision', read)
        self.assertIn('lines(user).firstOrNull { it.offerId == sourceId.toString() }', read)
        self.assertIn('line.units != selection.units || line.basis != selection.basis', read)
        self.assertIn('market.offersByIds(user, listOf(sourceId), now, selection.units)', read)
        self.assertNotIn('FOR UPDATE', read)
        self.assertNotRegex(read, r'\b(INSERT INTO|DELETE FROM|UPDATE\s+\w+)')

    def test_client_keeps_session_identity_and_has_no_legacy_fallback(self):
        client = (SHARED / 'MarketplaceClient.kt').read_text()
        read = between(client, 'suspend fun loadMarketComparisonWindow', '/** A separate endpoint')
        self.assertIn('readOwnedMarketComparisonWindow(owner, request)', read)
        self.assertIn('networkRequest<MarketComparisonWindowResult, MarketComparisonWindowRequest>', read)
        self.assertIn('endpointUrl = "market/compare/window", body = normalized', read)
        self.assertIn('expectedSessionGeneration = owner.generation', read)
        owned = between((SHARED / 'MarketComparisonWindow.kt').read_text(), 'suspend fun readOwnedMarketComparisonWindow', '/** A query/window change')
        self.assertEqual(1, owned.count('read(normalized)'))
        self.assertEqual(2, owned.count('currentCoroutineContext().ensureActive()'))
        self.assertEqual(2, owned.count('if (!owner.isCurrent())'))
        self.assertIn('response.httpStatusCode != 200', owned)
        self.assertIn('isValidComparisonWindowResult(owner.accountId, normalized)', owned)
        self.assertNotIn('loadMarketComparison(', owned)
        self.assertNotIn('readMarketComparisonWindow(', owned)

    def test_ui_only_publishes_the_latest_whole_window_and_disables_stale_actions(self):
        ui = UI.read_text()
        self.assertIn('loadMarketComparisonWindow(owned, wanted)', ui)
        self.assertNotIn('readMarketComparisonWindow(', ui)
        self.assertNotIn('loadMarketComparison(', ui)
        self.assertIn('requests.tryReceive().isSuccess', ui)
        self.assertIn('!refreshQueued && value.matchesComparisonRead(latestWanted, startedRevision, MarketplaceSignals.revision.value)', ui)
        self.assertIn('else requests.trySend(Unit)', ui)
        self.assertIn('data.readRevision, remote) == true', ui)
        self.assertIn('enabled = comparisonReady && reviewUnchanged', ui)
        self.assertIn('MarketComparisonCard(candidate, page?.reference?.subtotalMinor, target, shopping, comparisonReady', ui)
        self.assertIn('page?.moreCandidates == true', ui)
        self.assertIn('pages < MARKET_COMPARISON_MAX_PAGES', ui)
        self.assertNotIn('page.nextId', ui)

    def test_new_feedback_is_localized_and_uses_existing_icons(self):
        ui = UI.read_text()
        messages = (SHARED / 'messages/MarketplaceEventMessages.kt').read_text()
        definitions = re.findall(r'EventMessageTemplate\("(market\.comparison_window_[^"]+)"(.*?)(?=\n\s*EventMessageTemplate|\n\n\))', messages, re.S)
        self.assertEqual(6, len(definitions))
        for key, body in definitions:
            for language in ('ky', 'tg', 'uz'):
                self.assertRegex(body, language + r'\s*=\s*"[^"\n]+"', key)
        self.assertIn('eventMessage("market.comparison_window_scope", "checked"', ui)
        self.assertIn('eventMessage("market.comparison_window_empty_more")', ui)
        self.assertIn('marketIconPath(141)', ui)
        self.assertIn('marketIconFallback(141)', ui)

    def test_previous_paged_api_and_replacement_command_are_not_removed(self):
        routes = (SERVER / 'MarketplaceRoutes.kt').read_text()
        self.assertIn('post("/compare")', routes)
        self.assertIn('.comparison(user, body)', routes)
        self.assertIn('suspend fun readMarketComparisonWindow(', (SHARED / 'MarketComparisonModels.kt').read_text())
        self.assertIn('fun MarketComparisonSelection.reviewedReplacement(', (SHARED / 'MarketComparisonModels.kt').read_text())
        self.assertIn('onClick = { submittedId = shopping.replace(target, selected) }', UI.read_text())
        self.assertIn('AITA_MARKET_TEST_DB_URL', (ROOT / 'server/src/test/kotlin/kz/aita/server/marketplace/MarketplaceRepositoryDatabaseTest.kt').read_text())


if __name__ == '__main__':
    unittest.main(verbosity=2)
