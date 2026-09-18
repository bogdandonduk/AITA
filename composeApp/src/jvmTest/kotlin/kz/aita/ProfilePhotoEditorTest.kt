package kz.aita

import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.test.*

class ProfilePhotoEditorTest {
    companion object { private val jpeg=Base64.encode(ByteArrayOutputStream().use{ImageIO.write(BufferedImage(512,512,BufferedImage.TYPE_INT_RGB),"JPEG",it);it.toByteArray()}) }
    private class Backend:PhotoEditorBackend {
        var photo=ProfilePhotoSnapshot("owner")
        var current=true;var changes=0;var previews=0;var loads=0
        var gate:CompletableDeferred<Unit>?=null;var failure=false;var conflict=false;var malformed=false
        override suspend fun load():ResponseDataModel<ProfilePhotoSnapshot> {loads++;gate?.await();return ResponseDataModel(null,photo,false,200)}
        override suspend fun preview(bytes:ByteArray):ResponseDataModel<ProfilePhotoPreview> {previews++;gate?.await();return ResponseDataModel(null,ProfilePhotoPreview(jpeg),false,200)}
        override suspend fun change(value:ProfilePhotoChange):ResponseDataModel<ProfilePhotoSnapshot> {
            changes++;gate?.await();if(failure)throw IllegalStateException("simulated dropped response")
            photo=photo.copy(revision=photo.revision+1,jpegBase64=if(conflict)jpeg else value.jpegBase64,updatedAtMillis=1)
            return ResponseDataModel(null,if(malformed)photo.copy(accountId="other") else photo,conflict,if(conflict)409 else 200)
        }
    }
    private fun scenario(mode:ProfilePhotoMode?=null,block:suspend (Backend,PhotoEditor)->Unit)=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);val b=Backend();b.photo=b.photo.copy(mode=mode);val e=PhotoEditor("owner",{b.current},scope,b,mode)
        try{block(b,e)}finally{scope.cancel()}
    }
    @Test fun selectionRetriesMissingInitialSnapshotInsteadOfSilentlyDoingNothing()=scenario{b,e->
        e.select(ProfilePhotoPick(byteArrayOf(1)))
        assertEquals(1,b.loads);assertEquals(1,b.previews);assertNotNull(e.state.value.preview)
        assertEquals(0,b.changes)
    }
    @Test fun selectingAndPreviewingDoesNotPersistPicture()=scenario{b,e->
        e.load();e.select(ProfilePhotoPick(byteArrayOf(1)));assertEquals(1,b.previews);assertEquals(0,b.changes)
        assertNotNull(e.state.value.preview);assertNull(e.state.value.saved!!.jpegBase64)
        e.save();assertEquals(1,b.changes);assertEquals("saved",e.state.value.notice);assertNull(e.state.value.preview)
    }
    @Test fun cancellationOfPickerAndDiscardNeverSendWrite()=scenario{b,e->
        e.load();e.select(ProfilePhotoPick());assertEquals(0,b.previews)
        e.select(ProfilePhotoPick(byteArrayOf(1)));e.discard();assertEquals(0,b.changes);assertNull(e.state.value.preview)
    }
    @Test fun oversizedSelectionDoesNotUpload()=scenario{b,e->
        e.load();e.select(ProfilePhotoPick(ByteArray(PROFILE_PHOTO_MAX_INPUT_BYTES+1)))
        assertEquals(0,b.previews);assertEquals("size",e.state.value.error)
    }
    @Test fun lateLoadFromPreviousAccountDoesNotBecomeVisible()=scenario{b,e->
        b.gate=CompletableDeferred();e.load();b.current=false;b.gate!!.complete(Unit);yield()
        assertNull(e.state.value.saved)
    }
    @Test fun busyEditorDoesNotDuplicateRequests()=scenario{b,e->
        e.load();b.gate=CompletableDeferred();e.select(ProfilePhotoPick(byteArrayOf(1)))
        e.select(ProfilePhotoPick(byteArrayOf(2)));e.save();assertEquals(1,b.previews);assertEquals(0,b.changes)
        b.gate!!.complete(Unit);yield();assertFalse(e.state.value.busy)
    }
    @Test fun conflictAdoptsServerObservationWithoutReplayingOldEdit()=scenario{b,e->
        e.load();e.select(ProfilePhotoPick(byteArrayOf(1)));b.conflict=true;e.save()
        assertEquals("conflict",e.state.value.error);assertNull(e.state.value.preview);assertNull(e.state.value.notice)
        e.save();assertEquals(1,b.changes)
    }
    @Test fun lostReplyKeepsDraftAndNeverClaimsItWasSaved()=scenario{b,e->
        e.load();e.select(ProfilePhotoPick(byteArrayOf(1)));b.failure=true;e.save()
        assertNotNull(e.state.value.preview);assertEquals("network",e.state.value.error);assertNull(e.state.value.notice)
    }
    @Test fun anotherAccountCannotAcknowledgePhotoChange()=scenario{b,e->
        e.load();e.select(ProfilePhotoPick(byteArrayOf(1)));b.malformed=true;e.save()
        assertEquals("owner",e.state.value.saved!!.accountId);assertNotNull(e.state.value.preview);assertNull(e.state.value.notice)
    }
    @Test fun confirmedRemovalRetainsTombstoneRevision()=scenario{b,e->
        b.photo=ProfilePhotoSnapshot("owner",1,jpeg,1);e.load();e.save(remove=true)
        assertNull(e.state.value.saved!!.jpegBase64);assertEquals(2,e.state.value.saved!!.revision);assertEquals("removed",e.state.value.notice)
    }
    @Test fun lateWriteFromPreviousSessionCannotUpdateUi()=scenario{b,e->
        e.load();e.select(ProfilePhotoPick(byteArrayOf(1)));b.gate=CompletableDeferred();e.save()
        b.current=false;b.gate!!.complete(Unit);yield();assertNull(e.state.value.notice)
    }
    @Test fun anotherModeCannotBeLoadedIntoStorePhotoEditor()=scenario(ProfilePhotoMode.STORE){b,e->
        b.photo=b.photo.copy(mode=ProfilePhotoMode.SUPPLIER);e.load()
        assertNull(e.state.value.saved);assertNotNull(e.state.value.error)
    }
    @Test fun anotherModesAcknowledgementNeverCompletesStoreSave()=scenario(ProfilePhotoMode.STORE){b,e->
        e.load();e.select(ProfilePhotoPick(byteArrayOf(1)))
        b.photo=b.photo.copy(mode=ProfilePhotoMode.MARKETPLACE);e.save()
        assertEquals(ProfilePhotoMode.STORE,e.state.value.saved?.mode)
        assertNotNull(e.state.value.preview);assertNull(e.state.value.notice)
    }
    @Test fun modeSwitchInvalidatesInFlightPreviewWithoutChangingAnotherMode()=scenario(ProfilePhotoMode.STORE){b,e->
        e.load();b.gate=CompletableDeferred();e.select(ProfilePhotoPick(byteArrayOf(1)))
        b.current=false;b.gate!!.complete(Unit);yield()
        assertNull(e.state.value.preview);assertEquals(0,b.changes)
    }
}
