#!/usr/bin/env python3
"""Source wiring checks complement the compiled policy/delivery and opt-in PostgreSQL tests."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
UI = ROOT / "composeApp/src/commonMain/kotlin/kz/aita"
SHARED = ROOT / "shared/src/commonMain/kotlin/kz/aita"
SERVER = ROOT / "server/src/main/kotlin/kz/aita/server/marketplace"


class ShoppingLineReviewWiring(unittest.TestCase):
    def test_displayed_snapshot_owns_rows_and_reviews(self):
        source = (UI / "BuyerShoppingListScreen.kt").read_text()
        for text in ("val displayed = state.snapshot", "val visibleRows = view.rows(displayed)", "items(visibleRows", "displayed.reviewShoppingLine(row.line)",
                     "removal = review", "state.compareLine(it)", "MarketShoppingRemoveDialog(review, state"):
            self.assertIn(text, source)
        self.assertNotIn("state.change(", source)
        card = source.split("private fun AppConfiguration.ShoppingLineCard", 1)[1]
        self.assertEqual(2, card.count("state.changeLine(it, line.units"))
        self.assertNotIn("confirmationRequired = true", card)
        self.assertIn("enabled = editable", card)
        self.assertNotIn("state.snapshot?.revision", card)

    def test_remove_review_is_frozen_scrollable_and_rechecked(self):
        source = (UI / "MarketShoppingRemoveDialog.kt").read_text()
        for text in ("val line = review.line", "review.matches(shopping.snapshot)", "Text(line.title",
                     "Text(line.shopName", "line.units", "verticalScroll(rememberScrollState())",
                     "enabled = matches && shopping.canChange", "shopping.changeLine(review, 0)",
                     "onDismissRequest = onDismiss", "confirmationRequired = false"):
            self.assertIn(text, source)
        self.assertNotIn("shopping.snapshot?.revision", source)
        self.assertNotIn("copy(expectedRevision", source)
        self.assertNotIn("MarketShoppingDelivery", source)

    def test_all_add_entry_points_are_add_only(self):
        for file, token in (("BuyerMarketplaceScreen.kt", "shopping.add("),
                            ("MarketComparisonDialog.kt", "shopping.add("),
                            ("MarketOfferDetailDialog.kt", "latestShopping.add(")):
            source = (UI / file).read_text()
            self.assertIn(token, source)
            self.assertNotRegex(source, r"(?:shopping|latestShopping)\.change\(")
        state = (UI / "MarketShoppingUiState.kt").read_text()
        self.assertIn("current.newShoppingLineCommand(", state)
        self.assertIn("review.command(snapshot, units, newClientSideUuidString())", state)
        self.assertNotIn("fun change(offerId:", state)
        self.assertIn("fun retry()", state)

    def test_durable_new_command_check_does_not_replace_pending_identity(self):
        source = (SHARED / "MarketShoppingClient.kt").read_text()
        check = "current.pending != pending && current.snapshot?.revision?.let { it != command.expectedRevision } == true"
        self.assertIn(check, source)
        self.assertLess(source.index(check), source.index("current.prepare(pending)"))
        self.assertLess(source.index("current.pending != null && current.pending != pending"), source.index(check))
        retry = source.split("suspend fun retry(scope:", 1)[1].split("suspend fun checkResult", 1)[0]
        self.assertIn("submit(scope, pending, journal.localRevision)", retry)
        self.assertIn("sendCancellation(scope, journal)", retry)
        self.assertNotIn("copy(expectedRevision", source)

    def test_existing_server_revision_and_recorded_outcome_guards_remain(self):
        source = (SERVER / "MarketShoppingRepository.kt").read_text()
        method = source.split("private fun applyCommand(", 1)[1]
        self.assertIn('currentRevision != request.expectedRevision -> error = "market.shopping_changed"', method)
        self.assertLess(method.index("if (recorded != null)"), method.index("currentRevision != request.expectedRevision"))
        self.assertIn("if (recorded.hash != hash)", method)
        self.assertIn("request.expectedRevision", method)
        self.assertIn("if (error == null)", method)

    def test_review_is_local_and_does_not_add_wire_or_hash_fields(self):
        source = (SHARED / "MarketShoppingLineReview.kt").read_text()
        self.assertNotIn("@Serializable", source)
        self.assertIn("MarketShoppingCommand(commandId, expectedRevision, line.offerId, units", source)
        self.assertIn("lines.any { it.line.offerId == offerId }", source)
        self.assertIn("current.revision == expectedRevision", source)
        self.assertIn("?.line == line", source)
        self.assertNotIn("checkedAtMillis", source)

    def test_feedback_has_all_six_languages_and_no_unfilled_placeholders(self):
        source = (SHARED / "messages/MarketplaceEventMessages.kt").read_text()
        for key in ("shopping_edit_changed", "shopping_already_listed", "shopping_remove_title",
                    "shopping_remove_review", "shopping_remove_wait"):
            marker = f'EventMessageTemplate("market.{key}"'
            self.assertEqual(1, source.count(marker))
            entry = source.split(marker, 1)[1].split("EventMessageTemplate(", 1)[0]
            for lang in ("ky", "tg", "uz"):
                self.assertRegex(entry, rf'{lang}\s*=\s*"[^"\n]+"')
            values = re.findall(r'"(?:[^"\\]|\\.)*"', entry)
            self.assertEqual(6, len(values))
            self.assertFalse(any(re.search(r'\{[a-zA-Z_]+\}', value) for value in values))


if __name__ == "__main__":
    unittest.main()
