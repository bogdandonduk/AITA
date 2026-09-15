package kz.aita
import kotlin.test.*
class VoiceLanguagePolicyTest {
    @Test fun oldAndroidUsesDeviceDefaultNotAnInventedDetection() { assertEquals(VoiceLanguageMode.DeviceDefault,voiceLanguagePlan(33,true,null).mode) }
    @Test fun automaticModeWithoutAnInventoryRequestsUnrestrictedSwitching() { val p=voiceLanguagePlan(34,true,null);assertTrue(p.detect&&p.switch);assertNull(p.installedLanguages) }
    @Test fun everyInstalledLanguageCanParticipateRegardlessOfAppLanguage() { assertEquals(listOf("de-DE","ja-JP","ru-RU"),voiceLanguagePlan(36,true,listOf("de-DE","ja-JP","ru-RU")).installedLanguages) }
    @Test fun missingDownloadedModelsDoNotClaimSwitchingSupport() { assertEquals(VoiceLanguageMode.DetectionOnly,voiceLanguagePlan(34,true,emptyList()).mode) }
    @Test fun aSingleModelAllowsDetectionButNotSwitching() { assertFalse(voiceLanguagePlan(34,true,listOf("en-US")).switch) }
    @Test fun explicitLanguageModeStillWorks() { assertFalse(voiceLanguagePlan(36,false,null).detect) }
    @Test fun malformedAndUnknownLanguageResultsAreIgnored() { for(s in listOf("","und","ru<script>","../en","en-")) assertNull(normalizedDetectedLanguage(s)) }
    @Test fun languageTagsAreNormalizedAndDeduplicated() { assertEquals(listOf("en-US","ru-RU"),voiceLanguagePlan(34,true,listOf("en_US","en-US","ru-RU")).installedLanguages) }
}
