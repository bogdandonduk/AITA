#!/usr/bin/env python3
"""Offline source-wiring checks. Not a compiler, database, or device test."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
SHARED = ROOT / 'shared/src/commonMain/kotlin/kz/aita'
SERVER = ROOT / 'server/src/main/kotlin/kz/aita/server'

class BuyerSavedConsistencyContracts(unittest.TestCase):
    def test_cross_package_auth_locale_call_has_its_actual_import(self):
        auth = (SERVER / 'auth/AitaAdvancedAuthentication.kt').read_text()
        self.assertIn('import kz.aita.normalizeAuthEmailLocale\n', auth)
        self.assertIn('it[AuthOneTimeChallenges.locale] = normalizeAuthEmailLocale(locale)', auth)
        self.assertIn('fun normalizeAuthEmailLocale(locale: String?): String', (SHARED / 'AppLanguageRuntime.kt').read_text())

    def test_only_both_bookmark_write_routes_opt_into_serializable(self):
        routes = (SERVER / 'marketplace/MarketplaceRoutes.kt').read_text()
        for route, following in [('put("/saved")', 'post("/saved/clear-unavailable")'),
                                 ('post("/saved/clear-unavailable")', 'get("/seller")')]:
            fragment = routes[routes.index(route):routes.index(following, routes.index(route))]
            self.assertIn('call.checkPrincipal()', fragment)
            self.assertIn('transactionIsolation = Connection.TRANSACTION_SERIALIZABLE', fragment)
            self.assertIn('RealtimeServerBus.publish(entity="market/saved",userId=user.toString()', fragment)
        self.assertEqual(2, routes.count('call.marketResult(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE'))

    def test_transaction_boundary_keeps_whole_transaction_retry_and_post_commit_signal(self):
        routes = (SERVER / 'marketplace/MarketplaceRoutes.kt').read_text()
        wrapper = routes[routes.index('private suspend inline'):routes.index('private val basketReadGate')]
        self.assertIn('transactionIsolation: Int = Connection.TRANSACTION_REPEATABLE_READ', wrapper)
        self.assertIn('transactionIsolation = transactionIsolation, readOnly = readOnly', wrapper)
        self.assertIn('maxAttempts = 3', wrapper)
        self.assertIn('noinline after: suspend (T) -> Unit = {}', wrapper)
        self.assertLess(wrapper.index('newSuspendedTransaction'), wrapper.index('after(result)'))
        self.assertLess(wrapper.index('after(result)'), wrapper.index('genericResponse(HttpStatusCode.OK'))
        self.assertIn('throw cancelled', wrapper)
        self.assertIn('private, no-store, max-age=0', wrapper)

    def test_real_network_entry_points_use_owned_validation(self):
        source = (SHARED / 'MarketplaceClient.kt').read_text()
        fragment = source[source.index('suspend fun loadMarketSaved'):source.index('suspend fun loadMarketPublication')]
        for call in ['readOwnedMarketSaved(scope)', 'updateOwnedMarketSaved(scope, MarketSavedUpdate(id, saved), MarketplaceSignals::changed)',
                     'clearUnavailableOwnedMarketSaved(scope, MarketplaceSignals::changed)']:
            self.assertIn(call, fragment)
        self.assertEqual(3, fragment.count('expectedSessionGeneration = scope.generation'))

    def test_write_outcomes_invalidate_without_an_automatic_write_loop(self):
        source = (SHARED / 'MarketSavedClient.kt').read_text()
        self.assertIn('if (owner.isCurrent()) onChanged()', source)
        self.assertIn('finally {', source)
        self.assertIn('catch (cancelled: CancellationException) { throw cancelled }', source)
        self.assertNotIn('repeat(', source)
        self.assertNotIn('while (', source)
        self.assertIn('offers.any { it.id == request.offerId } == request.saved', source)
        self.assertIn('savedMutation == request && !unavailableSavedCleared', source)
        self.assertIn('it.unavailableSavedCleared && it.savedMutation == null', source)
        self.assertIn('it.unavailableSavedCount == 0', source)

    def test_mutation_only_acknowledgement_is_built_inside_the_committed_transaction(self):
        models = (SHARED / 'MarketplaceModels.kt').read_text()
        self.assertIn('val savedMutation: MarketSavedUpdate? = null', models)
        self.assertIn('val unavailableSavedCleared: Boolean = false', models)
        source = (SERVER / 'marketplace/MarketplaceRepository.kt').read_text()
        self.assertIn('saved(user).copy(savedMutation = MarketSavedUpdate(offer.toString(), request.saved))', source)
        self.assertIn('saved(user).copy(unavailableSavedCleared = true)', source)

    def test_new_feedback_has_all_six_translations(self):
        source = (SHARED / 'messages/MarketplaceEventMessages.kt').read_text()
        start = source.index('EventMessageTemplate("market.saved_unconfirmed"')
        fragment = source[start:source.index('EventMessageTemplate(', start + 1)]
        self.assertEqual(7, len(re.findall(r'"(?:[^"\\]|\\.)*"', fragment)))
        for language in ('ky', 'tg', 'uz'):
            self.assertIn(language + ' = ', fragment)

    def test_focused_ui_invalidates_counts_when_a_write_begins(self):
        source = (ROOT / 'composeApp/src/commonMain/kotlin/kz/aita/BuyerMarketplaceScreen.kt').read_text()
        self.assertIn('savingId = offer.id; saveFailure = null; data.countsFresh = false', source)
        self.assertIn('savingId = "clear"; saveFailure = null; data.countsFresh = false', source)
        self.assertIn('fence.acknowledgeDiscoverySaved', source)
        self.assertIn('market.saved_unconfirmed', source)
        self.assertIn('data.countsFresh = !savedChangedDuringRead && savingId == null', source)
        self.assertIn('if (savedChangedDuringRead) requests.trySend(Unit)', source)

    def test_repository_enforces_bounded_account_owned_saved_writes(self):
        source = (SERVER / 'marketplace/MarketplaceRepository.kt').read_text()
        fragment = source[source.index('fun updateSaved'):source.index('private fun items')]
        self.assertIn('>= MARKET_SAVED_MAX_OFFERS', fragment)
        self.assertIn('DELETE FROM buyer_saved_offers WHERE user_id=? AND listing_id=?', fragment)
        self.assertIn('DELETE FROM buyer_saved_offers WHERE user_id=? AND listing_id NOT IN', fragment)
        self.assertIn('isEmpty()) marketFail("market.shopping_denied", 403)', fragment)

if __name__ == '__main__':
    unittest.main(verbosity=2)
