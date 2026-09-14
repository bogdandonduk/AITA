#!/usr/bin/env python3
"""Quantity editor wiring and six-language resource checks; not rendered UI or Gradle tests."""
from pathlib import Path
import json
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
UI = ROOT / 'composeApp/src/commonMain/kotlin/kz/aita'
SHARED = ROOT / 'shared/src/commonMain/kotlin/kz/aita'


class ShoppingQuantityEditorWiring(unittest.TestCase):
    def test_editor_opens_from_displayed_line_with_its_original_revision(self):
        source = (UI / 'BuyerShoppingListScreen.kt').read_text()
        self.assertIn('displayed.reviewShoppingLine(row.line)', source)
        self.assertIn('state.canChange && review?.matches(state.snapshot) == true) quantityEdit = review', source)
        self.assertIn('MarketShoppingQuantityDialog(review, state, onDismiss = { quantityEdit = null })', source)
        self.assertIn('quantityEdit by remember(account, generation)', source)
        self.assertEqual(2, source.count('state.changeLine(it, line.units'))
        self.assertIn('onClick = onEditQuantity', source)
        self.assertIn('fun listDialogIsOpen(): Boolean', source)
        self.assertIn('if (!listDialogIsOpen() && state.canChange', source)
        self.assertIn('MarketShoppingRemoveDialog(review, state', source)

    def test_save_and_ime_share_one_live_rechecked_controller(self):
        source = (UI / 'MarketShoppingQuantityDialog.kt').read_text()
        self.assertIn('shopping::changeLine', source)
        self.assertIn('{ shopping.snapshot }', source)
        self.assertIn('{ shopping.canChange }', source)
        self.assertIn('enabled = editor.canSubmit', source)
        self.assertEqual(2, source.count('{ editor.submit() }'))
        controller = (UI / 'MarketShoppingQuantityUiState.kt').read_text()
        body = controller.split('fun submit(): Boolean', 1)[1].split('fun dismiss()', 1)[0]
        self.assertLess(body.index('!active || !canChange()'), body.index('changeLine(review, units)'))
        self.assertLess(body.index('review.quantityEditUnits(currentSnapshot(), draft)'), body.index('changeLine(review, units)'))
        self.assertLess(body.index('changeLine(review, units)'), body.index('dismiss()'))
        self.assertNotIn('MarketShoppingDelivery', controller)
        self.assertNotIn('copy(expectedRevision', controller)

    def test_dismissal_and_disposal_retire_callbacks_without_sending(self):
        controller = (UI / 'MarketShoppingQuantityUiState.kt').read_text()
        close = controller.split('fun dismiss()', 1)[1]
        self.assertNotIn('changeLine(', close)
        self.assertLess(close.index('active = false'), close.index('onDismiss()'))
        self.assertIn('fun dispose() { active = false }', close)
        source = (UI / 'MarketShoppingQuantityDialog.kt').read_text()
        self.assertIn('onDismissRequest = editor::dismiss', source)
        self.assertIn('onDispose { editor.dispose() }', source)

    def test_invalid_input_is_not_silently_transformed_or_persisted(self):
        controller = (UI / 'MarketShoppingQuantityUiState.kt').read_text()
        self.assertIn('if (active) draft = value', controller)
        source = (UI / 'MarketShoppingQuantityDialog.kt').read_text()
        self.assertIn('KeyboardType.Number', source)
        self.assertIn('ImeAction.Done', source)
        self.assertIn('parentOwnsValue = true', source)
        self.assertIn('sensitive = true', source)
        self.assertNotIn('onTransformValue', source)
        parser = (SHARED / 'MarketShoppingQuantityEdit.kt').read_text()
        self.assertIn('draft.length !in 1..16', parser)
        self.assertIn('text.toIntOrNull()?.takeIf { it in 1..MARKET_SHOPPING_MAX_UNITS }', parser)
        self.assertNotIn('coerceIn', parser)
        self.assertNotIn('.filter', parser)

    def test_short_window_keyboard_and_normal_button_layout_are_supported(self):
        source = (UI / 'MarketShoppingQuantityDialog.kt').read_text()
        for token in ('.imePadding()', '.heightIn(max = 620.dp)', '.verticalScroll(rememberScrollState())',
                      'DialogProperties(usePlatformDefaultWidth = false)', 'confirmationRequired = false'):
            self.assertIn(token, source)
        self.assertNotIn('fillMaxWidthIfTextPresent = false', source)
        self.assertNotIn('subtotalMinor', source)
        self.assertNotIn('priceMinor', source)

    def test_all_editor_messages_cover_six_languages_with_matching_placeholders(self):
        source = (SHARED / 'messages/MarketplaceEventMessages.kt').read_text()
        keys = ('edit', 'label', 'current', 'proposed', 'invalid', 'help', 'save')
        for key in keys:
            marker = 'EventMessageTemplate("market.shopping_quantity_' + key + '"'
            self.assertEqual(1, source.count(marker))
            block = source.split(marker, 1)[1].split('EventMessageTemplate(', 1)[0]
            texts = [json.loads(x) for x in re.findall(r'"(?:[^"\\]|\\.)*"', block)]
            self.assertEqual(6, len(texts))
            expected = set(re.findall(r'\{([a-zA-Z][a-zA-Z0-9_]*)\}', texts[0]))
            for text in texts:
                self.assertTrue(text.strip())
                self.assertEqual(expected, set(re.findall(r'\{([a-zA-Z][a-zA-Z0-9_]*)\}', text)))

    def test_frozen_review_and_durable_sender_still_own_the_command(self):
        parser = (SHARED / 'MarketShoppingQuantityEdit.kt').read_text()
        self.assertIn('it != line.units && matches(current)', parser)
        state = (UI / 'MarketShoppingUiState.kt').read_text()
        self.assertIn('review.command(snapshot, units, newClientSideUuidString())', state)
        self.assertIn('if (!canChange)', state)
        sender = (SHARED / 'MarketShoppingClient.kt').read_text()
        self.assertIn('current.pending != pending && current.snapshot?.revision?.let { it != command.expectedRevision } == true', sender)
        self.assertIn('current.prepare(pending)', sender)
        self.assertIn('submit(scope, pending, prepared.localRevision)', sender)

    def test_existing_backend_rechecks_basis_revision_and_returns_fresh_quote(self):
        source = (ROOT / 'server/src/main/kotlin/kz/aita/server/marketplace/MarketShoppingRepository.kt').read_text()
        method = source.split('private fun applyCommand(', 1)[1]
        self.assertIn('currentRevision != request.expectedRevision', method)
        self.assertIn('current.basis != request.basis', method)
        self.assertIn('UPDATE buyer_shopping_lines SET units=?,updated_at_millis=?', method)
        self.assertIn('snapshot = snapshot(user)', method)
        self.assertLess(method.index('if (recorded != null)'), method.index('currentRevision != request.expectedRevision'))


if __name__ == '__main__':
    unittest.main()
