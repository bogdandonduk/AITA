#!/usr/bin/env python3
"""Comparison integration contracts; Kotlin policy tests exercise the actual decisions."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
UI = ROOT / 'composeApp/src/commonMain/kotlin/kz/aita'
SHARED = ROOT / 'shared/src/commonMain/kotlin/kz/aita'
SERVER = ROOT / 'server/src/main/kotlin/kz/aita/server/marketplace'


class ComparisonActionContracts(unittest.TestCase):
    def setUp(self):
        self.ui = (UI / 'MarketComparisonDialog.kt').read_text()

    def test_refresh_and_disposal_retire_the_read_before_enqueue(self):
        refresh = self.ui.split('fun queueRefresh() {', 1)[1].split('fun comparisonIsCurrent', 1)[0]
        self.assertLess(refresh.index('data.invalidate()'), refresh.index('requests.trySend(Unit)'))
        self.assertIn('review == null && !shoppingBlocked()', refresh)
        self.assertIn('onDispose { data.active = false; data.invalidate(); requests.close() }', self.ui)
        self.assertIn('5_000L - it.elapsedNow().inWholeMilliseconds', self.ui)
        self.assertNotIn('readMarketComparisonWindow(', self.ui)

    def test_both_success_and_error_wait_for_the_current_stamp(self):
        read = self.ui.split('val response = loadMarketComparisonWindow(owned, wanted)', 1)[1]
        self.assertLess(read.index('if (!stillOwnsRead())'), read.index('val value = response.payload'))
        self.assertIn('data.fence.isCurrent(stamp, wantedNow(), MarketplaceSignals.revision.value)', self.ui)
        self.assertIn('if (!response.transportFailure && response.httpStatusCode', self.ui)
        self.assertIn('finally { if (data.active) data.loading = false }', self.ui)

    def test_all_candidate_actions_recheck_page_and_presentation_at_click(self):
        self.assertEqual(3, self.ui.count('comparisonIsCurrent(page, presentation) && data.page?.matches?.contains(candidate) == true'))
        self.assertIn('data.page !== displayed || data.presentation !== presentation', self.ui)
        self.assertIn('shopping.add(offer.id, target.units, target.basis)', self.ui)
        self.assertIn('latestVisitShop(shop)', self.ui)
        card = self.ui.split('private fun AppConfiguration.MarketComparisonCard', 1)[1]
        self.assertNotIn('shopping.add(', card)
        self.assertIn('onClick = onAdd', card)

    def test_quantity_input_is_owned_and_apply_reads_the_current_draft(self):
        self.assertEqual(2, self.ui.count('parentOwnsValue = true'))
        self.assertIn('quantityDraft.toIntOrNull()?.let { applyQuantity(it) }', self.ui)
        self.assertNotIn('enteredUnits?.let { units = it }', self.ui)
        self.assertEqual(2, self.ui.count('units == target.units && !draftPending()'))
        self.assertIn('value !in 1..MARKET_SHOPPING_MAX_UNITS', self.ui)
        self.assertIn('draftPending() || data.page !== displayed', self.ui)
        self.assertIn('city = value; pages = 1; data.page = null; data.error = null; queueRefresh()', self.ui)

    def test_final_review_is_frozen_and_never_sends_from_enabled_alone(self):
        self.assertIn('private data class FrozenComparisonReview', self.ui)
        self.assertIn('review === frozen && submittedId == null && reviewIsCurrent(frozen)', self.ui)
        self.assertIn('shopping.replace(frozen.page.request.selection, frozen.candidate)', self.ui)
        self.assertIn('frozen.started.elapsedNow().inWholeMilliseconds', self.ui)
        self.assertIn('data.page !== frozen.page || review !== frozen', self.ui)
        self.assertIn('if (review != null || shoppingBlocked()) continue', self.ui)
        self.assertIn('submittedId == null && !shoppingBlocked()', self.ui)
        self.assertNotIn('MarketShoppingDelivery', self.ui)

    def test_expansion_and_empty_result_are_not_from_a_stale_window(self):
        self.assertIn('if (comparisonIsCurrent(page, presentation) && data.page?.moreCandidates == true && pages < MARKET_COMPARISON_MAX_PAGES)', self.ui)
        self.assertIn('pages++; queueRefresh()', self.ui)
        self.assertIn('comparisonReady && page != null && page.matches.isEmpty()', self.ui)
        self.assertIn('MARKET_COMPARISON_FRESH_MILLIS', (SHARED / 'MarketComparisonReadFence.kt').read_text())
        self.assertNotIn('@Serializable', (SHARED / 'MarketComparisonReadFence.kt').read_text())

    def test_new_feedback_is_complete_in_six_languages_and_uses_existing_art(self):
        messages = (SHARED / 'messages/MarketplaceEventMessages.kt').read_text()
        for suffix in ('action_stale', 'review_stale', 'quantity_pending'):
            key = 'market.comparison_' + suffix
            self.assertEqual(1, messages.count(f'EventMessageTemplate("{key}"'))
            entry = messages.split(f'EventMessageTemplate("{key}"', 1)[1].split('EventMessageTemplate(', 1)[0]
            for language in ('ky', 'tg', 'uz'):
                self.assertRegex(entry, language + r'\s*=\s*"[^"\n]+"')
            self.assertEqual(6, len(re.findall(r'"(?:[^"\\]|\\.)*"', entry)))
            self.assertIn(f'eventMessage("{key}")', self.ui)
        self.assertIn('marketIconPath(141)', self.ui)
        self.assertIn('marketIconFallback(141)', self.ui)

    def test_server_still_rechecks_revision_and_reviewed_price_before_replace(self):
        server = (SERVER / 'MarketShoppingRepository.kt').read_text()
        method = server.split('private fun applyCommand(', 1)[1]
        self.assertIn('currentRevision != request.expectedRevision', method)
        self.assertIn('request.reviewedSubtotalMinor', method)
        self.assertLess(method.index('if (recorded != null)'), method.index('currentRevision != request.expectedRevision'))
        shared = (SHARED / 'MarketComparisonWindow.kt').read_text()
        self.assertIn('val previous = shops.put(shop.storeId, shop)', shared)
        self.assertIn('if (previous != null && previous != shop) return false', shared)


if __name__ == '__main__':
    unittest.main()
