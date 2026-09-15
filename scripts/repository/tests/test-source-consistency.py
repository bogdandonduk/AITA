"""Mixed-patch preflight regressions. Source checks, not substitute compiler tests."""
from pathlib import Path
import importlib.util
import tempfile
import unittest
import re

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location('aita_source_guard', ROOT / 'scripts/repository/rebuild-aita.py')
REPAIR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REPAIR)
SHARED = 'shared/src/commonMain/kotlin/kz/aita/'
UI = 'composeApp/src/commonMain/kotlin/kz/aita/'


class SourceConsistencyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve() / 'AITA'
        for relative in REPAIR.SOURCE_CONSISTENCY_FILES:
            self.put(relative, (ROOT / relative).read_text())

    def put(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def change(self, relative, old, new):
        path = self.root / relative
        text = path.read_text()
        self.assertIn(old, text)
        path.write_text(text.replace(old, new, 1))

    def errors(self):
        return '\n'.join(REPAIR.source_consistency_errors(self.root))

    def test_current_sources_are_consistent(self):
        self.assertEqual('', self.errors())

    def test_competing_shared_model_is_rejected(self):
        self.put(SHARED+'StockMarketplaceProfile.kt', 'package kz.aita\ndata class StockMarketplaceProfile(val legacy: String)')
        self.assertIn('StockMarketplaceProfile must have exactly one shared declaration (found 2)', self.errors())

    def test_duplicate_serialized_property_is_rejected(self):
        self.change(SHARED+'CommonMain.kt', 'val marketplaceProfile: StockMarketplaceProfile? = null,',
                    'val marketplaceProfile: StockMarketplaceProfile? = null,\nval marketplaceProfile: StockMarketplaceProfile? = null,')
        self.assertIn('GoodsItemDataModel.marketplaceProfile: expected exactly one parameter, found 2', self.errors())

    def test_missing_authentication_model_is_rejected(self):
        self.change(SHARED+'auth/AdvancedAuthenticationModels.kt', 'data class AitaAuthFlowDataModel', 'data class OldFlow')
        self.assertIn('AitaAuthFlowDataModel must have exactly one shared declaration (found 0)', self.errors())

    def test_duplicate_authentication_model_is_rejected(self):
        self.put(SHARED+'auth/Duplicate.kt', 'package kz.aita.auth\ndata class AitaAuthenticationSettingsDataModel(val id: String)')
        self.assertIn('AitaAuthenticationSettingsDataModel must have exactly one shared declaration (found 2)', self.errors())

    def test_lost_product_envelope_is_rejected(self):
        self.change(SHARED+'MarketplaceModels.kt', 'val product: MarketProductDetails', 'val legacyProduct: MarketProductDetails')
        self.assertIn('MarketListing.product', self.errors())

    def test_lost_branch_sharing_switch_is_rejected(self):
        self.change(SHARED+'MarketplaceModels.kt', 'val shareBranchAvailability: Boolean', 'val obsoleteFlag: Boolean')
        self.assertIn('MarketStorefront.shareBranchAvailability', self.errors())

    def test_lost_review_intent_is_rejected(self):
        self.change(SHARED+'MarketplaceModels.kt', 'val replaceProduct: Boolean', 'val oldReview: Boolean')
        self.assertIn('MarketListingUpdate.replaceProduct', self.errors())

    def test_obsolete_layout_variant_is_rejected(self):
        self.put(UI+'OldLoadingCaller.kt', 'package kz.aita\nfun old() = AitaLoadingLayout.Stock')
        self.assertIn('Obsolete loading-layout API', self.errors())

    def test_missing_widget_guard_is_rejected(self):
        self.change(UI+'CommonMainComposeWidgetsConfig.kt', 'shelfActionsEnabled: Boolean', 'oldGuard: Boolean')
        self.assertIn('GoodsItemInStockWidget.shelfActionsEnabled', self.errors())

    def test_missing_coupled_password_contract_is_rejected(self):
        self.change(UI+'CommonMainComposeWidgetsConfig.kt', 'passwordRevealed: Boolean?', 'oldVisibility: Boolean?')
        self.assertIn('aitaFormTextField.passwordRevealed', self.errors())

    def test_conflict_markers_are_rejected_without_touching_build_data(self):
        self.put(UI+'Conflict.kt', 'package kz.aita\n<<<<<<< ours\nval n=1\n=======\nval n=2\n>>>>>>> theirs\n')
        self.put('shared/build/keep', 'untouched')
        with self.assertRaises(REPAIR.RebuildError):
            REPAIR.validate_source_consistency(self.root)
        self.assertEqual('untouched', (self.root/'shared/build/keep').read_text())

    def test_named_numeric_query_bindings_cannot_regress(self):
        self.change('shared/src/commonMain/sqldelight/kz/aita/app_database.sq',
                    'CAST(:sliceOffset AS INTEGER)', '?')
        self.assertIn('sliceOffset', self.errors())

    def test_both_v112_scripts_cannot_coexist(self):
        self.put('server/src/main/resources/db/migration/V112__stock_marketplace_profiles_and_parent_publication.sql', 'SELECT 1;')
        self.assertIn('Duplicate migration version: V112__', self.errors())

    def test_equivalent_migration_versions_are_not_distinct(self):
        self.put('server/src/main/resources/db/migration/V0112_0__duplicate.sql', 'SELECT 1;')
        self.assertIn('Duplicate migration version', self.errors())

    def test_comments_literals_and_nested_comments_do_not_become_declarations(self):
        self.put(SHARED+'Comment.kt', (
            'package kz.aita\n'
            '// class MarketProductDetails\n'
            '/* /* nested */ class MarketProductDetails */\n'
            'val example = "class MarketProductDetails"\n'
            'val raw = """class MarketProductDetails\n'
            '<<<<<<< inside a raw literal\n'
            '"""\n'
        ))
        self.assertEqual('', self.errors())

    def test_preflight_runs_before_cleanup_or_gradle(self):
        source = (ROOT/'scripts/repository/rebuild-aita.py').read_text().split('def execute(', 1)[1]
        self.assertLess(source.index('validate_source_consistency(root)'), source.index('cleanup_plan('))
        self.assertLess(source.index('validate_source_consistency(root)'), source.index('run(wrapper_command('))

    def test_obsolete_ui_implementation_and_alternative_migration_are_removed(self):
        for relative in ('shared/src/commonMain/kotlin/kz/aita/StockMarketplaceProfile.kt',
                         UI+'StockMarketplaceProfileUi.kt',
                         'server/src/main/kotlin/kz/aita/server/marketplace/StockMarketplaceProjection.kt',
                         'server/src/main/resources/db/migration/V112__stock_marketplace_profiles_and_parent_publication.sql'):
            self.assertFalse((ROOT / relative).exists(), relative)

    def test_supplier_specific_layouts_are_kept_in_one_api(self):
        source=(ROOT/UI/'AitaLoadingMotion.kt').read_text()
        for name in ('SupplierCustomer','SupplierContract','SupplierOrder','SupplierDispatch'):
            self.assertIn('LoadingLayout.'+name+' ->',source)
        self.assertNotIn('AitaLoadingLayout',source)
        self.assertIn('if (scale <= 0f)',source)

    def test_loading_receivers_are_wired_to_the_public_helper(self):
        source=(ROOT/UI/'CommonMainComposeMenuA.kt').read_text()
        self.assertIn('loadingLayout: LoadingLayout? = null',source)
        self.assertIn('layout = loadingLayout',source)
        self.assertIn('AitaBusyIndicator(',source)

    def test_refresh_placeholders_preserve_loaded_conversations(self):
        source=(ROOT/UI/'SupportMessengerScreen.kt').read_text()
        self.assertIn('if(loading && tickets.isEmpty())',source)
        self.assertIn('if(loading && messages.isEmpty() && pending == null)',source)
        self.assertIn('remember(account) { mutableStateOf<SupportTeamMetrics?>',source)
        self.assertIn('if (!result.negative && result.payload != null) data=result.payload',source)


if __name__=='__main__':
    unittest.main()
