package kz.aita

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.io.encoding.Base64

const val PROFILE_PHOTO_MAX_INPUT_BYTES = 8 * 1024 * 1024
const val PROFILE_PHOTO_MAX_OUTPUT_BYTES = 512 * 1024
const val PROFILE_PHOTO_EDGE = 512

@Serializable data class ProfilePhotoSnapshot(
    val accountId: String, val revision: Long = 0, val jpegBase64: String? = null, val updatedAtMillis: Long = 0,
    val mode: ProfilePhotoMode? = null
)
@Serializable data class ProfilePhotoPreview(val jpegBase64: String)
@Serializable data class ProfilePhotoChange(val expectedRevision: Long, val jpegBase64: String? = null)

fun profilePhotoJpegBytes(value: String?): ByteArray? {
    if (value == null || value.length !in 4..((PROFILE_PHOTO_MAX_OUTPUT_BYTES + 2) / 3 * 4)) return null
    return runCatching { Base64.decode(value) }.getOrNull()?.takeIf {
        it.size in 4..PROFILE_PHOTO_MAX_OUTPUT_BYTES && profilePhotoHasBoundedFrame(it)
    }
}
fun validProfilePhotoSnapshot(value: ProfilePhotoSnapshot, owner: String, mode: ProfilePhotoMode? = null): Boolean =
    (mode == null || value.mode == mode) &&
    value.accountId == owner && value.revision >= 0 && value.updatedAtMillis >= 0 &&
        (if (value.revision == 0L) value.jpegBase64 == null && value.updatedAtMillis == 0L
        else value.updatedAtMillis > 0L && (value.jpegBase64 == null || profilePhotoJpegBytes(value.jpegBase64) != null))

object ProfilePhotoClient {
    suspend fun load(generation: Long, mode: ProfilePhotoMode? = null) = networkRequest<ProfilePhotoSnapshot, Unit>(HttpMethod.Get,
        endpointUrl = mode?.let { "users/mode-profile-photo/${it.name.lowercase()}" } ?: "users/profile-photo", expectedSessionGeneration = generation)
    suspend fun preview(bytes: ByteArray, generation: Long, mode: ProfilePhotoMode? = null): ResponseDataModel<ProfilePhotoPreview> {
        require(bytes.size in 1..PROFILE_PHOTO_MAX_INPUT_BYTES)
        return networkRequest(HttpMethod.Post, endpointUrl = mode?.let { "users/mode-profile-photo/${it.name.lowercase()}/preview" } ?: "users/profile-photo/preview", body = bytes,
            contentType = ContentType.Application.OctetStream, expectedSessionGeneration = generation)
    }
    suspend fun save(change: ProfilePhotoChange, generation: Long, mode: ProfilePhotoMode? = null) = networkRequest<ProfilePhotoSnapshot, ProfilePhotoChange>(
        HttpMethod.Put, endpointUrl = mode?.let { "users/mode-profile-photo/${it.name.lowercase()}" } ?: "users/profile-photo", body = change, expectedSessionGeneration = generation)
}

/** Read dimensions before passing downloaded bytes to a platform decoder. Only normalized JPEG frames are used. */
private fun profilePhotoHasBoundedFrame(bytes: ByteArray): Boolean {
    fun b(i: Int) = bytes[i].toInt() and 255
    fun u16(i: Int) = (b(i) shl 8) or b(i + 1)
    if (bytes.size < 4 || b(0) != 255 || b(1) != 216 || b(bytes.size - 2) != 255 || b(bytes.size - 1) != 217) return false
    var offset = 2
    while (offset + 4 <= bytes.size) {
        if (b(offset) != 255) return false
        while (offset < bytes.size && b(offset) == 255) offset++
        if (offset >= bytes.size) return false
        val marker = b(offset++)
        if (marker == 218 || marker == 217 || offset + 2 > bytes.size) return false
        if (marker in 208..216 || marker == 1) continue
        val length = u16(offset)
        if (length < 2 || offset.toLong() + length > bytes.size) return false
        if (marker in setOf(192, 193, 194)) {
            return length >= 8 && u16(offset + 3) == PROFILE_PHOTO_EDGE && u16(offset + 5) == PROFILE_PHOTO_EDGE
        }
        offset += length
    }
    return false
}

fun profilePhotoAcknowledges(change: ProfilePhotoChange, result: ProfilePhotoSnapshot, owner: String, mode: ProfilePhotoMode? = null): Boolean =
    validProfilePhotoSnapshot(result, owner, mode) && result.revision >= change.expectedRevision &&
        result.revision - change.expectedRevision <= 1 &&
        ((change.jpegBase64 == null) == (result.jpegBase64 == null))

/** A scoped server event carries no image bytes. The visible editor re-reads its own account. */
object ProfilePhotoSignals {
    private val mutable=MutableStateFlow(0L)
    val revision=mutable.asStateFlow()
    fun changed(){mutable.update {if(it==Long.MAX_VALUE)0L else it+1L}}
}
fun shouldRefreshProfilePhoto(signal:Long,observed:Long,hasPreview:Boolean,busy:Boolean):Boolean =
    signal!=observed && !hasPreview && !busy

/** Explicit scopes: switching workspaces never copies a picture across their audiences. */
@Serializable enum class ProfilePhotoMode { STORE, SUPPLIER, MARKETPLACE }
fun profilePhotoModeForApp(mode: Int): ProfilePhotoMode? = when(mode) {
    APP_MODE_STORE -> ProfilePhotoMode.STORE
    APP_MODE_SUPPLIER -> ProfilePhotoMode.SUPPLIER
    APP_MODE_BUYER -> ProfilePhotoMode.MARKETPLACE
    else -> null
}
