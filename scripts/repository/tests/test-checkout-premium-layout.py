#!/usr/bin/env python3
"""Checkout/resource/presentation integration contracts, not rendered UI tests."""
from pathlib import Path
import json
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
UI = ROOT / 'composeApp/src/commonMain/kotlin/kz/aita'
SHARED = ROOT / 'shared/src/commonMain/kotlin/kz/aita'


def body(file, name):
    text = (UI / file).read_text()
    start = text.index('fun ' + name + '(')
    end = text.find('\n@Composable', start + 1)
    return text[start:] if end < 0 else text[start:end]


class CheckoutPremiumLayoutTest(unittest.TestCase):
    def test_checkout_registers_visible_search_instead_of_competing_focus_timers(self):
        text = body('CommonMainComposeAuthTransaction.kt', 'AppConfiguration.TransactionSelectionScreen')
        self.assertIn('requester = searchTextFieldContent.focusRequester', text)
        self.assertIn('TransactionSearchFocusTarget(', text)
        self.assertIn('autoFocus = false', text)
        self.assertIn('forceRefocus = false', text)

    def test_focus_requests_are_bounded_and_wait_for_attached_nodes(self):
        text = (UI / 'TransactionBarcodeFocus.kt').read_text()
        for token in ('repeat(3)', 'withFrameNanos', 'LocalWindowInfo.current',
                      'latestDecision != decision', 'transactionSearchFocusRequester !== searchRequester'):
            self.assertIn(token, text)
        self.assertNotIn('while (true)', text)

    def test_editors_and_nested_modals_own_independent_guards(self):
        text = (UI / 'TransactionBarcodeFocus.kt').read_text()
        self.assertIn('transactionBarcodeModals[owner] = Unit', text)
        self.assertIn('transactionBarcodeModals.remove(owner)', text)
        self.assertIn('transactionBarcodeEditors.remove(owner)', text)
        text = body('CommonMainComposeWidgetsConfig.kt', 'AppConfiguration.genericTextField')
        self.assertIn('editorFocusGuard(it.isFocused && !captureTransactionBarcodeInput)', text)
        self.assertIn('!captureTransactionBarcodeInput && !isFocused && meta.focused', text)

    def test_payment_or_receipt_in_either_pane_disables_scanning(self):
        text = body('CommonMainComposeAuthTransaction.kt', 'AppConfiguration.TransactionScreen')
        self.assertIn('listOf(leftTransactionPaneModel, rightTransactionPaneModel)', text)
        self.assertIn('captureEnabled = visiblePaneModels.all', text)
        self.assertIn('it is NavigationScreenModel.Transaction.Selection || it is NavigationScreenModel.Transaction.Cart', text)

    def test_hidden_input_keeps_complete_buffer_and_scanner_terminator(self):
        text = body('CommonMainComposeAuthTransaction.kt', 'AppConfiguration.TransactionBarcodeHidInput')
        self.assertNotIn('removePrefix(buffer)', text)
        self.assertIn('transactionHidBuffer(raw)', text)
        self.assertIn('barcodeHandler(raw)', text)
        self.assertIn('barcodeHandler("$buffer\\n")', text)
        self.assertIn('enabled = captureEnabled', text)
        self.assertIn('activeTransactionBarcodeHandler === barcodeHandler', text)

    def test_sheet_caps_parent_before_filling_it(self):
        text = body('CommonMainComposeCartStockA.kt', 'AppConfiguration.AitaBottomSheet')
        self.assertIn('DialogProperties(usePlatformDefaultWidth = false)', text)
        self.assertIn('TransactionBarcodeModalGuard()', text)
        cap = text.index('.widthIn(max = 720.dp)')
        self.assertLess(cap, text.index('.fillMaxWidth()', cap))
        self.assertNotIn('else 0.78f', text)

    def test_fields_and_named_buttons_do_not_set_separate_width_caps(self):
        field = body('CommonMainComposeWidgetsConfig.kt', 'AppConfiguration.genericTextField')
        self.assertNotIn('modifier.aitaWidthCap', field)
        self.assertIn('modifier = modifier.fillMaxWidth()', field)
        for file in UI.glob('*.kt'):
            self.assertNotIn('fillMaxWidthIfTextPresent', file.read_text(), file.name)

    def test_delegated_supplier_action_groups_are_full_width_too(self):
        for name in ('SupplierBackorderWideFirstActionRow', 'SupplierBackorderCopyActionRow'):
            text = body('CommonMainComposeSupplierInsights.kt', 'AppConfiguration.' + name)
            self.assertIn('Column(', text)
            self.assertNotIn('Modifier.weight(1f)', text)
            self.assertIn('Modifier.fillMaxWidth()', text)

    def test_lifetime_card_has_no_refresh_or_billing_actions(self):
        text = (UI / 'StoreSubscriptionScreen.kt').read_text()
        self.assertIn('if (lifetime) LifetimeSubscriptionCard()', text)
        self.assertIn('else SubscriptionSurface {', text)
        card = (UI / 'LifetimeSubscriptionCard.kt').read_text()
        for forbidden in ('actionButton(', 'getStoreSubscription', 'purchaseStoreSubscription', 'requestStoreSubscription'):
            self.assertNotIn(forbidden, card)
        for required in ('Brush.linearGradient', 'cyan', '144L', 'tintColor = null'):
            self.assertIn(required, card)

    def test_lifetime_card_has_no_external_bottom_glow(self):
        # Response 031 explicitly removes the old under-car ellipse and reserved external space.
        text = (UI / 'LifetimeSubscriptionCard.kt').read_text()
        for token in ('drawOval(', 'Canvas(Modifier.matchParentSize())', '.padding(bottom = 36.dp)', 'rememberInfiniteTransition'):
            self.assertNotIn(token, text)
        for token in ('Brush.linearGradient', '144L', 'ice', 'cyan'):
            self.assertIn(token, text)

    def test_history_filters_server_and_old_cached_client_records(self):
        client = body('CommonMainComposeMenuB.kt', 'AppConfiguration.SecuritySessionHistoryCard')
        self.assertIn('securitySessionDisplayMetadata(event.metadata)', client)
        for forbidden in ('event.details', 'event.sessionId', 'event.userId', 'event.metadata.entries'):
            self.assertNotIn(forbidden, client)
        server = (ROOT / 'server/src/main/kotlin/kz/aita/server/Server.kt').read_text()
        dto = server.split('fun ResultRow.toSecuritySessionHistoryDataModel()', 1)[1].split('private const val', 1)[0]
        self.assertIn('details = emptyList()', dto)
        self.assertIn('securitySessionDisplayMetadata(this[SecuritySessionEvents.metadata])', dto)
        self.assertIn('sessionId = this[SecuritySessionEvents.sessionId]', dto)  # Operational links stay typed, not rendered.

    def test_diamond_assets_catalogue_android_and_offline_references_match(self):
        compose = ROOT / 'composeApp/src/commonMain/composeResources'
        server = ROOT / 'server/assets/drawable'
        self.assertEqual((server / 'drawables.json').read_bytes(),
                         (compose / 'files/assets/drawable/drawables.json').read_bytes())
        catalogue = json.loads((server / 'drawables.json').read_text())
        self.assertEqual(1, sum(item['id'] == 144 for item in catalogue['payload']))
        android = '{http://schemas.android.com/apk/res/android}'
        keep = (ROOT / 'composeApp/src/androidMain/res/raw/aita_lifetime_keep.xml').read_text()
        for variant in (0, 1):
            stem = f'144_{variant}'
            self.assertEqual((compose / f'drawable/{stem}.svg').read_bytes(), (server / f'svg/{stem}.svg').read_bytes())
            svg = ET.parse(compose / f'drawable/{stem}.svg').getroot()
            vector = ET.parse(ROOT / f'composeApp/src/androidMain/res/drawable/ic_aita_{stem}.xml').getroot()
            svg_paths = [(p.attrib['d'], ('#00000000' if p.attrib['fill'] == 'none' else p.attrib['fill'])) for p in svg if p.tag.endswith('path')]
            vector_paths = [(p.attrib[android+'pathData'], p.attrib[android+'fillColor']) for p in vector]
            self.assertEqual(svg_paths, vector_paths)
            self.assertEqual(svg[-1].attrib['stroke'], vector[-1].attrib[android+'strokeColor'])
            self.assertEqual(svg[-1].attrib['stroke-width'], vector[-1].attrib[android+'strokeWidth'])
            self.assertIn(f'ic_aita_{stem}', keep)
            self.assertIn(f'"{stem}" -> Res.drawable._{stem}', (UI / 'CommonMainComposeWidgetsConfig.kt').read_text())


if __name__ == '__main__':
    unittest.main(verbosity=2)
