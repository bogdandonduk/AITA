package kz.aita

import kotlin.test.*

class StorePeopleAndScaleTest {
    private fun profile()=StorePersonProfile("viewer","store","person","Name",checkedAtMillis=10000)
    @Test fun everyPhotoModeIsIsolatedAndUnavailableModesDoNotAliasStore() {
        assertEquals(ProfilePhotoMode.STORE,profilePhotoModeForApp(APP_MODE_STORE))
        assertEquals(ProfilePhotoMode.SUPPLIER,profilePhotoModeForApp(APP_MODE_SUPPLIER))
        assertEquals(ProfilePhotoMode.MARKETPLACE,profilePhotoModeForApp(APP_MODE_BUYER))
        assertNull(profilePhotoModeForApp(APP_MODE_MANUFACTURER));assertNull(profilePhotoModeForApp(-1))
        for(mode in ProfilePhotoMode.entries)for(other in ProfilePhotoMode.entries)
            assertEquals(mode==other,validProfilePhotoSnapshot(ProfilePhotoSnapshot("owner",mode=mode),"owner",other))
    }
    @Test fun profileCannotBeReusedForAnotherViewerStoreOrPerson() {
        val p=profile();assertTrue(validStorePersonProfile(p,"viewer","store","person"))
        assertFalse(validStorePersonProfile(p,"other","store","person"))
        assertFalse(validStorePersonProfile(p,"viewer","other","person"))
        assertFalse(validStorePersonProfile(p,"viewer","store","other"))
        assertFalse(validStorePersonProfile(p.copy(photo=ProfilePhotoSnapshot("person",mode=ProfilePhotoMode.MARKETPLACE)),"viewer","store","person"))
    }
    @Test fun duplicateOrMalformedChartDataAndUnboundedAmountsAreRejected() {
        val day=StorePersonDay("1970-01-01",1)
        val stats=StorePersonStatistics(0,9999,days=listOf(day))
        fun valid(s:StorePersonStatistics)=validStorePersonProfile(profile().copy(statistics=s),"viewer","store","person")
        assertTrue(valid(stats));assertFalse(valid(stats.copy(days=listOf(day,day))))
        assertFalse(valid(stats.copy(days=listOf(day.copy(dateUtc="garbage")))))
        assertFalse(valid(stats.copy(days=listOf(day.copy(transactions=-1)))))
        assertFalse(valid(stats.copy(lastActivityMillis=10000)));assertFalse(valid(stats.copy(incompletePriceLines=-1)))
    }
    @Test fun safeProfileLinksNeverAcceptPathOrQueryInjection() {
        assertTrue(validStorePersonId("11111111-1111-4111-8111-111111111111"))
        listOf("","../users","id?days=999","11111111-1111-4111-8111-111111111111/extra").forEach {assertFalse(validStorePersonId(it))}
    }
    @Test fun largeTextIsModeratelyAboveBigWithoutGrowingLayout() {
        val empty=emptyList<StylizedDimensionGroupDataModel>()
        for(id in 0L..3L)assertEquals(assertNotNull(empty.extractValue(id,1))*1.125f,empty.extractValue(id,2))
        for(id in 4L..11L)assertEquals(empty.extractValue(id,1),empty.extractValue(id,2))
        assertEquals(2,normalizeAppSizeModePreference(2));assertEquals(0,normalizeAppSizeModePreference(3))
    }
    @Test fun serverAndBundledBigDimensionsRemainTheBasisForLarge() {
        val big=StylizedDimensionGroupDataModel(0,listOf(StylizedDimensionDataModel(0,16f),StylizedDimensionDataModel(1,24f)))
        assertEquals(27f,AppearanceCatalog.build(bundledDimensions=listOf(big)).dimension(0,2,0f))
        assertEquals(27f,AppearanceCatalog.build(dimensions=listOf(big)).dimension(0,2,0f))
        val explicit=big.copy(values=big.values+StylizedDimensionDataModel(2,30f))
        assertEquals(30f,AppearanceCatalog.build(dimensions=listOf(explicit)).dimension(0,2,0f))
    }
    @Test fun largePreferenceAndModePhotoScopeSurviveWireRoundTrips() {
        val preferences=UserPreferencesDataModel("en",3,2)
        assertEquals(preferences,jsonBase.decodeFromString<UserPreferencesDataModel>(jsonBase.encodeToString(UserPreferencesDataModel.serializer(),preferences)))
        val p=profile().copy(photo=ProfilePhotoSnapshot("person",mode=ProfilePhotoMode.STORE))
        assertEquals(p,jsonBase.decodeFromString<StorePersonProfile>(jsonBase.encodeToString(StorePersonProfile.serializer(),p)))
    }
}
