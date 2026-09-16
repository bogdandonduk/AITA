package kz.aita.updates

import kotlinx.serialization.encodeToString
import kotlinx.coroutines.runBlocking
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.test.*

class ClientReleaseSignatureTest {
    companion object { private val pair=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair() }
    private val release=ClientRelease(channel=ReleaseChannel.RELEASE,sequence=1,id="one",version="1.0.1",build=2,publishedAtMillis=1,expiresAtMillis=2)
    private fun envelope(payload: ByteArray=clientReleaseJson.encodeToString(release).encodeToByteArray()): String {
        val sig=Signature.getInstance("SHA256withRSA").run{initSign(pair.private);update(payload);sign()}
        return clientReleaseJson.encodeToString(SignedClientRelease(Base64.encode(payload),Base64.encode(sig)))
    }
    private suspend fun verify(text: String,key:ByteArray=pair.public.encoded)=verifyClientReleaseEnvelope(text,key){body,sig,pub->
        runCatching { Signature.getInstance("SHA256withRSA").run{initVerify(KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(pub)));update(body);verify(sig)} }.getOrDefault(false)
    }
    @Test fun verifiesExactSerializedBytes()=runBlocking{assertEquals(release,verify(envelope())?.release)}
    @Test fun changedPayloadDoesNotVerify()=runBlocking{
        val original=clientReleaseJson.decodeFromString<SignedClientRelease>(envelope())
        val changed=original.copy(payload=Base64.encode(clientReleaseJson.encodeToString(release.copy(build=99)).encodeToByteArray()))
        assertNull(verify(clientReleaseJson.encodeToString(changed)))
    }
    @Test fun anotherSigningKeyDoesNotVerify()=runBlocking{
        val other=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
        assertNull(verify(envelope(),other.public.encoded))
    }
    @Test fun unsupportedAlgorithmMalformedOrOversizedEnvelopeFailsClosed()=runBlocking{
        assertNull(verify("{}"));assertNull(verify("x".repeat(CLIENT_RELEASE_MAX_BYTES+1)))
        val original=clientReleaseJson.decodeFromString<SignedClientRelease>(envelope())
        assertNull(verify(clientReleaseJson.encodeToString(original.copy(algorithm="none"))))
    }
    @Test fun iosPkcs1ExtractionAcceptsRsaOnlyAndRejectsTrailingBytes() {
        assertNotNull(clientRsaPkcs1PublicKey(pair.public.encoded))
        assertNull(clientRsaPkcs1PublicKey(pair.public.encoded+byteArrayOf(0)))
        assertNull(clientRsaPkcs1PublicKey(byteArrayOf(1,2,3)))
        val ec=KeyPairGenerator.getInstance("EC").apply{initialize(256)}.generateKeyPair()
        assertNull(clientRsaPkcs1PublicKey(ec.public.encoded))
    }
}
