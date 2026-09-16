package kz.aita

import kotlin.io.encoding.Base64
import kotlin.test.*

class ProfilePhotoProtocolTest {
    // Only header policy is under test; this is never passed to a bitmap decoder.
    private val boundedHeader=Base64.encode(byteArrayOf(-1,-40,-1,-64,0,8,8,2,0,2,0,0,-1,-39))
    @Test fun accountOwnershipAndEmptyStateAreExplicit() {
        assertTrue(validProfilePhotoSnapshot(ProfilePhotoSnapshot("a"),"a"))
        assertFalse(validProfilePhotoSnapshot(ProfilePhotoSnapshot("a"),"b"))
        assertFalse(validProfilePhotoSnapshot(ProfilePhotoSnapshot("a",-1),"a"))
        assertFalse(validProfilePhotoSnapshot(ProfilePhotoSnapshot("a",0,boundedHeader,1),"a"))
    }
    @Test fun tombstoneIsNotMistakenForNeverUploaded() {
        val removed=ProfilePhotoSnapshot("a",2,null,1)
        assertTrue(validProfilePhotoSnapshot(removed,"a"))
        assertEquals(removed,jsonBase.decodeFromString(ProfilePhotoSnapshot.serializer(),jsonBase.encodeToString(ProfilePhotoSnapshot.serializer(),removed)))
    }
    @Test fun bytesAreBoundedBeforePlatformImageDecode() {
        assertNotNull(profilePhotoJpegBytes(boundedHeader))
        assertNull(profilePhotoJpegBytes("../../anything"))
        assertNull(profilePhotoJpegBytes(Base64.encode(byteArrayOf(-1,-40,1,2))))
        assertNull(profilePhotoJpegBytes(Base64.encode(byteArrayOf(-1,-40,-1,-64,0,8,8,127,-1,127,-1,0,-1,-39))))
        assertNull(profilePhotoJpegBytes("a".repeat(PROFILE_PHOTO_MAX_OUTPUT_BYTES*2)))
    }
    @Test fun acknowledgementsMustMatchActionAndAdjacentRevision() {
        val change=ProfilePhotoChange(4,boundedHeader)
        assertTrue(profilePhotoAcknowledges(change,ProfilePhotoSnapshot("a",5,boundedHeader,1),"a"))
        assertFalse(profilePhotoAcknowledges(change,ProfilePhotoSnapshot("a",6,boundedHeader,1),"a"))
        assertFalse(profilePhotoAcknowledges(change,ProfilePhotoSnapshot("a",5,null,1),"a"))
        assertFalse(profilePhotoAcknowledges(change,ProfilePhotoSnapshot("other",5,boundedHeader,1),"a"))
    }
    @Test fun realtimeRefreshDoesNotDiscardPreviewOrRaiseItsExpectedRevision() {
        assertTrue(shouldRefreshProfilePhoto(2,1,false,false))
        assertFalse(shouldRefreshProfilePhoto(2,1,true,false))
        assertFalse(shouldRefreshProfilePhoto(2,1,false,true))
        assertFalse(shouldRefreshProfilePhoto(2,2,false,false))
        assertTrue(shouldRefreshProfilePhoto(3,2,false,false))
    }
}
