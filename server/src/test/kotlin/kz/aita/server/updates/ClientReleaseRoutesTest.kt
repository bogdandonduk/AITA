package kz.aita.server.updates

import kotlinx.serialization.encodeToString
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kz.aita.updates.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.*

class ClientReleaseRoutesTest {
    companion object {private val pair=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()}
    private val now=1_789_530_000_000L
    private fun release()=ClientRelease(channel=ReleaseChannel.RELEASE,sequence=4,id="release4",version="1.0.1",build=4,publishedAtMillis=now-1000,expiresAtMillis=now+86400000,
        artifacts=listOf(ClientArtifact(ClientOs.MACOS,kind=InstallerKind.PKG,url="https://updates.example.org/"+"a".repeat(64)+".pkg",bytes=3,sha256="a".repeat(64))))
    private fun write(root:Path,release:ClientRelease=release()) {
        val payload=clientReleaseJson.encodeToString(release).toByteArray();val sig=Signature.getInstance("SHA256withRSA").run{initSign(pair.private);update(payload);sign()}
        Files.writeString(root.resolve("release.json"),clientReleaseJson.encodeToString(SignedClientRelease(Base64.getEncoder().encodeToString(payload),Base64.getEncoder().encodeToString(sig))))
    }
    private fun temporary(test:(Path)->Unit) {val root=Files.createTempDirectory("aita-release-test-").toRealPath();try{test(root)}finally{root.toFile().deleteRecursively()}}
    @Test fun absentReleaseReturns204WithoutAuthentication()=temporary{root->testApplication{
        application{routing{installClientUpdateRoutes(ClientReleaseCatalog(root,pair.public.encoded){now})}}
        assertEquals(HttpStatusCode.NoContent,client.get("/client-updates/release.json").status)
        assertEquals(HttpStatusCode.NotFound,client.get("/client-updates/preview.json").status)
    }}
    @Test fun signedReleaseSupportsRevalidationAndChannelIsolation()=temporary{root->write(root);testApplication{
        application{routing{installClientUpdateRoutes(ClientReleaseCatalog(root,pair.public.encoded){now})}}
        val first=client.get("/client-updates/release.json");assertEquals(HttpStatusCode.OK,first.status);assertNotNull(first.headers[HttpHeaders.ETag])
        assertEquals(HttpStatusCode.NotModified,client.get("/client-updates/release.json"){header(HttpHeaders.IfNoneMatch,first.headers[HttpHeaders.ETag]!!)}.status)
        assertEquals(HttpStatusCode.NoContent,client.get("/client-updates/test.json").status)
    }}
    @Test fun invalidSignatureNeverBecomesPublicMetadata()=temporary{root->Files.writeString(root.resolve("release.json"),"{}");testApplication{
        application{routing{installClientUpdateRoutes(ClientReleaseCatalog(root,pair.public.encoded){now})}}
        assertEquals(HttpStatusCode.ServiceUnavailable,client.get("/client-updates/release.json").status)
    }}
    @Test fun expiredSignedReleaseIsRejected()=temporary{root->write(root,release().copy(expiresAtMillis=now));testApplication{
        application{routing{installClientUpdateRoutes(ClientReleaseCatalog(root,pair.public.encoded){now})}}
        assertEquals(HttpStatusCode.ServiceUnavailable,client.get("/client-updates/release.json").status)
    }}
    @Test fun filesAreServedOnlyThroughContentAddressedNames()=temporary{root->
        val directory=Files.createDirectory(root.resolve("artifacts"));val name="a".repeat(64)+".pkg";Files.writeString(directory.resolve(name),"abc")
        Files.writeString(directory.resolve("secret.txt"),"secret")
        val bundle="b".repeat(64)+".aab";Files.writeString(directory.resolve(bundle),"bundle")
        testApplication{application{routing{installClientUpdateRoutes(ClientReleaseCatalog(root,pair.public.encoded){now})}}
            assertEquals("abc",client.get("/client-updates/artifacts/$name").bodyAsText())
            assertEquals("bundle",client.get("/client-updates/artifacts/$bundle").bodyAsText())
            assertEquals(HttpStatusCode.NotFound,client.get("/client-updates/artifacts/secret.txt").status)
        }
    }
    @Test fun interruptedInstallerCanResumeWithAnExactBoundedRange() = temporary { root ->
        val directory = Files.createDirectory(root.resolve("artifacts"))
        val name = "c".repeat(64) + ".exe"
        Files.writeString(directory.resolve(name), "0123456789")
        testApplication {
            application { routing { installClientUpdateRoutes(ClientReleaseCatalog(root, pair.public.encoded) { now }) } }
            val response = client.get("/client-updates/artifacts/$name") { header(HttpHeaders.Range, "bytes=4-") }
            assertEquals(HttpStatusCode.PartialContent, response.status)
            assertEquals("bytes 4-9/10", response.headers[HttpHeaders.ContentRange])
            assertEquals("6", response.headers[HttpHeaders.ContentLength])
            assertEquals("456789", response.bodyAsText())
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable,
                client.get("/client-updates/artifacts/$name") { header(HttpHeaders.Range, "bytes=20-") }.status)
        }
    }
    @Test fun symlinkedInstallerIsNotServed()=temporary{root->
        val directory=Files.createDirectory(root.resolve("artifacts"));val outside=Files.createTempFile("aita-external-",".pkg").toRealPath();val name="b".repeat(64)+".pkg"
        try {Files.writeString(outside,"abc");Files.createSymbolicLink(directory.resolve(name),outside)
            testApplication{application{routing{installClientUpdateRoutes(ClientReleaseCatalog(root,pair.public.encoded){now})}}
                assertEquals(HttpStatusCode.NotFound,client.get("/client-updates/artifacts/$name").status)}
        } finally {Files.deleteIfExists(directory.resolve(name));Files.deleteIfExists(outside)}
    }
}
