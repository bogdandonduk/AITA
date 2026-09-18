package kz.aita

import kotlin.test.*
import kotlinx.coroutines.test.runTest

class MarketSavedShopsTest {
    private val shop="00000000-0000-0000-0000-000000000001"
    private fun snapshot()=MarketSavedShops("buyer",1,listOf(shop),100)
    @Test fun validatesBoundedUniqueCanonicalAccountBookmarks() {
        assertTrue(snapshot().isValidSavedShops("buyer"))
        assertFalse(snapshot().isValidSavedShops("other"))
        assertFalse(snapshot().copy(storeIds=listOf(shop,shop)).isValidSavedShops("buyer"))
        assertFalse(snapshot().copy(revision=-1).isValidSavedShops("buyer"))
        assertFalse(MarketSavedShopChange("invalid",true,0).isValidSavedShopChange())
        assertFalse(MarketSavedShopChange(shop,true,Long.MAX_VALUE).isValidSavedShopChange())
    }
    @Test fun mutationAcknowledgementMustContainRequestedStateAndRevision()=runTest {
        val owner=object:MarketAccountScope { override val accountId="buyer";override val generation=1L;override fun isCurrent()=true }
        val response=ResponseDataModel<MarketSavedShops>(null,snapshot(),false,200)
        assertFalse(readOwnedSavedShops(owner,MarketSavedShopChange(shop,true,0)){response}.negative)
        assertTrue(readOwnedSavedShops(owner,MarketSavedShopChange(shop,false,0)){response}.negative)
        assertTrue(readOwnedSavedShops(owner,MarketSavedShopChange(shop,true,2)){response}.negative)
    }
    @Test fun suspendedResultCannotCrossAccountChange()=runTest {
        var current=true
        val owner=object:MarketAccountScope {override val accountId="buyer";override val generation=1L;override fun isCurrent()=current}
        val result=readOwnedSavedShops(owner) {current=false;ResponseDataModel(null,snapshot(),false,200)}
        assertTrue(result.negative);assertNull(result.payload)
    }
    @Test fun savedDirectoryRequestCannotAcceptAnUnfilteredOlderServerResponse() {
        val request=MarketShopDirectoryRequest(savedOnly=true)
        val page=MarketShopDirectoryResult("buyer",request,emptyList(),0,100)
        assertTrue(page.isValidShopDirectoryResult("buyer",request))
        assertFalse(page.copy(request=request.copy(savedOnly=false)).isValidShopDirectoryResult("buyer",request))
    }
}
