#!/usr/bin/env python3
"""Basket UI/route wiring. These source checks complement compiled policy tests, not a build."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
SHARED = ROOT / 'shared/src/commonMain/kotlin/kz/aita'
UI = ROOT / 'composeApp/src/commonMain/kotlin/kz/aita'
SERVER = ROOT / 'server/src/main/kotlin/kz/aita/server/marketplace'


class BasketPlanFreshnessWiring(unittest.TestCase):
    def test_reader_distinguishes_transport_status_and_cancellation(self):
        source = (SHARED / 'MarketBasketPlanClient.kt').read_text()
        self.assertGreaterEqual(source.count('currentCoroutineContext().ensureActive()'), 3)
        self.assertLess(source.index('if (response.transportFailure)'), source.index('if (response.httpStatusCode == 404)'))
        self.assertIn('response.httpStatusCode != 200', source)
        self.assertIn('payload = null, negative = true', source)
        self.assertIn('!result.isValidBasketResult(owner.accountId, normalized)', source)
        self.assertNotIn('catch (', source)

    def test_refresh_retires_actions_before_enqueue_and_disposal(self):
        source = (UI / 'MarketBasketPlanDialog.kt').read_text()
        queue = source.split('fun queueRefresh()', 1)[1].split('fun planIsCurrent', 1)[0]
        self.assertLess(queue.index('data.invalidate()'), queue.index('requests.trySend(Unit)'))
        self.assertIn('data.active = false; data.invalidate(); requests.close()', source)
        self.assertIn('MarketBasketPlanReadFence()', source)
        self.assertIn('data.loadedStamp = stamp', source)
        self.assertNotIn('loadedSignal', source)

    def test_old_success_and_error_cannot_own_a_new_request(self):
        source = (UI / 'MarketBasketPlanDialog.kt').read_text()
        self.assertIn('wantedNow() != wanted', source)
        self.assertIn('data.fence.isCurrent(stamp, wantedNow(), MarketplaceSignals.revision.value)', source)
        self.assertEqual(2, source.count('if (!stillOwnsRead())'))
        self.assertIn('if (!data.active || !owned.isCurrent()) break', source)
        self.assertIn('catch (cancelled: CancellationException) { throw cancelled }', source)

    def test_action_callbacks_check_presentation_and_current_list(self):
        source = (UI / 'MarketBasketPlanDialog.kt').read_text()
        self.assertIn('data.result !== displayed || data.presentation !== presentation', source)
        self.assertEqual(3, source.count('planIsCurrent(result, presentation)'))
        self.assertIn('data.select(); currency = it', source)
        self.assertIn('data.select(); kind = it', source)
        self.assertIn('shopping.compareLine(lineReview)', source)
        self.assertIn('MarketBasketRequest(0, cityDraft).normalizedBasketRequest()?.city?.let', source)

    def test_confirmation_uses_original_read_mark_and_frozen_identity(self):
        source = (UI / 'MarketBasketPlanDialog.kt').read_text()
        self.assertIn('data.startedAt = started', source)
        self.assertIn('FrozenBasketReview(result, command, stamp, started)', source)
        self.assertIn('review !== frozen || submittedId != null || !reviewIsCurrent(frozen)', source)
        self.assertIn('shopping.applyBasket(frozen.command)', source)
        self.assertNotIn('lastRead.value ?: TimeSource.Monotonic.markNow()', source)
        review = (UI / 'MarketBasketReviewDialog.kt').read_text()
        self.assertIn('onConfirm: () -> Boolean', review)
        self.assertIn('|| !onConfirm()', review)
        self.assertIn('if (ownPending) MarketShoppingRecoveryControls(shopping)', review)
        self.assertIn('remember(command.commandId)', review)

    def test_guard_is_local_not_a_command_wire_or_wall_clock_change(self):
        source = (SHARED / 'MarketBasketPlanReadFence.kt').read_text()
        self.assertNotIn('@Serializable', source)
        self.assertNotIn('System.currentTimeMillis', source)
        self.assertIn('result.snapshot.hasSameBasketIntent(current)', source)
        self.assertIn('MARKET_BASKET_REVIEW_MAX_AGE_MILLIS', source)
        self.assertIn('basket.kind, command.commandId) == command', source)
        self.assertIn('current.lines.map { it.line } == lines.map { it.line }', source)

    def test_server_read_and_mutation_guards_remain_intact(self):
        routes = (SERVER / 'MarketplaceRoutes.kt').read_text()
        plan = routes.split('post("/shopping-list/plan")', 1)[1].split('put("/shopping-list/apply-plan")', 1)[0]
        self.assertIn('call.marketResult(readOnly = true)', plan)
        self.assertIn('transactionIsolation: Int = Connection.TRANSACTION_REPEATABLE_READ', routes)
        self.assertIn('SET LOCAL statement_timeout', plan)
        self.assertIn('finally { basketReadGate.release(user) }', plan)
        repository = (SERVER / 'MarketShoppingRepository.kt').read_text()
        self.assertIn('withBasketReadBudget', repository)
        self.assertIn('isWithinBasketReviewTime', repository)
        self.assertIn('basketQuotesError', repository)
        self.assertIn('if (recorded != null)', repository)

    def test_new_and_touched_feedback_has_all_six_languages(self):
        source = (SHARED / 'messages/MarketplaceEventMessages.kt').read_text()
        for key in ('basket_plan_stale', 'basket_review_stale', 'basket_invalid', 'basket_list_changed',
                    'basket_refresh', 'basket_upgrade', 'basket_busy'):
            marker = f'EventMessageTemplate("market.{key}"'
            self.assertEqual(1, source.count(marker))
            entry = source.split(marker, 1)[1].split('EventMessageTemplate(', 1)[0]
            for language in ('ky', 'tg', 'uz'):
                self.assertRegex(entry, rf'{language}\s*=\s*"[^"\n]+"')
            self.assertEqual(6, len(re.findall(r'"(?:[^"\\]|\\.)*"', entry)))


if __name__ == '__main__':
    unittest.main()
