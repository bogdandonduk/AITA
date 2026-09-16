package kz.aita

import kotlinx.coroutines.*
import kotlin.test.*

class AccountAppModeTest {
    private fun scenario(test: suspend (Fixture)->Unit)=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try { test(Fixture(scope)) } finally { scope.cancel() }
    }
    private class Fixture(scope:CoroutineScope) {
        var owner=AppModeOwner("account-a",1)
        val saved=mutableMapOf<String,SavedAppMode>()
        val sent=mutableListOf<Pair<AppModeOwner,Int>>()
        val ack=mutableListOf<Pair<AppModeOwner,Int>>()
        var visible=DEFAULT_NEW_ACCOUNT_APP_MODE
        var beforePublish: ((Int)->Unit)?=null
        var online=false
        var loading:CompletableDeferred<Unit>?=null
        var sending:CompletableDeferred<Unit>?=null
        val c=AccountAppModeCoordinator(scope,{it==owner},
            load={loading?.await();saved[it.accountId]},persist={who,value->saved[who.accountId]=value},
            publish={beforePublish?.invoke(it);visible=it},sync={who,mode->sent.add(who to mode);sending?.await();online},
            acknowledged={who,mode->ack.add(who to mode)})
    }
    @Test fun newAccountsStartInBuyerWithoutChangingLegacyFallback() {
        assertEquals(APP_MODE_BUYER,DEFAULT_NEW_ACCOUNT_APP_MODE)
        assertEquals(APP_MODE_STORE,initialAccountAppMode(null,null,null).mode)
    }
    @Test fun newServerDefaultWinsOverTheDevicesLegacyStore()=scenario { f->
        f.c.adopt(f.owner,APP_MODE_BUYER,true,APP_MODE_STORE)
        assertEquals(APP_MODE_BUYER,f.visible);assertFalse(f.c.snapshot.pending)
    }
    @Test fun existingExplicitStoreAndSupplierPreferencesArePreserved()=scenario { f->
        f.c.adopt(f.owner,APP_MODE_SUPPLIER,true,APP_MODE_BUYER);assertEquals(APP_MODE_SUPPLIER,f.visible)
        f.owner=f.owner.copy(generation=2);f.c.adopt(f.owner,APP_MODE_STORE,true);assertEquals(APP_MODE_STORE,f.visible)
    }
    @Test fun unmigratedOwnerCanKeepTheirLegacyChoice()=scenario { f->
        f.c.adopt(f.owner,null,true,APP_MODE_SUPPLIER)
        assertEquals(APP_MODE_SUPPLIER,f.visible);assertTrue(f.c.snapshot.pending)
        assertEquals(SavedAppMode(APP_MODE_SUPPLIER,true),f.saved[f.owner.accountId])
    }
    @Test fun oldCachedAccountCannotOverrideALiveServerChoice()=scenario { f->
        f.c.adopt(f.owner,null,false,APP_MODE_STORE)
        assertTrue(f.sent.isEmpty());assertFalse(f.c.snapshot.pending)
        f.c.adopt(f.owner,APP_MODE_BUYER,true)
        assertEquals(APP_MODE_BUYER,f.visible);assertTrue(f.sent.isEmpty())
    }
    @Test fun aNewerLocalClickBeatsALateAccountResponse()=scenario { f->
        f.c.adopt(f.owner,APP_MODE_BUYER,false)
        f.c.select(f.owner,APP_MODE_SUPPLIER)
        f.c.adopt(f.owner,APP_MODE_BUYER,true)
        assertEquals(APP_MODE_SUPPLIER,f.visible);assertTrue(f.c.snapshot.pending)
    }
    @Test fun offlineSelectionRestoresAndRetriesWithoutAnotherClick()=scenario { f->
        f.saved[f.owner.accountId]=SavedAppMode(APP_MODE_SUPPLIER,true)
        f.c.adopt(f.owner,APP_MODE_BUYER,true);assertEquals(APP_MODE_SUPPLIER,f.visible)
        f.online=true;f.c.retryPending();yield()
        assertFalse(f.c.snapshot.pending);assertEquals(SavedAppMode(APP_MODE_SUPPLIER,false),f.saved[f.owner.accountId])
    }
    @Test fun switchingAccountsDoesNotInheritAnotherAccountsPendingChoice()=scenario { f->
        f.c.adopt(f.owner,APP_MODE_STORE,true);f.c.select(f.owner,APP_MODE_SUPPLIER)
        f.owner=AppModeOwner("account-b",2);f.c.adopt(f.owner,APP_MODE_BUYER,true)
        assertEquals(APP_MODE_BUYER,f.visible);assertEquals(APP_MODE_SUPPLIER,f.saved["account-a"]?.mode)
    }
    @Test fun oldSessionCannotAdoptAfterAnAccountSwitch()=scenario { f->
        val old=f.owner;f.owner=AppModeOwner("account-b",2)
        f.c.adopt(f.owner,APP_MODE_BUYER,true);f.c.adopt(old,APP_MODE_STORE,true)
        assertEquals(APP_MODE_BUYER,f.visible);assertEquals(f.owner,f.c.snapshot.owner)
    }
    @Test fun lateAcknowledgementCannotClearANewerChoice()=scenario { f->
        f.c.adopt(f.owner,APP_MODE_BUYER,true);f.online=true;f.sending=CompletableDeferred()
        f.c.select(f.owner,APP_MODE_STORE);f.c.select(f.owner,APP_MODE_SUPPLIER)
        f.sending!!.complete(Unit);yield()
        assertEquals(APP_MODE_SUPPLIER,f.visible);assertFalse(f.c.snapshot.pending)
        assertEquals(APP_MODE_SUPPLIER,f.ack.last().second);assertTrue(f.ack.none { it.second==APP_MODE_STORE })
    }
    @Test fun aToBToANewSessionRejectsTheOldAcknowledgement()=scenario { f->
        f.c.adopt(f.owner,APP_MODE_BUYER,true);f.online=true;f.sending=CompletableDeferred()
        f.c.select(f.owner,APP_MODE_STORE);val old=f.owner
        f.owner=AppModeOwner("account-b",2);f.c.adopt(f.owner,APP_MODE_BUYER,true)
        f.owner=AppModeOwner("account-a",3);f.c.adopt(f.owner,APP_MODE_BUYER,true)
        f.sending!!.complete(Unit);yield()
        assertTrue(f.ack.none { it.first==old });assertEquals(3L,f.c.snapshot.owner?.generation)
    }
    @Test fun invalidAndUnavailableManufacturerSelectionsAreRejected()=scenario { f->
        f.c.adopt(f.owner,APP_MODE_BUYER,true)
        listOf(-1,99,APP_MODE_MANUFACTURER).forEach {f.c.select(f.owner,it)}
        assertEquals(APP_MODE_BUYER,f.visible);assertFalse(isSelectableAppMode(null))
    }
    @Test fun overlappingProjectionFinishesWithTheNewestMode()=scenario { f->
        f.c.adopt(f.owner,APP_MODE_BUYER,true)
        f.beforePublish={ mode ->
            if(mode==APP_MODE_STORE) {
                f.beforePublish=null
                f.c.select(f.owner,APP_MODE_SUPPLIER)
            }
        }
        f.c.select(f.owner,APP_MODE_STORE)
        assertEquals(APP_MODE_SUPPLIER,f.visible)
        assertEquals(APP_MODE_SUPPLIER,f.c.snapshot.mode)
    }
    @Test fun oldAppearancePreferencesHaveNoAppModeField() {
        val encoded=jsonBase.encodeToString(UserPreferencesDataModel.serializer(),UserPreferencesDataModel())
        assertFalse(encoded.contains("appMode"))
    }
}
