package kz.aita.updates

import kotlin.io.encoding.Base64

/** A transport envelope is untrusted until the embedded public key verifies its exact bytes. */
data class VerifiedClientRelease(val release: ClientRelease, val envelope: String, val payload: String)

suspend fun verifyClientReleaseEnvelope(
    text: String, key: ByteArray,
    verify: suspend (ByteArray, ByteArray, ByteArray) -> Boolean
): VerifiedClientRelease? {
    if (text.length > CLIENT_RELEASE_MAX_BYTES || key.size !in 256..2048) return null
    return try {
        val envelope = clientReleaseJson.decodeFromString<SignedClientRelease>(text)
        if (envelope.algorithm != "RS256" || envelope.payload.length > 240_000 || envelope.signature.length !in 300..1400) return null
        val payload = Base64.decode(envelope.payload)
        val signature = Base64.decode(envelope.signature)
        if (!verify(payload, signature, key)) return null
        VerifiedClientRelease(clientReleaseJson.decodeFromString<ClientRelease>(payload.decodeToString(throwOnInvalidSequence = true)), text, envelope.payload)
    } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
    catch (_: Exception) { null }
}

/** Same sequence must identify exactly the same signed payload; lower builds/sequences never win. */
fun isClientReleaseRollback(candidate: VerifiedClientRelease, accepted: VerifiedClientRelease?): Boolean =
    accepted != null && (candidate.release.channel != accepted.release.channel ||
        candidate.release.sequence < accepted.release.sequence || candidate.release.build < accepted.release.build ||
        compareClientVersions(candidate.release.version, accepted.release.version) < 0 ||
        (candidate.release.sequence == accepted.release.sequence && candidate.payload != accepted.payload) ||
        (candidate.release.build == accepted.release.build &&
            (candidate.release.id != accepted.release.id || candidate.release.version != accepted.release.version ||
                !candidate.release.artifacts.containsAll(accepted.release.artifacts))))
