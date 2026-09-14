#!/usr/bin/env python3
"""Source/resource wiring checks, not a Compose or database integration test."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
COMMON = ROOT / 'shared/src/commonMain/kotlin/kz/aita'
UI = ROOT / 'composeApp/src/commonMain/kotlin/kz/aita/MarketShoppingActivityPanel.kt'
ROUTES = ROOT / 'server/src/main/kotlin/kz/aita/server/marketplace/MarketplaceRoutes.kt'


class ShoppingActivitySafetyWiring(unittest.TestCase):
    def test_every_history_reader_checks_http_transport_cancellation_and_owner(self):
        for name, count in [('MarketShoppingActivityClient.kt', 2), ('MarketShoppingActivitySearchClient.kt', 1)]:
            text = (COMMON / name).read_text()
            self.assertEqual(count, text.count('response.httpStatusCode != 200'))
            self.assertEqual(count, text.count('if (response.transportFailure)'))
            self.assertEqual(count * 3, text.count('currentCoroutineContext().ensureActive()'))
            self.assertEqual(count * 3, text.count('owner.isCurrent()'))
            self.assertNotIn('catch', text)
            self.assertNotIn('acknowledge(', text)

    def test_all_activity_routes_share_admission_without_touching_recovery(self):
        text = ROUTES.read_text()
        activity = text.split('get("/shopping-list/activity/{commandId}")', 1)[1].split('post("/shopping-list/plan")', 1)[0]
        self.assertEqual(3, activity.count('shoppingActivityReadGate.acquire(user,'))
        self.assertEqual(3, activity.count('finally { shoppingActivityReadGate.release(user) }'))
        self.assertEqual(3, activity.count('private, no-store, max-age=0'))
        self.assertEqual(3, activity.count('call.marketResult(readOnly = true)'))
        self.assertEqual(3, activity.count('SET LOCAL statement_timeout'))
        self.assertEqual(3, activity.count('"Retry-After"'))
        recovery = text.split('post("/shopping-list/result")', 1)[1].split('get("/shopping-list/activity/{commandId}")', 1)[0]
        self.assertNotIn('shoppingActivityReadGate', recovery)
        self.assertIn('private val shoppingActivityReadGate = MarketBasketReadGate(requestsPerMinute = 60)', text)

    def test_filter_changes_retire_before_recomposition_including_aba(self):
        text = UI.read_text()
        self.assertIn('reads.invalidate(); selection = Any(); selectedRequest = value', text)
        self.assertIn('remember(account, generation, wanted, selection)', text)
        self.assertIn('navigation.selection === selection', text)
        refresh = text.split('fun refreshPage()', 1)[1].split('fun canUsePage', 1)[0]
        self.assertLess(refresh.index('navigation.reads.invalidate()'), refresh.index('requests.trySend(Unit)'))
        self.assertLess(refresh.index('data.retire()'), refresh.index('requests.trySend(Unit)'))

    def test_late_success_and_error_both_respect_live_page_stamp(self):
        text = UI.read_text()
        self.assertIn('if (!stillOwnsRead()) continue', text)
        self.assertIn('if (stillOwnsRead()) data.error', text)
        self.assertIn('MarketplaceSignals.revision.value', text)
        self.assertIn('data.stamp = stamp', text)
        self.assertIn('if (!response.transportFailure && response.httpStatusCode in setOf(401, 403))', text)

    def test_view_result_and_both_cursors_recheck_at_click(self):
        text = UI.read_text()
        self.assertIn('data.page === displayed', text)
        self.assertIn('page?.entries?.contains(entry) == true', text)
        self.assertEqual(2, text.count('if (canUsePage(page)) navigation.request = next else stalePage()'))
        self.assertIn('data.loading || data.error != null || opened != null', text)
        self.assertNotIn('transientNotice(', text)

    def test_immutable_history_is_not_treated_as_a_price_or_pending_ack(self):
        text = (COMMON / 'MarketShoppingActivityReadFence.kt').read_text()
        self.assertIn('stamp.request.boundary != null || stamp.signal == signal', text)
        self.assertNotIn('TimeSource', text)
        self.assertNotIn('@Serializable', text)
        for text in (UI.read_text(), (COMMON / 'MarketShoppingActivityClient.kt').read_text(),
                     (COMMON / 'MarketShoppingActivitySearchClient.kt').read_text()):
            for call in ('acknowledge(', '.applyBasket(', '.applyShopping(', '.requestCancellation('):
                self.assertNotIn(call, text)

    def test_retry_retires_the_previous_detail_before_recomposition(self):
        text = UI.read_text()
        self.assertIn('remember(account, generation, entry, attempt)', text)
        self.assertIn('detailLifetime.active = false; loading = true; attempt++', text)
        self.assertIn('detailLifetime.active && owner?.isCurrent() == true && !loading', text)
        self.assertIn('catch (cancelled: CancellationException) { throw cancelled }', text)

    def test_touched_feedback_keeps_all_six_languages_and_unique_keys(self):
        text = (COMMON / 'messages/MarketplaceEventMessages.kt').read_text()
        for key in ('activity_busy', 'activity_page_changed', 'activity_search_invalid', 'activity_search_upgrade',
                    'shopping_activity_invalid', 'shopping_activity_failed', 'shopping_activity_upgrade', 'shopping_activity_missing'):
            marker = f'EventMessageTemplate("market.{key}"'
            self.assertEqual(1, text.count(marker))
            line = text.split(marker, 1)[1].split('\n', 1)[0]
            for language in ('ky', 'tg', 'uz'):
                self.assertRegex(line, rf'{language}\s*=\s*"[^"\n]+"')
            self.assertEqual(6, len(re.findall(r'"(?:[^"\\]|\\.)*"', line)))


if __name__ == '__main__':
    unittest.main()
