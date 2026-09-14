#!/usr/bin/env python3
"""Local resource/wiring regression checks; not a Kotlin compilation or device UI test."""
from pathlib import Path
import json
import re
import struct
import unittest

ROOT = Path(__file__).resolve().parents[3]
SHARED = ROOT / 'shared/src/commonMain/kotlin/kz/aita'
COMPOSE = ROOT / 'composeApp/src/commonMain/kotlin/kz/aita'
SERVER = ROOT / 'server/src/main/kotlin/kz/aita/server'
Q = r'"(?:[^"\\]|\\.)*"'

class LanguageRuntimeContractsTest(unittest.TestCase):
    def test_all_six_languages_are_unique_in_configuration_and_resources(self):
        config = json.loads((ROOT/'server/config/app/global.json').read_text())['payload']
        self.assertEqual(['en','ru','kk','tg','ky','uz'], [v['language'] for v in config['languages']])
        for p in [ROOT/'server/assets/values/strings.json', ROOT/'composeApp/src/commonMain/composeResources/files/assets/values/strings.json']:
            rows=json.loads(p.read_text())['payload']
            self.assertEqual(1920,len(rows))
            for row in rows:
                values={v['language']:v['value'] for v in row['values']}
                self.assertEqual(len(values),len(row['values']))
                self.assertTrue(all(values[lang].strip() for lang in ['tg','ky','uz']))

    def test_shared_tajik_and_uzbek_tables_match_every_json_entry(self):
        source=(SHARED/'TajikUzbekStringResources.kt').read_text()
        tables={}
        for language,name in [('tg','Tajik'),('uz','Uzbek')]:
            chunks=re.findall(r'private fun MutableMap<Long, String>\.put'+name+r'StringsPart\d+\(\) \{(.*?)\n\}',source,re.S)
            rows=[(int(m[1]),json.loads(m[2])) for chunk in chunks for m in re.finditer(r'put\((\d+)L,\s*('+Q+r')\)',chunk)]
            self.assertEqual(2491,len(rows));self.assertEqual(len(rows),len(dict(rows)));tables[language]=dict(rows)
        for row in json.loads((ROOT/'server/assets/values/strings.json').read_text())['payload']:
            values={v['language']:v['value'] for v in row['values']}
            for lang,table in tables.items(): self.assertEqual(values[lang],table[row['id']],(lang,row['id']))

    def test_both_uzbek_flags_and_picker_resource_mapping_exist(self):
        client=ROOT/'composeApp/src/commonMain/composeResources/drawable/flag_uz.png'
        server=ROOT/'server/assets/drawable/png/flag_uz.png'
        self.assertEqual(client.read_bytes(),server.read_bytes())
        self.assertEqual((512,512),struct.unpack('>II',client.read_bytes()[16:24]))
        self.assertIn('"uz" -> Res.drawable.flag_uz',(COMPOSE/'CommonMainComposeWidgetsConfig.kt').read_text())
        self.assertIn('png/flag_uz.png',(SHARED/'AppLanguageCatalogue.kt').read_text())

    def test_ui_has_separate_raw_and_resolved_language(self):
        widgets=(COMPOSE/'CommonMainComposeWidgetsConfig.kt').read_text()
        self.assertIn('appLanguagePreference: String get() = appearance.appLanguage',widgets)
        self.assertIn('effectiveAppLanguage(appearance.appLanguage, systemLocaleLanguage)',widgets)
        self.assertIn('Lifecycle.Event.ON_RESUME',widgets)
        for file in ['CommonMainComposeMenuB.kt','CommonMainComposeAuthTransaction.kt']:
            source=(COMPOSE/file).read_text()
            self.assertIn('appLanguagePreference == "system"',source)
            self.assertIn('withBundledAppLanguages()',source)
        profile=(COMPOSE/'CommonMainComposeMenuA.kt').read_text()
        self.assertIn('appLanguage = stateValues.appLanguagePreference,',profile)
        self.assertNotIn('appLanguage = stateValues.appLanguage,',profile)

    def test_authentication_requests_send_resolved_client_locale(self):
        source=(SHARED/'auth/AdvancedAuthenticationClient.kt').read_text()
        self.assertEqual(12,source.count('request.copy(locale = authRequestLocale(request.locale))'))
        self.assertIn('HttpHeaders.AcceptLanguage to effectiveAppLanguage(appLanguageState.value)',(SHARED/'CommonMain.kt').read_text())
        self.assertIn('it[AuthOneTimeChallenges.locale] = normalizeAuthEmailLocale(locale)',(SERVER/'auth/AitaAdvancedAuthentication.kt').read_text())
        auth=(SERVER/'auth/AitaAdvancedAuthentication.kt').read_text()
        self.assertIn('import kz.aita.normalizeAuthEmailLocale', auth)
        self.assertIn('fun normalizeAuthEmailLocale(locale: String?): String', (SHARED/'AppLanguageRuntime.kt').read_text())

    def test_coerced_preferences_are_not_acknowledged(self):
        source=(SHARED/'ApplicationPreferences.kt').read_text()
        validation=source.index('acknowledged.id != id || !appPreferenceAcknowledges(')
        settle=source.index('putLocalKv(journalKey(id), null)',validation)
        self.assertLess(validation,settle)
        self.assertIn('return@withLock',source[validation:settle])

    def test_exact_lookup_and_legacy_projection_precede_fallback(self):
        source=(SHARED/'AppLanguageRuntime.kt').read_text()
        start=source.index('fun resolveLocalizedResource(')
        fragment=source[start:source.index('/** Preserve first-row',start)]
        self.assertLess(fragment.index('bundled?.exactLocalizedValue(selected)'), fragment.index('primary?.extractLocalizedString(selected)'))
        self.assertIn('val strings = mergeLocalizedStringGroups(strings, resourceStrings)',(SHARED/'CommonMain.kt').read_text())

    def test_server_overlay_runs_before_optional_public_url_rewrite(self):
        source=(SERVER/'Server.kt').read_text()
        start=source.index('fun Application.buildGlobalConfigurationJson(')
        fragment=source[start:source.index('\n}',start)+2]
        self.assertIn('enrichGlobalConfigurationLanguages(configured)',fragment)
        self.assertIn('.map(::completeResponseLanguages)',source)
        self.assertIn('internal val aitaServerRuntimeClassLoader',source)
        self.assertIn('from("config")',(ROOT/'server/build.gradle.kts').read_text())

    def test_logged_suspend_default_problem_is_fixed_in_both_real_wrappers(self):
        for folder,file,name in [('marketplace','MarketplaceRoutes.kt','marketResult'),('support','CompanySupportRoutes.kt','supportResult')]:
            source=(SERVER/folder/file).read_text()
            start=source.index('private suspend inline fun <reified T> RoutingCall.'+name)
            function=source[start:source.index('\n}',start)+2]
            self.assertIn('noinline after: suspend (T) -> Unit = {}',function)
            self.assertNotIn('crossinline after',function)
            self.assertLess(function.index('newSuspendedTransaction'),function.index('after(result)'))
            self.assertIn('throw cancelled',function)

    def test_untranslated_event_fallbacks_are_not_discarded(self):
        source=(SHARED/'messages/EventResourceCatalogue.kt').read_text()
        self.assertIn('EventMessages.renderExact(resolved, value.language, resources::values) == null',source)
        self.assertIn('rendered.withMissingLocalizedValues(fallback)',source)
        presentation=(SHARED/'messages/EventMessagePresentation.kt').read_text()
        self.assertLess(presentation.index('translations.exactLocalizedValue(language)'),presentation.index('?: EventMessages.render(reference'))

if __name__ == '__main__': unittest.main(verbosity=2)
